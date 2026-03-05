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

import org.rd.fullstack.springbooteda.util.Operation;
import org.rd.fullstack.springbooteda.util.OperationConverter;
import org.rd.fullstack.springbooteda.util.Result;
import org.rd.fullstack.springbooteda.util.ResultConverter;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "request")
public class Request {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
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
    @Convert(converter = OperationConverter.class)
    private Operation operation;

    @Column(name = "result", nullable = false)
    @Convert(converter = ResultConverter.class)
    private Result result;

    public Request() {
        super();
        this.requestId  = null;
        this.personId   = null;
        this.productId  = null;
        this.qty        = null;
        this.operation  = null;
        this.result     = null;
    }

    public Request(Long personId,Long productId, Long qty,  Operation operation, Result result) {
        this();
        this.personId   = personId;
        this.productId  = productId;
        this.qty        = qty;
        this.operation  = operation;
        this.result     = result;
    }
   
    public Long getRequestId() {
        return this.requestId;
    }

    public void setRequestId(Long requestId) {
        this.requestId = requestId;
    }

    public Long getPersonId() {
        return this.personId;
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
        return this.qty;
    }

    public void setQty(Long qty) {
        this.qty = qty;
    }

    public Operation getOperation() {
        return this.operation;
    }

    public void setOperation(Operation operation) {
        this.operation = operation;
    }

    public Result getResult() {
        return this.result;
    }

    public void setResult(Result result) {
        this.result = result;
    }

    public void setRequest(Request majRequest) {
        this.requestId  = majRequest.getRequestId();
        this.personId   = majRequest.getPersonId();
        this.productId  = majRequest.getProductId();
        this.qty        = majRequest.getQty();
        this.operation  = majRequest.getOperation();
        this.result     = majRequest.getResult();
    }

    @Override
    public String toString() {
        return super.toString() + 
               "Request [requestId=" + this.requestId + 
               ", productId=" + this.productId + 
               ", qty=" + this.qty + 
               ", operation=" + this.operation + 
               ", result=" + this.result + "]";
    }
}