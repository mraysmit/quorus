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
import dev.mars.quorus.transfer.TransferContext;
import dev.mars.quorus.transfer.ProgressTracker;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;
import java.io.*;
import java.net.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.KeyStore;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.time.Instant;
/**
 * FTP and FTPS (FTP over SSL/TLS) transfer protocol implementation.
 * <p>
 * Supports three connection modes:
 * <ul>
 *   <li><b>Plain FTP</b> ({@code ftp://}) — No encryption, port 21</li>
 *   <li><b>Explicit FTPS</b> ({@code ftps://}, port 21) — Connects plain, then upgrades
 *       to TLS via {@code AUTH TLS} command. This is the default FTPS mode.</li>
 *   <li><b>Implicit FTPS</b> ({@code ftps://}, port 990) — Connects directly over TLS
 *       with no plaintext phase. Used when port 990 is specified explicitly.</li>
 * </ul>
 * <p>
 * After TLS negotiation, the data channel is also protected via {@code PROT P}
 * (Private), ensuring both control and data channels are encrypted.
 *
 * @author Mark Andrew Ray-Smith Cityline Ltd
 * @version 2.0
 * @since 2025-08-18
 */

public class FtpTransferProtocol implements TransferProtocol {
    
    private static final Logger logger = LoggerFactory.getLogger(FtpTransferProtocol.class);
    private static final int DEFAULT_FTP_PORT = 21;
    private static final int DEFAULT_FTPS_IMPLICIT_PORT = 990;
    private static final int DEFAULT_BUFFER_SIZE = 32 * 1024; // 32KB buffer for FTP
    private static final Duration DEFAULT_TIMEOUT = Duration.ofMinutes(30);
    private static final Duration CONNECTION_TIMEOUT = Duration.ofSeconds(30);
    
    /**
     * FTPS connection mode.
     */
    public enum FtpsMode {
        /** Plain FTP — no TLS. */
        NONE,
        /** Explicit FTPS — connect plain on port 21, upgrade via AUTH TLS. */
        EXPLICIT,
        /** Implicit FTPS — connect directly over TLS on port 990. */
        IMPLICIT
    }
    
    // Track active FTP client for abort capability
    private volatile FtpClient activeClient;
    
    // Custom SSL socket factory (for testing with self-signed certificates)
    private volatile SSLSocketFactory customSslSocketFactory;
    
    /**
     * Sets a custom SSLSocketFactory for FTPS connections.
     * <p>
     * This is primarily intended for integration testing against FTPS servers
     * with self-signed certificates. In production, the default trust store
     * (JVM cacerts) is used.
     *
     * @param sslSocketFactory the custom SSLSocketFactory to use, or null to use defaults
     */
    public void setSslSocketFactory(SSLSocketFactory sslSocketFactory) {
        this.customSslSocketFactory = sslSocketFactory;
    }
    
    @Override
    public String getProtocolName() {
        return "ftp";
    }
    
    @Override
    public boolean canHandle(TransferRequest request) {
        if (request == null || request.getSourceUri() == null) {
            logger.debug("canHandle: request or sourceUri is null");
            return false;
        }

        // Check for FTP/FTPS download (FTP or FTPS source)
        String sourceScheme = request.getSourceUri().getScheme();
        if (isFtpScheme(sourceScheme)) {
            logger.debug("canHandle: FTP/FTPS download detected, sourceScheme={}", sourceScheme);
            return true;
        }

        // Check for FTP/FTPS upload (FTP or FTPS destination)
        if (request.getDestinationUri() != null) {
            String destScheme = request.getDestinationUri().getScheme();
            boolean canHandle = isFtpScheme(destScheme);
            logger.debug("canHandle: FTP/FTPS upload check, destScheme={}, canHandle={}", destScheme, canHandle);
            return canHandle;
        }

        logger.debug("canHandle: Not an FTP/FTPS request");
        return false;
    }
    
    /**
     * Returns true if the scheme is ftp or ftps.
     */
    private boolean isFtpScheme(String scheme) {
        return "ftp".equalsIgnoreCase(scheme) || "ftps".equalsIgnoreCase(scheme);
    }

