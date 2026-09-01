/*
 * Copyright 2026 Mark Andrew Ray-Smith Cityline Ltd
 * Licensed under the Apache License, Version 2.0.
 */
package dev.mars.quorus.controller.http;

import dev.mars.quorus.controller.raft.InMemoryTransportSimulator;
import dev.mars.quorus.controller.raft.RaftNode;
import dev.mars.quorus.controller.raft.RaftNodeMode;
import dev.mars.quorus.controller.security.AuthenticationHandler;
import dev.mars.quorus.controller.security.IdentityType;
import dev.mars.quorus.controller.security.SecurityConfig;
import dev.mars.quorus.controller.security.SecurityIdentity;
import dev.mars.quorus.controller.security.SecurityProfile;
import dev.mars.quorus.controller.security.SecurityRole;
import dev.mars.quorus.controller.security.audit.AuditEvent;
import dev.mars.quorus.controller.security.audit.AuditSink;
import dev.mars.quorus.controller.state.QuorusStateStore;
import dev.mars.quorus.controller.state.TransferJobCommand;
import dev.mars.quorus.core.TransferJob;
import dev.mars.quorus.core.TransferRequest;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.net.PemKeyCertOptions;
import io.vertx.core.net.PemTrustOptions;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.WebClientOptions;
import io.vertx.junit5.VertxExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;

import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static dev.mars.quorus.testing.TestFutureUtils.awaitFailure;
import static dev.mars.quorus.testing.TestFutureUtils.awaitSuccess;
import static dev.mars.quorus.testing.TestFutureUtils.eventually;
import static dev.mars.quorus.testing.TestResourceUtils.copyResource;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Retrospective external-path characterization of the Phase 1 HTTP trust boundary. */
@ExtendWith(VertxExtension.class)
class SecurityBoundaryIntegrationTest {
    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    @TempDir
    Path tempDir;

    @Test
    void directMtlsIdentityDrivesTenantIsolationAndCompletionAudit(Vertx vertx) throws Exception {
        {
            TlsMaterial tls = TlsMaterial.create(tempDir.resolve("tls"));
            List<AuditEvent> events = new ArrayList<>();
            SecurityIdentity identity = directIdentity(tls.clientSubject());
            SecurityConfig config = tls.config(Set.of(), Set.of(), Map.of(tls.clientSubject(), identity),
                    tempDir.resolve("direct-audit.jsonl"));
            RunningServer running = startServer(vertx, config, events::add);
            WebClient client = tls.authenticatedClient(vertx);
            WebClient anonymous = tls.anonymousClient(vertx);
            try {
                HttpResponse<Buffer> me = awaitSuccess(client
                        .get(running.server().actualPort(), "localhost", "/api/v1/security/me").send(), TIMEOUT);
                assertEquals(200, me.statusCode());
                assertEquals("payments-operator", me.bodyAsJsonObject().getString("principalId"));
                assertEquals("regulated-bank-a", me.bodyAsJsonObject().getString("tenantId"));

                HttpResponse<Buffer> info = awaitSuccess(client
                        .get(running.server().actualPort(), "localhost", "/api/v1/info").send(), TIMEOUT);
                assertEquals(200, info.statusCode());

                TransferRequest request = TransferRequest.builder()
                        .requestId("foreign-transfer")
                        .sourceUri(URI.create("https://payments.example.test/foreign.dat"))
                        .destinationPath(tempDir.resolve("foreign.dat"))
                        .build();
                running.state().apply(TransferJobCommand.create(new TransferJob(request), "regulated-bank-b"));
                HttpResponse<Buffer> forbidden = awaitSuccess(client
                        .get(running.server().actualPort(), "localhost", "/api/v1/transfers/foreign-transfer")
                        .send(), TIMEOUT);
                assertEquals(403, forbidden.statusCode());

                awaitSuccess(eventually(vertx, () -> events.stream().anyMatch(event ->
                                "PRIVILEGED_READ".equals(event.eventType())
                                        && "SUCCESS".equals(event.outcome())
                                        && "/api/v1/info".equals(event.path()))
                        && events.stream().anyMatch(event ->
                                "PRIVILEGED_READ".equals(event.eventType())
                                        && "FAILURE".equals(event.outcome())
                                        && "/api/v1/transfers/foreign-transfer".equals(event.path())), TIMEOUT),
                        TIMEOUT.plusSeconds(1));

                Throwable handshakeFailure = awaitFailure(anonymous
                        .get(running.server().actualPort(), "localhost", "/api/v1/security/me").send(), TIMEOUT);
                assertTrue(handshakeFailure.getMessage() != null && !handshakeFailure.getMessage().isBlank());
            } finally {
                anonymous.close();
                client.close();
                running.close();
            }
        }
    }

