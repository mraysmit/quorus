/*
 * Copyright 2026 Mark Andrew Ray-Smith Cityline Ltd
 * Licensed under the Apache License, Version 2.0.
 */
package dev.mars.quorus.agent;

import dev.mars.quorus.agent.config.AgentConfiguration;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServer;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.junit5.VertxExtension;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.CsvSource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static dev.mars.quorus.testing.TestFutureUtils.awaitSuccess;
import static org.junit.jupiter.api.Assertions.*;

/** Real agent startup/polling/authorization/status HTTP boundary; no private-method invocation. */
@ExtendWith(VertxExtension.class)
class PreExecutionFailureIntegrationTest {
    @TempDir Path root;
    private QuorusAgent agent;
    private HttpServer server;

    @AfterEach
    void stop() throws Exception {
        if (agent != null) {
            agent.shutdown();
            agent.awaitShutdown();
        }
        if (server != null) awaitSuccess(server.close(), Duration.ofSeconds(5));
    }

    @ParameterizedTest
    @ValueSource(strings = {"path", "policy", "revoked-secret", "missing-provider", "failed-ack", "malformed-request"})
    void preparationRejectionReportsFailedFromAcceptedWithoutStartingTransfer(String rejection, Vertx vertx)
            throws Exception {
        Path downloadRoot = Files.createDirectory(root.resolve("downloads"));
        Path destination = (rejection.equals("path") ? root : downloadRoot).resolve("settlement.dat");
        List<JsonObject> reports = new ArrayList<>();
        Promise<List<JsonObject>> terminal = Promise.promise();
        AtomicBoolean offered = new AtomicBoolean();
        AtomicInteger fileRequests = new AtomicInteger();
        AtomicBoolean failedAckDropped = new AtomicBoolean();
        JsonObject job = new JsonObject()
                .put("assignmentId", "settlement:payments-agent")
                .put("jobId", "settlement").put("agentId", "payments-agent")
                .put("tenantId", "bank-a").put("attemptId", "settlement-attempt-1")
                .put("fencingGeneration", 1L).put("lastReportSequence", 0L)
                .put("leaseExpiresAt", Instant.now().plusSeconds(60).toString())
                .put("sourceUri", "sftp://127.0.0.1/in/settlement.dat")
                .put("destinationUri", destination.toUri().toString())
                .put("remotePath", rejection.equals("policy") ? "/denied/settlement.dat" : "/in/settlement.dat")
                .put("controllerResolvedAddresses", new JsonArray().add("127.0.0.1"))
                .put("serviceConnection", connection())
                .put("secretReference", new JsonObject()
                        .put("secretReferenceId", "payments-key").put("tenantId", "bank-a")
                        .put("provider", "VAULT_KV_V2").put("path", "secret/data/payments")
                        .put("key", "password").put("version", "1")
                        .put("status", rejection.equals("revoked-secret") ? "REVOKED" : "ACTIVE"));
        if (rejection.equals("malformed-request")) {
            job.remove("serviceConnection");
            job.remove("secretReference");
            job.put("sourceUri", "invalid URI");
        }
        Router router = Router.router(vertx);
        router.route().handler(BodyHandler.create());
        router.post("/api/v1/agents/register").handler(ctx -> ctx.json(new JsonObject().put("status", "registered")));
        router.delete("/api/v1/agents/:id").handler(ctx -> ctx.response().setStatusCode(204).end());
        router.get("/api/v1/agents/:id/jobs").handler(ctx -> ctx.json(new JsonObject()
                .put("pendingJobs", offered.getAndSet(true) ? new JsonArray() : new JsonArray().add(job))));
        router.post("/api/v1/jobs/:id/status").handler(ctx -> {
            JsonObject report = ctx.body().asJsonObject();
            reports.add(report.copy());
            if (rejection.equals("failed-ack") && "FAILED".equals(report.getString("status"))
                    && !failedAckDropped.getAndSet(true)) {
                ctx.request().connection().close();
                return;
            }
            ctx.json(new JsonObject().put("success", true));
            if ("FAILED".equals(report.getString("status"))) terminal.tryComplete(List.copyOf(reports));
        });
        router.get("/in/settlement.dat").handler(ctx -> {
            fileRequests.incrementAndGet();
            ctx.response().end("must-not-transfer");
        });
        server = awaitSuccess(vertx.createHttpServer().requestHandler(router).listen(0), Duration.ofSeconds(5));
        agent = new QuorusAgent(vertx, new AgentConfiguration.Builder()
                .securityProfile("development").allowInsecure(true).controllerTlsEnabled(false)
                .agentId("payments-agent").tenantId("bank-a").agentPort(0)
                .controllerUrl("http://localhost:" + server.actualPort() + "/api/v1")
                .downloadRoot(downloadRoot).uploadRoot(root).agentPool("payments").networkZone("restricted")
                .jobPollingInitialDelayMs(1).jobPollingIntervalMs(20).build());
        agent.start();
        List<JsonObject> observed = awaitSuccess(terminal.future(), Duration.ofSeconds(10));
        assertEquals(rejection.equals("failed-ack") ? List.of("ACCEPTED", "FAILED", "FAILED")
                : List.of("ACCEPTED", "FAILED"), observed.stream().map(r -> r.getString("status")).toList());
        if (rejection.equals("failed-ack")) assertEquals(observed.get(1), observed.get(2));
        JsonObject failed = observed.get(1);
        String code = switch (rejection) {
            case "path" -> "Q-LOCAL-PATH";
            case "policy" -> "Remote path is outside the approved path scope";
            case "revoked-secret" -> "Secret reference is revoked or expired";
            case "malformed-request" -> "Illegal character in path";
            default -> "Secret provider is not configured";
        };
        assertTrue(failed.getString("errorMessage").contains(code), "Must exercise the intended rejection");
        assertEquals("ACCEPTED", failed.getString("expectedState"));
        assertEquals(2L, failed.getLong("reportSequence"));
        assertEquals(1L, failed.getLong("fencingGeneration"));
        assertEquals("settlement-attempt-1", failed.getString("attemptId"));
        assertFalse(Files.exists(destination));
        assertEquals(0, fileRequests.get());
    }

