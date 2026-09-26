/*
 * Copyright 2026 Mark Andrew Ray-Smith Cityline Ltd
 * Licensed under the Apache License, Version 2.0.
 */
package dev.mars.quorus.protocol;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsParameters;
import com.sun.net.httpserver.HttpsServer;
import dev.mars.quorus.connection.RuntimeCredential;
import dev.mars.quorus.connection.ServiceConnection;
import dev.mars.quorus.connection.TlsPeerPolicy;
import dev.mars.quorus.core.TransferJob;
import dev.mars.quorus.core.TransferRequest;
import dev.mars.quorus.core.TransferResult;
import dev.mars.quorus.core.TransferStatus;
import dev.mars.quorus.core.exceptions.TransferException;
import dev.mars.quorus.transfer.TransferContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SNIMatcher;
import javax.net.ssl.SNIServerName;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.StandardConstants;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchService;
import java.security.KeyStore;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end boundary contract of the HTTP transfer adapter (plan item RT-03b; ADR-0012 RT-Q5).
 *
 * <p>Every test drives {@link HttpTransferProtocol#transfer} against a real JDK HTTP or HTTPS server.
 * There is no Vert.x and no mocking. Governed tests use the certificates in
 * {@code security/governed} (see its README), whose names are reserved {@code .test} names that
 * never resolve, so the only way to reach the server is the approved-address pin. A server-side
 * {@link SNIMatcher} records the SNI name the client actually sent.
 */
@Timeout(value = 30, unit = TimeUnit.SECONDS, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
class HttpTransferProtocolBoundaryTest {

    private static final String SERVICE_HOST = "transfer.quorus.test";
    private static final char[] STORE_PASSWORD = "changeit".toCharArray();

    @TempDir
    Path directory;

    private final List<AutoCloseable> servers = new ArrayList<>();
    private final Seen seen = new Seen();

    @AfterEach
    void stopServers() throws Exception {
        for (AutoCloseable server : servers) {
            server.close();
        }
    }

    @Nested
    class GovernedHttps {

        @Test
        void downloadReachesTheApprovedAddressUsingTheServiceHostnameForSniAndHost() throws Exception {
            byte[] body = randomBytes(64 * 1024);
            int port = httpsServer("transfer.p12", null, fixedBody(body));

            TransferResult result = download(governedSource(port), credential(ServiceConnection.AuthenticationType.BEARER,
                    "svc", "token-1", Set.of(), Set.of(), "TLSv1.2"));

            assertEquals(TransferStatus.COMPLETED, result.getFinalStatus());
            assertArrayEquals(body, Files.readAllBytes(destination()));
            assertEquals(List.of(SERVICE_HOST), seen.sni, "SNI must carry the service hostname, not the pinned address");
            assertEquals(List.of(SERVICE_HOST + ":" + port), seen.host, "Host must carry the service hostname");
            assertEquals(List.of("Bearer token-1"), seen.authorization);
        }

        @Test
        void basicAuthenticationSendsTheIdentityAndSecret() throws Exception {
            int port = httpsServer("transfer.p12", null, fixedBody("ok".getBytes(StandardCharsets.UTF_8)));

            download(governedSource(port), credential(ServiceConnection.AuthenticationType.BASIC,
                    "svc-user", "pw-1", Set.of(), Set.of(), "TLSv1.2"));

            String expected = "Basic " + Base64.getEncoder().encodeToString("svc-user:pw-1".getBytes(StandardCharsets.UTF_8));
            assertEquals(List.of(expected), seen.authorization);
        }

        @Test
        void aCertificateForAnotherNameIsRejected() throws Exception {
            int port = httpsServer("other.p12", null, fixedBody("secret".getBytes(StandardCharsets.UTF_8)));

            TransferException failure = assertThrows(TransferException.class, () -> download(governedSource(port),
                    credential(ServiceConnection.AuthenticationType.BEARER, "svc", "t", Set.of(), Set.of(), "TLSv1.2")));

            assertCausedBy(failure, javax.net.ssl.SSLException.class);
            assertChainMentions(failure, SERVICE_HOST);
            assertNoDestinationWritten();
        }

        @Test
        void aLeafPinThatDoesNotMatchIsRejected() throws Exception {
            int port = httpsServer("transfer.p12", null, fixedBody("secret".getBytes(StandardCharsets.UTF_8)));

            TransferException failure = assertThrows(TransferException.class, () -> download(governedSource(port),
                    credential(ServiceConnection.AuthenticationType.BEARER, "svc", "t", Set.of(),
                            Set.of("SHA256:bm90LXRoZS1wZWVyLWNlcnRpZmljYXRlLWZpbmdlcnByaW50"), "TLSv1.2")));

            assertChainMentions(failure, "TLS peer certificate is not approved");
            assertNoDestinationWritten();
        }

        @Test
        void aMatchingLeafPinIsAccepted() throws Exception {
            int port = httpsServer("transfer.p12", null, fixedBody("pinned".getBytes(StandardCharsets.UTF_8)));

            download(governedSource(port), credential(ServiceConnection.AuthenticationType.BEARER, "svc", "t",
                    Set.of(), Set.of(leafFingerprint("transfer.p12", "transfer")), "TLSv1.2"));

            assertEquals("pinned", Files.readString(destination()));
        }

        @Test
        void anUnapprovedCertificateAuthorityIsRejected() throws Exception {
            int port = httpsServer("transfer.p12", null, fixedBody("secret".getBytes(StandardCharsets.UTF_8)));
            String unapprovedCa = TlsPeerPolicy.sha256Fingerprint(certificate("unapproved-ca.pem").getEncoded());

            TransferException failure = assertThrows(TransferException.class, () -> download(governedSource(port),
                    credential(ServiceConnection.AuthenticationType.BEARER, "svc", "t", Set.of(unapprovedCa),
                            Set.of(), "TLSv1.2")));

            assertChainMentions(failure, "TLS certificate authority is not approved");
            assertNoDestinationWritten();
        }

        @Test
        void theMinimumTlsVersionIsEnforced() throws Exception {
            int port = httpsServer("transfer.p12", new String[]{"TLSv1.2"}, fixedBody("old".getBytes(StandardCharsets.UTF_8)));

            TransferException failure = assertThrows(TransferException.class, () -> download(governedSource(port),
                    credential(ServiceConnection.AuthenticationType.BEARER, "svc", "t", Set.of(), Set.of(), "TLSv1.3")));

            assertCausedBy(failure, javax.net.ssl.SSLHandshakeException.class);
            assertNoDestinationWritten();
        }

        @Test
        void redirectsAreNotFollowed() throws Exception {
            int port = httpsServer("transfer.p12", null, exchange -> {
                seen.paths.add(exchange.getRequestURI().getPath());
                exchange.getResponseHeaders().add("Location", "/moved");
                exchange.sendResponseHeaders(302, -1);
                exchange.close();
            });

            TransferException failure = assertThrows(TransferException.class, () -> download(governedSource(port),
                    credential(ServiceConnection.AuthenticationType.BEARER, "svc", "t", Set.of(), Set.of(), "TLSv1.2")));

            assertTrue(failure.getMessage().contains("302"), failure.getMessage());
            assertEquals(List.of("/data.bin"), seen.paths, "the redirect target must never be requested");
            assertNoDestinationWritten();
        }
    }

    @Nested
    class Ungoverned {

        @Test
        void redirectsAreFollowed() throws Exception {
            byte[] body = "after redirect".getBytes(StandardCharsets.UTF_8);
            int port = httpServer(exchange -> {
                seen.paths.add(exchange.getRequestURI().getPath());
                if (exchange.getRequestURI().getPath().equals("/data.bin")) {
                    exchange.getResponseHeaders().add("Location", "/moved.bin");
                    exchange.sendResponseHeaders(302, -1);
                    exchange.close();
                } else {
                    respond(exchange, body);
                }
            });

            download(URI.create("http://127.0.0.1:" + port + "/data.bin"), null);

            assertArrayEquals(body, Files.readAllBytes(destination()));
            assertEquals(List.of("/data.bin", "/moved.bin"), seen.paths);
        }

        @Test
        void aNonSuccessStatusFailsTheTransferWithoutWritingTheDestination() throws Exception {
            int port = httpServer(exchange -> {
                exchange.sendResponseHeaders(404, -1);
                exchange.close();
            });

            TransferException failure = assertThrows(TransferException.class,
                    () -> download(URI.create("http://127.0.0.1:" + port + "/data.bin"), null));

            assertTrue(failure.getMessage().contains("404"), failure.getMessage());
            assertNoDestinationWritten();
        }

        @Test
        void aChecksumMismatchFailsTheTransferWithoutWritingTheDestination() throws Exception {
            int port = httpServer(fixedBody("content".getBytes(StandardCharsets.UTF_8)));
            TransferRequest request = TransferRequest.builder()
                    .requestId("checksum")
                    .sourceUri(URI.create("http://127.0.0.1:" + port + "/data.bin"))
                    .destinationPath(destination())
                    .expectedChecksum("0000000000000000000000000000000000000000000000000000000000000000")
                    .build();

            TransferException failure = assertThrows(TransferException.class,
                    () -> protocol().transfer(request, contextFor(request)));

            assertChainMentions(failure, "Checksum mismatch");
            assertNoDestinationWritten();
        }

        @Test
        void downloadsStreamToDiskBeforeTheResponseCompletes() throws Exception {
            int half = 1024 * 1024;
            byte[] body = randomBytes(2 * half);
            CompletableFuture<Void> release = new CompletableFuture<>();
            int port = httpServer(exchange -> {
                exchange.sendResponseHeaders(200, body.length);
                try (OutputStream out = exchange.getResponseBody()) {
                    out.write(body, 0, half);
                    out.flush();
                    release.join();                  // hold the second half until the test has seen the first
                    out.write(body, half, half);
                }
            });
            Path temp = destination().resolveSibling(destination().getFileName() + ".tmp");
            AtomicReference<Throwable> failure = new AtomicReference<>();
            Thread transfer;
            try (WatchService watcher = FileSystems.getDefault().newWatchService()) {
                directory.register(watcher, StandardWatchEventKinds.ENTRY_CREATE, StandardWatchEventKinds.ENTRY_MODIFY);
                Path ended = directory.resolve("transfer-ended.marker");
                transfer = Thread.ofVirtual().start(() -> {
                    try {
                        download(URI.create("http://127.0.0.1:" + port + "/data.bin"), null);
                    } catch (Throwable t) {
                        failure.set(t);
                    } finally {
                        try {
                            Files.createFile(ended);         // wakes the watcher even if the transfer failed early
                        } catch (IOException ignored) {
                            // the watcher will still time out through the test timeout
                        }
                    }
                });
                while (!(Files.exists(temp) && Files.size(temp) >= half / 2)) {
                    if (Files.exists(ended)) {
                        release.complete(null);
                        throw new AssertionError("the transfer ended before streaming any data to "
                                + temp.getFileName(), failure.get());
                    }
                    var key = watcher.take();               // event-driven: blocks until the directory changes
                    key.pollEvents();
                    key.reset();
                }
            }
            assertFalse(Files.exists(destination()), "the destination appears only after the whole body has arrived");
            release.complete(null);
            transfer.join();

            if (failure.get() != null) {
                throw new AssertionError("transfer failed", failure.get());
            }
            assertArrayEquals(body, Files.readAllBytes(destination()));
        }

        @Test
        void uploadStreamsTheFileWithPut() throws Exception {
            byte[] content = randomBytes(256 * 1024);
            Path source = directory.resolve("upload.bin");
            Files.write(source, content);
            AtomicReference<byte[]> received = new AtomicReference<>();
            AtomicReference<String> method = new AtomicReference<>();
            int port = httpServer(exchange -> {
                method.set(exchange.getRequestMethod());
                received.set(exchange.getRequestBody().readAllBytes());
                exchange.sendResponseHeaders(201, -1);
                exchange.close();
            });
            TransferRequest request = TransferRequest.builder()
                    .requestId("upload")
                    .sourceUri(source.toUri())
                    .destinationUri(URI.create("http://127.0.0.1:" + port + "/upload.bin"))
                    .build();

            TransferResult result = protocol().transfer(request, contextFor(request));

            assertEquals(TransferStatus.COMPLETED, result.getFinalStatus());
            assertEquals("PUT", method.get());
            assertArrayEquals(content, received.get());
        }
    }

    // ------------------------------------------------------------------ helpers

    private TransferResult download(URI source, RuntimeCredential credential) throws Exception {
        TransferRequest.Builder builder = TransferRequest.builder()
                .requestId("download")
                .sourceUri(source)
                .destinationPath(destination());
        if (credential != null) {
            builder.runtimeCredential(credential);
        }
        TransferRequest request = builder.build();
        return protocol().transfer(request, contextFor(request));
    }

    private HttpTransferProtocol protocol() throws Exception {
        return new HttpTransferProtocol(trustManager("ca.pem"));
    }

    private static TransferContext contextFor(TransferRequest request) {
        return new TransferContext(new TransferJob(request));
    }

    private Path destination() {
        return directory.resolve("data.bin");
    }

    private void assertNoDestinationWritten() throws IOException {
        assertFalse(Files.exists(destination()), "no destination file may be written");
        try (var files = Files.list(directory)) {
            assertEquals(List.of(), files.map(p -> p.getFileName().toString()).filter(n -> n.endsWith(".tmp")).toList(),
                    "no partial file may be left behind");
        }
    }

    private static void assertCausedBy(Throwable failure, Class<? extends Throwable> type) {
        for (Throwable t = failure; t != null; t = t.getCause()) {
            if (type.isInstance(t)) {
                return;
            }
        }
        throw new AssertionError("expected a " + type.getSimpleName() + " in the cause chain", failure);
    }

    private static void assertChainMentions(Throwable failure, String text) {
        for (Throwable t = failure; t != null; t = t.getCause()) {
            if (t.getMessage() != null && t.getMessage().contains(text)) {
                return;
            }
        }
        throw new AssertionError("expected the cause chain to mention '" + text + "'", failure);
    }

    private static URI governedSource(int port) {
        return URI.create("https://" + SERVICE_HOST + ":" + port + "/data.bin");
    }

    private static RuntimeCredential credential(ServiceConnection.AuthenticationType type, String identity,
                                                String secret, Set<String> approvedCaIds, Set<String> leafPins,
                                                String minimumTls) {
        return new RuntimeCredential(identity, type, secret.toCharArray(), Set.of(), approvedCaIds, leafPins,
                minimumTls, List.of("127.0.0.1"));
    }

    private int httpsServer(String keystore, String[] protocols, HttpHandler handler) throws Exception {
        KeyStore store = KeyStore.getInstance("PKCS12");
        try (InputStream in = resource(keystore)) {
            store.load(in, STORE_PASSWORD);
        }
        KeyManagerFactory keys = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        keys.init(store, STORE_PASSWORD);
        SSLContext context = SSLContext.getInstance("TLS");
        context.init(keys.getKeyManagers(), null, null);
        HttpsServer server = HttpsServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.setHttpsConfigurator(new HttpsConfigurator(context) {
            @Override
            public void configure(HttpsParameters params) {
                SSLParameters parameters = context.getDefaultSSLParameters();
                parameters.setSNIMatchers(List.of(new SNIMatcher(StandardConstants.SNI_HOST_NAME) {
                    @Override
                    public boolean matches(SNIServerName serverName) {
                        seen.sni.add(new String(serverName.getEncoded(), StandardCharsets.US_ASCII));
                        return true;
                    }
                }));
                if (protocols != null) {
                    parameters.setProtocols(protocols);
                }
                params.setSSLParameters(parameters);
            }
        });
        server.createContext("/", recording(handler));
        server.start();
        servers.add(() -> server.stop(0));
        return server.getAddress().getPort();
    }

    private int httpServer(HttpHandler handler) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/", handler);
        server.start();
        servers.add(() -> server.stop(0));
        return server.getAddress().getPort();
    }

    private HttpHandler recording(HttpHandler handler) {
        return exchange -> {
            seen.host.add(exchange.getRequestHeaders().getFirst("Host"));
            String authorization = exchange.getRequestHeaders().getFirst("Authorization");
            if (authorization != null) {
                seen.authorization.add(authorization);
            }
            handler.handle(exchange);
        };
    }

    private static HttpHandler fixedBody(byte[] body) {
        return exchange -> respond(exchange, body);
    }

    private static void respond(HttpExchange exchange, byte[] body) throws IOException {
        exchange.sendResponseHeaders(200, body.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(body);
        }
    }

    private static X509TrustManager trustManager(String caPem) throws Exception {
        KeyStore store = KeyStore.getInstance(KeyStore.getDefaultType());
        store.load(null, null);
        store.setCertificateEntry("ca", certificate(caPem));
        TrustManagerFactory factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        factory.init(store);
        return (X509TrustManager) factory.getTrustManagers()[0];
    }

    private static X509Certificate certificate(String pem) throws Exception {
        try (InputStream in = resource(pem)) {
            return (X509Certificate) CertificateFactory.getInstance("X.509").generateCertificate(in);
        }
    }

    private static String leafFingerprint(String keystore, String alias) throws Exception {
        KeyStore store = KeyStore.getInstance("PKCS12");
        try (InputStream in = resource(keystore)) {
            store.load(in, STORE_PASSWORD);
        }
        return TlsPeerPolicy.sha256Fingerprint(store.getCertificate(alias).getEncoded());
    }

    private static InputStream resource(String name) {
        InputStream in = HttpTransferProtocolBoundaryTest.class.getResourceAsStream("/security/governed/" + name);
        if (in == null) {
            throw new IllegalStateException("missing test fixture /security/governed/" + name);
        }
        return in;
    }

    private static byte[] randomBytes(int size) {
        byte[] bytes = new byte[size];
        new Random(42).nextBytes(bytes);
        return bytes;
    }

    /** What the servers observed, recorded on server threads. */
    private static final class Seen {
        final List<String> sni = Collections.synchronizedList(new ArrayList<>());
        final List<String> host = Collections.synchronizedList(new ArrayList<>());
        final List<String> authorization = Collections.synchronizedList(new ArrayList<>());
        final List<String> paths = Collections.synchronizedList(new ArrayList<>());
    }
}
