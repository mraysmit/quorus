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

package dev.mars.quorus.protocol;

import dev.mars.quorus.core.TransferRequest;
import dev.mars.quorus.core.TransferResult;
import dev.mars.quorus.core.TransferStatus;
import dev.mars.quorus.core.exceptions.TransferException;
import dev.mars.quorus.storage.ChecksumCalculator;
import dev.mars.quorus.transfer.TransferContext;
import dev.mars.quorus.transfer.ProgressTracker;

import static dev.mars.quorus.core.exceptions.QuorusErrorCode.*;
import io.vertx.core.Context;
import io.vertx.core.Vertx;

import com.jcraft.jsch.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.time.Instant;
/**
 * Description for SftpTransferProtocol
 *
 * @author Mark Andrew Ray-Smith Cityline Ltd
 * @version 1.0
 * @since 2025-08-18
 */

public class SftpTransferProtocol implements TransferProtocol {
    
    private static final Logger logger = LoggerFactory.getLogger(SftpTransferProtocol.class);
    private static final int DEFAULT_SFTP_PORT = 22;
    private static final int DEFAULT_BUFFER_SIZE = 32 * 1024; // 32KB buffer for SFTP
    private static final Duration CONNECTION_TIMEOUT = Duration.ofSeconds(30);

    // Track active SFTP client for abort capability
    private volatile SftpClient activeClient;
    
    @Override
    public String getProtocolName() {
        return "sftp";
    }
    
    @Override
    public boolean canHandle(TransferRequest request) {
        logger.debug("canHandle: checking request={}", request != null ? request.getRequestId() : "null");
        
        if (request == null || request.getSourceUri() == null) {
            logger.debug("canHandle: returning false - request or sourceUri is null");
            return false;
        }

        // Check if this is a download (SFTP source)
        String sourceScheme = request.getSourceUri().getScheme();
        logger.debug("canHandle: sourceScheme={}", sourceScheme);
        if ("sftp".equalsIgnoreCase(sourceScheme)) {
            logger.debug("canHandle: returning true - SFTP download detected");
            return true;
        }

        // Check if this is an upload (SFTP destination)
        if (request.getDestinationUri() != null) {
            String destScheme = request.getDestinationUri().getScheme();
            logger.debug("canHandle: destScheme={}", destScheme);
            if ("sftp".equalsIgnoreCase(destScheme)) {
                logger.debug("canHandle: returning true - SFTP upload detected");
                return true;
            }
        }

        logger.debug("canHandle: returning false - not an SFTP transfer");
        return false;
    }

    @Override
    public TransferResult transfer(TransferRequest request, TransferContext context) throws TransferException {
        Context vertxContext = Vertx.currentContext();
        if (vertxContext != null && vertxContext.isEventLoopContext()) {
            throw new TransferException(context.getJobId(),
                    "Blocking SFTP transfer() invoked on event loop. Use transferReactive() instead.");
        }

        logger.info("Starting SFTP transfer for job: {}", context.getJobId());
        logger.debug("transfer: request={}, sourceUri={}, destinationUri={}, isUpload={}", 
            request.getRequestId(), request.getSourceUri(), request.getDestinationUri(), request.isUpload());

        ProgressTracker progressTracker = new ProgressTracker(context.getJobId());
        progressTracker.start();
        logger.debug("transfer: progress tracker initialized for job={}", context.getJobId());

        try {
            TransferResult result = performSftpTransfer(request, progressTracker);
            logger.debug("transfer: completed successfully, bytesTransferred={}", result.getBytesTransferred());
            return result;
        } catch (TransferException e) {
            throw e; // Already logged at inner method level
        } catch (Exception e) {
            logger.error("[{}] SFTP transfer failed: requestId={}, error={}", QUORUS_1100.code(), request.getRequestId(), e.getMessage());
            logger.debug("SFTP transfer exception details for request: {}", request.getRequestId(), e);
            throw new TransferException(context.getJobId(), "SFTP transfer failed", e);
        }
    }

    @Override
    public boolean supportsResume() {
        return false; // SFTP resume not implemented in this version
    }

    @Override
    public boolean supportsPause() {
        return false; // SFTP pause not implemented in this version
    }

    @Override
    public long getMaxFileSize() {
        return -1; // No specific limit for SFTP
    }
    
