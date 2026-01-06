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

import dev.mars.quorus.agent.AgentInfo;
import dev.mars.quorus.agent.AgentStatus;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.Map;

/**
 * DTO for agent registration responses.
 * Contains the result of an agent registration attempt.
 * 
 * @author Mark Andrew Ray-Smith Cityline Ltd
 * @since 2025-08-26
 * @version 1.0
 */
public class AgentRegistrationResponse {

    @JsonProperty("success")
    private boolean success;

    @JsonProperty("message")
    private String message;

    @JsonProperty("agentId")
    private String agentId;

    @JsonProperty("status")
    private AgentStatus status;

    @JsonProperty("registrationTime")
    private Instant registrationTime;

    @JsonProperty("heartbeatInterval")
    private long heartbeatInterval; // milliseconds

    @JsonProperty("controllerEndpoint")
    private String controllerEndpoint;

    @JsonProperty("assignedRegion")
    private String assignedRegion;

    @JsonProperty("assignedDatacenter")
    private String assignedDatacenter;

    @JsonProperty("configuration")
    private Map<String, Object> configuration;

    @JsonProperty("errorCode")
    private String errorCode;

    /**
     * Default constructor.
     */
    public AgentRegistrationResponse() {
    }

    /**
     * Create a successful registration response.
     * 
     * @param agentInfo the registered agent info
     * @param heartbeatInterval the heartbeat interval in milliseconds
     * @param controllerEndpoint the controller endpoint
     * @return the registration response
     */
    public static AgentRegistrationResponse success(AgentInfo agentInfo, 
                                                   long heartbeatInterval, 
                                                   String controllerEndpoint) {
        AgentRegistrationResponse response = new AgentRegistrationResponse();
        response.setSuccess(true);
        response.setMessage("Agent registered successfully");
        response.setAgentId(agentInfo.getAgentId());
        response.setStatus(agentInfo.getStatus());
        response.setRegistrationTime(agentInfo.getRegistrationTime());
        response.setHeartbeatInterval(heartbeatInterval);
        response.setControllerEndpoint(controllerEndpoint);
        response.setAssignedRegion(agentInfo.getRegion());
        response.setAssignedDatacenter(agentInfo.getDatacenter());
        return response;
    }

    /**
     * Create a failed registration response.
     * 
     * @param message the error message
     * @param errorCode the error code
     * @return the registration response
     */
    public static AgentRegistrationResponse failure(String message, String errorCode) {
        AgentRegistrationResponse response = new AgentRegistrationResponse();
        response.setSuccess(false);
        response.setMessage(message);
        response.setErrorCode(errorCode);
        return response;
    }

    /**
     * Check if the registration was successful.
     * 
     * @return true if successful
     */
    public boolean isSuccess() {
        return success;
    }

    /**
     * Set the success status.
     * 
     * @param success the success status
     */
    public void setSuccess(boolean success) {
        this.success = success;
    }

    /**
     * Get the response message.
     * 
     * @return the message
     */
    public String getMessage() {
        return message;
    }

    /**
     * Set the response message.
     * 
     * @param message the message
     */
    public void setMessage(String message) {
        this.message = message;
    }

    /**
     * Get the agent ID.
     * 
     * @return the agent ID
     */
    public String getAgentId() {
        return agentId;
    }

    /**
     * Set the agent ID.
     * 
     * @param agentId the agent ID
     */
    public void setAgentId(String agentId) {
        this.agentId = agentId;
    }

    /**
     * Get the agent status.
     * 
     * @return the status
     */
    public AgentStatus getStatus() {
        return status;
    }

    /**
     * Set the agent status.
     * 
     * @param status the status
     */
    public void setStatus(AgentStatus status) {
        this.status = status;
    }

    /**
     * Get the registration time.
     * 
     * @return the registration time
     */
    public Instant getRegistrationTime() {
        return registrationTime;
    }

    /**
     * Set the registration time.
     * 
     * @param registrationTime the registration time
     */
    public void setRegistrationTime(Instant registrationTime) {
        this.registrationTime = registrationTime;
    }

    /**
     * Get the heartbeat interval.
     * 
     * @return the heartbeat interval in milliseconds
     */
    public long getHeartbeatInterval() {
        return heartbeatInterval;
    }

    /**
     * Set the heartbeat interval.
     * 
     * @param heartbeatInterval the heartbeat interval in milliseconds
     */
    public void setHeartbeatInterval(long heartbeatInterval) {
        this.heartbeatInterval = heartbeatInterval;
    }

    /**
     * Get the controller endpoint.
     * 
     * @return the controller endpoint
     */
    public String getControllerEndpoint() {
        return controllerEndpoint;
    }

    /**
     * Set the controller endpoint.
     * 
     * @param controllerEndpoint the controller endpoint
     */
    public void setControllerEndpoint(String controllerEndpoint) {
        this.controllerEndpoint = controllerEndpoint;
    }

    /**
     * Get the assigned region.
     * 
     * @return the assigned region
     */
    public String getAssignedRegion() {
        return assignedRegion;
    }

    /**
     * Set the assigned region.
     * 
     * @param assignedRegion the assigned region
     */
    public void setAssignedRegion(String assignedRegion) {
        this.assignedRegion = assignedRegion;
    }

    /**
     * Get the assigned datacenter.
     * 
     * @return the assigned datacenter
     */
    public String getAssignedDatacenter() {
        return assignedDatacenter;
    }

    /**
     * Set the assigned datacenter.
     * 
     * @param assignedDatacenter the assigned datacenter
     */
    public void setAssignedDatacenter(String assignedDatacenter) {
        this.assignedDatacenter = assignedDatacenter;
    }

    /**
     * Get the configuration.
     * 
     * @return the configuration
     */
    public Map<String, Object> getConfiguration() {
        return configuration;
    }

    /**
     * Set the configuration.
     * 
     * @param configuration the configuration
     */
    public void setConfiguration(Map<String, Object> configuration) {
        this.configuration = configuration;
    }

    /**
     * Get the error code.
     * 
     * @return the error code
     */
    public String getErrorCode() {
        return errorCode;
    }

    /**
     * Set the error code.
     * 
     * @param errorCode the error code
     */
    public void setErrorCode(String errorCode) {
        this.errorCode = errorCode;
    }

    @Override
    public String toString() {
        return "AgentRegistrationResponse{" +
                "success=" + success +
                ", message='" + message + '\'' +
                ", agentId='" + agentId + '\'' +
                ", status=" + status +
                ", registrationTime=" + registrationTime +
                ", heartbeatInterval=" + heartbeatInterval +
                ", controllerEndpoint='" + controllerEndpoint + '\'' +
                ", errorCode='" + errorCode + '\'' +
                '}';
    }
}
