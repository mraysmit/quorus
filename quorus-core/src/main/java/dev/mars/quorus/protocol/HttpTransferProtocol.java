package dev.mars.quorus.protocol;

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
import dev.mars.quorus.core.TransferRequest;
import dev.mars.quorus.core.TransferResult;
import dev.mars.quorus.core.TransferStatus;
import dev.mars.quorus.core.exceptions.TransferException;
import dev.mars.quorus.storage.ChecksumCalculator;
import dev.mars.quorus.transfer.ProgressTracker;
import dev.mars.quorus.transfer.TransferContext;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.WebClientOptions;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;

/**
 * HTTP/HTTPS transfer protocol implementation.
 * Supports both reactive (Vert.x Web Client) and blocking (HttpURLConnection) modes.
 * Phase 2 migration: Uses Vert.x Web Client when Vertx instance is available.
 *
 * @author Mark Andrew Ray-Smith Cityline Ltd
 * @since 2025-08-17
 * @version 1.0
 */
public class HttpTransferProtocol implements TransferProtocol {
    private static final Logger logger = LoggerFactory.getLogger(HttpTransferProtocol.class);

    private static final int BUFFER_SIZE = 8192; // 8KB buffer
    private static final int CONNECTION_TIMEOUT_MS = 30000; // 30 seconds
    private static final int READ_TIMEOUT_MS = 60000; // 60 seconds
    private static final long MAX_FILE_SIZE = 10L * 1024 * 1024 * 1024; // 10GB

    private final Vertx vertx;
    private final WebClient webClient;

    /**
     * Constructor with Vert.x dependency injection (recommended - uses reactive Web Client).
     * @param vertx Vert.x instance for reactive HTTP operations
     */
    public HttpTransferProtocol(Vertx vertx) {
        this.vertx = java.util.Objects.requireNonNull(vertx, "Vertx instance cannot be null");
        this.webClient = WebClient.create(vertx, new WebClientOptions()
            .setConnectTimeout(CONNECTION_TIMEOUT_MS)
            .setIdleTimeout(READ_TIMEOUT_MS)
            .setUserAgent("Quorus/1.0"));
        logger.info("HttpTransferProtocol initialized: connectTimeout={}ms, readTimeout={}ms, maxFileSize={} bytes",
                   CONNECTION_TIMEOUT_MS, READ_TIMEOUT_MS, MAX_FILE_SIZE);
        logger.debug("HttpTransferProtocol created with Vert.x Web Client (reactive mode)");
    }

    @Override
    public String getProtocolName() {
        return "http";
    }

    @Override
    public boolean canHandle(TransferRequest request) {
        if (request == null || request.getSourceUri() == null) {
            logger.debug("canHandle: request or sourceUri is null");
            return false;
        }

        TransferDirection direction = request.getDirection();
        
        if (direction == TransferDirection.DOWNLOAD) {
            // Download: HTTP/HTTPS source -> local destination
            String scheme = request.getSourceUri().getScheme();
            boolean canHandle = "http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme);
            logger.debug("canHandle: HTTP download check, scheme={}, canHandle={}", scheme, canHandle);
            return canHandle;
        } else if (direction == TransferDirection.UPLOAD) {
            // Upload: local source -> HTTP/HTTPS destination
            URI destinationUri = request.getDestinationUri();
            if (destinationUri == null) {
                logger.debug("canHandle: Upload with null destination URI");
                return false;
            }
            String scheme = destinationUri.getScheme();
            boolean canHandle = "http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme);
            logger.debug("canHandle: HTTP upload check, scheme={}, canHandle={}", scheme, canHandle);
            return canHandle;
        }
        
        logger.debug("canHandle: Unknown direction={}", direction);
        return false;
    }
    
