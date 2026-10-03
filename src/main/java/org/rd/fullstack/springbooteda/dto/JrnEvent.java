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

import java.time.Instant;

import org.rd.fullstack.springbooteda.util.EventType;
import org.rd.fullstack.springbooteda.util.EventTypeConverter;
import org.rd.fullstack.springbooteda.util.Result;
import org.rd.fullstack.springbooteda.util.ResultConverter;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "jrn_event")
public class JrnEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "jrn_event_id", unique = true, nullable = false)
    private Long jrnEventId;
  
    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @Column(name = "consumer_id", nullable = false, length = 64)
    private String consumerId;

    @Column(name = "event_id", nullable = false, length = 64)
    private String eventId;

    @Column(name = "batch_id", nullable = false, length = 64)
    private String batchId;

    @Column(name = "payload_hash", nullable = false, length = 64)
    private String payloadHash;

    @Column(name = "event_type", nullable = false)
    @Convert(converter = EventTypeConverter.class)
    private EventType eventType;

    @Column(name = "result", nullable = false)
    @Convert(converter = ResultConverter.class)
    private Result result;

    @Column(name = "received_at", nullable = false)
    private Instant receivedAt;

    @Column(name = "processed_at")
    private Instant processedAt;

    public JrnEvent() {
        super();
        this.jrnEventId  = null;
        this.version     = null;
        this.consumerId  = null;
        this.eventId     = null;
        this.batchId     = null;
        this.payloadHash = null;
        this.eventType   = null;
        this.result      = null;
        this.receivedAt  = null;
        this.processedAt = null;
    }

    public JrnEvent(String consumerId, String eventId, String batchId,
                    String payloadHash, EventType eventType, Result result, 
                    Instant receivedAt, Instant processedAt) {
        this();
        this.consumerId  = consumerId;
        this.eventId     = eventId;
        this.batchId     = batchId;
        this.payloadHash = payloadHash;
        this.eventType   = eventType;
        this.result      = result;
        this.receivedAt  = receivedAt;
        this.processedAt = processedAt;
    }

    public Long getJrnEventId() {
        return this.jrnEventId;
    }

    public void setJrnEventId(Long jrnEventId) {
        this.jrnEventId = jrnEventId;
    }
    
    public Long getVersion() {
        return this.version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }
    
    public String getConsumerId() {
        return this.consumerId;
    }

    public void setConsumerId(String consumerId) {
        this.consumerId = consumerId;
    }

    public String getEventId() {
        return this.eventId;
    }

    public void setEventId(String eventId) {
        this.eventId = eventId;
    }

    public String getBatchId() {
        return this.batchId;
    }

    public void setBatchId(String batchId) {
        this.batchId = batchId;
    }

    public String getPayloadHash() {
        return this.payloadHash;
    }

    public void setPayloadHash(String payloadHash) {
        this.payloadHash = payloadHash;
    }

    public EventType getEventType() {
        return this.eventType;
    }

    public void setEventType(EventType eventType) {
        this.eventType = eventType;
    }

    public Result getResult() {
        return this.result;
    }

    public void setResult(Result result) {
        this.result = result;
    }

    public Instant getReceivedAt() {
        return this.receivedAt;
    }

    public void setReceivedAt(Instant receivedAt) {
        this.receivedAt = receivedAt;
    }

    public Instant getProcessedAt() {
        return this.processedAt;
    }

    public void setProcessedAt(Instant processedAt) {
        this.processedAt = processedAt;
    }

    public void setJrnEvent(JrnEvent jrnEvent) {
        this.jrnEventId  = jrnEvent.getJrnEventId();
        this.version     = jrnEvent.getVersion();
        this.consumerId  = jrnEvent.getConsumerId();
        this.eventId     = jrnEvent.getEventId();
        this.batchId     = jrnEvent.getBatchId();
        this.payloadHash = jrnEvent.getPayloadHash();
        this.eventType   = jrnEvent.getEventType();
        this.result      = jrnEvent.getResult();
        this.receivedAt  = jrnEvent.getReceivedAt();
        this.processedAt = jrnEvent.getProcessedAt();
    }

    @Override
    public String toString() {
        return "JrnEvent [jrnEventId=" + this.jrnEventId +
               ", consumerId=" + this.consumerId +
               ", eventId=" + this.eventId +
               ", batchId=" + this.batchId +
               ", payloadHash=" + this.payloadHash +
               ", eventType=" + this.eventType +
               ", result=" + this.result +
               ", receivedAt=" + String.valueOf(this.receivedAt) +
               ", processedAt=" + String.valueOf(this.processedAt) +
               ", version=" + this.version + "]";
    }
}
