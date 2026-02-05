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

package dev.mars.quorus.api;

import dev.mars.quorus.api.dto.*;
import dev.mars.quorus.api.service.DistributedTransferService;
import dev.mars.quorus.core.TransferJob;
import dev.mars.quorus.core.TransferRequest;
import dev.mars.quorus.core.TransferResult;
import dev.mars.quorus.core.TransferStatus;
import dev.mars.quorus.core.exceptions.TransferException;
import dev.mars.quorus.transfer.TransferEngine;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.nio.file.Paths;
import java.util.concurrent.CompletableFuture;

/**
 * Transfer operations REST API.
 * Converted from JAX-RS to Vert.x Web for Vert.x 5.x migration.
 * @author Mark Andrew Ray-Smith Cityline Ltd
 * @version 1.0
 * @since 2025-08-20
 */
@ApplicationScoped
public class TransferResource {

    private static final Logger logger = LoggerFactory.getLogger(TransferResource.class);

    @Inject
    TransferEngine transferEngine;

    @Inject
    DistributedTransferService distributedTransferService;

    /**
     * Register routes with the Vert.x router.
     */
    public void registerRoutes(Router router) {
        router.post("/api/v1/transfers").handler(this::handleCreateTransfer);
        router.get("/api/v1/transfers/:jobId").handler(this::handleGetTransferStatus);
        router.delete("/api/v1/transfers/:jobId").handler(this::handleCancelTransfer);
        router.get("/api/v1/transfers/count").handler(this::handleGetActiveTransferCount);
    }

    /**
     * POST /api/v1/transfers - Create a new file transfer job
     */
    private void handleCreateTransfer(RoutingContext ctx) {
        try {
            // Parse request body
            TransferRequestDto requestDto = ctx.body().asPojo(TransferRequestDto.class);

            // Validate input
            if (requestDto == null) {
                ctx.response().setStatusCode(400)
                    .end(JsonObject.mapFrom(new ErrorResponse("Invalid request", "Request body is required")).encode());
                return;
            }

            if (requestDto.getSourceUri() == null || requestDto.getSourceUri().trim().isEmpty()) {
                ctx.response().setStatusCode(400)
                    .end(JsonObject.mapFrom(new ErrorResponse("Invalid request", "Source URI is required")).encode());
                return;
            }

            if (requestDto.getDestinationPath() == null || requestDto.getDestinationPath().trim().isEmpty()) {
                ctx.response().setStatusCode(400)
                    .end(JsonObject.mapFrom(new ErrorResponse("Invalid request", "Destination path is required")).encode());
                return;
            }

            // Convert DTO to TransferRequest
            TransferRequest request = TransferRequest.builder()
                    .sourceUri(URI.create(requestDto.getSourceUri().trim()))
                    .destinationPath(Paths.get(requestDto.getDestinationPath().trim()))
                    .build();

            // Check if distributed controller is available
            if (distributedTransferService.isControllerAvailable()) {
                // Submit transfer through distributed controller
                CompletableFuture<TransferJob> future = distributedTransferService.submitTransfer(request);

                try {
                    TransferJob job = future.get(5, java.util.concurrent.TimeUnit.SECONDS);

                    // Create response
                    TransferJobResponseDto response = new TransferJobResponseDto();
                    response.setJobId(job.getJobId());
                    response.setSourceUri(requestDto.getSourceUri());
                    response.setDestinationPath(requestDto.getDestinationPath());
                    response.setStatus(job.getStatus());
                    response.setMessage("Transfer job created successfully via distributed controller");

                    logger.info("Distributed transfer job created: {}", job.getJobId());
                    ctx.response().setStatusCode(201)
                        .end(JsonObject.mapFrom(response).encode());
                    return;

                } catch (Exception e) {
                    logger.warn("Distributed transfer submission failed, falling back to local: {}", e.getMessage());
                    logger.debug("Stack trace", e);
                    // Fall through to local processing
                }
            }

            // Fallback to local transfer engine
            CompletableFuture<TransferResult> future = transferEngine.submitTransfer(request)
                    .toCompletionStage().toCompletableFuture();

            // Get the job ID from the request
            String jobId = request.getRequestId();

            // Create response
            TransferJobResponseDto response = new TransferJobResponseDto();
            response.setJobId(jobId);
            response.setSourceUri(requestDto.getSourceUri());
            response.setDestinationPath(requestDto.getDestinationPath());
            response.setStatus(TransferStatus.PENDING);
            response.setMessage("Transfer job created successfully (local fallback)");

            logger.info("Local transfer job created: {}", jobId);
            ctx.response().setStatusCode(201)
                .end(JsonObject.mapFrom(response).encode());

        } catch (TransferException e) {
            logger.warn("Failed to create transfer: {}", e.getMessage());
            logger.debug("Stack trace", e);
            ctx.response().setStatusCode(400)
                .end(JsonObject.mapFrom(new ErrorResponse("Error", "Invalid transfer request: " + e.getMessage())).encode());
        } catch (IllegalArgumentException e) {
            // Handle URI creation errors and other validation errors
            logger.warn("Invalid request parameters: {}", e.getMessage());
            logger.debug("Stack trace", e);
            ctx.response().setStatusCode(400)
                .end(JsonObject.mapFrom(new ErrorResponse("Invalid request", "Invalid URI or path: " + e.getMessage())).encode());
        } catch (Exception e) {
            logger.error("Internal error creating transfer: {}", e.getMessage());
            logger.debug("Stack trace", e);
            ctx.response().setStatusCode(500)
                .end(JsonObject.mapFrom(new ErrorResponse("Internal server error", "An unexpected error occurred")).encode());
        }
    }

