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
import dev.mars.quorus.monitoring.TransferMetrics;
import io.vertx.core.Future;

import java.util.Map;

/**
 * Engine interface for managing file transfers.
 *
 * @author Mark Andrew Ray-Smith Cityline Ltd
 * @version 2.0
 * @since 2025-08-17
 */
public interface TransferEngine {

    Future<TransferResult> submitTransfer(TransferRequest request) throws TransferException;

    TransferJob getTransferJob(String jobId);

    boolean cancelTransfer(String jobId);

    boolean pauseTransfer(String jobId);

    boolean resumeTransfer(String jobId);

    int getActiveTransferCount();

    /**
     * Shutdown the transfer engine gracefully, completing active transfers.
     *
     * @param timeoutSeconds maximum time to wait for active transfers to complete
     * @return true if shutdown completed within timeout, false otherwise
     */
    boolean shutdown(long timeoutSeconds);

    /**
     * Get comprehensive health check for the transfer engine.
     * Includes protocol health status and system metrics.
     *
     * @return health check result
     * @since 2.0 (Phase 2 - Dec 2025)
     */
    TransferEngineHealthCheck getHealthCheck();

    /**
     * Get metrics for a specific protocol.
     *
     * @param protocolName protocol name (e.g., "http", "ftp", "sftp", "smb")
     * @return metrics for the protocol, or null if protocol not found
     * @since 2.0 (Phase 2 - Dec 2025)
     */
    TransferMetrics getProtocolMetrics(String protocolName);

    /**
     * Get metrics for all protocols.
     *
     * @return map of protocol name to metrics
     * @since 2.0 (Phase 2 - Dec 2025)
     */
    Map<String, TransferMetrics> getAllProtocolMetrics();
}
