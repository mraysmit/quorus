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
import dev.mars.quorus.controller.state.JobAssignmentCommand;
import dev.mars.quorus.controller.state.TransferJobCommand;
import dev.mars.quorus.core.JobAssignmentStatus;
import dev.mars.quorus.core.TransferStatus;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * HTTP handler for job status updates from agents.
 * 
 * Endpoint: POST /api/v1/jobs/{jobId}/status
 * 
 * Allows agents to update the status of their assigned jobs.
 * 
 * @author Mark Andrew Ray-Smith Cityline Ltd
 * @since 2025-12-11
 * @version 1.0
 */
public class JobStatusHandler implements HttpHandler {

    private static final Logger logger = Logger.getLogger(JobStatusHandler.class.getName());
    private final RaftNode raftNode;
    private final ObjectMapper objectMapper;

    public JobStatusHandler(RaftNode raftNode) {
        this.raftNode = raftNode;
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        if (!"POST".equals(exchange.getRequestMethod())) {
            sendJsonResponse(exchange, 405, Map.of("error", "Method not allowed"));
            return;
        }

        try {
            // Extract jobId from path: /api/v1/jobs/{jobId}/status
            String path = exchange.getRequestURI().getPath();
            String[] parts = path.split("/");
            if (parts.length < 5) {
                sendJsonResponse(exchange, 400, Map.of("error", "Missing jobId in path"));
                return;
            }

            String jobId = parts[4];

            // Parse request body
            InputStream is = exchange.getRequestBody();
            String requestBody = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            Map<String, Object> request = objectMapper.readValue(requestBody, Map.class);

            // Extract required fields
            String agentId = (String) request.get("agentId");
            String statusStr = (String) request.get("status");
            Long bytesTransferred = request.get("bytesTransferred") != null ? 
                    ((Number) request.get("bytesTransferred")).longValue() : null;
            String errorMessage = (String) request.get("errorMessage");

            if (agentId == null || statusStr == null) {
                sendJsonResponse(exchange, 400, Map.of("error", "Missing required fields: agentId, status"));
                return;
            }

            // Parse status
            JobAssignmentStatus assignmentStatus;
            TransferStatus transferStatus;
            try {
                assignmentStatus = JobAssignmentStatus.valueOf(statusStr);
                transferStatus = mapToTransferStatus(assignmentStatus);
            } catch (IllegalArgumentException e) {
                sendJsonResponse(exchange, 400, Map.of("error", "Invalid status: " + statusStr));
                return;
            }

            // Update job assignment status
            String assignmentId = jobId + "-" + agentId;
            JobAssignmentCommand assignmentCommand = JobAssignmentCommand.updateStatus(
                    assignmentId, assignmentStatus);

            Object assignmentResult = raftNode.submitCommand(assignmentCommand);
            if (assignmentResult == null) {
                sendJsonResponse(exchange, 404, Map.of("error", "Job assignment not found"));
                return;
            }

            // Update transfer job status if provided
            if (transferStatus != null) {
                TransferJobCommand transferCommand = TransferJobCommand.updateStatus(
                        jobId, transferStatus);
                raftNode.submitCommand(transferCommand);
            }

            // Wait for consensus
            TimeUnit.MILLISECONDS.sleep(100);

            Map<String, Object> response = Map.of(
                    "jobId", jobId,
                    "agentId", agentId,
                    "status", statusStr,
                    "updated", true,
                    "timestamp", Instant.now().toString()
            );

            sendJsonResponse(exchange, 200, response);

        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error updating job status", e);
            sendJsonResponse(exchange, 500, Map.of(
                    "error", "Internal server error",
                    "message", e.getMessage()
            ));
        }
    }

    private TransferStatus mapToTransferStatus(JobAssignmentStatus assignmentStatus) {
        switch (assignmentStatus) {
            case ASSIGNED:
            case ACCEPTED:
                return TransferStatus.PENDING;
            case IN_PROGRESS:
                return TransferStatus.IN_PROGRESS;
            case COMPLETED:
                return TransferStatus.COMPLETED;
            case FAILED:
                return TransferStatus.FAILED;
            case CANCELLED:
                return TransferStatus.CANCELLED;
            default:
                return null;
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

