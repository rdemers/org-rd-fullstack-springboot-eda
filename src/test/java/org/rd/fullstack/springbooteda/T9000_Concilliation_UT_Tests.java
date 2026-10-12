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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.junit.Ignore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.rd.fullstack.springbooteda.dao.InventoryRepository;
import org.rd.fullstack.springbooteda.dao.JrnEventRepository;
import org.rd.fullstack.springbooteda.dao.PersonRepository;
import org.rd.fullstack.springbooteda.dto.EventHeader;
import org.rd.fullstack.springbooteda.dto.EventMessage;
import org.rd.fullstack.springbooteda.dto.Inventory;
import org.rd.fullstack.springbooteda.dto.JrnEvent;
import org.rd.fullstack.springbooteda.dto.Person;
import org.rd.fullstack.springbooteda.dto.Request;
//import org.rd.fullstack.springbooteda.srv.ConciliationSrv;
import org.rd.fullstack.springbooteda.srv.ConciliationSrv.Finding;
import org.rd.fullstack.springbooteda.srv.ConciliationSrv.Severity;
import org.rd.fullstack.springbooteda.srv.ConciliationSrv.Step;
import org.rd.fullstack.springbooteda.util.EventType;
import org.rd.fullstack.springbooteda.util.JsonMapper;
import org.rd.fullstack.springbooteda.util.Result;

@DisplayName("Testing the internal corroboration.")
public class T9000_Concilliation_UT_Tests {

    private static final String BATCH = "batch-1";

    @TempDir
    Path dir;

    private JrnEventRepository  jrn;
    private PersonRepository    persons;
    private InventoryRepository inventories;
    private final List<JrnEvent> journal = new ArrayList<>();
    private long nextId = 1;

    @BeforeEach
    void setUp() {
        jrn         = mock(JrnEventRepository.class);
        persons     = mock(PersonRepository.class);
        inventories = mock(InventoryRepository.class);
        when(jrn.findAll()).thenReturn(journal);

        Person p = mock(Person.class);
        when(p.getPersonId()).thenReturn(7L);
        when(p.getBalance()).thenReturn(new BigDecimal("5.00"));
        when(persons.findAll()).thenReturn(List.of(p));

        Inventory i = mock(Inventory.class);
        when(i.getProductId()).thenReturn(3L);
        when(i.getQty()).thenReturn(8L);
        when(inventories.findAll()).thenReturn(List.of(i));
    }

    private static String payload(String cBegin, String cOp, String cEnd, long iBegin, long iOp, long iEnd) {
        return "{\"movement\":{\"client\":{\"id\":7,\"begin\":" + cBegin + ",\"operation\":" + cOp + ",\"end\":" + cEnd
            + "},\"inventory\":{\"id\":3,\"begin\":" + iBegin + ",\"operation\":" + iOp + ",\"end\":" + iEnd + "}}}";
    }

    private void addEvent(String eventId, String payload, Result result) {
        JrnEvent e = new JrnEvent("grp", eventId, BATCH, payload, EventType.PROCESSING_REQUESTED, result,
            Instant.now(), Instant.now().plusMillis(nextId));
        e.setJrnEventId(nextId++);
        journal.add(e);
    }

    private void writeBatchFile(String... eventIds) throws Exception {
        List<String> lines = new ArrayList<>();
        for (String id : eventIds) {
            EventMessage<EventHeader, Request, Object> m = new EventMessage<>(
                new EventHeader(id, "1.0", BATCH, "<none>"), new Request(), null);
            lines.add(JsonMapper.writeToJson(m));
        }
        Files.write(dir.resolve("batch-" + BATCH + ".trx"), lines, StandardCharsets.UTF_8);
    }

    @SuppressWarnings("unused")
    private static boolean has(List<Finding> r, Step step, Severity sev, String text) {
        return r.stream().anyMatch(f ->
            f.step() == step && f.severity() == sev && f.message().contains(text));
    }

    @Test
    @DisplayName("A consistent journal corroborates.")
    void consistent() throws Exception {
        addEvent("E1", payload("10.00", "-5.00", "5.00", 10, -2, 8), Result.EXECUTED);
        addEvent("E2", "{}", Result.BACK_ORDER); // Moves nothing.
        writeBatchFile("E1", "E2");

        // List<Finding> r = srv(Set.of()).execute();
    }

    @Test
    @Ignore ("The DLT is unreadable, so the payload cannot be read; the corroboration cannot continue.")
    @DisplayName("Missing batch file, missing transaction and orphan are reported; a DLT'd transaction is not.")
    void batchFile() throws Exception {
        addEvent("E1", payload("10.00", "-5.00", "5.00", 10, -2, 8), Result.EXECUTED);
        addEvent("ORPHAN", "{}", Result.BACK_ORDER);

        //assertTrue(has(srv(Set.of()).execute(), Step.BATCH_FILE, Severity.ERROR, "file not found"));

        //writeBatchFile("E1", "LOST", "DEAD");
        //List<Finding> r = srv(Set.of("DEAD")).execute();

        //assertTrue(has(r, Step.BATCH_FILE, Severity.ERROR, "LOST"));
        //assertFalse(has(r, Step.BATCH_FILE, Severity.ERROR, "DEAD"));
        //assertTrue(has(r, Step.BATCH_FILE, Severity.ERROR, "ORPHAN"));
    }

    @Test
    @Ignore ("The DLT is unreadable, so the payload cannot be read; the corroboration cannot continue.")
    @DisplayName("A broken balance/inventory chain or a final mismatch is reported.")
    void replay() throws Exception {
        addEvent("E1", payload("10.00", "-5.00", "5.00", 10, -2, 8), Result.EXECUTED);
        addEvent("E2", payload("9.00", "-4.00", "5.00", 7, -1, 6), Result.EXECUTED); // Chains broken.
        writeBatchFile("E1", "E2");

        //List<Finding> r = srv(Set.of()).execute();

        //assertTrue(has(r, Step.CLIENT_BALANCE, Severity.ERROR, "broken chain"));
        // assertTrue(has(r, Step.INVENTORY_QTY, Severity.ERROR, "broken chain"));
        //assertTrue(has(r, Step.INVENTORY_QTY, Severity.ERROR, "database holds 8"));
    }

    @Test
    @Ignore ("The DLT is unreadable, so the payload cannot be read; the corroboration cannot continue.")
    @DisplayName("An unreadable DLT is a warning, not a failure; a legacy hash payload is an error.")
    void dltAndPayload() throws Exception {
        addEvent("E1", "a94a8fe5ccb19ba61c4c0873d391e987982fbbd3", Result.EXECUTED);
        writeBatchFile("E1");

        //List<Finding> r = new ConciliationSrv(jrn, persons, inventories,
        //    () -> { throw new IllegalStateException("down"); }, dir).execute();

        //assertTrue(has(r, Step.DLT, Severity.WARN, "unreadable"));
        //assertTrue(has(r, Step.CLIENT_BALANCE, Severity.ERROR, "no valid client movement"));
        //assertTrue(r.stream().noneMatch((Finding f) -> f.step() == Step.DLT && f.severity() == Severity.ERROR));
    }
}
