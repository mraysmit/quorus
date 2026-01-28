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

package dev.mars.quorus.simulator.protocol;

import dev.mars.quorus.simulator.fs.InMemoryFileSystemSimulator;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * In-memory simulator for file transfer protocols (FTP, SFTP, HTTP, SMB).
 * 
 * <p>This simulator enables testing of file transfer operations without real
 * network connections or protocol servers. It works with {@link InMemoryFileSystemSimulator}
 * to provide complete file transfer simulation with:
 * <ul>
 *   <li>Protocol-specific behavior simulation</li>
 *   <li>Progress reporting with configurable update intervals</li>
 *   <li>Bandwidth throttling and latency simulation</li>
 *   <li>Failure injection (auth failures, timeouts, interruptions)</li>
 *   <li>Resume/pause support</li>
 * </ul>
 * 
 * <h2>Usage Example:</h2>
 * <pre>{@code
 * InMemoryFileSystemSimulator fs = new InMemoryFileSystemSimulator();
 * fs.createFile("/remote/data.csv", testData);
 * 
 * InMemoryTransferProtocolSimulator sftp = 
 *     InMemoryTransferProtocolSimulator.sftp(fs);
 * sftp.setSimulatedBytesPerSecond(1_000_000); // 1 MB/s
 * 
 * TransferResult result = sftp.transfer(request, context);
 * }</pre>
 * 
 * @author Mark Andrew Ray-Smith
 * @since 1.0
 */
public class InMemoryTransferProtocolSimulator {

    private final String protocolName;
    private final InMemoryFileSystemSimulator fileSystem;
    private final ScheduledExecutorService scheduler;
    
    // Protocol capabilities
    private boolean supportsResume = true;
    private boolean supportsPause = true;
    private long maxFileSize = Long.MAX_VALUE;
    
    // Performance simulation
    private long minLatencyMs = 0;
    private long maxLatencyMs = 0;
    private long simulatedBytesPerSecond = Long.MAX_VALUE;
    private int progressUpdateIntervalMs = 100;
    
    // Chaos engineering
    private ProtocolFailureMode failureMode = ProtocolFailureMode.NONE;
    private double failureRate = 0.0;
    private int failAtPercent = -1;
    
    // Statistics
    private final AtomicLong totalTransfers = new AtomicLong(0);
    private final AtomicLong successfulTransfers = new AtomicLong(0);
    private final AtomicLong failedTransfers = new AtomicLong(0);
    private final AtomicLong totalBytesTransferred = new AtomicLong(0);
    
    // Active transfers for pause/resume
    private final Map<String, ActiveTransfer> activeTransfers = new ConcurrentHashMap<>();

    /**
     * Failure modes for chaos engineering.
     */
    public enum ProtocolFailureMode {
        /** Normal operation */
        NONE,
        /** Authentication failure */
        AUTH_FAILURE,
        /** Connection timeout */
        CONNECTION_TIMEOUT,
        /** Connection refused by server */
        CONNECTION_REFUSED,
        /** Source file not found */
        FILE_NOT_FOUND,
        /** Permission denied */
        PERMISSION_DENIED,
        /** Destination disk full */
        DISK_FULL,
        /** Transfer interrupted mid-way */
        TRANSFER_INTERRUPTED,
        /** Checksum mismatch (corruption) */
        CHECKSUM_MISMATCH,
        /** Very slow transfer */
        SLOW_TRANSFER,
        /** Intermittent connection drops */
        FLAKY_CONNECTION
    }

    /**
     * Creates a new protocol simulator.
     *
     * @param protocolName the protocol name (ftp, sftp, http, smb)
     * @param fileSystem the virtual file system to use
     */
    public InMemoryTransferProtocolSimulator(String protocolName, InMemoryFileSystemSimulator fileSystem) {
        this.protocolName = protocolName.toLowerCase();
        this.fileSystem = fileSystem;
        this.scheduler = Executors.newScheduledThreadPool(2, r -> {
            Thread t = new Thread(r, "protocol-simulator-" + protocolName);
            t.setDaemon(true);
            return t;
        });
        configureProtocolDefaults();
    }

