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

import dev.mars.quorus.connection.ConnectionAccessRequest;
import dev.mars.quorus.connection.ConnectionPolicyEnforcer;
import dev.mars.quorus.connection.ConnectionPolicyException;
import dev.mars.quorus.connection.HostResolver;
import dev.mars.quorus.controller.http.ControllerConnectionAuthorizer;
import dev.mars.quorus.connection.SecretReference;
import dev.mars.quorus.connection.ServiceConnection;
import dev.mars.quorus.controller.http.ErrorCode;
import dev.mars.quorus.controller.http.QuorusApiException;
import dev.mars.quorus.controller.raft.RaftNode;
import dev.mars.quorus.controller.security.SecurityContext;
import dev.mars.quorus.controller.security.SecurityProfile;
import dev.mars.quorus.controller.state.CommandResult;
import dev.mars.quorus.controller.state.QuorusStateStore;
import dev.mars.quorus.controller.state.ServiceConnectionRegistry;
import dev.mars.quorus.controller.state.SystemMetadataCommand;
import dev.mars.quorus.controller.state.TransferJobCommand;
import dev.mars.quorus.controller.state.TransferJobSnapshot;
import dev.mars.quorus.controller.state.TransferOperationalContext;
import dev.mars.quorus.core.JobAssignment;
import dev.mars.quorus.core.TransferJob;
import dev.mars.quorus.security.CredentialBearingUriDetector;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * HTTP handler for transfer job operations.
 *
 * <p>Endpoints:
 * <ul>
 *   <li>{@code POST /api/v1/transfers} — Create a new transfer job</li>
 *   <li>{@code GET /api/v1/transfers/:jobId} — Get transfer job status</li>
 *   <li>{@code DELETE /api/v1/transfers/:jobId} — Cancel a transfer job</li>
 * </ul>
 *
 * <p>All write operations are submitted to Raft for distributed consensus.
 *
 * @author Mark Andrew Ray-Smith Cityline Ltd
 * @version 2.0 (Vert.x reactive)
 * @since 2025-12-11
 */
public class TransferHandler {

    private static final Logger logger = LoggerFactory.getLogger(TransferHandler.class);
    private final RaftNode raftNode;
    private final QuorusStateStore stateStore;
    private final ServiceConnectionRegistry connectionRegistry;

    private final SecurityProfile securityProfile;
    private final ControllerConnectionAuthorizer authorizer;

    public TransferHandler(RaftNode raftNode, QuorusStateStore stateStore) {
        this(raftNode, stateStore, SecurityProfile.DEVELOPMENT);
    }

    public TransferHandler(RaftNode raftNode, QuorusStateStore stateStore, SecurityProfile securityProfile) {
        this(raftNode, stateStore, securityProfile, HostResolver.system());
    }

    public TransferHandler(RaftNode raftNode, QuorusStateStore stateStore, SecurityProfile securityProfile,
                           HostResolver hostResolver) {
        this(raftNode, stateStore, securityProfile, new ControllerConnectionAuthorizer(hostResolver));
    }

    public TransferHandler(RaftNode raftNode, QuorusStateStore stateStore, SecurityProfile securityProfile,
                           ControllerConnectionAuthorizer authorizer) {
        this.raftNode = raftNode;
        this.stateStore = stateStore;
        this.connectionRegistry = new ServiceConnectionRegistry(stateStore);

        this.securityProfile = securityProfile;
        this.authorizer = java.util.Objects.requireNonNull(authorizer);
    }

