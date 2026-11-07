/* Copyright 2026 Mark Andrew Ray-Smith Cityline Ltd. Licensed under Apache-2.0. */
package dev.mars.quorus.transfer;

import dev.mars.quorus.core.TransferRequest;
import dev.mars.quorus.core.TransferResult;
import dev.mars.quorus.core.TransferStatus;
import dev.mars.quorus.monitoring.ProtocolHealthCheck;
import dev.mars.quorus.protocol.ProtocolFactory;
import dev.mars.quorus.protocol.TransferProtocol;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Register item ENG-11: an engine's health check reports its own transfers only. Before the fix the
 * statistics lived in a JVM-wide singleton, so every engine in a process saw every other engine's protocols.
 */
@Timeout(value = 30, unit = SECONDS, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
class SimpleTransferEngineHealthIsolationTest {
    private static final Set<String> REGISTERED = Set.of("http", "ftp", "sftp", "smb");

    @TempDir Path directory;

    private SimpleTransferEngine busy;
    private SimpleTransferEngine idle;

    @AfterEach
    void shutdownEngines() {
        if (busy != null) busy.shutdown(Duration.ofSeconds(5));
        if (idle != null) idle.shutdown(Duration.ofSeconds(5));
    }

    @Test
    void anEngineReportsOnlyItsOwnTransfersAndProtocols() throws Exception {
        ProtocolFactory protocols = new ProtocolFactory();
        protocols.registerProtocol(new CompletingProtocol());
        busy = new SimpleTransferEngine(1, 0, 1, protocols);
        idle = new SimpleTransferEngine(1, 0, 1);

        TransferResult result = busy.transfer(TransferRequest.builder()
                .requestId("isolated-transfer")
                .sourceUri(URI.create("isolated://service/file"))
                .destinationPath(directory.resolve("file"))
                .protocol("isolated")
                .build());
        assertEquals(TransferStatus.COMPLETED, result.getFinalStatus());

        Map<String, ProtocolHealthCheck> busyHealth = byProtocol(busy);
        assertEquals(1L, ((Number) busyHealth.get("isolated").getDetails().get("totalTransfers")).longValue(),
                "the engine that ran the transfer must count it");

        Map<String, ProtocolHealthCheck> idleHealth = byProtocol(idle);
        assertEquals(REGISTERED, idleHealth.keySet(),
                "an engine that ran nothing must report exactly the protocols it registered");
        idleHealth.values().forEach(check -> assertEquals(0L,
                ((Number) check.getDetails().get("totalTransfers")).longValue(), check.getProtocolName()));
    }

    private static Map<String, ProtocolHealthCheck> byProtocol(SimpleTransferEngine engine) {
        return engine.getHealthCheck().getProtocolHealthChecks().stream()
                .collect(Collectors.toMap(ProtocolHealthCheck::getProtocolName, Function.identity()));
    }

    /** An adapter that completes at once, so the busy engine records one finished transfer. */
    private static final class CompletingProtocol implements TransferProtocol {
        @Override public String getProtocolName() { return "isolated"; }
        @Override public boolean canHandle(TransferRequest request) { return "isolated".equals(request.getProtocol()); }
        @Override public boolean supportsResume() { return false; }
        @Override public boolean supportsPause() { return false; }
        @Override public long getMaxFileSize() { return -1; }

        @Override
        public TransferResult transfer(TransferRequest request, TransferContext context) {
            return TransferResult.builder().requestId(request.getRequestId())
                    .finalStatus(TransferStatus.COMPLETED).bytesTransferred(0).build();
        }
    }
}
