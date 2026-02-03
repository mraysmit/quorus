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
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link InMemoryTransferProtocolSimulator}.
 */
@ExtendWith(SimulatorTestLoggingExtension.class)
@DisplayName("InMemoryTransferProtocolSimulator Tests")
class InMemoryTransferProtocolSimulatorTest {

    private static final Logger log = LoggerFactory.getLogger(InMemoryTransferProtocolSimulatorTest.class);

    private InMemoryFileSystemSimulator fileSystem;
    private InMemoryTransferProtocolSimulator simulator;

    @BeforeEach
    void setUp() throws IOException {
        fileSystem = new InMemoryFileSystemSimulator();
        fileSystem.createFile("/source/test.txt", "Hello, World!".getBytes());
        simulator = InMemoryTransferProtocolSimulator.sftp(fileSystem);
    }

    @AfterEach
    void tearDown() {
        if (simulator != null) {
            simulator.shutdown();
        }
    }

    // ==================== Factory Methods ====================

    @Nested
    @DisplayName("Factory Methods")
    class FactoryMethodsTests {

        @Test
        @DisplayName("Should create FTP simulator")
        void testCreateFtp() {
            log.info("Testing FTP simulator factory method");
            var ftp = InMemoryTransferProtocolSimulator.ftp(fileSystem);
            try {
                log.info("Created FTP simulator: protocol={}, resume={}, pause={}", 
                    ftp.getProtocolName(), ftp.supportsResume(), ftp.supportsPause());
                assertThat(ftp.getProtocolName()).isEqualTo("ftp");
                assertThat(ftp.supportsResume()).isTrue();
                assertThat(ftp.supportsPause()).isTrue();
            } finally {
                ftp.shutdown();
            }
        }

        @Test
        @DisplayName("Should create SFTP simulator")
        void testCreateSftp() {
            log.info("Testing SFTP simulator factory method");
            var sftp = InMemoryTransferProtocolSimulator.sftp(fileSystem);
            try {
                log.info("Created SFTP simulator: protocol={}, resume={}, pause={}", 
                    sftp.getProtocolName(), sftp.supportsResume(), sftp.supportsPause());
                assertThat(sftp.getProtocolName()).isEqualTo("sftp");
                assertThat(sftp.supportsResume()).isTrue();
                assertThat(sftp.supportsPause()).isTrue();
            } finally {
                sftp.shutdown();
            }
        }

        @Test
        @DisplayName("Should create HTTP simulator")
        void testCreateHttp() {
            log.info("Testing HTTP simulator factory method");
            var http = InMemoryTransferProtocolSimulator.http(fileSystem);
            try {
                log.info("Created HTTP simulator: protocol={}, resume={}", 
                    http.getProtocolName(), http.supportsResume());
                assertThat(http.getProtocolName()).isEqualTo("http");
                assertThat(http.supportsResume()).isTrue();
            } finally {
                http.shutdown();
            }
        }

        @Test
        @DisplayName("Should create SMB simulator")
        void testCreateSmb() {
            log.info("Testing SMB simulator factory method");
            var smb = InMemoryTransferProtocolSimulator.smb(fileSystem);
            try {
                log.info("Created SMB simulator: protocol={}, resume={}", 
                    smb.getProtocolName(), smb.supportsResume());
                assertThat(smb.getProtocolName()).isEqualTo("smb");
                assertThat(smb.supportsResume()).isTrue();
            } finally {
                smb.shutdown();
            }
        }
    }

    // ==================== Protocol Handling ====================

    @Nested
    @DisplayName("Protocol Handling")
    class ProtocolHandlingTests {

        @ParameterizedTest
        @ValueSource(strings = {"sftp://host/file.txt", "SFTP://host/file.txt"})
        @DisplayName("Should handle matching protocol URIs")
        void testCanHandleMatchingProtocol(String uri) {
            log.info("Testing protocol matching for URI: {}", uri);
            var request = new InMemoryTransferProtocolSimulator.TransferRequest(
                URI.create(uri), Path.of("/dest/file.txt"));
            assertThat(simulator.canHandle(request)).isTrue();
        }

