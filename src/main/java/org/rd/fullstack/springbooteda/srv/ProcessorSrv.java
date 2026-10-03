/*
 * Copyright 2026; Réal Demers.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.rd.fullstack.springbooteda.srv;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;
import org.rd.fullstack.springbooteda.dao.InventoryRepository;
import org.rd.fullstack.springbooteda.dao.JrnEventRepository;
import org.rd.fullstack.springbooteda.dao.PersonRepository;
import org.rd.fullstack.springbooteda.dao.ProductRepository;
import org.rd.fullstack.springbooteda.dao.RequestRepository;
import org.rd.fullstack.springbooteda.dto.EventFooter;
import org.rd.fullstack.springbooteda.dto.EventHeader;
import org.rd.fullstack.springbooteda.dto.EventMessage;
import org.rd.fullstack.springbooteda.dto.Inventory;
import org.rd.fullstack.springbooteda.dto.JrnEvent;
import org.rd.fullstack.springbooteda.dto.Person;
import org.rd.fullstack.springbooteda.dto.Product;
import org.rd.fullstack.springbooteda.dto.Request;
import org.rd.fullstack.springbooteda.util.EventType;
import org.rd.fullstack.springbooteda.util.JsonMapper;
import org.rd.fullstack.springbooteda.util.Operation;
import org.rd.fullstack.springbooteda.util.Result;
import org.rd.fullstack.springbooteda.util.kafka.KafkaConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Processing unit of the pipeline.
 *
 * <p>This logic is intentionally kept in a dedicated bean (rather than as a method
 * of {@code PipelineSrv}) so that the Spring transactional proxy actually applies:
 * a self-invocation ({@code this.process(...)}) bypasses the AOP proxy and would
 * silently disable {@link Transactional}. Each message is therefore handled in its
 * own JPA transaction that rolls back on failure.</p>
 *
 * <p>The method is also <strong>idempotent</strong>: a request whose result is no
 * longer {@code PENDING}/{@code BACK_ORDER} has already been handled and is skipped.
 * This makes safe the at-least-once redelivery performed by the Kafka error handler
 * on retries/rebalances.</p>
 */
@Service
public class ProcessorSrv {

    /** Outcome of {@link #sanityCheck}: whether processing should continue, and the tracking
     *  jrn-event already resolved (created, fetched, or updated) for reuse by the caller —
     *  {@code null} when idempotency tracking does not apply (no header). */
    private record SanityResult(boolean proceed, JrnEvent jrnEvent) {}

    private static final Logger logger =
        LoggerFactory.getLogger(ProcessorSrv.class);

    private final JrnEventRepository jrnEventRepository;
    private final InventoryRepository inventoryRepository;
    private final RequestRepository requestRepository;
    private final ProductRepository productRepository;
    private final PersonRepository personRepository;
    private final InventoryCheckSrv inventoryCheckSrv;

    public ProcessorSrv(JrnEventRepository jrnEventRepository, InventoryRepository inventoryRepository,
                        RequestRepository requestRepository, ProductRepository productRepository,
                        PersonRepository personRepository, InventoryCheckSrv inventoryCheckSrv) {
        super();
        this.jrnEventRepository  = jrnEventRepository;
        this.inventoryRepository = inventoryRepository;
        this.requestRepository   = requestRepository;
        this.productRepository   = productRepository;
        this.personRepository    = personRepository;
        this.inventoryCheckSrv   = inventoryCheckSrv;
    }