    // ==================== Factory Methods ====================

    /**
     * Creates an FTP protocol simulator.
     *
     * @param fileSystem the virtual file system
     * @return a new FTP simulator
     */
    public static InMemoryTransferProtocolSimulator ftp(InMemoryFileSystemSimulator fileSystem) {
        InMemoryTransferProtocolSimulator sim = new InMemoryTransferProtocolSimulator("ftp", fileSystem);
        sim.supportsResume = true;
        sim.supportsPause = true;
        return sim;
    }

    /**
     * Creates an SFTP protocol simulator.
     *
     * @param fileSystem the virtual file system
     * @return a new SFTP simulator
     */
    public static InMemoryTransferProtocolSimulator sftp(InMemoryFileSystemSimulator fileSystem) {
        InMemoryTransferProtocolSimulator sim = new InMemoryTransferProtocolSimulator("sftp", fileSystem);
        sim.supportsResume = true;
        sim.supportsPause = true;
        return sim;
    }

    /**
     * Creates an HTTP protocol simulator.
     *
     * @param fileSystem the virtual file system
     * @return a new HTTP simulator
     */
    public static InMemoryTransferProtocolSimulator http(InMemoryFileSystemSimulator fileSystem) {
        InMemoryTransferProtocolSimulator sim = new InMemoryTransferProtocolSimulator("http", fileSystem);
        sim.supportsResume = true; // Via Range headers
        sim.supportsPause = true;
        return sim;
    }

    /**
     * Creates an SMB protocol simulator.
     *
     * @param fileSystem the virtual file system
     * @return a new SMB simulator
     */
    public static InMemoryTransferProtocolSimulator smb(InMemoryFileSystemSimulator fileSystem) {
        InMemoryTransferProtocolSimulator sim = new InMemoryTransferProtocolSimulator("smb", fileSystem);
        sim.supportsResume = true;
        sim.supportsPause = true;
        return sim;
    }

    // ==================== Protocol Interface ====================

    /**
     * Gets the protocol name.
     *
     * @return protocol name (ftp, sftp, http, smb)
     */
    public String getProtocolName() {
        return protocolName;
    }

    /**
     * Checks if this protocol can handle the given request.
     *
     * @param request the transfer request
     * @return true if this protocol can handle the request
     */
    public boolean canHandle(TransferRequest request) {
        if (request == null || request.sourceUri() == null) {
            return false;
        }
        String scheme = request.sourceUri().getScheme();
        if (scheme == null) {
            return false;
        }
        return scheme.equalsIgnoreCase(protocolName) || 
               (protocolName.equals("http") && scheme.equalsIgnoreCase("https"));
    }

    /**
     * Performs a synchronous file transfer.
     *
     * @param request the transfer request
     * @param context the transfer context
     * @return the transfer result
     * @throws TransferException if the transfer fails
     */
    public TransferResult transfer(TransferRequest request, TransferContext context) throws TransferException {
        try {
            return transferReactive(request, context).get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new TransferException("Transfer interrupted", e);
        } catch (ExecutionException e) {
            if (e.getCause() instanceof TransferException te) {
                throw te;
            }
            throw new TransferException("Transfer failed", e.getCause());
        }
    }

    /**
     * Performs an asynchronous file transfer.
     *
     * @param request the transfer request
     * @param context the transfer context
     * @return a future containing the transfer result
     */
    public CompletableFuture<TransferResult> transferReactive(TransferRequest request, TransferContext context) {
        String transferId = generateTransferId();
        totalTransfers.incrementAndGet();
        
        return CompletableFuture.supplyAsync(() -> {
            try {
                return executeTransfer(transferId, request, context);
            } catch (TransferException e) {
                failedTransfers.incrementAndGet();
                throw new CompletionException(e);
            }
        }, scheduler);
    }

