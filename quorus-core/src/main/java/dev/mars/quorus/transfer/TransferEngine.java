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
import dev.mars.quorus.core.exceptions.TransferException;

import java.util.concurrent.CompletableFuture;

/**
 * Core interface for the file transfer engine.
 * Defines the contract for initiating, monitoring, and controlling file transfers.
 */
public interface TransferEngine {
    
    /**
     * Submit a transfer request for asynchronous execution.
     *
     * @param request the transfer request containing source, destination, and metadata
     * @return a CompletableFuture that will complete with the transfer result
     * @throws TransferException if the transfer cannot be initiated
     */
    CompletableFuture<TransferResult> submitTransfer(TransferRequest request) throws TransferException;
    
    /**
     * Get the current status of an active transfer job.
     *
     * @param jobId the unique identifier of the transfer job
     * @return the transfer job if found, null otherwise
     */
    TransferJob getTransferJob(String jobId);
    
    /**
     * Cancel an active transfer.
     * 
     * @param jobId the unique identifier of the transfer job to cancel
     * @return true if the transfer was successfully cancelled, false if not found or already completed
     */
    boolean cancelTransfer(String jobId);
    
    /**
     * Pause an active transfer.
     * 
     * @param jobId the unique identifier of the transfer job to pause
     * @return true if the transfer was successfully paused, false if not found or not pausable
     */
    boolean pauseTransfer(String jobId);
    
    /**
     * Resume a paused transfer.
     * 
     * @param jobId the unique identifier of the transfer job to resume
     * @return true if the transfer was successfully resumed, false if not found or not resumable
     */
    boolean resumeTransfer(String jobId);
    
    /**
     * Get the number of currently active transfers.
     * 
     * @return the count of active transfers
     */
    int getActiveTransferCount();
    
    /**
     * Shutdown the transfer engine gracefully, completing active transfers.
     * 
     * @param timeoutSeconds maximum time to wait for active transfers to complete
     * @return true if shutdown completed within timeout, false otherwise
     */
    boolean shutdown(long timeoutSeconds);
}