    @Override
    public TransferResult transfer(TransferRequest request, TransferContext context) throws TransferException {
        logger.debug("Starting synchronous HTTP transfer: jobId={}, direction={}", 
                    context.getJobId(), request.getDirection());
        try {
            return transferReactive(request, context).toCompletionStage().toCompletableFuture().get();
        } catch (Exception e) {
            Throwable cause = e.getCause();
            if (cause instanceof TransferException) {
                throw (TransferException) cause;
            }
            String errorMsg = cause != null ? cause.getMessage() : e.getMessage();
            logger.error("HTTP transfer failed: jobId={}, error={}", context.getJobId(), errorMsg);
            if (logger.isDebugEnabled()) {
                logger.debug("HTTP transfer exception details for job: {}", context.getJobId(), cause != null ? cause : e);
            }
            throw new TransferException(context.getJobId(), "Transfer failed", cause != null ? cause : e);
        }
    }

    @Override
    public io.vertx.core.Future<TransferResult> transferReactive(TransferRequest request, TransferContext context) {
        Instant startTime = Instant.now();
        ProgressTracker progressTracker = new ProgressTracker(context.getJobId());
        progressTracker.start();

        logger.debug("Starting reactive HTTP transfer: jobId={}, direction={}", 
                    context.getJobId(), request.getDirection());

        try {
            validateRequest(request);

            TransferDirection direction = request.getDirection();
            if (direction == TransferDirection.UPLOAD) {
                logger.debug("Routing to HTTP upload handler");
                return performHttpUpload(request, context, startTime, progressTracker);
            } else {
                logger.debug("Routing to HTTP download handler");
                return performHttpDownload(request, context, startTime, progressTracker);
            }
        } catch (Exception e) {
            logger.error("Transfer validation failed: jobId={}, error={}", context.getJobId(), e.getMessage());
            return io.vertx.core.Future.failedFuture(e);
        }
    }

    /**
     * Performs HTTP download (HTTP/HTTPS source -> local destination).
     */
    private io.vertx.core.Future<TransferResult> performHttpDownload(
            TransferRequest request, 
            TransferContext context,
            Instant startTime,
            ProgressTracker progressTracker) {
        
        try {
            Path destinationPath = request.getDestinationPath();
            Path tempFile = destinationPath.resolveSibling(destinationPath.getFileName() + ".tmp");
            logger.debug("HTTP download: destination={}, tempFile={}", destinationPath, tempFile);

            // Create destination directory if needed
            try {
                Files.createDirectories(destinationPath.getParent());
                logger.debug("Created destination directory: {}", destinationPath.getParent());
            } catch (IOException e) {
                logger.error("Failed to create directory for job {}: {}", context.getJobId(), e.getMessage());
                return io.vertx.core.Future.failedFuture(new TransferException(context.getJobId(), "Failed to create directory", e));
            }

            String url = request.getSourceUri().toString();
            logger.info("Starting reactive HTTP download from {}", url);

            return webClient.getAbs(url)
                .timeout(READ_TIMEOUT_MS)
                .send()
                .compose(response -> {
                    logger.debug("HTTP response received: statusCode={}, statusMessage={}", 
                                response.statusCode(), response.statusMessage());
                    
                    if (response.statusCode() != 200) {
                        logger.error("HTTP download failed: statusCode={}, statusMessage={}", 
                                    response.statusCode(), response.statusMessage());
                        return io.vertx.core.Future.failedFuture(new TransferException(context.getJobId(),
                            "HTTP " + response.statusCode() + ": " + response.statusMessage()));
                    }

                    Buffer body = response.body();
                    if (body == null) {
                        logger.error("HTTP download failed: empty response body");
                        return io.vertx.core.Future.failedFuture(new TransferException(context.getJobId(), "Empty response body"));
                    }

                    long bytesTransferred = body.length();
                    logger.debug("Response body received: {} bytes", bytesTransferred);
                    
                    progressTracker.setTotalBytes(bytesTransferred);
                    progressTracker.updateProgress(bytesTransferred);
                    context.getJob().setTotalBytes(bytesTransferred);
                    context.getJob().updateProgress(bytesTransferred);

                    // Calculate checksum
                    logger.debug("Calculating checksum for downloaded content");
                    ChecksumCalculator checksumCalculator = new ChecksumCalculator();
                    checksumCalculator.update(body.getBytes(), 0, body.length());
                    String actualChecksum = checksumCalculator.getChecksum();
                    logger.debug("Checksum calculated: {}", actualChecksum);

                    // Verify checksum if provided
                    if (request.getExpectedChecksum() != null && !request.getExpectedChecksum().isEmpty()) {
                        if (!request.getExpectedChecksum().equals(actualChecksum)) {
                            logger.error("Checksum mismatch: expected={}, actual={}", 
                                        request.getExpectedChecksum(), actualChecksum);
                            return io.vertx.core.Future.failedFuture(new TransferException(context.getJobId(),
                                "Checksum mismatch - expected: " + request.getExpectedChecksum() +
                                ", actual: " + actualChecksum));
                        }
                        logger.debug("Checksum verified successfully");
                    }

                    // Write to file using Vert.x FileSystem
                    logger.debug("Writing {} bytes to temp file: {}", bytesTransferred, tempFile);
                    return vertx.fileSystem().writeFile(tempFile.toString(), body)
                        .compose(v -> {
                            logger.debug("Moving temp file to destination: {} -> {}", tempFile, destinationPath);
                            return vertx.fileSystem().move(tempFile.toString(), destinationPath.toString(), new io.vertx.core.file.CopyOptions().setReplaceExisting(true));
                        })
                        .map(v -> {
                            context.getJob().complete(actualChecksum);
                            logger.info("HTTP download completed: jobId={}, bytesTransferred={}, checksum={}",
                                       context.getJobId(), bytesTransferred, actualChecksum);
                            
                            return TransferResult.builder()
                                .requestId(context.getJobId())
                                .finalStatus(TransferStatus.COMPLETED)
                                .bytesTransferred(bytesTransferred)
                                .startTime(startTime)
                                .endTime(Instant.now())
                                .actualChecksum(actualChecksum)
                                .build();
                        });
                })
                .recover(err -> {
                    logger.error("HTTP download failed for job {}: {}", context.getJobId(), err.getMessage());
                    if (logger.isDebugEnabled()) {
                        logger.debug("HTTP download exception details for job: {}", context.getJobId(), err);
                    }
                    return io.vertx.core.Future.failedFuture(err);
                });
        } catch (Exception e) {
            logger.error("HTTP download setup failed: {}", e.getMessage());
            return io.vertx.core.Future.failedFuture(e);
        }
    }