    @Override
    public void abort() {
        logger.debug("abort: attempting to abort SFTP transfer");
        SftpClient client = activeClient;
        if (client != null) {
            logger.info("Aborting SFTP transfer - forcibly closing session");
            client.forceDisconnect();
            activeClient = null;
            logger.debug("abort: SFTP session forcibly closed");
        } else {
            logger.debug("abort: no active client to abort");
        }
    }
    
    private TransferResult performSftpTransfer(TransferRequest request, ProgressTracker progressTracker)
            throws TransferException {

        Instant startTime = Instant.now();
        String requestId = request.getRequestId();
        logger.debug("performSftpTransfer: starting for requestId={}, isUpload={}", requestId, request.isUpload());

        try {
            // Route based on transfer direction
            if (request.isUpload()) {
                logger.debug("performSftpTransfer: routing to upload handler");
                return performSftpUpload(request, progressTracker, startTime);
            } else {
                logger.debug("performSftpTransfer: routing to download handler");
                return performSftpDownload(request, progressTracker, startTime);
            }

        } catch (Exception e) {
            logger.error("[{}] SFTP transfer routing failed: requestId={}, error={}", QUORUS_1101.code(), requestId, e.getMessage());
            logger.debug("SFTP transfer routing exception details for request: {}", requestId, e);
            throw new TransferException(requestId, "SFTP transfer failed", e);
        }
    }

    /**
     * Performs an SFTP download (Remote to Local transfer).
     */
    private TransferResult performSftpDownload(TransferRequest request, ProgressTracker progressTracker,
                                               Instant startTime) throws TransferException {

        String requestId = request.getRequestId();
        logger.debug("performSftpDownload: starting for requestId={}", requestId);

        try {
            logger.info("Starting SFTP download: {} -> {}", request.getSourceUri(), request.getDestinationPath());

            // Parse SFTP URI and extract connection details
            SftpConnectionInfo connectionInfo = parseSftpUri(request.getSourceUri());
            logger.debug("performSftpDownload: parsed connection info - host={}, port={}, path={}", 
                connectionInfo.host, connectionInfo.port, connectionInfo.path);

            // Create SFTP client and perform transfer
            logger.debug("performSftpDownload: creating SFTP client");
            SftpClient sftpClient = new SftpClient(connectionInfo);
            activeClient = sftpClient; // Track for abort capability

            try {
                logger.debug("performSftpDownload: establishing connection");
                sftpClient.connect();
                logger.debug("performSftpDownload: connection established successfully");

                // Get file size for progress tracking
                long fileSize = sftpClient.getFileSize(connectionInfo.path);
                logger.debug("performSftpDownload: remote file size={} bytes", fileSize);

                // Ensure destination directory exists
                Files.createDirectories(request.getDestinationPath().getParent());
                logger.debug("performSftpDownload: destination directory ensured");

                // Perform the file transfer
                logger.debug("performSftpDownload: starting file transfer");
                long bytesTransferred = sftpClient.downloadFile(connectionInfo.path,
                        request.getDestinationPath(), progressTracker, fileSize);
                logger.debug("performSftpDownload: file transfer complete, bytesTransferred={}", bytesTransferred);

                // Calculate checksum if required
                String checksum = null;
                if (request.getExpectedChecksum() != null && !request.getExpectedChecksum().isEmpty()) {
                    logger.info("Calculating checksum for transferred file");
                    logger.debug("performSftpDownload: checksum calculation requested");
                    checksum = ChecksumCalculator.calculateFileChecksum(request.getDestinationPath());
                }

                Instant endTime = Instant.now();
                Duration transferTime = Duration.between(startTime, endTime);

                logger.info("SFTP download completed successfully: {} bytes in {}ms", bytesTransferred, transferTime.toMillis());
                logger.debug("performSftpDownload: transfer rate={} KB/s", 
                    transferTime.toMillis() > 0 ? (bytesTransferred / 1024.0) / (transferTime.toMillis() / 1000.0) : 0);

                return TransferResult.builder()
                        .requestId(requestId)
                        .finalStatus(TransferStatus.COMPLETED)
                        .bytesTransferred(bytesTransferred)
                        .startTime(startTime)
                        .endTime(endTime)
                        .actualChecksum(checksum)
                        .build();

            } finally {
                activeClient = null;
                logger.debug("performSftpDownload: disconnecting SFTP client");
                sftpClient.disconnect();
            }

        } catch (Exception e) {
            logger.error("[{}] SFTP download failed: requestId={}, error={}", QUORUS_1102.code(), requestId, e.getMessage());
            logger.debug("SFTP download exception details for request: {}", requestId, e);
            throw new TransferException(requestId, "SFTP download failed", e);
        }
    }

