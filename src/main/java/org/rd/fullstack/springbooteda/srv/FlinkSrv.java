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
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import org.apache.flink.api.common.JobStatus;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.common.serialization.SimpleStringSchema;
import org.apache.flink.api.connector.sink2.Sink;
import org.apache.flink.api.connector.sink2.SinkWriter;
import org.apache.flink.api.connector.sink2.WriterInitContext;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.configuration.RestartStrategyOptions;
import org.apache.flink.connector.kafka.source.KafkaSource;
import org.apache.flink.connector.kafka.source.enumerator.initializer.OffsetsInitializer;
import org.apache.flink.core.execution.CheckpointingMode;
import org.apache.flink.core.execution.JobClient;
import org.apache.flink.core.execution.SavepointFormatType;
import org.apache.flink.runtime.jobgraph.SavepointRestoreSettings;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.graph.StreamGraph;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.rd.fullstack.springbooteda.util.flink.FlinkSandbox;
import org.rd.fullstack.springbooteda.util.kafka.KafkaConstants;
import org.rd.fullstack.springbooteda.util.kafka.KafkaSandbox;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
public class FlinkSrv {

    private static final Logger logger = 
        LoggerFactory.getLogger(FlinkSrv.class);

    private static final String CST_PIPELINE_NAME      = "Flink-Pipeline";
    private static final String CST_SAVEPOINT_DIR_NAME = "springboot-eda-flink-savepoints";
    private static final SavepointFormatType CST_SAVEPOINT_FORMAT = SavepointFormatType.CANONICAL;
    private static final long CST_SHUTDOWN_TIMEOUT_SECONDS = 15;

    // Cross-classloader bridge to THIS service (the Flink record-processing entry point).
    //
    // The Flink operators (ProcessorSink) run on MiniCluster task threads whose user-code
    // classloader is NOT the one Spring used to load this service. Under Spring Boot DevTools the
    // beans live in a throwaway RestartClassLoader, while the Flink tasks resolve classes through a
    // different (base) classloader. A plain static field is therefore useless here: the static set
    // on the Spring-side copy of FlinkService stays null on the copy the task threads see, so every
    // record was silently dropped ("...not wired...; dropping record").
    //
    // The robust bridge is a carrier that has a SINGLE identity across ALL classloaders: the
    // java.lang.System properties (held by the bootstrap classloader). startJob() publishes this
    // service there; the sink reads it back and invokes processFromFlink REFLECTIVELY — reflection
    // dispatches on the object's OWN class, so it needs no shared FlinkService type identity between
    // the two classloaders.
    private static final String CST_BRIDGE_KEY = "org.rd.fullstack.springbooteda.flink.pipeline-bridge";

    @Value("${org.rd.fullstack.springbooteda.flink.pipeline.parallelism}")
    private int parallelism;

    @Value("${org.rd.fullstack.springbooteda.flink.pipeline.checkpoint-interval-ms}")
    private long checkpointIntervalMs;

    @Value("${org.rd.fullstack.springbooteda.flink.pipeline.restart-attempts}")
    private int restartAttempts;

    @Value("${org.rd.fullstack.springbooteda.flink.pipeline.restart-delay-ms}")
    private long restartDelayMs;

    @Value("${org.rd.fullstack.springbooteda.flink.pipeline.savepoint-dir}")
    private String savepointDir;

    @Value("${org.rd.fullstack.springbooteda.flink.pipeline.savepoint-timeout-sec}")
    private long savepointTimeoutSec;

    @Value("${org.rd.fullstack.springbooteda.flink.pipeline.status-timeout-sec}")
    private long statusTimeoutSec;

    // Savepoint captured when the job is paused (stop-with-savepoint); null when not paused. Used to
    // restart the job from exactly where it left off on resume.
    private String    pausedSavepoint;
    private JobClient client;

    private final KafkaSandbox kafkaSandbox;
    private final FlinkSandbox flinkSandbox;
    private final PipelineSrv  pipelineSrv;

