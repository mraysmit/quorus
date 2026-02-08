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

package dev.mars.quorus.simulator.transfer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * In-memory simulator for the transfer engine.
 * 
 * <p>This simulator manages file transfers without real protocol implementations,
 * enabling testing of:
 * <ul>
 *   <li>Transfer job lifecycle (submit, monitor, complete)</li>
 *   <li>Concurrency control (max concurrent transfers)</li>
 *   <li>Pause/resume/cancel operations</li>
 *   <li>Transfer metrics and health checks</li>
 *   <li>Failure scenarios and recovery</li>
 * </ul>
 * 
 * <h2>Usage Example:</h2>
 * <pre>{@code
 * InMemoryTransferEngineSimulator engine = new InMemoryTransferEngineSimulator();
 * engine.setMaxConcurrentTransfers(5);
 * engine.setDefaultTransferDurationMs(5000);
 * 
 * CompletableFuture<TransferResult> future = engine.submitTransfer(request);
 * TransferJob job = engine.getTransferJob(request.getJobId());
 * }</pre>
 * 
 * @author Mark Andrew Ray-Smith
 * @since 1.0
 */
public class InMemoryTransferEngineSimulator {

    private static final Logger log = LoggerFactory.getLogger(InMemoryTransferEngineSimulator.class);

    // Configuration
    private int maxConcurrentTransfers = 10;
    private long defaultTransferDurationMs = 1000;
    private long defaultTransferSizeBytes = 10_000_000; // 10MB default
    
    // Active transfers
    private final Map<String, SimulatedTransfer> transfers = new ConcurrentHashMap<>();
    private final AtomicInteger activeTransferCount = new AtomicInteger(0);
    private final Semaphore concurrencySemaphore;
    private final ScheduledExecutorService scheduler;
    
    // Queue for transfers waiting to start
    private final BlockingQueue<SimulatedTransfer> pendingQueue = new LinkedBlockingQueue<>();
    
    // Chaos engineering
    private TransferEngineFailureMode failureMode = TransferEngineFailureMode.NONE;
    private double transferFailureRate = 0.0;
    
    // Statistics
    private final AtomicLong totalSubmitted = new AtomicLong(0);
    private final AtomicLong totalCompleted = new AtomicLong(0);
    private final AtomicLong totalFailed = new AtomicLong(0);
    private final AtomicLong totalCancelled = new AtomicLong(0);
    private final AtomicLong totalBytesTransferred = new AtomicLong(0);
    
    // Metrics per protocol
    private final Map<String, ProtocolMetrics> protocolMetrics = new ConcurrentHashMap<>();
    
    // Callbacks
    private Consumer<TransferEvent> eventCallback;

    /**
     * Failure modes for chaos engineering.
     */
    public enum TransferEngineFailureMode {
        /** Normal operation */
        NONE,
        /** Queue is full - submissions rejected */
        QUEUE_FULL,
        /** Engine is overloaded */
        ENGINE_OVERLOADED,
        /** All transfers fail */
        ALL_TRANSFERS_FAIL,
        /** Random transfers fail */
        RANDOM_FAILURES,
        /** Transfers execute very slowly */
        SLOW_PROCESSING,
        /** Engine shutdown in progress */
        SHUTTING_DOWN
    }

    /**
     * Transfer status.
     */
    public enum TransferStatus {
        PENDING,
        QUEUED,
        IN_PROGRESS,
        PAUSED,
        COMPLETED,
        FAILED,
        CANCELLED
    }

    /**
     * Creates a new transfer engine simulator with default settings.
     */
    public InMemoryTransferEngineSimulator() {
        this.concurrencySemaphore = new Semaphore(maxConcurrentTransfers);
        this.scheduler = Executors.newScheduledThreadPool(4, r -> {
            Thread t = new Thread(r, "transfer-engine-simulator");
            t.setDaemon(true);
            return t;
        });
        
        // Start queue processor
        scheduler.submit(this::processQueue);
        log.info("InMemoryTransferEngineSimulator initialized with maxConcurrentTransfers={}", maxConcurrentTransfers);
    }

    // ==================== Transfer Operations ====================