    /**
     * Whether this protocol supports resuming interrupted transfers.
     *
     * @return true if resume is supported
     */
    public boolean supportsResume() {
        return supportsResume;
    }

    /**
     * Whether this protocol supports pausing transfers.
     *
     * @return true if pause is supported
     */
    public boolean supportsPause() {
        return supportsPause;
    }

    /**
     * Gets the maximum file size this protocol can transfer.
     *
     * @return max file size in bytes
     */
    public long getMaxFileSize() {
        return maxFileSize;
    }

    // ==================== Transfer Control ====================

    /**
     * Pauses an active transfer.
     *
     * @param transferId the transfer ID
     * @return true if the transfer was paused
     */
    public boolean pauseTransfer(String transferId) {
        ActiveTransfer transfer = activeTransfers.get(transferId);
        if (transfer != null && transfer.state == TransferState.IN_PROGRESS) {
            transfer.state = TransferState.PAUSED;
            return true;
        }
        return false;
    }

    /**
     * Resumes a paused transfer.
     *
     * @param transferId the transfer ID
     * @return true if the transfer was resumed
     */
    public boolean resumeTransfer(String transferId) {
        ActiveTransfer transfer = activeTransfers.get(transferId);
        if (transfer != null && transfer.state == TransferState.PAUSED) {
            transfer.state = TransferState.IN_PROGRESS;
            synchronized (transfer) {
                transfer.notifyAll();
            }
            return true;
        }
        return false;
    }

    /**
     * Cancels an active transfer.
     *
     * @param transferId the transfer ID
     * @return true if the transfer was cancelled
     */
    public boolean cancelTransfer(String transferId) {
        ActiveTransfer transfer = activeTransfers.get(transferId);
        if (transfer != null) {
            transfer.state = TransferState.CANCELLED;
            synchronized (transfer) {
                transfer.notifyAll();
            }
            return true;
        }
        return false;
    }

    // ==================== Configuration ====================

    /**
     * Sets the connection latency range.
     *
     * @param minMs minimum latency in milliseconds
     * @param maxMs maximum latency in milliseconds
     * @return this simulator for chaining
     */
    public InMemoryTransferProtocolSimulator setLatencyConfig(long minMs, long maxMs) {
        this.minLatencyMs = minMs;
        this.maxLatencyMs = maxMs;
        return this;
    }

    /**
     * Sets the simulated transfer speed.
     *
     * @param bytesPerSecond bytes per second
     * @return this simulator for chaining
     */
    public InMemoryTransferProtocolSimulator setSimulatedBytesPerSecond(long bytesPerSecond) {
        this.simulatedBytesPerSecond = bytesPerSecond;
        return this;
    }

    /**
     * Sets the progress update interval.
     *
     * @param intervalMs interval in milliseconds
     * @return this simulator for chaining
     */
    public InMemoryTransferProtocolSimulator setProgressUpdateIntervalMs(int intervalMs) {
        this.progressUpdateIntervalMs = intervalMs;
        return this;
    }

    /**
     * Sets the failure mode for the next transfer.
     *
     * @param mode the failure mode
     * @return this simulator for chaining
     */
    public InMemoryTransferProtocolSimulator setFailureMode(ProtocolFailureMode mode) {
        this.failureMode = mode;
        return this;
    }

    /**
     * Sets the random failure rate.
     *
     * @param rate failure rate (0.0 to 1.0)
     * @return this simulator for chaining
     */
    public InMemoryTransferProtocolSimulator setFailureRate(double rate) {
        this.failureRate = Math.max(0.0, Math.min(1.0, rate));
        return this;
    }

