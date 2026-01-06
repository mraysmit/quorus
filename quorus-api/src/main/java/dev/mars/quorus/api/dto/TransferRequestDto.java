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

/**
 * DTO for transfer request.
 * @author Mark Andrew Ray-Smith Cityline Ltd
 * @version 1.0
 * @since 2025-08-20
 */
public class TransferRequestDto {

    private String sourceUri;
    private String destinationPath;
    private String tenantId;
    private String description;
    private Integer priority;
    private Integer maxRetries;
    private Integer timeoutSeconds;

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
