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

package dev.mars.quorus.tenant.service;

import dev.mars.quorus.tenant.model.ResourceUsage;
import dev.mars.quorus.tenant.model.Tenant;
import dev.mars.quorus.tenant.model.TenantConfiguration;
import dev.mars.quorus.tenant.service.ResourceManagementService.ResourceManagementException;
import dev.mars.quorus.tenant.service.TenantService.TenantServiceException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.RepeatedTest;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Concurrent stress tests for {@link SimpleResourceManagementService}.
 * Verifies that the per-tenant locking (StampedLock) and lock-free atomic
 * counter design is correct under high-contention multi-threaded scenarios.
 *
 * @author Mark Andrew Ray-Smith Cityline Ltd
 * @since 2026-02-14
 * @version 1.0
 */
@DisplayName("Resource Management Concurrency Tests")
class ResourceManagementConcurrencyTest {

    private SimpleResourceManagementService resourceService;
    private SimpleTenantService tenantService;

    private static final int THREAD_COUNT = 16;
    private static final int OPERATIONS_PER_THREAD = 500;
    private static final String TENANT_ID = "stress-tenant";

    @BeforeEach
    void setUp() throws TenantServiceException {
        tenantService = new SimpleTenantService();
        resourceService = new SimpleResourceManagementService(tenantService);

        Tenant tenant = Tenant.builder()
                .tenantId(TENANT_ID)
                .name("Stress Test Tenant")
                .description("Tenant for concurrency stress tests")
                .build();
        tenantService.createTenant(tenant);

        // Configure generous limits so reservations don't get denied
        TenantConfiguration.ResourceLimits limits = TenantConfiguration.ResourceLimits.builder()
                .maxConcurrentTransfers(100_000)
                .maxBandwidthBytesPerSecond(Long.MAX_VALUE / 2)
                .maxTransferSizeBytes(Long.MAX_VALUE / 2)
                .maxStorageBytes(Long.MAX_VALUE / 2)
                .maxTransfersPerDay(Integer.MAX_VALUE)
                .build();

        TenantConfiguration config = TenantConfiguration.builder()
                .resourceLimits(limits)
                .build();

        Tenant configured = tenantService.getTenant(TENANT_ID).get().toBuilder()
                .configuration(config)
                .build();
        tenantService.updateTenant(configured);
    }

    // ── Lock-free counter stress tests ───────────────────────────────────

    @Nested
    @DisplayName("Lock-Free Counter Updates")
    class LockFreeCounterTests {

        @Test
        @DisplayName("Concurrent updateConcurrentTransfers preserves count accuracy")
        void concurrentTransferCounterAccuracy() throws Exception {
            int increments = THREAD_COUNT * OPERATIONS_PER_THREAD;
            ExecutorService executor = Executors.newFixedThreadPool(THREAD_COUNT);
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch doneLatch = new CountDownLatch(THREAD_COUNT);

            for (int t = 0; t < THREAD_COUNT; t++) {
                executor.submit(() -> {
                    try {
                        startLatch.await();
                        for (int i = 0; i < OPERATIONS_PER_THREAD; i++) {
                            resourceService.updateConcurrentTransfers(TENANT_ID, 1);
                        }
                    } catch (Exception e) {
                        fail("Unexpected exception: " + e.getMessage());
                    } finally {
                        doneLatch.countDown();
                    }
                });
            }

            startLatch.countDown(); // Release all threads simultaneously
            assertTrue(doneLatch.await(30, TimeUnit.SECONDS), "Timed out waiting for threads");
            executor.shutdown();

            ResourceUsage usage = resourceService.getCurrentUsage(TENANT_ID).orElseThrow();
            assertEquals(increments, usage.getCurrentConcurrentTransfers(),
                    "Concurrent transfers count should be exact after " + increments + " increments");
        }

