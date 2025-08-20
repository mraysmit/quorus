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
import dev.mars.quorus.core.TransferRequest;
import dev.mars.quorus.core.TransferResult;
import dev.mars.quorus.core.TransferStatus;
import dev.mars.quorus.core.exceptions.TransferException;
import dev.mars.quorus.protocol.ProtocolFactory;
import dev.mars.quorus.protocol.TransferProtocol;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Simple implementation of the TransferEngine interface.
 * Handles basic file transfers with retry logic and progress tracking.
 * 
 * @author Mark Andrew Ray-Smith Cityline Ltd
 * @since 1.0
 */
public class SimpleTransferEngine implements TransferEngine {
    private static final Logger logger = Logger.getLogger(SimpleTransferEngine.class.getName());
    
    private final ExecutorService executorService;
    private final ConcurrentHashMap<String, TransferJob> activeJobs;
    private final ConcurrentHashMap<String, TransferContext> activeContexts;
    private final ConcurrentHashMap<String, CompletableFuture<TransferResult>> activeFutures;
    private final ProtocolFactory protocolFactory;
    private final AtomicBoolean shutdown;
    
    // Configuration
    private final int maxConcurrentTransfers;
    private final int maxRetryAttempts;
    private final long retryDelayMs;
    
    public SimpleTransferEngine() {
        this(10, 3, 1000); // Default: 10 concurrent, 3 retries, 1s delay
    }
    
    public SimpleTransferEngine(int maxConcurrentTransfers, int maxRetryAttempts, long retryDelayMs) {
        this.maxConcurrentTransfers = maxConcurrentTransfers;
        this.maxRetryAttempts = maxRetryAttempts;
        this.retryDelayMs = retryDelayMs;
        
        this.executorService = new ThreadPoolExecutor(
                Math.min(4, maxConcurrentTransfers),
                maxConcurrentTransfers,
                60L, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(),
                r -> {
                    Thread t = new Thread(r, "quorus-transfer-" + System.currentTimeMillis());
                    t.setDaemon(true);
                    return t;
                }
        );
        
        this.activeJobs = new ConcurrentHashMap<>();
        this.activeContexts = new ConcurrentHashMap<>();
        this.activeFutures = new ConcurrentHashMap<>();
        this.protocolFactory = new ProtocolFactory();
        this.shutdown = new AtomicBoolean(false);
        
        logger.info("SimpleTransferEngine initialized with " + maxConcurrentTransfers + " max concurrent transfers");
    }
    
    @Override
    public CompletableFuture<TransferResult> submitTransfer(TransferRequest request) throws TransferException {
        if (shutdown.get()) {
            throw new TransferException(request.getRequestId(), "Transfer engine is shutdown");
        }
        
        if (activeJobs.size() >= maxConcurrentTransfers) {
            throw new TransferException(request.getRequestId(), "Maximum concurrent transfers reached");
        }
        
        // Create job and context
        TransferJob job = new TransferJob(request);
        TransferContext context = new TransferContext(job);
        
        // Store in active collections
        activeJobs.put(job.getJobId(), job);
        activeContexts.put(job.getJobId(), context);
        
        // Create and submit the transfer task
        CompletableFuture<TransferResult> future = CompletableFuture.supplyAsync(() -> {
            try {
                return executeTransfer(context);
            } catch (Exception e) {
                logger.log(Level.SEVERE, "Transfer execution failed for job " + job.getJobId(), e);
                job.fail("Transfer execution failed: " + e.getMessage(), e);
                return job.toResult();
            } finally {
                // Clean up
                activeJobs.remove(job.getJobId());
                activeContexts.remove(job.getJobId());
                activeFutures.remove(job.getJobId());
            }
        }, executorService);
        
        activeFutures.put(job.getJobId(), future);
        
        logger.info("Transfer submitted: " + job.getJobId());
        return future;
    }
    
    @Override
    public TransferJob getTransferJob(String jobId) {
        return activeJobs.get(jobId);
    }
    
    @Override
    public boolean cancelTransfer(String jobId) {
        TransferContext context = activeContexts.get(jobId);
        CompletableFuture<TransferResult> future = activeFutures.get(jobId);
        
        if (context != null) {
            context.cancel();
            TransferJob job = context.getJob();
            if (job != null) {
                job.cancel();
            }
        }
        
        if (future != null) {
            future.cancel(true);
        }
        
        return context != null;
    }
    
    @Override
    public boolean pauseTransfer(String jobId) {
        TransferContext context = activeContexts.get(jobId);
        if (context != null) {
            context.pause();
            TransferJob job = context.getJob();
            if (job != null) {
                job.pause();
            }
            return true;
        }
        return false;
    }
    
    @Override
    public boolean resumeTransfer(String jobId) {
        TransferContext context = activeContexts.get(jobId);
        if (context != null) {
            context.resume();
            TransferJob job = context.getJob();
            if (job != null) {
                job.resume();
            }
            return true;
        }
        return false;
    }
    
    @Override
    public int getActiveTransferCount() {
        return activeJobs.size();
    }
    
    @Override
    public boolean shutdown(long timeoutSeconds) {
        if (shutdown.getAndSet(true)) {
            return true; // Already shutdown
        }
        
        logger.info("Shutting down transfer engine...");
        
        // Cancel all active transfers
        activeContexts.values().forEach(TransferContext::cancel);
        
        // Shutdown executor
        executorService.shutdown();
        
        try {
            boolean terminated = executorService.awaitTermination(timeoutSeconds, TimeUnit.SECONDS);
            if (!terminated) {
                logger.warning("Transfer engine shutdown timed out, forcing shutdown");
                executorService.shutdownNow();
                return false;
            }
            logger.info("Transfer engine shutdown completed");
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            executorService.shutdownNow();
            return false;
        }
    }
    
    private TransferResult executeTransfer(TransferContext context) {
        TransferJob job = context.getJob();
        TransferRequest request = job.getRequest();
        
        logger.info("Starting transfer: " + job.getJobId());
        job.start();
        
        int attempt = 0;
        Exception lastException = null;
        
        while (attempt <= maxRetryAttempts && context.shouldContinue()) {
            try {
                // Get appropriate protocol
                TransferProtocol protocol = protocolFactory.getProtocol(request.getProtocol());
                if (protocol == null) {
                    throw new TransferException(job.getJobId(), "Unsupported protocol: " + request.getProtocol());
                }
                
                // Execute the transfer
                TransferResult result = protocol.transfer(request, context);
                
                if (result.isSuccessful()) {
                    logger.info("Transfer completed successfully: " + job.getJobId());
                    return result;
                } else {
                    throw new TransferException(job.getJobId(), "Transfer failed: " + result.getErrorMessage().orElse("Unknown error"));
                }
                
            } catch (Exception e) {
                lastException = e;
                attempt++;
                context.incrementRetryCount();
                
                logger.log(Level.WARNING, String.format("Transfer attempt %d failed for job %s: %s", 
                        attempt, job.getJobId(), e.getMessage()), e);
                
                if (attempt <= maxRetryAttempts && context.shouldContinue()) {
                    try {
                        Thread.sleep(retryDelayMs * attempt); // Exponential backoff
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        context.cancel();
                        break;
                    }
                }
            }
        }
        
        // All attempts failed
        String errorMessage = lastException != null ? lastException.getMessage() : "Transfer failed after " + maxRetryAttempts + " attempts";
        job.fail(errorMessage, lastException);
        
        logger.severe("Transfer failed permanently: " + job.getJobId() + " - " + errorMessage);
        return job.toResult();
    }
}
