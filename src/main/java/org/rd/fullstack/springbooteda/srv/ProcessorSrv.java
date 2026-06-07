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
import java.util.Optional;

import org.rd.fullstack.springbooteda.dao.InventoryRepository;
import org.rd.fullstack.springbooteda.dao.PersonRepository;
import org.rd.fullstack.springbooteda.dao.ProductRepository;
import org.rd.fullstack.springbooteda.dao.RequestRepository;
import org.rd.fullstack.springbooteda.dto.Inventory;
import org.rd.fullstack.springbooteda.dto.Person;
import org.rd.fullstack.springbooteda.dto.Product;
import org.rd.fullstack.springbooteda.dto.Request;
import org.rd.fullstack.springbooteda.util.JsonMapper;
import org.rd.fullstack.springbooteda.util.Operation;
import org.rd.fullstack.springbooteda.util.Result;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
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

    private static final Logger logger =
        LoggerFactory.getLogger(ProcessorSrv.class);

    @Autowired
    private InventoryRepository inventoryRepository;

    @Autowired
    private RequestRepository requestRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private PersonRepository personRepository;

    public ProcessorSrv() {
        super();
    }

    @Transactional(propagation = Propagation.REQUIRED, isolation = Isolation.READ_COMMITTED)
    public void process(String params, boolean latence) throws Exception {

        // Get the request.
        Request request = JsonMapper.readFromJson(params, Request.class);

        // Check state -- Must be PENDING or BACK_ORDER.
        // Any other state means the request was already processed: skip (idempotency).
        if ((request.getResult() != Result.PENDING) &&
            (request.getResult() != Result.BACK_ORDER))
            return;

        logger.info(request.toString());

        // Get inventory for the product.
        Optional<Inventory> inventoryOptional = inventoryRepository.findByProductId(request.getProductId());
        if (inventoryOptional.isEmpty()) { // Big problem here.
            request.setResult(Result.ERROR);
            requestRepository.save(request);
            requestRepository.flush();
            return;
        }

        Inventory inventory = inventoryOptional.get();

        // Product (unit price) and client (balance) are required to price the request and
        // to debit/credit the customer account. The client row is already serialized by the
        // Hazelcast per-client lock held in PipelineSrv.listen(), so this whole read-check-
        // write runs atomically per client within the single JPA transaction.
        Optional<Product> productOptional = productRepository.findById(request.getProductId());
        Optional<Person>  personOptional  = personRepository.findById(request.getPersonId());
        if (productOptional.isEmpty() || personOptional.isEmpty()) { // Big problem here.
            request.setResult(Result.ERROR);
            requestRepository.save(request);
            requestRepository.flush();
            return;
        }

        Product product = productOptional.get();
        Person  person  = personOptional.get();

        // Cost of the request = unit price * quantity.
        BigDecimal cost = product.getPrice().multiply(BigDecimal.valueOf(request.getQty()));
        Operation oper = request.getOperation();

        switch (oper) {
            case CREDIT: // Sale: inventory decreases, the client pays (balance decreases).
                // Two distinct back-order causes — logged separately so each can be counted
                // (e.g. grep "BACK_ORDER (insufficient STOCK)" vs "... BALANCE)").
                if (inventory.getQty() < request.getQty()) {          // not enough stock
                    logger.warn("BACK_ORDER (insufficient STOCK) — requestId={}, productId={}, requested={}, available={}.",
                        request.getRequestId(), request.getProductId(), request.getQty(), inventory.getQty());
                    request.setResult(Result.BACK_ORDER);
                    requestRepository.save(request);
                    requestRepository.flush();
                    return;
                }
                if (person.getBalance().compareTo(cost) < 0) {        // not enough balance
                    logger.warn("BACK_ORDER (insufficient BALANCE) — requestId={}, personId={}, cost={}, balance={}.",
                        request.getRequestId(), request.getPersonId(), cost, person.getBalance());
                    request.setResult(Result.BACK_ORDER);
                    requestRepository.save(request);
                    requestRepository.flush();
                    return;
                }

                // Widen the critical section between the read/check above and this write,
                // so a concurrent write on the SAME inventory row collides under MVLOCKS
                // (lock_timeout=0 -> the loser fails immediately -> retry -> DLT).
                applyLatency(latence);

                inventoryRepository.creditQTY(request.getQty(), inventory.getInventoryId());
                person.setBalance(person.getBalance().subtract(cost));
                personRepository.save(person);
                inventoryRepository.flush();
                personRepository.flush();

                request.setResult(Result.EXECUTED);
                requestRepository.save(request);
                requestRepository.flush();
                break;

            case DEBIT: // Restock: inventory increases, the client is credited (balance increases).
                applyLatency(latence);
                inventoryRepository.debitQTY(request.getQty(), inventory.getInventoryId());
                person.setBalance(person.getBalance().add(cost));
                personRepository.save(person);
                inventoryRepository.flush();
                personRepository.flush();

                request.setResult(Result.EXECUTED);
                requestRepository.save(request);
                requestRepository.flush();
                break;

            default:
                logger.info("Unimplemented operation.");
                request.setResult(Result.ERROR);
                requestRepository.save(request);
                requestRepository.flush();
        }
    }

    /**
     * Optional artificial delay applied INSIDE the transaction to widen the read-then-write
     * critical section. This makes concurrent same-row write conflicts observable (the
     * teaching goal): without a Kafka key, several threads process the same product in
     * parallel and collide on the inventory row.
     */
    private void applyLatency(boolean latence) {
        if (!latence)
            return;
        try {
            logger.info("Widening the critical section by 1/5 second (latency).");
            Thread.sleep(200);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }
}
