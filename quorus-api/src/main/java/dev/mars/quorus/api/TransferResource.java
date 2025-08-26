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
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponses;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.net.URI;
import java.nio.file.Paths;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import java.util.logging.Logger;

@Path("/api/v1/transfers")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Transfer Operations", description = "File transfer management API")
public class TransferResource {

    private static final Logger logger = Logger.getLogger(TransferResource.class.getName());

    @Inject
    TransferEngine transferEngine;

    @Inject
    DistributedTransferService distributedTransferService;

    @POST
    @RolesAllowed({"ADMIN", "USER"})
    @Operation(summary = "Create Transfer", description = "Create a new file transfer job")
    @APIResponses(value = {
        @APIResponse(responseCode = "201", description = "Transfer job created successfully"),
        @APIResponse(responseCode = "400", description = "Invalid transfer request"),
        @APIResponse(responseCode = "401", description = "Unauthorized"),
        @APIResponse(responseCode = "500", description = "Internal server error")
    })
    public Response createTransfer(
            @Parameter(description = "Transfer request details") TransferRequestDto requestDto) {
        try {
            // Validate input
            if (requestDto == null) {
                return Response.status(Response.Status.BAD_REQUEST)
                        .entity(new ErrorResponse("Invalid request", "Request body is required"))
                        .build();
            }

            if (requestDto.getSourceUri() == null || requestDto.getSourceUri().trim().isEmpty()) {
                return Response.status(Response.Status.BAD_REQUEST)
                        .entity(new ErrorResponse("Invalid request", "Source URI is required"))
                        .build();
            }

            if (requestDto.getDestinationPath() == null || requestDto.getDestinationPath().trim().isEmpty()) {
                return Response.status(Response.Status.BAD_REQUEST)
                        .entity(new ErrorResponse("Invalid request", "Destination path is required"))
                        .build();
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

                    logger.info("Distributed transfer job created: " + job.getJobId());
                    return Response.status(Response.Status.CREATED).entity(response).build();

                } catch (Exception e) {
                    logger.log(Level.WARNING, "Distributed transfer submission failed, falling back to local", e);
                    // Fall through to local processing
                }
            }

            // Fallback to local transfer engine
            CompletableFuture<TransferResult> future = transferEngine.submitTransfer(request);

            // Get the job ID from the request
            String jobId = request.getRequestId();

            // Create response
            TransferJobResponseDto response = new TransferJobResponseDto();
            response.setJobId(jobId);
            response.setSourceUri(requestDto.getSourceUri());
            response.setDestinationPath(requestDto.getDestinationPath());
            response.setStatus(TransferStatus.PENDING);
            response.setMessage("Transfer job created successfully (local fallback)");

            logger.info("Local transfer job created: " + jobId);
            return Response.status(Response.Status.CREATED).entity(response).build();
            
        } catch (TransferException e) {
            logger.log(Level.WARNING, "Failed to create transfer", e);
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(new ErrorResponse("Error", "Invalid transfer request: " + e.getMessage()))
                    .build();
        } catch (IllegalArgumentException e) {
            // Handle URI creation errors and other validation errors
            logger.log(Level.WARNING, "Invalid request parameters", e);
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(new ErrorResponse("Invalid request", "Invalid URI or path: " + e.getMessage()))
                    .build();
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Internal error creating transfer", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(new ErrorResponse("Internal server error", "An unexpected error occurred"))
                    .build();
        }
    }

    @GET
    @Path("/{jobId}")
    @RolesAllowed({"ADMIN", "USER"})
    @Operation(summary = "Get Transfer Status", description = "Get the status of a specific transfer job")
    @APIResponses(value = {
        @APIResponse(responseCode = "200", description = "Transfer status retrieved successfully"),
        @APIResponse(responseCode = "404", description = "Transfer job not found"),
        @APIResponse(responseCode = "401", description = "Unauthorized")
    })
    public Response getTransferStatus(
            @PathParam("jobId") @Parameter(description = "Transfer job ID") String jobId) {
        try {
            // Try distributed controller first
            if (distributedTransferService.isControllerAvailable()) {
                try {
                    CompletableFuture<TransferJob> future = distributedTransferService.getTransferJob(jobId);
                    TransferJob job = future.get(3, java.util.concurrent.TimeUnit.SECONDS);

                    if (job != null) {
                        TransferJobResponseDto response = TransferJobResponseDto.fromTransferJob(job);
                        return Response.ok(response).build();
                    }
                } catch (Exception e) {
                    logger.log(Level.FINE, "Distributed job query failed, trying local", e);
                    // Fall through to local query
                }
            }

            // Fallback to local transfer engine
            TransferJob job = transferEngine.getTransferJob(jobId);
            if (job != null) {
                TransferJobResponseDto response = TransferJobResponseDto.fromTransferJob(job);
                return Response.ok(response).build();
            } else {
                return Response.status(Response.Status.NOT_FOUND)
                        .entity(new ErrorResponse("Transfer job not found: " + jobId))
                        .build();
            }
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error retrieving transfer status", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(new ErrorResponse("Internal server error"))
                    .build();
        }
    }

    @DELETE
    @Path("/{jobId}")
    @RolesAllowed({"ADMIN", "USER"})
    @Operation(summary = "Cancel Transfer", description = "Cancel a running transfer job")
    @APIResponses(value = {
        @APIResponse(responseCode = "200", description = "Transfer cancelled successfully"),
        @APIResponse(responseCode = "404", description = "Transfer job not found"),
        @APIResponse(responseCode = "409", description = "Transfer cannot be cancelled"),
        @APIResponse(responseCode = "401", description = "Unauthorized")
    })
    public Response cancelTransfer(
            @PathParam("jobId") @Parameter(description = "Transfer job ID") String jobId) {
        try {
            // Try distributed controller first
            if (distributedTransferService.isControllerAvailable()) {
                try {
                    CompletableFuture<Boolean> future = distributedTransferService.cancelTransfer(jobId);
                    boolean cancelled = future.get(3, java.util.concurrent.TimeUnit.SECONDS);

                    if (cancelled) {
                        logger.info("Distributed transfer cancelled: " + jobId);
                        return Response.ok(new MessageResponse("Transfer cancelled successfully via distributed controller")).build();
                    }
                } catch (Exception e) {
                    logger.log(Level.FINE, "Distributed cancellation failed, trying local", e);
                    // Fall through to local cancellation
                }
            }

            // Fallback to local transfer engine
            boolean cancelled = transferEngine.cancelTransfer(jobId);
            if (cancelled) {
                logger.info("Local transfer cancelled: " + jobId);
                return Response.ok(new MessageResponse("Transfer cancelled successfully (local)")).build();
            } else {
                return Response.status(Response.Status.CONFLICT)
                        .entity(new ErrorResponse("Transfer cannot be cancelled or not found"))
                        .build();
            }
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error cancelling transfer", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(new ErrorResponse("Internal server error"))
                    .build();
        }
    }

    /**
     * Get active transfer count.
     */
    @GET
    @Path("/count")
    @RolesAllowed({"ADMIN", "USER"})
    @Operation(summary = "Get Active Transfer Count", description = "Get the number of currently active transfers")
    @APIResponses(value = {
        @APIResponse(responseCode = "200", description = "Active transfer count retrieved successfully"),
        @APIResponse(responseCode = "401", description = "Unauthorized")
    })
    public Response getActiveTransferCount() {
        try {
            int count = transferEngine.getActiveTransferCount();
            return Response.ok(new CountResponse(count)).build();
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error retrieving active transfer count", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(new ErrorResponse("Internal server error"))
                    .build();
        }
    }
}
