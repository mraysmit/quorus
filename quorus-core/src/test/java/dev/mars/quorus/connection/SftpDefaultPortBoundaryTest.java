/* Copyright 2026 Mark Andrew Ray-Smith Cityline Ltd. Licensed under Apache-2.0. */
package dev.mars.quorus.connection;

import dev.mars.quorus.core.TransferJob;
import dev.mars.quorus.core.TransferRequest;
import dev.mars.quorus.core.exceptions.TransferException;
import dev.mars.quorus.protocol.SftpTransferProtocol;
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

/**
 * An {@code sftp://} endpoint without a port is approved by the policy, and connected by the adapter, on the
 * same default port (register item ENG-07 part 1). The default-port table maps SFTP to an ephemeral port, so
 * the claim is tested end to end without binding the privileged port 22.
 */
@Timeout(value = 30, unit = SECONDS, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
class SftpDefaultPortBoundaryTest {
    private static final byte[] LOOPBACK = {127, 0, 0, 1};

    @TempDir Path directory;

    @Test
    void policyApprovesTheDefaultPortActuallyUsedByTheAdapter() throws Exception {
        try (ServerSocket server = new ServerSocket(0, 1, InetAddress.getByAddress(LOOPBACK))) {
            int defaultPort = server.getLocalPort();
            DefaultPorts ports = DefaultPorts.wellKnown().with(ServiceConnection.Protocol.SFTP, defaultPort);
            CompletableFuture<String> banner = CompletableFuture.supplyAsync(() -> clientBanner(server));
            var connection = new ServiceConnection("connection", "tenant", ServiceConnection.Protocol.SFTP,
                    URI.create("sftp://localhost"), "zone", Set.of("/approved"),
                    Set.of(ServiceConnection.Direction.DOWNLOAD), Set.of("pool"), "owner", "test", "internal",
                    "secret", "identity", ServiceConnection.AuthenticationType.PASSWORD,
                    new ServiceConnection.TrustPolicy(false, false, Set.of(), Set.of("SHA256:fixture"), "TLSv1.3"),
                    new ServiceConnection.EgressPolicy(Set.of("localhost"), Set.of("127.0.0.0/8"),
                            Set.of(defaultPort), false, true), 1, ServiceConnection.Status.ACTIVE,
                    Instant.now(), Instant.now());
            var authorization = new ConnectionPolicyEnforcer(ports).authorizeController(connection,
                    new ConnectionAccessRequest("tenant", "/approved/file", ServiceConnection.Direction.DOWNLOAD,
                            "pool", List.of()), host -> List.of(InetAddress.getByAddress(LOOPBACK)));
            assertEquals(-1, authorization.endpoint().getPort(), "the approved endpoint must name no port");

            try (var credential = new RuntimeCredential("fixture", ServiceConnection.AuthenticationType.PASSWORD,
                    "fixture".toCharArray(), Set.of("SHA256:fixture"), Set.of(), Set.of(), "TLSv1.3",
                    authorization.resolvedAddresses())) {
                var request = TransferRequest.builder().requestId("sftp-port")
                        .sourceUri(authorization.endpoint()).destinationPath(directory.resolve("file"))
                        .runtimeCredential(credential).build();

                assertThrows(TransferException.class, () -> new SftpTransferProtocol(ports).transfer(request,
                        new TransferContext(new TransferJob(request))));

                assertTrue(banner.get(5, TimeUnit.SECONDS).startsWith("SSH-2.0-"),
                        "the adapter must open an SSH session on the table's SFTP port");
                assertFalse(Files.exists(request.getDestinationPath()));
            }
        }
    }

    /** Sends a server banner, returns the client's identification line, then closes before key exchange. */
    private static String clientBanner(ServerSocket server) {
        try (Socket socket = server.accept();
             var in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.US_ASCII))) {
            OutputStream out = socket.getOutputStream();
            out.write("SSH-2.0-fixture\r\n".getBytes(StandardCharsets.US_ASCII));
            out.flush();
            return in.readLine();
        } catch (Exception e) {
            throw new IllegalStateException("SFTP fixture failed", e);
        }
    }
}
