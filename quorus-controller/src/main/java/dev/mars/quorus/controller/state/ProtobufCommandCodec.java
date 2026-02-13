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
import com.google.protobuf.InvalidProtocolBufferException;
import dev.mars.quorus.agent.AgentCapabilities;
import dev.mars.quorus.agent.AgentInfo;
import dev.mars.quorus.agent.AgentNetworkInfo;
import dev.mars.quorus.agent.AgentStatus;
import dev.mars.quorus.agent.AgentSystemInfo;
import dev.mars.quorus.controller.raft.grpc.*;
import dev.mars.quorus.core.*;

import java.net.URI;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Codec for converting between Java command objects and Protobuf-encoded bytes.
 * Replaces Java serialization (ObjectOutputStream/ObjectInputStream) with
 * version-safe Protobuf encoding for Raft log entry command payloads.
 *
 * <p>The codec handles all five state machine command types:
 * <ul>
 *   <li>{@link TransferJobCommand}</li>
 *   <li>{@link AgentCommand}</li>
 *   <li>{@link SystemMetadataCommand}</li>
 *   <li>{@link JobAssignmentCommand}</li>
 *   <li>{@link JobQueueCommand}</li>
 * </ul>
 *
 * @author Mark Andrew Ray-Smith Cityline Ltd
 * @since 2025
 */
public final class ProtobufCommandCodec {

    private ProtobufCommandCodec() {
        // Utility class — not instantiable
    }

    // ============================================================
    // Top-level serialize / deserialize
    // ============================================================

    /**
     * Serialize a state machine command to Protobuf-encoded bytes.
     *
     * @param command the command object (one of the five command types, or null for no-op)
     * @return Protobuf-encoded bytes as ByteString
     * @throws IllegalArgumentException if the command type is unsupported
     */
    public static ByteString serialize(Object command) {
        if (command == null) {
            // No-op entry: encode as empty RaftCommand (no oneof field set)
            return RaftCommand.getDefaultInstance().toByteString();
        }
        RaftCommand.Builder builder = RaftCommand.newBuilder();
        if (command instanceof TransferJobCommand cmd) {
            builder.setTransferJobCommand(toProto(cmd));
        } else if (command instanceof AgentCommand cmd) {
            builder.setAgentCommand(toProto(cmd));
        } else if (command instanceof SystemMetadataCommand cmd) {
            builder.setSystemMetadataCommand(toProto(cmd));
        } else if (command instanceof JobAssignmentCommand cmd) {
            builder.setJobAssignmentCommand(toProto(cmd));
        } else if (command instanceof JobQueueCommand cmd) {
            builder.setJobQueueCommand(toProto(cmd));
        } else {
            throw new IllegalArgumentException(
                    "Unsupported command type for Protobuf serialization: " + command.getClass().getName());
        }
        return builder.build().toByteString();
    }

    /**
     * Deserialize Protobuf-encoded bytes back to a state machine command object.
     *
     * @param data Protobuf-encoded bytes
     * @return the deserialized command object, or null for no-op entries
     * @throws RuntimeException if the data cannot be parsed
     */
    public static Object deserialize(ByteString data) {
        try {
            RaftCommand raftCommand = RaftCommand.parseFrom(data);
            return switch (raftCommand.getCommandCase()) {
                case TRANSFER_JOB_COMMAND -> fromProto(raftCommand.getTransferJobCommand());
                case AGENT_COMMAND -> fromProto(raftCommand.getAgentCommand());
                case SYSTEM_METADATA_COMMAND -> fromProto(raftCommand.getSystemMetadataCommand());
                case JOB_ASSIGNMENT_COMMAND -> fromProto(raftCommand.getJobAssignmentCommand());
                case JOB_QUEUE_COMMAND -> fromProto(raftCommand.getJobQueueCommand());
                case COMMAND_NOT_SET -> null; // No-op entry
            };
        } catch (InvalidProtocolBufferException e) {
            throw new RuntimeException("Failed to deserialize Protobuf command", e);
        }
    }

    // ============================================================
    // Command converters: Java → Protobuf
    // ============================================================

    private static TransferJobCommandProto toProto(TransferJobCommand cmd) {
        TransferJobCommandProto.Builder builder = TransferJobCommandProto.newBuilder()
                .setType(toProto(cmd.getType()));
        if (cmd.getJobId() != null) {
            builder.setJobId(cmd.getJobId());
        }
        if (cmd.getTransferJob() != null) {
            builder.setTransferJob(toProto(cmd.getTransferJob()));
        }
        if (cmd.getStatus() != null) {
            builder.setStatus(toProto(cmd.getStatus()));
        }
        if (cmd.getBytesTransferred() != null) {
            builder.setBytesTransferred(cmd.getBytesTransferred());
        }
        return builder.build();
    }

    private static AgentCommandProto toProto(AgentCommand cmd) {
        AgentCommandProto.Builder builder = AgentCommandProto.newBuilder()
                .setType(toProto(cmd.getType()));
        if (cmd.getAgentId() != null) {
            builder.setAgentId(cmd.getAgentId());
        }
        if (cmd.getAgentInfo() != null) {
            builder.setAgentInfo(toProto(cmd.getAgentInfo()));
        }
        if (cmd.getNewStatus() != null) {
            builder.setNewStatus(toProto(cmd.getNewStatus()));
        }
        if (cmd.getNewCapabilities() != null) {
            builder.setNewCapabilities(toProto(cmd.getNewCapabilities()));
        }
        if (cmd.getTimestamp() != null) {
            builder.setTimestampEpochMs(cmd.getTimestamp().toEpochMilli());
        }
        return builder.build();
    }

    private static SystemMetadataCommandProto toProto(SystemMetadataCommand cmd) {
        SystemMetadataCommandProto.Builder builder = SystemMetadataCommandProto.newBuilder()
                .setType(toProto(cmd.getType()));
        if (cmd.getKey() != null) {
            builder.setKey(cmd.getKey());
        }
        if (cmd.getValue() != null) {
            builder.setValue(cmd.getValue());
        }
        return builder.build();
    }

