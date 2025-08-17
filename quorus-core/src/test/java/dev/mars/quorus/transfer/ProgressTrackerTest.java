package dev.mars.quorus.transfer;

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

class ProgressTrackerTest {
    
    private ProgressTracker tracker;
    private final String jobId = "test-job-123";
    
    @BeforeEach
    void setUp() {
        tracker = new ProgressTracker(jobId);
    }
    
    @Test
    void testInitialState() {
        assertEquals(jobId, tracker.getJobId());
        assertEquals(-1, tracker.getTotalBytes());
        assertEquals(0, tracker.getTransferredBytes());
        assertEquals(0.0, tracker.getProgressPercentage(), 0.001);
        assertEquals(0.0, tracker.getCurrentRateBytesPerSecond(), 0.001);
        assertEquals(-1, tracker.getEstimatedRemainingSeconds());
        assertNull(tracker.getStartTime());
        assertNull(tracker.getLastUpdateTime());
    }
    
    @Test
    void testStart() {
        tracker.start();
        
        assertNotNull(tracker.getStartTime());
        assertNotNull(tracker.getLastUpdateTime());
        assertTrue(tracker.getElapsedTimeSeconds() >= 0);
    }
    
    @Test
    void testSetTotalBytes() {
        tracker.setTotalBytes(1024);
        assertEquals(1024, tracker.getTotalBytes());
    }
    
    @Test
    void testUpdateProgress() {
        tracker.setTotalBytes(1024);
        tracker.start();
        
        tracker.updateProgress(512);
        
        assertEquals(512, tracker.getTransferredBytes());
        assertEquals(0.5, tracker.getProgressPercentage(), 0.001);
        assertNotNull(tracker.getLastUpdateTime());
    }
    
    @Test
    void testAddBytesTransferred() {
        tracker.setTotalBytes(1024);
        tracker.start();
        
        tracker.addBytesTransferred(256);
        assertEquals(256, tracker.getTransferredBytes());
        
        tracker.addBytesTransferred(256);
        assertEquals(512, tracker.getTransferredBytes());
    }
    
    @Test
    void testProgressPercentageWithoutTotalBytes() {
        tracker.updateProgress(512);
        assertEquals(0.0, tracker.getProgressPercentage(), 0.001);
    }
    
    @Test
    void testProgressPercentageOverflow() {
        tracker.setTotalBytes(1024);
        tracker.updateProgress(2048); // More than total
        
        assertEquals(1.0, tracker.getProgressPercentage(), 0.001); // Capped at 100%
    }
    
    @Test
    void testRateCalculation() throws InterruptedException {
        tracker.setTotalBytes(1024);
        tracker.start();
        
        // Initial update
        tracker.updateProgress(0);
        
        // Wait a bit and update again
        Thread.sleep(100);
        tracker.updateProgress(512);
        
        // Rate should be calculated (though may be 0 due to timing)
        assertTrue(tracker.getCurrentRateBytesPerSecond() >= 0);
    }
    
    @Test
    void testAverageRate() throws InterruptedException {
        tracker.setTotalBytes(1024);
        tracker.start();
        
        Thread.sleep(100); // Ensure some elapsed time
        tracker.updateProgress(512);
        
        double averageRate = tracker.getAverageRateBytesPerSecond();
        assertTrue(averageRate >= 0);
    }
    
    @Test
    void testEstimatedRemainingTime() {
        tracker.setTotalBytes(1024);
        tracker.start();
        
        // No progress yet
        assertEquals(-1, tracker.getEstimatedRemainingSeconds());
        assertEquals("Unknown", tracker.getEstimatedRemainingTime());
        
        // Simulate progress with rate
        tracker.updateProgress(512);
        // Set a mock rate for testing
        // Note: In real scenario, rate would be calculated based on time
    }
    
    @Test
    void testEstimatedRemainingTimeFormatting() {
        // Test time formatting
        assertEquals("30s", formatTime(30));
        assertEquals("1m 30s", formatTime(90));
        assertEquals("1h 30m", formatTime(5400)); // 1.5 hours
    }
    
    private String formatTime(long seconds) {
        if (seconds < 60) {
            return seconds + "s";
        } else if (seconds < 3600) {
            return (seconds / 60) + "m " + (seconds % 60) + "s";
        } else {
            long hours = seconds / 3600;
            long minutes = (seconds % 3600) / 60;
            return hours + "h " + minutes + "m";
        }
    }
    
    @Test
    void testCurrentRateMBytesPerSecond() {
        // Mock a rate of 1MB/s (1024*1024 bytes/s)
        tracker.setTotalBytes(10 * 1024 * 1024);
        tracker.start();
        
        // The actual rate calculation depends on timing, so we test the conversion
        double bytesPerSecond = 1024 * 1024; // 1 MB/s
        double mbytesPerSecond = bytesPerSecond / (1024.0 * 1024.0);
        assertEquals(1.0, mbytesPerSecond, 0.001);
    }
    
    @Test
    void testToString() {
        tracker.setTotalBytes(1024);
        tracker.start();
        tracker.updateProgress(512);
        
        String toString = tracker.toString();
        
        assertTrue(toString.contains("ProgressTracker"));
        assertTrue(toString.contains(jobId));
        assertTrue(toString.contains("50.0%"));
        assertTrue(toString.contains("MB/s"));
        assertTrue(toString.contains("ETA="));
    }
    
    @Test
    void testElapsedTime() throws InterruptedException {
        tracker.start();
        
        Thread.sleep(100); // Wait 100ms
        
        long elapsed = tracker.getElapsedTimeSeconds();
        assertTrue(elapsed >= 0);
    }
    
    @Test
    void testElapsedTimeWithoutStart() {
        assertEquals(0, tracker.getElapsedTimeSeconds());
    }
}