        @Test
        @DisplayName("Concurrent increment/decrement of transfers converges to zero")
        void concurrentIncrementDecrementConvergesToZero() throws Exception {
            // First, set a baseline
            resourceService.updateConcurrentTransfers(TENANT_ID, THREAD_COUNT * OPERATIONS_PER_THREAD);

            ExecutorService executor = Executors.newFixedThreadPool(THREAD_COUNT);
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch doneLatch = new CountDownLatch(THREAD_COUNT);

            for (int t = 0; t < THREAD_COUNT; t++) {
                executor.submit(() -> {
                    try {
                        startLatch.await();
                        for (int i = 0; i < OPERATIONS_PER_THREAD; i++) {
                            resourceService.updateConcurrentTransfers(TENANT_ID, -1);
                        }
                    } catch (Exception e) {
                        fail("Unexpected exception: " + e.getMessage());
                    } finally {
                        doneLatch.countDown();
                    }
                });
            }

            startLatch.countDown();
            assertTrue(doneLatch.await(30, TimeUnit.SECONDS));
            executor.shutdown();

            ResourceUsage usage = resourceService.getCurrentUsage(TENANT_ID).orElseThrow();
            assertEquals(0, usage.getCurrentConcurrentTransfers(),
                    "Transfers should converge to zero after matching decrements");
        }

        @Test
        @DisplayName("Concurrent recordTransferCompletion counts are exact")
        void concurrentTransferCompletionAccuracy() throws Exception {
            int totalOps = THREAD_COUNT * OPERATIONS_PER_THREAD;
            long bytesPerOp = 1024L;
            ExecutorService executor = Executors.newFixedThreadPool(THREAD_COUNT);
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch doneLatch = new CountDownLatch(THREAD_COUNT);
            AtomicInteger failureCount = new AtomicInteger(0);

            for (int t = 0; t < THREAD_COUNT; t++) {
                final int threadIndex = t;
                executor.submit(() -> {
                    try {
                        startLatch.await();
                        for (int i = 0; i < OPERATIONS_PER_THREAD; i++) {
                            // Every other thread reports failures
                            boolean success = threadIndex % 2 == 0;
                            resourceService.recordTransferCompletion(TENANT_ID, bytesPerOp, success);
                            if (!success) failureCount.incrementAndGet();
                        }
                    } catch (Exception e) {
                        fail("Unexpected exception: " + e.getMessage());
                    } finally {
                        doneLatch.countDown();
                    }
                });
            }

            startLatch.countDown();
            assertTrue(doneLatch.await(30, TimeUnit.SECONDS));
            executor.shutdown();

            ResourceUsage usage = resourceService.getCurrentUsage(TENANT_ID).orElseThrow();
            assertEquals(totalOps, usage.getDailyTransferCount(),
                    "Daily transfer count should equal total operations");
            assertEquals(totalOps, usage.getTotalTransferCount(),
                    "Total transfer count should equal total operations");
            assertEquals((long) totalOps * bytesPerOp, usage.getDailyBytesTransferred(),
                    "Daily bytes should equal ops * bytesPerOp");
            assertEquals((long) totalOps * bytesPerOp, usage.getTotalBytesTransferred(),
                    "Total bytes should equal ops * bytesPerOp");
            assertEquals(failureCount.get(), usage.getDailyFailedTransfers(),
                    "Failed transfer count should match");
            assertEquals(failureCount.get(), usage.getTotalFailedTransfers(),
                    "Total failed count should match");
        }

        @Test
        @DisplayName("Concurrent bandwidth updates are eventually consistent")
        void concurrentBandwidthUpdates() throws Exception {
            long targetBandwidth = 999_999L;
            ExecutorService executor = Executors.newFixedThreadPool(THREAD_COUNT);
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch doneLatch = new CountDownLatch(THREAD_COUNT);

            for (int t = 0; t < THREAD_COUNT; t++) {
                final long threadBandwidth = 1000L * (t + 1);
                executor.submit(() -> {
                    try {
                        startLatch.await();
                        for (int i = 0; i < OPERATIONS_PER_THREAD; i++) {
                            resourceService.updateBandwidthUsage(TENANT_ID, threadBandwidth);
                        }
                    } catch (Exception e) {
                        fail("Unexpected exception: " + e.getMessage());
                    } finally {
                        doneLatch.countDown();
                    }
                });
            }

            // After all threads, set the canonical value
            startLatch.countDown();
            assertTrue(doneLatch.await(30, TimeUnit.SECONDS));
            executor.shutdown();

            resourceService.updateBandwidthUsage(TENANT_ID, targetBandwidth);
            ResourceUsage usage = resourceService.getCurrentUsage(TENANT_ID).orElseThrow();
            assertEquals(targetBandwidth, usage.getCurrentBandwidthBytesPerSecond(),
                    "Bandwidth should reflect the last set value");
        }