    /**
     * Submits a transfer for execution.
     *
     * @param request the transfer request
     * @return a future containing the transfer result
     */
    public CompletableFuture<TransferResult> submitTransfer(TransferRequest request) {
        log.debug("submitTransfer: Received request jobId={}, source={}", 
            request.jobId(), request.sourceUri());
        
        // Check for immediate failures
        if (failureMode == TransferEngineFailureMode.QUEUE_FULL) {
            log.warn("submitTransfer: Rejected - queue is full");
            return CompletableFuture.failedFuture(
                new TransferException("Transfer queue is full"));
        }
        if (failureMode == TransferEngineFailureMode.ENGINE_OVERLOADED) {
            log.warn("submitTransfer: Rejected - engine overloaded");
            return CompletableFuture.failedFuture(
                new TransferException("Transfer engine is overloaded"));
        }
        if (failureMode == TransferEngineFailureMode.SHUTTING_DOWN) {
            log.warn("submitTransfer: Rejected - engine shutting down");
            return CompletableFuture.failedFuture(
                new TransferException("Transfer engine is shutting down"));
        }
        
        String jobId = request.jobId() != null ? request.jobId() : generateJobId();
        long transferSize = request.expectedSizeBytes() > 0 ? 
            request.expectedSizeBytes() : defaultTransferSizeBytes;
        
        SimulatedTransfer transfer = new SimulatedTransfer(jobId, request, transferSize);
        transfers.put(jobId, transfer);
        totalSubmitted.incrementAndGet();
        log.info("submitTransfer: Created transfer jobId={}, size={} bytes", jobId, transferSize);
        
        // Try to start immediately or queue
        if (concurrencySemaphore.tryAcquire()) {
            log.debug("submitTransfer: Starting immediately jobId={}", jobId);
            startTransfer(transfer);
        } else {
            transfer.status = TransferStatus.QUEUED;
            pendingQueue.offer(transfer);
            log.debug("submitTransfer: Queued for later jobId={}, queueSize={}", jobId, pendingQueue.size());
            fireEvent(TransferEvent.queued(jobId));
        }
        
        return transfer.completionFuture;
    }

    /**
     * Gets a transfer job by ID.
     *
     * @param jobId the job ID
     * @return the transfer job, or null if not found
     */
    public TransferJob getTransferJob(String jobId) {
        SimulatedTransfer transfer = transfers.get(jobId);
        if (transfer == null) {
            return null;
        }
        return new TransferJob(
            transfer.jobId,
            transfer.status,
            transfer.bytesTransferred.get(),
            transfer.totalBytes,
            calculateProgress(transfer),
            transfer.startTime,
            transfer.endTime,
            transfer.error
        );
    }

    /**
     * Cancels a transfer.
     *
     * @param jobId the job ID
     * @return true if the transfer was cancelled
     */
    public boolean cancelTransfer(String jobId) {
        log.debug("cancelTransfer: Request to cancel jobId={}", jobId);
        SimulatedTransfer transfer = transfers.get(jobId);
        if (transfer == null) {
            log.warn("cancelTransfer: Job not found jobId={}", jobId);
            return false;
        }
        
        if (transfer.status == TransferStatus.COMPLETED || 
            transfer.status == TransferStatus.FAILED ||
            transfer.status == TransferStatus.CANCELLED) {
            log.debug("cancelTransfer: Cannot cancel - already in terminal state jobId={}, status={}", 
                jobId, transfer.status);
            return false;
        }
        
        transfer.status = TransferStatus.CANCELLED;
        transfer.endTime = Instant.now();
        totalCancelled.incrementAndGet();
        
        transfer.completionFuture.completeExceptionally(
            new TransferException("Transfer cancelled"));
        
        if (transfer.progressTask != null) {
            transfer.progressTask.cancel(true);
        }
        
        log.info("cancelTransfer: Successfully cancelled jobId={}", jobId);
        fireEvent(TransferEvent.cancelled(jobId));
        return true;
    }

    /**
     * Pauses a transfer.
     *
     * @param jobId the job ID
     * @return true if the transfer was paused
     */
    public boolean pauseTransfer(String jobId) {
        log.debug("pauseTransfer: Request to pause jobId={}", jobId);
        SimulatedTransfer transfer = transfers.get(jobId);
        if (transfer == null || transfer.status != TransferStatus.IN_PROGRESS) {
            log.debug("pauseTransfer: Cannot pause - not in progress jobId={}", jobId);
            return false;
        }
        
        transfer.status = TransferStatus.PAUSED;
        log.info("pauseTransfer: Successfully paused jobId={}", jobId);
        fireEvent(TransferEvent.paused(jobId));
        return true;
    }

