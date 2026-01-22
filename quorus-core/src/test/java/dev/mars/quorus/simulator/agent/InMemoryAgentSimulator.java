/*
 * Copyright (c) 2025 Cityline Ltd.
 * All rights reserved.
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

package dev.mars.quorus.simulator.agent;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * In-memory simulator for a Quorus agent.
 * 
 * <p>This simulator enables testing of agent lifecycle, job management, and
 * controller communication without HTTP or real network connections. It supports:
 * <ul>
 *   <li>Agent registration and discovery</li>
 *   <li>Job polling and execution</li>
 *   <li>Heartbeat monitoring</li>
 *   <li>Agent capabilities and selection</li>
 *   <li>Failure scenarios (crash, network partition, resource exhaustion)</li>
 * </ul>
 * 
 * <h2>Usage Example:</h2>
 * <pre>{@code
 * InMemoryAgentSimulator agent = new InMemoryAgentSimulator("agent-001")
 *     .withCapabilities(new AgentCapabilities()
 *         .supportedProtocols(Set.of("sftp", "http"))
 *         .maxConcurrentTransfers(5))
 *     .withRegion("us-east-1");
 * 
 * agent.connectToController(controllerCallback);
 * agent.start();
 * 
 * // Agent now accepts and executes jobs
 * }</pre>
 * 
 * @author Mark Andrew Ray-Smith
 * @since 1.0
 */
public class InMemoryAgentSimulator {

    // Agent identity
    private final String agentId;
    private String hostname;
    private String region;
    private String datacenter;
    private Map<String, String> tags = new HashMap<>();
    
    // Capabilities
    private AgentCapabilities capabilities = new AgentCapabilities();
    
    // State
    private volatile AgentState state = AgentState.STOPPED;
    private volatile Instant startTime;
    private volatile Instant lastHeartbeat;
    private final AtomicInteger activeJobCount = new AtomicInteger(0);
    
    // Jobs
    private final Map<String, JobExecution> activeJobs = new ConcurrentHashMap<>();
    private final List<CompletedJob> completedJobs = Collections.synchronizedList(new ArrayList<>());
    
    // Controller connection
    private ControllerConnection controllerConnection;
    
    // Background tasks
    private final ScheduledExecutorService scheduler;
    private ScheduledFuture<?> heartbeatTask;
    private ScheduledFuture<?> pollingTask;
    
    // Configuration
    private long heartbeatIntervalMs = 5000;
    private long pollingIntervalMs = 1000;
    private long jobExecutionDelayMs = 1000;
    private int progressUpdateIntervalMs = 500;
    
    // Chaos engineering
    private AgentFailureMode failureMode = AgentFailureMode.NONE;
    private double jobFailureRate = 0.0;
    
    // Statistics
    private final AtomicLong totalJobsReceived = new AtomicLong(0);
    private final AtomicLong totalJobsCompleted = new AtomicLong(0);
    private final AtomicLong totalJobsFailed = new AtomicLong(0);
    private final AtomicLong totalBytesTransferred = new AtomicLong(0);
    
    // Callbacks
    private Consumer<AgentEvent> eventCallback;
    private Function<JobAssignment, Boolean> jobAcceptanceFilter;

    /**
     * Agent states.
     */
    public enum AgentState {
        /** Agent is stopped */
        STOPPED,
        /** Agent is registering with controller */
        REGISTERING,
        /** Agent is active and accepting jobs */
        ACTIVE,
        /** Agent is busy (at max capacity) */
        BUSY,
        /** Agent is draining (not accepting new jobs) */
        DRAINING,
        /** Agent has crashed */
        CRASHED,
        /** Agent is in network partition */
        PARTITIONED
    }

    /**
     * Failure modes for chaos engineering.
     */
    public enum AgentFailureMode {
        /** Normal operation */
        NONE,
        /** Cannot register with controller */
        REGISTRATION_FAILURE,
        /** Stop sending heartbeats */
        HEARTBEAT_TIMEOUT,
        /** Reject all job assignments */
        JOB_REJECTION,
        /** Fail all jobs */
        JOB_FAILURE,
        /** Execute jobs very slowly */
        SLOW_EXECUTION,
        /** Crash mid-job */
        CRASH_DURING_JOB,
        /** Simulate out of memory */
        MEMORY_EXHAUSTED,
        /** Cannot reach controller */
        NETWORK_PARTITION
    }

