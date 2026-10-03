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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Supplier;

import org.apache.kafka.clients.producer.ProducerRecord;
import org.rd.fullstack.springbooteda.dao.JrnEventRepository;
import org.rd.fullstack.springbooteda.dao.RequestRepository;
import org.rd.fullstack.springbooteda.dto.EventFooter;
import org.rd.fullstack.springbooteda.dto.EventHeader;
import org.rd.fullstack.springbooteda.dto.EventMessage;
import org.rd.fullstack.springbooteda.dto.PipelineContext;
import org.rd.fullstack.springbooteda.dto.StatsContext;
import org.rd.fullstack.springbooteda.dto.Request;
import org.rd.fullstack.springbooteda.util.JsonMapper;
import org.rd.fullstack.springbooteda.util.PipelineState;
import org.rd.fullstack.springbooteda.util.Result;
import org.rd.fullstack.springbooteda.util.hazelcast.HazelcastConstants;
import org.rd.fullstack.springbooteda.util.hazelcast.HazelcastSandbox;
import org.rd.fullstack.springbooteda.util.kafka.KafkaConstants;
import org.rd.fullstack.springbooteda.util.kafka.KafkaSandbox;
import org.rd.fullstack.springbooteda.util.token.EventToken;
import org.rd.fullstack.springbooteda.util.token.EventTokenPayload;
import org.rd.fullstack.springbooteda.util.token.TokenToolskit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.hazelcast.map.IMap;

/**
 * Path-agnostic <strong>core</strong> of the pipeline: run lifecycle ({@link #start}), publication
 * ({@link #publish}), the shared per-record handler ({@link #handle}), completion accounting
 * ({@link #recordProcessed}), the distributed parameters/stats, and pause/resume orchestration
 * ({@link #setPause}).
 *
 * <p>The two transport-specific concerns live elsewhere and both delegate the per-record work back
 * to {@link #handle}: the direct Kafka path in {@link KafkaPipelineListener} ({@code @KafkaListener}),
 * and the Flink path in {@link FlinkSrv} ({@code processFromFlink}). This bean depends on neither
 * of them — pause/resume is signalled through events to keep the dependencies one-directional.</p>
 */
@Service
public class PipelineSrv {
    private final JrnEventRepository jrnEventRepository;

    private static final Logger logger =
        LoggerFactory.getLogger(PipelineSrv.class);

    private final KafkaSandbox kafkaSandbox;
    private final HazelcastSandbox hazelcastSandbox;
    private final RequestRepository requestRepository;
    private final ProcessorSrv processor;
    private final TokenToolskit tokenToolskit;

    // Used to signal the Kafka listener and the Flink job to pause/resume without depending on those
    // beans directly (they already depend on this one; an event keeps the dependency one-directional).
    private final ApplicationEventPublisher eventPublisher;

    private final byte CST_ENV_ID     = 1;
    private final short CST_SYSTEM_ID = 2;
    private final short CST_SRV_ID    = 3;

    public PipelineSrv(KafkaSandbox kafkaSandbox,   RequestRepository requestRepository,
                       ProcessorSrv processor,      HazelcastSandbox hazelcastSandbox,
                       TokenToolskit tokenToolskit, ApplicationEventPublisher eventPublisher, 
                       JrnEventRepository jrnEventRepository) {
        super();
        this.kafkaSandbox      = kafkaSandbox;
        this.requestRepository = requestRepository;
        this.processor         = processor;
        this.hazelcastSandbox  = hazelcastSandbox;
        this.tokenToolskit     = tokenToolskit;
        this.eventPublisher    = eventPublisher;
        this.jrnEventRepository = jrnEventRepository;
    }

    public PipelineContext getPipelineContext() {
        PipelineContext pipelineContext = getOrInit(contextMap(),
            HazelcastConstants.CST_KEY_PIPELINE_CONTEXT, PipelineContext::new);
        logger.info("Retrieving pipeline context: {}.", pipelineContext);
        return pipelineContext;
    }

    @SuppressWarnings("null")
    public void setPipelineContext(PipelineContext pipelineContext) {
        contextMap().set(HazelcastConstants.CST_KEY_PIPELINE_CONTEXT, pipelineContext);
        logger.info("Pipeline context updated: {}.", pipelineContext);
    }

