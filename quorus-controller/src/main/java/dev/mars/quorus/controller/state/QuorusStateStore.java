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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import dev.mars.quorus.agent.AgentInfo;
import dev.mars.quorus.agent.AgentStatus;
import dev.mars.quorus.agent.AgentCapabilities;
import dev.mars.quorus.controller.raft.RaftLogApplicator;
import dev.mars.quorus.core.JobPriority;
import dev.mars.quorus.core.TransferJob;
import dev.mars.quorus.core.TransferStatus;
import dev.mars.quorus.core.TransferAttempt;
import dev.mars.quorus.core.TransferAttemptOutcome;
import dev.mars.quorus.core.TransferAttemptStatus;
import dev.mars.quorus.util.SensitiveDataRedactor;
import dev.mars.quorus.core.JobAssignment;
import dev.mars.quorus.core.JobAssignmentStatus;
import dev.mars.quorus.core.QueuedJob;
import dev.mars.quorus.core.RouteConfiguration;
import dev.mars.quorus.core.RouteStatus;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.metrics.Meter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Map;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Description for QuorusStateStore
 *
 * @author Mark Andrew Ray-Smith Cityline Ltd
 * @version 1.0
 * @since 2025-08-20
 */

public class QuorusStateStore implements RaftLogApplicator {

    private static final Logger logger = LoggerFactory.getLogger(QuorusStateStore.class);

    // State data
    private final Map<String, TransferJobSnapshot> transferJobs = new ConcurrentHashMap<>();
    private final Map<String, AgentInfo> agents = new ConcurrentHashMap<>();
    private volatile Map<String, String> systemMetadata = new ConcurrentHashMap<>();
    private final Map<String, JobAssignment> jobAssignments = new ConcurrentHashMap<>();
    private final Map<String, QueuedJob> jobQueue = new ConcurrentHashMap<>();
    private final Map<String, RouteConfiguration> routes = new ConcurrentHashMap<>();
    private final Map<String, TransferAttempt> transferAttempts = new ConcurrentHashMap<>();
    private final Map<String, String> activeAttemptByJob = new ConcurrentHashMap<>();
    private final Map<String, List<TransferEvent>> transferEvents = new ConcurrentHashMap<>();
    private final AtomicLong lastAppliedIndex = new AtomicLong(0);

    // JSON serialization
    private final ObjectMapper objectMapper = new ObjectMapper();

    {
        objectMapper.registerModule(new JavaTimeModule());
    }

    // Default metadata version for compatibility tracking
    private static final String DEFAULT_VERSION = "2.0";

    public QuorusStateStore(Map<String, String> initialMetadata) {
        // Set default metadata first
        initializeDefaultMetadata();
        
        // Override with any provided initial metadata
        if (initialMetadata != null) {
            systemMetadata.putAll(initialMetadata);
        }

        // Initialize OpenTelemetry Metrics
        Meter meter = GlobalOpenTelemetry.getMeter("quorus-controller");

        meter.gaugeBuilder("quorus.agents.total")
                .setDescription("Total number of registered agents")
                .ofLongs()
                .buildWithCallback(measurement -> measurement.record(agents.size()));

        meter.gaugeBuilder("quorus.jobs.total")
                .setDescription("Total number of transfer jobs")
                .ofLongs()
                .buildWithCallback(measurement -> measurement.record(transferJobs.size()));

        meter.gaugeBuilder("quorus.jobs.queued")
                .setDescription("Number of jobs in the queue")
                .ofLongs()
                .buildWithCallback(measurement -> measurement.record(jobQueue.size()));

        meter.gaugeBuilder("quorus.jobs.assignments")
                .setDescription("Total number of job assignments")
                .ofLongs()
                .buildWithCallback(measurement -> measurement.record(jobAssignments.size()));

        meter.gaugeBuilder("quorus.routes.total")
                .setDescription("Total number of configured routes")
                .ofLongs()
                .buildWithCallback(measurement -> measurement.record(routes.size()));
    }

    public QuorusStateStore() {
        this(null);
    }

    /**
     * Initializes default metadata values including version information.
     * Called during construction and reset to ensure consistent initial state.
     */
    private void initializeDefaultMetadata() {
        logger.debug("Initializing default metadata: version={}", DEFAULT_VERSION);
        systemMetadata.put("version", DEFAULT_VERSION);
    }

    // ── Entity lookup helpers ───────────────────────────────────
    // In a Raft state machine, committed commands must be applied deterministically
    // on every node. Throwing on "entity not found" would break log replay (e.g.
    // a DELETE followed by an UPDATE for the same entity — both committed).
    @Override
    public CommandResult<?> apply(RaftCommand command) {
        if (command == null) {
            logger.debug("Received null command, returning NoOp");
            return new CommandResult.NoOp<>();
        }

        logger.debug("Applying command: type={}", command.getClass().getSimpleName());

        try {
            CommandResult<?> result = switch (command) {
                case TransferJobCommand cmd -> applyTransferJobCommand(cmd);
                case AgentCommand cmd -> applyAgentCommand(cmd);
                case SystemMetadataCommand cmd -> applySystemMetadataCommand(cmd);
                case JobAssignmentCommand cmd -> applyJobAssignmentCommand(cmd);
                case JobQueueCommand cmd -> applyJobQueueCommand(cmd);
                case RouteCommand cmd -> applyRouteCommand(cmd);
                case TransferAttemptCommand cmd -> applyTransferAttemptCommand(cmd);
            };
            logger.debug("Command applied successfully: type={}, result={}", 
                command.getClass().getSimpleName(), result.getClass().getSimpleName());
            return result;
        } catch (Exception e) {
            logger.error("Failed to apply command: type={}, message={}", command.getClass().getSimpleName(), e.getMessage());
            logger.debug("Stack trace for command apply failure: type={}", command.getClass().getSimpleName(), e);
            throw new RuntimeException("Failed to apply command", e);
        }
    }

