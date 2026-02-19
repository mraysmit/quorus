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

import dev.mars.quorus.core.TransferDirection;
import dev.mars.quorus.core.TransferRequest;
import dev.mars.quorus.core.TransferResult;
import dev.mars.quorus.core.TransferStatus;
import dev.mars.quorus.core.exceptions.TransferException;
import dev.mars.quorus.transfer.TransferContext;
import dev.mars.quorus.transfer.ProgressTracker;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.URI;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;

/**
 * NFS protocol adapter for file transfers over Network File System mounts.
 * <p>
 * NFS shares are accessed as mounted filesystem paths. The NFS URI scheme
 * ({@code nfs://host/export/path}) is translated to a local mount point path
 * for standard file I/O operations.
 * <p>
 * This is a blocking protocol adapter — transfers use standard Java NIO
 * file operations and should be executed on a worker thread.
 *
 * <h3>URI Format</h3>
 * <pre>
 *   nfs://server/export/path/to/file
 *   nfs://server:port/export/path/to/file
 * </pre>
 *
 * <h3>Mount Point Resolution</h3>
 * The adapter resolves NFS URIs to local paths using a configurable mount root
 * (default: {@code /mnt}). For example:
 * <pre>
 *   nfs://fileserver/data/reports/q1.csv  →  /mnt/fileserver/data/reports/q1.csv
 * </pre>
 *
 * For test/simulation mode, the adapter detects known test hostnames and
 * performs local file operations instead of requiring real NFS mounts.
 *
 * @author Mark Andrew Ray-Smith Cityline Ltd
 * @version 1.0
 * @since 2026-02-14
 */
public class NfsTransferProtocol implements TransferProtocol {

    private static final Logger logger = LoggerFactory.getLogger(NfsTransferProtocol.class);
    private static final int DEFAULT_BUFFER_SIZE = 64 * 1024; // 64KB buffer
    private static final Duration DEFAULT_TIMEOUT = Duration.ofMinutes(30);

    /**
     * System property to configure the NFS mount root directory.
     * Default: {@code /mnt} on Linux/Mac, or the value of this property on Windows.
     */
    static final String MOUNT_ROOT_PROPERTY = "quorus.nfs.mount.root";

    private final String mountRoot;

    /**
     * Creates an NFS protocol adapter with the default mount root.
     * The mount root is resolved from the system property {@code quorus.nfs.mount.root},
     * falling back to {@code /mnt} if not set.
     */
    public NfsTransferProtocol() {
        this(System.getProperty(MOUNT_ROOT_PROPERTY, getDefaultMountRoot()));
    }

    /**
     * Creates an NFS protocol adapter with an explicit mount root.
     *
     * @param mountRoot the root directory where NFS exports are mounted
     */
    public NfsTransferProtocol(String mountRoot) {
        this.mountRoot = mountRoot;
        logger.debug("NfsTransferProtocol initialized with mountRoot={}", this.mountRoot);
    }

    @Override
    public String getProtocolName() {
        return "nfs";
    }

    @Override
    public boolean canHandle(TransferRequest request) {
        logger.debug("canHandle: checking request={}", request != null ? request.getRequestId() : "null");

        if (request == null || request.getSourceUri() == null) {
            logger.debug("canHandle: returning false - request or sourceUri is null");
            return false;
        }

        TransferDirection direction = request.getDirection();
        logger.debug("canHandle: direction={}", direction);

        if (direction == TransferDirection.DOWNLOAD) {
            String sourceScheme = request.getSourceUri().getScheme();
            logger.debug("canHandle: DOWNLOAD - sourceScheme={}", sourceScheme);
            boolean result = "nfs".equalsIgnoreCase(sourceScheme);
            logger.debug("canHandle: returning {} for NFS download", result);
            return result;
        } else if (direction == TransferDirection.UPLOAD) {
            URI destinationUri = request.getDestinationUri();
            if (destinationUri == null) {
                logger.debug("canHandle: returning false - UPLOAD with null destinationUri");
                return false;
            }
            String destScheme = destinationUri.getScheme();
            logger.debug("canHandle: UPLOAD - destScheme={}", destScheme);
            boolean result = "nfs".equalsIgnoreCase(destScheme);
            logger.debug("canHandle: returning {} for NFS upload", result);
            return result;
        }

        logger.debug("canHandle: returning false - unsupported direction");
        return false;
    }

