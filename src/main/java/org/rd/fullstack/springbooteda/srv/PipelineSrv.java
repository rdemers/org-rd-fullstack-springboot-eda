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

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Supplier;

import javax.annotation.Nonnull;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.rd.fullstack.springbooteda.dao.RequestRepository;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.springframework.kafka.listener.RetryListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import com.hazelcast.map.IMap;

import jakarta.annotation.PostConstruct;

@Service
public class PipelineSrv {
    private static final Logger logger =
        LoggerFactory.getLogger(PipelineSrv.class);

    @Autowired
    private KafkaSandbox kafkaSandbox;

    @Autowired
    private RequestRepository requestRepository;

    @Autowired
    private ProcessorSrv processor;

    // Same instance used by the listener container factory (see KafkaConfig).
    // We register a RetryListener on it to observe records that exhaust their
    // retries and are routed to the DLT.
    @Autowired
    private DefaultErrorHandler errorHandler;

    @Autowired
    private HazelcastSandbox hazelcastSandbox;

    // Registry of @KafkaListener containers — used to pause/resume the processor listener.
    @Autowired
    private KafkaListenerEndpointRegistry listenerRegistry;

    // Shutdown sentinel: isRunning() flips to false as soon as the context starts closing.
    @Autowired
    private SmartLifecycleSrv smartLifecycleSrv;

    public PipelineSrv() {
        super();
    }

    /**
     * Registers a {@link RetryListener} so that a message which exhausts all retries
     * and is published to the Dead Letter Topic is counted exactly once as a
     * processing error. This is the correct hook for a manually configured
     * {@link DefaultErrorHandler} (the {@code @DltHandler} annotation only applies to
     * the {@code @RetryableTopic} mechanism, which is not used here).
     */
    @PostConstruct
    void registerDltRetryListener() {
        errorHandler.setRetryListeners(new RetryListener() {
            @Override
            public void failedDelivery(ConsumerRecord<?, ?> record, Exception ex, int deliveryAttempt) {
                logger.warn("Delivery attempt {} failed for topic={}, partition={}, offset={}.",
                    deliveryAttempt, record.topic(), record.partition(), record.offset(), ex);
            }

            @Override
            public void recovered(ConsumerRecord<?, ?> record, Exception ex) {
                // During shutdown, do not count this as a processing error: the record was
                // not genuinely processed, and it will be redelivered after restart.
                if (!smartLifecycleSrv.isRunning()) {
                    logger.warn("Application is shutting down — DLT recovery NOT counted (topic={}, partition={}, offset={}).",
                        record.topic(), record.partition(), record.offset());
                    return;
                }

                recordProcessed(true);
                logger.warn("=== MESSAGE ROUTED TO DLT (retries exhausted) ===");
                logger.warn("Topic       : {}", record.topic());
                logger.warn("Partition   : {}", record.partition());
                logger.warn("Offset      : {}", record.offset());
                logger.warn("Value       : {}", record.value());
                logger.warn("Cause       : ", ex);
                logger.warn("=================================================");
            }
        });
    }

    public PipelineContext getPipelineContext() {
        PipelineContext pipelineContext = getOrInit(contextMap(),
            HazelcastConstants.CST_KEY_PIPELINE_CONTEXT, PipelineContext::new);
        logger.info("Retrieving pipeline context: {}.", pipelineContext);
        return pipelineContext;
    }

    public void setPipelineContext(@Nonnull PipelineContext pipelineContext) {
        contextMap().set(HazelcastConstants.CST_KEY_PIPELINE_CONTEXT, pipelineContext);
        logger.info("Pipeline context updated: {}.", pipelineContext);
    }