    /**
     * Resumes a paused transfer.
     *
     * @param jobId the job ID
     * @return true if the transfer was resumed
     */
    public boolean resumeTransfer(String jobId) {
        log.debug("resumeTransfer: Request to resume jobId={}", jobId);
        SimulatedTransfer transfer = transfers.get(jobId);
        if (transfer == null || transfer.status != TransferStatus.PAUSED) {
            log.debug("resumeTransfer: Cannot resume - not paused jobId={}", jobId);
            return false;
        }
        
        transfer.status = TransferStatus.IN_PROGRESS;
        log.info("resumeTransfer: Successfully resumed jobId={}", jobId);
        fireEvent(TransferEvent.resumed(jobId));
        return true;
    }

    /**
     * Gets the count of currently active transfers.
     *
     * @return active transfer count
     */
    public int getActiveTransferCount() {
        return activeTransferCount.get();
    }

    /**
     * Gets all transfer jobs.
     *
     * @return list of all transfer jobs
     */
    public List<TransferJob> getAllTransferJobs() {
        return transfers.values().stream()
            .map(t -> new TransferJob(
                t.jobId,
                t.status,
                t.bytesTransferred.get(),
                t.totalBytes,
                calculateProgress(t),
                t.startTime,
                t.endTime,
                t.error
            ))
            .toList();
    }

    /**
     * Gets transfers by status.
     *
     * @param status the status to filter by
     * @return list of matching transfers
     */
    public List<TransferJob> getTransfersByStatus(TransferStatus status) {
        return transfers.values().stream()
            .filter(t -> t.status == status)
            .map(t -> new TransferJob(
                t.jobId,
                t.status,
                t.bytesTransferred.get(),
                t.totalBytes,
                calculateProgress(t),
                t.startTime,
                t.endTime,
                t.error
            ))
            .toList();
    }

    // ==================== Configuration ====================

    /**
     * Sets the maximum concurrent transfers.
     *
     * @param max maximum concurrent transfers
     * @return this simulator for chaining
     */
    public InMemoryTransferEngineSimulator setMaxConcurrentTransfers(int max) {
        this.maxConcurrentTransfers = max;
        return this;
    }

    /**
     * Sets the default transfer duration.
     *
     * @param durationMs duration in milliseconds
     * @return this simulator for chaining
     */
    public InMemoryTransferEngineSimulator setDefaultTransferDurationMs(long durationMs) {
        this.defaultTransferDurationMs = durationMs;
        return this;
    }

    /**
     * Sets the default transfer size for progress simulation.
     *
     * @param sizeBytes size in bytes
     * @return this simulator for chaining
     */
    public InMemoryTransferEngineSimulator setDefaultTransferSizeBytes(long sizeBytes) {
        this.defaultTransferSizeBytes = sizeBytes;
        return this;
    }

    /**
     * Sets the failure mode.
     *
     * @param mode the failure mode
     * @return this simulator for chaining
     */
    public InMemoryTransferEngineSimulator setFailureMode(TransferEngineFailureMode mode) {
        this.failureMode = mode;
        return this;
    }

    /**
     * Sets the transfer failure rate for random failures.
     *
     * @param rate failure rate (0.0 to 1.0)
     * @return this simulator for chaining
     */
    public InMemoryTransferEngineSimulator setTransferFailureRate(double rate) {
        this.transferFailureRate = Math.max(0.0, Math.min(1.0, rate));
        return this;
    }

    /**
     * Sets the event callback for transfer events.
     *
     * @param callback the callback
     * @return this simulator for chaining
     */
    public InMemoryTransferEngineSimulator setEventCallback(Consumer<TransferEvent> callback) {
        this.eventCallback = callback;
        return this;
    }

    /**
     * Resets all chaos engineering settings.
     *
     * @return this simulator for chaining
     */
    public InMemoryTransferEngineSimulator reset() {
        this.failureMode = TransferEngineFailureMode.NONE;
        this.transferFailureRate = 0.0;
        return this;
    }

    // ==================== Health & Metrics ====================

    /**
     * Gets the engine health check status.
     *
     * @return health check result
     */
    public HealthCheck getHealthCheck() {
        boolean healthy = failureMode == TransferEngineFailureMode.NONE;
        int queueSize = pendingQueue.size();
        int activeCount = activeTransferCount.get();
        
        return new HealthCheck(
            healthy,
            healthy ? "Transfer engine is healthy" : "Engine in failure mode: " + failureMode,
            activeCount,
            queueSize,
            maxConcurrentTransfers
        );
    }