    private static JobAssignmentCommandProto toProto(JobAssignmentCommand cmd) {
        JobAssignmentCommandProto.Builder builder = JobAssignmentCommandProto.newBuilder()
                .setType(toProto(cmd.getType()));
        if (cmd.getAssignmentId() != null) {
            builder.setAssignmentId(cmd.getAssignmentId());
        }
        if (cmd.getJobAssignment() != null) {
            builder.setJobAssignment(toProto(cmd.getJobAssignment()));
        }
        if (cmd.getNewStatus() != null) {
            builder.setNewStatus(toProto(cmd.getNewStatus()));
        }
        if (cmd.getReason() != null) {
            builder.setReason(cmd.getReason());
        }
        if (cmd.getTimestamp() != null) {
            builder.setTimestampEpochMs(cmd.getTimestamp().toEpochMilli());
        }
        return builder.build();
    }

    private static JobQueueCommandProto toProto(JobQueueCommand cmd) {
        JobQueueCommandProto.Builder builder = JobQueueCommandProto.newBuilder()
                .setType(toProto(cmd.getType()));
        if (cmd.getJobId() != null) {
            builder.setJobId(cmd.getJobId());
        }
        if (cmd.getQueuedJob() != null) {
            builder.setQueuedJob(toProto(cmd.getQueuedJob()));
        }
        if (cmd.getNewPriority() != null) {
            builder.setNewPriority(toProto(cmd.getNewPriority()));
        }
        if (cmd.getReason() != null) {
            builder.setReason(cmd.getReason());
        }
        if (cmd.getTimestamp() != null) {
            builder.setTimestampEpochMs(cmd.getTimestamp().toEpochMilli());
        }
        return builder.build();
    }

    // ============================================================
    // Command converters: Protobuf → Java
    // ============================================================

    private static TransferJobCommand fromProto(TransferJobCommandProto proto) {
        return switch (fromProto(proto.getType())) {
            case CREATE -> TransferJobCommand.create(fromProto(proto.getTransferJob()));
            case UPDATE_STATUS -> TransferJobCommand.updateStatus(
                    proto.getJobId(), fromProto(proto.getStatus()));
            case UPDATE_PROGRESS -> TransferJobCommand.updateProgress(
                    proto.getJobId(), proto.getBytesTransferred());
            case DELETE -> TransferJobCommand.delete(proto.getJobId());
        };
    }

    private static AgentCommand fromProto(AgentCommandProto proto) {
        Instant timestamp = proto.getTimestampEpochMs() > 0
                ? Instant.ofEpochMilli(proto.getTimestampEpochMs()) : null;
        AgentCommand.CommandType type = fromProto(proto.getType());
        AgentStatus newStatus = proto.getNewStatus() != AgentStatusProto.AGENT_STATUS_UNSPECIFIED
                ? fromProto(proto.getNewStatus()) : null;
        return switch (type) {
            case REGISTER -> new AgentCommand(type, proto.getAgentId(),
                    fromProto(proto.getAgentInfo()), null, null, timestamp);
            case DEREGISTER -> new AgentCommand(type, proto.getAgentId(),
                    null, null, null, timestamp);
            case UPDATE_STATUS -> new AgentCommand(type, proto.getAgentId(),
                    null, newStatus, null, timestamp);
            case UPDATE_CAPABILITIES -> new AgentCommand(type, proto.getAgentId(),
                    null, null, fromProto(proto.getNewCapabilities()), timestamp);
            case HEARTBEAT -> new AgentCommand(type, proto.getAgentId(),
                    null, newStatus, null, timestamp);
        };
    }

    private static SystemMetadataCommand fromProto(SystemMetadataCommandProto proto) {
        return switch (fromProto(proto.getType())) {
            case SET -> SystemMetadataCommand.set(proto.getKey(), proto.getValue());
            case DELETE -> SystemMetadataCommand.delete(proto.getKey());
        };
    }

    private static JobAssignmentCommand fromProto(JobAssignmentCommandProto proto) {
        Instant timestamp = proto.getTimestampEpochMs() > 0
                ? Instant.ofEpochMilli(proto.getTimestampEpochMs()) : null;
        JobAssignmentCommand.CommandType type = fromProto(proto.getType());
        JobAssignment assignment = proto.hasJobAssignment() ? fromProto(proto.getJobAssignment()) : null;
        JobAssignmentStatus newStatus = proto.getNewStatus() != JobAssignmentStatusProto.JOB_ASSIGNMENT_STATUS_UNSPECIFIED
                ? fromProto(proto.getNewStatus()) : null;
        String reason = proto.getReason().isEmpty() ? null : proto.getReason();

        return new JobAssignmentCommand(type, proto.getAssignmentId(),
                assignment, newStatus, reason, timestamp);
    }

    private static JobQueueCommand fromProto(JobQueueCommandProto proto) {
        Instant timestamp = proto.getTimestampEpochMs() > 0
                ? Instant.ofEpochMilli(proto.getTimestampEpochMs()) : null;
        JobQueueCommand.CommandType type = fromProto(proto.getType());
        QueuedJob queuedJob = proto.hasQueuedJob() ? fromProto(proto.getQueuedJob()) : null;
        JobPriority newPriority = proto.getNewPriority() != JobPriorityProto.JOB_PRIORITY_UNSPECIFIED
                ? fromProto(proto.getNewPriority()) : null;
        String reason = proto.getReason().isEmpty() ? null : proto.getReason();

        return new JobQueueCommand(type, proto.getJobId(),
                queuedJob, newPriority, reason, timestamp);
    }

    // ============================================================
    // Domain model converters: Java → Protobuf
    // ============================================================

    private static TransferJobProto toProto(TransferJob job) {
        TransferJobProto.Builder builder = TransferJobProto.newBuilder()
                .setStatus(toProto(job.getStatus()))
                .setBytesTransferred(job.getBytesTransferred())
                .setTotalBytes(job.getTotalBytes());
        if (job.getRequest() != null) {
            builder.setRequest(toProto(job.getRequest()));
        }
        if (job.getStartTime() != null) {
            builder.setStartTimeEpochMs(job.getStartTime().toEpochMilli());
        }
        if (job.getLastUpdateTime() != null) {
            builder.setLastUpdateTimeEpochMs(job.getLastUpdateTime().toEpochMilli());
        }
        if (job.getErrorMessage() != null) {
            builder.setErrorMessage(job.getErrorMessage());
        }
        if (job.getActualChecksum() != null) {
            builder.setActualChecksum(job.getActualChecksum());
        }
        return builder.build();
    }

