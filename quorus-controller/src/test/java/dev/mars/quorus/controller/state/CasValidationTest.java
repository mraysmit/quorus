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

import com.google.protobuf.ByteString;
import dev.mars.quorus.agent.AgentCapabilities;
import dev.mars.quorus.agent.AgentInfo;
import dev.mars.quorus.agent.AgentStatus;
import dev.mars.quorus.core.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for Compare-And-Swap (CAS) validation in the Raft state machine.
 *
 * <p>Verifies that:
 * <ul>
 *   <li>CAS-protected UpdateStatus commands return {@link CommandResult.CasMismatch}
 *       when the entity's current status does not match the expected status</li>
 *   <li>CAS-protected commands succeed when expectedStatus matches</li>
 *   <li>CAS expectedStatus serialises correctly through protobuf</li>
 * </ul>
 *
 * @author Mark Andrew Ray-Smith Cityline Ltd
 * @version 1.0
 * @since 2026-02-21
 */
class CasValidationTest {

    private QuorusStateStore stateMachine;

    @BeforeEach
    void setUp() {
        stateMachine = new QuorusStateStore();
    }

    // ── TransferJob CAS ─────────────────────────────────────────

    @Nested
    @DisplayName("TransferJob CAS")
    class TransferJobCas {

        @Test
        @DisplayName("CAS succeeds when expectedStatus matches current status")
        void casSucceeds_whenExpectedStatusMatches() {
            TransferJob job = createTransferJob("job-cas-1");
            stateMachine.apply(TransferJobCommand.create(job));

            // Current status is PENDING; expect PENDING → should succeed
            CommandResult<?> result = stateMachine.apply(
                    TransferJobCommand.updateStatus("job-cas-1", TransferStatus.PENDING, TransferStatus.IN_PROGRESS));

            assertInstanceOf(CommandResult.Success.class, result);
            TransferJobSnapshot updated = (TransferJobSnapshot) ((CommandResult.Success<?>) result).entity();
            assertEquals(TransferStatus.IN_PROGRESS, updated.getStatus());
        }

        @Test
        @DisplayName("CAS returns CasMismatch when expectedStatus does not match")
        void casReturnsMismatch_whenExpectedStatusDoesNotMatch() {
            TransferJob job = createTransferJob("job-cas-2");
            stateMachine.apply(TransferJobCommand.create(job));

            // Current status is PENDING; expect IN_PROGRESS → should fail
            CommandResult<?> result = stateMachine.apply(
                    TransferJobCommand.updateStatus("job-cas-2", TransferStatus.IN_PROGRESS, TransferStatus.COMPLETED));

            assertInstanceOf(CommandResult.CasMismatch.class, result);
            TransferJobSnapshot snapshot = (TransferJobSnapshot) ((CommandResult.CasMismatch<?>) result).entity();
            assertEquals(TransferStatus.PENDING, snapshot.getStatus(), "Entity should reflect current (unchanged) status");
        }

        @Test
        @DisplayName("CAS returns NotFound when job does not exist")
        void casReturnsNotFound_whenJobDoesNotExist() {
            CommandResult<?> result = stateMachine.apply(
                    TransferJobCommand.updateStatus("ghost", TransferStatus.PENDING, TransferStatus.IN_PROGRESS));

            assertInstanceOf(CommandResult.NotFound.class, result);
        }
    }

    // ── Agent CAS ───────────────────────────────────────────────

    @Nested
    @DisplayName("Agent CAS")
    class AgentCas {

        @Test
        @DisplayName("CAS succeeds when expectedStatus matches current agent status")
        void casSucceeds_whenExpectedStatusMatches() {
            AgentInfo agent = createAgentInfo("agent-cas-1", AgentStatus.HEALTHY);
            stateMachine.apply(AgentCommand.register(agent));

            CommandResult<?> result = stateMachine.apply(
                    AgentCommand.updateStatus("agent-cas-1", AgentStatus.HEALTHY, AgentStatus.MAINTENANCE));

            assertInstanceOf(CommandResult.Success.class, result);
            AgentInfo updated = (AgentInfo) ((CommandResult.Success<?>) result).entity();
            assertEquals(AgentStatus.MAINTENANCE, updated.getStatus());
        }

        @Test
        @DisplayName("CAS returns CasMismatch when agent status does not match expected")
        void casReturnsMismatch_whenStatusDoesNotMatch() {
            AgentInfo agent = createAgentInfo("agent-cas-2", AgentStatus.HEALTHY);
            stateMachine.apply(AgentCommand.register(agent));

            // Expect DEGRADED but current is HEALTHY → mismatch
            CommandResult<?> result = stateMachine.apply(
                    AgentCommand.updateStatus("agent-cas-2", AgentStatus.DEGRADED, AgentStatus.MAINTENANCE));

            assertInstanceOf(CommandResult.CasMismatch.class, result);
            AgentInfo current = (AgentInfo) ((CommandResult.CasMismatch<?>) result).entity();
            assertEquals(AgentStatus.HEALTHY, current.getStatus());
        }

    }