    /**
     * Creates a new agent simulator.
     *
     * @param agentId the unique agent ID
     */
    public InMemoryAgentSimulator(String agentId) {
        this.agentId = agentId;
        this.hostname = "sim-" + agentId;
        this.scheduler = Executors.newScheduledThreadPool(3, r -> {
            Thread t = new Thread(r, "agent-simulator-" + agentId);
            t.setDaemon(true);
            return t;
        });
    }

    // ==================== Builder Methods ====================

    /**
     * Sets the hostname.
     *
     * @param hostname the hostname
     * @return this simulator for chaining
     */
    public InMemoryAgentSimulator withHostname(String hostname) {
        this.hostname = hostname;
        return this;
    }

    /**
     * Sets the region.
     *
     * @param region the region
     * @return this simulator for chaining
     */
    public InMemoryAgentSimulator withRegion(String region) {
        this.region = region;
        return this;
    }

    /**
     * Sets the datacenter.
     *
     * @param datacenter the datacenter
     * @return this simulator for chaining
     */
    public InMemoryAgentSimulator withDatacenter(String datacenter) {
        this.datacenter = datacenter;
        return this;
    }

    /**
     * Sets agent capabilities.
     *
     * @param capabilities the capabilities
     * @return this simulator for chaining
     */
    public InMemoryAgentSimulator withCapabilities(AgentCapabilities capabilities) {
        this.capabilities = capabilities;
        return this;
    }

    /**
     * Adds a tag.
     *
     * @param key tag key
     * @param value tag value
     * @return this simulator for chaining
     */
    public InMemoryAgentSimulator withTag(String key, String value) {
        this.tags.put(key, value);
        return this;
    }

    /**
     * Sets the event callback.
     *
     * @param callback the callback
     * @return this simulator for chaining
     */
    public InMemoryAgentSimulator withEventCallback(Consumer<AgentEvent> callback) {
        this.eventCallback = callback;
        return this;
    }

    /**
     * Sets a job acceptance filter.
     *
     * @param filter function that returns true if job should be accepted
     * @return this simulator for chaining
     */
    public InMemoryAgentSimulator withJobAcceptanceFilter(Function<JobAssignment, Boolean> filter) {
        this.jobAcceptanceFilter = filter;
        return this;
    }

    // ==================== Controller Connection ====================

    /**
     * Connects to a controller (via callback interface).
     *
     * @param connection the controller connection
     * @return this simulator for chaining
     */
    public InMemoryAgentSimulator connectToController(ControllerConnection connection) {
        this.controllerConnection = connection;
        return this;
    }

    // ==================== Lifecycle ====================

    /**
     * Starts the agent.
     *
     * @throws AgentException if startup fails
     */
    public void start() throws AgentException {
        if (state != AgentState.STOPPED) {
            throw new AgentException("Agent is already running");
        }
        
        if (controllerConnection == null) {
            throw new AgentException("No controller connection configured");
        }
        
        state = AgentState.REGISTERING;
        fireEvent(AgentEvent.stateChanged(agentId, AgentState.STOPPED, AgentState.REGISTERING));
        
        // Check for registration failure
        if (failureMode == AgentFailureMode.REGISTRATION_FAILURE) {
            state = AgentState.STOPPED;
            throw new AgentException("Registration failed (simulated)");
        }
        
        // Register with controller
        try {
            AgentRegistration registration = new AgentRegistration(
                agentId,
                hostname,
                region,
                datacenter,
                capabilities,
                tags
            );
            controllerConnection.registerAgent(registration);
        } catch (Exception e) {
            state = AgentState.STOPPED;
            throw new AgentException("Registration failed", e);
        }
        
        state = AgentState.ACTIVE;
        startTime = Instant.now();
        lastHeartbeat = Instant.now();
        fireEvent(AgentEvent.stateChanged(agentId, AgentState.REGISTERING, AgentState.ACTIVE));
        
        // Start heartbeat
        if (failureMode != AgentFailureMode.HEARTBEAT_TIMEOUT) {
            heartbeatTask = scheduler.scheduleAtFixedRate(
                this::sendHeartbeat,
                heartbeatIntervalMs,
                heartbeatIntervalMs,
                TimeUnit.MILLISECONDS
            );
        }
        
        // Start job polling
        if (failureMode != AgentFailureMode.NETWORK_PARTITION) {
            pollingTask = scheduler.scheduleAtFixedRate(
                this::pollForJobs,
                pollingIntervalMs,
                pollingIntervalMs,
                TimeUnit.MILLISECONDS
            );
        }
    }

