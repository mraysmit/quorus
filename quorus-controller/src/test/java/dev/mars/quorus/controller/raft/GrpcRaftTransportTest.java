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

package dev.mars.quorus.controller.raft;

import dev.mars.quorus.controller.raft.grpc.AppendEntriesRequest;
import dev.mars.quorus.controller.raft.grpc.AppendEntriesResponse;
import dev.mars.quorus.controller.raft.grpc.RaftServiceGrpc;
import dev.mars.quorus.controller.raft.grpc.VoteRequest;
import dev.mars.quorus.controller.raft.grpc.VoteResponse;
import dev.mars.quorus.controller.state.QuorusStateMachine;
import io.grpc.*;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import java.io.IOException;
import java.net.ServerSocket;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Edge case tests for GrpcRaftTransport (client-side gRPC transport).
 * Tests various scenarios including:
 * - Connection handling
 * - Failure scenarios
 * - Retry behavior
 * - Timeout handling
 * - Concurrent operations
 * 
 * @author Mark Andrew Ray-Smith Cityline Ltd
 * @version 1.0
 * @since 2026-01-08
 */
@Execution(ExecutionMode.SAME_THREAD)
class GrpcRaftTransportTest {

    private Vertx vertx;
    private GrpcRaftServer targetServer;
    private RaftNode targetNode;
    private int targetPort;

    @BeforeEach
    void setUp() throws Exception {
        vertx = Vertx.vertx();
        targetPort = findAvailablePort();
        
        // Set up a target server to receive requests
        Set<String> clusterNodes = Set.of("target");
        InMemoryTransportSimulator transport = new InMemoryTransportSimulator("target");
        QuorusStateMachine stateMachine = new QuorusStateMachine();
        targetNode = new RaftNode(vertx, "target", clusterNodes, transport, stateMachine, 5000, 1000);
        targetNode.start();
        await().atMost(Duration.ofSeconds(5))
            .pollInterval(Duration.ofMillis(10))
            .until(() -> targetNode.isRunning());
        
        targetServer = new GrpcRaftServer(vertx, targetPort, targetNode);
        targetServer.start().toCompletionStage().toCompletableFuture().get(5, TimeUnit.SECONDS);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (targetServer != null) {
            targetServer.stop().toCompletionStage().toCompletableFuture().get(5, TimeUnit.SECONDS);
        }
        if (targetNode != null) {
            targetNode.stop();
        }
        if (vertx != null) {
            vertx.close().toCompletionStage().toCompletableFuture().get(5, TimeUnit.SECONDS);
        }
    }

    private int findAvailablePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    // ========== BASIC CONNECTIVITY TESTS ==========

    @Test
    @DisplayName("Transport should successfully send vote request to live server")
    void testSendVoteRequestSuccess() throws Exception {
        Map<String, String> cluster = new HashMap<>();
        cluster.put("target", "localhost:" + targetPort);
        
        GrpcRaftTransport transport = new GrpcRaftTransport(vertx, "client", cluster);
        transport.start(msg -> {});
        
        VoteRequest request = VoteRequest.newBuilder()
                .setTerm(1)
                .setCandidateId("client")
                .setLastLogIndex(0)
                .setLastLogTerm(0)
                .build();
        
        Future<VoteResponse> future = transport.sendVoteRequest("target", request);
        VoteResponse response = future.toCompletionStage().toCompletableFuture()
                .get(10, TimeUnit.SECONDS);
        
        assertNotNull(response);
        transport.stop();
    }

    @Test
    @DisplayName("Transport should successfully send append entries to live server")
    void testSendAppendEntriesSuccess() throws Exception {
        Map<String, String> cluster = new HashMap<>();
        cluster.put("target", "localhost:" + targetPort);
        
        GrpcRaftTransport transport = new GrpcRaftTransport(vertx, "client", cluster);
        transport.start(msg -> {});
        
        AppendEntriesRequest request = AppendEntriesRequest.newBuilder()
                .setTerm(1)
                .setLeaderId("client")
                .setPrevLogIndex(0)
                .setPrevLogTerm(0)
                .setLeaderCommit(0)
                .build();
        
        Future<AppendEntriesResponse> future = transport.sendAppendEntries("target", request);
        AppendEntriesResponse response = future.toCompletionStage().toCompletableFuture()
                .get(10, TimeUnit.SECONDS);
        
        assertNotNull(response);
        transport.stop();
    }

    // ========== CONNECTION FAILURE TESTS ==========