    private CommandResult<?> applyTransferJobCommand(TransferJobCommand command) {
        String jobId = command.jobId();

        return switch (command) {
            case TransferJobCommand.Create cmd -> {
                TransferJob job = cmd.transferJob();
                if (!jobId.equals(job.getJobId())) {
                    yield rejected("IDENTIFIER_MISMATCH", "Transfer command ID does not match the transfer job ID");
                }
                if (transferJobs.containsKey(jobId)) {
                    yield rejected("DUPLICATE_ENTITY", "Transfer job already exists: " + jobId);
                }
                logger.debug("Creating transfer job: jobId={}, sourceUri={}, destPath={}", 
                    jobId, SensitiveDataRedactor.redactUri(job.getRequest().getSourceUri()),
                    SensitiveDataRedactor.redactUri(job.getRequest().getDestinationUri()));
                TransferJobSnapshot snapshot = TransferJobSnapshot.fromTransferJob(job, cmd.tenantId());
                transferJobs.put(jobId, snapshot);
                appendTransferEvent(jobId, "TRANSFER_SUBMITTED", cmd.timestamp(), null, null,
                        null, snapshot.getStatus().name(), null, null, null);
                logger.info("Created transfer job: jobId={}, protocol={}, totalJobs={}", 
                    jobId, job.getRequest().getProtocol(), transferJobs.size());
                yield new CommandResult.Success<>(job);
            }
            case TransferJobCommand.UpdateStatus cmd -> {
                logger.debug("Updating transfer job status: jobId={}, newStatus={}", jobId, cmd.newStatus());
                TransferJobSnapshot existingJob = transferJobs.get(jobId);
                if (existingJob == null) {
                    logger.warn("Transfer job not found for status update: id={}", jobId);
                    yield new CommandResult.NotFound<>(jobId, "TransferJob");
                }
                // CAS check: reject if current status doesn't match expected
                if (existingJob.getStatus() != cmd.expectedStatus()) {
                    logger.debug("CAS mismatch for transfer job: jobId={}, expected={}, actual={}",
                        jobId, cmd.expectedStatus(), existingJob.getStatus());
                    yield new CommandResult.CasMismatch<>(existingJob);
                }
                if (!existingJob.getStatus().canTransitionTo(cmd.newStatus())) {
                    yield rejected("INVALID_STATE_TRANSITION", "Transfer job " + jobId + " cannot transition from "
                            + existingJob.getStatus() + " to " + cmd.newStatus());
                }
                TransferStatus oldStatus = existingJob.getStatus();
                TransferJobSnapshot updatedJob = new TransferJobSnapshot(
                        existingJob.getJobId(),
                        existingJob.getSourceUri(),
                        existingJob.getDestinationPath(),
                        cmd.newStatus(),
                        existingJob.getBytesTransferred(),
                        existingJob.getTotalBytes(),
                        existingJob.getStartTime(),
                        cmd.timestamp(),
                        existingJob.getErrorMessage(),
                        existingJob.getDescription(),
                        existingJob.getTenantId(),
                        existingJob.getOperationalContext(),
                        existingJob.getLastProgressAt(),
                        existingJob.getServiceConnectionId(), existingJob.getRemotePath(),
                        existingJob.getConnectionPolicyVersion(), existingJob.getConnectionPolicyDigest(),
                        existingJob.getAgentPool(),
                        existingJob.getControllerResolvedAddresses());
                transferJobs.put(jobId, updatedJob);
                logger.info("Updated transfer job status: jobId={}, oldStatus={}, newStatus={}", 
                    jobId, oldStatus, cmd.newStatus());
                yield new CommandResult.Success<>(updatedJob);
            }
            case TransferJobCommand.UpdateProgress cmd -> {
                logger.debug("Updating transfer job progress: jobId={}, bytesTransferred={}", 
                    jobId, cmd.bytesTransferred());
                TransferJobSnapshot progressJob = transferJobs.get(jobId);
                if (progressJob == null) {
                    logger.warn("Transfer job not found for progress update: id={}", jobId);
                    yield new CommandResult.NotFound<>(jobId, "TransferJob");
                }
                if (cmd.bytesTransferred() < progressJob.getBytesTransferred()) {
                    yield rejected("INVALID_PROGRESS", "Transfer progress cannot move backwards for job " + jobId);
                }
                if (cmd.bytesTransferred() < 0
                        || (progressJob.getTotalBytes() > 0 && cmd.bytesTransferred() > progressJob.getTotalBytes())) {
                    yield rejected("INVALID_PROGRESS", "Transfer progress is outside the valid byte range for job " + jobId);
                }
                if (progressJob.getStatus().isTerminal()) {
                    yield rejected("INVALID_STATE_TRANSITION", "Transfer progress cannot change after terminal status for job " + jobId);
                }
                long oldBytes = progressJob.getBytesTransferred();
                TransferJobSnapshot updatedJob = new TransferJobSnapshot(
                        progressJob.getJobId(),
                        progressJob.getSourceUri(),
                        progressJob.getDestinationPath(),
                        progressJob.getStatus(),
                        cmd.bytesTransferred(),
                        progressJob.getTotalBytes(),
                        progressJob.getStartTime(),
                        cmd.timestamp(),
                        progressJob.getErrorMessage(),
                        progressJob.getDescription(),
                        progressJob.getTenantId(),
                        progressJob.getOperationalContext(),
                        cmd.bytesTransferred() > oldBytes ? cmd.timestamp() : progressJob.getLastProgressAt(),
                        progressJob.getServiceConnectionId(), progressJob.getRemotePath(),
                        progressJob.getConnectionPolicyVersion(), progressJob.getConnectionPolicyDigest(),
                        progressJob.getAgentPool(),
                        progressJob.getControllerResolvedAddresses());
                transferJobs.put(jobId, updatedJob);
                logger.debug("Updated transfer job progress: jobId={}, oldBytes={}, newBytes={}, totalBytes={}", 
                    jobId, oldBytes, cmd.bytesTransferred(), progressJob.getTotalBytes());
                yield new CommandResult.Success<>(updatedJob);
            }
            case TransferJobCommand.Delete delete -> {
                logger.debug("Deleting transfer job: jobId={}", jobId);
                logger.trace("Transfer delete command timestamp={}", delete.timestamp());
                if (jobAssignments.values().stream().anyMatch(a -> jobId.equals(a.getJobId()))
                        || transferAttempts.values().stream().anyMatch(a -> jobId.equals(a.getJobId()))) {
                    yield rejected("DEPENDENT_ENTITY_EXISTS", "Transfer job is still referenced by an assignment: " + jobId);
                }
                TransferJobSnapshot removedJob = transferJobs.remove(jobId);
                if (removedJob == null) {
                    logger.warn("Transfer job not found for deletion: id={}", jobId);
                    yield new CommandResult.NotFound<>(jobId, "TransferJob");
                }
                logger.info("Deleted transfer job: jobId={}, finalStatus={}, totalJobs={}", 
                    jobId, removedJob.getStatus(), transferJobs.size());
                yield new CommandResult.Success<>(removedJob);
            }
        };
    }

    private CommandResult<?> applyAgentCommand(AgentCommand command) {
        String agentId = command.agentId();
        logger.debug("Processing agent command: agentId={}, type={}", agentId, command.getClass().getSimpleName());

        return switch (command) {
            case AgentCommand.Register cmd -> {
                AgentInfo agentInfo = cmd.agentInfo();
                if (!agentId.equals(agentInfo.getAgentId())) {
                    yield rejected("IDENTIFIER_MISMATCH", "Agent command ID does not match the agent payload ID");
                }
                logger.debug("Registering agent: agentId={}, endpoint={}, status={}", 
                    agentId, agentInfo.getEndpoint(), agentInfo.getStatus());
                agents.put(agentId, agentInfo);
                logger.info("Registered agent: agentId={}, endpoint={}, totalAgents={}", 
                    agentId, agentInfo.getEndpoint(), agents.size());
                yield new CommandResult.Success<>(agentInfo);
            }
            case AgentCommand.Deregister deregister -> {
                logger.debug("Deregistering agent: agentId={}", agentId);
                logger.trace("Agent deregister command timestamp={}", deregister.timestamp());
                if (jobAssignments.values().stream().anyMatch(a -> agentId.equals(a.getAgentId()))
                        || transferAttempts.values().stream().anyMatch(a -> agentId.equals(a.getAgentId()))) {
                    yield rejected("DEPENDENT_ENTITY_EXISTS", "Agent is still referenced by an assignment: " + agentId);
                }
                AgentInfo removedAgent = agents.remove(agentId);
                if (removedAgent == null) {
                    logger.warn("Agent not found for deregistration: id={}", agentId);
                    yield new CommandResult.NotFound<>(agentId, "Agent");
                }
                logger.info("Deregistered agent: agentId={}, endpoint={}, totalAgents={}", 
                    agentId, removedAgent.getEndpoint(), agents.size());
                yield new CommandResult.Success<>(removedAgent);
            }
            case AgentCommand.UpdateStatus cmd -> {
                logger.debug("Updating agent status: agentId={}, newStatus={}", agentId, cmd.newStatus());
                AgentInfo existingAgent = agents.get(agentId);
                if (existingAgent == null) {
                    logger.warn("Agent not found for status update: id={}", agentId);
                    yield new CommandResult.NotFound<>(agentId, "Agent");
                }
                // CAS check: reject if current status doesn't match expected
                if (existingAgent.getStatus() != cmd.expectedStatus()) {
                    logger.debug("CAS mismatch for agent: agentId={}, expected={}, actual={}",
                        agentId, cmd.expectedStatus(), existingAgent.getStatus());
                    yield new CommandResult.CasMismatch<>(existingAgent);
                }
                if (!existingAgent.getStatus().canTransitionTo(cmd.newStatus())) {
                    yield rejected("INVALID_STATE_TRANSITION", "Agent " + agentId + " cannot transition from "
                            + existingAgent.getStatus() + " to " + cmd.newStatus());
                }
                AgentStatus oldStatus = existingAgent.getStatus();
                AgentInfo updated = AgentInfo.copyOf(existingAgent);
                updated.setStatus(cmd.newStatus());
                updated.setLastHeartbeat(cmd.timestamp());
                agents.put(agentId, updated);
                logger.info("Updated agent status: agentId={}, oldStatus={}, newStatus={}", 
                    agentId, oldStatus, cmd.newStatus());
                yield new CommandResult.Success<>(updated);
            }
            case AgentCommand.UpdateCapabilities cmd -> {
                logger.debug("Updating agent capabilities: agentId={}", agentId);
                AgentInfo agentToUpdate = agents.get(agentId);
                if (agentToUpdate == null) {
                    logger.warn("Agent not found for capabilities update: id={}", agentId);
                    yield new CommandResult.NotFound<>(agentId, "Agent");
                }
                AgentCapabilities newCapabilities = cmd.newCapabilities();
                AgentInfo updated = AgentInfo.copyOf(agentToUpdate);
                updated.setCapabilities(newCapabilities);
                updated.setLastHeartbeat(cmd.timestamp());
                agents.put(agentId, updated);
                logger.info("Updated agent capabilities: agentId={}, protocols={}", 
                    agentId, newCapabilities != null ? newCapabilities.getSupportedProtocols() : "null");
                yield new CommandResult.Success<>(updated);
            }
            case AgentCommand.Heartbeat cmd -> {
                logger.debug("Processing heartbeat: agentId={}", agentId);
                AgentInfo agentForHeartbeat = agents.get(agentId);
                if (agentForHeartbeat == null) {
                    logger.warn("Agent not found for heartbeat: id={}", agentId);
                    yield new CommandResult.NotFound<>(agentId, "Agent");
                }
                AgentInfo updated = AgentInfo.copyOf(agentForHeartbeat);
                updated.setLastHeartbeat(cmd.timestamp());
                if (agentForHeartbeat.getStatus() == AgentStatus.REGISTERING) {
                    logger.debug("Agent transitioning from REGISTERING to HEALTHY: agentId={}", agentId);
                    updated.setStatus(AgentStatus.HEALTHY);
                }
                agents.put(agentId, updated);
                logger.debug("Heartbeat received: agentId={}, status={}", agentId, updated.getStatus());
                yield new CommandResult.Success<>(updated);
            }
        };
    }

