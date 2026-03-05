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
package org.rd.fullstack.springbooteda;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.common.serialization.SimpleStringSchema;
import org.apache.flink.connector.kafka.sink.KafkaRecordSerializationSchema;
import org.apache.flink.connector.kafka.sink.KafkaSink;
import org.apache.flink.connector.kafka.source.KafkaSource;
import org.apache.flink.connector.kafka.source.enumerator.initializer.OffsetsInitializer;
import org.apache.flink.core.execution.JobClient;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.rd.fullstack.springbooteda.config.Application;
import org.rd.fullstack.springbooteda.srv.SmartLifecycleSrv;
import org.rd.fullstack.springbooteda.util.flink.FlinkSandbox;
import org.rd.fullstack.springbooteda.util.kafka.KafkaSandbox;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.Acknowledgment;

@SpringBootTest(classes = Application.class)
@DisplayName("Apache Flink demo.")
public class T2400_KafkaFlink_UT_Tests {
    private static final Logger logger = 
        LoggerFactory.getLogger(T2400_KafkaFlink_UT_Tests.class);

    @Autowired
    private KafkaSandbox kafkaSandbox;

    @Autowired
    private SmartLifecycleSrv smartLifecycleService;

    @Autowired
    private FlinkSandbox flinkSandbox;

    private AtomicInteger msgCount;
    private ConcurrentLinkedQueue<Throwable> sendExceptions;

    private static final int CST_NBR_MSG = 100;

    public T2400_KafkaFlink_UT_Tests() {
        super();
    }

    @Test
    @Order(1)
    public void apacheFlinkWithKafkaDemotests() throws Exception {
        assertTrue(kafkaSandbox.isStarted());

        final String CST_TOPIC_INPUT  = "T2400-JVM-Topic-Input";
        final String CST_TOPIC_OUTPUT = "T2400-JVM-Topic-Output";

        // Broker info.
        String ports = kafkaSandbox.getBootstrapServers();
        assertNotNull(ports);
        logger.info(ports);

        // Create topics.
        kafkaSandbox.addTopic(CST_TOPIC_INPUT, 10, (short)1);
        kafkaSandbox.addTopic(CST_TOPIC_OUTPUT, 10, (short)1);

        // Our meters.
        msgCount = new AtomicInteger(0);
        sendExceptions = new ConcurrentLinkedQueue<>();

        // KafkaTemplate et listener output topic.
        KafkaTemplate<String, String> template = kafkaSandbox.getKafkaTemplate(CST_TOPIC_OUTPUT);
        kafkaSandbox.setupMessageListener(CST_TOPIC_OUTPUT, this::processMessageFlinkOutput);

        // Send messages.
        sendMessages(template, CST_TOPIC_INPUT, CST_NBR_MSG);

        // Setup Flink.
        StreamExecutionEnvironment env = StreamExecutionEnvironment.createLocalEnvironment();

        KafkaSource<String> kafkaSource = KafkaSource.<String>builder()
                .setBootstrapServers(ports)
                .setTopics(List.of(CST_TOPIC_INPUT))
                .setGroupId(kafkaSandbox.getCfg().uuidID().toString())
                .setStartingOffsets(OffsetsInitializer.earliest())
                .setValueOnlyDeserializer(new SimpleStringSchema())
                .build();

        KafkaSink<String> kafkaSink = KafkaSink.<String>builder()
                .setBootstrapServers(ports)
                .setRecordSerializer(KafkaRecordSerializationSchema.builder()
                        .setTopic(CST_TOPIC_OUTPUT)
                        .setValueSerializationSchema(new SimpleStringSchema())
                        .build())
                .build();

        env.fromSource(kafkaSource, WatermarkStrategy.noWatermarks(), "Kafka Source")
           .map(String::toUpperCase)
           .sinkTo(kafkaSink);

        // Execute Flink job.
        JobClient client = env.executeAsync("FlinkJVM-Kafka-Pipeline");

        // Wait until all messages are processed or timeout
        Awaitility.await().atMost(40, TimeUnit.SECONDS).until(() -> msgCount.get() == CST_NBR_MSG);

        // Stop flink.
        client.cancel().get();

        // Verify that all messages were sent without error.
        assertTrue(sendExceptions.isEmpty(), "Errors during sending: " + sendExceptions);

        // Checking the number of messages.
        assertEquals(CST_NBR_MSG, msgCount.get());
    }

