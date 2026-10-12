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
 * ------------------------------------------------------------------------------------------------------
 * Internal conciliation: cross-checks the journal ({@code JRN_EVENT}), the batch trace files,
 * the Dead Letter Topics and the live client/inventory tables against each other.
 * 
 * WARNING: this is a sandbox-only service, it is not meant to be used in production.
 *          Only basic sanity checks are performed, no security or access control is enforced.
 *          For example, the conciliation may be far more extensive than what is shown here.
 *
 * <ol>
 *   <li><b>DLT</b> — collect the event-ids of every record sitting in a Dead Letter Topic.</li>
 *   <li><b>JRN_EVENT</b> — load the journal, identify its batch-ids, verify each batch file exists.</li>
 *   <li><b>Batch files</b> — every transaction of a batch file must be in the journal (or in a DLT),
 *       and every journal event of the batch must be in the file (no orphan).</li>
 *   <li><b>Client balances</b> — replay the movements: {@code BALANCE = BALANCE ± COST}.</li>
 *   <li><b>Inventory</b> — replay the movements: {@code QTY = QTY ± QTY}.</li>
 * </ol>
 *
 * <p>The journal {@code payload} of an executed event is expected to be a JSON movement:</p>
 * <pre>
 * {"movement":{
 *    "client":   {"id": 7, "begin": 10.00, "operation": -5.00, "end": 5.00},
 *    "inventory":{"id": 3, "begin": 10,    "operation": -2,    "end": 8}}}
 * </pre>
 * where {@code client.id} is the person id and {@code inventory.id} the product id (the
 * {@code operation} is signed). The class only reads: it never modifies the journal, the files
 * or the business tables, and it reports anomalies as {@link Finding}s instead of throwing.
 *
 * <p>Events are replayed in processing order ({@code processedAt}, then journal id). A broken
 * chain is therefore a genuine signal — e.g. the inventory oversell race this sandbox teaches
 * is expected to surface here.</p>
 */
package org.rd.fullstack.springbooteda.srv;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.rd.fullstack.springbooteda.dao.InventoryRepository;
import org.rd.fullstack.springbooteda.dao.JrnEventRepository;
import org.rd.fullstack.springbooteda.dao.PersonRepository;
import org.rd.fullstack.springbooteda.dto.EventFooter;
import org.rd.fullstack.springbooteda.dto.EventHeader;
import org.rd.fullstack.springbooteda.dto.EventMessage;
import org.rd.fullstack.springbooteda.dto.Inventory;
import org.rd.fullstack.springbooteda.dto.JrnEvent;
import org.rd.fullstack.springbooteda.dto.Person;
import org.rd.fullstack.springbooteda.dto.Request;
import org.rd.fullstack.springbooteda.util.JsonMapper;
import org.rd.fullstack.springbooteda.util.Result;
import org.rd.fullstack.springbooteda.util.kafka.KafkaConstants;
import org.rd.fullstack.springbooteda.util.kafka.KafkaSandbox;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.type.TypeReference;

@Service
public class ConciliationSrv {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    // Movements are represented as a pair of legs, one for the client and one for the inventory.
    public record Leg(Long id, BigDecimal begin, BigDecimal operation, BigDecimal end) {}
    public record Movement(Leg client, Leg inventory) {}
    public record Payload(Movement movement) {}

    // Findings - RTC Reporting: the conciliation outcome is a list of findings, each with a step, a severity and a message.
    public enum Step { DLT, JOURNAL, BATCH_FILE, CLIENT_BALANCE, INVENTORY_QTY }
    public enum Severity { INFO, WARN, ERROR }
    public record Finding(Step step, Severity severity, String message) {}

    private static final Path     DEFAULT_BATCH_DIR = Path.of("log"); // Same as PipelineSrv.
    private static final Duration DLT_POLL_BUDGET   = Duration.ofSeconds(10);

    @SuppressWarnings("null")
    private static final Comparator<JrnEvent> PROCESSING_ORDER =
        Comparator.comparing(JrnEvent::getProcessedAt, Comparator.nullsLast(Comparator.naturalOrder()))
                  .thenComparing(JrnEvent::getJrnEventId, Comparator.nullsLast(Comparator.naturalOrder()));

    private final JrnEventRepository    jrnEventRepository;
    private final PersonRepository      personRepository;
    private final InventoryRepository   inventoryRepository;
    private final KafkaSandbox          kafkaSandbox;

    public ConciliationSrv(JrnEventRepository jrnEventRepository, PersonRepository personRepository,
                           InventoryRepository inventoryRepository, KafkaSandbox kafkaSandbox) {
        super();
        this.jrnEventRepository  = jrnEventRepository;
        this.personRepository    = personRepository;
        this.inventoryRepository = inventoryRepository;
        this.kafkaSandbox        = kafkaSandbox;
    }