    @Test
    void revokedCertificateIsRejectedAfterSuccessfulTlsAuthentication(Vertx vertx) throws Exception {
        {
            TlsMaterial tls = TlsMaterial.create(tempDir.resolve("tls"));
            SecurityIdentity identity = directIdentity(tls.clientSubject());
            SecurityConfig config = tls.config(Set.of(), Set.of(tls.clientSerial()),
                    Map.of(tls.clientSubject(), identity), tempDir.resolve("revoked-audit.jsonl"));
            RunningServer running = startServer(vertx, config, AuditSink.noOp());
            WebClient client = tls.authenticatedClient(vertx);
            try {
                HttpResponse<Buffer> response = awaitSuccess(client
                        .get(running.server().actualPort(), "localhost", "/api/v1/security/me").send(), TIMEOUT);
                assertEquals(401, response.statusCode());
            } finally {
                client.close();
                running.close();
            }
        }
    }

    @Test
    void trustedGatewayRequiresCompleteShortLivedAssertion(Vertx vertx) throws Exception {
        {
            TlsMaterial tls = TlsMaterial.create(tempDir.resolve("tls"));
            SecurityConfig config = tls.config(Set.of(tls.clientSubject()), Set.of(), Map.of(),
                    tempDir.resolve("gateway-audit.jsonl"));
            RunningServer running = startServer(vertx, config, AuditSink.noOp());
            WebClient client = tls.authenticatedClient(vertx);
            try {
                HttpResponse<Buffer> accepted = awaitSuccess(client
                        .get(running.server().actualPort(), "localhost", "/api/v1/security/me")
                        .putHeader(AuthenticationHandler.PRINCIPAL, "gateway-user")
                        .putHeader(AuthenticationHandler.IDENTITY_TYPE, "HUMAN")
                        .putHeader(AuthenticationHandler.TENANT, "regulated-bank-a")
                        .putHeader(AuthenticationHandler.ENVIRONMENT, "production")
                        .putHeader(AuthenticationHandler.ROLES, "OPERATOR")
                        .putHeader(AuthenticationHandler.SCOPES, "security:self:read")
                        .putHeader(AuthenticationHandler.EXPIRES_AT, Instant.now().plusSeconds(60).toString())
                        .send(), TIMEOUT);
                assertEquals(200, accepted.statusCode());
                assertEquals("gateway-user", accepted.bodyAsJsonObject().getString("principalId"));

                HttpResponse<Buffer> expired = awaitSuccess(client
                        .get(running.server().actualPort(), "localhost", "/api/v1/security/me")
                        .putHeader(AuthenticationHandler.PRINCIPAL, "expired-user")
                        .putHeader(AuthenticationHandler.IDENTITY_TYPE, "HUMAN")
                        .putHeader(AuthenticationHandler.TENANT, "regulated-bank-a")
                        .putHeader(AuthenticationHandler.ENVIRONMENT, "production")
                        .putHeader(AuthenticationHandler.ROLES, "OPERATOR")
                        .putHeader(AuthenticationHandler.SCOPES, "security:self:read")
                        .putHeader(AuthenticationHandler.EXPIRES_AT, Instant.now().minusSeconds(1).toString())
                        .send(), TIMEOUT);
                assertEquals(401, expired.statusCode());

                HttpResponse<Buffer> incomplete = awaitSuccess(client
                        .get(running.server().actualPort(), "localhost", "/api/v1/security/me")
                        .putHeader(AuthenticationHandler.PRINCIPAL, "forged-user")
                        .send(), TIMEOUT);
                assertEquals(401, incomplete.statusCode());
            } finally {
                client.close();
                running.close();
            }
        }
    }

