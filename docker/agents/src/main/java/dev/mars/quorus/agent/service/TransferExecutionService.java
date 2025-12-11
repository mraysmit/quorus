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

package dev.mars.quorus.agent.service;

import dev.mars.quorus.agent.config.AgentConfiguration;
import dev.mars.quorus.core.TransferRequest;
import dev.mars.quorus.core.TransferResult;
import dev.mars.quorus.transfer.SimpleTransferEngine;
import dev.mars.quorus.transfer.TransferEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Service for executing file transfer operations.
 * 
 * @author Mark Andrew Ray-Smith Cityline Ltd
 * @since 1.0
 */
public class TransferExecutionService {
    
    private static final Logger logger = LoggerFactory.getLogger(TransferExecutionService.class);
    
    private final AgentConfiguration config;
    private final TransferEngine transferEngine;
    private final ExecutorService executorService;
    
    private volatile boolean running = false;
    
    public TransferExecutionService(AgentConfiguration config) {
        this.config = config;
        this.transferEngine = new SimpleTransferEngine(
                config.getMaxConcurrentTransfers(),
                3,      // maxRetryAttempts
                1000    // retryDelayMs
        );
        this.executorService = Executors.newFixedThreadPool(config.getMaxConcurrentTransfers());
    }
    
    public void start() {
        running = true;
        logger.info("Transfer execution service started with {} max concurrent transfers", 
                   config.getMaxConcurrentTransfers());
    }
    
    public CompletableFuture<TransferResult> executeTransfer(TransferRequest request) {
        if (!running) {
            return CompletableFuture.failedFuture(
                new IllegalStateException("Transfer execution service is not running"));
        }

        logger.info("Executing transfer: {} -> {}",
                   request.getSourceUri(), request.getDestinationPath());

        try {
            return transferEngine.submitTransfer(request)
                .whenComplete((result, throwable) -> {
                    if (throwable != null) {
                        logger.error("Transfer failed: " + request.getRequestId(), throwable);
                    } else if (result.isSuccessful()) {
                        String durationStr = result.getDuration()
                                .map(d -> d.toMillis() + "ms")
                                .orElse("unknown");
                        logger.info("Transfer completed successfully: {} ({} bytes in {})",
                                   request.getRequestId(),
                                   result.getBytesTransferred(),
                                   durationStr);
                    } else {
                        logger.warn("Transfer failed: {} - {}",
                                   request.getRequestId(),
                                   result.getErrorMessage().orElse("Unknown error"));
                    }
                });
        } catch (Exception e) {
            logger.error("Failed to submit transfer: " + request.getRequestId(), e);
            return CompletableFuture.failedFuture(e);
        }
    }
    
    public boolean canAcceptTransfer() {
        // Check if we have capacity for more transfers
        // This is a simplified check - in reality, we'd track active transfers
        return running;
    }
    
    public int getActiveTransferCount() {
        // TODO: Implement actual tracking of active transfers
        return 0;
    }
    
    public int getAvailableCapacity() {
        return config.getMaxConcurrentTransfers() - getActiveTransferCount();
    }
    
    public void shutdown() {
        if (!running) {
            return;
        }
        
        logger.info("Shutting down transfer execution service...");
        running = false;
        
        try {
            // Shutdown executor service
            executorService.shutdown();
            if (!executorService.awaitTermination(30, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
            
            // Shutdown transfer engine
            transferEngine.shutdown(30); // 30 seconds timeout
            
            logger.info("Transfer execution service shutdown complete");
            
        } catch (Exception e) {
            logger.error("Error during transfer execution service shutdown", e);
        }
    }
}
