/*
 * Copyright 2026 Mark Andrew Ray-Smith Cityline Ltd
 * Licensed under the Apache License, Version 2.0.
 */
package dev.mars.quorus.controller.http.handlers;

import dev.mars.quorus.agent.AgentInfo;
import dev.mars.quorus.controller.http.ErrorCode;
import dev.mars.quorus.controller.http.QuorusApiException;
import dev.mars.quorus.controller.raft.RaftNode;
import dev.mars.quorus.controller.security.IdentityType;
import dev.mars.quorus.controller.security.SecurityContext;
import dev.mars.quorus.controller.security.SecurityIdentity;
import dev.mars.quorus.controller.state.AgentCommand;
import dev.mars.quorus.controller.state.CommandResult;
import dev.mars.quorus.controller.state.QuorusStateStore;
import io.vertx.core.Handler;
import io.vertx.ext.web.RoutingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * HTTP handler for agent deregistration.
 *
 * <p>Endpoint: {@code DELETE /api/v1/agents/{agentId}}
 *
 * <p>An agent identity may deregister only itself. The command is committed through Raft; the state
 * machine refuses it while the agent holds an active assignment.
 */
public class AgentDeregistrationHandler implements Handler<RoutingContext> {

    private static final Logger logger = LoggerFactory.getLogger(AgentDeregistrationHandler.class);
    private final RaftNode raftNode;
    private final QuorusStateStore stateStore;

    public AgentDeregistrationHandler(RaftNode raftNode, QuorusStateStore stateStore) {
        this.raftNode = raftNode;
        this.stateStore = stateStore;
    }

    @Override
    public void handle(RoutingContext ctx) {
        try {
            String agentId = ctx.pathParam("agentId");
            SecurityIdentity identity = SecurityContext.identity(ctx);
            if (identity != null && identity.type() == IdentityType.AGENT
                    && !identity.principalId().equals(agentId)) {
                ctx.fail(new QuorusApiException(ErrorCode.FORBIDDEN,
                        "An agent identity may deregister only its own agentId"));
                return;
            }
            AgentInfo registeredAgent = stateStore.findAgent(agentId)
                    .orElseThrow(() -> QuorusApiException.notFound(ErrorCode.AGENT_NOT_FOUND, agentId));
            SecurityContext.trustedTenant(ctx, registeredAgent.getTenantId());

            logger.info("Deregistering agent: agentId={}", agentId);
            raftNode.submitCommand(AgentCommand.deregister(agentId))
                    .onSuccess(result -> {
                        if (CommandResultHandler.failIfRejected(ctx, result)) return;
                        if (result instanceof CommandResult.NotFound<?> nf) {
                            ctx.fail(QuorusApiException.notFound(ErrorCode.AGENT_NOT_FOUND, nf.id()));
                            return;
                        }
                        logger.info("Agent deregistered via Raft: agentId={}", agentId);
                        ctx.response().setStatusCode(204).end();
                    })
                    .onFailure(ctx::fail);
        } catch (Exception e) {
            logger.error("Failed to deregister agent: {}", e.getMessage());
            logger.debug("Stack trace for agent deregistration failure", e);
            ctx.fail(e);
        }
    }
}