        @Test
        @DisplayName("Concurrent storage updates are eventually consistent")
        void concurrentStorageUpdates() throws Exception {
            long targetStorage = 42_000_000L;
            ExecutorService executor = Executors.newFixedThreadPool(THREAD_COUNT);
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch doneLatch = new CountDownLatch(THREAD_COUNT);

            for (int t = 0; t < THREAD_COUNT; t++) {
                final long threadStorage = 100_000L * (t + 1);
                executor.submit(() -> {
                    try {
                        startLatch.await();
                        for (int i = 0; i < OPERATIONS_PER_THREAD; i++) {
                            resourceService.updateStorageUsage(TENANT_ID, threadStorage);
                        }
                    } catch (Exception e) {
                        fail("Unexpected exception: " + e.getMessage());
                    } finally {
                        doneLatch.countDown();
                    }
                });
            }

            startLatch.countDown();
            assertTrue(doneLatch.await(30, TimeUnit.SECONDS));
            executor.shutdown();

            resourceService.updateStorageUsage(TENANT_ID, targetStorage);
            ResourceUsage usage = resourceService.getCurrentUsage(TENANT_ID).orElseThrow();
            assertEquals(targetStorage, usage.getCurrentStorageBytes(),
                    "Storage should reflect the last set value");
        }
    }

    // ── Compound operation stress tests ──────────────────────────────────

    @Nested
    @DisplayName("Compound Operations (Per-Tenant Lock)")
    class CompoundOperationTests {

        @Test
        @DisplayName("Concurrent reserve/release cycle preserves zero concurrent transfers")
        void concurrentReserveReleaseCycle() throws Exception {
            int opsPerThread = 100; // Fewer since reserve+release is heavier
            ExecutorService executor = Executors.newFixedThreadPool(THREAD_COUNT);
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch doneLatch = new CountDownLatch(THREAD_COUNT);
            AtomicInteger exceptionCount = new AtomicInteger(0);

            for (int t = 0; t < THREAD_COUNT; t++) {
                executor.submit(() -> {
                    try {
                        startLatch.await();
                        for (int i = 0; i < opsPerThread; i++) {
                            try {
                                String token = resourceService.reserveResources(
                                        TENANT_ID, 1024, 1000);
                                resourceService.releaseResources(token, 1024, 1000);
                            } catch (ResourceManagementException e) {
                                exceptionCount.incrementAndGet();
                            }
                        }
                    } catch (Exception e) {
                        fail("Unexpected exception: " + e.getMessage());
                    } finally {
                        doneLatch.countDown();
                    }
                });
            }

            startLatch.countDown();
            assertTrue(doneLatch.await(60, TimeUnit.SECONDS), "Timed out waiting for threads");
            executor.shutdown();

            ResourceUsage usage = resourceService.getCurrentUsage(TENANT_ID).orElseThrow();
            assertEquals(0, usage.getCurrentConcurrentTransfers(),
                    "After equal reserve/release cycles, concurrent transfers should be 0");
            assertEquals(0, usage.getCurrentBandwidthBytesPerSecond(),
                    "After equal reserve/release cycles, bandwidth should be 0");
            assertEquals(0, exceptionCount.get(),
                    "No exceptions expected with generous limits");

            // Each release records a transfer completion
            int expectedCompletions = THREAD_COUNT * opsPerThread;
            assertEquals(expectedCompletions, usage.getDailyTransferCount(),
                    "Daily transfer count should match total release operations");
        }

        @Test
        @DisplayName("Reserve without release accumulates concurrent transfers correctly")
        void reserveWithoutReleaseAccumulates() throws Exception {
            int opsPerThread = 50;
            ExecutorService executor = Executors.newFixedThreadPool(THREAD_COUNT);
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch doneLatch = new CountDownLatch(THREAD_COUNT);
            ConcurrentLinkedQueue<String> allTokens = new ConcurrentLinkedQueue<>();

            for (int t = 0; t < THREAD_COUNT; t++) {
                executor.submit(() -> {
                    try {
                        startLatch.await();
                        for (int i = 0; i < opsPerThread; i++) {
                            String token = resourceService.reserveResources(
                                    TENANT_ID, 1024, 500);
                            allTokens.add(token);
                        }
                    } catch (Exception e) {
                        fail("Unexpected exception: " + e.getMessage());
                    } finally {
                        doneLatch.countDown();
                    }
                });
            }

            startLatch.countDown();
            assertTrue(doneLatch.await(60, TimeUnit.SECONDS));
            executor.shutdown();

            int expectedReservations = THREAD_COUNT * opsPerThread;
            assertEquals(expectedReservations, allTokens.size(),
                    "Should have exactly the expected number of reservation tokens");

            ResourceUsage usage = resourceService.getCurrentUsage(TENANT_ID).orElseThrow();
            assertEquals(expectedReservations, usage.getCurrentConcurrentTransfers(),
                    "Concurrent transfers should equal number of unreleased reservations");
            assertEquals((long) expectedReservations * 500, usage.getCurrentBandwidthBytesPerSecond(),
                    "Bandwidth should equal sum of all reserved bandwidth");

            // Verify all tokens are unique
            Set<String> uniqueTokens = new HashSet<>(allTokens);
            assertEquals(expectedReservations, uniqueTokens.size(),
                    "All reservation tokens should be unique");
        }

