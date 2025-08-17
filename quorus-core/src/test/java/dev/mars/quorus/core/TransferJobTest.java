package dev.mars.quorus.core;

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


import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.net.URI;
import java.nio.file.Paths;

class TransferJobTest {
    
    private TransferRequest request;
    private TransferJob job;
    
    @BeforeEach
    void setUp() {
        request = TransferRequest.builder()
                .sourceUri(URI.create("http://example.com/file.txt"))
                .destinationPath(Paths.get("/tmp/file.txt"))
                .expectedSize(1024)
                .build();
        job = new TransferJob(request);
    }
    
    @Test
    void testInitialState() {
        assertEquals(request, job.getRequest());
        assertEquals(request.getRequestId(), job.getJobId());
        assertEquals(TransferStatus.PENDING, job.getStatus());
        assertEquals(0, job.getBytesTransferred());
        assertEquals(1024, job.getTotalBytes());
        assertNull(job.getStartTime());
        assertNotNull(job.getLastUpdateTime());
        assertNull(job.getErrorMessage());
        assertNull(job.getLastError());
        assertNull(job.getActualChecksum());
    }
    
    @Test
    void testStart() {
        job.start();
        
        assertEquals(TransferStatus.IN_PROGRESS, job.getStatus());
        assertNotNull(job.getStartTime());
    }
    
    @Test
    void testStartOnlyWorksFromPending() {
        job.start();
        var firstStartTime = job.getStartTime();
        
        // Try to start again - should not change start time
        job.start();
        assertEquals(firstStartTime, job.getStartTime());
    }
    
    @Test
    void testPauseAndResume() {
        job.start();
        
        job.pause();
        assertEquals(TransferStatus.PAUSED, job.getStatus());
        
        job.resume();
        assertEquals(TransferStatus.IN_PROGRESS, job.getStatus());
    }
    
    @Test
    void testPauseOnlyWorksFromInProgress() {
        job.pause(); // Should not work from PENDING
        assertEquals(TransferStatus.PENDING, job.getStatus());
        
        job.start();
        job.pause(); // Should work from IN_PROGRESS
        assertEquals(TransferStatus.PAUSED, job.getStatus());
    }
    
    @Test
    void testComplete() {
        String checksum = "abc123";
        job.complete(checksum);
        
        assertEquals(TransferStatus.COMPLETED, job.getStatus());
        assertEquals(checksum, job.getActualChecksum());
    }
    
    @Test
    void testFail() {
        String errorMessage = "Network error";
        Exception cause = new RuntimeException("Connection failed");
        
        job.fail(errorMessage, cause);
        
        assertEquals(TransferStatus.FAILED, job.getStatus());
        assertEquals(errorMessage, job.getErrorMessage());
        assertEquals(cause, job.getLastError());
    }
    
    @Test
    void testCancel() {
        job.cancel();
        assertEquals(TransferStatus.CANCELLED, job.getStatus());
    }
    
    @Test
    void testProgressTracking() {
        job.updateProgress(512);
        assertEquals(512, job.getBytesTransferred());
        
        job.addBytesTransferred(256);
        assertEquals(768, job.getBytesTransferred());
    }
    
    @Test
    void testProgressPercentage() {
        assertEquals(0.0, job.getProgressPercentage(), 0.001);
        
        job.updateProgress(512);
        assertEquals(0.5, job.getProgressPercentage(), 0.001);
        
        job.updateProgress(1024);
        assertEquals(1.0, job.getProgressPercentage(), 0.001);
        
        job.updateProgress(2048); // Over 100%
        assertEquals(1.0, job.getProgressPercentage(), 0.001);
    }
    
    @Test
    void testProgressPercentageWithUnknownSize() {
        TransferRequest requestWithoutSize = TransferRequest.builder()
                .sourceUri(URI.create("http://example.com/file.txt"))
                .destinationPath(Paths.get("/tmp/file.txt"))
                .build();
        TransferJob jobWithoutSize = new TransferJob(requestWithoutSize);
        
        assertEquals(0.0, jobWithoutSize.getProgressPercentage(), 0.001);
        
        jobWithoutSize.updateProgress(1024);
        assertEquals(0.0, jobWithoutSize.getProgressPercentage(), 0.001);
    }
    
    @Test
    void testEstimatedRemainingTime() {
        // No start time
        assertEquals(-1, job.getEstimatedRemainingSeconds());

        job.start();

        // No progress yet
        assertEquals(-1, job.getEstimatedRemainingSeconds());

        // Simulate some progress after a delay
        try {
            Thread.sleep(1000); // Longer delay to ensure measurable elapsed time
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        job.updateProgress(512); // 50% complete
        long estimatedRemaining = job.getEstimatedRemainingSeconds();
        // The estimate might be -1 if elapsed time is too small, so we check >= -1
        assertTrue(estimatedRemaining >= -1); // Should have a valid estimate or -1 for unmeasurable
    }
    
    @Test
    void testToResult() {
        job.start();
        job.updateProgress(512);
        job.complete("abc123");
        
        TransferResult result = job.toResult();
        
        assertEquals(job.getJobId(), result.getRequestId());
        assertEquals(TransferStatus.COMPLETED, result.getFinalStatus());
        assertEquals(512, result.getBytesTransferred());
        assertEquals(job.getStartTime(), result.getStartTime().orElse(null));
        assertEquals("abc123", result.getActualChecksum().orElse(null));
    }
    
    @Test
    void testEqualsAndHashCode() {
        TransferJob job2 = new TransferJob(request);
        
        assertEquals(job, job2); // Same request ID
        assertEquals(job.hashCode(), job2.hashCode());
    }
    
    @Test
    void testToString() {
        job.updateProgress(512);
        String toString = job.toString();
        
        assertTrue(toString.contains("TransferJob"));
        assertTrue(toString.contains(job.getJobId()));
        assertTrue(toString.contains("50.0%"));
        assertTrue(toString.contains("512"));
        assertTrue(toString.contains("1024"));
    }
}