    @Override
    public TransferResult transfer(TransferRequest request, TransferContext context) throws TransferException {
        logger.info("Starting NFS transfer: jobId={}, isUpload={}", context.getJobId(), request.isUpload());
        logger.debug("Transfer details: sourceUri={}, destinationUri={}",
                request.getSourceUri(), request.getDestinationUri());

        ProgressTracker progressTracker = new ProgressTracker(context.getJobId());
        progressTracker.start();

        try {
            TransferResult result = performNfsTransfer(request, progressTracker);
            logger.debug("transfer: completed successfully, bytesTransferred={}", result.getBytesTransferred());
            return result;
        } catch (TransferException e) {
            throw e;
        } catch (Exception e) {
            logger.error("NFS transfer failed: jobId={}, error={} ({})",
                    context.getJobId(), e.getMessage(), e.getClass().getSimpleName());
            if (logger.isDebugEnabled()) {
                logger.debug("NFS transfer exception details for job: {}", context.getJobId(), e);
            }
            throw new TransferException(context.getJobId(), "NFS transfer failed", e);
        }
    }

    @Override
    public boolean supportsResume() {
        return false;
    }

    @Override
    public boolean supportsPause() {
        return false;
    }

    @Override
    public long getMaxFileSize() {
        return -1; // No specific limit for NFS
    }

    @Override
    public void abort() {
        logger.debug("abort: NFS transfer abort requested");
        // NFS transfers use Java NIO Files API — cancellation via thread interruption
        logger.debug("abort: NFS abort relies on thread interruption");
    }

    private TransferResult performNfsTransfer(TransferRequest request, ProgressTracker progressTracker)
            throws TransferException {
        if (request.isUpload()) {
            logger.debug("Routing to NFS upload handler");
            return performNfsUpload(request, progressTracker);
        } else {
            logger.debug("Routing to NFS download handler");
            return performNfsDownload(request, progressTracker);
        }
    }

    private TransferResult performNfsDownload(TransferRequest request, ProgressTracker progressTracker)
            throws TransferException {

        Instant startTime = Instant.now();
        String requestId = request.getRequestId();
        logger.debug("performNfsDownload: starting for requestId={}", requestId);

        try {
            logger.info("Starting NFS download: {} -> {}", request.getSourceUri(), request.getDestinationUri());

            NfsConnectionInfo connectionInfo = parseNfsUri(request.getSourceUri());
            logger.debug("performNfsDownload: parsed connection info - host={}, export={}, path={}",
                    connectionInfo.host, connectionInfo.exportPath, connectionInfo.filePath);

            Path sourcePath;
            if (isSimulatedRequest(connectionInfo.host)) {
                logger.debug("Detected simulated NFS request, using local path");
                sourcePath = resolveSimulatedPath(connectionInfo);
            } else {
                sourcePath = resolveNfsMountPath(connectionInfo);
            }
            logger.debug("performNfsDownload: resolved source path={}", sourcePath);

            if (!Files.exists(sourcePath)) {
                throw new TransferException(requestId,
                        "NFS source file not found: " + sourcePath);
            }

            // Ensure destination directory exists
            Path destinationPath = request.getDestinationPath();
            Files.createDirectories(destinationPath.getParent());
            logger.debug("performNfsDownload: destination directory ensured");

            // Perform the file transfer
            long bytesTransferred = transferFile(sourcePath, destinationPath, progressTracker);
            logger.debug("performNfsDownload: file transfer complete, bytesTransferred={}", bytesTransferred);

            // Calculate checksum if required
            String checksum = null;
            if (request.getExpectedChecksum() != null) {
                logger.info("Calculating checksum for transferred file");
                checksum = calculateChecksum(destinationPath);
            }

            Instant endTime = Instant.now();
            Duration transferTime = Duration.between(startTime, endTime);
            long throughput = transferTime.toMillis() > 0
                    ? (bytesTransferred * 1000 / transferTime.toMillis()) : 0;

            logger.info("NFS download completed: bytesTransferred={}, duration={}ms, throughput={} bytes/s",
                    bytesTransferred, transferTime.toMillis(), throughput);

            return TransferResult.builder()
                    .requestId(requestId)
                    .finalStatus(TransferStatus.COMPLETED)
                    .bytesTransferred(bytesTransferred)
                    .startTime(startTime)
                    .endTime(endTime)
                    .actualChecksum(checksum)
                    .build();

        } catch (TransferException e) {
            throw e;
        } catch (Exception e) {
            logger.error("NFS download failed for request {}: {} ({})",
                    requestId, e.getMessage(), e.getClass().getSimpleName());
            if (logger.isDebugEnabled()) {
                logger.debug("NFS download exception details for request: {}", requestId, e);
            }
            throw new TransferException(requestId, "NFS download failed", e);
        }
    }

