/* Copyright 2026 Mark Andrew Ray-Smith Cityline Ltd. Licensed under Apache-2.0. */
package dev.mars.quorus.protocol;

import dev.mars.quorus.connection.RuntimeCredential;
import dev.mars.quorus.connection.ServiceConnection;
import dev.mars.quorus.core.*;
import dev.mars.quorus.transfer.TransferContext;
import io.vertx.core.*;
import io.vertx.junit5.VertxExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Set;
import static dev.mars.quorus.testing.TestFutureUtils.*;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(VertxExtension.class)
class BlockingProtocolWorkerBoundaryTest {
    @TempDir Path directory;

    @Test
    void reactiveNfsCopiesFromAnApprovedMount(Vertx vertx) throws Exception {
        Files.createDirectories(directory.resolve("server/export"));
        Files.writeString(directory.resolve("server/export/file"), "mounted fixture");
        var request = request("nfs://server/export/file", null);
        var result = awaitSuccess(execute(vertx, new NfsTransferProtocol(directory.toString()), request),
                Duration.ofSeconds(5));
        assertEquals(TransferStatus.COMPLETED, result.getFinalStatus());
        assertEquals("mounted fixture", Files.readString(request.getDestinationPath()));
    }

    @Test
    void reactiveSmbStillEnforcesMountAttestation(Vertx vertx) {
        try (var credential = new RuntimeCredential("fixture", ServiceConnection.AuthenticationType.KERBEROS,
                new char[0], Set.of(), Set.of(), "TLSv1.3")) {
            var error = awaitFailure(execute(vertx, new SmbTransferProtocol(),
                    request("smb://server/share/file", credential)), Duration.ofSeconds(5));
            assertTrue(error.getMessage().contains("mount lacks an agent attestation"), error.getMessage());
        }
    }

    @Test
    void reactiveSftpReachesTheProtocolPeer(Vertx vertx) {
        Promise<Void> connected = Promise.promise();
        var server = awaitSuccess(vertx.createNetServer().connectHandler(socket -> {
            connected.tryComplete();
            socket.close(); // Negotiation rejection; never accept authentication or a payload.
        }).listen(0, "127.0.0.1"), Duration.ofSeconds(5));
        try {
            var result = execute(vertx, new SftpTransferProtocol(),
                    request("sftp://127.0.0.1:" + server.actualPort() + "/file", null));
            result.onFailure(error -> {
                if (!connected.future().isComplete()) connected.tryFail(error);
            });
            awaitSuccess(connected.future(), Duration.ofSeconds(5));
            awaitFailure(result, Duration.ofSeconds(5));
        } finally {
            awaitSuccess(server.close(), Duration.ofSeconds(5));
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"ftp", "sftp", "smb", "nfs"})
    void directBlockingEntryStillRejectsEventLoopThreads(String scheme, Vertx vertx) {
        TransferProtocol protocol = switch (scheme) {
            case "ftp" -> new FtpTransferProtocol();
            case "sftp" -> new SftpTransferProtocol();
            case "smb" -> new SmbTransferProtocol();
            default -> new NfsTransferProtocol(directory.toString());
        };
        Promise<Void> checked = Promise.promise();
        vertx.runOnContext(ignored -> {
            var request = request(scheme + "://server/export/file", null);
            try {
                var error = assertThrows(Exception.class,
                        () -> protocol.transfer(request, new TransferContext(new TransferJob(request))));
                assertTrue(error.getMessage().contains("event loop"));
                checked.complete();
            } catch (Throwable error) { checked.fail(error); }
        });
        awaitSuccess(checked.future(), Duration.ofSeconds(5));
    }

    private TransferRequest request(String source, RuntimeCredential credential) {
        return TransferRequest.builder().requestId("worker-boundary").sourceUri(URI.create(source))
                .destinationPath(directory.resolve("download")).runtimeCredential(credential).build();
    }

    private static Future<TransferResult> execute(Vertx vertx, TransferProtocol protocol, TransferRequest request) {
        Promise<TransferResult> result = Promise.promise();
        vertx.runOnContext(ignored -> protocol.transferReactive(request, new TransferContext(new TransferJob(request)))
                .onComplete(result));
        return result.future();
    }
}
