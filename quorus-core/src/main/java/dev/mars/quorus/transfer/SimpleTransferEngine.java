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


import dev.mars.quorus.core.TransferDirection;
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
import dev.mars.quorus.transfer.observability.TransferTelemetryMetrics;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Simple implementation of the TransferEngine interface.
 * Handles basic file transfers with retry logic and progress tracking.
 * Uses Vert.x Futures/timers for non-blocking execution.
 *
 * @author Mark Andrew Ray-Smith Cityline Ltd
 * @since 2025-08-17
 * @version 1.0
 */
public class SimpleTransferEngine implements TransferEngine {
    private static final Logger logger = LoggerFactory.getLogger(SimpleTransferEngine.class);

    private final Vertx vertx;
    private final boolean closeVertxOnShutdown;
    private final ConcurrentHashMap<String, TransferJob> activeJobs;
    private final ConcurrentHashMap<String, TransferContext> activeContexts;
    private final ConcurrentHashMap<String, Future<TransferResult>> activeFutures;
    private final ProtocolFactory protocolFactory;
    private final AtomicBoolean shutdown;

    // Configuration
    private final int maxConcurrentTransfers;
    private final int maxRetryAttempts;
    private final long retryDelayMs;

    // Monitoring (Phase 2 - Dec 2025)
    private final Instant startTime;
    private final Map<String, TransferMetrics> protocolMetrics;

    // OpenTelemetry Metrics (Phase 8 - Jan 2026)
    private final TransferTelemetryMetrics telemetryMetrics;

    /**
     * Constructor with Vert.x dependency injection (recommended).
     *
     * @param vertx Vert.x instance for reactive operations
     * @param maxConcurrentTransfers Maximum number of concurrent transfers
     * @param maxRetryAttempts Maximum retry attempts per transfer
     * @param retryDelayMs Base delay between retries in milliseconds
     */
    public SimpleTransferEngine(Vertx vertx, int maxConcurrentTransfers, int maxRetryAttempts, long retryDelayMs) {
        this(vertx, maxConcurrentTransfers, maxRetryAttempts, retryDelayMs, false);
    }

    private SimpleTransferEngine(Vertx vertx, int maxConcurrentTransfers, int maxRetryAttempts, long retryDelayMs,
                                 boolean closeVertxOnShutdown) {
        logger.debug("Initializing SimpleTransferEngine: maxConcurrent={}, maxRetries={}, retryDelay={}ms",
            maxConcurrentTransfers, maxRetryAttempts, retryDelayMs);
        
        this.vertx = Objects.requireNonNull(vertx, "Vertx cannot be null");
        this.closeVertxOnShutdown = closeVertxOnShutdown;
        this.maxConcurrentTransfers = maxConcurrentTransfers;
        this.maxRetryAttempts = maxRetryAttempts;
        this.retryDelayMs = retryDelayMs;

        this.activeJobs = new ConcurrentHashMap<>();
        this.activeContexts = new ConcurrentHashMap<>();
        this.activeFutures = new ConcurrentHashMap<>();
        this.protocolFactory = new ProtocolFactory(vertx);  // Pass Vertx to ProtocolFactory
        this.shutdown = new AtomicBoolean(false);

        // Initialize monitoring (Phase 2 - Dec 2025)
        this.startTime = Instant.now();
        this.protocolMetrics = new ConcurrentHashMap<>();
        // Initialize metrics for all supported protocols with direction tracking
        logger.debug("Initializing protocol metrics for supported protocols (with direction tracking)");
        // Download metrics
        protocolMetrics.put("http-DOWNLOAD", new TransferMetrics("http-DOWNLOAD"));
        protocolMetrics.put("ftp-DOWNLOAD", new TransferMetrics("ftp-DOWNLOAD"));
        protocolMetrics.put("sftp-DOWNLOAD", new TransferMetrics("sftp-DOWNLOAD"));
        protocolMetrics.put("smb-DOWNLOAD", new TransferMetrics("smb-DOWNLOAD"));
        // Upload metrics
        protocolMetrics.put("http-UPLOAD", new TransferMetrics("http-UPLOAD"));
        protocolMetrics.put("ftp-UPLOAD", new TransferMetrics("ftp-UPLOAD"));
        protocolMetrics.put("sftp-UPLOAD", new TransferMetrics("sftp-UPLOAD"));
        protocolMetrics.put("smb-UPLOAD", new TransferMetrics("smb-UPLOAD"));
        // Legacy protocol-only metrics (for backward compatibility)
        protocolMetrics.put("http", new TransferMetrics("http"));
        protocolMetrics.put("ftp", new TransferMetrics("ftp"));
        protocolMetrics.put("sftp", new TransferMetrics("sftp"));
        protocolMetrics.put("smb", new TransferMetrics("smb"));

        // Initialize OpenTelemetry metrics (Phase 8 - Jan 2026)
        this.telemetryMetrics = TransferTelemetryMetrics.getInstance();

        logger.info("SimpleTransferEngine initialized with {} max concurrent transfers (reactive mode, OpenTelemetry enabled)",
            maxConcurrentTransfers);
        logger.debug("SimpleTransferEngine initialization complete - startTime={}", startTime);
    }
    
