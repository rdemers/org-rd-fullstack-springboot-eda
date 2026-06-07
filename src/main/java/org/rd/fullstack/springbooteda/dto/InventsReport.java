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

import java.util.List;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;

@JsonPropertyOrder({ "header", "body", "footer" })
public class InventsReport {

    final private List<?> header;

    @JacksonXmlElementWrapper(localName = "body")
    final private List<InventoryView> body;
    
    @JacksonXmlElementWrapper(localName = "footer")
    final private List<?> footer;

	  public InventsReport(List<?> header, List<InventoryView> body, List<?> footer) {
        super();
        this.header = header;
        this.body   = body;
        this.footer = footer;
    }

    @JacksonXmlElementWrapper(useWrapping = false)
    public List<?> getHeader() {
        return header;
    }

    @JacksonXmlProperty(localName = "inventory")
    public List<InventoryView> getBody() {
        return body;
    }

    @JacksonXmlElementWrapper(useWrapping = false)
    public List<?> getFooter() {
        return footer;
    }   
}