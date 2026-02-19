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

package dev.mars.quorus.controller.state;

import dev.mars.quorus.core.TransferJob;
import dev.mars.quorus.core.TransferRequest;
import dev.mars.quorus.core.TransferStatus;
import dev.mars.quorus.core.JobAssignment;
import dev.mars.quorus.core.JobPriority;
import dev.mars.quorus.core.QueuedJob;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.nio.file.Paths;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive tests for QuorusStateMachine.
 * 
 * @author Mark Andrew Ray-Smith Cityline Ltd
 * @version 1.0
 * @since 2025-08-20
 */
class QuorusStateMachineTest {

    private QuorusStateMachine stateMachine;

    @BeforeEach
    void setUp() {
        stateMachine = new QuorusStateMachine();
    }

    @Test
    void testInitialState() {
        assertEquals(0, stateMachine.getLastAppliedIndex());
        assertEquals(0, stateMachine.getTransferJobCount());

        Map<String, String> metadata = stateMachine.getSystemMetadata();
        assertEquals("2.0", metadata.get("version"));

    }

    @Test
    void testTransferJobOperations() throws Exception {
        // Create transfer job
        TransferRequest request = TransferRequest.builder()
                .sourceUri(URI.create("https://example.com/test.txt"))
                .destinationPath(Paths.get("/tmp/test.txt"))
                .build();
        TransferJob job = new TransferJob(request);

        // Test CREATE command
        TransferJobCommand createCmd = TransferJobCommand.create(job);
        Object result = stateMachine.apply(createCmd);

        assertNotNull(result);
        assertTrue(result instanceof TransferJob);
        assertEquals(job.getJobId(), ((TransferJob) result).getJobId());
        assertEquals(1, stateMachine.getTransferJobCount());

        // Verify job is stored
        TransferJobSnapshot storedJob = stateMachine.getTransferJob(job.getJobId());
        assertNotNull(storedJob);
        assertEquals(job.getJobId(), storedJob.getJobId());

        // Test UPDATE_STATUS command
        TransferJobCommand updateCmd = TransferJobCommand.updateStatus(job.getJobId(), TransferStatus.IN_PROGRESS);
        Object updateResult = stateMachine.apply(updateCmd);
        assertNotNull(updateResult);

        // Test DELETE command
        TransferJobCommand deleteCmd = TransferJobCommand.delete(job.getJobId());
        Object deleteResult = stateMachine.apply(deleteCmd);
        assertNotNull(deleteResult);
        assertEquals(0, stateMachine.getTransferJobCount());

        // Verify job is removed
        assertNull(stateMachine.getTransferJob(job.getJobId()));
    }

    @Test
    void testTransferJobNotFound() {
        // Test update non-existent job
        TransferJobCommand updateCmd = TransferJobCommand.updateStatus("non-existent", TransferStatus.IN_PROGRESS);
        Object result = stateMachine.apply(updateCmd);
        assertNull(result);

        // Test delete non-existent job
        TransferJobCommand deleteCmd = TransferJobCommand.delete("non-existent");
        Object deleteResult = stateMachine.apply(deleteCmd);
        assertNull(deleteResult);
    }

    @Test
    void testSystemMetadataOperations() {
        // Test SET command
        SystemMetadataCommand setCmd = SystemMetadataCommand.set("testKey", "testValue");
        Object result = stateMachine.apply(setCmd);

        // Should return previous value (null for new key)
        assertNull(result);
        assertEquals("testValue", stateMachine.getMetadata("testKey"));

        // Test SET command with existing key
        SystemMetadataCommand updateCmd = SystemMetadataCommand.set("testKey", "newValue");
        Object updateResult = stateMachine.apply(updateCmd);

        // Should return previous value
        assertEquals("testValue", updateResult);
        assertEquals("newValue", stateMachine.getMetadata("testKey"));

        // Test DELETE command
        SystemMetadataCommand deleteCmd = SystemMetadataCommand.delete("testKey");
        Object deleteResult = stateMachine.apply(deleteCmd);

        // Should return deleted value
        assertEquals("newValue", deleteResult);
        assertNull(stateMachine.getMetadata("testKey"));

        // Test DELETE non-existent key
        SystemMetadataCommand deleteNonExistentCmd = SystemMetadataCommand.delete("nonExistent");
        Object deleteNonExistentResult = stateMachine.apply(deleteNonExistentCmd);
        assertNull(deleteNonExistentResult);
    }

