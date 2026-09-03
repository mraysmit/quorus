/*
 * Copyright 2026 Mark Andrew Ray-Smith Cityline Ltd
 * Licensed under the Apache License, Version 2.0.
 */
package dev.mars.quorus.controller.http;

import dev.mars.quorus.controller.raft.InMemoryTransportSimulator;
import dev.mars.quorus.controller.raft.RaftNode;
import dev.mars.quorus.controller.raft.RaftNodeMode;
import dev.mars.quorus.controller.state.QuorusStateStore;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClient;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Set;

import static dev.mars.quorus.testing.TestFutureUtils.awaitSuccess;
import static dev.mars.quorus.testing.TestFutureUtils.eventually;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Phase 4 authoritative service-connection and opaque-secret HTTP contract. */
class ServiceConnectionHttpIntegrationTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(10);
    private static final String TENANT = "regulated-bank-a";
    private static Vertx vertx;
    private static RaftNode raftNode;
    private static QuorusStateStore stateStore;
    private static HttpApiServer server;
    private static WebClient client;

    @BeforeAll
    static void setUp() throws Exception {
        vertx = Vertx.vertx();
        stateStore = new QuorusStateStore();
        raftNode = RaftNode.builder()
                .vertx(vertx)
                .nodeId("phase4-service-connection-node")
                .clusterNodes(Set.of("phase4-service-connection-node"))
                .transport(new InMemoryTransportSimulator("phase4-service-connection-node"))
                .stateMachine(stateStore)
                .mode(RaftNodeMode.volatileMode())
                .electionTimeout(200)
                .heartbeatInterval(50)
                .build();
        awaitSuccess(raftNode.start(), TIMEOUT);
        awaitSuccess(eventually(vertx, raftNode::isLeader, TIMEOUT), TIMEOUT);
        server = new HttpApiServer(vertx, 0, raftNode, stateStore);
        awaitSuccess(server.start(), TIMEOUT);
        client = WebClient.create(vertx);
    }

    @AfterAll
    static void tearDown() throws Exception {
        if (client != null) client.close();
        if (server != null) awaitSuccess(server.stop(), TIMEOUT);
        if (raftNode != null) awaitSuccess(raftNode.stop(), TIMEOUT);
        if (vertx != null) awaitSuccess(vertx.close(), TIMEOUT);
    }

    @Test
    void governsConnectionLifecycleTransferUseValidationEventsAndSnapshot() throws Exception {
        JsonObject secretReference = new JsonObject()
                .put("secretReferenceId", "vault-payments-sftp")
                .put("tenantId", TENANT)
                .put("provider", "VAULT_KV_V2")
                .put("path", "quorus/data/payments/sftp")
                .put("key", "password")
                .put("version", "7")
                .put("status", "ACTIVE");
        HttpResponse<Buffer> secretCreated = awaitSuccess(client
                .post(server.actualPort(), "localhost", "/api/v1/secret-references")
                .sendJsonObject(secretReference), TIMEOUT);
        assertEquals(201, secretCreated.statusCode());
        assertFalse(secretCreated.bodyAsString().contains("secretValue"));

        HttpResponse<Buffer> secretRejected = awaitSuccess(client
                .post(server.actualPort(), "localhost", "/api/v1/secret-references")
                .sendJsonObject(secretReference.copy()
                        .put("secretReferenceId", "unsafe-secret")
                        .put("secretValue", "must-never-enter-state")), TIMEOUT);
        assertEquals(400, secretRejected.statusCode());
        assertFalse(stateStore.getSystemMetadata().values().stream()
                .anyMatch(value -> value.contains("must-never-enter-state")));

        JsonObject connection = new JsonObject()
                .put("serviceConnectionId", "payments-sftp")
                .put("serviceIdentity", "payments-batch")
                .put("authenticationType", "PASSWORD")
                .put("tenantId", TENANT)
                .put("protocol", "SFTP")
                .put("endpoint", "sftp://192.0.2.10:22")
                .put("networkZone", "payments-dmz")
                .put("allowedPaths", new JsonArray().add("/outbound"))
                .put("allowedDirections", new JsonArray().add("DOWNLOAD").add("UPLOAD"))
                .put("allowedAgentPools", new JsonArray().add("payments-agents"))
                .put("owner", "payments-platform")
                .put("environment", "PRODUCTION")
                .put("classification", "CONFIDENTIAL")
                .put("secretReferenceId", "vault-payments-sftp")
                .put("trustPolicy", new JsonObject()
                        .put("sshHostKeyFingerprints", new JsonArray().add("SHA256:synthetic-host-key-pin")))
                .put("egressPolicy", new JsonObject()
                        .put("allowedHostnames", new JsonArray().add("192.0.2.10"))
                        .put("allowedCidrs", new JsonArray().add("192.0.2.0/24"))
                        .put("allowedPorts", new JsonArray().add(22))
                        .put("allowRedirects", false)
                        .put("pinResolvedAddresses", true));

        HttpResponse<Buffer> connectionCreated = awaitSuccess(client
                .post(server.actualPort(), "localhost", "/api/v1/service-connections")
                .sendJsonObject(connection), TIMEOUT);
        assertEquals(201, connectionCreated.statusCode());
        assertFalse(connectionCreated.bodyAsString().contains("must-never-enter-state"));

        HttpResponse<Buffer> invalidPath = awaitSuccess(client
                .post(server.actualPort(), "localhost", "/api/v1/transfers")
                .sendJsonObject(governedTransfer("phase4-invalid-path", "/private/ledger.dat")), TIMEOUT);
        assertEquals(400, invalidPath.statusCode());
        assertFalse(stateStore.hasTransferJob("phase4-invalid-path"));

        HttpResponse<Buffer> submitted = awaitSuccess(client
                .post(server.actualPort(), "localhost", "/api/v1/transfers")
                .sendJsonObject(governedTransfer("phase4-governed-transfer", "/outbound/settlement.dat")), TIMEOUT);
        assertEquals(201, submitted.statusCode());

        HttpResponse<Buffer> transfer = awaitSuccess(client
                .get(server.actualPort(), "localhost", "/api/v1/transfers/phase4-governed-transfer")
                .send(), TIMEOUT);
        assertEquals(200, transfer.statusCode());
        assertEquals("payments-sftp", transfer.bodyAsJsonObject().getString("serviceConnectionId"));
        assertEquals("sftp://192.0.2.10:22/outbound/settlement.dat",
                transfer.bodyAsJsonObject().getString("sourceUri"));

        HttpResponse<Buffer> uploadSubmitted = awaitSuccess(client
                .post(server.actualPort(), "localhost", "/api/v1/transfers")
                .sendJsonObject(new JsonObject()
                        .put("jobId", "phase4-governed-upload")
                        .put("tenantId", TENANT)
                        .put("serviceConnectionId", "payments-sftp")
                        .put("remotePath", "/outbound/upload.dat")
                        .put("agentPool", "payments-agents")
                        .put("direction", "UPLOAD")
                        .put("sourceUri", "file:///C:/quorus-agent-spool/upload.dat")
                        .put("totalBytes", 1024L)), TIMEOUT);
        assertEquals(201, uploadSubmitted.statusCode());
        HttpResponse<Buffer> upload = awaitSuccess(client
                .get(server.actualPort(), "localhost", "/api/v1/transfers/phase4-governed-upload")
                .send(), TIMEOUT);
        assertEquals("file:///C:/quorus-agent-spool/upload.dat", upload.bodyAsJsonObject().getString("sourceUri"));
        assertEquals("sftp://192.0.2.10:22/outbound/upload.dat",
                upload.bodyAsJsonObject().getString("destinationUri"));

        HttpResponse<Buffer> validation = awaitSuccess(client
                .post(server.actualPort(), "localhost", "/api/v1/service-connections/payments-sftp/validate")
                .sendJsonObject(new JsonObject().put("tenantId", TENANT)
                        .put("direction", "DOWNLOAD")
                        .put("remotePath", "/outbound/settlement.dat")
                        .put("agentPool", "payments-agents")), TIMEOUT);
        assertEquals(200, validation.statusCode());
        assertEquals("POLICY_APPROVED", validation.bodyAsJsonObject().getString("status"));
        assertTrue(validation.bodyAsJsonObject().getJsonArray("stages").size() >= 2);

        byte[] snapshot = stateStore.takeSnapshot();
        stateStore.reset();
        stateStore.restoreSnapshot(snapshot);
        HttpResponse<Buffer> restored = awaitSuccess(client
                .get(server.actualPort(), "localhost", "/api/v1/service-connections/payments-sftp")
                .addQueryParam("tenantId", TENANT)
                .send(), TIMEOUT);
        assertEquals(200, restored.statusCode());
        assertEquals("payments-platform", restored.bodyAsJsonObject().getString("owner"));

        HttpResponse<Buffer> events = awaitSuccess(client
                .get(server.actualPort(), "localhost", "/api/v1/security-events")
                .addQueryParam("tenantId", TENANT)
                .send(), TIMEOUT);
        assertEquals(200, events.statusCode());
        assertTrue(events.bodyAsJsonObject().getJsonArray("events").size() >= 3);
        assertFalse(events.bodyAsString().contains("must-never-enter-state"));

        HttpResponse<Buffer> listedConnections = awaitSuccess(client
                .get(server.actualPort(), "localhost", "/api/v1/service-connections")
                .addQueryParam("tenantId", TENANT).send(), TIMEOUT);
        assertEquals(1, listedConnections.bodyAsJsonObject().getInteger("total"));
        HttpResponse<Buffer> updatedConnection = awaitSuccess(client
                .put(server.actualPort(), "localhost", "/api/v1/service-connections/payments-sftp")
                .sendJsonObject(new JsonObject().put("tenantId", TENANT)
                        .put("trustPolicy", connection.getJsonObject("trustPolicy").copy()
                                .put("sshHostKeyFingerprints", new JsonArray().add("SHA256:rotated-host-key")))), TIMEOUT);
        assertEquals(2, updatedConnection.bodyAsJsonObject().getInteger("policyVersion"));

        HttpResponse<Buffer> rotatedSecret = awaitSuccess(client
                .put(server.actualPort(), "localhost", "/api/v1/secret-references/vault-payments-sftp")
                .sendJsonObject(new JsonObject().put("tenantId", TENANT).put("version", "8")
                        .put("lastRotatedAt", "2026-09-03T04:00:00Z")), TIMEOUT);
        assertEquals("8", rotatedSecret.bodyAsJsonObject().getString("version"));

        HttpResponse<Buffer> lifecycleEvents = awaitSuccess(client
                .get(server.actualPort(), "localhost", "/api/v1/security-events")
                .addQueryParam("tenantId", TENANT).send(), TIMEOUT);
        String eventJson = lifecycleEvents.bodyAsString();
        assertTrue(eventJson.contains("SERVICE_CONNECTION_AUTHORIZED"));
        assertFalse(eventJson.contains("SERVICE_CONNECTION_LAST_USED"),
                "Submission must not be reported as actual agent use");
        assertTrue(eventJson.contains("SERVICE_TRUST_CHANGED"));
        assertTrue(eventJson.contains("SECRET_REFERENCE_ROTATED"));

        JsonObject expiredSecret = secretReference.copy()
                .put("secretReferenceId", "expired-secret")
                .put("expiresAt", "2026-09-03T00:00:00Z");
        assertEquals(201, awaitSuccess(client.post(server.actualPort(), "localhost",
                "/api/v1/secret-references").sendJsonObject(expiredSecret), TIMEOUT).statusCode());
        JsonObject expiredConnection = connection.copy()
                .put("serviceConnectionId", "expired-connection")
                .put("secretReferenceId", "expired-secret");
        assertEquals(201, awaitSuccess(client.post(server.actualPort(), "localhost",
                "/api/v1/service-connections").sendJsonObject(expiredConnection), TIMEOUT).statusCode());
        JsonObject expiredTransfer = governedTransfer("expired-transfer", "/outbound/expired.dat")
                .put("serviceConnectionId", "expired-connection");
        assertEquals(409, awaitSuccess(client.post(server.actualPort(), "localhost", "/api/v1/transfers")
                .sendJsonObject(expiredTransfer), TIMEOUT).statusCode());
        HttpResponse<Buffer> expiredReference = awaitSuccess(client.get(server.actualPort(), "localhost",
                "/api/v1/secret-references/expired-secret").addQueryParam("tenantId", TENANT).send(), TIMEOUT);
        assertEquals("EXPIRED", expiredReference.bodyAsJsonObject().getString("status"));
        HttpResponse<Buffer> expiryEvents = awaitSuccess(client.get(server.actualPort(), "localhost",
                "/api/v1/security-events").addQueryParam("tenantId", TENANT).send(), TIMEOUT);
        assertTrue(expiryEvents.bodyAsString().contains("SECRET_REFERENCE_EXPIRED"));

        assertEquals(200, awaitSuccess(client.delete(server.actualPort(), "localhost",
                "/api/v1/service-connections/payments-sftp").addQueryParam("tenantId", TENANT).send(), TIMEOUT)
                .statusCode());
        assertEquals(200, awaitSuccess(client.delete(server.actualPort(), "localhost",
                "/api/v1/secret-references/vault-payments-sftp").addQueryParam("tenantId", TENANT).send(), TIMEOUT)
                .statusCode());
    }

    private static JsonObject governedTransfer(String jobId, String remotePath) {
        return new JsonObject()
                .put("jobId", jobId)
                .put("tenantId", TENANT)
                .put("serviceConnectionId", "payments-sftp")
                .put("remotePath", remotePath)
                .put("agentPool", "payments-agents")
                .put("destinationPath", "target/" + jobId + ".dat")
                .put("totalBytes", 1024L);
    }
}
