/*
 * TO BE COMPLETED
 * 
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

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.apache.kafka.common.serialization.StringDeserializer;

import org.rd.fullstack.springbooteda.util.kafka.KafkaSandbox;

import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.stereotype.Service;

@Service
public class DltRecoverySrv {

    private static final Duration READ_TIMEOUT =
        Duration.ofSeconds(10);

    private final KafkaSandbox kafkaSandbox;
    private final KafkaTemplate<String, String> kafkaTemplate;

    public DltRecoverySrv(KafkaSandbox kafkaSandbox, 
                          KafkaTemplate<String, String> kafkaTemplate) {
        super();
        this.kafkaSandbox = kafkaSandbox;
        this.kafkaTemplate = kafkaTemplate;
    }

    public record ReplayResult(
        String eventId,
        String originalTopic,
        int originalPartition,
        long originalOffset,
        String publishedTopic,
        int publishedPartition,
        long publishedOffset
    ) {}

    public ReplayResult replayDlt(String dltTopic, int partition, long offset) {

        if (partition < 0 || offset < 0)
            throw new IllegalArgumentException("Invalid partition or offset.");

        if (!dltTopic.endsWith("-dlt"))
            throw new IllegalArgumentException("Not a DLT topic: " + dltTopic);

        // 1. Read the exact record from the DLT.
        ConsumerRecord<String, String> record =
            readDltRecord(dltTopic, partition, offset);

        // 2. Retrieve the original Kafka topic.
        String originalTopic =
            getOriginalTopic(record);

        if (originalTopic == null || originalTopic.isBlank())
            throw new IllegalStateException("Original Kafka topic not found.");

        if (originalTopic.equals(dltTopic))
            throw new IllegalStateException("Cannot replay into the same DLT.");

        // 3. Preserve business headers, excluding DLT metadata.
        RecordHeaders headers = new RecordHeaders();
        for (Header header : record.headers()) {
            if (!header.key().startsWith("kafka_dlt-")) {
                headers.add(header);
            }
        }

        // 4. Create a new record for the original topic.
        // Partition is intentionally null: the producer
        // selects it according to the key/partitioner.
        ProducerRecord<String, String> replay =
            new ProducerRecord<>(originalTopic, null, record.key(), record.value(), headers);

        // 5. Publish and wait for broker acknowledgment.
        try {
            var result = kafkaTemplate
                .send(replay)
                .get(10, TimeUnit.SECONDS);

            var metadata = result.getRecordMetadata();

            return new ReplayResult(
                record.key(), // Replace with eventIdOf(payload)
                dltTopic,
                partition,
                offset,
                metadata.topic(),
                metadata.partition(),
                metadata.offset()
            );

        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(
                "DLT replay interrupted.", ex);

        } catch (Exception ex) {
            throw new IllegalStateException(
                "DLT replay publication failed or is uncertain.", ex);
        }
    }

    private ConsumerRecord<String, String> readDltRecord(String topic, int partition, long offset) {

        Map<String, Object> cfg = Map.of(
            ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG,
                kafkaSandbox.getBootstrapServers(),
            ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false,
            ConsumerConfig.ISOLATION_LEVEL_CONFIG, "read_committed",
            ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG,
                StringDeserializer.class,
            ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG,
                StringDeserializer.class
        );

        TopicPartition tp = new TopicPartition(topic, partition);
        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(cfg)) {
            consumer.assign(List.of(tp));
            consumer.seek(tp, offset);

            long deadline = System.nanoTime() + READ_TIMEOUT.toNanos();
            while (System.nanoTime() < deadline) {

                var records =
                    consumer.poll(Duration.ofMillis(250));

                for (ConsumerRecord<String, String> record : records) {
                    if (record.offset() == offset)
                        return record;

                    if (record.offset() > offset)
                        throw new IllegalStateException("Requested DLT offset is unavailable.");
                }
            }

            throw new IllegalStateException("DLT record not found within timeout.");
        }
    }

    private String getOriginalTopic(ConsumerRecord<String, String> record) {

        Header header = record.headers()
            .lastHeader(KafkaHeaders.DLT_ORIGINAL_TOPIC);

        if (header == null || header.value() == null)
            return null;

        return new String(header.value(), java.nio.charset.StandardCharsets.UTF_8
        );
    }
}