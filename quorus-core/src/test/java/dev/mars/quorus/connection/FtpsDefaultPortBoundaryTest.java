/* Copyright 2026 Mark Andrew Ray-Smith Cityline Ltd. Licensed under Apache-2.0. */
package dev.mars.quorus.connection;

import dev.mars.quorus.core.TransferJob;
import dev.mars.quorus.core.TransferRequest;
import dev.mars.quorus.core.exceptions.TransferException;
import dev.mars.quorus.protocol.FtpTransferProtocol;
import dev.mars.quorus.transfer.TransferContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.jupiter.api.Assertions.*;

@Timeout(value = 30, unit = SECONDS, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
class FtpsDefaultPortBoundaryTest {
    @TempDir Path directory;

    @Test
    void policyApprovesTheExplicitTlsPortActuallyUsedByTheAdapter() throws Exception {
        // The fixture listens on port 21 because the claim under test is that an ftps:// URI without a
        // port is approved, and connected, on the explicit-TLS default port.
        try (ServerSocket server = new ServerSocket(21, 1, InetAddress.getByAddress(new byte[]{127, 0, 0, 1}))) {
            CompletableFuture<String> command = CompletableFuture.supplyAsync(() -> firstCommand(server));
            var connection = new ServiceConnection("connection", "tenant", ServiceConnection.Protocol.FTPS,
                    URI.create("ftps://localhost"), "zone", Set.of("/approved"),
                    Set.of(ServiceConnection.Direction.DOWNLOAD), Set.of("pool"), "owner", "test", "internal",
                    "secret", "identity", ServiceConnection.AuthenticationType.PASSWORD,
                    new ServiceConnection.TrustPolicy(true, true, Set.of("fixture"), Set.of(), "TLSv1.3"),
                    new ServiceConnection.EgressPolicy(Set.of("localhost"), Set.of("127.0.0.0/8"),
                            Set.of(21), false, true), 1, ServiceConnection.Status.ACTIVE, Instant.now(), Instant.now());
            var authorization = new ConnectionPolicyEnforcer().authorizeController(connection,
                    new ConnectionAccessRequest("tenant", "/approved/file", ServiceConnection.Direction.DOWNLOAD,
                            "pool", List.of()), host -> List.of(InetAddress.getByAddress(new byte[]{127, 0, 0, 1})));
            try (var credential = new RuntimeCredential("fixture", ServiceConnection.AuthenticationType.PASSWORD,
                    new char[0], Set.of(), Set.of(), Set.of(), "TLSv1.3", authorization.resolvedAddresses())) {
                var request = TransferRequest.builder().requestId("ftps-port")
                        .sourceUri(authorization.endpoint()).destinationPath(directory.resolve("file"))
                        .runtimeCredential(credential).build();

                assertThrows(TransferException.class, () -> new FtpTransferProtocol().transfer(request,
                        new TransferContext(new TransferJob(request))));

                assertEquals("AUTH TLS", command.get(5, TimeUnit.SECONDS));
                assertFalse(Files.exists(request.getDestinationPath()));
            }
        }
    }

    /** Greets one client, returns its first command and refuses it, so the adapter stops before authenticating. */
    private static String firstCommand(ServerSocket server) {
        try (Socket socket = server.accept();
             var in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.US_ASCII))) {
            OutputStream out = socket.getOutputStream();
            out.write("220 fixture ready\r\n".getBytes(StandardCharsets.US_ASCII));
            out.flush();
            String command = in.readLine();
            out.write("421 fixture stops before authentication\r\n".getBytes(StandardCharsets.US_ASCII));
            out.flush();
            return command;
        } catch (Exception e) {
            throw new IllegalStateException("FTPS fixture failed", e);
        }
    }
}
