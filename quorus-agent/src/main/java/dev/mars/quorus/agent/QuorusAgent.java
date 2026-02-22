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

    // OpenTelemetry metrics (Phase 6 - Jan 2026)
    private final AgentMetrics metrics;

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

        // Initialize services with Vert.x for non-blocking HTTP 
        this.registrationService = new AgentRegistrationService(vertx, config);
        this.heartbeatService = new HeartbeatService(vertx, config, registrationService);
        this.transferService = new TransferExecutionService(vertx, config);  // Pass Vertx
        this.healthService = new HealthService(config);
        this.jobPollingService = new JobPollingService(vertx, config);
        this.jobStatusReportingService = new JobStatusReportingService(vertx, config);

        // Initialize OpenTelemetry metrics 
        this.metrics = new AgentMetrics(config.getAgentId(), System.currentTimeMillis());

        logger.info("Quorus Agent initialized: {} (reactive mode with OpenTelemetry)", config.getAgentId());
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

        logger.info("Starting Quorus Agent services (Vert.x reactive mode)...");

        running = true;

        // Set metrics status
        metrics.setStatusRunning();

        // Start health service first
        healthService.start();
        logger.info("Health service started on port {}", config.getAgentPort());

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
                            logger.error("Error sending heartbeat", err);
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
            shutdownLatch.countDown();
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

            // Stop services (Future-based shutdown)
            transferService.shutdown();
            heartbeatService.shutdown();
            healthService.shutdown();
            jobPollingService.shutdown();
            jobStatusReportingService.shutdown();

            // Deregister from controller (reactive WebClient)
            registrationService.deregister()
                .onComplete(ar -> {
                    if (ar.succeeded() && ar.result()) {
                        logger.info("Agent deregistered from controller");
                    } else if (ar.failed()) {
                        logger.warn("Failed to deregister from controller: {}", ar.cause().getMessage());
                    }
                    logger.info("Quorus Agent shutdown complete (0 threads to cleanup)");
                    shutdownLatch.countDown();
                });
            return; // Early return - shutdownLatch will be counted down in callback

        } catch (Exception e) {
            logger.error("Error during shutdown: {}", e.getMessage());
            logger.debug("Stack trace", e);
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

        logger.info("Processing job: {} ({})", jobId, pendingJob.getDescription());

        // Report that we've accepted the job (reactive)
        jobStatusReportingService.reportAccepted(jobId)
            .onComplete(ar -> {
                // Convert to transfer request
                TransferRequest request = pendingJob.toTransferRequest();

                // Record job started
                metrics.recordJobStarted();

                // Execute the transfer
                transferService.executeTransfer(request)
                    .onSuccess(result -> handleTransferComplete(jobId, result))
                    .onFailure(throwable -> handleTransferError(jobId, throwable));
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
            jobStatusReportingService.reportCompleted(jobId, result.getBytesTransferred());
            metrics.recordJobCompleted(true, protocol, direction, result.getBytesTransferred(), durationSeconds);
        } else {
            String errorMessage = result.getErrorMessage().orElse("Unknown error");
            logger.warn("Transfer failed: {} - {}", jobId, errorMessage);
            jobStatusReportingService.reportFailed(jobId, errorMessage);
            metrics.recordJobCompleted(false, protocol, direction, 0, durationSeconds);
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
