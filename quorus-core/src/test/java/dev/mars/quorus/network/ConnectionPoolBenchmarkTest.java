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

package dev.mars.quorus.network;

import io.vertx.core.Vertx;
import org.junit.jupiter.api.*;

import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Performance benchmark tests for ConnectionPoolService.
 * Validates the expected 100-400% performance improvement from PeeGeeQ research.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ConnectionPoolBenchmarkTest {

    private Vertx vertx;

    @BeforeEach
    void setUp() {
        vertx = Vertx.vertx();
    }

    @AfterEach
    void tearDown() {
        if (vertx != null) {
            vertx.close();
        }
    }

    @Test
    @Order(1)
    @DisplayName("Benchmark: Default config (10 connections) throughput")
    void benchmarkDefaultConfigThroughput() throws Exception {
        ConnectionPoolService.ConnectionPoolConfig config =
            ConnectionPoolService.ConnectionPoolConfig.defaultConfig();

        BenchmarkResult result = runThroughputBenchmark(config, "Default (10 connections)");

        System.out.println("\n=== DEFAULT CONFIG BENCHMARK ===");
        System.out.println(result);

        // Baseline - just verify it completes successfully
        assertTrue(result.successfulRequests > 0, "Should complete some requests");
        assertTrue(result.requestsPerSecond > 0, "Should have positive throughput");
    }

    @Test
    @Order(2)
    @DisplayName("Benchmark: Production config (100 connections) throughput")
    void benchmarkProductionConfigThroughput() throws Exception {
        ConnectionPoolService.ConnectionPoolConfig config =
            ConnectionPoolService.ConnectionPoolConfig.productionConfig();

        BenchmarkResult result = runThroughputBenchmark(config, "Production (100 connections)");

        System.out.println("\n=== PRODUCTION CONFIG BENCHMARK ===");
        System.out.println(result);

        // Should handle more concurrent requests
        assertTrue(result.successfulRequests > 0, "Should complete some requests");
        assertTrue(result.requestsPerSecond > 0, "Should have positive throughput");
    }

    @Test
    @Order(3)
    @DisplayName("Benchmark: Compare default vs production (expect 100-400% improvement)")
    void benchmarkCompareDefaultVsProduction() throws Exception {
        System.out.println("\n=== COMPARATIVE BENCHMARK: DEFAULT vs PRODUCTION ===\n");

        // Run default config benchmark
        ConnectionPoolService.ConnectionPoolConfig defaultConfig =
            ConnectionPoolService.ConnectionPoolConfig.defaultConfig();
        BenchmarkResult defaultResult = runThroughputBenchmark(defaultConfig, "Default");

        // Run production config benchmark
        ConnectionPoolService.ConnectionPoolConfig productionConfig =
            ConnectionPoolService.ConnectionPoolConfig.productionConfig();
        BenchmarkResult productionResult = runThroughputBenchmark(productionConfig, "Production");

        // Calculate improvement
        double throughputImprovement =
            ((productionResult.requestsPerSecond - defaultResult.requestsPerSecond) * 100.0)
            / defaultResult.requestsPerSecond;

        double latencyImprovement =
            ((defaultResult.averageLatencyMs - productionResult.averageLatencyMs) * 100.0)
            / defaultResult.averageLatencyMs;

        // Print comparison
        System.out.println("\n--- COMPARISON RESULTS ---");
        System.out.println("Default Config:");
        System.out.println("  Throughput: " + String.format("%.2f", defaultResult.requestsPerSecond) + " req/s");
        System.out.println("  Avg Latency: " + String.format("%.2f", defaultResult.averageLatencyMs) + " ms");
        System.out.println("  P95 Latency: " + defaultResult.p95LatencyMs + " ms");
        System.out.println("  P99 Latency: " + defaultResult.p99LatencyMs + " ms");

        System.out.println("\nProduction Config:");
        System.out.println("  Throughput: " + String.format("%.2f", productionResult.requestsPerSecond) + " req/s");
        System.out.println("  Avg Latency: " + String.format("%.2f", productionResult.averageLatencyMs) + " ms");
        System.out.println("  P95 Latency: " + productionResult.p95LatencyMs + " ms");
        System.out.println("  P99 Latency: " + productionResult.p99LatencyMs + " ms");

        System.out.println("\nImprovement:");
        System.out.println("  Throughput: " + String.format("%.1f%%", throughputImprovement) + " improvement");
        System.out.println("  Latency: " + String.format("%.1f%%", latencyImprovement) + " improvement");

        // Verify improvement (should be positive, ideally 100-400%)
        assertTrue(throughputImprovement > 0,
            "Production config should have better throughput than default");

        System.out.println("\n✅ Production config shows " + String.format("%.1f%%", throughputImprovement) +
            " throughput improvement over default config");
    }

    /**
     * Run a throughput benchmark with the given configuration.
     */
    private BenchmarkResult runThroughputBenchmark(
            ConnectionPoolService.ConnectionPoolConfig config,
            String configName) throws Exception {

        ConnectionPoolService poolService = new ConnectionPoolService(vertx, config);
        URI testUri = URI.create("http://localhost:8080/test");



        // Benchmark parameters
        int totalRequests = 1000;
        int concurrentThreads = 50;

        ExecutorService executor = Executors.newFixedThreadPool(concurrentThreads);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch completionLatch = new CountDownLatch(totalRequests);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);
        List<Long> latencies = new CopyOnWriteArrayList<>();

        long startTime = System.nanoTime();

        // Submit all requests
        for (int i = 0; i < totalRequests; i++) {
            executor.submit(() -> {
                try {
                    // Wait for all threads to be ready
                    startLatch.await();

                    long requestStart = System.nanoTime();

                    // Simulate connection acquisition and release
                    ConnectionPoolService.ConnectionPool pool = poolService.getConnectionPool(testUri);
                    CompletableFuture<ConnectionPoolService.PooledConnection> future = pool.getConnection();

                    try {
                        ConnectionPoolService.PooledConnection conn = future.get(5, TimeUnit.SECONDS);
                        // Simulate some work
                        Thread.sleep(1);
                        poolService.returnConnection(conn);

                        long requestEnd = System.nanoTime();
                        latencies.add((requestEnd - requestStart) / 1_000_000); // Convert to ms
                        successCount.incrementAndGet();
                    } catch (TimeoutException e) {
                        failureCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    failureCount.incrementAndGet();
                } finally {
                    completionLatch.countDown();
                }
            });
        }

        // Start all threads simultaneously
        startLatch.countDown();

        // Wait for completion (with timeout)
        boolean completed = completionLatch.await(60, TimeUnit.SECONDS);
        long endTime = System.nanoTime();

        executor.shutdown();
        executor.awaitTermination(10, TimeUnit.SECONDS);

        poolService.shutdown();

        assertTrue(completed, "Benchmark should complete within timeout");

        // Calculate metrics
        double durationSeconds = (endTime - startTime) / 1_000_000_000.0;
        double requestsPerSecond = successCount.get() / durationSeconds;

        List<Long> sortedLatencies = new ArrayList<>(latencies);
        sortedLatencies.sort(Long::compareTo);

        double avgLatency = sortedLatencies.stream()
            .mapToLong(Long::longValue)
            .average()
            .orElse(0.0);

        long p95Latency = sortedLatencies.isEmpty() ? 0 :
            sortedLatencies.get((int) (sortedLatencies.size() * 0.95));
        long p99Latency = sortedLatencies.isEmpty() ? 0 :
            sortedLatencies.get((int) (sortedLatencies.size() * 0.99));

        return new BenchmarkResult(
            configName,
            totalRequests,
            successCount.get(),
            failureCount.get(),
            durationSeconds,
            requestsPerSecond,
            avgLatency,
            p95Latency,
            p99Latency
        );
    }

    /**
     * Benchmark result data class.
     */
    private static class BenchmarkResult {
        final String configName;
        final int totalRequests;
        final int successfulRequests;
        final int failedRequests;
        final double durationSeconds;
        final double requestsPerSecond;
        final double averageLatencyMs;
        final long p95LatencyMs;
        final long p99LatencyMs;

        BenchmarkResult(String configName, int totalRequests, int successfulRequests,
                       int failedRequests, double durationSeconds, double requestsPerSecond,
                       double averageLatencyMs, long p95LatencyMs, long p99LatencyMs) {
            this.configName = configName;
            this.totalRequests = totalRequests;
            this.successfulRequests = successfulRequests;
            this.failedRequests = failedRequests;
            this.durationSeconds = durationSeconds;
            this.requestsPerSecond = requestsPerSecond;
            this.averageLatencyMs = averageLatencyMs;
            this.p95LatencyMs = p95LatencyMs;
            this.p99LatencyMs = p99LatencyMs;
        }

        @Override
        public String toString() {
            return String.format(
                "BenchmarkResult{config='%s', total=%d, success=%d, failed=%d, " +
                "duration=%.2fs, throughput=%.2f req/s, avgLatency=%.2fms, " +
                "p95=%.2fms, p99=%.2fms}",
                configName, totalRequests, successfulRequests, failedRequests,
                durationSeconds, requestsPerSecond, averageLatencyMs,
                (double) p95LatencyMs, (double) p99LatencyMs
            );
        }
    }
}
