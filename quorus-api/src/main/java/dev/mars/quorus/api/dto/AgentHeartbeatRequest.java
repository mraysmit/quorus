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

import dev.mars.quorus.agent.AgentStatus;
import dev.mars.quorus.agent.AgentSystemInfo;
import dev.mars.quorus.agent.AgentNetworkInfo;
import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Min;
import java.time.Instant;
import java.util.Map;
import java.util.HashMap;

/**
 * DTO for agent heartbeat requests.
 * Contains current agent status, metrics, and health information.
 * 
 * @author Mark Andrew Ray-Smith Cityline Ltd
 * @since 2025-08-26
 * @version 1.0
 */
public class AgentHeartbeatRequest {

    @JsonProperty("agentId")
    @NotBlank(message = "Agent ID is required")
    private String agentId;

    @JsonProperty("timestamp")
    @NotNull(message = "Timestamp is required")
    private Instant timestamp;

    @JsonProperty("sequenceNumber")
    @Min(value = 0, message = "Sequence number must be non-negative")
    private long sequenceNumber;

    @JsonProperty("status")
    @NotNull(message = "Status is required")
    private AgentStatus status;

    @JsonProperty("currentJobs")
    @Min(value = 0, message = "Current jobs must be non-negative")
    private int currentJobs;

    @JsonProperty("availableCapacity")
    @Min(value = 0, message = "Available capacity must be non-negative")
    private int availableCapacity;

    @JsonProperty("systemInfo")
    private AgentSystemInfo systemInfo;

    @JsonProperty("networkInfo")
    private AgentNetworkInfo networkInfo;

    @JsonProperty("transferMetrics")
    private TransferMetrics transferMetrics;

    @JsonProperty("healthStatus")
    private HealthStatus healthStatus;

    @JsonProperty("lastJobCompletion")
    private Instant lastJobCompletion;

    @JsonProperty("nextMaintenanceWindow")
    private Instant nextMaintenanceWindow;

    @JsonProperty("metadata")
    private Map<String, String> metadata;

    /**
     * Default constructor.
     */
    public AgentHeartbeatRequest() {
        this.timestamp = Instant.now();
        this.metadata = new HashMap<>();
        this.transferMetrics = new TransferMetrics();
        this.healthStatus = new HealthStatus();
    }

    /**
     * Constructor with required fields.
     */
    public AgentHeartbeatRequest(String agentId, long sequenceNumber, AgentStatus status) {
        this();
        this.agentId = agentId;
        this.sequenceNumber = sequenceNumber;
        this.status = status;
    }

    // Getters and setters

    public String getAgentId() {
        return agentId;
    }

    public void setAgentId(String agentId) {
        this.agentId = agentId;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Instant timestamp) {
        this.timestamp = timestamp;
    }

    public long getSequenceNumber() {
        return sequenceNumber;
    }

    public void setSequenceNumber(long sequenceNumber) {
        this.sequenceNumber = sequenceNumber;
    }

    public AgentStatus getStatus() {
        return status;
    }

    public void setStatus(AgentStatus status) {
        this.status = status;
    }

    public int getCurrentJobs() {
        return currentJobs;
    }

    public void setCurrentJobs(int currentJobs) {
        this.currentJobs = currentJobs;
    }

    public int getAvailableCapacity() {
        return availableCapacity;
    }

    public void setAvailableCapacity(int availableCapacity) {
        this.availableCapacity = availableCapacity;
    }

    public AgentSystemInfo getSystemInfo() {
        return systemInfo;
    }

    public void setSystemInfo(AgentSystemInfo systemInfo) {
        this.systemInfo = systemInfo;
    }

    public AgentNetworkInfo getNetworkInfo() {
        return networkInfo;
    }

    public void setNetworkInfo(AgentNetworkInfo networkInfo) {
        this.networkInfo = networkInfo;
    }

    public TransferMetrics getTransferMetrics() {
        return transferMetrics;
    }

    public void setTransferMetrics(TransferMetrics transferMetrics) {
        this.transferMetrics = transferMetrics;
    }

    public HealthStatus getHealthStatus() {
        return healthStatus;
    }

    public void setHealthStatus(HealthStatus healthStatus) {
        this.healthStatus = healthStatus;
    }

    public Instant getLastJobCompletion() {
        return lastJobCompletion;
    }

    public void setLastJobCompletion(Instant lastJobCompletion) {
        this.lastJobCompletion = lastJobCompletion;
    }

    public Instant getNextMaintenanceWindow() {
        return nextMaintenanceWindow;
    }

    public void setNextMaintenanceWindow(Instant nextMaintenanceWindow) {
        this.nextMaintenanceWindow = nextMaintenanceWindow;
    }

    public Map<String, String> getMetadata() {
        return metadata;
    }

    public void setMetadata(Map<String, String> metadata) {
        this.metadata = metadata;
    }

    /**
     * Transfer metrics embedded class.
     */
    public static class TransferMetrics {
        @JsonProperty("active")
        private int active;

        @JsonProperty("completed")
        private long completed;

        @JsonProperty("failed")
        private long failed;

        @JsonProperty("bytesTransferred")
        private long bytesTransferred;

        @JsonProperty("averageDuration")
        private double averageDuration; // seconds

        @JsonProperty("successRate")
        private double successRate; // percentage

        // Constructors, getters, and setters
        public TransferMetrics() {}

        public int getActive() { return active; }
        public void setActive(int active) { this.active = active; }

        public long getCompleted() { return completed; }
        public void setCompleted(long completed) { this.completed = completed; }

        public long getFailed() { return failed; }
        public void setFailed(long failed) { this.failed = failed; }

        public long getBytesTransferred() { return bytesTransferred; }
        public void setBytesTransferred(long bytesTransferred) { this.bytesTransferred = bytesTransferred; }

        public double getAverageDuration() { return averageDuration; }
        public void setAverageDuration(double averageDuration) { this.averageDuration = averageDuration; }

        public double getSuccessRate() { return successRate; }
        public void setSuccessRate(double successRate) { this.successRate = successRate; }
    }

    /**
     * Health status embedded class.
     */
    public static class HealthStatus {
        @JsonProperty("diskSpace")
        private String diskSpace = "healthy";

        @JsonProperty("networkConnectivity")
        private String networkConnectivity = "healthy";

        @JsonProperty("systemLoad")
        private String systemLoad = "normal";

        @JsonProperty("overallHealth")
        private String overallHealth = "healthy";

        // Constructors, getters, and setters
        public HealthStatus() {}

        public String getDiskSpace() { return diskSpace; }
        public void setDiskSpace(String diskSpace) { this.diskSpace = diskSpace; }

        public String getNetworkConnectivity() { return networkConnectivity; }
        public void setNetworkConnectivity(String networkConnectivity) { this.networkConnectivity = networkConnectivity; }

        public String getSystemLoad() { return systemLoad; }
        public void setSystemLoad(String systemLoad) { this.systemLoad = systemLoad; }

        public String getOverallHealth() { return overallHealth; }
        public void setOverallHealth(String overallHealth) { this.overallHealth = overallHealth; }
    }

    @Override
    public String toString() {
        return "AgentHeartbeatRequest{" +
                "agentId='" + agentId + '\'' +
                ", timestamp=" + timestamp +
                ", sequenceNumber=" + sequenceNumber +
                ", status=" + status +
                ", currentJobs=" + currentJobs +
                ", availableCapacity=" + availableCapacity +
                '}';
    }
}
