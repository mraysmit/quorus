package dev.mars.quorus.storage;

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


import dev.mars.quorus.core.TransferJob;
import dev.mars.quorus.core.TransferStatus;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

public class TransferStateRepository {
    private static final Logger logger = Logger.getLogger(TransferStateRepository.class.getName());
    
    private final Map<String, TransferState> transferStates;
    
    public TransferStateRepository() {
        this.transferStates = new ConcurrentHashMap<>();
    }
    
    public void saveTransferState(TransferJob job) {
        TransferState state = new TransferState(
                job.getJobId(),
                job.getStatus(),
                job.getBytesTransferred(),
                job.getTotalBytes(),
                job.getStartTime(),
                job.getLastUpdateTime(),
                job.getActualChecksum(),
                job.getErrorMessage()
        );
        
        transferStates.put(job.getJobId(), state);
        logger.fine("Saved transfer state for job: " + job.getJobId());
    }
    
    public TransferState loadTransferState(String jobId) {
        return transferStates.get(jobId);
    }
    
    public void removeTransferState(String jobId) {
        TransferState removed = transferStates.remove(jobId);
        if (removed != null) {
            logger.fine("Removed transfer state for job: " + jobId);
        }
    }
    
    public boolean hasTransferState(String jobId) {
        return transferStates.containsKey(jobId);
    }
    
    public Map<String, TransferState> getAllTransferStates() {
        return Map.copyOf(transferStates);
    }
    
    public int getTransferStateCount() {
        return transferStates.size();
    }
    
    public void clearAll() {
        int count = transferStates.size();
        transferStates.clear();
        logger.info("Cleared " + count + " transfer states");
    }
    
    public void cleanupOldTransfers(long maxAgeMs) {
        Instant cutoff = Instant.now().minusMillis(maxAgeMs);
        
        transferStates.entrySet().removeIf(entry -> {
            TransferState state = entry.getValue();
            if (state.getStatus().isTerminal() && 
                state.getLastUpdateTime() != null && 
                state.getLastUpdateTime().isBefore(cutoff)) {
                logger.fine("Cleaned up old transfer state: " + entry.getKey());
                return true;
            }
            return false;
        });
    }
    
    /**
     * Immutable transfer state data class
     */
    public static class TransferState {
        private final String jobId;
        private final TransferStatus status;
        private final long bytesTransferred;
        private final long totalBytes;
        private final Instant startTime;
        private final Instant lastUpdateTime;
        private final String actualChecksum;
        private final String errorMessage;
        
        public TransferState(String jobId, TransferStatus status, long bytesTransferred, 
                           long totalBytes, Instant startTime, Instant lastUpdateTime,
                           String actualChecksum, String errorMessage) {
            this.jobId = jobId;
            this.status = status;
            this.bytesTransferred = bytesTransferred;
            this.totalBytes = totalBytes;
            this.startTime = startTime;
            this.lastUpdateTime = lastUpdateTime;
            this.actualChecksum = actualChecksum;
            this.errorMessage = errorMessage;
        }
        
        public String getJobId() { return jobId; }
        public TransferStatus getStatus() { return status; }
        public long getBytesTransferred() { return bytesTransferred; }
        public long getTotalBytes() { return totalBytes; }
        public Instant getStartTime() { return startTime; }
        public Instant getLastUpdateTime() { return lastUpdateTime; }
        public String getActualChecksum() { return actualChecksum; }
        public String getErrorMessage() { return errorMessage; }
        
        public double getProgressPercentage() {
            if (totalBytes <= 0) {
                return 0.0;
            }
            return Math.min(1.0, (double) bytesTransferred / totalBytes);
        }
        
        @Override
        public String toString() {
            return "TransferState{" +
                    "jobId='" + jobId + '\'' +
                    ", status=" + status +
                    ", progress=" + String.format("%.1f%%", getProgressPercentage() * 100) +
                    ", bytesTransferred=" + bytesTransferred +
                    ", totalBytes=" + totalBytes +
                    '}';
        }
    }
}