    private CommandResult<?> applySystemMetadataCommand(SystemMetadataCommand command) {
        String key = command.key();
        if (ServiceConnectionRegistry.forbiddenLegacyMutation(systemMetadata, key)) {
            return rejected("REGISTRY_SCHEMA_CONFLICT", "Legacy registry mutation is not permitted after migration");
        }
        Map<String, String> target = systemMetadata;
        if (ServiceConnectionRegistry.versioned(key)) {
            try {
                target = ServiceConnectionRegistry.prepareMutation(systemMetadata, command);
            } catch (IllegalArgumentException error) {
                return rejected("REGISTRY_OWNERSHIP_CONFLICT",
                        "Registry ownership or migration conflict; operator review required");
            }
        }
        logger.debug("Processing system metadata command: key={}, type={}", key, command.getClass().getSimpleName());

        return switch (command) {
            case SystemMetadataCommand.Set cmd -> {
                String oldValue = target.put(key, cmd.value());
                systemMetadata = target;
                logger.info("Set system metadata: key={}, valuePresent=true, replaced={}", key, oldValue != null);
                yield new CommandResult.Success<>(oldValue);
            }
            case SystemMetadataCommand.Delete delete -> {
                logger.trace("System metadata delete command={}", delete);
                String removedValue = target.remove(key);
                if (removedValue == null) {
                    logger.warn("System metadata not found for deletion: key={}", key);
                    yield new CommandResult.NotFound<>(key, "SystemMetadata");
                }
                systemMetadata = target;
                    logger.info("Deleted system metadata: key={}, valueRemoved=true", key);
                yield new CommandResult.Success<>(removedValue);
            }
        };
    }

    private CommandResult<?> applyJobAssignmentCommand(JobAssignmentCommand command) {
        String assignmentId = command.assignmentId();
        logger.debug("Processing job assignment command: assignmentId={}, type={}", assignmentId, command.getClass().getSimpleName());

        return switch (command) {
            case JobAssignmentCommand.Assign cmd -> {
                JobAssignment assignment = cmd.jobAssignment();
                String canonicalAssignmentId = assignment.getJobId() + ":" + assignment.getAgentId();
                if (!assignmentId.equals(canonicalAssignmentId)) {
                    yield rejected("IDENTIFIER_MISMATCH", "Assignment ID does not match its job and agent IDs");
                }
                if (jobAssignments.containsKey(assignmentId)) {
                    yield rejected("DUPLICATE_ENTITY", "Job assignment already exists: " + assignmentId);
                }
                if (assignment.getStatus() != dev.mars.quorus.core.JobAssignmentStatus.ASSIGNED) {
                    yield rejected("INVALID_INITIAL_STATE", "New job assignments must start in ASSIGNED state");
                }
                CommandResult<?> referenceValidation = validateAssignmentReferences(assignment);
                if (referenceValidation != null) {
                    yield referenceValidation;
                }
                TransferAttempt initialAttempt = null;
                if ((cmd.attemptId() == null) != (cmd.leaseExpiresAt() == null)) {
                    yield rejected("INVALID_ATTEMPT", "Attempt ID and lease expiry must be supplied together");
                }
                if (cmd.attemptId() != null) {
                    if (cmd.attemptId().isBlank()) {
                        yield rejected("INVALID_ATTEMPT", "Attempt ID cannot be blank");
                    }
                    if (transferAttempts.containsKey(cmd.attemptId())) {
                        yield rejected("DUPLICATE_ENTITY", "Transfer attempt already exists: " + cmd.attemptId());
                    }
                    if (activeAttemptByJob.containsKey(assignment.getJobId())) {
                        yield rejected("ACTIVE_ATTEMPT_MISMATCH",
                                "A transfer cannot receive a new assignment while an attempt is active");
                    }
                    if (!cmd.leaseExpiresAt().isAfter(cmd.timestamp())) {
                        yield rejected("INVALID_LEASE", "Attempt lease must expire after assignment creation");
                    }
                    long highestGeneration = transferAttempts.values().stream()
                            .filter(existing -> assignment.getJobId().equals(existing.getJobId()))
                            .mapToLong(TransferAttempt::getFencingGeneration)
                            .max().orElse(0);
                    int highestAttemptNumber = transferAttempts.values().stream()
                            .filter(existing -> assignment.getJobId().equals(existing.getJobId()))
                            .mapToInt(TransferAttempt::getAttemptNumber)
                            .max().orElse(0);
                    initialAttempt = new TransferAttempt.Builder()
                            .attemptId(cmd.attemptId())
                            .jobId(assignment.getJobId())
                            .agentId(assignment.getAgentId())
                            .tenantId(assignment.getTenantId())
                            .attemptNumber(highestAttemptNumber + 1)
                            .fencingGeneration(highestGeneration + 1)
                            .leaseExpiresAt(cmd.leaseExpiresAt())
                            .status(TransferAttemptStatus.OFFERED)
                            .outcome(TransferAttemptOutcome.NONE)
                            .createdAt(cmd.timestamp())
                            .updatedAt(cmd.timestamp())
                            .build();
                }
                logger.debug("Creating job assignment: assignmentId={}, jobId={}, agentId={}", 
                    assignmentId, assignment.getJobId(), assignment.getAgentId());
                jobAssignments.put(assignmentId, assignment);
                if (initialAttempt != null) {
                    transferAttempts.put(initialAttempt.getAttemptId(), initialAttempt);
                    activeAttemptByJob.put(initialAttempt.getJobId(), initialAttempt.getAttemptId());
                    appendTransferEvent(initialAttempt.getJobId(), "TRANSFER_ASSIGNED", cmd.timestamp(),
                            initialAttempt.getAttemptId(), initialAttempt.getAgentId(), null,
                            initialAttempt.getStatus().name(), null, null, null);
                }
                logger.info("Created job assignment: assignmentId={}, jobId={}, agentId={}, totalAssignments={}", 
                    assignmentId, assignment.getJobId(), assignment.getAgentId(), jobAssignments.size());
                yield new CommandResult.Success<>(assignment);
            }
            case JobAssignmentCommand.Accept cmd -> {
                logger.debug("Processing assignment accept: assignmentId={}", assignmentId);
                JobAssignment existing = jobAssignments.get(assignmentId);
                if (existing == null) {
                    logger.warn("Job assignment not found for accept: id={}", assignmentId);
                    yield new CommandResult.NotFound<>(assignmentId, "JobAssignment");
                }
                CommandResult<?> transitionValidation = validateAssignmentTransition(existing, cmd.newStatus());
                if (transitionValidation != null) {
                    yield transitionValidation;
                }
                JobAssignment updated = existing.withStatusAndTimestamp(cmd.newStatus(),
                        cmd.timestamp());
                jobAssignments.put(assignmentId, updated);
                logger.info("Job assignment accepted: assignmentId={}, jobId={}, agentId={}", 
                    assignmentId, existing.getJobId(), existing.getAgentId());
                yield new CommandResult.Success<>(updated);
            }
            case JobAssignmentCommand.Reject cmd -> {
                logger.debug("Processing assignment reject: assignmentId={}, reason={}", 
                    assignmentId, cmd.reason());
                JobAssignment rejectedAssignment = jobAssignments.get(assignmentId);
                if (rejectedAssignment == null) {
                    logger.warn("Job assignment not found for reject: id={}", assignmentId);
                    yield new CommandResult.NotFound<>(assignmentId, "JobAssignment");
                }
                CommandResult<?> transitionValidation = validateAssignmentTransition(rejectedAssignment, cmd.newStatus());
                if (transitionValidation != null) {
                    yield transitionValidation;
                }
                JobAssignment updated = rejectedAssignment.withStatusAndTimestamp(cmd.newStatus(),
                        cmd.timestamp());
                jobAssignments.put(assignmentId, updated);
                logger.info("Job assignment rejected: assignmentId={}, jobId={}, reason={}", 
                    assignmentId, rejectedAssignment.getJobId(), cmd.reason());
                yield new CommandResult.Success<>(updated);
            }
            case JobAssignmentCommand.UpdateStatus cmd -> {
                logger.debug("Updating assignment status: assignmentId={}, newStatus={}", 
                    assignmentId, cmd.newStatus());
                JobAssignment statusAssignment = jobAssignments.get(assignmentId);
                if (statusAssignment == null) {
                    logger.warn("Job assignment not found for status update: id={}", assignmentId);
                    yield new CommandResult.NotFound<>(assignmentId, "JobAssignment");
                }
                // CAS check: reject if current status doesn't match expected
                if (statusAssignment.getStatus() != cmd.expectedStatus()) {
                    logger.debug("CAS mismatch for job assignment: assignmentId={}, expected={}, actual={}",
                        assignmentId, cmd.expectedStatus(), statusAssignment.getStatus());
                    yield new CommandResult.CasMismatch<>(statusAssignment);
                }
                CommandResult<?> transitionValidation = validateAssignmentTransition(statusAssignment, cmd.newStatus());
                if (transitionValidation != null) {
                    yield transitionValidation;
                }
                JobAssignment updated = statusAssignment.withStatusAndTimestamp(cmd.newStatus(),
                        cmd.timestamp());
                jobAssignments.put(assignmentId, updated);
                logger.info("Updated job assignment status: assignmentId={}, newStatus={}", 
                    assignmentId, cmd.newStatus());
                yield new CommandResult.Success<>(updated);
            }
            case JobAssignmentCommand.Timeout cmd -> {
                logger.debug("Processing assignment timeout: assignmentId={}", assignmentId);
                JobAssignment timeoutAssignment = jobAssignments.get(assignmentId);
                if (timeoutAssignment == null) {
                    logger.warn("Job assignment not found for timeout: id={}", assignmentId);
                    yield new CommandResult.NotFound<>(assignmentId, "JobAssignment");
                }
                CommandResult<?> transitionValidation = validateAssignmentTransition(timeoutAssignment, cmd.newStatus());
                if (transitionValidation != null) {
                    yield transitionValidation;
                }
                JobAssignment updated = timeoutAssignment.withStatusAndTimestamp(cmd.newStatus(),
                        cmd.timestamp());
                jobAssignments.put(assignmentId, updated);
                logger.info("Job assignment timed out: assignmentId={}, jobId={}", 
                    assignmentId, timeoutAssignment.getJobId());
                yield new CommandResult.Success<>(updated);
            }
            case JobAssignmentCommand.Cancel cmd -> {
                logger.debug("Processing assignment cancel: assignmentId={}, reason={}", 
                    assignmentId, cmd.reason());
                JobAssignment cancelAssignment = jobAssignments.get(assignmentId);
                if (cancelAssignment == null) {
                    logger.warn("Job assignment not found for cancel: id={}", assignmentId);
                    yield new CommandResult.NotFound<>(assignmentId, "JobAssignment");
                }
                CommandResult<?> transitionValidation = validateAssignmentTransition(cancelAssignment, cmd.newStatus());
                if (transitionValidation != null) {
                    yield transitionValidation;
                }
                JobAssignment updated = cancelAssignment.withStatusAndTimestamp(cmd.newStatus(),
                        cmd.timestamp());
                jobAssignments.put(assignmentId, updated);
                logger.info("Job assignment cancelled: assignmentId={}, jobId={}, reason={}", 
                    assignmentId, cancelAssignment.getJobId(), cmd.reason());
                yield new CommandResult.Success<>(updated);
            }
            case JobAssignmentCommand.Remove remove -> {
                logger.debug("Removing job assignment: assignmentId={}", assignmentId);
                logger.trace("Job assignment remove command={}", remove);
                JobAssignment removed = jobAssignments.get(assignmentId);
                if (removed == null) {
                    logger.warn("Job assignment not found for removal: id={}", assignmentId);
                    yield new CommandResult.NotFound<>(assignmentId, "JobAssignment");
                }
                if (!removed.getStatus().isTerminal()) {
                    yield rejected("INVALID_STATE_TRANSITION", "Active job assignment cannot be removed: " + assignmentId);
                }
                jobAssignments.remove(assignmentId);
                logger.info("Removed job assignment: assignmentId={}, totalAssignments={}", 
                    assignmentId, jobAssignments.size());
                yield new CommandResult.Success<>(removed);
            }
        };
    }

