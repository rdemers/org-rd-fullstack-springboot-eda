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

import org.rd.fullstack.springbooteda.util.PipelineState;

/**
 * Runtime <strong>statistics and state</strong> of the pipeline, split out of
 * {@link PipelineContext}. Stored in its own distributed Hazelcast map and refreshed by
 * the UI on every poll (whereas the parameters in {@code PipelineContext} are read once).
 *
 * <p>The counters are plain {@code int}: thread-safety does not come from
 * {@code AtomicInteger} but from the cluster-wide Hazelcast key lock under which every
 * read-modify-write is performed (see {@code PipelineSrv.mutate(...)}). A bulk
 * {@code AtomicInteger} would only protect a single JVM and would not survive the
 * deserialize-copy / write-back cycle of an {@code IMap} anyway.</p>
 */
public class StatsContext implements Serializable {

    private static final long serialVersionUID = 1L;

    // Counters.
    private int nbrPublished;          // The number of requests published.
    private int nbrProcessed;          // The number of requests processed.
    private int nbrProcessedWithError; // The number of requests processed with error.

    // State.
    private PipelineState pipelineState; // The state of the pipeline.
    private String exceptionMSG;         // The exception message.

    public StatsContext() {
        super();
        reset();
    }

    public void reset() {
        this.nbrPublished          = 0;
        this.nbrProcessed          = 0;
        this.nbrProcessedWithError = 0;
        this.pipelineState         = PipelineState.READY;
        this.exceptionMSG          = null;
    }

    public int incrementNbrProcessed() {
        return ++this.nbrProcessed;
    }

    public int incrementNbrProcessedWithError() {
        return ++this.nbrProcessedWithError;
    }

    public int getNbrPublished() {
        return nbrPublished;
    }

    public void setNbrPublished(int nbrPublished) {
        this.nbrPublished = nbrPublished;
    }

    public int getNbrProcessed() {
        return nbrProcessed;
    }

    public void setNbrProcessed(int nbrProcessed) {
        this.nbrProcessed = nbrProcessed;
    }

    public int getNbrProcessedWithError() {
        return nbrProcessedWithError;
    }

    public void setNbrProcessedWithError(int nbrProcessedWithError) {
        this.nbrProcessedWithError = nbrProcessedWithError;
    }

    public PipelineState getPipelineState() {
        return pipelineState;
    }

    public void setPipelineState(PipelineState pipelineState) {
        this.pipelineState = pipelineState;
    }

    public String getExceptionMSG() {
        return exceptionMSG;
    }

    public void setExceptionMSG(String exceptionMSG) {
        this.exceptionMSG = exceptionMSG;
    }

    @Override
    public String toString() {
        return super.toString() +
            "StatsContext [nbrPublished=" + this.nbrPublished +
            ", nbrProcessed="             + this.nbrProcessed +
            ", nbrProcessedWithError="    + this.nbrProcessedWithError +
            ", pipelineState="            + this.pipelineState +
            ", exceptionMSG="             + this.exceptionMSG + "]";
    }
}
