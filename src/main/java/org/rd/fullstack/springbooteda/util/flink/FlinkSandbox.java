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
package org.rd.fullstack.springbooteda.util.flink;

import java.net.URI;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutionException;

import org.apache.flink.configuration.Configuration;
import org.apache.flink.configuration.RestOptions;
import org.apache.flink.configuration.JobManagerOptions;
import org.apache.flink.configuration.TaskManagerOptions;
import org.apache.flink.runtime.minicluster.MiniCluster;
import org.apache.flink.runtime.minicluster.MiniClusterConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.annotation.PreDestroy;

/**
 * FlinkSandbox provides an embedded Flink MiniCluster for testing or local experimentation.
 * <p>
 * Supports configurable number of TaskManagers, slots per TaskManager, and ports.
 * Thread-safe start/stop and optional autoStart via builder.
 */
public class FlinkSandbox {
    private static final Logger logger =
            LoggerFactory.getLogger(FlinkSandbox.class);

    private final int numTaskManagers;
    private final int numSlotsPerTaskManager;
    
    private String clusterId;
    private final MiniClusterConfiguration cfg;

    private MiniCluster miniCluster;

    private FlinkSandbox(String clusterId, MiniClusterConfiguration cfg, int numTaskManagers, int numSlotsPerTaskManager) {
        this.numTaskManagers = numTaskManagers;
        this.numSlotsPerTaskManager = numSlotsPerTaskManager;
        this.clusterId = clusterId;
        this.cfg = cfg;
        this.miniCluster = null;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private int restPort = 0;               // 0 = auto-select.
        private int jobManagerPort = 0;         // 0 = auto-select.
        private String taskManagerRpcPort = ""; // empty = auto-select.

        private int numTaskManagers = 1;
        private int numSlotsPerTaskManager = 4;

        private boolean autoStart = false;
        private boolean activeMetrics = false;

        public Builder restPort(int port) {
            this.restPort = port;
            return this;
        }

        public Builder jobManagerPort(int port) {
            this.jobManagerPort = port;
            return this;
        }

        public Builder taskManagerRpcPort(String port) {
            this.taskManagerRpcPort = port;
            return this;
        }

        public Builder numTaskManagers(int numTaskManagers) {
            this.numTaskManagers = numTaskManagers;
            return this;
        }

        public Builder numSlotsPerTaskManager(int numSlotsPerTaskManager) {
            this.numSlotsPerTaskManager = numSlotsPerTaskManager;
            return this;
        }

        public Builder autoStart(boolean autoStart) {
            this.autoStart = autoStart;
            return this;
        }

        public Builder activeMetrics(boolean activeMetrics) {
            this.activeMetrics = activeMetrics;
            return this;
        }

        public FlinkSandbox build() throws Exception {
            Configuration config = new Configuration();

            config.set(RestOptions.PORT, restPort);
            config.set(JobManagerOptions.PORT, jobManagerPort);

            // Configuring the Metric Reporter.
            if (activeMetrics) {
                config.setString("metrics.reporter.prom.class",
                                 "org.apache.flink.metrics.prometheus.PrometheusReporter");
                config.setString("metrics.reporter.prom.port",
                                 "9249"); // Fixme .. Should not be hardcoded.
            }

            if (!taskManagerRpcPort.isEmpty())
                config.set(TaskManagerOptions.RPC_PORT, taskManagerRpcPort);

            String clusterId = UUID.randomUUID().toString();
            MiniClusterConfiguration cfg = new MiniClusterConfiguration.Builder()
                    .setConfiguration(config)
                    .setNumTaskManagers(numTaskManagers)
                    .setNumSlotsPerTaskManager(numSlotsPerTaskManager)
                    .build();

            FlinkSandbox flinkSandbox = new FlinkSandbox(clusterId, cfg, numTaskManagers, numSlotsPerTaskManager);

            if (autoStart) 
                flinkSandbox.start();

            return flinkSandbox;
        }
    }

    public synchronized void start() throws Exception {
        if (miniCluster != null) 
            return;

        miniCluster = new MiniCluster(cfg);
        miniCluster.start();
    }

    @PreDestroy
    public synchronized void stop() throws Exception {
        if (miniCluster == null) 
            return;

        miniCluster.close();
        miniCluster = null;
    }

    public boolean isStarted() {
        return miniCluster != null;
    }

    public String getClusterId() {
        return this.clusterId;
    }

    public MiniClusterConfiguration getCfg() {
        return this.cfg;
    }

    public int getNumTaskManagers() {
        return this.numTaskManagers;
    }

    public int getNumSlotsPerTaskManager() {
        return this.numSlotsPerTaskManager;
    }

    public URI getURI() {
        requireStarted();
        try {
            return miniCluster.getRestAddress().get();
        } catch (InterruptedException ex) {
            logger.error("Interrupted while getting Flink MiniCluster REST address: {}.", ex);
        } catch (ExecutionException ex) {
            logger.error("Error while getting Flink MiniCluster REST address: {}.", ex);
        }
        return null;
    }

    public FlinkDashboard getDashboardData() {
        requireStarted();
        String uriString = getURI().toString();
        if (uriString == null)
            throw new IllegalStateException("Flink MiniCluster REST address is not available.");

        FlinkDashboard flinkDashboard = new FlinkDashboard(
            new ClusterInfo(
                getClusterId(),
                uriString,
                ClusterStatus.OK
            ),
            List.of(
                new JobInfo(
                    "7684be6004e4e955c2a558a9bc463f65",
                    "Streaming WordCount",
                    JobState.RUNNING,
                    new TaskStats(4, 4),
                    520_000
                ),
                new JobInfo(
                    "ab78dcdbb1db025539e30217ec54ee16",
                    "Inventory Analytics",
                    JobState.RESTARTING,
                    new TaskStats(0, 2),
                    45_000
                )
            ),
            List.of(
                new Notification(
                    "1",
                    NotificationLevel.CRITICAL,
                    "02:07",
                    "billing – Checkpoints failing (5x)"
                )
            )
        );
        return flinkDashboard;
    }

    private void requireStarted() {
        if (!isStarted())
            throw new IllegalStateException("FlinkSandbox must be started first.");
    }

    @Override
    public String toString() {
        String status = isStarted() ? "started" : "stopped";
        return "FlinkSandbox[numTaskManagers=" + this.numTaskManagers
                + ", numSlotsPerTaskManager=" + this.numSlotsPerTaskManager
                + ", status=" + status + "]";
    }
}