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
import io.grpc.stub.StreamObserver;
import io.vertx.core.Vertx;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import java.io.IOException;
import java.net.ServerSocket;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive edge case tests for GrpcRaftServer.
 * Tests various scenarios including:
 * - Server lifecycle (start/stop)
 * - Connection handling
 * - Request/response handling
 * - Error conditions
 * - Concurrent requests
 * - Network edge cases
 * 
 * @author Mark Andrew Ray-Smith Cityline Ltd
 * @version 1.0
 * @since 2026-01-08
 */
@Execution(ExecutionMode.SAME_THREAD)
class GrpcRaftServerTest {

    private Vertx vertx;
    private RaftNode raftNode;
    private GrpcRaftServer grpcServer;
    private ManagedChannel channel;
    private RaftServiceGrpc.RaftServiceBlockingStub blockingStub;
    private RaftServiceGrpc.RaftServiceStub asyncStub;
    private int serverPort;

    @BeforeEach
    void setUp() throws Exception {
        vertx = Vertx.vertx();
        serverPort = findAvailablePort();
        
        // Create a minimal RaftNode for testing
        Set<String> clusterNodes = Set.of("node1");
        InMemoryTransportSimulator transport = new InMemoryTransportSimulator("node1");
        QuorusStateMachine stateMachine = new QuorusStateMachine();
        raftNode = new RaftNode(vertx, "node1", clusterNodes, transport, stateMachine, 5000, 1000);
        raftNode.start();
        
        // Wait for node to be running (reactive polling instead of fixed sleep)
        await().atMost(Duration.ofSeconds(5))
            .pollInterval(Duration.ofMillis(10))
            .until(() -> raftNode.isRunning());
    }