    /**
     * Handles {@code POST /api/v1/transfers} — creates a new transfer job.
     */
    public Handler<RoutingContext> handleCreate() {
        return ctx -> {
            try {
                JsonObject body = ctx.body().asJsonObject();
                if (body == null) {
                    ctx.fail(400, new IllegalArgumentException("Request body is required"));
                    return;
                }

                // Extract tenantId BEFORE mapTo — TransferJob has no tenantId field and
                // Jackson would throw "Unrecognized field" if we leave it in the body.
                String tenantId = SecurityContext.trustedTenant(ctx, body.getString("tenantId"));
                if (tenantId == null || tenantId.isBlank()) {
                    ctx.fail(400, new IllegalArgumentException(
                            "Missing required field: tenantId (or authenticated tenant identity)"));
                    return;
                }

                String serviceConnectionId = body.getString("serviceConnectionId");

                JsonObject jobBody = body.copy();
                if (serviceConnectionId != null && !serviceConnectionId.isBlank()) {
                    ServiceConnection connection = connectionRegistry.findConnection(tenantId, serviceConnectionId);
                    if (connection == null) {
                        throw new QuorusApiException(ErrorCode.NOT_FOUND, "Service connection not found");
                    }
                    SecretReference secretReference = connectionRegistry.findSecret(
                            tenantId, connection.secretReferenceId());
                    Instant authorizationTime = Instant.now();
                    if (secretReference != null
                            && secretReference.status() == SecretReference.Status.ACTIVE
                            && secretReference.expiresAt() != null
                            && !authorizationTime.isBefore(secretReference.expiresAt())) {
                        expireSecretReference(secretReference).onComplete(ignored -> ctx.fail(
                                new QuorusApiException(ErrorCode.CONFLICT,
                                        "Service connection secret reference is unavailable")));
                        return;
                    }
                    if (secretReference == null || !secretReference.usableAt(authorizationTime)) {
                        throw new QuorusApiException(ErrorCode.CONFLICT,
                                "Service connection secret reference is unavailable");
                    }
                    String remotePath = body.getString("remotePath");
                    String agentPool = body.getString("agentPool");
                    if (agentPool == null || agentPool.isBlank()) {
                        throw new QuorusApiException(ErrorCode.VALIDATION_ERROR,
                                "Governed transfers require agentPool");
                    }
                    ServiceConnection.Direction direction = ServiceConnection.Direction.valueOf(
                            body.getString("direction", "DOWNLOAD").toUpperCase(Locale.ROOT));
                    authorizer.authorize(ctx.vertx(), connection,
                            new ConnectionAccessRequest(tenantId, remotePath, direction, agentPool, List.of()))
                            .onSuccess(connectionAuthorization -> {
                                try {
                                    if (!connection.equals(connectionRegistry.findConnection(tenantId, serviceConnectionId))) {
                                        throw new QuorusApiException(ErrorCode.CONFLICT,
                                                "Service connection changed during DNS authorization; retry");
                                    }
                                    SecretReference currentSecret = connectionRegistry.findSecret(
                                            tenantId, connection.secretReferenceId());
                                    if (!secretReference.equals(currentSecret)) {
                                        throw new QuorusApiException(ErrorCode.CONFLICT,
                                                "Service connection secret changed during DNS authorization; retry");
                                    }
                                    Instant completedAt = Instant.now();
                                    if (currentSecret.status() == SecretReference.Status.ACTIVE
                                            && currentSecret.expiresAt() != null
                                            && !completedAt.isBefore(currentSecret.expiresAt())) {
                                        expireSecretReference(currentSecret).onComplete(ignored -> ctx.fail(
                                                new QuorusApiException(ErrorCode.CONFLICT,
                                                        "Service connection secret reference is unavailable")));
                                        return;
                                    }
                                    if (!currentSecret.usableAt(completedAt)) {
                                        throw new QuorusApiException(ErrorCode.CONFLICT,
                                                "Service connection secret reference is unavailable");
                                    }
                                    JsonObject metadata = jobBody.getJsonObject("metadata", new JsonObject());
                                    metadata.put("serviceConnectionId", serviceConnectionId)
                                            .put("remotePath", remotePath)
                                            .put("connectionPolicyVersion", Integer.toString(connection.policyVersion()))
                                            .put("connectionPolicyDigest", connectionAuthorization.policyDigest())
                                            .put("agentPool", agentPool)
                                            .put("networkZone", connection.networkZone())
                                            .put("controllerResolvedAddresses",
                                                    String.join(",", connectionAuthorization.resolvedAddresses()));
                                    if (direction == ServiceConnection.Direction.DOWNLOAD) {
                                        if (body.getString("destinationPath") == null) {
                                            throw new QuorusApiException(ErrorCode.VALIDATION_ERROR,
                                                    "Governed downloads require destinationPath");
                                        }
                                        jobBody.put("sourceUri", connectionAuthorization.endpoint().toString())
                                                .remove("destinationUri");
                                    } else {
                                        String localSource = body.getString("sourceUri");
                                        if (localSource == null || !"file".equalsIgnoreCase(URI.create(localSource).getScheme())) {
                                            throw new QuorusApiException(ErrorCode.VALIDATION_ERROR,
                                                    "Governed uploads require a local file sourceUri");
                                        }
                                        CredentialBearingUriDetector.requireCredentialFree(URI.create(localSource), "Source");
                                        jobBody.put("sourceUri", localSource)
                                                .put("destinationUri", connectionAuthorization.endpoint().toString())
                                                .remove("destinationPath");
                                    }
                                    jobBody.put("metadata", metadata);
                                    jobBody.remove("serviceConnectionId");
                                    jobBody.remove("remotePath");
                                    jobBody.remove("direction");
                                    jobBody.remove("agentPool");
                                    submitTransfer(ctx, jobBody, tenantId, connectionAuthorization);
                                } catch (Exception error) {
                                    failCreate(ctx, error);
                                }
                            }).onFailure(error -> failCreate(ctx, error));
                    return;
                } else {
                    String sourceUri = body.getString("sourceUri");
                    if (securityProfile == SecurityProfile.PRODUCTION) {
                        throw new QuorusApiException(ErrorCode.VALIDATION_ERROR,
                                "Production transfers require serviceConnectionId and remotePath");
                    }
                    if (sourceUri != null) {
                        CredentialBearingUriDetector.requireCredentialFree(URI.create(sourceUri), "Source");
                    }
                }

                submitTransfer(ctx, jobBody, tenantId, null);
            } catch (Exception error) {
                failCreate(ctx, error);
            }
        };
    }

