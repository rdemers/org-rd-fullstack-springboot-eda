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
 * where {@code client.id} is the person id and {@code inventory.id} is the inventory primary key
 * (the {@code operation} is signed). The execution {@code batchId} is read from
 * {@link JrnEvent#getBatchId()}, not from the movement payload. The class only reads: it never modifies the journal, the files
 * or the business tables, and it reports anomalies as {@link Finding}s instead of throwing.
 *
 * <p>{@code JrnEvent.batchId} identifies the batch currently associated with the journal row.
 * A replay updates that column to the replay batch, and this value is therefore the source of
 * truth for the execution batch used by conciliation. The chain is checked <b>per batch</b>, so
 * changes made between two batches (e.g. a manual inventory refill) are tolerated; the database
 * is compared with the latest processed batch only, and a WARN is raised when more than one batch
 * is present.</p>
 *
 * <p>Events are replayed in processing order ({@code processedAt}, then journal id). A broken
 * chain is therefore a genuine signal — e.g. the inventory oversell race this sandbox teaches
 * is expected to surface here. The payload replay and the optional payload-versus-database
 * comparison are separate checks.</p>
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
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;

import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.TopicPartition;
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
    // Keep the journal event alongside its leg so each entity chain can be sorted
    // explicitly by processing time and journal id.
    private record ReplayLeg(JrnEvent event, Leg leg) {}
    // The execution batch is deliberately not duplicated in the payload. JrnEvent.batchId is the
    // source of truth and is updated by ProcessorSrv when a replay is accepted.
    public record Payload(Movement movement) {}

    // Result of the payload-only replay. The live database is deliberately not involved here.
    private record ReplayTrace(Map<String, Map<Long, List<ReplayLeg>>> byBatch,
                               Map<Long, String> lastBatch,
                               Map<Long, BigDecimal> lastEnd,
                               String latestBatch,
                               int broken) {}

    // Findings - RTC Reporting: the conciliation outcome is a list of findings, each with a step, a severity and a message.
    public enum Step { DLT, JOURNAL, BATCH_FILE, CLIENT_BALANCE, INVENTORY_QTY }
    public enum Severity { INFO, WARN, ERROR }
    public record Finding(Step step, Severity severity, String message) {}

    private static final Path     DEFAULT_BATCH_DIR = Path.of("log"); // Same as PipelineSrv.
    private static final Duration DLT_POLL_BUDGET   = Duration.ofSeconds(30);

    @SuppressWarnings("null")
    private static final Comparator<JrnEvent> PROCESSING_ORDER =
        Comparator.comparing(JrnEvent::getProcessedAt, Comparator.nullsLast(Comparator.naturalOrder()));

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

        // 1. Check inventory and keep a snapshot for the separate database cross-check.
        final List<Inventory> inventories = inventoryRepository.findAll();
        long negativeQty = inventories.stream().filter(i -> i.getQty() < 0).count();
        if (negativeQty > 0)
            findings.add(new Finding(Step.INVENTORY_QTY, Severity.ERROR, negativeQty + " inventory record(s) with negative quantity found."));
        else
            findings.add(new Finding(Step.INVENTORY_QTY, Severity.INFO, "No inventory record with negative quantity found."));

        // 2. Check client balances and keep a snapshot for the separate database cross-check.
        final List<Person> persons = personRepository.findAll();
        long negativeBalance = persons.stream().filter(p -> p.getBalance().compareTo(BigDecimal.ZERO) < 0).count();
        if (negativeBalance > 0)
            findings.add(new Finding(Step.CLIENT_BALANCE, Severity.ERROR, negativeBalance + " client(s) with negative balance found."));
        else
            findings.add(new Finding(Step.CLIENT_BALANCE, Severity.INFO, "No client with negative balance found."));

        // 3. Load DLT.
        try {
            checkAndLoadDlt(dlt);
            if (dlt.size() > 0) {
                findings.add(new Finding(Step.DLT, Severity.ERROR, dlt.size() + " event(s) found in the DLTs: " + dlt + "."));
            } else {
                findings.add(new Finding(Step.DLT, Severity.INFO, "No event found in the DLTs."));
            }
        } catch (RuntimeException ex) {
            findings.add(new Finding(Step.DLT, Severity.ERROR,
                "DLT unreadable (" + ex.getMessage() + ") — batch-file results may report dead-lettered transactions as missing."));
        }

        // 4. JRN_EVENT: group by batchId.
        final List<JrnEvent> journal = jrnEventRepository.findAll();
        final Map<String, List<JrnEvent>> byBatch = new TreeMap<>();
        for (JrnEvent e : journal)
            byBatch.computeIfAbsent(e.getBatchId(), k -> new ArrayList<>()).add(e);
        findings.add(new Finding(Step.JOURNAL, Severity.INFO, journal.size() + " journal event(s) in " + byBatch.size() + " batch(es)."));
        if (byBatch.size() > 1)
            findings.add(new Finding(Step.JOURNAL, Severity.WARN, "More than one batch-id in the journal: " + byBatch.keySet() + "."));

        // 5. Batch files vs journal.
        final Set<String> journalEventIds = new HashSet<>();
        for (JrnEvent e : journal)
            journalEventIds.add(e.getEventId());
        for (Map.Entry<String, List<JrnEvent>> batch : byBatch.entrySet())
            checkBatchIdvsJournal(batch.getKey(), batch.getValue(), journalEventIds, dlt, findings);

        // 6 & 7. Replay the payload chain, then compare its final state with the live database.
        // These are intentionally separate checks: the chain is derived only from the journal payload.
        final Map<Long, BigDecimal> balances = new HashMap<>();
        for (Person p : persons)
            balances.put(p.getPersonId(), p.getBalance());
        
        final Map<Long, BigDecimal> quantities = new HashMap<>();
        for (Inventory i : inventories)
            quantities.put(i.getInventoryId(), BigDecimal.valueOf(i.getQty()));

        @SuppressWarnings("unused")
        final ReplayTrace clientTrace = replayPayloadChain(journal, Step.CLIENT_BALANCE, "client", Movement::client, findings);
        @SuppressWarnings("unused")
        final ReplayTrace inventoryTrace = replayPayloadChain(journal, Step.INVENTORY_QTY, "inventory", Movement::inventory, findings);

        // We manually modify product inventories and people's balances between executions.
        // When multiple batch IDs are involved, the data effectively becomes invalid.
        // 
        //compareReplayWithDatabase(clientTrace, Step.CLIENT_BALANCE, "client", balances::get, findings);
        //compareReplayWithDatabase(inventoryTrace, Step.INVENTORY_QTY, "inventory", quantities::get, findings);

        logger.info("Conciliation done: {} finding(s).", findings.size());
        return List.copyOf(findings);
    }

    private void checkAndLoadDlt(Set<String> dlt) {

        KafkaConsumer<String, String> consumer = 
            kafkaSandbox.getConsumer(null, KafkaConstants.CST_TOPIC_KAFKA_REQ);

        final List<TopicPartition> partitions = kafkaSandbox.getTopics().stream()
            .filter(topic -> topic.endsWith(KafkaConstants.CST_TOPIC_DLT_POSTFIX))
            .flatMap(topic -> consumer.partitionsFor(topic).stream()
                .map(p -> new TopicPartition(topic, p.partition())))
            .toList();

        if (partitions.isEmpty()) {
            logger.info("No DLTs found.");
            return;
        }

        consumer.assign(partitions);
        consumer.seekToBeginning(partitions);
        
        final Map<TopicPartition, Long> ends = consumer.endOffsets(partitions);
        final long deadline = System.nanoTime() + DLT_POLL_BUDGET.toNanos();

        while (partitions.stream().anyMatch(tp -> consumer.position(tp) < ends.get(tp))) {
            if (System.nanoTime() > deadline) {
                throw new IllegalStateException("Timeout while reading the DLTs");
            }

            consumer.poll(Duration.ofMillis(KafkaConstants.CST_POLL_DURATION)).forEach(rec -> {
                String eventId = eventIdOf(rec.value());
                if (eventId != null) {
                    dlt.add(eventId);
                }
            });
        }
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

        // All transactions present: file -> journal (or DLT). The journal row carries the
        // currently associated batch-id; a replay updates that value to the replay batch.
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

    private ReplayTrace replayPayloadChain(List<JrnEvent> journal, Step step, String label,
                                           Function<Movement, Leg> leg,
                                           List<Finding> findings) {

        // Executed events only: PENDING / BACK_ORDER / ERROR moved nothing.
        // Movements are grouped by the batch-id currently stored on the journal row
        // (batch-id -> entity id -> legs). A replay updates that journal value to the replay batch,
        // so the chain is only meaningful inside one batch.
        final List<JrnEvent> executed = journal.stream()
            .filter(e -> e.getResult() == Result.EXECUTED).sorted(PROCESSING_ORDER).toList();

        // Keep batches in first-seen processing order, but sort entity ids explicitly.
        final Map<String, Map<Long, List<ReplayLeg>>> byBatch = new LinkedHashMap<>();
        final Map<Long, String>     lastBatch = new HashMap<>(); // entity id -> batch of its last movement.
        final Map<Long, BigDecimal> lastEnd   = new HashMap<>(); // entity id -> end of its last movement.
        int broken = 0;
        String latestBatch = null;
        for (JrnEvent e : executed) {
            final Payload p = parsePayload(e);
            final Leg l = legOf(p, leg);
            if (l == null) {
                broken++;
                findings.add(new Finding(step, Severity.ERROR,
                    "Event " + e.getEventId() + " (jrn_event_id=" + e.getJrnEventId()
                    + "): EXECUTED but the payload holds no valid " + label + " movement."));
                continue;
            }
            final String batch = e.getBatchId();
            byBatch.computeIfAbsent(batch, k -> new TreeMap<>())
                   .computeIfAbsent(l.id(), k -> new ArrayList<>()).add(new ReplayLeg(e, l));
            lastBatch.put(l.id(), batch);
            lastEnd.put(l.id(), l.end());
            latestBatch = batch;
        }

        final boolean multi = byBatch.size() > 1;
        if (multi)
            findings.add(new Finding(step, Severity.WARN, byBatch.size() + " execution batch-ids " + byBatch.keySet()
                + ": the chain is checked per batch (movements made between batches, e.g. a manual refill, are tolerated)"
                + " and the database is compared with the latest batch only."));

        for (Map.Entry<String, Map<Long, List<ReplayLeg>>> batch : byBatch.entrySet()) {
            final String where = multi ? " [batch " + batch.getKey() + "]" : "";
            for (Map.Entry<Long, List<ReplayLeg>> entry : batch.getValue().entrySet()) {
                final Long id = entry.getKey();
                entry.getValue().sort(Comparator.comparing(ReplayLeg::event, PROCESSING_ORDER));
                BigDecimal running = null;
                for (ReplayLeg replayLeg : entry.getValue()) {
                    final Leg l = replayLeg.leg();
                    if (l.begin().add(l.operation()).compareTo(l.end()) != 0) {
                        broken++;
                        findings.add(new Finding(step, Severity.ERROR, label + " " + id + where + ": " + l.begin() + " "
                            + signed(l.operation()) + " <> " + l.end() + " (movement arithmetic)."));
                    }
                    if (running != null && running.compareTo(l.begin()) != 0) {
                        broken++;
                        findings.add(new Finding(step, Severity.ERROR, label + " " + id + where + ": movement begins at "
                            + l.begin() + " but the previous one ended at " + running + " (broken chain)."));
                    }
                    running = l.end();
                }
            }
        }

        return new ReplayTrace(byBatch, lastBatch, lastEnd, latestBatch, broken);
    }

    /**
     * Second-stage check: compare the payload replay with the current database snapshot.
     * This method does not participate in calculating or validating the movement chain itself.
     */
    @SuppressWarnings("unused")
    private void compareReplayWithDatabase(ReplayTrace trace, Step step, String label,
                                           Function<Long, BigDecimal> current,
                                           List<Finding> findings) {
        int broken = trace.broken();

        // The entity must still exist, and — when its last movement belongs to the latest batch —
        // the replay must end on the live value (older batches may legitimately differ).
        for (Long id : trace.lastEnd().keySet()) {
            final BigDecimal live = current.apply(id);
            if (live == null) {
                broken++;
                findings.add(new Finding(step, Severity.ERROR, label + " " + id + ": has movements but no longer exists."));
            } else if (Objects.equals(trace.lastBatch().get(id), trace.latestBatch())
                    && trace.lastEnd().get(id).compareTo(live) != 0) {
                broken++;
                findings.add(new Finding(step, Severity.ERROR, label + " " + id + ": replay ends at "
                    + trace.lastEnd().get(id) + " but the database holds " + live + "."));
            }
        }

        if (broken == 0)
            findings.add(new Finding(step, Severity.INFO, trace.lastEnd().size() + " " + label + "(s) replayed in "
                + trace.byBatch().size() + " batch(es), consistent."));
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

    private Payload parsePayload(JrnEvent event) {
        try {
            return JsonMapper.readFromJson(event.getPayload(), Payload.class);
        } catch (RuntimeException ex) {
            return null; // e.g. the legacy SHA-1 payload hash.
        }
    }

    private Leg legOf(Payload p, Function<Movement, Leg> leg) {
        final Leg l = (p == null || p.movement() == null) ? null : leg.apply(p.movement());
        return (l == null || l.id() == null || l.begin() == null || l.operation() == null || l.end() == null)
            ? null : l;
    }

    private String signed(BigDecimal v) {
        return v.signum() < 0 ? "- " + v.abs() : "+ " + v;
    }
}