        @Test
        @DisplayName("Should not handle non-matching protocol")
        void testCannotHandleNonMatching() {
            log.info("Testing non-matching protocol rejection (ftp vs sftp simulator)");
            var request = new InMemoryTransferProtocolSimulator.TransferRequest(
                URI.create("ftp://host/file.txt"), Path.of("/dest/file.txt"));
            assertThat(simulator.canHandle(request)).isFalse();
        }

        @Test
        @DisplayName("HTTP simulator should handle HTTPS")
        void testHttpHandlesHttps() throws IOException {
            log.info("Testing HTTP simulator handles HTTPS protocol");
            var http = InMemoryTransferProtocolSimulator.http(fileSystem);
            try {
                var request = new InMemoryTransferProtocolSimulator.TransferRequest(
                    URI.create("https://host/file.txt"), Path.of("/dest/file.txt"));
                log.info("HTTP simulator can handle HTTPS: {}", http.canHandle(request));
                assertThat(http.canHandle(request)).isTrue();
            } finally {
                http.shutdown();
            }
        }

        @Test
        @DisplayName("Should not handle null request")
        void testCannotHandleNull() {
            log.info("Testing null request rejection");
            assertThat(simulator.canHandle(null)).isFalse();
        }
    }

    // ==================== Basic Transfers ====================

    @Nested
    @DisplayName("Basic Transfers")
    class BasicTransferTests {

        @Test
        @DisplayName("Should perform successful transfer")
        void testSuccessfulTransfer() throws Exception {
            log.info("Testing successful file transfer");
            var request = InMemoryTransferProtocolSimulator.TransferRequest.builder()
                .sourceUri(URI.create("sftp://host/source/test.txt"))
                .destinationPath(Path.of("/destination/test.txt"))
                .build();

            var result = simulator.transfer(request, new InMemoryTransferProtocolSimulator.TransferContext());
            log.info("Transfer completed: bytes={}, id={}, duration={}ms", 
                result.bytesTransferred(), result.transferId(), 
                result.duration() != null ? result.duration().toMillis() : "null");

            assertThat(result.isSuccessful()).isTrue();
            assertThat(result.bytesTransferred()).isEqualTo(13); // "Hello, World!"
            assertThat(result.transferId()).isNotEmpty();
            assertThat(result.duration()).isNotNull();
            assertThat(fileSystem.exists("/destination/test.txt")).isTrue();
        }

        @Test
        @DisplayName("Should perform async transfer")
        void testAsyncTransfer() throws Exception {
            log.info("Testing async file transfer");
            var request = InMemoryTransferProtocolSimulator.TransferRequest.builder()
                .sourceUri(URI.create("sftp://host/source/test.txt"))
                .destinationPath(Path.of("/async/test.txt"))
                .build();

            CompletableFuture<InMemoryTransferProtocolSimulator.TransferResult> future = 
                simulator.transferReactive(request, new InMemoryTransferProtocolSimulator.TransferContext());

            var result = future.get(5, TimeUnit.SECONDS);
            log.info("Async transfer completed: successful={}", result.isSuccessful());

            assertThat(result.isSuccessful()).isTrue();
            assertThat(fileSystem.exists("/async/test.txt")).isTrue();
        }

        @Test
        @DisplayName("Should throw on non-existent source")
        void testNonExistentSource() {
            log.info("Testing non-existent source file rejection");
            var request = InMemoryTransferProtocolSimulator.TransferRequest.builder()
                .sourceUri(URI.create("sftp://host/nonexistent.txt"))
                .destinationPath(Path.of("/dest/file.txt"))
                .build();

            assertThatThrownBy(() -> simulator.transfer(request, 
                new InMemoryTransferProtocolSimulator.TransferContext()))
                .isInstanceOf(InMemoryTransferProtocolSimulator.TransferException.class)
                .hasMessageContaining("not found");
            log.info("Non-existent source correctly rejected");
        }

