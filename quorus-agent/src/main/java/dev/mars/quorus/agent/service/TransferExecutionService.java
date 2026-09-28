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
import dev.mars.quorus.core.exceptions.TransferException;
import dev.mars.quorus.transfer.SimpleTransferEngine;
import dev.mars.quorus.transfer.TransferEngine;
import dev.mars.quorus.connection.ConnectionAccessRequest;
import dev.mars.quorus.connection.HostResolver;
import dev.mars.quorus.connection.SecretProvider;
import dev.mars.quorus.connection.ServiceConnection;
import dev.mars.quorus.connection.VaultKvV2SecretProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.net.URI;
import java.time.Duration;
import java.util.List;

/**
 * Service for executing file transfer operations. Transfers run on the calling thread (RT-05b); the
 * agent calls from a virtual thread per job, so the engine's cancellation interrupt, sent on
 * {@link #shutdown()}, breaks a transfer blocked in a socket read at once.
 *
 * @author Mark Andrew Ray-Smith Cityline Ltd
 * @since 2025-09-04
 * @version 2.0
 */
public class TransferExecutionService {

    private static final Logger logger = LoggerFactory.getLogger(TransferExecutionService.class);

    /** How long {@link #shutdown()} waits for cancelled transfers to end. */
    public static final Duration SHUTDOWN_BOUND = Duration.ofSeconds(30);

    private final AgentConfiguration config;
    private final TransferEngine transferEngine;
    private final AgentConnectionPolicyService connectionPolicyService;
    private final AgentLocalPathPolicy localPathPolicy;

    private final AtomicBoolean closed = new AtomicBoolean(false);
    private volatile boolean running = false;

    /**
     * Runs once a governed assignment is authorized and before its transfer starts; the agent uses it
     * to acknowledge the start to the controller. A failure prevents the transfer.
     */
    @FunctionalInterface
    public interface AuthorizedStart {
        void run() throws Exception;
    }

    public TransferExecutionService(AgentConfiguration config) {
        this.config = Objects.requireNonNull(config, "AgentConfiguration cannot be null");
        this.transferEngine = new SimpleTransferEngine(
                config.getMaxConcurrentTransfers(),
                3,      // maxRetryAttempts
                1000,   // retryDelayMs
                config.getNfsMountRoot(),
                config.isSmbMountSecurityVerified(),
                config.isNfsMountSecurityVerified()
        );
        this.connectionPolicyService = createConnectionPolicyService(config);
        this.localPathPolicy = new AgentLocalPathPolicy(config.getUploadRoot(), config.getDownloadRoot());
        logger.info("TransferExecutionService initialized");
    }

    public void start() {
        if (closed.get()) {
            throw new IllegalStateException("TransferExecutionService is closed");
        }
        running = true;
        logger.info("Transfer execution service started with {} max concurrent transfers",
                   config.getMaxConcurrentTransfers());
    }

    /**
     * Runs a transfer on the calling thread.
     *
     * @throws IllegalStateException if the service is not running
     * @throws TransferException     if the engine could not run it
     */
    public TransferResult executeTransfer(TransferRequest request) throws TransferException {
        if (!running) {
            throw new IllegalStateException("Transfer execution service is not running");
        }
        logger.info("Executing transfer: {} -> {}", request.getSourceUri(), request.getDestinationUri());
        try {
            TransferResult result = transferEngine.transfer(request);
            if (result.isSuccessful()) {
                logger.info("Transfer completed successfully: {} ({} bytes in {})", request.getRequestId(),
                        result.getBytesTransferred(), result.getDuration().map(d -> d.toMillis() + "ms").orElse("unknown"));
            } else {
                logger.warn("Transfer failed: {} - {}", request.getRequestId(),
                        result.getErrorMessage().orElse("Unknown error"));
            }
            return result;
        } catch (TransferException | RuntimeException e) {
            logger.error("Transfer failed: {}", request.getRequestId());
            logger.debug("Stack trace for transfer failure: requestId={}", request.getRequestId(), e);
            throw e;
        }
    }

