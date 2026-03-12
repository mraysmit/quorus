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

package dev.mars.quorus.transfer;

import dev.mars.quorus.core.TransferJob;
import dev.mars.quorus.core.TransferRequest;
import dev.mars.quorus.core.TransferResult;
import dev.mars.quorus.core.exceptions.TransferException;
import dev.mars.quorus.monitoring.TransferEngineHealthCheck;
import dev.mars.quorus.monitoring.TransferMetrics;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.lang.reflect.Field;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive test suite for {@link SimpleTransferEngine}.
 * Tests transfer submission, cancellation, pause/resume, health checks, and metrics.
 *
 * @author Mark Andrew Ray-Smith Cityline Ltd
 * @since 2025-08-27
 * @version 1.0
 */
@ExtendWith(VertxExtension.class)
class SimpleTransferEngineTest {

    private SimpleTransferEngine engine;
    private Vertx vertx;

    @BeforeEach
    void setUp(Vertx vertx) {
        this.vertx = vertx;
        engine = new SimpleTransferEngine(vertx, 5, 3, 100);
    }

    @AfterEach
    void tearDown(VertxTestContext testContext) {
        if (engine != null) {
            engine.shutdown(5)
                    .onComplete(ar -> testContext.completeNow());
        } else {
            testContext.completeNow();
        }
    }

    @Test
    void testConstructor() {
        assertNotNull(engine);
        assertEquals(0, engine.getActiveTransferCount());
    }

    @Test
    void testInjectedVertxConstructorDoesNotOwnVertxLifecycle() {
        assertFalse(engine.isClosingOwnedVertxOnShutdown());
    }

    @Test
    void testGetActiveTransferCount() {
        assertEquals(0, engine.getActiveTransferCount());
    }

    @Test
    void testSubmitTransferThrowsWhenShutdown(VertxTestContext testContext) {
        engine.shutdown(5);
        
        TransferRequest request = TransferRequest.builder()
            .requestId("test-job-1")
            .sourceUri(java.net.URI.create("http://example.com/file.txt"))
            .destinationPath("/tmp/file.txt")
            .protocol("http")
            .build();
        
        assertThrows(TransferException.class, () -> {
            engine.submitTransfer(request);
        });
        
        testContext.completeNow();
    }

    @Test
    void testCancelTransferNonExistent() {
        boolean cancelled = engine.cancelTransfer("nonexistent-job");
        assertFalse(cancelled);
    }

    @Test
    void testPauseTransferNonExistent() {
        boolean paused = engine.pauseTransfer("nonexistent-job");
        assertFalse(paused);
    }

    @Test
    void testResumeTransferNonExistent() {
        boolean resumed = engine.resumeTransfer("nonexistent-job");
        assertFalse(resumed);
    }

    @Test
    void testGetTransferJobNonExistent() {
        TransferJob job = engine.getTransferJob("nonexistent-job");
        assertNull(job);
    }

    @Test
    void testShutdownIdempotent(VertxTestContext testContext) {
        engine.shutdown(5)
                .compose(v -> engine.shutdown(5))
                .onComplete(testContext.succeedingThenComplete());
    }

    @Test
    void testGetHealthCheckWhenRunning() {
        TransferEngineHealthCheck healthCheck = engine.getHealthCheck();
        
        assertNotNull(healthCheck);
        assertTrue(healthCheck.getStatus() == TransferEngineHealthCheck.Status.UP || 
                   healthCheck.getStatus() == TransferEngineHealthCheck.Status.DEGRADED);
    }

    @Test
    void testGetHealthCheckAfterShutdown() {
        engine.shutdown(5);
        
        TransferEngineHealthCheck healthCheck = engine.getHealthCheck();
        
        assertNotNull(healthCheck);
        assertEquals(TransferEngineHealthCheck.Status.DOWN, healthCheck.getStatus());
    }

    @Test
    void testGetProtocolMetrics() {
        TransferMetrics httpMetrics = engine.getProtocolMetrics("http");
        assertNotNull(httpMetrics);
        
        TransferMetrics ftpMetrics = engine.getProtocolMetrics("ftp");
        assertNotNull(ftpMetrics);
        
        TransferMetrics sftpMetrics = engine.getProtocolMetrics("sftp");
        assertNotNull(sftpMetrics);
        
        TransferMetrics smbMetrics = engine.getProtocolMetrics("smb");
        assertNotNull(smbMetrics);
    }

    @Test
    void testGetProtocolMetricsNonExistent() {
        TransferMetrics metrics = engine.getProtocolMetrics("unknown");
        assertNull(metrics);
    }

    @Test
    void testGetAllProtocolMetrics() {
        Map<String, TransferMetrics> allMetrics = engine.getAllProtocolMetrics();
        
        assertNotNull(allMetrics);
        // 4 legacy protocol metrics + 4 download + 4 upload = 12 total
        assertEquals(12, allMetrics.size());
        // Legacy metrics
        assertTrue(allMetrics.containsKey("http"));
        assertTrue(allMetrics.containsKey("ftp"));
        assertTrue(allMetrics.containsKey("sftp"));
        assertTrue(allMetrics.containsKey("smb"));
        // Direction-specific metrics
        assertTrue(allMetrics.containsKey("http-DOWNLOAD"));
        assertTrue(allMetrics.containsKey("http-UPLOAD"));
        assertTrue(allMetrics.containsKey("ftp-DOWNLOAD"));
        assertTrue(allMetrics.containsKey("ftp-UPLOAD"));
        assertTrue(allMetrics.containsKey("sftp-DOWNLOAD"));
        assertTrue(allMetrics.containsKey("sftp-UPLOAD"));
        assertTrue(allMetrics.containsKey("smb-DOWNLOAD"));
        assertTrue(allMetrics.containsKey("smb-UPLOAD"));
    }