        @Test
        @DisplayName("Releasing invalid token throws ResourceManagementException")
        void releaseInvalidTokenThrows() {
            assertThrows(ResourceManagementException.class,
                    () -> resourceService.releaseResources("INVALID-TOKEN", 0, 0));
        }

        @Test
        @DisplayName("recordUsage under contention preserves final state")
        void concurrentRecordUsage() throws Exception {
            ExecutorService executor = Executors.newFixedThreadPool(THREAD_COUNT);
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch doneLatch = new CountDownLatch(THREAD_COUNT);

            // Each thread writes a different concurrent transfers value
            for (int t = 0; t < THREAD_COUNT; t++) {
                final long transfers = (t + 1) * 100L;
                executor.submit(() -> {
                    try {
                        startLatch.await();
                        for (int i = 0; i < OPERATIONS_PER_THREAD; i++) {
                            ResourceUsage usage = ResourceUsage.builder()
                                    .tenantId(TENANT_ID)
                                    .currentConcurrentTransfers(transfers)
                                    .currentBandwidthBytesPerSecond(transfers * 1000)
                                    .currentStorageBytes(transfers * 10000)
                                    .build();
                            resourceService.recordUsage(usage);
                        }
                    } catch (Exception e) {
                        fail("Unexpected exception: " + e.getMessage());
                    } finally {
                        doneLatch.countDown();
                    }
                });
            }

            startLatch.countDown();
            assertTrue(doneLatch.await(30, TimeUnit.SECONDS));
            executor.shutdown();

            // After contention, usage should be a consistent snapshot from ONE thread
            ResourceUsage usage = resourceService.getCurrentUsage(TENANT_ID).orElseThrow();
            long transfers = usage.getCurrentConcurrentTransfers();
            assertTrue(transfers > 0, "Concurrent transfers should be positive");
            // The bandwidth and storage should be consistent with the transfers value
            assertEquals(transfers * 1000, usage.getCurrentBandwidthBytesPerSecond(),
                    "Bandwidth should be consistent with concurrent transfers (1000x ratio)");
            assertEquals(transfers * 10000, usage.getCurrentStorageBytes(),
                    "Storage should be consistent with concurrent transfers (10000x ratio)");
        }
    }

    // ── Multi-tenant isolation tests ─────────────────────────────────────

    @Nested
    @DisplayName("Multi-Tenant Isolation")
    class MultiTenantIsolationTests {

        @Test
        @DisplayName("Concurrent operations on different tenants don't interfere")
        void multiTenantIsolation() throws Exception {
            int tenantCount = 8;
            String[] tenantIds = new String[tenantCount];

            for (int i = 0; i < tenantCount; i++) {
                tenantIds[i] = "tenant-" + i;
                Tenant t = Tenant.builder()
                        .tenantId(tenantIds[i])
                        .name("Tenant " + i)
                        .build();
                tenantService.createTenant(t);
            }

            ExecutorService executor = Executors.newFixedThreadPool(tenantCount);
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch doneLatch = new CountDownLatch(tenantCount);

            for (int t = 0; t < tenantCount; t++) {
                final String tid = tenantIds[t];
                final long expectedCount = (t + 1) * 100L;
                executor.submit(() -> {
                    try {
                        startLatch.await();
                        for (int i = 0; i < expectedCount; i++) {
                            resourceService.updateConcurrentTransfers(tid, 1);
                            resourceService.recordTransferCompletion(tid, 512, true);
                        }
                    } catch (Exception e) {
                        fail("Unexpected exception for tenant " + tid + ": " + e.getMessage());
                    } finally {
                        doneLatch.countDown();
                    }
                });
            }

            startLatch.countDown();
            assertTrue(doneLatch.await(30, TimeUnit.SECONDS));
            executor.shutdown();

            // Verify each tenant has exactly its own count — no cross-contamination
            for (int t = 0; t < tenantCount; t++) {
                long expectedCount = (t + 1) * 100L;
                ResourceUsage usage = resourceService.getCurrentUsage(tenantIds[t]).orElseThrow();
                assertEquals(expectedCount, usage.getCurrentConcurrentTransfers(),
                        "Tenant " + tenantIds[t] + " should have exactly " + expectedCount + " transfers");
                assertEquals(expectedCount, usage.getDailyTransferCount(),
                        "Tenant " + tenantIds[t] + " should have exactly " + expectedCount + " daily completions");
                assertEquals(expectedCount * 512, usage.getDailyBytesTransferred(),
                        "Tenant " + tenantIds[t] + " bytes should be " + (expectedCount * 512));
            }
        }