    /**
     * Sets the percentage at which to fail the transfer.
     *
     * @param percent percentage (0-100), or -1 to disable
     * @return this simulator for chaining
     */
    public InMemoryTransferProtocolSimulator setFailureAtPercent(int percent) {
        this.failAtPercent = percent;
        return this;
    }

    /**
     * Sets the maximum file size this protocol can handle.
     *
     * @param maxSize max file size in bytes
     * @return this simulator for chaining
     */
    public InMemoryTransferProtocolSimulator setMaxFileSize(long maxSize) {
        this.maxFileSize = maxSize;
        return this;
    }

    /**
     * Sets whether resume is supported.
     *
     * @param supported true if resume is supported
     * @return this simulator for chaining
     */
    public InMemoryTransferProtocolSimulator setSupportsResume(boolean supported) {
        this.supportsResume = supported;
        return this;
    }

    /**
     * Sets whether pause is supported.
     *
     * @param supported true if pause is supported
     * @return this simulator for chaining
     */
    public InMemoryTransferProtocolSimulator setSupportsPause(boolean supported) {
        this.supportsPause = supported;
        return this;
    }

    /**
     * Resets all chaos engineering settings.
     *
     * @return this simulator for chaining
     */
    public InMemoryTransferProtocolSimulator reset() {
        this.failureMode = ProtocolFailureMode.NONE;
        this.failureRate = 0.0;
        this.failAtPercent = -1;
        return this;
    }

    // ==================== Statistics ====================

    /**
     * Gets total transfer count.
     *
     * @return total transfers initiated
     */
    public long getTotalTransfers() {
        return totalTransfers.get();
    }

    /**
     * Gets successful transfer count.
     *
     * @return successful transfers
     */
    public long getSuccessfulTransfers() {
        return successfulTransfers.get();
    }

    /**
     * Gets failed transfer count.
     *
     * @return failed transfers
     */
    public long getFailedTransfers() {
        return failedTransfers.get();
    }

    /**
     * Gets total bytes transferred.
     *
     * @return bytes transferred
     */
    public long getTotalBytesTransferred() {
        return totalBytesTransferred.get();
    }

    /**
     * Resets statistics.
     */
    public void resetStatistics() {
        totalTransfers.set(0);
        successfulTransfers.set(0);
        failedTransfers.set(0);
        totalBytesTransferred.set(0);
    }

