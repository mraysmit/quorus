package dev.mars.quorus.transfer;

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


import dev.mars.quorus.core.TransferJob;
import dev.mars.quorus.core.TransferRequest;
import dev.mars.quorus.core.TransferResult;
import dev.mars.quorus.core.TransferStatus;
import dev.mars.quorus.core.exceptions.TransferException;
import dev.mars.quorus.monitoring.ProtocolHealthCheck;
import dev.mars.quorus.monitoring.TransferEngineHealthCheck;
import dev.mars.quorus.monitoring.TransferMetrics;
import dev.mars.quorus.protocol.ProtocolFactory;
import dev.mars.quorus.protocol.TransferProtocol;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.WorkerExecutor;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Simple implementation of the TransferEngine interface.
 * Handles basic file transfers with retry logic and progress tracking.
 * Converted to Vert.x WorkerExecutor (Phase 1.5 - Dec 2025).
 *
 * @author Mark Andrew Ray-Smith Cityline Ltd
 * @since 2025-08-17
 * @version 1.0
 */
public class SimpleTransferEngine implements TransferEngine {
    private static final Logger logger = Logger.getLogger(SimpleTransferEngine.class.getName());

    private final Vertx vertx;
    private final WorkerExecutor workerExecutor;
    private final ConcurrentHashMap<String, TransferJob> activeJobs;
    private final ConcurrentHashMap<String, TransferContext> activeContexts;
    private final ConcurrentHashMap<String, CompletableFuture<TransferResult>> activeFutures;
    private final ProtocolFactory protocolFactory;
    private final AtomicBoolean shutdown;

    // Configuration
    private final int maxConcurrentTransfers;
    private final int maxRetryAttempts;
    private final long retryDelayMs;

    // Monitoring (Phase 2 - Dec 2025)
    private final Instant startTime;
    private final Map<String, TransferMetrics> protocolMetrics;

    /**
     * Default constructor - creates internal Vert.x instance.
     * @deprecated Use {@link #SimpleTransferEngine(Vertx, int, int, long)} instead
     */
    @Deprecated
    public SimpleTransferEngine() {
        this(Vertx.vertx(), 10, 3, 1000);
        logger.warning("Using deprecated constructor - Vert.x instance created internally");
    }

    /**
     * Constructor without Vertx for backward compatibility.
     * @deprecated Use {@link #SimpleTransferEngine(Vertx, int, int, long)} instead
     */
    @Deprecated
    public SimpleTransferEngine(int maxConcurrentTransfers, int maxRetryAttempts, long retryDelayMs) {
        this(Vertx.vertx(), maxConcurrentTransfers, maxRetryAttempts, retryDelayMs);
        logger.warning("Using deprecated constructor - Vert.x instance created internally");
    }

    /**
     * Constructor with Vert.x dependency injection (recommended).
     *
     * @param vertx Vert.x instance for reactive operations
     * @param maxConcurrentTransfers Maximum number of concurrent transfers
     * @param maxRetryAttempts Maximum retry attempts per transfer
     * @param retryDelayMs Base delay between retries in milliseconds
     */
    public SimpleTransferEngine(Vertx vertx, int maxConcurrentTransfers, int maxRetryAttempts, long retryDelayMs) {
        this.vertx = Objects.requireNonNull(vertx, "Vertx cannot be null");
        this.maxConcurrentTransfers = maxConcurrentTransfers;
        this.maxRetryAttempts = maxRetryAttempts;
        this.retryDelayMs = retryDelayMs;

        // Create Vert.x worker executor instead of ExecutorService
        this.workerExecutor = vertx.createSharedWorkerExecutor(
                "quorus-transfer-pool",
                maxConcurrentTransfers,
                TimeUnit.MINUTES.toNanos(10)  // 10 minute max execution time
        );

        this.activeJobs = new ConcurrentHashMap<>();
        this.activeContexts = new ConcurrentHashMap<>();
        this.activeFutures = new ConcurrentHashMap<>();
        this.protocolFactory = new ProtocolFactory(vertx);  // Pass Vertx to ProtocolFactory
        this.shutdown = new AtomicBoolean(false);

        // Initialize monitoring (Phase 2 - Dec 2025)
        this.startTime = Instant.now();
        this.protocolMetrics = new ConcurrentHashMap<>();
        // Initialize metrics for all supported protocols
        protocolMetrics.put("http", new TransferMetrics("http"));
        protocolMetrics.put("ftp", new TransferMetrics("ftp"));
        protocolMetrics.put("sftp", new TransferMetrics("sftp"));
        protocolMetrics.put("smb", new TransferMetrics("smb"));

        logger.info("SimpleTransferEngine initialized with " + maxConcurrentTransfers +
                   " max concurrent transfers (Vert.x WorkerExecutor mode)");
    }
    
