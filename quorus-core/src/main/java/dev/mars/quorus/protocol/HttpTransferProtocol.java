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

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * HTTP/HTTPS transfer protocol implementation.
 * Supports basic HTTP downloads with progress tracking and integrity verification.
 */
public class HttpTransferProtocol implements TransferProtocol {
    private static final Logger logger = Logger.getLogger(HttpTransferProtocol.class.getName());
    
    private static final int BUFFER_SIZE = 8192; // 8KB buffer
    private static final int CONNECTION_TIMEOUT_MS = 30000; // 30 seconds
    private static final int READ_TIMEOUT_MS = 60000; // 60 seconds
    private static final long MAX_FILE_SIZE = 10L * 1024 * 1024 * 1024; // 10GB
    
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
        logger.info("Starting HTTP transfer for job: " + context.getJobId());
        
        Instant startTime = Instant.now();
        ProgressTracker progressTracker = new ProgressTracker(context.getJobId());
        progressTracker.start();
        
        try {
            // Validate request
            validateRequest(request);
            
            // Create connection
            HttpURLConnection connection = createConnection(request);
            
            // Get file size
            long contentLength = connection.getContentLengthLong();
            if (contentLength > 0) {
                progressTracker.setTotalBytes(contentLength);
                context.getJob().setTotalBytes(contentLength);
            }
            
            // Create destination directory if needed
            Path destinationPath = request.getDestinationPath();
            Files.createDirectories(destinationPath.getParent());
            
            // Create temporary file for download
            Path tempFile = destinationPath.resolveSibling(destinationPath.getFileName() + ".tmp");
            
            // Perform the transfer
            String actualChecksum = performTransfer(connection, tempFile, context, progressTracker);
            
            // Verify checksum if provided
            if (request.getExpectedChecksum() != null && !request.getExpectedChecksum().isEmpty()) {
                if (!request.getExpectedChecksum().equals(actualChecksum)) {
                    Files.deleteIfExists(tempFile);
                    throw new TransferException(context.getJobId(), 
                            "Checksum mismatch - expected: " + request.getExpectedChecksum() + 
                            ", actual: " + actualChecksum);
                }
            }
            
            // Move temp file to final destination
            Files.move(tempFile, destinationPath, StandardCopyOption.REPLACE_EXISTING);
            
            // Complete the job
            context.getJob().complete(actualChecksum);
            
            logger.info("HTTP transfer completed successfully for job: " + context.getJobId());
            
            return TransferResult.builder()
                    .requestId(context.getJobId())
                    .finalStatus(TransferStatus.COMPLETED)
                    .bytesTransferred(progressTracker.getTransferredBytes())
                    .startTime(startTime)
                    .endTime(Instant.now())
                    .actualChecksum(actualChecksum)
                    .build();
                    
        } catch (Exception e) {
            logger.log(Level.SEVERE, "HTTP transfer failed for job: " + context.getJobId(), e);
            
            return TransferResult.builder()
                    .requestId(context.getJobId())
                    .finalStatus(TransferStatus.FAILED)
                    .bytesTransferred(progressTracker.getTransferredBytes())
                    .startTime(startTime)
                    .endTime(Instant.now())
                    .errorMessage(e.getMessage())
                    .cause(e)
                    .build();
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
    
    private HttpURLConnection createConnection(TransferRequest request) throws TransferException {
        try {
            URL url = request.getSourceUri().toURL();
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            
            // Set timeouts
            connection.setConnectTimeout(CONNECTION_TIMEOUT_MS);
            connection.setReadTimeout(READ_TIMEOUT_MS);
            
            // Set user agent
            connection.setRequestProperty("User-Agent", "Quorus/1.0");
            
            // Add any custom headers from metadata
            request.getMetadata().forEach((key, value) -> {
                if (key.startsWith("header.")) {
                    String headerName = key.substring(7);
                    connection.setRequestProperty(headerName, value);
                }
            });
            
            return connection;
            
        } catch (Exception e) {
            throw new TransferException(request.getRequestId(), "Failed to create HTTP connection", e);
        }
    }
    
    private String performTransfer(HttpURLConnection connection, Path tempFile, 
                                 TransferContext context, ProgressTracker progressTracker) throws TransferException {
        try (InputStream inputStream = new BufferedInputStream(connection.getInputStream());
             OutputStream outputStream = new BufferedOutputStream(Files.newOutputStream(tempFile))) {
            
            ChecksumCalculator checksumCalculator = new ChecksumCalculator();
            byte[] buffer = new byte[BUFFER_SIZE];
            long totalBytesRead = 0;
            int bytesRead;
            
            while ((bytesRead = inputStream.read(buffer)) != -1 && context.shouldContinue()) {
                // Handle pause
                if (context.isPaused()) {
                    if (!context.waitForResumeOrCancel(60000)) { // Wait up to 1 minute
                        break;
                    }
                }
                
                outputStream.write(buffer, 0, bytesRead);
                checksumCalculator.update(buffer, 0, bytesRead);
                
                totalBytesRead += bytesRead;
                progressTracker.updateProgress(totalBytesRead);
                context.getJob().updateProgress(totalBytesRead);
            }
            
            outputStream.flush();
            
            if (context.isCancelled()) {
                throw new TransferException(context.getJobId(), "Transfer was cancelled");
            }
            
            return checksumCalculator.getChecksum();
            
        } catch (IOException e) {
            throw new TransferException(context.getJobId(), "Failed to transfer file", e);
        }
    }
}
