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
package org.rd.fullstack.springbooteda.dto;

import java.io.Serializable;

import com.fasterxml.jackson.annotation.JsonIgnore;

/**
 * Execution <strong>parameters</strong> of the pipeline (the user-controlled options).
 *
 * <p>Runtime counters and state were intentionally split out into {@link StatsContext}:
 * the parameters change rarely (set before a run) and are read once on the UI mount,
 * whereas the stats evolve continuously during a run and are polled. The two live in
 * separate distributed Hazelcast maps.</p>
 */
public class PipelineContext implements Serializable {

    // Explicit serialization contract. Pinning this value keeps deserialization stable
    // across cluster members (e.g. Hazelcast IMap, rolling upgrades): without it the JVM
    // derives the UID from the class structure, so any field/method change would break
    // compatibility with already-serialized instances. Bump it only on an intentional,
    // incompatible change to the serialized form.
    private static final long serialVersionUID = 3L;

    // Parameters.
    private Boolean pause;              // Pause the pipeline.
    private Boolean key;                // Use topic/kafka keys.
    private Boolean replay;             // Replay the pipeline requests.
    private Boolean latence;            // Add a fake latency.
    private Boolean flink;              // Route requests through the Flink pipeline.

    public PipelineContext() {
        super();
        reset();
    }

    public void reset() {
        this.pause   = false; // Do not pause the consummer topic by default (run continuously).
        this.key     = true;  // Use topic/kafka keys by default (ordered, per-product partitioning).
        this.replay  = false; // Replay the pipeline requests by default (no replay - Means empty JRN_EVENT).
        this.latence = false; // Add a fake latency by default (no latency).
        this.flink   = false; // Direct path by default (publish straight to the processor topic).
    }

    public Boolean getPause() {
        return pause;
    }

    public void setPause(Boolean pause) {
        this.pause = pause;
    }

    public Boolean getKey() {
        return key;
    }

    public void setKey(Boolean key) {
        this.key = key;
    }

    public Boolean getReplay() {
        return replay;
    }

    public void setReplay(Boolean replay) {
        this.replay = replay;
    }

    public Boolean getLatence() {
        return latence;
    }

    public void setLatence(Boolean latence) {
        this.latence = latence;
    }

    public Boolean getFlink() {
        return flink;
    }

    public void setFlink(Boolean flink) {
        this.flink = flink;
    }

    @JsonIgnore // Ignore this method during JSON serialization/deserialization.
    public void setPipelineContext(PipelineContext pipelineContext) {
        this.pause   = pipelineContext.getPause();
        this.key     = pipelineContext.getKey();
        this.replay  = pipelineContext.getReplay();
        this.latence = pipelineContext.getLatence();
        this.flink   = pipelineContext.getFlink();
    }

    @Override
    public String toString() {
        return "PipelineContext [pause=" + this.pause +
            ", key="                  + this.key +
            ", replay="               + this.replay +
            ", latence="              + this.latence +
            ", flink="                + this.flink + "]";
    }
}