    @Test
    void liveAuthorizationExplanationAndCompletionAuditCoverEnterpriseDenials(Vertx vertx) throws Exception {
        TlsMaterial tls = TlsMaterial.create(tempDir.resolve("tls"));
        List<AuditEvent> events = new ArrayList<>();
        SecurityIdentity identity = new SecurityIdentity("security-reviewer", IdentityType.HUMAN,
                "regulated-bank-a", "production", Set.of(SecurityRole.SECURITY), Set.of("security:explain"),
                tls.clientSubject(), Instant.now(), Instant.now().plusSeconds(300), null);
        SecurityConfig config = tls.config(Set.of(), Set.of(), Map.of(tls.clientSubject(), identity),
                tempDir.resolve("explain-audit.jsonl"));
        RunningServer running = startServer(vertx, config, events::add);
        WebClient client = tls.authenticatedClient(vertx);
        try {
            HttpResponse<Buffer> tenant = awaitSuccess(client
                    .get(running.server().actualPort(), "localhost", "/api/v1/security/authorization/explain")
                    .addQueryParam("method", "GET")
                    .addQueryParam("path", "/api/v1/transfers/foreign-transfer")
                    .addQueryParam("tenantId", "regulated-bank-b")
                    .send(), TIMEOUT);
            assertEquals(200, tenant.statusCode());
            assertEquals(false, tenant.bodyAsJsonObject().getBoolean("allowed"));
            assertEquals("Q-AUTHZ-TENANT-MISMATCH", tenant.bodyAsJsonObject().getString("decisionCode"));

            HttpResponse<Buffer> environment = awaitSuccess(client
                    .post(running.server().actualPort(), "localhost", "/api/v1/security/authorization/check")
                    .sendJsonObject(new JsonObject()
                            .put("method", "GET")
                            .put("path", "/api/v1/info")
                            .put("environment", "development")), TIMEOUT);
            assertEquals(200, environment.statusCode());
            assertEquals(false, environment.bodyAsJsonObject().getBoolean("allowed"));
            assertEquals("Q-AUTHZ-ENVIRONMENT-MISMATCH", environment.bodyAsJsonObject().getString("decisionCode"));

            HttpResponse<Buffer> wrongRole = awaitSuccess(client
                    .get(running.server().actualPort(), "localhost", "/api/v1/agents").send(), TIMEOUT);
            assertEquals(403, wrongRole.statusCode());

            awaitSuccess(eventually(vertx, () -> events.stream().anyMatch(event ->
                    "MUTATION".equals(event.eventType())
                            && "SUCCESS".equals(event.outcome())
                            && "/api/v1/security/authorization/check".equals(event.path())), TIMEOUT),
                    TIMEOUT.plusSeconds(1));
        } finally {
            client.close();
            running.close();
        }
    }

    @Test
    void revocationUpdateTerminatesAuthorizationOnAnExistingTlsClient(Vertx vertx) throws Exception {
        TlsMaterial tls = TlsMaterial.create(tempDir.resolve("tls"));
        List<AuditEvent> events = new ArrayList<>();
        SecurityIdentity identity = new SecurityIdentity("security-administrator", IdentityType.HUMAN,
                "regulated-bank-a", "production", Set.of(SecurityRole.SECURITY), Set.of("*"),
                tls.clientSubject(), Instant.now(), Instant.now().plusSeconds(300), Instant.now().plusSeconds(120));
        SecurityConfig config = tls.config(Set.of(), Set.of(), Map.of(tls.clientSubject(), identity),
                tempDir.resolve("revocation-update-audit.jsonl"));
        RunningServer running = startServer(vertx, config, events::add);
        WebClient client = tls.authenticatedClient(vertx);
        try {
            HttpResponse<Buffer> before = awaitSuccess(client
                    .get(running.server().actualPort(), "localhost", "/api/v1/security/trust").send(), TIMEOUT);
            assertEquals(200, before.statusCode());
            assertEquals("configuration", before.bodyAsJsonObject().getString("trustBundleVersion"));
            assertTrue(before.bodyAsJsonObject().containsKey("certificateSecondsRemaining"));
            assertTrue(before.bodyAsJsonObject().containsKey("expiryAlertState"));

            HttpResponse<Buffer> update = awaitSuccess(client
                    .put(running.server().actualPort(), "localhost", "/api/v1/security/trust/revocations")
                    .sendJsonObject(new JsonObject()
                            .put("trustBundleVersion", "phase1-v2")
                            .put("revokedCertificateSerials", new JsonArray().add(tls.clientSerial()))), TIMEOUT);
            assertEquals(200, update.statusCode());
            assertEquals("phase1-v2", update.bodyAsJsonObject().getString("trustBundleVersion"));

            HttpResponse<Buffer> after = awaitSuccess(client
                    .get(running.server().actualPort(), "localhost", "/api/v1/security/me").send(), TIMEOUT);
            assertEquals(401, after.statusCode());

            awaitSuccess(eventually(vertx, () -> events.stream().anyMatch(event ->
                    "SECURITY_CONFIGURATION_CHANGE".equals(event.eventType())
                            && "SUCCESS".equals(event.outcome())
                            && "phase1-v2".equals(event.attributes().get("trustBundleVersion"))), TIMEOUT),
                    TIMEOUT.plusSeconds(1));
        } finally {
            client.close();
            running.close();
        }
    }