    private CommandResult<?> applyJobQueueCommand(JobQueueCommand command) {
        String jobId = command.jobId();
        logger.debug("Processing job queue command: jobId={}, type={}", jobId, command.getClass().getSimpleName());

        return switch (command) {
            case JobQueueCommand.Enqueue cmd -> {
                QueuedJob queuedJob = cmd.queuedJob();
                logger.debug("Enqueueing job: jobId={}, priority={}", jobId, queuedJob.getPriority());
                jobQueue.put(jobId, queuedJob);
                logger.info("Enqueued job: jobId={}, priority={}, queueSize={}", 
                    jobId, queuedJob.getPriority(), jobQueue.size());
                yield new CommandResult.Success<>(queuedJob);
            }
            case JobQueueCommand.Dequeue dequeue -> {
                logger.debug("Dequeueing job: jobId={}", jobId);
                logger.trace("Job dequeue command timestamp={}", dequeue.timestamp());
                QueuedJob dequeuedJob = jobQueue.remove(jobId);
                if (dequeuedJob == null) {
                    logger.warn("Job not found for dequeue: id={}", jobId);
                    yield new CommandResult.NotFound<>(jobId, "QueuedJob");
                }
                logger.info("Dequeued job: jobId={}, queueSize={}", jobId, jobQueue.size());
                yield new CommandResult.Success<>(dequeuedJob);
            }
            case JobQueueCommand.Prioritize cmd -> {
                logger.debug("Updating job priority: jobId={}, newPriority={}", jobId, cmd.newPriority());
                QueuedJob existingJob = jobQueue.get(jobId);
                if (existingJob == null) {
                    logger.warn("Job not found for prioritize: id={}", jobId);
                    yield new CommandResult.NotFound<>(jobId, "QueuedJob");
                }
                JobPriority oldPriority = existingJob.getPriority();
                QueuedJob updatedJob = existingJob.withPriority(cmd.newPriority());
                jobQueue.put(jobId, updatedJob);
                logger.info("Updated job priority: jobId={}, oldPriority={}, newPriority={}, reason={}", 
                    jobId, oldPriority, cmd.newPriority(), cmd.reason());
                yield new CommandResult.Success<>(updatedJob);
            }
            case JobQueueCommand.Remove cmd -> {
                logger.debug("Removing job from queue: jobId={}", jobId);
                QueuedJob removedJob = jobQueue.remove(jobId);
                if (removedJob == null) {
                    logger.warn("Job not found for removal: id={}", jobId);
                    yield new CommandResult.NotFound<>(jobId, "QueuedJob");
                }
                logger.info("Removed job from queue: jobId={}, reason={}, queueSize={}", 
                    jobId, cmd.reason(), jobQueue.size());
                yield new CommandResult.Success<>(removedJob);
            }
            case JobQueueCommand.Expedite cmd -> {
                logger.debug("Expediting job: jobId={}", jobId);
                QueuedJob expediteJob = jobQueue.get(jobId);
                if (expediteJob == null) {
                    logger.warn("Job not found for expedite: id={}", jobId);
                    yield new CommandResult.NotFound<>(jobId, "QueuedJob");
                }
                QueuedJob expedited = expediteJob.withPriority(JobPriority.CRITICAL);
                jobQueue.put(jobId, expedited);
                logger.info("Expedited job: jobId={}, oldPriority={}, newPriority=CRITICAL, reason={}",
                    jobId, expediteJob.getPriority(), cmd.reason());
                yield new CommandResult.Success<>(expedited);
            }
            case JobQueueCommand.UpdateRequirements cmd -> {
                logger.debug("Updating job requirements: jobId={}", jobId);
                QueuedJob updatedJob = cmd.queuedJob();
                jobQueue.put(jobId, updatedJob);
                logger.info("Updated job requirements: jobId={}", jobId);
                yield new CommandResult.Success<>(updatedJob);
            }
        };
    }