    /**
     * Updates the pause parameter in the distributed context AND applies it immediately to
     * the Kafka processor listener (pause stops consuming, resume restarts it). Works at any
     * time, independently of the start/reset lifecycle.
     */
    public PipelineContext setPause(boolean pause) {
        PipelineContext pipelineContext = getPipelineContext();
        pipelineContext.setPause(pause);
        setPipelineContext(pipelineContext);
        applyPause(pause);
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
    public CompletableFuture<PipelineContext> start(@Nonnull PipelineContext pipelineContext) throws InterruptedException  {
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

            // Store the run parameters, then send the messages to start the processing.
            setPipelineContext(pipelineContext);
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
        KafkaTemplate<String, String> template =
            kafkaSandbox.getKafkaTemplate(KafkaConstants.CST_TOPIC_PROCESSOR, true);

        // Serialize OUTSIDE the Kafka transaction: a serialization error must never
        // abort the producer transaction (and JSON mapping needs no broker round-trip).
        List<Map.Entry<String, String>> outbound = new ArrayList<>();
        for (Request request : requestRepository.findAll()) {
            if ((request.getResult() != Result.PENDING) &&
                (request.getResult() != Result.BACK_ORDER))
                continue;

            final String key      = String.valueOf(request.getProductId());
            final String jSonReq  = JsonMapper.writeToJson(request);
            outbound.add(Map.entry(key, jSonReq));
        }

        if (outbound.isEmpty()) {
            logger.info("No eligible request to publish.");
            mutateStats(stats -> stats.setNbrPublished(0));
            return;
        }

        // Read the parameters once: effectively final for use inside the transactional lambda.
        //  - "key" enabled: publish WITH the product id as key (key-hash partitioning),
        //    otherwise WITHOUT a key (round-robin/sticky spread).
        //  - "replay" enabled: tag every record of THIS publication with a single shared
        //    "replay-id" header (one UUID for the whole batch).
        final PipelineContext context  = getPipelineContext();
        final boolean         useKey   = Boolean.TRUE.equals(context.getKey());
        final boolean         replay   = Boolean.TRUE.equals(context.getReplay());
        final String          batchId  = UUID.randomUUID().toString();
        final String          replayId = replay ? UUID.randomUUID().toString() : KafkaConstants.CST_NONE;

        if (replay)
            logger.info("Replay enabled — tagging this publication with replay-id={}.", replayId);

        // Publish every message atomically in a single Kafka transaction. If the
        // transaction aborts, executeInTransaction throws and start() flags the
        // pipeline as EXCEPTION; nbrPublished is left untouched.
        template.executeInTransaction(ops -> {
            for (Map.Entry<String, String> message : outbound) {
                final String key = useKey ? message.getKey() : null;
                final List<org.apache.kafka.common.header.Header> headers = new ArrayList<>();
                
                headers.add(new RecordHeader(KafkaConstants.CST_HEADER_BATCH_ID, 
                    batchId.getBytes(StandardCharsets.UTF_8)));
                if (replay)
                    headers.add(new RecordHeader(KafkaConstants.CST_HEADER_REPLAY_ID, 
                        replayId.getBytes(StandardCharsets.UTF_8)));

                logger.info("Publishing for key/payload: {}/{} - batch-id: {}, replay-id: {}.",
                    useKey ? message.getKey() : KafkaConstants.CST_NONE, message.getValue(),
                    batchId, replay ? replayId : KafkaConstants.CST_NONE);

                ops.send(new ProducerRecord<>(
                    KafkaConstants.CST_TOPIC_PROCESSOR, null, key, message.getValue(), headers));
            }
            return null; // executeInTransaction requires a return value.
        });

        // The denominator of the completion check: set only after a successful commit.
        mutateStats(stats -> stats.setNbrPublished(outbound.size()));
        logger.info("Total messages published: {}.", outbound.size());
    }

    @KafkaListener(id = KafkaConstants.CST_LISTENER_PROCESSOR,
                  topics = KafkaConstants.CST_TOPIC_PROCESSOR,
                  groupId = KafkaConstants.CST_TOPIC_GROUP,
                  containerFactory = "kafkaListenerContainerFactory")
    public void listen(ConsumerRecord<String, String> record, Acknowledgment ack,
                @Header(KafkaHeaders.RECEIVED_TOPIC)     String topic,
                @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
                @Header(KafkaHeaders.OFFSET)             long offset) throws Exception {

        // If the application is shutting down, stop processing immediately. The record is
        // neither processed nor acknowledged, so its offset is not committed and it will be
        // redelivered after restart (at-least-once).
        if (!smartLifecycleSrv.isRunning()) {
            logger.warn("Application is shutting down — skipping record (topic={}, partition={}, offset={}); it will be redelivered after restart.",
                topic, partition, offset);
            return;
        }

        // Log the record metadata and value for visibility. 
        // This is especially useful to observe the records that are retried and 
        // ultimately routed to the DLT by the error handler.
        //
        // Another good place to log the replay header is inside the processor, 
        // after deserialization, to correlate it with the business data (e.g. productId) 
        // and observe the actual replay behavior.
        //
        // Check lags and offsets in the logs to verify the pause/resume behavior and 
        // the at-least-once processing guarantees (no offset is committed before processing, 
        // so a crash before the ack results in redelivery of the same record after restart).
        checkpoint(record, topic, partition, offset);

        // Log the replay correlation id when the record carries one (replay mode).
        var replayHeader = record.headers().lastHeader(KafkaConstants.CST_HEADER_REPLAY_ID);
        if (replayHeader != null) {
            logger.info("Replay header present — replay-id: {}.",
                new String(replayHeader.value(), StandardCharsets.UTF_8));
        }

        // The optional artificial latency is applied INSIDE the processor transaction
        // (between the inventory read/check and the write) to widen the critical section
        // and make concurrent same-row conflicts observable. Read the flag once.
        final boolean latence = Boolean.TRUE.equals(getPipelineContext().getLatence());

        // Why a Hazelcast distributed lock here, and not just a Kafka key?
        // -----------------------------------------------------------------------------
        // A Kafka record carries a SINGLE partition key, so partitioning can serialize
        // processing along ONE dimension only. We already (optionally) key by productId to
        // serialize per-product for INVENTORY consistency. But the balance update requires
        // serialization per CLIENT as well, and a record cannot have a "double key"
        // (productId AND personId) in Kafka — keying by one necessarily scatters the other
        // across partitions/threads. There is therefore no way to obtain per-client ordering
        // from Kafka partitioning in this case.
        //
        // The Hazelcast IMap lock on personId fills that gap: it provides cluster-wide,
        // per-client mutual exclusion that is INDEPENDENT of the Kafka partition key (and
        // works across all members). It is acquired BEFORE the processor's transaction and
        // released AFTER it returns (i.e. after commit), so the balance read-check-write is
        // atomic per client and concurrent requests for the same client cannot overdraw.
        final long personId = JsonMapper.readFromJson(record.value(), Request.class).getPersonId();
        final IMap<Long, Object> clientLocks = hazelcastSandbox.getMap(HazelcastConstants.CST_MAPNAME_CLIENT_LOCKS);
        clientLocks.lock(personId);
        try {
            // Delegate to the transactional processor bean. Any exception propagates to
            // the container's DefaultErrorHandler, which retries with a bounded back-off
            // and ultimately routes the record to the DLT. The JPA transaction in the
            // processor rolls back on failure, so no partial DB state is committed.
            processor.process(record.value(), latence);
        } finally {
            clientLocks.unlock(personId);
        }

        // Reached only when processing succeeded.
        recordProcessed(false);

        // Commit the offset only after a successful, committed processing.
        // (at-least-once: a crash before this point replays the message.)
        ack.acknowledge();
    }

    private void applyPause(boolean pause) {
        MessageListenerContainer container =
            listenerRegistry.getListenerContainer(KafkaConstants.CST_LISTENER_PROCESSOR);
        if (container == null) {
            logger.warn("Processor listener '{}' not found; cannot {} it.",
                KafkaConstants.CST_LISTENER_PROCESSOR, pause ? "pause" : "resume");
            return;
        }
        if (pause) {
            container.pause();
            logger.info("Kafka processor listener PAUSED.");
        } else {
            container.resume();
            logger.info("Kafka processor listener RESUMED.");
        }
    }

    private void checkpoint(ConsumerRecord<String, String> record, String topic, int partition, long offset) {
        logger.info("Message : -> topic: {}, partition: {}, offset: {}, valeur: {}.",
                    topic, partition, offset, record.value());
        // Nothing to do here for now, but good place to set a breakpoint and observe the 
        // replay behavior in debug mode. Or to do some light processing before the main 
        // processor (e.g. to extract the replay header and log it).
    }

    /**
     * Records one processed message (successful or routed to the DLT) and evaluates the
     * terminal state of the pipeline. The increment and the completion check form a single
     * compound read-modify-write executed atomically under the distributed key lock, so
     * concurrent consumer threads (container concurrency &gt; 1), the DLT recovery callback
     * and other cluster members cannot lose the completion edge.
     */
    private void recordProcessed(boolean withError) {
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
    private <T> T getOrInit(IMap<String, T> map, @Nonnull String key, Supplier<T> factory) {
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
    private <T> void mutate(IMap<String, T> map, String key, Supplier<T> factory, Consumer<T> mutation) {
        map.lock(key);
        try {
            T value = getOrInit(map, key, factory);
            mutation.accept(value);
            map.set(key, value);
        } finally {
            map.unlock(key);
        }
    }

    /** Atomic read-modify-write on the distributed {@link StatsContext}. */
    private void mutateStats(Consumer<StatsContext> mutation) {
        mutate(statsMap(), HazelcastConstants.CST_KEY_PIPELINE_STATS, StatsContext::new, mutation);
    }
}