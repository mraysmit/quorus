/*
 * Copyright 2024 Quorus Project
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

/**
 * REST API Resource for file transfer operations.
 * Provides endpoints for creating, monitoring, and managing file transfers.
 */
@Path("/api/v1/transfers")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Transfer Operations", description = "File transfer management API")
public class TransferResource {

    private static final Logger logger = Logger.getLogger(TransferResource.class.getName());

    @Inject
    TransferEngine transferEngine;

    /**
     * Create a new file transfer job.
     */
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
            // Convert DTO to TransferRequest
            TransferRequest request = TransferRequest.builder()
                    .sourceUri(URI.create(requestDto.getSourceUri()))
                    .destinationPath(Paths.get(requestDto.getDestinationPath()))
                    .build();

            // Submit transfer
            CompletableFuture<TransferResult> future = transferEngine.submitTransfer(request);
            
            // Get the job ID from the request
            String jobId = request.getRequestId();
            
            // Create response
            TransferJobResponseDto response = new TransferJobResponseDto();
            response.setJobId(jobId);
            response.setSourceUri(requestDto.getSourceUri());
            response.setDestinationPath(requestDto.getDestinationPath());
            response.setStatus(TransferStatus.PENDING);
            response.setMessage("Transfer job created successfully");

            logger.info("Transfer job created: " + jobId);
            return Response.status(Response.Status.CREATED).entity(response).build();
            
        } catch (TransferException e) {
            logger.log(Level.WARNING, "Failed to create transfer", e);
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(new ErrorResponse("Invalid transfer request: " + e.getMessage()))
                    .build();
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Internal error creating transfer", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(new ErrorResponse("Internal server error"))
                    .build();
        }
    }

    /**
     * Get transfer job status by ID.
     */
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

    /**
     * Cancel a transfer job.
     */
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
            boolean cancelled = transferEngine.cancelTransfer(jobId);
            if (cancelled) {
                logger.info("Transfer cancelled: " + jobId);
                return Response.ok(new MessageResponse("Transfer cancelled successfully")).build();
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
