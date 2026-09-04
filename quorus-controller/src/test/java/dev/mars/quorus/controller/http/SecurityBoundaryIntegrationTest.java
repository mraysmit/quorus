/*
 * Copyright 2026 Mark Andrew Ray-Smith Cityline Ltd
 * Licensed under the Apache License, Version 2.0.
 */
package dev.mars.quorus.controller.http;

import dev.mars.quorus.controller.config.ControllerTestConfig;
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
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.net.PemKeyCertOptions;
import io.vertx.core.net.PemTrustOptions;
import io.vertx.ext.web.client.HttpRequest;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.WebClientOptions;
import io.vertx.junit5.VertxExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

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

/** Phase 1 trust-boundary characterization and test-first R2 tenant registry isolation. */
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


    @ParameterizedTest
    @ValueSource(strings = {
            "secret-read", "secret-create", "secret-list", "secret-update", "secret-delete",
            "connection-read", "connection-create", "connection-list", "connection-update",
            "connection-delete", "events"})
    void registryBoundariesSeparateDottedTenantAndResourceIds(String operation, Vertx vertx) throws Exception {
        TlsMaterial tls = TlsMaterial.create(tempDir.resolve("r2-tls"));
        SecurityConfig config = tls.config(Set.of(tls.clientSubject()), Set.of(), Map.of(),
                tempDir.resolve("r2-audit.jsonl"));
        RunningServer running = startServer(vertx, config, AuditSink.noOp());
        WebClient client = tls.authenticatedClient(vertx);
        try {
            String foreignTenant = "bank.branch";
            String tenant = "bank";
            assertEquals(201, awaitSuccess(registryRequest(client, running, "POST", "/secret-references",
                    foreignTenant).sendJsonObject(registrySecret("ledger")), TIMEOUT).statusCode());
            boolean connection = operation.startsWith("connection");
            if (connection) {
                assertEquals(201, awaitSuccess(registryRequest(client, running, "POST", "/service-connections",
                        foreignTenant).sendJsonObject(registryConnection("ledger", "ledger")), TIMEOUT).statusCode());
                assertEquals(201, awaitSuccess(registryRequest(client, running, "POST", "/secret-references",
                        tenant).sendJsonObject(registrySecret("own-secret")), TIMEOUT).statusCode());
            }
            String resource = connection ? "/service-connections" : "/secret-references";
            String id = "branch.ledger";
            if (operation.endsWith("read")) {
                assertEquals(404, awaitSuccess(registryRequest(client, running, "GET", resource + "/" + id,
                        tenant).send(), TIMEOUT).statusCode(), "Foreign resource must not be readable");
            } else if (operation.endsWith("create")) {
                JsonObject body = connection ? registryConnection(id, "own-secret") : registrySecret(id);
                assertEquals(201, awaitSuccess(registryRequest(client, running, "POST", resource, tenant)
                        .sendJsonObject(body), TIMEOUT).statusCode(), "Distinct tenant/resource pairs must coexist");
                assertEquals(foreignTenant, awaitSuccess(registryRequest(client, running, "GET",
                        resource + "/ledger", foreignTenant).send(), TIMEOUT).bodyAsJsonObject().getString("tenantId"));
            } else if (operation.endsWith("list") || operation.equals("events")) {
                String path = operation.equals("events") ? "/security-events" : resource;
                String field = operation.equals("events") ? "events" : connection ? "serviceConnections" : "secretReferences";
                HttpResponse<Buffer> response = awaitSuccess(registryRequest(client, running, "GET", path, tenant)
                        .send(), TIMEOUT);
                assertEquals(200, response.statusCode());
                assertTrue(response.bodyAsJsonObject().getJsonArray(field).stream()
                        .map(JsonObject.class::cast).allMatch(value -> tenant.equals(value.getString("tenantId"))),
                        "Collection must not include a dotted child tenant");
            } else if (operation.endsWith("update")) {
                assertEquals(404, awaitSuccess(registryRequest(client, running, "PUT", resource + "/" + id, tenant)
                        .sendJsonObject(connection ? new JsonObject().put("owner", "changed")
                                : new JsonObject().put("version", "2")), TIMEOUT).statusCode(),
                        "Foreign resource must not be mutable");
            } else {
                assertEquals(404, awaitSuccess(registryRequest(client, running, "DELETE", resource + "/" + id, tenant)
                        .send(), TIMEOUT).statusCode(), "Foreign resource must not be deletable");
            }
        } finally {
            client.close();
            running.close();
        }
    }

    private static HttpRequest<Buffer> registryRequest(WebClient client,
            RunningServer running, String method, String path, String tenant) {
        return client.request(HttpMethod.valueOf(method), running.server().actualPort(),
                "localhost", "/api/v1" + path)
                .putHeader(AuthenticationHandler.PRINCIPAL, "r2-security-admin")
                .putHeader(AuthenticationHandler.IDENTITY_TYPE, "HUMAN")
                .putHeader(AuthenticationHandler.TENANT, tenant)
                .putHeader(AuthenticationHandler.ENVIRONMENT, "production")
                .putHeader(AuthenticationHandler.ROLES, "SECURITY")
                .putHeader(AuthenticationHandler.SCOPES, "*")
                .putHeader(AuthenticationHandler.EXPIRES_AT, Instant.now().plusSeconds(120).toString())
                .putHeader(AuthenticationHandler.ELEVATION_EXPIRES_AT, Instant.now().plusSeconds(120).toString());
    }

    private static JsonObject registrySecret(String id) {
        return new JsonObject().put("secretReferenceId", id).put("provider", "VAULT_KV_V2")
                .put("path", "quorus/data/payments").put("key", "password").put("version", "1").put("status", "ACTIVE");
    }

    private static JsonObject registryConnection(String id, String secretId) {
        return new JsonObject().put("serviceConnectionId", id).put("protocol", "SFTP")
                .put("endpoint", "sftp://192.0.2.10:22").put("networkZone", "payments-dmz")
                .put("allowedPaths", new JsonArray().add("/outbound"))
                .put("allowedDirections", new JsonArray().add("DOWNLOAD"))
                .put("allowedAgentPools", new JsonArray().add("payments-agents"))
                .put("owner", "payments-platform").put("environment", "PRODUCTION")
                .put("classification", "CONFIDENTIAL").put("secretReferenceId", secretId)
                .put("serviceIdentity", "payments-batch").put("authenticationType", "PASSWORD")
                .put("trustPolicy", new JsonObject().put("sshHostKeyFingerprints",
                        new JsonArray().add("SHA256:synthetic-host-key-pin")))
                .put("egressPolicy", new JsonObject().put("allowedHostnames", new JsonArray().add("192.0.2.10"))
                        .put("allowedCidrs", new JsonArray().add("192.0.2.0/24"))
                        .put("allowedPorts", new JsonArray().add(22)).put("pinResolvedAddresses", true));
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
        HttpApiServer server = new HttpApiServer(vertx, "127.0.0.1", 0, node, state, -1,
                ControllerTestConfig.create(), config, auditSink);
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
