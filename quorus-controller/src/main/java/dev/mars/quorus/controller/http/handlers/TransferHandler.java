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
import dev.mars.quorus.controller.state.TransferJobCommand;
import dev.mars.quorus.controller.state.TransferJobSnapshot;
import dev.mars.quorus.core.TransferJob;
import dev.mars.quorus.core.TransferRequest;
import dev.mars.quorus.core.TransferStatus;
import io.vertx.core.Future;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * HTTP handler for transfer job operations.
 * 
 * Endpoints:
 * - POST /api/v1/transfers - Create a new transfer job
 * - GET /api/v1/transfers/{jobId} - Get transfer job status
 * - DELETE /api/v1/transfers/{jobId} - Cancel a transfer job
 * 
 * All operations are submitted to Raft for distributed consensus.
 * 
 * @author Mark Andrew Ray-Smith Cityline Ltd
 * @since 2025-12-11
 * @version 1.0
 */
public class TransferHandler implements HttpHandler {

    private static final Logger logger = LoggerFactory.getLogger(TransferHandler.class);
    private final RaftNode raftNode;
    private final ObjectMapper objectMapper;

    public TransferHandler(RaftNode raftNode) {
        this.raftNode = raftNode;
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
        logger.debug("TransferHandler initialized with raftNode={}", raftNode.getNodeId());
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String method = exchange.getRequestMethod();
        String path = exchange.getRequestURI().getPath();
        logger.debug("handle() entry: method={}, path={}", method, path);

        try {
            switch (method) {
                case "POST":
                    logger.debug("Routing to handleCreateTransfer");
                    handleCreateTransfer(exchange);
                    break;
                case "GET":
                    logger.debug("Routing to handleGetTransfer");
                    handleGetTransfer(exchange, path);
                    break;
                case "DELETE":
                    logger.debug("Routing to handleCancelTransfer");
                    handleCancelTransfer(exchange, path);
                    break;
                default:
                    logger.debug("Method not allowed: {}", method);
                    sendJsonResponse(exchange, 405, Map.of("error", "Method not allowed"));
            }
        } catch (Exception e) {
            logger.error("Error handling transfer request: method={}, path={}", method, path, e);
            sendJsonResponse(exchange, 500, Map.of("error", "Internal server error", "message", e.getMessage()));
        }
    }

    private void handleCreateTransfer(HttpExchange exchange) throws Exception {
        logger.debug("handleCreateTransfer() entry");
        
        // Read request body
        String requestBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        logger.debug("Request body received: length={}", requestBody.length());
        
        Map<String, Object> requestData = objectMapper.readValue(requestBody, Map.class);
        logger.debug("Parsed request data: sourceUri={}, destinationPath={}", 
                requestData.get("sourceUri"), requestData.get("destinationPath"));

        // Validate required fields
        if (!requestData.containsKey("sourceUri") || !requestData.containsKey("destinationPath")) {
            logger.debug("Validation failed: missing required fields");
            sendJsonResponse(exchange, 400, Map.of("error", "Missing required fields: sourceUri, destinationPath"));
            return;
        }

        // Create TransferRequest
        TransferRequest request = TransferRequest.builder()
                .sourceUri(URI.create((String) requestData.get("sourceUri")))
                .destinationPath(Paths.get((String) requestData.get("destinationPath")))
                .protocol(requestData.containsKey("protocol") ? (String) requestData.get("protocol") : "http")
                .build();
        logger.debug("TransferRequest created: protocol={}", request.getProtocol());

        // Create TransferJob
        TransferJob job = new TransferJob(request);
        logger.debug("TransferJob created: jobId={}", job.getJobId());

        // Submit to Raft
        logger.debug("Submitting TransferJobCommand to Raft: jobId={}", job.getJobId());
        TransferJobCommand command = TransferJobCommand.create(job);
        Future<Object> future = raftNode.submitCommand(command);

        // Wait for consensus
        logger.debug("Waiting for Raft consensus: jobId={}", job.getJobId());
        Object result = future.toCompletionStage().toCompletableFuture().get(5, TimeUnit.SECONDS);
        logger.debug("Raft consensus completed: resultType={}", result != null ? result.getClass().getSimpleName() : "null");

        if (result instanceof TransferJob) {
            TransferJob createdJob = (TransferJob) result;
            logger.debug("Transfer job created successfully: jobId={}, status={}", 
                    createdJob.getJobId(), createdJob.getStatus());
            Map<String, Object> response = Map.of(
                    "jobId", createdJob.getJobId(),
                    "status", createdJob.getStatus().toString(),
                    "sourceUri", createdJob.getRequest().getSourceUri().toString(),
                    "destinationPath", createdJob.getRequest().getDestinationPath().toString(),
                    "message", "Transfer job created successfully"
            );
            sendJsonResponse(exchange, 201, response);
        } else {
            logger.warn("Failed to create transfer job: unexpected result type={}", 
                    result != null ? result.getClass().getSimpleName() : "null");
            sendJsonResponse(exchange, 500, Map.of("error", "Failed to create transfer job"));
        }
    }