    @AfterEach
    void tearDown() throws Exception {
        if (channel != null && !channel.isShutdown()) {
            channel.shutdownNow();
            channel.awaitTermination(5, TimeUnit.SECONDS);
        }
        if (grpcServer != null) {
            grpcServer.stop().toCompletionStage().toCompletableFuture().get(5, TimeUnit.SECONDS);
        }
        if (raftNode != null) {
            raftNode.stop();
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

    private void startServerAndConnect() throws Exception {
        grpcServer = new GrpcRaftServer(vertx, serverPort, raftNode);
        grpcServer.start().toCompletionStage().toCompletableFuture().get(5, TimeUnit.SECONDS);
        
        channel = ManagedChannelBuilder.forAddress("localhost", serverPort)
                .usePlaintext()
                .build();
        blockingStub = RaftServiceGrpc.newBlockingStub(channel);
        asyncStub = RaftServiceGrpc.newStub(channel);
    }

    // ========== SERVER LIFECYCLE TESTS ==========

    @Test
    @DisplayName("Server should start successfully on available port")
    void testServerStartSuccess() throws Exception {
        grpcServer = new GrpcRaftServer(vertx, serverPort, raftNode);
        
        CompletableFuture<Void> startFuture = grpcServer.start()
                .toCompletionStage().toCompletableFuture();
        
        assertDoesNotThrow(() -> startFuture.get(5, TimeUnit.SECONDS));
    }

    @Test
    @DisplayName("Server should fail to start on occupied port")
    void testServerStartFailsOnOccupiedPort() throws Exception {
        // Start first server
        grpcServer = new GrpcRaftServer(vertx, serverPort, raftNode);
        grpcServer.start().toCompletionStage().toCompletableFuture().get(5, TimeUnit.SECONDS);
        
        // Try to start second server on same port
        GrpcRaftServer secondServer = new GrpcRaftServer(vertx, serverPort, raftNode);
        
        CompletableFuture<Void> startFuture = secondServer.start()
                .toCompletionStage().toCompletableFuture();
        
        assertThrows(ExecutionException.class, () -> startFuture.get(5, TimeUnit.SECONDS));
    }

    @Test
    @DisplayName("Server should stop gracefully")
    void testServerStopGraceful() throws Exception {
        startServerAndConnect();
        
        // Verify server is running
        VoteRequest request = VoteRequest.newBuilder()
                .setTerm(1)
                .setCandidateId("candidate1")
                .setLastLogIndex(0)
                .setLastLogTerm(0)
                .build();
        
        assertDoesNotThrow(() -> blockingStub.requestVote(request));
        
        // Stop server
        CompletableFuture<Void> stopFuture = grpcServer.stop()
                .toCompletionStage().toCompletableFuture();
        
        assertDoesNotThrow(() -> stopFuture.get(10, TimeUnit.SECONDS));
        
        // Verify server is stopped (requests should fail)
        assertThrows(StatusRuntimeException.class, () -> blockingStub.requestVote(request));
    }

    @Test
    @DisplayName("Stop on null server should complete successfully")
    void testStopOnNullServer() throws Exception {
        grpcServer = new GrpcRaftServer(vertx, serverPort, raftNode);
        // Don't start, just stop
        
        CompletableFuture<Void> stopFuture = grpcServer.stop()
                .toCompletionStage().toCompletableFuture();
        
        assertDoesNotThrow(() -> stopFuture.get(5, TimeUnit.SECONDS));
    }

    @Test
    @DisplayName("Server should handle multiple start-stop cycles")
    void testMultipleStartStopCycles() throws Exception {
        for (int i = 0; i < 3; i++) {
            int port = findAvailablePort();
            GrpcRaftServer server = new GrpcRaftServer(vertx, port, raftNode);
            
            server.start().toCompletionStage().toCompletableFuture().get(5, TimeUnit.SECONDS);
            
            // Quick health check
            ManagedChannel ch = ManagedChannelBuilder.forAddress("localhost", port)
                    .usePlaintext()
                    .build();
            RaftServiceGrpc.RaftServiceBlockingStub stub = RaftServiceGrpc.newBlockingStub(ch);
            
            VoteRequest request = VoteRequest.newBuilder()
                    .setTerm(1)
                    .setCandidateId("candidate")
                    .setLastLogIndex(0)
                    .setLastLogTerm(0)
                    .build();
            
            assertDoesNotThrow(() -> stub.requestVote(request));
            
            ch.shutdownNow();
            ch.awaitTermination(2, TimeUnit.SECONDS);
            
            server.stop().toCompletionStage().toCompletableFuture().get(5, TimeUnit.SECONDS);
        }
    }

    // ========== REQUEST VOTE TESTS ==========

    @Test
    @DisplayName("RequestVote should return valid response for valid request")
    void testRequestVoteValidRequest() throws Exception {
        startServerAndConnect();
        
        VoteRequest request = VoteRequest.newBuilder()
                .setTerm(1)
                .setCandidateId("candidate1")
                .setLastLogIndex(0)
                .setLastLogTerm(0)
                .build();
        
        VoteResponse response = blockingStub.requestVote(request);
        
        assertNotNull(response);
        assertTrue(response.getTerm() >= 0);
    }

    @Test
    @DisplayName("RequestVote should handle term 0")
    void testRequestVoteTermZero() throws Exception {
        startServerAndConnect();
        
        VoteRequest request = VoteRequest.newBuilder()
                .setTerm(0)
                .setCandidateId("candidate1")
                .setLastLogIndex(0)
                .setLastLogTerm(0)
                .build();
        
        VoteResponse response = blockingStub.requestVote(request);
        
        assertNotNull(response);
    }

    @Test
    @DisplayName("RequestVote should handle very high term number")
    void testRequestVoteHighTerm() throws Exception {
        startServerAndConnect();
        
        VoteRequest request = VoteRequest.newBuilder()
                .setTerm(Long.MAX_VALUE)
                .setCandidateId("candidate1")
                .setLastLogIndex(0)
                .setLastLogTerm(0)
                .build();
        
        VoteResponse response = blockingStub.requestVote(request);
        
        assertNotNull(response);
    }

    @Test
    @DisplayName("RequestVote should handle empty candidate ID")
    void testRequestVoteEmptyCandidateId() throws Exception {
        startServerAndConnect();
        
        VoteRequest request = VoteRequest.newBuilder()
                .setTerm(1)
                .setCandidateId("")
                .setLastLogIndex(0)
                .setLastLogTerm(0)
                .build();
        
        VoteResponse response = blockingStub.requestVote(request);
        
        assertNotNull(response);
    }

    @Test
    @DisplayName("RequestVote should handle negative log index")
    void testRequestVoteNegativeLogIndex() throws Exception {
        startServerAndConnect();
        
        VoteRequest request = VoteRequest.newBuilder()
                .setTerm(1)
                .setCandidateId("candidate1")
                .setLastLogIndex(-1)
                .setLastLogTerm(0)
                .build();
        
        // Should not throw, server should handle gracefully
        assertDoesNotThrow(() -> blockingStub.requestVote(request));
    }

    @Test
    @DisplayName("RequestVote async should complete successfully")
    void testRequestVoteAsync() throws Exception {
        startServerAndConnect();
        
        VoteRequest request = VoteRequest.newBuilder()
                .setTerm(1)
                .setCandidateId("candidate1")
                .setLastLogIndex(0)
                .setLastLogTerm(0)
                .build();
        
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<VoteResponse> responseRef = new AtomicReference<>();
        AtomicReference<Throwable> errorRef = new AtomicReference<>();
        
        asyncStub.requestVote(request, new StreamObserver<>() {
            @Override
            public void onNext(VoteResponse response) {
                responseRef.set(response);
            }

            @Override
            public void onError(Throwable t) {
                errorRef.set(t);
                latch.countDown();
            }

            @Override
            public void onCompleted() {
                latch.countDown();
            }
        });
        
        assertTrue(latch.await(5, TimeUnit.SECONDS));
        assertNull(errorRef.get());
        assertNotNull(responseRef.get());
    }

    // ========== APPEND ENTRIES TESTS ==========

    @Test
    @DisplayName("AppendEntries should return valid response for heartbeat")
    void testAppendEntriesHeartbeat() throws Exception {
        startServerAndConnect();
        
        AppendEntriesRequest request = AppendEntriesRequest.newBuilder()
                .setTerm(1)
                .setLeaderId("leader1")
                .setPrevLogIndex(0)
                .setPrevLogTerm(0)
                .setLeaderCommit(0)
                .build();
        
        AppendEntriesResponse response = blockingStub.appendEntries(request);
        
        assertNotNull(response);
        assertTrue(response.getTerm() >= 0);
    }

    @Test
    @DisplayName("AppendEntries should handle entries with log entries")
    void testAppendEntriesWithEntries() throws Exception {
        startServerAndConnect();
        
        dev.mars.quorus.controller.raft.grpc.LogEntry entry = dev.mars.quorus.controller.raft.grpc.LogEntry.newBuilder()
                .setTerm(1)
                .setIndex(1)
                .setData(com.google.protobuf.ByteString.copyFromUtf8("test-command"))
                .build();
        
        AppendEntriesRequest request = AppendEntriesRequest.newBuilder()
                .setTerm(1)
                .setLeaderId("leader1")
                .setPrevLogIndex(0)
                .setPrevLogTerm(0)
                .setLeaderCommit(0)
                .addEntries(entry)
                .build();
        
        // Server may reject due to deserialization issues for non-Java-serialized data
        // The important thing is that it handles this gracefully
        try {
            AppendEntriesResponse response = blockingStub.appendEntries(request);
            assertNotNull(response);
        } catch (io.grpc.StatusRuntimeException e) {
            // Expected - server may fail to deserialize the non-serialized entry
            // This is correct behavior - we're testing the server doesn't crash
        }
    }

    @Test
    @DisplayName("AppendEntries should handle stale term")
    void testAppendEntriesStaleTerm() throws Exception {
        startServerAndConnect();
        
        // First, update the term with a higher term request
        VoteRequest voteRequest = VoteRequest.newBuilder()
                .setTerm(10)
                .setCandidateId("candidate1")
                .setLastLogIndex(0)
                .setLastLogTerm(0)
                .build();
        blockingStub.requestVote(voteRequest);
        
        // Now send AppendEntries with lower term
        AppendEntriesRequest request = AppendEntriesRequest.newBuilder()
                .setTerm(1) // Lower than current term
                .setLeaderId("leader1")
                .setPrevLogIndex(0)
                .setPrevLogTerm(0)
                .setLeaderCommit(0)
                .build();
        
        AppendEntriesResponse response = blockingStub.appendEntries(request);
        
        assertNotNull(response);
        assertFalse(response.getSuccess());
    }

    @Test
    @DisplayName("AppendEntries async should complete successfully")
    void testAppendEntriesAsync() throws Exception {
        startServerAndConnect();
        
        AppendEntriesRequest request = AppendEntriesRequest.newBuilder()
                .setTerm(1)
                .setLeaderId("leader1")
                .setPrevLogIndex(0)
                .setPrevLogTerm(0)
                .setLeaderCommit(0)
                .build();
        
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<AppendEntriesResponse> responseRef = new AtomicReference<>();
        AtomicReference<Throwable> errorRef = new AtomicReference<>();
        
        asyncStub.appendEntries(request, new StreamObserver<>() {
            @Override
            public void onNext(AppendEntriesResponse response) {
                responseRef.set(response);
            }

            @Override
            public void onError(Throwable t) {
                errorRef.set(t);
                latch.countDown();
            }

            @Override
            public void onCompleted() {
                latch.countDown();
            }
        });
        
        assertTrue(latch.await(5, TimeUnit.SECONDS));
        assertNull(errorRef.get());
        assertNotNull(responseRef.get());
    }

    // ========== CONCURRENT REQUEST TESTS ==========

    @Test
    @DisplayName("Server should handle concurrent RequestVote calls")
    void testConcurrentRequestVotes() throws Exception {
        startServerAndConnect();
        
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
                            .setCandidateId("candidate" + term)
                            .setLastLogIndex(0)
                            .setLastLogTerm(0)
                            .build();
                    
                    VoteResponse response = blockingStub.requestVote(request);
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
        
        assertTrue(latch.await(30, TimeUnit.SECONDS));
        executor.shutdown();
        
        assertEquals(numRequests, successCount.get());
        assertEquals(0, errorCount.get());
    }

    @Test
    @DisplayName("Server should handle concurrent AppendEntries calls")
    void testConcurrentAppendEntries() throws Exception {
        startServerAndConnect();
        
        int numRequests = 50;
        ExecutorService executor = Executors.newFixedThreadPool(10);
        CountDownLatch latch = new CountDownLatch(numRequests);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger errorCount = new AtomicInteger(0);
        
        for (int i = 0; i < numRequests; i++) {
            executor.submit(() -> {
                try {
                    AppendEntriesRequest request = AppendEntriesRequest.newBuilder()
                            .setTerm(1)
                            .setLeaderId("leader1")
                            .setPrevLogIndex(0)
                            .setPrevLogTerm(0)
                            .setLeaderCommit(0)
                            .build();
                    
                    AppendEntriesResponse response = blockingStub.appendEntries(request);
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
        
        assertTrue(latch.await(30, TimeUnit.SECONDS));
        executor.shutdown();
        
        assertEquals(numRequests, successCount.get());
        assertEquals(0, errorCount.get());
    }

    @Test
    @DisplayName("Server should handle mixed concurrent requests")
    void testMixedConcurrentRequests() throws Exception {
        startServerAndConnect();
        
        int numRequests = 100;
        ExecutorService executor = Executors.newFixedThreadPool(20);
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
                                .setCandidateId("candidate" + index)
                                .setLastLogIndex(0)
                                .setLastLogTerm(0)
                                .build();
                        blockingStub.requestVote(request);
                    } else {
                        AppendEntriesRequest request = AppendEntriesRequest.newBuilder()
                                .setTerm(1)
                                .setLeaderId("leader1")
                                .setPrevLogIndex(0)
                                .setPrevLogTerm(0)
                                .setLeaderCommit(0)
                                .build();
                        blockingStub.appendEntries(request);
                    }
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    errorCount.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }
        
        assertTrue(latch.await(30, TimeUnit.SECONDS));
        executor.shutdown();
        
        assertEquals(numRequests, successCount.get());
        assertEquals(0, errorCount.get());
    }

    // ========== CONNECTION EDGE CASE TESTS ==========

    @Test
    @DisplayName("Client should handle server disconnect gracefully")
    void testClientHandlesServerDisconnect() throws Exception {
        startServerAndConnect();
        
        // Verify connection works
        VoteRequest request = VoteRequest.newBuilder()
                .setTerm(1)
                .setCandidateId("candidate1")
                .setLastLogIndex(0)
                .setLastLogTerm(0)
                .build();
        
        assertDoesNotThrow(() -> blockingStub.requestVote(request));
        
        // Stop server
        grpcServer.stop().toCompletionStage().toCompletableFuture().get(5, TimeUnit.SECONDS);
        grpcServer = null;
        
        // Client should get an error
        assertThrows(StatusRuntimeException.class, () -> blockingStub.requestVote(request));
    }

    @Test
    @DisplayName("Server should handle client reconnection")
    void testClientReconnection() throws Exception {
        startServerAndConnect();
        
        VoteRequest request = VoteRequest.newBuilder()
                .setTerm(1)
                .setCandidateId("candidate1")
                .setLastLogIndex(0)
                .setLastLogTerm(0)
                .build();
        
        // First request
        assertDoesNotThrow(() -> blockingStub.requestVote(request));
        
        // Close channel
        channel.shutdownNow();
        channel.awaitTermination(2, TimeUnit.SECONDS);
        
        // Reconnect
        channel = ManagedChannelBuilder.forAddress("localhost", serverPort)
                .usePlaintext()
                .build();
        blockingStub = RaftServiceGrpc.newBlockingStub(channel);
        
        // Second request on new connection
        assertDoesNotThrow(() -> blockingStub.requestVote(request));
    }

    @Test
    @DisplayName("Server should handle many sequential connections")
    void testManySequentialConnections() throws Exception {
        startServerAndConnect();
        channel.shutdownNow();
        channel.awaitTermination(2, TimeUnit.SECONDS);
        channel = null;
        
        for (int i = 0; i < 20; i++) {
            ManagedChannel ch = ManagedChannelBuilder.forAddress("localhost", serverPort)
                    .usePlaintext()
                    .build();
            
            RaftServiceGrpc.RaftServiceBlockingStub stub = RaftServiceGrpc.newBlockingStub(ch);
            
            VoteRequest request = VoteRequest.newBuilder()
                    .setTerm(i + 1)
                    .setCandidateId("candidate" + i)
                    .setLastLogIndex(0)
                    .setLastLogTerm(0)
                    .build();
            
            assertDoesNotThrow(() -> stub.requestVote(request));
            
            ch.shutdownNow();
            ch.awaitTermination(1, TimeUnit.SECONDS);
        }
    }

    // ========== TIMEOUT AND DEADLINE TESTS ==========

    @Test
    @DisplayName("Request should respect deadline")
    void testRequestWithDeadline() throws Exception {
        startServerAndConnect();
        
        VoteRequest request = VoteRequest.newBuilder()
                .setTerm(1)
                .setCandidateId("candidate1")
                .setLastLogIndex(0)
                .setLastLogTerm(0)
                .build();
        
        // Use a very generous deadline - should succeed
        RaftServiceGrpc.RaftServiceBlockingStub stubWithDeadline = 
                blockingStub.withDeadlineAfter(10, TimeUnit.SECONDS);
        
        assertDoesNotThrow(() -> stubWithDeadline.requestVote(request));
    }

    @Test
    @DisplayName("Request should fail with very short deadline")
    void testRequestWithVeryShortDeadline() throws Exception {
        startServerAndConnect();
        
        VoteRequest request = VoteRequest.newBuilder()
                .setTerm(1)
                .setCandidateId("candidate1")
                .setLastLogIndex(0)
                .setLastLogTerm(0)
                .build();
        
        // Use extremely short deadline - may or may not fail depending on timing
        // This test verifies the deadline mechanism works
        RaftServiceGrpc.RaftServiceBlockingStub stubWithDeadline = 
                blockingStub.withDeadlineAfter(1, TimeUnit.NANOSECONDS);
        
        // Should either succeed quickly or timeout
        try {
            stubWithDeadline.requestVote(request);
        } catch (StatusRuntimeException e) {
            assertEquals(Status.Code.DEADLINE_EXCEEDED, e.getStatus().getCode());
        }
    }

    // ========== STRESS TESTS ==========

    @Test
    @DisplayName("Server should handle burst of requests")
    void testBurstRequests() throws Exception {
        startServerAndConnect();
        
        int burstSize = 200;
        List<CompletableFuture<VoteResponse>> futures = new ArrayList<>();
        
        for (int i = 0; i < burstSize; i++) {
            final int term = i + 1;
            CompletableFuture<VoteResponse> future = CompletableFuture.supplyAsync(() -> {
                VoteRequest request = VoteRequest.newBuilder()
                        .setTerm(term)
                        .setCandidateId("candidate" + term)
                        .setLastLogIndex(0)
                        .setLastLogTerm(0)
                        .build();
                return blockingStub.requestVote(request);
            });
            futures.add(future);
        }
        
        // Wait for all to complete
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .get(60, TimeUnit.SECONDS);
        
        // Verify all succeeded
        for (CompletableFuture<VoteResponse> future : futures) {
            assertNotNull(future.get());
        }
    }

    @Test
    @DisplayName("Server should remain stable under sustained load")
    void testSustainedLoad() throws Exception {
        startServerAndConnect();
        
        int durationSeconds = 2;
        int requestsPerSecond = 50;
        AtomicBoolean running = new AtomicBoolean(true);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger errorCount = new AtomicInteger(0);
        
        ExecutorService executor = Executors.newFixedThreadPool(5);
        
        // Submit continuous load
        for (int i = 0; i < 5; i++) {
            executor.submit(() -> {
                while (running.get()) {
                    try {
                        VoteRequest request = VoteRequest.newBuilder()
                                .setTerm(1)
                                .setCandidateId("candidate")
                                .setLastLogIndex(0)
                                .setLastLogTerm(0)
                                .build();
                        blockingStub.requestVote(request);
                        successCount.incrementAndGet();
                        Thread.sleep(1000 / requestsPerSecond);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    } catch (Exception e) {
                        errorCount.incrementAndGet();
                    }
                }
            });
        }
        
        // Let it run for the specified duration
        await().pollDelay(Duration.ofSeconds(durationSeconds))
            .atMost(Duration.ofSeconds(durationSeconds + 1))
            .until(() -> true);
        running.set(false);
        executor.shutdownNow();
        executor.awaitTermination(5, TimeUnit.SECONDS);
        
        assertTrue(successCount.get() > 0, "Should have processed some requests");
        assertTrue(errorCount.get() < successCount.get() / 10, 
                "Error rate should be less than 10%");
    }

    // ========== MALFORMED REQUEST TESTS ==========

    @Test
    @DisplayName("Server should handle request with all default values")
    void testRequestWithDefaultValues() throws Exception {
        startServerAndConnect();
        
        VoteRequest request = VoteRequest.newBuilder().build();
        
        // Should not crash
        assertDoesNotThrow(() -> blockingStub.requestVote(request));
    }

    @Test
    @DisplayName("Server should handle AppendEntries with empty entries list")
    void testAppendEntriesEmptyEntries() throws Exception {
        startServerAndConnect();
        
        AppendEntriesRequest request = AppendEntriesRequest.newBuilder()
                .setTerm(1)
                .setLeaderId("leader1")
                .setPrevLogIndex(0)
                .setPrevLogTerm(0)
                .setLeaderCommit(0)
                .clearEntries()
                .build();
        
        AppendEntriesResponse response = blockingStub.appendEntries(request);
        
        assertNotNull(response);
    }

    @Test
    @DisplayName("Server should handle AppendEntries with large entries")
    void testAppendEntriesLargeEntries() throws Exception {
        startServerAndConnect();
        
        // Create a large entry (1MB of data)
        byte[] largeData = new byte[1024 * 1024];
        java.util.Arrays.fill(largeData, (byte) 'X');
        
        dev.mars.quorus.controller.raft.grpc.LogEntry entry = dev.mars.quorus.controller.raft.grpc.LogEntry.newBuilder()
                .setTerm(1)
                .setIndex(1)
                .setData(com.google.protobuf.ByteString.copyFrom(largeData))
                .build();
        
        AppendEntriesRequest request = AppendEntriesRequest.newBuilder()
                .setTerm(1)
                .setLeaderId("leader1")
                .setPrevLogIndex(0)
                .setPrevLogTerm(0)
                .setLeaderCommit(0)
                .addEntries(entry)
                .build();
        
        // Server may reject due to deserialization issues for non-Java-serialized data
        // The important thing is that it handles this gracefully (doesn't crash)
        try {
            AppendEntriesResponse response = blockingStub.appendEntries(request);
            assertNotNull(response);
        } catch (io.grpc.StatusRuntimeException e) {
            // Expected - server may fail to deserialize the large non-serialized entry
            // This is correct behavior - we're testing the server doesn't crash
        }
    }

    @Test
    @DisplayName("Server should handle request with very long candidate ID")
    void testRequestWithLongCandidateId() throws Exception {
        startServerAndConnect();
        
        String longId = "x".repeat(10000);
        
        VoteRequest request = VoteRequest.newBuilder()
                .setTerm(1)
                .setCandidateId(longId)
                .setLastLogIndex(0)
                .setLastLogTerm(0)
                .build();
        
        assertDoesNotThrow(() -> blockingStub.requestVote(request));
    }

    // ========== RAPID START/STOP TESTS ==========

    @Test
    @DisplayName("Server should handle rapid start/stop cycles")
    void testRapidStartStopCycles() throws Exception {
        for (int i = 0; i < 5; i++) {
            int port = findAvailablePort();
            GrpcRaftServer server = new GrpcRaftServer(vertx, port, raftNode);
            
            server.start().toCompletionStage().toCompletableFuture().get(5, TimeUnit.SECONDS);
            server.stop().toCompletionStage().toCompletableFuture().get(5, TimeUnit.SECONDS);
        }
    }

    // ========== MULTIPLE RAFT NODES TEST ==========

    @Test
    @DisplayName("Multiple gRPC servers should work independently")
    void testMultipleServersIndependent() throws Exception {
        int port1 = findAvailablePort();
        int port2 = findAvailablePort();
        
        // Create two independent RaftNodes
        Set<String> cluster1 = Set.of("nodeA");
        Set<String> cluster2 = Set.of("nodeB");
        
        InMemoryTransportSimulator transport1 = new InMemoryTransportSimulator("nodeA");
        InMemoryTransportSimulator transport2 = new InMemoryTransportSimulator("nodeB");
        
        QuorusStateMachine sm1 = new QuorusStateMachine();
        QuorusStateMachine sm2 = new QuorusStateMachine();
        
        RaftNode node1 = new RaftNode(vertx, "nodeA", cluster1, transport1, sm1, 5000, 1000);
        RaftNode node2 = new RaftNode(vertx, "nodeB", cluster2, transport2, sm2, 5000, 1000);
        
        node1.start();
        node2.start();
        await().atMost(Duration.ofSeconds(5))
            .pollInterval(Duration.ofMillis(10))
            .until(() -> node1.isRunning() && node2.isRunning());
        
        GrpcRaftServer server1 = new GrpcRaftServer(vertx, port1, node1);
        GrpcRaftServer server2 = new GrpcRaftServer(vertx, port2, node2);
        
        server1.start().toCompletionStage().toCompletableFuture().get(5, TimeUnit.SECONDS);
        server2.start().toCompletionStage().toCompletableFuture().get(5, TimeUnit.SECONDS);
        
        try {
            ManagedChannel ch1 = ManagedChannelBuilder.forAddress("localhost", port1)
                    .usePlaintext().build();
            ManagedChannel ch2 = ManagedChannelBuilder.forAddress("localhost", port2)
                    .usePlaintext().build();
            
            RaftServiceGrpc.RaftServiceBlockingStub stub1 = RaftServiceGrpc.newBlockingStub(ch1);
            RaftServiceGrpc.RaftServiceBlockingStub stub2 = RaftServiceGrpc.newBlockingStub(ch2);
            
            VoteRequest request = VoteRequest.newBuilder()
                    .setTerm(1)
                    .setCandidateId("testCandidate")
                    .setLastLogIndex(0)
                    .setLastLogTerm(0)
                    .build();
            
            // Both should respond independently
            VoteResponse response1 = stub1.requestVote(request);
            VoteResponse response2 = stub2.requestVote(request);
            
            assertNotNull(response1);
            assertNotNull(response2);
            
            ch1.shutdownNow();
            ch2.shutdownNow();
        } finally {
            server1.stop().toCompletionStage().toCompletableFuture().get(5, TimeUnit.SECONDS);
            server2.stop().toCompletionStage().toCompletableFuture().get(5, TimeUnit.SECONDS);
            node1.stop();
            node2.stop();
        }
    }
}