    @Test
    @DisplayName("Transport should fail when target server is down")
    void testSendToDownServer() throws Exception {
        int deadPort = findAvailablePort(); // Port with nothing listening
        
        Map<String, String> cluster = new HashMap<>();
        cluster.put("dead", "localhost:" + deadPort);
        
        GrpcRaftTransport transport = new GrpcRaftTransport(vertx, "client", cluster);
        transport.start(msg -> {});
        
        VoteRequest request = VoteRequest.newBuilder()
                .setTerm(1)
                .setCandidateId("client")
                .setLastLogIndex(0)
                .setLastLogTerm(0)
                .build();
        
        Future<VoteResponse> future = transport.sendVoteRequest("dead", request);
        
        assertThrows(ExecutionException.class, () -> 
                future.toCompletionStage().toCompletableFuture().get(30, TimeUnit.SECONDS));
        
        transport.stop();
    }

    @Test
    @DisplayName("Transport should handle server shutdown during request")
    void testServerShutdownDuringRequest() throws Exception {
        Map<String, String> cluster = new HashMap<>();
        cluster.put("target", "localhost:" + targetPort);
        
        GrpcRaftTransport transport = new GrpcRaftTransport(vertx, "client", cluster);
        transport.start(msg -> {});
        
        // First request should work
        VoteRequest request = VoteRequest.newBuilder()
                .setTerm(1)
                .setCandidateId("client")
                .setLastLogIndex(0)
                .setLastLogTerm(0)
                .build();
        
        VoteResponse response1 = transport.sendVoteRequest("target", request)
                .toCompletionStage().toCompletableFuture().get(10, TimeUnit.SECONDS);
        assertNotNull(response1);
        
        // Shut down the server
        targetServer.stop().toCompletionStage().toCompletableFuture().get(5, TimeUnit.SECONDS);
        targetServer = null;
        
        // Next request should fail
        Future<VoteResponse> failFuture = transport.sendVoteRequest("target", request);
        
        assertThrows(ExecutionException.class, () -> 
                failFuture.toCompletionStage().toCompletableFuture().get(30, TimeUnit.SECONDS));
        
        transport.stop();
    }

    @Test
    @DisplayName("Transport should throw for unknown node")
    void testUnknownNode() {
        Map<String, String> cluster = new HashMap<>();
        cluster.put("known", "localhost:" + targetPort);
        
        GrpcRaftTransport transport = new GrpcRaftTransport(vertx, "client", cluster);
        transport.start(msg -> {});
        
        VoteRequest request = VoteRequest.newBuilder()
                .setTerm(1)
                .setCandidateId("client")
                .setLastLogIndex(0)
                .setLastLogTerm(0)
                .build();
        
        assertThrows(IllegalArgumentException.class, () -> 
                transport.sendVoteRequest("unknown", request));
        
        transport.stop();
    }

    // ========== CONCURRENT REQUEST TESTS ==========

