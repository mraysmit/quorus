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
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;

/**
 * HTTP handler for API information.
 *
 * <p>Endpoint: {@code GET /api/v1/info}
 *
 * <p>Provides the API version, controller information and system capabilities, and the
 * location of the OpenAPI contract. The contract is the only endpoint inventory: its route set
 * is verified against the registered routes, so this handler does not keep a second list.
 *
 * @author Mark Andrew Ray-Smith Cityline Ltd
 * @version 2.0 (Vert.x reactive)
 * @since 2025-12-11
 */
public class InfoHandler implements Handler<RoutingContext> {

    private static final Logger logger = LoggerFactory.getLogger(InfoHandler.class);
    private static final String API_VERSION = "v1";
    private static final String OPENAPI_PATH = "/api/v1/openapi.yaml";

    private final RaftNode raftNode;
    private final String quorusVersion;

    public InfoHandler(RaftNode raftNode, String quorusVersion) {
        this.raftNode = raftNode;
        this.quorusVersion = quorusVersion;
    }

    @Override
    public void handle(RoutingContext ctx) {
        logger.debug("API info requested");
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
                .put("openApi", OPENAPI_PATH)
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
}

