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
 *
 * Concurrency and retry strategy
 * ==============================
 * The four concurrency-aware methods above deliberately stop at the database
 * boundary. They detect a conflict, but they do not decide how the application
 * should recover from it.
 *
 * For example, an optimistic update can return `0` because another transaction
 * modified the same inventory row between the initial read and the attempted UPDATE.
 *
 * The application layer must then choose an appropriate strategy:
 *
 *  1. Re-read the inventory and retry the operation.
 *  2. Retry a limited number of times.
 *  3. Throw a dedicated concurrency exception.
 *  4. Propagate the exception to the Kafka listener so that the Kafka error
 *      handling mechanism can perform the retry.
 *
 * A retry loop should normally be bounded. An unbounded loop can transform a
 * temporary contention problem into sustained database load.
 *
 * Kafka interaction
 * =================
 * When these repository methods are called from a Kafka listener, a concurrency
 * conflict can be propagated as an exception to the listener.
 *
 * Spring Kafka's `DefaultErrorHandler` can then retry the record according to
 * its configured `BackOff`. For example:
 *
 *  new FixedBackOff(1000L, 2L)
 *
 * means two retries after the initial delivery, for a maximum of three delivery
 * attempts in total. After the configured retries are exhausted, a DeadLetterPublishingRecoverer
 * can publish the failed record to a Dead Letter Topic (DLT). 
 *   
 * The exact behavior is configuration-dependent and should not be assumed from Kafka alone.
 *
 * The DLT should therefore be considered a recovery mechanism for messages that
 * could not be successfully processed within the configured retry policy, notas a substitute 
 * for concurrency control.
 *
 * High-concurrency considerations
 * ===============================
 * Optimistic locking does not eliminate database contention.
 * Consider several dozen Kafka consumer threads processing messages that modify
 * the same inventory record:
 *
 *  Thread 01 ----\
 *  Thread 02 -----\
 *  Thread 03 ------> Inventory #42
 *  ...       -----/
 *  Thread 99 ----/
 *
 * The database still has to serialize conflicting modifications to the same row. 
 * Optimistic locking changes how the application reacts to the conflict. instead of waiting 
 * indefinitely or silently overwriting a previously-read state, the application detects that its
 * expected version or quantity is no longer current.
 *
 * High concurrency wil therefore produce a combination of:
 *  - database row contention;
 *  - lock waits;
 *  - increased transaction duration;
 *  - increased CPU and I/O utilization;
 *  - more failed optimistic updates;
 *  - additional database reads caused by retries;
 *  - additional Kafka processing attempts;
 *  - increased end-to-end latency.
 *
 * Increasing Kafka consumer concurrency does not automatically increase useful
 * throughput. If many messages target the same database rows, the database can
 * become the limiting resource.
 *
 * Spring Kafka supports concurrent listener containers, with multiple consumer
 * threads processing records concurrently. Listener instances must consequently
 * be designed to be thread-safe.
 *
 * The retry amplification effect
 * ==============================
 * Retries are particularly important when analyzing a high-contention workload.
 * Suppose 30 concurrent messages target the same inventory row. If many of them
 * read the same version, only one can successfully perform the corresponding
 * optimistic UPDATE. The others can detect a version conflict.
 *
 * If every failed operation is immediately retried, those retries create new
 * database work. This can create a positive feedback loop where contention causes 
 * retries and retries create additional contention.
 *
 * For that reason, retry policies should generally consider:
 *  - maximum retry attempts;
 *  - back-off duration;
 *  - exponential back-off;
 *  - maximum back-off;
 *  - the type of exception being retried;
 *  - the expected contention level;
 *  - database capacity;
 *  - Kafka consumer concurrency.
 *
 * Spring Kafka supports configurable back-off policies for error handling,
 * including fixed and exponential back-off strategies.
 *
 * SAAS cost considerations
 * ========================
 * In a SAAS environment, high contention can have a direct or indirect cost
 * impact. The important point is not that "concurrency automatically costs more", but
 * that excessive concurrency and retries can consume additional infrastructure
 * capacity. For example:
 *
 * - Higher CPU / I/O / connection usage
 * - Potentially larger database instance additional application capacity
 * - Higher infrastructure cost (EKS scaling, RDS scaling, etc.)
 *
 * Depending on the cloud provider and service model, additional consumption can
 * translate into higher costs for database capacity, compute, I/O, storage,
 * network traffic, or provisioned resources.
 *
 * Consequently, blindly increasing Kafka consumer concurrency is not necessarily
 * a cost-effective way to improve throughput.
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
    Optional<InventoryView> findByInventoryIdView(
            @Param("inventoryId") long inventoryId);

    @Query("""
              SELECT inv
                FROM Inventory inv
          INNER JOIN Product prd ON inv.productId = prd.productId
               WHERE prd.code = :productCode
          """)
    List<Inventory> findByProductCode(
            @Param("productCode") String productCode);

    @Query("""
              SELECT new org.rd.fullstack.springbooteda.dto.InventoryView(
                     inv.inventoryId, inv.productId, inv.qty,
                     prod.code, prod.description)
                FROM Inventory inv
          INNER JOIN Product prod ON inv.productId = prod.productId
          """)
    List<InventoryView> findAllView();

    /*
     * ========================================================================
     * Direct inventory updates
     * ========================================================================
     *
     * These methods intentionally perform a direct database update without
     * optimistic concurrency control.
     *
     * They are useful as a baseline for demonstrating the behavior of
     * concurrent updates.
     *
     * IMPORTANT:
     * These methods do not verify whether another transaction modified the
     * inventory between the caller's read and this update.
     *
     * The returned integer is the number of rows affected by the UPDATE.
     */

    /**
     * Increases the inventory quantity directly.
     *
     * <p>No optimistic concurrency check is performed.</p>
     *
     * @param qty quantity to add
     * @param id inventory identifier
     * @return number of rows updated
     */
    @Modifying
    @Query("""
               UPDATE Inventory inv
                  SET inv.qty = (inv.qty + :qty)
                WHERE inv.inventoryId = :id
           """)
    int debitQTY(
            @Param("qty") Long qty,
            @Param("id") Long id);

    /**
     * Decreases the inventory quantity directly.
     *
     * <p>No optimistic concurrency check is performed.</p>
     *
     * @param qty quantity to subtract
     * @param id inventory identifier
     * @return number of rows updated
     */
    @Modifying
    @Query("""
               UPDATE Inventory inv
                  SET inv.qty = (inv.qty - :qty)
                WHERE inv.inventoryId = :id
           """)
    int creditQTY(
            @Param("qty") Long qty,
            @Param("id") Long id);

    /*
     * ========================================================================
     * Optimistic concurrency control using the version column
     * ========================================================================
     *
     * These methods implement application-level optimistic locking.
     *
     * The caller must first read the inventory, including its current version.
     * The UPDATE is then executed only if the version in the database is still
     * equal to the version previously read by the caller.
     *
     * The version is incremented as part of the successful UPDATE.
     *
     * A return value of:
     *
     *     1 -> update succeeded
     *     0 -> optimistic concurrency conflict
     *
     * The caller is responsible for deciding what to do after a conflict.
     * Typical strategies include:
     *
     *     - re-read the inventory and retry the operation;
     *     - retry with a bounded number of attempts;
     *     - propagate a concurrency exception;
     *     - allow the Kafka error-handling mechanism to retry the message.
     *
     * IMPORTANT:
     * These methods do not automatically retry.
     *
     * ========================================================================
     */

    /**
     * Increases the inventory quantity using optimistic locking.
     *
     * <p>The update succeeds only when the supplied version still matches
     * the version stored in the database.</p>
     *
     * <p>On success, the version is incremented atomically with the quantity
     * update.</p>
     *
     * @param qty quantity to add
     * @param id inventory identifier
     * @param version version previously read by the caller
     * @return 1 if the update succeeded; 0 if a concurrency conflict occurred
     */
    @Modifying
    @Query("""
               UPDATE Inventory inv
                  SET inv.qty = (inv.qty + :qty),
                      inv.version = (inv.version + 1)
                WHERE inv.inventoryId = :id
                  AND inv.version = :version
           """)
    int debitQTYOptimistic(
            @Param("qty") Long qty,
            @Param("id") Long id,
            @Param("version") Long version);

    /**
     * Decreases the inventory quantity using optimistic locking.
     *
     * <p>The update succeeds only when the supplied version still matches
     * the version stored in the database.</p>
     *
     * <p>On success, the version is incremented atomically with the quantity
     * update.</p>
     *
     * @param qty quantity to subtract
     * @param id inventory identifier
     * @param version version previously read by the caller
     * @return 1 if the update succeeded; 0 if a concurrency conflict occurred
     */
    @Modifying
    @Query("""
               UPDATE Inventory inv
                  SET inv.qty = (inv.qty - :qty),
                      inv.version = (inv.version + 1)
                WHERE inv.inventoryId = :id
                  AND inv.version = :version
           """)
    int creditQTYOptimistic(
            @Param("qty") Long qty,
            @Param("id") Long id,
            @Param("version") Long version);

    /*
     * ========================================================================
     * Optimistic concurrency control using the previously-read quantity
     * ========================================================================
     *
     * These methods demonstrate an alternative optimistic concurrency
     * technique without using the version column.
     *
     * The caller supplies the quantity that it previously read.
     *
     * The UPDATE succeeds only when the quantity currently stored in the
     * database is still equal to the quantity supplied by the caller.
     *
     * A return value of:
     *
     *     1 -> update succeeded
     *     0 -> the quantity changed before the UPDATE
     *
     * This technique is useful pedagogically because it demonstrates that
     * optimistic concurrency does not necessarily require a dedicated version
     * column.
     *
     * However, a dedicated version column is generally clearer because the
     * business value (qty) is not being used as a concurrency token.
     *
     * IMPORTANT:
     * These methods do not automatically retry.
     *
     * ========================================================================
     */

    /**
     * Increases the inventory quantity after validating the quantity that was
     * previously read by the caller.
     *
     * <p>The update succeeds only when the current database quantity still
     * equals {@code currentQty}.</p>
     *
     * @param qty quantity to add
     * @param id inventory identifier
     * @param currentQty quantity previously read by the caller
     * @return 1 if the update succeeded; 0 if the quantity has changed
     */
    @Modifying
    @Query("""
               UPDATE Inventory inv
                  SET inv.qty = (inv.qty + :qty)
                WHERE inv.inventoryId = :id
                  AND inv.qty = :currentQty
           """)
    int debitQTYValidate(
            @Param("qty") Long qty,
            @Param("id") Long id,
            @Param("currentQty") Long currentQty);

    /**
     * Decreases the inventory quantity after validating the quantity that was
     * previously read by the caller.
     *
     * <p>The update succeeds only when the current database quantity still
     * equals {@code currentQty}.</p>
     *
     * @param qty quantity to subtract
     * @param id inventory identifier
     * @param currentQty quantity previously read by the caller
     * @return 1 if the update succeeded; 0 if the quantity has changed
     */
    @Modifying
    @Query("""
               UPDATE Inventory inv
                  SET inv.qty = (inv.qty - :qty)
                WHERE inv.inventoryId = :id
                  AND inv.qty = :currentQty
           """)
    int creditQTYValidate(
            @Param("qty") Long qty,
            @Param("id") Long id,
            @Param("currentQty") Long currentQty);

    /*
     * ========================================================================
     * Bulk inventory update
     * ========================================================================
     */

    @Modifying(clearAutomatically = true)
    @Query("""
               UPDATE Inventory inv
                  SET inv.qty = (inv.qty + :qty)
           """)
    int refillAll(@Param("qty") Long qty);
}