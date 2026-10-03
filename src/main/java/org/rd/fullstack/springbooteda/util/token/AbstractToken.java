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

abstract class AbstractToken<T extends Record> {

    private short type;
    private T payload;

    public AbstractToken() {
        super();
        this.type = 0; // Default - Unspecified token type.
        this.payload = null;
    }

    public short getType()            { return this.type; }
    public void setType(short type)   { this.type = type; }
    public T getPayload()             { return this.payload; }
    public void setPayload(T payload) { this.payload = payload; }

    public void validate() throws IllegalArgumentException {
        if (type < 0)
            throw new IllegalArgumentException("Invalid token type.");

        if (payload == null)
            throw new IllegalArgumentException("Invalid token payload.");
    }

    public abstract byte[] toBytes() throws UnsupportedOperationException;
    public abstract void fromBytes(byte[] bytes) throws IllegalArgumentException;
}