        @Test
        @DisplayName("Should respect max file size")
        void testMaxFileSizeLimit() throws IOException {
            log.info("Testing max file size limit enforcement");
            fileSystem.createFile("/large.txt", new byte[10000]);
            simulator.setMaxFileSize(5000);

            var request = InMemoryTransferProtocolSimulator.TransferRequest.builder()
                .sourceUri(URI.create("sftp://host/large.txt"))
                .destinationPath(Path.of("/dest/large.txt"))
                .build();

            assertThatThrownBy(() -> simulator.transfer(request, 
                new InMemoryTransferProtocolSimulator.TransferContext()))
                .isInstanceOf(InMemoryTransferProtocolSimulator.TransferException.class)
                .hasMessageContaining("exceeds maximum");
            log.info("Max file size limit correctly enforced (10000 > 5000)");
        }
    }

    // ==================== Progress Reporting ====================

    @Nested
    @DisplayName("Progress Reporting")
    class ProgressReportingTests {

        @Test
        @DisplayName("Should report progress during transfer")
        void testProgressCallback() throws Exception {
            log.info("Testing progress callback during transfer");
            fileSystem.createFile("/source/data.bin", new byte[10000]);
            simulator.setSimulatedBytesPerSecond(50000); // 50KB/s for faster test
            simulator.setProgressUpdateIntervalMs(50);

            List<InMemoryTransferProtocolSimulator.TransferProgress> progressReports = 
                new CopyOnWriteArrayList<>();

            var context = new InMemoryTransferProtocolSimulator.TransferContext(
                progressReports::add);

            var request = InMemoryTransferProtocolSimulator.TransferRequest.builder()
                .sourceUri(URI.create("sftp://host/source/data.bin"))
                .destinationPath(Path.of("/dest/data.bin"))
                .build();

            simulator.transfer(request, context);
            log.info("Received {} progress reports, final percent: {}%", 
                progressReports.size(), 
                progressReports.isEmpty() ? 0 : progressReports.get(progressReports.size() - 1).percentComplete());

            assertThat(progressReports).isNotEmpty();
            
            // Last progress should be 100%
            var lastProgress = progressReports.get(progressReports.size() - 1);
            assertThat(lastProgress.percentComplete()).isEqualTo(100);
        }

        @Test
        @DisplayName("Should include ETA in progress")
        void testProgressEta() throws Exception {
            log.info("Testing ETA calculation in progress reports");
            fileSystem.createFile("/source/data.bin", new byte[50000]);
            simulator.setSimulatedBytesPerSecond(10000); // 10KB/s

            AtomicInteger progressCount = new AtomicInteger(0);

            var context = new InMemoryTransferProtocolSimulator.TransferContext(
                progress -> {
                    if (progress.percentComplete() < 100) {
                        assertThat(progress.estimatedTimeRemaining()).isNotNull();
                    }
                    progressCount.incrementAndGet();
                });

            var request = InMemoryTransferProtocolSimulator.TransferRequest.builder()
                .sourceUri(URI.create("sftp://host/source/data.bin"))
                .destinationPath(Path.of("/dest/data.bin"))
                .build();

            simulator.transfer(request, context);
            log.info("Received {} progress updates with ETA", progressCount.get());

            assertThat(progressCount.get()).isGreaterThan(0);
        }
    }

    // ==================== Resume and Pause ====================

    @Nested
    @DisplayName("Resume and Pause")
    class ResumePauseTests {

        @Test
        @DisplayName("Should resume from specified position")
        void testResumeFromPosition() throws Exception {
            log.info("Testing resume transfer from byte position 500");
            fileSystem.createFile("/source/data.bin", new byte[1000]);
            fileSystem.createFile("/dest/data.bin", new byte[500]); // Partial file

            var request = InMemoryTransferProtocolSimulator.TransferRequest.builder()
                .sourceUri(URI.create("sftp://host/source/data.bin"))
                .destinationPath(Path.of("/dest/data.bin"))
                .resumeFromBytes(500)
                .build();

            var result = simulator.transfer(request, new InMemoryTransferProtocolSimulator.TransferContext());
            log.info("Resume completed: successful={}, resumedFrom={}", 
                result.isSuccessful(), result.resumedFromBytes());

            assertThat(result.isSuccessful()).isTrue();
            assertThat(result.resumedFromBytes()).isEqualTo(500);
        }