    private static TransferRequestProto toProto(TransferRequest req) {
        TransferRequestProto.Builder builder = TransferRequestProto.newBuilder()
                .setExpectedSize(req.getExpectedSize());
        if (req.getRequestId() != null) {
            builder.setRequestId(req.getRequestId());
        }
        if (req.getSourceUri() != null) {
            builder.setSourceUri(req.getSourceUri().toString());
        }
        if (req.getDestinationUri() != null) {
            builder.setDestinationUri(req.getDestinationUri().toString());
            builder.setDestinationPath(req.getDestinationUri().toString());
        }
        if (req.getProtocol() != null) {
            builder.setProtocol(req.getProtocol());
        }
        if (req.getMetadata() != null) {
            builder.putAllMetadata(req.getMetadata());
        }
        if (req.getCreatedAt() != null) {
            builder.setCreatedAtEpochMs(req.getCreatedAt().toEpochMilli());
        }
        if (req.getExpectedChecksum() != null) {
            builder.setExpectedChecksum(req.getExpectedChecksum());
        }
        return builder.build();
    }

    private static AgentInfoProto toProto(AgentInfo info) {
        AgentInfoProto.Builder builder = AgentInfoProto.newBuilder()
                .setPort(info.getPort());
        if (info.getAgentId() != null) {
            builder.setAgentId(info.getAgentId());
        }
        if (info.getHostname() != null) {
            builder.setHostname(info.getHostname());
        }
        if (info.getAddress() != null) {
            builder.setAddress(info.getAddress());
        }
        if (info.getCapabilities() != null) {
            builder.setCapabilities(toProto(info.getCapabilities()));
        }
        if (info.getStatus() != null) {
            builder.setStatus(toProto(info.getStatus()));
        }
        if (info.getRegistrationTime() != null) {
            builder.setRegistrationTimeEpochMs(info.getRegistrationTime().toEpochMilli());
        }
        if (info.getLastHeartbeat() != null) {
            builder.setLastHeartbeatEpochMs(info.getLastHeartbeat().toEpochMilli());
        }
        if (info.getVersion() != null) {
            builder.setVersion(info.getVersion());
        }
        if (info.getRegion() != null) {
            builder.setRegion(info.getRegion());
        }
        if (info.getDatacenter() != null) {
            builder.setDatacenter(info.getDatacenter());
        }
        if (info.getMetadata() != null) {
            builder.putAllMetadata(info.getMetadata());
        }
        return builder.build();
    }

    private static AgentCapabilitiesProto toProto(AgentCapabilities caps) {
        AgentCapabilitiesProto.Builder builder = AgentCapabilitiesProto.newBuilder()
                .setMaxConcurrentTransfers(caps.getMaxConcurrentTransfers())
                .setMaxTransferSize(caps.getMaxTransferSize())
                .setMaxBandwidth(caps.getMaxBandwidth());
        if (caps.getSupportedProtocols() != null) {
            builder.addAllSupportedProtocols(caps.getSupportedProtocols());
        }
        if (caps.getAvailableRegions() != null) {
            builder.addAllAvailableRegions(caps.getAvailableRegions());
        }
        if (caps.getSupportedCompressionTypes() != null) {
            builder.addAllSupportedCompressionTypes(caps.getSupportedCompressionTypes());
        }
        if (caps.getSupportedEncryptionTypes() != null) {
            builder.addAllSupportedEncryptionTypes(caps.getSupportedEncryptionTypes());
        }
        if (caps.getCustomCapabilities() != null) {
            // Convert Map<String, Object> to Map<String, String>
            caps.getCustomCapabilities().forEach((k, v) ->
                    builder.putCustomCapabilities(k, v != null ? v.toString() : ""));
        }
        if (caps.getSystemInfo() != null) {
            builder.setSystemInfo(toProto(caps.getSystemInfo()));
        }
        if (caps.getNetworkInfo() != null) {
            builder.setNetworkInfo(toProto(caps.getNetworkInfo()));
        }
        return builder.build();
    }

    private static AgentSystemInfoProto toProto(AgentSystemInfo info) {
        AgentSystemInfoProto.Builder builder = AgentSystemInfoProto.newBuilder()
                .setTotalMemory(info.getTotalMemory())
                .setAvailableMemory(info.getAvailableMemory())
                .setTotalDiskSpace(info.getTotalDiskSpace())
                .setAvailableDiskSpace(info.getAvailableDiskSpace())
                .setCpuCores(info.getCpuCores())
                .setCpuUsage(info.getCpuUsage())
                .setLoadAverage(info.getLoadAverage());
        if (info.getOperatingSystem() != null) {
            builder.setOperatingSystem(info.getOperatingSystem());
        }
        if (info.getArchitecture() != null) {
            builder.setArchitecture(info.getArchitecture());
        }
        if (info.getJavaVersion() != null) {
            builder.setJavaVersion(info.getJavaVersion());
        }
        return builder.build();
    }

    private static AgentNetworkInfoProto toProto(AgentNetworkInfo info) {
        AgentNetworkInfoProto.Builder builder = AgentNetworkInfoProto.newBuilder()
                .setBandwidthCapacity(info.getBandwidthCapacity())
                .setCurrentBandwidthUsage(info.getCurrentBandwidthUsage())
                .setLatencyMs(info.getLatencyMs())
                .setPacketLossPercentage(info.getPacketLossPercentage())
                .setIsNatTraversal(info.isNatTraversal());
        if (info.getPublicIpAddress() != null) {
            builder.setPublicIpAddress(info.getPublicIpAddress());
        }
        if (info.getPrivateIpAddress() != null) {
            builder.setPrivateIpAddress(info.getPrivateIpAddress());
        }
        if (info.getNetworkInterfaces() != null) {
            builder.addAllNetworkInterfaces(info.getNetworkInterfaces());
        }
        if (info.getConnectionType() != null) {
            builder.setConnectionType(info.getConnectionType());
        }
        if (info.getFirewallPorts() != null) {
            builder.addAllFirewallPorts(info.getFirewallPorts());
        }
        return builder.build();
    }

