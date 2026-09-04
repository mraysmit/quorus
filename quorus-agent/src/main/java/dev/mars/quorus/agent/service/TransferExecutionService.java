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
    private final boolean closeVertxOnShutdown;
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
        this(vertx, config, false);
    }

    private TransferExecutionService(Vertx vertx, AgentConfiguration config, boolean closeVertxOnShutdown) {
        this.vertx = Objects.requireNonNull(vertx, "Vertx cannot be null");
        this.closeVertxOnShutdown = closeVertxOnShutdown;
        this.config = Objects.requireNonNull(config, "AgentConfiguration cannot be null");
        this.transferEngine = new SimpleTransferEngine(
                vertx,  // Pass Vertx to SimpleTransferEngine
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

    /**
     * Legacy constructor for backward compatibility.
     * @deprecated Use {@link #TransferExecutionService(Vertx, AgentConfiguration)} instead
     */
    @Deprecated
    public TransferExecutionService(AgentConfiguration config) {
        this(Vertx.vertx(), config, true);
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
                   request.getSourceUri(), request.getDestinationUri());

        try {
            return transferEngine.submitTransfer(request)
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

        // Reactively shutdown transfer engine (awaits in-flight transfers, then closes WorkerExecutor)
        return transferEngine.shutdown(30)
                .recover(err -> {
                    logger.warn("Error shutting down transfer engine: {}", err.getMessage());
                    return Future.succeededFuture();
                })
                .compose(v -> closeOwnedVertxIfNeeded())
                .onComplete(ar -> logger.info("Transfer execution service shutdown complete"));
    }

    private Future<Void> closeOwnedVertxIfNeeded() {
        if (!closeVertxOnShutdown) {
            return Future.succeededFuture();
        }

        logger.info("Closing internally managed Vert.x instance for TransferExecutionService");
        return vertx.close()
                .onSuccess(v -> logger.info("Internally managed Vert.x instance closed for TransferExecutionService"))
                .recover(err -> {
                    logger.warn("Failed to close internally managed Vert.x instance: {}", err.getMessage());
                    return Future.succeededFuture();
                });
    }
}
