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

import dev.mars.quorus.agent.AgentInfo;
import dev.mars.quorus.controller.raft.RaftNode;
import dev.mars.quorus.controller.state.QuorusStateStore;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * HTTP handler for listing registered agents.
 *
 * <p>
 * Endpoint: {@code GET /api/v1/agents}
 *
 * <p>
 * Returns a list of all registered agents from the Raft state machine.
 *
 * @author Mark Andrew Ray-Smith Cityline Ltd
 * @version 2.0 (Vert.x reactive)
 * @since 2025-12-11
 */
public class AgentListHandler implements Handler<RoutingContext> {

    private static final Logger logger = LoggerFactory.getLogger(AgentListHandler.class);
    private final RaftNode raftNode;

    public AgentListHandler(RaftNode raftNode) {
        this.raftNode = raftNode;
    }

    @Override
    public void handle(RoutingContext ctx) {
        QuorusStateStore stateMachine = (QuorusStateStore) raftNode.getStateStore();
        Map<String, AgentInfo> agents = stateMachine.getAgents();

        JsonArray agentArray = new JsonArray();
        for (AgentInfo agent : agents.values()) {
            agentArray.add(new JsonObject()
                    .put("agentId", agent.getAgentId())
                    .put("hostname", agent.getHostname())
                    .put("address", agent.getAddress())
                    .put("port", agent.getPort())
                    .put("status", agent.getStatus().toString())
                    .put("version", agent.getVersion())
                    .put("region", agent.getRegion())
                    .put("datacenter", agent.getDatacenter())
                    .put("registrationTime", agent.getRegistrationTime())
                    .put("lastHeartbeat", agent.getLastHeartbeat())
                    .put("healthy", agent.isHealthy())
                    .put("available", agent.isAvailable()));
        }

        logger.info("Returning {} agents", agents.size());
        ctx.json(new JsonObject()
                .put("agents", agentArray)
                .put("count", agents.size()));
    }
}
