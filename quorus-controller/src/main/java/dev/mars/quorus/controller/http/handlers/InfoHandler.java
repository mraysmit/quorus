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

import dev.mars.quorus.controller.raft.RaftNode;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;

import java.time.Instant;

/**
 * HTTP handler for API information.
 *
 * <p>Endpoint: {@code GET /api/v1/info}
 *
 * <p>Provides information about the Quorus controller API including:
 * API version, available endpoints, controller information, and system capabilities.
 *
 * @author Mark Andrew Ray-Smith Cityline Ltd
 * @version 2.0 (Vert.x reactive)
 * @since 2025-12-11
 */
public class InfoHandler implements Handler<RoutingContext> {

    private static final String API_VERSION = "v1";

    private final RaftNode raftNode;
    private final String quorusVersion;

    public InfoHandler(RaftNode raftNode, String quorusVersion) {
        this.raftNode = raftNode;
        this.quorusVersion = quorusVersion;
    }

    @Override
    public void handle(RoutingContext ctx) {
        JsonObject info = new JsonObject()
                .put("api", new JsonObject()
                        .put("version", API_VERSION)
                        .put("quorusVersion", quorusVersion)
                        .put("description", "Quorus Distributed File Transfer System API"))
                .put("controller", new JsonObject()
                        .put("nodeId", raftNode.getNodeId())
                        .put("state", raftNode.getState().toString())
                        .put("isLeader", raftNode.isLeader())
                        .put("currentTerm", raftNode.getCurrentTerm()))
                .put("endpoints", buildEndpoints())
                .put("capabilities", new JsonObject()
                        .put("raftConsensus", true)
                        .put("distributedState", true)
                        .put("agentFleetManagement", true)
                        .put("transferJobCoordination", true)
                        .put("prometheusMetrics", true)
                        .put("healthChecks", true))
                .put("timestamp", Instant.now().toString());

        ctx.json(info);
    }

    private JsonObject buildEndpoints() {
        return new JsonObject()
                .put("health", new JsonArray()
                        .add(endpoint("GET", "/health", "Overall system health"))
                        .add(endpoint("GET", "/health/ready", "Readiness probe"))
                        .add(endpoint("GET", "/health/live", "Liveness probe"))
                        .add(endpoint("GET", "/status", "Simple status check")))
                .put("agents", new JsonArray()
                        .add(endpoint("POST", "/api/v1/agents/register", "Register new agent"))
                        .add(endpoint("POST", "/api/v1/agents/heartbeat", "Send agent heartbeat"))
                        .add(endpoint("GET", "/api/v1/agents", "List all registered agents")))
                .put("transfers", new JsonArray()
                        .add(endpoint("POST", "/api/v1/transfers", "Create new transfer job"))
                        .add(endpoint("GET", "/api/v1/transfers/:jobId", "Get transfer job status"))
                        .add(endpoint("DELETE", "/api/v1/transfers/:jobId", "Cancel transfer job")))
                .put("cluster", new JsonArray()
                        .add(endpoint("GET", "/raft/status", "Get cluster status")))
                .put("metrics", new JsonArray()
                        .add(endpoint("GET", "/metrics", "Prometheus metrics")))
                .put("info", new JsonArray()
                        .add(endpoint("GET", "/api/v1/info", "API information and available endpoints")));
    }

    private static JsonObject endpoint(String method, String path, String description) {
        return new JsonObject()
                .put("method", method)
                .put("path", path)
                .put("description", description);
    }
}

