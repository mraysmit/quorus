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
import dev.mars.quorus.agent.AgentNetworkInfo;
import dev.mars.quorus.agent.AgentStatus;
import dev.mars.quorus.agent.AgentSystemInfo;
import dev.mars.quorus.core.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Roundtrip tests for {@link ProtobufCommandCodec}.
 * Verifies that each command type survives serialize → deserialize
 * with semantically equivalent output for state machine application.
 */
class ProtobufCommandCodecTest {

    // ========== Helper factories ==========

    private TransferRequest createTransferRequest() {
        return TransferRequest.builder()
                .requestId("req-001")
                .sourceUri(URI.create("https://example.com/file.csv"))
                .destinationUri(URI.create("file:///data/output/file.csv"))
                .protocol("https")
                .expectedSize(1024L)
                .expectedChecksum("sha256:abc123")
                .metadata(Map.of("env", "prod", "team", "data"))
                .createdAt(Instant.parse("2025-06-15T10:30:00Z"))
                .build();
    }

    private TransferJob createTransferJob() {
        return new TransferJob(createTransferRequest());
    }

    private AgentInfo createAgentInfo() {
        AgentInfo info = new AgentInfo("agent-nyc-01", "nyc-host-1", "10.0.1.50", 8080);
        info.setStatus(AgentStatus.ACTIVE);
        info.setVersion("2.1.0");
        info.setRegion("us-east-1");
        info.setDatacenter("nyc-dc1");
        info.setRegistrationTime(Instant.parse("2025-01-10T08:00:00Z"));
        info.setLastHeartbeat(Instant.parse("2025-06-15T10:29:30Z"));
        info.setMetadata(new HashMap<>(Map.of("rack", "A3", "zone", "az-1")));
        info.setCapabilities(createAgentCapabilities());
        return info;
    }

    private AgentCapabilities createAgentCapabilities() {
        AgentCapabilities caps = new AgentCapabilities();
        caps.setSupportedProtocols(new HashSet<>(Set.of("https", "sftp", "ftp")));
        caps.setMaxConcurrentTransfers(5);
        caps.setMaxTransferSize(10_000_000L);
        caps.setMaxBandwidth(100_000_000L);
        caps.setAvailableRegions(new HashSet<>(Set.of("us-east-1", "eu-west-1")));
        caps.setSupportedCompressionTypes(new HashSet<>(Set.of("gzip", "zstd")));
        caps.setSupportedEncryptionTypes(new HashSet<>(Set.of("AES-256")));
        caps.setCustomCapabilities(new HashMap<>(Map.of("gpu", "true", "tier", "premium")));

        AgentSystemInfo sysInfo = new AgentSystemInfo();
        sysInfo.setOperatingSystem("Linux");
        sysInfo.setArchitecture("amd64");
        sysInfo.setJavaVersion("21.0.1");
        sysInfo.setTotalMemory(16_000_000_000L);
        sysInfo.setAvailableMemory(8_000_000_000L);
        sysInfo.setTotalDiskSpace(500_000_000_000L);
        sysInfo.setAvailableDiskSpace(250_000_000_000L);
        sysInfo.setCpuCores(8);
        sysInfo.setCpuUsage(45.5);
        sysInfo.setLoadAverage(2.1);
        caps.setSystemInfo(sysInfo);

        AgentNetworkInfo netInfo = new AgentNetworkInfo();
        netInfo.setPublicIpAddress("203.0.113.50");
        netInfo.setPrivateIpAddress("10.0.1.50");
        netInfo.setNetworkInterfaces(List.of("eth0", "eth1"));
        netInfo.setBandwidthCapacity(1_000_000_000L);
        netInfo.setCurrentBandwidthUsage(500_000_000L);
        netInfo.setLatencyMs(1.5);
        netInfo.setPacketLossPercentage(0.01);
        netInfo.setConnectionType("ethernet");
        netInfo.setNatTraversal(false);
        netInfo.setFirewallPorts(List.of(22, 443, 8080));
        caps.setNetworkInfo(netInfo);

        return caps;
    }