    @Test
    void testGetAllProtocolMetricsReturnsDefensiveCopy() {
        Map<String, TransferMetrics> allMetrics1 = engine.getAllProtocolMetrics();
        Map<String, TransferMetrics> allMetrics2 = engine.getAllProtocolMetrics();
        
        // Should be different map instances
        assertNotSame(allMetrics1, allMetrics2);
        
        // But same content
        assertEquals(allMetrics1.keySet(), allMetrics2.keySet());
    }

    @Test
    void testHealthCheckIncludesProtocolChecks() {
        TransferEngineHealthCheck healthCheck = engine.getHealthCheck();
        
        assertNotNull(healthCheck);
        assertNotNull(healthCheck.getProtocolHealthChecks());
        // 4 legacy + 4 download + 4 upload = 12 protocol metrics generate health checks
        assertEquals(12, healthCheck.getProtocolHealthChecks().size());
    }

    @Test
    void testHealthCheckIncludesSystemMetrics() {
        TransferEngineHealthCheck healthCheck = engine.getHealthCheck();
        
        assertNotNull(healthCheck);
        assertNotNull(healthCheck.getSystemMetrics());
        
        Map<String, Object> systemMetrics = healthCheck.getSystemMetrics();
        assertTrue(systemMetrics.containsKey("activeTransfers"));
        assertTrue(systemMetrics.containsKey("maxConcurrentTransfers"));
        assertTrue(systemMetrics.containsKey("uptime"));
        assertTrue(systemMetrics.containsKey("memoryUsedMB"));
        assertTrue(systemMetrics.containsKey("memoryTotalMB"));
        assertTrue(systemMetrics.containsKey("memoryMaxMB"));
        
        assertEquals(0, systemMetrics.get("activeTransfers"));
        assertEquals(5, systemMetrics.get("maxConcurrentTransfers"));
    }

    @Test
    void testProtocolMetricsInitialState() {
        TransferMetrics httpMetrics = engine.getProtocolMetrics("http");
        
        Map<String, Object> metricsMap = httpMetrics.toMap();
        assertEquals(0L, metricsMap.get("totalTransfers"));
        assertEquals(0L, metricsMap.get("successfulTransfers"));
        assertEquals(0L, metricsMap.get("failedTransfers"));
        assertEquals(0L, metricsMap.get("activeTransfers"));
    }

        @Test
        void testSubmitTransferRetriesWithBackoffThenFails() throws Exception {
        Path localFile = Files.createTempFile("retry-failure", ".txt");
        Files.writeString(localFile, "retry-me");

        TransferRequest request = TransferRequest.builder()
            .requestId("retry-backoff-test")
            .sourceUri(localFile.toUri())
            .destinationUri(URI.create("ftp://user:pass@127.0.0.1:1/unreachable.txt"))
            .protocol("ftp")
            .build();

        Instant start = Instant.now();
        TransferResult result = engine.submitTransfer(request)
            .toCompletionStage()
            .toCompletableFuture()
            .get(10, TimeUnit.SECONDS);
        long elapsedMs = java.time.Duration.between(start, Instant.now()).toMillis();

        assertNotNull(result);
        assertFalse(result.isSuccessful());
        assertEquals(dev.mars.quorus.core.TransferStatus.FAILED, result.getFinalStatus());
        assertTrue(result.getErrorMessage().isPresent());

        // Retry delay is 100ms with linear backoff (100 + 200 + 300ms) before final failure.
        assertTrue(elapsedMs >= 500,
            "Expected retry backoff to delay completion; elapsed=" + elapsedMs + "ms");
        }

        @Test
        void testAwaitActiveTransfersReturnsAfterTimeoutForStuckFuture() throws Exception {
        TransferRequest request = TransferRequest.builder()
            .requestId("stuck-future-test")
            .sourceUri(URI.create("http://example.com/file.txt"))
            .destinationPath("/tmp/file.txt")
            .protocol("http")
            .build();
        TransferJob job = new TransferJob(request);
        TransferContext context = new TransferContext(job);
        Promise<TransferResult> neverCompletes = Promise.promise();

        mapField("activeJobs").put(job.getJobId(), job);
        mapField("activeContexts").put(job.getJobId(), context);
        @SuppressWarnings("unchecked")
        ConcurrentHashMap<String, Future<TransferResult>> activeFutures =
            (ConcurrentHashMap<String, Future<TransferResult>>) getField("activeFutures");
        activeFutures.put(job.getJobId(), neverCompletes.future());

        Instant start = Instant.now();
        engine.awaitActiveTransfers(150)
            .toCompletionStage()
            .toCompletableFuture()
            .get(2, TimeUnit.SECONDS);
        long elapsedMs = java.time.Duration.between(start, Instant.now()).toMillis();

        assertTrue(elapsedMs >= 120,
            "awaitActiveTransfers should wait close to timeout before recovering; elapsed=" + elapsedMs + "ms");
        }

        @SuppressWarnings("unchecked")
        private ConcurrentHashMap<String, Object> mapField(String fieldName) throws Exception {
        return (ConcurrentHashMap<String, Object>) getField(fieldName);
        }

        private Object getField(String fieldName) throws Exception {
        Field field = SimpleTransferEngine.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.get(engine);
        }
}