    /**
     * Updates the pause parameter in the distributed context AND applies it immediately. For the
     * DIRECT path this pauses/resumes the Kafka processor listener (handled by
     * {@link KafkaPipelineListener}). For the FLINK path (when the "flink" option is on) it signals
     * {@link FlinkSrv} to stop-with-savepoint / resume the Flink job — the closest equivalent to
     * the listener's pause. Both are routed through events so this core does not depend on those
     * beans. Works at any time, independently of the start/reset lifecycle.
     */
    public PipelineContext setPause(boolean pause) {
        // Read-modify-write under the distributed key lock: setPause() racing with itself (a
        // rapid pause/resume) or with start()'s own locked write below could otherwise have the
        // last writer silently discard the other's change (e.g. a resume lost right after a pause).
        PipelineContext pipelineContext = mutate(contextMap(),
            HazelcastConstants.CST_KEY_PIPELINE_CONTEXT, PipelineContext::new,
            ctx -> ctx.setPause(pause));

        // Direct path: pause/resume the Kafka listener (harmless no-op when in Flink mode, as that
        // listener then carries no records).
        eventPublisher.publishEvent(new KafkaListenerPauseEvent(pause));

        // Flink path: pause/resume the Flink job via savepoint, only when the option is enabled —
        // otherwise the (idle) Flink job is left running and we avoid a needless savepoint round-trip.
        if (Boolean.TRUE.equals(pipelineContext.getFlink()))
            eventPublisher.publishEvent(new FlinkPauseEvent(pause));

        return pipelineContext;
    }

    public StatsContext getStatsContext() {
        StatsContext statsContext = getOrInit(statsMap(),
            HazelcastConstants.CST_KEY_PIPELINE_STATS, StatsContext::new);
        logger.info("Retrieving pipeline stats: {}.", statsContext);
        return statsContext;
    }

    public StatsContext resetStatsContext() {
        StatsContext fresh = new StatsContext();
        statsMap().set(HazelcastConstants.CST_KEY_PIPELINE_STATS, fresh);
        return fresh;
    }

    @Async
    public CompletableFuture<PipelineContext> start(PipelineContext pipelineContext) throws InterruptedException  {

        try {
            // Readiness is held by the STATS context now. The check-and-reset is performed
            // under the distributed key lock so two members cannot both start it. A fresh
            // StatsContext zeroes the counters and flags the run as EXECUTING.
            IMap<String, StatsContext> sMap = statsMap();
            sMap.lock(HazelcastConstants.CST_KEY_PIPELINE_STATS);
            try {
                StatsContext stats = getOrInit(sMap,
                    HazelcastConstants.CST_KEY_PIPELINE_STATS, StatsContext::new);
                if (stats.getPipelineState() != PipelineState.READY) {
                    throw new IllegalStateException("Pipeline is not ready for execution.");
                }

                StatsContext fresh = new StatsContext();
                fresh.setPipelineState(PipelineState.EXECUTING);
                sMap.set(HazelcastConstants.CST_KEY_PIPELINE_STATS, fresh);
            } finally {
                sMap.unlock(HazelcastConstants.CST_KEY_PIPELINE_STATS);
            }

            // Store the run parameters under the same distributed key lock setPause() uses,
            // so a concurrent pause/resume cannot be silently overwritten by (or overwrite)
            // this run's fresh parameters.
            IMap<String, PipelineContext> cMap = contextMap();
            cMap.lock(HazelcastConstants.CST_KEY_PIPELINE_CONTEXT);
            try {
                setPipelineContext(pipelineContext);
            } finally {
                cMap.unlock(HazelcastConstants.CST_KEY_PIPELINE_CONTEXT);
            }
            publish();
            return CompletableFuture.completedFuture(pipelineContext);
        } catch (Exception ex) {
            logger.error("Error processing request.", ex);
            mutateStats(stats -> {
                stats.setPipelineState(PipelineState.EXCEPTION);
                stats.setExceptionMSG(ex.getMessage());
            });
            return CompletableFuture.failedFuture(ex);
        }
    }

