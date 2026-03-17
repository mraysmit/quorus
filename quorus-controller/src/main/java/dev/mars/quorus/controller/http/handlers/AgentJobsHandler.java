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
import dev.mars.quorus.controller.state.QuorusStateStore;
import dev.mars.quorus.controller.state.TransferJobSnapshot;
import dev.mars.quorus.core.JobAssignment;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * HTTP handler for agent job polling.
 *
 * <p>Endpoint: {@code GET /api/v1/agents/:agentId/jobs}
 *
 * <p>Returns pending job assignments for a specific agent, enriched with
 * transfer job details (source URI, destination, total bytes).
 *
 * @author Mark Andrew Ray-Smith Cityline Ltd
 * @version 2.0 (Vert.x reactive)
 * @since 2025-12-11
 */
public class AgentJobsHandler implements Handler<RoutingContext> {

    private static final Logger logger = LoggerFactory.getLogger(AgentJobsHandler.class);
    private final QuorusStateStore stateStore;

    public AgentJobsHandler(QuorusStateStore stateStore) {
        this.stateStore = stateStore;
    }

    @Override
    public void handle(RoutingContext ctx) {
        String agentId = ctx.pathParam("agentId");
        QuorusStateStore stateMachine = this.stateStore;

        // Tenant isolation: resolve the agent's tenant before returning any jobs
        AgentInfo agent = stateMachine.getAgent(agentId);
        if (agent == null) {
            ctx.fail(404, new IllegalArgumentException("Agent not found: " + agentId));
            return;
        }
        String agentTenantId = agent.getTenantId();

        Map<String, JobAssignment> allAssignments = stateMachine.getJobAssignments();
        JsonArray pendingJobs = new JsonArray();

        for (JobAssignment assignment : allAssignments.values()) {
            if (!assignment.getAgentId().equals(agentId)) {
                continue;
            }
            if (!assignment.isActive()) {
                continue;
            }

            // Tenant isolation: only return jobs whose tenant matches the agent's tenant
            TransferJobSnapshot transferJob = stateMachine.getTransferJob(assignment.getJobId());
            if (transferJob != null && agentTenantId != null && transferJob.getTenantId() != null
                    && !agentTenantId.equals(transferJob.getTenantId())) {
                logger.warn("Cross-tenant job access blocked: agentId={}, agentTenant={}, jobId={}, jobTenant={}",
                        agentId, agentTenantId, assignment.getJobId(), transferJob.getTenantId());
                continue;
            }

            JsonObject jobInfo = new JsonObject()
                    .put("assignmentId", assignment.getJobId() + ":" + assignment.getAgentId())
                    .put("jobId", assignment.getJobId())
                    .put("agentId", assignment.getAgentId())
                    .put("status", assignment.getStatus().toString())
                    .put("assignedAt", assignment.getAssignedAt().toString());

            if (transferJob != null) {
                jobInfo.put("sourceUri", transferJob.getSourceUri())
                        .put("destinationPath", transferJob.getDestinationPath())
                        .put("totalBytes", transferJob.getTotalBytes());
                if (transferJob.getDescription() != null) {
                    jobInfo.put("description", transferJob.getDescription());
                }
            }

            pendingJobs.add(jobInfo);
        }

        logger.debug("Returning {} pending jobs for agent: agentId={}, tenantId={}", pendingJobs.size(), agentId, agentTenantId);
        ctx.json(new JsonObject()
                .put("pendingJobs", pendingJobs)
                .put("total", pendingJobs.size()));
    }
}

