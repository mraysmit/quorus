/* Copyright 2026 Mark Andrew Ray-Smith Cityline Ltd. Licensed under Apache-2.0. */
package dev.mars.quorus.connection;

import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.net.PemKeyCertOptions;
import io.vertx.core.net.PemTrustOptions;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.WebClientOptions;
import io.vertx.junit5.VertxExtension;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.Arguments;

import java.net.InetAddress;
import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;

import static dev.mars.quorus.testing.TestFutureUtils.awaitSuccess;
import static dev.mars.quorus.testing.TestResourceUtils.copyResource;
import static org.junit.jupiter.api.Assertions.*;

/** Policy-approved paths must survive real HTTPS request serialization without losing filename data. */
@ExtendWith(VertxExtension.class)
class RemotePathBoundaryTest {
    @TempDir Path directory;

    @Test
    void pinnedHostnameSupportsTlsOnNonDefaultPort(Vertx vertx) throws Exception {
        Path certificate = copyResource(getClass(), "/security/server-cert.pem", directory);
        Path key = copyResource(getClass(), "/security/server-key.pem", directory);
        var server = awaitSuccess(vertx.createHttpServer(new HttpServerOptions().setSsl(true)
                        .setKeyCertOptions(new PemKeyCertOptions().setCertPath(certificate.toString())
                                .setKeyPath(key.toString())))
                .requestHandler(request -> request.response().end(request.getHeader("Host")))
                .listen(0, "127.0.0.1"), Duration.ofSeconds(10));
        var client = WebClient.create(vertx, new WebClientOptions()
                .setTrustOptions(new PemTrustOptions().addCertPath(certificate.toString())));
        try (var credential = new RuntimeCredential("fixture", ServiceConnection.AuthenticationType.BEARER,
                new char[0], Set.of(), Set.of(), Set.of(), "TLSv1.3", List.of("127.0.0.1"))) {
            URI authority = URI.create("https://localhost:" + server.actualPort() + "/file");
            var response = awaitSuccess(client.getAbs(PinnedEndpoint.connectUri(authority, credential).toString())
                    .virtualHost(PinnedEndpoint.virtualHost(authority)).send(), Duration.ofSeconds(10));
            assertEquals(200, response.statusCode());
            assertEquals("localhost:" + server.actualPort(), response.bodyAsString());
        } finally {
            client.close();
            awaitSuccess(server.close(), Duration.ofSeconds(10));
        }
    }

    @ParameterizedTest
    @MethodSource("paths")
    void rootScopePreservesLiteralFilenameOverHttps(String scope, String remotePath, Vertx vertx) throws Exception {
        Path certificate = copyResource(getClass(), "/security/server-cert.pem", directory);
        Path key = copyResource(getClass(), "/security/server-key.pem", directory);
        var server = awaitSuccess(vertx.createHttpServer(new HttpServerOptions().setSsl(true)
                        .setKeyCertOptions(new PemKeyCertOptions().setCertPath(certificate.toString())
                                .setKeyPath(key.toString())))
                .requestHandler(request -> request.response().end(request.path()))
                .listen(0, "127.0.0.1"), Duration.ofSeconds(10));
        var client = WebClient.create(vertx, new WebClientOptions()
                .setTrustOptions(new PemTrustOptions().addCertPath(certificate.toString())));
        try {
            var connection = connection(server.actualPort(), Set.of(scope));
            var authorization = new ConnectionPolicyEnforcer().authorizeController(connection,
                    new ConnectionAccessRequest("tenant", remotePath, ServiceConnection.Direction.DOWNLOAD,
                            "pool", List.of()), host -> List.of(InetAddress.getByAddress(new byte[]{127, 0, 0, 1})));
            var response = awaitSuccess(client.getAbs(authorization.endpoint().toASCIIString()).send(),
                    Duration.ofSeconds(10));
            assertEquals(200, response.statusCode());
            assertEquals(remotePath, URI.create("https://localhost" + response.bodyAsString()).getPath());
            assertNull(authorization.endpoint().getQuery());
            assertNull(authorization.endpoint().getFragment());
        } finally {
            client.close();
            awaitSuccess(server.close(), Duration.ofSeconds(10));
        }
    }

    static java.util.stream.Stream<Arguments> paths() {
        return java.util.stream.Stream.of("/", "/out").flatMap(scope -> java.util.stream.Stream.of(
                "/out/file.dat", "/out/report#1?.dat", "/out/month end.dat", "/out/version..dat",
                "/out/percent%2Fname.dat", "/out/账目.dat").map(path -> Arguments.of(scope, path)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"/out/../private", "/out/./../private", "/out\\..\\private"})
    void traversalCannotReachAnEndpoint(String path) {
        assertThrows(IllegalArgumentException.class, () -> connection(443, Set.of("/out")).resolveRemotePath(path));
    }

    private static ServiceConnection connection(int port, Set<String> paths) {
        return new ServiceConnection("connection", "tenant", ServiceConnection.Protocol.HTTPS,
                URI.create("https://localhost:" + port), "zone", paths,
                Set.of(ServiceConnection.Direction.DOWNLOAD), Set.of("pool"), "owner", "test", "internal",
                "secret", "identity", ServiceConnection.AuthenticationType.BEARER,
                new ServiceConnection.TrustPolicy(true, true, Set.of("fixture"), Set.of(), "TLSv1.3"),
                new ServiceConnection.EgressPolicy(Set.of("localhost"), Set.of("127.0.0.0/8"),
                        Set.of(port), false, true), 1, ServiceConnection.Status.ACTIVE, Instant.now(), Instant.now());
    }
}