    private CommandResult<?> applyRouteCommand(RouteCommand command) {
        String routeId = command.routeId();
        logger.debug("Processing route command: routeId={}, type={}", routeId, command.getClass().getSimpleName());

        return switch (command) {
            case RouteCommand.Create cmd -> {
                RouteConfiguration routeConfig = cmd.routeConfiguration();
                if (!routeId.equals(routeConfig.getRouteId())) {
                    yield rejected("IDENTIFIER_MISMATCH", "Route command ID does not match the route payload ID");
                }
                if (routes.containsKey(routeId)) {
                    yield rejected("DUPLICATE_ENTITY", "Route already exists: " + routeId);
                }
                logger.debug("Creating route: routeId={}, name={}", routeId, routeConfig.getName());
                routes.put(routeId, routeConfig);
                logger.info("Created route: routeId={}, name={}, triggerType={}, totalRoutes={}",
                    routeId, routeConfig.getName(),
                    routeConfig.getTrigger() != null ? routeConfig.getTrigger().getType() : "none",
                    routes.size());
                yield new CommandResult.Success<>(routeConfig);
            }
            case RouteCommand.Update cmd -> {
                logger.debug("Updating route: routeId={}", routeId);
                RouteConfiguration existingRoute = routes.get(routeId);
                if (existingRoute == null) {
                    logger.warn("Route not found for update: id={}", routeId);
                    yield new CommandResult.NotFound<>(routeId, "Route");
                }
                if (!routeId.equals(cmd.routeConfiguration().getRouteId())) {
                    yield rejected("IDENTIFIER_MISMATCH", "Route update ID does not match the route payload ID");
                }
                RouteConfiguration updatedRoute = existingRoute.withUpdate(cmd.routeConfiguration());
                routes.put(routeId, updatedRoute);
                logger.info("Updated route: routeId={}, name={}", routeId, updatedRoute.getName());
                yield new CommandResult.Success<>(updatedRoute);
            }
            case RouteCommand.Delete delete -> {
                logger.debug("Deleting route: routeId={}", routeId);
                logger.trace("Route delete command timestamp={}", delete.timestamp());
                RouteConfiguration removedRoute = routes.remove(routeId);
                if (removedRoute == null) {
                    logger.warn("Route not found for deletion: id={}", routeId);
                    yield new CommandResult.NotFound<>(routeId, "Route");
                }
                logger.info("Deleted route: routeId={}, totalRoutes={}", routeId, routes.size());
                yield new CommandResult.Success<>(removedRoute);
            }
            case RouteCommand.Suspend cmd -> {
                logger.debug("Suspending route: routeId={}, reason={}", routeId, cmd.reason());
                RouteConfiguration suspendRoute = routes.get(routeId);
                if (suspendRoute == null) {
                    logger.warn("Route not found for suspend: id={}", routeId);
                    yield new CommandResult.NotFound<>(routeId, "Route");
                }
                if (!suspendRoute.getStatus().canTransitionTo(RouteStatus.SUSPENDED)) {
                    yield rejected("INVALID_STATE_TRANSITION", "Route " + routeId + " cannot be suspended from "
                            + suspendRoute.getStatus());
                }
                RouteStatus oldStatus = suspendRoute.getStatus();
                RouteConfiguration suspended = suspendRoute.withStatus(RouteStatus.SUSPENDED);
                routes.put(routeId, suspended);
                logger.info("Suspended route: routeId={}, oldStatus={}, reason={}",
                    routeId, oldStatus, cmd.reason());
                yield new CommandResult.Success<>(suspended);
            }
            case RouteCommand.Resume resume -> {
                logger.debug("Resuming route: routeId={}", routeId);
                logger.trace("Route resume command={}", resume);
                RouteConfiguration resumeRoute = routes.get(routeId);
                if (resumeRoute == null) {
                    logger.warn("Route not found for resume: id={}", routeId);
                    yield new CommandResult.NotFound<>(routeId, "Route");
                }
                if (resumeRoute.getStatus() != RouteStatus.SUSPENDED) {
                    yield rejected("INVALID_STATE_TRANSITION", "Route " + routeId + " cannot be resumed from "
                            + resumeRoute.getStatus());
                }
                RouteStatus oldStatus = resumeRoute.getStatus();
                RouteConfiguration resumed = resumeRoute.withStatus(RouteStatus.ACTIVE);
                routes.put(routeId, resumed);
                logger.info("Resumed route: routeId={}, oldStatus={}", routeId, oldStatus);
                yield new CommandResult.Success<>(resumed);
            }
            case RouteCommand.UpdateStatus cmd -> {
                logger.debug("Updating route status: routeId={}, newStatus={}", routeId, cmd.newStatus());
                RouteConfiguration statusRoute = routes.get(routeId);
                if (statusRoute == null) {
                    logger.warn("Route not found for status update: id={}", routeId);
                    yield new CommandResult.NotFound<>(routeId, "Route");
                }
                // CAS check: reject if current status doesn't match expected
                if (statusRoute.getStatus() != cmd.expectedStatus()) {
                    logger.debug("CAS mismatch for route: routeId={}, expected={}, actual={}",
                        routeId, cmd.expectedStatus(), statusRoute.getStatus());
                    yield new CommandResult.CasMismatch<>(statusRoute);
                }
                if (!statusRoute.getStatus().canTransitionTo(cmd.newStatus())) {
                    yield rejected("INVALID_STATE_TRANSITION", "Route " + routeId + " cannot transition from "
                            + statusRoute.getStatus() + " to " + cmd.newStatus());
                }
                RouteStatus oldStatus = statusRoute.getStatus();
                RouteConfiguration updated = statusRoute.withStatus(cmd.newStatus());
                routes.put(routeId, updated);
                logger.info("Updated route status: routeId={}, oldStatus={}, newStatus={}",
                    routeId, oldStatus, cmd.newStatus());
                yield new CommandResult.Success<>(updated);
            }
        };
    }

    private CommandResult<?> applyTransferAttemptCommand(TransferAttemptCommand command) {
        return switch (command) {
            case TransferAttemptCommand.Offer offer -> applyAttemptOffer(offer);
            case TransferAttemptCommand.Report report -> applyAttemptReport(report);
            case TransferAttemptCommand.LifecycleReport report -> applyAttemptLifecycleReport(report);
            case TransferAttemptCommand.RenewLease renew -> applyAttemptLeaseRenewal(renew);
        };
    }

    private CommandResult<?> applyAttemptOffer(TransferAttemptCommand.Offer command) {
        TransferAttempt attempt = command.attempt();
        if (!command.attemptId().equals(attempt.getAttemptId())) {
            return rejected("IDENTIFIER_MISMATCH", "Attempt command ID does not match attempt ID");
        }
        if (transferAttempts.containsKey(command.attemptId())) {
            return rejected("DUPLICATE_ENTITY", "Transfer attempt already exists: " + command.attemptId());
        }
        if (attempt.getStatus() != TransferAttemptStatus.OFFERED
                || attempt.getOutcome() != TransferAttemptOutcome.NONE
                || attempt.getLastReportSequence() != 0) {
            return rejected("INVALID_INITIAL_STATE",
                    "New transfer attempts must start in OFFERED with no outcome or reports");
        }
        TransferJobSnapshot job = transferJobs.get(attempt.getJobId());
        if (job == null) {
            return new CommandResult.NotFound<>(attempt.getJobId(), "TransferJob");
        }
        AgentInfo agent = agents.get(attempt.getAgentId());
        if (agent == null) {
            return new CommandResult.NotFound<>(attempt.getAgentId(), "Agent");
        }
        if (isBlank(attempt.getTenantId()) || !attempt.getTenantId().equals(job.getTenantId())
                || !attempt.getTenantId().equals(agent.getTenantId())) {
            return rejected("TENANT_MISMATCH", "Job, agent, and attempt tenant IDs must match");
        }
        if (!attempt.getLeaseExpiresAt().isAfter(command.timestamp())) {
            return rejected("INVALID_LEASE", "Attempt lease must expire after the offer timestamp");
        }

        String currentId = activeAttemptByJob.get(attempt.getJobId());
        if (!Objects.equals(currentId, command.expectedActiveAttemptId())) {
            return rejected("ACTIVE_ATTEMPT_MISMATCH", "Active attempt changed before offer application");
        }
        TransferAttempt current = currentId == null ? null : transferAttempts.get(currentId);
        long highestGeneration = transferAttempts.values().stream()
                .filter(existing -> attempt.getJobId().equals(existing.getJobId()))
                .mapToLong(TransferAttempt::getFencingGeneration)
                .max().orElse(0);
        int highestAttemptNumber = transferAttempts.values().stream()
                .filter(existing -> attempt.getJobId().equals(existing.getJobId()))
                .mapToInt(TransferAttempt::getAttemptNumber)
                .max().orElse(0);
        if (attempt.getFencingGeneration() != highestGeneration + 1
                || attempt.getAttemptNumber() != highestAttemptNumber + 1) {
            return rejected("INVALID_ATTEMPT_SEQUENCE",
                    "Attempt number and fencing generation must advance exactly once");
        }

        if (current != null) {
            transferAttempts.put(current.getAttemptId(), current.fenced(command.timestamp()));
        }
        transferAttempts.put(attempt.getAttemptId(), attempt);
        activeAttemptByJob.put(attempt.getJobId(), attempt.getAttemptId());
        return new CommandResult.Success<>(attempt);
    }

