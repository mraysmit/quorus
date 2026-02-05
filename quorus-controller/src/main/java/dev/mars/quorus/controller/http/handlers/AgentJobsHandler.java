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
import dev.mars.quorus.controller.raft.RaftNode;
import dev.mars.quorus.controller.state.QuorusStateMachine;
import dev.mars.quorus.core.JobAssignment;
import dev.mars.quorus.core.JobAssignmentStatus;
import dev.mars.quorus.controller.state.TransferJobSnapshot;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * HTTP handler for agent job polling.
 * 
 * Endpoint: GET /api/v1/agents/{agentId}/jobs
 * 
 * Returns pending job assignments for a specific agent.
 * 
 * @author Mark Andrew Ray-Smith Cityline Ltd
 * @since 2025-12-11
 * @version 1.0
 */
public class AgentJobsHandler implements HttpHandler {

    private static final Logger logger = LoggerFactory.getLogger(AgentJobsHandler.class);
    private final RaftNode raftNode;
    private final ObjectMapper objectMapper;

    public AgentJobsHandler(RaftNode raftNode) {
        this.raftNode = raftNode;
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
        logger.debug("AgentJobsHandler initialized with raftNode={}", raftNode.getNodeId());
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String method = exchange.getRequestMethod();
        String path = exchange.getRequestURI().getPath();
        logger.debug("handle() entry: method={}, path={}", method, path);
        
        if (!"GET".equals(method)) {
            logger.debug("Method not allowed: {}", method);
            sendJsonResponse(exchange, 405, Map.of("error", "Method not allowed"));
            return;
        }

        try {
            // Extract agentId from path: /api/v1/agents/{agentId}/jobs
            String[] parts = path.split("/");
            if (parts.length < 5) {
                logger.debug("Invalid path format: parts.length={}", parts.length);
                sendJsonResponse(exchange, 400, Map.of("error", "Missing agentId in path"));
                return;
            }

            String agentId = parts[4];
            logger.debug("Extracted agentId={}", agentId);

            // Get state machine
            logger.debug("Querying state machine for job assignments: agentId={}", agentId);
            QuorusStateMachine stateMachine = (QuorusStateMachine) raftNode.getStateMachine();

            // Get all job assignments for this agent
            Map<String, JobAssignment> allAssignments = stateMachine.getJobAssignments();
            logger.debug("Total job assignments in state machine: {}", allAssignments.size());
            
            // Filter for this agent and only ASSIGNED status (not yet accepted)
            List<Map<String, Object>> pendingJobs = allAssignments.values().stream()
                    .filter(assignment -> {
                        boolean matches = assignment.getAgentId().equals(agentId);
                        if (matches) {
                            logger.debug("Found assignment for agent: assignmentId={}-{}, status={}", 
                                    assignment.getJobId(), assignment.getAgentId(), assignment.getStatus());
                        }
                        return matches;
                    })
                    .filter(assignment -> {
                        boolean isAssigned = assignment.getStatus() == JobAssignmentStatus.ASSIGNED;
                        logger.debug("Assignment status check: jobId={}, status={}, isAssigned={}", 
                                assignment.getJobId(), assignment.getStatus(), isAssigned);
                        return isAssigned;
                    })
                    .map(assignment -> {
                        Map<String, Object> jobInfo = new HashMap<>();
                        jobInfo.put("assignmentId", assignment.getJobId() + "-" + assignment.getAgentId());
                        jobInfo.put("jobId", assignment.getJobId());
                        jobInfo.put("agentId", assignment.getAgentId());
                        jobInfo.put("status", assignment.getStatus().toString());
                        jobInfo.put("assignedAt", assignment.getAssignedAt().toString());
                        
                        // Get transfer job details
                        logger.debug("Fetching transfer job details: jobId={}", assignment.getJobId());
                        TransferJobSnapshot transferJob = stateMachine.getTransferJob(assignment.getJobId());
                        if (transferJob != null) {
                            jobInfo.put("sourceUri", transferJob.getSourceUri());
                            jobInfo.put("destinationPath", transferJob.getDestinationPath());
                            jobInfo.put("totalBytes", transferJob.getTotalBytes());
                            if (transferJob.getDescription() != null) {
                                jobInfo.put("description", transferJob.getDescription());
                            }
                            logger.debug("Transfer job details added: jobId={}, sourceUri={}, destinationPath={}", 
                                    assignment.getJobId(), transferJob.getSourceUri(), transferJob.getDestinationPath());
                        } else {
                            logger.warn("Transfer job not found for assignment: jobId={}", assignment.getJobId());
                        }
                        
                        return jobInfo;
                    })
                    .collect(Collectors.toList());

            Map<String, Object> response = new HashMap<>();
            response.put("agentId", agentId);
            response.put("pendingJobs", pendingJobs);
            response.put("count", pendingJobs.size());

            logger.debug("Returning {} pending jobs for agent: agentId={}", pendingJobs.size(), agentId);
            sendJsonResponse(exchange, 200, response);

        } catch (Exception e) {
            logger.error("Error retrieving agent jobs: {}", e.getMessage());
            logger.trace("Stack trace for agent jobs retrieval error", e);
            sendJsonResponse(exchange, 500, Map.of(
                    "error", "Internal server error",
                    "message", e.getMessage()
            ));
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