    @SuppressWarnings("null")
    @Transactional(readOnly = true)
    public List<Finding> execute() {
        final Set<String> dlt        = new HashSet<>();
        final List<Finding> findings = new ArrayList<>();

        // 1. Load DLT.
        try {
            checkAndLoadDlt(dlt);
            findings.add(new Finding(Step.DLT, Severity.INFO, dlt.size() + " event(s) found in the DLTs."));
        } catch (RuntimeException ex) {
            findings.add(new Finding(Step.DLT, Severity.WARN,
                "DLT unreadable (" + ex.getMessage() + ") — batch-file results may report dead-lettered transactions as missing."));
        }

        // 2. JRN_EVENT: group by batchId.
        final List<JrnEvent> journal = jrnEventRepository.findAll();
        final Map<String, List<JrnEvent>> byBatch = new TreeMap<>();
        for (JrnEvent e : journal)
            byBatch.computeIfAbsent(e.getBatchId(), k -> new ArrayList<>()).add(e);
        findings.add(new Finding(Step.JOURNAL, Severity.INFO, journal.size() + " journal event(s) in " + byBatch.size() + " batch(es)."));

        // 3. Batch files vs journal.
        final Set<String> journalEventIds = new HashSet<>();
        for (JrnEvent e : journal)
            journalEventIds.add(e.getEventId());
        for (Map.Entry<String, List<JrnEvent>> batch : byBatch.entrySet())
            checkBatchIdvsJournal(batch.getKey(), batch.getValue(), journalEventIds, dlt, findings);

        // 4 & 5. Replay the movements.
        final Map<Long, BigDecimal> balances = new HashMap<>();
        for (Person p : personRepository.findAll())
            balances.put(p.getPersonId(), p.getBalance());
        
        final Map<Long, BigDecimal> quantities = new HashMap<>();
        for (Inventory i : inventoryRepository.findAll())
            quantities.put(i.getProductId(), BigDecimal.valueOf(i.getQty()));

        replay(journal, Step.CLIENT_BALANCE, "client", Movement::client, balances::get, findings);
        replay(journal, Step.INVENTORY_QTY, "product", Movement::inventory, quantities::get, findings);

        logger.info("Conciliation done: {} finding(s).", findings.size());
        return List.copyOf(findings);
    }