    private static JobAssignmentProto toProto(JobAssignment assignment) {
        JobAssignmentProto.Builder builder = JobAssignmentProto.newBuilder()
                .setRetryCount(assignment.getRetryCount())
                .setEstimatedDurationMs(assignment.getEstimatedDurationMs());
        if (assignment.getJobId() != null) {
            builder.setJobId(assignment.getJobId());
        }
        if (assignment.getAgentId() != null) {
            builder.setAgentId(assignment.getAgentId());
        }
        if (assignment.getAssignedAt() != null) {
            builder.setAssignedAtEpochMs(assignment.getAssignedAt().toEpochMilli());
        }
        if (assignment.getAcceptedAt() != null) {
            builder.setAcceptedAtEpochMs(assignment.getAcceptedAt().toEpochMilli());
        }
        if (assignment.getStartedAt() != null) {
            builder.setStartedAtEpochMs(assignment.getStartedAt().toEpochMilli());
        }
        if (assignment.getCompletedAt() != null) {
            builder.setCompletedAtEpochMs(assignment.getCompletedAt().toEpochMilli());
        }
        if (assignment.getStatus() != null) {
            builder.setStatus(toProto(assignment.getStatus()));
        }
        if (assignment.getFailureReason() != null) {
            builder.setFailureReason(assignment.getFailureReason());
        }
        if (assignment.getAssignmentStrategy() != null) {
            builder.setAssignmentStrategy(assignment.getAssignmentStrategy());
        }
        return builder.build();
    }

    private static QueuedJobProto toProto(QueuedJob queuedJob) {
        QueuedJobProto.Builder builder = QueuedJobProto.newBuilder()
                .setRetryCount(queuedJob.getRetryCount());
        if (queuedJob.getTransferJob() != null) {
            builder.setTransferJob(toProto(queuedJob.getTransferJob()));
        }
        if (queuedJob.getPriority() != null) {
            builder.setPriority(toProto(queuedJob.getPriority()));
        }
        if (queuedJob.getQueueTime() != null) {
            builder.setQueueTimeEpochMs(queuedJob.getQueueTime().toEpochMilli());
        }
        if (queuedJob.getRequirements() != null) {
            builder.setRequirements(toProto(queuedJob.getRequirements()));
        }
        if (queuedJob.getSubmittedBy() != null) {
            builder.setSubmittedBy(queuedJob.getSubmittedBy());
        }
        if (queuedJob.getWorkflowId() != null) {
            builder.setWorkflowId(queuedJob.getWorkflowId());
        }
        if (queuedJob.getGroupName() != null) {
            builder.setGroupName(queuedJob.getGroupName());
        }
        if (queuedJob.getEarliestStartTime() != null) {
            builder.setEarliestStartTimeEpochMs(queuedJob.getEarliestStartTime().toEpochMilli());
        }
        return builder.build();
    }

    private static JobRequirementsProto toProto(JobRequirements req) {
        JobRequirementsProto.Builder builder = JobRequirementsProto.newBuilder()
                .setMinBandwidth(req.getMinBandwidth())
                .setMaxTransferSize(req.getMaxTransferSize())
                .setRequiresEncryption(req.isRequiresEncryption())
                .setRequiresCompression(req.isRequiresCompression())
                .setMaxConcurrentJobs(req.getMaxConcurrentJobs());
        if (req.getTargetRegion() != null) {
            builder.setTargetRegion(req.getTargetRegion());
        }
        if (req.getRequiredProtocols() != null) {
            builder.addAllRequiredProtocols(req.getRequiredProtocols());
        }
        if (req.getPreferredRegions() != null) {
            builder.addAllPreferredRegions(req.getPreferredRegions());
        }
        if (req.getExcludedAgents() != null) {
            builder.addAllExcludedAgents(req.getExcludedAgents());
        }
        if (req.getPreferredAgents() != null) {
            builder.addAllPreferredAgents(req.getPreferredAgents());
        }
        if (req.getSelectionStrategy() != null) {
            builder.setSelectionStrategy(toProto(req.getSelectionStrategy()));
        }
        if (req.getCustomAttributes() != null) {
            builder.putAllCustomAttributes(req.getCustomAttributes());
        }
        if (req.getTenantId() != null) {
            builder.setTenantId(req.getTenantId());
        }
        if (req.getNamespace() != null) {
            builder.setNamespace(req.getNamespace());
        }
        return builder.build();
    }

    // ============================================================
    // Domain model converters: Protobuf → Java
    // ============================================================

    private static TransferJob fromProto(TransferJobProto proto) {
        TransferRequest request = fromProto(proto.getRequest());
        TransferJob job = new TransferJob(request);
        // The job starts in PENDING. For CREATE commands this is always correct.
        return job;
    }

    private static TransferRequest fromProto(TransferRequestProto proto) {
        TransferRequest.Builder builder = TransferRequest.builder()
                .requestId(proto.getRequestId())
                .sourceUri(URI.create(proto.getSourceUri()))
                .expectedSize(proto.getExpectedSize());

        if (!proto.getDestinationUri().isEmpty()) {
            builder.destinationUri(URI.create(proto.getDestinationUri()));
        } else if (!proto.getDestinationPath().isEmpty()) {
            builder.destinationUri(URI.create(proto.getDestinationPath()));
        }

        if (!proto.getProtocol().isEmpty()) {
            builder.protocol(proto.getProtocol());
        }
        if (proto.getMetadataCount() > 0) {
            builder.metadata(new HashMap<>(proto.getMetadataMap()));
        }
        if (proto.getCreatedAtEpochMs() > 0) {
            builder.createdAt(Instant.ofEpochMilli(proto.getCreatedAtEpochMs()));
        }
        if (!proto.getExpectedChecksum().isEmpty()) {
            builder.expectedChecksum(proto.getExpectedChecksum());
        }
        return builder.build();
    }

