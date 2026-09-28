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

package dev.mars.quorus.agent;

import dev.mars.quorus.agent.config.AgentConfig;
import dev.mars.quorus.agent.config.AgentConfiguration;
import dev.mars.quorus.agent.observability.AgentMetrics;
import dev.mars.quorus.agent.observability.AgentTelemetryConfig;
import dev.mars.quorus.agent.service.AgentRegistrationService;
import dev.mars.quorus.agent.service.ControllerClient;
import dev.mars.quorus.agent.service.HealthService;
import dev.mars.quorus.agent.service.HeartbeatService;
import dev.mars.quorus.agent.service.JobPollingService;
import dev.mars.quorus.agent.service.JobStatusReportingService;
import dev.mars.quorus.agent.service.JobStatusReportingService.StatusReportException;
import dev.mars.quorus.agent.service.TransferExecutionService;
import dev.mars.quorus.core.TransferAttemptStatus;
import dev.mars.quorus.core.TransferResult;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

/**
 * Main class for the Quorus Agent. The agent registers with the Quorus controller, sends heartbeats,
 * polls for assignments and runs their transfers.
 *
 * <p>Runtime (RT-05b): everything runs on virtual threads owned by the agent, with no framework.
 * <ul>
 *   <li>A runtime thread registers; if registration fails, the agent shuts down.</li>
 *   <li>A heartbeat loop and a job-polling loop then run at their configured intervals.</li>
 *   <li>Each accepted assignment runs on its own job thread: start acknowledgements, the transfer,
 *       periodic progress reports (on a reporter thread) and the final report.</li>
 * </ul>
 * {@link #shutdown()} stops the loops, closes status reporting, cancels running transfers (the
 * interrupt breaks a transfer blocked in a socket read at once, because it runs on a virtual thread),
 * waits a bounded time for job threads, stops the health endpoint and deregisters. It returns at once;
 * {@link #awaitShutdown()} waits for it to finish.
 *
 * @author Mark Andrew Ray-Smith Cityline Ltd
 * @since 2025-09-04
 * @version 2.0
 */
public class QuorusAgent {

    private static final Logger logger = LoggerFactory.getLogger(QuorusAgent.class);

    /** How long shutdown waits for the heartbeat, polling and runtime threads to end. */
    static final Duration LOOP_STOP_BOUND = Duration.ofSeconds(5);
    /** How long shutdown waits for job threads after their transfers were cancelled. */
    static final Duration JOB_STOP_BOUND = Duration.ofSeconds(15);

    private final AgentConfiguration config;
    private final ControllerClient controllerClient;
    private final AgentRegistrationService registrationService;
    private final HeartbeatService heartbeatService;
    private final TransferExecutionService transferService;
    private final HealthService healthService;
    private final JobPollingService jobPollingService;
    private final JobStatusReportingService jobStatusReportingService;

    // OpenTelemetry metrics (Phase 6 - Jan 2026)
    private final AgentMetrics metrics;

    // Shutdown coordination
    private final AtomicBoolean closed = new AtomicBoolean(false);
    private volatile boolean running = false;
    private final CountDownLatch stopSignal = new CountDownLatch(1);
    private final CountDownLatch stopped = new CountDownLatch(1);
    private Thread runtimeThread;
    private Thread heartbeatThread;
    private Thread pollingThread;
    private final Set<Thread> jobThreads = ConcurrentHashMap.newKeySet();

    private final AtomicInteger foreignAssignmentMismatchCount = new AtomicInteger(0);
    private final int foreignAssignmentMismatchThreshold;
    // Keep completed/unresolved claims until their lease ends: a stale poll must not
    // start the same fenced attempt again. This is process-local, not a durable outbox.
    private final ConcurrentMap<AttemptIdentity, Instant> claimedAttempts = new ConcurrentHashMap<>();

    private record AttemptIdentity(String jobId, String attemptId, long generation) { }

