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
    // These helpers centralise the warn-and-return-null pattern.

    /**
     * Look up an entity by ID, logging a warning if absent.
     */
    private <T> T getOrWarn(Map<String, T> store, String id, String entityType, String operation) {
        T entity = store.get(id);
        if (entity == null) {
            logger.warn("{} not found for {}: id={}", entityType, operation, id);
        }
        return entity;
    }

    /**
     * Remove an entity by ID, logging a warning if absent.
     */
    private <T> T removeOrWarn(Map<String, T> store, String id, String entityType, String operation) {
        T entity = store.remove(id);
        if (entity == null) {
            logger.warn("{} not found for {}: id={}", entityType, operation, id);
        }
        return entity;
    }

    @Override
    public Object apply(RaftCommand command) {
        if (command == null) {
            logger.debug("Received null command, returning null");
            return null; // No-op command
        }

        logger.debug("Applying command: type={}", command.getClass().getSimpleName());

        try {
            Object result = switch (command) {
                case TransferJobCommand cmd -> applyTransferJobCommand(cmd);
                case AgentCommand cmd -> applyAgentCommand(cmd);
                case SystemMetadataCommand cmd -> applySystemMetadataCommand(cmd);
                case JobAssignmentCommand cmd -> applyJobAssignmentCommand(cmd);
                case JobQueueCommand cmd -> applyJobQueueCommand(cmd);
                case RouteCommand cmd -> applyRouteCommand(cmd);
            };
            logger.debug("Command applied successfully: type={}, result={}", 
                command.getClass().getSimpleName(), result != null ? "non-null" : "null");
            return result;
        } catch (Exception e) {
            logger.error("Failed to apply command: type={}", command.getClass().getSimpleName(), e);
            throw new RuntimeException("Failed to apply command", e);
        }
    }

    private Object applyTransferJobCommand(TransferJobCommand command) {
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
                yield job;
            }
            case TransferJobCommand.UpdateStatus cmd -> {
                logger.debug("Updating transfer job status: jobId={}, newStatus={}", jobId, cmd.status());
                TransferJobSnapshot existingJob = getOrWarn(transferJobs, jobId, "Transfer job", "status update");
                if (existingJob == null) yield null;
                TransferStatus oldStatus = existingJob.getStatus();
                TransferJobSnapshot updatedJob = new TransferJobSnapshot(
                        existingJob.getJobId(),
                        existingJob.getSourceUri(),
                        existingJob.getDestinationPath(),
                        cmd.status(),
                        existingJob.getBytesTransferred(),
                        existingJob.getTotalBytes(),
                        existingJob.getStartTime(),
                        java.time.Instant.now(),
                        existingJob.getErrorMessage(),
                        existingJob.getDescription());
                transferJobs.put(jobId, updatedJob);
                logger.info("Updated transfer job status: jobId={}, oldStatus={}, newStatus={}", 
                    jobId, oldStatus, cmd.status());
                yield updatedJob;
            }
            case TransferJobCommand.UpdateProgress cmd -> {
                logger.debug("Updating transfer job progress: jobId={}, bytesTransferred={}", 
                    jobId, cmd.bytesTransferred());
                TransferJobSnapshot progressJob = getOrWarn(transferJobs, jobId, "Transfer job", "progress update");
                if (progressJob == null) yield null;
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
                yield updatedJob;
            }
            case TransferJobCommand.Delete ignored -> {
                logger.debug("Deleting transfer job: jobId={}", jobId);
                TransferJobSnapshot removedJob = removeOrWarn(transferJobs, jobId, "Transfer job", "deletion");
                if (removedJob == null) yield null;
                logger.info("Deleted transfer job: jobId={}, finalStatus={}, totalJobs={}", 
                    jobId, removedJob.getStatus(), transferJobs.size());
                yield removedJob;
            }
        };
    }

    private Object applyAgentCommand(AgentCommand command) {
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
                yield agentInfo;
            }
            case AgentCommand.Deregister ignored -> {
                logger.debug("Deregistering agent: agentId={}", agentId);
                AgentInfo removedAgent = removeOrWarn(agents, agentId, "Agent", "deregistration");
                if (removedAgent == null) yield null;
                logger.info("Deregistered agent: agentId={}, endpoint={}, totalAgents={}", 
                    agentId, removedAgent.getEndpoint(), agents.size());
                yield removedAgent;
            }
            case AgentCommand.UpdateStatus cmd -> {
                logger.debug("Updating agent status: agentId={}, newStatus={}", agentId, cmd.newStatus());
                AgentInfo existingAgent = getOrWarn(agents, agentId, "Agent", "status update");
                if (existingAgent == null) yield null;
                AgentStatus oldStatus = existingAgent.getStatus();
                AgentStatus newStatus = cmd.newStatus();
                existingAgent.setStatus(newStatus);
                existingAgent.setLastHeartbeat(Instant.now());
                agents.put(agentId, existingAgent);
                logger.info("Updated agent status: agentId={}, oldStatus={}, newStatus={}", 
                    agentId, oldStatus, newStatus);
                yield existingAgent;
            }
            case AgentCommand.UpdateCapabilities cmd -> {
                logger.debug("Updating agent capabilities: agentId={}", agentId);
                AgentInfo agentToUpdate = getOrWarn(agents, agentId, "Agent", "capabilities update");
                if (agentToUpdate == null) yield null;
                AgentCapabilities newCapabilities = cmd.newCapabilities();
                agentToUpdate.setCapabilities(newCapabilities);
                agentToUpdate.setLastHeartbeat(Instant.now());
                agents.put(agentId, agentToUpdate);
                logger.info("Updated agent capabilities: agentId={}, protocols={}", 
                    agentId, newCapabilities != null ? newCapabilities.getSupportedProtocols() : "null");
                yield agentToUpdate;
            }
            case AgentCommand.Heartbeat ignored -> {
                logger.debug("Processing heartbeat: agentId={}", agentId);
                AgentInfo agentForHeartbeat = getOrWarn(agents, agentId, "Agent", "heartbeat");
                if (agentForHeartbeat == null) yield null;
                agentForHeartbeat.setLastHeartbeat(Instant.now());
                if (agentForHeartbeat.getStatus() == AgentStatus.REGISTERING) {
                    logger.debug("Agent transitioning from REGISTERING to HEALTHY: agentId={}", agentId);
                    agentForHeartbeat.setStatus(AgentStatus.HEALTHY);
                }
                agents.put(agentId, agentForHeartbeat);
                logger.debug("Heartbeat received: agentId={}, status={}", agentId, agentForHeartbeat.getStatus());
                yield agentForHeartbeat;
            }
        };
    }

    private Object applySystemMetadataCommand(SystemMetadataCommand command) {
        String key = command.key();
        logger.debug("Processing system metadata command: key={}, type={}", key, command.getClass().getSimpleName());

        return switch (command) {
            case SystemMetadataCommand.Set cmd -> {
                String oldValue = systemMetadata.put(key, cmd.value());
                logger.info("Set system metadata: key={}, value={}, previousValue={}", 
                    key, cmd.value(), oldValue);
                yield oldValue;
            }
            case SystemMetadataCommand.Delete ignored -> {
                String removedValue = systemMetadata.remove(key);
                logger.info("Deleted system metadata: key={}, removedValue={}", key, removedValue);
                yield removedValue;
            }
        };
    }

    private Object applyJobAssignmentCommand(JobAssignmentCommand command) {
        String assignmentId = command.getAssignmentId();
        logger.debug("Processing job assignment command: assignmentId={}, type={}", assignmentId, command.getType());

        return switch (command.getType()) {
            case ASSIGN -> {
                JobAssignment assignment = command.getJobAssignment();
                logger.debug("Creating job assignment: assignmentId={}, jobId={}, agentId={}", 
                    assignmentId, assignment.getJobId(), assignment.getAgentId());
                jobAssignments.put(assignmentId, assignment);
                logger.info("Created job assignment: assignmentId={}, jobId={}, agentId={}, totalAssignments={}", 
                    assignmentId, assignment.getJobId(), assignment.getAgentId(), jobAssignments.size());
                yield assignment;
            }
            case ACCEPT -> {
                logger.debug("Processing assignment accept: assignmentId={}", assignmentId);
                JobAssignment existing = getOrWarn(jobAssignments, assignmentId, "Job assignment", "accept");
                if (existing == null) yield null;
                JobAssignment updated = existing.withStatusAndTimestamp(command.getNewStatus(),
                        command.getTimestamp());
                jobAssignments.put(assignmentId, updated);
                logger.info("Job assignment accepted: assignmentId={}, jobId={}, agentId={}", 
                    assignmentId, existing.getJobId(), existing.getAgentId());
                yield updated;
            }
            case REJECT -> {
                logger.debug("Processing assignment reject: assignmentId={}, reason={}", 
                    assignmentId, command.getReason());
                JobAssignment rejectedAssignment = getOrWarn(jobAssignments, assignmentId, "Job assignment", "reject");
                if (rejectedAssignment == null) yield null;
                JobAssignment updated = rejectedAssignment.withStatusAndTimestamp(command.getNewStatus(),
                        command.getTimestamp());
                jobAssignments.put(assignmentId, updated);
                logger.info("Job assignment rejected: assignmentId={}, jobId={}, reason={}", 
                    assignmentId, rejectedAssignment.getJobId(), command.getReason());
                yield updated;
            }
            case UPDATE_STATUS -> {
                logger.debug("Updating assignment status: assignmentId={}, newStatus={}", 
                    assignmentId, command.getNewStatus());
                JobAssignment statusAssignment = getOrWarn(jobAssignments, assignmentId, "Job assignment", "status update");
                if (statusAssignment == null) yield null;
                JobAssignment updated = statusAssignment.withStatusAndTimestamp(command.getNewStatus(),
                        command.getTimestamp());
                jobAssignments.put(assignmentId, updated);
                logger.info("Updated job assignment status: assignmentId={}, newStatus={}", 
                    assignmentId, command.getNewStatus());
                yield updated;
            }
            case TIMEOUT -> {
                logger.debug("Processing assignment timeout: assignmentId={}", assignmentId);
                JobAssignment timeoutAssignment = getOrWarn(jobAssignments, assignmentId, "Job assignment", "timeout");
                if (timeoutAssignment == null) yield null;
                JobAssignment updated = timeoutAssignment.withStatusAndTimestamp(command.getNewStatus(),
                        command.getTimestamp());
                jobAssignments.put(assignmentId, updated);
                logger.info("Job assignment timed out: assignmentId={}, jobId={}", 
                    assignmentId, timeoutAssignment.getJobId());
                yield updated;
            }
            case CANCEL -> {
                logger.debug("Processing assignment cancel: assignmentId={}, reason={}", 
                    assignmentId, command.getReason());
                JobAssignment cancelAssignment = getOrWarn(jobAssignments, assignmentId, "Job assignment", "cancel");
                if (cancelAssignment == null) yield null;
                JobAssignment updated = cancelAssignment.withStatusAndTimestamp(command.getNewStatus(),
                        command.getTimestamp());
                jobAssignments.put(assignmentId, updated);
                logger.info("Job assignment cancelled: assignmentId={}, jobId={}, reason={}", 
                    assignmentId, cancelAssignment.getJobId(), command.getReason());
                yield updated;
            }
            case REMOVE -> {
                logger.debug("Removing job assignment: assignmentId={}", assignmentId);
                JobAssignment removed = removeOrWarn(jobAssignments, assignmentId, "Job assignment", "removal");
                if (removed == null) yield null;
                logger.info("Removed job assignment: assignmentId={}, totalAssignments={}", 
                    assignmentId, jobAssignments.size());
                yield removed;
            }
        };
    }

    private Object applyJobQueueCommand(JobQueueCommand command) {
        String jobId = command.jobId();
        logger.debug("Processing job queue command: jobId={}, type={}", jobId, command.getClass().getSimpleName());

        return switch (command) {
            case JobQueueCommand.Enqueue cmd -> {
                QueuedJob queuedJob = cmd.queuedJob();
                logger.debug("Enqueueing job: jobId={}, priority={}", jobId, queuedJob.getPriority());
                jobQueue.put(jobId, queuedJob);
                logger.info("Enqueued job: jobId={}, priority={}, queueSize={}", 
                    jobId, queuedJob.getPriority(), jobQueue.size());
                yield queuedJob;
            }
            case JobQueueCommand.Dequeue ignored -> {
                logger.debug("Dequeueing job: jobId={}", jobId);
                QueuedJob dequeuedJob = removeOrWarn(jobQueue, jobId, "Job", "dequeue");
                if (dequeuedJob == null) yield null;
                logger.info("Dequeued job: jobId={}, queueSize={}", jobId, jobQueue.size());
                yield dequeuedJob;
            }
            case JobQueueCommand.Prioritize cmd -> {
                logger.debug("Updating job priority: jobId={}, newPriority={}", jobId, cmd.newPriority());
                QueuedJob existingJob = getOrWarn(jobQueue, jobId, "Job", "prioritize");
                if (existingJob == null) yield null;
                JobPriority oldPriority = existingJob.getPriority();
                QueuedJob updatedJob = existingJob.withPriority(cmd.newPriority());
                jobQueue.put(jobId, updatedJob);
                logger.info("Updated job priority: jobId={}, oldPriority={}, newPriority={}, reason={}", 
                    jobId, oldPriority, cmd.newPriority(), cmd.reason());
                yield updatedJob;
            }
            case JobQueueCommand.Remove cmd -> {
                logger.debug("Removing job from queue: jobId={}", jobId);
                QueuedJob removedJob = removeOrWarn(jobQueue, jobId, "Job", "removal");
                if (removedJob == null) yield null;
                logger.info("Removed job from queue: jobId={}, reason={}, queueSize={}", 
                    jobId, cmd.reason(), jobQueue.size());
                yield removedJob;
            }
            case JobQueueCommand.Expedite cmd -> {
                logger.debug("Expediting job: jobId={}", jobId);
                QueuedJob expediteJob = getOrWarn(jobQueue, jobId, "Job", "expedite");
                if (expediteJob == null) yield null;
                logger.info("Expedited job: jobId={}, reason={}", jobId, cmd.reason());
                yield expediteJob;
            }
            case JobQueueCommand.UpdateRequirements cmd -> {
                logger.debug("Updating job requirements: jobId={}", jobId);
                QueuedJob updatedJob = cmd.queuedJob();
                jobQueue.put(jobId, updatedJob);
                logger.info("Updated job requirements: jobId={}", jobId);
                yield updatedJob;
            }
        };
    }

    private Object applyRouteCommand(RouteCommand command) {
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
                yield routeConfig;
            }
            case RouteCommand.Update cmd -> {
                logger.debug("Updating route: routeId={}", routeId);
                RouteConfiguration existingRoute = getOrWarn(routes, routeId, "Route", "update");
                if (existingRoute == null) yield null;
                RouteConfiguration updatedRoute = existingRoute.withUpdate(cmd.routeConfiguration());
                routes.put(routeId, updatedRoute);
                logger.info("Updated route: routeId={}, name={}", routeId, updatedRoute.getName());
                yield updatedRoute;
            }
            case RouteCommand.Delete ignored -> {
                logger.debug("Deleting route: routeId={}", routeId);
                RouteConfiguration removedRoute = removeOrWarn(routes, routeId, "Route", "deletion");
                if (removedRoute == null) yield null;
                logger.info("Deleted route: routeId={}, totalRoutes={}", routeId, routes.size());
                yield removedRoute;
            }
            case RouteCommand.Suspend cmd -> {
                logger.debug("Suspending route: routeId={}, reason={}", routeId, cmd.reason());
                RouteConfiguration suspendRoute = getOrWarn(routes, routeId, "Route", "suspend");
                if (suspendRoute == null) yield null;
                RouteStatus oldStatus = suspendRoute.getStatus();
                RouteConfiguration suspended = suspendRoute.withStatus(RouteStatus.SUSPENDED);
                routes.put(routeId, suspended);
                logger.info("Suspended route: routeId={}, oldStatus={}, reason={}",
                    routeId, oldStatus, cmd.reason());
                yield suspended;
            }
            case RouteCommand.Resume ignored -> {
                logger.debug("Resuming route: routeId={}", routeId);
                RouteConfiguration resumeRoute = getOrWarn(routes, routeId, "Route", "resume");
                if (resumeRoute == null) yield null;
                RouteStatus oldStatus = resumeRoute.getStatus();
                RouteConfiguration resumed = resumeRoute.withStatus(RouteStatus.ACTIVE);
                routes.put(routeId, resumed);
                logger.info("Resumed route: routeId={}, oldStatus={}", routeId, oldStatus);
                yield resumed;
            }
            case RouteCommand.UpdateStatus cmd -> {
                logger.debug("Updating route status: routeId={}, newStatus={}", routeId, cmd.newStatus());
                RouteConfiguration statusRoute = getOrWarn(routes, routeId, "Route", "status update");
                if (statusRoute == null) yield null;
                RouteStatus oldStatus = statusRoute.getStatus();
                RouteConfiguration updated = statusRoute.withStatus(cmd.newStatus());
                routes.put(routeId, updated);
                logger.info("Updated route status: routeId={}, oldStatus={}, newStatus={}",
                    routeId, oldStatus, cmd.newStatus());
                yield updated;
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
}