        @Test
        @DisplayName("Should disable resume support")
        void testDisableResume() {
            log.info("Testing disabling resume support");
            simulator.setSupportsResume(false);
            assertThat(simulator.supportsResume()).isFalse();
            log.info("Resume support disabled: supportsResume={}", simulator.supportsResume());
        }

        @Test
        @DisplayName("Should disable pause support")
        void testDisablePause() {
            log.info("Testing disabling pause support");
            simulator.setSupportsPause(false);
            assertThat(simulator.supportsPause()).isFalse();
            log.info("Pause support disabled: supportsPause={}", simulator.supportsPause());
        }
    }

    // ==================== Performance Simulation ====================

    @Nested
    @DisplayName("Performance Simulation")
    class PerformanceSimulationTests {

        @Test
        @DisplayName("Should simulate connection latency")
        void testLatencySimulation() throws Exception {
            log.info("Testing 100ms connection latency simulation");
            simulator.setLatencyConfig(100, 100); // Fixed 100ms latency

            var request = InMemoryTransferProtocolSimulator.TransferRequest.builder()
                .sourceUri(URI.create("sftp://host/source/test.txt"))
                .destinationPath(Path.of("/dest/test.txt"))
                .build();

            long start = System.currentTimeMillis();
            simulator.transfer(request, new InMemoryTransferProtocolSimulator.TransferContext());
            long elapsed = System.currentTimeMillis() - start;
            log.info("Transfer with 100ms latency completed in {}ms", elapsed);

            assertThat(elapsed).isGreaterThanOrEqualTo(100);
        }

        @Test
        @DisplayName("Should simulate bandwidth throttling")
        void testBandwidthThrottling() throws Exception {
            log.info("Testing bandwidth throttling: 5KB file at 5KB/s");
            fileSystem.createFile("/source/data.bin", new byte[5000]);
            simulator.setSimulatedBytesPerSecond(5000); // 5KB/s
            simulator.setLatencyConfig(0, 0); // No latency

            var request = InMemoryTransferProtocolSimulator.TransferRequest.builder()
                .sourceUri(URI.create("sftp://host/source/data.bin"))
                .destinationPath(Path.of("/dest/data.bin"))
                .build();

            long start = System.currentTimeMillis();
            simulator.transfer(request, new InMemoryTransferProtocolSimulator.TransferContext());
            long elapsed = System.currentTimeMillis() - start;
            log.info("Throttled transfer completed in {}ms (expected ~1000ms)", elapsed);

            // Should take ~1 second for 5KB at 5KB/s
            assertThat(elapsed).isGreaterThanOrEqualTo(900); // Allow some variance
        }
    }

    // ==================== Chaos Engineering ====================

    @Nested
    @DisplayName("Chaos Engineering")
    class ChaosEngineeringTests {

        @Test
        @DisplayName("Should simulate authentication failure")
        void testAuthFailure() {
            log.info("Testing AUTH_FAILURE chaos mode");
            simulator.setFailureMode(InMemoryTransferProtocolSimulator.ProtocolFailureMode.AUTH_FAILURE);

            var request = InMemoryTransferProtocolSimulator.TransferRequest.builder()
                .sourceUri(URI.create("sftp://host/source/test.txt"))
                .destinationPath(Path.of("/dest/test.txt"))
                .build();

            assertThatThrownBy(() -> simulator.transfer(request, 
                new InMemoryTransferProtocolSimulator.TransferContext()))
                .isInstanceOf(InMemoryTransferProtocolSimulator.TransferException.class)
                .hasMessageContaining("Authentication failed");
            log.info("AUTH_FAILURE correctly simulated");
        }