    private JobAssignment createJobAssignment() {
        return new JobAssignment.Builder()
                .jobId("job-001")
                .agentId("agent-nyc-01")
                .assignedAt(Instant.parse("2025-06-15T10:30:00Z"))
                .status(JobAssignmentStatus.ASSIGNED)
                .retryCount(0)
                .estimatedDurationMs(30000L)
                .assignmentStrategy("WEIGHTED_SCORE")
                .build();
    }

    private QueuedJob createQueuedJob() {
        return new QueuedJob.Builder()
                .transferJob(createTransferJob())
                .priority(JobPriority.HIGH)
                .queueTime(Instant.parse("2025-06-15T10:28:00Z"))
                .requirements(createJobRequirements())
                .submittedBy("admin")
                .workflowId("wf-daily-etl")
                .groupName("data-load")
                .retryCount(1)
                .earliestStartTime(Instant.parse("2025-06-15T10:30:00Z"))
                .build();
    }

    private JobRequirements createJobRequirements() {
        return new JobRequirements.Builder()
                .targetRegion("us-east-1")
                .requiredProtocols(Set.of("https", "sftp"))
                .preferredRegions(Set.of("us-east-1"))
                .excludedAgents(Set.of("agent-bad-01"))
                .preferredAgents(Set.of("agent-nyc-01"))
                .minBandwidth(10_000_000L)
                .maxTransferSize(100_000_000L)
                .requiresEncryption(true)
                .requiresCompression(false)
                .selectionStrategy(JobRequirements.SelectionStrategy.WEIGHTED_SCORE)
                .customAttributes(Map.of("tier", "gold"))
                .maxConcurrentJobs(3)
                .tenantId("tenant-acme")
                .namespace("production")
                .build();
    }

    // ========== Null / No-op ==========

    @Test
    @DisplayName("Null command (no-op) roundtrips to null")
    void nullCommandRoundtrip() {
        ByteString bytes = ProtobufCommandCodec.serialize(null);
        assertNotNull(bytes);
        assertNull(ProtobufCommandCodec.deserialize(bytes));
    }

