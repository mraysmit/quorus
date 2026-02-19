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
import dev.mars.quorus.controller.raft.grpc.*;

/**
 * Facade for converting between Java command objects and Protobuf-encoded bytes.
 * Replaces Java serialization (ObjectOutputStream/ObjectInputStream) with
 * version-safe Protobuf encoding for Raft log entry command payloads.
 *
 * <p>Delegates to domain-specific codecs for the six {@link StateMachineCommand} sealed subtypes:
 * <ul>
 *   <li>{@link TransferCodec} — {@link TransferJobCommand}, TransferJob, TransferRequest, TransferStatus</li>
 *   <li>{@link AgentCodec} — {@link AgentCommand}, AgentInfo, AgentCapabilities, AgentStatus</li>
 *   <li>{@link SystemMetadataCodec} — {@link SystemMetadataCommand}</li>
 *   <li>{@link JobAssignmentCodec} — {@link JobAssignmentCommand}, JobAssignment, JobAssignmentStatus</li>
 *   <li>{@link JobQueueCodec} — {@link JobQueueCommand}, QueuedJob, JobRequirements, JobPriority</li>
 *   <li>{@link RouteCodec} — {@link RouteCommand}, RouteConfiguration, TriggerConfiguration, RouteStatus</li>
 * </ul>
 *
 * @author Mark Andrew Ray-Smith Cityline Ltd
 * @since 2025
 */
public final class ProtobufCommandCodec {

    private ProtobufCommandCodec() {
        // Utility class — not instantiable
    }

    /**
     * Serialize a state machine command to Protobuf-encoded bytes.
     *
     * @param command the command object (one of the six sealed command types, or null for no-op)
     * @return Protobuf-encoded bytes as ByteString
     */
    public static ByteString serialize(StateMachineCommand command) {
        if (command == null) {
            // No-op entry: encode as empty RaftCommand (no oneof field set)
            return RaftCommand.getDefaultInstance().toByteString();
        }
        RaftCommand raftCommand = switch (command) {
            case TransferJobCommand cmd -> RaftCommand.newBuilder()
                    .setTransferJobCommand(TransferCodec.toProto(cmd)).build();
            case AgentCommand cmd -> RaftCommand.newBuilder()
                    .setAgentCommand(AgentCodec.toProto(cmd)).build();
            case SystemMetadataCommand cmd -> RaftCommand.newBuilder()
                    .setSystemMetadataCommand(SystemMetadataCodec.toProto(cmd)).build();
            case JobAssignmentCommand cmd -> RaftCommand.newBuilder()
                    .setJobAssignmentCommand(JobAssignmentCodec.toProto(cmd)).build();
            case JobQueueCommand cmd -> RaftCommand.newBuilder()
                    .setJobQueueCommand(JobQueueCodec.toProto(cmd)).build();
            case RouteCommand cmd -> RaftCommand.newBuilder()
                    .setRouteCommand(RouteCodec.toProto(cmd)).build();
        };
        return raftCommand.toByteString();
    }

    /**
     * Deserialize Protobuf-encoded bytes back to a state machine command object.
     *
     * @param data Protobuf-encoded bytes
     * @return the deserialized command object, or null for no-op entries
     * @throws RuntimeException if the data cannot be parsed
     */
    public static StateMachineCommand deserialize(ByteString data) {
        try {
            RaftCommand raftCommand = RaftCommand.parseFrom(data);
            return switch (raftCommand.getCommandCase()) {
                case TRANSFER_JOB_COMMAND -> TransferCodec.fromProto(raftCommand.getTransferJobCommand());
                case AGENT_COMMAND -> AgentCodec.fromProto(raftCommand.getAgentCommand());
                case SYSTEM_METADATA_COMMAND -> SystemMetadataCodec.fromProto(raftCommand.getSystemMetadataCommand());
                case JOB_ASSIGNMENT_COMMAND -> JobAssignmentCodec.fromProto(raftCommand.getJobAssignmentCommand());
                case JOB_QUEUE_COMMAND -> JobQueueCodec.fromProto(raftCommand.getJobQueueCommand());
                case ROUTE_COMMAND -> RouteCodec.fromProto(raftCommand.getRouteCommand());
                case COMMAND_NOT_SET -> null; // No-op entry
            };
        } catch (InvalidProtocolBufferException e) {
            throw new RuntimeException("Failed to deserialize Protobuf command", e);
        }
    }
}
