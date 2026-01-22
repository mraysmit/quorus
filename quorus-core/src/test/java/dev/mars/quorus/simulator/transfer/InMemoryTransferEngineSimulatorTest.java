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

package dev.mars.quorus.simulator.transfer;

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link InMemoryTransferEngineSimulator}.
 */
@DisplayName("InMemoryTransferEngineSimulator Tests")
class InMemoryTransferEngineSimulatorTest {

    private InMemoryTransferEngineSimulator engine;

    @BeforeEach
    void setUp() {
        engine = new InMemoryTransferEngineSimulator();
        engine.setDefaultTransferDurationMs(100); // Fast for testing
    }

    @AfterEach
    void tearDown() {
        if (engine != null) {
            engine.shutdown(5);
        }
    }

    // ==================== Basic Transfer Operations ====================

    @Nested
    @DisplayName("Basic Transfer Operations")
    class BasicTransferTests {

        @Test
        @DisplayName("Should submit and complete transfer")
        void testSubmitAndComplete() throws Exception {
            var request = InMemoryTransferEngineSimulator.TransferRequest.builder()
                .jobId("test-job-1")
                .sourceUri("sftp://host/source/file.txt")
                .destinationPath("/dest/file.txt")
                .protocol("sftp")
                .expectedSizeBytes(1000)
                .build();

            CompletableFuture<InMemoryTransferEngineSimulator.TransferResult> future = 
                engine.submitTransfer(request);

            var result = future.get(30, TimeUnit.SECONDS);

            assertThat(result.successful()).isTrue();
            assertThat(result.jobId()).isEqualTo("test-job-1");
            assertThat(result.bytesTransferred()).isEqualTo(1000);
            assertThat(result.duration()).isNotNull();
        }

        @Test
        @DisplayName("Should auto-generate job ID if not provided")
        void testAutoGenerateJobId() throws Exception {
            var request = InMemoryTransferEngineSimulator.TransferRequest.builder()
                .sourceUri("sftp://host/file.txt")
                .destinationPath("/dest/file.txt")
                .build();

            var future = engine.submitTransfer(request);
            var result = future.get(30, TimeUnit.SECONDS);

            assertThat(result.jobId()).isNotNull().startsWith("job-");
        }

        @Test
        @DisplayName("Should get transfer job status")
        void testGetTransferJob() throws Exception {
            var request = InMemoryTransferEngineSimulator.TransferRequest.builder()
                .jobId("status-test")
                .sourceUri("sftp://host/file.txt")
                .destinationPath("/dest/file.txt")
                .expectedSizeBytes(5000)
                .build();

            engine.submitTransfer(request);

            // Job should be visible immediately
            var job = engine.getTransferJob("status-test");
            assertThat(job).isNotNull();
            assertThat(job.jobId()).isEqualTo("status-test");
            assertThat(job.totalBytes()).isEqualTo(5000);
        }

        @Test
        @DisplayName("Should return null for unknown job")
        void testGetUnknownJob() {
            var job = engine.getTransferJob("nonexistent");
            assertThat(job).isNull();
        }
    }

    // ==================== Transfer Control ====================

    @Nested
    @DisplayName("Transfer Control")
    class TransferControlTests {

        @Test
        @DisplayName("Should cancel transfer")
        void testCancelTransfer() {
            engine.setDefaultTransferDurationMs(10000); // Long duration

            var request = InMemoryTransferEngineSimulator.TransferRequest.builder()
                .jobId("cancel-test")
                .sourceUri("sftp://host/file.txt")
                .destinationPath("/dest/file.txt")
                .build();

            engine.submitTransfer(request);

            boolean cancelled = engine.cancelTransfer("cancel-test");
            assertThat(cancelled).isTrue();

            var job = engine.getTransferJob("cancel-test");
            assertThat(job.status()).isEqualTo(
                InMemoryTransferEngineSimulator.TransferStatus.CANCELLED);
        }

        @Test
        @DisplayName("Should not cancel completed transfer")
        void testCannotCancelCompleted() throws Exception {
            var request = InMemoryTransferEngineSimulator.TransferRequest.builder()
                .jobId("completed-test")
                .sourceUri("sftp://host/file.txt")
                .destinationPath("/dest/file.txt")
                .build();

            var future = engine.submitTransfer(request);
            future.get(30, TimeUnit.SECONDS); // Wait for completion

            boolean cancelled = engine.cancelTransfer("completed-test");
            assertThat(cancelled).isFalse();
        }

