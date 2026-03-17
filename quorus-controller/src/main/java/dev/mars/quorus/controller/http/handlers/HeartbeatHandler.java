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
import dev.mars.quorus.agent.AgentStatus;
import dev.mars.quorus.controller.http.ErrorCode;
import dev.mars.quorus.controller.http.QuorusApiException;
import dev.mars.quorus.controller.raft.RaftNode;
import dev.mars.quorus.controller.state.AgentCommand;
import dev.mars.quorus.controller.state.CommandResult;
import dev.mars.quorus.controller.state.QuorusStateStore;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;

/**
 * HTTP handler for agent heartbeats.
 *
 * <p>Endpoint: {@code POST /api/v1/agents/heartbeat}
 *
 * <p>Accepts heartbeat data from agents and submits heartbeat command to Raft
 * for distributed consensus. Updates the agent's last heartbeat timestamp
 * and optionally status.
 *
 * @author Mark Andrew Ray-Smith Cityline Ltd
 * @version 2.0 (Vert.x reactive)
 * @since 2025-12-11
 */
public class HeartbeatHandler implements Handler<RoutingContext> {

    private static final Logger logger = LoggerFactory.getLogger(HeartbeatHandler.class);
    private final RaftNode raftNode;
    private final QuorusStateStore stateStore;

    public HeartbeatHandler(RaftNode raftNode, QuorusStateStore stateStore) {
        this.raftNode = raftNode;
        this.stateStore = stateStore;
    }

    @Override
    public void handle(RoutingContext ctx) {
        try {
            JsonObject body = ctx.body().asJsonObject();
            if (body == null) {
                ctx.fail(400, new IllegalArgumentException("Request body is required"));
                return;
            }

            String agentId = body.getString("agentId");
            if (agentId == null || agentId.isEmpty()) {
                ctx.fail(400, new IllegalArgumentException("Missing required field: agentId"));
                return;
            }

            logger.debug("Processing heartbeat: agentId={}", agentId);

            // Verify agent exists (throws HTTP 404 if not found)
            AgentInfo registeredAgent = stateStore.findAgent(agentId)
                    .orElseThrow(() -> QuorusApiException.notFound(ErrorCode.AGENT_NOT_FOUND, agentId));

            // Tenant isolation: if heartbeat carries tenantId, verify it matches the registered agent's tenant
            String heartbeatTenantId = body.getString("tenantId");
            if (heartbeatTenantId != null && registeredAgent.getTenantId() != null
                    && !heartbeatTenantId.equals(registeredAgent.getTenantId())) {
                logger.warn("Cross-tenant heartbeat blocked: agentId={}, heartbeatTenant={}, registeredTenant={}",
                        agentId, heartbeatTenantId, registeredAgent.getTenantId());
                ctx.fail(403, new IllegalArgumentException(
                        "Heartbeat tenantId does not match agent's registered tenant"));
                return;
            }

            // Extract optional status
            AgentStatus status = null;
            String statusStr = body.getString("status");
            if (statusStr != null) {
                try {
                    status = AgentStatus.valueOf(statusStr.toUpperCase());
                } catch (IllegalArgumentException e) {
                    logger.warn("Invalid status in heartbeat: agentId={}, status={}", agentId, statusStr);
                }
            }

            // Create and submit heartbeat command
            AgentCommand command = (status != null)
                    ? AgentCommand.heartbeat(agentId, status, Instant.now())
                    : AgentCommand.heartbeat(agentId);

            raftNode.submitCommand(command)
                    .onSuccess(result -> {
                        if (result instanceof CommandResult.NotFound<?> nf) {
                            logger.warn("Agent disappeared during heartbeat (race condition): agentId={}", nf.id());
                            ctx.fail(QuorusApiException.notFound(ErrorCode.AGENT_NOT_FOUND, nf.id()));
                            return;
                        }

                        logger.debug("Heartbeat processed: agentId={}", agentId);

                        JsonObject response = new JsonObject()
                                .put("success", true)
                                .put("agentId", agentId)
                                .put("message", "Heartbeat received");

                        if (result instanceof CommandResult.Success<?> success
                                && success.entity() instanceof AgentInfo updatedAgent) {
                            response.put("status", updatedAgent.getStatus().toString());
                            response.put("lastHeartbeat", updatedAgent.getLastHeartbeat().toString());
                        }

                        // Echo sequence number if provided
                        Integer seqNum = body.getInteger("sequenceNumber");
                        if (seqNum != null) {
                            response.put("acknowledgedSequenceNumber", seqNum);
                        }

                        ctx.json(response);
                    })
                    .onFailure(ctx::fail);
        } catch (Exception e) {
            logger.error("Error processing heartbeat: {}", e.getMessage());
            logger.debug("Stack trace for heartbeat processing error", e);
            ctx.fail(e);
        }
    }
}