    /**
     * Stops the agent gracefully.
     */
    public void stop() {
        if (state == AgentState.STOPPED || state == AgentState.CRASHED) {
            return;
        }
        
        AgentState previousState = state;
        state = AgentState.DRAINING;
        fireEvent(AgentEvent.stateChanged(agentId, previousState, AgentState.DRAINING));
        
        // Cancel scheduled tasks
        if (heartbeatTask != null) {
            heartbeatTask.cancel(false);
        }
        if (pollingTask != null) {
            pollingTask.cancel(false);
        }
        
        // Wait for active jobs to complete (with timeout)
        long deadline = System.currentTimeMillis() + 30000;
        while (activeJobCount.get() > 0 && System.currentTimeMillis() < deadline) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        
        // Unregister from controller
        try {
            controllerConnection.unregisterAgent(agentId);
        } catch (Exception e) {
            // Ignore unregistration errors during shutdown
        }
        
        state = AgentState.STOPPED;
        fireEvent(AgentEvent.stateChanged(agentId, AgentState.DRAINING, AgentState.STOPPED));
    }

    /**
     * Simulates a sudden crash.
     */
    public void crash() {
        AgentState previousState = state;
        state = AgentState.CRASHED;
        
        // Cancel all tasks immediately
        if (heartbeatTask != null) {
            heartbeatTask.cancel(true);
        }
        if (pollingTask != null) {
            pollingTask.cancel(true);
        }
        
        // Fail all active jobs
        activeJobs.forEach((jobId, job) -> {
            job.status = JobStatus.FAILED;
            job.error = "Agent crashed";
            job.endTime = Instant.now();
            completedJobs.add(new CompletedJob(job));
            totalJobsFailed.incrementAndGet();
        });
        activeJobs.clear();
        activeJobCount.set(0);
        
        fireEvent(AgentEvent.crashed(agentId, "Simulated crash"));
        fireEvent(AgentEvent.stateChanged(agentId, previousState, AgentState.CRASHED));
    }

    /**
     * Recovers from a crash.
     *
     * @throws AgentException if recovery fails
     */
    public void recover() throws AgentException {
        if (state != AgentState.CRASHED) {
            throw new AgentException("Agent is not crashed");
        }
        state = AgentState.STOPPED;
        start();
    }

    // ==================== Job Management ====================

    /**
     * Gets active jobs.
     *
     * @return map of job ID to job execution
     */
    public Map<String, JobExecution> getActiveJobs() {
        return new HashMap<>(activeJobs);
    }

    /**
     * Gets completed jobs.
     *
     * @return list of completed jobs
     */
    public List<CompletedJob> getCompletedJobs() {
        return new ArrayList<>(completedJobs);
    }

    /**
     * Gets the active job count.
     *
     * @return active job count
     */
    public int getActiveJobCount() {
        return activeJobCount.get();
    }

    /**
     * Manually assigns a job to this agent.
     *
     * @param assignment the job assignment
     */
    public void assignJob(JobAssignment assignment) {
        if (state != AgentState.ACTIVE && state != AgentState.BUSY) {
            fireEvent(AgentEvent.jobRejected(agentId, assignment.jobId(), 
                "Agent not accepting jobs"));
            return;
        }
        
        // Check job rejection mode
        if (failureMode == AgentFailureMode.JOB_REJECTION) {
            fireEvent(AgentEvent.jobRejected(agentId, assignment.jobId(), 
                "Job rejection mode enabled"));
            return;
        }
        
        // Check capacity
        if (activeJobCount.get() >= capabilities.maxConcurrentTransfers()) {
            state = AgentState.BUSY;
            fireEvent(AgentEvent.jobRejected(agentId, assignment.jobId(), 
                "Agent at capacity"));
            return;
        }
        
        // Check acceptance filter
        if (jobAcceptanceFilter != null && !jobAcceptanceFilter.apply(assignment)) {
            fireEvent(AgentEvent.jobRejected(agentId, assignment.jobId(), 
                "Job filtered out"));
            return;
        }
        
        // Accept job
        JobExecution execution = new JobExecution(assignment);
        activeJobs.put(assignment.jobId(), execution);
        activeJobCount.incrementAndGet();
        totalJobsReceived.incrementAndGet();
        
        fireEvent(AgentEvent.jobAccepted(agentId, assignment.jobId()));
        
        // Start execution
        scheduler.submit(() -> executeJob(execution));
    }