    @Override
    public Future<TransferResult> submitTransfer(TransferRequest request) throws TransferException {
        TransferDirection direction = request.getDirection();
        logger.debug("submitTransfer: request={}, protocol={}, direction={}", 
            request.getRequestId(), request.getProtocol(), direction);
        
        if (shutdown.get()) {
            logger.debug("submitTransfer: rejected - engine is shutdown");
            throw new TransferException(request.getRequestId(), "Transfer engine is shutdown");
        }
        
        // Validate transfer request
        validateTransferRequest(request);
        
        if (activeJobs.size() >= maxConcurrentTransfers) {
            logger.debug("submitTransfer: rejected - max concurrent transfers reached ({})", activeJobs.size());
            throw new TransferException(request.getRequestId(), "Maximum concurrent transfers reached");
        }
        
        // Create job and context
        logger.debug("submitTransfer: creating job and context");
        TransferJob job = new TransferJob(request);
        TransferContext context = new TransferContext(job);
        
        // Store in active collections
        activeJobs.put(job.getJobId(), job);
        activeContexts.put(job.getJobId(), context);
        logger.debug("submitTransfer: job registered, activeJobs={}", activeJobs.size());
        
        // Record OpenTelemetry metric (Phase 8 - Jan 2026)
        telemetryMetrics.recordTransferStarted(request.getProtocol(), direction.name());
        
        // Execute transfer reactively (no blocking waits)
        logger.debug("submitTransfer: executing transfer via reactive pipeline");
        Map<String, String> mdcContext = MDC.getCopyOfContextMap();
        Promise<TransferResult> executionPromise = Promise.promise();
        vertx.runOnContext(v -> {
            if (mdcContext != null) {
                MDC.setContextMap(mdcContext);
            }
            executeTransfer(context).onComplete(executionPromise);
        });

        Future<TransferResult> future = executionPromise.future()
            .recover(err -> {
                logger.error("Transfer execution failed for job {}: {} ({})",
                        job.getJobId(), err.getMessage(), err.getClass().getSimpleName());
                if (logger.isDebugEnabled()) {
                    logger.debug("Transfer execution exception details for job: {}", job.getJobId(), err);
                }
                job.fail("Transfer execution failed: " + err.getMessage(), err);
                return Future.succeededFuture(job.toResult());
            })
            .eventually(() -> {
                logger.debug("submitTransfer: cleaning up job {} from active collections", job.getJobId());
                activeJobs.remove(job.getJobId());
                activeContexts.remove(job.getJobId());
                activeFutures.remove(job.getJobId());
                MDC.clear();
                return Future.succeededFuture();
            });

        activeFutures.put(job.getJobId(), future);

        logger.info("Transfer submitted: {}", job.getJobId());
        logger.debug("submitTransfer: complete, future registered");
        return future;
    }
    
    @Override
    public TransferJob getTransferJob(String jobId) {
        logger.debug("getTransferJob: looking up jobId={}", jobId);
        TransferJob job = activeJobs.get(jobId);
        logger.debug("getTransferJob: found={}", job != null);
        return job;
    }
    
    @Override
    public boolean cancelTransfer(String jobId) {
        logger.debug("cancelTransfer: attempting to cancel jobId={}", jobId);
        TransferContext context = activeContexts.get(jobId);
        Future<TransferResult> future = activeFutures.get(jobId);
        
        if (context != null) {
            // Set cancellation flag
            logger.debug("cancelTransfer: setting cancellation flag for jobId={}", jobId);
            context.cancel();
            TransferJob job = context.getJob();
            if (job != null) {
                job.cancel();
                
                // Get the protocol and call abort() for hard cancellation
                TransferRequest request = job.getRequest();
                if (request != null) {
                    try {
                        TransferProtocol protocol = protocolFactory.getProtocol(request.getProtocol());
                        if (protocol != null) {
                            logger.info("Aborting transfer {} via protocol.abort()", jobId);
                            protocol.abort();
                            logger.debug("cancelTransfer: protocol abort called successfully");
                        }
                    } catch (Exception e) {
                        logger.warn("Error aborting protocol for job {}: {}", jobId, e.getMessage());
                        logger.debug("Stack trace for abort error on job {}", jobId, e);
                    }
                }
            }
            logger.debug("cancelTransfer: cancellation complete for jobId={}", jobId);
        } else {
            logger.debug("cancelTransfer: no active context found for jobId={}", jobId);
        }
        
        // Note: Vert.x Future doesn't have cancel() method, cancellation handled via context and abort()
        
        return context != null;
    }
    