    private void submitTransfer(RoutingContext ctx, JsonObject jobBody, String tenantId,
                                ConnectionPolicyEnforcer.ConnectionAuthorization connectionAuthorization) {
        jobBody.remove("tenantId");
        TransferJob job = jobBody.mapTo(TransferJob.class);
        TransferOperationalContext.fromMetadata(job.getRequest().getMetadata());

        logger.info("Creating transfer job: jobId={}, tenantId={}", job.getJobId(), tenantId);
        TransferJobCommand command = TransferJobCommand.create(job, tenantId);

        ConnectionPolicyEnforcer.ConnectionAuthorization committedAuthorization = connectionAuthorization;
        raftNode.submitCommand(command)
                .onSuccess(result -> {
                    if (CommandResultHandler.failIfRejected(ctx, result)) return;
                    if (result instanceof CommandResult.NotFound<?> nf) {
                        logger.warn("Transfer job disappeared during creation (race condition): jobId={}", nf.id());
                        ctx.fail(QuorusApiException.notFound(ErrorCode.TRANSFER_NOT_FOUND, nf.id()));
                    } else {
                        logger.info("Transfer job created: jobId={}", job.getJobId());
                        if (committedAuthorization == null) {
                            respondCreated(ctx, job.getJobId());
                        } else {
                            recordConnectionAuthorization(tenantId, job.getJobId(),
                                    committedAuthorization).onSuccess(ignored ->
                                    respondCreated(ctx, job.getJobId())).onFailure(ctx::fail);
                        }
                    }
                })
                .onFailure(ctx::fail);
    }

    private void failCreate(RoutingContext ctx, Throwable error) {
        if (error instanceof ConnectionPolicyException policyError) {
            ctx.fail(new QuorusApiException(ErrorCode.VALIDATION_ERROR, policyError.getMessage()));
        } else {
            ctx.fail(error);
        }
    }

    /**
     * Handles {@code GET /api/v1/transfers/:jobId} — gets transfer job status.
     */
    public Handler<RoutingContext> handleGet() {
        return ctx -> {
            String jobId = ctx.pathParam("jobId");
            logger.debug("Getting transfer job: jobId={}", jobId);

            TransferJobSnapshot job = stateStore.findTransferJob(jobId)
                    .orElseThrow(() -> QuorusApiException.notFound(ErrorCode.TRANSFER_NOT_FOUND, jobId));
            SecurityContext.trustedTenant(ctx, job.getTenantId());

                // Get the latest assignment status for this job
            JobAssignment latestAssignment = stateStore.getJobAssignments().values().stream()
                    .filter(a -> a.getJobId().equals(jobId))
                    .max((a, b) -> assignmentLastActivity(a).compareTo(assignmentLastActivity(b)))
                    .orElse(null);

            JsonObject response = new JsonObject()
                    .put("jobId", job.getJobId())
                    .put("sourceUri", job.getSourceUri())
                    .put("destinationUri", job.getDestinationUri())
                    .put("totalBytes", job.getTotalBytes())
                    .put("bytesTransferred", job.getBytesTransferred());
            if (job.getLocalDestinationPath() != null) {
                response.put("destinationPath", job.getLocalDestinationPath());
            }

            if (latestAssignment != null) {
                response.put("status", latestAssignment.getStatus().name());
            } else {
                response.put("status", job.getStatus().name());
            }

            if (job.getStartTime() != null) {
                response.put("startTime", job.getStartTime().toString());
            }
            if (job.getLastUpdateTime() != null) {
                response.put("lastUpdateTime", job.getLastUpdateTime().toString());
            }
            if (job.getErrorMessage() != null) {
                response.put("errorMessage", job.getErrorMessage());
            }
            if (job.getDescription() != null) {
                response.put("description", job.getDescription());
            }
            if (job.getServiceConnectionId() != null) {
                response.put("serviceConnectionId", job.getServiceConnectionId())
                        .put("remotePath", job.getRemotePath())
                        .put("connectionPolicyVersion", job.getConnectionPolicyVersion())
                        .put("connectionPolicyDigest", job.getConnectionPolicyDigest());
            }
            if (job.getTotalBytes() > 0) {
                double progress = (double) job.getBytesTransferred() / job.getTotalBytes() * 100.0;
                response.put("progressPercentage", Math.round(progress * 100.0) / 100.0);
            }

            ctx.json(response);
        };
    }