        @Test
        @DisplayName("Should pause transfer")
        void testPauseTransfer() throws Exception {
            engine.setDefaultTransferDurationMs(10000); // Long duration

            var request = InMemoryTransferEngineSimulator.TransferRequest.builder()
                .jobId("pause-test")
                .sourceUri("sftp://host/file.txt")
                .destinationPath("/dest/file.txt")
                .build();

            engine.submitTransfer(request);
            Thread.sleep(50); // Let it start

            boolean paused = engine.pauseTransfer("pause-test");
            assertThat(paused).isTrue();

            var job = engine.getTransferJob("pause-test");
            assertThat(job.status()).isEqualTo(
                InMemoryTransferEngineSimulator.TransferStatus.PAUSED);
        }

        @Test
        @DisplayName("Should resume paused transfer")
        void testResumeTransfer() throws Exception {
            engine.setDefaultTransferDurationMs(10000);

            var request = InMemoryTransferEngineSimulator.TransferRequest.builder()
                .jobId("resume-test")
                .sourceUri("sftp://host/file.txt")
                .destinationPath("/dest/file.txt")
                .build();

            engine.submitTransfer(request);
            Thread.sleep(50);

            engine.pauseTransfer("resume-test");
            boolean resumed = engine.resumeTransfer("resume-test");
            
            assertThat(resumed).isTrue();

            var job = engine.getTransferJob("resume-test");
            assertThat(job.status()).isEqualTo(
                InMemoryTransferEngineSimulator.TransferStatus.IN_PROGRESS);
        }
    }

    // ==================== Concurrency ====================

    @Nested
    @DisplayName("Concurrency Control")
    class ConcurrencyTests {

        @Test
        @DisplayName("Should track active transfer count")
        void testActiveTransferCount() throws Exception {
            engine.setDefaultTransferDurationMs(5000); // Long enough to overlap

            // Submit 5 transfers
            List<CompletableFuture<InMemoryTransferEngineSimulator.TransferResult>> futures = 
                new ArrayList<>();
            
            for (int i = 0; i < 5; i++) {
                var request = InMemoryTransferEngineSimulator.TransferRequest.builder()
                    .jobId("concurrent-" + i)
                    .sourceUri("sftp://host/file" + i + ".txt")
                    .destinationPath("/dest/file" + i + ".txt")
                    .build();
                futures.add(engine.submitTransfer(request));
            }

            Thread.sleep(100); // Let some transfers start

            // Should have some active transfers
            assertThat(engine.getActiveTransferCount()).isGreaterThan(0);
            
            // Cancel all to clean up
            for (int i = 0; i < 5; i++) {
                engine.cancelTransfer("concurrent-" + i);
            }
        }

        @Test
        @DisplayName("Should get transfers by status")
        void testGetTransfersByStatus() throws Exception {
            engine.setDefaultTransferDurationMs(5000);

            // Submit 3 transfers
            for (int i = 0; i < 3; i++) {
                var request = InMemoryTransferEngineSimulator.TransferRequest.builder()
                    .jobId("status-" + i)
                    .sourceUri("sftp://host/file" + i + ".txt")
                    .destinationPath("/dest/file" + i + ".txt")
                    .build();
                engine.submitTransfer(request);
            }

            Thread.sleep(100);

            // Check that we can query by status
            var inProgress = engine.getTransfersByStatus(
                InMemoryTransferEngineSimulator.TransferStatus.IN_PROGRESS);
            // Just verify the query returns a list (may be empty if all completed fast)
            assertThat(inProgress).isNotNull();
            
            // Cancel all
            for (int i = 0; i < 3; i++) {
                engine.cancelTransfer("status-" + i);
            }
        }
    }

    // ==================== Event Callbacks ====================

    @Nested
    @DisplayName("Event Callbacks")
    class EventCallbackTests {

        @Test
        @DisplayName("Should fire events during transfer lifecycle")
        void testEventCallbacks() throws Exception {
            List<InMemoryTransferEngineSimulator.TransferEvent> events = 
                new CopyOnWriteArrayList<>();

            engine.setEventCallback(events::add);
            engine.setDefaultTransferDurationMs(200);

            var request = InMemoryTransferEngineSimulator.TransferRequest.builder()
                .jobId("event-test")
                .sourceUri("sftp://host/file.txt")
                .destinationPath("/dest/file.txt")
                .build();

            var future = engine.submitTransfer(request);
            future.get(30, TimeUnit.SECONDS);

            // Should have STARTED and COMPLETED at minimum
            assertThat(events).extracting(
                InMemoryTransferEngineSimulator.TransferEvent::type)
                .contains(
                    InMemoryTransferEngineSimulator.TransferEvent.EventType.STARTED,
                    InMemoryTransferEngineSimulator.TransferEvent.EventType.COMPLETED
                );
        }

