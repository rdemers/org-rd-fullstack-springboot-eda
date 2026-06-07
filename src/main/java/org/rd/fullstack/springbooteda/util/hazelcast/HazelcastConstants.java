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

package org.rd.fullstack.springbooteda.util.hazelcast;

public class HazelcastConstants {

    private HazelcastConstants() {} // No instanciation.

    public static final String CST_MAPNAME_CTX          = "MAP-CTX";
    public static final String CST_MAPNAME_STATS        = "MAP-STATS";
    public static final String CST_MAPNAME_CLIENT_LOCKS = "MAP-CLIENT-LOCKS";

    // Well-known key under which the (single) distributed pipeline context is stored
    // in the CST_MAPNAME_CTX map.
    public static final String CST_KEY_PIPELINE_CONTEXT = "PIPELINE-CONTEXT";

    // Well-known key under which the (single) distributed pipeline stats are stored
    // in the CST_MAPNAME_STATS map.
    public static final String CST_KEY_PIPELINE_STATS = "PIPELINE-STATS";
}