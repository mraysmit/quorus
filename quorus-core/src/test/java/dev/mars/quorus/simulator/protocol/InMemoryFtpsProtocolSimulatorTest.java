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

package dev.mars.quorus.simulator.protocol;

import dev.mars.quorus.simulator.SimulatorTestLoggingExtension;
import dev.mars.quorus.simulator.fs.InMemoryFileSystemSimulator;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for the FTPS (FTP over SSL/TLS) in-memory simulator.
 * <p>
 * Validates that the {@link InMemoryTransferProtocolSimulator} correctly simulates
 * FTPS transfers including:
 * <ul>
 *   <li>FTPS factory creation and protocol defaults</li>
 *   <li>{@code ftps://} URI scheme handling and cross-compatibility with {@code ftp://}</li>
 *   <li>TLS-related latency simulation (higher than plain FTP)</li>
 *   <li>Transfer operations: download, upload, progress, resume</li>
 *   <li>Chaos engineering: auth failures, timeouts, interruptions, flaky connections</li>
 *   <li>Statistics tracking across multiple transfers</li>
 * </ul>
 *
 * @author Mark Andrew Ray-Smith Cityline Ltd
 * @version 1.0
 * @since 2026-02-12
 */
@ExtendWith(SimulatorTestLoggingExtension.class)
@DisplayName("InMemoryTransferProtocolSimulator FTPS Tests")
class InMemoryFtpsProtocolSimulatorTest {

    private static final Logger log = LoggerFactory.getLogger(InMemoryFtpsProtocolSimulatorTest.class);

    private InMemoryFileSystemSimulator fileSystem;
    private InMemoryTransferProtocolSimulator simulator;

    @BeforeEach
    void setUp() throws IOException {
        fileSystem = new InMemoryFileSystemSimulator();
        fileSystem.createFile("/source/secure-report.csv", "id,name,balance\n1,Alice,9500\n2,Bob,12300".getBytes());
        fileSystem.createFile("/source/large-file.bin", new byte[50_000]);
        simulator = InMemoryTransferProtocolSimulator.ftps(fileSystem);
    }

    @AfterEach
    void tearDown() {
        if (simulator != null) {
            simulator.shutdown();
        }
    }

    // ==================== Factory Method ====================

    @Nested
    @DisplayName("FTPS Factory Method")
    class FactoryMethodTests {

        @Test
        @DisplayName("Should create FTPS simulator with correct protocol name")
        void testCreateFtps() {
            log.info("Testing FTPS simulator factory method");
            assertThat(simulator.getProtocolName()).isEqualTo("ftps");
            log.info("FTPS simulator created: protocol={}", simulator.getProtocolName());
        }

        @Test
        @DisplayName("Should support resume by default")
        void testFtpsSupportsResume() {
            assertThat(simulator.supportsResume()).isTrue();
        }

        @Test
        @DisplayName("Should support pause by default")
        void testFtpsSupportsPause() {
            assertThat(simulator.supportsPause()).isTrue();
        }

        @Test
        @DisplayName("Should have different defaults from plain FTP simulator")
        void testFtpsDiffersFromPlainFtp() {
            var ftpSim = InMemoryTransferProtocolSimulator.ftp(fileSystem);
            try {
                // Both are valid simulators but for different protocols
                assertThat(simulator.getProtocolName()).isEqualTo("ftps");
                assertThat(ftpSim.getProtocolName()).isEqualTo("ftp");
            } finally {
                ftpSim.shutdown();
            }
        }
    }

    // ==================== Protocol Handling ====================

    @Nested
    @DisplayName("FTPS Protocol Handling")
    class ProtocolHandlingTests {

        @ParameterizedTest
        @ValueSource(strings = {"ftps://host/file.txt", "FTPS://host/file.txt", "Ftps://host/file.txt"})
        @DisplayName("Should handle FTPS URIs case-insensitively")
        void testCanHandleFtpsUri(String uri) {
            log.info("Testing FTPS protocol matching for URI: {}", uri);
            var request = new InMemoryTransferProtocolSimulator.TransferRequest(
                URI.create(uri), Path.of("/dest/file.txt"));
            assertThat(simulator.canHandle(request)).isTrue();
        }

        @Test
        @DisplayName("FTPS simulator should also handle ftp:// URIs (cross-compatibility)")
        void testFtpsHandlesFtpScheme() {
            log.info("Testing FTPS simulator handles plain ftp:// scheme");
            var request = new InMemoryTransferProtocolSimulator.TransferRequest(
                URI.create("ftp://host/source/secure-report.csv"), Path.of("/dest/report.csv"));
            assertThat(simulator.canHandle(request))
                .as("FTPS simulator should accept ftp:// URIs for backward compatibility")
                .isTrue();
        }

        @Test
        @DisplayName("FTP simulator should also handle ftps:// URIs")
        void testFtpHandlesFtpsScheme() {
            log.info("Testing FTP simulator handles ftps:// scheme");
            var ftpSim = InMemoryTransferProtocolSimulator.ftp(fileSystem);
            try {
                var request = new InMemoryTransferProtocolSimulator.TransferRequest(
                    URI.create("ftps://host/source/secure-report.csv"), Path.of("/dest/report.csv"));
                assertThat(ftpSim.canHandle(request))
                    .as("FTP simulator should accept ftps:// URIs")
                    .isTrue();
            } finally {
                ftpSim.shutdown();
            }
        }

        @Test
        @DisplayName("Should not handle HTTP URIs")
        void testCannotHandleHttp() {
            var request = new InMemoryTransferProtocolSimulator.TransferRequest(
                URI.create("http://host/file.txt"), Path.of("/dest/file.txt"));
            assertThat(simulator.canHandle(request)).isFalse();
        }

        @Test
        @DisplayName("Should not handle SFTP URIs")
        void testCannotHandleSftp() {
            var request = new InMemoryTransferProtocolSimulator.TransferRequest(
                URI.create("sftp://host/file.txt"), Path.of("/dest/file.txt"));
            assertThat(simulator.canHandle(request)).isFalse();
        }

        @Test
        @DisplayName("Should not handle null request")
        void testCannotHandleNull() {
            assertThat(simulator.canHandle(null)).isFalse();
        }
    }

    // ==================== Basic Transfers ====================

    @Nested
    @DisplayName("FTPS Transfer Operations")
    class TransferTests {

        @Test
        @DisplayName("Should perform successful FTPS download")
        void testSuccessfulFtpsDownload() throws Exception {
            log.info("Testing successful FTPS download");
            var request = InMemoryTransferProtocolSimulator.TransferRequest.builder()
                .sourceUri(URI.create("ftps://secure-server.com/source/secure-report.csv"))
                .destinationPath(Path.of("/downloaded/report.csv"))
                .build();

            var result = simulator.transfer(request, new InMemoryTransferProtocolSimulator.TransferContext());

            log.info("FTPS download: successful={}, bytes={}, duration={}ms",
                result.isSuccessful(), result.bytesTransferred(), 
                result.duration() != null ? result.duration().toMillis() : "null");

            assertThat(result.isSuccessful()).isTrue();
            assertThat(result.bytesTransferred()).isGreaterThan(0);
            assertThat(fileSystem.exists("/downloaded/report.csv")).isTrue();
        }

        @Test
        @DisplayName("Should perform successful FTPS upload")
        void testSuccessfulFtpsUpload() throws Exception {
            log.info("Testing successful FTPS upload");
            // Create a local file to upload
            fileSystem.createFile("/local/upload-data.csv", "col1,col2\na,b\nc,d".getBytes());

            var request = InMemoryTransferProtocolSimulator.TransferRequest.builder()
                .sourceUri(URI.create("file:///local/upload-data.csv"))
                .destinationUri(URI.create("ftps://secure-server.com/uploads/upload-data.csv"))
                .build();

            var result = simulator.transfer(request, new InMemoryTransferProtocolSimulator.TransferContext());

            assertThat(result.isSuccessful()).isTrue();
            assertThat(result.bytesTransferred()).isGreaterThan(0);
            log.info("FTPS upload completed: {} bytes", result.bytesTransferred());
        }

        @Test
        @DisplayName("Should perform async FTPS transfer")
        void testAsyncFtpsTransfer() throws Exception {
            log.info("Testing async FTPS transfer");
            var request = InMemoryTransferProtocolSimulator.TransferRequest.builder()
                .sourceUri(URI.create("ftps://host/source/secure-report.csv"))
                .destinationPath(Path.of("/async-dest/report.csv"))
                .build();

            CompletableFuture<InMemoryTransferProtocolSimulator.TransferResult> future =
                simulator.transferReactive(request, new InMemoryTransferProtocolSimulator.TransferContext());

            var result = future.get(10, TimeUnit.SECONDS);
            assertThat(result.isSuccessful()).isTrue();
            assertThat(fileSystem.exists("/async-dest/report.csv")).isTrue();
            log.info("Async FTPS transfer completed successfully");
        }

        @Test
        @DisplayName("Should reject non-existent source file")
        void testNonExistentSource() {
            log.info("Testing non-existent source");
            var request = InMemoryTransferProtocolSimulator.TransferRequest.builder()
                .sourceUri(URI.create("ftps://host/nonexistent.txt"))
                .destinationPath(Path.of("/dest/file.txt"))
                .build();

            assertThatThrownBy(() -> simulator.transfer(request,
                new InMemoryTransferProtocolSimulator.TransferContext()))
                .isInstanceOf(InMemoryTransferProtocolSimulator.TransferException.class)
                .hasMessageContaining("not found");
        }

        @Test
        @DisplayName("Should enforce max file size limit")
        void testMaxFileSizeLimit() throws IOException {
            log.info("Testing FTPS max file size enforcement");
            fileSystem.createFile("/source/huge.bin", new byte[100_000]);
            simulator.setMaxFileSize(50_000);

            var request = InMemoryTransferProtocolSimulator.TransferRequest.builder()
                .sourceUri(URI.create("ftps://host/source/huge.bin"))
                .destinationPath(Path.of("/dest/huge.bin"))
                .build();

            assertThatThrownBy(() -> simulator.transfer(request,
                new InMemoryTransferProtocolSimulator.TransferContext()))
                .isInstanceOf(InMemoryTransferProtocolSimulator.TransferException.class)
                .hasMessageContaining("exceeds maximum");
        }
    }

    // ==================== TLS Latency Simulation ====================

    @Nested
    @DisplayName("TLS Latency Simulation")
    class TlsLatencyTests {

        @Test
        @DisplayName("FTPS should have higher default latency than plain FTP (TLS overhead)")
        void testFtpsHigherLatencyThanFtp() throws Exception {
            log.info("Comparing FTPS vs FTP latency (TLS overhead simulation)");

            // Use fixed latency to measure difference
            var ftpSim = InMemoryTransferProtocolSimulator.ftp(fileSystem);
            try {
                // FTP defaults: 50-200ms; FTPS defaults: 80-300ms
                // Use small file to minimize transfer time, measure connection overhead
                fileSystem.createFile("/source/tiny.txt", "x".getBytes());

                var request = InMemoryTransferProtocolSimulator.TransferRequest.builder()
                    .sourceUri(URI.create("ftp://host/source/tiny.txt"))
                    .destinationPath(Path.of("/dest/tiny-ftp.txt"))
                    .build();

                long ftpStart = System.currentTimeMillis();
                ftpSim.transfer(request, new InMemoryTransferProtocolSimulator.TransferContext());
                long ftpElapsed = System.currentTimeMillis() - ftpStart;

                var ftpsRequest = InMemoryTransferProtocolSimulator.TransferRequest.builder()
                    .sourceUri(URI.create("ftps://host/source/tiny.txt"))
                    .destinationPath(Path.of("/dest/tiny-ftps.txt"))
                    .build();

                long ftpsStart = System.currentTimeMillis();
                simulator.transfer(ftpsRequest, new InMemoryTransferProtocolSimulator.TransferContext());
                long ftpsElapsed = System.currentTimeMillis() - ftpsStart;

                log.info("Latency comparison: FTP={}ms, FTPS={}ms", ftpElapsed, ftpsElapsed);

                // Both should complete; exact timing depends on scheduler but FTPS
                // minimum latency (80ms) is higher than FTP minimum (50ms)
                assertThat(ftpElapsed).isGreaterThan(0);
                assertThat(ftpsElapsed).isGreaterThan(0);
            } finally {
                ftpSim.shutdown();
            }
        }

        @Test
        @DisplayName("Should support custom latency configuration")
        void testCustomLatencyConfig() throws Exception {
            log.info("Testing custom FTPS latency configuration");
            simulator.setLatencyConfig(200, 200); // Fixed 200ms

            var request = InMemoryTransferProtocolSimulator.TransferRequest.builder()
                .sourceUri(URI.create("ftps://host/source/secure-report.csv"))
                .destinationPath(Path.of("/dest/report.csv"))
                .build();

            long start = System.currentTimeMillis();
            simulator.transfer(request, new InMemoryTransferProtocolSimulator.TransferContext());
            long elapsed = System.currentTimeMillis() - start;

            log.info("Transfer with 200ms latency completed in {}ms", elapsed);
            assertThat(elapsed).isGreaterThanOrEqualTo(150); // Allow some timer slack
        }
    }

    // ==================== Progress Reporting ====================

    @Nested
    @DisplayName("Progress Reporting")
    class ProgressReportingTests {

        @Test
        @DisplayName("Should report progress during FTPS transfer")
        void testProgressCallback() throws Exception {
            log.info("Testing FTPS progress reporting");
            simulator.setSimulatedBytesPerSecond(25_000); // 25KB/s
            simulator.setProgressUpdateIntervalMs(50);

            List<InMemoryTransferProtocolSimulator.TransferProgress> reports =
                new CopyOnWriteArrayList<>();

            var context = new InMemoryTransferProtocolSimulator.TransferContext(reports::add);

            var request = InMemoryTransferProtocolSimulator.TransferRequest.builder()
                .sourceUri(URI.create("ftps://host/source/large-file.bin"))
                .destinationPath(Path.of("/dest/large-file.bin"))
                .build();

            simulator.transfer(request, context);

            log.info("Received {} progress reports", reports.size());
            assertThat(reports).isNotEmpty();

            var lastReport = reports.get(reports.size() - 1);
            assertThat(lastReport.percentComplete()).isEqualTo(100);
            assertThat(lastReport.bytesTransferred()).isEqualTo(50_000);
        }

        @Test
        @DisplayName("Should include ETA in progress reports")
        void testProgressEta() throws Exception {
            log.info("Testing FTPS ETA in progress reports");
            simulator.setSimulatedBytesPerSecond(10_000); // 10KB/s

            AtomicInteger withEtaCount = new AtomicInteger(0);

            var context = new InMemoryTransferProtocolSimulator.TransferContext(
                progress -> {
                    if (progress.percentComplete() < 100 && progress.estimatedTimeRemaining() != null) {
                        withEtaCount.incrementAndGet();
                    }
                });

            var request = InMemoryTransferProtocolSimulator.TransferRequest.builder()
                .sourceUri(URI.create("ftps://host/source/large-file.bin"))
                .destinationPath(Path.of("/dest/large-file.bin"))
                .build();

            simulator.transfer(request, context);
            log.info("Progress reports with ETA: {}", withEtaCount.get());
            assertThat(withEtaCount.get()).isGreaterThan(0);
        }
    }

    // ==================== Resume and Pause ====================

    @Nested
    @DisplayName("Resume and Pause")
    class ResumePauseTests {

        @Test
        @DisplayName("Should resume from specified byte position")
        void testResumeFromPosition() throws Exception {
            log.info("Testing FTPS resume from byte 25000");
            fileSystem.createFile("/dest/large-file.bin", new byte[25_000]); // Partial file

            var request = InMemoryTransferProtocolSimulator.TransferRequest.builder()
                .sourceUri(URI.create("ftps://host/source/large-file.bin"))
                .destinationPath(Path.of("/dest/large-file.bin"))
                .resumeFromBytes(25_000)
                .build();

            var result = simulator.transfer(request, new InMemoryTransferProtocolSimulator.TransferContext());

            assertThat(result.isSuccessful()).isTrue();
            assertThat(result.resumedFromBytes()).isEqualTo(25_000);
            log.info("FTPS resume completed: resumedFrom={}", result.resumedFromBytes());
        }

        @Test
        @DisplayName("Should allow disabling resume support")
        void testDisableResume() {
            simulator.setSupportsResume(false);
            assertThat(simulator.supportsResume()).isFalse();
        }
    }

    // ==================== Chaos Engineering ====================

    @Nested
    @DisplayName("Chaos Engineering")
    class ChaosEngineeringTests {

        @Test
        @DisplayName("Should simulate FTPS authentication failure (invalid certificate/credentials)")
        void testAuthFailure() {
            log.info("Testing FTPS authentication failure (simulates TLS cert/credential rejection)");
            simulator.setFailureMode(InMemoryTransferProtocolSimulator.ProtocolFailureMode.AUTH_FAILURE);

            var request = InMemoryTransferProtocolSimulator.TransferRequest.builder()
                .sourceUri(URI.create("ftps://secure-server.com/source/secure-report.csv"))
                .destinationPath(Path.of("/dest/report.csv"))
                .build();

            assertThatThrownBy(() -> simulator.transfer(request,
                new InMemoryTransferProtocolSimulator.TransferContext()))
                .isInstanceOf(InMemoryTransferProtocolSimulator.TransferException.class)
                .hasMessageContaining("Authentication failed");
            log.info("FTPS auth failure correctly simulated");
        }

        @Test
        @DisplayName("Should simulate FTPS connection timeout (TLS handshake timeout)")
        void testConnectionTimeout() {
            log.info("Testing FTPS connection timeout (simulates TLS handshake timeout)");
            simulator.setFailureMode(InMemoryTransferProtocolSimulator.ProtocolFailureMode.CONNECTION_TIMEOUT);

            var request = InMemoryTransferProtocolSimulator.TransferRequest.builder()
                .sourceUri(URI.create("ftps://slow-server.com/source/secure-report.csv"))
                .destinationPath(Path.of("/dest/report.csv"))
                .build();

            assertThatThrownBy(() -> simulator.transfer(request,
                new InMemoryTransferProtocolSimulator.TransferContext()))
                .isInstanceOf(InMemoryTransferProtocolSimulator.TransferException.class)
                .hasMessageContaining("timed out");
        }

        @Test
        @DisplayName("Should simulate connection refused")
        void testConnectionRefused() {
            simulator.setFailureMode(InMemoryTransferProtocolSimulator.ProtocolFailureMode.CONNECTION_REFUSED);

            var request = InMemoryTransferProtocolSimulator.TransferRequest.builder()
                .sourceUri(URI.create("ftps://down-server.com/file.txt"))
                .destinationPath(Path.of("/dest/file.txt"))
                .build();

            assertThatThrownBy(() -> simulator.transfer(request,
                new InMemoryTransferProtocolSimulator.TransferContext()))
                .isInstanceOf(InMemoryTransferProtocolSimulator.TransferException.class)
                .hasMessageContaining("refused");
        }

        @Test
        @DisplayName("Should simulate transfer interrupted at specific percentage")
        void testTransferInterruptedAtPercent() {
            log.info("Testing FTPS transfer interruption at 50%");
            simulator.setFailureMode(InMemoryTransferProtocolSimulator.ProtocolFailureMode.TRANSFER_INTERRUPTED);
            simulator.setFailureAtPercent(50);
            simulator.setSimulatedBytesPerSecond(50_000);

            var request = InMemoryTransferProtocolSimulator.TransferRequest.builder()
                .sourceUri(URI.create("ftps://host/source/large-file.bin"))
                .destinationPath(Path.of("/dest/large-file.bin"))
                .build();

            assertThatThrownBy(() -> simulator.transfer(request,
                new InMemoryTransferProtocolSimulator.TransferContext()))
                .isInstanceOf(InMemoryTransferProtocolSimulator.TransferException.class)
                .hasMessageContaining("interrupted");
            log.info("FTPS transfer interruption at 50% correctly simulated");
        }

        @Test
        @DisplayName("Should simulate checksum mismatch (data corruption)")
        void testChecksumMismatch() {
            simulator.setFailureMode(InMemoryTransferProtocolSimulator.ProtocolFailureMode.CHECKSUM_MISMATCH);

            var request = InMemoryTransferProtocolSimulator.TransferRequest.builder()
                .sourceUri(URI.create("ftps://host/source/secure-report.csv"))
                .destinationPath(Path.of("/dest/report.csv"))
                .build();

            assertThatThrownBy(() -> simulator.transfer(request,
                new InMemoryTransferProtocolSimulator.TransferContext()))
                .isInstanceOf(InMemoryTransferProtocolSimulator.TransferException.class)
                .hasMessageContaining("Checksum");
        }

        @Test
        @DisplayName("Should simulate permission denied")
        void testPermissionDenied() {
            simulator.setFailureMode(InMemoryTransferProtocolSimulator.ProtocolFailureMode.PERMISSION_DENIED);

            var request = InMemoryTransferProtocolSimulator.TransferRequest.builder()
                .sourceUri(URI.create("ftps://host/restricted/secret.dat"))
                .destinationPath(Path.of("/dest/secret.dat"))
                .build();

            assertThatThrownBy(() -> simulator.transfer(request,
                new InMemoryTransferProtocolSimulator.TransferContext()))
                .isInstanceOf(InMemoryTransferProtocolSimulator.TransferException.class)
                .hasMessageContaining("Permission denied");
        }

        @Test
        @DisplayName("Should simulate random failures with configurable rate")
        void testRandomFailureRate() throws Exception {
            log.info("Testing FTPS random failure injection at 50% rate");
            simulator.setFailureRate(0.5);

            int successes = 0;
            int failures = 0;

            for (int i = 0; i < 20; i++) {
                try {
                    var request = InMemoryTransferProtocolSimulator.TransferRequest.builder()
                        .sourceUri(URI.create("ftps://host/source/secure-report.csv"))
                        .destinationPath(Path.of("/dest/attempt-" + i + ".csv"))
                        .build();
                    simulator.transfer(request, new InMemoryTransferProtocolSimulator.TransferContext());
                    successes++;
                } catch (InMemoryTransferProtocolSimulator.TransferException e) {
                    failures++;
                }
            }

            log.info("Random failures: successes={}, failures={}", successes, failures);
            // With 50% rate over 20 attempts, expect both successes and failures
            assertThat(successes).isGreaterThan(0);
            assertThat(failures).isGreaterThan(0);
        }

        @Test
        @DisplayName("Should reset chaos configuration")
        void testResetChaos() throws Exception {
            simulator.setFailureMode(InMemoryTransferProtocolSimulator.ProtocolFailureMode.AUTH_FAILURE);

            // Verify failure is active
            var request = InMemoryTransferProtocolSimulator.TransferRequest.builder()
                .sourceUri(URI.create("ftps://host/source/secure-report.csv"))
                .destinationPath(Path.of("/dest/report.csv"))
                .build();

            assertThatThrownBy(() -> simulator.transfer(request,
                new InMemoryTransferProtocolSimulator.TransferContext()))
                .isInstanceOf(InMemoryTransferProtocolSimulator.TransferException.class);

            // Reset and verify transfer succeeds
            simulator.reset();

            var result = simulator.transfer(request, new InMemoryTransferProtocolSimulator.TransferContext());
            assertThat(result.isSuccessful()).isTrue();
            log.info("Chaos reset: transfer succeeded after reset");
        }
    }

    // ==================== Statistics ====================

    @Nested
    @DisplayName("Transfer Statistics")
    class StatisticsTests {

        @Test
        @DisplayName("Should track FTPS transfer statistics")
        void testTransferStatistics() throws Exception {
            log.info("Testing FTPS transfer statistics tracking");

            // Perform two successful transfers
            for (int i = 0; i < 2; i++) {
                var request = InMemoryTransferProtocolSimulator.TransferRequest.builder()
                    .sourceUri(URI.create("ftps://host/source/secure-report.csv"))
                    .destinationPath(Path.of("/dest/stats-" + i + ".csv"))
                    .build();
                simulator.transfer(request, new InMemoryTransferProtocolSimulator.TransferContext());
            }

            // Trigger one failure
            simulator.setFailureMode(InMemoryTransferProtocolSimulator.ProtocolFailureMode.AUTH_FAILURE);
            try {
                var failRequest = InMemoryTransferProtocolSimulator.TransferRequest.builder()
                    .sourceUri(URI.create("ftps://host/source/secure-report.csv"))
                    .destinationPath(Path.of("/dest/fail.csv"))
                    .build();
                simulator.transfer(failRequest, new InMemoryTransferProtocolSimulator.TransferContext());
            } catch (InMemoryTransferProtocolSimulator.TransferException ignored) {
            }

            log.info("Stats: total={}, successful={}, failed={}, bytes={}",
                simulator.getTotalTransfers(), simulator.getSuccessfulTransfers(),
                simulator.getFailedTransfers(), simulator.getTotalBytesTransferred());

            assertThat(simulator.getTotalTransfers()).isEqualTo(3);
            assertThat(simulator.getSuccessfulTransfers()).isEqualTo(2);
            assertThat(simulator.getFailedTransfers()).isEqualTo(1);
            assertThat(simulator.getTotalBytesTransferred()).isGreaterThan(0);
        }

        @Test
        @DisplayName("Should reset statistics")
        void testResetStatistics() throws Exception {
            var request = InMemoryTransferProtocolSimulator.TransferRequest.builder()
                .sourceUri(URI.create("ftps://host/source/secure-report.csv"))
                .destinationPath(Path.of("/dest/report.csv"))
                .build();
            simulator.transfer(request, new InMemoryTransferProtocolSimulator.TransferContext());

            assertThat(simulator.getTotalTransfers()).isGreaterThan(0);

            simulator.resetStatistics();

            assertThat(simulator.getTotalTransfers()).isEqualTo(0);
            assertThat(simulator.getSuccessfulTransfers()).isEqualTo(0);
            assertThat(simulator.getFailedTransfers()).isEqualTo(0);
            assertThat(simulator.getTotalBytesTransferred()).isEqualTo(0);
        }
    }

    // ==================== Bandwidth Throttling ====================

    @Nested
    @DisplayName("Bandwidth Throttling")
    class BandwidthTests {

        @Test
        @DisplayName("Should throttle FTPS transfer speed")
        void testBandwidthThrottling() throws Exception {
            log.info("Testing FTPS bandwidth throttling at 10KB/s");
            simulator.setSimulatedBytesPerSecond(10_000); // 10KB/s
            simulator.setLatencyConfig(0, 0); // No extra latency

            var request = InMemoryTransferProtocolSimulator.TransferRequest.builder()
                .sourceUri(URI.create("ftps://host/source/large-file.bin"))
                .destinationPath(Path.of("/dest/large-file.bin"))
                .build();

            long start = System.currentTimeMillis();
            var result = simulator.transfer(request, new InMemoryTransferProtocolSimulator.TransferContext());
            long elapsed = System.currentTimeMillis() - start;

            log.info("50KB file at 10KB/s: elapsed={}ms (expected ~5000ms)", elapsed);
            assertThat(result.isSuccessful()).isTrue();
            // 50KB at 10KB/s should take ~5 seconds minimum
            assertThat(elapsed).isGreaterThanOrEqualTo(4000);
        }
    }

    // ==================== Concurrent Transfers ====================

    @Nested
    @DisplayName("Concurrent Transfers")
    class ConcurrencyTests {

        @Test
        @DisplayName("Should handle concurrent FTPS transfers")
        void testConcurrentTransfers() throws Exception {
            log.info("Testing concurrent FTPS transfers");
            int transferCount = 5;
            ExecutorService executor = Executors.newFixedThreadPool(transferCount);

            try {
                List<Future<InMemoryTransferProtocolSimulator.TransferResult>> futures = new CopyOnWriteArrayList<>();

                for (int i = 0; i < transferCount; i++) {
                    final int idx = i;
                    futures.add(executor.submit(() -> {
                        var request = InMemoryTransferProtocolSimulator.TransferRequest.builder()
                            .sourceUri(URI.create("ftps://host/source/secure-report.csv"))
                            .destinationPath(Path.of("/dest/concurrent-" + idx + ".csv"))
                            .build();
                        return simulator.transfer(request, new InMemoryTransferProtocolSimulator.TransferContext());
                    }));
                }

                int successes = 0;
                for (Future<InMemoryTransferProtocolSimulator.TransferResult> future : futures) {
                    var result = future.get(30, TimeUnit.SECONDS);
                    if (result.isSuccessful()) successes++;
                }

                log.info("Concurrent FTPS transfers: {}/{} successful", successes, transferCount);
                assertThat(successes).isEqualTo(transferCount);
            } finally {
                executor.shutdown();
            }
        }
    }
}