    public FlinkSrv(KafkaSandbox kafkaSandbox, FlinkSandbox flinkSandbox, PipelineSrv pipelineSrv) {
        this.client       = null;
        this.kafkaSandbox = kafkaSandbox;
        this.flinkSandbox = flinkSandbox;
        this.pipelineSrv  = pipelineSrv;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onStart() {
        synchronized (this) {
            startJob(null);
        }
    }

    /**
     * Reacts to a pause/resume request (published by {@link PipelineSrv#setPause}). Pausing performs
     * a stop-with-savepoint and tears the job down; resuming restarts it from that savepoint. Routed
     * through an event so {@code PipelineSrv} does not have to depend on this service directly.
     */
    @EventListener
    public void onFlinkPauseEvent(FlinkPauseEvent event) {
        if (event.paused())
            pause();
        else
            resume();
    }

    /**
     * Pauses the Flink job by taking a savepoint and stopping it (the closest equivalent to the
     * Kafka listener's {@code container.pause()}). The savepoint path is retained so {@link #resume()}
     * can restart exactly where it left off. A no-op if there is no running job.
     */
    public void pause() {
        synchronized (this) {
            if (client == null) {
                logger.info("Flink pause requested, but no running job — nothing to pause.");
                return;
            }
            try {
                JobStatus status = client.getJobStatus().get(statusTimeoutSec, TimeUnit.SECONDS);
                if (status != JobStatus.RUNNING) {
                    logger.warn("Flink job is {} (not RUNNING) — cannot stop-with-savepoint.", status);
                    return;
                }

                String dir = savepointDirUri();
                logger.info("Pausing Flink job — stop-with-savepoint to {} ...", dir);
                pausedSavepoint = client
                                    .stopWithSavepoint(false, dir, CST_SAVEPOINT_FORMAT)
                                    .get(savepointTimeoutSec, TimeUnit.SECONDS);
                client = null;
                logger.info("Flink job paused; savepoint at {}.", pausedSavepoint);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                logger.error("Interrupted while pausing the Flink job: {}.", ex);
            } catch (Exception ex) {
                logger.error("Failed to pause the Flink job via savepoint: {}.", ex);
            }
        }
    }

    /**
     * Resumes a previously paused Flink job by restarting it from the savepoint captured in
     * {@link #pause()}. A no-op if the job is already running or if no savepoint was recorded.
     */
    public void resume() {
        synchronized (this) {
            if (client != null) {
                logger.info("Flink resume requested, but the job is already running — nothing to do.");
                return;
            }
            if (pausedSavepoint == null) {
                logger.info("Flink resume requested, but no savepoint was recorded — nothing to resume.");
                return;
            }

            logger.info("Resuming Flink job from savepoint {} ...", pausedSavepoint);
            startJob(pausedSavepoint);
            if (client != null)
                pausedSavepoint = null; // Clear only once the restart actually succeeded.
        }
    }

    /**
     * Builds and submits the Flink job. When {@code restoreFromSavepoint} is non-null the job is
     * restored from that savepoint (resume); otherwise it starts fresh. Callers must hold
     * {@link #jobLock}.
     */
    private void startJob(String restoreFromSavepoint) {
        if (client != null)
            return;

        logger.info("Flink pipeline starting{}.",
            restoreFromSavepoint != null ? " (restoring from savepoint " + restoreFromSavepoint + ")" : "");

        // Publish THIS service for the Flink operators through the cross-classloader bridge.
        // The task-side code resolves a JDK Consumer<String> from the JVM-global properties so it
        // uses a shared bootstrap interface.
        System.getProperties().put(CST_BRIDGE_KEY, (Consumer<String>) value -> {
            try {
                processFromFlink(value);
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
        });

        URI uri;
        try {
            uri = flinkSandbox.getURI();
        } catch (IllegalStateException ex) {
            logger.error("Cannot start Flink pipeline: {}.", ex);
            return;
        }

        StreamExecutionEnvironment env = StreamExecutionEnvironment.createRemoteEnvironment(uri.getHost(), uri.getPort());
        env.setParallelism(parallelism);

        // At-least-once fault tolerance for the Flink path, the counterpart of the direct listener's
        // "ack only after a successful, committed processing". The KafkaSource keeps its consuming
        // offsets in Flink's checkpointed state (NOT in Kafka's committed offsets, which it writes
        // only on checkpoint for lag monitoring); on recovery it restores from the last checkpoint,
        // so records in flight since then are REPLAYED. AT_LEAST_ONCE is the honest mode: the sink
        // performs an external DB side effect and is not transactional/2PC, so exactly-once cannot be
        // claimed. Replays are safe for the DB because ProcessorSrv.process() is idempotent
        // (already-EXECUTED requests are skipped); the stats counter is not replay-idempotent, which
        // is acceptable here as the recovery path is exceptional.
        env.enableCheckpointing(checkpointIntervalMs, CheckpointingMode.AT_LEAST_ONCE);

        Configuration restartCfg = new Configuration();
        restartCfg.set(RestartStrategyOptions.RESTART_STRATEGY, "fixed-delay");
        restartCfg.set(RestartStrategyOptions.RESTART_STRATEGY_FIXED_DELAY_ATTEMPTS, restartAttempts);
        restartCfg.set(RestartStrategyOptions.RESTART_STRATEGY_FIXED_DELAY_DELAY, Duration.ofMillis(restartDelayMs));
        env.configure(restartCfg);

        KafkaSource<String> source = KafkaSource.<String>builder()
                .setBootstrapServers(kafkaSandbox.getBootstrapServers())
                .setTopics(KafkaConstants.CST_TOPIC_FLINK_REQ)
                .setGroupId(KafkaConstants.CST_TOPIC_GROUP)
                .setStartingOffsets(OffsetsInitializer.earliest())
                .setValueOnlyDeserializer(new SimpleStringSchema())
                .setProperty( // Exclude uncommitted and aborted Kafka transactional records.
                        ConsumerConfig.ISOLATION_LEVEL_CONFIG,
                        KafkaConstants.CST_ISOLATION_LEVEL_CONFIG
                )
                .build();

        env.fromSource(source, WatermarkStrategy.noWatermarks(), KafkaConstants.CST_TOPIC_FLINK_REQ).sinkTo(new ProcessorSink());

        try {
            StreamGraph streamGraph = env.getStreamGraph();
            streamGraph.setJobName(CST_PIPELINE_NAME);
            if (restoreFromSavepoint != null)
                streamGraph.setSavepointRestoreSettings(SavepointRestoreSettings.forPath(restoreFromSavepoint));
            client = env.executeAsync(streamGraph);
        } catch (Exception ex) {
            client = null;
            logger.error("Flink pipeline startup failed: {}.", ex);
        }
    }

    @EventListener(ContextClosedEvent.class)
    public void onStop() {
        synchronized (this) {
            try {
                if (client != null)
                    client.cancel().get(CST_SHUTDOWN_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                logger.error("Flink pipeline shutdown interrupted: {}.", ex);
            } catch (ExecutionException ex) {
                logger.error("Flink pipeline shutdown failed: {}.", ex);
            } catch (java.util.concurrent.TimeoutException ex) {
                logger.error("Flink pipeline shutdown timed out after {} second(s).",
                    CST_SHUTDOWN_TIMEOUT_SECONDS, ex);
            } finally {
                client = null;
                // Drop the bridge so a stale reference to this context's bean cannot linger across
                // a DevTools restart (startJob() republishes a fresh one anyway).
                System.getProperties().remove(CST_BRIDGE_KEY);
            }
        }
    }

    /**
     * Entry point invoked by the Flink SINK ({@link ProcessorSink}) for every record. It runs the
     * exact same per-record processing as the Kafka listener via {@link PipelineSrv#handle} (so
     * stats/completion and per-client serialization behave identically), but there is no Kafka offset
     * to commit here — Flink manages its own source progress.
     *
     * <p>Dead Letter handling mirrors the direct path's {@code DefaultErrorHandler}: the record is
     * retried with a bounded back-off (same policy as the Kafka path) and, if all attempts fail, it
     * is routed to the Flink DLT and counted as processed-with-error via
     * {@link PipelineSrv#recordProcessed} — rather than letting the exception fail the whole Flink
     * job. Only a failure to publish to the DLT itself (a genuine infrastructure problem) propagates,
     * so the job restart strategy applies and the record is replayed from the last checkpoint.</p>
     */
    public void processFromFlink(String value) throws Exception {

        final int attempts = (int) KafkaConstants.CST_RETRY_ATTEMPTS + 1; // total tries (mirror Kafka path).
        Exception last = null;

        for (int attempt = 1; attempt <= attempts; attempt++) {
            try {
                pipelineSrv.handle(value);
                return; // Success — completion already counted inside handle().
            } catch (Exception ex) {
                last = ex;
                logger.warn("Flink processing attempt {}/{} failed for record: {}.", attempt, attempts, value, ex);
            }
            if (attempt < attempts)
                Thread.sleep(KafkaConstants.CST_RETRY_INTERVAL);
        }

        // Retries exhausted: route the poison record to the DLT and count it as processed-with-error,
        // the counterpart of the Kafka DefaultErrorHandler's retry-then-DLT recovery.
        logger.warn("Flink processing exhausted {} attempt(s) — routing record to the Flink DLT.", attempts);
        sendToFlinkDlt(value, last);
        pipelineSrv.recordProcessed(true);
    }

    /**
     * Publishes a record that exhausted its processing attempts on the Flink path to the Dead
     * Letter Topic paired with the Flink source topic ({@code APP-Flink-requests-dlt}, created in
     * KafkaConfig). The publish is transactional — like the direct path's DLT recoverer — so the
     * dead-lettered record is visible to read_committed consumers. The original failure cause is
     * attached as a record header for inspection.
     */
    private void sendToFlinkDlt(String value, Exception cause) {
        final String dlt = KafkaConstants.CST_TOPIC_FLINK_REQ + KafkaConstants.CST_TOPIC_DLT_POSTFIX;
        KafkaTemplate<String, String> template = kafkaSandbox.getKafkaTemplate(dlt, true);

        final List<org.apache.kafka.common.header.Header> headers = new ArrayList<>();
        if (cause != null) {
            final String reason = cause.getClass().getName() + ": " + String.valueOf(cause.getMessage());
            headers.add(new RecordHeader(KafkaConstants.CST_HEADER_FLINK_DLT_CAUSE,
                reason.getBytes(StandardCharsets.UTF_8)));
        }

        template.executeInTransaction(ops -> {
            // Cast the null key to String so the (topic, partition, key, value, headers) constructor
            // is selected unambiguously (otherwise it collides with the timestamp overload).
            ops.send(new ProducerRecord<>(dlt, null, (String) null, value, headers));
            return null; // executeInTransaction requires a return value.
        });
    }

    /**
     * Returns (creating it if needed) the local directory URI where the MiniCluster writes
     * savepoints, under the JVM temp directory. Adequate for the embedded sandbox; a real deployment
     * would point this at a shared/durable filesystem.
     */
    private String savepointDirUri() throws IOException {
        Path dir = (savepointDir == null || savepointDir.isBlank())
            ? Path.of(System.getProperty("java.io.tmpdir"), CST_SAVEPOINT_DIR_NAME)
            : Path.of(savepointDir);

        Files.createDirectories(dir);
        return dir.toUri().toString();
    }

    /**
     * Flink sink (sink2 API) that invokes {@link #processFromFlink} for every record, in place of
     * writing to a Kafka topic. It holds no Spring references (so Flink can serialize it); the Flink
     * service is resolved at runtime from the cross-classloader bridge populated by {@link #startJob}
     * (see {@link #CST_BRIDGE_KEY}). The resolved target is the real, Spring-managed {@code FlinkService}
     * bean, so {@code PipelineSrv.handle()} and the processor's {@code @Transactional} semantics apply
     * on the Flink task thread.
     */
    private static final class ProcessorSink implements Sink<String> {
        private static final long serialVersionUID = 1L;

        @Override
        public SinkWriter<String> createWriter(WriterInitContext context) {
            return new ProcessorSinkWriter();
        }
    }

    /**
     * Stateless writer: processing is synchronous per record, so there is nothing to buffer,
     * flush or release. Each element is handed to {@link #processFromFlink} for the same per-client
     * lock + transactional processing + completion counting as the Kafka listener path.
     */
    private static final class ProcessorSinkWriter implements SinkWriter<String> {

        // The bridge target is a JDK Consumer<String> resolved lazily from the JVM-global bridge.
        // Using java.util.function.Consumer avoids app-specific class identity problems across
        // Flink task classloaders while still allowing the runtime to invoke the Spring-side handler.
        private transient Consumer<String> bridge;

        @Override
        public void write(String element, SinkWriter.Context context) throws IOException, InterruptedException {

            resolveBridge();
            if (bridge == null) { // Should not happen: the bridge is published before the job is submitted.
                logger.error("Flink sink is not wired to the pipeline service; dropping record: {}.", element);
                return;
            }

            try {
                bridge.accept(element); // The bridge target is the Spring-managed FlinkService bean, 
                                        // so this invokes processFromFlink() there. 
                                        // Any exception is wrapped in a RuntimeException by the Consumer 
                                        // interface, so we unwrap it to preserve the original semantics.
            } catch (RuntimeException ex) {
                Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
                if (cause instanceof IOException io)
                    throw io;
                if (cause instanceof InterruptedException ie)
                    throw ie;
                throw new IOException("Flink sink failed to process record.", cause);
            }
        }

        // Resolves the bridge consumer from the JVM-global bridge. A no-op once resolved;
        // tolerant of the (transient, should-not-happen) window where the bridge is not yet published.
        private void resolveBridge() throws IOException {

            if (bridge != null)
                return;

            Object bean = System.getProperties().get(CST_BRIDGE_KEY);
            if (bean == null)
                return;

            if (!(bean instanceof Consumer<?> consumer))
                throw new IOException("Flink sink bridge value is not a Consumer<String>.");

            @SuppressWarnings("unchecked")
            Consumer<String> stringConsumer = (Consumer<String>) consumer;
            this.bridge = stringConsumer;
        }

        @Override
        public void flush(boolean endOfInput) {
            // Nothing buffered.
        }

        @Override
        public void close() {
            // Nothing to release.
        }
    }
}