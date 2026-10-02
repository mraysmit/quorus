/* Copyright 2026 Mark Andrew Ray-Smith Cityline Ltd. Licensed under Apache-2.0. */
package dev.mars.quorus.protocol;

import dev.mars.quorus.connection.RuntimeCredential;
import dev.mars.quorus.connection.ServiceConnection;
import dev.mars.quorus.core.TransferJob;
import dev.mars.quorus.core.TransferRequest;
import dev.mars.quorus.core.TransferResult;
import dev.mars.quorus.core.TransferStatus;
import dev.mars.quorus.core.exceptions.TransferException;
import dev.mars.quorus.transfer.TransferContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Adapter boundaries, called the way the engine calls them: blocking, on the caller's thread.
 * Until RT-03d these cases ran through the Vert.x {@code transferReactive} worker dispatch, which is
 * removed together with the adapters' event-loop guards.
 */
@Timeout(value = 30, unit = SECONDS, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
class ProtocolAdapterBoundaryTest {
    @TempDir Path directory;

    @Test
    void nfsCopiesFromAnApprovedMount() throws Exception {
        Files.createDirectories(directory.resolve("server/export"));
        Files.writeString(directory.resolve("server/export/file"), "mounted fixture");
        TransferRequest request = request("nfs://server/export/file", null);

        TransferResult result = transfer(new NfsTransferProtocol(directory.toString()), request);

        assertEquals(TransferStatus.COMPLETED, result.getFinalStatus());
        assertEquals("mounted fixture", Files.readString(request.getDestinationPath()));
    }

    @Test
    void smbStillEnforcesMountAttestation() {
        try (var credential = new RuntimeCredential("fixture", ServiceConnection.AuthenticationType.KERBEROS,
                new char[0], Set.of(), Set.of(), "TLSv1.3")) {
            TransferException error = assertThrows(TransferException.class,
                    () -> transfer(new SmbTransferProtocol(), request("smb://server/share/file", credential)));
            assertTrue(error.getMessage().contains("mount lacks an agent attestation"), error.getMessage());
        }
    }

    @Test
    void sftpReachesTheProtocolPeer() throws Exception {
        CompletableFuture<Void> connected = new CompletableFuture<>();
        try (ServerSocket server = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            Thread.ofVirtual().start(() -> {
                try (Socket socket = server.accept()) {
                    connected.complete(null);   // Negotiation rejection: never accept authentication or a payload.
                } catch (Exception e) {
                    connected.completeExceptionally(e);
                }
            });
            TransferRequest request = request("sftp://127.0.0.1:" + server.getLocalPort() + "/file", null);

            assertThrows(TransferException.class, () -> transfer(new SftpTransferProtocol(), request));
            connected.get(5, SECONDS);
        }
    }

    private TransferRequest request(String source, RuntimeCredential credential) {
        return TransferRequest.builder().requestId("adapter-boundary").sourceUri(URI.create(source))
                .destinationPath(directory.resolve("download")).runtimeCredential(credential).build();
    }

    private static TransferResult transfer(TransferProtocol protocol, TransferRequest request) throws TransferException {
        return protocol.transfer(request, new TransferContext(new TransferJob(request)));
    }
}
