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
import dev.mars.quorus.controller.state.AgentCommand;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * HTTP handler for agent registration.
 *
 * <p>Endpoint: {@code POST /api/v1/agents/register}
 *
 * <p>Accepts AgentInfo JSON and submits registration command to Raft for distributed consensus.
 *
 * @author Mark Andrew Ray-Smith Cityline Ltd
 * @version 2.0 (Vert.x reactive)
 * @since 2025-12-11
 */
public class AgentRegistrationHandler implements Handler<RoutingContext> {

    private static final Logger logger = LoggerFactory.getLogger(AgentRegistrationHandler.class);
    private final RaftNode raftNode;

    public AgentRegistrationHandler(RaftNode raftNode) {
        this.raftNode = raftNode;
    }

    @Override
    public void handle(RoutingContext ctx) {
        try {
            JsonObject body = ctx.body().asJsonObject();
            AgentInfo agentInfo = body.mapTo(AgentInfo.class);

            if (agentInfo.getAgentId() == null || agentInfo.getAgentId().isEmpty()) {
                ctx.fail(400, new IllegalArgumentException("Missing required field: agentId"));
                return;
            }

            logger.info("Registering agent: agentId={}, hostname={}", 
                    agentInfo.getAgentId(), agentInfo.getHostname());

            AgentCommand command = AgentCommand.register(agentInfo);
            raftNode.submitCommand(command)
                    .onSuccess(result -> {
                        ctx.response().setStatusCode(201);
                        ctx.json(new JsonObject()
                                .put("success", true)
                                .put("agentId", agentInfo.getAgentId()));
                    })
                    .onFailure(ctx::fail);
        } catch (Exception e) {
            logger.error("Failed to register agent: {}", e.getMessage());
            logger.debug("Stack trace for agent registration failure", e);
            ctx.fail(e);
        }
    }
}

