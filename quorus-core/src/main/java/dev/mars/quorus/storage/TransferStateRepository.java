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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
/**
 * Description for TransferStateRepository
 *
 * @author Mark Andrew Ray-Smith Cityline Ltd
 * @version 1.0
 * @since 2025-08-17
 */

public class TransferStateRepository {
    private static final Logger logger = LoggerFactory.getLogger(TransferStateRepository.class);
    
    private final Map<String, TransferState> transferStates;
    
    public TransferStateRepository() {
        this.transferStates = new ConcurrentHashMap<>();
        logger.debug("TransferStateRepository initialized with empty state map");
    }
    
    public void saveTransferState(TransferJob job) {
        logger.debug("saveTransferState: saving state for jobId={}", job.getJobId());
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
        logger.debug("Saved transfer state for job: {}, status={}, progress={}%", 
            job.getJobId(), job.getStatus(), 
            job.getTotalBytes() > 0 ? (job.getBytesTransferred() * 100) / job.getTotalBytes() : 0);
    }
    
    public TransferState loadTransferState(String jobId) {
        logger.debug("loadTransferState: loading state for jobId={}", jobId);
        TransferState state = transferStates.get(jobId);
        if (state != null) {
            logger.debug("loadTransferState: found state for jobId={}, status={}", jobId, state.getStatus());
        } else {
            logger.debug("loadTransferState: no state found for jobId={}", jobId);
        }
        return state;
    }
    
    public void removeTransferState(String jobId) {
        logger.debug("removeTransferState: removing state for jobId={}", jobId);
        TransferState removed = transferStates.remove(jobId);
        if (removed != null) {
            logger.debug("Removed transfer state for job: {}, finalStatus={}", jobId, removed.getStatus());
        } else {
            logger.debug("removeTransferState: no state to remove for jobId={}", jobId);
        }
    }
    
    public boolean hasTransferState(String jobId) {
        boolean exists = transferStates.containsKey(jobId);
        logger.trace("hasTransferState: jobId={}, exists={}", jobId, exists);
        return exists;
    }
    
    public Map<String, TransferState> getAllTransferStates() {
        logger.debug("getAllTransferStates: returning {} states", transferStates.size());
        return Map.copyOf(transferStates);
    }
    
    public int getTransferStateCount() {
        int count = transferStates.size();
        logger.trace("getTransferStateCount: count={}", count);
        return count;
    }
    
    public void clearAll() {
        int count = transferStates.size();
        logger.debug("clearAll: clearing {} transfer states", count);
        transferStates.clear();
        logger.info("Cleared {} transfer states", count);
    }
    
    public void cleanupOldTransfers(long maxAgeMs) {
        logger.debug("cleanupOldTransfers: cleaning up transfers older than {}ms", maxAgeMs);
        Instant cutoff = Instant.now().minusMillis(maxAgeMs);
        logger.debug("cleanupOldTransfers: cutoff time={}", cutoff);
        
        int initialCount = transferStates.size();
        transferStates.entrySet().removeIf(entry -> {
            TransferState state = entry.getValue();
            if (state.getStatus().isTerminal() && 
                state.getLastUpdateTime() != null && 
                state.getLastUpdateTime().isBefore(cutoff)) {
                logger.debug("Cleaned up old transfer state: {}, lastUpdate={}", entry.getKey(), state.getLastUpdateTime());
                return true;
            }
            return false;
        });
        int removedCount = initialCount - transferStates.size();
        logger.debug("cleanupOldTransfers: removed {} of {} states", removedCount, initialCount);
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