    private static Instant assignmentLastActivity(JobAssignment assignment) {
        if (assignment.getCompletedAt() != null) {
            return assignment.getCompletedAt();
        }
        if (assignment.getStartedAt() != null) {
            return assignment.getStartedAt();
        }
        if (assignment.getAcceptedAt() != null) {
            return assignment.getAcceptedAt();
        }
        return assignment.getAssignedAt();
    }

    private void respondCreated(RoutingContext ctx, String jobId) {
        ctx.response().setStatusCode(201);
        ctx.json(new JsonObject().put("success", true).put("jobId", jobId));
    }

    private io.vertx.core.Future<CommandResult<?>> recordConnectionAuthorization(
            String tenantId, String jobId,
            ConnectionPolicyEnforcer.ConnectionAuthorization authorization) {
        Instant now = Instant.now();
        ServiceConnectionRegistry.SecurityEvent event = new ServiceConnectionRegistry.SecurityEvent(
                UUID.randomUUID().toString(), tenantId, "SERVICE_CONNECTION_AUTHORIZED",
                "TRANSFER", jobId, "SUCCESS", "Q-CONNECTION-AUTHORIZED",
                authorization.policyVersion(), now);
        return raftNode.submitCommand(new SystemMetadataCommand.Set(
                connectionRegistry.eventKey(tenantId, now), ServiceConnectionRegistry.encode(event)));
    }

    private io.vertx.core.Future<CommandResult<?>> expireSecretReference(SecretReference reference) {
        Instant now = Instant.now();
        SecretReference expired = new SecretReference(reference.secretReferenceId(), reference.tenantId(),
                reference.provider(), reference.path(), reference.key(), reference.version(),
                SecretReference.Status.EXPIRED, reference.expiresAt(), reference.lastRotatedAt());
        ServiceConnectionRegistry.SecurityEvent event = new ServiceConnectionRegistry.SecurityEvent(
                UUID.randomUUID().toString(), reference.tenantId(), "SECRET_REFERENCE_EXPIRED",
                "SECRET_REFERENCE", reference.secretReferenceId(), "DENIED", "Q-SECRET-EXPIRED", null, now);
        return raftNode.submitCommand(new SystemMetadataCommand.Set(
                        connectionRegistry.secretKey(reference.tenantId(), reference.secretReferenceId()),
                        ServiceConnectionRegistry.encode(expired)))
                .compose(result -> raftNode.submitCommand(new SystemMetadataCommand.Set(
                        connectionRegistry.eventKey(reference.tenantId(), now),
                        ServiceConnectionRegistry.encode(event))));
    }

    /**
     * Handles {@code DELETE /api/v1/transfers/:jobId} — cancels a transfer job.
     */
    public Handler<RoutingContext> handleDelete() {
        return ctx -> {
            try {
                String jobId = ctx.pathParam("jobId");
                logger.info("Deleting transfer job: jobId={}", jobId);
                QuorusStateStore stateMachine = this.stateStore;

                if (!stateMachine.hasTransferJob(jobId)) {
                    throw QuorusApiException.notFound(ErrorCode.TRANSFER_NOT_FOUND, jobId);
                }
                SecurityContext.trustedTenant(ctx, stateMachine.getTransferJob(jobId).getTenantId());

                TransferJobCommand command = TransferJobCommand.delete(jobId);
                raftNode.submitCommand(command)
                        .onSuccess(result -> {
                            if (CommandResultHandler.failIfRejected(ctx, result)) return;
                            if (result instanceof CommandResult.NotFound<?> nf) {
                                logger.warn("Transfer job disappeared during deletion (race condition): jobId={}", nf.id());
                                ctx.fail(QuorusApiException.notFound(ErrorCode.TRANSFER_NOT_FOUND, nf.id()));
                            } else {
                                logger.info("Transfer job deleted: jobId={}", jobId);
                                ctx.json(new JsonObject()
                                        .put("jobId", jobId)
                                        .put("message", "Transfer job cancelled and deleted successfully"));
                            }
                        })
                        .onFailure(ctx::fail);
            } catch (Exception e) {
                logger.error("Failed to delete transfer job: {}", e.getMessage());
                logger.debug("Stack trace for transfer job deletion failure", e);
                ctx.fail(e);
            }
        };
    }
}

