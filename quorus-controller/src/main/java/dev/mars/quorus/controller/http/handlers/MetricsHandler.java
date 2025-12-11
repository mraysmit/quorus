/*
 * Copyright 2025 Mark Andrew Ray-Smith Cityline Ltd
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package dev.mars.quorus.controller.http.handlers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import dev.mars.quorus.agent.AgentInfo;
import dev.mars.quorus.agent.AgentStatus;
import dev.mars.quorus.controller.raft.RaftNode;
import dev.mars.quorus.controller.state.QuorusStateMachine;
import dev.mars.quorus.controller.state.TransferJobSnapshot;
import dev.mars.quorus.core.TransferStatus;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * HTTP handler for system metrics.
 *
 * Endpoint: GET /metrics
 *
 * Provides Prometheus-compatible metrics and JSON metrics for monitoring.
 * Includes Raft cluster metrics, agent fleet metrics, and transfer job metrics.
 *
 * @author Mark Andrew Ray-Smith Cityline Ltd
 * @since 2.0
 */
public class MetricsHandler implements HttpHandler {

    private static final Logger logger = Logger.getLogger(MetricsHandler.class.getName());
    private final RaftNode raftNode;
    private final ObjectMapper objectMapper;

    public MetricsHandler(RaftNode raftNode) {
        this.raftNode = raftNode;
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        if (!"GET".equals(exchange.getRequestMethod())) {
            sendJsonResponse(exchange, 405, Map.of("error", "Method not allowed"));
            return;
        }

        try {
            // Check Accept header to determine format
            String acceptHeader = exchange.getRequestHeaders().getFirst("Accept");
            boolean wantsPrometheus = acceptHeader != null && acceptHeader.contains("text/plain");

            if (wantsPrometheus) {
                sendPrometheusMetrics(exchange);
            } else {
                sendJsonMetrics(exchange);
            }

        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error generating metrics", e);
            sendJsonResponse(exchange, 500, Map.of(
                    "error", "Internal server error",
                    "message", e.getMessage()
            ));
        }
    }

    private void sendJsonMetrics(HttpExchange exchange) throws IOException {
        QuorusStateMachine stateMachine = (QuorusStateMachine) raftNode.getStateMachine();

        Map<String, Object> metrics = new HashMap<>();

        // Cluster metrics
        Map<String, Object> clusterMetrics = new HashMap<>();
        clusterMetrics.put("nodeId", raftNode.getNodeId());
        clusterMetrics.put("state", raftNode.getState().toString());
        clusterMetrics.put("currentTerm", raftNode.getCurrentTerm());
        clusterMetrics.put("isLeader", raftNode.isLeader());
        clusterMetrics.put("leaderId", raftNode.getLeaderId());
        clusterMetrics.put("lastAppliedIndex", stateMachine.getLastAppliedIndex());
        metrics.put("cluster", clusterMetrics);

        // Agent metrics
        Map<String, Object> agentMetrics = new HashMap<>();
        Map<String, AgentInfo> agents = stateMachine.getAgents();
        agentMetrics.put("totalAgents", agents.size());

        Map<String, Long> agentsByStatus = new HashMap<>();
        for (AgentStatus status : AgentStatus.values()) {
            long count = agents.values().stream()
                    .filter(a -> a.getStatus() == status)
                    .count();
            if (count > 0) {
                agentsByStatus.put(status.toString().toLowerCase(), count);
            }
        }
        agentMetrics.put("agentsByStatus", agentsByStatus);

        // Calculate healthy agents (heartbeat within last 60 seconds)
        Instant sixtySecondsAgo = Instant.now().minusSeconds(60);
        long healthyAgents = agents.values().stream()
                .filter(a -> a.getLastHeartbeat() != null && a.getLastHeartbeat().isAfter(sixtySecondsAgo))
                .count();
        agentMetrics.put("healthyAgents", healthyAgents);

        metrics.put("agents", agentMetrics);

        // Transfer job metrics
        Map<String, Object> transferMetrics = new HashMap<>();
        Map<String, TransferJobSnapshot> jobs = stateMachine.getTransferJobs();
        transferMetrics.put("totalJobs", jobs.size());

        Map<String, Long> jobsByStatus = new HashMap<>();
        for (TransferStatus status : TransferStatus.values()) {
            long count = jobs.values().stream()
                    .filter(j -> j.getStatus() == status)
                    .count();
            if (count > 0) {
                jobsByStatus.put(status.toString().toLowerCase(), count);
            }
        }
        transferMetrics.put("jobsByStatus", jobsByStatus);

        // Calculate total bytes transferred
        long totalBytesTransferred = jobs.values().stream()
                .mapToLong(TransferJobSnapshot::getBytesTransferred)
                .sum();
        transferMetrics.put("totalBytesTransferred", totalBytesTransferred);

        metrics.put("transfers", transferMetrics);

        // System metrics
        Map<String, Object> systemMetrics = new HashMap<>();
        Runtime runtime = Runtime.getRuntime();
        systemMetrics.put("memoryUsed", runtime.totalMemory() - runtime.freeMemory());
        systemMetrics.put("memoryTotal", runtime.totalMemory());
        systemMetrics.put("memoryMax", runtime.maxMemory());
        systemMetrics.put("cpuCores", runtime.availableProcessors());
        systemMetrics.put("timestamp", Instant.now().toString());
        metrics.put("system", systemMetrics);

        sendJsonResponse(exchange, 200, metrics);
    }

