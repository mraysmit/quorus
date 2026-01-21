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

import dev.mars.quorus.agent.config.AgentConfiguration;
import dev.mars.quorus.agent.service.AgentRegistrationService;
import dev.mars.quorus.agent.service.HeartbeatService;
import dev.mars.quorus.agent.service.TransferExecutionService;
import dev.mars.quorus.agent.service.HealthService;
import dev.mars.quorus.agent.service.JobPollingService;
import dev.mars.quorus.agent.service.JobStatusReportingService;
import dev.mars.quorus.core.TransferRequest;
import dev.mars.quorus.core.TransferResult;
import io.vertx.core.Vertx;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Main class for the Quorus Agent.
 * This agent registers with the Quorus controller cluster and executes file transfer tasks.
 *
 * <p>Vert.x 5 Migration: Refactored to use Vert.x dependency injection and reactive patterns.
 * Eliminates ScheduledExecutorService in favor of Vert.x timers for better integration
 * with the event loop and reduced thread overhead.</p>
 *
 * @author Mark Andrew Ray-Smith Cityline Ltd
 * @since 2025-09-04
 * @version 1.0
 */
public class QuorusAgent {

    private static final Logger logger = LoggerFactory.getLogger(QuorusAgent.class);

    private final Vertx vertx;
    private final AgentConfiguration config;
    private final AgentRegistrationService registrationService;
    private final HeartbeatService heartbeatService;
    private final TransferExecutionService transferService;
    private final HealthService healthService;
    private final JobPollingService jobPollingService;
    private final JobStatusReportingService jobStatusReportingService;

    // Vert.x timer IDs for proper cleanup
    private long heartbeatTimerId = 0;
    private long jobPollingTimerId = 0;

    // Shutdown coordination
    private final AtomicBoolean closed = new AtomicBoolean(false);
    private volatile boolean running = false;
    private final CountDownLatch shutdownLatch = new CountDownLatch(1);

    /**
     * Creates a QuorusAgent with Vert.x dependency injection.
     *
     * @param vertx the Vert.x instance (must not be null)
     * @param config the agent configuration (must not be null)
     * @throws NullPointerException if vertx or config is null
     */
    public QuorusAgent(Vertx vertx, AgentConfiguration config) {
        this.vertx = Objects.requireNonNull(vertx, "Vertx instance cannot be null");
        this.config = Objects.requireNonNull(config, "AgentConfiguration cannot be null");

        logger.info("Creating QuorusAgent with Vert.x instance: {} (using Vert.x timers, no ScheduledExecutorService)",
                    System.identityHashCode(vertx));

        // Initialize services
        this.registrationService = new AgentRegistrationService(config);
        this.heartbeatService = new HeartbeatService(config, registrationService);
        this.transferService = new TransferExecutionService(vertx, config);  // Pass Vertx
        this.healthService = new HealthService(config);
        this.jobPollingService = new JobPollingService(config);
        this.jobStatusReportingService = new JobStatusReportingService(config);

        logger.info("Quorus Agent initialized: {} (reactive mode)", config.getAgentId());
    }

    /**
     * Legacy constructor for backward compatibility.
     * Creates a new Vert.x instance internally.
     *
     * @param config the agent configuration
     * @deprecated Use {@link #QuorusAgent(Vertx, AgentConfiguration)} instead to share Vert.x instance
     */
    @Deprecated(since = "1.0", forRemoval = true)
    public QuorusAgent(AgentConfiguration config) {
        this(Vertx.vertx(), config);
        logger.warn("Using deprecated constructor - consider passing shared Vert.x instance");
    }
    
