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

package dev.mars.quorus.controller.state;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import dev.mars.quorus.core.TransferJob;
import dev.mars.quorus.core.TransferRequest;
import dev.mars.quorus.core.TransferStatus;

import java.io.Serializable;
import java.time.Instant;
import java.util.List;
/**
 * Description for TransferJobSnapshot
 *
 * @author Mark Andrew Ray-Smith Cityline Ltd
 * @version 1.0
 * @since 2025-08-20
 */

public class TransferJobSnapshot implements Serializable {

    private static final long serialVersionUID = 1L;

    private final String jobId;
    private final String sourceUri;
    private final String destinationPath;
    private final TransferStatus status;
    private final long bytesTransferred;
    private final long totalBytes;
    private final Instant startTime;
    private final Instant lastUpdateTime;
    private final Instant lastProgressAt;
    private final String errorMessage;
    private final String description;
    private final String tenantId;
    private final TransferOperationalContext operationalContext;
    private final String serviceConnectionId;
    private final String remotePath;
    private final Integer connectionPolicyVersion;
    private final String connectionPolicyDigest;
    private final String agentPool;
    private final List<String> controllerResolvedAddresses;

    public TransferJobSnapshot(
            String jobId, String sourceUri, String destinationPath, TransferStatus status,
            long bytesTransferred, long totalBytes, Instant startTime, Instant lastUpdateTime,
            String errorMessage, String description, String tenantId) {
        this(jobId, sourceUri, destinationPath, status, bytesTransferred, totalBytes, startTime,
                lastUpdateTime, errorMessage, description, tenantId, null, null,
                null, null, null, null, null, null);
    }

    public TransferJobSnapshot(
            String jobId, String sourceUri, String destinationPath, TransferStatus status,
            long bytesTransferred, long totalBytes, Instant startTime, Instant lastUpdateTime,
            String errorMessage, String description, String tenantId,
            TransferOperationalContext operationalContext) {
        this(jobId, sourceUri, destinationPath, status, bytesTransferred, totalBytes, startTime,
                lastUpdateTime, errorMessage, description, tenantId, operationalContext, null,
                null, null, null, null, null, null);
    }

    public TransferJobSnapshot(
            String jobId, String sourceUri, String destinationPath, TransferStatus status,
            long bytesTransferred, long totalBytes, Instant startTime, Instant lastUpdateTime,
            String errorMessage, String description, String tenantId,
            TransferOperationalContext operationalContext, Instant lastProgressAt) {
        this(jobId, sourceUri, destinationPath, status, bytesTransferred, totalBytes, startTime,
                lastUpdateTime, errorMessage, description, tenantId, operationalContext, lastProgressAt,
                null, null, null, null, null, null);
    }

    @JsonCreator
    public TransferJobSnapshot(
            @JsonProperty("jobId") String jobId,
            @JsonProperty("sourceUri") String sourceUri,
            @JsonProperty("destinationPath") String destinationPath,
            @JsonProperty("status") TransferStatus status,
            @JsonProperty("bytesTransferred") long bytesTransferred,
            @JsonProperty("totalBytes") long totalBytes,
            @JsonProperty("startTime") Instant startTime,
            @JsonProperty("lastUpdateTime") Instant lastUpdateTime,
            @JsonProperty("errorMessage") String errorMessage,
            @JsonProperty("description") String description,
            @JsonProperty("tenantId") String tenantId,
            @JsonProperty("operationalContext") TransferOperationalContext operationalContext,
            @JsonProperty("lastProgressAt") Instant lastProgressAt,
            @JsonProperty("serviceConnectionId") String serviceConnectionId,
            @JsonProperty("remotePath") String remotePath,
            @JsonProperty("connectionPolicyVersion") Integer connectionPolicyVersion,
            @JsonProperty("connectionPolicyDigest") String connectionPolicyDigest,
            @JsonProperty("agentPool") String agentPool,
            @JsonProperty("controllerResolvedAddresses") List<String> controllerResolvedAddresses) {
        this.jobId = jobId;
        this.sourceUri = sourceUri;
        this.destinationPath = destinationPath;
        this.status = status;
        this.bytesTransferred = bytesTransferred;
        this.totalBytes = totalBytes;
        this.startTime = startTime;
        this.lastUpdateTime = lastUpdateTime;
        this.lastProgressAt = lastProgressAt;
        this.errorMessage = errorMessage;
        this.description = description;
        this.tenantId = tenantId;
        this.operationalContext = operationalContext;
        this.serviceConnectionId = serviceConnectionId;
        this.remotePath = remotePath;
        this.connectionPolicyVersion = connectionPolicyVersion;
        this.connectionPolicyDigest = connectionPolicyDigest;
        this.agentPool = agentPool;
        this.controllerResolvedAddresses = List.copyOf(
                controllerResolvedAddresses == null ? List.of() : controllerResolvedAddresses);
    }

    public static TransferJobSnapshot fromTransferJob(TransferJob job) {
        String tenantId = job.getRequest().getMetadata().get("tenantId");
        return fromTransferJob(job, tenantId);
    }

