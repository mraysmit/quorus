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
import com.fasterxml.jackson.annotation.JsonProperty;
import dev.mars.quorus.core.TransferJob;
import dev.mars.quorus.core.TransferRequest;
import dev.mars.quorus.core.TransferStatus;

import java.io.Serializable;
import java.time.Instant;
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

    public TransferJobSnapshot(
            String jobId, String sourceUri, String destinationPath, TransferStatus status,
            long bytesTransferred, long totalBytes, Instant startTime, Instant lastUpdateTime,
            String errorMessage, String description, String tenantId) {
        this(jobId, sourceUri, destinationPath, status, bytesTransferred, totalBytes, startTime,
                lastUpdateTime, errorMessage, description, tenantId, null, null);
    }

    public TransferJobSnapshot(
            String jobId, String sourceUri, String destinationPath, TransferStatus status,
            long bytesTransferred, long totalBytes, Instant startTime, Instant lastUpdateTime,
            String errorMessage, String description, String tenantId,
            TransferOperationalContext operationalContext) {
        this(jobId, sourceUri, destinationPath, status, bytesTransferred, totalBytes, startTime,
                lastUpdateTime, errorMessage, description, tenantId, operationalContext, null);
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
            @JsonProperty("lastProgressAt") Instant lastProgressAt) {
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
    }

    public static TransferJobSnapshot fromTransferJob(TransferJob job) {
        String tenantId = job.getRequest().getMetadata().get("tenantId");
        return fromTransferJob(job, tenantId);
    }

    public static TransferJobSnapshot fromTransferJob(TransferJob job, String tenantId) {
        return new TransferJobSnapshot(
                job.getJobId(),
                job.getRequest().getSourceUri().toString(),
                job.getRequest().getDestinationPath().toString(),
                job.getStatus(),
                job.getBytesTransferred(),
                job.getTotalBytes(),
                job.getStartTime(),
                job.getLastUpdateTime(),
                job.getErrorMessage(),
                job.getRequest().getMetadata().get("description"),
                tenantId,
                TransferOperationalContext.fromMetadata(job.getRequest().getMetadata()),
                job.getBytesTransferred() > 0 ? job.getLastUpdateTime() : null);
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
                .destinationUri(java.net.URI.create(destinationPath))
                .expectedSize(totalBytes);

        if (description != null) {
            builder.metadata("description", description);
        }
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
        if (value != null) builder.metadata(key, value);
    }

    // Getters
    public String getJobId() { return jobId; }
    public String getSourceUri() { return sourceUri; }
    public String getDestinationPath() { return destinationPath; }
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
