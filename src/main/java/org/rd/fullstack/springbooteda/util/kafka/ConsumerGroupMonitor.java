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
package org.rd.fullstack.springbooteda.util.kafka;

import org.apache.kafka.clients.admin.*;
import org.apache.kafka.common.TopicPartition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class ConsumerGroupMonitor {
    private static final Logger logger = 
        LoggerFactory.getLogger(ConsumerGroupMonitor.class);

    private final KafkaSandbox kafkaSandbox;
    private final Map<String, Long> lagThresholds = new ConcurrentHashMap<>();
    private final Map<String, CopyOnWriteArrayList<Consumer<LagAlertEvent>>> listeners = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {

        Thread thread = new Thread(r, "ConsumerGroupMonitor");
        thread.setDaemon(true);
        return thread;
    });

    public ConsumerGroupMonitor(KafkaSandbox sandbox) {
        this.kafkaSandbox = sandbox;
    }

    /** Start periodic monitoring of the given consumer
     *  groups with the specified interval (ms). 
     */
    public void startMonitoring(Collection<String> groupIds, long pollIntervalMs) {
        scheduler.scheduleAtFixedRate(() -> {
            try {
                checkGroups(groupIds);
            } catch (Exception ex) {
                logger.error("Error during consumer group check: {}.", ex);
            }
        }, 0, pollIntervalMs, TimeUnit.MILLISECONDS);
        logger.info("ConsumerGroupMonitor started for groups {} with interval {} ms.", groupIds, pollIntervalMs);
    }

    public void stopMonitoring() {
        if (scheduler.isShutdown()) 
            return;  // idempotent

        scheduler.shutdownNow();
        kafkaSandbox.unregisterMonitor(this);
        logger.info("ConsumerGroupMonitor stopped.");
    }

    public void setLagThreshold(String groupId, long lagThreshold) {
        lagThresholds.put(groupId, lagThreshold);
    }

    public void addLagAlertListener(String groupId, Consumer<LagAlertEvent> listener) {
        listeners.computeIfAbsent(groupId, k -> new CopyOnWriteArrayList<>()).add(listener);
    }

    public void checkGroups(Collection<String> groupIds) {
        for (String groupId : groupIds) {
            try {
                ConsumerGroupInfo info = getConsumerGroupInfo(groupId);
                long threshold = lagThresholds.getOrDefault(groupId, Long.MAX_VALUE);

                info.offsets().forEach((tp, offsetInfo) -> {
                    if (offsetInfo.lag() > threshold) {
                        listeners.getOrDefault(groupId, new CopyOnWriteArrayList<>())
                                 .forEach(l -> l.accept(new LagAlertEvent(groupId, tp, offsetInfo.lag())));
                        logger.warn("Lag alert: group={}, topic={}, partition={}, lag={}.",
                                    groupId, tp.topic(), tp.partition(), offsetInfo.lag());
                    }
                });
            } catch (Exception ex) {
                logger.error("Failed to fetch info for consumer group: {}.", groupId, ex);
            }
        }
    }

    /** Fetch consumer group info including committed offsets and log end offsets. */
    public ConsumerGroupInfo getConsumerGroupInfo(String groupId) throws Exception {
        ConsumerGroupDescription desc = kafkaSandbox.getAdminClient()
                                            .describeConsumerGroups(List.of(groupId))
                                            .all().get().get(groupId);

        Map<TopicPartition, Long> committedOffsets = kafkaSandbox.getAdminClient()
                                                        .listConsumerGroupOffsets(groupId)
                                                        .partitionsToOffsetAndMetadata()
                                                        .get().entrySet().stream()
                                                        .collect(Collectors.toMap(
                                                            Map.Entry::getKey,
                                                            e -> e.getValue().offset()
                                                        ));

        Map<TopicPartition, Long> logEndOffsets = fetchLogEndOffsets(committedOffsets.keySet());

        Map<TopicPartition, OffsetInfo> offsetsInfo = new HashMap<>();
        committedOffsets.forEach((tp, committed) -> {
            long logEnd = logEndOffsets.getOrDefault(tp, committed);
            offsetsInfo.put(tp, new OffsetInfo(committed, logEnd, Math.max(logEnd - committed, 0)));
        });

        List<MemberInfo> members = desc.members().stream()
                                       .map(m -> new MemberInfo(m.consumerId(), m.clientId(),
                                               new ArrayList<>(m.assignment().topicPartitions())))
                                       .toList();

        return new ConsumerGroupInfo(groupId, desc.groupState().toString(), members, offsetsInfo);
    }

    private Map<TopicPartition, Long> fetchLogEndOffsets(Set<TopicPartition> partitions) throws InterruptedException, ExecutionException {
        Map<TopicPartition, OffsetSpec> request = 
            partitions.stream()
                .collect(Collectors
                .toMap(tp -> tp, tp -> OffsetSpec.latest()));

        Map<TopicPartition, ListOffsetsResult.ListOffsetsResultInfo> offsets =
                kafkaSandbox.getAdminClient().listOffsets(request).all().get();

        return offsets.entrySet().stream()
                        .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().offset()));                  
    }
}