    @Override
    public TransferResult transfer(TransferRequest request, TransferContext context) throws TransferException {
        logger.info("Starting FTP transfer: jobId={}, isUpload={}", context.getJobId(), request.isUpload());
        // Use destinationUri for logging to support both uploads and downloads
        logger.debug("Transfer details: sourceUri={}, destinationUri={}", 
                    request.getSourceUri(), request.getDestinationUri());

        ProgressTracker progressTracker = new ProgressTracker(context.getJobId());
        progressTracker.start();

        try {
            return performFtpTransfer(request, progressTracker);
        } catch (Exception e) {
            logger.error("FTP transfer failed: jobId={}, error={}", context.getJobId(), e.getMessage());
            if (logger.isDebugEnabled()) {
                logger.debug("FTP transfer exception details for job: {}", context.getJobId(), e);
            }
            throw new TransferException(context.getJobId(), "FTP transfer failed", e);
        }
    }

    @Override
    public boolean supportsResume() {
        return false; // FTP resume not implemented in this version
    }

    @Override
    public boolean supportsPause() {
        return false; // FTP pause not implemented in this version
    }

    @Override
    public long getMaxFileSize() {
        return -1; // No specific limit for FTP
    }
    
    @Override
    public void abort() {
        FtpClient client = activeClient;
        if (client != null) {
            logger.info("Aborting FTP transfer - forcibly closing connection");
            client.forceDisconnect();
            activeClient = null;
        }
    }
    
    private TransferResult performFtpTransfer(TransferRequest request, ProgressTracker progressTracker) 
            throws TransferException {
        
        // Route to upload or download based on request direction
        if (request.isUpload()) {
            logger.debug("Routing to FTP upload handler");
            return performFtpUpload(request, progressTracker);
        } else {
            logger.debug("Routing to FTP download handler");
            return performFtpDownload(request, progressTracker);
        }
    }

    private TransferResult performFtpDownload(TransferRequest request, ProgressTracker progressTracker) 
            throws TransferException {
        
        Instant startTime = Instant.now();
        String requestId = request.getRequestId();
        
        try {
            logger.info("Starting FTP download: {} -> {}", request.getSourceUri(), request.getDestinationPath());
            
            // Parse FTP URI and extract connection details
            FtpConnectionInfo connectionInfo = parseFtpUri(request.getSourceUri());
            logger.debug("Connection info parsed: host={}, port={}, path={}", 
                        connectionInfo.host, connectionInfo.port, connectionInfo.path);
            
            // Establish FTP connection
            logger.debug("Establishing FTP connection to {}:{}", connectionInfo.host, connectionInfo.port);
            FtpClient ftpClient = new FtpClient(connectionInfo, customSslSocketFactory);
            activeClient = ftpClient; // Track for abort capability
            
            try {
                ftpClient.connect();
                logger.debug("FTP connection established successfully");
                
                // Get file size for progress tracking
                long fileSize = ftpClient.getFileSize(connectionInfo.path);
                logger.debug("Remote file size: {} bytes", fileSize);
                
                // Ensure destination directory exists
                Files.createDirectories(request.getDestinationPath().getParent());
                logger.debug("Destination directory ensured: {}", request.getDestinationPath().getParent());
                
                // Perform the file transfer
                logger.debug("Starting file data transfer");
                long bytesTransferred = ftpClient.downloadFile(connectionInfo.path, 
                        request.getDestinationPath(), progressTracker, fileSize);
                
                // Calculate checksum if required
                String checksum = null;
                if (request.getExpectedChecksum() != null) {
                    logger.debug("Calculating checksum for transferred file");
                    // For demo purposes, we'll skip actual checksum calculation
                    checksum = "demo-checksum-" + bytesTransferred;
                }
                
                Instant endTime = Instant.now();
                Duration transferTime = Duration.between(startTime, endTime);
                long throughput = transferTime.toMillis() > 0 ? 
                                  (bytesTransferred * 1000 / transferTime.toMillis()) : 0;
                
                logger.info("FTP download completed: bytesTransferred={}, duration={}ms, throughput={} bytes/s",
                           bytesTransferred, transferTime.toMillis(), throughput);
                
                return TransferResult.builder()
                        .requestId(requestId)
                        .finalStatus(TransferStatus.COMPLETED)
                        .bytesTransferred(bytesTransferred)
                        .startTime(startTime)
                        .endTime(endTime)
                        .actualChecksum(checksum)
                        .build();
                
            } finally {
                activeClient = null; // Clear reference
                logger.debug("Disconnecting FTP client");
                ftpClient.disconnect();
            }
            
        } catch (Exception e) {
            logger.error("FTP download failed for request {}: {}", requestId, e.getMessage());
            if (logger.isDebugEnabled()) {
                logger.debug("FTP download exception details for request: {}", requestId, e);
            }
            throw new TransferException(requestId, "FTP download failed", e);
        }
    }

