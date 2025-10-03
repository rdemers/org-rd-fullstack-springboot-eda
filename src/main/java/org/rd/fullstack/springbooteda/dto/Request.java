/*
 * Copyright 2025; Réal Demers.
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

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "request")
public class Request {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "request_id", unique = true, nullable = false)
    private Long requestId;

    // Foreign key relationship to product.
    // Not used ... But presented.
    // @ManyToOne
    // @JoinColumn(name = "person_id") // FK column.
    // private Person person;   

    @Column(name = "person_id", nullable = false)
    private Long personId;    

    // Foreign key relationship to product.
    // Not used ... But presented.
    // @ManyToOne
    // @JoinColumn(name = "product_id") // FK column.
    // private Product product;   

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "qty", nullable = false)
    private Long qty;

    @Column(name = "operation", nullable = false)
    private Long operation;

    @Column(name = "result", nullable = false)
    private Long result;

    public Request() {
        super();

        requestId  = null;
        personId   = null;
        productId  = null;
        qty        = null;
        operation  = null;
        result     = null;
    }

    public Request(Long personId,Long productId, Long qty,  Long operation, Long result) {
        this();
        this.personId   = personId;
        this.productId  = productId;
        this.qty        = qty;
        this.operation  = operation;
        this.result     = result;
    }
   
    public Long getRequestId() {
        return requestId;
    }

    public void setRequestId(Long requestId) {
        this.requestId = requestId;
    }

    public Long getPersonId() {
        return personId;
    }

    public void setPersonId(Long personId) {
        this.personId = personId;
    }

    public Long getProductId() {
        return productId;
    }

    public void setProductId(Long productId) {
        this.productId = productId;
    }

    public Long getQty() {
        return qty;
    }

    public void setQty(Long qty) {
        this.qty = qty;
    }

    public Long getOperation() {
        return operation;
    }

    public void setOperation(Long operation) {
        this.operation = operation;
    }

    public Long getResult() {
        return result;
    }

    public void setResult(Long result) {
        this.result = result;
    }

    @Override
    public String toString() {
        return super.toString() + 
               "Request [requestId=" + requestId + 
               ", productId=" + productId + 
               ", qty=" + qty + 
               ", operation=" + operation + 
               ", result=" + result + "]";
    }
}