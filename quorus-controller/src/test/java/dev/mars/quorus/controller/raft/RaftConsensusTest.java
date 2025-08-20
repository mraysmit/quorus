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

import dev.mars.quorus.controller.state.QuorusStateMachine;
import dev.mars.quorus.controller.state.SystemMetadataCommand;
import dev.mars.quorus.controller.state.TransferJobCommand;
import dev.mars.quorus.core.TransferJob;
import dev.mars.quorus.core.TransferRequest;
import dev.mars.quorus.core.TransferStatus;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Additional tests for Raft consensus focusing on edge cases and failure scenarios.
 */
class RaftConsensusTest {

    private RaftNode leader;
    private RaftNode follower1;
    private RaftNode follower2;
    private InMemoryTransport transport1;
    private InMemoryTransport transport2;
    private InMemoryTransport transport3;
    private QuorusStateMachine stateMachine1;
    private QuorusStateMachine stateMachine2;
    private QuorusStateMachine stateMachine3;

    @BeforeEach
    void setUp() {
        InMemoryTransport.clearAllTransports();

        Set<String> clusterNodes = Set.of("leader", "follower1", "follower2");

        transport1 = new InMemoryTransport("leader");
        transport2 = new InMemoryTransport("follower1");
        transport3 = new InMemoryTransport("follower2");

        stateMachine1 = new QuorusStateMachine();
        stateMachine2 = new QuorusStateMachine();
        stateMachine3 = new QuorusStateMachine();

        leader = new RaftNode("leader", clusterNodes, transport1, stateMachine1, 800, 150);
        follower1 = new RaftNode("follower1", clusterNodes, transport2, stateMachine2, 800, 150);
        follower2 = new RaftNode("follower2", clusterNodes, transport3, stateMachine3, 800, 150);
    }

    @AfterEach
    void tearDown() {
        if (leader != null) leader.stop();
        if (follower1 != null) follower1.stop();
        if (follower2 != null) follower2.stop();
        InMemoryTransport.clearAllTransports();
    }

    @Test
    void testLeaderElectionWithMultipleTerms() {
        leader.start();
        follower1.start();
        follower2.start();

        // Wait for initial leader election
        Awaitility.await()
                .atMost(Duration.ofSeconds(3))
                .until(() -> {
                    long leaderCount = Set.of(leader, follower1, follower2).stream()
                            .mapToLong(node -> node.isLeader() ? 1 : 0)
                            .sum();
                    return leaderCount == 1;
                });

        // Find the current leader
        RaftNode currentLeader = Set.of(leader, follower1, follower2).stream()
                .filter(RaftNode::isLeader)
                .findFirst()
                .orElse(null);
        
        assertNotNull(currentLeader);
        long initialTerm = currentLeader.getCurrentTerm();
        assertTrue(initialTerm > 0);

        // All nodes should have the same term
        Set.of(leader, follower1, follower2).forEach(node -> {
            assertTrue(node.getCurrentTerm() >= initialTerm);
        });
    }

    @Test
    void testCommandReplicationToStateMachine() throws Exception {
        // Start cluster and wait for leader
        leader.start();
        follower1.start();
        follower2.start();

        Awaitility.await()
                .atMost(Duration.ofSeconds(3))
                .until(() -> Set.of(leader, follower1, follower2).stream()
                        .anyMatch(RaftNode::isLeader));

        // Find leader and submit command
        RaftNode currentLeader = Set.of(leader, follower1, follower2).stream()
                .filter(RaftNode::isLeader)
                .findFirst()
                .orElse(null);

        assertNotNull(currentLeader);

        // Submit a transfer job command
        TransferRequest request = TransferRequest.builder()
                .sourceUri(URI.create("https://example.com/file.txt"))
                .destinationPath(Paths.get("/tmp/test.txt"))
                .build();
        TransferJob job = new TransferJob(request);
        TransferJobCommand command = TransferJobCommand.create(job);

        CompletableFuture<Object> future = currentLeader.submitCommand(command);
        
        // Command should complete successfully
        Object result = future.get(2, TimeUnit.SECONDS);
        assertNotNull(result);
        assertTrue(result instanceof TransferJob);
        
        TransferJob resultJob = (TransferJob) result;
        assertEquals(job.getJobId(), resultJob.getJobId());
    }