    /**
     * Performs an SFTP upload (Local to Remote transfer).
     */
    private TransferResult performSftpUpload(TransferRequest request, ProgressTracker progressTracker,
                                             Instant startTime) throws TransferException {

        String requestId = request.getRequestId();
        logger.debug("performSftpUpload: starting for requestId={}", requestId);

        try {
            URI destinationUri = request.getDestinationUri();
            logger.info("Starting SFTP upload: {} -> {}", request.getSourceUri(), destinationUri);

            // Parse destination SFTP URI and extract connection details
            SftpConnectionInfo connectionInfo = parseSftpUri(destinationUri);
            logger.debug("performSftpUpload: parsed connection info - host={}, port={}, path={}", 
                connectionInfo.host, connectionInfo.port, connectionInfo.path);

            // Validate host
            if (connectionInfo.host == null || connectionInfo.host.isEmpty()) {
                logger.debug("performSftpUpload: validation failed - missing host");
                throw new TransferException(requestId, "SFTP destination host is required");
            }

            // Validate destination path
            if (connectionInfo.path == null || connectionInfo.path.isEmpty()) {
                logger.debug("performSftpUpload: validation failed - missing path");
                throw new TransferException(requestId, "SFTP destination path is required");
            }

            // Validate source file exists
            java.nio.file.Path sourcePath = java.nio.file.Paths.get(request.getSourceUri());
            logger.debug("performSftpUpload: checking source file exists at {}", sourcePath);
            if (!Files.exists(sourcePath)) {
                logger.debug("performSftpUpload: validation failed - source file not found");
                throw new TransferException(requestId, "Source file does not exist: " + sourcePath);
            }

            // Create SFTP client and perform upload
            logger.debug("performSftpUpload: creating SFTP client");
            SftpClient sftpClient = new SftpClient(connectionInfo);
            activeClient = sftpClient;

            try {
                logger.debug("performSftpUpload: establishing connection");
                sftpClient.connect();
                logger.debug("performSftpUpload: connection established successfully");

                // Get source file size for progress tracking
                long fileSize = Files.size(sourcePath);
                logger.debug("performSftpUpload: source file size={} bytes", fileSize);

                // Perform the upload
                logger.debug("performSftpUpload: starting file upload");
                long bytesTransferred = sftpClient.uploadFile(sourcePath, connectionInfo.path,
                        progressTracker, fileSize);
                logger.debug("performSftpUpload: file upload complete, bytesTransferred={}", bytesTransferred);

                // Calculate checksum if required
                String checksum = null;
                if (request.getExpectedChecksum() != null && !request.getExpectedChecksum().isEmpty()) {
                    logger.info("Calculating checksum for uploaded file");
                    logger.debug("performSftpUpload: checksum calculation requested");
                    checksum = ChecksumCalculator.calculateFileChecksum(sourcePath);
                }

                Instant endTime = Instant.now();
                Duration transferTime = Duration.between(startTime, endTime);

                logger.info("SFTP upload completed successfully: {} bytes in {}ms", bytesTransferred, transferTime.toMillis());
                logger.debug("performSftpUpload: transfer rate={} KB/s", 
                    transferTime.toMillis() > 0 ? (bytesTransferred / 1024.0) / (transferTime.toMillis() / 1000.0) : 0);

                return TransferResult.builder()
                        .requestId(requestId)
                        .finalStatus(TransferStatus.COMPLETED)
                        .bytesTransferred(bytesTransferred)
                        .startTime(startTime)
                        .endTime(endTime)
                        .actualChecksum(checksum)
                        .build();

            } finally {
                activeClient = null;
                logger.debug("performSftpUpload: disconnecting SFTP client");
                sftpClient.disconnect();
            }

        } catch (Exception e) {
            logger.error("[{}] SFTP upload failed: requestId={}, error={}", QUORUS_1103.code(), requestId, e.getMessage());
            logger.debug("SFTP upload exception details for request: {}", requestId, e);
            throw new TransferException(requestId, "SFTP upload failed", e);
        }
    }
    
    /**
     * Real SFTP client implementation using JSch
     */
    private static class SftpClient {
        private final SftpConnectionInfo connectionInfo;
        private JSch jsch;
        private Session session;
        private ChannelSftp sftpChannel;

        SftpClient(SftpConnectionInfo connectionInfo) {
            this.connectionInfo = connectionInfo;
            this.jsch = new JSch();
        }