    /**
     * Gets metrics for a specific protocol.
     *
     * @param protocol the protocol name
     * @return protocol metrics
     */
    public ProtocolMetrics getProtocolMetrics(String protocol) {
        return protocolMetrics.computeIfAbsent(protocol, k -> new ProtocolMetrics(protocol));
    }

    /**
     * Gets metrics for all protocols.
     *
     * @return map of protocol name to metrics
     */
    public Map<String, ProtocolMetrics> getAllProtocolMetrics() {
        return new HashMap<>(protocolMetrics);
    }

    // ==================== Statistics ====================

    /**
     * Gets the total number of submitted transfers.
     *
     * @return total submitted
     */
    public long getTotalSubmitted() {
        return totalSubmitted.get();
    }

    /**
     * Gets the total number of completed transfers.
     *
     * @return total completed
     */
    public long getTotalCompleted() {
        return totalCompleted.get();
    }

    /**
     * Gets the total number of failed transfers.
     *
     * @return total failed
     */
    public long getTotalFailed() {
        return totalFailed.get();
    }

    /**
     * Gets the total number of cancelled transfers.
     *
     * @return total cancelled
     */
    public long getTotalCancelled() {
        return totalCancelled.get();
    }

    /**
     * Gets the total bytes transferred.
     *
     * @return total bytes
     */
    public long getTotalBytesTransferred() {
        return totalBytesTransferred.get();
    }

    /**
     * Resets all statistics.
     */
    public void resetStatistics() {
        totalSubmitted.set(0);
        totalCompleted.set(0);
        totalFailed.set(0);
        totalCancelled.set(0);
        totalBytesTransferred.set(0);
        protocolMetrics.clear();
    }

    // ==================== Lifecycle ====================

