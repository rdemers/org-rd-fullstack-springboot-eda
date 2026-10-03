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
package org.rd.fullstack.springbooteda.util.token;

import java.nio.ByteBuffer;

public class EventToken extends AbstractToken<EventTokenPayload> {
     
    private static final short CST_TYPE = 1;       // Token type for EventToken.
    private static final short CST_TOTAL_LEN = 15; // 2(type) + 1(envId) + 2(systemId) + 2(srvId) + 8(requestId)

    public EventToken() {
        super();
        setType(CST_TYPE);
    }

    @Override
    public void validate() throws IllegalArgumentException {

        super.validate();
        EventTokenPayload payload = getPayload();

        if (payload.envId() < 0)
            throw new IllegalArgumentException("Invalid envId in EventToken payload.");

        if (payload.systemId() < 0)
            throw new IllegalArgumentException("Invalid systemId in EventToken payload.");

        if (payload.srvId() < 0)
            throw new IllegalArgumentException("Invalid srvId in EventToken payload.");

        if (payload.requestId() < 0)
            throw new IllegalArgumentException("Invalid requestId in EventToken payload.");
    }

    @Override
    public byte[] toBytes() throws UnsupportedOperationException{

        try {
            validate();
        } catch (IllegalArgumentException e) {
            throw new UnsupportedOperationException("Invalid EventToken: " + e.getMessage());
        }

        EventTokenPayload payload = getPayload();
        ByteBuffer buffer = ByteBuffer.allocate(CST_TOTAL_LEN);

        // The array length MUST BE a multiple of 5.
        buffer.putShort(getType());          // 2 bytes.
        buffer.put(payload.envId());         // 1 byte.
        buffer.putShort(payload.systemId()); // 2 bytes.
        buffer.putShort(payload.srvId());    // 2 bytes.
        buffer.putLong(payload.requestId()); // 8 bytes.
                                             // Total length = 15 bytes. 
        return buffer.array();
    }

    @Override
    public void fromBytes(byte[] bytes) throws IllegalArgumentException {

        if (bytes == null || bytes.length < CST_TOTAL_LEN)
            throw new IllegalArgumentException("Invalid byte array length for EventToken.");

        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        short type = buffer.getShort(); 

        if (type != getType())
            throw new IllegalArgumentException("Invalid token type in EventToken.");

        byte envId     = buffer.get();
        short systemId = buffer.getShort(); 
        short srvId    = buffer.getShort();
        long requestId = buffer.getLong();
        setPayload(new EventTokenPayload(envId, systemId, srvId, requestId));
        validate();
    }
 }