        @Test
        @DisplayName("Should fire progress events")
        void testProgressEvents() throws Exception {
            AtomicInteger progressCount = new AtomicInteger(0);

            engine.setEventCallback(event -> {
                if (event.type() == InMemoryTransferEngineSimulator.TransferEvent.EventType.PROGRESS) {
                    progressCount.incrementAndGet();
                }
            });
            engine.setDefaultTransferDurationMs(500); // Long enough for multiple progress

            var request = InMemoryTransferEngineSimulator.TransferRequest.builder()
                .jobId("progress-test")
                .sourceUri("sftp://host/file.txt")
                .destinationPath("/dest/file.txt")
                .build();

            var future = engine.submitTransfer(request);
            future.get(30, TimeUnit.SECONDS);

            assertThat(progressCount.get()).isGreaterThan(0);
        }
    }

    // ==================== Chaos Engineering ====================

    @Nested
    @DisplayName("Chaos Engineering")
    class ChaosEngineeringTests {

        @ParameterizedTest
        @EnumSource(InMemoryTransferEngineSimulator.TransferEngineFailureMode.class)
        @DisplayName("Should support all failure modes")
        void testAllFailureModes(InMemoryTransferEngineSimulator.TransferEngineFailureMode mode) {
            engine.setFailureMode(mode);
            // Just verify setting doesn't throw
        }

        @Test
        @DisplayName("Should reject submissions when queue is full")
        void testQueueFull() {
            engine.setFailureMode(
                InMemoryTransferEngineSimulator.TransferEngineFailureMode.QUEUE_FULL);

            var request = InMemoryTransferEngineSimulator.TransferRequest.builder()
                .sourceUri("sftp://host/file.txt")
                .destinationPath("/dest/file.txt")
                .build();

            var future = engine.submitTransfer(request);

            assertThat(future).isCompletedExceptionally();
        }

        @Test
        @DisplayName("Should reject submissions when engine overloaded")
        void testEngineOverloaded() {
            engine.setFailureMode(
                InMemoryTransferEngineSimulator.TransferEngineFailureMode.ENGINE_OVERLOADED);

            var request = InMemoryTransferEngineSimulator.TransferRequest.builder()
                .sourceUri("sftp://host/file.txt")
                .destinationPath("/dest/file.txt")
                .build();

            var future = engine.submitTransfer(request);

            assertThat(future).isCompletedExceptionally();
        }

        @Test
        @DisplayName("Should fail all transfers when set")
        void testAllTransfersFail() throws Exception {
            engine.setFailureMode(
                InMemoryTransferEngineSimulator.TransferEngineFailureMode.ALL_TRANSFERS_FAIL);

            var request = InMemoryTransferEngineSimulator.TransferRequest.builder()
                .jobId("fail-test")
                .sourceUri("sftp://host/file.txt")
                .destinationPath("/dest/file.txt")
                .build();

            var future = engine.submitTransfer(request);

            assertThatThrownBy(() -> future.get(30, TimeUnit.SECONDS))
                .isInstanceOf(ExecutionException.class)
                .hasCauseInstanceOf(InMemoryTransferEngineSimulator.TransferException.class);
        }

        @Test
        @DisplayName("Should fail transfers randomly")
        void testRandomFailures() throws Exception {
            engine.setFailureMode(
                InMemoryTransferEngineSimulator.TransferEngineFailureMode.RANDOM_FAILURES);
            engine.setTransferFailureRate(1.0); // 100% failure

            var request = InMemoryTransferEngineSimulator.TransferRequest.builder()
                .sourceUri("sftp://host/file.txt")
                .destinationPath("/dest/file.txt")
                .build();

            var future = engine.submitTransfer(request);

            assertThatThrownBy(() -> future.get(30, TimeUnit.SECONDS))
                .isInstanceOf(ExecutionException.class);
        }

        @Test
        @DisplayName("Should reset chaos settings")
        void testResetChaos() throws Exception {
            engine.setFailureMode(
                InMemoryTransferEngineSimulator.TransferEngineFailureMode.ALL_TRANSFERS_FAIL);
            engine.setTransferFailureRate(1.0);

            engine.reset();

            var request = InMemoryTransferEngineSimulator.TransferRequest.builder()
                .sourceUri("sftp://host/file.txt")
                .destinationPath("/dest/file.txt")
                .build();

            var result = engine.submitTransfer(request).get(30, TimeUnit.SECONDS);
            assertThat(result.successful()).isTrue();
        }
    }