    @Override
    public boolean pauseTransfer(String jobId) {
        logger.debug("pauseTransfer: attempting to pause jobId={}", jobId);
        TransferContext context = activeContexts.get(jobId);
        if (context != null) {
            context.pause();
            TransferJob job = context.getJob();
            if (job != null) {
                job.pause();
            }
            logger.debug("pauseTransfer: paused successfully");
            return true;
        }
        logger.debug("pauseTransfer: no active context found for jobId={}", jobId);
        return false;
    }
    
    @Override
    public boolean resumeTransfer(String jobId) {
        logger.debug("resumeTransfer: attempting to resume jobId={}", jobId);
        TransferContext context = activeContexts.get(jobId);
        if (context != null) {
            context.resume();
            TransferJob job = context.getJob();
            if (job != null) {
                job.resume();
            }
            logger.debug("resumeTransfer: resumed successfully");
            return true;
        }
        logger.debug("resumeTransfer: no active context found for jobId={}", jobId);
        return false;
    }
    
    @Override
    public int getActiveTransferCount() {
        int count = activeJobs.size();
        logger.debug("getActiveTransferCount: count={}", count);
        return count;
    }
    
    @Override
    public Future<Void> shutdown(long timeoutSeconds) {
        logger.debug("shutdown: initiating with timeout={}s", timeoutSeconds);

        if (shutdown.getAndSet(true)) {
            logger.debug("shutdown: already shutdown");
            return Future.succeededFuture();
        }

        logger.info("Shutting down transfer engine...");
        logger.debug("shutdown: cancelling {} active transfers", activeContexts.size());

        // Cancel all active transfers
        activeContexts.values().forEach(TransferContext::cancel);

        // Reactively wait for in-flight transfers to drain
        return awaitActiveTransfers(timeoutSeconds * 1000)
            .compose(v -> closeOwnedVertx())
            .onSuccess(v -> logger.info("Transfer engine shutdown completed"));
    }

    private Future<Void> closeOwnedVertx() {
        if (!closeVertxOnShutdown) {
            return Future.succeededFuture();
        }

        logger.info("Closing internally-managed Vert.x instance");
        return vertx.close();
    }
    
    /**
     * Waits for all active transfers to complete or timeout.
     * 
     * <p>This method is useful for graceful shutdown where you want to allow
     * in-flight transfers to finish before closing resources.
     *
     * @param timeoutMs maximum time to wait in milliseconds
     * @return a Future that completes when all transfers are done or timeout expires
     */
    public Future<Void> awaitActiveTransfers(long timeoutMs) {
        int activeCount = activeJobs.size();
        if (activeCount == 0) {
            logger.debug("awaitActiveTransfers: no active transfers");
            return Future.succeededFuture();
        }
        
        logger.info("Waiting for {} active transfers to complete (timeout={}ms)", activeCount, timeoutMs);
        
        // Collect all active transfer futures
        List<Future<TransferResult>> futures = new ArrayList<>(activeFutures.values());
        if (futures.isEmpty()) {
            return Future.succeededFuture();
        }
        
        // Wait for all with timeout
        return Future.all(futures)
                .timeout(timeoutMs, TimeUnit.MILLISECONDS)
                .map(v -> {
                    logger.info("All active transfers completed");
                    return (Void) null;
                })
                .recover(err -> {
                    int remaining = activeJobs.size();
                    if (remaining > 0) {
                        logger.warn("Timeout waiting for transfers, {} still active", remaining);
                    } else {
                        logger.info("All active transfers completed");
                    }
                    return Future.succeededFuture();
                });
    }
    
    /**
     * Checks if the transfer engine is in shutdown state.
     *
     * @return true if shutdown has been initiated
     */
    public boolean isShutdown() {
        return shutdown.get();
    }

