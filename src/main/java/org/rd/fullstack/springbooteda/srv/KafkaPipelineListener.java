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

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.rd.fullstack.springbooteda.util.kafka.KafkaConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.springframework.kafka.listener.RetryListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;

/**
 * The <strong>direct (Kafka) path</strong> of the pipeline: the {@code @KafkaListener} that consumes
 * the processor topic and hands each record to the shared {@link PipelineSrv#handle(String)} core.
 *
 * <p>Split out of {@code PipelineSrv} so that the path-agnostic orchestration (start, publish, state,
 * stats, per-record handling) stays in {@code PipelineSrv}, the Flink-specific entry point lives in
 * {@link FlinkSrv}, and this bean owns only the Kafka-listener concerns: the listener itself, its
 * Dead-Letter retry observer, and pause/resume of the container.</p>
 *
 * <p>When the "flink" option is enabled, {@code PipelineSrv.publish()} sends to the Flink source topic
 * instead, so <em>no</em> record flows through this listener in that mode (and the pause is a harmless
 * no-op). Both paths share the same per-record handling via {@code PipelineSrv.handle()}.</p>
 */
@Service
public class KafkaPipelineListener {

    private static final Logger logger =
        LoggerFactory.getLogger(KafkaPipelineListener.class);

    // Shared, path-agnostic pipeline core (per-record handling + completion counting).
    private final PipelineSrv pipeline;

    // Same instance used by the listener container factory (see KafkaConfig). A RetryListener is
    // registered on it to observe records that exhaust their retries and are routed to the DLT.
    private final DefaultErrorHandler errorHandler;

    // Registry of @KafkaListener containers — used to pause/resume the processor listener.
    private final KafkaListenerEndpointRegistry listenerRegistry;

    // Shutdown sentinel: isRunning() flips to false as soon as the context starts closing.
    private final SmartLifecycleSrv smartLifecycleSrv;

    public KafkaPipelineListener(PipelineSrv pipeline, DefaultErrorHandler errorHandler,
                                 KafkaListenerEndpointRegistry listenerRegistry,
                                 SmartLifecycleSrv smartLifecycleSrv) {

        this.pipeline          = pipeline;
        this.errorHandler      = errorHandler;
        this.listenerRegistry  = listenerRegistry;
        this.smartLifecycleSrv = smartLifecycleSrv;
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

                pipeline.recordProcessed(true);
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

    // Serves the DIRECT path only (flink disabled): publish() sends requests straight to
    // CST_TOPIC_KAFKA_REQ and they are processed here. When the "flink" option is enabled,
    // publish() sends to the Flink source topic instead and the Flink job's sink invokes the
    // processor directly (see FlinkService.processFromFlink()), so NO record flows through
    // this listener in that mode. Both paths share the same per-record handling (PipelineSrv.handle()).
    @KafkaListener(id = KafkaConstants.CST_LISTENER_PROCESSOR,
                  topics = KafkaConstants.CST_TOPIC_KAFKA_REQ,
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
            logger.warn("Application is shutting down — skipping record - topic={}, partition={}, offset={}; it will be redelivered after restart.",
                topic, partition, offset);
            return;
        }

        // check the record metadata and value for visibility (especially the records that are retried
        // and ultimately routed to the DLT by the error handler). Check lags and offsets in the logs
        // to verify the pause/resume behavior and the at-least-once processing guarantees (no offset
        // is committed before processing, so a crash before the ack replays the same record).
        checkpoint(record, topic, partition, offset);

        // Per-record processing (per-client lock + transactional processor + completion count)
        // is shared with the Flink sink path; see PipelineSrv.handle(). Any exception propagates to
        // the container's DefaultErrorHandler, which retries with a bounded back-off and ultimately
        // routes the record to the DLT. The JPA transaction in the processor rolls back on failure,
        // so no partial DB state is committed.
        pipeline.handle(record.value());

        // Commit the offset only after a successful, committed processing.
        // (at-least-once: a crash before this point replays the message.)
        ack.acknowledge();
    }

    /**
     * Reacts to a pause/resume request (published by {@link PipelineSrv#setPause}). Pausing stops the
     * container from consuming; resuming restarts it. Routed through an event so {@code PipelineSrv}
     * does not have to depend on this bean directly (it already depends on {@code PipelineSrv}).
     */
    @EventListener
    public void onKafkaListenerPauseEvent(KafkaListenerPauseEvent event) {
        applyPause(event.paused());
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
    }
}