    private static JsonObject connection() {
        return new JsonObject().put("serviceConnectionId", "payments-sftp").put("tenantId", "bank-a")
                .put("protocol", "SFTP").put("endpoint", "sftp://127.0.0.1:22")
                .put("networkZone", "restricted").put("allowedPaths", new JsonArray().add("/in"))
                .put("allowedDirections", new JsonArray().add("DOWNLOAD"))
                .put("allowedAgentPools", new JsonArray().add("payments"))
                .put("owner", "payments-ops").put("environment", "TEST").put("classification", "CONFIDENTIAL")
                .put("secretReferenceId", "payments-key").put("serviceIdentity", "payments-batch")
                .put("authenticationType", "PASSWORD").put("policyVersion", 1).put("status", "ACTIVE")
                .put("createdAt", Instant.now().toString()).put("updatedAt", Instant.now().toString())
                .put("trustPolicy", new JsonObject().put("tlsRequired", false).put("hostnameVerification", false)
                        .put("sshHostKeyFingerprints", new JsonArray().add("SHA256:synthetic"))
                        .put("minimumTlsVersion", "TLSv1.3").put("transportEncryptionRequired", true))
                .put("egressPolicy", new JsonObject().put("allowedHostnames", new JsonArray().add("127.0.0.1"))
                        .put("allowedCidrs", new JsonArray().add("127.0.0.1/32"))
                        .put("allowedPorts", new JsonArray().add(22)).put("allowRedirects", false)
                        .put("pinResolvedAddresses", true));
    }