    /**
     * Performs HTTP upload (local source -> HTTP/HTTPS destination) using PUT method.
     */
    private io.vertx.core.Future<TransferResult> performHttpUpload(
            TransferRequest request, 
            TransferContext context,
            Instant startTime,
            ProgressTracker progressTracker) {
        
        try {
            Path sourcePath = Path.of(request.getSourceUri());
            logger.debug("HTTP upload: sourcePath={}", sourcePath);
            
            // Validate source file exists
            if (!Files.exists(sourcePath)) {
                logger.error("Source file not found: {}", sourcePath);
                return io.vertx.core.Future.failedFuture(
                    new TransferException(context.getJobId(), "Source file not found: " + sourcePath));
            }

            URI destinationUri = request.getDestinationUri();
            String url = destinationUri.toString();
            logger.info("Starting reactive HTTP upload to {}", url);

            // Read file content
            logger.debug("Reading source file: {}", sourcePath);
            return vertx.fileSystem().readFile(sourcePath.toString())
                .compose(fileContent -> {
                    long bytesTransferred = fileContent.length();
                    logger.debug("Source file loaded: {} bytes", bytesTransferred);
                    
                    progressTracker.setTotalBytes(bytesTransferred);
                    context.getJob().setTotalBytes(bytesTransferred);

                    // Calculate checksum before upload
                    logger.debug("Calculating checksum for source file");
                    ChecksumCalculator checksumCalculator = new ChecksumCalculator();
                    checksumCalculator.update(fileContent.getBytes(), 0, fileContent.length());
                    String actualChecksum = checksumCalculator.getChecksum();
                    logger.debug("Source file checksum: {}", actualChecksum);

                    // Verify expected checksum if provided
                    if (request.getExpectedChecksum() != null && !request.getExpectedChecksum().isEmpty()) {
                        if (!request.getExpectedChecksum().equals(actualChecksum)) {
                            logger.error("Checksum mismatch before upload: expected={}, actual={}",
                                        request.getExpectedChecksum(), actualChecksum);
                            return io.vertx.core.Future.failedFuture(new TransferException(context.getJobId(),
                                "Checksum mismatch - expected: " + request.getExpectedChecksum() +
                                ", actual: " + actualChecksum));
                        }
                        logger.debug("Pre-upload checksum verified successfully");
                    }

                    // Perform PUT request
                    logger.debug("Sending HTTP PUT request: {} bytes to {}", bytesTransferred, url);
                    return webClient.putAbs(url)
                        .timeout(READ_TIMEOUT_MS)
                        .sendBuffer(fileContent)
                        .map(response -> {
                            int statusCode = response.statusCode();
                            logger.debug("HTTP PUT response: statusCode={}, statusMessage={}",
                                        statusCode, response.statusMessage());
                            
                            // Accept 200 OK, 201 Created, 204 No Content as success
                            if (statusCode != 200 && statusCode != 201 && statusCode != 204) {
                                logger.error("HTTP PUT failed: statusCode={}, statusMessage={}",
                                            statusCode, response.statusMessage());
                                throw new RuntimeException(new TransferException(context.getJobId(),
                                    "HTTP PUT failed - " + statusCode + ": " + response.statusMessage()));
                            }

                            progressTracker.updateProgress(bytesTransferred);
                            context.getJob().updateProgress(bytesTransferred);
                            context.getJob().complete(actualChecksum);
                            
                            logger.info("HTTP upload completed: jobId={}, bytesTransferred={}, statusCode={}",
                                       context.getJobId(), bytesTransferred, statusCode);

                            return TransferResult.builder()
                                .requestId(context.getJobId())
                                .finalStatus(TransferStatus.COMPLETED)
                                .bytesTransferred(bytesTransferred)
                                .startTime(startTime)
                                .endTime(Instant.now())
                                .actualChecksum(actualChecksum)
                                .build();
                        });
                })
                .recover(err -> {
                    logger.error("HTTP upload failed for job {}: {}", context.getJobId(), err.getMessage());
                    Throwable cause = err.getCause();
                    if (cause instanceof TransferException) {
                        return io.vertx.core.Future.failedFuture(cause);
                    }
                    return io.vertx.core.Future.failedFuture(err);
                });
        } catch (Exception e) {
            logger.error("HTTP upload setup failed: {}", e.getMessage());
            return io.vertx.core.Future.failedFuture(e);
        }
    }
    
