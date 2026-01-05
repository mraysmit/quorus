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

package dev.mars.quorus.workflow;

import dev.mars.quorus.core.TransferJob;
import dev.mars.quorus.core.TransferRequest;
import dev.mars.quorus.core.TransferResult;
import dev.mars.quorus.core.TransferStatus;
import dev.mars.quorus.core.exceptions.TransferException;
import dev.mars.quorus.transfer.TransferEngine;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Test implementation of TransferEngine for use in unit tests.
 * This is a real implementation (not a mock) that simulates transfer behavior.
 */
class TestTransferEngine implements TransferEngine {
    
    private final ConcurrentHashMap<String, TransferJob> jobs = new ConcurrentHashMap<>();
    private final AtomicInteger activeTransferCount = new AtomicInteger(0);
    private boolean shutdown = false;
    
    // Configuration for test behavior
    private TransferBehavior behavior = TransferBehavior.SUCCESS;
    private RuntimeException exceptionToThrow = null;
    
    /**
     * Defines the behavior of the test transfer engine.
     */
    enum TransferBehavior {
        SUCCESS,    // Transfers complete successfully
        FAILURE,    // Transfers fail with error message
        EXCEPTION   // Transfers throw exceptions
    }
    
    /**
     * Configure the engine to simulate successful transfers.
     */
    public void simulateSuccess() {
        this.behavior = TransferBehavior.SUCCESS;
        this.exceptionToThrow = null;
    }
    
    /**
     * Configure the engine to simulate failed transfers.
     */
    public void simulateFailure() {
        this.behavior = TransferBehavior.FAILURE;
        this.exceptionToThrow = null;
    }
    
    /**
     * Configure the engine to simulate transfer exceptions.
     */
    public void simulateException(RuntimeException exception) {
        this.behavior = TransferBehavior.EXCEPTION;
        this.exceptionToThrow = exception;
    }
    
    @Override
    public Future<TransferResult> submitTransfer(TransferRequest request) throws TransferException {
        if (shutdown) {
            throw new TransferException(request.getRequestId(), "Transfer engine is shutdown");
        }
        
        activeTransferCount.incrementAndGet();
        
        TransferJob job = new TransferJob(request);
        jobs.put(job.getJobId(), job);
        
        Promise<TransferResult> promise = Promise.promise();
        
        // Execute asynchronously
        new Thread(() -> {
            try {
                // Simulate transfer based on configured behavior
                TransferResult result;
                switch (behavior) {
                    case SUCCESS:
                        result = createSuccessResult(request.getRequestId());
                        break;
                    case FAILURE:
                        result = createFailureResult(request.getRequestId());
                        break;
                    case EXCEPTION:
                        promise.fail(exceptionToThrow != null ? exceptionToThrow : new RuntimeException("Transfer failed"));
                        return;
                    default:
                        result = createSuccessResult(request.getRequestId());
                        break;
                }
                promise.complete(result);
            } finally {
                activeTransferCount.decrementAndGet();
                jobs.remove(job.getJobId());
            }
        }).start();
        
        return promise.future();
    }
    
    @Override
    public TransferJob getTransferJob(String jobId) {
        return jobs.get(jobId);
    }
    
    @Override
    public boolean cancelTransfer(String jobId) {
        TransferJob job = jobs.get(jobId);
        if (job != null) {
            job.cancel();
            jobs.remove(jobId);
            activeTransferCount.decrementAndGet();
            return true;
        }
        return false;
    }
    
    @Override
    public boolean pauseTransfer(String jobId) {
        TransferJob job = jobs.get(jobId);
        if (job != null) {
            job.pause();
            return true;
        }
        return false;
    }
    
    @Override
    public boolean resumeTransfer(String jobId) {
        TransferJob job = jobs.get(jobId);
        if (job != null) {
            job.resume();
            return true;
        }
        return false;
    }
    
    @Override
    public int getActiveTransferCount() {
        return activeTransferCount.get();
    }
    
    @Override
    public boolean shutdown(long timeoutSeconds) {
        shutdown = true;
        jobs.clear();
        activeTransferCount.set(0);
        return true;
    }

    @Override
    public dev.mars.quorus.monitoring.TransferEngineHealthCheck getHealthCheck() {
        return dev.mars.quorus.monitoring.TransferEngineHealthCheck.builder()
                .up()
                .message("Test Transfer Engine is healthy")
                .build();
    }

    @Override
    public dev.mars.quorus.monitoring.TransferMetrics getProtocolMetrics(String protocolName) {
        return new dev.mars.quorus.monitoring.TransferMetrics(protocolName);
    }

    @Override
    public java.util.Map<String, dev.mars.quorus.monitoring.TransferMetrics> getAllProtocolMetrics() {
        return java.util.Collections.emptyMap();
    }

    // Helper methods to create transfer results

    private TransferResult createSuccessResult(String requestId) {
        return TransferResult.builder()
                .requestId(requestId)
                .finalStatus(TransferStatus.COMPLETED)
                .bytesTransferred(1024L)
                .startTime(Instant.now().minusMillis(100))
                .endTime(Instant.now())
                .actualChecksum("test-checksum")
                .build();
    }

    private TransferResult createFailureResult(String requestId) {
        return TransferResult.builder()
                .requestId(requestId)
                .finalStatus(TransferStatus.FAILED)
                .bytesTransferred(0L)
                .errorMessage("Transfer failed")
                .cause(new RuntimeException("Transfer failed"))
                .build();
    }
}