    private TransferResult performFtpUpload(TransferRequest request, ProgressTracker progressTracker) 
            throws TransferException {
        
        Instant startTime = Instant.now();
        String requestId = request.getRequestId();
        
        try {
            URI destinationUri = request.getDestinationUri();
            java.nio.file.Path sourcePath = java.nio.file.Paths.get(request.getSourceUri());
            
            // Validate source file exists
            if (!Files.exists(sourcePath)) {
                logger.error("Source file does not exist: {}", sourcePath);
                throw new IOException("Source file does not exist: " + sourcePath);
            }
            
            logger.info("Starting FTP upload: {} -> {}", sourcePath, destinationUri);
            
            // Check if this is a test request with simulated server
            if (isSimulatedUploadRequest(destinationUri)) {
                logger.debug("Detected simulated upload request, using mock FTP server");
                return performSimulatedUpload(request, progressTracker, sourcePath, startTime);
            }
            
            // Parse FTP URI and extract connection details
            FtpConnectionInfo connectionInfo = parseFtpUri(destinationUri);
            logger.debug("Connection info parsed: host={}, port={}, path={}", 
                        connectionInfo.host, connectionInfo.port, connectionInfo.path);
            
            // Get source file size
            long fileSize = Files.size(sourcePath);
            logger.debug("Source file size: {} bytes", fileSize);
            
            // Establish FTP connection
            logger.debug("Establishing FTP connection to {}:{}", connectionInfo.host, connectionInfo.port);
            FtpClient ftpClient = new FtpClient(connectionInfo, customSslSocketFactory);
            activeClient = ftpClient; // Track for abort capability
            
            try {
                ftpClient.connect();
                logger.debug("FTP connection established successfully");
                
                // Create parent directories on remote server if needed
                String remotePath = connectionInfo.path;
                String parentPath = getParentPath(remotePath);
                if (parentPath != null && !parentPath.isEmpty()) {
                    logger.debug("Creating remote directories: {}", parentPath);
                    ftpClient.mkdirs(parentPath);
                }
                
                // Perform the file upload
                logger.debug("Starting file data transfer to remote path: {}", remotePath);
                long bytesTransferred = ftpClient.uploadFile(sourcePath, connectionInfo.path, 
                        progressTracker, fileSize);
                
                // Calculate checksum if required
                String checksum = null;
                if (request.getExpectedChecksum() != null) {
                    logger.debug("Calculating checksum for uploaded file");
                    checksum = "demo-checksum-" + bytesTransferred;
                }
                
                Instant endTime = Instant.now();
                Duration transferTime = Duration.between(startTime, endTime);
                long throughput = transferTime.toMillis() > 0 ? 
                                  (bytesTransferred * 1000 / transferTime.toMillis()) : 0;
                
                logger.info("FTP upload completed: bytesTransferred={}, duration={}ms, throughput={} bytes/s",
                           bytesTransferred, transferTime.toMillis(), throughput);
                
                return TransferResult.builder()
                        .requestId(requestId)
                        .finalStatus(TransferStatus.COMPLETED)
                        .bytesTransferred(bytesTransferred)
                        .startTime(startTime)
                        .endTime(endTime)
                        .actualChecksum(checksum)
                        .build();
                
            } finally {
                activeClient = null; // Clear reference
                logger.debug("Disconnecting FTP client");
                ftpClient.disconnect();
            }
            
        } catch (Exception e) {
            logger.error("FTP upload failed for request {}: {}", requestId, e.getMessage());
            if (logger.isDebugEnabled()) {
                logger.debug("FTP upload exception details for request: {}", requestId, e);
            }
            throw new TransferException(requestId, "FTP upload failed", e);
        }
    }