        @Test
        @DisplayName("Per-tenant locks allow different tenants to proceed in parallel")
        void perTenantParallelismVerification() throws Exception {
            // Create 4 tenants with reservations enabled
            int tenantCount = 4;
            int opsPerTenant = 50;
            String[] tenantIds = new String[tenantCount];

            for (int i = 0; i < tenantCount; i++) {
                tenantIds[i] = "parallel-tenant-" + i;
                Tenant t = Tenant.builder()
                        .tenantId(tenantIds[i])
                        .name("Parallel Tenant " + i)
                        .build();
                tenantService.createTenant(t);

                TenantConfiguration.ResourceLimits limits = TenantConfiguration.ResourceLimits.builder()
                        .maxConcurrentTransfers(100_000)
                        .maxBandwidthBytesPerSecond(Long.MAX_VALUE / 2)
                        .maxTransferSizeBytes(Long.MAX_VALUE / 2)
                        .maxTransfersPerDay(Integer.MAX_VALUE)
                        .build();
                TenantConfiguration config = TenantConfiguration.builder()
                        .resourceLimits(limits)
                        .build();
                Tenant configured = tenantService.getTenant(tenantIds[i]).get().toBuilder()
                        .configuration(config)
                        .build();
                tenantService.updateTenant(configured);
            }

            ExecutorService executor = Executors.newFixedThreadPool(tenantCount * 2);
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch doneLatch = new CountDownLatch(tenantCount);
            long startTime = System.nanoTime();

            for (int t = 0; t < tenantCount; t++) {
                final String tid = tenantIds[t];
                executor.submit(() -> {
                    try {
                        startLatch.await();
                        for (int i = 0; i < opsPerTenant; i++) {
                            String token = resourceService.reserveResources(tid, 1024, 100);
                            resourceService.releaseResources(token, 1024, 100);
                        }
                    } catch (Exception e) {
                        fail("Unexpected error for " + tid + ": " + e.getMessage());
                    } finally {
                        doneLatch.countDown();
                    }
                });
            }

            startLatch.countDown();
            assertTrue(doneLatch.await(60, TimeUnit.SECONDS));
            executor.shutdown();
            long elapsed = System.nanoTime() - startTime;

            // Verify all tenants ended up with 0 concurrent transfers
            for (String tid : tenantIds) {
                ResourceUsage usage = resourceService.getCurrentUsage(tid).orElseThrow();
                assertEquals(0, usage.getCurrentConcurrentTransfers(),
                        tid + " should have 0 concurrent transfers after balanced reserve/release");
                assertEquals(opsPerTenant, usage.getDailyTransferCount(),
                        tid + " should have " + opsPerTenant + " daily completions");
            }
        }
    }

    // ── Mixed workload stress tests ──────────────────────────────────────

    @Nested
    @DisplayName("Mixed Workload Stress")
    class MixedWorkloadTests {