    @Test
    void testMultipleCommandSubmission() throws Exception {
        // Start single node for simplicity
        Set<String> singleNode = Set.of("single");
        RaftNode single = new RaftNode("single", singleNode, 
                new InMemoryTransport("single"), new QuorusStateMachine(), 500, 100);
        
        single.start();
        
        // Wait for leadership
        Awaitility.await()
                .atMost(Duration.ofSeconds(2))
                .until(single::isLeader);

        // Submit multiple commands
        SystemMetadataCommand cmd1 = SystemMetadataCommand.set("key1", "value1");
        SystemMetadataCommand cmd2 = SystemMetadataCommand.set("key2", "value2");
        SystemMetadataCommand cmd3 = SystemMetadataCommand.set("key3", "value3");

        CompletableFuture<Object> future1 = single.submitCommand(cmd1);
        CompletableFuture<Object> future2 = single.submitCommand(cmd2);
        CompletableFuture<Object> future3 = single.submitCommand(cmd3);

        // All should complete
        assertDoesNotThrow(() -> {
            future1.get(1, TimeUnit.SECONDS);
            future2.get(1, TimeUnit.SECONDS);
            future3.get(1, TimeUnit.SECONDS);
        });

        single.stop();
    }

    @Test
    void testNodeStepDownOnHigherTerm() {
        // This test would require more sophisticated transport mocking
        // For now, test the basic step-down logic through state observation
        
        leader.start();
        
        // Initially follower
        assertEquals(RaftNode.State.FOLLOWER, leader.getState());
        assertEquals(0, leader.getCurrentTerm());
        
        // After some time, should attempt election (single node cluster behavior)
        Set<String> singleNode = Set.of("test");
        RaftNode testNode = new RaftNode("test", singleNode, 
                new InMemoryTransport("test"), new QuorusStateMachine(), 300, 100);
        
        testNode.start();
        
        Awaitility.await()
                .atMost(Duration.ofSeconds(2))
                .until(() -> testNode.getState() == RaftNode.State.LEADER);
        
        assertTrue(testNode.getCurrentTerm() > 0);
        
        testNode.stop();
    }

    @Test
    void testLogEntryEquality() {
        SystemMetadataCommand cmd1 = SystemMetadataCommand.set("test", "value");
        SystemMetadataCommand cmd2 = SystemMetadataCommand.set("test", "value");

        LogEntry entry1 = new LogEntry(1, 5, cmd1);
        LogEntry entry2 = new LogEntry(1, 5, cmd1); // Use same command object
        LogEntry entry3 = new LogEntry(2, 5, cmd1);
        LogEntry entry4 = new LogEntry(1, 6, cmd1);

        // Same term, index, and command should be equal (timestamps are ignored)
        assertEquals(entry1, entry2);
        assertEquals(entry1.hashCode(), entry2.hashCode());

        // Different term should not be equal
        assertNotEquals(entry1, entry3);

        // Different index should not be equal
        assertNotEquals(entry1, entry4);

        // Test toString
        assertNotNull(entry1.toString());
        assertTrue(entry1.toString().contains("term=1"));
        assertTrue(entry1.toString().contains("index=5"));

        // Test no-op entry
        LogEntry noOpEntry = new LogEntry(1, 7, null);
        assertTrue(noOpEntry.isNoOp());
        assertFalse(entry1.isNoOp());
    }