    private CommandResult<?> applyAttemptReport(TransferAttemptCommand.Report command) {
        TransferAttempt attempt = transferAttempts.get(command.attemptId());
        if (attempt == null) {
            return new CommandResult.NotFound<>(command.attemptId(), "TransferAttempt");
        }
        if (command.fencingGeneration() == attempt.getFencingGeneration()
                && command.reportSequence() == attempt.getLastReportSequence()
                && command.newStatus() == attempt.getStatus()
                && command.bytesTransferred() == attempt.getBytesTransferred()
                && command.outcome() == attempt.getOutcome()
                && Objects.equals(command.reason(), attempt.getOutcomeReason())) {
            return new CommandResult.Success<>(attempt);
        }
        CommandResult<?> fenceValidation = validateCurrentAttemptFence(attempt, command.fencingGeneration());
        if (fenceValidation != null) {
            return fenceValidation;
        }
        if (!command.timestamp().isBefore(attempt.getLeaseExpiresAt())) {
            return rejected("LEASE_EXPIRED", "Attempt lease expired before the report was received");
        }
        CommandResult<?> sequenceValidation = validateNextReportSequence(attempt, command.reportSequence());
        if (sequenceValidation != null) {
            return sequenceValidation;
        }
        if (attempt.getStatus() != command.expectedStatus()) {
            return new CommandResult.CasMismatch<>(attempt);
        }
        if (!attempt.getStatus().canTransitionTo(command.newStatus())) {
            return rejected("INVALID_STATE_TRANSITION", "Attempt cannot transition from "
                    + attempt.getStatus() + " to " + command.newStatus());
        }
        if (command.bytesTransferred() < attempt.getBytesTransferred()) {
            return rejected("PROGRESS_REGRESSION", "Attempt progress cannot decrease");
        }
        if (!validOutcome(command.newStatus(), command.outcome())) {
            return rejected("INVALID_ATTEMPT_OUTCOME", "Attempt outcome does not match target status");
        }

        TransferAttempt updated = attempt.withReport(command.reportSequence(), command.newStatus(),
                command.bytesTransferred(), command.outcome(), command.reason(), command.timestamp());
        transferAttempts.put(updated.getAttemptId(), updated);
        if (updated.getStatus().isTerminal()) {
            activeAttemptByJob.remove(updated.getJobId(), updated.getAttemptId());
        }
        return new CommandResult.Success<>(updated);
    }

    private CommandResult<?> applyAttemptLifecycleReport(TransferAttemptCommand.LifecycleReport command) {
        TransferAttempt attempt = transferAttempts.get(command.attemptId());
        if (attempt == null) {
            return new CommandResult.NotFound<>(command.attemptId(), "TransferAttempt");
        }
        JobAssignment assignment = jobAssignments.get(command.assignmentId());
        if (assignment == null) {
            return new CommandResult.NotFound<>(command.assignmentId(), "JobAssignment");
        }
        TransferJobSnapshot job = transferJobs.get(command.jobId());
        if (job == null) {
            return new CommandResult.NotFound<>(command.jobId(), "TransferJob");
        }
        if (!command.jobId().equals(attempt.getJobId())
                || !command.jobId().equals(assignment.getJobId())
                || !attempt.getAgentId().equals(assignment.getAgentId())
                || !attempt.getTenantId().equals(assignment.getTenantId())
                || !attempt.getTenantId().equals(job.getTenantId())) {
            return rejected("LIFECYCLE_REFERENCE_MISMATCH",
                    "Attempt, assignment, transfer, agent, and tenant references must match");
        }

        boolean exactAttemptRetry = command.fencingGeneration() == attempt.getFencingGeneration()
                && command.reportSequence() == attempt.getLastReportSequence()
                && command.newStatus() == attempt.getStatus()
                && command.bytesTransferred() == attempt.getBytesTransferred()
                && command.outcome() == attempt.getOutcome()
                && Objects.equals(command.reason(), attempt.getOutcomeReason());
        if (exactAttemptRetry) {
            boolean assignmentMatches = assignment.getStatus() == command.newAssignmentStatus();
            boolean transferMatches = command.newTransferStatus() == null
                    || job.getStatus() == command.newTransferStatus();
            boolean progressMatches = command.bytesTransferred() == 0
                    || job.getBytesTransferred() == command.bytesTransferred();
            if (assignmentMatches && transferMatches && progressMatches) {
                return new CommandResult.Success<>(attempt);
            }
            return rejected("INCONSISTENT_LIFECYCLE_RETRY",
                    "Attempt report was applied without the matching assignment and transfer state");
        }

        CommandResult<?> fenceValidation = validateCurrentAttemptFence(attempt, command.fencingGeneration());
        if (fenceValidation != null) {
            return fenceValidation;
        }
        if (!command.timestamp().isBefore(attempt.getLeaseExpiresAt())) {
            return rejected("LEASE_EXPIRED", "Attempt lease expired before the report was received");
        }
        CommandResult<?> sequenceValidation = validateNextReportSequence(attempt, command.reportSequence());
        if (sequenceValidation != null) {
            return sequenceValidation;
        }
        if (attempt.getStatus() != command.expectedStatus()
                || assignment.getStatus() != command.expectedAssignmentStatus()
                || (command.expectedTransferStatus() != null
                    && job.getStatus() != command.expectedTransferStatus())) {
            return new CommandResult.CasMismatch<>(attempt);
        }
        if (!attempt.getStatus().canTransitionTo(command.newStatus())
                || !assignment.getStatus().canTransitionTo(command.newAssignmentStatus())
                || (command.newTransferStatus() != null
                    && !job.getStatus().canTransitionTo(command.newTransferStatus()))) {
            return rejected("INVALID_STATE_TRANSITION",
                    "Attempt, assignment, and transfer lifecycle transition must all be valid");
        }
        if (command.bytesTransferred() < attempt.getBytesTransferred()
                || command.bytesTransferred() < job.getBytesTransferred()
                || command.bytesTransferred() < 0
                || (job.getTotalBytes() > 0 && command.bytesTransferred() > job.getTotalBytes())) {
            return rejected("PROGRESS_REGRESSION", "Lifecycle progress is outside the valid byte range");
        }
        if (!validOutcome(command.newStatus(), command.outcome())) {
            return rejected("INVALID_ATTEMPT_OUTCOME", "Attempt outcome does not match target status");
        }

        TransferAttempt updatedAttempt = attempt.withReport(command.reportSequence(), command.newStatus(),
                command.bytesTransferred(), command.outcome(), command.reason(), command.timestamp());
        JobAssignment updatedAssignment = assignment.withStatusAndTimestamp(
                command.newAssignmentStatus(), command.timestamp());
        TransferStatus updatedTransferStatus = command.newTransferStatus() == null
                ? job.getStatus() : command.newTransferStatus();
        TransferJobSnapshot updatedJob = new TransferJobSnapshot(
                job.getJobId(), job.getSourceUri(), job.getDestinationPath(), updatedTransferStatus,
                command.bytesTransferred(), job.getTotalBytes(), job.getStartTime(), command.timestamp(),
                command.reason(), job.getDescription(), job.getTenantId(), job.getOperationalContext(),
                command.bytesTransferred() > job.getBytesTransferred()
                        ? command.timestamp() : job.getLastProgressAt(),
                job.getServiceConnectionId(), job.getRemotePath(), job.getConnectionPolicyVersion(),
                job.getConnectionPolicyDigest(), job.getAgentPool(), job.getControllerResolvedAddresses());

        transferAttempts.put(updatedAttempt.getAttemptId(), updatedAttempt);
        jobAssignments.put(command.assignmentId(), updatedAssignment);
        transferJobs.put(command.jobId(), updatedJob);
        if (updatedAttempt.getStatus() == TransferAttemptStatus.ACCEPTED) {
            appendTransferEvent(command.jobId(), "TRANSFER_ACCEPTED", command.timestamp(),
                    updatedAttempt.getAttemptId(), updatedAttempt.getAgentId(),
                    attempt.getStatus().name(), updatedAttempt.getStatus().name(),
                    command.bytesTransferred(), job.getTotalBytes(), command.reportSequence());
        }
        if (updatedAttempt.getStatus() == TransferAttemptStatus.IN_PROGRESS) {
            if (attempt.getStatus() != TransferAttemptStatus.IN_PROGRESS) {
                appendTransferEvent(command.jobId(), "TRANSFER_STARTED", command.timestamp(),
                        updatedAttempt.getAttemptId(), updatedAttempt.getAgentId(),
                        job.getStatus().name(), updatedJob.getStatus().name(),
                        command.bytesTransferred(), job.getTotalBytes(), command.reportSequence());
            }
            appendTransferEvent(command.jobId(), "TRANSFER_PROGRESS", command.timestamp(),
                    updatedAttempt.getAttemptId(), updatedAttempt.getAgentId(),
                    updatedAttempt.getStatus().name(), updatedAttempt.getStatus().name(),
                    command.bytesTransferred(), job.getTotalBytes(), command.reportSequence());
        }
        if (updatedAttempt.getStatus().isTerminal()) {
            activeAttemptByJob.remove(updatedAttempt.getJobId(), updatedAttempt.getAttemptId());
        }
        return new CommandResult.Success<>(updatedAttempt);
    }