        @Test
        @DisplayName("Should simulate connection timeout")
        void testConnectionTimeout() {
            log.info("Testing CONNECTION_TIMEOUT chaos mode");
            simulator.setFailureMode(InMemoryTransferProtocolSimulator.ProtocolFailureMode.CONNECTION_TIMEOUT);

            var request = InMemoryTransferProtocolSimulator.TransferRequest.builder()
                .sourceUri(URI.create("sftp://host/source/test.txt"))
                .destinationPath(Path.of("/dest/test.txt"))
                .build();

            assertThatThrownBy(() -> simulator.transfer(request, 
                new InMemoryTransferProtocolSimulator.TransferContext()))
                .isInstanceOf(InMemoryTransferProtocolSimulator.TransferException.class)
                .hasMessageContaining("timed out");
            log.info("CONNECTION_TIMEOUT correctly simulated");
        }

        @Test
        @DisplayName("Should simulate connection refused")
        void testConnectionRefused() {
            log.info("Testing CONNECTION_REFUSED chaos mode");
            simulator.setFailureMode(InMemoryTransferProtocolSimulator.ProtocolFailureMode.CONNECTION_REFUSED);

            var request = InMemoryTransferProtocolSimulator.TransferRequest.builder()
                .sourceUri(URI.create("sftp://host/source/test.txt"))
                .destinationPath(Path.of("/dest/test.txt"))
                .build();

            assertThatThrownBy(() -> simulator.transfer(request, 
                new InMemoryTransferProtocolSimulator.TransferContext()))
                .isInstanceOf(InMemoryTransferProtocolSimulator.TransferException.class)
                .hasMessageContaining("Connection refused");
            log.info("CONNECTION_REFUSED correctly simulated");
        }

        @Test
        @DisplayName("Should simulate permission denied")
        void testPermissionDenied() {
            log.info("Testing PERMISSION_DENIED chaos mode");
            simulator.setFailureMode(InMemoryTransferProtocolSimulator.ProtocolFailureMode.PERMISSION_DENIED);

            var request = InMemoryTransferProtocolSimulator.TransferRequest.builder()
                .sourceUri(URI.create("sftp://host/source/test.txt"))
                .destinationPath(Path.of("/dest/test.txt"))
                .build();

            assertThatThrownBy(() -> simulator.transfer(request, 
                new InMemoryTransferProtocolSimulator.TransferContext()))
                .isInstanceOf(InMemoryTransferProtocolSimulator.TransferException.class)
                .hasMessageContaining("Permission denied");
            log.info("PERMISSION_DENIED correctly simulated");
        }

        @Test
        @DisplayName("Should simulate disk full")
        void testDiskFull() {
            log.info("Testing DISK_FULL chaos mode");
            simulator.setFailureMode(InMemoryTransferProtocolSimulator.ProtocolFailureMode.DISK_FULL);

            var request = InMemoryTransferProtocolSimulator.TransferRequest.builder()
                .sourceUri(URI.create("sftp://host/source/test.txt"))
                .destinationPath(Path.of("/dest/test.txt"))
                .build();

            assertThatThrownBy(() -> simulator.transfer(request, 
                new InMemoryTransferProtocolSimulator.TransferContext()))
                .isInstanceOf(InMemoryTransferProtocolSimulator.TransferException.class)
                .hasMessageContaining("Disk full");
            log.info("DISK_FULL correctly simulated");
        }

        @Test
        @DisplayName("Should simulate checksum mismatch")
        void testChecksumMismatch() {
            log.info("Testing CHECKSUM_MISMATCH chaos mode");
            simulator.setFailureMode(InMemoryTransferProtocolSimulator.ProtocolFailureMode.CHECKSUM_MISMATCH);

            var request = InMemoryTransferProtocolSimulator.TransferRequest.builder()
                .sourceUri(URI.create("sftp://host/source/test.txt"))
                .destinationPath(Path.of("/dest/test.txt"))
                .build();

            assertThatThrownBy(() -> simulator.transfer(request, 
                new InMemoryTransferProtocolSimulator.TransferContext()))
                .isInstanceOf(InMemoryTransferProtocolSimulator.TransferException.class)
                .hasMessageContaining("Checksum mismatch");
            log.info("CHECKSUM_MISMATCH correctly simulated");
        }