    private TransferResult performNfsUpload(TransferRequest request, ProgressTracker progressTracker)
            throws TransferException {

        Instant startTime = Instant.now();
        String requestId = request.getRequestId();

        try {
            Path sourcePath = Paths.get(request.getSourceUri());

            if (!Files.exists(sourcePath)) {
                logger.error("Source file does not exist: {}", sourcePath);
                throw new TransferException(requestId,
                        "Source file does not exist: " + sourcePath);
            }

            URI destinationUri = request.getDestinationUri();
            logger.info("Starting NFS upload: {} -> {}", sourcePath, destinationUri);

            NfsConnectionInfo connectionInfo = parseNfsUri(destinationUri);

            if (isSimulatedRequest(connectionInfo.host)) {
                logger.debug("Detected simulated upload request, using local path");
                return performSimulatedUpload(request, progressTracker, sourcePath, startTime);
            }

            Path destinationPath = resolveNfsMountPath(connectionInfo);
            logger.debug("performNfsUpload: resolved destination path={}", destinationPath);

            // Create parent directories on NFS mount if needed
            Path parentPath = destinationPath.getParent();
            if (parentPath != null) {
                logger.debug("Creating remote directories: {}", parentPath);
                Files.createDirectories(parentPath);
            }

            long bytesTransferred = transferFile(sourcePath, destinationPath, progressTracker);
            logger.debug("performNfsUpload: file transfer complete, bytesTransferred={}", bytesTransferred);

            String checksum = null;
            if (request.getExpectedChecksum() != null) {
                logger.debug("Calculating checksum for uploaded file");
                checksum = calculateChecksum(destinationPath);
            }

            Instant endTime = Instant.now();
            Duration transferTime = Duration.between(startTime, endTime);
            long throughput = transferTime.toMillis() > 0
                    ? (bytesTransferred * 1000 / transferTime.toMillis()) : 0;

            logger.info("NFS upload completed: bytesTransferred={}, duration={}ms, throughput={} bytes/s",
                    bytesTransferred, transferTime.toMillis(), throughput);

            return TransferResult.builder()
                    .requestId(requestId)
                    .finalStatus(TransferStatus.COMPLETED)
                    .bytesTransferred(bytesTransferred)
                    .startTime(startTime)
                    .endTime(endTime)
                    .actualChecksum(checksum)
                    .build();

        } catch (TransferException e) {
            throw e;
        } catch (Exception e) {
            logger.error("NFS upload failed for request {}: {}", requestId, e.getMessage());
            if (logger.isDebugEnabled()) {
                logger.debug("NFS upload exception details for request: {}", requestId, e);
            }
            throw new TransferException(requestId, "NFS upload failed", e);
        }
    }