    @Test
    @Order(2)
    public void apacheFlinkMiniClusterWithKafkaDemotests() throws Exception {
        assertTrue(kafkaSandbox.isStarted());
        assertTrue(flinkSandbox.isStarted());

        final String CST_TOPIC_INPUT  = "T2400-Cluster-Topic-Input";
        final String CST_TOPIC_OUTPUT = "T2400-Cluster-Topic-Output";

        // Broker info.
        String ports = kafkaSandbox.getBootstrapServers();
        assertNotNull(ports);
        logger.info(ports);

        // Create topics.
        kafkaSandbox.addTopic(CST_TOPIC_INPUT, 10, (short)1);
        kafkaSandbox.addTopic(CST_TOPIC_OUTPUT, 10, (short)1);

        // Our meters.
        msgCount = new AtomicInteger(0);
        sendExceptions = new ConcurrentLinkedQueue<>();

        // KafkaTemplate et listener output topic.
        KafkaTemplate<String, String> template = kafkaSandbox.getKafkaTemplate(CST_TOPIC_INPUT);
        kafkaSandbox.setupMessageListener(CST_TOPIC_OUTPUT, this::processMessageFlinkOutput);

        // Send messages.
        sendMessages(template, CST_TOPIC_INPUT, CST_NBR_MSG);

        // Setup Flink.
        URI uri = flinkSandbox.getURI();
        StreamExecutionEnvironment env =
                StreamExecutionEnvironment.createRemoteEnvironment(uri.getHost(),uri.getPort());
        env.setParallelism(4);

        KafkaSource<String> source = KafkaSource.<String>builder()
                .setBootstrapServers(kafkaSandbox.getBootstrapServers())
                .setTopics(CST_TOPIC_INPUT)
                .setGroupId(kafkaSandbox.getCfg().uuidID().toString())
                .setStartingOffsets(OffsetsInitializer.earliest())
                .setValueOnlyDeserializer(new SimpleStringSchema())
                .build();

        KafkaSink<String> sink = KafkaSink.<String>builder()
                .setBootstrapServers(kafkaSandbox.getBootstrapServers())
                .setRecordSerializer(
                        KafkaRecordSerializationSchema.builder()
                                .setTopic(CST_TOPIC_OUTPUT)
                                .setValueSerializationSchema(new SimpleStringSchema())
                                .build()
                ).build();       
                
        // Execute async → no deadlock.
        env.fromSource(source, WatermarkStrategy.noWatermarks(), "KafkaSource")
            .map(String::toUpperCase)
            .sinkTo(sink);

        // Execute Flink job.
        JobClient client = env.executeAsync("FlinkCluster-Kafka-Pipeline");
        
        // Wait until all messages are processed or timeout
        Awaitility.await().atMost(40, TimeUnit.SECONDS).until(() -> msgCount.get() == CST_NBR_MSG);

        // Cancel job to cleanly exit
        client.cancel().get();

        // Verify that all messages were sent without error.
        assertTrue(sendExceptions.isEmpty(), "Errors during sending: " + sendExceptions);

        // Checking the number of messages.
        assertEquals(CST_NBR_MSG, msgCount.get());
    }

    private void sendMessages(KafkaTemplate<String, String> template, String topic, int nbrMsg) {
        for (int i = 0; i < nbrMsg; i++) {
            template.send(new ProducerRecord<>(topic, "noKey", "Hello Flink: " + (i+1)))
                    .whenComplete((result, ex) -> {
                        if (ex != null) {
                            sendExceptions.add(ex);
                            logger.error("Error sending message: {}.", ex);
                        } else {
                            logger.info("Message sent: topic={}, partition={}, offset={}",
                                    result.getRecordMetadata().topic(),
                                    result.getRecordMetadata().partition(),
                                    result.getRecordMetadata().offset());
                        }
                    });
        }
    }

    private void processMessageFlinkOutput(ConsumerRecord<String, String> record, 
                                           Acknowledgment acknowledgment) {
        if (!smartLifecycleService.isRunning())
            return;

        logger.info("FLINK OUTPUT - Message received: partition={}, offset={}, value={}",
                record.partition(), record.offset(), record.value());

        acknowledgment.acknowledge();
        msgCount.incrementAndGet();
    }
}