    @Transactional(propagation = Propagation.REQUIRED, isolation = Isolation.READ_COMMITTED)
    public void process(EventMessage<EventHeader, Request, EventFooter> message,
        boolean latence) throws Exception {

        SanityResult sanity = sanityCheck(message);
        if (! sanity.proceed())
            return; // Already processed, or exception thrown (rollback and retry).

        final Request  request  = message.getPayload();
        final JrnEvent jrnEvent = sanity.jrnEvent();

        // Get inventory for the product.
        Optional<Inventory> inventoryOptional = inventoryRepository.findByProductId(request.getProductId());
        if (inventoryOptional.isEmpty()) { // Big problem here.
            finalizeOutcome(request, jrnEvent, Result.ERROR, false);
            return;
        }

        // Product (unit price) and client (balance) are required to price the request and
        // to debit/credit the customer account. The client row is already serialized by the
        // Hazelcast per-client lock held in PipelineSrv.handle(), so this whole read-check-
        // write runs atomically per client within the single JPA transaction.
        Inventory inventory = inventoryOptional.get();
        Optional<Product> productOptional = productRepository.findById(request.getProductId());
        Optional<Person>  personOptional  = personRepository.findById(request.getPersonId());
        if (productOptional.isEmpty() || personOptional.isEmpty()) { // Big problem here.
            finalizeOutcome(request, jrnEvent, Result.ERROR, false);
            return;
        }

        Product product = productOptional.get();
        Person  person  = personOptional.get();

        // Cost of the request = unit price * quantity.
        BigDecimal cost = product.getPrice().multiply(BigDecimal.valueOf(request.getQty()));
        Operation oper = request.getOperation();

        switch (oper) {
            case CREDIT: // Sale: inventory decreases, the client pays (balance decreases).
                // Stock check — read via InventoryCheckSrv.readAvailableQty(), NOT via the
                // `inventory` object already loaded above. That call is annotated REQUIRES_NEW:
                // it suspends THIS transaction, runs the read in a brand-new one, and commits it
                // immediately on return — releasing it well before the sleep below. This split
                // was originally added to defeat mvlocks' TABLE-level write lock (which, under
                // that mode, serialized check+sleep+write across ALL requests and silently closed
                // the race window — verified empirically). The database now runs in MVCC mode
                // instead (see application.yml), under which a blind relative UPDATE like
                // creditQTY below doesn't even need this split to race (MVCC raises no conflict
                // for it either way — see the comment above the applyLatency call). The split is
                // kept regardless: it's still a correct, more realistic way to force the stale
                // read, and doesn't depend on which HSQLDB tx mode is active.
                final long availableQty = inventoryCheckSrv.readAvailableQty(request.getProductId());

                // Two distinct back-order causes — logged separately so each can be counted
                // (e.g. grep "BACK_ORDER (insufficient STOCK)" vs "... BALANCE)").
                if (availableQty < request.getQty()) {                // not enough stock
                    logger.warn("BACK_ORDER (insufficient STOCK) — requestId={}, productId={}, requested={}, available={}.",
                        request.getRequestId(), request.getProductId(),
                        request.getQty(), availableQty);

                    finalizeOutcome(request, jrnEvent, Result.BACK_ORDER, false);
                    return;
                }
                if (person.getBalance().compareTo(cost) < 0) {        // not enough balance
                    logger.warn("BACK_ORDER (insufficient BALANCE) — requestId={}, personId={}, cost={}, balance={}.",
                        request.getRequestId(), request.getPersonId(),
                        cost, person.getBalance());

                    finalizeOutcome(request, jrnEvent, Result.BACK_ORDER, false);
                    return;
                }

                // Widen the critical section between the stale read/check above and this
                // write, so a concurrent request for the SAME product reproduces the
                // check-then-act lost update. The decrement is an atomic relative UPDATE
                // (qty = qty - :qty) — empirically confirmed (standalone JDBC probe) that
                // HSQLDB's MVCC mode never raises a conflict for this pattern, however many
                // transactions concurrently apply it to the same row: every decrement just
                // applies against whatever is currently committed, so qty silently goes
                // negative (oversell). No conflict is raised, so there is NO retry/DLT here.
                applyLatency(latence, request.getRequestId(), request.getProductId(), availableQty);

                inventoryRepository.creditQTY(request.getQty(), inventory.getInventoryId());
                person.setBalance(person.getBalance().subtract(cost));
                personRepository.save(person);
                inventoryRepository.flush();
                personRepository.flush();

                finalizeOutcome(request, jrnEvent, Result.EXECUTED, true);
                break;

            case DEBIT: // Restock: inventory increases, the client is credited (balance increases).
                applyLatency(latence, request.getRequestId(), request.getProductId(), inventory.getQty());
                inventoryRepository.debitQTY(request.getQty(), inventory.getInventoryId());
                person.setBalance(person.getBalance().add(cost));
                personRepository.save(person);
                inventoryRepository.flush();
                personRepository.flush();

                finalizeOutcome(request, jrnEvent, Result.EXECUTED, true);
                break;

            default:
                logger.info("Unimplemented operation.");
                finalizeOutcome(request, jrnEvent, Result.ERROR, false);
                return;
        }
    }

