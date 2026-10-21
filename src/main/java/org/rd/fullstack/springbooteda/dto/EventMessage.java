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

public class EventMessage<T1, T2, T3> {
    private T1 header;
    private T2 payload;
    private T3 footer;

    public EventMessage() {
        super();
        header  = null;
        payload = null;
        footer  = null;
    }

    public EventMessage(T1 header, T2 payload, T3 footer) {
        this.header  = header;
        this.payload = payload;
        this.footer  = footer;
    }

    public T1 getHeader() {
        return header;
    }

    public void setHeader(T1 header) {
        this.header = header;
    }

    public T2 getPayload() {
        return payload;
    }

    public void setPayload(T2 payload) {
        this.payload = payload;
    }

    public T3 getFooter() {
        return footer;
    }

    public void setFooter(T3 footer) {
        this.footer = footer;
    }

    @Override
    public String toString() {
        return "EventMessage [header=" + header + 
            ", payload=" + payload.toString() + ", footer=" + footer.toString() + "]";
    }
}