    // ── JobAssignment CAS ───────────────────────────────────────

    @Nested
    @DisplayName("JobAssignment CAS")
    class JobAssignmentCas {

        @Test
        @DisplayName("CAS succeeds when expectedStatus matches current assignment status")
        void casSucceeds_whenExpectedStatusMatches() {
            JobAssignment assignment = createJobAssignment("job-1", "agent-1");
            stateMachine.apply(JobAssignmentCommand.assign(assignment));

            String assignmentId = "job-1:agent-1";
            CommandResult<?> result = stateMachine.apply(
                    JobAssignmentCommand.updateStatus(assignmentId, JobAssignmentStatus.ASSIGNED, JobAssignmentStatus.IN_PROGRESS));

            assertInstanceOf(CommandResult.Success.class, result);
            JobAssignment updated = (JobAssignment) ((CommandResult.Success<?>) result).entity();
            assertEquals(JobAssignmentStatus.IN_PROGRESS, updated.getStatus());
        }

        @Test
        @DisplayName("CAS returns CasMismatch when assignment status does not match expected")
        void casReturnsMismatch_whenStatusDoesNotMatch() {
            JobAssignment assignment = createJobAssignment("job-2", "agent-2");
            stateMachine.apply(JobAssignmentCommand.assign(assignment));

            String assignmentId = "job-2:agent-2";
            // Expect IN_PROGRESS but current is ASSIGNED → mismatch
            CommandResult<?> result = stateMachine.apply(
                    JobAssignmentCommand.updateStatus(assignmentId, JobAssignmentStatus.IN_PROGRESS, JobAssignmentStatus.COMPLETED));

            assertInstanceOf(CommandResult.CasMismatch.class, result);
            JobAssignment current = (JobAssignment) ((CommandResult.CasMismatch<?>) result).entity();
            assertEquals(JobAssignmentStatus.ASSIGNED, current.getStatus());
        }

    }

    // ── Route CAS ───────────────────────────────────────────────

    @Nested
    @DisplayName("Route CAS")
    class RouteCas {

        @Test
        @DisplayName("CAS succeeds when expectedStatus matches current route status")
        void casSucceeds_whenExpectedStatusMatches() {
            RouteConfiguration route = createRoute("route-cas-1");
            stateMachine.apply(RouteCommand.create(route));

            CommandResult<?> result = stateMachine.apply(
                    RouteCommand.updateStatus("route-cas-1", RouteStatus.CONFIGURED, RouteStatus.ACTIVE, "activated"));

            assertInstanceOf(CommandResult.Success.class, result);
            RouteConfiguration updated = (RouteConfiguration) ((CommandResult.Success<?>) result).entity();
            assertEquals(RouteStatus.ACTIVE, updated.getStatus());
        }

        @Test
        @DisplayName("CAS returns CasMismatch when route status does not match expected")
        void casReturnsMismatch_whenStatusDoesNotMatch() {
            RouteConfiguration route = createRoute("route-cas-2");
            stateMachine.apply(RouteCommand.create(route));

            // Expect ACTIVE but current is CONFIGURED → mismatch
            CommandResult<?> result = stateMachine.apply(
                    RouteCommand.updateStatus("route-cas-2", RouteStatus.ACTIVE, RouteStatus.TRIGGERED, "should fail"));

            assertInstanceOf(CommandResult.CasMismatch.class, result);
            RouteConfiguration current = (RouteConfiguration) ((CommandResult.CasMismatch<?>) result).entity();
            assertEquals(RouteStatus.CONFIGURED, current.getStatus());
        }

    }

    // ── Protobuf CAS roundtrip ──────────────────────────────────

    @Nested
    @DisplayName("Protobuf CAS Roundtrip")
    class ProtobufCasRoundtrip {

        @Test
        @DisplayName("TransferJobCommand CAS expectedStatus survives serialization")
        void transferJob_casSerializationRoundtrip() {
            TransferJobCommand original = TransferJobCommand.updateStatus(
                    "job-proto-1", TransferStatus.PENDING, TransferStatus.IN_PROGRESS);

            ByteString bytes = ProtobufCommandCodec.serialize(original);
            RaftCommand deserialized = ProtobufCommandCodec.deserialize(bytes);

            assertInstanceOf(TransferJobCommand.UpdateStatus.class, deserialized);
            TransferJobCommand.UpdateStatus restored = (TransferJobCommand.UpdateStatus) deserialized;
            assertEquals(TransferStatus.PENDING, restored.expectedStatus());
            assertEquals(TransferStatus.IN_PROGRESS, restored.newStatus());
        }