    @Test
    void testNoOpCommand() {
        // Test null command (no-op)
        Object result = stateMachine.apply(null);
        assertNull(result);

        // State should be unchanged
        assertEquals(0, stateMachine.getTransferJobCount());
        assertEquals("2.0", stateMachine.getMetadata("version"));
    }

    // NOTE: "testUnknownCommand" test removed.
    // With the StateMachineCommand sealed interface, the compiler prevents
    // passing non-command types to apply() — no runtime check needed.

    @Test
    void testSnapshotOperations() {
        // Add some data
        stateMachine.apply(SystemMetadataCommand.set("key1", "value1"));
        stateMachine.apply(SystemMetadataCommand.set("key2", "value2"));

        TransferRequest request = TransferRequest.builder()
                .sourceUri(URI.create("https://test.com/file"))
                .destinationPath(Paths.get("/tmp/file"))
                .build();
        TransferJob job = new TransferJob(request);
        stateMachine.apply(TransferJobCommand.create(job));

        stateMachine.setLastAppliedIndex(10);

        // Take snapshot
        byte[] snapshot = stateMachine.takeSnapshot();
        assertNotNull(snapshot);
        assertTrue(snapshot.length > 0);

        // Reset state machine
        stateMachine.reset();
        assertEquals(0, stateMachine.getTransferJobCount());
        assertEquals("2.0", stateMachine.getMetadata("version")); // Back to default
        assertEquals(0, stateMachine.getLastAppliedIndex());

        // Restore snapshot
        stateMachine.restoreSnapshot(snapshot);

        // Verify data is restored
        assertEquals("value1", stateMachine.getMetadata("key1"));
        assertEquals("value2", stateMachine.getMetadata("key2"));
        assertEquals(1, stateMachine.getTransferJobCount());
        assertEquals(10, stateMachine.getLastAppliedIndex());

        TransferJobSnapshot restoredJob = stateMachine.getTransferJob(job.getJobId());
        assertNotNull(restoredJob);
        assertEquals(job.getJobId(), restoredJob.getJobId());
    }

    @Test
    void testSnapshotWithEmptyState() {
        // Take snapshot of empty state
        byte[] snapshot = stateMachine.takeSnapshot();
        assertNotNull(snapshot);

        // Add some data
        stateMachine.apply(SystemMetadataCommand.set("temp", "value"));
        assertEquals("value", stateMachine.getMetadata("temp"));

        // Restore empty snapshot
        stateMachine.restoreSnapshot(snapshot);

        // Should be back to initial state
        assertNull(stateMachine.getMetadata("temp"));
        assertEquals("2.0", stateMachine.getMetadata("version"));
    }

    @Test
    void testLastAppliedIndexManagement() {
        assertEquals(0, stateMachine.getLastAppliedIndex());

        stateMachine.setLastAppliedIndex(5);
        assertEquals(5, stateMachine.getLastAppliedIndex());

        stateMachine.setLastAppliedIndex(10);
        assertEquals(10, stateMachine.getLastAppliedIndex());

        // Reset should clear index
        stateMachine.reset();
        assertEquals(0, stateMachine.getLastAppliedIndex());
    }

    @Test
    void testGetAllOperations() {
        // Add multiple transfer jobs
        for (int i = 0; i < 3; i++) {
            TransferRequest request = TransferRequest.builder()
                    .sourceUri(URI.create("https://test.com/file" + i))
                    .destinationPath(Paths.get("/tmp/file" + i))
                    .build();
            TransferJob job = new TransferJob(request);
            stateMachine.apply(TransferJobCommand.create(job));
        }

        // Add metadata
        stateMachine.apply(SystemMetadataCommand.set("env", "test"));
        stateMachine.apply(SystemMetadataCommand.set("region", "us-east-1"));

        // Test getTransferJobs
        Map<String, TransferJobSnapshot> jobs = stateMachine.getTransferJobs();
        assertEquals(3, jobs.size());

        // Test getSystemMetadata
        Map<String, String> metadata = stateMachine.getSystemMetadata();
        assertTrue(metadata.size() >= 3); // 1 default (version) + 2 added (env, region)
        assertEquals("test", metadata.get("env"));
        assertEquals("us-east-1", metadata.get("region"));

        // Verify returned maps are copies (not live views)
        jobs.clear();
        metadata.clear();
        assertEquals(3, stateMachine.getTransferJobCount());
        assertTrue(stateMachine.getSystemMetadata().size() >= 2);
    }

