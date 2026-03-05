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

public class KafkaConstant {
    private KafkaConstant() {} // No instanciation.

    public static final int    CST_NBR_CLUSTERS            = 1;   // NEVER change this value for this version.
    public static final int    CST_NBR_CLUSTERS_PARTITIONS = 10;  // Kafka cluster partitions.
    public static final int    CST_NBR_CONCURRENCY         = 3;   // Specify the container concurrency.

    public static final int    CST_NBR_PARTITIONS = 5;    // Topic partitions.
    public static final short  CST_NBR_REPLICAS   = 1;    // Topic replicas.

    public static final long   CST_RETRY_INTERVAL = 1000; // Retry interval in milliseconds.
    public static final long   CST_RETRY_ATTEMPTS = 3;    // Number of retry attempts.
    public static final long   CST_POOL_DURATION  = 500;  // Pool duration in milliseconds.

    public static final String CST_NO_DLT = "";           // No Dead Letter Topic.

    public static final String  CST_TRX_PREFIX                = "ks-trx-";
    public static final String  CST_TRX_NO_TRX_POSTFIX        = ":notrx";
    public static final String  CST_TRX_TRX_POSTFIX           = ":trx";
    public static final String  CST_SEPARATOR                 = "-";
    public static final String  CST_DEFAULT_DLT               = "default-dlt";
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
    public static final int CST_POLL_TIMEOUT_MS             = 1;

    public static final List<String> CST_RESERVED_TOPIC_NAMES = List.of(
        "__consumer_offsets",
        "__transaction_state",
        "_schemas"
    );
}