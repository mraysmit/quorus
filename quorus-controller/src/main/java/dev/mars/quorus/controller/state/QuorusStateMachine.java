/*
 * Copyright 2024 Quorus Project
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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import dev.mars.quorus.controller.raft.RaftStateMachine;
import dev.mars.quorus.core.TransferJob;
import dev.mars.quorus.core.TransferStatus;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Quorus-specific state machine implementation for Raft consensus.
 * Manages distributed state for transfer jobs and system metadata.
 */
public class QuorusStateMachine implements RaftStateMachine {

    private static final Logger logger = Logger.getLogger(QuorusStateMachine.class.getName());

    // State data
    private final Map<String, TransferJob> transferJobs = new ConcurrentHashMap<>();
    private final Map<String, String> systemMetadata = new ConcurrentHashMap<>();
    private final AtomicLong lastAppliedIndex = new AtomicLong(0);

    // JSON serialization
    private final ObjectMapper objectMapper = new ObjectMapper();

    {
        objectMapper.registerModule(new JavaTimeModule());
    }

    /**
     * Create a new Quorus state machine.
     */
    public QuorusStateMachine() {
        // Initialize with system metadata
        systemMetadata.put("version", "2.0");
        systemMetadata.put("phase", "2.2 - Controller Quorum Architecture");
    }

    @Override
    public Object apply(Object command) {
        if (command == null) {
            return null; // No-op command
        }

        try {
            if (command instanceof TransferJobCommand) {
                return applyTransferJobCommand((TransferJobCommand) command);
            } else if (command instanceof SystemMetadataCommand) {
                return applySystemMetadataCommand((SystemMetadataCommand) command);
            } else {
                logger.warning("Unknown command type: " + command.getClass().getName());
                return null;
            }
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Failed to apply command: " + command, e);
            throw new RuntimeException("Failed to apply command", e);
        }
    }

    /**
     * Apply a transfer job command.
     */
    private Object applyTransferJobCommand(TransferJobCommand command) {
        String jobId = command.getJobId();
        
        switch (command.getType()) {
            case CREATE:
                TransferJob job = command.getTransferJob();
                transferJobs.put(jobId, job);
                logger.info("Created transfer job: " + jobId);
                return job;
                
            case UPDATE_STATUS:
                TransferJob existingJob = transferJobs.get(jobId);
                if (existingJob != null) {
                    // Update job status (simplified - in real implementation would update the job object)
                    logger.info("Updated transfer job status: " + jobId + " -> " + command.getStatus());
                    return existingJob;
                } else {
                    logger.warning("Transfer job not found for update: " + jobId);
                    return null;
                }
                
            case DELETE:
                TransferJob removedJob = transferJobs.remove(jobId);
                if (removedJob != null) {
                    logger.info("Deleted transfer job: " + jobId);
                    return removedJob;
                } else {
                    logger.warning("Transfer job not found for deletion: " + jobId);
                    return null;
                }
                
            default:
                logger.warning("Unknown transfer job command type: " + command.getType());
                return null;
        }
    }

    /**
     * Apply a system metadata command.
     */
    private Object applySystemMetadataCommand(SystemMetadataCommand command) {
        String key = command.getKey();
        
        switch (command.getType()) {
            case SET:
                String oldValue = systemMetadata.put(key, command.getValue());
                logger.info("Set system metadata: " + key + " = " + command.getValue());
                return oldValue;
                
            case DELETE:
                String removedValue = systemMetadata.remove(key);
                logger.info("Deleted system metadata: " + key);
                return removedValue;
                
            default:
                logger.warning("Unknown system metadata command type: " + command.getType());
                return null;
        }
    }

    @Override
    public byte[] takeSnapshot() {
        try {
            QuorusSnapshot snapshot = new QuorusSnapshot();
            snapshot.setTransferJobs(new ConcurrentHashMap<>(transferJobs));
            snapshot.setSystemMetadata(new ConcurrentHashMap<>(systemMetadata));
            snapshot.setLastAppliedIndex(lastAppliedIndex.get());
            
            byte[] data = objectMapper.writeValueAsBytes(snapshot);
            logger.info("Created snapshot with " + transferJobs.size() + " transfer jobs");
            return data;
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Failed to create snapshot", e);
            throw new RuntimeException("Failed to create snapshot", e);
        }
    }

    @Override
    public void restoreSnapshot(byte[] snapshot) {
        try {
            QuorusSnapshot restoredSnapshot = objectMapper.readValue(snapshot, QuorusSnapshot.class);
            
            transferJobs.clear();
            transferJobs.putAll(restoredSnapshot.getTransferJobs());
            
            systemMetadata.clear();
            systemMetadata.putAll(restoredSnapshot.getSystemMetadata());
            
            lastAppliedIndex.set(restoredSnapshot.getLastAppliedIndex());
            
            logger.info("Restored snapshot with " + transferJobs.size() + " transfer jobs");
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Failed to restore snapshot", e);
            throw new RuntimeException("Failed to restore snapshot", e);
        }
    }

    @Override
    public long getLastAppliedIndex() {
        return lastAppliedIndex.get();
    }

    @Override
    public void reset() {
        transferJobs.clear();
        systemMetadata.clear();
        systemMetadata.put("version", "2.0");
        systemMetadata.put("phase", "2.2 - Controller Quorum Architecture");
        lastAppliedIndex.set(0);
        logger.info("State machine reset");
    }

    /**
     * Get all transfer jobs (for querying).
     */
    public Map<String, TransferJob> getTransferJobs() {
        return new ConcurrentHashMap<>(transferJobs);
    }

    /**
     * Get a specific transfer job.
     */
    public TransferJob getTransferJob(String jobId) {
        return transferJobs.get(jobId);
    }

    /**
     * Get system metadata.
     */
    public Map<String, String> getSystemMetadata() {
        return new ConcurrentHashMap<>(systemMetadata);
    }

    /**
     * Get a specific metadata value.
     */
    public String getMetadata(String key) {
        return systemMetadata.get(key);
    }

    /**
     * Get the number of transfer jobs.
     */
    public int getTransferJobCount() {
        return transferJobs.size();
    }

    /**
     * Update the last applied index.
     */
    public void setLastAppliedIndex(long index) {
        lastAppliedIndex.set(index);
    }
}
