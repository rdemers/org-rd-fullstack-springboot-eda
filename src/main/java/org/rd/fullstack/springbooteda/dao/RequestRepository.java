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
import org.rd.fullstack.springbooteda.dto.RequestCount;
import org.rd.fullstack.springbooteda.dto.RequestView;
import org.rd.fullstack.springbooteda.util.Result;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
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

        @Query("""
        SELECT new org.rd.fullstack.springbooteda.dto.RequestCount(
              SUM(CASE WHEN req.result = 10 
                  THEN 1 ELSE 0 END) AS nbrPending,
              SUM(CASE WHEN req.result = 20 
                  THEN 1 ELSE 0 END) AS nbrBackOrder,
              SUM(CASE WHEN req.result = 30 
                  THEN 1 ELSE 0 END) AS nbrExecuted,
              SUM(CASE WHEN req.result = 99 
                  THEN 1 ELSE 0 END) AS nbrError,
              SUM(CASE WHEN req.result NOT IN (10,20,30,99) OR req.result IS NULL
                  THEN 1 ELSE 0 END) AS nbrUnknown)
          FROM Request req
           """)
    RequestCount countRequest();

    @Modifying(clearAutomatically = true)
    @Query("""
               UPDATE Request req
                  SET req.result = :result
            """)
    int resetAllResults(@Param("result") Result result);
}