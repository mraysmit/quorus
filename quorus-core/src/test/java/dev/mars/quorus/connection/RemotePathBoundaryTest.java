/* Copyright 2026 Mark Andrew Ray-Smith Cityline Ltd. Licensed under Apache-2.0. */
package dev.mars.quorus.connection;

import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsServer;
import dev.mars.quorus.core.TransferJob;
import dev.mars.quorus.core.TransferRequest;
import dev.mars.quorus.core.TransferResult;
import dev.mars.quorus.core.TransferStatus;
import dev.mars.quorus.protocol.HttpTransferProtocol;
import dev.mars.quorus.transfer.TransferContext;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.cert.CertificateFactory;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Policy-approved paths must survive real HTTPS request serialization without losing filename data.
 * Each case authorizes a path, downloads it through {@link HttpTransferProtocol} from a JDK HTTPS
 * server that echoes the raw request path, and compares what the server received. The server uses the
 * governed fixtures in {@code security/governed} (see its README); the service name never resolves, so
 * the request reaches the server only through the approved-address pin.
 */
@Timeout(value = 30, unit = SECONDS, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
class RemotePathBoundaryTest {
    private static final String SERVICE_HOST = "transfer.quorus.test";
    private static final char[] STORE_PASSWORD = "changeit".toCharArray();

    @TempDir Path directory;

    @ParameterizedTest
    @MethodSource("paths")
    void rootScopePreservesLiteralFilenameOverHttps(String scope, String remotePath) throws Exception {
        HttpsServer server = echoPathServer();
        try {
            var connection = connection(server.getAddress().getPort(), Set.of(scope));
            var authorization = new ConnectionPolicyEnforcer().authorizeController(connection,
                    new ConnectionAccessRequest("tenant", remotePath, ServiceConnection.Direction.DOWNLOAD,
                            "pool", List.of()), host -> List.of(InetAddress.getByAddress(new byte[]{127, 0, 0, 1})));
            try (var credential = new RuntimeCredential("fixture", ServiceConnection.AuthenticationType.BEARER,
                    "token".toCharArray(), Set.of(), Set.of(), Set.of(), "TLSv1.3", authorization.resolvedAddresses())) {
                var request = TransferRequest.builder().requestId("remote-path")
                        .sourceUri(authorization.endpoint()).destinationPath(directory.resolve("received"))
                        .runtimeCredential(credential).build();

                TransferResult result = new HttpTransferProtocol(trustManager()).transfer(request,
                        new TransferContext(new TransferJob(request)));

                assertEquals(TransferStatus.COMPLETED, result.getFinalStatus());
                String receivedRawPath = Files.readString(request.getDestinationPath(), StandardCharsets.US_ASCII);
                assertEquals(remotePath, URI.create("https://" + SERVICE_HOST + receivedRawPath).getPath());
                assertNull(authorization.endpoint().getQuery());
                assertNull(authorization.endpoint().getFragment());
            }
        } finally {
            server.stop(0);
        }
    }

    static Stream<Arguments> paths() {
        return Stream.of("/", "/out").flatMap(scope -> Stream.of(
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
                URI.create("https://" + SERVICE_HOST + ":" + port), "zone", paths,
                Set.of(ServiceConnection.Direction.DOWNLOAD), Set.of("pool"), "owner", "test", "internal",
                "secret", "identity", ServiceConnection.AuthenticationType.BEARER,
                new ServiceConnection.TrustPolicy(true, true, Set.of("fixture"), Set.of(), "TLSv1.3"),
                new ServiceConnection.EgressPolicy(Set.of(SERVICE_HOST), Set.of("127.0.0.0/8"),
                        Set.of(port), false, true), 1, ServiceConnection.Status.ACTIVE, Instant.now(), Instant.now());
    }

    /** An HTTPS server for {@value #SERVICE_HOST} whose response body is the raw path it received. */
    private static HttpsServer echoPathServer() throws Exception {
        KeyStore store = KeyStore.getInstance("PKCS12");
        try (InputStream in = resource("transfer.p12")) {
            store.load(in, STORE_PASSWORD);
        }
        KeyManagerFactory keys = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        keys.init(store, STORE_PASSWORD);
        SSLContext context = SSLContext.getInstance("TLS");
        context.init(keys.getKeyManagers(), null, null);
        HttpsServer server = HttpsServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.setHttpsConfigurator(new HttpsConfigurator(context));
        server.createContext("/", exchange -> {
            byte[] body = exchange.getRequestURI().getRawPath().getBytes(StandardCharsets.US_ASCII);
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        });
        server.start();
        return server;
    }

    private static X509TrustManager trustManager() throws Exception {
        KeyStore store = KeyStore.getInstance(KeyStore.getDefaultType());
        store.load(null, null);
        try (InputStream in = resource("ca.pem")) {
            store.setCertificateEntry("ca", CertificateFactory.getInstance("X.509").generateCertificate(in));
        }
        TrustManagerFactory factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        factory.init(store);
        return (X509TrustManager) factory.getTrustManagers()[0];
    }

    private static InputStream resource(String name) {
        InputStream in = RemotePathBoundaryTest.class.getResourceAsStream("/security/governed/" + name);
        if (in == null) {
            throw new IllegalStateException("missing test fixture /security/governed/" + name);
        }
        return in;
    }
}