    /**
     * Creates an agent. Nothing starts until {@link #start()}.
     *
     * @throws NullPointerException  if config is null
     * @throws IllegalStateException if the controller TLS material cannot be loaded
     */
    public QuorusAgent(AgentConfiguration config) {
        this.config = Objects.requireNonNull(config, "AgentConfiguration cannot be null");
        this.foreignAssignmentMismatchThreshold = config.getForeignAssignmentMismatchThreshold();
        this.controllerClient = ControllerClient.create(config);
        this.registrationService = new AgentRegistrationService(controllerClient, config);
        this.heartbeatService = new HeartbeatService(controllerClient, config, registrationService);
        this.transferService = new TransferExecutionService(config);
        this.healthService = new HealthService(config);
        this.jobPollingService = new JobPollingService(controllerClient, config);
        this.jobStatusReportingService = new JobStatusReportingService(controllerClient, config);
        this.metrics = new AgentMetrics(config.getAgentId(), System.currentTimeMillis());

        logger.info("Quorus Agent initialized: {}", config.getAgentId());
        logger.info("Foreign-assignment mismatch threshold configured to {}", foreignAssignmentMismatchThreshold);
    }

    private static final String BANNER = """

              ██████  ██    ██  ██████  ██████  ██    ██ ███████
             ██    ██ ██    ██ ██    ██ ██   ██ ██    ██ ██
             ██    ██ ██    ██ ██    ██ ██████  ██    ██ ███████
             ██ ▄▄ ██ ██    ██ ██    ██ ██   ██ ██    ██      ██
              ██████   ██████   ██████  ██   ██  ██████  ███████
                 ▀▀                          Agent
            """;

    public static void main(String[] args) {
        System.out.println(BANNER);
        logger.info("Starting Quorus Agent with OpenTelemetry...");

        try {
            // Load and validate configuration (fail fast on misconfiguration)
            AgentConfig sourceConfig = new AgentConfig("default", new Properties());
            AgentConfiguration config = AgentConfiguration.from(sourceConfig);

            Optional<OpenTelemetrySdk> telemetry = AgentTelemetryConfig.configure(config);
            logger.info("Telemetry {} (Prometheus on port {})",
                    telemetry.isPresent() ? "enabled" : "disabled", config.getPrometheusPort());

            QuorusAgent agent = new QuorusAgent(config);

            // The JVM exits when the hook returns, so it waits for the agent to stop and deregister.
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                logger.info("Shutdown signal received");
                agent.shutdown();
                try {
                    agent.awaitShutdown(TransferExecutionService.SHUTDOWN_BOUND.plus(JOB_STOP_BOUND));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                telemetry.ifPresent(OpenTelemetrySdk::close);
            }, "quorus-agent-shutdown-hook"));

