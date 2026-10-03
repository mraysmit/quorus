/*
 * Copyright 2026 Mark Andrew Ray-Smith Cityline Ltd
 * Licensed under the Apache License, Version 2.0.
 */
package dev.mars.quorus.controller.http;

import dev.mars.quorus.agent.AgentInfo;
import dev.mars.quorus.agent.AgentStatus;
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
import dev.mars.quorus.controller.state.AgentCommand;
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

    /**
     * Register item ENG-16: a request waiting for its audit record to become durable must not hold the
     * event loop, or every other request waits behind that disk sync.
     */
    @Test
    void aRequestWaitingForItsAuditSyncDoesNotBlockOtherRequests(Vertx vertx) throws Exception {
        TlsMaterial tls = TlsMaterial.create(tempDir.resolve("tls"));
        SecurityIdentity identity = directIdentity(tls.clientSubject());
        SecurityConfig config = tls.config(Set.of(), Set.of(), Map.of(tls.clientSubject(), identity),
                tempDir.resolve("held-audit.jsonl"));
        java.util.concurrent.CompletableFuture<Void> auditWaiting = new java.util.concurrent.CompletableFuture<>();
        java.util.concurrent.CompletableFuture<Void> auditHeld = new java.util.concurrent.CompletableFuture<>();
        AuditSink held = new AuditSink() {
            @Override
            public void append(AuditEvent event) {
                appendAsync(event).join();
            }

            @Override
            public java.util.concurrent.CompletableFuture<Void> appendAsync(AuditEvent event) {
                auditWaiting.complete(null);
                return auditHeld;                          // durable only when the test releases it
            }
        };
        RunningServer running = startServer(vertx, config, held);
        WebClient client = tls.authenticatedClient(vertx);
        try {
            int port = running.server().actualPort();
            var info = client.get(port, "localhost", "/api/v1/info").send();
            auditWaiting.get(10, java.util.concurrent.TimeUnit.SECONDS);

            HttpResponse<Buffer> live = awaitSuccess(client.get(port, "localhost", "/health/live").send(), TIMEOUT);

            assertEquals(200, live.statusCode(), "another request is served while the first waits for its audit");
            assertTrue(!info.isComplete(), "the audited request is not answered before its record is durable");
            auditHeld.complete(null);
            assertEquals(200, awaitSuccess(info, TIMEOUT).statusCode());
        } finally {
            auditHeld.complete(null);
            client.close();
            running.close();
        }
    }

    /**
     * Register item ENG-16: the request body is read after authentication and authorization. While those
     * wait for their audit records, the body that has already arrived must be kept for the body handler.
     */
    @Test
    void aRequestBodyArrivingWhileItsAuditIsPendingIsStillRead(Vertx vertx) throws Exception {
        TlsMaterial tls = TlsMaterial.create(tempDir.resolve("tls"));
        SecurityIdentity identity = new SecurityIdentity("security-reviewer", IdentityType.HUMAN,
                "regulated-bank-a", "production", Set.of(SecurityRole.SECURITY), Set.of("security:explain"),
                tls.clientSubject(), Instant.now(), Instant.now().plusSeconds(300), null);
        SecurityConfig config = tls.config(Set.of(), Set.of(), Map.of(tls.clientSubject(), identity),
                tempDir.resolve("body-audit.jsonl"));
        java.util.concurrent.CompletableFuture<Void> auditWaiting = new java.util.concurrent.CompletableFuture<>();
        java.util.concurrent.CompletableFuture<Void> auditHeld = new java.util.concurrent.CompletableFuture<>();
        AuditSink heldFirst = new AuditSink() {
            @Override
            public void append(AuditEvent event) {
                appendAsync(event).join();
            }

            @Override
            public java.util.concurrent.CompletableFuture<Void> appendAsync(AuditEvent event) {
                auditWaiting.complete(null);
                return auditHeld;                          // every record waits until the test releases the first
            }
        };
        RunningServer running = startServer(vertx, config, heldFirst);
        WebClient client = tls.authenticatedClient(vertx);
        try {
            var check = client.post(running.server().actualPort(), "localhost", "/api/v1/security/authorization/check")
                    .sendJsonObject(new JsonObject()
                            .put("method", "GET")
                            .put("path", "/api/v1/info")
                            .put("environment", "development"));
            auditWaiting.get(10, java.util.concurrent.TimeUnit.SECONDS);
            // A full round trip after the POST was written, so its small body has reached the server's event
            // loop before the audit is released (the body always arrives while the audit is still held).
            awaitSuccess(client.get(running.server().actualPort(), "localhost", "/health/live").send(), TIMEOUT);

            auditHeld.complete(null);
            HttpResponse<Buffer> response = awaitSuccess(check, TIMEOUT);

            assertEquals(200, response.statusCode(), () -> response.bodyAsString());
            assertEquals("Q-AUTHZ-ENVIRONMENT-MISMATCH", response.bodyAsJsonObject().getString("decisionCode"));
        } finally {
            auditHeld.complete(null);
            client.close();
            running.close();
        }
    }

    @Test
    void revokedCertificateIsRejectedAfterSuccessfulTlsAuthentication(Vertx vertx) throws Exception {
        {
            TlsMaterial tls = TlsMaterial.create(tempDir.resolve("tls"));
            SecurityIdentity identity = directIdentity(tls.clientSubject());
            // OpenSSL commonly prints serials with leading zero octets. Configuration and
            // certificate-derived values must compare as the same positive integer.
            SecurityConfig config = tls.config(Set.of(), Set.of("00:" + tls.clientSerial()),
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

    /**
     * Register item SEC-09: runtime revocation is node-local (decision DR-Q2), so an operator sends the
     * update to every controller. A follower must apply it rather than reject it as a write that needs
     * the leader, or it keeps accepting the revoked certificate.
     */
    @Test
    void revocationUpdateAppliesOnAFollower(Vertx vertx) throws Exception {
        TlsMaterial tls = TlsMaterial.create(tempDir.resolve("tls"));
        SecurityIdentity identity = elevatedSecurityIdentity("security-administrator", tls.clientSubject());
        SecurityConfig config = tls.config(Set.of(), Set.of(), Map.of(tls.clientSubject(), identity),
                tempDir.resolve("follower-revocation-audit.jsonl"));
        try (RunningCluster cluster = startServerOnFollower(vertx, config)) {
            WebClient client = tls.authenticatedClient(vertx);
            try {
                int port = cluster.server().actualPort();
                assertEquals(200, awaitSuccess(client.get(port, "localhost", "/api/v1/security/me").send(),
                        TIMEOUT).statusCode());

                HttpResponse<Buffer> update = awaitSuccess(client
                        .put(port, "localhost", "/api/v1/security/trust/revocations")
                        .sendJsonObject(new JsonObject()
                                .put("trustBundleVersion", "follower-v2")
                                .put("revokedCertificateSerials", new JsonArray().add(tls.clientSerial()))), TIMEOUT);
                assertEquals(200, update.statusCode(), "A follower must apply a node-local revocation update");

                assertEquals(401, awaitSuccess(client.get(port, "localhost", "/api/v1/security/me").send(),
                        TIMEOUT).statusCode(), "The follower must reject the revoked certificate");
            } finally {
                client.close();
            }
        }
    }

    /**
     * Register item SEC-11: the update replaces the whole revocation set, so a body without the serial list
     * must be rejected. Treating it as an empty list would silently clear every revocation.
     */
    @Test
    void revocationUpdateWithoutASerialListIsRejectedAndChangesNothing(Vertx vertx) throws Exception {
        TlsMaterial tls = TlsMaterial.create(tempDir.resolve("tls"));
        SecurityIdentity identity = elevatedSecurityIdentity("security-administrator", tls.clientSubject());
        SecurityConfig config = tls.config(Set.of(), Set.of(tls.serverSerial()),
                Map.of(tls.clientSubject(), identity), tempDir.resolve("missing-serials-audit.jsonl"));
        RunningServer running = startServer(vertx, config, AuditSink.noOp());
        WebClient client = tls.authenticatedClient(vertx);
        try {
            int port = running.server().actualPort();
            HttpResponse<Buffer> update = awaitSuccess(client
                    .put(port, "localhost", "/api/v1/security/trust/revocations")
                    .sendJsonObject(new JsonObject().put("trustBundleVersion", "no-serials")), TIMEOUT);
            assertEquals(400, update.statusCode());

            JsonObject trust = awaitSuccess(client.get(port, "localhost", "/api/v1/security/trust").send(),
                    TIMEOUT).bodyAsJsonObject();
            assertEquals("configuration", trust.getString("trustBundleVersion"));
            assertEquals(1, trust.getInteger("revokedCertificateCount"), "The configured revocation must remain");
        } finally {
            client.close();
            running.close();
        }
    }

    /** Register item SEC-11: the audit record of a revocation update must say which serials it revoked. */
    @Test
    void revocationAuditRecordsTheRevokedSerials(Vertx vertx) throws Exception {
        TlsMaterial tls = TlsMaterial.create(tempDir.resolve("tls"));
        List<AuditEvent> events = new java.util.concurrent.CopyOnWriteArrayList<>();
        SecurityIdentity identity = elevatedSecurityIdentity("security-administrator", tls.clientSubject());
        SecurityConfig config = tls.config(Set.of(), Set.of(), Map.of(tls.clientSubject(), identity),
                tempDir.resolve("serials-audit.jsonl"));
        RunningServer running = startServer(vertx, config, events::add);
        WebClient client = tls.authenticatedClient(vertx);
        try {
            HttpResponse<Buffer> update = awaitSuccess(client
                    .put(running.server().actualPort(), "localhost", "/api/v1/security/trust/revocations")
                    .sendJsonObject(new JsonObject()
                            .put("trustBundleVersion", "serials-v2")
                            .put("revokedCertificateSerials", new JsonArray().add("00:0A:1B").add("ff01"))), TIMEOUT);
            assertEquals(200, update.statusCode());
            AuditEvent change = events.stream()
                    .filter(event -> "SECURITY_CONFIGURATION_CHANGE".equals(event.eventType()))
                    .findFirst().orElseThrow();
            assertEquals("A1B,FF01", change.attributes().get("revokedCertificateSerials"));
        } finally {
            client.close();
            running.close();
        }
    }

    /**
     * Register item SEC-11: a revocation change whose audit record cannot be made durable must not take
     * effect, or the request reports failure for a change that has been applied.
     */
    @Test
    void revocationUpdateIsNotAppliedWhenItsAuditFails(Vertx vertx) throws Exception {
        TlsMaterial tls = TlsMaterial.create(tempDir.resolve("tls"));
        SecurityIdentity identity = elevatedSecurityIdentity("security-administrator", tls.clientSubject());
        SecurityConfig config = tls.config(Set.of(), Set.of(), Map.of(tls.clientSubject(), identity),
                tempDir.resolve("failing-audit.jsonl"));
        AuditSink failsConfigurationChanges = event -> {
            if ("SECURITY_CONFIGURATION_CHANGE".equals(event.eventType())) {
                throw new IllegalStateException("audit storage unavailable");
            }
        };
        RunningServer running = startServer(vertx, config, failsConfigurationChanges);
        WebClient client = tls.authenticatedClient(vertx);
        try {
            int port = running.server().actualPort();
            HttpResponse<Buffer> update = awaitSuccess(client
                    .put(port, "localhost", "/api/v1/security/trust/revocations")
                    .sendJsonObject(new JsonObject()
                            .put("trustBundleVersion", "unaudited")
                            .put("revokedCertificateSerials", new JsonArray().add(tls.clientSerial()))), TIMEOUT);
            assertTrue(update.statusCode() >= 500, "An unaudited change must fail, got " + update.statusCode());

            assertEquals(200, awaitSuccess(client.get(port, "localhost", "/api/v1/security/me").send(),
                    TIMEOUT).statusCode(), "The revocation must not have been applied");
            assertEquals("configuration", awaitSuccess(client.get(port, "localhost", "/api/v1/security/trust")
                    .send(), TIMEOUT).bodyAsJsonObject().getString("trustBundleVersion"));
        } finally {
            client.close();
            running.close();
        }
    }

    /**
     * Register item SEC-09: an authorization check evaluates policy and changes nothing, so a follower
     * answers it although it is a POST.
     */
    @Test
    void authorizationCheckIsAnsweredByAFollower(Vertx vertx) throws Exception {
        TlsMaterial tls = TlsMaterial.create(tempDir.resolve("tls"));
        SecurityIdentity identity = elevatedSecurityIdentity("security-reviewer", tls.clientSubject());
        SecurityConfig config = tls.config(Set.of(), Set.of(), Map.of(tls.clientSubject(), identity),
                tempDir.resolve("follower-check-audit.jsonl"));
        try (RunningCluster cluster = startServerOnFollower(vertx, config)) {
            WebClient client = tls.authenticatedClient(vertx);
            try {
                HttpResponse<Buffer> check = awaitSuccess(client
                        .post(cluster.server().actualPort(), "localhost", "/api/v1/security/authorization/check")
                        .sendJsonObject(new JsonObject().put("method", "GET").put("path", "/api/v1/info")),
                        TIMEOUT);
                assertEquals(200, check.statusCode(), "A follower must answer a read-only authorization check");
                assertEquals(true, check.bodyAsJsonObject().getBoolean("allowed"));
            } finally {
                client.close();
            }
        }
    }

    /**
     * Register item ENG-25: an agent identity may deregister only itself, an operator may deregister any
     * agent of its tenant, an integration identity may deregister none, and each attempt is audited.
     */
    @Test
    void agentDeregistrationIsSelfOnlyForAgentsAndAllowedForOperators(Vertx vertx) throws Exception {
        TlsMaterial tls = TlsMaterial.create(tempDir.resolve("tls"));
        List<AuditEvent> events = new java.util.concurrent.CopyOnWriteArrayList<>();
        assertEquals(403, deregisterAs(vertx, tls, events, Set.of(SecurityRole.AGENT), IdentityType.AGENT,
                "agent-self", "agent-other"), "An agent identity must not deregister another agent");
        assertEquals(204, deregisterAs(vertx, tls, events, Set.of(SecurityRole.AGENT), IdentityType.AGENT,
                "agent-self", "agent-self"), "An agent identity deregisters itself");
        assertEquals(204, deregisterAs(vertx, tls, events, Set.of(SecurityRole.OPERATOR), IdentityType.HUMAN,
                "payments-operator", "agent-other"), "An operator deregisters an agent of its tenant");
        assertEquals(403, deregisterAs(vertx, tls, events, Set.of(SecurityRole.SERVICE_INTEGRATION),
                IdentityType.SERVICE_INTEGRATION, "payments-batch", "agent-other"),
                "An integration identity has no deregistration scope");

        awaitSuccess(eventually(vertx, () -> events.stream().anyMatch(event ->
                        "MUTATION".equals(event.eventType()) && "SUCCESS".equals(event.outcome())
                                && "agent-self".equals(event.principalId())
                                && "/api/v1/agents/agent-self".equals(event.path()))
                && events.stream().anyMatch(event ->
                        "MUTATION".equals(event.eventType()) && "FAILURE".equals(event.outcome())
                                && "agent-self".equals(event.principalId())
                                && "/api/v1/agents/agent-other".equals(event.path())), TIMEOUT),
                TIMEOUT.plusSeconds(1));
    }

    /** Starts a server whose only client identity has the given roles, and deletes one registered agent. */
    private int deregisterAs(Vertx vertx, TlsMaterial tls, List<AuditEvent> events, Set<SecurityRole> roles,
                             IdentityType type, String principal, String agentId) {
        SecurityIdentity identity = new SecurityIdentity(principal, type, "regulated-bank-a", "production",
                roles, Set.of(), tls.clientSubject(), Instant.now(), Instant.now().plusSeconds(300), null);
        SecurityConfig config = tls.config(Set.of(), Set.of(), Map.of(tls.clientSubject(), identity),
                tempDir.resolve("deregistration-audit-" + System.nanoTime() + ".jsonl"));
        RunningServer running = startServer(vertx, config, events::add);
        WebClient client = tls.authenticatedClient(vertx);
        try {
            for (String registered : List.of("agent-self", "agent-other")) {
                AgentInfo agent = new AgentInfo(registered, registered + ".example.test", "127.0.0.1", 8080);
                agent.setTenantId("regulated-bank-a");
                agent.setStatus(AgentStatus.HEALTHY);
                running.state().apply(AgentCommand.register(agent));
            }
            return awaitSuccess(client.delete(running.server().actualPort(), "localhost",
                    "/api/v1/agents/" + agentId).send(), TIMEOUT).statusCode();
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

    /**
     * Starts a three-node cluster and an HTTP server on one of its followers. The other two nodes have
     * long election timeouts so that the leader is the fast node and the follower stays a follower.
     */
    private static RunningCluster startServerOnFollower(Vertx vertx, SecurityConfig config) {
        config.validate();
        String prefix = "security-follower-" + System.nanoTime() + "-";
        String leaderId = prefix + "leader";
        String followerId = prefix + "follower";
        String otherId = prefix + "other";
        Set<String> members = Set.of(leaderId, followerId, otherId);
        QuorusStateStore followerState = new QuorusStateStore();
        RaftNode follower = clusterNode(vertx, followerId, members, followerState, 30_000);
        RaftNode other = clusterNode(vertx, otherId, members, new QuorusStateStore(), 45_000);
        RaftNode leader = clusterNode(vertx, leaderId, members, new QuorusStateStore(), 400);
        awaitSuccess(follower.start(), TIMEOUT);
        awaitSuccess(other.start(), TIMEOUT);
        awaitSuccess(leader.start(), TIMEOUT);
        awaitSuccess(eventually(vertx, leader::isLeader, TIMEOUT), TIMEOUT.plusSeconds(1));
        awaitSuccess(eventually(vertx, () -> leaderId.equals(follower.getLeaderId()), TIMEOUT),
                TIMEOUT.plusSeconds(1));
        HttpApiServer server = new HttpApiServer(vertx, "127.0.0.1", 0, follower, followerState, -1,
                ControllerTestConfig.create(), config, AuditSink.noOp());
        awaitSuccess(server.start(), TIMEOUT);
        return new RunningCluster(server, List.of(leader, follower, other));
    }

    private static RaftNode clusterNode(Vertx vertx, String nodeId, Set<String> members, QuorusStateStore state,
                                        long electionTimeoutMs) {
        return RaftNode.builder()
                .vertx(vertx)
                .nodeId(nodeId)
                .clusterNodes(members)
                .transport(new InMemoryTransportSimulator(nodeId))
                .stateMachine(state)
                .mode(RaftNodeMode.volatileMode())
                .electionTimeout(electionTimeoutMs)
                .heartbeatInterval(100)
                .build();
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

    private record RunningCluster(HttpApiServer server, List<RaftNode> nodes) implements AutoCloseable {
        @Override
        public void close() {
            awaitSuccess(server.stop(), TIMEOUT);
            nodes.forEach(node -> awaitSuccess(node.stop(), TIMEOUT));
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