    /**
     * Shuts down the transfer engine.
     *
     * @param timeoutSeconds maximum time to wait
     * @return true if shutdown completed cleanly
     */
    public boolean shutdown(long timeoutSeconds) {
        failureMode = TransferEngineFailureMode.SHUTTING_DOWN;
        
        // Cancel all pending
        pendingQueue.forEach(t -> cancelTransfer(t.jobId));
        pendingQueue.clear();
        
        scheduler.shutdown();
        try {
            return scheduler.awaitTermination(timeoutSeconds, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            scheduler.shutdownNow();
            return false;
        }
    }

    /**
     * Immediately shuts down the transfer engine without waiting for tasks to complete.
     * Preferred for test teardown where pending tasks are not needed.
     */
    public void shutdownNow() {
        failureMode = TransferEngineFailureMode.SHUTTING_DOWN;
        pendingQueue.forEach(t -> cancelTransfer(t.jobId));
        pendingQueue.clear();
        scheduler.shutdownNow();
    }

    /**
     * Clears all transfers and resets state.
     */
    public void clear() {
        transfers.clear();
        pendingQueue.clear();
        resetStatistics();
        reset();
    }

    // ==================== Private Implementation ====================

    private void processQueue() {
        while (!Thread.currentThread().isInterrupted()) {
            try {
                SimulatedTransfer transfer = pendingQueue.poll(100, TimeUnit.MILLISECONDS);
                if (transfer != null) {
                    concurrencySemaphore.acquire();
                    if (transfer.status == TransferStatus.QUEUED) {
                        startTransfer(transfer);
                    } else {
                        concurrencySemaphore.release();
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    private void startTransfer(SimulatedTransfer transfer) {
        log.debug("startTransfer: Starting transfer jobId={}", transfer.jobId);
        transfer.status = TransferStatus.IN_PROGRESS;
        transfer.startTime = Instant.now();
        activeTransferCount.incrementAndGet();
        
        fireEvent(TransferEvent.started(transfer.jobId));
        
        // Determine transfer duration
        long duration = defaultTransferDurationMs;
        if (failureMode == TransferEngineFailureMode.SLOW_PROCESSING) {
            duration *= 10; // 10x slower
            log.debug("startTransfer: Using slow processing mode, duration={}ms", duration);
        }
        
        // Calculate progress interval
        int progressIntervalMs = Math.max(100, (int) (duration / 10));
        long bytesPerInterval = transfer.totalBytes / 10;
        log.debug("startTransfer: Progress interval={}ms, bytesPerInterval={}", 
            progressIntervalMs, bytesPerInterval);
        
        // Schedule progress updates
        transfer.progressTask = scheduler.scheduleAtFixedRate(() -> {
            if (transfer.status == TransferStatus.IN_PROGRESS) {
                long current = transfer.bytesTransferred.addAndGet(bytesPerInterval);
                if (current >= transfer.totalBytes) {
                    transfer.bytesTransferred.set(transfer.totalBytes);
                }
                fireEvent(TransferEvent.progress(transfer.jobId, 
                    calculateProgress(transfer)));
            }
        }, progressIntervalMs, progressIntervalMs, TimeUnit.MILLISECONDS);
        
        // Schedule completion
        scheduler.schedule(() -> completeTransfer(transfer), duration, TimeUnit.MILLISECONDS);
    }

    private void completeTransfer(SimulatedTransfer transfer) {
        log.debug("completeTransfer: Processing completion for jobId={}", transfer.jobId);
        if (transfer.progressTask != null) {
            transfer.progressTask.cancel(false);
        }
        
        if (transfer.status == TransferStatus.CANCELLED || 
            transfer.status == TransferStatus.COMPLETED ||
            transfer.status == TransferStatus.FAILED) {
            log.debug("completeTransfer: Skipping - already in terminal state jobId={}, status={}", 
                transfer.jobId, transfer.status);
            concurrencySemaphore.release();
            activeTransferCount.decrementAndGet();
            return;
        }
        
        // Check for failures
        boolean shouldFail = failureMode == TransferEngineFailureMode.ALL_TRANSFERS_FAIL ||
            (failureMode == TransferEngineFailureMode.RANDOM_FAILURES && 
             ThreadLocalRandom.current().nextDouble() < transferFailureRate) ||
            (transferFailureRate > 0 && 
             ThreadLocalRandom.current().nextDouble() < transferFailureRate);
        
        if (shouldFail) {
            transfer.status = TransferStatus.FAILED;
            transfer.error = "Simulated transfer failure";
            transfer.endTime = Instant.now();
            totalFailed.incrementAndGet();
            
            log.warn("completeTransfer: Transfer failed jobId={}, error={}", transfer.jobId, transfer.error);
            transfer.completionFuture.completeExceptionally(
                new TransferException(transfer.error));
            
            fireEvent(TransferEvent.failed(transfer.jobId, transfer.error));
        } else {
            transfer.status = TransferStatus.COMPLETED;
            transfer.bytesTransferred.set(transfer.totalBytes);
            transfer.endTime = Instant.now();
            totalCompleted.incrementAndGet();
            totalBytesTransferred.addAndGet(transfer.totalBytes);
            
            Duration duration = Duration.between(transfer.startTime, transfer.endTime);
            TransferDirection direction = transfer.request.getDirection();
            log.info("completeTransfer: Transfer completed successfully jobId={}, direction={}, bytes={}, duration={}ms", 
                transfer.jobId, direction, transfer.totalBytes, duration.toMillis());
            
            // Update protocol metrics with direction
            String protocol = transfer.request.protocol();
            if (protocol != null) {
                ProtocolMetrics metrics = getProtocolMetrics(protocol);
                metrics.recordTransfer(transfer.totalBytes, duration, direction);
            }
            
            transfer.completionFuture.complete(new TransferResult(
                transfer.jobId,
                true,
                transfer.totalBytes,
                duration,
                null
            ));
            
            fireEvent(TransferEvent.completed(transfer.jobId));
        }
        
        concurrencySemaphore.release();
        activeTransferCount.decrementAndGet();
    }

    private int calculateProgress(SimulatedTransfer transfer) {
        if (transfer.totalBytes == 0) return 100;
        return (int) ((transfer.bytesTransferred.get() * 100) / transfer.totalBytes);
    }

    private void fireEvent(TransferEvent event) {
        if (eventCallback != null) {
            try {
                eventCallback.accept(event);
            } catch (Exception e) {
                // Ignore callback errors
            }
        }
    }

    private String generateJobId() {
        return "job-" + System.nanoTime() + "-" + 
            ThreadLocalRandom.current().nextInt(10000);
    }

    // ==================== Inner Classes ====================

    private static class SimulatedTransfer {
        final String jobId;
        final TransferRequest request;
        final long totalBytes;
        final AtomicLong bytesTransferred = new AtomicLong(0);
        final CompletableFuture<TransferResult> completionFuture = new CompletableFuture<>();
        volatile TransferStatus status = TransferStatus.PENDING;
        volatile Instant startTime;
        volatile Instant endTime;
        volatile String error;
        volatile ScheduledFuture<?> progressTask;

        SimulatedTransfer(String jobId, TransferRequest request, long totalBytes) {
            this.jobId = jobId;
            this.request = request;
            this.totalBytes = totalBytes;
        }
    }

    /**
     * Transfer direction enumeration.
     * Matches the production TransferDirection enum.
     */
    public enum TransferDirection {
        /** Transfer from remote source to local file:// destination */
        DOWNLOAD,
        /** Transfer from local file:// source to remote destination */
        UPLOAD,
        /** Transfer between two remote endpoints (relay/proxy) */
        REMOTE_TO_REMOTE
    }

    /**
     * Transfer request with bidirectional support.
     * 
     * <p>Supports both uploads and downloads:</p>
     * <ul>
     *   <li><b>DOWNLOAD:</b> Remote source → Local file:// destination</li>
     *   <li><b>UPLOAD:</b> Local file:// source → Remote destination</li>
     * </ul>
     */
    public record TransferRequest(
        String jobId,
        String sourceUri,
        String destinationUri,
        String protocol,
        long expectedSizeBytes,
        Map<String, String> options
    ) {
        /**
         * Gets the destination path (only valid for downloads).
         * For backward compatibility, extracts path from file:// URI.
         */
        public String destinationPath() {
            if (destinationUri == null) return null;
            if (destinationUri.startsWith("file://")) {
                return destinationUri.substring(7); // Remove "file://" prefix
            }
            return destinationUri;
        }
        
        /**
         * Determines the transfer direction based on URI schemes.
         * @return the transfer direction
         */
        public TransferDirection getDirection() {
            boolean sourceIsFile = sourceUri != null && 
                (sourceUri.startsWith("file://") || sourceUri.startsWith("file:///"));
            boolean destIsFile = destinationUri != null && 
                (destinationUri.startsWith("file://") || destinationUri.startsWith("file:///"));
            
            if (!sourceIsFile && destIsFile) {
                return TransferDirection.DOWNLOAD;
            } else if (sourceIsFile && !destIsFile) {
                return TransferDirection.UPLOAD;
            } else {
                return TransferDirection.REMOTE_TO_REMOTE;
            }
        }
        
        /**
         * Returns true if this is a download operation (remote → local).
         */
        public boolean isDownload() {
            return getDirection() == TransferDirection.DOWNLOAD;
        }
        
        /**
         * Returns true if this is an upload operation (local → remote).
         */
        public boolean isUpload() {
            return getDirection() == TransferDirection.UPLOAD;
        }
        
        public static Builder builder() {
            return new Builder();
        }
        
        public static class Builder {
            private String jobId;
            private String sourceUri;
            private String destinationUri;
            private String destinationPath; // For backward compatibility
            private String protocol;
            private long expectedSizeBytes = 0;
            private Map<String, String> options = Map.of();
            
            public Builder jobId(String jobId) {
                this.jobId = jobId;
                return this;
            }
            
            public Builder sourceUri(String uri) {
                this.sourceUri = uri;
                return this;
            }
            
            /**
             * Sets the destination URI (new bidirectional API).
             * @param uri the destination URI (can be file://, sftp://, ftp://, etc.)
             */
            public Builder destinationUri(String uri) {
                this.destinationUri = uri;
                return this;
            }
            
            /**
             * Sets the destination path (backward compatibility - converts to file:// URI).
             * @param path the local destination path
             */
            public Builder destinationPath(String path) {
                this.destinationPath = path;
                return this;
            }
            
            public Builder protocol(String protocol) {
                this.protocol = protocol;
                return this;
            }
            
            public Builder expectedSizeBytes(long size) {
                this.expectedSizeBytes = size;
                return this;
            }
            
            public Builder options(Map<String, String> options) {
                this.options = options;
                return this;
            }
            
            public TransferRequest build() {
                // Prefer destinationUri, fall back to destinationPath converted to URI
                String destUri = destinationUri;
                if (destUri == null && destinationPath != null) {
                    // Convert local path to file:// URI
                    destUri = "file://" + destinationPath;
                }
                return new TransferRequest(jobId, sourceUri, destUri, 
                    protocol, expectedSizeBytes, options);
            }
        }
    }

    /**
     * Transfer job status.
     */
    public record TransferJob(
        String jobId,
        TransferStatus status,
        long bytesTransferred,
        long totalBytes,
        int progressPercent,
        Instant startTime,
        Instant endTime,
        String error
    ) {
        public Duration getDuration() {
            if (startTime == null) return Duration.ZERO;
            Instant end = endTime != null ? endTime : Instant.now();
            return Duration.between(startTime, end);
        }
    }

    /**
     * Transfer result.
     */
    public record TransferResult(
        String jobId,
        boolean successful,
        long bytesTransferred,
        Duration duration,
        String errorMessage
    ) {}

    /**
     * Transfer event.
     */
    public record TransferEvent(
        String jobId,
        EventType type,
        int progress,
        String message
    ) {
        public enum EventType {
            QUEUED, STARTED, PROGRESS, PAUSED, RESUMED, COMPLETED, FAILED, CANCELLED
        }
        
        static TransferEvent queued(String jobId) {
            return new TransferEvent(jobId, EventType.QUEUED, 0, null);
        }
        
        static TransferEvent started(String jobId) {
            return new TransferEvent(jobId, EventType.STARTED, 0, null);
        }
        
        static TransferEvent progress(String jobId, int percent) {
            return new TransferEvent(jobId, EventType.PROGRESS, percent, null);
        }
        
        static TransferEvent paused(String jobId) {
            return new TransferEvent(jobId, EventType.PAUSED, 0, null);
        }
        
        static TransferEvent resumed(String jobId) {
            return new TransferEvent(jobId, EventType.RESUMED, 0, null);
        }
        
        static TransferEvent completed(String jobId) {
            return new TransferEvent(jobId, EventType.COMPLETED, 100, null);
        }
        
        static TransferEvent failed(String jobId, String error) {
            return new TransferEvent(jobId, EventType.FAILED, 0, error);
        }
        
        static TransferEvent cancelled(String jobId) {
            return new TransferEvent(jobId, EventType.CANCELLED, 0, null);
        }
    }

    /**
     * Health check result.
     */
    public record HealthCheck(
        boolean healthy,
        String message,
        int activeTransfers,
        int queuedTransfers,
        int maxConcurrentTransfers
    ) {}

    /**
     * Protocol-specific metrics with direction tracking.
     */
    public static class ProtocolMetrics {
        private final String protocol;
        private final AtomicLong transferCount = new AtomicLong(0);
        private final AtomicLong totalBytes = new AtomicLong(0);
        private final AtomicLong totalDurationMs = new AtomicLong(0);
        private final AtomicLong downloadCount = new AtomicLong(0);
        private final AtomicLong uploadCount = new AtomicLong(0);
        private final AtomicLong downloadBytes = new AtomicLong(0);
        private final AtomicLong uploadBytes = new AtomicLong(0);
        
        public ProtocolMetrics(String protocol) {
            this.protocol = protocol;
        }
        
        void recordTransfer(long bytes, Duration duration) {
            recordTransfer(bytes, duration, null);
        }
        
        void recordTransfer(long bytes, Duration duration, TransferDirection direction) {
            transferCount.incrementAndGet();
            totalBytes.addAndGet(bytes);
            totalDurationMs.addAndGet(duration.toMillis());
            
            // Track direction-specific metrics
            if (direction == TransferDirection.DOWNLOAD) {
                downloadCount.incrementAndGet();
                downloadBytes.addAndGet(bytes);
            } else if (direction == TransferDirection.UPLOAD) {
                uploadCount.incrementAndGet();
                uploadBytes.addAndGet(bytes);
            }
        }
        
        public String getProtocol() { return protocol; }
        public long getTransferCount() { return transferCount.get(); }
        public long getTotalBytes() { return totalBytes.get(); }
        public long getTotalDurationMs() { return totalDurationMs.get(); }
        public long getDownloadCount() { return downloadCount.get(); }
        public long getUploadCount() { return uploadCount.get(); }
        public long getDownloadBytes() { return downloadBytes.get(); }
        public long getUploadBytes() { return uploadBytes.get(); }
        
        public double getAverageBytesPerSecond() {
            if (totalDurationMs.get() == 0) return 0;
            return (totalBytes.get() * 1000.0) / totalDurationMs.get();
        }
    }

    /**
     * Transfer exception.
     */
    public static class TransferException extends Exception {
        public TransferException(String message) {
            super(message);
        }
        
        public TransferException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
