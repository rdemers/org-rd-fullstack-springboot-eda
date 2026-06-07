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
package org.rd.fullstack.springbooteda.util.kafka;

import java.util.List;

public class KafkaConstants {
    private KafkaConstants() {} // No instanciation.

    public static final String  CST_NONE            = "<none>";
    public static final String  CST_TOPIC_GROUP     = "APP-Kafka-Group";
    public static final String  CST_TOPIC_PROCESSOR = "APP-Kafka-Processor";

    // Explicit @KafkaListener id so the container can be looked up in the
    // KafkaListenerEndpointRegistry to be paused/resumed at runtime.
    public static final String  CST_LISTENER_PROCESSOR = "processor-listener";

    // Record header carrying the replay correlation id (one UUID per publication when the
    // "replay" option is enabled).
    public static final String  CST_HEADER_REPLAY_ID = "replay-id";
    public static final String  CST_TOPIC_FLINK_IN   = "APP-Flink-Input";
    public static final String  CST_TOPIC_FLINK_OUT  = "APP-Flink-Output";

    public static final int    CST_NBR_CLUSTERS_DEFAULT            = 1;   // NEVER change this value for this version.
    public static final int    CST_NBR_CLUSTERS_PARTITIONS_DEFAULT = 128; // Kafka cluster partitions.
    public static final int    CST_NBR_CONCURRENCY_DEFAULT         = 8;   // Specify the container concurrency.

    public static final int    CST_NBR_TOPICS_PARTITIONS = 8;     // Topic partitions.
    public static final short  CST_NBR_TOPICS_REPLICAS   = 1;     // Topic replicas.

    public static final int    CST_MAX_POLL_RECORDS = 10; // Max records per poll (bounds batch vs. max.poll.interval.ms).

    public static final long   CST_RETRY_INTERVAL = 250;  // Retry interval in milliseconds.
    public static final long   CST_RETRY_ATTEMPTS = 1;    // Number of retry attempts (1 retry = 2 attempts total).
    public static final long   CST_POLL_DURATION  = 500;  // Poll duration in milliseconds.

    public static final String CST_NO_DLT = "";           // Means no Dead Letter Topic.

    public static final String  CST_TRX_PREFIX                = "ks-trx-";
    public static final String  CST_TRX_NO_TRX_POSTFIX        = ":notrx";
    public static final String  CST_TRX_TRX_POSTFIX           = ":trx";
    public static final String  CST_SEPARATOR                 = "-";
    public static final String  CST_DEFAULT_DLT               = "default-dlt";
    public static final String  CST_TOPIC_DLT_POSTFIX         = "-dlt";
    public static final String  CST_ACKS_CONFIG               = "all";
    public static final String  CST_AUTO_OFFSET_RESET_CONFIG  = "earliest";
    public static final String  CST_ISOLATION_LEVEL_CONFIG    = "read_committed";
    public static final boolean CST_ENABLE_AUTO_COMMIT_CONFIG = false;

    public static final int CST_PARTITION_DLT             = -1; // Means kafka choice.    
    public static final int CST_LINGER_MS_CONFIG          = 1;
    public static final int CST_MAX_BLOCK_MS_CONFIG       = 5000;
    public static final int CST_REQUEST_TIMEOUT_MS_CONFIG = 2000;
    public static final int CST_RETRIES_CONFIG            = 5;
    public static final int CST_RETRY_BACKOFF_MS_CONFIG   = 100;

    public static final int CST_ADMIN_CLOSE_TIMEOUT_SECONDS = 5;
    public static final int CST_ADMIN_TIMEOUT_SECONDS       = 10; // Bound for blocking AdminClient futures.
    public static final int CST_POLL_TIMEOUT_MS             = 1;

    public static final String CST_CONSUMER_GROUP_MONITOR = "consumer-group-monitor";

    public static final List<String> CST_RESERVED_TOPIC_NAMES = List.of(
        "__consumer_offsets",
        "__transaction_state",
        "_schemas"
    );
}