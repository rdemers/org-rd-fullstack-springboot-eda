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

import java.net.URI;
import java.util.concurrent.ExecutionException;

import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.common.serialization.SimpleStringSchema;
import org.apache.flink.connector.kafka.sink.KafkaRecordSerializationSchema;
import org.apache.flink.connector.kafka.sink.KafkaSink;
import org.apache.flink.connector.kafka.source.KafkaSource;
import org.apache.flink.connector.kafka.source.enumerator.initializer.OffsetsInitializer;
import org.apache.flink.core.execution.JobClient;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.rd.fullstack.springbooteda.util.flink.FlinkSandbox;
import org.rd.fullstack.springbooteda.util.kafka.KafkaConstants;
import org.rd.fullstack.springbooteda.util.kafka.KafkaSandbox;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

@Service
public class FlinkService {
    private static final Logger logger = 
        LoggerFactory.getLogger(FlinkService.class);

    private static final String CST_PIPELINE_NAME = "Flink-Pipeline";
    private static final String CST_SOURCE_NAME   = "KafkaSource";

    @Autowired
    private KafkaSandbox kafkaSandbox;

    @Autowired
    private FlinkSandbox flinkSandbox;

    private JobClient client;

    public FlinkService() {
        this.client = null;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onStart() {
        if (client != null)
            return;

        logger.info("Flink pipeline starting.");

        URI uri;
        try {
            uri = flinkSandbox.getURI();
        } catch (IllegalStateException ex) {
            logger.error("Cannot start Flink pipeline: {}.", ex);
            return;
        }

        StreamExecutionEnvironment env =
                StreamExecutionEnvironment.createRemoteEnvironment(uri.getHost(), uri.getPort());
        env.setParallelism(4);

        KafkaSource<String> source = KafkaSource.<String>builder()
                .setBootstrapServers(kafkaSandbox.getBootstrapServers())
                .setTopics(KafkaConstants.CST_TOPIC_FLINK_IN)
                .setGroupId(kafkaSandbox.getCfg().uuidID().toString())
                .setStartingOffsets(OffsetsInitializer.earliest())
                .setValueOnlyDeserializer(new SimpleStringSchema())
                .build();

        KafkaSink<String> sink = KafkaSink.<String>builder()
                .setBootstrapServers(kafkaSandbox.getBootstrapServers())
                .setRecordSerializer(
                        KafkaRecordSerializationSchema.builder()
                                .setTopic(KafkaConstants.CST_TOPIC_FLINK_OUT)
                                .setValueSerializationSchema(new SimpleStringSchema())
                                .build()
                ).build();

        // Execute async -> no deadlock.
        env.fromSource(source, WatermarkStrategy.noWatermarks(), CST_SOURCE_NAME)
            .map(String::toUpperCase)
            .sinkTo(sink);

        // Execute Flink job.
        try {
            client = env.executeAsync(CST_PIPELINE_NAME);
        } catch (Exception ex) {
            client = null;
            logger.error("Flink pipeline startup failed: {}.", ex);
        }
    }

    @EventListener(ContextClosedEvent.class)
    public void onStop() {
        if (client == null)
            return;

        try {
            client.cancel().get();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            logger.error("Flink pipeline shutdown interrupted: {}.", ex);
        } catch (ExecutionException ex) {
            logger.error("Flink pipeline shutdown failed: {}.", ex);
        } finally {
            client = null;
        }
    }
}