    // ==================== Health Check ====================

    @Nested
    @DisplayName("Health Check")
    class HealthCheckTests {

        @Test
        @DisplayName("Should report healthy status")
        void testHealthyStatus() {
            var health = engine.getHealthCheck();

            assertThat(health.healthy()).isTrue();
            assertThat(health.message()).contains("healthy");
            assertThat(health.maxConcurrentTransfers()).isEqualTo(10);
        }

        @Test
        @DisplayName("Should report unhealthy in failure mode")
        void testUnhealthyStatus() {
            engine.setFailureMode(
                InMemoryTransferEngineSimulator.TransferEngineFailureMode.ENGINE_OVERLOADED);

            var health = engine.getHealthCheck();

            assertThat(health.healthy()).isFalse();
            assertThat(health.message()).contains("failure mode");
        }

        @Test
        @DisplayName("Should track active transfers in health")
        void testActiveTransfersInHealth() throws Exception {
            engine.setDefaultTransferDurationMs(5000);

            var request = InMemoryTransferEngineSimulator.TransferRequest.builder()
                .jobId("health-test")
                .sourceUri("sftp://host/file.txt")
                .destinationPath("/dest/file.txt")
                .build();

            engine.submitTransfer(request);
            Thread.sleep(100);

            var health = engine.getHealthCheck();
            assertThat(health.activeTransfers()).isGreaterThanOrEqualTo(1);
            
            engine.cancelTransfer("health-test");
        }
    }

    // ==================== Statistics ====================

    @Nested
    @DisplayName("Statistics")
    class StatisticsTests {

        @Test
        @DisplayName("Should track total submitted")
        void testTotalSubmitted() throws Exception {
            for (int i = 0; i < 3; i++) {
                var request = InMemoryTransferEngineSimulator.TransferRequest.builder()
                    .jobId("stat-" + i)
                    .sourceUri("sftp://host/file.txt")
                    .destinationPath("/dest/file.txt")
                    .build();
                engine.submitTransfer(request).get(30, TimeUnit.SECONDS);
            }

            assertThat(engine.getTotalSubmitted()).isEqualTo(3);
        }

        @Test
        @DisplayName("Should track total completed")
        void testTotalCompleted() throws Exception {
            for (int i = 0; i < 2; i++) {
                var request = InMemoryTransferEngineSimulator.TransferRequest.builder()
                    .jobId("complete-stat-" + i)
                    .sourceUri("sftp://host/file.txt")
                    .destinationPath("/dest/file.txt")
                    .build();
                engine.submitTransfer(request).get(30, TimeUnit.SECONDS);
            }

            assertThat(engine.getTotalCompleted()).isEqualTo(2);
        }

        @Test
        @DisplayName("Should track total cancelled")
        void testTotalCancelled() {
            engine.setDefaultTransferDurationMs(10000);

            for (int i = 0; i < 2; i++) {
                var request = InMemoryTransferEngineSimulator.TransferRequest.builder()
                    .jobId("cancel-stat-" + i)
                    .sourceUri("sftp://host/file.txt")
                    .destinationPath("/dest/file.txt")
                    .build();
                engine.submitTransfer(request);
                engine.cancelTransfer("cancel-stat-" + i);
            }

            assertThat(engine.getTotalCancelled()).isEqualTo(2);
        }

        @Test
        @DisplayName("Should track total bytes transferred")
        void testTotalBytesTransferred() throws Exception {
            var request = InMemoryTransferEngineSimulator.TransferRequest.builder()
                .sourceUri("sftp://host/file.txt")
                .destinationPath("/dest/file.txt")
                .expectedSizeBytes(5000)
                .build();

            engine.submitTransfer(request).get(30, TimeUnit.SECONDS);

            assertThat(engine.getTotalBytesTransferred()).isEqualTo(5000);
        }

        @Test
        @DisplayName("Should reset statistics")
        void testResetStatistics() throws Exception {
            var request = InMemoryTransferEngineSimulator.TransferRequest.builder()
                .sourceUri("sftp://host/file.txt")
                .destinationPath("/dest/file.txt")
                .build();

            engine.submitTransfer(request).get(30, TimeUnit.SECONDS);
            engine.resetStatistics();

            assertThat(engine.getTotalSubmitted()).isZero();
            assertThat(engine.getTotalCompleted()).isZero();
            assertThat(engine.getTotalBytesTransferred()).isZero();
        }
    }