    private static AgentInfo fromProto(AgentInfoProto proto) {
        AgentInfo info = new AgentInfo(
                proto.getAgentId(),
                proto.getHostname(),
                proto.getAddress(),
                proto.getPort());
        if (proto.hasCapabilities()) {
            info.setCapabilities(fromProto(proto.getCapabilities()));
        }
        if (proto.getStatus() != AgentStatusProto.AGENT_STATUS_UNSPECIFIED) {
            info.setStatus(fromProto(proto.getStatus()));
        }
        if (proto.getRegistrationTimeEpochMs() > 0) {
            info.setRegistrationTime(Instant.ofEpochMilli(proto.getRegistrationTimeEpochMs()));
        }
        if (proto.getLastHeartbeatEpochMs() > 0) {
            info.setLastHeartbeat(Instant.ofEpochMilli(proto.getLastHeartbeatEpochMs()));
        }
        if (!proto.getVersion().isEmpty()) {
            info.setVersion(proto.getVersion());
        }
        if (!proto.getRegion().isEmpty()) {
            info.setRegion(proto.getRegion());
        }
        if (!proto.getDatacenter().isEmpty()) {
            info.setDatacenter(proto.getDatacenter());
        }
        if (proto.getMetadataCount() > 0) {
            info.setMetadata(new HashMap<>(proto.getMetadataMap()));
        }
        return info;
    }

    private static AgentCapabilities fromProto(AgentCapabilitiesProto proto) {
        AgentCapabilities caps = new AgentCapabilities();
        caps.setSupportedProtocols(new HashSet<>(proto.getSupportedProtocolsList()));
        caps.setMaxConcurrentTransfers(proto.getMaxConcurrentTransfers());
        caps.setMaxTransferSize(proto.getMaxTransferSize());
        caps.setMaxBandwidth(proto.getMaxBandwidth());
        caps.setAvailableRegions(new HashSet<>(proto.getAvailableRegionsList()));
        caps.setSupportedCompressionTypes(new HashSet<>(proto.getSupportedCompressionTypesList()));
        caps.setSupportedEncryptionTypes(new HashSet<>(proto.getSupportedEncryptionTypesList()));
        if (proto.getCustomCapabilitiesCount() > 0) {
            caps.setCustomCapabilities(new HashMap<>(proto.getCustomCapabilitiesMap()));
        }
        if (proto.hasSystemInfo()) {
            caps.setSystemInfo(fromProto(proto.getSystemInfo()));
        }
        if (proto.hasNetworkInfo()) {
            caps.setNetworkInfo(fromProto(proto.getNetworkInfo()));
        }
        return caps;
    }

    private static AgentSystemInfo fromProto(AgentSystemInfoProto proto) {
        AgentSystemInfo info = new AgentSystemInfo();
        info.setOperatingSystem(proto.getOperatingSystem());
        info.setArchitecture(proto.getArchitecture());
        info.setJavaVersion(proto.getJavaVersion());
        info.setTotalMemory(proto.getTotalMemory());
        info.setAvailableMemory(proto.getAvailableMemory());
        info.setTotalDiskSpace(proto.getTotalDiskSpace());
        info.setAvailableDiskSpace(proto.getAvailableDiskSpace());
        info.setCpuCores(proto.getCpuCores());
        info.setCpuUsage(proto.getCpuUsage());
        info.setLoadAverage(proto.getLoadAverage());
        return info;
    }

    private static AgentNetworkInfo fromProto(AgentNetworkInfoProto proto) {
        AgentNetworkInfo info = new AgentNetworkInfo();
        info.setPublicIpAddress(proto.getPublicIpAddress());
        info.setPrivateIpAddress(proto.getPrivateIpAddress());
        info.setNetworkInterfaces(new ArrayList<>(proto.getNetworkInterfacesList()));
        info.setBandwidthCapacity(proto.getBandwidthCapacity());
        info.setCurrentBandwidthUsage(proto.getCurrentBandwidthUsage());
        info.setLatencyMs(proto.getLatencyMs());
        info.setPacketLossPercentage(proto.getPacketLossPercentage());
        info.setConnectionType(proto.getConnectionType());
        info.setNatTraversal(proto.getIsNatTraversal());
        info.setFirewallPorts(new ArrayList<>(proto.getFirewallPortsList()));
        return info;
    }

    private static JobAssignment fromProto(JobAssignmentProto proto) {
        JobAssignment.Builder builder = new JobAssignment.Builder()
                .jobId(proto.getJobId())
                .agentId(proto.getAgentId())
                .retryCount(proto.getRetryCount())
                .estimatedDurationMs(proto.getEstimatedDurationMs());
        if (proto.getAssignedAtEpochMs() > 0) {
            builder.assignedAt(Instant.ofEpochMilli(proto.getAssignedAtEpochMs()));
        }
        if (proto.getAcceptedAtEpochMs() > 0) {
            builder.acceptedAt(Instant.ofEpochMilli(proto.getAcceptedAtEpochMs()));
        }
        if (proto.getStartedAtEpochMs() > 0) {
            builder.startedAt(Instant.ofEpochMilli(proto.getStartedAtEpochMs()));
        }
        if (proto.getCompletedAtEpochMs() > 0) {
            builder.completedAt(Instant.ofEpochMilli(proto.getCompletedAtEpochMs()));
        }
        if (proto.getStatus() != JobAssignmentStatusProto.JOB_ASSIGNMENT_STATUS_UNSPECIFIED) {
            builder.status(fromProto(proto.getStatus()));
        }
        if (!proto.getFailureReason().isEmpty()) {
            builder.failureReason(proto.getFailureReason());
        }
        if (!proto.getAssignmentStrategy().isEmpty()) {
            builder.assignmentStrategy(proto.getAssignmentStrategy());
        }
        return builder.build();
    }

    private static QueuedJob fromProto(QueuedJobProto proto) {
        QueuedJob.Builder builder = new QueuedJob.Builder()
                .transferJob(fromProto(proto.getTransferJob()))
                .retryCount(proto.getRetryCount());
        if (proto.getPriority() != JobPriorityProto.JOB_PRIORITY_UNSPECIFIED) {
            builder.priority(fromProto(proto.getPriority()));
        }
        if (proto.getQueueTimeEpochMs() > 0) {
            builder.queueTime(Instant.ofEpochMilli(proto.getQueueTimeEpochMs()));
        }
        if (proto.hasRequirements()) {
            builder.requirements(fromProto(proto.getRequirements()));
        }
        if (!proto.getSubmittedBy().isEmpty()) {
            builder.submittedBy(proto.getSubmittedBy());
        }
        if (!proto.getWorkflowId().isEmpty()) {
            builder.workflowId(proto.getWorkflowId());
        }
        if (!proto.getGroupName().isEmpty()) {
            builder.groupName(proto.getGroupName());
        }
        if (proto.getEarliestStartTimeEpochMs() > 0) {
            builder.earliestStartTime(Instant.ofEpochMilli(proto.getEarliestStartTimeEpochMs()));
        }
        return builder.build();
    }