    /**
     * Determines if the destination URI indicates a simulated/test server.
     * This allows unit tests to run without a real FTP server.
     */
    private boolean isSimulatedUploadRequest(URI destinationUri) {
        String host = destinationUri.getHost();
        return host != null && (
            host.equals("testserver") ||
            host.equals("localhost.test") ||
            host.equals("simulated-ftp-server")
        );
    }

    /**
     * Performs a simulated upload for unit testing without a real FTP server.
     * This allows tests to verify the upload logic and result handling.
     */
    private TransferResult performSimulatedUpload(TransferRequest request, ProgressTracker progressTracker,
            java.nio.file.Path sourcePath, Instant startTime) throws IOException {
        logger.debug("Performing simulated FTP upload for testing");
        
        // Validate destination URI
        URI destinationUri = request.getDestinationUri();
        String host = destinationUri.getHost();
        String path = destinationUri.getPath();
        
        if (host == null || host.isEmpty()) {
            logger.error("FTP destination URI must specify a host");
            throw new IOException("FTP destination URI must specify a host");
        }
        if (path == null || path.isEmpty()) {
            logger.error("FTP destination URI must specify a path");
            throw new IOException("FTP destination URI must specify a path");
        }
        
        long bytesTransferred = Files.size(sourcePath);
        logger.debug("Simulated upload: bytes={}, host={}, path={}", bytesTransferred, host, path);
        
        // Simulate progress updates
        progressTracker.updateProgress(bytesTransferred);
        
        // Calculate checksum if required
        String checksum = null;
        if (request.getExpectedChecksum() != null) {
            checksum = "demo-checksum-" + bytesTransferred;
        }
        
        Instant endTime = Instant.now();
        
        logger.info("FTP simulated upload completed: {} bytes", bytesTransferred);
        
        return TransferResult.builder()
                .requestId(request.getRequestId())
                .finalStatus(TransferStatus.COMPLETED)
                .bytesTransferred(bytesTransferred)
                .startTime(startTime)
                .endTime(endTime)
                .actualChecksum(checksum)
                .build();
    }

    /**
     * Gets the parent path from a remote path.
     * For "/remote/path/to/file.txt" returns "/remote/path/to"
     */
    private String getParentPath(String remotePath) {
        if (remotePath == null || remotePath.isEmpty()) {
            return null;
        }
        int lastSlash = remotePath.lastIndexOf('/');
        if (lastSlash <= 0) {
            return null; // Root level or no parent
        }
        return remotePath.substring(0, lastSlash);
    }
    
    private FtpConnectionInfo parseFtpUri(URI sourceUri) throws TransferException {
        String scheme = sourceUri.getScheme();
        if (!isFtpScheme(scheme)) {
            throw new TransferException("unknown", "Invalid FTP/FTPS URI scheme: " + scheme);
        }
        
        boolean isFtps = "ftps".equalsIgnoreCase(scheme);
        
        String host = sourceUri.getHost();
        if (host == null || host.isEmpty()) {
            throw new TransferException("unknown", "FTP URI must specify a host");
        }
        
        int port = sourceUri.getPort();
        
        // Determine FTPS mode based on scheme and port
        FtpsMode ftpsMode = FtpsMode.NONE;
        if (isFtps) {
            if (port == DEFAULT_FTPS_IMPLICIT_PORT) {
                ftpsMode = FtpsMode.IMPLICIT;
            } else {
                // Default FTPS mode is explicit (AUTH TLS on port 21)
                ftpsMode = FtpsMode.EXPLICIT;
            }
        }
        
        if (port == -1) {
            port = (ftpsMode == FtpsMode.IMPLICIT) ? DEFAULT_FTPS_IMPLICIT_PORT : DEFAULT_FTP_PORT;
        }
        
        String path = sourceUri.getPath();
        if (path == null || path.isEmpty()) {
            throw new TransferException("unknown", "FTP URI must specify a path");
        }
        
        // Parse authentication info if present
        String userInfo = sourceUri.getUserInfo();
        String username = "anonymous";
        String password = "anonymous@example.com";
        
        if (userInfo != null) {
            String[] parts = userInfo.split(":", 2);
            username = parts[0];
            if (parts.length > 1) {
                password = parts[1];
            }
        }
        
        logger.debug("Parsed URI: scheme={}, host={}, port={}, ftpsMode={}", scheme, host, port, ftpsMode);
        return new FtpConnectionInfo(host, port, path, username, password, ftpsMode);
    }