    private CommandResult<?> applyAttemptLeaseRenewal(TransferAttemptCommand.RenewLease command) {
        TransferAttempt attempt = transferAttempts.get(command.attemptId());
        if (attempt == null) {
            return new CommandResult.NotFound<>(command.attemptId(), "TransferAttempt");
        }
        CommandResult<?> fenceValidation = validateCurrentAttemptFence(attempt, command.fencingGeneration());
        if (fenceValidation != null) {
            return fenceValidation;
        }
        if (!command.timestamp().isBefore(attempt.getLeaseExpiresAt())) {
            return rejected("LEASE_EXPIRED", "An expired attempt lease cannot be renewed");
        }
        if (command.reportSequence() == attempt.getLastReportSequence()
                && command.leaseExpiresAt().equals(attempt.getLeaseExpiresAt())) {
            return new CommandResult.Success<>(attempt);
        }
        CommandResult<?> sequenceValidation = validateNextReportSequence(attempt, command.reportSequence());
        if (sequenceValidation != null) {
            return sequenceValidation;
        }
        if (!command.leaseExpiresAt().isAfter(command.timestamp())
                || !command.leaseExpiresAt().isAfter(attempt.getLeaseExpiresAt())) {
            return rejected("LEASE_NOT_EXTENDED", "Renewed lease must extend the current future expiry");
        }
        TransferAttempt updated = attempt.withLease(command.reportSequence(), command.leaseExpiresAt(),
                command.timestamp());
        transferAttempts.put(updated.getAttemptId(), updated);
        return new CommandResult.Success<>(updated);
    }

    private CommandResult<?> validateCurrentAttemptFence(TransferAttempt attempt, long fencingGeneration) {
        String activeId = activeAttemptByJob.get(attempt.getJobId());
        if (!attempt.getAttemptId().equals(activeId)
                || attempt.getFencingGeneration() != fencingGeneration) {
            return rejected("STALE_FENCE", "Attempt is no longer the active fencing generation");
        }
        return null;
    }

    private CommandResult<?> validateNextReportSequence(TransferAttempt attempt, long reportSequence) {
        if (reportSequence <= attempt.getLastReportSequence()) {
            return rejected("STALE_REPORT_SEQUENCE", "Attempt report sequence has already been applied");
        }
        if (reportSequence != attempt.getLastReportSequence() + 1) {
            return rejected("REPORT_SEQUENCE_GAP", "Attempt report sequence must advance exactly once");
        }
        return null;
    }

    private static boolean validOutcome(TransferAttemptStatus status, TransferAttemptOutcome outcome) {
        return switch (status) {
            case OFFERED, ACCEPTED, IN_PROGRESS -> outcome == TransferAttemptOutcome.NONE;
            case COMPLETED -> outcome == TransferAttemptOutcome.SUCCEEDED;
            case FAILED -> outcome == TransferAttemptOutcome.RETRYABLE_FAILURE
                    || outcome == TransferAttemptOutcome.TERMINAL_FAILURE;
            case REJECTED -> outcome == TransferAttemptOutcome.REJECTED;
            case CANCELLED -> outcome == TransferAttemptOutcome.CANCELLED;
            case EXPIRED -> outcome == TransferAttemptOutcome.LEASE_EXPIRED;
            case FENCED -> outcome == TransferAttemptOutcome.SUPERSEDED;
            case RECONCILIATION_REQUIRED ->
                    outcome == TransferAttemptOutcome.RECONCILIATION_REQUIRED;
        };
    }

    private CommandResult<?> validateAssignmentReferences(JobAssignment assignment) {
        TransferJobSnapshot job = transferJobs.get(assignment.getJobId());
        if (job == null) {
            return new CommandResult.NotFound<>(assignment.getJobId(), "TransferJob");
        }
        AgentInfo agent = agents.get(assignment.getAgentId());
        if (agent == null) {
            return new CommandResult.NotFound<>(assignment.getAgentId(), "Agent");
        }
        String assignmentTenant = assignment.getTenantId();
        String jobTenant = job.getTenantId();
        String agentTenant = agent.getTenantId();
        if (isBlank(assignmentTenant) || isBlank(jobTenant) || isBlank(agentTenant)) {
            return rejected("TENANT_REQUIRED", "Job, agent, and assignment must all have a tenant ID");
        }
        if (!assignmentTenant.equals(jobTenant) || !assignmentTenant.equals(agentTenant)) {
            return rejected("TENANT_MISMATCH", "Job, agent, and assignment tenant IDs must match");
        }
        return null;
    }

