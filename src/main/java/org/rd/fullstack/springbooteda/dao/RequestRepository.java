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

import org.rd.fullstack.springbooteda.dto.Request;
import org.rd.fullstack.springbooteda.dto.RequestView;
import org.rd.fullstack.springbooteda.dto.RequestViewMapping;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RequestRepository extends JpaRepository<Request, Long> {
    @Query("""
        SELECT new org.rd.fullstack.springbooteda.dto.RequestView(
               req.requestId,
               per.personId,
               per.firstName,
               per.lastName,
               prod.productId,
               prod.code,
               prod.description,
               req.qty,
               req.operation, 
               req.result)
          FROM Request req
    INNER JOIN Person per   ON req.personId = per.personId
    INNER JOIN Product prod ON req.productId = prod.productId
         WHERE req.requestId = :requestId
           """)
    Optional<RequestView> findByRequestIdView(@Param("requestId") long requestId);


@Query(value = """
        SELECT req.request_id as requestId,
               per.person_id as personId,
               per.first_name as personFirstName,
               per.last_name as personLastName,
               prod.product_id as productId,
               prod.code as productCode,
               prod.description as productDescription,
               req.qty as qty,
               req.operation as intOperation,
          CASE WHEN req.operation = 10 THEN 'operation.credit'
               WHEN req.operation = 20 THEN 'operation.debit'
               WHEN req.operation = 97 THEN 'operation.refill'
               WHEN req.operation = 98 THEN 'operation.regenerate'
               WHEN req.operation = 99 THEN 'operation.error'
               ELSE 'operation.unknown'
           END as strOperation,
               req.result as intResult,
          CASE WHEN req.result = 10 THEN 'result.pending'
               WHEN req.result = 20 THEN 'result.back_order'
               WHEN req.result = 30 THEN 'result.executed'
               WHEN req.result = 99 THEN 'result.error'
               ELSE 'result.unknown'
           END as strResult
          FROM request req
          JOIN person per   ON req.person_id  = per.person_id
          JOIN product prod ON req.product_id = prod.product_id
         WHERE req.request_id = :requestId
               """, nativeQuery = true)
    Optional<RequestViewMapping> findByRequestIdViewNative(@Param("requestId") long requestId);

    @Query("""
        SELECT new org.rd.fullstack.springbooteda.dto.RequestView(
               req.requestId,
               per.personId,
               per.firstName,
               per.lastName,
               prod.productId,
               prod.code,
               prod.description,
               req.qty,
               req.operation, 
               req.result)
          FROM Request req
    INNER JOIN Person per   ON req.personId = per.personId
    INNER JOIN Product prod ON req.productId = prod.productId
           """)
    List<RequestView> findAllView();

@Query(value = """
        SELECT req.request_id as requestId,
               per.person_id as personId,
               per.first_name as personFirstName,
               per.last_name as personLastName,
               prod.product_id as productId,
               prod.code as productCode,
               prod.description as productDescription,
               req.qty as qty,
               req.operation as intOperation,
          CASE WHEN req.operation = 10 THEN 'operation.credit'
               WHEN req.operation = 20 THEN 'operation.debit'
               WHEN req.operation = 97 THEN 'operation.refill'
               WHEN req.operation = 98 THEN 'operation.regenerate'
               WHEN req.operation = 99 THEN 'operation.error'
               ELSE 'operation.unknown'
           END as strOperation,
               req.result as intResult,
          CASE WHEN req.result = 10 THEN 'result.pending'
               WHEN req.result = 20 THEN 'result.back_order'
               WHEN req.result = 30 THEN 'result.executed'
               WHEN req.result = 99 THEN 'result.error'
               ELSE 'result.unknown'
           END as strResult
          FROM request req
          JOIN person per   ON req.person_id  = per.person_id
          JOIN product prod ON req.product_id = prod.product_id
               """, nativeQuery = true)
    List<RequestViewMapping> findAllViewNative();
}