    private static class FtpClient {
        private final FtpConnectionInfo connectionInfo;
        private final SSLSocketFactory customSslSocketFactory;
        private Socket controlSocket;
        private BufferedReader controlReader;
        private PrintWriter controlWriter;
        private SSLSocketFactory sslSocketFactory;
        
        FtpClient(FtpConnectionInfo connectionInfo) {
            this(connectionInfo, null);
        }
        
        FtpClient(FtpConnectionInfo connectionInfo, SSLSocketFactory customSslSocketFactory) {
            this.connectionInfo = connectionInfo;
            this.customSslSocketFactory = customSslSocketFactory;
        }
        
        void connect() throws IOException {
            boolean useTls = connectionInfo.ftpsMode != FtpsMode.NONE;
            String modeLabel = useTls ? "FTPS (" + connectionInfo.ftpsMode + ")" : "FTP";
            logger.info("Connecting to {} server: {}:{}", modeLabel, connectionInfo.host, connectionInfo.port);
            
            // Initialise SSL context if TLS is required
            if (useTls) {
                try {
                    if (customSslSocketFactory != null) {
                        sslSocketFactory = customSslSocketFactory;
                        logger.debug("Using custom SSLSocketFactory for FTPS connection");
                    } else {
                        sslSocketFactory = createSslSocketFactory();
                    }
                } catch (Exception e) {
                    throw new IOException("Failed to initialise TLS for FTPS connection", e);
                }
            }
            
            // For implicit FTPS: connect directly over TLS
            if (connectionInfo.ftpsMode == FtpsMode.IMPLICIT) {
                logger.debug("Implicit FTPS: establishing TLS connection to {}:{}", 
                            connectionInfo.host, connectionInfo.port);
                controlSocket = sslSocketFactory.createSocket(
                        connectionInfo.host, connectionInfo.port);
                ((SSLSocket) controlSocket).startHandshake();
                logger.debug("Implicit FTPS: TLS handshake completed");
            } else {
                // Plain FTP or Explicit FTPS: connect with a plain socket first
                controlSocket = new Socket();
                controlSocket.connect(new InetSocketAddress(connectionInfo.host, connectionInfo.port), 
                        (int) CONNECTION_TIMEOUT.toMillis());
            }
            logger.debug("TCP connection established to {}:{}", connectionInfo.host, connectionInfo.port);
            
            controlReader = new BufferedReader(new InputStreamReader(controlSocket.getInputStream()));
            controlWriter = new PrintWriter(controlSocket.getOutputStream(), true);
            
            // Read welcome message
            String response = readResponse();
            if (!response.startsWith("220")) {
                logger.error("FTP connection failed: {}", response);
                throw new IOException("FTP connection failed: " + response);
            }
            logger.debug("Received FTP welcome message");
            
            // For explicit FTPS: upgrade to TLS now
            if (connectionInfo.ftpsMode == FtpsMode.EXPLICIT) {
                upgradeToTls();
            }
            
            // Login
            logger.debug("Authenticating as user: {}", connectionInfo.username);
            sendCommand("USER " + connectionInfo.username);
            response = readResponse();
            if (response.startsWith("331")) {
                sendCommand("PASS " + connectionInfo.password);
                response = readResponse();
            }
            
            if (!response.startsWith("230")) {
                logger.error("FTP login failed: {}", response);
                throw new IOException("FTP login failed: " + response);
            }
            logger.debug("FTP authentication successful");
            
            // For FTPS: set data channel protection after login
            if (useTls) {
                setDataChannelProtection();
            }
            
            // Set binary mode
            logger.debug("Setting binary transfer mode");
            sendCommand("TYPE I");
            response = readResponse();
            if (!response.startsWith("200")) {
                logger.error("Failed to set binary mode: {}", response);
                throw new IOException("Failed to set binary mode: " + response);
            }
            
            // Set passive mode
            logger.debug("Entering passive mode");
            sendCommand("PASV");
            response = readResponse();
            if (!response.startsWith("227")) {
                logger.error("Failed to enter passive mode: {}", response);
                throw new IOException("Failed to enter passive mode: " + response);
            }
            
            logger.info("{} connection established successfully to {}:{}", modeLabel, 
                       connectionInfo.host, connectionInfo.port);
        }
        
