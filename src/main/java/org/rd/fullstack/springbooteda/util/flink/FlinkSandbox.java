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
import java.util.Collection;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.apache.flink.configuration.Configuration;
import org.apache.flink.configuration.RestOptions;
import org.apache.flink.configuration.JobManagerOptions;
import org.apache.flink.configuration.TaskManagerOptions;
import org.apache.flink.runtime.client.JobStatusMessage;
import org.apache.flink.runtime.messages.webmonitor.ClusterOverview;
import org.apache.flink.runtime.minicluster.MiniCluster;
import org.apache.flink.runtime.minicluster.MiniClusterConfiguration;
import org.apache.flink.runtime.util.EnvironmentInformation;
import org.rd.fullstack.springbooteda.util.TimeMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * FlinkSandbox provides an embedded Flink MiniCluster for testing or local experimentation.
 * Supports configurable number of TaskManagers, slots per TaskManager, and ports.
 * Thread-safe start/stop and optional autoStart via builder.
 */
public class FlinkSandbox implements AutoCloseable {
    private static final Logger logger =
            LoggerFactory.getLogger(FlinkSandbox.class);

    private static final long CLUSTER_TIMEOUT_SECONDS = 30;

    private final int numTaskManagers;
    private final int numSlotsPerTaskManager;

    private final String clusterId;
    private final MiniClusterConfiguration cfg;

    private volatile MiniCluster miniCluster;

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
        private int prometheusPort = 9249;

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

        public Builder prometheusPort(int prometheusPort) {
            this.prometheusPort = prometheusPort;
            return this;
        }

        public FlinkSandbox build() throws Exception {
            if (numTaskManagers <= 0)
                throw new IllegalArgumentException("numTaskManagers must be > 0.");
            if (numSlotsPerTaskManager <= 0)
                throw new IllegalArgumentException("numSlotsPerTaskManager must be > 0.");

            validatePort(restPort, "restPort");
            validatePort(jobManagerPort, "jobManagerPort");
            if (!taskManagerRpcPort.isEmpty()) {
                try {
                    int port = Integer.parseInt(taskManagerRpcPort);
                    validatePort(port, "taskManagerRpcPort");
                } catch (NumberFormatException ex) {
                    // May be a port range (e.g., "6122-6125"), which is valid for Flink.
                }
            }

            Configuration config = new Configuration();

            config.set(RestOptions.PORT, restPort);
            config.set(JobManagerOptions.PORT, jobManagerPort);

            if (activeMetrics) {
                config.setString("metrics.reporter.prom.class",
                                 "org.apache.flink.metrics.prometheus.PrometheusReporter");
                config.setString("metrics.reporter.prom.port",
                                 String.valueOf(prometheusPort));
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

        private void validatePort(int port, String name) {
            if (port != 0 && (port < 1 || port > 65535))
                throw new IllegalArgumentException(name + " must be 0 (auto) or between 1 and 65535.");
        }
    }

    public synchronized void start() throws Exception {
        if (miniCluster != null)
            return;

        MiniCluster cluster = new MiniCluster(cfg);
        cluster.start();
        miniCluster = cluster;
    }

    public synchronized void stop() {
        if (miniCluster == null)
            return;

        try {
            miniCluster.close();
        } catch (Exception ex) {
            logger.warn("Exception closing Flink MiniCluster: {}.", ex);
        } finally {
            miniCluster = null;
        }
    }

    @Override
    public void close() {
        stop();
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
        MiniCluster cluster = requireStartedCluster();
        try {
            return cluster.getRestAddress().get(CLUSTER_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while getting Flink MiniCluster REST address: {}.", ex);
        } catch (ExecutionException ex) {
            throw new IllegalStateException("Error while getting Flink MiniCluster REST address: {}.", ex);
        } catch (TimeoutException ex) {
            throw new IllegalStateException("Timeout while getting Flink MiniCluster REST address: {}.", ex);
        }
    }

    public FlinkDashboard getDashboardData() {
        MiniCluster cluster = requireStartedCluster();
        String uriString = getURI().toString();

        ClusterOverview overview;
        Collection<JobStatusMessage> jobs;
        try {
            overview = cluster.requestClusterOverview().get(CLUSTER_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            jobs     = cluster.listJobs().get(CLUSTER_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while fetching Flink dashboard data: {}.", ex);
        } catch (ExecutionException ex) {
            throw new IllegalStateException("Error while fetching Flink dashboard data: {}.", ex);
        } catch (TimeoutException ex) {
            throw new IllegalStateException("Timeout while fetching Flink dashboard data: {}.", ex);
        }

        return new FlinkDashboard(
            getClusterId(),
            uriString,
            new FlinkSummary(
                overview.getNumTaskManagersConnected(),
                overview.getNumSlotsTotal(),
                overview.getNumSlotsAvailable(),
                overview.getNumJobsRunningOrPending(),
                overview.getNumJobsFinished(),
                overview.getNumJobsCancelled(),
                overview.getNumJobsFailed(),
                EnvironmentInformation.getVersion(),
                EnvironmentInformation.getRevisionInformation().commitId
            ),
            jobs.stream().map(job -> new JobInfo(
                job.getJobId().toString(),
                job.getJobName(),
                job.getJobState().toString(),
                TimeMapper.longTimeToString(job.getStartTime())
            )).toList()
        );
    }

    private MiniCluster requireStartedCluster() {
        MiniCluster cluster = miniCluster;
        if (cluster == null)
            throw new IllegalStateException("FlinkSandbox must be started first.");
        return cluster;
    }

    @Override
    public String toString() {
        String status = isStarted() ? "started" : "stopped";
        return "FlinkSandbox[numTaskManagers=" + this.numTaskManagers
                + ", numSlotsPerTaskManager=" + this.numSlotsPerTaskManager
                + ", status=" + status + "]";
    }
}