    private void handleGetTransfer(HttpExchange exchange, String path) throws Exception {
        // Extract jobId from path: /api/v1/transfers/{jobId}
        String[] parts = path.split("/");
        if (parts.length < 5) {
            sendJsonResponse(exchange, 400, Map.of("error", "Missing jobId in path"));
            return;
        }

        String jobId = parts[4];

        // Query state machine for job status
        QuorusStateMachine stateMachine = (QuorusStateMachine) raftNode.getStateMachine();
        TransferJobSnapshot jobSnapshot = stateMachine.getTransferJob(jobId);

        if (jobSnapshot == null) {
            sendJsonResponse(exchange, 404, Map.of("error", "Transfer job not found", "jobId", jobId));
            return;
        }

        // Build response with all job details
        Map<String, Object> response = new HashMap<>();
        response.put("jobId", jobSnapshot.getJobId());
        response.put("sourceUri", jobSnapshot.getSourceUri());
        response.put("destinationPath", jobSnapshot.getDestinationPath());
        response.put("status", jobSnapshot.getStatus().toString());
        response.put("bytesTransferred", jobSnapshot.getBytesTransferred());
        response.put("totalBytes", jobSnapshot.getTotalBytes());

        if (jobSnapshot.getTotalBytes() > 0) {
            double progress = (double) jobSnapshot.getBytesTransferred() / jobSnapshot.getTotalBytes() * 100.0;
            response.put("progressPercentage", Math.round(progress * 100.0) / 100.0);
        } else {
            response.put("progressPercentage", 0.0);
        }

        if (jobSnapshot.getStartTime() != null) {
            response.put("startTime", jobSnapshot.getStartTime().toString());
        }

        if (jobSnapshot.getLastUpdateTime() != null) {
            response.put("lastUpdateTime", jobSnapshot.getLastUpdateTime().toString());
        }

        if (jobSnapshot.getErrorMessage() != null) {
            response.put("errorMessage", jobSnapshot.getErrorMessage());
        }

        if (jobSnapshot.getDescription() != null) {
            response.put("description", jobSnapshot.getDescription());
        }

        sendJsonResponse(exchange, 200, response);
    }

    private void handleCancelTransfer(HttpExchange exchange, String path) throws Exception {
        // Extract jobId from path
        String[] parts = path.split("/");
        if (parts.length < 5) {
            sendJsonResponse(exchange, 400, Map.of("error", "Missing jobId in path"));
            return;
        }

        String jobId = parts[4];

        // Check if job exists before attempting to delete
        QuorusStateMachine stateMachine = (QuorusStateMachine) raftNode.getStateMachine();
        if (!stateMachine.hasTransferJob(jobId)) {
            sendJsonResponse(exchange, 404, Map.of("error", "Transfer job not found", "jobId", jobId));
            return;
        }

        // Submit delete command to Raft
        TransferJobCommand command = TransferJobCommand.delete(jobId);
        Future<Object> future = raftNode.submitCommand(command);

        // Wait for consensus
        Object result = future.toCompletionStage().toCompletableFuture().get(5, TimeUnit.SECONDS);

        if (result instanceof TransferJobSnapshot) {
            TransferJobSnapshot deletedJob = (TransferJobSnapshot) result;
            Map<String, Object> response = Map.of(
                    "jobId", deletedJob.getJobId(),
                    "status", deletedJob.getStatus().toString(),
                    "message", "Transfer job cancelled and deleted successfully"
            );
            sendJsonResponse(exchange, 200, response);
        } else if (result == null) {
            sendJsonResponse(exchange, 404, Map.of("error", "Transfer job not found", "jobId", jobId));
        } else {
            sendJsonResponse(exchange, 500, Map.of("error", "Failed to cancel transfer job"));
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

