/* Copyright 2026 Mark Andrew Ray-Smith Cityline Ltd. Licensed under Apache-2.0. */
package dev.mars.quorus.connection;

import dev.mars.quorus.core.TransferJob;
import dev.mars.quorus.core.TransferRequest;
import dev.mars.quorus.protocol.FtpTransferProtocol;
import dev.mars.quorus.transfer.TransferContext;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.parsetools.RecordParser;
import io.vertx.junit5.VertxExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import java.net.InetAddress;
import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import static dev.mars.quorus.testing.TestFutureUtils.*;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(VertxExtension.class)
class FtpsDefaultPortBoundaryTest {
    @TempDir Path directory;

    @Test
    void policyApprovesTheExplicitTlsPortActuallyUsedByTheAdapter(Vertx vertx) throws Exception {
        Promise<String> command = Promise.promise();
        var server = awaitSuccess(vertx.createNetServer().connectHandler(socket -> {
            socket.write("220 fixture ready\r\n");
            socket.handler(RecordParser.newDelimited("\r\n", buffer -> {
                command.tryComplete(buffer.toString());
                socket.write("421 fixture stops before authentication\r\n").onComplete(done -> socket.close());
            }));
        }).listen(21, "127.0.0.1"), Duration.ofSeconds(5));
        try {
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
                var result = vertx.executeBlocking(() -> new FtpTransferProtocol().transfer(request,
                        new TransferContext(new TransferJob(request))), false);
                result.onFailure(command::tryFail);
                assertEquals("AUTH TLS", awaitSuccess(command.future(), Duration.ofSeconds(5)));
                awaitFailure(result, Duration.ofSeconds(5));
                assertFalse(java.nio.file.Files.exists(request.getDestinationPath()));
            }
        } finally {
            awaitSuccess(server.close(), Duration.ofSeconds(5));
        }
    }
}