    @Override
    public CompletableFuture<TransferResult> submitTransfer(TransferRequest request) throws TransferException {
        if (shutdown.get()) {
            throw new TransferException(request.getRequestId(), "Transfer engine is shutdown");
        }
        
        if (activeJobs.size() >= maxConcurrentTransfers) {
            throw new TransferException(request.getRequestId(), "Maximum concurrent transfers reached");
        }
        
        // Create job and context
        TransferJob job = new TransferJob(request);
        TransferContext context = new TransferContext(job);
        
        // Store in active collections
        activeJobs.put(job.getJobId(), job);
        activeContexts.put(job.getJobId(), context);
        
        // Create promise to bridge Vert.x Future to CompletableFuture
        Promise<TransferResult> promise = Promise.promise();

        // Execute transfer on Vert.x worker pool
        workerExecutor.<TransferResult>executeBlocking(() -> {
            try {
                return executeTransfer(context);
            } catch (Exception e) {
                logger.log(Level.SEVERE, "Transfer execution failed for job " + job.getJobId(), e);
                job.fail("Transfer execution failed: " + e.getMessage(), e);
                return job.toResult();
            } finally {
                // Clean up
                activeJobs.remove(job.getJobId());
                activeContexts.remove(job.getJobId());
                activeFutures.remove(job.getJobId());
            }
        }).onComplete(ar -> {
            if (ar.succeeded()) {
                promise.complete(ar.result());
            } else {
                promise.fail(ar.cause());
            }
        });

        // Convert Vert.x Future to CompletableFuture
        CompletableFuture<TransferResult> future = promise.future()
                .toCompletionStage()
                .toCompletableFuture();

        activeFutures.put(job.getJobId(), future);

        logger.info("Transfer submitted: " + job.getJobId());
        return future;
    }
    
    @Override
    public TransferJob getTransferJob(String jobId) {
        return activeJobs.get(jobId);
    }
    
    @Override
    public boolean cancelTransfer(String jobId) {
        TransferContext context = activeContexts.get(jobId);
        CompletableFuture<TransferResult> future = activeFutures.get(jobId);
        
        if (context != null) {
            context.cancel();
            TransferJob job = context.getJob();
            if (job != null) {
                job.cancel();
            }
        }
        
        if (future != null) {
            future.cancel(true);
        }
        
        return context != null;
    }
    
    @Override
    public boolean pauseTransfer(String jobId) {
        TransferContext context = activeContexts.get(jobId);
        if (context != null) {
            context.pause();
            TransferJob job = context.getJob();
            if (job != null) {
                job.pause();
            }
            return true;
        }
        return false;
    }
    
    @Override
    public boolean resumeTransfer(String jobId) {
        TransferContext context = activeContexts.get(jobId);
        if (context != null) {
            context.resume();
            TransferJob job = context.getJob();
            if (job != null) {
                job.resume();
            }
            return true;
        }
        return false;
    }
    
    @Override
    public int getActiveTransferCount() {
        return activeJobs.size();
    }
    
    @Override
    public boolean shutdown(long timeoutSeconds) {
        if (shutdown.getAndSet(true)) {
            return true; // Already shutdown
        }

        logger.info("Shutting down transfer engine...");

        // Cancel all active transfers
        activeContexts.values().forEach(TransferContext::cancel);

        // Close Vert.x worker executor (non-blocking)
        workerExecutor.close();

        logger.info("Transfer engine shutdown completed (Vert.x WorkerExecutor closed)");
        return true;
    }

