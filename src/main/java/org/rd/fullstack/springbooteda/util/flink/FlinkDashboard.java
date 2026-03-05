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
package org.rd.fullstack.springbooteda.util.flink;

import java.util.List;

public record FlinkDashboard(
    ClusterInfo cluster,
    List<JobInfo> jobs,
    List<Notification> notifications
) {}

//GET /overview
// JOBS SUMMARY
//  "jobs-running": 0,
//  "jobs-finished": 0,
//  "jobs-cancelled": 0,
//  "jobs-failed": 0,
//GET /jobs/overview
//GET /jobs/{jobId}
//runningTasks = sum(vertices where status == RUNNING)
//totalTasks   = sum(vertices.parallelism)
//
//GET /jobs/{jobId}/exceptions
//GET /jobs/{jobId}/checkpoints
//
//if failed >= 3 → CRITICAL
//if failed > 0  → WARNING