    @Test
    void overlappingHttpCertificatesPermitCutoverBeforeOldIdentityRevocation(Vertx vertx) throws Exception {
        TlsMaterial tls = TlsMaterial.create(tempDir.resolve("tls"));
        Path overlapBundle = tempDir.resolve("tls/http-overlap.pem");
        Files.writeString(overlapBundle, Files.readString(tls.clientCertificate())
                + System.lineSeparator() + Files.readString(tls.serverCertificate()));
        SecurityIdentity oldIdentity = elevatedSecurityIdentity("old-security-admin", tls.clientSubject());
        SecurityIdentity rotatedIdentity = elevatedSecurityIdentity("rotated-security-admin", tls.serverSubject());
        SecurityConfig config = tls.configWithTrust(overlapBundle, Set.of(), Set.of(), Map.of(
                tls.clientSubject(), oldIdentity, tls.serverSubject(), rotatedIdentity),
                tempDir.resolve("http-overlap-audit.jsonl"));
        RunningServer running = startServer(vertx, config, AuditSink.noOp());
        WebClient oldClient = tls.authenticatedClient(vertx);
        WebClient rotatedClient = tls.rotatedAuthenticatedClient(vertx);
        try {
            assertEquals(200, awaitSuccess(oldClient
                    .get(running.server().actualPort(), "localhost", "/api/v1/security/me").send(), TIMEOUT)
                    .statusCode());
            assertEquals(200, awaitSuccess(rotatedClient
                    .get(running.server().actualPort(), "localhost", "/api/v1/security/me").send(), TIMEOUT)
                    .statusCode());

            HttpResponse<Buffer> update = awaitSuccess(oldClient
                    .put(running.server().actualPort(), "localhost", "/api/v1/security/trust/revocations")
                    .sendJsonObject(new JsonObject()
                            .put("trustBundleVersion", "http-rotation-v2")
                            .put("revokedCertificateSerials", new JsonArray().add(tls.clientSerial()))), TIMEOUT);
            assertEquals(200, update.statusCode());
            assertEquals(401, awaitSuccess(oldClient
                    .get(running.server().actualPort(), "localhost", "/api/v1/security/me").send(), TIMEOUT)
                    .statusCode());
            HttpResponse<Buffer> rotated = awaitSuccess(rotatedClient
                    .get(running.server().actualPort(), "localhost", "/api/v1/security/me").send(), TIMEOUT);
            assertEquals(200, rotated.statusCode());
            assertEquals("rotated-security-admin", rotated.bodyAsJsonObject().getString("principalId"));
        } finally {
            oldClient.close();
            rotatedClient.close();
            running.close();
        }
    }

    private static RunningServer startServer(Vertx vertx, SecurityConfig config, AuditSink auditSink) {
        config.validate();
        String nodeId = "security-boundary-" + System.nanoTime();
        QuorusStateStore state = new QuorusStateStore();
        RaftNode node = RaftNode.builder()
                .vertx(vertx)
                .nodeId(nodeId)
                .clusterNodes(Set.of(nodeId))
                .transport(new InMemoryTransportSimulator(nodeId))
                .stateMachine(state)
                .mode(RaftNodeMode.volatileMode())
                .electionTimeout(250)
                .heartbeatInterval(50)
                .build();
        awaitSuccess(node.start(), TIMEOUT);
        awaitSuccess(eventually(vertx, node::isLeader, TIMEOUT), TIMEOUT.plusSeconds(1));
        HttpApiServer server = new HttpApiServer(vertx, "127.0.0.1", 0, node, state, -1, config, auditSink);
        awaitSuccess(server.start(), TIMEOUT);
        return new RunningServer(server, node, state);
    }

