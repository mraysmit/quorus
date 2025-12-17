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

package dev.mars.quorus.api.dto;

import dev.mars.quorus.core.TransferJob;
import dev.mars.quorus.core.TransferStatus;

import java.time.LocalDateTime;public class TransferJobResponseDto {    private String jobId;    private String sourceUri;    private String destinationPath;    private TransferStatus status;    private double progressPercentage;    private long bytesTransferred;    private long totalBytes;    private long transferRateBytesPerSecond;    private long estimatedTimeRemainingSeconds;    private LocalDateTime createdAt;    private LocalDateTime startedAt;    private LocalDateTime completedAt;    private String errorMessage;    private int retryCount;    private int maxRetries;    private String tenantId;    private String message;

    public TransferJobResponseDto() {
    }

    /**
     * Create a response DTO from a TransferJob.
     */
    public static TransferJobResponseDto fromTransferJob(TransferJob job) {
        TransferJobResponseDto dto = new TransferJobResponseDto();
        dto.setJobId(job.getJobId());
        dto.setSourceUri(job.getRequest().getSourceUri().toString());
        dto.setDestinationPath(job.getRequest().getDestinationPath().toString());
        dto.setStatus(job.getStatus());
        dto.setProgressPercentage(job.getProgressPercentage() * 100); // Convert to percentage
        dto.setBytesTransferred(job.getBytesTransferred());
        dto.setTotalBytes(job.getTotalBytes());

        // Calculate transfer rate (simplified)
        if (job.getStartTime() != null) {
            long elapsedSeconds = java.time.Duration.between(job.getStartTime(),
                job.getLastUpdateTime() != null ? job.getLastUpdateTime() : java.time.Instant.now()).toSeconds();
            if (elapsedSeconds > 0) {
                dto.setTransferRateBytesPerSecond(job.getBytesTransferred() / elapsedSeconds);
            }
        }

        dto.setEstimatedTimeRemainingSeconds(job.getEstimatedRemainingSeconds());

        // Convert Instant to LocalDateTime (simplified - using system timezone)
        if (job.getLastUpdateTime() != null) {
            dto.setCreatedAt(java.time.LocalDateTime.ofInstant(job.getLastUpdateTime(), java.time.ZoneId.systemDefault()));
        }
        if (job.getStartTime() != null) {
            dto.setStartedAt(java.time.LocalDateTime.ofInstant(job.getStartTime(), java.time.ZoneId.systemDefault()));
        }
        if (job.getStatus() == TransferStatus.COMPLETED || job.getStatus() == TransferStatus.FAILED) {
            dto.setCompletedAt(java.time.LocalDateTime.ofInstant(job.getLastUpdateTime(), java.time.ZoneId.systemDefault()));
        }

        dto.setErrorMessage(job.getErrorMessage());

        // Set default values for fields not available in current TransferJob
        dto.setRetryCount(0);
        dto.setMaxRetries(3);
        dto.setTenantId("default");

        return dto;
    }

    // Getters and setters
    public String getJobId() { return jobId; }
    public void setJobId(String jobId) { this.jobId = jobId; }

    public String getSourceUri() { return sourceUri; }
    public void setSourceUri(String sourceUri) { this.sourceUri = sourceUri; }

    public String getDestinationPath() { return destinationPath; }
    public void setDestinationPath(String destinationPath) { this.destinationPath = destinationPath; }

    public TransferStatus getStatus() { return status; }
    public void setStatus(TransferStatus status) { this.status = status; }

    public double getProgressPercentage() { return progressPercentage; }
    public void setProgressPercentage(double progressPercentage) { this.progressPercentage = progressPercentage; }

    public long getBytesTransferred() { return bytesTransferred; }
    public void setBytesTransferred(long bytesTransferred) { this.bytesTransferred = bytesTransferred; }

    public long getTotalBytes() { return totalBytes; }
    public void setTotalBytes(long totalBytes) { this.totalBytes = totalBytes; }

    public long getTransferRateBytesPerSecond() { return transferRateBytesPerSecond; }
    public void setTransferRateBytesPerSecond(long transferRateBytesPerSecond) { 
        this.transferRateBytesPerSecond = transferRateBytesPerSecond; 
    }

    public long getEstimatedTimeRemainingSeconds() { return estimatedTimeRemainingSeconds; }
    public void setEstimatedTimeRemainingSeconds(long estimatedTimeRemainingSeconds) { 
        this.estimatedTimeRemainingSeconds = estimatedTimeRemainingSeconds; 
    }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getStartedAt() { return startedAt; }
    public void setStartedAt(LocalDateTime startedAt) { this.startedAt = startedAt; }

    public LocalDateTime getCompletedAt() { return completedAt; }
    public void setCompletedAt(LocalDateTime completedAt) { this.completedAt = completedAt; }

    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }

    public int getRetryCount() { return retryCount; }
    public void setRetryCount(int retryCount) { this.retryCount = retryCount; }

    public int getMaxRetries() { return maxRetries; }
    public void setMaxRetries(int maxRetries) { this.maxRetries = maxRetries; }

    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
}