    private void checkAndLoadDlt(Set<String> dlt) {
        final Map<String, Object> cfg = Map.of(
            ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaSandbox.getBootstrapServers(),
            ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false,
            ConsumerConfig.ISOLATION_LEVEL_CONFIG, KafkaConstants.CST_ISOLATION_LEVEL_CONFIG,
            ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
            ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class
        );

        // Direct resource declaration: Java will automatically close it at the end of the block.
        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(cfg)) {
            
            final List<TopicPartition> partitions = new ArrayList<>();
            for (String topic : kafkaSandbox.getTopics()) {
                if (topic.endsWith(KafkaConstants.CST_TOPIC_DLT_POSTFIX)) {
                    consumer.partitionsFor(topic).forEach(p -> partitions.add(new TopicPartition(topic, p.partition())));
                }
            }

            if (partitions.isEmpty()) {
                logger.info("No DLTs found.");
                return; // The consumer will be closed automatically here before exiting the method.
            }

            consumer.assign(partitions);
            consumer.seekToBeginning(partitions);
            final Map<TopicPartition, Long> ends = consumer.endOffsets(partitions);
            final long deadline = System.nanoTime() + DLT_POLL_BUDGET.toNanos();

            while (partitions.stream().anyMatch(tp -> consumer.position(tp) < ends.get(tp))) {
                if (System.nanoTime() > deadline)
                    throw new IllegalStateException("timeout while reading the DLTs");

                for (ConsumerRecord<String, String> rec : consumer.poll(Duration.ofMillis(KafkaConstants.CST_POLL_DURATION))) {
                    final String eventId = eventIdOf(rec.value());
                    if (eventId != null)
                        dlt.add(eventId);
                }
            }
        } // The consumer will be closed automatically here.
    }

    private void checkBatchIdvsJournal(String batchId, List<JrnEvent> events, 
                                       Set<String> journalEventIds, Set<String> dlt, List<Finding> findings) {

        final Path tmpFile = DEFAULT_BATCH_DIR.resolve("batch-" + batchId + ".tmp");
        final Path trxFile = DEFAULT_BATCH_DIR.resolve("batch-" + batchId + ".trx");
        if (!Files.isRegularFile(trxFile)) {
            final String why = Files.isRegularFile(tmpFile)
                ? "only the unconfirmed .tmp exists (publication never committed)" : "file not found";
            findings.add(new Finding(Step.BATCH_FILE, Severity.ERROR,
                "Batch " + batchId + ": " + trxFile + " — " + why + " (" + events.size() + " journal event(s))."));
            return;
        }

        final Set<String> inFile = new HashSet<>();
        try {
            for (String line : Files.readAllLines(trxFile, StandardCharsets.UTF_8)) {
                if (line.isBlank())
                    continue;

                final String eventId = eventIdOf(line);
                if (eventId == null)
                    findings.add(new Finding(Step.BATCH_FILE, Severity.ERROR,
                        "Batch " + batchId + ": unreadable line in " + trxFile + "."));
                else if (!inFile.add(eventId))
                    findings.add(new Finding(Step.BATCH_FILE, Severity.WARN,
                        "Batch " + batchId + ": duplicate event " + eventId + " in the file."));
            }
        } catch (IOException ex) {
            findings.add(new Finding(Step.BATCH_FILE, Severity.ERROR,
                "Batch " + batchId + ": cannot read " + trxFile + " (" + ex.getMessage() + ")."));
            return;
        }

        // All transactions present: file -> journal (or DLT). A replay keeps the journal row of
        // the first delivery, so a file event may legitimately live under another batch-id.
        int missing = 0;
        for (String eventId : inFile) {
            if (!journalEventIds.contains(eventId) && !dlt.contains(eventId)) {
                missing++;
                findings.add(new Finding(Step.BATCH_FILE, Severity.ERROR,
                    "Batch " + batchId + ": transaction " + eventId + " is in the file but neither in the journal nor in a DLT."));
            }
        }

        // No orphan: journal -> file.
        int orphans = 0;
        for (JrnEvent e : events) {
            if (!inFile.contains(e.getEventId())) {
                orphans++;
                findings.add(new Finding(Step.BATCH_FILE, Severity.ERROR,
                    "Batch " + batchId + ": journal event " + e.getEventId() + " (jrn_event_id=" + e.getJrnEventId()
                    + ") is not in the batch file — orphan."));
            }
        }

        if (missing == 0 && orphans == 0)
            findings.add(new Finding(Step.BATCH_FILE, Severity.INFO, "Batch " + batchId + ": " + inFile.size() + " transaction(s), consistent."));
    }

    private void replay(List<JrnEvent> journal, Step step, String label,
                        Function<Movement, Leg> leg, 
                        Function<Long, BigDecimal> current,
                        List<Finding> findings) {

        // Executed events only: PENDING / BACK_ORDER / ERROR moved nothing.
        final Map<Long, List<Leg>> byKey = new LinkedHashMap<>();
        journal.stream().filter(e -> e.getResult() == Result.EXECUTED).sorted(PROCESSING_ORDER).forEach(e -> {
            final Leg l = parseLeg(e, leg);
            if (l == null)
                findings.add(new Finding(step, Severity.ERROR,
                    "Event " + e.getEventId() + " (jrn_event_id=" + e.getJrnEventId()
                    + "): EXECUTED but the payload holds no valid " + label + " movement."));
            else
                byKey.computeIfAbsent(l.id(), k -> new ArrayList<>()).add(l);
        });

        int broken = 0;
        for (Map.Entry<Long, List<Leg>> entry : byKey.entrySet()) {
            final Long id = entry.getKey();
            BigDecimal running = null;
            for (Leg l : entry.getValue()) {
                if (l.begin().add(l.operation()).compareTo(l.end()) != 0) {
                    broken++;
                    findings.add(new Finding(step, Severity.ERROR, label + " " + id + ": " + l.begin() + " "
                        + signed(l.operation()) + " <> " + l.end() + " (movement arithmetic)."));
                }
                if (running != null && running.compareTo(l.begin()) != 0) {
                    broken++;
                    findings.add(new Finding(step, Severity.ERROR, label + " " + id + ": movement begins at "
                        + l.begin() + " but the previous one ended at " + running + " (broken chain)."));
                }
                running = l.end();
            }

            final BigDecimal live = current.apply(id);
            if (live == null) {
                broken++;
                findings.add(new Finding(step, Severity.ERROR, label + " " + id + ": has movements but no longer exists."));
            } else if (running != null && running.compareTo(live) != 0) {
                broken++;
                findings.add(new Finding(step, Severity.ERROR, label + " " + id + ": replay ends at "
                    + running + " but the database holds " + live + "."));
            }
        }

        if (broken == 0)
            findings.add(new Finding(step, Severity.INFO, byKey.size() + " " + label + "(s) replayed, consistent."));
    }

    private String eventIdOf(String json) {
        try {
            EventMessage<EventHeader, Request, EventFooter> msg = JsonMapper.readFromJson(json,
                new TypeReference<EventMessage<EventHeader, Request, EventFooter>>() {});
            return (msg == null || msg.getHeader() == null) ? null : msg.getHeader().getEventId();
        } catch (RuntimeException ex) {
            return null; // Not one of our messages — cannot be matched to a transaction.
        }
    }

    private Leg parseLeg(JrnEvent event, Function<Movement, Leg> leg) {
        try {
            final Payload p = JsonMapper.readFromJson(event.getPayload(), Payload.class);
            final Leg l = (p == null || p.movement() == null) ? null : leg.apply(p.movement());
            return (l == null || l.id() == null || l.begin() == null || l.operation() == null || l.end() == null)
                ? null : l;
        } catch (RuntimeException ex) {
            return null; // e.g. the legacy SHA-1 payload hash.
        }
    }

    private String signed(BigDecimal v) {
        return v.signum() < 0 ? "- " + v.abs() : "+ " + v;
    }
}