        void connect() throws JSchException {
            logger.debug("Connecting to SFTP server: {}:{}", connectionInfo.host, connectionInfo.port);
            logger.debug("SftpClient.connect: username={}, hasPassword={}", 
                connectionInfo.username, connectionInfo.password != null);

            // Create session
            logger.debug("SftpClient.connect: creating JSch session");
            session = jsch.getSession(connectionInfo.username, connectionInfo.host, connectionInfo.port);

            if (connectionInfo.password != null) {
                session.setPassword(connectionInfo.password);
                logger.debug("SftpClient.connect: password authentication configured");
            }

            // Configure session properties
            session.setConfig("StrictHostKeyChecking", "no"); // For demo purposes
            session.setTimeout((int) CONNECTION_TIMEOUT.toMillis());
            logger.debug("SftpClient.connect: session configured with timeout={}ms", CONNECTION_TIMEOUT.toMillis());

            // Connect session
            logger.debug("SftpClient.connect: establishing SSH session");
            session.connect();
            logger.debug("SftpClient.connect: SSH session established");

            // Open SFTP channel
            logger.debug("SftpClient.connect: opening SFTP channel");
            sftpChannel = (ChannelSftp) session.openChannel("sftp");
            sftpChannel.connect();

            logger.info("SFTP connection established successfully");
            logger.debug("SftpClient.connect: SFTP channel ready");
        }

        long getFileSize(String remotePath) throws SftpException {
            logger.debug("SftpClient.getFileSize: remotePath={}", remotePath);
            try {
                SftpATTRS attrs = sftpChannel.stat(remotePath);
                long size = attrs.getSize();
                logger.debug("SftpClient.getFileSize: size={} bytes", size);
                return size;
            } catch (SftpException e) {
                logger.warn("Could not get file size for {}: {}", remotePath, e.getMessage());
                return -1; // Unknown size
            }
        }

        long downloadFile(String remotePath, java.nio.file.Path localPath,
                         ProgressTracker progressTracker, long fileSize) throws SftpException, IOException {

            long bytesTransferred = 0;

            try (OutputStream fileOutput = Files.newOutputStream(localPath,
                    StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING);
                 BufferedOutputStream bufferedOutput = new BufferedOutputStream(fileOutput, DEFAULT_BUFFER_SIZE)) {

                // Create progress monitor
                SftpProgressMonitor progressMonitor = new SftpProgressMonitor() {
                    private long transferred = 0;

                    @Override
                    public void init(int op, String src, String dest, long max) {
                        transferred = 0;
                    }

                    @Override
                    public boolean count(long count) {
                        transferred += count;
                        progressTracker.updateProgress(transferred);

                        // Check for cancellation
                        return !Thread.currentThread().isInterrupted();
                    }

                    @Override
                    public void end() {
                        // Transfer completed
                    }
                };

                // Download file with progress monitoring
                sftpChannel.get(remotePath, bufferedOutput, progressMonitor);
                bufferedOutput.flush();

                bytesTransferred = Files.size(localPath);
            }

            return bytesTransferred;
        }

        /**
         * Uploads a local file to the remote SFTP server.
         */
        long uploadFile(java.nio.file.Path localPath, String remotePath,
                       ProgressTracker progressTracker, long fileSize) throws SftpException, IOException {

            long bytesTransferred = 0;

            try (InputStream fileInput = Files.newInputStream(localPath);
                 BufferedInputStream bufferedInput = new BufferedInputStream(fileInput, DEFAULT_BUFFER_SIZE)) {

                // Create progress monitor
                SftpProgressMonitor progressMonitor = new SftpProgressMonitor() {
                    private long transferred = 0;

                    @Override
                    public void init(int op, String src, String dest, long max) {
                        transferred = 0;
                    }

                    @Override
                    public boolean count(long count) {
                        transferred += count;
                        progressTracker.updateProgress(transferred);

                        // Check for cancellation
                        return !Thread.currentThread().isInterrupted();
                    }

                    @Override
                    public void end() {
                        // Transfer completed
                    }
                };

                // Ensure parent directory exists on remote server
                String parentDir = getParentPath(remotePath);
                if (parentDir != null && !parentDir.isEmpty()) {
                    mkdirs(parentDir);
                }

                // Upload file with progress monitoring
                sftpChannel.put(bufferedInput, remotePath, progressMonitor, ChannelSftp.OVERWRITE);

                bytesTransferred = fileSize;
            }

            return bytesTransferred;
        }