    @Test
    @DisplayName("Transport should handle concurrent requests to same target")
    void testConcurrentRequestsSameTarget() throws Exception {
        Map<String, String> cluster = new HashMap<>();
        cluster.put("target", "localhost:" + targetPort);
        
        GrpcRaftTransport transport = new GrpcRaftTransport(vertx, "client", cluster);
        transport.start(msg -> {});
        
        int numRequests = 50;
        ExecutorService executor = Executors.newFixedThreadPool(10);
        CountDownLatch latch = new CountDownLatch(numRequests);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger errorCount = new AtomicInteger(0);
        
        for (int i = 0; i < numRequests; i++) {
            final int term = i + 1;
            executor.submit(() -> {
                try {
                    VoteRequest request = VoteRequest.newBuilder()
                            .setTerm(term)
                            .setCandidateId("client")
                            .setLastLogIndex(0)
                            .setLastLogTerm(0)
                            .build();
                    
                    VoteResponse response = transport.sendVoteRequest("target", request)
                            .toCompletionStage().toCompletableFuture()
                            .get(10, TimeUnit.SECONDS);
                    
                    if (response != null) {
                        successCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    errorCount.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }
        
        assertTrue(latch.await(60, TimeUnit.SECONDS));
        executor.shutdown();
        
        assertEquals(numRequests, successCount.get());
        assertEquals(0, errorCount.get());
        
        transport.stop();
    }

    @Test
    @DisplayName("Transport should handle mixed concurrent vote and append requests")
    void testMixedConcurrentRequests() throws Exception {
        Map<String, String> cluster = new HashMap<>();
        cluster.put("target", "localhost:" + targetPort);
        
        GrpcRaftTransport transport = new GrpcRaftTransport(vertx, "client", cluster);
        transport.start(msg -> {});
        
        int numRequests = 100;
        ExecutorService executor = Executors.newFixedThreadPool(10);
        CountDownLatch latch = new CountDownLatch(numRequests);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger errorCount = new AtomicInteger(0);
        
        for (int i = 0; i < numRequests; i++) {
            final int index = i;
            executor.submit(() -> {
                try {
                    if (index % 2 == 0) {
                        VoteRequest request = VoteRequest.newBuilder()
                                .setTerm(index + 1)
                                .setCandidateId("client")
                                .setLastLogIndex(0)
                                .setLastLogTerm(0)
                                .build();
                        transport.sendVoteRequest("target", request)
                                .toCompletionStage().toCompletableFuture()
                                .get(10, TimeUnit.SECONDS);
                    } else {
                        AppendEntriesRequest request = AppendEntriesRequest.newBuilder()
                                .setTerm(1)
                                .setLeaderId("client")
                                .setPrevLogIndex(0)
                                .setPrevLogTerm(0)
                                .setLeaderCommit(0)
                                .build();
                        transport.sendAppendEntries("target", request)
                                .toCompletionStage().toCompletableFuture()
                                .get(10, TimeUnit.SECONDS);
                    }
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    errorCount.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }
        
        assertTrue(latch.await(60, TimeUnit.SECONDS));
        executor.shutdown();
        
        assertEquals(numRequests, successCount.get());
        assertEquals(0, errorCount.get());
        
        transport.stop();
    }

    // ========== MULTI-TARGET TESTS ==========

    @Test
    @DisplayName("Transport should handle requests to multiple targets")
    void testMultipleTargets() throws Exception {
        // Set up second target
        int targetPort2 = findAvailablePort();
        Set<String> cluster2 = Set.of("target2");
        InMemoryTransportSimulator transport2 = new InMemoryTransportSimulator("target2");
        QuorusStateMachine sm2 = new QuorusStateMachine();
        RaftNode node2 = new RaftNode(vertx, "target2", cluster2, transport2, sm2, 5000, 1000);
        node2.start();
        await().atMost(Duration.ofSeconds(5))
            .pollInterval(Duration.ofMillis(10))
            .until(() -> node2.isRunning());
        
        GrpcRaftServer server2 = new GrpcRaftServer(vertx, targetPort2, node2);
        server2.start().toCompletionStage().toCompletableFuture().get(5, TimeUnit.SECONDS);
        
        try {
            Map<String, String> cluster = new HashMap<>();
            cluster.put("target1", "localhost:" + targetPort);
            cluster.put("target2", "localhost:" + targetPort2);
            
            GrpcRaftTransport transport = new GrpcRaftTransport(vertx, "client", cluster);
            transport.start(msg -> {});
            
            VoteRequest request = VoteRequest.newBuilder()
                    .setTerm(1)
                    .setCandidateId("client")
                    .setLastLogIndex(0)
                    .setLastLogTerm(0)
                    .build();
            
            // Send to both targets
            VoteResponse response1 = transport.sendVoteRequest("target1", request)
                    .toCompletionStage().toCompletableFuture().get(10, TimeUnit.SECONDS);
            VoteResponse response2 = transport.sendVoteRequest("target2", request)
                    .toCompletionStage().toCompletableFuture().get(10, TimeUnit.SECONDS);
            
            assertNotNull(response1);
            assertNotNull(response2);
            
            transport.stop();
        } finally {
            server2.stop().toCompletionStage().toCompletableFuture().get(5, TimeUnit.SECONDS);
            node2.stop();
        }
    }

    // ========== REQUEST CONTENT TESTS ==========

    @Test
    @DisplayName("Transport should handle large append entries")
    void testLargeAppendEntries() throws Exception {
        Map<String, String> cluster = new HashMap<>();
        cluster.put("target", "localhost:" + targetPort);
        
        GrpcRaftTransport transport = new GrpcRaftTransport(vertx, "client", cluster);
        transport.start(msg -> {});
        
        // Create large entries
        byte[] largeData = new byte[100 * 1024]; // 100KB
        java.util.Arrays.fill(largeData, (byte) 'X');
        
        dev.mars.quorus.controller.raft.grpc.LogEntry entry = dev.mars.quorus.controller.raft.grpc.LogEntry.newBuilder()
                .setTerm(1)
                .setIndex(1)
                .setData(com.google.protobuf.ByteString.copyFrom(largeData))
                .build();
        
        AppendEntriesRequest request = AppendEntriesRequest.newBuilder()
                .setTerm(1)
                .setLeaderId("client")
                .setPrevLogIndex(0)
                .setPrevLogTerm(0)
                .setLeaderCommit(0)
                .addEntries(entry)
                .build();
        
        // Server may reject due to deserialization issues for non-Java-serialized data
        // The important thing is that the transport doesn't crash
        try {
            AppendEntriesResponse response = transport.sendAppendEntries("target", request)
                    .toCompletionStage().toCompletableFuture().get(10, TimeUnit.SECONDS);
            assertNotNull(response);
        } catch (ExecutionException e) {
            // Expected - server may fail to deserialize the large non-serialized entry
            // This is correct behavior - we're testing the transport handles this gracefully
        }
        
        transport.stop();
    }

    @Test
    @DisplayName("Transport should handle multiple log entries in single request")
    void testMultipleLogEntries() throws Exception {
        Map<String, String> cluster = new HashMap<>();
        cluster.put("target", "localhost:" + targetPort);
        
        GrpcRaftTransport transport = new GrpcRaftTransport(vertx, "client", cluster);
        transport.start(msg -> {});
        
        AppendEntriesRequest.Builder requestBuilder = AppendEntriesRequest.newBuilder()
                .setTerm(1)
                .setLeaderId("client")
                .setPrevLogIndex(0)
                .setPrevLogTerm(0)
                .setLeaderCommit(0);
        
        // Add 100 log entries
        for (int i = 0; i < 100; i++) {
            dev.mars.quorus.controller.raft.grpc.LogEntry entry = dev.mars.quorus.controller.raft.grpc.LogEntry.newBuilder()
                    .setTerm(1)
                    .setIndex(i + 1)
                    .setData(com.google.protobuf.ByteString.copyFromUtf8("command-" + i))
                    .build();
            requestBuilder.addEntries(entry);
        }
        
        // Server may reject due to deserialization issues for non-Java-serialized data
        // The important thing is that the transport doesn't crash
        try {
            AppendEntriesResponse response = transport.sendAppendEntries("target", requestBuilder.build())
                    .toCompletionStage().toCompletableFuture().get(10, TimeUnit.SECONDS);
            assertNotNull(response);
        } catch (ExecutionException e) {
            // Expected - server may fail to deserialize the non-serialized entries
            // This is correct behavior - we're testing the transport handles this gracefully
        }
        
        transport.stop();
    }

    // ========== TRANSPORT LIFECYCLE TESTS ==========

    @Test
    @DisplayName("Transport stop should be idempotent")
    void testTransportStopIdempotent() {
        Map<String, String> cluster = new HashMap<>();
        cluster.put("target", "localhost:" + targetPort);
        
        GrpcRaftTransport transport = new GrpcRaftTransport(vertx, "client", cluster);
        transport.start(msg -> {});
        
        // Stop multiple times should not throw
        assertDoesNotThrow(transport::stop);
        assertDoesNotThrow(transport::stop);
        assertDoesNotThrow(transport::stop);
    }

    @Test
    @DisplayName("Transport should work with new instance after old one stops")
    void testTransportRestart() throws Exception {
        Map<String, String> cluster = new HashMap<>();
        cluster.put("target", "localhost:" + targetPort);
        
        VoteRequest request = VoteRequest.newBuilder()
                .setTerm(1)
                .setCandidateId("client")
                .setLastLogIndex(0)
                .setLastLogTerm(0)
                .build();
        
        // First transport instance
        GrpcRaftTransport transport1 = new GrpcRaftTransport(vertx, "client", cluster);
        transport1.start(msg -> {});
        
        VoteResponse response1 = transport1.sendVoteRequest("target", request)
                .toCompletionStage().toCompletableFuture().get(10, TimeUnit.SECONDS);
        assertNotNull(response1);
        
        // Stop first transport (properly shuts down executor - T3.2 fix)
        transport1.stop();
        
        // Create new transport instance (proper lifecycle management)
        GrpcRaftTransport transport2 = new GrpcRaftTransport(vertx, "client", cluster);
        transport2.start(msg -> {});
        
        // Second use with new instance
        VoteResponse response2 = transport2.sendVoteRequest("target", request)
                .toCompletionStage().toCompletableFuture().get(10, TimeUnit.SECONDS);
        assertNotNull(response2);
        
        transport2.stop();
    }

    // ========== EDGE CASE REQUEST TESTS ==========

    @Test
    @DisplayName("Transport should handle request with empty candidate ID")
    void testEmptyCandidateId() throws Exception {
        Map<String, String> cluster = new HashMap<>();
        cluster.put("target", "localhost:" + targetPort);
        
        GrpcRaftTransport transport = new GrpcRaftTransport(vertx, "client", cluster);
        transport.start(msg -> {});
        
        VoteRequest request = VoteRequest.newBuilder()
                .setTerm(1)
                .setCandidateId("")
                .setLastLogIndex(0)
                .setLastLogTerm(0)
                .build();
        
        VoteResponse response = transport.sendVoteRequest("target", request)
                .toCompletionStage().toCompletableFuture().get(10, TimeUnit.SECONDS);
        
        assertNotNull(response);
        
        transport.stop();
    }

    @Test
    @DisplayName("Transport should handle request with max values")
    void testMaxValues() throws Exception {
        Map<String, String> cluster = new HashMap<>();
        cluster.put("target", "localhost:" + targetPort);
        
        GrpcRaftTransport transport = new GrpcRaftTransport(vertx, "client", cluster);
        transport.start(msg -> {});
        
        VoteRequest request = VoteRequest.newBuilder()
                .setTerm(Long.MAX_VALUE)
                .setCandidateId("client")
                .setLastLogIndex(Long.MAX_VALUE)
                .setLastLogTerm(Long.MAX_VALUE)
                .build();
        
        VoteResponse response = transport.sendVoteRequest("target", request)
                .toCompletionStage().toCompletableFuture().get(10, TimeUnit.SECONDS);
        
        assertNotNull(response);
        
        transport.stop();
    }

    @Test
    @DisplayName("Transport should handle request with zero values")
    void testZeroValues() throws Exception {
        Map<String, String> cluster = new HashMap<>();
        cluster.put("target", "localhost:" + targetPort);
        
        GrpcRaftTransport transport = new GrpcRaftTransport(vertx, "client", cluster);
        transport.start(msg -> {});
        
        VoteRequest request = VoteRequest.newBuilder()
                .setTerm(0)
                .setCandidateId("")
                .setLastLogIndex(0)
                .setLastLogTerm(0)
                .build();
        
        VoteResponse response = transport.sendVoteRequest("target", request)
                .toCompletionStage().toCompletableFuture().get(10, TimeUnit.SECONDS);
        
        assertNotNull(response);
        
        transport.stop();
    }

    // ========== T3.2 BOUNDED THREAD POOL TESTS ==========

    @Test
    @DisplayName("Transport should have configurable pool size")
    void testConfigurablePoolSize() {
        Map<String, String> cluster = new HashMap<>();
        cluster.put("target", "localhost:" + targetPort);
        
        // Test with custom pool size
        GrpcRaftTransport transport = new GrpcRaftTransport(vertx, "client", cluster, 5);
        assertEquals(5, transport.getPoolSize(), "Pool size should be configurable");
        transport.stop();
    }

    @Test
    @DisplayName("Transport should use default pool size of 10")
    void testDefaultPoolSize() {
        Map<String, String> cluster = new HashMap<>();
        cluster.put("target", "localhost:" + targetPort);
        
        GrpcRaftTransport transport = new GrpcRaftTransport(vertx, "client", cluster);
        assertEquals(10, transport.getPoolSize(), "Default pool size should be 10");
        transport.stop();
    }

    @Test
    @DisplayName("Transport should handle concurrent requests within pool limits")
    void testConcurrentRequestsWithinLimits() throws Exception {
        Map<String, String> cluster = new HashMap<>();
        cluster.put("target", "localhost:" + targetPort);
        
        GrpcRaftTransport transport = new GrpcRaftTransport(vertx, "client", cluster, 5);
        transport.start(msg -> {});
        
        VoteRequest request = VoteRequest.newBuilder()
                .setTerm(1)
                .setCandidateId("client")
                .setLastLogIndex(0)
                .setLastLogTerm(0)
                .build();
        
        // Send multiple concurrent requests (within pool limits)
        CompletableFuture<?>[] futures = new CompletableFuture[5];
        for (int i = 0; i < 5; i++) {
            futures[i] = transport.sendVoteRequest("target", request)
                    .toCompletionStage().toCompletableFuture();
        }
        
        // All should complete within timeout
        CompletableFuture.allOf(futures).get(30, TimeUnit.SECONDS);
        
        for (CompletableFuture<?> f : futures) {
            assertNotNull(f.get(), "All responses should be non-null");
        }
        
        transport.stop();
    }
}