    @ParameterizedTest
    @CsvSource({"ACCEPTED,503", "IN_PROGRESS,503", "COMPLETED,503", "IN_PROGRESS,0",
            "IN_PROGRESS,403", "IN_PROGRESS,409", "IN_PROGRESS,-503", "IN_PROGRESS,202"})
    void uncertainAcknowledgementReplaysExactReportBeforeAdvancing(String uncertainStatus, int responseCode,
                                                                   Vertx vertx) throws Exception {
        Path destination = root.resolve("settlement.dat");
        List<JsonObject> reports = new ArrayList<>();
        Promise<List<JsonObject>> terminal = Promise.promise();
        AtomicBoolean offered = new AtomicBoolean();
        AtomicBoolean dropped = new AtomicBoolean();
        AtomicInteger fileRequests = new AtomicInteger();
        boolean unresolved = responseCode == 403 || responseCode == 409 || responseCode < 0;
        boolean repeatedPoll = responseCode == 202;
        Router router = Router.router(vertx);
        router.route().handler(BodyHandler.create());
        router.post("/api/v1/agents/register").handler(ctx -> ctx.json(new JsonObject().put("status", "registered")));
        router.delete("/api/v1/agents/:id").handler(ctx -> ctx.response().setStatusCode(204).end());
        router.get("/api/v1/agents/:id/jobs").handler(ctx -> ctx.json(new JsonObject().put("pendingJobs",
                offered.getAndSet(true) && !repeatedPoll ? new JsonArray() : new JsonArray().add(new JsonObject()
                        .put("jobId", "settlement").put("agentId", "payments-agent")
                        .put("attemptId", "settlement-attempt-1").put("fencingGeneration", 1L)
                        .put("leaseExpiresAt", Instant.now().plusSeconds(60).toString())
                        .put("sourceUri", "http://localhost:" + server.actualPort() + "/in/settlement.dat")
                        .put("destinationUri", destination.toUri().toString()).put("totalBytes", 7L)))));
        router.post("/api/v1/jobs/:id/status").handler(ctx -> {
            JsonObject report = ctx.body().asJsonObject();
            reports.add(report.copy());
            if (!repeatedPoll && uncertainStatus.equals(report.getString("status")) && (unresolved || !dropped.getAndSet(true))) {
                if (unresolved && !dropped.getAndSet(true)) {
                    vertx.setTimer(1500, id -> terminal.tryComplete(List.copyOf(reports)));
                }
                if (responseCode == 0) ctx.request().connection().close();
                else ctx.response().setStatusCode(Math.abs(responseCode)).end();
                return;
            }
            ctx.json(new JsonObject().put("success", true));
            if ("COMPLETED".equals(report.getString("status"))) terminal.tryComplete(List.copyOf(reports));
        });
        router.get("/in/settlement.dat").handler(ctx -> {
            fileRequests.incrementAndGet();
            if (repeatedPoll) vertx.setTimer(200, id -> ctx.response().end("payment"));
            else ctx.response().end("payment");
        });
        server = awaitSuccess(vertx.createHttpServer().requestHandler(router).listen(0), Duration.ofSeconds(5));
        agent = new QuorusAgent(vertx, new AgentConfiguration.Builder()
                .securityProfile("development").allowInsecure(true).controllerTlsEnabled(false)
                .agentId("payments-agent").tenantId("bank-a").agentPort(0)
                .controllerUrl("http://localhost:" + server.actualPort() + "/api/v1")
                .jobPollingInitialDelayMs(1).jobPollingIntervalMs(20).httpIdleTimeout(500)
                .build());
        agent.start();
        List<JsonObject> observed = awaitSuccess(terminal.future(), Duration.ofSeconds(5));
        if (unresolved) {
            assertEquals(responseCode < 0 ? 4 : 2, observed.size(), "Only ACCEPTED and the rejected/unresolved start report(s)");
            assertTrue(observed.stream().noneMatch(r -> "FAILED".equals(r.getString("status"))),
                    "An uncertain start must not invent an expected state or consume the next sequence");
            assertEquals(1L, observed.stream().filter(r -> "IN_PROGRESS".equals(r.getString("status"))).distinct().count());
            assertEquals(0, fileRequests.get());
            assertFalse(Files.exists(destination));
            return;
        }
        assertEquals(repeatedPoll ? 3 : 4, observed.size());
        List<JsonObject> retries = observed.stream().filter(r -> uncertainStatus.equals(r.getString("status"))).toList();
        assertEquals(repeatedPoll ? 1 : 2, retries.size());
        if (!repeatedPoll) assertEquals(retries.get(0), retries.get(1), "Replay must not allocate another sequence or change expected state");
        assertEquals(List.of(1L, 2L, 3L), observed.stream().map(r -> r.getLong("reportSequence")).distinct().toList());
        assertEquals(1, fileRequests.get());
        assertEquals("payment", Files.readString(destination));
    }
}