    private static JobRequirements fromProto(JobRequirementsProto proto) {
        JobRequirements.Builder builder = new JobRequirements.Builder()
                .minBandwidth(proto.getMinBandwidth())
                .maxTransferSize(proto.getMaxTransferSize())
                .requiresEncryption(proto.getRequiresEncryption())
                .requiresCompression(proto.getRequiresCompression())
                .maxConcurrentJobs(proto.getMaxConcurrentJobs());
        if (!proto.getTargetRegion().isEmpty()) {
            builder.targetRegion(proto.getTargetRegion());
        }
        if (proto.getRequiredProtocolsCount() > 0) {
            builder.requiredProtocols(new HashSet<>(proto.getRequiredProtocolsList()));
        }
        if (proto.getPreferredRegionsCount() > 0) {
            builder.preferredRegions(new HashSet<>(proto.getPreferredRegionsList()));
        }
        if (proto.getExcludedAgentsCount() > 0) {
            builder.excludedAgents(new HashSet<>(proto.getExcludedAgentsList()));
        }
        if (proto.getPreferredAgentsCount() > 0) {
            builder.preferredAgents(new HashSet<>(proto.getPreferredAgentsList()));
        }
        if (proto.getSelectionStrategy() != SelectionStrategyProto.SELECTION_STRATEGY_UNSPECIFIED) {
            builder.selectionStrategy(fromProto(proto.getSelectionStrategy()));
        }
        if (proto.getCustomAttributesCount() > 0) {
            builder.customAttributes(new HashMap<>(proto.getCustomAttributesMap()));
        }
        if (!proto.getTenantId().isEmpty()) {
            builder.tenantId(proto.getTenantId());
        }
        if (!proto.getNamespace().isEmpty()) {
            builder.namespace(proto.getNamespace());
        }
        return builder.build();
    }

    // ============================================================
    // Enum converters: Java ↔ Protobuf
    // ============================================================

    // --- TransferJobCommand.Type ---

    private static TransferJobCommandType toProto(TransferJobCommand.Type type) {
        return switch (type) {
            case CREATE -> TransferJobCommandType.TRANSFER_JOB_CMD_CREATE;
            case UPDATE_STATUS -> TransferJobCommandType.TRANSFER_JOB_CMD_UPDATE_STATUS;
            case UPDATE_PROGRESS -> TransferJobCommandType.TRANSFER_JOB_CMD_UPDATE_PROGRESS;
            case DELETE -> TransferJobCommandType.TRANSFER_JOB_CMD_DELETE;
        };
    }

    private static TransferJobCommand.Type fromProto(TransferJobCommandType type) {
        return switch (type) {
            case TRANSFER_JOB_CMD_CREATE -> TransferJobCommand.Type.CREATE;
            case TRANSFER_JOB_CMD_UPDATE_STATUS -> TransferJobCommand.Type.UPDATE_STATUS;
            case TRANSFER_JOB_CMD_UPDATE_PROGRESS -> TransferJobCommand.Type.UPDATE_PROGRESS;
            case TRANSFER_JOB_CMD_DELETE -> TransferJobCommand.Type.DELETE;
            default -> throw new IllegalArgumentException("Unknown TransferJobCommandType: " + type);
        };
    }

    // --- AgentCommand.CommandType ---

    private static AgentCommandType toProto(AgentCommand.CommandType type) {
        return switch (type) {
            case REGISTER -> AgentCommandType.AGENT_CMD_REGISTER;
            case DEREGISTER -> AgentCommandType.AGENT_CMD_DEREGISTER;
            case UPDATE_STATUS -> AgentCommandType.AGENT_CMD_UPDATE_STATUS;
            case UPDATE_CAPABILITIES -> AgentCommandType.AGENT_CMD_UPDATE_CAPABILITIES;
            case HEARTBEAT -> AgentCommandType.AGENT_CMD_HEARTBEAT;
        };
    }

    private static AgentCommand.CommandType fromProto(AgentCommandType type) {
        return switch (type) {
            case AGENT_CMD_REGISTER -> AgentCommand.CommandType.REGISTER;
            case AGENT_CMD_DEREGISTER -> AgentCommand.CommandType.DEREGISTER;
            case AGENT_CMD_UPDATE_STATUS -> AgentCommand.CommandType.UPDATE_STATUS;
            case AGENT_CMD_UPDATE_CAPABILITIES -> AgentCommand.CommandType.UPDATE_CAPABILITIES;
            case AGENT_CMD_HEARTBEAT -> AgentCommand.CommandType.HEARTBEAT;
            default -> throw new IllegalArgumentException("Unknown AgentCommandType: " + type);
        };
    }

    // --- SystemMetadataCommand.Type ---

    private static SystemMetadataCommandType toProto(SystemMetadataCommand.Type type) {
        return switch (type) {
            case SET -> SystemMetadataCommandType.SYSTEM_METADATA_CMD_SET;
            case DELETE -> SystemMetadataCommandType.SYSTEM_METADATA_CMD_DELETE;
        };
    }

    private static SystemMetadataCommand.Type fromProto(SystemMetadataCommandType type) {
        return switch (type) {
            case SYSTEM_METADATA_CMD_SET -> SystemMetadataCommand.Type.SET;
            case SYSTEM_METADATA_CMD_DELETE -> SystemMetadataCommand.Type.DELETE;
            default -> throw new IllegalArgumentException("Unknown SystemMetadataCommandType: " + type);
        };
    }

    // --- JobAssignmentCommand.CommandType ---

    private static JobAssignmentCommandType toProto(JobAssignmentCommand.CommandType type) {
        return switch (type) {
            case ASSIGN -> JobAssignmentCommandType.JOB_ASSIGNMENT_CMD_ASSIGN;
            case ACCEPT -> JobAssignmentCommandType.JOB_ASSIGNMENT_CMD_ACCEPT;
            case REJECT -> JobAssignmentCommandType.JOB_ASSIGNMENT_CMD_REJECT;
            case UPDATE_STATUS -> JobAssignmentCommandType.JOB_ASSIGNMENT_CMD_UPDATE_STATUS;
            case TIMEOUT -> JobAssignmentCommandType.JOB_ASSIGNMENT_CMD_TIMEOUT;
            case CANCEL -> JobAssignmentCommandType.JOB_ASSIGNMENT_CMD_CANCEL;
            case REMOVE -> JobAssignmentCommandType.JOB_ASSIGNMENT_CMD_REMOVE;
        };
    }

