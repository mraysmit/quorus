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
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link InMemoryTransferProtocolSimulator}.
 */
@DisplayName("InMemoryTransferProtocolSimulator Tests")
class InMemoryTransferProtocolSimulatorTest {

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
            var ftp = InMemoryTransferProtocolSimulator.ftp(fileSystem);
            try {
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
            var sftp = InMemoryTransferProtocolSimulator.sftp(fileSystem);
            try {
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
            var http = InMemoryTransferProtocolSimulator.http(fileSystem);
            try {
                assertThat(http.getProtocolName()).isEqualTo("http");
                assertThat(http.supportsResume()).isTrue();
            } finally {
                http.shutdown();
            }
        }

        @Test
        @DisplayName("Should create SMB simulator")
        void testCreateSmb() {
            var smb = InMemoryTransferProtocolSimulator.smb(fileSystem);
            try {
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
            var request = new InMemoryTransferProtocolSimulator.TransferRequest(
                URI.create(uri), Path.of("/dest/file.txt"));
            assertThat(simulator.canHandle(request)).isTrue();
        }

        @Test
        @DisplayName("Should not handle non-matching protocol")
        void testCannotHandleNonMatching() {
            var request = new InMemoryTransferProtocolSimulator.TransferRequest(
                URI.create("ftp://host/file.txt"), Path.of("/dest/file.txt"));
            assertThat(simulator.canHandle(request)).isFalse();
        }

        @Test
        @DisplayName("HTTP simulator should handle HTTPS")
        void testHttpHandlesHttps() throws IOException {
            var http = InMemoryTransferProtocolSimulator.http(fileSystem);
            try {
                var request = new InMemoryTransferProtocolSimulator.TransferRequest(
                    URI.create("https://host/file.txt"), Path.of("/dest/file.txt"));
                assertThat(http.canHandle(request)).isTrue();
            } finally {
                http.shutdown();
            }
        }

        @Test
        @DisplayName("Should not handle null request")
        void testCannotHandleNull() {
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
            var request = InMemoryTransferProtocolSimulator.TransferRequest.builder()
                .sourceUri(URI.create("sftp://host/source/test.txt"))
                .destinationPath(Path.of("/destination/test.txt"))
                .build();

            var result = simulator.transfer(request, new InMemoryTransferProtocolSimulator.TransferContext());

            assertThat(result.isSuccessful()).isTrue();
            assertThat(result.bytesTransferred()).isEqualTo(13); // "Hello, World!"
            assertThat(result.transferId()).isNotEmpty();
            assertThat(result.duration()).isNotNull();
            assertThat(fileSystem.exists("/destination/test.txt")).isTrue();
        }

        @Test
        @DisplayName("Should perform async transfer")
        void testAsyncTransfer() throws Exception {
            var request = InMemoryTransferProtocolSimulator.TransferRequest.builder()
                .sourceUri(URI.create("sftp://host/source/test.txt"))
                .destinationPath(Path.of("/async/test.txt"))
                .build();

            CompletableFuture<InMemoryTransferProtocolSimulator.TransferResult> future = 
                simulator.transferReactive(request, new InMemoryTransferProtocolSimulator.TransferContext());

            var result = future.get(30, TimeUnit.SECONDS);

            assertThat(result.isSuccessful()).isTrue();
            assertThat(fileSystem.exists("/async/test.txt")).isTrue();
        }

        @Test
        @DisplayName("Should throw on non-existent source")
        void testNonExistentSource() {
            var request = InMemoryTransferProtocolSimulator.TransferRequest.builder()
                .sourceUri(URI.create("sftp://host/nonexistent.txt"))
                .destinationPath(Path.of("/dest/file.txt"))
                .build();

            assertThatThrownBy(() -> simulator.transfer(request, 
                new InMemoryTransferProtocolSimulator.TransferContext()))
                .isInstanceOf(InMemoryTransferProtocolSimulator.TransferException.class)
                .hasMessageContaining("not found");
        }

        @Test
        @DisplayName("Should respect max file size")
        void testMaxFileSizeLimit() throws IOException {
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
        }
    }

    // ==================== Progress Reporting ====================

    @Nested
    @DisplayName("Progress Reporting")
    class ProgressReportingTests {

        @Test
        @DisplayName("Should report progress during transfer")
        void testProgressCallback() throws Exception {
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

            assertThat(progressReports).isNotEmpty();
            
            // Last progress should be 100%
            var lastProgress = progressReports.get(progressReports.size() - 1);
            assertThat(lastProgress.percentComplete()).isEqualTo(100);
        }

        @Test
        @DisplayName("Should include ETA in progress")
        void testProgressEta() throws Exception {
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
            fileSystem.createFile("/source/data.bin", new byte[1000]);
            fileSystem.createFile("/dest/data.bin", new byte[500]); // Partial file

            var request = InMemoryTransferProtocolSimulator.TransferRequest.builder()
                .sourceUri(URI.create("sftp://host/source/data.bin"))
                .destinationPath(Path.of("/dest/data.bin"))
                .resumeFromBytes(500)
                .build();

            var result = simulator.transfer(request, new InMemoryTransferProtocolSimulator.TransferContext());

            assertThat(result.isSuccessful()).isTrue();
            assertThat(result.resumedFromBytes()).isEqualTo(500);
        }

        @Test
        @DisplayName("Should disable resume support")
        void testDisableResume() {
            simulator.setSupportsResume(false);
            assertThat(simulator.supportsResume()).isFalse();
        }

        @Test
        @DisplayName("Should disable pause support")
        void testDisablePause() {
            simulator.setSupportsPause(false);
            assertThat(simulator.supportsPause()).isFalse();
        }
    }

    // ==================== Performance Simulation ====================

    @Nested
    @DisplayName("Performance Simulation")
    class PerformanceSimulationTests {

        @Test
        @DisplayName("Should simulate connection latency")
        void testLatencySimulation() throws Exception {
            simulator.setLatencyConfig(100, 100); // Fixed 100ms latency

            var request = InMemoryTransferProtocolSimulator.TransferRequest.builder()
                .sourceUri(URI.create("sftp://host/source/test.txt"))
                .destinationPath(Path.of("/dest/test.txt"))
                .build();

            long start = System.currentTimeMillis();
            simulator.transfer(request, new InMemoryTransferProtocolSimulator.TransferContext());
            long elapsed = System.currentTimeMillis() - start;

            assertThat(elapsed).isGreaterThanOrEqualTo(100);
        }

        @Test
        @DisplayName("Should simulate bandwidth throttling")
        void testBandwidthThrottling() throws Exception {
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
            simulator.setFailureMode(InMemoryTransferProtocolSimulator.ProtocolFailureMode.AUTH_FAILURE);

            var request = InMemoryTransferProtocolSimulator.TransferRequest.builder()
                .sourceUri(URI.create("sftp://host/source/test.txt"))
                .destinationPath(Path.of("/dest/test.txt"))
                .build();

            assertThatThrownBy(() -> simulator.transfer(request, 
                new InMemoryTransferProtocolSimulator.TransferContext()))
                .isInstanceOf(InMemoryTransferProtocolSimulator.TransferException.class)
                .hasMessageContaining("Authentication failed");
        }

        @Test
        @DisplayName("Should simulate connection timeout")
        void testConnectionTimeout() {
            simulator.setFailureMode(InMemoryTransferProtocolSimulator.ProtocolFailureMode.CONNECTION_TIMEOUT);

            var request = InMemoryTransferProtocolSimulator.TransferRequest.builder()
                .sourceUri(URI.create("sftp://host/source/test.txt"))
                .destinationPath(Path.of("/dest/test.txt"))
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
                .sourceUri(URI.create("sftp://host/source/test.txt"))
                .destinationPath(Path.of("/dest/test.txt"))
                .build();

            assertThatThrownBy(() -> simulator.transfer(request, 
                new InMemoryTransferProtocolSimulator.TransferContext()))
                .isInstanceOf(InMemoryTransferProtocolSimulator.TransferException.class)
                .hasMessageContaining("Connection refused");
        }

        @Test
        @DisplayName("Should simulate permission denied")
        void testPermissionDenied() {
            simulator.setFailureMode(InMemoryTransferProtocolSimulator.ProtocolFailureMode.PERMISSION_DENIED);

            var request = InMemoryTransferProtocolSimulator.TransferRequest.builder()
                .sourceUri(URI.create("sftp://host/source/test.txt"))
                .destinationPath(Path.of("/dest/test.txt"))
                .build();

            assertThatThrownBy(() -> simulator.transfer(request, 
                new InMemoryTransferProtocolSimulator.TransferContext()))
                .isInstanceOf(InMemoryTransferProtocolSimulator.TransferException.class)
                .hasMessageContaining("Permission denied");
        }

        @Test
        @DisplayName("Should simulate disk full")
        void testDiskFull() {
            simulator.setFailureMode(InMemoryTransferProtocolSimulator.ProtocolFailureMode.DISK_FULL);

            var request = InMemoryTransferProtocolSimulator.TransferRequest.builder()
                .sourceUri(URI.create("sftp://host/source/test.txt"))
                .destinationPath(Path.of("/dest/test.txt"))
                .build();

            assertThatThrownBy(() -> simulator.transfer(request, 
                new InMemoryTransferProtocolSimulator.TransferContext()))
                .isInstanceOf(InMemoryTransferProtocolSimulator.TransferException.class)
                .hasMessageContaining("Disk full");
        }

        @Test
        @DisplayName("Should simulate checksum mismatch")
        void testChecksumMismatch() {
            simulator.setFailureMode(InMemoryTransferProtocolSimulator.ProtocolFailureMode.CHECKSUM_MISMATCH);

            var request = InMemoryTransferProtocolSimulator.TransferRequest.builder()
                .sourceUri(URI.create("sftp://host/source/test.txt"))
                .destinationPath(Path.of("/dest/test.txt"))
                .build();

            assertThatThrownBy(() -> simulator.transfer(request, 
                new InMemoryTransferProtocolSimulator.TransferContext()))
                .isInstanceOf(InMemoryTransferProtocolSimulator.TransferException.class)
                .hasMessageContaining("Checksum mismatch");
        }

        @Test
        @DisplayName("Should simulate failure at specific percent")
        void testFailureAtPercent() throws IOException {
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
        }

        @Test
        @DisplayName("Should simulate random failures")
        void testRandomFailures() throws IOException {
            simulator.setFailureRate(1.0); // 100% failure rate

            var request = InMemoryTransferProtocolSimulator.TransferRequest.builder()
                .sourceUri(URI.create("sftp://host/source/test.txt"))
                .destinationPath(Path.of("/dest/test.txt"))
                .build();

            assertThatThrownBy(() -> simulator.transfer(request, 
                new InMemoryTransferProtocolSimulator.TransferContext()))
                .isInstanceOf(InMemoryTransferProtocolSimulator.TransferException.class)
                .hasMessageContaining("Random");
        }

        @Test
        @DisplayName("Should reset chaos settings")
        void testResetChaos() throws Exception {
            simulator.setFailureMode(InMemoryTransferProtocolSimulator.ProtocolFailureMode.AUTH_FAILURE);
            simulator.setFailureRate(1.0);

            simulator.reset();

            var request = InMemoryTransferProtocolSimulator.TransferRequest.builder()
                .sourceUri(URI.create("sftp://host/source/test.txt"))
                .destinationPath(Path.of("/dest/test.txt"))
                .build();

            var result = simulator.transfer(request, new InMemoryTransferProtocolSimulator.TransferContext());
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
            var request = InMemoryTransferProtocolSimulator.TransferRequest.builder()
                .sourceUri(URI.create("sftp://host/source/test.txt"))
                .destinationPath(Path.of("/dest/test.txt"))
                .build();

            simulator.transfer(request, new InMemoryTransferProtocolSimulator.TransferContext());
            simulator.transfer(request, new InMemoryTransferProtocolSimulator.TransferContext());

            assertThat(simulator.getTotalTransfers()).isEqualTo(2);
        }

        @Test
        @DisplayName("Should track successful transfers")
        void testSuccessfulTransfers() throws Exception {
            var request = InMemoryTransferProtocolSimulator.TransferRequest.builder()
                .sourceUri(URI.create("sftp://host/source/test.txt"))
                .destinationPath(Path.of("/dest/test.txt"))
                .build();

            simulator.transfer(request, new InMemoryTransferProtocolSimulator.TransferContext());

            assertThat(simulator.getSuccessfulTransfers()).isEqualTo(1);
        }

        @Test
        @DisplayName("Should track failed transfers")
        void testFailedTransfers() {
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

            assertThat(simulator.getFailedTransfers()).isEqualTo(1);
        }

        @Test
        @DisplayName("Should track bytes transferred")
        void testBytesTransferred() throws Exception {
            var request = InMemoryTransferProtocolSimulator.TransferRequest.builder()
                .sourceUri(URI.create("sftp://host/source/test.txt"))
                .destinationPath(Path.of("/dest/test.txt"))
                .build();

            simulator.transfer(request, new InMemoryTransferProtocolSimulator.TransferContext());

            assertThat(simulator.getTotalBytesTransferred()).isEqualTo(13); // "Hello, World!"
        }

        @Test
        @DisplayName("Should reset statistics")
        void testResetStatistics() throws Exception {
            var request = InMemoryTransferProtocolSimulator.TransferRequest.builder()
                .sourceUri(URI.create("sftp://host/source/test.txt"))
                .destinationPath(Path.of("/dest/test.txt"))
                .build();

            simulator.transfer(request, new InMemoryTransferProtocolSimulator.TransferContext());
            simulator.resetStatistics();

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
            simulator.setLatencyConfig(10, 100);
            // Configuration doesn't throw
        }

        @Test
        @DisplayName("Should configure bandwidth")
        void testConfigureBandwidth() {
            simulator.setSimulatedBytesPerSecond(1_000_000);
            // Configuration doesn't throw
        }

        @Test
        @DisplayName("Should configure progress interval")
        void testConfigureProgressInterval() {
            simulator.setProgressUpdateIntervalMs(50);
            // Configuration doesn't throw
        }

        @Test
        @DisplayName("Should configure max file size")
        void testConfigureMaxFileSize() {
            simulator.setMaxFileSize(1_000_000_000);
            assertThat(simulator.getMaxFileSize()).isEqualTo(1_000_000_000);
        }

        @Test
        @DisplayName("Method chaining should work")
        void testMethodChaining() {
            var result = simulator
                .setLatencyConfig(10, 50)
                .setSimulatedBytesPerSecond(1_000_000)
                .setProgressUpdateIntervalMs(100)
                .setFailureMode(InMemoryTransferProtocolSimulator.ProtocolFailureMode.NONE);

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
                .get(60, TimeUnit.SECONDS);

            for (int i = 0; i < 5; i++) {
                assertThat(fileSystem.exists("/dest/file" + i + ".txt")).isTrue();
            }
        }
    }
}
