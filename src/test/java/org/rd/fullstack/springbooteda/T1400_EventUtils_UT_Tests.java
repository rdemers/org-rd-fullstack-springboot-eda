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
package org.rd.fullstack.springbooteda;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import com.fasterxml.jackson.core.type.TypeReference;

import org.rd.fullstack.springbooteda.config.Application;
import org.rd.fullstack.springbooteda.dto.EventFooter;
import org.rd.fullstack.springbooteda.dto.EventHeader;
import org.rd.fullstack.springbooteda.dto.EventMessage;
import org.rd.fullstack.springbooteda.dto.Request;
import org.rd.fullstack.springbooteda.util.JsonMapper;
import org.rd.fullstack.springbooteda.util.Operation;
import org.rd.fullstack.springbooteda.util.Result;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.context.SpringBootTest;

/*
 * See POM.XML file
 * - Plugins section: maven-surefire-plugin
 * - Unit tests VS integrated tests.
 */
@SpringBootTest(classes = Application.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("Testing the tool for Event management.")
public class T1400_EventUtils_UT_Tests {
    private static final Logger logger = LoggerFactory.getLogger(T1400_EventUtils_UT_Tests.class);

    // Fictitious data used across the tests below.
    private static final String EVENT_ID  = "EVT-0001";
    private static final String VERSION   = "1.0";
    private static final String BATCH_ID  = "BATCH-2026-08-21";
    private static final String REPLAY_ID = "REPLAY-001";
    private static final String SIGNATURE = "SIGN-ABC123XYZ";
    private static final String PAYLOAD   = "Fictitious payload content for testing purposes.";

    @Test
    @Order(1)
    public void evtHeader_defaultConstructor() throws Exception {
        EventHeader header = new EventHeader();

        assertNull(header.getEventId(), "The eventId should be null by default.");
        assertNull(header.getVersion(), "The version should be null by default.");
        assertNull(header.getBatchId(), "The batchId should be null by default.");
        assertNull(header.getReplayId(), "The replayId should be null by default.");
        logger.info("EventHeader default constructor initializes all fields to null.");
    }

    @Test
    @Order(2)
    public void evtHeader_parameterizedConstructorAndAccessors() throws Exception {
        EventHeader header = new EventHeader(EVENT_ID, VERSION, BATCH_ID, REPLAY_ID);

        assertEquals(EVENT_ID, header.getEventId(), "The eventId should match the value provided to the constructor.");
        assertEquals(VERSION, header.getVersion(), "The version should match the value provided to the constructor.");
        assertEquals(BATCH_ID, header.getBatchId(), "The batchId should match the value provided to the constructor.");
        assertEquals(REPLAY_ID, header.getReplayId(), "The replayId should match the value provided to the constructor.");
        logger.info("EventHeader parameterized constructor sets all fields correctly.");
    }

    @Test
    @Order(3)
    public void evtHeader_setters() throws Exception {
        EventHeader header = new EventHeader();
        header.setEventId(EVENT_ID);
        header.setVersion(VERSION);
        header.setBatchId(BATCH_ID);
        header.setReplayId(REPLAY_ID);

        assertEquals(EVENT_ID, header.getEventId(), "The eventId should match the value set through the setter.");
        assertEquals(VERSION, header.getVersion(), "The version should match the value set through the setter.");
        assertEquals(BATCH_ID, header.getBatchId(), "The batchId should match the value set through the setter.");
        assertEquals(REPLAY_ID, header.getReplayId(), "The replayId should match the value set through the setter.");
        logger.info("EventHeader setters update all fields correctly.");
    }

    @Test
    @Order(4)
    public void evtFooter_defaultConstructor() throws Exception {
        EventFooter footer = new EventFooter();

        assertNull(footer.getSignature(), "The signature should be null by default.");
        logger.info("EventFooter default constructor initializes the signature to null.");
    }

    @Test
    @Order(5)
    public void evtFooter_parameterizedConstructorAndAccessors() throws Exception {
        EventFooter footer = new EventFooter(SIGNATURE);

        assertEquals(SIGNATURE, footer.getSignature(), "The signature should match the value provided to the constructor.");
        logger.info("EventFooter parameterized constructor sets the signature correctly.");
    }

    @Test
    @Order(6)
    public void evtFooter_setter() throws Exception {
        EventFooter footer = new EventFooter();
        footer.setSignature(SIGNATURE);

        assertEquals(SIGNATURE, footer.getSignature(), "The signature should match the value set through the setter.");
        logger.info("EventFooter setter updates the signature correctly.");
    }

    @Test
    @Order(7)
    public void evtMessage_defaultConstructor() throws Exception {
        EventMessage<EventHeader, String, EventFooter> message = new EventMessage<>();

        assertNull(message.getHeader(), "The header should be null by default.");
        assertNull(message.getPayload(), "The payload should be null by default.");
        assertNull(message.getFooter(), "The footer should be null by default.");
        logger.info("EventMessage default constructor initializes all fields to null.");
    }

    @Test
    @Order(8)
    public void evtMessage_parameterizedConstructorAndAccessors() throws Exception {
        EventHeader header = new EventHeader(EVENT_ID, VERSION, BATCH_ID, REPLAY_ID);
        EventFooter footer = new EventFooter(SIGNATURE);
        EventMessage<EventHeader, String, EventFooter> message = new EventMessage<>(header, PAYLOAD, footer);

        assertEquals(header, message.getHeader(), "The header should match the value provided to the constructor.");
        assertEquals(PAYLOAD, message.getPayload(), "The payload should match the value provided to the constructor.");
        assertEquals(footer, message.getFooter(), "The footer should match the value provided to the constructor.");
        logger.info("EventMessage parameterized constructor sets all fields correctly.");
    }

    @Test
    @Order(9)
    public void evtMessage_setters() throws Exception {
        EventHeader header = new EventHeader(EVENT_ID, VERSION, BATCH_ID, REPLAY_ID);
        EventFooter footer = new EventFooter(SIGNATURE);
        EventMessage<EventHeader, String, EventFooter> message = new EventMessage<>();
        message.setHeader(header);
        message.setPayload(PAYLOAD);
        message.setFooter(footer);

        assertEquals(header, message.getHeader(), "The header should match the value set through the setter.");
        assertEquals(PAYLOAD, message.getPayload(), "The payload should match the value set through the setter.");
        assertEquals(footer, message.getFooter(), "The footer should match the value set through the setter.");
        logger.info("EventMessage setters update all fields correctly.");
    }

    @Test
    @Order(10)
    public void evtMessage_PayloadJsonString() throws Exception {
        EventHeader header = new EventHeader(EVENT_ID, VERSION, BATCH_ID, REPLAY_ID);
        EventFooter footer = new EventFooter(SIGNATURE);
        EventMessage<EventHeader, String, EventFooter> message = new EventMessage<>();

        message.setHeader(header);
        message.setPayload(PAYLOAD);
        message.setFooter(footer);

        final String jSonReq = JsonMapper.writeToJson(message);
        assertNotNull(jSonReq, "The JSON representation should not be null.");
        logger.info("Serialized EventMessage to JSON: {}", jSonReq);

        EventMessage<EventHeader, String, EventFooter> deserializedMessage = 
            JsonMapper.readFromJson(jSonReq, new 
                TypeReference<EventMessage<EventHeader, String, EventFooter>>() {});

        assertEquals(header.toString(), deserializedMessage.getHeader().toString(), "The header should match the value.");
        assertEquals(PAYLOAD, deserializedMessage.getPayload(), "The payload should match the value.");
        assertEquals(footer.toString(), deserializedMessage.getFooter().toString(), "The footer should match the value.");
        logger.info("EventMessage deserialized all fields correctly.");
    }

    @Test
    @Order(11)
    public void evtMessage_PayloadJsonRequest() throws Exception {

        // Long personId,Long productId, Long qty,  Operation operation, Result result
        Request request = new Request(10L, 20L, 30L, 
            Operation.CREDIT, Result.PENDING);

        EventHeader header = new EventHeader(EVENT_ID, VERSION, BATCH_ID, REPLAY_ID);
        EventFooter footer = new EventFooter(SIGNATURE);
        EventMessage<EventHeader, Request, EventFooter> message = new EventMessage<>();

        message.setHeader(header);
        message.setPayload(request);
        message.setFooter(footer);

        final String jSonReq = JsonMapper.writeToJson(message);
        assertNotNull(jSonReq, "The JSON representation should not be null.");
        logger.info("Serialized EventMessage to JSON: {}", jSonReq);

        EventMessage<EventHeader, Request, EventFooter> deserializedMessage = 
            JsonMapper.readFromJson(jSonReq, new 
                TypeReference<EventMessage<EventHeader, Request, EventFooter>>() {});

        assertEquals(header.toString(), deserializedMessage.getHeader().toString(), "The header should match the value.");
        assertEquals(request.toString(), deserializedMessage.getPayload().toString(), "The payload should match the value.");
        assertEquals(footer.toString(), deserializedMessage.getFooter().toString(), "The footer should match the value.");
        logger.info("EventMessage deserialized all fields correctly.");
    }
}