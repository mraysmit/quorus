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

package dev.mars.quorus.api.dto;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * Data Transfer Object for transfer request.
 * Contains the information needed to create a new file transfer.
 */
@Schema(description = "Transfer request containing source and destination information")
public class TransferRequestDto {

    @Schema(description = "Source URI for the transfer", example = "https://example.com/file.txt", required = true)
    private String sourceUri;

    @Schema(description = "Destination path for the transfer", example = "/tmp/file.txt", required = true)
    private String destinationPath;

    @Schema(description = "Optional tenant ID for multi-tenant environments", example = "tenant-123")
    private String tenantId;

    @Schema(description = "Optional description for the transfer", example = "Monthly report download")
    private String description;

    @Schema(description = "Transfer priority (1-10, higher is more important)", example = "5")
    private Integer priority;

    @Schema(description = "Maximum number of retry attempts", example = "3")
    private Integer maxRetries;

    @Schema(description = "Timeout in seconds for the transfer", example = "300")
    private Integer timeoutSeconds;

    /**
     * Default constructor.
     */
    public TransferRequestDto() {
    }

    /**
     * Constructor with required fields.
     */
    public TransferRequestDto(String sourceUri, String destinationPath) {
        this.sourceUri = sourceUri;
        this.destinationPath = destinationPath;
    }

    // Getters and setters
    public String getSourceUri() {
        return sourceUri;
    }

    public void setSourceUri(String sourceUri) {
        this.sourceUri = sourceUri;
    }

    public String getDestinationPath() {
        return destinationPath;
    }

    public void setDestinationPath(String destinationPath) {
        this.destinationPath = destinationPath;
    }

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Integer getPriority() {
        return priority;
    }

    public void setPriority(Integer priority) {
        this.priority = priority;
    }

    public Integer getMaxRetries() {
        return maxRetries;
    }

    public void setMaxRetries(Integer maxRetries) {
        this.maxRetries = maxRetries;
    }

    public Integer getTimeoutSeconds() {
        return timeoutSeconds;
    }

    public void setTimeoutSeconds(Integer timeoutSeconds) {
        this.timeoutSeconds = timeoutSeconds;
    }

    @Override
    public String toString() {
        return "TransferRequestDto{" +
                "sourceUri='" + sourceUri + '\'' +
                ", destinationPath='" + destinationPath + '\'' +
                ", tenantId='" + tenantId + '\'' +
                ", description='" + description + '\'' +
                ", priority=" + priority +
                ", maxRetries=" + maxRetries +
                ", timeoutSeconds=" + timeoutSeconds +
                '}';
    }
}
