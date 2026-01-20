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

package dev.mars.quorus.transfer;

import dev.mars.quorus.core.TransferJob;
import dev.mars.quorus.core.TransferRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive test suite for {@link TransferContext}.
 * Tests attribute management, cancellation, pause/resume, and retry logic.
 *
 * @author Mark Andrew Ray-Smith Cityline Ltd
 * @since 2025-08-27
 * @version 1.0
 */
class TransferContextTest {

    private TransferJob testJob;
    private TransferContext context;

    @BeforeEach
    void setUp() {
        TransferRequest request = TransferRequest.builder()
            .requestId("test-job-123")
            .sourceUri(java.net.URI.create("file:///source/file.txt"))
            .destinationPath("/dest/file.txt")
            .protocol("http")
            .build();
        
        testJob = new TransferJob(request);
        context = new TransferContext(testJob);
    }

    @Test
    void testConstructor() {
        assertNotNull(context);
        assertEquals(testJob, context.getJob());
        assertEquals("test-job-123", context.getJobId());
        assertFalse(context.isCancelled());
        assertFalse(context.isPaused());
        assertEquals(0, context.getRetryCount());
        assertEquals(0, context.getLastRetryTime());
    }

    @Test
    void testGetJob() {
        TransferJob job = context.getJob();
        assertNotNull(job);
        assertEquals(testJob, job);
        assertEquals("test-job-123", job.getJobId());
    }

    @Test
    void testGetJobId() {
        assertEquals("test-job-123", context.getJobId());
    }

    @Test
    void testSetAndGetAttribute() {
        context.setAttribute("key1", "value1");
        context.setAttribute("key2", 123);
        context.setAttribute("key3", true);
        
        assertEquals("value1", context.getAttribute("key1"));
        assertEquals(123, context.getAttribute("key2"));
        assertEquals(true, context.getAttribute("key3"));
    }

    @Test
    void testGetAttributeWithType() {
        context.setAttribute("stringKey", "stringValue");
        context.setAttribute("intKey", 456);
        context.setAttribute("boolKey", false);
        
        String strValue = context.getAttribute("stringKey", String.class);
        assertEquals("stringValue", strValue);
        
        Integer intValue = context.getAttribute("intKey", Integer.class);
        assertEquals(456, intValue);
        
        Boolean boolValue = context.getAttribute("boolKey", Boolean.class);
        assertEquals(false, boolValue);
    }

    @Test
    void testGetAttributeWithWrongType() {
        context.setAttribute("key", "stringValue");
        
        Integer wrongType = context.getAttribute("key", Integer.class);
        assertNull(wrongType);
    }

    @Test
    void testGetAttributeNonExistent() {
        Object value = context.getAttribute("nonExistent");
        assertNull(value);
        
        String typedValue = context.getAttribute("nonExistent", String.class);
        assertNull(typedValue);
    }

    @Test
    void testRemoveAttribute() {
        context.setAttribute("key1", "value1");
        assertEquals("value1", context.getAttribute("key1"));
        
        context.removeAttribute("key1");
        assertNull(context.getAttribute("key1"));
    }

    @Test
    void testCancellation() {
        assertFalse(context.isCancelled());
        
        context.cancel();
        assertTrue(context.isCancelled());
    }

    @Test
    void testPauseAndResume() {
        assertFalse(context.isPaused());
        
        context.pause();
        assertTrue(context.isPaused());
        
        context.resume();
        assertFalse(context.isPaused());
    }

    @Test
    void testRetryCount() {
        assertEquals(0, context.getRetryCount());
        
        context.incrementRetryCount();
        assertEquals(1, context.getRetryCount());
        
        context.incrementRetryCount();
        assertEquals(2, context.getRetryCount());
        
        long lastRetryTime = context.getLastRetryTime();
        assertTrue(lastRetryTime > 0);
        
        context.resetRetryCount();
        assertEquals(0, context.getRetryCount());
        assertEquals(0, context.getLastRetryTime());
    }