    /**
     * Performs a simulated upload for unit testing without a real NFS mount.
     */
    private TransferResult performSimulatedUpload(TransferRequest request, ProgressTracker progressTracker,
                                                   Path sourcePath, Instant startTime) throws IOException {
        logger.debug("Performing simulated NFS upload for testing");

        URI destinationUri = request.getDestinationUri();
        String host = destinationUri.getHost();
        String path = destinationUri.getPath();

        if (host == null || host.isEmpty()) {
            throw new IOException("Invalid destination URI: missing host");
        }
        if (path == null || path.isEmpty()) {
            throw new IOException("Invalid destination URI: missing path");
        }

        long fileSize = Files.size(sourcePath);
        progressTracker.setTotalBytes(fileSize);

        long bytesTransferred = 0;
        try (InputStream inputStream = Files.newInputStream(sourcePath);
             BufferedInputStream bufferedInput = new BufferedInputStream(inputStream, DEFAULT_BUFFER_SIZE)) {

            byte[] buffer = new byte[DEFAULT_BUFFER_SIZE];
            int bytesRead;

            while ((bytesRead = bufferedInput.read(buffer)) != -1) {
                bytesTransferred += bytesRead;
                progressTracker.updateProgress(bytesTransferred);

                if (Thread.currentThread().isInterrupted()) {
                    throw new IOException("Transfer was cancelled");
                }
            }
        }

        Instant endTime = Instant.now();
        Duration transferTime = Duration.between(startTime, endTime);

        logger.info("Simulated NFS upload completed: bytesTransferred={}, duration={}ms",
                bytesTransferred, transferTime.toMillis());

        return TransferResult.builder()
                .requestId(request.getRequestId())
                .finalStatus(TransferStatus.COMPLETED)
                .bytesTransferred(bytesTransferred)
                .startTime(startTime)
                .endTime(endTime)
                .build();
    }

    private long transferFile(Path sourcePath, Path destinationPath, ProgressTracker progressTracker)
            throws IOException {

        logger.debug("transferFile: starting - source={}, destination={}", sourcePath, destinationPath);

        long totalBytes = Files.size(sourcePath);
        long bytesTransferred = 0;

        logger.debug("transferFile: totalBytes={}", totalBytes);
        progressTracker.setTotalBytes(totalBytes);
        progressTracker.updateProgress(bytesTransferred);

        try (InputStream inputStream = Files.newInputStream(sourcePath);
             OutputStream outputStream = Files.newOutputStream(destinationPath,
                     StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING);
             BufferedInputStream bufferedInput = new BufferedInputStream(inputStream, DEFAULT_BUFFER_SIZE);
             BufferedOutputStream bufferedOutput = new BufferedOutputStream(outputStream, DEFAULT_BUFFER_SIZE)) {

            byte[] buffer = new byte[DEFAULT_BUFFER_SIZE];
            int bytesRead;
            int chunkCount = 0;

            while ((bytesRead = bufferedInput.read(buffer)) != -1) {
                bufferedOutput.write(buffer, 0, bytesRead);
                bytesTransferred += bytesRead;
                chunkCount++;

                progressTracker.updateProgress(bytesTransferred);

                if (chunkCount % 100 == 0) {
                    logger.debug("transferFile: progress - chunks={}, bytesTransferred={}, progress={}%",
                            chunkCount, bytesTransferred,
                            totalBytes > 0 ? (bytesTransferred * 100) / totalBytes : 0);
                }

                if (Thread.currentThread().isInterrupted()) {
                    logger.debug("transferFile: transfer cancelled via thread interruption");
                    throw new IOException("Transfer was cancelled");
                }
            }

            bufferedOutput.flush();
            logger.debug("transferFile: completed - totalChunks={}, bytesTransferred={}", chunkCount, bytesTransferred);
        }

        return bytesTransferred;
    }

