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
import dev.mars.quorus.monitoring.TransferEngineHealthCheck;

import java.time.Duration;

/**
 * Runs file transfers.
 *
 * <p>The API is blocking (ADR-0012, plan item RT-03c). {@link #transfer} runs the transfer on the
 * calling thread, which should be a virtual thread, and returns when it has ended. A caller that
 * wants several transfers at once forks them in a {@code TaskScope}. Cancellation interrupts the
 * thread running the named transfer and affects no other transfer.
 *
 * @author Mark Andrew Ray-Smith Cityline Ltd
 * @version 3.0
 * @since 2025-08-17
 */
public interface TransferEngine {

    /**
     * Runs a transfer on the calling thread and returns its outcome.
     *
     * <p>A transfer that fails, is cancelled, or runs out of retries still returns a result: its
     * {@link TransferResult#getFinalStatus() final status} is {@code FAILED} or {@code CANCELLED}.
     * If the calling thread is interrupted by anything other than {@link #cancelTransfer}, the
     * transfer ends as {@code CANCELLED} and the thread's interrupt status is left set.
     *
     * @param request the transfer to run; its request ID is the job ID
     * @return the outcome
     * @throws TransferException if the request is invalid, the engine is shut down, the
     *                           concurrency limit is reached, or a transfer with the same ID is running
     */
    TransferResult transfer(TransferRequest request) throws TransferException;

    /** Returns the running transfer's job, or {@code null} if no transfer with that ID is running. */
    TransferJob getTransferJob(String jobId);

    /**
     * Cancels a running transfer by interrupting the thread that runs it. Other transfers are not
     * affected. The cancelled {@link #transfer} call returns promptly with status {@code CANCELLED}.
     *
     * @return {@code true} if a running transfer was cancelled
     */
    boolean cancelTransfer(String jobId);

    boolean pauseTransfer(String jobId);

    boolean resumeTransfer(String jobId);

    int getActiveTransferCount();

    /**
     * Stops accepting transfers, cancels the running ones, and waits for them to end.
     * Calling it again is harmless.
     *
     * @param timeout how long to wait for running transfers to end
     * @return {@code true} if no transfer is still running when it returns
     */
    boolean shutdown(Duration timeout);

    /**
     * Get comprehensive health check for the transfer engine.
     * Includes protocol health status and system metrics.
     *
     * @return health check result
     * @since 2.0 (Phase 2 - Dec 2025)
     */
    TransferEngineHealthCheck getHealthCheck();
}
