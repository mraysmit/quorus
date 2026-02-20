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

import dev.mars.quorus.controller.raft.grpc.*;
import dev.mars.quorus.core.*;

import java.time.Instant;
import java.util.Optional;

/**
 * Protobuf codec for {@link JobAssignmentCommand}, {@link JobAssignment},
 * and {@link JobAssignmentStatus}.
 *
 * <p>Package-private utility class used by {@link ProtobufCommandCodec}.
 *
 * @author Mark Andrew Ray-Smith Cityline Ltd
 * @since 2025
 */
final class JobAssignmentCodec {

    private JobAssignmentCodec() {
    }

    // ── Command ─────────────────────────────────────────────────

    static JobAssignmentCommandProto toProto(JobAssignmentCommand cmd) {
        JobAssignmentCommandProto.Builder builder = JobAssignmentCommandProto.newBuilder()
                .setType(toProto(cmd.getType()));
        Optional.ofNullable(cmd.getAssignmentId()).ifPresent(builder::setAssignmentId);
        Optional.ofNullable(cmd.getJobAssignment()).ifPresent(a -> builder.setJobAssignment(toProto(a)));
        Optional.ofNullable(cmd.getNewStatus()).ifPresent(s -> builder.setNewStatus(toProto(s)));
        Optional.ofNullable(cmd.getReason()).ifPresent(builder::setReason);
        Optional.ofNullable(cmd.getTimestamp()).ifPresent(t -> builder.setTimestampEpochMs(t.toEpochMilli()));
        return builder.build();
    }

    static JobAssignmentCommand fromProto(JobAssignmentCommandProto proto) {
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

    // ── Domain models ───────────────────────────────────────────

    private static JobAssignmentProto toProto(JobAssignment assignment) {
        JobAssignmentProto.Builder builder = JobAssignmentProto.newBuilder()
                .setRetryCount(assignment.getRetryCount())
                .setEstimatedDurationMs(assignment.getEstimatedDurationMs());
        Optional.ofNullable(assignment.getJobId()).ifPresent(builder::setJobId);
        Optional.ofNullable(assignment.getAgentId()).ifPresent(builder::setAgentId);
        Optional.ofNullable(assignment.getAssignedAt()).ifPresent(t -> builder.setAssignedAtEpochMs(t.toEpochMilli()));
        Optional.ofNullable(assignment.getAcceptedAt()).ifPresent(t -> builder.setAcceptedAtEpochMs(t.toEpochMilli()));
        Optional.ofNullable(assignment.getStartedAt()).ifPresent(t -> builder.setStartedAtEpochMs(t.toEpochMilli()));
        Optional.ofNullable(assignment.getCompletedAt()).ifPresent(t -> builder.setCompletedAtEpochMs(t.toEpochMilli()));
        Optional.ofNullable(assignment.getStatus()).ifPresent(s -> builder.setStatus(toProto(s)));
        Optional.ofNullable(assignment.getFailureReason()).ifPresent(builder::setFailureReason);
        Optional.ofNullable(assignment.getAssignmentStrategy()).ifPresent(builder::setAssignmentStrategy);
        return builder.build();
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

    // ── Enums ───────────────────────────────────────────────────

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
}
