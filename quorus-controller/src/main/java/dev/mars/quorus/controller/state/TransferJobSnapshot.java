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
    private final String errorMessage;
    private final String description;

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
            @JsonProperty("description") String description) {
        this.jobId = jobId;
        this.sourceUri = sourceUri;
        this.destinationPath = destinationPath;
        this.status = status;
        this.bytesTransferred = bytesTransferred;
        this.totalBytes = totalBytes;
        this.startTime = startTime;
        this.lastUpdateTime = lastUpdateTime;
        this.errorMessage = errorMessage;
        this.description = description;
    }

    public static TransferJobSnapshot fromTransferJob(TransferJob job) {
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
                job.getRequest().getMetadata().get("description")
        );
    }

    /**
     * Convert back to a TransferJob (simplified reconstruction).
     */
    public TransferJob toTransferJob() {
        try {
            TransferRequest request = TransferRequest.builder()
                    .sourceUri(java.net.URI.create(sourceUri))
                    .destinationPath(java.nio.file.Paths.get(destinationPath))
                    .expectedSize(totalBytes)
                    .build();
            
            if (description != null) {
                request.getMetadata().put("description", description);
            }
            
            TransferJob job = new TransferJob(request);
            
            // Set the status and other fields (simplified)
            // In a real implementation, we'd need to properly restore the atomic references
            return job;
        } catch (Exception e) {
            throw new RuntimeException("Failed to reconstruct TransferJob from snapshot", e);
        }
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
    public String getErrorMessage() { return errorMessage; }
    public String getDescription() { return description; }

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