    public void publish() throws Exception {
        // Read the parameters once: effectively final for use inside the transactional lambda.
        //  - "flink" enabled: publish to the Flink SOURCE topic. The Flink job consumes that
        //    topic and its sink invokes the processor DIRECTLY (see FlinkService.processFromFlink());
        //    no second Kafka topic and no extra listener are involved. Disabled: publish straight to
        //    the processor topic (KafkaPipelineListener consumes it).
        //  - "key" enabled: publish WITH the product id as key (key-hash partitioning),
        //    otherwise WITHOUT a key (round-robin/sticky spread).
        //  - "replay" enabled: tag every record of THIS publication with a single shared
        //    "replay-id" header (one UUID for the whole batch).
        final PipelineContext context  = getPipelineContext();
        final boolean         flink    = Boolean.TRUE.equals(context.getFlink());
        final String          target   = flink ? KafkaConstants.CST_TOPIC_FLINK_REQ
                                               : KafkaConstants.CST_TOPIC_KAFKA_REQ;

        MessageDigest digest = MessageDigest.getInstance("SHA-1");
        EventHeader header   = new EventHeader(null, "1.0", null, null);
        EventFooter footer   = new EventFooter(null);
        EventMessage<EventHeader, Request, EventFooter> message = new EventMessage<>();

        message.setHeader(header);
        message.setFooter(footer);

        final String batchId  = UUID.randomUUID().toString();
        final String replayId = Boolean.TRUE.equals(context.getReplay())
            ? UUID.randomUUID().toString()
            : KafkaConstants.CST_NONE;

        KafkaTemplate<String, String> template =
            kafkaSandbox.getKafkaTemplate(target, true);

        // Serialize OUTSIDE the Kafka transaction: a serialization error must never
        // abort the producer transaction (and JSON mapping needs no broker round-trip).
        List<Map.Entry<String, String>> outbound = new ArrayList<>();
        EventToken token = new EventToken();
        for (Request request : requestRepository.findAll()) {
            if ((request.getResult() != Result.PENDING) &&
                (request.getResult() != Result.BACK_ORDER))
                continue;

            final String key = String.valueOf(request.getProductId());
            token.setPayload(new EventTokenPayload(CST_ENV_ID, CST_SYSTEM_ID, CST_SRV_ID, request.getRequestId()));
            final String eventId = tokenToolskit.encode(token, TokenToolskit.Format.TOKEN_AND_MAC);

            header.setEventId(eventId);
            header.setBatchId(batchId);
            header.setReplayId(replayId);
            message.setPayload(request);

            String strDigest = HexFormat.of().
                formatHex(digest.digest(JsonMapper.writeToJson(request).getBytes(StandardCharsets.UTF_8)));
            footer.setSignature(strDigest);
    
            outbound.add(Map.entry(key, JsonMapper.writeToJson(message)));
        }

        if (outbound.isEmpty()) {
            logger.info("No eligible request to publish.");
            mutateStats(stats -> stats.setNbrPublished(0));
            return;
        }

        // If this is not a replay, purge the journal of any prior records. 
        // This is done BEFORE the Kafka transaction.
        if (Boolean.FALSE.equals(context.getReplay())) {
            jrnEventRepository.deleteAll();
            jrnEventRepository.flush();
            logger.info("Purged the journal of prior records because this is not a replay.");
            // If this is a replay, the journal is left intact so the consumer job can reprocess it.
            // Remenber, this Sandbox is a single-node, single-VM, single-database simulation of a 
            // distributed system. In a real distributed system, the replay should be handle differently.
        }

        // The remaining parameters, read once for use inside the transactional lambda.
        final boolean useKey = Boolean.TRUE.equals(context.getKey());
        if (useKey) {
            logger.info("The pipeline will use a key for each message.");
        } else {
            logger.info("The pipeline will NOT use a key for each message.");
        }

        // Trace the messages BEFORE publishing, under ".tmp": if the process dies mid-publish,
        // a leftover ".tmp" file signals an unconfirmed attempt rather than a false record of
        // success. A write failure here is logged and swallowed — it must not block the Kafka
        // publication below, which is the primary outcome.
        Path tmpFile = Path.of("log", "batch-" +batchId + ".tmp");
        Path trxFile = Path.of("log", "batch-" +batchId + ".trx");
        writeTrxTmpFile(tmpFile, outbound);

        // Publish every message atomically in a single Kafka transaction. If the
        // transaction aborts, executeInTransaction throws and start() flags the
        // pipeline as EXCEPTION; nbrPublished is left untouched.
        //
        // Partition assignment — round-robin instead of leaving it to Kafka: when no key is
        // used, Kafka's default (Uniform) Sticky Partitioner sticks ALL records of ONE producer
        // batch onto a SINGLE partition (to optimize batching), rather than spreading them per
        // record. Because this whole loop runs inside ONE Kafka transaction — one batch window —
        // every "no key" run would otherwise land on a single partition, consumed by a single
        // listener thread. That silently serializes "concurrent" processing and hides the very
        // concurrency anomalies (e.g. the inventory race) this sandbox is meant to let us expose.
        // We therefore compute the partition explicitly, round-robin over the topic's partition
        // count, so keyless messages are genuinely spread across partitions/threads. When a key
        // IS used, partition is left to Kafka's key-hash so same-key (same-product) messages
        // keep landing on the same partition, preserving per-product ordering.
        final int partitionCount = kafkaSandbox.getTopicConfig(target).partitions();
        template.executeInTransaction(ops -> {
            int partitionIndex = 0;
            for (Map.Entry<String, String> outboundMessage : outbound) {
                final String  key       = useKey ? outboundMessage.getKey() : null;
                final Integer partition = useKey ? null : (partitionIndex++ % partitionCount);
                ops.send(new ProducerRecord<>(target, partition, key, outboundMessage.getValue()));
            }
            return null; // executeInTransaction requires a return value.
        });

        // The denominator of the completion check: set only after a successful commit.
        mutateStats(stats -> stats.setNbrPublished(outbound.size()));
        logger.info("Total messages published: {}.", outbound.size());

        // Only after the commit above does the trace become a ".trx": a ".trx" file always
        // means "actually published", never a pending/aborted attempt.
        renameTrxFile(tmpFile, trxFile);
    }