        @Test
        @DisplayName("AgentCommand CAS expectedStatus survives serialization")
        void agent_casSerializationRoundtrip() {
            AgentCommand original = AgentCommand.updateStatus(
                    "agent-proto-1", AgentStatus.HEALTHY, AgentStatus.MAINTENANCE);

            ByteString bytes = ProtobufCommandCodec.serialize(original);
            RaftCommand deserialized = ProtobufCommandCodec.deserialize(bytes);

            assertInstanceOf(AgentCommand.UpdateStatus.class, deserialized);
            AgentCommand.UpdateStatus restored = (AgentCommand.UpdateStatus) deserialized;
            assertEquals(AgentStatus.HEALTHY, restored.expectedStatus());
            assertEquals(AgentStatus.MAINTENANCE, restored.newStatus());
        }

        @Test
        @DisplayName("JobAssignmentCommand CAS expectedStatus survives serialization")
        void jobAssignment_casSerializationRoundtrip() {
            JobAssignmentCommand original = JobAssignmentCommand.updateStatus(
                    "assign-proto-1", JobAssignmentStatus.ASSIGNED, JobAssignmentStatus.IN_PROGRESS);

            ByteString bytes = ProtobufCommandCodec.serialize(original);
            RaftCommand deserialized = ProtobufCommandCodec.deserialize(bytes);

            assertInstanceOf(JobAssignmentCommand.UpdateStatus.class, deserialized);
            JobAssignmentCommand.UpdateStatus restored = (JobAssignmentCommand.UpdateStatus) deserialized;
            assertEquals(JobAssignmentStatus.ASSIGNED, restored.expectedStatus());
            assertEquals(JobAssignmentStatus.IN_PROGRESS, restored.newStatus());
        }

        @Test
        @DisplayName("RouteCommand CAS expectedStatus survives serialization")
        void route_casSerializationRoundtrip() {
            RouteCommand original = RouteCommand.updateStatus(
                    "route-proto-1", RouteStatus.CONFIGURED, RouteStatus.ACTIVE, "activated");

            ByteString bytes = ProtobufCommandCodec.serialize(original);
            RaftCommand deserialized = ProtobufCommandCodec.deserialize(bytes);

            assertInstanceOf(RouteCommand.UpdateStatus.class, deserialized);
            RouteCommand.UpdateStatus restored = (RouteCommand.UpdateStatus) deserialized;
            assertEquals(RouteStatus.CONFIGURED, restored.expectedStatus());
            assertEquals(RouteStatus.ACTIVE, restored.newStatus());
        }

    }

    // ── CommandResult.CasMismatch ───────────────────────────────

    @Nested
    @DisplayName("CommandResult.CasMismatch")
    class CasMismatchRecord {

        @Test
        @DisplayName("CasMismatch carries the entity in its current state")
        void casMismatch_carriesCurrentEntity() {
            TransferJob job = createTransferJob("job-mismatch");
            stateMachine.apply(TransferJobCommand.create(job));

            // Force mismatch
            CommandResult<?> result = stateMachine.apply(
                    TransferJobCommand.updateStatus("job-mismatch", TransferStatus.COMPLETED, TransferStatus.FAILED));

            assertInstanceOf(CommandResult.CasMismatch.class, result);
            Object entity = ((CommandResult.CasMismatch<?>) result).entity();
            assertNotNull(entity);
            assertInstanceOf(TransferJobSnapshot.class, entity);
        }

        @Test
        @DisplayName("CasMismatch rejects null entity")
        void casMismatch_rejectsNullEntity() {
            assertThrows(NullPointerException.class, () -> new CommandResult.CasMismatch<>(null));
        }
    }

    // ── Helper methods ──────────────────────────────────────────

    private TransferJob createTransferJob(String jobId) {
        TransferRequest request = TransferRequest.builder()
                .requestId(jobId)
                .sourceUri(URI.create("https://example.com/file.csv"))
                .destinationUri(URI.create("file:///data/file.csv"))
                .protocol("https")
                .build();
        return new TransferJob(request);
    }

    private AgentInfo createAgentInfo(String agentId, AgentStatus status) {
        AgentInfo info = new AgentInfo(agentId, "host-" + agentId, "127.0.0.1", 8080);
        info.setStatus(status);
        info.setCapabilities(new AgentCapabilities());
        return info;
    }

    private JobAssignment createJobAssignment(String jobId, String agentId) {
        return new JobAssignment.Builder()
                .jobId(jobId)
                .agentId(agentId)
                .status(JobAssignmentStatus.ASSIGNED)
                .assignedAt(Instant.now())
                .build();
    }

    private RouteConfiguration createRoute(String routeId) {
        return new RouteConfiguration(
                routeId, "Test Route", "Description",
                "source-agent", "/source",
                "dest-agent", "/dest",
                null, RouteStatus.CONFIGURED, null,
                Instant.now(), null);
    }
}
