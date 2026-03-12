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
import dev.mars.quorus.agent.service.HeartbeatService;
import dev.mars.quorus.agent.service.TransferExecutionService;
import dev.mars.quorus.agent.service.HealthService;
import dev.mars.quorus.agent.service.JobPollingService;
import dev.mars.quorus.agent.service.JobStatusReportingService;
import dev.mars.quorus.core.TransferRequest;
import dev.mars.quorus.core.TransferResult;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

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
    private final boolean closeVertxOnShutdown;
    private final AgentConfiguration config;
    private final AgentRegistrationService registrationService;
    private final HeartbeatService heartbeatService;
    private final TransferExecutionService transferService;
    private final HealthService healthService;
    private final JobPollingService jobPollingService;
    private final JobStatusReportingService jobStatusReportingService;

    // OpenTelemetry metrics (Phase 6 - Jan 2026)
    private final AgentMetrics metrics;

    // Vert.x timer IDs for proper cleanup
    private long heartbeatTimerId = 0;
    private long jobPollingTimerId = 0;

    // Shutdown coordination
    private final AtomicBoolean closed = new AtomicBoolean(false);
    private volatile boolean running = false;
    private final io.vertx.core.Promise<Void> shutdownPromise = io.vertx.core.Promise.promise();
    private final AtomicInteger foreignAssignmentMismatchCount = new AtomicInteger(0);
    private final int foreignAssignmentMismatchThreshold;

    /**
     * Creates a QuorusAgent with Vert.x dependency injection.
     *
     * @param vertx the Vert.x instance (must not be null)
     * @param config the agent configuration (must not be null)
     * @throws NullPointerException if vertx or config is null
     */
    public QuorusAgent(Vertx vertx, AgentConfiguration config) {
        this(vertx, config, false);
    }

    private QuorusAgent(Vertx vertx, AgentConfiguration config, boolean closeVertxOnShutdown) {
        this.vertx = Objects.requireNonNull(vertx, "Vertx instance cannot be null");
        this.closeVertxOnShutdown = closeVertxOnShutdown;
        this.config = Objects.requireNonNull(config, "AgentConfiguration cannot be null");
        this.foreignAssignmentMismatchThreshold = AgentConfig.get().getForeignAssignmentMismatchThreshold();

        logger.info("Creating QuorusAgent with Vert.x instance: {} (using Vert.x timers, no ScheduledExecutorService)",
                    System.identityHashCode(vertx));

        // Initialize services with Vert.x for non-blocking HTTP 
        this.registrationService = new AgentRegistrationService(vertx, config);
        this.heartbeatService = new HeartbeatService(vertx, config, registrationService);
        this.transferService = new TransferExecutionService(vertx, config);  // Pass Vertx
        this.healthService = new HealthService(vertx, config);
        this.jobPollingService = new JobPollingService(vertx, config);
        this.jobStatusReportingService = new JobStatusReportingService(vertx, config);

        // Initialize OpenTelemetry metrics 
        this.metrics = new AgentMetrics(config.getAgentId(), System.currentTimeMillis());

        logger.info("Quorus Agent initialized: {} (reactive mode with OpenTelemetry)", config.getAgentId());
        logger.info("Foreign-assignment mismatch threshold configured to {}", foreignAssignmentMismatchThreshold);
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
        this(Vertx.vertx(), config, true);
        logger.warn("Using deprecated constructor - consider passing shared Vert.x instance");
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
            AgentConfiguration config = AgentConfiguration.fromEnvironment();
            AgentConfig.get().validate();

            // Create shared Vert.x instance with OpenTelemetry tracing
            VertxOptions options = new VertxOptions();
            options = AgentTelemetryConfig.configure(options, config.getAgentId());
            Vertx vertx = Vertx.vertx(options);
            
            logger.info("Created Vert.x instance with OpenTelemetry: {} (Prometheus on port {})", 
                    System.identityHashCode(vertx), AgentTelemetryConfig.getPrometheusPort());

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
                        logger.warn("Error closing Vert.x instance", ar.cause());
                    }
                });
            }));

            // Start the agent
            agent.start();

            // Wait for shutdown
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
    
    public void start() throws Exception {
        if (closed.get()) {
            throw new IllegalStateException("Agent is closed, cannot start");
        }

        // Set process-lifetime MDC context for all agent logs
        MDC.put("agentId", config.getAgentId());

        logger.info("Starting Quorus Agent services (Vert.x reactive mode)...");

        running = true;

        // Set metrics status
        metrics.setStatusRunning();

        // Start health service first (reactive)
        healthService.start()
            .onFailure(err -> {
                logger.error("Health service failed to start: {}", err.getMessage());
                metrics.setStatusError();
            });

        // Register with controller (reactive WebClient)
        registrationService.register()
            .onSuccess(registered -> {
                metrics.recordRegistration(registered);
                if (!registered) {
                    metrics.setStatusError();
                    logger.error("Failed to register with controller");
                    shutdown();
                    return;
                }
                logger.info("Agent registered successfully with controller");
                startBackgroundServices();
            })
            .onFailure(err -> {
                metrics.setStatusError();
                logger.error("Failed to register with controller: {}", err.getMessage());
                shutdown();
            });
    }
    
    private void startBackgroundServices() {
        // Guard against race condition: shutdown may have been called before registration callback fires
        if (closed.get() || !running) {
            logger.warn("Agent was shutdown before background services could start, aborting startup");
            return;
        }

        // Start heartbeat service using Vert.x timer (no ScheduledExecutorService!)
        heartbeatTimerId = vertx.setPeriodic(
            config.getHeartbeatInterval(),
            id -> {
                if (!closed.get() && running) {
                    heartbeatService.sendHeartbeat()
                        .onSuccess(success -> metrics.recordHeartbeat(success))
                        .onFailure(err -> {
                            logger.error("Error sending heartbeat: {}", err.getMessage());
                            logger.debug("Stack trace for heartbeat send failure", err);
                            metrics.recordHeartbeat(false);
                        });
                }
            }
        );
        logger.info("Heartbeat service started (interval: {}ms) [Vert.x timer ID: {}]",
                    config.getHeartbeatInterval(), heartbeatTimerId);

        // Start transfer execution service
        transferService.start();
        logger.info("Transfer execution service started");

        // Start job polling using Vert.x timer (no ScheduledExecutorService!)
        // Configurable initial delay and polling interval
        final AgentConfig agentConfig = AgentConfig.get();
        final long initialDelay = agentConfig.getJobPollingInitialDelayMs();
        final long pollingInterval = agentConfig.getJobPollingIntervalMs();
        
        vertx.setTimer(initialDelay, initialId -> {
            if (!closed.get() && running) {
                jobPollingTimerId = vertx.setPeriodic(pollingInterval, id -> {
                    if (!closed.get() && running) {
                        pollForJobs();
                    }
                });
                logger.info("Job polling started (interval: {}ms) [Vert.x timer ID: {}]", pollingInterval, jobPollingTimerId);
            }
        });

        logger.info("Quorus Agent started successfully (reactive mode, 0 extra threads)");
    }
    
    public void shutdown() {
        // Idempotent shutdown with AtomicBoolean
        if (closed.getAndSet(true)) {
            logger.warn("Agent already closed, skipping shutdown");
            return;
        }

        if (!running) {
            logger.info("Agent not running, performing cleanup only");
            closeOwnedVertxIfNeeded()
                    .onComplete(ar -> shutdownPromise.tryComplete());
            return;
        }

        logger.info("Shutting down Quorus Agent (reactive mode)...");
        running = false;
        metrics.setStatusStopped();

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
            transferService.shutdown()
                .onFailure(err -> logger.warn("Error shutting down transfer service: {}", err.getMessage()));
            heartbeatService.shutdown();
            jobPollingService.shutdown();
            jobStatusReportingService.shutdown();

            // Shutdown health service (reactive) then deregister from controller
            healthService.shutdown()
                .compose(v -> registrationService.deregister())
                .recover(err -> {
                    logger.warn("Failed during health shutdown/deregister sequence: {}", err.getMessage());
                    return io.vertx.core.Future.succeededFuture(Boolean.FALSE);
                })
                .compose(deregistered -> closeOwnedVertxIfNeeded().map(deregistered))
                .onComplete(ar -> {
                    if (ar.succeeded() && Boolean.TRUE.equals(ar.result())) {
                        logger.info("Agent deregistered from controller");
                    }
                    logger.info("Quorus Agent shutdown complete (0 threads to cleanup)");
                    shutdownPromise.tryComplete();
                });
            return; // Early return - promise will be completed in callback

        } catch (Exception e) {
            logger.error("Error during shutdown: {}", e.getMessage());
            logger.debug("Stack trace", e);
            closeOwnedVertxIfNeeded()
                    .onComplete(ar -> shutdownPromise.tryComplete());
        }
    }

    private io.vertx.core.Future<Void> closeOwnedVertxIfNeeded() {
        if (!closeVertxOnShutdown) {
            return io.vertx.core.Future.succeededFuture();
        }

        logger.info("Closing internally managed Vert.x instance for QuorusAgent");
        return vertx.close()
                .onSuccess(v -> logger.info("Internally managed Vert.x instance closed for QuorusAgent"))
                .recover(err -> {
                    logger.warn("Failed to close internally managed Vert.x instance: {}", err.getMessage());
                    return io.vertx.core.Future.succeededFuture();
                });
    }
    
    public void awaitShutdown() throws InterruptedException {
        shutdownPromise.future().toCompletionStage().toCompletableFuture().join();
    }
    
    private void pollForJobs() {
        if (!running) {
            return;
        }

        // Poll controller for new jobs (reactive migration)
        jobPollingService.pollForJobs()
            .onSuccess(pendingJobs -> {
                if (pendingJobs.isEmpty()) {
                    logger.debug("No pending jobs found");
                    return;
                }

                logger.info("Found {} pending job(s)", pendingJobs.size());
                metrics.recordJobPolled(pendingJobs.size());

                // Process each pending job
                for (JobPollingService.PendingJob pendingJob : pendingJobs) {
                    processJob(pendingJob);
                }
            })
            .onFailure(err -> logger.warn("Error polling for jobs: {}", err.getMessage()));
    }

    private void processJob(JobPollingService.PendingJob pendingJob) {
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

        logger.info("Processing job: {} ({})", jobId, pendingJob.getDescription());

        // Strict contract: do not execute unless ACCEPTED status is acknowledged by controller
        jobStatusReportingService.reportAccepted(jobId)
            .onSuccess(v -> {
                // Convert to transfer request
                TransferRequest request = pendingJob.toTransferRequest();

                // Record job started
                metrics.recordJobStarted();

                // Execute the transfer
                transferService.executeTransfer(request)
                    .onSuccess(result -> handleTransferComplete(jobId, result))
                    .onFailure(throwable -> handleTransferError(jobId, throwable));
            })
            .onFailure(err -> {
                logger.error("Refusing to execute job {} because ACCEPTED status was not acknowledged: {}",
                        jobId, err.getMessage());
            });
    }

    private void handleTransferComplete(String jobId, TransferResult result) {
        long durationSeconds = result.getDuration().map(d -> d.toSeconds()).orElse(0L);
        // Protocol and direction not available on TransferResult, use defaults for metrics
        String protocol = "unknown";
        String direction = "DOWNLOAD";
        
        if (result.isSuccessful()) {
            String durationStr = result.getDuration()
                    .map(d -> d.toMillis() + "ms")
                    .orElse("unknown");
            logger.info("Transfer completed successfully: {} ({} bytes in {})",
                       jobId,
                       result.getBytesTransferred(),
                       durationStr);
            jobStatusReportingService.reportCompleted(jobId, result.getBytesTransferred())
                    .onFailure(err -> logger.error("Failed to report COMPLETED for job {}: {}", jobId, err.getMessage()));
            metrics.recordJobCompleted(true, protocol, direction, result.getBytesTransferred(), durationSeconds);
        } else {
            String errorMessage = result.getErrorMessage().orElse("Unknown error");
            logger.warn("Transfer failed: {} - {}", jobId, errorMessage);
            jobStatusReportingService.reportFailed(jobId, errorMessage)
                    .onFailure(err -> logger.error("Failed to report FAILED for job {}: {}", jobId, err.getMessage()));
            metrics.recordJobCompleted(false, protocol, direction, 0, durationSeconds);
        }
    }

    private void handleTransferError(String jobId, Throwable throwable) {
        logger.error("Transfer error: {}: {}", jobId, throwable.getMessage());
        logger.debug("Stack trace for transfer error: jobId={}", jobId, throwable);
        jobStatusReportingService.reportFailed(jobId, throwable.getMessage())
                .onFailure(err -> logger.error("Failed to report FAILED for job {}: {}", jobId, err.getMessage()));
        metrics.recordJobCompleted(false, "unknown", "DOWNLOAD", 0, 0);
    }
    
    public boolean isRunning() {
        return running;
    }
    
    public AgentConfiguration getConfiguration() {
        return config;
    }
}