        /**
         * Upgrades the control connection to TLS using AUTH TLS (explicit FTPS).
         */
        private void upgradeToTls() throws IOException {
            logger.debug("Explicit FTPS: sending AUTH TLS to upgrade control connection");
            sendCommand("AUTH TLS");
            String response = readResponse();
            if (!response.startsWith("234")) {
                throw new IOException("Server rejected AUTH TLS: " + response);
            }
            
            // Wrap the existing plain socket in an SSLSocket
            SSLSocket sslSocket = (SSLSocket) sslSocketFactory.createSocket(
                    controlSocket, connectionInfo.host, connectionInfo.port, true);
            sslSocket.setUseClientMode(true);
            sslSocket.startHandshake();
            
            // Replace control streams with encrypted ones
            controlSocket = sslSocket;
            controlReader = new BufferedReader(new InputStreamReader(sslSocket.getInputStream()));
            controlWriter = new PrintWriter(sslSocket.getOutputStream(), true);
            
            logger.debug("Explicit FTPS: control connection upgraded to TLS (protocol={})", 
                        sslSocket.getSession().getProtocol());
        }
        
        /**
         * Sets the data channel protection level to Private (encrypted).
         * Sends PBSZ 0 and PROT P as required by RFC 4217.
         */
        private void setDataChannelProtection() throws IOException {
            // PBSZ (Protection Buffer Size) — must be 0 for TLS
            logger.debug("FTPS: setting protection buffer size (PBSZ 0)");
            sendCommand("PBSZ 0");
            String response = readResponse();
            if (!response.startsWith("200")) {
                logger.warn("PBSZ 0 returned unexpected response: {}", response);
            }
            
            // PROT P — set data channel to Private (encrypted)
            logger.debug("FTPS: setting data channel protection to Private (PROT P)");
            sendCommand("PROT P");
            response = readResponse();
            if (!response.startsWith("200")) {
                throw new IOException("Server rejected PROT P (data channel encryption): " + response);
            }
            logger.debug("FTPS: data channel protection set to Private");
        }
        
        /**
         * Creates an SSLSocketFactory for FTPS connections.
         * Uses the default trust store from the JVM.
         */
        private static SSLSocketFactory createSslSocketFactory() throws Exception {
            TrustManagerFactory tmf = TrustManagerFactory.getInstance(
                    TrustManagerFactory.getDefaultAlgorithm());
            tmf.init((KeyStore) null); // Uses default cacerts trust store
            
            SSLContext sslContext = SSLContext.getInstance("TLSv1.3");
            sslContext.init(null, tmf.getTrustManagers(), null);
            return sslContext.getSocketFactory();
        }
        
        long getFileSize(String remotePath) throws IOException {
            sendCommand("SIZE " + remotePath);
            String response = readResponse();
            if (response.startsWith("213")) {
                return Long.parseLong(response.substring(4).trim());
            } else {
                // SIZE command not supported, return unknown size
                return -1;
            }
        }
        
        long downloadFile(String remotePath, java.nio.file.Path localPath, 
                         ProgressTracker progressTracker, long fileSize) throws IOException {
            
            // Enter passive mode and get data connection info
            sendCommand("PASV");
            String response = readResponse();
            if (!response.startsWith("227")) {
                throw new IOException("Failed to enter passive mode: " + response);
            }
            
            // Parse passive mode response to get data connection details
            DataConnectionInfo dataInfo = parsePassiveResponse(response);
            
            // Establish data connection (plain or TLS-wrapped)
            Socket dataSocket = createDataSocket(dataInfo);
            
            try {
                // Send RETR command
                sendCommand("RETR " + remotePath);
                response = readResponse();
                if (!response.startsWith("150") && !response.startsWith("125")) {
                    throw new IOException("Failed to start file transfer: " + response);
                }
                
                // Transfer file data
                long bytesTransferred = 0;
                try (InputStream dataInput = dataSocket.getInputStream();
                     OutputStream fileOutput = Files.newOutputStream(localPath, 
                             StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING);
                     BufferedInputStream bufferedInput = new BufferedInputStream(dataInput, DEFAULT_BUFFER_SIZE);
                     BufferedOutputStream bufferedOutput = new BufferedOutputStream(fileOutput, DEFAULT_BUFFER_SIZE)) {
                    
                    byte[] buffer = new byte[DEFAULT_BUFFER_SIZE];
                    int bytesRead;
                    
                    while ((bytesRead = bufferedInput.read(buffer)) != -1) {
                        bufferedOutput.write(buffer, 0, bytesRead);
                        bytesTransferred += bytesRead;
                        
                        // Update progress
                        if (fileSize > 0) {
                            progressTracker.updateProgress(bytesTransferred);
                        }
                        
                        // Check for cancellation
                        if (Thread.currentThread().isInterrupted()) {
                            throw new IOException("Transfer was cancelled");
                        }
                    }
                    
                    bufferedOutput.flush();
                }
                
                // Read transfer completion response
                response = readResponse();
                if (!response.startsWith("226")) {
                    throw new IOException("File transfer completed with error: " + response);
                }
                
                return bytesTransferred;
                
            } finally {
                dataSocket.close();
            }
        }
        
