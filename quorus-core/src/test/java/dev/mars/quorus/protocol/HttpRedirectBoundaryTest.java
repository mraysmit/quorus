/* Copyright 2026 Mark Andrew Ray-Smith Cityline Ltd. Licensed under Apache-2.0. */
package dev.mars.quorus.protocol;

import dev.mars.quorus.core.TransferJob;
import dev.mars.quorus.core.TransferRequest;
import dev.mars.quorus.transfer.TransferContext;
import dev.mars.quorus.connection.RuntimeCredential;
import dev.mars.quorus.connection.ServiceConnection;
import io.vertx.core.Vertx;
import io.vertx.junit5.VertxExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import static dev.mars.quorus.testing.TestFutureUtils.awaitSuccess;
import static dev.mars.quorus.testing.TestFutureUtils.awaitFailure;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(VertxExtension.class)
class HttpRedirectBoundaryTest {
    @TempDir Path directory;

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void ordinaryDownloadsRetainRedirectSupportWhileGovernedRequestsDenyIt(boolean governed, Vertx vertx) throws Exception {
        var server = awaitSuccess(vertx.createHttpServer().requestHandler(request -> {
            if (request.path().equals("/redirect")) {
                request.response().setStatusCode(302).putHeader("Location", "/file").end();
            } else request.response().end("fixture payload");
        }).listen(0, "127.0.0.1"), Duration.ofSeconds(10));
        try (var credential = new RuntimeCredential("fixture", ServiceConnection.AuthenticationType.BEARER,
                new char[0], Set.of(), Set.of(), Set.of(), "TLSv1.3", List.of("127.0.0.1"))) {
            var request = TransferRequest.builder().requestId("redirect")
                    .sourceUri(URI.create("http://127.0.0.1:" + server.actualPort() + "/redirect"))
                    .destinationPath(directory.resolve("file.dat"))
                    .runtimeCredential(governed ? credential : null).build();
            var result = new HttpTransferProtocol(vertx).transferReactive(request,
                    new TransferContext(new TransferJob(request)));
            if (governed) {
                assertTrue(awaitFailure(result, Duration.ofSeconds(10)).getMessage().contains("302"));
                assertFalse(Files.exists(request.getDestinationPath()));
            } else {
                awaitSuccess(result, Duration.ofSeconds(10));
                assertEquals("fixture payload", Files.readString(request.getDestinationPath()));
            }
        } finally {
            awaitSuccess(server.close(), Duration.ofSeconds(10));
        }
    }
}
