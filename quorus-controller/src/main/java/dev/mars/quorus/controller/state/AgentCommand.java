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

import dev.mars.quorus.agent.AgentInfo;
import dev.mars.quorus.agent.AgentStatus;
import dev.mars.quorus.agent.AgentCapabilities;

import java.io.Serializable;
import java.time.Instant;

/**
 * Command for agent operations in the Raft state machine.
 * This class represents commands that can be submitted to the distributed controller
 * for managing agents in the fleet.
 * 
 * @author Mark Andrew Ray-Smith Cityline Ltd
 * @since 2025-08-26
 * @version 1.0
 */
public class AgentCommand implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * Types of agent commands.
     */
    public enum CommandType {
        REGISTER,
        DEREGISTER,
        UPDATE_STATUS,
        UPDATE_CAPABILITIES,
        HEARTBEAT
    }

    private final CommandType type;
    private final String agentId;
    private final AgentInfo agentInfo;
    private final AgentStatus newStatus;
    private final AgentCapabilities newCapabilities;
    private final Instant timestamp;

    /**
     * Private constructor for creating commands.
     */
    private AgentCommand(CommandType type, String agentId, AgentInfo agentInfo, 
                        AgentStatus newStatus, AgentCapabilities newCapabilities) {
        this(type, agentId, agentInfo, newStatus, newCapabilities, null);
    }

    /**
     * Package-private constructor for protobuf deserialization with explicit timestamp.
     */
    AgentCommand(CommandType type, String agentId, AgentInfo agentInfo,
                 AgentStatus newStatus, AgentCapabilities newCapabilities, Instant timestamp) {
        this.type = type;
        this.agentId = agentId;
        this.agentInfo = agentInfo;
        this.newStatus = newStatus;
        this.newCapabilities = newCapabilities;
        this.timestamp = (timestamp != null) ? timestamp : Instant.now();
    }

    /**
     * Create a command to register a new agent.
     * 
     * @param agentInfo the agent to register
     * @return the command
     */
    public static AgentCommand register(AgentInfo agentInfo) {
        return new AgentCommand(CommandType.REGISTER, agentInfo.getAgentId(), agentInfo, null, null);
    }

    /**
     * Create a command to deregister an agent.
     * 
     * @param agentId the agent ID to deregister
     * @return the command
     */
    public static AgentCommand deregister(String agentId) {
        return new AgentCommand(CommandType.DEREGISTER, agentId, null, AgentStatus.DEREGISTERED, null);
    }

    /**
     * Create a command to update an agent's status.
     * 
     * @param agentId the agent ID
     * @param newStatus the new status
     * @return the command
     */
    public static AgentCommand updateStatus(String agentId, AgentStatus newStatus) {
        return new AgentCommand(CommandType.UPDATE_STATUS, agentId, null, newStatus, null);
    }

    /**
     * Create a command to update an agent's capabilities.
     * 
     * @param agentId the agent ID
     * @param newCapabilities the new capabilities
     * @return the command
     */
    public static AgentCommand updateCapabilities(String agentId, AgentCapabilities newCapabilities) {
        return new AgentCommand(CommandType.UPDATE_CAPABILITIES, agentId, null, null, newCapabilities);
    }

    /**
     * Create a command to record an agent heartbeat.
     *
     * @param agentId the agent ID
     * @return the command
     */
    public static AgentCommand heartbeat(String agentId) {
        return new AgentCommand(CommandType.HEARTBEAT, agentId, null, null, null);
    }

    /**
     * Create a command to record an agent heartbeat with status.
     *
     * @param agentId the agent ID
     * @param status the agent status
     * @param timestamp the heartbeat timestamp
     * @return the command
     */
    public static AgentCommand heartbeat(String agentId, AgentStatus status, Instant timestamp) {
        return new AgentCommand(CommandType.HEARTBEAT, agentId, null, status, null);
    }

    /**
     * Get the command type.
     * 
     * @return the command type
     */
    public CommandType getType() {
        return type;
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
     * Get the agent info (for REGISTER commands).
     * 
     * @return the agent info, or null if not applicable
     */
    public AgentInfo getAgentInfo() {
        return agentInfo;
    }

    /**
     * Get the new status (for UPDATE_STATUS commands).
     * 
     * @return the new status, or null if not applicable
     */
    public AgentStatus getNewStatus() {
        return newStatus;
    }

    /**
     * Get the new capabilities (for UPDATE_CAPABILITIES commands).
     * 
     * @return the new capabilities, or null if not applicable
     */
    public AgentCapabilities getNewCapabilities() {
        return newCapabilities;
    }

    /**
     * Get the command timestamp.
     * 
     * @return the timestamp when the command was created
     */
    public Instant getTimestamp() {
        return timestamp;
    }

    /**
     * Check if this is a REGISTER command.
     * 
     * @return true if this is a REGISTER command
     */
    public boolean isRegister() {
        return type == CommandType.REGISTER;
    }

    /**
     * Check if this is a DEREGISTER command.
     * 
     * @return true if this is a DEREGISTER command
     */
    public boolean isDeregister() {
        return type == CommandType.DEREGISTER;
    }

    /**
     * Check if this is an UPDATE_STATUS command.
     * 
     * @return true if this is an UPDATE_STATUS command
     */
    public boolean isUpdateStatus() {
        return type == CommandType.UPDATE_STATUS;
    }

    /**
     * Check if this is an UPDATE_CAPABILITIES command.
     * 
     * @return true if this is an UPDATE_CAPABILITIES command
     */
    public boolean isUpdateCapabilities() {
        return type == CommandType.UPDATE_CAPABILITIES;
    }

    /**
     * Check if this is a HEARTBEAT command.
     * 
     * @return true if this is a HEARTBEAT command
     */
    public boolean isHeartbeat() {
        return type == CommandType.HEARTBEAT;
    }

    @Override
    public String toString() {
        return "AgentCommand{" +
                "type=" + type +
                ", agentId='" + agentId + '\'' +
                ", newStatus=" + newStatus +
                ", timestamp=" + timestamp +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        AgentCommand that = (AgentCommand) o;

        if (type != that.type) return false;
        if (!agentId.equals(that.agentId)) return false;
        if (agentInfo != null ? !agentInfo.equals(that.agentInfo) : that.agentInfo != null) return false;
        if (newStatus != that.newStatus) return false;
        if (newCapabilities != null ? !newCapabilities.equals(that.newCapabilities) : that.newCapabilities != null)
            return false;
        return timestamp.equals(that.timestamp);
    }

    @Override
    public int hashCode() {
        int result = type.hashCode();
        result = 31 * result + agentId.hashCode();
        result = 31 * result + (agentInfo != null ? agentInfo.hashCode() : 0);
        result = 31 * result + (newStatus != null ? newStatus.hashCode() : 0);
        result = 31 * result + (newCapabilities != null ? newCapabilities.hashCode() : 0);
        result = 31 * result + timestamp.hashCode();
        return result;
    }
}
