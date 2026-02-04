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
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Service for executing file transfer operations.
 * Converted to Vert.x reactive patterns 
 *
 * @author Mark Andrew Ray-Smith Cityline Ltd
 * @since 2025-09-04
 * @version 1.0
 */
public class TransferExecutionService {

    private static final Logger logger = LoggerFactory.getLogger(TransferExecutionService.class);

    private final Vertx vertx;
    private final AgentConfiguration config;
    private final TransferEngine transferEngine;

    private final AtomicBoolean closed = new AtomicBoolean(false);
    private volatile boolean running = false;

    /**
     * Constructor with Vert.x dependency injection.
     *
     * @param vertx Vert.x instance for reactive operations
     * @param config Agent configuration
     */
    public TransferExecutionService(Vertx vertx, AgentConfiguration config) {
        this.vertx = Objects.requireNonNull(vertx, "Vertx cannot be null");
        this.config = Objects.requireNonNull(config, "AgentConfiguration cannot be null");
        this.transferEngine = new SimpleTransferEngine(
                vertx,  // Pass Vertx to SimpleTransferEngine
                config.getMaxConcurrentTransfers(),
                3,      // maxRetryAttempts
                1000    // retryDelayMs
        );

        logger.info("TransferExecutionService initialized (Vert.x reactive mode)");
    }

    /**
     * Legacy constructor for backward compatibility.
     * @deprecated Use {@link #TransferExecutionService(Vertx, AgentConfiguration)} instead
     */
    @Deprecated
    public TransferExecutionService(AgentConfiguration config) {
        this(Vertx.vertx(), config);
        logger.warn("Using deprecated constructor - Vert.x instance created internally");
    }
    
    public void start() {
        if (closed.get()) {
            throw new IllegalStateException("TransferExecutionService is closed");
        }

        running = true;
        logger.info("Transfer execution service started with {} max concurrent transfers",
                   config.getMaxConcurrentTransfers());
    }
    
    public Future<TransferResult> executeTransfer(TransferRequest request) {
        if (!running) {
            return Future.failedFuture(
                new IllegalStateException("Transfer execution service is not running"));
        }

        logger.info("Executing transfer: {} -> {}",
                   request.getSourceUri(), request.getDestinationPath());

        try {
            return transferEngine.submitTransfer(request)
                .onComplete(ar -> {
                    if (ar.failed()) {
                        logger.error("Transfer failed: " + request.getRequestId(), ar.cause());
                    } else {
                        TransferResult result = ar.result();
                        if (result.isSuccessful()) {
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
                    }
                });
        } catch (Exception e) {
            logger.error("Failed to submit transfer: " + request.getRequestId(), e);
            return Future.failedFuture(e);
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
        if (closed.getAndSet(true)) {
            return; // Already shutdown
        }

        logger.info("Shutting down transfer execution service...");
        running = false;

        // Shutdown transfer engine (which will close Vert.x WorkerExecutor)
        transferEngine.shutdown(30); // 30 seconds timeout

        logger.info("Transfer execution service shutdown complete");
    }
}