    @Override
    public boolean supportsResume() {
        return false; // Basic implementation doesn't support resume yet
    }
    
    @Override
    public boolean supportsPause() {
        return true; // Can be paused via context
    }
    
    @Override
    public long getMaxFileSize() {
        return MAX_FILE_SIZE;
    }
    
    @Override
    public void abort() {
        logger.debug("HTTP abort called - reactive transfers handle cancellation via Future");
        // HTTP transfers are reactive and cancellation is handled via Future cancellation
        // No explicit resources to close
    }
    
    private void validateRequest(TransferRequest request) throws TransferException {
        logger.debug("Validating HTTP transfer request: requestId={}", request.getRequestId());
        
        if (request.getSourceUri() == null) {
            logger.error("Validation failed: Source URI is null");
            throw new TransferException(request.getRequestId(), "Source URI cannot be null");
        }
        
        TransferDirection direction = request.getDirection();
        
        if (direction == TransferDirection.DOWNLOAD) {
            if (request.getDestinationPath() == null) {
                logger.error("Validation failed: Destination path is null for download");
                throw new TransferException(request.getRequestId(), "Destination path cannot be null for download");
            }
        } else if (direction == TransferDirection.UPLOAD) {
            if (request.getDestinationUri() == null) {
                logger.error("Validation failed: Destination URI is null for upload");
                throw new TransferException(request.getRequestId(), "Destination URI cannot be null for upload");
            }
        }
        
        if (!canHandle(request)) {
            logger.error("Validation failed: HTTP protocol cannot handle this request type");
            throw new TransferException(request.getRequestId(), "HTTP protocol cannot handle this request");
        }
        
        logger.debug("Request validation passed");
    }
}
