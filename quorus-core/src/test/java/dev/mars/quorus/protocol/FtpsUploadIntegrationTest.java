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

import org.apache.commons.net.ftp.FTPReply;
import org.apache.commons.net.ftp.FTPSClient;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.io.TempDir;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.cert.X509Certificate;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Integration tests for FTPS (FTP over SSL/TLS) using a real FTPS server
 * running in a Docker container via Testcontainers.
 * <p>
 * Uses {@code stilliard/pure-ftpd} with TLS enabled (explicit FTPS via AUTH TLS).
 * The container auto-generates a self-signed certificate, so tests configure
 * a trust-all SSLSocketFactory.
 * <p>
 * Tests are ordered to validate connectivity first, then progress to transfers.
 * <p>
 * <b>Requires Docker to be running.</b> Tests are automatically skipped if Docker
 * is not available.
 *
 * @author Mark Andrew Ray-Smith Cityline Ltd
 * @version 1.0
 * @since 2026-02-12
 */
@DisplayName("FTPS Upload Integration Tests (Docker)")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class FtpsUploadIntegrationTest {

    private static final Logger logger = Logger.getLogger(FtpsUploadIntegrationTest.class.getName());
    private static final String TEST_USERNAME = "testuser";
    private static final String TEST_PASSWORD = "testpass";

    private FtpTransferProtocol protocol;
    private TransferContext context;
    private String ftpsHost;
    private int ftpsPort;

    @TempDir
    Path tempDir;

    /**
     * Creates an SSLSocketFactory that trusts all certificates.
     * Required for testing against Docker containers with self-signed certs.
     */
    private static SSLSocketFactory createTrustAllSslSocketFactory() throws Exception {
        TrustManager[] trustAllCerts = new TrustManager[]{
            new X509TrustManager() {
                @Override
                public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
                @Override
                public void checkClientTrusted(X509Certificate[] certs, String authType) { }
                @Override
                public void checkServerTrusted(X509Certificate[] certs, String authType) { }
            }
        };
        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(null, trustAllCerts, new java.security.SecureRandom());
        return sslContext.getSocketFactory();
    }

    @BeforeAll
    static void checkDockerAvailable() {
        assumeTrue(SharedTestContainers.isDockerAvailable(),
                "Docker is not available - skipping FTPS integration tests");
        // Start the shared FTPS container (will be reused across tests)
        SharedTestContainers.getFtpsContainer();
    }

    @BeforeEach
    void setUp() throws Exception {
        protocol = new FtpTransferProtocol();
        // Configure trust-all SSL for self-signed container certificate
        protocol.setSslSocketFactory(createTrustAllSslSocketFactory());

        // Get FTPS container connection details from shared container
        ftpsHost = SharedTestContainers.getFtpsHost();
        ftpsPort = SharedTestContainers.getFtpsPort();

        logger.info("FTPS server available at " + ftpsHost + ":" + ftpsPort);

        // Create context for tests
        TransferRequest dummyRequest = TransferRequest.builder()
                .requestId("ftps-integration-test-setup")
                .sourceUri(URI.create("ftps://" + ftpsHost + ":" + ftpsPort + "/test.txt"))
                .destinationPath(tempDir.resolve("test.txt"))
                .build();
        context = new TransferContext(new dev.mars.quorus.core.TransferJob(dummyRequest));
    }

    // ==================== Connectivity Validation ====================

    @Nested
    @DisplayName("FTPS Server Connectivity")
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    class ConnectivityTests {

        @Test
        @Order(1)
        @DisplayName("Verify FTPS server is reachable and accepts AUTH TLS")
        void verifyFtpsServerConnectivity() throws Exception {
            logger.info("=== FTPS Server Connectivity Test ===");
            logger.info("Target: " + ftpsHost + ":" + ftpsPort);

            // Use Apache Commons Net FTPSClient for raw connectivity validation
            FTPSClient ftpsClient = new FTPSClient("TLS", false); // explicit FTPS
            ftpsClient.setTrustManager(new org.apache.commons.net.util.TrustManagerUtils()
                    .getAcceptAllTrustManager());
            ftpsClient.setConnectTimeout(10_000);
            ftpsClient.setDefaultTimeout(10_000);

            try {
                // Connect to the FTP port
                long startConnect = System.currentTimeMillis();
                ftpsClient.connect(ftpsHost, ftpsPort);
                int reply = ftpsClient.getReplyCode();
                long connectTime = System.currentTimeMillis() - startConnect;

                logger.info("  > Connection established in " + connectTime + "ms");
                logger.info("  > Server reply code: " + reply + " (" + ftpsClient.getReplyString().trim() + ")");

                assertTrue(FTPReply.isPositiveCompletion(reply),
                        "FTPS server should return positive completion code, got: " + reply);

                // Execute AUTH TLS to upgrade to TLS
                long startAuth = System.currentTimeMillis();
                int authReply = ftpsClient.execAUTH("TLS");
                long authTime = System.currentTimeMillis() - startAuth;
                assertTrue(FTPReply.isPositiveCompletion(authReply), 
                        "AUTH TLS should succeed, got reply: " + authReply);

                logger.info("  > AUTH TLS completed in " + authTime + "ms");

                // Set PBSZ and PROT P per RFC 4217
                ftpsClient.execPBSZ(0);
                ftpsClient.execPROT("P");

                // Login
                boolean loggedIn = ftpsClient.login(TEST_USERNAME, TEST_PASSWORD);
                assertTrue(loggedIn, "FTPS authentication should succeed");
                logger.info("  > Authenticated as: " + TEST_USERNAME);

                // Verify FTPS control channel is fully functional
                // Data channel (PASV + LIST) is validated in the upload/download tests
                // using our FtpTransferProtocol which handles passive mode without
                // requiring TLS session reuse from the commons-net FTPSClient.
                int sysReply = ftpsClient.syst();
                assertTrue(FTPReply.isPositiveCompletion(sysReply),
                        "SYST command should succeed over encrypted control channel");
                logger.info("  > SYST command succeeded: " + ftpsClient.getReplyString().trim());
                logger.info("[PASS] FTPS Server Connectivity Validated");

            } finally {
                if (ftpsClient.isConnected()) {
                    try {
                        ftpsClient.logout();
                    } catch (Exception ignored) {
                        // Best-effort logout
                    }
                    ftpsClient.disconnect();
                    logger.info("  > Connection closed gracefully");
                }
            }
        }
    }

    // ==================== Upload Tests ====================

    @Nested
    @DisplayName("FTPS Upload Operations")
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    class UploadTests {

        @Test
        @Order(1)
        @DisplayName("Upload small text file via FTPS")
        void uploadSmallFile() throws IOException, TransferException {
            // Create local file to upload
            Path localFile = tempDir.resolve("small-ftps-upload.txt");
            String content = "Hello from FTPS integration test!";
            Files.writeString(localFile, content);

            // Build FTPS upload request
            TransferRequest uploadRequest = TransferRequest.builder()
                    .requestId("integration-ftps-upload-small")
                    .sourceUri(localFile.toUri())
                    .destinationUri(buildFtpsUri("/small-ftps-upload.txt"))
                    .build();

            // Verify direction detection
            assertEquals(TransferDirection.UPLOAD, uploadRequest.getDirection());
            assertTrue(protocol.canHandle(uploadRequest));

            // Execute upload
            TransferResult result = protocol.transfer(uploadRequest, context);

            // Verify result
            assertNotNull(result);
            assertEquals(TransferStatus.COMPLETED, result.getFinalStatus());
            assertEquals(content.length(), result.getBytesTransferred());
            logger.info("FTPS upload completed: " + content.length() + " bytes");
        }

        @Test
        @Order(2)
        @DisplayName("Upload 1KB file via FTPS")
        void upload1KBFile() throws IOException, TransferException {
            // Create 1KB file
            Path localFile = tempDir.resolve("1kb-ftps-upload.txt");
            StringBuilder content = new StringBuilder();
            for (int i = 0; i < 20; i++) {
                content.append("Line ").append(i).append(": FTPS encrypted test content for 1KB upload\n");
            }
            Files.writeString(localFile, content.toString());
            long expectedSize = Files.size(localFile);

            // Build upload request
            TransferRequest uploadRequest = TransferRequest.builder()
                    .requestId("integration-ftps-upload-1kb")
                    .sourceUri(localFile.toUri())
                    .destinationUri(buildFtpsUri("/1kb-ftps-upload.txt"))
                    .build();

            // Execute upload
            TransferResult result = protocol.transfer(uploadRequest, context);

            // Verify result
            assertNotNull(result);
            assertEquals(TransferStatus.COMPLETED, result.getFinalStatus());
            assertEquals(expectedSize, result.getBytesTransferred());
            assertTrue(result.getStartTime().isPresent());
            assertTrue(result.getEndTime().isPresent());
            logger.info("FTPS 1KB upload completed: " + expectedSize + " bytes");
        }

        @Test
        @Order(3)
        @DisplayName("Upload binary file via FTPS")
        void uploadBinaryFile() throws IOException, TransferException {
            // Create binary file
            Path localFile = tempDir.resolve("binary-ftps-upload.bin");
            byte[] binaryContent = new byte[512];
            for (int i = 0; i < binaryContent.length; i++) {
                binaryContent[i] = (byte) (i % 256);
            }
            Files.write(localFile, binaryContent);

            // Build upload request
            TransferRequest uploadRequest = TransferRequest.builder()
                    .requestId("integration-ftps-upload-binary")
                    .sourceUri(localFile.toUri())
                    .destinationUri(buildFtpsUri("/binary-ftps-upload.bin"))
                    .build();

            // Execute upload
            TransferResult result = protocol.transfer(uploadRequest, context);

            // Verify result
            assertNotNull(result);
            assertEquals(TransferStatus.COMPLETED, result.getFinalStatus());
            assertEquals(binaryContent.length, result.getBytesTransferred());
            logger.info("FTPS binary upload completed: " + binaryContent.length + " bytes");
        }

        @Test
        @Order(4)
        @DisplayName("Upload and download roundtrip via FTPS verifying data integrity")
        void uploadDownloadRoundtrip() throws IOException, TransferException {
            // Create local file with unique content
            Path localFile = tempDir.resolve("ftps-roundtrip-source.txt");
            String originalContent = "FTPS Roundtrip test content - encrypted transfer - " + System.currentTimeMillis();
            Files.writeString(localFile, originalContent);

            // Upload via FTPS
            TransferRequest uploadRequest = TransferRequest.builder()
                    .requestId("integration-ftps-roundtrip-upload")
                    .sourceUri(localFile.toUri())
                    .destinationUri(buildFtpsUri("/ftps-roundtrip.txt"))
                    .build();

            TransferResult uploadResult = protocol.transfer(uploadRequest, context);
            assertEquals(TransferStatus.COMPLETED, uploadResult.getFinalStatus());
            logger.info("FTPS roundtrip upload completed: " + uploadResult.getBytesTransferred() + " bytes");

            // Download back via FTPS
            Path downloadedFile = tempDir.resolve("ftps-roundtrip-downloaded.txt");
            TransferRequest downloadRequest = TransferRequest.builder()
                    .requestId("integration-ftps-roundtrip-download")
                    .sourceUri(buildFtpsUri("/ftps-roundtrip.txt"))
                    .destinationPath(downloadedFile)
                    .build();

            TransferResult downloadResult = protocol.transfer(downloadRequest, context);
            assertEquals(TransferStatus.COMPLETED, downloadResult.getFinalStatus());
            logger.info("FTPS roundtrip download completed: " + downloadResult.getBytesTransferred() + " bytes");

            // Verify content matches — this proves end-to-end FTPS data integrity
            String downloadedContent = Files.readString(downloadedFile);
            assertEquals(originalContent, downloadedContent,
                    "Content after FTPS upload→download roundtrip must match original");
            logger.info("[PASS] FTPS roundtrip data integrity verified");
        }

        @Test
        @Order(5)
        @DisplayName("Verify timing information in FTPS transfer results")
        void uploadWithTimingInfo() throws IOException, TransferException {
            // Create file
            Path localFile = tempDir.resolve("ftps-timing-test.txt");
            String content = "Content for FTPS timing test";
            Files.writeString(localFile, content);

            // Build upload request
            TransferRequest uploadRequest = TransferRequest.builder()
                    .requestId("integration-ftps-timing")
                    .sourceUri(localFile.toUri())
                    .destinationUri(buildFtpsUri("/ftps-timing-test.txt"))
                    .build();

            // Execute upload
            TransferResult result = protocol.transfer(uploadRequest, context);

            // Verify timing
            assertNotNull(result);
            assertEquals(TransferStatus.COMPLETED, result.getFinalStatus());
            assertTrue(result.getStartTime().isPresent(), "Start time should be present");
            assertTrue(result.getEndTime().isPresent(), "End time should be present");
            assertTrue(result.getEndTime().get().isAfter(result.getStartTime().get()) ||
                            result.getEndTime().get().equals(result.getStartTime().get()),
                    "End time should be at or after start time");
            logger.info("FTPS timing: start=" + result.getStartTime().get() 
                    + ", end=" + result.getEndTime().get());
        }
    }

    // ==================== Download Tests ====================

    @Nested
    @DisplayName("FTPS Download Operations")
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    class DownloadTests {

        /**
         * Seeds a file on the FTPS server by uploading it via our protocol.
         * This avoids TLS session reuse complications with commons-net FTPSClient
         * on the data channel.
         */
        private void seedFileOnServer(String remotePath, byte[] content) throws Exception {
            // Write content to a temp file
            Path tempFile = tempDir.resolve("seed-" + System.currentTimeMillis() + ".tmp");
            Files.write(tempFile, content);
            
            // Upload via our protocol
            TransferRequest uploadRequest = TransferRequest.builder()
                    .requestId("seed-" + remotePath.replace("/", ""))
                    .sourceUri(tempFile.toUri())
                    .destinationUri(buildFtpsUri(remotePath))
                    .build();
            
            TransferResult result = protocol.transfer(uploadRequest, context);
            assertEquals(TransferStatus.COMPLETED, result.getFinalStatus(),
                    "Failed to seed file on FTPS server: " + remotePath);
        }

        @Test
        @Order(1)
        @DisplayName("Download file from FTPS server")
        void downloadFile() throws Exception {
            // Seed a file on the server
            String content = "FTPS download test content - " + System.currentTimeMillis();
            seedFileOnServer("/ftps-download-test.txt", content.getBytes());

            // Download via our protocol
            Path downloadDest = tempDir.resolve("ftps-downloaded.txt");
            TransferRequest downloadRequest = TransferRequest.builder()
                    .requestId("integration-ftps-download")
                    .sourceUri(buildFtpsUri("/ftps-download-test.txt"))
                    .destinationPath(downloadDest)
                    .build();

            TransferResult result = protocol.transfer(downloadRequest, context);

            assertNotNull(result);
            assertEquals(TransferStatus.COMPLETED, result.getFinalStatus());
            assertTrue(result.getBytesTransferred() > 0);

            // Verify content
            String downloaded = Files.readString(downloadDest);
            assertEquals(content, downloaded);
            logger.info("FTPS download verified: " + result.getBytesTransferred() + " bytes");
        }

        @Test
        @Order(2)
        @DisplayName("Download binary file from FTPS server")
        void downloadBinaryFile() throws Exception {
            // Seed a binary file
            byte[] binaryContent = new byte[1024];
            for (int i = 0; i < binaryContent.length; i++) {
                binaryContent[i] = (byte) (i % 256);
            }
            seedFileOnServer("/ftps-binary-download.bin", binaryContent);

            // Download via our protocol
            Path downloadDest = tempDir.resolve("ftps-binary-downloaded.bin");
            TransferRequest downloadRequest = TransferRequest.builder()
                    .requestId("integration-ftps-download-binary")
                    .sourceUri(buildFtpsUri("/ftps-binary-download.bin"))
                    .destinationPath(downloadDest)
                    .build();

            TransferResult result = protocol.transfer(downloadRequest, context);

            assertNotNull(result);
            assertEquals(TransferStatus.COMPLETED, result.getFinalStatus());
            assertEquals(binaryContent.length, result.getBytesTransferred());

            // Verify binary content matches byte-for-byte
            byte[] downloaded = Files.readAllBytes(downloadDest);
            assertArrayEquals(binaryContent, downloaded,
                    "Binary content must match after FTPS download");
            logger.info("FTPS binary download verified: " + result.getBytesTransferred() + " bytes");
        }
    }

    // ==================== Error Handling ====================

    @Nested
    @DisplayName("FTPS Error Handling")
    class ErrorHandlingTests {

        @Test
        @DisplayName("Attempt to upload non-existent local file via FTPS")
        void uploadNonExistentFile() {
            Path nonExistent = tempDir.resolve("does-not-exist.txt");

            TransferRequest uploadRequest = TransferRequest.builder()
                    .requestId("integration-ftps-upload-missing")
                    .sourceUri(nonExistent.toUri())
                    .destinationUri(buildFtpsUri("/should-not-arrive.txt"))
                    .build();

            assertThrows(TransferException.class, () ->
                    protocol.transfer(uploadRequest, context));
            logger.info("FTPS correctly rejected upload of non-existent file");
        }
    }

    /**
     * Builds an FTPS URI for the test server.
     * Uses ftps:// scheme to trigger explicit FTPS (AUTH TLS).
     */
    private URI buildFtpsUri(String path) {
        return URI.create("ftps://" + TEST_USERNAME + ":" + TEST_PASSWORD + "@" + ftpsHost + ":" + ftpsPort + path);
    }
}