    private static SecurityIdentity directIdentity(String subject) {
        return new SecurityIdentity("payments-operator", IdentityType.HUMAN, "regulated-bank-a", "production",
                Set.of(SecurityRole.OPERATOR), Set.of("*"), subject, Instant.now(),
                Instant.now().plusSeconds(300), null);
    }

    private static SecurityIdentity elevatedSecurityIdentity(String principal, String subject) {
        return new SecurityIdentity(principal, IdentityType.HUMAN, "regulated-bank-a", "production",
                Set.of(SecurityRole.SECURITY), Set.of("*"), subject, Instant.now(),
                Instant.now().plusSeconds(300), Instant.now().plusSeconds(120));
    }

    private record RunningServer(HttpApiServer server, RaftNode node, QuorusStateStore state) implements AutoCloseable {
        @Override
        public void close() {
            awaitSuccess(server.stop(), TIMEOUT);
            awaitSuccess(node.stop(), TIMEOUT);
        }
    }

    private record TlsMaterial(Path serverCertificate, Path serverPrivateKey,
                               Path clientCertificate, Path clientPrivateKey,
                               String serverSubject, String serverSerial,
                               String clientSubject, String clientSerial) {
        static TlsMaterial create(Path targetDirectory) throws Exception {
            Path serverCertificate = copyResource(SecurityBoundaryIntegrationTest.class,
                    "/security/server-cert.pem", targetDirectory);
            Path serverPrivateKey = copyResource(SecurityBoundaryIntegrationTest.class,
                    "/security/server-key.pem", targetDirectory);
            Path clientCertificate = copyResource(SecurityBoundaryIntegrationTest.class,
                    "/security/client-cert.pem", targetDirectory);
            Path clientPrivateKey = copyResource(SecurityBoundaryIntegrationTest.class,
                    "/security/client-key.pem", targetDirectory);
            X509Certificate server = readCertificate(serverCertificate);
            X509Certificate certificate = readCertificate(clientCertificate);
            return new TlsMaterial(serverCertificate, serverPrivateKey, clientCertificate, clientPrivateKey,
                    server.getSubjectX500Principal().getName(),
                    server.getSerialNumber().toString(16).toUpperCase(Locale.ROOT),
                    certificate.getSubjectX500Principal().getName(),
                    certificate.getSerialNumber().toString(16).toUpperCase(Locale.ROOT));
        }

        SecurityConfig config(Set<String> gateways, Set<String> revoked,
                              Map<String, SecurityIdentity> identities, Path auditPath) {
            return configWithTrust(clientCertificate, gateways, revoked, identities, auditPath);
        }

        SecurityConfig configWithTrust(Path trustBundle, Set<String> gateways, Set<String> revoked,
                                       Map<String, SecurityIdentity> identities, Path auditPath) {
            return new SecurityConfig(SecurityProfile.PRODUCTION, true, false, true,
                    serverCertificate, serverPrivateKey, trustBundle, null,
                    gateways, revoked, identities, auditPath);
        }

        WebClient authenticatedClient(Vertx vertx) {
            return WebClient.create(vertx, baseOptions()
                    .setKeyCertOptions(new PemKeyCertOptions()
                            .setCertPath(clientCertificate.toString())
                            .setKeyPath(clientPrivateKey.toString())));
        }

        WebClient anonymousClient(Vertx vertx) {
            return WebClient.create(vertx, baseOptions());
        }

        WebClient rotatedAuthenticatedClient(Vertx vertx) {
            return WebClient.create(vertx, baseOptions()
                    .setKeyCertOptions(new PemKeyCertOptions()
                            .setCertPath(serverCertificate.toString())
                            .setKeyPath(serverPrivateKey.toString())));
        }

        private WebClientOptions baseOptions() {
            return new WebClientOptions()
                    .setSsl(true)
                    .setVerifyHost(true)
                    .setTrustAll(false)
                    .setTrustOptions(new PemTrustOptions().addCertPath(serverCertificate.toString()))
                    .setEnabledSecureTransportProtocols(Set.of("TLSv1.3"));
        }

        private static X509Certificate readCertificate(Path path) throws Exception {
            try (InputStream input = Files.newInputStream(path)) {
                return (X509Certificate) CertificateFactory.getInstance("X.509").generateCertificate(input);
            }
        }
    }
}