    public static TransferJobSnapshot fromTransferJob(TransferJob job, String tenantId) {
        return new TransferJobSnapshot(
                job.getJobId(),
                job.getRequest().getSourceUri().toString(),
                job.getRequest().getDestinationUri().toString(),
                job.getStatus(),
                job.getBytesTransferred(),
                job.getTotalBytes(),
                job.getStartTime(),
                job.getLastUpdateTime(),
                job.getErrorMessage(),
                job.getRequest().getMetadata().get("description"),
                tenantId,
                TransferOperationalContext.fromMetadata(job.getRequest().getMetadata()),
                job.getBytesTransferred() > 0 ? job.getLastUpdateTime() : null,
                job.getRequest().getMetadata().get("serviceConnectionId"),
                job.getRequest().getMetadata().get("remotePath"),
                integer(job.getRequest().getMetadata().get("connectionPolicyVersion")),
                job.getRequest().getMetadata().get("connectionPolicyDigest"),
                job.getRequest().getMetadata().get("agentPool"),
                csv(job.getRequest().getMetadata().get("controllerResolvedAddresses")));
    }

    /**
     * Convert back to a TransferJob.
     * Note: TransferJob uses AtomicReference for status management,
     * so the restored job always starts in PENDING. Use the snapshot
     * fields directly when current state is needed.
     */
    public TransferJob toTransferJob() {
        TransferRequest.Builder builder = TransferRequest.builder()
                .requestId(jobId)
                .sourceUri(java.net.URI.create(sourceUri))
                .destinationUri(destinationUri(destinationPath))
                .expectedSize(totalBytes);

        if (description != null) {
            builder.metadata("description", description);
        }
        putMetadata(builder, "serviceConnectionId", serviceConnectionId);
        putMetadata(builder, "remotePath", remotePath);
        putMetadata(builder, "connectionPolicyVersion",
                connectionPolicyVersion == null ? null : connectionPolicyVersion.toString());
        putMetadata(builder, "connectionPolicyDigest", connectionPolicyDigest);
        putMetadata(builder, "agentPool", agentPool);
        putMetadata(builder, "controllerResolvedAddresses", String.join(",", controllerResolvedAddresses));
        if (operationalContext != null) {
            putMetadata(builder, "businessService", operationalContext.businessService());
            putMetadata(builder, "owner", operationalContext.owner());
            putMetadata(builder, "criticality", operationalContext.criticality());
            putMetadata(builder, "environment", operationalContext.environment());
            putMetadata(builder, "processingDate", operationalContext.processingDate());
            putMetadata(builder, "expectedStartAt", operationalContext.expectedStartAt() == null
                    ? null : operationalContext.expectedStartAt().toString());
            putMetadata(builder, "requiredCompletionAt", operationalContext.requiredCompletionAt() == null
                    ? null : operationalContext.requiredCompletionAt().toString());
            putMetadata(builder, "runbookUrl", operationalContext.runbookUrl());
        }

        return new TransferJob(builder.build());
    }

    private static void putMetadata(TransferRequest.Builder builder, String key, String value) {
        if (value != null && !value.isBlank()) builder.metadata(key, value);
    }

    private static Integer integer(String value) { return value == null ? null : Integer.valueOf(value); }
    private static List<String> csv(String value) {
        return value == null || value.isBlank() ? List.of() : List.of(value.split(","));
    }

    // Getters
    public String getJobId() { return jobId; }
    public String getSourceUri() { return sourceUri; }
    public String getDestinationPath() { return destinationPath; }
    @JsonIgnore
    public String getDestinationUri() { return destinationUri(destinationPath).toString(); }
    @JsonIgnore
    public String getLocalDestinationPath() {
        java.net.URI uri = destinationUri(destinationPath);
        return "file".equalsIgnoreCase(uri.getScheme()) ? java.nio.file.Paths.get(uri).toString() : null;
    }
    public TransferStatus getStatus() { return status; }
    public long getBytesTransferred() { return bytesTransferred; }
    public long getTotalBytes() { return totalBytes; }
    public Instant getStartTime() { return startTime; }
    public Instant getLastUpdateTime() { return lastUpdateTime; }
    public Instant getLastProgressAt() { return lastProgressAt; }
    public String getErrorMessage() { return errorMessage; }
    public String getDescription() { return description; }
    public String getTenantId() { return tenantId; }
    public TransferOperationalContext getOperationalContext() { return operationalContext; }
    public String getServiceConnectionId() { return serviceConnectionId; }
    public String getRemotePath() { return remotePath; }
    public Integer getConnectionPolicyVersion() { return connectionPolicyVersion; }
    public String getConnectionPolicyDigest() { return connectionPolicyDigest; }
    public String getAgentPool() { return agentPool; }
    public List<String> getControllerResolvedAddresses() { return controllerResolvedAddresses; }

    private static java.net.URI destinationUri(String value) {
        try {
            java.net.URI uri = java.net.URI.create(value);
            if (uri.getScheme() != null && uri.getScheme().length() > 1) return uri;
        } catch (IllegalArgumentException ignored) {
            // Older snapshots stored a platform path rather than a URI.
        }
        return java.nio.file.Paths.get(value).toUri();
    }

    @Override
    public String toString() {
        return "TransferJobSnapshot{" +
                "jobId='" + jobId + '\'' +
                ", sourceUri='" + sourceUri + '\'' +
                ", destinationPath='" + destinationPath + '\'' +
                ", status=" + status +
                ", bytesTransferred=" + bytesTransferred +
                ", totalBytes=" + totalBytes +
                '}';
    }
}