    /**
     * GET /api/v1/transfers/:jobId - Get the status of a specific transfer job
     */
    private void handleGetTransferStatus(RoutingContext ctx) {
        try {
            String jobId = ctx.pathParam("jobId");

            // Try distributed controller first
            if (distributedTransferService.isControllerAvailable()) {
                try {
                    CompletableFuture<TransferJob> future = distributedTransferService.getTransferJob(jobId);
                    TransferJob job = future.get(3, java.util.concurrent.TimeUnit.SECONDS);

                    if (job != null) {
                        TransferJobResponseDto response = TransferJobResponseDto.fromTransferJob(job);
                        ctx.json(response);
                        return;
                    }
                } catch (Exception e) {
                    logger.debug("Distributed job query failed, trying local", e);
                    // Fall through to local query
                }
            }

            // Fallback to local transfer engine
            TransferJob job = transferEngine.getTransferJob(jobId);
            if (job != null) {
                TransferJobResponseDto response = TransferJobResponseDto.fromTransferJob(job);
                ctx.json(response);
            } else {
                ctx.response().setStatusCode(404)
                    .end(JsonObject.mapFrom(new ErrorResponse("Transfer job not found: " + jobId)).encode());
            }
        } catch (Exception e) {
            logger.error("Error retrieving transfer status: {}", e.getMessage());
            logger.debug("Stack trace", e);
            ctx.response().setStatusCode(500)
                .end(JsonObject.mapFrom(new ErrorResponse("Internal server error")).encode());
        }
    }

    /**
     * DELETE /api/v1/transfers/:jobId - Cancel a running transfer job
     */
    private void handleCancelTransfer(RoutingContext ctx) {
        try {
            String jobId = ctx.pathParam("jobId");

            // Try distributed controller first
            if (distributedTransferService.isControllerAvailable()) {
                try {
                    CompletableFuture<Boolean> future = distributedTransferService.cancelTransfer(jobId);
                    boolean cancelled = future.get(3, java.util.concurrent.TimeUnit.SECONDS);

                    if (cancelled) {
                        logger.info("Distributed transfer cancelled: {}", jobId);
                        ctx.json(new MessageResponse("Transfer cancelled successfully via distributed controller"));
                        return;
                    }
                } catch (Exception e) {
                    logger.debug("Distributed cancellation failed, trying local", e);
                    // Fall through to local cancellation
                }
            }

            // Fallback to local transfer engine
            boolean cancelled = transferEngine.cancelTransfer(jobId);
            if (cancelled) {
                logger.info("Local transfer cancelled: {}", jobId);
                ctx.json(new MessageResponse("Transfer cancelled successfully (local)"));
            } else {
                ctx.response().setStatusCode(409)
                    .end(JsonObject.mapFrom(new ErrorResponse("Transfer cannot be cancelled or not found")).encode());
            }
        } catch (Exception e) {
            logger.error("Error cancelling transfer: {}", e.getMessage());
            logger.debug("Stack trace", e);
            ctx.response().setStatusCode(500)
                .end(JsonObject.mapFrom(new ErrorResponse("Internal server error")).encode());
        }
    }

    /**
     * GET /api/v1/transfers/count - Get the number of currently active transfers
     */
    private void handleGetActiveTransferCount(RoutingContext ctx) {
        try {
            int count = transferEngine.getActiveTransferCount();
            ctx.json(new CountResponse(count));
        } catch (Exception e) {
            logger.error("Error retrieving active transfer count: {}", e.getMessage());
            logger.debug("Stack trace", e);
            ctx.response().setStatusCode(500)
                .end(JsonObject.mapFrom(new ErrorResponse("Internal server error")).encode());
        }
    }
}