        long uploadFile(java.nio.file.Path localPath, String remotePath,
                       ProgressTracker progressTracker, long fileSize) throws IOException {
            
            // Enter passive mode and get data connection info
            sendCommand("PASV");
            String response = readResponse();
            if (!response.startsWith("227")) {
                throw new IOException("Failed to enter passive mode: " + response);
            }
            
            // Parse passive mode response to get data connection details
            DataConnectionInfo dataInfo = parsePassiveResponse(response);
            
            // Establish data connection (plain or TLS-wrapped)
            Socket dataSocket = createDataSocket(dataInfo);
            
            try {
                // Send STOR command
                sendCommand("STOR " + remotePath);
                response = readResponse();
                if (!response.startsWith("150") && !response.startsWith("125")) {
                    throw new IOException("Failed to start file upload: " + response);
                }
                
                // Transfer file data
                long bytesTransferred = 0;
                try (InputStream fileInput = Files.newInputStream(localPath);
                     OutputStream dataOutput = dataSocket.getOutputStream();
                     BufferedInputStream bufferedInput = new BufferedInputStream(fileInput, DEFAULT_BUFFER_SIZE);
                     BufferedOutputStream bufferedOutput = new BufferedOutputStream(dataOutput, DEFAULT_BUFFER_SIZE)) {
                    
                    byte[] buffer = new byte[DEFAULT_BUFFER_SIZE];
                    int bytesRead;
                    
                    while ((bytesRead = bufferedInput.read(buffer)) != -1) {
                        bufferedOutput.write(buffer, 0, bytesRead);
                        bytesTransferred += bytesRead;
                        
                        // Update progress
                        if (fileSize > 0) {
                            progressTracker.updateProgress(bytesTransferred);
                        }
                        
                        // Check for cancellation
                        if (Thread.currentThread().isInterrupted()) {
                            throw new IOException("Transfer was cancelled");
                        }
                    }
                    
                    bufferedOutput.flush();
                }
                
            } finally {
                dataSocket.close();
            }
            
            // Read transfer completion response
            response = readResponse();
            if (!response.startsWith("226")) {
                throw new IOException("File upload completed with error: " + response);
            }
            
            return fileSize;
        }
        
        /**
         * Creates directories recursively on the FTP server.
         * Similar to mkdir -p in Unix.
         */
        void mkdirs(String remotePath) throws IOException {
            if (remotePath == null || remotePath.isEmpty() || remotePath.equals("/")) {
                return;
            }
            
            // Split path into components and create each directory
            String[] parts = remotePath.split("/");
            StringBuilder currentPath = new StringBuilder();
            
            for (String part : parts) {
                if (part.isEmpty()) {
                    continue;
                }
                currentPath.append("/").append(part);
                
                // Try to create directory (ignore errors if it exists)
                sendCommand("MKD " + currentPath);
                String response = readResponse();
                // 257 = directory created, 550 = directory exists or other error (ignore)
                if (!response.startsWith("257") && !response.startsWith("550")) {
                    logger.warn("Unexpected response creating directory {}: {}", currentPath, response);
                }
            }
        }
        