    @Test
    void testShouldContinueWhenNotCancelledOrPaused() {
        assertTrue(context.shouldContinue());
    }

    @Test
    void testShouldContinueWhenCancelled() {
        context.cancel();
        assertFalse(context.shouldContinue());
    }

    @Test
    void testShouldContinueWhenPaused() {
        context.pause();
        assertFalse(context.shouldContinue());
    }

    @Test
    void testWaitForResumeOrCancelWhenNotPaused() {
        boolean result = context.waitForResumeOrCancel(1000);
        assertTrue(result);
    }

    @Test
    void testWaitForResumeOrCancelWhenCancelled() {
        context.cancel();
        boolean result = context.waitForResumeOrCancel(1000);
        assertFalse(result);
    }

    @Test
    void testWaitForResumeOrCancelWhenPausedThenResumed() throws InterruptedException {
        context.pause();
        
        AtomicBoolean waitCompleted = new AtomicBoolean(false);
        CountDownLatch latch = new CountDownLatch(1);
        
        // Start waiting thread
        Thread waitThread = new Thread(() -> {
            boolean result = context.waitForResumeOrCancel(5000);
            waitCompleted.set(result);
            latch.countDown();
        });
        waitThread.start();
        
        // Wait a bit, then resume
        Thread.sleep(100);
        context.resume();
        
        // Wait for completion
        assertTrue(latch.await(2, TimeUnit.SECONDS));
        assertTrue(waitCompleted.get());
    }

    @Test
    void testWaitForResumeOrCancelWhenPausedThenCancelled() throws InterruptedException {
        context.pause();
        
        AtomicBoolean waitCompleted = new AtomicBoolean(true);
        CountDownLatch latch = new CountDownLatch(1);
        
        // Start waiting thread
        Thread waitThread = new Thread(() -> {
            boolean result = context.waitForResumeOrCancel(5000);
            waitCompleted.set(result);
            latch.countDown();
        });
        waitThread.start();
        
        // Wait a bit, then cancel
        Thread.sleep(100);
        context.cancel();
        
        // Wait for completion
        assertTrue(latch.await(2, TimeUnit.SECONDS));
        assertFalse(waitCompleted.get());
    }

    @Test
    void testWaitForResumeOrCancelTimeout() {
        context.pause();
        
        long startTime = System.currentTimeMillis();
        boolean result = context.waitForResumeOrCancel(500);
        long duration = System.currentTimeMillis() - startTime;
        
        // Current implementation returns true (not cancelled) even on timeout
        // TODO: Consider if this is the desired behavior - should return false if still paused after timeout
        assertTrue(result); // Returns true because not cancelled (even though still paused)
        assertTrue(duration >= 400 && duration < 700); // Should timeout around 500ms
        assertTrue(context.isPaused()); // Verify it's still paused after timeout
    }

    @Test
    void testConcurrentAttributeAccess() throws InterruptedException {
        int threadCount = 10;
        CountDownLatch latch = new CountDownLatch(threadCount);
        
        for (int i = 0; i < threadCount; i++) {
            final int index = i;
            new Thread(() -> {
                context.setAttribute("key" + index, "value" + index);
                latch.countDown();
            }).start();
        }
        
        assertTrue(latch.await(5, TimeUnit.SECONDS));
        
        for (int i = 0; i < threadCount; i++) {
            assertEquals("value" + i, context.getAttribute("key" + i));
        }
    }

    @Test
    void testIncrementRetryCountUpdatesLastRetryTime() throws InterruptedException {
        long beforeTime = System.currentTimeMillis();
        
        Thread.sleep(10); // Ensure time difference
        context.incrementRetryCount();
        
        long afterTime = System.currentTimeMillis();
        long lastRetryTime = context.getLastRetryTime();
        
        assertTrue(lastRetryTime >= beforeTime);
        assertTrue(lastRetryTime <= afterTime);
    }
}
