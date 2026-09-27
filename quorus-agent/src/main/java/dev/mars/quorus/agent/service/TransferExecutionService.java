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
import dev.mars.quorus.connection.ConnectionAccessRequest;
import dev.mars.quorus.connection.HostResolver;
import dev.mars.quorus.connection.SecretProvider;
import dev.mars.quorus.connection.ServiceConnection;
import dev.mars.quorus.connection.VaultKvV2SecretProvider;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.function.Supplier;

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
    private final AgentConnectionPolicyService connectionPolicyService;
    private final AgentLocalPathPolicy localPathPolicy;

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
                config.getMaxConcurrentTransfers(),
                3,      // maxRetryAttempts
                1000,   // retryDelayMs
                config.getNfsMountRoot(),
                config.isSmbMountSecurityVerified(),
                config.isNfsMountSecurityVerified()
        );
        this.connectionPolicyService = createConnectionPolicyService(config);
        this.localPathPolicy = new AgentLocalPathPolicy(config.getUploadRoot(), config.getDownloadRoot());

        logger.info("TransferExecutionService initialized (Vert.x reactive mode)");
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
                   request.getSourceUri(), request.getDestinationUri());

        try {
            // The engine is blocking (RT-03c). Until this module leaves Vert.x (RT-05), the transfer
            // runs on a Vert.x worker, as the engine's own executeBlocking did before.
            return vertx.executeBlocking(() -> transferEngine.transfer(request), false)
                .onComplete(ar -> {
                    if (ar.failed()) {
                        logger.error("Transfer failed: {}", request.getRequestId());
                        logger.debug("Stack trace for transfer failure: requestId={}", request.getRequestId(), ar.cause());
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
            logger.error("Failed to submit transfer: {}", request.getRequestId());
            logger.debug("Stack trace for transfer submission failure: requestId={}", request.getRequestId(), e);
            return Future.failedFuture(e);
        }
    }

    /** Resolves and authorizes a governed assignment on the executing agent. */
    public Future<TransferResult> executeTransfer(JobPollingService.PendingJob pendingJob) {
        return executeTransfer(pendingJob, () -> Future.succeededFuture());
    }

    /** Invokes the acknowledgement only after governed policy and secret resolution succeed. */
    public Future<TransferResult> executeTransfer(JobPollingService.PendingJob pendingJob,
                                                   Supplier<Future<Void>> onAuthorized) {
        if (!pendingJob.isGoverned()) {
            if ("production".equalsIgnoreCase(config.getSecurityProfile())) {
                return Future.failedFuture(new SecurityException(
                        "Production agents reject assignments without a governed service connection"));
            }
            try {
                TransferRequest request = pendingJob.toTransferRequest();
                return onAuthorized.get().compose(ignored -> executeTransfer(request));
            } catch (Exception failure) {
                return Future.failedFuture(failure);
            }
        }
        return vertx.executeBlocking(() -> {
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
            var authorized = connectionPolicyService.authorize(connection, reference, access,
                    pendingJob.getConnectionPolicyVersion(), pendingJob.getConnectionPolicyDigest());
            try {
                TransferRequest request = pendingJob.toAuthorizedTransferRequest(
                        authorized.resolved().authorization().endpoint(), authorized.runtimeCredential(), localPathPolicy);
                return new PreparedTransfer(request, authorized);
            } catch (Exception failure) {
                authorized.close();
                throw failure;
            }
        }, false).compose(prepared -> Future.<Void>succeededFuture()
                .compose(ignored -> onAuthorized.get())
                .compose(ignored -> executeTransfer(prepared.request()))
                .onComplete(ignored -> prepared.authorization().close()));
    }

    private record PreparedTransfer(TransferRequest request,
                                    AgentConnectionPolicyService.AuthorizedConnection authorization) { }

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
    
    public Future<Void> shutdown() {
        if (closed.getAndSet(true)) {
            return Future.succeededFuture(); // Already shutdown
        }

        logger.info("Shutting down transfer execution service...");
        running = false;

        // The engine's shutdown blocks until running transfers end, so it runs on a worker.
        return vertx.executeBlocking(() -> {
                    stopTransferEngine();
                    return null;
                }, false)
                .<Void>mapEmpty()
                .onComplete(ar -> logger.info("Transfer execution service shutdown complete"));
    }

    /** Stops the engine, waiting up to 30 seconds for running transfers to end. Never throws. */
    private void stopTransferEngine() {
        try {
            if (!transferEngine.shutdown(Duration.ofSeconds(30))) {
                logger.warn("Transfer engine shutdown timed out with transfers still running");
            }
        } catch (RuntimeException e) {
            logger.warn("Error shutting down transfer engine: {}", e.getMessage());
        }
    }
}
