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
package org.rd.fullstack.springbooteda.dao;

import java.util.List;
import java.util.Optional;

import org.rd.fullstack.springbooteda.dto.Inventory;
import org.rd.fullstack.springbooteda.dto.InventoryView;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InventoryRepository extends JpaRepository<Inventory, Long> {
    Optional<Inventory> findByProductId(Long productId);

    @Query("""
              SELECT new org.rd.fullstack.springbooteda.dto.InventoryView(
                     inv.inventoryId, inv.productId, inv.qty, 
                     prod.code, prod.description)
                FROM Inventory inv
          INNER JOIN Product prod ON inv.productId = prod.productId
               WHERE inv.inventoryId = :inventoryId
          """)
    Optional<InventoryView> findByInventoryIdView(@Param("inventoryId") long inventoryId); 

    @Query("""
              SELECT inv 
                FROM Inventory inv 
          INNER JOIN Product prd ON inv.productId = prd.productId 
               WHERE prd.code = :productCode
          """)
    List<Inventory> findByProductCode(@Param("productCode") String productCode);

    @Query("""
              SELECT new org.rd.fullstack.springbooteda.dto.InventoryView(
                     inv.inventoryId, inv.productId, inv.qty, 
                     prod.code, prod.description)
                FROM Inventory inv
          INNER JOIN Product prod ON inv.productId = prod.id
           """)
    List<InventoryView> findAllView();

    @Modifying
    @Query (""" 
               UPDATE Inventory inv 
                  SET inv.qty = (inv.qty + :qty) 
                WHERE inv.inventoryId = :id
            """)
    int debitQTY(@Param("qty") Long qty, @Param("id") Long id);

    @Modifying
    @Query (""" 
               UPDATE Inventory inv 
                  SET inv.qty = (inv.qty - :qty) 
                WHERE inv.inventoryId = :id
            """)
    int creditQTY(@Param("qty") Long qty, @Param("id") Long id);
}