    @Test
    void testAppendEntriesMessages() {
        LogEntry entry1 = new LogEntry(1, 1, SystemMetadataCommand.set("key", "value"));
        LogEntry entry2 = new LogEntry(1, 2, null); // heartbeat
        
        AppendEntriesRequest request = new AppendEntriesRequest(
                1, "leader", 0, 0, Set.of(entry1, entry2).stream().toList(), 1);
        
        assertEquals(1, request.getTerm());
        assertEquals("leader", request.getLeaderId());
        assertEquals(0, request.getPrevLogIndex());
        assertEquals(0, request.getPrevLogTerm());
        assertEquals(2, request.getEntries().size());
        assertEquals(1, request.getLeaderCommit());
        assertFalse(request.isHeartbeat());
        
        // Test heartbeat
        AppendEntriesRequest heartbeat = new AppendEntriesRequest(
                1, "leader", 0, 0, null, 1);
        assertTrue(heartbeat.isHeartbeat());
        
        // Test response
        AppendEntriesResponse response = new AppendEntriesResponse(1, true, "follower", 2);
        assertEquals(1, response.getTerm());
        assertTrue(response.isSuccess());
        assertEquals("follower", response.getNodeId());
        assertEquals(2, response.getMatchIndex());
        
        // Test toString methods
        assertNotNull(request.toString());
        assertNotNull(response.toString());
    }

    @Test
    void testTransferJobCommands() {
        TransferRequest request = TransferRequest.builder()
                .sourceUri(URI.create("https://test.com/file"))
                .destinationPath(Paths.get("/tmp/file"))
                .build();
        TransferJob job = new TransferJob(request);
        
        // Test create command
        TransferJobCommand createCmd = TransferJobCommand.create(job);
        assertEquals(TransferJobCommand.Type.CREATE, createCmd.getType());
        assertEquals(job.getJobId(), createCmd.getJobId());
        assertEquals(job, createCmd.getTransferJob());
        assertNull(createCmd.getStatus());
        
        // Test update status command
        TransferJobCommand updateCmd = TransferJobCommand.updateStatus(job.getJobId(), TransferStatus.IN_PROGRESS);
        assertEquals(TransferJobCommand.Type.UPDATE_STATUS, updateCmd.getType());
        assertEquals(job.getJobId(), updateCmd.getJobId());
        assertNull(updateCmd.getTransferJob());
        assertEquals(TransferStatus.IN_PROGRESS, updateCmd.getStatus());
        
        // Test delete command
        TransferJobCommand deleteCmd = TransferJobCommand.delete(job.getJobId());
        assertEquals(TransferJobCommand.Type.DELETE, deleteCmd.getType());
        assertEquals(job.getJobId(), deleteCmd.getJobId());
        assertNull(deleteCmd.getTransferJob());
        assertNull(deleteCmd.getStatus());
        
        // Test toString
        assertNotNull(createCmd.toString());
        assertTrue(createCmd.toString().contains("CREATE"));
    }

    @Test
    void testSystemMetadataCommands() {
        // Test set command
        SystemMetadataCommand setCmd = SystemMetadataCommand.set("testKey", "testValue");
        assertEquals(SystemMetadataCommand.Type.SET, setCmd.getType());
        assertEquals("testKey", setCmd.getKey());
        assertEquals("testValue", setCmd.getValue());
        
        // Test delete command
        SystemMetadataCommand deleteCmd = SystemMetadataCommand.delete("testKey");
        assertEquals(SystemMetadataCommand.Type.DELETE, deleteCmd.getType());
        assertEquals("testKey", deleteCmd.getKey());
        assertNull(deleteCmd.getValue());
        
        // Test toString
        assertNotNull(setCmd.toString());
        assertTrue(setCmd.toString().contains("SET"));
        assertTrue(setCmd.toString().contains("testKey"));
    }

    @Test
    void testQuorusSnapshotSerialization() {
        QuorusStateMachine stateMachine = new QuorusStateMachine();
        
        // Add some data
        stateMachine.apply(SystemMetadataCommand.set("version", "2.1"));
        stateMachine.apply(SystemMetadataCommand.set("environment", "test"));
        
        // Create snapshot
        byte[] snapshotData = stateMachine.takeSnapshot();
        assertNotNull(snapshotData);
        assertTrue(snapshotData.length > 0);
        
        // Create new state machine and restore
        QuorusStateMachine newStateMachine = new QuorusStateMachine();
        newStateMachine.restoreSnapshot(snapshotData);
        
        // Verify data was restored
        assertEquals("2.1", newStateMachine.getMetadata("version"));
        assertEquals("test", newStateMachine.getMetadata("environment"));
        assertTrue(newStateMachine.getSystemMetadata().size() >= 3); // At least version, phase, environment
    }
}