        @Test
        @DisplayName("Should simulate failure at specific percent")
        void testFailureAtPercent() throws IOException {
            log.info("Testing TRANSFER_INTERRUPTED at 50% completion");
            fileSystem.createFile("/source/data.bin", new byte[10000]);
            simulator.setFailureMode(InMemoryTransferProtocolSimulator.ProtocolFailureMode.TRANSFER_INTERRUPTED);
            simulator.setFailureAtPercent(50);
            simulator.setSimulatedBytesPerSecond(50000); // Fast enough for test

            var request = InMemoryTransferProtocolSimulator.TransferRequest.builder()
                .sourceUri(URI.create("sftp://host/source/data.bin"))
                .destinationPath(Path.of("/dest/data.bin"))
                .build();

            assertThatThrownBy(() -> simulator.transfer(request, 
                new InMemoryTransferProtocolSimulator.TransferContext()))
                .isInstanceOf(InMemoryTransferProtocolSimulator.TransferException.class)
                .hasMessageContaining("interrupted");
            log.info("TRANSFER_INTERRUPTED at 50% correctly simulated");
        }

        @Test
        @DisplayName("Should simulate random failures")
        void testRandomFailures() throws IOException {
            log.info("Testing random failures with 100% failure rate");
            simulator.setFailureRate(1.0); // 100% failure rate

            var request = InMemoryTransferProtocolSimulator.TransferRequest.builder()
                .sourceUri(URI.create("sftp://host/source/test.txt"))
                .destinationPath(Path.of("/dest/test.txt"))
                .build();

            assertThatThrownBy(() -> simulator.transfer(request, 
                new InMemoryTransferProtocolSimulator.TransferContext()))
                .isInstanceOf(InMemoryTransferProtocolSimulator.TransferException.class)
                .hasMessageContaining("Random");
            log.info("Random failure correctly simulated");
        }

        @Test
        @DisplayName("Should reset chaos settings")
        void testResetChaos() throws Exception {
            log.info("Testing chaos settings reset");
            simulator.setFailureMode(InMemoryTransferProtocolSimulator.ProtocolFailureMode.AUTH_FAILURE);
            simulator.setFailureRate(1.0);

            simulator.reset();
            log.info("Chaos settings reset, attempting normal transfer");

            var request = InMemoryTransferProtocolSimulator.TransferRequest.builder()
                .sourceUri(URI.create("sftp://host/source/test.txt"))
                .destinationPath(Path.of("/dest/test.txt"))
                .build();

            var result = simulator.transfer(request, new InMemoryTransferProtocolSimulator.TransferContext());
            log.info("After reset, transfer succeeded: {}", result.isSuccessful());
            assertThat(result.isSuccessful()).isTrue();
        }
    }

    // ==================== Statistics ====================

    @Nested
    @DisplayName("Statistics")
    class StatisticsTests {

        @Test
        @DisplayName("Should track total transfers")
        void testTotalTransfers() throws Exception {
            log.info("Testing total transfers tracking");
            var request = InMemoryTransferProtocolSimulator.TransferRequest.builder()
                .sourceUri(URI.create("sftp://host/source/test.txt"))
                .destinationPath(Path.of("/dest/test.txt"))
                .build();

            simulator.transfer(request, new InMemoryTransferProtocolSimulator.TransferContext());
            simulator.transfer(request, new InMemoryTransferProtocolSimulator.TransferContext());
            log.info("Total transfers after 2 operations: {}", simulator.getTotalTransfers());

            assertThat(simulator.getTotalTransfers()).isEqualTo(2);
        }