    private static JobAssignmentCommand.CommandType fromProto(JobAssignmentCommandType type) {
        return switch (type) {
            case JOB_ASSIGNMENT_CMD_ASSIGN -> JobAssignmentCommand.CommandType.ASSIGN;
            case JOB_ASSIGNMENT_CMD_ACCEPT -> JobAssignmentCommand.CommandType.ACCEPT;
            case JOB_ASSIGNMENT_CMD_REJECT -> JobAssignmentCommand.CommandType.REJECT;
            case JOB_ASSIGNMENT_CMD_UPDATE_STATUS -> JobAssignmentCommand.CommandType.UPDATE_STATUS;
            case JOB_ASSIGNMENT_CMD_TIMEOUT -> JobAssignmentCommand.CommandType.TIMEOUT;
            case JOB_ASSIGNMENT_CMD_CANCEL -> JobAssignmentCommand.CommandType.CANCEL;
            case JOB_ASSIGNMENT_CMD_REMOVE -> JobAssignmentCommand.CommandType.REMOVE;
            default -> throw new IllegalArgumentException("Unknown JobAssignmentCommandType: " + type);
        };
    }

    // --- JobQueueCommand.CommandType ---

    private static JobQueueCommandType toProto(JobQueueCommand.CommandType type) {
        return switch (type) {
            case ENQUEUE -> JobQueueCommandType.JOB_QUEUE_CMD_ENQUEUE;
            case DEQUEUE -> JobQueueCommandType.JOB_QUEUE_CMD_DEQUEUE;
            case PRIORITIZE -> JobQueueCommandType.JOB_QUEUE_CMD_PRIORITIZE;
            case REMOVE -> JobQueueCommandType.JOB_QUEUE_CMD_REMOVE;
            case EXPEDITE -> JobQueueCommandType.JOB_QUEUE_CMD_EXPEDITE;
            case UPDATE_REQUIREMENTS -> JobQueueCommandType.JOB_QUEUE_CMD_UPDATE_REQUIREMENTS;
        };
    }

    private static JobQueueCommand.CommandType fromProto(JobQueueCommandType type) {
        return switch (type) {
            case JOB_QUEUE_CMD_ENQUEUE -> JobQueueCommand.CommandType.ENQUEUE;
            case JOB_QUEUE_CMD_DEQUEUE -> JobQueueCommand.CommandType.DEQUEUE;
            case JOB_QUEUE_CMD_PRIORITIZE -> JobQueueCommand.CommandType.PRIORITIZE;
            case JOB_QUEUE_CMD_REMOVE -> JobQueueCommand.CommandType.REMOVE;
            case JOB_QUEUE_CMD_EXPEDITE -> JobQueueCommand.CommandType.EXPEDITE;
            case JOB_QUEUE_CMD_UPDATE_REQUIREMENTS -> JobQueueCommand.CommandType.UPDATE_REQUIREMENTS;
            default -> throw new IllegalArgumentException("Unknown JobQueueCommandType: " + type);
        };
    }

    // --- TransferStatus ---

    private static TransferStatusProto toProto(TransferStatus status) {
        return switch (status) {
            case PENDING -> TransferStatusProto.TRANSFER_STATUS_PENDING;
            case IN_PROGRESS -> TransferStatusProto.TRANSFER_STATUS_IN_PROGRESS;
            case COMPLETED -> TransferStatusProto.TRANSFER_STATUS_COMPLETED;
            case FAILED -> TransferStatusProto.TRANSFER_STATUS_FAILED;
            case CANCELLED -> TransferStatusProto.TRANSFER_STATUS_CANCELLED;
            case PAUSED -> TransferStatusProto.TRANSFER_STATUS_PAUSED;
        };
    }

    private static TransferStatus fromProto(TransferStatusProto status) {
        return switch (status) {
            case TRANSFER_STATUS_PENDING -> TransferStatus.PENDING;
            case TRANSFER_STATUS_IN_PROGRESS -> TransferStatus.IN_PROGRESS;
            case TRANSFER_STATUS_COMPLETED -> TransferStatus.COMPLETED;
            case TRANSFER_STATUS_FAILED -> TransferStatus.FAILED;
            case TRANSFER_STATUS_CANCELLED -> TransferStatus.CANCELLED;
            case TRANSFER_STATUS_PAUSED -> TransferStatus.PAUSED;
            default -> throw new IllegalArgumentException("Unknown TransferStatusProto: " + status);
        };
    }

    // --- AgentStatus ---

    private static AgentStatusProto toProto(AgentStatus status) {
        return switch (status) {
            case REGISTERING -> AgentStatusProto.AGENT_STATUS_REGISTERING;
            case HEALTHY -> AgentStatusProto.AGENT_STATUS_HEALTHY;
            case ACTIVE -> AgentStatusProto.AGENT_STATUS_ACTIVE;
            case IDLE -> AgentStatusProto.AGENT_STATUS_IDLE;
            case DEGRADED -> AgentStatusProto.AGENT_STATUS_DEGRADED;
            case OVERLOADED -> AgentStatusProto.AGENT_STATUS_OVERLOADED;
            case MAINTENANCE -> AgentStatusProto.AGENT_STATUS_MAINTENANCE;
            case DRAINING -> AgentStatusProto.AGENT_STATUS_DRAINING;
            case UNREACHABLE -> AgentStatusProto.AGENT_STATUS_UNREACHABLE;
            case FAILED -> AgentStatusProto.AGENT_STATUS_FAILED;
            case DEREGISTERED -> AgentStatusProto.AGENT_STATUS_DEREGISTERED;
        };
    }

