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
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;

/**
 * DTO for agent heartbeat responses.
 * Contains acknowledgment and any instructions for the agent.
 * 
 * @author Mark Andrew Ray-Smith Cityline Ltd
 * @since 2025-08-26
 * @version 1.0
 */
public class AgentHeartbeatResponse {

    @JsonProperty("success")
    private boolean success;

    @JsonProperty("message")
    private String message;

    @JsonProperty("agentId")
    private String agentId;

    @JsonProperty("acknowledgedSequenceNumber")
    private long acknowledgedSequenceNumber;

    @JsonProperty("serverTimestamp")
    private Instant serverTimestamp;

    @JsonProperty("nextHeartbeatInterval")
    private long nextHeartbeatInterval; // milliseconds

    @JsonProperty("assignedStatus")
    private AgentStatus assignedStatus;

    @JsonProperty("instructions")
    private List<AgentInstruction> instructions;

    @JsonProperty("configuration")
    private Map<String, Object> configuration;

    @JsonProperty("leaderEndpoint")
    private String leaderEndpoint;

    @JsonProperty("clusterHealth")
    private String clusterHealth;

    @JsonProperty("errorCode")
    private String errorCode;

    /**
     * Default constructor.
     */
    public AgentHeartbeatResponse() {
        this.serverTimestamp = Instant.now();
        this.instructions = new ArrayList<>();
        this.configuration = new HashMap<>();
    }

    /**
     * Create a successful heartbeat response.
     */
    public static AgentHeartbeatResponse success(String agentId, long sequenceNumber, long heartbeatInterval) {
        AgentHeartbeatResponse response = new AgentHeartbeatResponse();
        response.setSuccess(true);
        response.setMessage("Heartbeat acknowledged");
        response.setAgentId(agentId);
        response.setAcknowledgedSequenceNumber(sequenceNumber);
        response.setNextHeartbeatInterval(heartbeatInterval);
        return response;
    }

    /**
     * Create a failed heartbeat response.
     */
    public static AgentHeartbeatResponse failure(String agentId, String errorMessage, String errorCode) {
        AgentHeartbeatResponse response = new AgentHeartbeatResponse();
        response.setSuccess(false);
        response.setMessage(errorMessage);
        response.setAgentId(agentId);
        response.setErrorCode(errorCode);
        return response;
    }

    // Getters and setters

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getAgentId() {
        return agentId;
    }

    public void setAgentId(String agentId) {
        this.agentId = agentId;
    }

    public long getAcknowledgedSequenceNumber() {
        return acknowledgedSequenceNumber;
    }

    public void setAcknowledgedSequenceNumber(long acknowledgedSequenceNumber) {
        this.acknowledgedSequenceNumber = acknowledgedSequenceNumber;
    }

    public Instant getServerTimestamp() {
        return serverTimestamp;
    }

    public void setServerTimestamp(Instant serverTimestamp) {
        this.serverTimestamp = serverTimestamp;
    }

    public long getNextHeartbeatInterval() {
        return nextHeartbeatInterval;
    }

    public void setNextHeartbeatInterval(long nextHeartbeatInterval) {
        this.nextHeartbeatInterval = nextHeartbeatInterval;
    }

    public AgentStatus getAssignedStatus() {
        return assignedStatus;
    }

    public void setAssignedStatus(AgentStatus assignedStatus) {
        this.assignedStatus = assignedStatus;
    }

    public List<AgentInstruction> getInstructions() {
        return instructions;
    }

    public void setInstructions(List<AgentInstruction> instructions) {
        this.instructions = instructions;
    }

    public void addInstruction(AgentInstruction instruction) {
        this.instructions.add(instruction);
    }

    public Map<String, Object> getConfiguration() {
        return configuration;
    }

    public void setConfiguration(Map<String, Object> configuration) {
        this.configuration = configuration;
    }

    public String getLeaderEndpoint() {
        return leaderEndpoint;
    }

    public void setLeaderEndpoint(String leaderEndpoint) {
        this.leaderEndpoint = leaderEndpoint;
    }

    public String getClusterHealth() {
        return clusterHealth;
    }

    public void setClusterHealth(String clusterHealth) {
        this.clusterHealth = clusterHealth;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public void setErrorCode(String errorCode) {
        this.errorCode = errorCode;
    }

    /**
     * Agent instruction embedded class.
     */
    public static class AgentInstruction {
        @JsonProperty("type")
        private String type;

        @JsonProperty("action")
        private String action;

        @JsonProperty("parameters")
        private Map<String, Object> parameters;

        @JsonProperty("priority")
        private int priority;

        @JsonProperty("deadline")
        private Instant deadline;

        public AgentInstruction() {
            this.parameters = new HashMap<>();
        }

        public AgentInstruction(String type, String action) {
            this();
            this.type = type;
            this.action = action;
        }

        // Getters and setters
        public String getType() { return type; }
        public void setType(String type) { this.type = type; }

        public String getAction() { return action; }
        public void setAction(String action) { this.action = action; }

        public Map<String, Object> getParameters() { return parameters; }
        public void setParameters(Map<String, Object> parameters) { this.parameters = parameters; }

        public int getPriority() { return priority; }
        public void setPriority(int priority) { this.priority = priority; }

        public Instant getDeadline() { return deadline; }
        public void setDeadline(Instant deadline) { this.deadline = deadline; }
    }

    @Override
    public String toString() {
        return "AgentHeartbeatResponse{" +
                "success=" + success +
                ", agentId='" + agentId + '\'' +
                ", acknowledgedSequenceNumber=" + acknowledgedSequenceNumber +
                ", serverTimestamp=" + serverTimestamp +
                ", nextHeartbeatInterval=" + nextHeartbeatInterval +
                '}';
    }
}