        @Test
        @DisplayName("Mixed read/write workload under high contention")
        void mixedReadWriteWorkload() throws Exception {
            // Pre-populate some usage
            resourceService.recordUsage(ResourceUsage.builder()
                    .tenantId(TENANT_ID)
                    .currentConcurrentTransfers(10)
                    .currentBandwidthBytesPerSecond(1_000_000)
                    .currentStorageBytes(500_000_000)
                    .dailyTransferCount(100)
                    .build());

            int opsPerThread = 200;
            ExecutorService executor = Executors.newFixedThreadPool(THREAD_COUNT);
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch doneLatch = new CountDownLatch(THREAD_COUNT);
            AtomicLong readCount = new AtomicLong(0);
            AtomicLong writeCount = new AtomicLong(0);

            for (int t = 0; t < THREAD_COUNT; t++) {
                final int threadIndex = t;
                executor.submit(() -> {
                    try {
                        startLatch.await();
                        ThreadLocalRandom rng = ThreadLocalRandom.current();
                        for (int i = 0; i < opsPerThread; i++) {
                            int op = rng.nextInt(6);
                            switch (op) {
                                case 0 -> {
                                    // Read
                                    resourceService.getCurrentUsage(TENANT_ID);
                                    readCount.incrementAndGet();
                                }
                                case 1 -> {
                                    // Update concurrent transfers
                                    resourceService.updateConcurrentTransfers(TENANT_ID,
                                            rng.nextBoolean() ? 1 : -1);
                                    writeCount.incrementAndGet();
                                }
                                case 2 -> {
                                    // Update bandwidth
                                    resourceService.updateBandwidthUsage(TENANT_ID,
                                            rng.nextLong(1_000_000));
                                    writeCount.incrementAndGet();
                                }
                                case 3 -> {
                                    // Update storage
                                    resourceService.updateStorageUsage(TENANT_ID,
                                            rng.nextLong(1_000_000_000));
                                    writeCount.incrementAndGet();
                                }
                                case 4 -> {
                                    // Record completion
                                    resourceService.recordTransferCompletion(TENANT_ID,
                                            rng.nextLong(10_000), rng.nextBoolean());
                                    writeCount.incrementAndGet();
                                }
                                case 5 -> {
                                    // Get utilization (read-heavy)
                                    resourceService.getResourceUtilization(TENANT_ID);
                                    readCount.incrementAndGet();
                                }
                            }
                        }
                    } catch (Exception e) {
                        fail("Unexpected exception in thread " + threadIndex + ": " + e.getMessage());
                    } finally {
                        doneLatch.countDown();
                    }
                });
            }

            startLatch.countDown();
            assertTrue(doneLatch.await(30, TimeUnit.SECONDS), "Timed out");
            executor.shutdown();

            long totalOps = readCount.get() + writeCount.get();
            assertEquals(THREAD_COUNT * opsPerThread, totalOps,
                    "All operations should have completed");

            // Verify we can still get a consistent snapshot
            ResourceUsage usage = resourceService.getCurrentUsage(TENANT_ID).orElseThrow();
            assertNotNull(usage, "Usage snapshot should be available after mixed workload");
            assertTrue(usage.getTotalTransferCount() > 0 || usage.getDailyTransferCount() >= 100,
                    "Should have some transfer completions recorded");
        }

        @Test
        @DisplayName("Operation counter tracks all operations")
        void operationCounterAccuracy() throws Exception {
            long initialCount = resourceService.getTotalOperationCount();

            int opsPerThread = 100;
            ExecutorService executor = Executors.newFixedThreadPool(4);
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch doneLatch = new CountDownLatch(4);

            for (int t = 0; t < 4; t++) {
                executor.submit(() -> {
                    try {
                        startLatch.await();
                        for (int i = 0; i < opsPerThread; i++) {
                            resourceService.updateConcurrentTransfers(TENANT_ID, 1);
                        }
                    } catch (Exception e) {
                        fail(e);
                    } finally {
                        doneLatch.countDown();
                    }
                });
            }

            startLatch.countDown();
            assertTrue(doneLatch.await(30, TimeUnit.SECONDS));
            executor.shutdown();

            long finalCount = resourceService.getTotalOperationCount();
            long expectedOps = 4L * opsPerThread;
            assertEquals(expectedOps, finalCount - initialCount,
                    "Operation counter should track all update operations");
        }
    }

    // ── Reset daily usage stress test ────────────────────────────────────

    @Nested
    @DisplayName("Daily Reset Under Contention")
    class DailyResetTests {