    private static AgentStatus fromProto(AgentStatusProto status) {
        return switch (status) {
            case AGENT_STATUS_REGISTERING -> AgentStatus.REGISTERING;
            case AGENT_STATUS_HEALTHY -> AgentStatus.HEALTHY;
            case AGENT_STATUS_ACTIVE -> AgentStatus.ACTIVE;
            case AGENT_STATUS_IDLE -> AgentStatus.IDLE;
            case AGENT_STATUS_DEGRADED -> AgentStatus.DEGRADED;
            case AGENT_STATUS_OVERLOADED -> AgentStatus.OVERLOADED;
            case AGENT_STATUS_MAINTENANCE -> AgentStatus.MAINTENANCE;
            case AGENT_STATUS_DRAINING -> AgentStatus.DRAINING;
            case AGENT_STATUS_UNREACHABLE -> AgentStatus.UNREACHABLE;
            case AGENT_STATUS_FAILED -> AgentStatus.FAILED;
            case AGENT_STATUS_DEREGISTERED -> AgentStatus.DEREGISTERED;
            default -> throw new IllegalArgumentException("Unknown AgentStatusProto: " + status);
        };
    }

    // --- JobPriority ---

    private static JobPriorityProto toProto(JobPriority priority) {
        return switch (priority) {
            case LOW -> JobPriorityProto.JOB_PRIORITY_LOW;
            case NORMAL -> JobPriorityProto.JOB_PRIORITY_NORMAL;
            case HIGH -> JobPriorityProto.JOB_PRIORITY_HIGH;
            case CRITICAL -> JobPriorityProto.JOB_PRIORITY_CRITICAL;
        };
    }

    private static JobPriority fromProto(JobPriorityProto priority) {
        return switch (priority) {
            case JOB_PRIORITY_LOW -> JobPriority.LOW;
            case JOB_PRIORITY_NORMAL -> JobPriority.NORMAL;
            case JOB_PRIORITY_HIGH -> JobPriority.HIGH;
            case JOB_PRIORITY_CRITICAL -> JobPriority.CRITICAL;
            default -> throw new IllegalArgumentException("Unknown JobPriorityProto: " + priority);
        };
    }

    // --- JobAssignmentStatus ---

    private static JobAssignmentStatusProto toProto(JobAssignmentStatus status) {
        return switch (status) {
            case ASSIGNED -> JobAssignmentStatusProto.JOB_ASSIGNMENT_STATUS_ASSIGNED;
            case ACCEPTED -> JobAssignmentStatusProto.JOB_ASSIGNMENT_STATUS_ACCEPTED;
            case IN_PROGRESS -> JobAssignmentStatusProto.JOB_ASSIGNMENT_STATUS_IN_PROGRESS;
            case COMPLETED -> JobAssignmentStatusProto.JOB_ASSIGNMENT_STATUS_COMPLETED;
            case FAILED -> JobAssignmentStatusProto.JOB_ASSIGNMENT_STATUS_FAILED;
            case REJECTED -> JobAssignmentStatusProto.JOB_ASSIGNMENT_STATUS_REJECTED;
            case TIMEOUT -> JobAssignmentStatusProto.JOB_ASSIGNMENT_STATUS_TIMEOUT;
            case CANCELLED -> JobAssignmentStatusProto.JOB_ASSIGNMENT_STATUS_CANCELLED;
        };
    }

    private static JobAssignmentStatus fromProto(JobAssignmentStatusProto status) {
        return switch (status) {
            case JOB_ASSIGNMENT_STATUS_ASSIGNED -> JobAssignmentStatus.ASSIGNED;
            case JOB_ASSIGNMENT_STATUS_ACCEPTED -> JobAssignmentStatus.ACCEPTED;
            case JOB_ASSIGNMENT_STATUS_IN_PROGRESS -> JobAssignmentStatus.IN_PROGRESS;
            case JOB_ASSIGNMENT_STATUS_COMPLETED -> JobAssignmentStatus.COMPLETED;
            case JOB_ASSIGNMENT_STATUS_FAILED -> JobAssignmentStatus.FAILED;
            case JOB_ASSIGNMENT_STATUS_REJECTED -> JobAssignmentStatus.REJECTED;
            case JOB_ASSIGNMENT_STATUS_TIMEOUT -> JobAssignmentStatus.TIMEOUT;
            case JOB_ASSIGNMENT_STATUS_CANCELLED -> JobAssignmentStatus.CANCELLED;
            default -> throw new IllegalArgumentException("Unknown JobAssignmentStatusProto: " + status);
        };
    }

    // --- SelectionStrategy ---

    private static SelectionStrategyProto toProto(JobRequirements.SelectionStrategy strategy) {
        return switch (strategy) {
            case ROUND_ROBIN -> SelectionStrategyProto.SELECTION_STRATEGY_ROUND_ROBIN;
            case LEAST_LOADED -> SelectionStrategyProto.SELECTION_STRATEGY_LEAST_LOADED;
            case CAPABILITY_BASED -> SelectionStrategyProto.SELECTION_STRATEGY_CAPABILITY_BASED;
            case LOCALITY_AWARE -> SelectionStrategyProto.SELECTION_STRATEGY_LOCALITY_AWARE;
            case WEIGHTED_SCORE -> SelectionStrategyProto.SELECTION_STRATEGY_WEIGHTED_SCORE;
            case PREFERRED_AGENT -> SelectionStrategyProto.SELECTION_STRATEGY_PREFERRED_AGENT;
        };
    }

    private static JobRequirements.SelectionStrategy fromProto(SelectionStrategyProto strategy) {
        return switch (strategy) {
            case SELECTION_STRATEGY_ROUND_ROBIN -> JobRequirements.SelectionStrategy.ROUND_ROBIN;
            case SELECTION_STRATEGY_LEAST_LOADED -> JobRequirements.SelectionStrategy.LEAST_LOADED;
            case SELECTION_STRATEGY_CAPABILITY_BASED -> JobRequirements.SelectionStrategy.CAPABILITY_BASED;
            case SELECTION_STRATEGY_LOCALITY_AWARE -> JobRequirements.SelectionStrategy.LOCALITY_AWARE;
            case SELECTION_STRATEGY_WEIGHTED_SCORE -> JobRequirements.SelectionStrategy.WEIGHTED_SCORE;
            case SELECTION_STRATEGY_PREFERRED_AGENT -> JobRequirements.SelectionStrategy.PREFERRED_AGENT;
            default -> throw new IllegalArgumentException("Unknown SelectionStrategyProto: " + strategy);
        };
    }
}