    // ==================== Configuration ====================

    /**
     * Sets the heartbeat interval.
     *
     * @param intervalMs interval in milliseconds
     * @return this simulator for chaining
     */
    public InMemoryAgentSimulator setHeartbeatIntervalMs(long intervalMs) {
        this.heartbeatIntervalMs = intervalMs;
        return this;
    }

    /**
     * Sets the job polling interval.
     *
     * @param intervalMs interval in milliseconds
     * @return this simulator for chaining
     */
    public InMemoryAgentSimulator setPollingIntervalMs(long intervalMs) {
        this.pollingIntervalMs = intervalMs;
        return this;
    }

    /**
     * Sets the simulated job execution delay.
     *
     * @param delayMs delay in milliseconds
     * @return this simulator for chaining
     */
    public InMemoryAgentSimulator setJobExecutionDelayMs(long delayMs) {
        this.jobExecutionDelayMs = delayMs;
        return this;
    }

    /**
     * Sets the progress update interval.
     *
     * @param intervalMs interval in milliseconds
     * @return this simulator for chaining
     */
    public InMemoryAgentSimulator setProgressUpdateIntervalMs(int intervalMs) {
        this.progressUpdateIntervalMs = intervalMs;
        return this;
    }

    /**
     * Sets the failure mode.
     *
     * @param mode the failure mode
     * @return this simulator for chaining
     */
    public InMemoryAgentSimulator setFailureMode(AgentFailureMode mode) {
        this.failureMode = mode;
        
        // Handle immediate effects
        if (mode == AgentFailureMode.HEARTBEAT_TIMEOUT && heartbeatTask != null) {
            heartbeatTask.cancel(false);
        }
        if (mode == AgentFailureMode.NETWORK_PARTITION) {
            if (pollingTask != null) pollingTask.cancel(false);
            if (heartbeatTask != null) heartbeatTask.cancel(false);
            state = AgentState.PARTITIONED;
        }
        
        return this;
    }

    /**
     * Sets the job failure rate.
     *
     * @param rate failure rate (0.0 to 1.0)
     * @return this simulator for chaining
     */
    public InMemoryAgentSimulator setJobFailureRate(double rate) {
        this.jobFailureRate = Math.max(0.0, Math.min(1.0, rate));
        return this;
    }

    /**
     * Resets all chaos settings.
     *
     * @return this simulator for chaining
     */
    public InMemoryAgentSimulator reset() {
        this.failureMode = AgentFailureMode.NONE;
        this.jobFailureRate = 0.0;
        return this;
    }

    // ==================== Getters ====================

    /**
     * Gets the agent ID.
     *
     * @return agent ID
     */
    public String getAgentId() {
        return agentId;
    }

    /**
     * Gets the agent state.
     *
     * @return current state
     */
    public AgentState getState() {
        return state;
    }

    /**
     * Gets agent capabilities.
     *
     * @return capabilities
     */
    public AgentCapabilities getCapabilities() {
        return capabilities;
    }

    /**
     * Gets the region.
     *
     * @return region
     */
    public String getRegion() {
        return region;
    }

    /**
     * Gets the datacenter.
     *
     * @return datacenter
     */
    public String getDatacenter() {
        return datacenter;
    }

    /**
     * Gets the start time.
     *
     * @return start time
     */
    public Instant getStartTime() {
        return startTime;
    }

    /**
     * Gets the last heartbeat time.
     *
     * @return last heartbeat time
     */
    public Instant getLastHeartbeat() {
        return lastHeartbeat;
    }

    // ==================== Statistics ====================

    /**
     * Gets total jobs received.
     *
     * @return total jobs
     */
    public long getTotalJobsReceived() {
        return totalJobsReceived.get();
    }