    /**
     * Persists the terminal outcome of a request, mirroring it onto the tracking jrn-event
     * (when idempotency tracking applies) so the journal always reflects the same result as
     * the request itself. {@code processed} marks a genuinely executed outcome (sets
     * {@code processedAt}), as opposed to a terminal-but-not-executed one (ERROR/BACK_ORDER).
     */
    private void finalizeOutcome(Request request, JrnEvent jrnEvent, Result result, boolean processed) {
        request.setResult(result);
        requestRepository.save(request);
        requestRepository.flush();

        if (jrnEvent != null) {
            jrnEvent.setResult(result);
            if (processed)
                jrnEvent.setProcessedAt(Instant.now());
            jrnEventRepository.save(jrnEvent);
            jrnEventRepository.flush();
        }
    }

    // We apply a sanity check on the message to ensure that the header, request, and footer are valid. 
    // We also check if the request has already been processed or if it is a replay event. 
    // If any of these checks fail, we throw an exception to trigger a rollback and retry.
    //
    // WARNING: ******************************************************************************************************
    // This method is called inside a transaction, so any exception thrown will cause a rollback of the transaction.
    // sanity check logic is just a demonstration of how to validate the message and ensure idempotency. 
    // In a real-world application, you may want to implement more robust validation and error handling.
    // ***************************************************************************************************************
    private SanityResult sanityCheck(EventMessage<EventHeader,Request,EventFooter> message) throws Exception {

        Request request      = message.getPayload();
        EventHeader header   = message.getHeader();
        EventFooter footer   = message.getFooter();
        MessageDigest digest = MessageDigest.getInstance("SHA-1");


        // header is optional for the sandbox (in tests, it is null).
        if (header != null) {
            if (header.getEventId() == null ||
                header.getVersion() == null ||
                header.getBatchId() == null ||
                header.getReplayId() == null ||
                header.getVersion().compareTo("1.0") != 0) {
                logger.warn("Invalid header — exception.");
                throw new IllegalArgumentException("Invalid header.");
            }
        }

        if (request == null) {  // The request is persisted and mandatory.
                                // Should never happen in production,
                                // but if it does, log and throw an exception to trigger
                                // a rollback and a retry.
            logger.warn("Null request — exception.");
            throw new IllegalArgumentException("Request cannot be null.");
        }

        // footer is optional for the sandbox (in tests, it is null). The hash, once computed
        // here, is reused below as the jrn-event payload hash so the request is never
        // re-serialized/re-hashed for the same purpose.
        String payloadHash = null;
        if (footer != null) {
            payloadHash = HexFormat.of().formatHex(digest
                .digest(JsonMapper.writeToJson(request).getBytes(StandardCharsets.UTF_8)));

            // Check signature of payload against footer signature.
            if (footer.getSignature() == null || !footer.getSignature().equals(payloadHash)) {
                logger.warn("Payload/footer signature mismatch — exception.");
                throw new IllegalArgumentException("Payload/footer signature mismatch.");
            }
        }

        Optional<Request> optRequest = requestRepository.findById(request.getRequestId());
        if (!optRequest.isPresent()) {
            logger.warn("Request not found — exception.");
            throw new IllegalArgumentException("Request not found.");
        }

        Request requestDB = optRequest.get();
        if (requestDB.getResult() != Result.PENDING &&
            requestDB.getResult() != Result.BACK_ORDER) {
            logger.info("Request already processed — skipping.");
            return new SanityResult(false, null); // Already processed, skip it.
        }

        if (header == null) { // header/footer are optionals for the sandbox (in tests, they are null).
            logger.warn("Null header/footer — skipping the jrn-event check.");
            return new SanityResult(true, null); // Skip the jrn-event check, but continue processing the request.
        }

        // The jrn-event table is used to track the processing of events and to ensure idempotency.
        final String eventId   = header.getEventId();
        final String replayId  = header.getReplayId();

        // Check if the event is already processed.
        Optional<JrnEvent> optJrnEvent = jrnEventRepository.findByConsumerIdAndEventId(KafkaConstants.CST_TOPIC_GROUP, eventId);
        final JrnEvent jrnEvent;
        switch (replayId) {
            case KafkaConstants.CST_NONE:
                if (optJrnEvent.isPresent() &&
                    optJrnEvent.get().getResult() != Result.PENDING  &&
                    optJrnEvent.get().getResult() != Result.BACK_ORDER) {
                    logger.info("Event already processed — skipping.");
                    return new SanityResult(false, null); // Already processed, skip it.
                }

                if (optJrnEvent.isPresent()) {
                    jrnEvent = optJrnEvent.get();
                } else {
                    // New event, create a new jrn-event record.
                    jrnEvent = new JrnEvent();
                    jrnEvent.setEventId(eventId);
                    jrnEvent.setConsumerId(KafkaConstants.CST_TOPIC_GROUP);
                    jrnEvent.setBatchId(header.getBatchId());
                    jrnEvent.setPayloadHash(payloadHash != null ? payloadHash
                        : HexFormat.of().formatHex(digest
                            .digest(JsonMapper.writeToJson(request).getBytes(StandardCharsets.UTF_8))));
                    jrnEvent.setEventType(EventType.PROCESSING_REQUESTED);
                    jrnEvent.setResult(Result.PENDING);
                    jrnEvent.setReceivedAt(Instant.now());
                    jrnEventRepository.save(jrnEvent);
                    jrnEventRepository.flush();
                }
                break;

            default: // Handle replay case
                if (! optJrnEvent.isPresent()) {
                    logger.warn("Replay event not found — exception.");
                    throw new IllegalArgumentException("Replay event not found.");
                }

                jrnEvent = optJrnEvent.get();
                if (jrnEvent.getResult() != Result.PENDING &&
                    jrnEvent.getResult() != Result.BACK_ORDER) {
                    logger.info("Replay event already processed — skipping.");
                    return new SanityResult(false, null); // Already processed, skip it.
                }
                // receivedAt is left untouched: it must keep recording the original
                // (first) delivery time, not the time of this replay.
                jrnEvent.setEventType(EventType.PROCESSING_REPLAY);
                jrnEvent.setResult(Result.PENDING);
                jrnEventRepository.save(jrnEvent);
                jrnEventRepository.flush();
                break;
        }

        return new SanityResult(true, jrnEvent); // Continue processing the request.
    }

    /**
     * Optional artificial delay applied INSIDE the transaction to widen the read-then-write
     * critical section. This makes the check-then-act lost update observable (the teaching
     * goal): without a Kafka key, several threads process the same product in parallel, both
     * pass the stale stock check, and both apply the relative decrement — so the inventory
     * quantity silently goes negative. The race is a lost update, not a lock conflict.
     */
    private void applyLatency(boolean latence, Long requestId, Long productId, Long availableAtCheck) {
        if (!latence)
            return;
        try {
            // requestId/productId/availableAtCheck pin down exactly which transaction read what
            // stale stock value, on which thread, at what time — the only way to tell from the
            // logs whether two requests for the same product genuinely overlapped (both reading
            // before either commits) instead of merely running on different threads.
            logger.info("Adding latency on thread {} for requestId={}, productId={} (stale available={}).",
                Thread.currentThread().getName(), requestId, productId, availableAtCheck);
            Thread.sleep(250L);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }
}