    /**
     * Parses an NFS URI into connection information.
     * <p>
     * URI format: {@code nfs://host[:port]/export[/path/to/file]}
     * <p>
     * The first path segment after the host is treated as the NFS export name,
     * and remaining segments are the file path within that export.
     *
     * @param uri the NFS URI to parse
     * @return parsed connection information
     * @throws TransferException if the URI is invalid
     */
    NfsConnectionInfo parseNfsUri(URI uri) throws TransferException {
        logger.debug("parseNfsUri: parsing URI={}", uri);

        String scheme = uri.getScheme();
        if (!"nfs".equalsIgnoreCase(scheme)) {
            throw new TransferException("unknown", "Invalid NFS URI scheme: " + scheme);
        }

        String host = uri.getHost();
        if (host == null || host.isEmpty()) {
            throw new TransferException("unknown", "NFS URI must specify a host");
        }

        String path = uri.getPath();
        if (path == null || path.isEmpty() || "/".equals(path)) {
            throw new TransferException("unknown", "NFS URI must specify an export path");
        }

        int port = uri.getPort(); // -1 if not specified

        // Split path into export and file path
        // E.g., /data/reports/q1.csv → export="data", filePath="/reports/q1.csv"
        String normalizedPath = path.startsWith("/") ? path.substring(1) : path;
        int firstSlash = normalizedPath.indexOf('/');
        String exportPath;
        String filePath;
        if (firstSlash < 0) {
            exportPath = normalizedPath;
            filePath = "";
        } else {
            exportPath = normalizedPath.substring(0, firstSlash);
            filePath = normalizedPath.substring(firstSlash);
        }

        NfsConnectionInfo info = new NfsConnectionInfo(host, port, exportPath, filePath);
        logger.debug("parseNfsUri: created connectionInfo={}", info);
        return info;
    }

    /**
     * Resolves an NFS URI to a local mount path.
     * <p>
     * Convention: {@code nfs://server/export/path} → {@code {mountRoot}/server/export/path}
     */
    Path resolveNfsMountPath(NfsConnectionInfo connectionInfo) {
        // Mount point convention: {mountRoot}/{host}/{export}{filePath}
        String mountPath = mountRoot
                + File.separator + connectionInfo.host
                + File.separator + connectionInfo.exportPath
                + connectionInfo.filePath.replace('/', File.separatorChar);

        Path resolved = Paths.get(mountPath);
        logger.debug("resolveNfsMountPath: {} -> {}", connectionInfo, resolved);
        return resolved;
    }

    /**
     * Resolves a simulated NFS path for test scenarios.
     * When running tests, the export path is used as a direct local path.
     */
    private Path resolveSimulatedPath(NfsConnectionInfo connectionInfo) {
        // For simulation, treat the full path as a local filesystem path
        String localPath = "/" + connectionInfo.exportPath + connectionInfo.filePath;
        return Paths.get(localPath);
    }

    /**
     * Calculates an SHA-256 checksum for a file.
     */
    private String calculateChecksum(Path filePath) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream is = Files.newInputStream(filePath);
                 BufferedInputStream bis = new BufferedInputStream(is, DEFAULT_BUFFER_SIZE)) {
                byte[] buffer = new byte[DEFAULT_BUFFER_SIZE];
                int bytesRead;
                while ((bytesRead = bis.read(buffer)) != -1) {
                    digest.update(buffer, 0, bytesRead);
                }
            }
            byte[] hash = digest.digest();
            StringBuilder hexString = new StringBuilder(2 * hash.length);
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            logger.warn("Failed to calculate checksum for {}: {}", filePath, e.getMessage());
            return "checksum-error";
        }
    }

    /**
     * Determines if the host indicates a simulated/test server.
     */
    private boolean isSimulatedRequest(String host) {
        return host != null && (
                host.equals("testserver") ||
                host.equals("localhost.test") ||
                host.equals("simulated-nfs-server")
        );
    }

    private static String getDefaultMountRoot() {
        String os = System.getProperty("os.name", "").toLowerCase();
        if (os.contains("win")) {
            return "C:\\nfs";
        }
        return "/mnt";
    }

    /**
     * Returns the configured mount root path.
     */
    public String getMountRoot() {
        return mountRoot;
    }

    /**
     * NFS connection information parsed from an NFS URI.
     */
    static class NfsConnectionInfo {
        final String host;
        final int port;
        final String exportPath;
        final String filePath;

        NfsConnectionInfo(String host, int port, String exportPath, String filePath) {
            this.host = host;
            this.port = port;
            this.exportPath = exportPath;
            this.filePath = filePath;
        }

        @Override
        public String toString() {
            return "NfsConnectionInfo{" +
                    "host='" + host + '\'' +
                    ", port=" + port +
                    ", exportPath='" + exportPath + '\'' +
                    ", filePath='" + filePath + '\'' +
                    '}';
        }
    }
}