    @Test
    void testCommandToString() {
        // Test TransferJobCommand toString
        TransferRequest request = TransferRequest.builder()
                .sourceUri(URI.create("https://test.com/file"))
                .destinationPath(Paths.get("/tmp/file"))
                .build();
        TransferJob job = new TransferJob(request);

        TransferJobCommand createCmd = TransferJobCommand.create(job);
        String createStr = createCmd.toString();
        assertNotNull(createStr);
        assertTrue(createStr.contains("CREATE"));
        assertTrue(createStr.contains(job.getJobId()));

        TransferJobCommand updateCmd = TransferJobCommand.updateStatus(job.getJobId(), TransferStatus.IN_PROGRESS);
        String updateStr = updateCmd.toString();
        assertNotNull(updateStr);
        assertTrue(updateStr.contains("UPDATE_STATUS"));
        assertTrue(updateStr.contains("IN_PROGRESS"));

        // Test SystemMetadataCommand toString
        SystemMetadataCommand setCmd = SystemMetadataCommand.set("key", "value");
        String setStr = setCmd.toString();
        assertNotNull(setStr);
        assertTrue(setStr.contains("SET"));
        assertTrue(setStr.contains("key"));
        assertTrue(setStr.contains("value"));

        SystemMetadataCommand deleteCmd = SystemMetadataCommand.delete("key");
        String deleteStr = deleteCmd.toString();
        assertNotNull(deleteStr);
        assertTrue(deleteStr.contains("DELETE"));
        assertTrue(deleteStr.contains("key"));
    }

    @Test
    void testSnapshotToString() {
        // Add some data
        stateMachine.apply(SystemMetadataCommand.set("test", "value"));

        TransferRequest request = TransferRequest.builder()
                .sourceUri(URI.create("https://test.com/file"))
                .destinationPath(Paths.get("/tmp/file"))
                .build();
        TransferJob job = new TransferJob(request);
        stateMachine.apply(TransferJobCommand.create(job));

        // Take snapshot and test toString
        byte[] snapshotData = stateMachine.takeSnapshot();

        // Create QuorusSnapshot object for toString test
        QuorusSnapshot snapshot = new QuorusSnapshot();
        snapshot.setTransferJobs(stateMachine.getTransferJobs());
        snapshot.setSystemMetadata(stateMachine.getSystemMetadata());
        snapshot.setLastAppliedIndex(5);

        String snapshotStr = snapshot.toString();
        assertNotNull(snapshotStr);
        assertTrue(snapshotStr.contains("1 jobs"));
        assertTrue(snapshotStr.contains("lastAppliedIndex=5"));
    }

    @Test
    void testSnapshotIncludesJobAssignmentsAndQueue() {
        // Add metadata and transfer job through the state machine
        stateMachine.apply(SystemMetadataCommand.set("region", "us-east"));

        TransferRequest request = TransferRequest.builder()
                .sourceUri(URI.create("https://source.com/data.csv"))
                .destinationPath(Paths.get("/dest/data.csv"))
                .build();
        TransferJob transferJob = new TransferJob(request);
        stateMachine.apply(TransferJobCommand.create(transferJob));

        // Add a job assignment via command
        JobAssignment assignment = new JobAssignment.Builder()
                .jobId(transferJob.getJobId())
                .agentId("agent-001")
                .build();
        stateMachine.apply(JobAssignmentCommand.assign(assignment));

        stateMachine.setLastAppliedIndex(20);

        // Take snapshot
        byte[] snapshotData = stateMachine.takeSnapshot();
        assertNotNull(snapshotData);
        assertTrue(snapshotData.length > 0);

        // Restore to a fresh state machine
        QuorusStateMachine restored = new QuorusStateMachine();
        restored.restoreSnapshot(snapshotData);

        // Verify metadata survived
        assertEquals("us-east", restored.getMetadata("region"));

        // Verify transfer job survived
        assertNotNull(restored.getTransferJob(transferJob.getJobId()));

        // Verify job assignment survived the round-trip
        String assignmentId = transferJob.getJobId() + ":" + "agent-001";
        JobAssignment restoredAssignment = restored.getJobAssignment(assignmentId);
        assertNotNull(restoredAssignment, "Job assignment should survive snapshot round-trip");
        assertEquals("agent-001", restoredAssignment.getAgentId());
        assertEquals(transferJob.getJobId(), restoredAssignment.getJobId());

        // Verify lastAppliedIndex survived
        assertEquals(20, restored.getLastAppliedIndex());
    }
}
