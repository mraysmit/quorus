/*
 * Copyright 2026 Mark Andrew Ray-Smith Cityline Ltd
 * Licensed under the Apache License, Version 2.0.
 */
package dev.mars.quorus.agent.service;

import dev.mars.quorus.agent.config.AgentConfiguration;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.ClientAuth;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.net.PemKeyCertOptions;
import io.vertx.core.net.PemTrustOptions;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClient;
import io.vertx.junit5.VertxExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.nio.file.Files;
import java.time.Duration;
import java.util.Set;

import static dev.mars.quorus.testing.TestFutureUtils.awaitFailure;
import static dev.mars.quorus.testing.TestFutureUtils.awaitSuccess;
import static dev.mars.quorus.testing.TestResourceUtils.copyResource;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/** Retrospective external-path characterization of the Phase 1 agent trust boundary. */
@ExtendWith(VertxExtension.class)
class ControllerWebClientFactoryTlsTest {
    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    @TempDir
    Path tempDir;

    @Test
    void productionAgentPresentsItsCertificateToTrustedController(Vertx vertx) throws Exception {
        TlsMaterial tls = TlsMaterial.load(tempDir.resolve("tls"));
        HttpServer server = startServer(vertx, tls.serverCertificate(), tls.serverPrivateKey(),
                tls.clientCertificate());
        WebClient client = ControllerWebClientFactory.create(vertx, tls.productionConfig(server.actualPort()));
        try {
            HttpResponse<Buffer> response = awaitSuccess(client
                    .get(server.actualPort(), "localhost", "/api/v1/agents/security-check").send(), TIMEOUT);
            assertEquals(204, response.statusCode());
        } finally {
            client.close();
            awaitSuccess(server.close(), TIMEOUT);
        }
    }

    @Test
    void productionAgentRejectsControllerOutsideItsTrustBundle(Vertx vertx) throws Exception {
        TlsMaterial tls = TlsMaterial.load(tempDir.resolve("tls"));
        HttpServer server = startServer(vertx, tls.clientCertificate(), tls.clientPrivateKey(),
                tls.clientCertificate());
        WebClient client = ControllerWebClientFactory.create(vertx, tls.productionConfig(server.actualPort()));
        try {
            Throwable failure = awaitFailure(client
                    .get(server.actualPort(), "localhost", "/api/v1/agents/security-check").send(), TIMEOUT);
            assertNotNull(failure);
        } finally {
            client.close();
            awaitSuccess(server.close(), TIMEOUT);
        }
    }

    @Test
    void productionAgentRejectsControllerHostnameMismatch(Vertx vertx) throws Exception {
        TlsMaterial tls = TlsMaterial.load(tempDir.resolve("tls"));
        HttpServer server = startServer(vertx, tls.serverCertificate(), tls.serverPrivateKey(),
                tls.clientCertificate());
        WebClient client = ControllerWebClientFactory.create(vertx, tls.productionConfig(server.actualPort()));
        try {
            Throwable failure = awaitFailure(client
                    .get(server.actualPort(), "127.0.0.2", "/api/v1/agents/security-check").send(), TIMEOUT);
            assertNotNull(failure);
        } finally {
            client.close();
            awaitSuccess(server.close(), TIMEOUT);
        }
    }

    @Test
    void overlappingAgentCertificatesRemainTrustedDuringRotation(Vertx vertx) throws Exception {
        TlsMaterial tls = TlsMaterial.load(tempDir.resolve("tls"));
        Path overlapBundle = tempDir.resolve("tls/agent-overlap.pem");
        Files.writeString(overlapBundle, Files.readString(tls.clientCertificate())
                + System.lineSeparator() + Files.readString(tls.serverCertificate()));
        HttpServer server = startServer(vertx, tls.serverCertificate(), tls.serverPrivateKey(), overlapBundle);
        WebClient oldClient = ControllerWebClientFactory.create(vertx,
                tls.productionConfig(server.actualPort(), tls.clientCertificate(), tls.clientPrivateKey()));
        WebClient rotatedClient = ControllerWebClientFactory.create(vertx,
                tls.productionConfig(server.actualPort(), tls.serverCertificate(), tls.serverPrivateKey()));
        try {
            HttpResponse<Buffer> oldResponse = awaitSuccess(oldClient
                    .get(server.actualPort(), "localhost", "/api/v1/agents/security-check").send(), TIMEOUT);
            HttpResponse<Buffer> rotatedResponse = awaitSuccess(rotatedClient
                    .get(server.actualPort(), "localhost", "/api/v1/agents/security-check").send(), TIMEOUT);
            assertEquals(204, oldResponse.statusCode());
            assertEquals(204, rotatedResponse.statusCode());
        } finally {
            oldClient.close();
            rotatedClient.close();
            awaitSuccess(server.close(), TIMEOUT);
        }
    }

    private static HttpServer startServer(Vertx vertx, Path certificate, Path privateKey, Path clientTrust) {
        HttpServerOptions options = new HttpServerOptions()
                .setSsl(true)
                .setKeyCertOptions(new PemKeyCertOptions()
                        .setCertPath(certificate.toString())
                        .setKeyPath(privateKey.toString()))
                .setTrustOptions(new PemTrustOptions().addCertPath(clientTrust.toString()))
                .setClientAuth(ClientAuth.REQUIRED)
                .setEnabledSecureTransportProtocols(Set.of("TLSv1.3"));
        return awaitSuccess(vertx.createHttpServer(options)
                .requestHandler(request -> request.response().setStatusCode(204).end())
                .listen(0, "127.0.0.1"), TIMEOUT);
    }

    private record TlsMaterial(Path serverCertificate, Path serverPrivateKey,
                               Path clientCertificate, Path clientPrivateKey) {
        static TlsMaterial load(Path targetDirectory) throws Exception {
            return new TlsMaterial(
                    copyResource(ControllerWebClientFactoryTlsTest.class,
                            "/security/server-cert.pem", targetDirectory),
                    copyResource(ControllerWebClientFactoryTlsTest.class,
                            "/security/server-key.pem", targetDirectory),
                    copyResource(ControllerWebClientFactoryTlsTest.class,
                            "/security/client-cert.pem", targetDirectory),
                    copyResource(ControllerWebClientFactoryTlsTest.class,
                            "/security/client-key.pem", targetDirectory));
        }

        AgentConfiguration productionConfig(int port) {
            return productionConfig(port, clientCertificate, clientPrivateKey);
        }

        AgentConfiguration productionConfig(int port, Path certificate, Path privateKey) {
            return new AgentConfiguration.Builder()
                    .agentId("regulated-agent-1")
                    .tenantId("regulated-bank-a")
                    .controllerUrl("https://localhost:" + port + "/api/v1")
                    .securityProfile("production")
                    .allowInsecure(false)
                    .controllerTlsEnabled(true)
                    .tlsCertificatePath(certificate.toString())
                    .tlsPrivateKeyPath(privateKey.toString())
                    .tlsTrustBundlePath(serverCertificate.toString())
                    .build();
        }

    }
}