    private void sendPrometheusMetrics(HttpExchange exchange) throws IOException {
        QuorusStateMachine stateMachine = (QuorusStateMachine) raftNode.getStateMachine();
        StringBuilder prometheus = new StringBuilder();

        // Cluster metrics
        prometheus.append("# HELP quorus_cluster_state Current Raft state (0=FOLLOWER, 1=CANDIDATE, 2=LEADER)\n");
        prometheus.append("# TYPE quorus_cluster_state gauge\n");
        prometheus.append("quorus_cluster_state{node=\"").append(raftNode.getNodeId()).append("\"} ");
        prometheus.append(raftNode.getState().ordinal()).append("\n\n");

        prometheus.append("# HELP quorus_cluster_term Current Raft term\n");
        prometheus.append("# TYPE quorus_cluster_term counter\n");
        prometheus.append("quorus_cluster_term{node=\"").append(raftNode.getNodeId()).append("\"} ");
        prometheus.append(raftNode.getCurrentTerm()).append("\n\n");

        prometheus.append("# HELP quorus_cluster_is_leader Whether this node is the leader (1=yes, 0=no)\n");
        prometheus.append("# TYPE quorus_cluster_is_leader gauge\n");
        prometheus.append("quorus_cluster_is_leader{node=\"").append(raftNode.getNodeId()).append("\"} ");
        prometheus.append(raftNode.isLeader() ? "1" : "0").append("\n\n");

        prometheus.append("# HELP quorus_cluster_last_applied_index Last applied log index\n");
        prometheus.append("# TYPE quorus_cluster_last_applied_index counter\n");
        prometheus.append("quorus_cluster_last_applied_index{node=\"").append(raftNode.getNodeId()).append("\"} ");
        prometheus.append(stateMachine.getLastAppliedIndex()).append("\n\n");

        // Agent metrics
        Map<String, AgentInfo> agents = stateMachine.getAgents();
        prometheus.append("# HELP quorus_agents_total Total number of registered agents\n");
        prometheus.append("# TYPE quorus_agents_total gauge\n");
        prometheus.append("quorus_agents_total ").append(agents.size()).append("\n\n");

        prometheus.append("# HELP quorus_agents_by_status Number of agents by status\n");
        prometheus.append("# TYPE quorus_agents_by_status gauge\n");
        for (AgentStatus status : AgentStatus.values()) {
            long count = agents.values().stream()
                    .filter(a -> a.getStatus() == status)
                    .count();
            prometheus.append("quorus_agents_by_status{status=\"").append(status.toString().toLowerCase()).append("\"} ");
            prometheus.append(count).append("\n");
        }
        prometheus.append("\n");

        // Healthy agents (heartbeat within last 60 seconds)
        Instant sixtySecondsAgo = Instant.now().minusSeconds(60);
        long healthyAgents = agents.values().stream()
                .filter(a -> a.getLastHeartbeat() != null && a.getLastHeartbeat().isAfter(sixtySecondsAgo))
                .count();
        prometheus.append("# HELP quorus_agents_healthy Number of agents with recent heartbeat\n");
        prometheus.append("# TYPE quorus_agents_healthy gauge\n");
        prometheus.append("quorus_agents_healthy ").append(healthyAgents).append("\n\n");

        // Transfer job metrics
        Map<String, TransferJobSnapshot> jobs = stateMachine.getTransferJobs();
        prometheus.append("# HELP quorus_transfers_total Total number of transfer jobs\n");
        prometheus.append("# TYPE quorus_transfers_total gauge\n");
        prometheus.append("quorus_transfers_total ").append(jobs.size()).append("\n\n");

        prometheus.append("# HELP quorus_transfers_by_status Number of transfers by status\n");
        prometheus.append("# TYPE quorus_transfers_by_status gauge\n");
        for (TransferStatus status : TransferStatus.values()) {
            long count = jobs.values().stream()
                    .filter(j -> j.getStatus() == status)
                    .count();
            prometheus.append("quorus_transfers_by_status{status=\"").append(status.toString().toLowerCase()).append("\"} ");
            prometheus.append(count).append("\n");
        }
        prometheus.append("\n");

        // Total bytes transferred
        long totalBytesTransferred = jobs.values().stream()
                .mapToLong(TransferJobSnapshot::getBytesTransferred)
                .sum();
        prometheus.append("# HELP quorus_transfer_bytes_total Total bytes transferred across all jobs\n");
        prometheus.append("# TYPE quorus_transfer_bytes_total counter\n");
        prometheus.append("quorus_transfer_bytes_total ").append(totalBytesTransferred).append("\n\n");

        // System metrics
        Runtime runtime = Runtime.getRuntime();
        prometheus.append("# HELP quorus_system_memory_used_bytes Memory used by JVM\n");
        prometheus.append("# TYPE quorus_system_memory_used_bytes gauge\n");
        prometheus.append("quorus_system_memory_used_bytes ").append(runtime.totalMemory() - runtime.freeMemory()).append("\n\n");

        prometheus.append("# HELP quorus_system_memory_total_bytes Total memory allocated to JVM\n");
        prometheus.append("# TYPE quorus_system_memory_total_bytes gauge\n");
        prometheus.append("quorus_system_memory_total_bytes ").append(runtime.totalMemory()).append("\n\n");

        prometheus.append("# HELP quorus_system_memory_max_bytes Maximum memory available to JVM\n");
        prometheus.append("# TYPE quorus_system_memory_max_bytes gauge\n");
        prometheus.append("quorus_system_memory_max_bytes ").append(runtime.maxMemory()).append("\n\n");

        prometheus.append("# HELP quorus_system_cpu_cores Number of CPU cores available\n");
        prometheus.append("# TYPE quorus_system_cpu_cores gauge\n");
        prometheus.append("quorus_system_cpu_cores ").append(runtime.availableProcessors()).append("\n");

        // Send Prometheus format response
        byte[] responseBytes = prometheus.toString().getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/plain; version=0.0.4");
        exchange.sendResponseHeaders(200, responseBytes.length);

        try (OutputStream os = exchange.getResponseBody()) {
            os.write(responseBytes);
        }
    }

    private void sendJsonResponse(HttpExchange exchange, int statusCode, Object data) throws IOException {
        String json = objectMapper.writeValueAsString(data);
        byte[] responseBytes = json.getBytes(StandardCharsets.UTF_8);

        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(statusCode, responseBytes.length);

        try (OutputStream os = exchange.getResponseBody()) {
            os.write(responseBytes);
        }
    }
}
