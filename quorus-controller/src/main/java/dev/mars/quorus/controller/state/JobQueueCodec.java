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
import java.util.HashMap;
import java.util.HashSet;

/**
 * Protobuf codec for {@link JobQueueCommand}, {@link QueuedJob},
 * {@link JobRequirements}, {@link JobPriority}, and
 * {@link JobRequirements.SelectionStrategy}.
 *
 * <p>Package-private utility class used by {@link ProtobufCommandCodec}.
 * Depends on {@link TransferCodec} for {@link TransferJob} conversions.
 *
 * @author Mark Andrew Ray-Smith Cityline Ltd
 * @since 2025
 */
final class JobQueueCodec {

    private JobQueueCodec() {
    }

    // ── Command ─────────────────────────────────────────────────

    static JobQueueCommandProto toProto(JobQueueCommand cmd) {
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

    static JobQueueCommand fromProto(JobQueueCommandProto proto) {
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

    // ── Domain models ───────────────────────────────────────────

    private static QueuedJobProto toProto(QueuedJob queuedJob) {
        QueuedJobProto.Builder builder = QueuedJobProto.newBuilder()
                .setRetryCount(queuedJob.getRetryCount());
        if (queuedJob.getTransferJob() != null) {
            builder.setTransferJob(TransferCodec.toProto(queuedJob.getTransferJob()));
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

    private static QueuedJob fromProto(QueuedJobProto proto) {
        QueuedJob.Builder builder = new QueuedJob.Builder()
                .transferJob(TransferCodec.fromProto(proto.getTransferJob()))
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

    // ── Enums ───────────────────────────────────────────────────

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
