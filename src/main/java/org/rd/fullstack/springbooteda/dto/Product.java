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

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "product")
public class Product {

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "product_id", unique = true, nullable = false)
    private Long productId;

    @Column(name = "code", nullable = false)
    private String code;

    @Column(name = "description", nullable = false)
    private String description;

    @Column(name = "price", nullable = false)
    private BigDecimal price;

    public Product() {
        super();
        this.version     = null;
        this.productId   = null;
        this.code        = null;
        this.description = null;
        this.price       = null;
    }

    public Product(String code, String description, BigDecimal price) {
        this();
        this.code = code;
        this.description = description;
        this.price = price;
    }

    public Long getVersion() {
        return this.version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }
    
    public Long getProductId() {
        return productId;
    }

    public void setProductId(Long productId) {
        this.productId = productId;
    }

    public String getCode() {
        return this.code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getDescription() {
        return this.description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public BigDecimal getPrice() {
        return this.price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    public void setProduct(Product product) {
        // productId and version are intentionally left untouched: productId is the managed
        // entity's primary key (already correct from findById) and version is maintained by
        // Hibernate's optimistic locking — neither must be overwritten by the update body.
        this.code        = product.getCode();
        this.description = product.getDescription();
        this.price       = product.getPrice();
    }

    @Override
    public String toString() {
        return "Product [productId=" + this.productId +
               ", code=" + this.code +
               ", description=" + this.description +
               ", price=" + String.valueOf(this.price) +
               ", version=" + this.version + "]";
    }
}