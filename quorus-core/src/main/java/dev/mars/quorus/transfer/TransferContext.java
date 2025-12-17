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


import dev.mars.quorus.core.TransferJob;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class TransferContext {
    private final TransferJob job;
    private final Map<String, Object> attributes;
    private final AtomicBoolean cancelled;
    private final AtomicBoolean paused;
    private volatile int retryCount;
    private volatile long lastRetryTime;

    // Lock and condition for pause/resume coordination (Phase 2 - Dec 2025)
    private final Lock pauseLock = new ReentrantLock();
    private final Condition resumeCondition = pauseLock.newCondition();
    
    public TransferContext(TransferJob job) {
        this.job = job;
        this.attributes = new ConcurrentHashMap<>();
        this.cancelled = new AtomicBoolean(false);
        this.paused = new AtomicBoolean(false);
        this.retryCount = 0;
        this.lastRetryTime = 0;
    }
    
    public TransferJob getJob() {
        return job;
    }
    
    public String getJobId() {
        return job.getJobId();
    }
    
    // Attribute management
    public void setAttribute(String key, Object value) {
        attributes.put(key, value);
    }
    
    @SuppressWarnings("unchecked")
    public <T> T getAttribute(String key, Class<T> type) {
        Object value = attributes.get(key);
        if (value != null && type.isInstance(value)) {
            return (T) value;
        }
        return null;
    }
    
    public Object getAttribute(String key) {
        return attributes.get(key);
    }
    
    public void removeAttribute(String key) {
        attributes.remove(key);
    }
    
    // Control flags
    public boolean isCancelled() {
        return cancelled.get();
    }
    
    public void cancel() {
        pauseLock.lock();
        try {
            cancelled.set(true);
            resumeCondition.signalAll(); // Wake up any waiting threads
        } finally {
            pauseLock.unlock();
        }
    }
    
    public boolean isPaused() {
        return paused.get();
    }
    
    public void pause() {
        paused.set(true);
    }

    public void resume() {
        pauseLock.lock();
        try {
            paused.set(false);
            resumeCondition.signalAll(); // Wake up any waiting threads
        } finally {
            pauseLock.unlock();
        }
    }
    
    // Retry management
    public int getRetryCount() {
        return retryCount;
    }
    
    public void incrementRetryCount() {
        this.retryCount++;
        this.lastRetryTime = System.currentTimeMillis();
    }
    
    public long getLastRetryTime() {
        return lastRetryTime;
    }
    
    public void resetRetryCount() {
        this.retryCount = 0;
        this.lastRetryTime = 0;
    }
    
    public boolean shouldContinue() {
        return !cancelled.get() && !paused.get();
    }
    
    /**
     * Wait for resume if paused, or return immediately if cancelled.
     * Uses Lock/Condition for efficient waiting instead of Thread.sleep.
     *
     * @param maxWaitMs maximum time to wait for resume
     * @return true if should continue, false if cancelled
     * @since 2.0 (Phase 2 - Dec 2025) - Replaced Thread.sleep with Lock/Condition
     */
    public boolean waitForResumeOrCancel(long maxWaitMs) {
        if (cancelled.get()) {
            return false;
        }

        if (!paused.get()) {
            return true;
        }

        pauseLock.lock();
        try {
            long remainingNanos = maxWaitMs * 1_000_000; // Convert ms to nanoseconds

            while (paused.get() && !cancelled.get() && remainingNanos > 0) {
                try {
                    remainingNanos = resumeCondition.awaitNanos(remainingNanos);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return false;
                }
            }

            return !cancelled.get();
        } finally {
            pauseLock.unlock();
        }
    }
}
