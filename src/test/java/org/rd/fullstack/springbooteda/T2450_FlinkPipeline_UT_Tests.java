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

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.rd.fullstack.springbooteda.config.Application;
import org.rd.fullstack.springbooteda.dao.RequestRepository;
import org.rd.fullstack.springbooteda.dto.PipelineContext;
import org.rd.fullstack.springbooteda.dto.StatsContext;
import org.rd.fullstack.springbooteda.srv.DataGeneratorSrv;
import org.rd.fullstack.springbooteda.srv.PipelineSrv;
import org.rd.fullstack.springbooteda.util.PipelineState;
import org.rd.fullstack.springbooteda.util.Result;
import org.rd.fullstack.springbooteda.util.flink.FlinkSandbox;
import org.rd.fullstack.springbooteda.util.kafka.KafkaSandbox;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * End-to-end integration test of the <strong>Flink-ON</strong> pipeline path, which was not
 * covered by {@link T2400_KafkaFlink_UT_Tests} (those build a standalone Flink job with a
 * {@code KafkaSink} and never touch {@code FlinkService}/{@code PipelineSrv.processFromFlink}).
 *
 * <p>It drives the REAL production flow: {@link PipelineSrv#start} with {@code flink=true}
 * publishes the eligible requests to the Flink source topic; the embedded Flink job (auto-started
 * on {@code ApplicationReadyEvent}) consumes them and its sink ({@code ProcessorSink}) invokes
 * {@code PipelineSrv.processFromFlink()} in-JVM. The test asserts that every published record is
 * actually processed and the pipeline reaches {@code COMPLETED}.</p>
 *
 * <p>This is the regression guard for the cross-classloader bridge bug: under Spring Boot DevTools
 * the sink read a {@code null} static reference and silently dropped every record, so the pipeline
 * never completed. If the bridge breaks again, this test times out waiting for {@code COMPLETED}.</p>
 */
@SpringBootTest(classes = Application.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("Flink-ON pipeline integration (publish -> processFromFlink -> COMPLETED).")
public class T2450_FlinkPipeline_UT_Tests {

    private static final Logger logger =
        LoggerFactory.getLogger(T2450_FlinkPipeline_UT_Tests.class);

    @Autowired
    private KafkaSandbox kafkaSandbox;

    @Autowired
    private FlinkSandbox flinkSandbox;

    @Autowired
    private PipelineSrv pipelineSrv;

    @Autowired
    private DataGeneratorSrv dataGenerator;

    @Autowired
    private RequestRepository requestRepository;

    private static final int  CST_NBR_REQUEST = 10;
    private static final int  CST_MAX_QTY     = 10;
    private static final long CST_INV_QTY     = 1000;

    @Test
    @Order(1)
    public void flinkPipelineProcessesEveryPublishedRecord() throws Exception {

        // Both sandboxes must be up; the Flink job is auto-started on ApplicationReadyEvent.
        assertTrue(kafkaSandbox.isStarted(), "Kafka sandbox is not started.");
        assertTrue(flinkSandbox.isStarted(), "Flink sandbox is not started.");

        // Fresh, self-contained dataset (the Spring context — and its DB — is shared across the
        // @SpringBootTest classes, so we cannot rely on the startup seed being intact here).
        dataGenerator.genRandomInventory(CST_INV_QTY, true);
        dataGenerator.genRandomRequest(CST_NBR_REQUEST, CST_MAX_QTY, true);

        long eligible = requestRepository.findAll().stream()
            .filter(r -> r.getResult() == Result.PENDING || r.getResult() == Result.BACK_ORDER)
            .count();
        assertEquals(CST_NBR_REQUEST, eligible, "Unexpected number of eligible requests.");

        // Arm the pipeline: start() requires the stats state to be READY.
        pipelineSrv.resetStatsContext();

        // Run with the FLINK option ON. publish() targets the Flink source topic; the embedded
        // Flink job consumes it and its sink calls processFromFlink() in-JVM. start() is @Async and
        // returns once the (transactional) publish has committed.
        PipelineContext ctx = new PipelineContext();
        ctx.setFlink(true);
        ctx.setLatence(false);

        pipelineSrv.start(ctx);

        // The Flink job processes asynchronously across its parallel sink subtasks: wait for the
        // pipeline to reach a terminal state. A timeout here means records are not being processed
        // (e.g. the Flink -> Spring bridge is broken again).
        await().atMost(60, TimeUnit.SECONDS).until(() -> {
            PipelineState state = pipelineSrv.getStatsContext().getPipelineState();
            return state == PipelineState.COMPLETED || state == PipelineState.EXCEPTION;
        });

        StatsContext stats = pipelineSrv.getStatsContext();
        logger.info("Final pipeline stats (Flink ON): {}.", stats);

        // Every published record must have flowed through processFromFlink() and completed cleanly.
        assertEquals(PipelineState.COMPLETED, stats.getPipelineState(),
            "Flink pipeline did not complete cleanly: " + stats);
        assertEquals(CST_NBR_REQUEST, stats.getNbrPublished(), "Wrong number published.");
        assertEquals(stats.getNbrPublished(), stats.getNbrProcessed() + stats.getNbrProcessedWithError(),
            "Processed count does not match published count: " + stats);
        assertEquals(0, stats.getNbrProcessedWithError(),
            "No record should have been routed to the Flink DLT: " + stats);
    }
}
