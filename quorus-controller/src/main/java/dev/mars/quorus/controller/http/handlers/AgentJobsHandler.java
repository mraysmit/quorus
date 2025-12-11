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
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * HTTP handler for agent job polling.
 * 
 * Endpoint: GET /api/v1/agents/{agentId}/jobs
 * 
 * Returns pending job assignments for a specific agent.
 * 
 * @author Mark Andrew Ray-Smith Cityline Ltd
 * @since 2.0
 */
public class AgentJobsHandler implements HttpHandler {

    private static final Logger logger = Logger.getLogger(AgentJobsHandler.class.getName());
    private final RaftNode raftNode;
    private final ObjectMapper objectMapper;

    public AgentJobsHandler(RaftNode raftNode) {
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
            // Extract agentId from path: /api/v1/agents/{agentId}/jobs
            String path = exchange.getRequestURI().getPath();
            String[] parts = path.split("/");
            if (parts.length < 5) {
                sendJsonResponse(exchange, 400, Map.of("error", "Missing agentId in path"));
                return;
            }

            String agentId = parts[4];

            // Get state machine
            QuorusStateMachine stateMachine = (QuorusStateMachine) raftNode.getStateMachine();

            // Get all job assignments for this agent
            Map<String, JobAssignment> allAssignments = stateMachine.getJobAssignments();
            
            // Filter for this agent and only ASSIGNED status (not yet accepted)
            List<Map<String, Object>> pendingJobs = allAssignments.values().stream()
                    .filter(assignment -> assignment.getAgentId().equals(agentId))
                    .filter(assignment -> assignment.getStatus() == JobAssignmentStatus.ASSIGNED)
                    .map(assignment -> {
                        Map<String, Object> jobInfo = new HashMap<>();
                        jobInfo.put("assignmentId", assignment.getJobId() + "-" + assignment.getAgentId());
                        jobInfo.put("jobId", assignment.getJobId());
                        jobInfo.put("agentId", assignment.getAgentId());
                        jobInfo.put("status", assignment.getStatus().toString());
                        jobInfo.put("assignedAt", assignment.getAssignedAt().toString());
                        
                        // Get transfer job details
                        TransferJobSnapshot transferJob = stateMachine.getTransferJob(assignment.getJobId());
                        if (transferJob != null) {
                            jobInfo.put("sourceUri", transferJob.getSourceUri());
                            jobInfo.put("destinationPath", transferJob.getDestinationPath());
                            jobInfo.put("totalBytes", transferJob.getTotalBytes());
                            if (transferJob.getDescription() != null) {
                                jobInfo.put("description", transferJob.getDescription());
                            }
                        }
                        
                        return jobInfo;
                    })
                    .collect(Collectors.toList());

            Map<String, Object> response = new HashMap<>();
            response.put("agentId", agentId);
            response.put("pendingJobs", pendingJobs);
            response.put("count", pendingJobs.size());

            sendJsonResponse(exchange, 200, response);

        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error retrieving agent jobs", e);
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