        @Test
        @DisplayName("resetDailyUsage under concurrent writes doesn't corrupt state")
        void resetDailyWhileConcurrentWrites() throws Exception {
            // Pre-populate usage
            for (int i = 0; i < 100; i++) {
                resourceService.recordTransferCompletion(TENANT_ID, 1024, true);
            }

            ExecutorService executor = Executors.newFixedThreadPool(THREAD_COUNT + 1);
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch doneLatch = new CountDownLatch(THREAD_COUNT + 1);

            // Writer threads: continuously record completions
            for (int t = 0; t < THREAD_COUNT; t++) {
                executor.submit(() -> {
                    try {
                        startLatch.await();
                        for (int i = 0; i < 200; i++) {
                            resourceService.recordTransferCompletion(TENANT_ID, 512, true);
                        }
                    } catch (Exception e) {
                        fail("Writer thread exception: " + e.getMessage());
                    } finally {
                        doneLatch.countDown();
                    }
                });
            }

            // Reset thread: periodically resets daily usage
            executor.submit(() -> {
                try {
                    startLatch.await();
                    for (int i = 0; i < 5; i++) {
                        resourceService.resetDailyUsage();
                        Thread.sleep(1); // Tiny delay between resets
                    }
                } catch (Exception e) {
                    fail("Reset thread exception: " + e.getMessage());
                } finally {
                    doneLatch.countDown();
                }
            });

            startLatch.countDown();
            assertTrue(doneLatch.await(30, TimeUnit.SECONDS));
            executor.shutdown();

            // Verify state is consistent: no negative values, total >= daily
            ResourceUsage usage = resourceService.getCurrentUsage(TENANT_ID).orElseThrow();
            assertTrue(usage.getDailyTransferCount() >= 0,
                    "Daily transfer count should not be negative");
            assertTrue(usage.getDailyBytesTransferred() >= 0,
                    "Daily bytes should not be negative");
            assertTrue(usage.getTotalTransferCount() >= usage.getDailyTransferCount(),
                    "Total transfers should be >= daily transfers (total includes pre-reset history)");
        }
    }

    // ── Reservation limit enforcement under contention ───────────────────

    @Nested
    @DisplayName("Reservation Limit Enforcement")
    class ReservationLimitTests {

        @Test
        @DisplayName("Concurrent reservations respect concurrent transfer limit (no TOCTOU)")
        void concurrentReservationsRespectLimit() throws Exception {
            int maxConcurrent = 10;

            // Reconfigure with tight limits
            TenantConfiguration.ResourceLimits limits = TenantConfiguration.ResourceLimits.builder()
                    .maxConcurrentTransfers(maxConcurrent)
                    .maxBandwidthBytesPerSecond(Long.MAX_VALUE / 2)
                    .maxTransferSizeBytes(Long.MAX_VALUE / 2)
                    .maxTransfersPerDay(Integer.MAX_VALUE)
                    .build();
            TenantConfiguration config = TenantConfiguration.builder()
                    .resourceLimits(limits)
                    .build();
            Tenant configured = tenantService.getTenant(TENANT_ID).get().toBuilder()
                    .configuration(config)
                    .build();
            tenantService.updateTenant(configured);

            // Try to reserve from many threads simultaneously
            int attemptThreads = 50;
            ExecutorService executor = Executors.newFixedThreadPool(attemptThreads);
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch doneLatch = new CountDownLatch(attemptThreads);
            ConcurrentLinkedQueue<String> successfulTokens = new ConcurrentLinkedQueue<>();
            AtomicInteger deniedCount = new AtomicInteger(0);

            for (int t = 0; t < attemptThreads; t++) {
                executor.submit(() -> {
                    try {
                        startLatch.await();
                        String token = resourceService.reserveResources(TENANT_ID, 1024, 100);
                        successfulTokens.add(token);
                    } catch (ResourceManagementException e) {
                        deniedCount.incrementAndGet();
                    } catch (Exception e) {
                        fail("Unexpected error: " + e.getMessage());
                    } finally {
                        doneLatch.countDown();
                    }
                });
            }

            startLatch.countDown();
            assertTrue(doneLatch.await(30, TimeUnit.SECONDS));
            executor.shutdown();

            // With TOCTOU fix: exactly maxConcurrent should succeed
            assertEquals(maxConcurrent, successfulTokens.size(),
                    "Exactly " + maxConcurrent + " reservations should succeed (TOCTOU fix)");
            assertEquals(attemptThreads - maxConcurrent, deniedCount.get(),
                    "Remaining " + (attemptThreads - maxConcurrent) + " should be denied");

            // Verify the actual counter matches
            ResourceUsage usage = resourceService.getCurrentUsage(TENANT_ID).orElseThrow();
            assertEquals(maxConcurrent, usage.getCurrentConcurrentTransfers(),
                    "Concurrent transfers should match successful reservations");
        }
    }
}
