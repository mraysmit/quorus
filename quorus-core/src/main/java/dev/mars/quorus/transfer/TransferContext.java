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

/**
 * Context object that holds state and configuration for an individual transfer operation.
 * Provides a way to pass shared state between different components during a transfer.
 */
public class TransferContext {
    private final TransferJob job;
    private final Map<String, Object> attributes;
    private final AtomicBoolean cancelled;
    private final AtomicBoolean paused;
    private volatile int retryCount;
    private volatile long lastRetryTime;
    
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
        cancelled.set(true);
    }
    
    public boolean isPaused() {
        return paused.get();
    }
    
    public void pause() {
        paused.set(true);
    }
    
    public void resume() {
        paused.set(false);
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
    
    /**
     * Check if the transfer should continue (not cancelled and not paused)
     */
    public boolean shouldContinue() {
        return !cancelled.get() && !paused.get();
    }
    
    /**
     * Wait for resume if paused, or return immediately if cancelled
     * @param maxWaitMs maximum time to wait for resume
     * @return true if should continue, false if cancelled
     */
    public boolean waitForResumeOrCancel(long maxWaitMs) {
        if (cancelled.get()) {
            return false;
        }
        
        if (!paused.get()) {
            return true;
        }
        
        long startTime = System.currentTimeMillis();
        while (paused.get() && !cancelled.get()) {
            if (System.currentTimeMillis() - startTime > maxWaitMs) {
                break;
            }
            
            try {
                Thread.sleep(100); // Check every 100ms
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        
        return !cancelled.get();
    }
}
