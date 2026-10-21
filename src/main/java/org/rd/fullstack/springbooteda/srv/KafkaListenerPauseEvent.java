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

/**
 * Application event signalling that the Kafka processor listener should be paused
 * ({@code paused=true}) or resumed ({@code paused=false}).
 *
 * <p>Mirror of {@link FlinkPauseEvent} for the direct path. It decouples {@link PipelineSrv} (which
 * owns the pause flag and is triggered by the REST endpoint) from {@link KafkaPipelineListener}
 * (which owns the {@code @KafkaListener} container). {@code KafkaPipelineListener} already depends
 * on {@code PipelineSrv} for the shared per-record handling; routing the pause signal through an
 * event avoids a bidirectional bean dependency (a cycle).</p>
 */
public record KafkaListenerPauseEvent(boolean paused) {}
