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
import java.util.logging.Level;
import java.util.logging.Logger;

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

    private static final Logger logger = Logger.getLogger(TransferHandler.class.getName());
    private final RaftNode raftNode;
    private final ObjectMapper objectMapper;

    public TransferHandler(RaftNode raftNode) {
        this.raftNode = raftNode;
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String method = exchange.getRequestMethod();
        String path = exchange.getRequestURI().getPath();

        try {
            switch (method) {
                case "POST":
                    handleCreateTransfer(exchange);
                    break;
                case "GET":
                    handleGetTransfer(exchange, path);
                    break;
                case "DELETE":
                    handleCancelTransfer(exchange, path);
                    break;
                default:
                    sendJsonResponse(exchange, 405, Map.of("error", "Method not allowed"));
            }
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error handling transfer request", e);
            sendJsonResponse(exchange, 500, Map.of("error", "Internal server error", "message", e.getMessage()));
        }
    }

    private void handleCreateTransfer(HttpExchange exchange) throws Exception {
        // Read request body
        String requestBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        Map<String, Object> requestData = objectMapper.readValue(requestBody, Map.class);

        // Validate required fields
        if (!requestData.containsKey("sourceUri") || !requestData.containsKey("destinationPath")) {
            sendJsonResponse(exchange, 400, Map.of("error", "Missing required fields: sourceUri, destinationPath"));
            return;
        }

        // Create TransferRequest
        TransferRequest request = TransferRequest.builder()
                .sourceUri(URI.create((String) requestData.get("sourceUri")))
                .destinationPath(Paths.get((String) requestData.get("destinationPath")))
                .protocol(requestData.containsKey("protocol") ? (String) requestData.get("protocol") : "http")
                .build();

        // Create TransferJob
        TransferJob job = new TransferJob(request);

        // Submit to Raft
        TransferJobCommand command = TransferJobCommand.create(job);
<<<<<<< HEAD
        CompletableFuture<Object> future = raftNode.submitCommand(command).toCompletionStage().toCompletableFuture();
=======
        Future<Object> future = raftNode.submitCommand(command);
>>>>>>> 99ead9a4bf7a397233245aa6831aa3ff67de12ca

        // Wait for consensus
        Object result = future.toCompletionStage().toCompletableFuture().get(5, TimeUnit.SECONDS);

        if (result instanceof TransferJob) {
            TransferJob createdJob = (TransferJob) result;
            Map<String, Object> response = Map.of(
                    "jobId", createdJob.getJobId(),
                    "status", createdJob.getStatus().toString(),
                    "sourceUri", createdJob.getRequest().getSourceUri().toString(),
                    "destinationPath", createdJob.getRequest().getDestinationPath().toString(),
                    "message", "Transfer job created successfully"
            );
            sendJsonResponse(exchange, 201, response);
        } else {
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
<<<<<<< HEAD
        CompletableFuture<Object> future = raftNode.submitCommand(command).toCompletionStage().toCompletableFuture();
=======
        Future<Object> future = raftNode.submitCommand(command);
>>>>>>> 99ead9a4bf7a397233245aa6831aa3ff67de12ca

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