    /**
     * Writes one line per message about to be published to {@code tmpFile} ("<batchId>.tmp"),
     * BEFORE the Kafka transaction is attempted. A failure here is logged and swallowed: it
     * must not block the Kafka publication, which is the primary outcome.
     */
    private void writeTrxTmpFile(Path tmpFile, List<Map.Entry<String, String>> outbound) {
        try {
            Files.createDirectories(tmpFile.getParent());
            Files.write(tmpFile, outbound.stream().map(Map.Entry::getValue).toList(),
                StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException ex) {
            logger.warn("Failed to write transaction trace file '{}'.", tmpFile, ex);
        }
    }

    /**
     * Renames the ".tmp" trace file to ".trx" once the Kafka transaction has committed
     * successfully, so a ".trx" file is only ever produced for messages that were actually
     * published. A failure here is logged and swallowed rather than propagated: the Kafka
     * publication already succeeded and must not be reported as a pipeline exception.
     */
    private void renameTrxFile(Path tmpFile, Path trxFile) {
        try {
            Files.move(tmpFile, trxFile, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ex) {
            logger.warn("Failed to rename transaction trace file '{}' to '{}'.", tmpFile, trxFile, ex);
        }
    }

    /**
     * Core per-record handling shared by the Kafka listener ({@link KafkaPipelineListener}) and the
     * Flink sink ({@link FlinkSrv}).
     *
     * <p>The optional artificial latency is applied INSIDE the processor transaction (between the
     * inventory read/check and the write) to widen the critical section and make concurrent
     * same-row conflicts observable; the flag is read once here.</p>
     *
     * <p>Why a Hazelcast distributed lock, and not just a Kafka key? A Kafka record carries a
     * SINGLE partition key, so partitioning can serialize processing along ONE dimension only. We
     * already (optionally) key by productId to serialize per-product for INVENTORY consistency, but
     * the balance update requires serialization per CLIENT as well, and a record cannot have a
     * "double key" (productId AND personId). The Hazelcast IMap lock on personId fills that gap:
     * cluster-wide, per-client mutual exclusion INDEPENDENT of the Kafka partition key (and of the
     * Flink parallelism). It is acquired BEFORE the processor's transaction and released AFTER it
     * returns (commit), so the balance read-check-write is atomic per client and concurrent
     * requests for the same client cannot overdraw.</p>
     */
    public void handle(String value) throws Exception {

        final boolean latence = Boolean.TRUE.equals(getPipelineContext().getLatence());
        EventMessage<EventHeader, Request, EventFooter> message = 
            JsonMapper.readFromJson(value, 
                new TypeReference<EventMessage<EventHeader, Request, EventFooter>>() {});

        // Lock the clientId in a distributed map so concurrent requests
        // for the same client cannot overdraw. Bounded by a timeout: a holder stuck inside
        // processor.process() (e.g. a hung downstream call) must not block this personId's
        // lock — and one of the few consumer threads — forever.
        final Request request = message.getPayload();
        final long personId = request.getPersonId();
        final IMap<Long, Object> clientLocks =
            hazelcastSandbox.getMap(HazelcastConstants.CST_MAPNAME_CLIENT_LOCKS);

        boolean locked = false;
        try {
            locked = clientLocks.tryLock(personId, HazelcastConstants.CST_CLIENT_LOCK_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException ex) {
            throw new IllegalStateException(
                "Interrupted while acquiring the client lock for personId=" + personId + ".", ex);
        }

        if (!locked)
            throw new IllegalStateException(
                "Timed out waiting for the client lock for personId=" + personId + ".");

        try {
            processor.process(message, latence);
        } finally {
            clientLocks.unlock(personId);
        }
        // Reached only when processing succeeded.
        recordProcessed(false);
    }

    /**
     * Records one processed message (successful or routed to the DLT) and evaluates the
     * terminal state of the pipeline. The increment and the completion check form a single
     * compound read-modify-write executed atomically under the distributed key lock, so
     * concurrent consumer threads (container concurrency &gt; 1), the DLT recovery callback
     * and other cluster members cannot lose the completion edge.
     */
    public void recordProcessed(boolean withError) {
        mutateStats(stats -> {
            if (withError)
                stats.incrementNbrProcessedWithError();
            else
                stats.incrementNbrProcessed();

            if (stats.getNbrPublished() == (stats.getNbrProcessed() + stats.getNbrProcessedWithError())) {
                stats.setPipelineState(stats.getNbrProcessedWithError() == 0
                    ? PipelineState.COMPLETED
                    : PipelineState.EXCEPTION);
            }
        });
    }

    /** The distributed map backing the pipeline parameters. */
    private IMap<String, PipelineContext> contextMap() {
        return hazelcastSandbox.getMap(HazelcastConstants.CST_MAPNAME_CTX);
    }

    /** The distributed map backing the pipeline stats & state. */
    private IMap<String, StatsContext> statsMap() {
        return hazelcastSandbox.getMap(HazelcastConstants.CST_MAPNAME_STATS);
    }

    /**
     * Returns the value stored under {@code key}, lazily creating it from {@code factory}
     * on first access. Used as the read side and as the seed for {@link #mutate}.
     */
    @SuppressWarnings("null")
    private <T> T getOrInit(IMap<String, T> map, String key, Supplier<T> factory) {
        T value = map.get(key);
        if (value == null) {
            T fresh = factory.get();
            value = map.putIfAbsent(key, fresh);
            if (value == null)
                value = fresh;
        }
        return value;
    }

    /**
     * Atomically applies a mutation to a distributed entry. Because an IMap hands out
     * deserialized copies, the read-modify-write must run under the cluster-wide key lock
     * and the mutated copy must be written back.
     */
    @SuppressWarnings("null")
    private <T> T mutate(IMap<String, T> map, String key, Supplier<T> factory, Consumer<T> mutation) {
        map.lock(key);
        try {
            T value = getOrInit(map, key, factory);
            mutation.accept(value);
            map.set(key, value);
            return value;
        } finally {
            map.unlock(key);
        }
    }

    /** Atomic read-modify-write on the distributed {@link StatsContext}. */
    private void mutateStats(Consumer<StatsContext> mutation) {
        mutate(statsMap(), HazelcastConstants.CST_KEY_PIPELINE_STATS, StatsContext::new, mutation);
    }
}