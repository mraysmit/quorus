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
import dev.mars.quorus.core.JobAssignment;
import dev.mars.quorus.core.QueuedJob;
import dev.mars.quorus.core.RouteConfiguration;
import dev.mars.quorus.core.RouteStatus;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.metrics.Meter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;
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
    private final Map<String, String> systemMetadata = new ConcurrentHashMap<>();
    private final Map<String, JobAssignment> jobAssignments = new ConcurrentHashMap<>();
    private final Map<String, QueuedJob> jobQueue = new ConcurrentHashMap<>();
    private final Map<String, RouteConfiguration> routes = new ConcurrentHashMap<>();
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
            };
            logger.debug("Command applied successfully: type={}, result={}", 
                command.getClass().getSimpleName(), result.getClass().getSimpleName());
            return result;
        } catch (Exception e) {
            logger.error("Failed to apply command: type={}", command.getClass().getSimpleName(), e);
            throw new RuntimeException("Failed to apply command", e);
        }
    }

    private CommandResult<?> applyTransferJobCommand(TransferJobCommand command) {
        String jobId = command.jobId();

        return switch (command) {
            case TransferJobCommand.Create cmd -> {
                TransferJob job = cmd.transferJob();
                logger.debug("Creating transfer job: jobId={}, sourceUri={}, destPath={}", 
                    jobId, job.getRequest().getSourceUri(), job.getRequest().getDestinationPath());
                TransferJobSnapshot snapshot = TransferJobSnapshot.fromTransferJob(job);
                transferJobs.put(jobId, snapshot);
                logger.info("Created transfer job: jobId={}, protocol={}, totalJobs={}", 
                    jobId, job.getRequest().getProtocol(), transferJobs.size());
                yield new CommandResult.Success<>(job);
            }
            case TransferJobCommand.UpdateStatus cmd -> {
                logger.debug("Updating transfer job status: jobId={}, newStatus={}", jobId, cmd.status());
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
                TransferStatus oldStatus = existingJob.getStatus();
                TransferJobSnapshot updatedJob = new TransferJobSnapshot(
                        existingJob.getJobId(),
                        existingJob.getSourceUri(),
                        existingJob.getDestinationPath(),
                        cmd.newStatus(),
                        existingJob.getBytesTransferred(),
                        existingJob.getTotalBytes(),
                        existingJob.getStartTime(),
                        java.time.Instant.now(),
                        existingJob.getErrorMessage(),
                        existingJob.getDescription());
                transferJobs.put(jobId, updatedJob);
                logger.info("Updated transfer job status: jobId={}, oldStatus={}, newStatus={}", 
                    jobId, oldStatus, cmd.status());
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
                long oldBytes = progressJob.getBytesTransferred();
                TransferJobSnapshot updatedJob = new TransferJobSnapshot(
                        progressJob.getJobId(),
                        progressJob.getSourceUri(),
                        progressJob.getDestinationPath(),
                        progressJob.getStatus(),
                        cmd.bytesTransferred(),
                        progressJob.getTotalBytes(),
                        progressJob.getStartTime(),
                        java.time.Instant.now(),
                        progressJob.getErrorMessage(),
                        progressJob.getDescription());
                transferJobs.put(jobId, updatedJob);
                logger.debug("Updated transfer job progress: jobId={}, oldBytes={}, newBytes={}, totalBytes={}", 
                    jobId, oldBytes, cmd.bytesTransferred(), progressJob.getTotalBytes());
                yield new CommandResult.Success<>(updatedJob);
            }
            case TransferJobCommand.Delete ignored -> {
                logger.debug("Deleting transfer job: jobId={}", jobId);
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
                logger.debug("Registering agent: agentId={}, endpoint={}, status={}", 
                    agentId, agentInfo.getEndpoint(), agentInfo.getStatus());
                agents.put(agentId, agentInfo);
                logger.info("Registered agent: agentId={}, endpoint={}, totalAgents={}", 
                    agentId, agentInfo.getEndpoint(), agents.size());
                yield new CommandResult.Success<>(agentInfo);
            }
            case AgentCommand.Deregister ignored -> {
                logger.debug("Deregistering agent: agentId={}", agentId);
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
                AgentStatus oldStatus = existingAgent.getStatus();
                AgentStatus newStatus = cmd.newStatus();
                existingAgent.setStatus(newStatus);
                existingAgent.setLastHeartbeat(Instant.now());
                agents.put(agentId, existingAgent);
                logger.info("Updated agent status: agentId={}, oldStatus={}, newStatus={}", 
                    agentId, oldStatus, newStatus);
                yield new CommandResult.Success<>(existingAgent);
            }
            case AgentCommand.UpdateCapabilities cmd -> {
                logger.debug("Updating agent capabilities: agentId={}", agentId);
                AgentInfo agentToUpdate = agents.get(agentId);
                if (agentToUpdate == null) {
                    logger.warn("Agent not found for capabilities update: id={}", agentId);
                    yield new CommandResult.NotFound<>(agentId, "Agent");
                }
                AgentCapabilities newCapabilities = cmd.newCapabilities();
                agentToUpdate.setCapabilities(newCapabilities);
                agentToUpdate.setLastHeartbeat(Instant.now());
                agents.put(agentId, agentToUpdate);
                logger.info("Updated agent capabilities: agentId={}, protocols={}", 
                    agentId, newCapabilities != null ? newCapabilities.getSupportedProtocols() : "null");
                yield new CommandResult.Success<>(agentToUpdate);
            }
            case AgentCommand.Heartbeat ignored -> {
                logger.debug("Processing heartbeat: agentId={}", agentId);
                AgentInfo agentForHeartbeat = agents.get(agentId);
                if (agentForHeartbeat == null) {
                    logger.warn("Agent not found for heartbeat: id={}", agentId);
                    yield new CommandResult.NotFound<>(agentId, "Agent");
                }
                agentForHeartbeat.setLastHeartbeat(Instant.now());
                if (agentForHeartbeat.getStatus() == AgentStatus.REGISTERING) {
                    logger.debug("Agent transitioning from REGISTERING to HEALTHY: agentId={}", agentId);
                    agentForHeartbeat.setStatus(AgentStatus.HEALTHY);
                }
                agents.put(agentId, agentForHeartbeat);
                logger.debug("Heartbeat received: agentId={}, status={}", agentId, agentForHeartbeat.getStatus());
                yield new CommandResult.Success<>(agentForHeartbeat);
            }
        };
    }

    private CommandResult<?> applySystemMetadataCommand(SystemMetadataCommand command) {
        String key = command.key();
        logger.debug("Processing system metadata command: key={}, type={}", key, command.getClass().getSimpleName());

        return switch (command) {
            case SystemMetadataCommand.Set cmd -> {
                String oldValue = systemMetadata.put(key, cmd.value());
                logger.info("Set system metadata: key={}, value={}, previousValue={}", 
                    key, cmd.value(), oldValue);
                yield new CommandResult.Success<>(oldValue);
            }
            case SystemMetadataCommand.Delete ignored -> {
                String removedValue = systemMetadata.remove(key);
                logger.info("Deleted system metadata: key={}, removedValue={}", key, removedValue);
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
                logger.debug("Creating job assignment: assignmentId={}, jobId={}, agentId={}", 
                    assignmentId, assignment.getJobId(), assignment.getAgentId());
                jobAssignments.put(assignmentId, assignment);
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
                JobAssignment updated = cancelAssignment.withStatusAndTimestamp(cmd.newStatus(),
                        cmd.timestamp());
                jobAssignments.put(assignmentId, updated);
                logger.info("Job assignment cancelled: assignmentId={}, jobId={}, reason={}", 
                    assignmentId, cancelAssignment.getJobId(), cmd.reason());
                yield new CommandResult.Success<>(updated);
            }
            case JobAssignmentCommand.Remove ignored -> {
                logger.debug("Removing job assignment: assignmentId={}", assignmentId);
                JobAssignment removed = jobAssignments.remove(assignmentId);
                if (removed == null) {
                    logger.warn("Job assignment not found for removal: id={}", assignmentId);
                    yield new CommandResult.NotFound<>(assignmentId, "JobAssignment");
                }
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
            case JobQueueCommand.Dequeue ignored -> {
                logger.debug("Dequeueing job: jobId={}", jobId);
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
                logger.info("Expedited job: jobId={}, reason={}", jobId, cmd.reason());
                yield new CommandResult.Success<>(expediteJob);
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
                RouteConfiguration updatedRoute = existingRoute.withUpdate(cmd.routeConfiguration());
                routes.put(routeId, updatedRoute);
                logger.info("Updated route: routeId={}, name={}", routeId, updatedRoute.getName());
                yield new CommandResult.Success<>(updatedRoute);
            }
            case RouteCommand.Delete ignored -> {
                logger.debug("Deleting route: routeId={}", routeId);
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
                RouteStatus oldStatus = suspendRoute.getStatus();
                RouteConfiguration suspended = suspendRoute.withStatus(RouteStatus.SUSPENDED);
                routes.put(routeId, suspended);
                logger.info("Suspended route: routeId={}, oldStatus={}, reason={}",
                    routeId, oldStatus, cmd.reason());
                yield new CommandResult.Success<>(suspended);
            }
            case RouteCommand.Resume ignored -> {
                logger.debug("Resuming route: routeId={}", routeId);
                RouteConfiguration resumeRoute = routes.get(routeId);
                if (resumeRoute == null) {
                    logger.warn("Route not found for resume: id={}", routeId);
                    yield new CommandResult.NotFound<>(routeId, "Route");
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
                RouteStatus oldStatus = statusRoute.getStatus();
                RouteConfiguration updated = statusRoute.withStatus(cmd.newStatus());
                routes.put(routeId, updated);
                logger.info("Updated route status: routeId={}, oldStatus={}, newStatus={}",
                    routeId, oldStatus, cmd.newStatus());
                yield new CommandResult.Success<>(updated);
            }
        };
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
            snapshot.setLastAppliedIndex(lastAppliedIndex.get());

            byte[] data = objectMapper.writeValueAsBytes(snapshot);
            logger.info("Created snapshot: size={}bytes, jobs={}, agents={}, assignments={}, queue={}, routes={}, lastAppliedIndex={}", 
                data.length, transferJobs.size(), agents.size(), jobAssignments.size(), jobQueue.size(), routes.size(), lastAppliedIndex.get());
            return data;
        } catch (IOException e) {
            logger.error("Failed to create snapshot: jobs={}, agents={}", transferJobs.size(), agents.size(), e);
            throw new RuntimeException("Failed to create snapshot", e);
        }
    }

    @Override
    public void restoreSnapshot(byte[] snapshot) {
        logger.debug("Restoring state machine snapshot: snapshotSize={}bytes", snapshot.length);
        try {
            QuorusSnapshot restoredSnapshot = objectMapper.readValue(snapshot, QuorusSnapshot.class);

            transferJobs.clear();
            transferJobs.putAll(restoredSnapshot.getTransferJobs());

            agents.clear();
            Optional.ofNullable(restoredSnapshot.getAgents()).ifPresent(agents::putAll);

            systemMetadata.clear();
            systemMetadata.putAll(restoredSnapshot.getSystemMetadata());

            jobAssignments.clear();
            Optional.ofNullable(restoredSnapshot.getJobAssignments()).ifPresent(jobAssignments::putAll);

            jobQueue.clear();
            Optional.ofNullable(restoredSnapshot.getJobQueue()).ifPresent(jobQueue::putAll);

            routes.clear();
            Optional.ofNullable(restoredSnapshot.getRoutes()).ifPresent(routes::putAll);

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