    /**
     * Gets total jobs completed.
     *
     * @return completed jobs
     */
    public long getTotalJobsCompleted() {
        return totalJobsCompleted.get();
    }

    /**
     * Gets total jobs failed.
     *
     * @return failed jobs
     */
    public long getTotalJobsFailed() {
        return totalJobsFailed.get();
    }

    /**
     * Gets total bytes transferred.
     *
     * @return total bytes
     */
    public long getTotalBytesTransferred() {
        return totalBytesTransferred.get();
    }

    /**
     * Resets statistics.
     */
    public void resetStatistics() {
        totalJobsReceived.set(0);
        totalJobsCompleted.set(0);
        totalJobsFailed.set(0);
        totalBytesTransferred.set(0);
        completedJobs.clear();
    }

    // ==================== Lifecycle ====================

    /**
     * Shuts down the agent simulator.
     */
    public void shutdown() {
        stop();
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    // ==================== Private Implementation ====================

    private void sendHeartbeat() {
        if (state == AgentState.CRASHED || state == AgentState.PARTITIONED) {
            return;
        }
        
        try {
            HeartbeatInfo heartbeat = new HeartbeatInfo(
                agentId,
                state,
                activeJobCount.get(),
                capabilities.maxConcurrentTransfers() - activeJobCount.get(),
                getMemoryUsage(),
                getCpuUsage()
            );
            
            controllerConnection.sendHeartbeat(heartbeat);
            lastHeartbeat = Instant.now();
        } catch (Exception e) {
            fireEvent(AgentEvent.heartbeatFailed(agentId, e.getMessage()));
        }
    }

    private void pollForJobs() {
        if (state != AgentState.ACTIVE) {
            return;
        }
        
        if (activeJobCount.get() >= capabilities.maxConcurrentTransfers()) {
            return; // At capacity
        }
        
        try {
            List<JobAssignment> jobs = controllerConnection.pollPendingJobs(agentId);
            for (JobAssignment job : jobs) {
                if (activeJobCount.get() < capabilities.maxConcurrentTransfers()) {
                    assignJob(job);
                }
            }
        } catch (Exception e) {
            fireEvent(AgentEvent.pollingFailed(agentId, e.getMessage()));
        }
    }

    private void executeJob(JobExecution execution) {
        execution.status = JobStatus.IN_PROGRESS;
        execution.startTime = Instant.now();
        
        fireEvent(AgentEvent.jobStarted(agentId, execution.assignment.jobId()));
        
        // Report initial status
        reportJobStatus(execution);
        
        try {
            // Determine execution time
            long executionTime = jobExecutionDelayMs;
            if (failureMode == AgentFailureMode.SLOW_EXECUTION) {
                executionTime *= 10;
            }
            
            // Simulate transfer progress
            long totalBytes = execution.assignment.expectedSizeBytes();
            long bytesPerUpdate = totalBytes / 10;
            int updates = 10;
            long updateInterval = executionTime / updates;
            
            for (int i = 0; i < updates; i++) {
                // Check for crash during job
                if (failureMode == AgentFailureMode.CRASH_DURING_JOB && i == 5) {
                    crash();
                    return;
                }
                
                // Check for memory exhaustion
                if (failureMode == AgentFailureMode.MEMORY_EXHAUSTED && i == 3) {
                    throw new RuntimeException("Out of memory (simulated)");
                }
                
                Thread.sleep(updateInterval);
                
                execution.bytesTransferred += bytesPerUpdate;
                execution.progress = (i + 1) * 10;
                reportJobStatus(execution);
            }
            
            // Check for failure
            boolean shouldFail = failureMode == AgentFailureMode.JOB_FAILURE ||
                (jobFailureRate > 0 && ThreadLocalRandom.current().nextDouble() < jobFailureRate);
            
            if (shouldFail) {
                throw new RuntimeException("Job execution failed (simulated)");
            }
            
            // Complete job
            execution.status = JobStatus.COMPLETED;
            execution.bytesTransferred = totalBytes;
            execution.progress = 100;
            execution.endTime = Instant.now();
            
            totalJobsCompleted.incrementAndGet();
            totalBytesTransferred.addAndGet(totalBytes);
            
            fireEvent(AgentEvent.jobCompleted(agentId, execution.assignment.jobId()));
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            execution.status = JobStatus.CANCELLED;
            execution.error = "Job cancelled";
            execution.endTime = Instant.now();
            totalJobsFailed.incrementAndGet();
            fireEvent(AgentEvent.jobFailed(agentId, execution.assignment.jobId(), "Cancelled"));
            
        } catch (Exception e) {
            execution.status = JobStatus.FAILED;
            execution.error = e.getMessage();
            execution.endTime = Instant.now();
            totalJobsFailed.incrementAndGet();
            fireEvent(AgentEvent.jobFailed(agentId, execution.assignment.jobId(), e.getMessage()));
        }
        
        // Report final status
        reportJobStatus(execution);
        
        // Move to completed
        activeJobs.remove(execution.assignment.jobId());
        completedJobs.add(new CompletedJob(execution));
        activeJobCount.decrementAndGet();
        
        // Update state
        if (state == AgentState.BUSY && 
            activeJobCount.get() < capabilities.maxConcurrentTransfers()) {
            state = AgentState.ACTIVE;
        }
    }

    private void reportJobStatus(JobExecution execution) {
        try {
            JobStatusUpdate update = new JobStatusUpdate(
                execution.assignment.jobId(),
                agentId,
                execution.status,
                execution.progress,
                execution.bytesTransferred,
                execution.error
            );
            controllerConnection.reportJobStatus(update);
        } catch (Exception e) {
            // Log but don't fail the job
            fireEvent(AgentEvent.statusReportFailed(agentId, execution.assignment.jobId(), 
                e.getMessage()));
        }
    }

    private void fireEvent(AgentEvent event) {
        if (eventCallback != null) {
            try {
                eventCallback.accept(event);
            } catch (Exception e) {
                // Ignore callback errors
            }
        }
    }

    private double getMemoryUsage() {
        // Simulate memory usage (0.0 to 1.0)
        if (failureMode == AgentFailureMode.MEMORY_EXHAUSTED) {
            return 0.95;
        }
        return 0.3 + (activeJobCount.get() * 0.1);
    }

    private double getCpuUsage() {
        // Simulate CPU usage (0.0 to 1.0)
        return 0.2 + (activeJobCount.get() * 0.15);
    }

    // ==================== Inner Classes ====================

    /**
     * Agent capabilities.
     */
    public static class AgentCapabilities {
        private Set<String> supportedProtocols = Set.of("sftp", "ftp", "http", "smb");
        private int maxConcurrentTransfers = 5;
        private long maxTransferSize = 10_000_000_000L; // 10GB

        public AgentCapabilities supportedProtocols(Set<String> protocols) {
            this.supportedProtocols = new HashSet<>(protocols);
            return this;
        }

        public AgentCapabilities maxConcurrentTransfers(int max) {
            this.maxConcurrentTransfers = max;
            return this;
        }

        public AgentCapabilities maxTransferSize(long maxSize) {
            this.maxTransferSize = maxSize;
            return this;
        }

        public Set<String> supportedProtocols() { return supportedProtocols; }
        public int maxConcurrentTransfers() { return maxConcurrentTransfers; }
        public long maxTransferSize() { return maxTransferSize; }
    }

    /**
     * Agent registration information.
     */
    public record AgentRegistration(
        String agentId,
        String hostname,
        String region,
        String datacenter,
        AgentCapabilities capabilities,
        Map<String, String> tags
    ) {}

    /**
     * Heartbeat information.
     */
    public record HeartbeatInfo(
        String agentId,
        AgentState state,
        int activeJobs,
        int availableCapacity,
        double memoryUsage,
        double cpuUsage
    ) {}

    /**
     * Job assignment.
     */
    public record JobAssignment(
        String jobId,
        String sourceUri,
        String destinationPath,
        String protocol,
        long expectedSizeBytes,
        Map<String, String> options
    ) {}

    /**
     * Job status.
     */
    public enum JobStatus {
        PENDING, ACCEPTED, IN_PROGRESS, COMPLETED, FAILED, CANCELLED
    }

    /**
     * Job status update.
     */
    public record JobStatusUpdate(
        String jobId,
        String agentId,
        JobStatus status,
        int progress,
        long bytesTransferred,
        String error
    ) {}

    /**
     * Job execution state.
     */
    public static class JobExecution {
        final JobAssignment assignment;
        volatile JobStatus status = JobStatus.ACCEPTED;
        volatile int progress = 0;
        volatile long bytesTransferred = 0;
        volatile String error;
        volatile Instant startTime;
        volatile Instant endTime;

        JobExecution(JobAssignment assignment) {
            this.assignment = assignment;
        }

        public JobAssignment getAssignment() { return assignment; }
        public JobStatus getStatus() { return status; }
        public int getProgress() { return progress; }
        public long getBytesTransferred() { return bytesTransferred; }
        public String getError() { return error; }
        public Instant getStartTime() { return startTime; }
        public Instant getEndTime() { return endTime; }

        public Duration getDuration() {
            if (startTime == null) return Duration.ZERO;
            Instant end = endTime != null ? endTime : Instant.now();
            return Duration.between(startTime, end);
        }
    }

    /**
     * Completed job record.
     */
    public record CompletedJob(
        String jobId,
        JobStatus status,
        long bytesTransferred,
        Duration duration,
        String error
    ) {
        CompletedJob(JobExecution execution) {
            this(
                execution.assignment.jobId(),
                execution.status,
                execution.bytesTransferred,
                execution.getDuration(),
                execution.error
            );
        }
    }

    /**
     * Agent event.
     */
    public record AgentEvent(
        String agentId,
        EventType type,
        String jobId,
        String message,
        AgentState oldState,
        AgentState newState
    ) {
        public enum EventType {
            STATE_CHANGED, JOB_ACCEPTED, JOB_REJECTED, JOB_STARTED, 
            JOB_COMPLETED, JOB_FAILED, CRASHED, HEARTBEAT_FAILED,
            POLLING_FAILED, STATUS_REPORT_FAILED
        }

        static AgentEvent stateChanged(String agentId, AgentState oldState, AgentState newState) {
            return new AgentEvent(agentId, EventType.STATE_CHANGED, null, null, oldState, newState);
        }

        static AgentEvent jobAccepted(String agentId, String jobId) {
            return new AgentEvent(agentId, EventType.JOB_ACCEPTED, jobId, null, null, null);
        }

        static AgentEvent jobRejected(String agentId, String jobId, String reason) {
            return new AgentEvent(agentId, EventType.JOB_REJECTED, jobId, reason, null, null);
        }

        static AgentEvent jobStarted(String agentId, String jobId) {
            return new AgentEvent(agentId, EventType.JOB_STARTED, jobId, null, null, null);
        }

        static AgentEvent jobCompleted(String agentId, String jobId) {
            return new AgentEvent(agentId, EventType.JOB_COMPLETED, jobId, null, null, null);
        }

        static AgentEvent jobFailed(String agentId, String jobId, String error) {
            return new AgentEvent(agentId, EventType.JOB_FAILED, jobId, error, null, null);
        }

        static AgentEvent crashed(String agentId, String reason) {
            return new AgentEvent(agentId, EventType.CRASHED, null, reason, null, null);
        }

        static AgentEvent heartbeatFailed(String agentId, String error) {
            return new AgentEvent(agentId, EventType.HEARTBEAT_FAILED, null, error, null, null);
        }

        static AgentEvent pollingFailed(String agentId, String error) {
            return new AgentEvent(agentId, EventType.POLLING_FAILED, null, error, null, null);
        }

        static AgentEvent statusReportFailed(String agentId, String jobId, String error) {
            return new AgentEvent(agentId, EventType.STATUS_REPORT_FAILED, jobId, error, null, null);
        }
    }

    /**
     * Controller connection interface for agent-controller communication.
     */
    public interface ControllerConnection {
        /** Registers the agent */
        void registerAgent(AgentRegistration registration);
        
        /** Unregisters the agent */
        void unregisterAgent(String agentId);
        
        /** Sends heartbeat */
        void sendHeartbeat(HeartbeatInfo heartbeat);
        
        /** Polls for pending jobs */
        List<JobAssignment> pollPendingJobs(String agentId);
        
        /** Reports job status */
        void reportJobStatus(JobStatusUpdate update);
    }

    /**
     * Agent exception.
     */
    public static class AgentException extends Exception {
        public AgentException(String message) {
            super(message);
        }

        public AgentException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
