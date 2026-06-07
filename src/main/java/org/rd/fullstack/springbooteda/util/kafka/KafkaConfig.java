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

import java.util.Objects;
import java.util.UUID;

public record KafkaConfig(
    UUID    uuidID,
    int     clusters,
    int     clusterPartitions,
    int     concurrency,
    boolean autoCreateTopic
) {
    public KafkaConfig {
        Objects.requireNonNull(uuidID, "uuidID must not be null.");

        // clusters is currently always 1 (see Builder.clusters() @Deprecated).
        if (clusters <= 0)
            throw new IllegalArgumentException("clusters must be > 0.");

        if (clusterPartitions <= 0)
            throw new IllegalArgumentException("clusterPartitions must be > 0.");

        if (concurrency <= 0)
            throw new IllegalArgumentException("concurrency must be > 0.");
    }
}