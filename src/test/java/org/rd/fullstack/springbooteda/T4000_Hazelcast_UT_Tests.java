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
package org.rd.fullstack.springbooteda;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.rd.fullstack.springbooteda.config.Application;
import org.rd.fullstack.springbooteda.dto.PipelineContext;
import org.rd.fullstack.springbooteda.util.hazelcast.HazelcastConstants;
import org.rd.fullstack.springbooteda.util.hazelcast.HazelcastDashboard;
import org.rd.fullstack.springbooteda.util.hazelcast.HazelcastSandbox;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;

import com.hazelcast.cluster.Cluster;
import com.hazelcast.cluster.ClusterState;
import com.hazelcast.cluster.Member;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.cp.lock.FencedLock;
import com.hazelcast.map.IMap;
import com.hazelcast.map.LocalMapStats;
import com.hazelcast.partition.PartitionService;

@SpringBootTest(classes = Application.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("Hazelcast demo and tests.")
public class T4000_Hazelcast_UT_Tests {

    private static final Logger logger =
        LoggerFactory.getLogger(T4000_Hazelcast_UT_Tests.class);

    private static final String NON_EXISTENT_KEY = "non-existent-key";
    private static final String KEY_1            = "pipeline-001";
    private static final String KEY_2            = "pipeline-002";
    private static final String FENCED_LOCK_KEY  = "pipeline-fenced-lock";

    @Autowired
    private HazelcastSandbox hazelcastSandbox;

    // Indicates whether the CP subsystem is active (>= 1) or disabled (0).
    // Value injected from the application properties.
    @Value("${org.rd.fullstack.springbooteda.hazelcast.sandbox.cp-subsystem.cp-member-count}")
    private int cpMemberCount;

    @AfterEach
    void cleanUp() {
        hazelcastSandbox.getMap(HazelcastConstants.CST_MAPNAME_CTX).clear();
    }

    @Test
    @Order(1)
    public void test1_GetMissingEntry() throws Exception {
        IMap<String, PipelineContext> map =
            hazelcastSandbox.getMap(HazelcastConstants.CST_MAPNAME_CTX);

        PipelineContext pc = map.get(NON_EXISTENT_KEY);
        assertNull(pc, "Reading a non-existent key must return - null.");
    }

    @Test
    @Order(2)
    public void test2_BasicCrudOperations() throws Exception {
        IMap<String, PipelineContext> map =
            hazelcastSandbox.getMap(HazelcastConstants.CST_MAPNAME_CTX);

        PipelineContext pc = new PipelineContext();
        map.put(KEY_1, pc);
        logger.info("PUT: {} -> {}.", KEY_1, pc);

        PipelineContext retrieved = map.get(KEY_1);
        logger.info("GET: {} -> {}.", KEY_1, retrieved);
        assertNotNull(retrieved, "The entry must be retrieved correctly after a put().");
        
        map.remove(KEY_1);
        assertNull(map.get(KEY_1), "The key must be removed after a remove().");
        logger.info("REMOVE: key {} removed.", KEY_1);
    }

    @Test
    @Order(3)
    @SuppressWarnings("null")
    public void test3_MapLock() throws Exception {
        IMap<String, PipelineContext> map =
            hazelcastSandbox.getMap(HazelcastConstants.CST_MAPNAME_CTX);

        map.put(KEY_1, new PipelineContext());
        map.lock(KEY_1);
        try {
            PipelineContext pc = map.get(KEY_1);
            assertNotNull(pc,"The entry must be present before modification under lock.");

            pc.setPause(true);
            map.put(KEY_1, pc);
            logger.info("Entry modified under map.lock(): {}.", pc);
        } finally {
            map.unlock(KEY_1);
            logger.info("Entry unlocked under map.lock(): {}.", KEY_1);
        }

        assertNotNull(map.get(KEY_1),"The entry must be not null.");
    }

    @Test
    @Order(4)
    @SuppressWarnings("null")
    public void test4_TryLockWithTimeout() throws Exception {
        IMap<String, PipelineContext> map =
            hazelcastSandbox.getMap(HazelcastConstants.CST_MAPNAME_CTX);

        map.put(KEY_1, new PipelineContext());
        boolean lockAcquired = map.tryLock(KEY_1, 10, TimeUnit.SECONDS);
        if (lockAcquired) {
            try {
                PipelineContext pc = map.get(KEY_1);
                assertNotNull(pc,"The entry must be present before modification under lock."); 
                pc.setPause(true);

                map.put(KEY_1, pc);
                logger.info("Entry modified via tryLock(): {}.", pc);
            } finally {
                map.unlock(KEY_1);
                logger.info("Lock released for key: {}.", KEY_1);
            }
        } else {
            logger.warn("Impossible to get the lock for key: {}.", KEY_1);
        }

        assertTrue(lockAcquired,"The lock must be acquired as no concurrent is holding it.");
    }

    // ----------------------------------------------------------------------------
    // Test 5 — FencedLock (CP Subsystem): strong consistency (CP model).
    //
    // PREREQUISITE: cp-member-count >= 1 in the application properties.
    // If cpMemberCount == 0, the test is cleanly skipped using assumeTrue.
    //
    // To enable locally, add the following to application.properties:
    //   org.rd.fullstack.springbooteda.hazelcast.cp-subsystem.cp-member-count=1
    //
    // WARNING: cp-member-count=1 -> UNSAFE mode (tests only).
    //          cp-member-count >= 3 (odd number) -> strict Raft mode (production).
    // ----------------------------------------------------------------------------
    @Test
    @Order(5)
    @SuppressWarnings("null")
    public void test5_FencedLock() throws Exception {
        assumeTrue(cpMemberCount > 0,
            "Test ignored : CP Subsystem disabled (cp-member-count=" + cpMemberCount + ")." +
            " Set cp-member-count >= 1 to enable this test -- Must have at least two CP members.");

        IMap<String, PipelineContext> map =
            hazelcastSandbox.getMap(HazelcastConstants.CST_MAPNAME_CTX);

        map.put(KEY_2, new PipelineContext());
        FencedLock lock = hazelcastSandbox
            .getCPSubsystem()
            .getLock(FENCED_LOCK_KEY);

        lock.lock();
        try {
            PipelineContext pc = map.get(KEY_2);
            assertNotNull(pc,"The entry must be present before modification under FencedLock.");
            pc.setPause(true);
            
            map.put(KEY_2, pc);
            logger.info("Entry modified in the critical section (FencedLock): {}.", pc);
        } finally {
            lock.unlock();
            logger.info("FencedLock released.");
        }

        assertNotNull(map.get(KEY_2),"The entry must be present after modification under FencedLock.");
    }

    @Test
    @Order(6)
    public void test6_EntryProcessor() throws Exception {
        IMap<String, PipelineContext> map =
            hazelcastSandbox.getMap(HazelcastConstants.CST_MAPNAME_CTX);

        map.put(KEY_1, new PipelineContext());
        map.executeOnKey(KEY_1, entry -> {
            PipelineContext pc = entry.getValue();
            if (pc != null) {
                pc.setPause(true);
                entry.setValue(pc);
                logger.info("EntryProcessor: atomic modification applied.");
            }
            return null;
        });

        assertNotNull(map.get(KEY_1),"The entry must be present after atomic modification.");
        logger.info("EntryProcessor: atomic modification applied.");
    }

    @Test
    @Order(7)
    public void test7_EntryProcessorOnKeys() throws Exception {
        IMap<String, PipelineContext> map =
            hazelcastSandbox.getMap(HazelcastConstants.CST_MAPNAME_CTX);

        map.put(KEY_1, new PipelineContext());
        map.put(KEY_2, new PipelineContext());

        @SuppressWarnings("null")
        Map<String, Object> results = map.executeOnKeys(
            java.util.Set.of(KEY_1, KEY_2),
            entry -> {
                PipelineContext pc = entry.getValue();
                if (pc != null) {
                    pc.setPause(true);
                    entry.setValue(pc);
                }
                return entry.getKey();
            }
        );

        logger.info("Keys executed by executeOnKeys: {}.", results.keySet());
        assertNotNull(map.get(KEY_1), "The entry for KEY_1 must be present.");
        assertNotNull(map.get(KEY_2), "The entry for KEY_2 must be present.");
    }

    // -----------------------------------------------------------------------------|
    // Test 8 — Comparative summary of the four locking strategies.                 |
    // -----------------------------------------------------------------------------|
    // | Strategy         | Model | Performance | Consistency | Use case            |
    // |------------------|-------|-------------|-------------|---------------------|
    // | map.lock(key)    | AP    | Fast        | Weak        | Distributed cache   |
    // | tryLock(timeout) | AP    | Fast        | Weak        | Cache + tolerance   |
    // | FencedLock (CP)  | CP    | Slower      | Strong      | Critical data       |
    // | EntryProcessor   | AP    | Optimal     | Atomic      | Production / perf   |
    // -----------------------------------------------------------------------------|
    // The FencedLock branch applies a fallback to map.lock() if CP is disabled.
    // -----------------------------------------------------------------------------|
    @Test
    @Order(8)
    @SuppressWarnings("null")
    public void test8_LockingStrategySummary() throws Exception {
        IMap<String, PipelineContext> map =
            hazelcastSandbox.getMap(HazelcastConstants.CST_MAPNAME_CTX);

        final String keyLock      = "key-lock";
        final String keyTryLock   = "key-trylock";
        final String keyFenced    = "key-fenced";
        final String keyProcessor = "key-processor";

        map.put(keyLock,      new PipelineContext());
        map.put(keyTryLock,   new PipelineContext());
        map.put(keyFenced,    new PipelineContext());
        map.put(keyProcessor, new PipelineContext());

        // --- Stratégy 1 : map.lock() ---
        map.lock(keyLock);
        try {
            map.put(keyLock, map.get(keyLock));
            logger.info("[map.lock]      : writing under AP lock.");
        } finally {
            map.unlock(keyLock);
        }

        // --- Strategy 2: tryLock() ---
        if (map.tryLock(keyTryLock, 5, TimeUnit.SECONDS)) {
            try {
                map.put(keyTryLock, map.get(keyTryLock));
                logger.info("[tryLock]       : writing under AP lock with timeout.");
            } finally {
                map.unlock(keyTryLock);
            }
        }

        // --- Strategy 3 : FencedLock si CP active, else fallback on map.lock() ---
        if (cpMemberCount > 0) {
            FencedLock lock = hazelcastSandbox
                .getCPSubsystem()
                .getLock("summary-fenced-lock");
            lock.lock();
            try {
                map.put(keyFenced, map.get(keyFenced));
                logger.info("[FencedLock]    : writing under CP lock (strong consistency).");
            } finally {
                lock.unlock();
            }
        } else {
            map.lock(keyFenced);
            try {
                map.put(keyFenced, map.get(keyFenced));
                logger.info("[FencedLock→AP] : CP inactive, fallback on map.lock().");
            } finally {
                map.unlock(keyFenced);
            }
        }

        // --- Strategy 4 : EntryProcessor ---
        map.executeOnKey(keyProcessor, entry -> {
            entry.setValue(entry.getValue());
            logger.info("[EntryProcessor]: atomic modification on the server side.");
            return null;
        });

        assertNotNull(map.get(keyLock),"The entry for keyLock must be present.");
        assertNotNull(map.get(keyTryLock),"The entry for keyTryLock must be present.");
        assertNotNull(map.get(keyFenced),"The entry for keyFenced must be present.");
        assertNotNull(map.get(keyProcessor),"The entry for keyProcessor must be present.");

        logger.info("Comparative summary completed. cpMemberCount: {}.", cpMemberCount);
    }

    @Test
    @Order(9)
    public void test9_ClusterHealthCheck() throws Exception {

        // Load some entries to make the map stats significant.
        IMap<String, PipelineContext> map =
            hazelcastSandbox.getMap(HazelcastConstants.CST_MAPNAME_CTX);

        map.put(KEY_1, new PipelineContext());
        map.put(KEY_2, new PipelineContext());
        map.get(KEY_1);               // A hit.
        map.get(NON_EXISTENT_KEY);    // A miss.

        HazelcastInstance hazelcastInstance = hazelcastSandbox.getHazelcastInstance();
        Cluster cluster = hazelcastInstance.getCluster();

        HazelcastDashboard hazelcastDashboard = hazelcastSandbox.getDashboardData();
        logger.info("=== DASHBOARD ===");
        logger.info("  {}", hazelcastDashboard);

        logger.info("=== CLUSTER ===");
        ClusterState clusterState = cluster.getClusterState();
        logger.info("  State            : {}.", clusterState);
        logger.info("  Date/Time cluster: {}.,", cluster.getClusterTime());

        for (Member member : cluster.getMembers()) {
            logger.info("  -- Member --");
            logger.info("    Address    : {}.", member.getAddress());
            logger.info("    UUID       : {}.", member.getUuid());
            logger.info("    Local      : {}.", member.localMember());
            logger.info("    Lite member: {}.", member.isLiteMember());
        }

        assertTrue(clusterState==ClusterState.ACTIVE,
             "The cluster state must be ACTIVE or ACTIVATING.");

        assertTrue(cluster.getMembers() != null && !cluster.getMembers().isEmpty(),
            "The cluster must contain at least one member.");
 
        boolean hasLocalMember = cluster.getMembers().stream().anyMatch(Member::localMember);
        assertTrue(hasLocalMember == true,
             "The cluster must contain a local member (this instance).");

        LocalMapStats stats = map.getLocalMapStats();
        logger.info("=== LOCAL MAP STATS [{}] ===", HazelcastConstants.CST_MAPNAME_CTX);
        logger.info("  Owned entries  : {}.", stats.getOwnedEntryCount());
        logger.info("  Backup entries : {}.", stats.getBackupEntryCount());
        logger.info("  Hits           : {}.", stats.getHits());
        logger.info("  Gets           : {}.", stats.getGetOperationCount());
        logger.info("  Puts           : {}.", stats.getPutOperationCount());
        logger.info("  Removes        : {}.", stats.getRemoveOperationCount());
        logger.info("  Locks actifs   : {}.", stats.getLockedEntryCount());
        logger.info("  Dirty entries  : {}.", stats.getDirtyEntryCount());
        logger.info("  Heap cost (B)  : {}.", stats.getHeapCost());
        logger.info("  Last access    : {}.", stats.getLastAccessTime());
        logger.info("  Last update    : {}.", stats.getLastUpdateTime());

        assertTrue(stats.getOwnedEntryCount() >= 2L,
             "The local map must contain at least two entries.");

        assertTrue(stats.getGetOperationCount() >= 2L,
             "At least two get operations must have been counted.");

        assertTrue(stats.getPutOperationCount() >= 2L,
             "At least two put operations must have been counted.");

        PartitionService ps = hazelcastInstance.getPartitionService();
        long totalPartitions = ps.getPartitions().size();
        @SuppressWarnings("null")
        long localPartitions = ps.getPartitions().stream()
            .filter(p -> p.getOwner() != null && p.getOwner().localMember())
            .count();

        logger.info("=== PARTITIONS ===");
        logger.info("  Total partitions     : {}.", totalPartitions);
        logger.info("  Partitions locales   : {}.", localPartitions);
        logger.info("  Cluster sain         : {}.", ps.isClusterSafe());
        logger.info("  Local member safe    : {}.", ps.isLocalMemberSafe());
        logger.info("  Migration in progress: {}.", !ps.isClusterSafe() ? "Yes" : "No");

        assertTrue(totalPartitions > 0L,
            "The number of partitions must be greater than 0.");

        assertTrue(localPartitions > 0L,
            "The local member must possess at least one partition.");

        assertTrue(ps.isClusterSafe(),
            "The cluster must be in a healthy state (no migration in progress).");

        assertTrue(ps.isLocalMemberSafe(),
            "The local member must be in a healthy state.");

        logger.info("Health : CLUSTER={} | MEMBERS={} | PARTITIONS={}/{} locals | HEALTH={}.",
            clusterState,
            cluster.getMembers().size(),
            localPartitions,
            totalPartitions,
            ps.isClusterSafe()
        );
    }
}