        @Test
        @DisplayName("Should track successful transfers")
        void testSuccessfulTransfers() throws Exception {
            log.info("Testing successful transfers tracking");
            var request = InMemoryTransferProtocolSimulator.TransferRequest.builder()
                .sourceUri(URI.create("sftp://host/source/test.txt"))
                .destinationPath(Path.of("/dest/test.txt"))
                .build();

            simulator.transfer(request, new InMemoryTransferProtocolSimulator.TransferContext());
            log.info("Successful transfers: {}", simulator.getSuccessfulTransfers());

            assertThat(simulator.getSuccessfulTransfers()).isEqualTo(1);
        }

        @Test
        @DisplayName("Should track failed transfers")
        void testFailedTransfers() {
            log.info("Testing failed transfers tracking");
            simulator.setFailureMode(InMemoryTransferProtocolSimulator.ProtocolFailureMode.AUTH_FAILURE);

            var request = InMemoryTransferProtocolSimulator.TransferRequest.builder()
                .sourceUri(URI.create("sftp://host/source/test.txt"))
                .destinationPath(Path.of("/dest/test.txt"))
                .build();

            try {
                simulator.transfer(request, new InMemoryTransferProtocolSimulator.TransferContext());
            } catch (Exception e) {
                // Expected
            }
            log.info("Failed transfers: {}", simulator.getFailedTransfers());

            assertThat(simulator.getFailedTransfers()).isEqualTo(1);
        }

        @Test
        @DisplayName("Should track bytes transferred")
        void testBytesTransferred() throws Exception {
            log.info("Testing bytes transferred tracking");
            var request = InMemoryTransferProtocolSimulator.TransferRequest.builder()
                .sourceUri(URI.create("sftp://host/source/test.txt"))
                .destinationPath(Path.of("/dest/test.txt"))
                .build();

            simulator.transfer(request, new InMemoryTransferProtocolSimulator.TransferContext());
            log.info("Total bytes transferred: {}", simulator.getTotalBytesTransferred());

            assertThat(simulator.getTotalBytesTransferred()).isEqualTo(13); // "Hello, World!"
        }

        @Test
        @DisplayName("Should reset statistics")
        void testResetStatistics() throws Exception {
            log.info("Testing statistics reset");
            var request = InMemoryTransferProtocolSimulator.TransferRequest.builder()
                .sourceUri(URI.create("sftp://host/source/test.txt"))
                .destinationPath(Path.of("/dest/test.txt"))
                .build();

            simulator.transfer(request, new InMemoryTransferProtocolSimulator.TransferContext());
            simulator.resetStatistics();
            log.info("After reset - total: {}, successful: {}, bytes: {}", 
                simulator.getTotalTransfers(), simulator.getSuccessfulTransfers(), 
                simulator.getTotalBytesTransferred());

            assertThat(simulator.getTotalTransfers()).isZero();
            assertThat(simulator.getSuccessfulTransfers()).isZero();
            assertThat(simulator.getTotalBytesTransferred()).isZero();
        }
    }

    // ==================== Configuration ====================

    @Nested
    @DisplayName("Configuration")
    class ConfigurationTests {

        @Test
        @DisplayName("Should configure latency range")
        void testConfigureLatency() {
            log.info("Testing latency configuration (10-100ms)");
            simulator.setLatencyConfig(10, 100);
            log.info("Latency configured successfully");
            // Configuration doesn't throw
        }

        @Test
        @DisplayName("Should configure bandwidth")
        void testConfigureBandwidth() {
            log.info("Testing bandwidth configuration (1MB/s)");
            simulator.setSimulatedBytesPerSecond(1_000_000);
            log.info("Bandwidth configured successfully");
            // Configuration doesn't throw
        }

        @Test
        @DisplayName("Should configure progress interval")
        void testConfigureProgressInterval() {
            log.info("Testing progress interval configuration (50ms)");
            simulator.setProgressUpdateIntervalMs(50);
            log.info("Progress interval configured successfully");
            // Configuration doesn't throw
        }