    /**
     * Shuts down the simulator.
     */
    public void shutdown() {
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

    private void configureProtocolDefaults() {
        switch (protocolName) {
            case "ftp" -> {
                minLatencyMs = 50;
                maxLatencyMs = 200;
            }
            case "sftp" -> {
                minLatencyMs = 30;
                maxLatencyMs = 150;
            }
            case "http", "https" -> {
                minLatencyMs = 10;
                maxLatencyMs = 100;
            }
            case "smb" -> {
                minLatencyMs = 5;
                maxLatencyMs = 50;
            }
        }
    }

    private TransferResult executeTransfer(String transferId, TransferRequest request, TransferContext context) 
            throws TransferException {
        Instant startTime = Instant.now();
        
        // Simulate connection latency
        simulateLatency();
        
        // Check for immediate failures
        checkPreTransferFailures(request);
        
        // Determine transfer direction
        TransferDirection direction = request.getDirection();
        
        byte[] content;
        String sourcePath;
        String destPath;
        
        if (direction == TransferDirection.DOWNLOAD) {
            // DOWNLOAD: Read from remote source, write to local destination
            sourcePath = extractPath(request.sourceUri());
            destPath = extractPath(request.destinationUri());
            try {
                content = fileSystem.readFile(sourcePath);
            } catch (IOException e) {
                throw new TransferException("Source file not found: " + sourcePath, e);
            }
        } else if (direction == TransferDirection.UPLOAD) {
            // UPLOAD: Read from local source, write to remote destination
            sourcePath = extractPath(request.sourceUri());
            destPath = extractPath(request.destinationUri());
            try {
                content = fileSystem.readFile(sourcePath);
            } catch (IOException e) {
                throw new TransferException("Local source file not found: " + sourcePath, e);
            }
        } else {
            // REMOTE_TO_REMOTE: Not yet supported
            throw new TransferException("Remote-to-remote transfers not yet supported");
        }
        
        // Check file size
        if (content.length > maxFileSize) {
            throw new TransferException("File size " + content.length + 
                " exceeds maximum " + maxFileSize);
        }
        
        // Create active transfer record
        ActiveTransfer activeTransfer = new ActiveTransfer(transferId, request, content.length);
        activeTransfers.put(transferId, activeTransfer);
        
        try {
            // Calculate resume position
            long resumeFrom = 0;
            if (request.resumeFromBytes() > 0 && supportsResume) {
                resumeFrom = request.resumeFromBytes();
                activeTransfer.bytesTransferred.set(resumeFrom);
            }
            
            // Simulate transfer with progress
            simulateTransferProgress(activeTransfer, content, context, resumeFrom);
            
            // Check for post-transfer failures
            checkPostTransferFailures();
            
            // Write to destination (works for both upload and download in virtual FS)
            try {
                fileSystem.writeFile(destPath, content);
            } catch (IOException e) {
                throw new TransferException("Failed to write destination: " + destPath, e);
            }
            
            successfulTransfers.incrementAndGet();
            totalBytesTransferred.addAndGet(content.length - resumeFrom);
            
            return new TransferResult(
                transferId,
                true,
                content.length,
                Duration.between(startTime, Instant.now()),
                resumeFrom,
                null
            );
        } finally {
            activeTransfers.remove(transferId);
        }
    }

    private void simulateLatency() {
        if (maxLatencyMs > 0) {
            long latency = minLatencyMs + ThreadLocalRandom.current().nextLong(maxLatencyMs - minLatencyMs + 1);
            try {
                Thread.sleep(latency);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private void checkPreTransferFailures(TransferRequest request) throws TransferException {
        // Check random failure
        if (failureRate > 0 && ThreadLocalRandom.current().nextDouble() < failureRate) {
            throw new TransferException("Random transfer failure");
        }
        
        // Check specific failure modes
        switch (failureMode) {
            case AUTH_FAILURE -> throw new TransferException("Authentication failed for " + 
                request.sourceUri().getHost());
            case CONNECTION_TIMEOUT -> throw new TransferException("Connection timed out connecting to " + 
                request.sourceUri().getHost());
            case CONNECTION_REFUSED -> throw new TransferException("Connection refused by " + 
                request.sourceUri().getHost());
            case FILE_NOT_FOUND -> throw new TransferException("File not found: " + 
                request.sourceUri().getPath());
            case PERMISSION_DENIED -> throw new TransferException("Permission denied: " + 
                request.sourceUri().getPath());
            case DISK_FULL -> throw new TransferException("Disk full on destination");
            default -> {}
        }
    }

    private void checkPostTransferFailures() throws TransferException {
        if (failureMode == ProtocolFailureMode.CHECKSUM_MISMATCH) {
            throw new TransferException("Checksum mismatch: file may be corrupted");
        }
    }

    private void simulateTransferProgress(ActiveTransfer transfer, byte[] content, 
            TransferContext context, long resumeFrom) throws TransferException {
        
        long totalBytes = content.length;
        long bytesToTransfer = totalBytes - resumeFrom;
        
        if (bytesToTransfer <= 0) {
            return; // Already complete
        }
        
        // Calculate chunk size based on speed and update interval
        long chunkSize;
        if (simulatedBytesPerSecond == Long.MAX_VALUE) {
            // Fast mode - transfer in one chunk
            chunkSize = bytesToTransfer;
        } else if (failureMode == ProtocolFailureMode.SLOW_TRANSFER) {
            // Very slow - 10KB/s
            chunkSize = Math.max(1024, (10_000L * progressUpdateIntervalMs) / 1000);
        } else {
            chunkSize = Math.max(1024, (simulatedBytesPerSecond * progressUpdateIntervalMs) / 1000);
        }
        
        long bytesRemaining = bytesToTransfer;
        
        while (bytesRemaining > 0) {
            // Check for cancellation
            if (transfer.state == TransferState.CANCELLED) {
                throw new TransferException("Transfer cancelled");
            }
            
            // Handle pause
            while (transfer.state == TransferState.PAUSED) {
                synchronized (transfer) {
                    try {
                        transfer.wait(100);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new TransferException("Transfer interrupted while paused");
                    }
                }
                if (transfer.state == TransferState.CANCELLED) {
                    throw new TransferException("Transfer cancelled while paused");
                }
            }
            
            // Check for flaky connection
            if (failureMode == ProtocolFailureMode.FLAKY_CONNECTION && 
                ThreadLocalRandom.current().nextDouble() < 0.1) {
                // 10% chance of temporary disconnect
                try {
                    Thread.sleep(500 + ThreadLocalRandom.current().nextInt(1000));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            
            // Transfer chunk
            long chunk = Math.min(chunkSize, bytesRemaining);
            transfer.bytesTransferred.addAndGet(chunk);
            bytesRemaining -= chunk;
            
            // Calculate progress
            int percent = (int) ((transfer.bytesTransferred.get() * 100) / totalBytes);
            
            // Check for failure at specific percent
            if (failAtPercent >= 0 && percent >= failAtPercent) {
                if (failureMode == ProtocolFailureMode.TRANSFER_INTERRUPTED) {
                    throw new TransferException("Transfer interrupted at " + percent + "%");
                }
            }
            
            // Report progress
            if (context != null && context.progressCallback() != null) {
                context.progressCallback().accept(new TransferProgress(
                    transfer.transferId,
                    transfer.bytesTransferred.get(),
                    totalBytes,
                    percent,
                    calculateEta(bytesRemaining)
                ));
            }
            
            // Simulate transfer time
            if (simulatedBytesPerSecond < Long.MAX_VALUE && chunk > 0) {
                long delayMs = (chunk * 1000) / simulatedBytesPerSecond;
                if (delayMs > 0) {
                    try {
                        Thread.sleep(delayMs);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new TransferException("Transfer interrupted");
                    }
                }
            }
        }
        
        // Final progress callback
        if (context != null && context.progressCallback() != null) {
            context.progressCallback().accept(new TransferProgress(
                transfer.transferId,
                totalBytes,
                totalBytes,
                100,
                Duration.ZERO
            ));
        }
    }

    private Duration calculateEta(long bytesRemaining) {
        if (simulatedBytesPerSecond == Long.MAX_VALUE || simulatedBytesPerSecond == 0) {
            return Duration.ZERO;
        }
        long secondsRemaining = bytesRemaining / simulatedBytesPerSecond;
        return Duration.ofSeconds(secondsRemaining);
    }

    private String extractPath(URI uri) {
        String path = uri.getPath();
        if (path == null || path.isEmpty()) {
            return "/";
        }

        // Standardize Windows file URIs: file:///C:/path -> /path
        if ("file".equalsIgnoreCase(uri.getScheme()) && path.startsWith("/")) {
            // Look for pattern: / + Letter + : + /
            if (path.length() >= 3 && Character.isLetter(path.charAt(1)) && path.charAt(2) == ':') {
                String normalized = path.substring(3);
                return normalized.isEmpty() ? "/" : normalized;
            }
        }
        
        return path;
    }

    private String generateTransferId() {
        return protocolName + "-" + System.nanoTime() + "-" + 
            ThreadLocalRandom.current().nextInt(10000);
    }

    // ==================== Inner Classes ====================

    private enum TransferState {
        IN_PROGRESS, PAUSED, CANCELLED, COMPLETED
    }

    private static class ActiveTransfer {
        final String transferId;
        final TransferRequest request;
        final long totalBytes;
        final AtomicLong bytesTransferred = new AtomicLong(0);
        volatile TransferState state = TransferState.IN_PROGRESS;

        ActiveTransfer(String transferId, TransferRequest request, long totalBytes) {
            this.transferId = transferId;
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
        URI sourceUri,
        URI destinationUri,
        long resumeFromBytes,
        Map<String, String> options
    ) {
        /**
         * Creates a download request (backward compatibility).
         * @param sourceUri remote source URI
         * @param destinationPath local destination path
         */
        public TransferRequest(URI sourceUri, Path destinationPath) {
            this(sourceUri, destinationPath.toUri(), 0, Map.of());
        }
        
        /**
         * Gets the destination as a Path (only valid for downloads).
         * @return the destination path
         * @throws UnsupportedOperationException if destination is not file://
         */
        public Path destinationPath() {
            if (!"file".equalsIgnoreCase(destinationUri.getScheme())) {
                throw new UnsupportedOperationException(
                    "destinationPath() only valid for file:// destinations. Use destinationUri() instead. " +
                    "Current destination: " + destinationUri);
            }
            return Path.of(destinationUri);
        }
        
        /**
         * Determines the transfer direction based on URI schemes.
         * @return the transfer direction
         */
        public TransferDirection getDirection() {
            boolean sourceIsFile = "file".equalsIgnoreCase(sourceUri.getScheme());
            boolean destIsFile = "file".equalsIgnoreCase(destinationUri.getScheme());
            
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
            private URI sourceUri;
            private URI destinationUri;
            private Path destinationPath; // For backward compatibility
            private long resumeFromBytes = 0;
            private Map<String, String> options = Map.of();
            
            public Builder sourceUri(URI uri) {
                this.sourceUri = uri;
                return this;
            }
            
            /**
             * Sets the destination URI (new bidirectional API).
             * @param uri the destination URI (can be file://, sftp://, ftp://, etc.)
             */
            public Builder destinationUri(URI uri) {
                this.destinationUri = uri;
                return this;
            }
            
            /**
             * Sets the destination path (backward compatibility - converts to file:// URI).
             * @param path the local destination path
             */
            public Builder destinationPath(Path path) {
                this.destinationPath = path;
                return this;
            }
            
            public Builder resumeFromBytes(long bytes) {
                this.resumeFromBytes = bytes;
                return this;
            }
            
            public Builder options(Map<String, String> options) {
                this.options = options;
                return this;
            }
            
            public TransferRequest build() {
                // Prefer destinationUri, fall back to destinationPath converted to URI
                URI destUri = destinationUri != null ? destinationUri : 
                    (destinationPath != null ? destinationPath.toUri() : null);
                if (destUri == null) {
                    throw new IllegalStateException("Either destinationUri or destinationPath must be set");
                }
                return new TransferRequest(sourceUri, destUri, resumeFromBytes, options);
            }
        }
    }

    /**
     * Transfer context with callbacks.
     */
    public record TransferContext(
        Consumer<TransferProgress> progressCallback,
        Map<String, Object> attributes
    ) {
        public TransferContext() {
            this(null, Map.of());
        }
        
        public TransferContext(Consumer<TransferProgress> progressCallback) {
            this(progressCallback, Map.of());
        }
    }

    /**
     * Transfer progress information.
     */
    public record TransferProgress(
        String transferId,
        long bytesTransferred,
        long totalBytes,
        int percentComplete,
        Duration estimatedTimeRemaining
    ) {}

    /**
     * Transfer result.
     */
    public record TransferResult(
        String transferId,
        boolean successful,
        long bytesTransferred,
        Duration duration,
        long resumedFromBytes,
        String errorMessage
    ) {
        public boolean isSuccessful() {
            return successful;
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
