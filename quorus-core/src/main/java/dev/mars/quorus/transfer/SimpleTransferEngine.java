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


import dev.mars.quorus.core.TransferDirection;
import dev.mars.quorus.core.TransferJob;
import dev.mars.quorus.core.TransferRequest;
import dev.mars.quorus.core.TransferResult;
import dev.mars.quorus.core.exceptions.TransferException;
import dev.mars.quorus.monitoring.ProtocolHealthCheck;
import dev.mars.quorus.monitoring.TransferEngineHealthCheck;
import dev.mars.quorus.protocol.ProtocolFactory;
import dev.mars.quorus.protocol.TransferProtocol;
import dev.mars.quorus.transfer.observability.TransferTelemetryMetrics;
import dev.mars.quorus.util.SensitiveDataRedactor;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Blocking {@link TransferEngine} (ADR-0012, RT-03c).
 *
 * <p>{@link #transfer} runs on the caller's thread: it validates the request, takes one of
 * {@code maxConcurrentTransfers} slots, runs the protocol adapter with retries, and returns the
 * outcome. There is no event loop, executor or future. Concurrency between transfers is the
 * caller's choice, made with a {@code TaskScope}.
 *
 * <h2>Cancellation</h2>
 * {@link #cancelTransfer} marks the transfer's context cancelled and interrupts the thread running
 * it. On a virtual thread, an interrupt also closes a socket the thread is blocked on, so the
 * adapter's I/O ends at once. Only the named transfer is affected; protocol adapters are shared, so
 * their adapter-wide {@code abort()} is deliberately not used. The engine consumes the interrupt it
 * sent before {@code transfer} returns, so the caller's thread is not left interrupted. An interrupt
 * from anywhere else also ends the transfer as {@code CANCELLED}, and stays set for the caller.
 *
 * <h2>Thread safety</h2>
 * Running transfers are held in a concurrent map. Each {@link ActiveTransfer} guards the race
 * between cancellation and completion with its own monitor, so an interrupt can never reach a
 * thread after its transfer has finished.
 *
 * @author Mark Andrew Ray-Smith Cityline Ltd
 * @since 2025-08-17
 * @version 3.0
 */
public class SimpleTransferEngine implements TransferEngine {
    private static final Logger logger = LoggerFactory.getLogger(SimpleTransferEngine.class);
    private static final Tracer tracer = GlobalOpenTelemetry.getTracer("quorus-core");

    private final Map<String, ActiveTransfer> active = new ConcurrentHashMap<>();
    private final Semaphore slots;
    private final ProtocolFactory protocolFactory;
    private final AtomicBoolean shutdown = new AtomicBoolean(false);

    private final int maxConcurrentTransfers;
    private final int maxRetryAttempts;
    private final long retryDelayMs;

    private final Instant startTime = Instant.now();
    private final TransferTelemetryMetrics telemetryMetrics;

    /**
     * Creates an engine without mounted-filesystem security attestations.
     *
     * @param maxConcurrentTransfers transfers that may run at once; further calls are rejected
     * @param maxRetryAttempts       retries after the first attempt fails
     * @param retryDelayMs           base retry delay; attempt {@code n} waits {@code n} times this
     */
    public SimpleTransferEngine(int maxConcurrentTransfers, int maxRetryAttempts, long retryDelayMs) {
        this(maxConcurrentTransfers, maxRetryAttempts, retryDelayMs, null, false, false);
    }

    /** Creates an engine with explicit mounted-filesystem security attestations. */
    public SimpleTransferEngine(int maxConcurrentTransfers, int maxRetryAttempts, long retryDelayMs,
                                String nfsMountRoot, boolean smbMountSecurityVerified,
                                boolean nfsMountSecurityVerified) {
        if (maxConcurrentTransfers < 1) {
            throw new IllegalArgumentException("maxConcurrentTransfers must be at least 1");
        }
        this.maxConcurrentTransfers = maxConcurrentTransfers;
        this.maxRetryAttempts = maxRetryAttempts;
        this.retryDelayMs = retryDelayMs;
        this.slots = new Semaphore(maxConcurrentTransfers);
        this.protocolFactory = new ProtocolFactory(nfsMountRoot, smbMountSecurityVerified, nfsMountSecurityVerified);

        this.telemetryMetrics = TransferTelemetryMetrics.getInstance();
        telemetryMetrics.registerProtocol("http");
        telemetryMetrics.registerProtocol("ftp");
        telemetryMetrics.registerProtocol("sftp");
        telemetryMetrics.registerProtocol("smb");

        logger.info("SimpleTransferEngine initialized: maxConcurrent={}, maxRetries={}, retryDelay={}ms",
                maxConcurrentTransfers, maxRetryAttempts, retryDelayMs);
    }

    @Override
    public TransferResult transfer(TransferRequest request) throws TransferException {
        if (shutdown.get()) {
            throw new TransferException(request.getRequestId(), "Transfer engine is shutdown");
        }
        validateTransferRequest(request);
        if (!slots.tryAcquire()) {
            throw new TransferException(request.getRequestId(), "Maximum concurrent transfers reached");
        }

        TransferJob job = new TransferJob(request);
        ActiveTransfer transfer = new ActiveTransfer(job, new TransferContext(job), Thread.currentThread());
        if (active.putIfAbsent(job.getJobId(), transfer) != null) {
            slots.release();
            throw new TransferException(request.getRequestId(), "A transfer with this ID is already running");
        }
        try {
            if (shutdown.get()) {
                transfer.cancel();          // shutdown began after the first check; do not start work
            }
            telemetryMetrics.recordTransferStarted(request.getProtocol(), request.getDirection().name());
            logger.info("Transfer started: {}", job.getJobId());
            return execute(transfer);
        } finally {
            if (transfer.finish()) {
                Thread.interrupted();       // consume the interrupt cancelTransfer sent to this thread
            }
            active.remove(job.getJobId());
            slots.release();
            transfer.ended.complete(null);
        }
    }

    @Override
    public TransferJob getTransferJob(String jobId) {
        ActiveTransfer transfer = active.get(jobId);
        return transfer == null ? null : transfer.job;
    }

    @Override
    public boolean cancelTransfer(String jobId) {
        ActiveTransfer transfer = active.get(jobId);
        boolean cancelled = transfer != null && transfer.cancel();
        if (cancelled) {
            logger.info("Transfer cancellation requested: {}", jobId);
        }
        return cancelled;
    }

    @Override
    public boolean pauseTransfer(String jobId) {
        ActiveTransfer transfer = active.get(jobId);
        if (transfer == null) {
            return false;
        }
        transfer.context.pause();
        transfer.job.pause();
        return true;
    }

    @Override
    public boolean resumeTransfer(String jobId) {
        ActiveTransfer transfer = active.get(jobId);
        if (transfer == null) {
            return false;
        }
        transfer.context.resume();
        transfer.job.resume();
        return true;
    }

    @Override
    public int getActiveTransferCount() {
        return active.size();
    }

    @Override
    public boolean shutdown(Duration timeout) {
        if (shutdown.compareAndSet(false, true)) {
            logger.info("Shutting down transfer engine; cancelling {} running transfer(s)", active.size());
        }
        List<ActiveTransfer> running = List.copyOf(active.values());
        running.forEach(ActiveTransfer::cancel);
        CompletableFuture<?>[] ended = running.stream().map(t -> t.ended).toArray(CompletableFuture[]::new);
        try {
            CompletableFuture.allOf(ended).get(timeout.toMillis(), TimeUnit.MILLISECONDS);
            logger.info("Transfer engine shutdown completed");
            return true;
        } catch (TimeoutException e) {
            logger.warn("Transfer engine shutdown timed out with {} transfer(s) still running", active.size());
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return active.isEmpty();
        } catch (ExecutionException e) {
            throw new IllegalStateException("A transfer end signal failed", e);   // never completed exceptionally
        }
    }

    /**
     * Checks if the transfer engine is in shutdown state.
     *
     * @return true if shutdown has been initiated
     */
    public boolean isShutdown() {
        return shutdown.get();
    }

    @Override
    public TransferEngineHealthCheck getHealthCheck() {
        TransferEngineHealthCheck.Builder builder = TransferEngineHealthCheck.builder();

        // Check if engine is shutdown
        if (shutdown.get()) {
            return builder
                    .down()
                    .message("Transfer engine is shutdown")
                    .build();
        }

        // Check protocol health from OTel metrics
        boolean allProtocolsHealthy = true;
        Map<String, TransferTelemetryMetrics.ProtocolStats> allStats = telemetryMetrics.getAllProtocolStats();

        for (var entry : allStats.entrySet()) {
            String protocolName = entry.getKey();
            TransferTelemetryMetrics.ProtocolStats stats = entry.getValue();

            ProtocolHealthCheck.Builder protocolBuilder = ProtocolHealthCheck.builder(protocolName);
            long totalTransfers = stats.totalTransfers();
            long failedTransfers = stats.failedTransfers();

            if (totalTransfers > 0) {
                double failureRate = (failedTransfers * 100.0) / totalTransfers;
                if (failureRate > 50) {
                    protocolBuilder.down()
                            .message("High failure rate: " + String.format("%.2f%%", failureRate));
                    allProtocolsHealthy = false;
                } else if (failureRate > 20) {
                    protocolBuilder.degraded()
                            .message("Elevated failure rate: " + String.format("%.2f%%", failureRate));
                    allProtocolsHealthy = false;
                } else {
                    protocolBuilder.up()
                            .message("Protocol operational");
                }
            } else {
                protocolBuilder.up()
                        .message("No transfers yet");
            }

            protocolBuilder.detail("totalTransfers", totalTransfers)
                    .detail("failedTransfers", failedTransfers)
                    .detail("activeTransfers", stats.activeTransfers());

            builder.addProtocolHealthCheck(protocolBuilder.build());
        }

        // System metrics
        Runtime runtime = Runtime.getRuntime();
        builder.systemMetric("activeTransfers", active.size())
                .systemMetric("maxConcurrentTransfers", maxConcurrentTransfers)
                .systemMetric("uptime", Duration.between(startTime, Instant.now()).toString())
                .systemMetric("memoryUsedMB", (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024))
                .systemMetric("memoryTotalMB", runtime.totalMemory() / (1024 * 1024))
                .systemMetric("memoryMaxMB", runtime.maxMemory() / (1024 * 1024));

        // Overall status
        if (allProtocolsHealthy) {
            builder.up().message("All systems operational");
        } else {
            builder.degraded().message("Some protocols experiencing issues");
        }

        return builder.build();
    }

    /** Runs the attempts of one transfer on the current thread and returns its outcome. */
    private TransferResult execute(ActiveTransfer transfer) {
        TransferJob job = transfer.job;
        TransferRequest request = job.getRequest();
        TransferDirection direction = request.getDirection();
        String protocolName = request.getProtocol();
        logger.debug("Executing {} transfer {}: protocol={}, source={}, destination={}", direction,
                job.getJobId(), protocolName, SensitiveDataRedactor.redactUri(request.getSourceUri()),
                SensitiveDataRedactor.redactUri(request.getDestinationUri()));
        job.start();

        Span span = tracer.spanBuilder("quorus.transfer")
                .setSpanKind(SpanKind.INTERNAL)
                .setAttribute("transfer.id", job.getJobId())
                .setAttribute("transfer.protocol", protocolName)
                .setAttribute("transfer.direction", direction.name())
                .startSpan();
        Instant transferStart = Instant.now();
        try (Scope ignored = span.makeCurrent()) {
            Throwable lastError = null;
            for (int attempt = 0; ; attempt++) {
                if (stopRequested(transfer)) {
                    return stopped(transfer, span, lastError);
                }
                TransferProtocol protocol = protocolFactory.getProtocol(protocolName);
                if (protocol == null || !protocol.canHandle(request)) {
                    return fail(job, span, new TransferException(job.getJobId(),
                            "Protocol '" + protocolName + "' cannot handle request direction/URI combination"));
                }
                try {
                    TransferResult result = runAttempt(protocol, request, transfer.context);
                    if (!result.isSuccessful()) {
                        throw new TransferException(job.getJobId(),
                                "Transfer failed: " + result.getErrorMessage().orElse("Unknown error"));
                    }
                    return completed(job, span, result, transferStart);
                } catch (Exception failure) {
                    lastError = failure;
                    transfer.context.incrementRetryCount();
                    if (stopRequested(transfer)) {
                        return stopped(transfer, span, lastError);
                    }
                    if (attempt >= maxRetryAttempts) {
                        return fail(job, span, failure);
                    }
                    logger.warn("Transfer attempt {} of {} failed for job {}: {}", attempt + 1,
                            maxRetryAttempts + 1, job.getJobId(), failure.getMessage());
                    telemetryMetrics.recordRetryAttempt(protocolName, direction.name(), attempt + 1);
                    if (!backOff(attempt + 1)) {
                        return stopped(transfer, span, lastError);
                    }
                }
            }
        }
    }

    /**
     * Runs one attempt through the adapter's blocking entry point. It is deprecated only in favour of
     * the Vert.x {@code transferReactive}, which RT-03d removes together with the deprecation.
     */
    @SuppressWarnings("deprecation")
    private static TransferResult runAttempt(TransferProtocol protocol, TransferRequest request,
                                             TransferContext context) throws TransferException {
        return protocol.transfer(request, context);
    }

    /** Waits before retry {@code retry}; returns {@code false} if interrupted, restoring the interrupt. */
    private boolean backOff(int retry) {
        long delay = retryDelayMs * retry;
        logger.debug("Retrying after {} ms", delay);
        try {
            Thread.sleep(delay);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private static boolean stopRequested(ActiveTransfer transfer) {
        return !transfer.context.shouldContinue() || Thread.currentThread().isInterrupted();
    }

    /** Ends a transfer that was cancelled, interrupted or paused. */
    private TransferResult stopped(ActiveTransfer transfer, Span span, Throwable lastError) {
        TransferJob job = transfer.job;
        TransferRequest request = job.getRequest();
        telemetryMetrics.recordTransferCancelled(request.getProtocol(), request.getDirection().name());
        if (transfer.context.isCancelled() || Thread.currentThread().isInterrupted()) {
            job.cancel();
            logger.info("Transfer cancelled: {}", job.getJobId());
        } else {
            job.fail("Transfer paused before completion", lastError);
            logger.warn("Transfer paused before completion and was stopped: {}", job.getJobId());
        }
        span.setStatus(StatusCode.ERROR, "Transfer stopped: " + job.getStatus());
        span.end();
        TransferResult result = job.toResult();
        if (result.getErrorMessage().isPresent()) {
            return result;
        }
        // A cancelled job has no error message of its own, and consumers such as the agent report
        // the message of any unsuccessful result, so the reason is stated here.
        return TransferResult.builder()
                .requestId(result.getRequestId())
                .finalStatus(result.getFinalStatus())
                .bytesTransferred(result.getBytesTransferred())
                .startTime(result.getStartTime().orElse(null))
                .endTime(result.getEndTime().orElse(null))
                .actualChecksum(result.getActualChecksum().orElse(null))
                .errorMessage("Transfer cancelled")
                .cause(lastError)
                .build();
    }

    private TransferResult completed(TransferJob job, Span span, TransferResult result, Instant transferStart) {
        TransferRequest request = job.getRequest();
        Duration duration = Duration.between(transferStart, Instant.now());
        telemetryMetrics.recordTransferCompleted(request.getProtocol(), request.getDirection().name(),
                result.getBytesTransferred(), duration.toMillis() / 1000.0);
        span.setAttribute("transfer.bytes", result.getBytesTransferred());
        span.setAttribute("transfer.duration_ms", duration.toMillis());
        span.setStatus(StatusCode.OK);
        span.end();
        logger.info("{} transfer completed: {} ({} bytes in {} ms)", request.getDirection(), job.getJobId(),
                result.getBytesTransferred(), duration.toMillis());
        return result;
    }

    private TransferResult fail(TransferJob job, Span span, Throwable error) {
        TransferRequest request = job.getRequest();
        job.fail(error.getMessage(), error);
        telemetryMetrics.recordTransferFailed(request.getProtocol(), request.getDirection().name(),
                error.getClass().getSimpleName());
        span.setStatus(StatusCode.ERROR, error.getMessage());
        span.recordException(error);
        span.end();
        logger.error("{} transfer failed permanently: {} - {}", request.getDirection(), job.getJobId(),
                error.getMessage());
        logger.debug("Failure detail for transfer {}", job.getJobId(), error);
        return job.toResult();
    }

    /**
     * Validates that the transfer request is supported.
     *
     * @param request the transfer request to validate
     * @throws TransferException if the request is invalid
     */
    private void validateTransferRequest(TransferRequest request) throws TransferException {
        URI source = request.getSourceUri();
        URI dest = request.getDestinationUri();

        if (source == null) {
            throw new TransferException(request.getRequestId(), "Source URI cannot be null");
        }
        if (dest == null) {
            throw new TransferException(request.getRequestId(), "Destination URI cannot be null");
        }

        boolean sourceIsFile = "file".equalsIgnoreCase(source.getScheme());
        boolean destIsFile = "file".equalsIgnoreCase(dest.getScheme());

        // At least one must be file://
        if (!sourceIsFile && !destIsFile) {
            throw new TransferException(request.getRequestId(),
                "At least one endpoint must be file:// (local filesystem). " +
                "Remote-to-remote transfers not yet supported.");
        }

        // Both can't be file:// (use Files.copy instead)
        if (sourceIsFile && destIsFile) {
            throw new TransferException(request.getRequestId(),
                "Both source and destination are local files. Use Files.copy() for local file-to-file operations.");
        }

        // Validate configured protocol (protocol is authoritative from job configuration)
        String configuredProtocol = request.getProtocol();
        if (!protocolFactory.isProtocolSupported(configuredProtocol)) {
            throw new TransferException(request.getRequestId(),
                "Unsupported protocol: " + configuredProtocol + ". Supported: " +
                String.join(", ", protocolFactory.getSupportedProtocols()));
        }

        TransferProtocol protocol = protocolFactory.getProtocol(configuredProtocol);
        if (protocol == null || !protocol.canHandle(request)) {
            throw new TransferException(request.getRequestId(),
                "Configured protocol '" + configuredProtocol + "' cannot handle this request direction or URIs");
        }
    }

    /**
     * One running transfer and the thread that runs it. Its monitor orders cancellation against
     * completion: {@link #cancel()} interrupts only while the transfer has not finished, and
     * {@link #finish()} reports whether an interrupt was sent, so the transfer can consume it.
     */
    private static final class ActiveTransfer {
        final TransferJob job;
        final TransferContext context;
        final CompletableFuture<Void> ended = new CompletableFuture<>();
        private final Thread thread;
        private boolean finished;
        private boolean interruptSent;

        ActiveTransfer(TransferJob job, TransferContext context, Thread thread) {
            this.job = job;
            this.context = context;
            this.thread = thread;
        }

        synchronized boolean cancel() {
            if (finished) {
                return false;
            }
            context.cancel();
            if (!interruptSent) {
                interruptSent = true;
                thread.interrupt();
            }
            return true;
        }

        /** Marks the transfer finished; returns {@code true} if {@link #cancel()} interrupted its thread. */
        synchronized boolean finish() {
            finished = true;
            return interruptSent;
        }
    }
}