    @Override
    public TransferEngineHealthCheck getHealthCheck() {
        TransferEngineHealthCheck.Builder builder = TransferEngineHealthCheck.builder();

        // Check if engine is shutdown
        if (shutdown.get()) {
            return builder
                    .down()
                    .message("Transfer engine is shutdown")
                    .build();
        }

        // Check protocol health
        boolean allProtocolsHealthy = true;
        for (String protocolName : protocolMetrics.keySet()) {
            ProtocolHealthCheck.Builder protocolBuilder = ProtocolHealthCheck.builder(protocolName);

            TransferMetrics metrics = protocolMetrics.get(protocolName);
            Map<String, Object> metricsMap = metrics.toMap();

            // Determine protocol health based on metrics
            long totalTransfers = (long) metricsMap.get("totalTransfers");
            long failedTransfers = (long) metricsMap.get("failedTransfers");

            if (totalTransfers > 0) {
                double failureRate = (failedTransfers * 100.0) / totalTransfers;
                if (failureRate > 50) {
                    protocolBuilder.down()
                            .message("High failure rate: " + String.format("%.2f%%", failureRate));
                    allProtocolsHealthy = false;
                } else if (failureRate > 20) {
                    protocolBuilder.degraded()
                            .message("Elevated failure rate: " + String.format("%.2f%%", failureRate));
                    allProtocolsHealthy = false;
                } else {
                    protocolBuilder.up()
                            .message("Protocol operational");
                }
            } else {
                protocolBuilder.up()
                        .message("No transfers yet");
            }

            protocolBuilder.detail("totalTransfers", totalTransfers)
                    .detail("failedTransfers", failedTransfers)
                    .detail("activeTransfers", metricsMap.get("activeTransfers"));

            builder.addProtocolHealthCheck(protocolBuilder.build());
        }

        // System metrics
        Runtime runtime = Runtime.getRuntime();
        builder.systemMetric("activeTransfers", activeJobs.size())
                .systemMetric("maxConcurrentTransfers", maxConcurrentTransfers)
                .systemMetric("uptime", Duration.between(startTime, Instant.now()).toString())
                .systemMetric("memoryUsedMB", (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024))
                .systemMetric("memoryTotalMB", runtime.totalMemory() / (1024 * 1024))
                .systemMetric("memoryMaxMB", runtime.maxMemory() / (1024 * 1024));

        // Overall status
        if (allProtocolsHealthy) {
            builder.up().message("All systems operational");
        } else {
            builder.degraded().message("Some protocols experiencing issues");
        }

        return builder.build();
    }

    @Override
    public TransferMetrics getProtocolMetrics(String protocolName) {
        return protocolMetrics.get(protocolName);
    }

    @Override
    public Map<String, TransferMetrics> getAllProtocolMetrics() {
        return new HashMap<>(protocolMetrics);
    }

    private TransferResult executeTransfer(TransferContext context) {
        TransferJob job = context.getJob();
        TransferRequest request = job.getRequest();

        logger.info("Starting transfer: " + job.getJobId());
        job.start();

        // Record transfer start in metrics
        String protocolName = request.getProtocol();
        TransferMetrics metrics = protocolMetrics.get(protocolName);
        if (metrics != null) {
            metrics.recordTransferStart();
        }

        Instant transferStartTime = Instant.now();
        int attempt = 0;
        Exception lastException = null;

        while (attempt <= maxRetryAttempts && context.shouldContinue()) {
            try {
                // Get appropriate protocol
                TransferProtocol protocol = protocolFactory.getProtocol(request.getProtocol());
                if (protocol == null) {
                    throw new TransferException(job.getJobId(), "Unsupported protocol: " + request.getProtocol());
                }

                // Execute the transfer
                TransferResult result = protocol.transfer(request, context);

                if (result.isSuccessful()) {
                    logger.info("Transfer completed successfully: " + job.getJobId());

                    // Record success in metrics
                    if (metrics != null) {
                        Duration duration = Duration.between(transferStartTime, Instant.now());
                        long bytesTransferred = result.getBytesTransferred();
                        metrics.recordTransferSuccess(bytesTransferred, duration);
                    }

                    return result;
                } else {
                    throw new TransferException(job.getJobId(), "Transfer failed: " + result.getErrorMessage().orElse("Unknown error"));
                }

            } catch (Exception e) {
                lastException = e;
                attempt++;
                context.incrementRetryCount();

                logger.log(Level.WARNING, String.format("Transfer attempt %d failed for job %s: %s",
                        attempt, job.getJobId(), e.getMessage()), e);

                if (attempt <= maxRetryAttempts && context.shouldContinue()) {
                    try {
                        Thread.sleep(retryDelayMs * attempt); // Exponential backoff
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        context.cancel();
                        break;
                    }
                }
            }
        }

        // All attempts failed
        String errorMessage = lastException != null ? lastException.getMessage() : "Transfer failed after " + maxRetryAttempts + " attempts";
        job.fail(errorMessage, lastException);

        // Record failure in metrics
        if (metrics != null) {
            String errorType = lastException != null ? lastException.getClass().getSimpleName() : "UnknownError";
            metrics.recordTransferFailure(errorType);
        }

        logger.severe("Transfer failed permanently: " + job.getJobId() + " - " + errorMessage);
        return job.toResult();
    }
}