    /** Resolves and authorizes a governed assignment on the executing agent, then runs it. */
    public TransferResult executeTransfer(JobPollingService.PendingJob pendingJob) throws Exception {
        return executeTransfer(pendingJob, () -> { });
    }

    /**
     * Runs an assignment on the calling thread. A governed assignment's local path, connection policy
     * and secret are resolved first; {@code onAuthorized} runs only once they succeed, and the
     * transfer only once it returns. The resolved credential is closed when the transfer ends.
     *
     * @throws SecurityException if a production agent receives an assignment that is not governed
     * @throws Exception         from authorization, from {@code onAuthorized}, or from the engine
     */
    public TransferResult executeTransfer(JobPollingService.PendingJob pendingJob, AuthorizedStart onAuthorized)
            throws Exception {
        if (!pendingJob.isGoverned()) {
            if ("production".equalsIgnoreCase(config.getSecurityProfile())) {
                throw new SecurityException("Production agents reject assignments without a governed service connection");
            }
            TransferRequest request = pendingJob.toTransferRequest();
            onAuthorized.run();
            return executeTransfer(request);
        }
        var direction = pendingJob.direction();
        URI source = URI.create(pendingJob.getSourceUri());
        URI destination = URI.create(pendingJob.getDestinationPath());
        localPathPolicy.authorize("file".equalsIgnoreCase(source.getScheme()) ? source : destination, direction);
        var connection = AgentConnectionPolicyService.parseConnection(pendingJob.getServiceConnection());
        var reference = AgentConnectionPolicyService.parseSecret(pendingJob.getSecretReference());
        var access = new ConnectionAccessRequest(pendingJob.getTenantId(), pendingJob.getRemotePath(),
                direction == dev.mars.quorus.core.TransferDirection.DOWNLOAD
                        ? ServiceConnection.Direction.DOWNLOAD : ServiceConnection.Direction.UPLOAD,
                config.getAgentPool(), config.getNetworkZone(), pendingJob.getControllerResolvedAddresses());
        try (var authorized = connectionPolicyService.authorize(connection, reference, access,
                pendingJob.getConnectionPolicyVersion(), pendingJob.getConnectionPolicyDigest())) {
            TransferRequest request = pendingJob.toAuthorizedTransferRequest(
                    authorized.resolved().authorization().endpoint(), authorized.runtimeCredential(), localPathPolicy);
            onAuthorized.run();
            return executeTransfer(request);
        }
    }

    private static AgentConnectionPolicyService createConnectionPolicyService(AgentConfiguration config) {
        String address = config.getVaultAddress();
        String token = config.getVaultToken();
        List<SecretProvider> providers = List.of();
        if (address != null && !address.isBlank() && token != null && !token.isBlank()) {
            providers = List.of(VaultKvV2SecretProvider.usingHttpClient(URI.create(address),
                    () -> token.toCharArray(), Duration.ofMillis(config.getHttpConnectionTimeout())));
        }
        return new AgentConnectionPolicyService(HostResolver.system(), providers);
    }

    /**
     * Returns the bytes moved so far by the running transfer with this job ID, or -1 if no such
     * transfer is running. Read by the agent's progress reports (ENG-10).
     */
    public long transferredBytes(String jobId) {
        var job = transferEngine.getTransferJob(jobId);
        return job == null ? -1 : job.getBytesTransferred();
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

    /**
     * Refuses new transfers, cancels running ones and waits up to {@link #SHUTDOWN_BOUND} for them to
     * end. Blocks the calling thread; never throws. Does nothing if already shut down.
     */
    public void shutdown() {
        if (closed.getAndSet(true)) {
            return;
        }
        logger.info("Shutting down transfer execution service...");
        running = false;
        try {
            if (!transferEngine.shutdown(SHUTDOWN_BOUND)) {
                logger.warn("Transfer engine shutdown timed out with transfers still running");
            }
        } catch (RuntimeException e) {
            logger.warn("Error shutting down transfer engine: {}", e.getMessage());
        }
        logger.info("Transfer execution service shutdown complete");
    }
}
