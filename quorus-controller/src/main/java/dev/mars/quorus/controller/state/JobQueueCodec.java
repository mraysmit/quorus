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
import java.util.Optional;

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
                .setJobId(cmd.jobId())
                .setTimestampEpochMs(cmd.timestamp().toEpochMilli());

        switch (cmd) {
            case JobQueueCommand.Enqueue e -> {
                builder.setType(JobQueueCommandType.JOB_QUEUE_CMD_ENQUEUE);
                builder.setQueuedJob(toProto(e.queuedJob()));
            }
            case JobQueueCommand.Dequeue d -> {
                builder.setType(JobQueueCommandType.JOB_QUEUE_CMD_DEQUEUE);
                if (d.reason() != null) builder.setReason(d.reason());
            }
            case JobQueueCommand.Prioritize p -> {
                builder.setType(JobQueueCommandType.JOB_QUEUE_CMD_PRIORITIZE);
                builder.setNewPriority(toProto(p.newPriority()));
                if (p.reason() != null) builder.setReason(p.reason());
            }
            case JobQueueCommand.Remove r -> {
                builder.setType(JobQueueCommandType.JOB_QUEUE_CMD_REMOVE);
                if (r.reason() != null) builder.setReason(r.reason());
            }
            case JobQueueCommand.Expedite ex -> {
                builder.setType(JobQueueCommandType.JOB_QUEUE_CMD_EXPEDITE);
                if (ex.reason() != null) builder.setReason(ex.reason());
            }
            case JobQueueCommand.UpdateRequirements ur -> {
                builder.setType(JobQueueCommandType.JOB_QUEUE_CMD_UPDATE_REQUIREMENTS);
                builder.setQueuedJob(toProto(ur.queuedJob()));
            }
        }

        return builder.build();
    }

    static JobQueueCommand fromProto(JobQueueCommandProto proto) {
        Instant timestamp = proto.getTimestampEpochMs() > 0
                ? Instant.ofEpochMilli(proto.getTimestampEpochMs()) : Instant.now();
        String jobId = proto.getJobId();
        String reason = proto.getReason().isEmpty() ? null : proto.getReason();

        return switch (proto.getType()) {
            case JOB_QUEUE_CMD_ENQUEUE -> new JobQueueCommand.Enqueue(jobId,
                    fromProto(proto.getQueuedJob()), timestamp);
            case JOB_QUEUE_CMD_DEQUEUE -> new JobQueueCommand.Dequeue(jobId, reason, timestamp);
            case JOB_QUEUE_CMD_PRIORITIZE -> {
                JobPriority newPriority = proto.getNewPriority() != JobPriorityProto.JOB_PRIORITY_UNSPECIFIED
                        ? fromProto(proto.getNewPriority()) : null;
                yield new JobQueueCommand.Prioritize(jobId, newPriority, reason, timestamp);
            }
            case JOB_QUEUE_CMD_REMOVE -> new JobQueueCommand.Remove(jobId, reason, timestamp);
            case JOB_QUEUE_CMD_EXPEDITE -> new JobQueueCommand.Expedite(jobId, reason, timestamp);
            case JOB_QUEUE_CMD_UPDATE_REQUIREMENTS -> new JobQueueCommand.UpdateRequirements(jobId,
                    fromProto(proto.getQueuedJob()), timestamp);
            default -> throw new IllegalArgumentException("Unknown JobQueueCommandType: " + proto.getType());
        };
    }

    // ── Domain models ───────────────────────────────────────────

    private static QueuedJobProto toProto(QueuedJob queuedJob) {
        QueuedJobProto.Builder builder = QueuedJobProto.newBuilder()
                .setRetryCount(queuedJob.getRetryCount());
        Optional.ofNullable(queuedJob.getTransferJob()).ifPresent(j -> builder.setTransferJob(TransferCodec.toProto(j)));
        Optional.ofNullable(queuedJob.getPriority()).ifPresent(p -> builder.setPriority(toProto(p)));
        Optional.ofNullable(queuedJob.getQueueTime()).ifPresent(t -> builder.setQueueTimeEpochMs(t.toEpochMilli()));
        Optional.ofNullable(queuedJob.getRequirements()).ifPresent(r -> builder.setRequirements(toProto(r)));
        Optional.ofNullable(queuedJob.getSubmittedBy()).ifPresent(builder::setSubmittedBy);
        Optional.ofNullable(queuedJob.getWorkflowId()).ifPresent(builder::setWorkflowId);
        Optional.ofNullable(queuedJob.getGroupName()).ifPresent(builder::setGroupName);
        Optional.ofNullable(queuedJob.getEarliestStartTime()).ifPresent(t -> builder.setEarliestStartTimeEpochMs(t.toEpochMilli()));
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
        Optional.ofNullable(req.getTargetRegion()).ifPresent(builder::setTargetRegion);
        Optional.ofNullable(req.getRequiredProtocols()).ifPresent(builder::addAllRequiredProtocols);
        Optional.ofNullable(req.getPreferredRegions()).ifPresent(builder::addAllPreferredRegions);
        Optional.ofNullable(req.getExcludedAgents()).ifPresent(builder::addAllExcludedAgents);
        Optional.ofNullable(req.getPreferredAgents()).ifPresent(builder::addAllPreferredAgents);
        Optional.ofNullable(req.getSelectionStrategy()).ifPresent(s -> builder.setSelectionStrategy(toProto(s)));
        Optional.ofNullable(req.getCustomAttributes()).ifPresent(builder::putAllCustomAttributes);
        Optional.ofNullable(req.getTenantId()).ifPresent(builder::setTenantId);
        Optional.ofNullable(req.getNamespace()).ifPresent(builder::setNamespace);
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
