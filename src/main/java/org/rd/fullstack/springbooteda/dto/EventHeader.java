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

public class EventHeader {
    private String eventId;
    private String version;
    private String batchId;
    private String replayId;

    public EventHeader() {
        super();
        this.eventId = null;
        this.version = null;
        this.batchId = null;
        this.replayId = null;
    }

    public EventHeader(String eventId, String version, String batchId, String replayId) {
        this.eventId  = eventId;
        this.version  = version;
        this.batchId  = batchId;
        this.replayId = replayId;
    }

    // Getters and Setters
    public String getEventId() {
        return eventId;
    }

    public void setEventId(String eventId) {
        this.eventId = eventId;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getBatchId() {
        return batchId;
    }

    public void setBatchId(String batchId) {
        this.batchId = batchId;
    }

    public String getReplayId() {
        return replayId;
    }

    public void setReplayId(String replayId) {
        this.replayId = replayId;
    }

    @Override
    public String toString() {
        return "EventHeader [" +
                "eventId=" + eventId +
                ", version=" + version +
                ", batchId=" + batchId + 
                ", replayId=" + replayId + "]";
    }
}
