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

import java.time.Instant;
import java.util.Objects;

/**
 * Sealed interface for agent lifecycle commands.
 *
 * <p>Each permitted subtype carries only the fields relevant to its operation,
 * eliminating nullable "bag-of-fields" patterns. Pattern matching in
 * {@code switch} expressions provides compile-time exhaustiveness.
 *
 * <h3>Permitted subtypes</h3>
 * <ul>
 *   <li>{@link Register} — register a new agent</li>
 *   <li>{@link Deregister} — deregister an agent</li>
 *   <li>{@link UpdateStatus} — change agent status</li>
 *   <li>{@link UpdateCapabilities} — update agent capabilities</li>
 *   <li>{@link Heartbeat} — record an agent heartbeat</li>
 * </ul>
 *
 * @author Mark Andrew Ray-Smith Cityline Ltd
 * @version 2.0
 * @since 2025-08-26
 */
public sealed interface AgentCommand extends RaftCommand
        permits AgentCommand.Register,
                AgentCommand.Deregister,
                AgentCommand.UpdateStatus,
                AgentCommand.UpdateCapabilities,
                AgentCommand.Heartbeat {

    /** Common accessor: every subtype carries an agent ID. */
    String agentId();

    /** Common accessor: every subtype carries a timestamp. */
    Instant timestamp();

    /**
     * Register a new agent.
     *
     * @param agentId   the agent identifier
     * @param agentInfo the full agent information
     * @param timestamp the command timestamp
     */
    record Register(String agentId, AgentInfo agentInfo, Instant timestamp) implements AgentCommand {
        private static final long serialVersionUID = 1L;

        public Register {
            Objects.requireNonNull(agentId, "agentId");
            Objects.requireNonNull(agentInfo, "agentInfo");
            Objects.requireNonNull(timestamp, "timestamp");
        }
    }

    /**
     * Deregister an agent.
     *
     * @param agentId   the agent identifier
     * @param timestamp the command timestamp
     */
    record Deregister(String agentId, Instant timestamp) implements AgentCommand {
        private static final long serialVersionUID = 1L;

        public Deregister {
            Objects.requireNonNull(agentId, "agentId");
            Objects.requireNonNull(timestamp, "timestamp");
        }
    }

    /**
     * Update the status of an existing agent.
     *
     * @param agentId        the agent identifier
     * @param expectedStatus the expected current status for CAS validation (null to skip check)
     * @param newStatus      the new status
     * @param timestamp      the command timestamp
     */
    record UpdateStatus(String agentId, AgentStatus expectedStatus, AgentStatus newStatus, Instant timestamp) implements AgentCommand {
        private static final long serialVersionUID = 1L;

        public UpdateStatus {
            Objects.requireNonNull(agentId, "agentId");
            Objects.requireNonNull(expectedStatus, "expectedStatus");
            Objects.requireNonNull(newStatus, "newStatus");
            Objects.requireNonNull(timestamp, "timestamp");
        }
    }

    /**
     * Update the capabilities of an existing agent.
     *
     * @param agentId         the agent identifier
     * @param newCapabilities the new capabilities
     * @param timestamp       the command timestamp
     */
    record UpdateCapabilities(String agentId, AgentCapabilities newCapabilities, Instant timestamp) implements AgentCommand {
        private static final long serialVersionUID = 1L;

        public UpdateCapabilities {
            Objects.requireNonNull(agentId, "agentId");
            Objects.requireNonNull(newCapabilities, "newCapabilities");
            Objects.requireNonNull(timestamp, "timestamp");
        }
    }

    /**
     * Record an agent heartbeat.
     *
     * @param agentId   the agent identifier
     * @param status    optional status update with heartbeat (may be null)
     * @param timestamp the command timestamp
     */
    record Heartbeat(String agentId, AgentStatus status, Instant timestamp) implements AgentCommand {
        private static final long serialVersionUID = 1L;

        public Heartbeat {
            Objects.requireNonNull(agentId, "agentId");
            Objects.requireNonNull(timestamp, "timestamp");
            // status may be null — heartbeat doesn't always carry a status update
        }
    }

    // ── Factory methods (preserve existing API) ─────────────────

    /**
     * Create a command to register a new agent.
     */
    static AgentCommand register(AgentInfo agentInfo) {
        return new Register(agentInfo.getAgentId(), agentInfo, Instant.now());
    }

    /**
     * Create a command to deregister an agent.
     */
    static AgentCommand deregister(String agentId) {
        return new Deregister(agentId, Instant.now());
    }

    /**
     * Create a command to update an agent's status with CAS protection.
     *
     * @param agentId        the agent identifier
     * @param expectedStatus the expected current status (must match for command to apply)
     * @param newStatus      the new status
     */
    static AgentCommand updateStatus(String agentId, AgentStatus expectedStatus, AgentStatus newStatus) {
        return new UpdateStatus(agentId, expectedStatus, newStatus, Instant.now());
    }

    /**
     * Create a command to update an agent's capabilities.
     */
    static AgentCommand updateCapabilities(String agentId, AgentCapabilities newCapabilities) {
        return new UpdateCapabilities(agentId, newCapabilities, Instant.now());
    }

    /**
     * Create a command to record an agent heartbeat.
     */
    static AgentCommand heartbeat(String agentId) {
        return new Heartbeat(agentId, null, Instant.now());
    }

    /**
     * Create a command to record an agent heartbeat with status.
     */
    static AgentCommand heartbeat(String agentId, AgentStatus status, Instant timestamp) {
        return new Heartbeat(agentId, status, timestamp != null ? timestamp : Instant.now());
    }
}
