/* Copyright 2026 Mark Andrew Ray-Smith Cityline Ltd. Licensed under Apache-2.0. */
package dev.mars.quorus.protocol;

import com.sun.net.httpserver.HttpServer;
import dev.mars.quorus.connection.RuntimeCredential;
import dev.mars.quorus.connection.ServiceConnection;
import dev.mars.quorus.core.TransferJob;
import dev.mars.quorus.core.TransferRequest;
import dev.mars.quorus.core.exceptions.TransferException;
import dev.mars.quorus.transfer.TransferContext;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Ordinary downloads follow redirects; governed downloads, even over plain HTTP to an IP literal, never do. */
class HttpRedirectBoundaryTest {
    @TempDir Path directory;

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void ordinaryDownloadsRetainRedirectSupportWhileGovernedRequestsDenyIt(boolean governed) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/", exchange -> {
            if (exchange.getRequestURI().getPath().equals("/redirect")) {
                exchange.getResponseHeaders().add("Location", "/file");
                exchange.sendResponseHeaders(302, -1);
                exchange.close();
            } else {
                byte[] body = "fixture payload".getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(200, body.length);
                try (OutputStream out = exchange.getResponseBody()) {
                    out.write(body);
                }
            }
        });
        server.start();
        try (var credential = new RuntimeCredential("fixture", ServiceConnection.AuthenticationType.BEARER,
                new char[0], Set.of(), Set.of(), Set.of(), "TLSv1.3", List.of("127.0.0.1"))) {
            var request = TransferRequest.builder().requestId("redirect")
                    .sourceUri(URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/redirect"))
                    .destinationPath(directory.resolve("file.dat"))
                    .runtimeCredential(governed ? credential : null).build();
            var context = new TransferContext(new TransferJob(request));
            if (governed) {
                TransferException failure = assertThrows(TransferException.class,
                        () -> new HttpTransferProtocol().transfer(request, context));
                assertTrue(failure.getMessage().contains("302"), failure.getMessage());
                assertFalse(Files.exists(request.getDestinationPath()));
            } else {
                new HttpTransferProtocol().transfer(request, context);
                assertEquals("fixture payload", Files.readString(request.getDestinationPath()));
            }
        } finally {
            server.stop(0);
        }
    }
}