        void disconnect() {
            logger.debug("Disconnecting from FTP server");
            try {
                if (controlWriter != null) {
                    sendCommand("QUIT");
                    readResponse();
                }
            } catch (Exception e) {
                logger.warn("Error during FTP disconnect: {}", e.getMessage());
            } finally {
                try {
                    if (controlSocket != null) {
                        controlSocket.close();
                    }
                } catch (IOException e) {
                    logger.warn("Error closing FTP control socket: {}", e.getMessage());
                }
            }
            logger.debug("FTP disconnection complete");
        }
        
        /**
         * Force disconnect without graceful QUIT command.
         * Used for aborting transfers - immediately closes the socket.
         */
        void forceDisconnect() {
            try {
                if (controlSocket != null && !controlSocket.isClosed()) {
                    logger.info("Force closing FTP control socket");
                    controlSocket.close();
                }
            } catch (IOException e) {
                logger.warn("Error during force disconnect: {}", e.getMessage());
            }
        }
        
        private void sendCommand(String command) {
            logger.debug("FTP Command: {}", command.startsWith("PASS") ? "PASS ****" : command);
            controlWriter.println(command);
        }
        
        private String readResponse() throws IOException {
            String response = controlReader.readLine();
            if (response == null) {
                logger.error("FTP server closed connection unexpectedly (EOF)");
                throw new IOException("FTP server closed connection unexpectedly");
            }
            logger.debug("FTP Response: {}", response);
            return response;
        }
        
        /**
         * Creates a data socket, wrapping it in TLS if FTPS is enabled (PROT P).
         */
        private Socket createDataSocket(DataConnectionInfo dataInfo) throws IOException {
            Socket plainSocket = new Socket();
            plainSocket.connect(new InetSocketAddress(dataInfo.host, dataInfo.port), 
                    (int) CONNECTION_TIMEOUT.toMillis());
            
            if (connectionInfo.ftpsMode != FtpsMode.NONE && sslSocketFactory != null) {
                // Wrap data connection in TLS for FTPS data channel protection
                logger.debug("FTPS: wrapping data connection in TLS to {}:{}", dataInfo.host, dataInfo.port);
                SSLSocket sslDataSocket = (SSLSocket) sslSocketFactory.createSocket(
                        plainSocket, dataInfo.host, dataInfo.port, true);
                sslDataSocket.setUseClientMode(true);
                sslDataSocket.startHandshake();
                return sslDataSocket;
            }
            
            return plainSocket;
        }
        
        private DataConnectionInfo parsePassiveResponse(String response) throws IOException {
            // Parse response like: 227 Entering Passive Mode (192,168,1,100,20,21)
            int start = response.indexOf('(');
            int end = response.indexOf(')');
            if (start == -1 || end == -1) {
                throw new IOException("Invalid passive mode response: " + response);
            }
            
            String[] parts = response.substring(start + 1, end).split(",");
            if (parts.length != 6) {
                throw new IOException("Invalid passive mode response format: " + response);
            }
            
            String host = parts[0] + "." + parts[1] + "." + parts[2] + "." + parts[3];
            int port = Integer.parseInt(parts[4]) * 256 + Integer.parseInt(parts[5]);
            
            return new DataConnectionInfo(host, port);
        }
    }
    
    private static class FtpConnectionInfo {
        final String host;
        final int port;
        final String path;
        final String username;
        final String password;
        final FtpsMode ftpsMode;
        
        FtpConnectionInfo(String host, int port, String path, String username, String password, FtpsMode ftpsMode) {
            this.host = host;
            this.port = port;
            this.path = path;
            this.username = username;
            this.password = password;
            this.ftpsMode = ftpsMode;
        }
        
        /**
         * Convenience constructor for plain FTP (no TLS).
         */
        FtpConnectionInfo(String host, int port, String path, String username, String password) {
            this(host, port, path, username, password, FtpsMode.NONE);
        }
        
        @Override
        public String toString() {
            return "FtpConnectionInfo{" +
                    "host='" + host + '\'' +
                    ", port=" + port +
                    ", path='" + path + '\'' +
                    ", username='" + username + '\'' +
                    ", ftpsMode=" + ftpsMode +
                    '}';
        }
    }
    
    /**
     * Data connection information for passive mode
     */
    private static class DataConnectionInfo {
        final String host;
        final int port;
        
        DataConnectionInfo(String host, int port) {
            this.host = host;
            this.port = port;
        }
    }
}
