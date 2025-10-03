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
@Table(name = "inventory")
public class Inventory {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "inventory_id", unique = true, nullable = false)
    private Long inventoryId;

    // Foreign key relationship to product.
    // Not used ... But presented.
    // @ManyToOne
    // @JoinColumn(name = "product_id") // FK column.
    // private Product product;   

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "qty", nullable = false)
    private Long qty;

    public Inventory() {
        super();

        inventoryId = null;
        productId   = null;
        qty         = null;
    }

    public Inventory(Long productId, Long qty) {
        this();
        this.productId = productId;
        this.qty = qty;
    }

    public Long getInventoryId() {
        return inventoryId;
    }

    public void setInventoryId(Long inventoryId) {
        this.inventoryId = inventoryId;
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

    @Override
    public String toString() {
        return super.toString() + 
               "Inventory [inventoryId=" + inventoryId + 
               ", productId=" + productId + 
               ", qty=" + qty + "]";
    }
}