        @Test
        @DisplayName("Should configure max file size")
        void testConfigureMaxFileSize() {
            log.info("Testing max file size configuration (1GB)");
            simulator.setMaxFileSize(1_000_000_000);
            log.info("Max file size configured: {}", simulator.getMaxFileSize());
            assertThat(simulator.getMaxFileSize()).isEqualTo(1_000_000_000);
        }

        @Test
        @DisplayName("Method chaining should work")
        void testMethodChaining() {
            log.info("Testing configuration method chaining");
            var result = simulator
                .setLatencyConfig(10, 50)
                .setSimulatedBytesPerSecond(1_000_000)
                .setProgressUpdateIntervalMs(100)
                .setFailureMode(InMemoryTransferProtocolSimulator.ProtocolFailureMode.NONE);
            log.info("Method chaining completed successfully");

            assertThat(result).isSameAs(simulator);
        }
    }

    // ==================== Concurrent Transfers ====================

    @Nested
    @DisplayName("Concurrent Transfers")
    class ConcurrentTransferTests {

        @Test
        @DisplayName("Should handle multiple concurrent transfers")
        void testConcurrentTransfers() throws Exception {
            log.info("Testing 5 concurrent async transfers");
            // Create source files
            for (int i = 0; i < 5; i++) {
                fileSystem.createFile("/source/file" + i + ".txt", ("content" + i).getBytes());
            }

            List<CompletableFuture<InMemoryTransferProtocolSimulator.TransferResult>> futures = 
                new ArrayList<>();

            for (int i = 0; i < 5; i++) {
                var request = InMemoryTransferProtocolSimulator.TransferRequest.builder()
                    .sourceUri(URI.create("sftp://host/source/file" + i + ".txt"))
                    .destinationPath(Path.of("/dest/file" + i + ".txt"))
                    .build();

                futures.add(simulator.transferReactive(request, 
                    new InMemoryTransferProtocolSimulator.TransferContext()));
            }

            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .get(10, TimeUnit.SECONDS);
            log.info("All 5 concurrent transfers completed");

            for (int i = 0; i < 5; i++) {
                assertThat(fileSystem.exists("/dest/file" + i + ".txt")).isTrue();
            }
        }
    }

    // ==================== Path Extraction ====================

    @Nested
    @DisplayName("Path Extraction")
    class PathExtractionTests {

        @ParameterizedTest(name = "{0} -> {1}")
        @MethodSource("provideUrisForExtraction")
        @DisplayName("Should extract path correctly from URI")
        void testExtractPath(String uriString, String expected) throws Exception {
            log.info("Testing path extraction: {} -> {}", uriString, expected);
            // Use reflection to test private method
            var method = InMemoryTransferProtocolSimulator.class.getDeclaredMethod("extractPath", URI.class);
            method.setAccessible(true);
            
            URI uri = URI.create(uriString);
            String result = (String) method.invoke(simulator, uri);
            log.info("Extracted path: {}", result);
            assertThat(result).isEqualTo(expected);
        }

        private static Stream<Arguments> provideUrisForExtraction() {
            return Stream.of(
                // Standard Unix/Linux paths
                Arguments.of("file:///home/user/test.txt", "/home/user/test.txt"),
                
                // Windows-style paths (The "C:" stripping logic)
                Arguments.of("file:///C:/destination/test.txt", "/destination/test.txt"),
                Arguments.of("file:///D:/work/project", "/work/project"),
                
                // Edge Cases: Root directories
                Arguments.of("file:///C:/", "/"),
                Arguments.of("file:///etc/", "/etc/"),
                
                // Non-file schemes (Should remain untouched)
                Arguments.of("http://localhost:8080/api/data", "/api/data"),
                Arguments.of("sftp://host/remote/path.txt", "/remote/path.txt"),
                
                // Empty/Null path scenarios
                Arguments.of("mailto:user@example.com", "/")
            );
        }
    }
}