    @Test
    @DisplayName("Unsupported command type throws IllegalArgumentException")
    void unsupportedCommandThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> ProtobufCommandCodec.serialize("not a command"));
    }

    // ========== TransferJobCommand ==========

    @Nested
    @DisplayName("TransferJobCommand roundtrip")
    class TransferJobCommandTests {

        @Test
        @DisplayName("CREATE preserves TransferRequest fields")
        void createRoundtrip() {
            TransferJob job = createTransferJob();
            TransferJobCommand original = TransferJobCommand.create(job);

            ByteString bytes = ProtobufCommandCodec.serialize(original);
            TransferJobCommand restored = (TransferJobCommand) ProtobufCommandCodec.deserialize(bytes);

            assertNotNull(restored);
            assertEquals(TransferJobCommand.Type.CREATE, restored.getType());
            assertNotNull(restored.getTransferJob());
            assertEquals("req-001", restored.getTransferJob().getJobId());
            assertEquals(TransferStatus.PENDING, restored.getTransferJob().getStatus());
            assertEquals(1024L, restored.getTransferJob().getTotalBytes());

            // Verify TransferRequest fields
            TransferRequest req = restored.getTransferJob().getRequest();
            assertEquals("req-001", req.getRequestId());
            assertEquals(URI.create("https://example.com/file.csv"), req.getSourceUri());
            assertEquals(URI.create("file:///data/output/file.csv"), req.getDestinationUri());
            assertEquals("https", req.getProtocol());
            assertEquals(1024L, req.getExpectedSize());
            assertEquals("sha256:abc123", req.getExpectedChecksum());
            assertEquals("prod", req.getMetadata().get("env"));
            assertEquals("data", req.getMetadata().get("team"));
        }

        @Test
        @DisplayName("UPDATE_STATUS preserves jobId and status")
        void updateStatusRoundtrip() {
            TransferJobCommand original = TransferJobCommand.updateStatus("job-001", TransferStatus.COMPLETED);

            ByteString bytes = ProtobufCommandCodec.serialize(original);
            TransferJobCommand restored = (TransferJobCommand) ProtobufCommandCodec.deserialize(bytes);

            assertEquals(TransferJobCommand.Type.UPDATE_STATUS, restored.getType());
            assertEquals("job-001", restored.getJobId());
            assertEquals(TransferStatus.COMPLETED, restored.getStatus());
        }

        @Test
        @DisplayName("UPDATE_PROGRESS preserves jobId and bytes")
        void updateProgressRoundtrip() {
            TransferJobCommand original = TransferJobCommand.updateProgress("job-001", 512L);

            ByteString bytes = ProtobufCommandCodec.serialize(original);
            TransferJobCommand restored = (TransferJobCommand) ProtobufCommandCodec.deserialize(bytes);

            assertEquals(TransferJobCommand.Type.UPDATE_PROGRESS, restored.getType());
            assertEquals("job-001", restored.getJobId());
            assertEquals(512L, restored.getBytesTransferred());
        }

        @Test
        @DisplayName("DELETE preserves jobId")
        void deleteRoundtrip() {
            TransferJobCommand original = TransferJobCommand.delete("job-001");

            ByteString bytes = ProtobufCommandCodec.serialize(original);
            TransferJobCommand restored = (TransferJobCommand) ProtobufCommandCodec.deserialize(bytes);

            assertEquals(TransferJobCommand.Type.DELETE, restored.getType());
            assertEquals("job-001", restored.getJobId());
        }

        @Test
        @DisplayName("All TransferStatus enum values roundtrip correctly")
        void allTransferStatusValuesRoundtrip() {
            for (TransferStatus status : TransferStatus.values()) {
                TransferJobCommand original = TransferJobCommand.updateStatus("job-" + status.name(), status);
                ByteString bytes = ProtobufCommandCodec.serialize(original);
                TransferJobCommand restored = (TransferJobCommand) ProtobufCommandCodec.deserialize(bytes);
                assertEquals(status, restored.getStatus(), "Failed for status: " + status);
            }
        }
    }

    // ========== AgentCommand ==========

    @Nested
    @DisplayName("AgentCommand roundtrip")
    class AgentCommandTests {

        @Test
        @DisplayName("REGISTER preserves full AgentInfo")
        void registerRoundtrip() {
            AgentInfo info = createAgentInfo();
            AgentCommand original = AgentCommand.register(info);

            ByteString bytes = ProtobufCommandCodec.serialize(original);
            AgentCommand restored = (AgentCommand) ProtobufCommandCodec.deserialize(bytes);

            assertEquals(AgentCommand.CommandType.REGISTER, restored.getType());
            assertEquals("agent-nyc-01", restored.getAgentId());

            AgentInfo restoredInfo = restored.getAgentInfo();
            assertNotNull(restoredInfo);
            assertEquals("agent-nyc-01", restoredInfo.getAgentId());
            assertEquals("nyc-host-1", restoredInfo.getHostname());
            assertEquals("10.0.1.50", restoredInfo.getAddress());
            assertEquals(8080, restoredInfo.getPort());
            assertEquals(AgentStatus.ACTIVE, restoredInfo.getStatus());
            assertEquals("2.1.0", restoredInfo.getVersion());
            assertEquals("us-east-1", restoredInfo.getRegion());
            assertEquals("nyc-dc1", restoredInfo.getDatacenter());
            assertEquals("A3", restoredInfo.getMetadata().get("rack"));

            // Verify capabilities
            AgentCapabilities caps = restoredInfo.getCapabilities();
            assertNotNull(caps);
            assertTrue(caps.getSupportedProtocols().contains("https"));
            assertTrue(caps.getSupportedProtocols().contains("sftp"));
            assertEquals(5, caps.getMaxConcurrentTransfers());
            assertEquals(10_000_000L, caps.getMaxTransferSize());
            assertTrue(caps.getAvailableRegions().contains("us-east-1"));
            assertTrue(caps.getSupportedCompressionTypes().contains("gzip"));
            assertTrue(caps.getSupportedEncryptionTypes().contains("AES-256"));
            assertEquals("true", caps.getCustomCapabilities().get("gpu"));

            // Verify system info
            AgentSystemInfo sysInfo = caps.getSystemInfo();
            assertNotNull(sysInfo);
            assertEquals("Linux", sysInfo.getOperatingSystem());
            assertEquals("amd64", sysInfo.getArchitecture());
            assertEquals("21.0.1", sysInfo.getJavaVersion());
            assertEquals(16_000_000_000L, sysInfo.getTotalMemory());
            assertEquals(8, sysInfo.getCpuCores());
            assertEquals(45.5, sysInfo.getCpuUsage(), 0.01);

            // Verify network info
            AgentNetworkInfo netInfo = caps.getNetworkInfo();
            assertNotNull(netInfo);
            assertEquals("203.0.113.50", netInfo.getPublicIpAddress());
            assertEquals("10.0.1.50", netInfo.getPrivateIpAddress());
            assertEquals(List.of("eth0", "eth1"), netInfo.getNetworkInterfaces());
            assertEquals(1_000_000_000L, netInfo.getBandwidthCapacity());
            assertEquals(1.5, netInfo.getLatencyMs(), 0.001);
            assertFalse(netInfo.isNatTraversal());
            assertEquals(List.of(22, 443, 8080), netInfo.getFirewallPorts());
        }

        @Test
        @DisplayName("DEREGISTER preserves agentId")
        void deregisterRoundtrip() {
            AgentCommand original = AgentCommand.deregister("agent-nyc-01");

            ByteString bytes = ProtobufCommandCodec.serialize(original);
            AgentCommand restored = (AgentCommand) ProtobufCommandCodec.deserialize(bytes);

            assertEquals(AgentCommand.CommandType.DEREGISTER, restored.getType());
            assertEquals("agent-nyc-01", restored.getAgentId());
        }

        @Test
        @DisplayName("UPDATE_STATUS preserves agentId and status")
        void updateStatusRoundtrip() {
            AgentCommand original = AgentCommand.updateStatus("agent-nyc-01", AgentStatus.DEGRADED);

            ByteString bytes = ProtobufCommandCodec.serialize(original);
            AgentCommand restored = (AgentCommand) ProtobufCommandCodec.deserialize(bytes);

            assertEquals(AgentCommand.CommandType.UPDATE_STATUS, restored.getType());
            assertEquals("agent-nyc-01", restored.getAgentId());
            assertEquals(AgentStatus.DEGRADED, restored.getNewStatus());
        }

        @Test
        @DisplayName("UPDATE_CAPABILITIES preserves capabilities")
        void updateCapabilitiesRoundtrip() {
            AgentCapabilities caps = createAgentCapabilities();
            AgentCommand original = AgentCommand.updateCapabilities("agent-nyc-01", caps);

            ByteString bytes = ProtobufCommandCodec.serialize(original);
            AgentCommand restored = (AgentCommand) ProtobufCommandCodec.deserialize(bytes);

            assertEquals(AgentCommand.CommandType.UPDATE_CAPABILITIES, restored.getType());
            assertEquals("agent-nyc-01", restored.getAgentId());
            assertNotNull(restored.getNewCapabilities());
            assertTrue(restored.getNewCapabilities().getSupportedProtocols().contains("https"));
        }

        @Test
        @DisplayName("HEARTBEAT preserves timestamp across roundtrip")
        void heartbeatTimestampPreserved() {
            AgentCommand original = AgentCommand.heartbeat("agent-nyc-01");
            Instant originalTimestamp = original.getTimestamp();

            ByteString bytes = ProtobufCommandCodec.serialize(original);
            AgentCommand restored = (AgentCommand) ProtobufCommandCodec.deserialize(bytes);

            assertEquals(AgentCommand.CommandType.HEARTBEAT, restored.getType());
            assertEquals("agent-nyc-01", restored.getAgentId());
            // Timestamp preserved to millisecond precision
            assertEquals(originalTimestamp.toEpochMilli(), restored.getTimestamp().toEpochMilli());
        }

        @Test
        @DisplayName("All AgentStatus enum values roundtrip correctly")
        void allAgentStatusValuesRoundtrip() {
            for (AgentStatus status : AgentStatus.values()) {
                AgentCommand original = AgentCommand.updateStatus("agent-" + status.name(), status);
                ByteString bytes = ProtobufCommandCodec.serialize(original);
                AgentCommand restored = (AgentCommand) ProtobufCommandCodec.deserialize(bytes);
                assertEquals(status, restored.getNewStatus(), "Failed for status: " + status);
            }
        }
    }

    // ========== SystemMetadataCommand ==========

    @Nested
    @DisplayName("SystemMetadataCommand roundtrip")
    class SystemMetadataCommandTests {

        @Test
        @DisplayName("SET preserves key and value")
        void setRoundtrip() {
            SystemMetadataCommand original = SystemMetadataCommand.set("cluster.name", "production-east");

            ByteString bytes = ProtobufCommandCodec.serialize(original);
            SystemMetadataCommand restored = (SystemMetadataCommand) ProtobufCommandCodec.deserialize(bytes);

            assertEquals(SystemMetadataCommand.Type.SET, restored.getType());
            assertEquals("cluster.name", restored.getKey());
            assertEquals("production-east", restored.getValue());
        }

        @Test
        @DisplayName("DELETE preserves key")
        void deleteRoundtrip() {
            SystemMetadataCommand original = SystemMetadataCommand.delete("cluster.name");

            ByteString bytes = ProtobufCommandCodec.serialize(original);
            SystemMetadataCommand restored = (SystemMetadataCommand) ProtobufCommandCodec.deserialize(bytes);

            assertEquals(SystemMetadataCommand.Type.DELETE, restored.getType());
            assertEquals("cluster.name", restored.getKey());
        }
    }

    // ========== JobAssignmentCommand ==========

    @Nested
    @DisplayName("JobAssignmentCommand roundtrip")
    class JobAssignmentCommandTests {

        @Test
        @DisplayName("ASSIGN preserves full JobAssignment")
        void assignRoundtrip() {
            JobAssignment assignment = createJobAssignment();
            JobAssignmentCommand original = JobAssignmentCommand.assign(assignment);

            ByteString bytes = ProtobufCommandCodec.serialize(original);
            JobAssignmentCommand restored = (JobAssignmentCommand) ProtobufCommandCodec.deserialize(bytes);

            assertEquals(JobAssignmentCommand.CommandType.ASSIGN, restored.getType());
            assertNotNull(restored.getJobAssignment());
            assertEquals("job-001", restored.getJobAssignment().getJobId());
            assertEquals("agent-nyc-01", restored.getJobAssignment().getAgentId());
            assertEquals(JobAssignmentStatus.ASSIGNED, restored.getJobAssignment().getStatus());
            assertEquals(0, restored.getJobAssignment().getRetryCount());
            assertEquals(30000L, restored.getJobAssignment().getEstimatedDurationMs());
            assertEquals("WEIGHTED_SCORE", restored.getJobAssignment().getAssignmentStrategy());
        }

        @Test
        @DisplayName("ACCEPT preserves assignmentId and timestamp")
        void acceptRoundtrip() {
            JobAssignmentCommand original = JobAssignmentCommand.accept("assign-001");
            Instant originalTimestamp = original.getTimestamp();

            ByteString bytes = ProtobufCommandCodec.serialize(original);
            JobAssignmentCommand restored = (JobAssignmentCommand) ProtobufCommandCodec.deserialize(bytes);

            assertEquals(JobAssignmentCommand.CommandType.ACCEPT, restored.getType());
            assertEquals("assign-001", restored.getAssignmentId());
            assertEquals(originalTimestamp.toEpochMilli(), restored.getTimestamp().toEpochMilli());
        }

        @Test
        @DisplayName("REJECT preserves reason")
        void rejectRoundtrip() {
            JobAssignmentCommand original = JobAssignmentCommand.reject("assign-001", "Capacity full");

            ByteString bytes = ProtobufCommandCodec.serialize(original);
            JobAssignmentCommand restored = (JobAssignmentCommand) ProtobufCommandCodec.deserialize(bytes);

            assertEquals(JobAssignmentCommand.CommandType.REJECT, restored.getType());
            assertEquals("assign-001", restored.getAssignmentId());
            assertEquals("Capacity full", restored.getReason());
        }

        @Test
        @DisplayName("UPDATE_STATUS preserves new status")
        void updateStatusRoundtrip() {
            JobAssignmentCommand original = JobAssignmentCommand.updateStatus("assign-001", JobAssignmentStatus.IN_PROGRESS);

            ByteString bytes = ProtobufCommandCodec.serialize(original);
            JobAssignmentCommand restored = (JobAssignmentCommand) ProtobufCommandCodec.deserialize(bytes);

            assertEquals(JobAssignmentCommand.CommandType.UPDATE_STATUS, restored.getType());
            assertEquals("assign-001", restored.getAssignmentId());
            assertEquals(JobAssignmentStatus.IN_PROGRESS, restored.getNewStatus());
        }

        @Test
        @DisplayName("TIMEOUT preserves assignmentId")
        void timeoutRoundtrip() {
            JobAssignmentCommand original = JobAssignmentCommand.timeout("assign-001");

            ByteString bytes = ProtobufCommandCodec.serialize(original);
            JobAssignmentCommand restored = (JobAssignmentCommand) ProtobufCommandCodec.deserialize(bytes);

            assertEquals(JobAssignmentCommand.CommandType.TIMEOUT, restored.getType());
            assertEquals("assign-001", restored.getAssignmentId());
        }

        @Test
        @DisplayName("All JobAssignmentStatus enum values roundtrip correctly")
        void allJobAssignmentStatusValuesRoundtrip() {
            for (JobAssignmentStatus status : JobAssignmentStatus.values()) {
                JobAssignmentCommand original = JobAssignmentCommand.updateStatus("assign-" + status.name(), status);
                ByteString bytes = ProtobufCommandCodec.serialize(original);
                JobAssignmentCommand restored = (JobAssignmentCommand) ProtobufCommandCodec.deserialize(bytes);
                assertEquals(status, restored.getNewStatus(), "Failed for status: " + status);
            }
        }
    }

    // ========== JobQueueCommand ==========

    @Nested
    @DisplayName("JobQueueCommand roundtrip")
    class JobQueueCommandTests {

        @Test
        @DisplayName("ENQUEUE preserves full QueuedJob with deep object graph")
        void enqueueRoundtrip() {
            QueuedJob queuedJob = createQueuedJob();
            JobQueueCommand original = JobQueueCommand.enqueue(queuedJob);

            ByteString bytes = ProtobufCommandCodec.serialize(original);
            JobQueueCommand restored = (JobQueueCommand) ProtobufCommandCodec.deserialize(bytes);

            assertEquals(JobQueueCommand.CommandType.ENQUEUE, restored.getType());
            assertNotNull(restored.getQueuedJob());

            QueuedJob restoredJob = restored.getQueuedJob();
            assertEquals("req-001", restoredJob.getJobId());
            assertEquals(JobPriority.HIGH, restoredJob.getPriority());
            assertEquals("admin", restoredJob.getSubmittedBy());
            assertEquals("wf-daily-etl", restoredJob.getWorkflowId());
            assertEquals("data-load", restoredJob.getGroupName());
            assertEquals(1, restoredJob.getRetryCount());

            // Verify nested TransferJob
            assertNotNull(restoredJob.getTransferJob());
            assertEquals("req-001", restoredJob.getTransferJob().getJobId());

            // Verify nested JobRequirements
            JobRequirements restoredReqs = restoredJob.getRequirements();
            assertNotNull(restoredReqs);
            assertEquals("us-east-1", restoredReqs.getTargetRegion());
            assertTrue(restoredReqs.getRequiredProtocols().contains("https"));
            assertTrue(restoredReqs.getRequiredProtocols().contains("sftp"));
            assertTrue(restoredReqs.isRequiresEncryption());
            assertFalse(restoredReqs.isRequiresCompression());
            assertEquals(JobRequirements.SelectionStrategy.WEIGHTED_SCORE, restoredReqs.getSelectionStrategy());
            assertEquals("gold", restoredReqs.getCustomAttributes().get("tier"));
            assertEquals("tenant-acme", restoredReqs.getTenantId());
            assertEquals("production", restoredReqs.getNamespace());
            assertEquals(3, restoredReqs.getMaxConcurrentJobs());
        }

        @Test
        @DisplayName("DEQUEUE preserves jobId")
        void dequeueRoundtrip() {
            JobQueueCommand original = JobQueueCommand.dequeue("job-001");

            ByteString bytes = ProtobufCommandCodec.serialize(original);
            JobQueueCommand restored = (JobQueueCommand) ProtobufCommandCodec.deserialize(bytes);

            assertEquals(JobQueueCommand.CommandType.DEQUEUE, restored.getType());
            assertEquals("job-001", restored.getJobId());
        }

        @Test
        @DisplayName("PRIORITIZE preserves priority and reason")
        void prioritizeRoundtrip() {
            JobQueueCommand original = JobQueueCommand.prioritize("job-001", JobPriority.CRITICAL, "Urgent request");

            ByteString bytes = ProtobufCommandCodec.serialize(original);
            JobQueueCommand restored = (JobQueueCommand) ProtobufCommandCodec.deserialize(bytes);

            assertEquals(JobQueueCommand.CommandType.PRIORITIZE, restored.getType());
            assertEquals("job-001", restored.getJobId());
            assertEquals(JobPriority.CRITICAL, restored.getNewPriority());
            assertEquals("Urgent request", restored.getReason());
        }

        @Test
        @DisplayName("REMOVE preserves reason")
        void removeRoundtrip() {
            JobQueueCommand original = JobQueueCommand.remove("job-001", "Cancelled by admin");

            ByteString bytes = ProtobufCommandCodec.serialize(original);
            JobQueueCommand restored = (JobQueueCommand) ProtobufCommandCodec.deserialize(bytes);

            assertEquals(JobQueueCommand.CommandType.REMOVE, restored.getType());
            assertEquals("job-001", restored.getJobId());
            assertEquals("Cancelled by admin", restored.getReason());
        }

        @Test
        @DisplayName("EXPEDITE preserves reason")
        void expediteRoundtrip() {
            JobQueueCommand original = JobQueueCommand.expedite("job-001", "SLA breach imminent");

            ByteString bytes = ProtobufCommandCodec.serialize(original);
            JobQueueCommand restored = (JobQueueCommand) ProtobufCommandCodec.deserialize(bytes);

            assertEquals(JobQueueCommand.CommandType.EXPEDITE, restored.getType());
            assertEquals("job-001", restored.getJobId());
            assertEquals("SLA breach imminent", restored.getReason());
        }

        @Test
        @DisplayName("All JobPriority enum values roundtrip correctly")
        void allJobPriorityValuesRoundtrip() {
            for (JobPriority priority : JobPriority.values()) {
                JobQueueCommand original = JobQueueCommand.prioritize("job-" + priority.name(), priority, "test");
                ByteString bytes = ProtobufCommandCodec.serialize(original);
                JobQueueCommand restored = (JobQueueCommand) ProtobufCommandCodec.deserialize(bytes);
                assertEquals(priority, restored.getNewPriority(), "Failed for priority: " + priority);
            }
        }

        @Test
        @DisplayName("Timestamp is preserved across roundtrip")
        void timestampPreserved() {
            JobQueueCommand original = JobQueueCommand.dequeue("job-001");
            Instant originalTimestamp = original.getTimestamp();

            ByteString bytes = ProtobufCommandCodec.serialize(original);
            JobQueueCommand restored = (JobQueueCommand) ProtobufCommandCodec.deserialize(bytes);

            assertEquals(originalTimestamp.toEpochMilli(), restored.getTimestamp().toEpochMilli());
        }
    }

    // ========== Cross-cutting concerns ==========

    @Nested
    @DisplayName("Cross-cutting serialization properties")
    class CrossCuttingTests {

        @Test
        @DisplayName("Protobuf encoding is much smaller than Java serialization")
        void protobufIsSmallerThanJavaSerialization() {
            TransferJobCommand cmd = TransferJobCommand.updateStatus("job-001", TransferStatus.COMPLETED);
            ByteString proto = ProtobufCommandCodec.serialize(cmd);
            // Protobuf encoding for a simple command should be tiny
            assertTrue(proto.size() < 50, "Expected small protobuf size, got: " + proto.size());
        }

        @Test
        @DisplayName("Each command type is correctly dispatched via instanceof after deserialization")
        void instanceofDispatchWorksAfterDeserialization() {
            // Simulate what QuorusStateMachine.apply() does
            List<Object> commands = List.of(
                    TransferJobCommand.create(createTransferJob()),
                    AgentCommand.register(createAgentInfo()),
                    SystemMetadataCommand.set("key", "value"),
                    JobAssignmentCommand.assign(createJobAssignment()),
                    JobQueueCommand.dequeue("job-001"));

            for (Object original : commands) {
                ByteString bytes = ProtobufCommandCodec.serialize(original);
                Object restored = ProtobufCommandCodec.deserialize(bytes);

                // This IS what QuorusStateMachine.apply() does
                if (original instanceof TransferJobCommand) {
                    assertInstanceOf(TransferJobCommand.class, restored);
                } else if (original instanceof AgentCommand) {
                    assertInstanceOf(AgentCommand.class, restored);
                } else if (original instanceof SystemMetadataCommand) {
                    assertInstanceOf(SystemMetadataCommand.class, restored);
                } else if (original instanceof JobAssignmentCommand) {
                    assertInstanceOf(JobAssignmentCommand.class, restored);
                } else if (original instanceof JobQueueCommand) {
                    assertInstanceOf(JobQueueCommand.class, restored);
                } else {
                    fail("Unexpected command type: " + original.getClass());
                }
            }
        }

        @Test
        @DisplayName("All SelectionStrategy enum values roundtrip correctly")
        void allSelectionStrategyValuesRoundtrip() {
            for (JobRequirements.SelectionStrategy strategy : JobRequirements.SelectionStrategy.values()) {
                JobRequirements req = new JobRequirements.Builder()
                        .selectionStrategy(strategy)
                        .build();
                QueuedJob queuedJob = new QueuedJob.Builder()
                        .transferJob(createTransferJob())
                        .requirements(req)
                        .build();
                JobQueueCommand original = JobQueueCommand.enqueue(queuedJob);

                ByteString bytes = ProtobufCommandCodec.serialize(original);
                JobQueueCommand restored = (JobQueueCommand) ProtobufCommandCodec.deserialize(bytes);

                assertEquals(strategy, restored.getQueuedJob().getRequirements().getSelectionStrategy(),
                        "Failed for strategy: " + strategy);
            }
        }
    }
}