        /**
         * Creates remote directories recursively.
         */
        private void mkdirs(String path) throws SftpException {
            String[] folders = path.split("/");
            StringBuilder currentPath = new StringBuilder();

            for (String folder : folders) {
                if (folder.isEmpty()) {
                    currentPath.append("/");
                    continue;
                }
                currentPath.append(folder).append("/");
                try {
                    sftpChannel.stat(currentPath.toString());
                } catch (SftpException e) {
                    if (e.id == ChannelSftp.SSH_FX_NO_SUCH_FILE) {
                        sftpChannel.mkdir(currentPath.toString());
                    }
                }
            }
        }

        /**
         * Gets the parent path from a full path.
         */
        private String getParentPath(String path) {
            if (path == null || path.isEmpty()) {
                return null;
            }
            int lastSlash = path.lastIndexOf('/');
            if (lastSlash <= 0) {
                return null;
            }
            return path.substring(0, lastSlash);
        }

        void disconnect() {
            logger.debug("SftpClient.disconnect: closing connections");
            try {
                if (sftpChannel != null && sftpChannel.isConnected()) {
                    sftpChannel.disconnect();
                    logger.debug("SftpClient.disconnect: SFTP channel disconnected");
                }
            } catch (Exception e) {
                logger.warn("Error disconnecting SFTP channel: {}", e.getMessage());
            }

            try {
                if (session != null && session.isConnected()) {
                    session.disconnect();
                    logger.debug("SftpClient.disconnect: SSH session disconnected");
                }
            } catch (Exception e) {
                logger.warn("Error disconnecting SFTP session: {}", e.getMessage());
            }
        }
        
        /**
         * Force disconnect without graceful shutdown.
         * Used for aborting transfers - immediately closes the session.
         */
        void forceDisconnect() {
            logger.debug("SftpClient.forceDisconnect: force closing all connections");
            try {
                if (sftpChannel != null && sftpChannel.isConnected()) {
                    logger.info("Force closing SFTP channel");
                    sftpChannel.disconnect();
                    logger.debug("SftpClient.forceDisconnect: SFTP channel force closed");
                }
            } catch (Exception e) {
                logger.warn("Error during SFTP channel force disconnect: {}", e.getMessage());
            }
            
            try {
                if (session != null && session.isConnected()) {
                    logger.info("Force closing SFTP session");
                    session.disconnect();
                    logger.debug("SftpClient.forceDisconnect: SSH session force closed");
                }
            } catch (Exception e) {
                logger.warn("Error during SFTP session force disconnect: {}", e.getMessage());
            }
        }
    }
    
    private SftpConnectionInfo parseSftpUri(URI sourceUri) throws TransferException {
        String scheme = sourceUri.getScheme();
        if (!"sftp".equalsIgnoreCase(scheme)) {
            throw new TransferException("unknown", "Invalid SFTP URI scheme: " + scheme);
        }
        
        String host = sourceUri.getHost();
        if (host == null || host.isEmpty()) {
            throw new TransferException("unknown", "SFTP URI must specify a host");
        }
        
        int port = sourceUri.getPort();
        if (port == -1) {
            port = DEFAULT_SFTP_PORT;
        }
        
        String path = sourceUri.getPath();
        if (path == null || path.isEmpty()) {
            throw new TransferException("unknown", "SFTP URI must specify a path");
        }
        
        // Parse authentication info if present
        String userInfo = sourceUri.getUserInfo();
        String username = null;
        String password = null;
        
        if (userInfo != null) {
            String[] parts = userInfo.split(":", 2);
            username = parts[0];
            if (parts.length > 1) {
                password = parts[1];
            }
        }
        
        return new SftpConnectionInfo(host, port, path, username, password);
    }

    /**
     * SFTP connection information
     */
    private static class SftpConnectionInfo {
        final String host;
        final int port;
        final String path;
        final String username;
        final String password;
        
        SftpConnectionInfo(String host, int port, String path, String username, String password) {
            this.host = host;
            this.port = port;
            this.path = path;
            this.username = username;
            this.password = password;
        }
        
        boolean hasAuthentication() {
            return username != null && !username.isEmpty();
        }
        
        @Override
        public String toString() {
            return "SftpConnectionInfo{" +
                    "host='" + host + '\'' +
                    ", port=" + port +
                    ", path='" + path + '\'' +
                    ", username='" + username + '\'' +
                    ", hasAuth=" + hasAuthentication() +
                    '}';
        }
    }
}
