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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.logging.Level;
import java.util.logging.Logger;

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
    private static final Logger logger = Logger.getLogger(HttpTransferProtocol.class.getName());

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
        logger.info("HttpTransferProtocol created with Vert.x Web Client (reactive mode)");
    }

    @Override
    public String getProtocolName() {
        return "http";
    }

    @Override
    public boolean canHandle(TransferRequest request) {
        if (request == null || request.getSourceUri() == null) {
            return false;
        }

        String scheme = request.getSourceUri().getScheme();
        return "http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme);
    }
    
    @Override
    public TransferResult transfer(TransferRequest request, TransferContext context) throws TransferException {
        try {
            return transferReactive(request, context).toCompletionStage().toCompletableFuture().get();
        } catch (Exception e) {
            Throwable cause = e.getCause();
            if (cause instanceof TransferException) {
                throw (TransferException) cause;
            }
            throw new TransferException(context.getJobId(), "Transfer failed", cause != null ? cause : e);
        }
    }

    @Override
    public io.vertx.core.Future<TransferResult> transferReactive(TransferRequest request, TransferContext context) {
        Instant startTime = Instant.now();
        ProgressTracker progressTracker = new ProgressTracker(context.getJobId());
        progressTracker.start();

        try {
            validateRequest(request);

            Path destinationPath = request.getDestinationPath();
            Path tempFile = destinationPath.resolveSibling(destinationPath.getFileName() + ".tmp");

            // Create destination directory if needed
            try {
                Files.createDirectories(destinationPath.getParent());
            } catch (IOException e) {
                return io.vertx.core.Future.failedFuture(new TransferException(context.getJobId(), "Failed to create directory", e));
            }

            String url = request.getSourceUri().toString();
            logger.info("Starting reactive HTTP transfer from " + url);

            return webClient.getAbs(url)
                .timeout(READ_TIMEOUT_MS)
                .send()
                .compose(response -> {
                    if (response.statusCode() != 200) {
                        return io.vertx.core.Future.failedFuture(new TransferException(context.getJobId(),
                            "HTTP " + response.statusCode() + ": " + response.statusMessage()));
                    }

                    Buffer body = response.body();
                    if (body == null) {
                        return io.vertx.core.Future.failedFuture(new TransferException(context.getJobId(), "Empty response body"));
                    }

                    long bytesTransferred = body.length();
                    progressTracker.setTotalBytes(bytesTransferred);
                    progressTracker.updateProgress(bytesTransferred);
                    context.getJob().setTotalBytes(bytesTransferred);
                    context.getJob().updateProgress(bytesTransferred);

                    // Calculate checksum
                    ChecksumCalculator checksumCalculator = new ChecksumCalculator();
                    checksumCalculator.update(body.getBytes(), 0, body.length());
                    String actualChecksum = checksumCalculator.getChecksum();

                    // Verify checksum if provided
                    if (request.getExpectedChecksum() != null && !request.getExpectedChecksum().isEmpty()) {
                        if (!request.getExpectedChecksum().equals(actualChecksum)) {
                            return io.vertx.core.Future.failedFuture(new TransferException(context.getJobId(),
                                "Checksum mismatch - expected: " + request.getExpectedChecksum() +
                                ", actual: " + actualChecksum));
                        }
                    }

                    // Write to file using Vert.x FileSystem
                    return vertx.fileSystem().writeFile(tempFile.toString(), body)
                        .compose(v -> vertx.fileSystem().move(tempFile.toString(), destinationPath.toString(), new io.vertx.core.file.CopyOptions().setReplaceExisting(true)))
                        .map(v -> {
                            context.getJob().complete(actualChecksum);
                            logger.info("HTTP transfer completed successfully for job: " + context.getJobId());
                            
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
                    logger.log(Level.SEVERE, "HTTP transfer failed for job: " + context.getJobId(), err);
                    return io.vertx.core.Future.failedFuture(err);
                });

        } catch (Exception e) {
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
        // HTTP transfers are reactive and cancellation is handled via Future cancellation
        // No explicit resources to close
    }
    
    private void validateRequest(TransferRequest request) throws TransferException {
        if (request.getSourceUri() == null) {
            throw new TransferException(request.getRequestId(), "Source URI cannot be null");
        }
        
        if (request.getDestinationPath() == null) {
            throw new TransferException(request.getRequestId(), "Destination path cannot be null");
        }
        
        if (!canHandle(request)) {
            throw new TransferException(request.getRequestId(), "HTTP protocol cannot handle this request");
        }
    }
}