    public static void main(String[] args) {
        logger.info("Starting Quorus Agent...");

        // Create shared Vert.x instance
        Vertx vertx = Vertx.vertx();
        logger.info("Created Vert.x instance: {}", System.identityHashCode(vertx));

        try {
            // Load configuration from environment
            AgentConfiguration config = AgentConfiguration.fromEnvironment();

            // Create and start agent with Vert.x instance
            QuorusAgent agent = new QuorusAgent(vertx, config);

            // Setup shutdown hook
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                logger.info("Shutdown signal received");
                agent.shutdown();

                // Close Vert.x instance
                vertx.close().onComplete(ar -> {
                    if (ar.succeeded()) {
                        logger.info("Vert.x instance closed successfully");
                    } else {
                        logger.error("Error closing Vert.x instance", ar.cause());
                    }
                });
            }));

            // Start the agent
            agent.start();

            // Wait for shutdown
            agent.awaitShutdown();

        } catch (Exception e) {
            logger.error("Failed to start Quorus Agent", e);
            vertx.close();
            System.exit(1);
        }

        logger.info("Quorus Agent stopped");
    }
    
    public void start() throws Exception {
        if (closed.get()) {
            throw new IllegalStateException("Agent is closed, cannot start");
        }

        logger.info("Starting Quorus Agent services (Vert.x reactive mode)...");

        running = true;

        // Start health service first
        healthService.start();
        logger.info("Health service started on port {}", config.getAgentPort());

        // Register with controller
        boolean registered = registrationService.register();
        if (!registered) {
            throw new RuntimeException("Failed to register with controller");
        }
        logger.info("Agent registered successfully with controller");

        // Start heartbeat service using Vert.x timer (no ScheduledExecutorService!)
        heartbeatTimerId = vertx.setPeriodic(
            config.getHeartbeatInterval(),
            id -> {
                if (!closed.get() && running) {
                    try {
                        heartbeatService.sendHeartbeat();
                    } catch (Exception e) {
                        logger.error("Error sending heartbeat", e);
                    }
                }
            }
        );
        logger.info("Heartbeat service started (interval: {}ms) [Vert.x timer ID: {}]",
                    config.getHeartbeatInterval(), heartbeatTimerId);

        // Start transfer execution service
        transferService.start();
        logger.info("Transfer execution service started");

        // Start job polling using Vert.x timer (no ScheduledExecutorService!)
        // Initial delay of 5 seconds, then poll every 10 seconds
        vertx.setTimer(5000, initialId -> {
            if (!closed.get() && running) {
                jobPollingTimerId = vertx.setPeriodic(10000, id -> {
                    if (!closed.get() && running) {
                        try {
                            pollForJobs();
                        } catch (Exception e) {
                            logger.error("Error polling for jobs", e);
                        }
                    }
                });
                logger.info("Job polling started (interval: 10s) [Vert.x timer ID: {}]", jobPollingTimerId);
            }
        });

        logger.info("Quorus Agent started successfully (reactive mode, 0 extra threads)");
    }
    
    public void shutdown() {
        // Idempotent shutdown with AtomicBoolean
        if (closed.getAndSet(true)) {
            logger.info("Agent already closed, skipping shutdown");
            return;
        }

        if (!running) {
            logger.info("Agent not running, performing cleanup only");
            shutdownLatch.countDown();
            return;
        }

        logger.info("Shutting down Quorus Agent (reactive mode)...");
        running = false;

        try {
            // Cancel Vert.x timers (no ScheduledExecutorService to shutdown!)
            if (heartbeatTimerId != 0) {
                boolean cancelled = vertx.cancelTimer(heartbeatTimerId);
                logger.info("Heartbeat timer cancelled: {} [ID: {}]", cancelled, heartbeatTimerId);
                heartbeatTimerId = 0;
            }

            if (jobPollingTimerId != 0) {
                boolean cancelled = vertx.cancelTimer(jobPollingTimerId);
                logger.info("Job polling timer cancelled: {} [ID: {}]", cancelled, jobPollingTimerId);
                jobPollingTimerId = 0;
            }

            // Stop services
            transferService.shutdown();
            heartbeatService.shutdown();
            healthService.shutdown();
            jobPollingService.shutdown();
            jobStatusReportingService.shutdown();

            // Deregister from controller
            registrationService.deregister();

            logger.info("Quorus Agent shutdown complete (0 threads to cleanup)");

        } catch (Exception e) {
            logger.error("Error during shutdown", e);
        } finally {
            shutdownLatch.countDown();
        }
    }
    
    public void awaitShutdown() throws InterruptedException {
        shutdownLatch.await();
    }
    
    private void pollForJobs() {
        if (!running) {
            return;
        }

        try {
            // Poll controller for new jobs
            List<JobPollingService.PendingJob> pendingJobs = jobPollingService.pollForJobs();

            if (pendingJobs.isEmpty()) {
                logger.debug("No pending jobs found");
                return;
            }

            logger.info("Found {} pending job(s)", pendingJobs.size());

            // Process each pending job
            for (JobPollingService.PendingJob pendingJob : pendingJobs) {
                processJob(pendingJob);
            }

        } catch (Exception e) {
            logger.warn("Error polling for jobs", e);
        }
    }

    private void processJob(JobPollingService.PendingJob pendingJob) {
        String jobId = pendingJob.getJobId();

        try {
            logger.info("Processing job: {} ({})", jobId, pendingJob.getDescription());

            // Report that we've accepted the job
            jobStatusReportingService.reportAccepted(jobId);

            // Convert to transfer request
            TransferRequest request = pendingJob.toTransferRequest();

            // Execute the transfer
            transferService.executeTransfer(request)
                    .onSuccess(result -> handleTransferComplete(jobId, result))
                    .onFailure(throwable -> handleTransferError(jobId, throwable));

        } catch (Exception e) {
            logger.error("Error processing job: {}", jobId, e);
            jobStatusReportingService.reportFailed(jobId, e.getMessage());
        }
    }

    private void handleTransferComplete(String jobId, TransferResult result) {
        if (result.isSuccessful()) {
            String durationStr = result.getDuration()
                    .map(d -> d.toMillis() + "ms")
                    .orElse("unknown");
            logger.info("Transfer completed successfully: {} ({} bytes in {})",
                       jobId,
                       result.getBytesTransferred(),
                       durationStr);
            jobStatusReportingService.reportCompleted(jobId, result.getBytesTransferred());
        } else {
            String errorMessage = result.getErrorMessage().orElse("Unknown error");
            logger.warn("Transfer failed: {} - {}", jobId, errorMessage);
            jobStatusReportingService.reportFailed(jobId, errorMessage);
        }
    }

    private void handleTransferError(String jobId, Throwable throwable) {
        logger.error("Transfer error: {}", jobId, throwable);
        jobStatusReportingService.reportFailed(jobId, throwable.getMessage());
    }
    
    public boolean isRunning() {
        return running;
    }
    
    public AgentConfiguration getConfiguration() {
        return config;
    }
}