            agent.start();
            agent.awaitShutdown();

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Agent interrupted: {}", e.getMessage());
            System.exit(1);
        } catch (Exception e) {
            logger.error("Failed to start Quorus Agent: {}", e.getMessage());
            logger.debug("Stack trace", e);
            System.exit(1);
        }

        logger.info("Quorus Agent stopped");
    }

    /**
     * Starts the health endpoint and the runtime thread, which registers with the controller and then
     * starts the heartbeat and polling loops. Returns at once.
     *
     * @throws IllegalStateException if the agent was shut down
     */
    public void start() {
        if (closed.get()) {
            throw new IllegalStateException("Agent is closed, cannot start");
        }
        logger.info("Starting Quorus Agent services...");
        running = true;
        metrics.setStatusRunning();

        try {
            healthService.start();
        } catch (IOException e) {
            logger.error("Health service failed to start: {}", e.getMessage());
            metrics.setStatusError();
        }

        synchronized (this) {
            runtimeThread = Thread.ofVirtual().name("quorus-agent-runtime").start(() -> withMdc(this::registerAndRun));
        }
    }

    private void registerAndRun() {
        boolean registered;
        try {
            registered = registrationService.register();
        } catch (InterruptedException e) {
            return; // shutting down
        }
        metrics.recordRegistration(registered);
        if (!registered) {
            metrics.setStatusError();
            logger.error("Failed to register with controller");
            shutdown();
            return;
        }
        logger.info("Agent registered successfully with controller");
        startBackgroundServices();
    }

    private synchronized void startBackgroundServices() {
        // Shutdown may have begun while registration was in flight.
        if (closed.get() || !running) {
            logger.warn("Agent was shutdown before background services could start, aborting startup");
            return;
        }
        transferService.start();
        heartbeatThread = loop("quorus-agent-heartbeat", config.getHeartbeatInterval(),
                config.getHeartbeatInterval(), this::sendHeartbeat);
        pollingThread = loop("quorus-agent-job-polling", config.getJobPollingInitialDelayMs(),
                config.getJobPollingIntervalMs(), this::pollForJobs);
        logger.info("Quorus Agent started (heartbeat every {}ms, polling every {}ms after {}ms)",
                config.getHeartbeatInterval(), config.getJobPollingIntervalMs(), config.getJobPollingInitialDelayMs());
    }

    @FunctionalInterface
    private interface Tick {
        void run() throws InterruptedException;
    }

    /** A virtual thread that runs {@code tick} after {@code initialDelayMs} and then every {@code intervalMs}. */
    private Thread loop(String name, long initialDelayMs, long intervalMs, Tick tick) {
        return Thread.ofVirtual().name(name).start(() -> withMdc(() -> {
            try {
                long delay = initialDelayMs;
                while (!stopSignal.await(delay, TimeUnit.MILLISECONDS)) {
                    try {
                        tick.run();
                    } catch (RuntimeException e) {
                        logger.error("{} failed: {}", name, e.getMessage());
                        logger.debug("Stack trace for {} failure", name, e);
                    }
                    delay = intervalMs;
                }
            } catch (InterruptedException e) {
                // Shutting down.
            }
        }));
    }

    private void sendHeartbeat() throws InterruptedException {
        metrics.recordHeartbeat(heartbeatService.sendHeartbeat());
    }

    /**
     * Stops the agent. Returns at once; the work runs on its own thread, so this may be called from
     * any agent thread. Does nothing after the first call.
     */
    public void shutdown() {
        if (closed.getAndSet(true)) {
            logger.warn("Agent already closed, skipping shutdown");
            return;
        }
        Thread.ofVirtual().name("quorus-agent-shutdown").start(() -> withMdc(this::stop));
    }

    private void stop() {
        try {
            logger.info("Shutting down Quorus Agent...");
            running = false;
            metrics.setStatusStopped();
            stopSignal.countDown();
            List<Thread> loops;
            synchronized (this) {
                loops = Stream.of(runtimeThread, heartbeatThread, pollingThread).filter(Objects::nonNull).toList();
            }
            loops.forEach(Thread::interrupt);
            awaitThreads(loops, LOOP_STOP_BOUND, "agent loop");

            // Reports stop first; the attempts of cancelled transfers are reconciled by lease expiry.
            jobStatusReportingService.shutdown();
            transferService.shutdown();
            awaitThreads(List.copyOf(jobThreads), JOB_STOP_BOUND, "job");

            healthService.shutdown();
            boolean wasRegistered = registrationService.isRegistered();
            if (wasRegistered && registrationService.deregister()) {
                logger.info("Agent deregistered from controller");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.warn("Agent shutdown interrupted");
        } catch (RuntimeException e) {
            logger.error("Error during shutdown: {}", e.getMessage());
            logger.debug("Stack trace", e);
        } finally {
            controllerClient.close();
            logger.info("Quorus Agent shutdown complete");
            stopped.countDown();
        }
    }

    private static void awaitThreads(List<Thread> threads, Duration bound, String kind) throws InterruptedException {
        long deadline = System.nanoTime() + bound.toNanos();
        for (Thread thread : threads) {
            long remaining = deadline - System.nanoTime();
            if (remaining <= 0 || !thread.join(Duration.ofNanos(remaining))) {
                logger.warn("A {} thread did not end within {}: {}", kind, bound, thread.getName());
            }
        }
    }

    /** Waits until the agent has shut down. */
    public void awaitShutdown() throws InterruptedException {
        stopped.await();
    }

    /** Waits until the agent has shut down, for at most {@code timeout}; returns whether it has. */
    public boolean awaitShutdown(Duration timeout) throws InterruptedException {
        return stopped.await(timeout.toNanos(), TimeUnit.NANOSECONDS);
    }

    private void pollForJobs() throws InterruptedException {
        if (!running) {
            return;
        }
        List<JobPollingService.PendingJob> pendingJobs = jobPollingService.pollForJobs();
        if (pendingJobs.isEmpty()) {
            logger.debug("No pending jobs found");
            return;
        }
        logger.info("Found {} pending job(s)", pendingJobs.size());
        metrics.recordJobPolled(pendingJobs.size());
        for (JobPollingService.PendingJob pendingJob : pendingJobs) {
            processJob(pendingJob);
        }
    }

    /** Checks an assignment on the polling thread and, if it is this agent's, starts its job thread. */
    private void processJob(JobPollingService.PendingJob pendingJob) {
        if (closed.get()) return;
        String jobId = pendingJob.getJobId();
        String assignedAgentId = pendingJob.getAgentId();

        if (assignedAgentId == null || !config.getAgentId().equals(assignedAgentId)) {
            int mismatchCount = foreignAssignmentMismatchCount.incrementAndGet();
            metrics.recordForeignAssignmentMismatch(assignedAgentId);
            logger.error("Refusing to process job {} because assignment agentId {} does not match local agentId {}",
                    jobId, assignedAgentId, config.getAgentId());
            logger.error("Foreign assignment mismatch count: {}/{}", mismatchCount, foreignAssignmentMismatchThreshold);

            if (mismatchCount >= foreignAssignmentMismatchThreshold) {
                logger.error("Foreign assignment mismatch threshold reached ({}). Initiating fail-fast shutdown.",
                foreignAssignmentMismatchThreshold);
                metrics.setStatusError();
                shutdown();
            }
            return;
        }

        if (pendingJob.hasAttemptContext()) {
            Instant now = Instant.now();
            claimedAttempts.entrySet().removeIf(entry -> !now.isBefore(entry.getValue()));
            if (!now.isBefore(pendingJob.getLeaseExpiresAt())) return;
            AttemptIdentity identity = new AttemptIdentity(jobId, pendingJob.getAttemptId(), pendingJob.getFencingGeneration());
            if (claimedAttempts.putIfAbsent(identity, pendingJob.getLeaseExpiresAt()) != null) return;
        }

        logger.info("Processing job: {} ({})", jobId, pendingJob.getDescription());
        Thread job = Thread.ofVirtual().name("quorus-agent-job-" + jobId).unstarted(() -> withMdc(() -> {
            try {
                runJob(pendingJob);
            } finally {
                jobThreads.remove(Thread.currentThread());
            }
        }));
        jobThreads.add(job);
        job.start();
    }

    /** Runs one assignment on its job thread, from the start acknowledgements to the final report. */
    private void runJob(JobPollingService.PendingJob pendingJob) {
        String jobId = pendingJob.getJobId();
        // Strict contract: do not execute unless both lifecycle acknowledgements are
        // durably accepted by the controller. This keeps the authoritative assignment
        // state aligned with the real transfer process before any bytes are moved.
        try {
            reportAccepted(pendingJob);
        } catch (StatusReportException e) {
            logger.error("Refusing to execute job {} because its start lifecycle was not acknowledged: {}",
                    jobId, e.getMessage());
            return;
        } catch (InterruptedException e) {
            return;
        }
        metrics.recordJobStarted();

        // Resolve policy and credentials first; IN_PROGRESS is the durable
        // evidence that the governed connection was actually used.
        AtomicBoolean started = new AtomicBoolean();
        ProgressReporter progress = new ProgressReporter(pendingJob);
        TransferResult result;
        try {
            result = transferService.executeTransfer(pendingJob, () -> {
                reportInProgress(pendingJob, 0L);
                started.set(true);
                progress.start();
            });
        } catch (InterruptedException e) {
            progress.stopQuietly();
            return;
        } catch (Exception failure) {
            // The final report is sent only after any progress report has settled, so
            // report sequences reach the controller in order.
            if (progress.stopQuietly()) {
                handleTransferError(pendingJob, failure, started.get());
            }
            return;
        }
        if (progress.stopQuietly()) {
            handleTransferComplete(pendingJob, result);
        }
    }

    private void handleTransferComplete(JobPollingService.PendingJob pendingJob, TransferResult result) {
        String jobId = pendingJob.getJobId();
        long durationSeconds = result.getDuration().map(d -> d.toSeconds()).orElse(0L);
        // Protocol and direction not available on TransferResult, use defaults for metrics
        String protocol = "unknown";
        String direction = "DOWNLOAD";

        try {
            if (result.isSuccessful()) {
                String durationStr = result.getDuration()
                        .map(d -> d.toMillis() + "ms")
                        .orElse("unknown");
                logger.info("Transfer completed successfully: {} ({} bytes in {})",
                           jobId, result.getBytesTransferred(), durationStr);
                metrics.recordJobCompleted(true, protocol, direction, result.getBytesTransferred(), durationSeconds);
                reportCompleted(pendingJob, result.getBytesTransferred());
            } else {
                String errorMessage = result.getErrorMessage().orElse("Unknown error");
                logger.warn("Transfer failed: {} - {}", jobId, errorMessage);
                metrics.recordJobCompleted(false, protocol, direction, 0, durationSeconds);
                reportFailed(pendingJob, errorMessage, TransferAttemptStatus.IN_PROGRESS);
            }
        } catch (StatusReportException e) {
            logger.error("Failed to report the outcome of job {}: {}", jobId, e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void handleTransferError(JobPollingService.PendingJob pendingJob, Throwable throwable, boolean started) {
        String jobId = pendingJob.getJobId();
        if (throwable instanceof StatusReportException) {
            // The controller may have committed the report even though its reply was lost.
            // Never advance the sequence or guess a FAILED transition after an unresolved start.
            logger.error("Job {} was not executed because its start report was rejected or unresolved: {}",
                    jobId, throwable.getMessage());
            return;
        }
        logger.error("Transfer error: {}: {}", jobId, throwable.getMessage());
        logger.debug("Stack trace for transfer error: jobId={}", jobId, throwable);
        metrics.recordJobCompleted(false, "unknown", "DOWNLOAD", 0, 0);
        try {
            reportFailed(pendingJob, throwable.getMessage(), started
                    ? TransferAttemptStatus.IN_PROGRESS : TransferAttemptStatus.ACCEPTED);
        } catch (StatusReportException e) {
            logger.error("Failed to report FAILED for job {}: {}", jobId, e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Reports a running transfer's growing byte count to the controller (ENG-10), so progress,
     * freshness and stall detection see the transfer between its start and final reports.
     *
     * <p>A reporter thread wakes every progress interval, reads the engine job's byte count, and sends
     * a report when it has grown; sends are sequential, so one report is in flight at a time. Each
     * report takes the next sequence number. A report that stays unresolved after its bounded retries
     * may or may not have been applied, so no further progress is reported and {@link #stop()} resends
     * it exactly before the final report: an exact resend is idempotent if it was applied, and closes
     * the sequence gap if it was not. Legacy jobs without an attempt context get no progress reports.
     */
    private final class ProgressReporter {
        private final JobPollingService.PendingJob job;
        private final CountDownLatch stopRequested = new CountDownLatch(1);
        private Thread thread;
        private long lastReportedBytes;
        private long[] unresolved;                     // {bytes, sequence} of a report not yet settled

        ProgressReporter(JobPollingService.PendingJob job) {
            this.job = job;
        }

        void start() {
            if (job.hasAttemptContext()) {
                thread = Thread.ofVirtual().name("quorus-agent-progress-" + job.getJobId()).start(() -> withMdc(() -> {
                    try {
                        while (!stopRequested.await(config.getProgressReportIntervalMs(), TimeUnit.MILLISECONDS)) {
                            if (unresolved == null) {
                                reportIfGrown();
                            }
                        }
                    } catch (InterruptedException e) {
                        // Stopped.
                    }
                }));
            }
        }

        private void reportIfGrown() throws InterruptedException {
            long bytes = transferService.transferredBytes(job.getJobId());
            if (bytes <= lastReportedBytes) {
                return;
            }
            lastReportedBytes = bytes;
            send(bytes, job.nextReportSequence());
        }

        private void send(long bytes, long sequence) throws InterruptedException {
            try {
                jobStatusReportingService.reportProgress(job.getJobId(), bytes, job.getAttemptId(),
                        job.getFencingGeneration(), sequence);
                unresolved = null;
            } catch (StatusReportException e) {
                logger.warn("Progress report unresolved for job {} (sequence {}): {}",
                        job.getJobId(), sequence, e.getMessage());
                unresolved = new long[]{bytes, sequence};
            }
        }

        /** Stops reporting: waits for a report in flight, then resends an unresolved one exactly. */
        void stop() throws InterruptedException {
            if (thread == null) {
                return;
            }
            stopRequested.countDown();
            thread.join();                               // the join makes the reporter's fields visible here
            if (unresolved != null) {
                send(unresolved[0], unresolved[1]);
            }
        }

        /** {@link #stop()}, reporting whether the job thread may go on to its final report. */
        boolean stopQuietly() {
            try {
                stop();
                return true;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
    }

    private void reportAccepted(JobPollingService.PendingJob job) throws InterruptedException {
        if (!job.hasAttemptContext()) {
            jobStatusReportingService.reportAccepted(job.getJobId());
            return;
        }
        jobStatusReportingService.reportAccepted(job.getJobId(), job.getAttemptId(),
                job.getFencingGeneration(), job.nextReportSequence());
    }

    private void reportInProgress(JobPollingService.PendingJob job, long bytesTransferred) throws InterruptedException {
        if (!job.hasAttemptContext()) {
            jobStatusReportingService.reportInProgress(job.getJobId(), bytesTransferred);
            return;
        }
        jobStatusReportingService.reportInProgress(job.getJobId(), bytesTransferred,
                job.getAttemptId(), job.getFencingGeneration(), job.nextReportSequence());
    }

    private void reportCompleted(JobPollingService.PendingJob job, long bytesTransferred) throws InterruptedException {
        if (!job.hasAttemptContext()) {
            jobStatusReportingService.reportCompleted(job.getJobId(), bytesTransferred);
            return;
        }
        jobStatusReportingService.reportCompleted(job.getJobId(), bytesTransferred,
                job.getAttemptId(), job.getFencingGeneration(), job.nextReportSequence());
    }

    private void reportFailed(JobPollingService.PendingJob job, String reason, TransferAttemptStatus expectedState)
            throws InterruptedException {
        if (!job.hasAttemptContext()) {
            jobStatusReportingService.reportFailed(job.getJobId(), reason);
            return;
        }
        jobStatusReportingService.reportFailed(job.getJobId(), reason, job.getAttemptId(),
                job.getFencingGeneration(), job.nextReportSequence(), expectedState);
    }

    /** Runs {@code body} with this agent's id in the logging context of the current (virtual) thread. */
    private void withMdc(Runnable body) {
        MDC.put("agentId", config.getAgentId());
        try {
            body.run();
        } finally {
            MDC.remove("agentId");
        }
    }

    public boolean isRunning() {
        return running;
    }

    /** The port of the agent's health endpoint, or -1 if it is not running. */
    public int healthPort() {
        return healthService.port();
    }

    public AgentConfiguration getConfiguration() {
        return config;
    }
}