    boolean isClosingOwnedVertxOnShutdown() {
        return closeVertxOnShutdown;
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

    private Future<TransferResult> executeTransfer(TransferContext context) {
        TransferJob job = context.getJob();
        TransferRequest request = job.getRequest();
        TransferDirection direction = request.getDirection();

        logger.info("Starting {} transfer: {}", direction, job.getJobId());
        logger.debug("executeTransfer: starting for jobId={}, protocol={}, direction={}, sourceUri={}, destinationUri={}", 
            job.getJobId(), request.getProtocol(), direction, request.getSourceUri(), request.getDestinationUri());
        job.start();

        // Record transfer start in metrics (both direction-specific and legacy)
        String protocolName = request.getProtocol();
        String metricsKey = protocolName + "-" + direction.name();
        TransferMetrics directionMetrics = protocolMetrics.get(metricsKey);
        TransferMetrics legacyMetrics = protocolMetrics.get(protocolName);
        
        if (directionMetrics != null) {
            directionMetrics.recordTransferStart();
            logger.debug("executeTransfer: recorded transfer start in direction metrics for key={}", metricsKey);
        }
        if (legacyMetrics != null) {
            legacyMetrics.recordTransferStart();
            logger.debug("executeTransfer: recorded transfer start in legacy metrics for protocol={}", protocolName);
        }

        Instant transferStartTime = Instant.now();
        return executeAttempt(context, request, direction, transferStartTime, directionMetrics,
                legacyMetrics, metricsKey, protocolName, 0, null);
    }

    private Future<TransferResult> executeAttempt(
            TransferContext context,
            TransferRequest request,
            TransferDirection direction,
            Instant transferStartTime,
            TransferMetrics directionMetrics,
            TransferMetrics legacyMetrics,
            String metricsKey,
            String protocolName,
            int attempt,
            Throwable lastError) {

        TransferJob job = context.getJob();
        if (!context.shouldContinue()) {
            String message = "Transfer cancelled or paused before completion";
            logger.warn("executeTransfer: {} for jobId={}", message, job.getJobId());
            job.fail(message, lastError);
            return Future.succeededFuture(job.toResult());
        }

        logger.debug("executeTransfer: attempt {} of {} for jobId={}",
                attempt + 1, maxRetryAttempts + 1, job.getJobId());

        TransferProtocol protocol = protocolFactory.getProtocol(protocolName);
        if (protocol == null) {
            TransferException error = new TransferException(job.getJobId(), "Unsupported protocol: " + protocolName);
            return failTransfer(job, direction, directionMetrics, legacyMetrics, metricsKey, protocolName, error);
        }
        if (!protocol.canHandle(request)) {
            TransferException error = new TransferException(job.getJobId(),
                    "Protocol '" + protocolName + "' cannot handle request direction/URI combination");
            return failTransfer(job, direction, directionMetrics, legacyMetrics, metricsKey, protocolName, error);
        }

        return protocol.transferReactive(request, context)
                .compose(result -> {
                    if (!result.isSuccessful()) {
                        String errorMsg = result.getErrorMessage().orElse("Unknown error");
                        return Future.failedFuture(new TransferException(job.getJobId(), "Transfer failed: " + errorMsg));
                    }

                    logger.info("{} transfer completed successfully: {}", direction, job.getJobId());
                    Duration duration = Duration.between(transferStartTime, Instant.now());
                    long bytesTransferred = result.getBytesTransferred();

                    if (directionMetrics != null) {
                        directionMetrics.recordTransferSuccess(bytesTransferred, duration);
                        logger.debug("executeTransfer: recorded success in direction metrics - key={}, bytes={}, duration={}ms",
                                metricsKey, bytesTransferred, duration.toMillis());
                    }
                    if (legacyMetrics != null) {
                        legacyMetrics.recordTransferSuccess(bytesTransferred, duration);
                        logger.debug("executeTransfer: recorded success in legacy metrics - protocol={}", protocolName);
                    }

                    telemetryMetrics.recordTransferCompleted(protocolName, direction.name(),
                            bytesTransferred, duration.toMillis() / 1000.0);

                    return Future.succeededFuture(result);
                })
                .recover(err -> {
                    int nextAttempt = attempt + 1;
                    context.incrementRetryCount();

                    logger.warn("Transfer attempt {} failed for job {}: {}",
                            nextAttempt, job.getJobId(), err.getMessage());
                    logger.debug("Stack trace for failed attempt {} on job {}", nextAttempt, job.getJobId(), err);

                    if (nextAttempt <= maxRetryAttempts && context.shouldContinue()) {
                        long delay = retryDelayMs * nextAttempt;
                        logger.debug("executeTransfer: scheduling retry after {}ms", delay);
                        telemetryMetrics.recordRetryAttempt(protocolName, direction.name(), nextAttempt);

                        return delayFuture(delay)
                                .compose(v -> executeAttempt(context, request, direction, transferStartTime,
                                        directionMetrics, legacyMetrics, metricsKey, protocolName, nextAttempt, err));
                    }

                    return failTransfer(job, direction, directionMetrics, legacyMetrics,
                            metricsKey, protocolName, err);
                });
    }

    private Future<Void> delayFuture(long delayMs) {
        Promise<Void> delayPromise = Promise.promise();
        vertx.setTimer(delayMs, id -> delayPromise.complete());
        return delayPromise.future();
    }

    private Future<TransferResult> failTransfer(
            TransferJob job,
            TransferDirection direction,
            TransferMetrics directionMetrics,
            TransferMetrics legacyMetrics,
            String metricsKey,
            String protocolName,
            Throwable error) {
        String errorMessage = error != null ? error.getMessage() :
                "Transfer failed after " + maxRetryAttempts + " attempts";
        logger.debug("executeTransfer: all attempts exhausted for jobId={}, direction={}", job.getJobId(), direction);
        job.fail(errorMessage, error);

        String errorType = error != null ? error.getClass().getSimpleName() : "UnknownError";
        if (directionMetrics != null) {
            directionMetrics.recordTransferFailure(errorType);
            logger.debug("executeTransfer: recorded failure in direction metrics - key={}, errorType={}",
                    metricsKey, errorType);
        }
        if (legacyMetrics != null) {
            legacyMetrics.recordTransferFailure(errorType);
            logger.debug("executeTransfer: recorded failure in legacy metrics - protocol={}", protocolName);
        }

        telemetryMetrics.recordTransferFailed(protocolName, direction.name(), errorType);
        logger.error("{} transfer failed permanently: {} - {}", direction, job.getJobId(), errorMessage);
        if (logger.isTraceEnabled() && error != null) {
            logger.debug("Full stack trace for failed transfer {}", job.getJobId(), error);
        }

        return Future.succeededFuture(job.toResult());
    }
    
    /**
     * Validates that the transfer request is supported.
     * 
     * @param request the transfer request to validate
     * @throws TransferException if the request is invalid
     */
    private void validateTransferRequest(TransferRequest request) throws TransferException {
        java.net.URI source = request.getSourceUri();
        java.net.URI dest = request.getDestinationUri();
        
        // Validate URIs are not null
        if (source == null) {
            throw new TransferException(request.getRequestId(), "Source URI cannot be null");
        }
        if (dest == null) {
            throw new TransferException(request.getRequestId(), "Destination URI cannot be null");
        }
        
        boolean sourceIsFile = "file".equalsIgnoreCase(source.getScheme());
        boolean destIsFile = "file".equalsIgnoreCase(dest.getScheme());
        
        // At least one must be file://
        if (!sourceIsFile && !destIsFile) {
            throw new TransferException(request.getRequestId(),
                "At least one endpoint must be file:// (local filesystem). " +
                "Remote-to-remote transfers not yet supported.");
        }
        
        // Both can't be file:// (use Files.copy instead)
        if (sourceIsFile && destIsFile) {
            throw new TransferException(request.getRequestId(),
                "Both source and destination are local files. Use Files.copy() for local file-to-file operations.");
        }
        
        // Validate configured protocol (protocol is authoritative from job configuration)
        String configuredProtocol = request.getProtocol();
        if (!protocolFactory.isProtocolSupported(configuredProtocol)) {
            throw new TransferException(request.getRequestId(),
                "Unsupported protocol: " + configuredProtocol + ". Supported: " +
                String.join(", ", protocolFactory.getSupportedProtocols()));
        }

        TransferProtocol protocol = protocolFactory.getProtocol(configuredProtocol);
        if (protocol == null || !protocol.canHandle(request)) {
            throw new TransferException(request.getRequestId(),
                "Configured protocol '" + configuredProtocol + "' cannot handle this request direction or URIs");
        }
        
        logger.debug("validateTransferRequest: request {} validated successfully (direction={}, protocol={})",
            request.getRequestId(), request.getDirection(), configuredProtocol);
    }
    
    /**
     * Gets metrics for a specific protocol and direction combination.
     * 
     * @param protocolName the protocol name (e.g., "sftp")
     * @param direction the transfer direction
     * @return the metrics for the specified protocol and direction, or null if not found
     */
    public TransferMetrics getProtocolMetrics(String protocolName, TransferDirection direction) {
        String key = protocolName + "-" + direction.name();
        return protocolMetrics.get(key);
    }
}
