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

import java.io.Serializable;

/**
 * Sealed interface for all Raft state machine commands.
 *
 * <p>Using a sealed type provides compile-time exhaustiveness checking in
 * {@code switch} expressions (Java 21), eliminating the need for
 * {@code instanceof} chains and runtime fallback logic. The compiler
 * will flag any new permitted subtype that is missing from a switch.
 *
 * <p>All permitted subtypes represent a distinct category of state
 * mutation that the {@link QuorusStateStore} can apply:
 * <ul>
 *   <li>{@link TransferJobCommand} — transfer lifecycle operations</li>
 *   <li>{@link AgentCommand} — agent registration, heartbeat, capability updates</li>
 *   <li>{@link SystemMetadataCommand} — cluster metadata key-value operations</li>
 *   <li>{@link JobAssignmentCommand} — job-to-agent assignment operations</li>
 *   <li>{@link JobQueueCommand} — priority job queue operations</li>
 *   <li>{@link RouteCommand} — route lifecycle and trigger operations</li>
 * </ul>
 *
 * @author Mark Andrew Ray-Smith Cityline Ltd
 * @version 1.0
 * @since 2026-02-19
 */
public sealed interface RaftCommand extends Serializable
        permits TransferJobCommand, AgentCommand, SystemMetadataCommand,
                JobAssignmentCommand, JobQueueCommand, RouteCommand {
}