    private CommandResult<?> validateAssignmentTransition(
            JobAssignment assignment, dev.mars.quorus.core.JobAssignmentStatus newStatus) {
        CommandResult<?> referenceValidation = validateAssignmentReferences(assignment);
        if (referenceValidation != null) {
            return referenceValidation;
        }
        if (!assignment.getStatus().canTransitionTo(newStatus)) {
            return rejected("INVALID_STATE_TRANSITION", "Assignment "
                    + assignment.getJobId() + ":" + assignment.getAgentId() + " cannot transition from "
                    + assignment.getStatus() + " to " + newStatus);
        }
        return null;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static <T> CommandResult.Rejected<T> rejected(String code, String message) {
        return new CommandResult.Rejected<>(code, message);
    }

    @Override
    public byte[] takeSnapshot() {
        logger.debug("Taking state machine snapshot: jobs={}, agents={}, metadata={}, assignments={}, queue={}", 
            transferJobs.size(), agents.size(), systemMetadata.size(), jobAssignments.size(), jobQueue.size());
        try {
            QuorusSnapshot snapshot = new QuorusSnapshot();
            snapshot.setTransferJobs(new ConcurrentHashMap<>(transferJobs));
            snapshot.setAgents(new ConcurrentHashMap<>(agents));
            snapshot.setSystemMetadata(new ConcurrentHashMap<>(systemMetadata));
            snapshot.setJobAssignments(new ConcurrentHashMap<>(jobAssignments));
            snapshot.setJobQueue(new ConcurrentHashMap<>(jobQueue));
            snapshot.setRoutes(new ConcurrentHashMap<>(routes));
            snapshot.setTransferAttempts(new ConcurrentHashMap<>(transferAttempts));
            snapshot.setActiveAttemptByJob(new ConcurrentHashMap<>(activeAttemptByJob));
            snapshot.setTransferEvents(new ConcurrentHashMap<>(transferEvents));
            snapshot.setLastAppliedIndex(lastAppliedIndex.get());

            byte[] data = objectMapper.writeValueAsBytes(snapshot);
            logger.info("Created snapshot: size={}bytes, jobs={}, agents={}, assignments={}, queue={}, routes={}, lastAppliedIndex={}", 
                data.length, transferJobs.size(), agents.size(), jobAssignments.size(), jobQueue.size(), routes.size(), lastAppliedIndex.get());
            return data;
        } catch (IOException e) {
            logger.error("Failed to create snapshot: jobs={}, agents={}, message={}", transferJobs.size(), agents.size(), e.getMessage());
            logger.debug("Stack trace for state store snapshot creation failure", e);
            throw new RuntimeException("Failed to create snapshot", e);
        }
    }

    @Override
    public void restoreSnapshot(byte[] snapshot) {
        logger.debug("Restoring state machine snapshot: snapshotSize={}bytes", snapshot.length);
        try {
            QuorusSnapshot restoredSnapshot = objectMapper.readValue(snapshot, QuorusSnapshot.class);
            SchemaVersionRegistry.requireReadable(
                    SchemaVersionRegistry.Contract.STATE_SNAPSHOT,
                    restoredSnapshot.getSchemaVersion());

            transferJobs.clear();
            Optional.ofNullable(restoredSnapshot.getTransferJobs()).ifPresent(transferJobs::putAll);

            agents.clear();
            Optional.ofNullable(restoredSnapshot.getAgents()).ifPresent(agents::putAll);

            systemMetadata.clear();
            Optional.ofNullable(restoredSnapshot.getSystemMetadata()).ifPresent(systemMetadata::putAll);

            jobAssignments.clear();
            Optional.ofNullable(restoredSnapshot.getJobAssignments()).ifPresent(jobAssignments::putAll);

            jobQueue.clear();
            Optional.ofNullable(restoredSnapshot.getJobQueue()).ifPresent(jobQueue::putAll);

            routes.clear();
            Optional.ofNullable(restoredSnapshot.getRoutes()).ifPresent(routes::putAll);

            transferAttempts.clear();
            Optional.ofNullable(restoredSnapshot.getTransferAttempts()).ifPresent(transferAttempts::putAll);

            activeAttemptByJob.clear();
            Optional.ofNullable(restoredSnapshot.getActiveAttemptByJob()).ifPresent(activeAttemptByJob::putAll);

            transferEvents.clear();
            Optional.ofNullable(restoredSnapshot.getTransferEvents()).ifPresent(transferEvents::putAll);

            lastAppliedIndex.set(restoredSnapshot.getLastAppliedIndex());

            logger.info("Restored snapshot: jobs={}, agents={}, metadata={}, assignments={}, queue={}, routes={}, lastAppliedIndex={}", 
                transferJobs.size(), agents.size(), systemMetadata.size(), 
                jobAssignments.size(), jobQueue.size(), routes.size(), lastAppliedIndex.get());
        } catch (IOException e) {
            logger.error("Failed to restore snapshot: snapshotSize={}bytes, error={}", snapshot.length, e.getMessage());
            logger.debug("Stack trace for snapshot restore failure", e);
            throw new RuntimeException("Failed to restore snapshot", e);
        }
    }

    @Override
    public long getLastAppliedIndex() {
        return lastAppliedIndex.get();
    }

    @Override
    public void reset() {
        logger.debug("Resetting state machine: jobs={}, agents={}, metadata={}", 
            transferJobs.size(), agents.size(), systemMetadata.size());
        transferJobs.clear();
        agents.clear();
        systemMetadata.clear();
        jobAssignments.clear();
        jobQueue.clear();
        routes.clear();
        transferAttempts.clear();
        activeAttemptByJob.clear();
        transferEvents.clear();
        lastAppliedIndex.set(0);
        
        // Restore default metadata after clearing
        initializeDefaultMetadata();
        
        logger.info("State machine reset completed: version={}", systemMetadata.get("version"));
    }

    public Map<String, TransferJobSnapshot> getTransferJobs() {
        return new ConcurrentHashMap<>(transferJobs);
    }

    public TransferJobSnapshot getTransferJob(String jobId) {
        return transferJobs.get(jobId);
    }

    public Map<String, AgentInfo> getAgents() {
        return new ConcurrentHashMap<>(agents);
    }

    public AgentInfo getAgent(String agentId) {
        return agents.get(agentId);
    }

    public int getAgentCount() {
        return agents.size();
    }

    public Map<String, String> getSystemMetadata() {
        return new ConcurrentHashMap<>(systemMetadata);
    }

    /** Read-only view pinned to one migration generation, without copying the full event registry. */
    Map<String, String> registryMetadataView() {
        return java.util.Collections.unmodifiableMap(systemMetadata);
    }

    public String getMetadata(String key) {
        return systemMetadata.get(key);
    }

    public int getTransferJobCount() {
        return transferJobs.size();
    }

    /**
     * Check if a transfer job exists in the state machine.
     */
    public boolean hasTransferJob(String jobId) {
        return transferJobs.containsKey(jobId);
    }

    /**
     * Update the last applied index.
     */
    public void setLastAppliedIndex(long index) {
        lastAppliedIndex.set(index);
    }

    /**
     * Get all job assignments.
     */
    public Map<String, JobAssignment> getJobAssignments() {
        return new ConcurrentHashMap<>(jobAssignments);
    }

    /**
     * Get a specific job assignment.
     */
    public JobAssignment getJobAssignment(String assignmentId) {
        return jobAssignments.get(assignmentId);
    }

    /**
     * Get all queued jobs.
     */
    public Map<String, QueuedJob> getJobQueue() {
        return new ConcurrentHashMap<>(jobQueue);
    }

    /**
     * Get a specific queued job.
     */
    public QueuedJob getQueuedJob(String jobId) {
        return jobQueue.get(jobId);
    }

    /**
     * Get all routes.
     */
    public Map<String, RouteConfiguration> getRoutes() {
        return new ConcurrentHashMap<>(routes);
    }

    /**
     * Get a specific route.
     */
    public RouteConfiguration getRoute(String routeId) {
        return routes.get(routeId);
    }

    /**
     * Check if a route exists in the state machine.
     */
    public boolean hasRoute(String routeId) {
        return routes.containsKey(routeId);
    }

    /**
     * Get the total number of routes.
     */
    public int getRouteCount() {
        return routes.size();
    }

    public Map<String, TransferAttempt> getTransferAttempts() {
        return Map.copyOf(transferAttempts);
    }

    public Optional<TransferAttempt> findTransferAttempt(String attemptId) {
        return Optional.ofNullable(transferAttempts.get(attemptId));
    }

    public Optional<TransferAttempt> findActiveTransferAttempt(String jobId) {
        return Optional.ofNullable(activeAttemptByJob.get(jobId)).map(transferAttempts::get);
    }

    // ── Optional query methods ─────────────────────────────────────────────

    /**
     * Find a transfer job by ID.
     *
     * @param jobId the transfer job identifier
     * @return an Optional containing the snapshot, or empty if not found
     */
    public Optional<TransferJobSnapshot> findTransferJob(String jobId) {
        return Optional.ofNullable(transferJobs.get(jobId));
    }

    public List<TransferEvent> findTransferEvents(String jobId) {
        return List.copyOf(transferEvents.getOrDefault(jobId, List.of()));
    }

    private void appendTransferEvent(String jobId, String eventType, Instant occurredAt,
                                     String attemptId, String agentId,
                                     String previousState, String currentState,
                                     Long bytesTransferred, Long totalBytes, Long reportSequence) {
        TransferJobSnapshot job = transferJobs.get(jobId);
        List<TransferEvent> existing = transferEvents.getOrDefault(jobId, List.of());
        long sequence = existing.isEmpty() ? 1 : existing.getLast().sequence() + 1;
        String businessService = job == null || job.getOperationalContext() == null
                ? null : job.getOperationalContext().businessService();
        String tenantId = job == null ? null : job.getTenantId();
        List<TransferEvent> updated = new ArrayList<>(existing);
        updated.add(new TransferEvent(jobId + ":" + sequence, sequence, eventType,
                occurredAt, occurredAt, jobId, tenantId, businessService, attemptId, agentId,
                bytesTransferred, totalBytes, reportSequence, previousState, currentState));
        transferEvents.put(jobId, List.copyOf(updated));
    }

    /**
     * Find an agent by ID.
     *
     * @param agentId the agent identifier
     * @return an Optional containing the agent info, or empty if not found
     */
    public Optional<AgentInfo> findAgent(String agentId) {
        return Optional.ofNullable(agents.get(agentId));
    }

    /**
     * Find a job assignment by ID.
     *
     * @param assignmentId the assignment identifier
     * @return an Optional containing the assignment, or empty if not found
     */
    public Optional<JobAssignment> findJobAssignment(String assignmentId) {
        return Optional.ofNullable(jobAssignments.get(assignmentId));
    }

    /**
     * Find a route configuration by ID.
     *
     * @param routeId the route identifier
     * @return an Optional containing the route, or empty if not found
     */
    public Optional<RouteConfiguration> findRoute(String routeId) {
        return Optional.ofNullable(routes.get(routeId));
    }

    /**
     * Find a queued job by ID.
     *
     * @param jobId the queued job identifier
     * @return an Optional containing the queued job, or empty if not found
     */
    public Optional<QueuedJob> findQueuedJob(String jobId) {
        return Optional.ofNullable(jobQueue.get(jobId));
    }

    /**
     * Find a system metadata value by key.
     *
     * @param key the metadata key
     * @return an Optional containing the value, or empty if not found
     */
    public Optional<String> findMetadata(String key) {
        return Optional.ofNullable(systemMetadata.get(key));
    }
}