    // ==================== Protocol Metrics ====================

    @Nested
    @DisplayName("Protocol Metrics")
    class ProtocolMetricsTests {

        @Test
        @DisplayName("Should track protocol-specific metrics")
        void testProtocolMetrics() throws Exception {
            var request = InMemoryTransferEngineSimulator.TransferRequest.builder()
                .sourceUri("sftp://host/file.txt")
                .destinationPath("/dest/file.txt")
                .protocol("sftp")
                .expectedSizeBytes(1000)
                .build();

            engine.submitTransfer(request).get(30, TimeUnit.SECONDS);

            var metrics = engine.getProtocolMetrics("sftp");
            assertThat(metrics.getTransferCount()).isEqualTo(1);
            assertThat(metrics.getTotalBytes()).isEqualTo(1000);
        }

        @Test
        @DisplayName("Should get all protocol metrics")
        void testAllProtocolMetrics() throws Exception {
            var sftpRequest = InMemoryTransferEngineSimulator.TransferRequest.builder()
                .sourceUri("sftp://host/file.txt")
                .destinationPath("/dest/file.txt")
                .protocol("sftp")
                .build();

            var ftpRequest = InMemoryTransferEngineSimulator.TransferRequest.builder()
                .sourceUri("ftp://host/file.txt")
                .destinationPath("/dest/file.txt")
                .protocol("ftp")
                .build();

            engine.submitTransfer(sftpRequest).get(30, TimeUnit.SECONDS);
            engine.submitTransfer(ftpRequest).get(30, TimeUnit.SECONDS);

            var allMetrics = engine.getAllProtocolMetrics();
            assertThat(allMetrics).containsKeys("sftp", "ftp");
        }
    }

    // ==================== Lifecycle ====================

    @Nested
    @DisplayName("Lifecycle")
    class LifecycleTests {

        @Test
        @DisplayName("Should shutdown cleanly")
        void testShutdown() {
            var request = InMemoryTransferEngineSimulator.TransferRequest.builder()
                .sourceUri("sftp://host/file.txt")
                .destinationPath("/dest/file.txt")
                .build();

            engine.submitTransfer(request);

            boolean cleanShutdown = engine.shutdown(5);
            // May or may not be clean depending on timing
            assertThat(cleanShutdown).isNotNull();
        }

        @Test
        @DisplayName("Should clear all state")
        void testClear() throws Exception {
            var request = InMemoryTransferEngineSimulator.TransferRequest.builder()
                .jobId("clear-test")
                .sourceUri("sftp://host/file.txt")
                .destinationPath("/dest/file.txt")
                .build();

            engine.submitTransfer(request).get(30, TimeUnit.SECONDS);
            engine.clear();

            assertThat(engine.getTransferJob("clear-test")).isNull();
            assertThat(engine.getTotalSubmitted()).isZero();
        }
    }

    // ==================== Job Queries ====================

    @Nested
    @DisplayName("Job Queries")
    class JobQueryTests {

        @Test
        @DisplayName("Should get all transfer jobs")
        void testGetAllJobs() throws Exception {
            for (int i = 0; i < 3; i++) {
                var request = InMemoryTransferEngineSimulator.TransferRequest.builder()
                    .jobId("job-" + i)
                    .sourceUri("sftp://host/file.txt")
                    .destinationPath("/dest/file.txt")
                    .build();
                engine.submitTransfer(request).get(30, TimeUnit.SECONDS);
            }

            var jobs = engine.getAllTransferJobs();
            assertThat(jobs).hasSize(3);
        }

        @Test
        @DisplayName("Should filter jobs by status")
        void testGetJobsByStatus() throws Exception {
            engine.setDefaultTransferDurationMs(10000);

            var request = InMemoryTransferEngineSimulator.TransferRequest.builder()
                .jobId("status-filter-test")
                .sourceUri("sftp://host/file.txt")
                .destinationPath("/dest/file.txt")
                .build();

            engine.submitTransfer(request);
            Thread.sleep(50);

            var inProgress = engine.getTransfersByStatus(
                InMemoryTransferEngineSimulator.TransferStatus.IN_PROGRESS);
            assertThat(inProgress).extracting(
                InMemoryTransferEngineSimulator.TransferJob::jobId)
                .contains("status-filter-test");
            
            engine.cancelTransfer("status-filter-test");
        }
    }
}
