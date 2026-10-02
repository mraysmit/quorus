/*
 * Copyright 2026 Mark Andrew Ray-Smith Cityline Ltd
 * Licensed under the Apache License, Version 2.0.
 */
package dev.mars.quorus.agent;

import com.fasterxml.jackson.databind.JsonNode;
import dev.mars.quorus.agent.config.AgentConfiguration;
import dev.mars.quorus.agent.testing.FakeController;
import dev.mars.quorus.agent.testing.FakeController.Reply;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.jupiter.api.Assertions.*;

/** Real agent startup/polling/authorization/status HTTP boundary; no private-method invocation. */
@Timeout(value = 60, unit = SECONDS, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
class PreExecutionFailureIntegrationTest {
    private static final String STATUS = "/api/v1/jobs/.+/status";
    private static final String JOBS = "/api/v1/agents/.+/jobs";

    @TempDir Path root;
    private QuorusAgent agent;
    private FakeController controller;

    @AfterEach
    void stop() throws Exception {
        if (agent != null) {
            agent.shutdown();
            agent.awaitShutdown();
        }
        if (controller != null) controller.close();
    }

    @ParameterizedTest
    @ValueSource(strings = {"path", "policy", "revoked-secret", "missing-provider", "failed-ack", "malformed-request"})
    void preparationRejectionReportsFailedFromAcceptedWithoutStartingTransfer(String rejection) throws Exception {
        Path downloadRoot = Files.createDirectory(root.resolve("downloads"));
        Path destination = (rejection.equals("path") ? root : downloadRoot).resolve("settlement.dat");
        List<JsonNode> reports = new CopyOnWriteArrayList<>();
        CompletableFuture<List<JsonNode>> terminal = new CompletableFuture<>();
        AtomicBoolean offered = new AtomicBoolean();
        AtomicInteger fileRequests = new AtomicInteger();
        AtomicBoolean failedAckDropped = new AtomicBoolean();
        String governed = rejection.equals("malformed-request") ? "" : ",\"serviceConnection\":" + connection()
                + ",\"secretReference\":{\"secretReferenceId\":\"payments-key\",\"tenantId\":\"bank-a\","
                + "\"provider\":\"VAULT_KV_V2\",\"path\":\"secret/data/payments\",\"key\":\"password\","
                + "\"version\":\"1\",\"status\":\"" + (rejection.equals("revoked-secret") ? "REVOKED" : "ACTIVE") + "\"}";
        String job = "{\"assignmentId\":\"settlement:payments-agent\",\"jobId\":\"settlement\","
                + "\"agentId\":\"payments-agent\",\"tenantId\":\"bank-a\",\"attemptId\":\"settlement-attempt-1\","
                + "\"fencingGeneration\":1,\"lastReportSequence\":0,"
                + "\"leaseExpiresAt\":\"" + Instant.now().plusSeconds(60) + "\","
                + "\"sourceUri\":\"" + (rejection.equals("malformed-request") ? "invalid URI" : "sftp://127.0.0.1/in/settlement.dat")
                + "\",\"destinationUri\":\"" + destination.toUri() + "\","
                + "\"remotePath\":\"" + (rejection.equals("policy") ? "/denied/settlement.dat" : "/in/settlement.dat") + "\","
                + "\"controllerResolvedAddresses\":[\"127.0.0.1\"]" + governed + "}";
        controller = FakeController.start()
                .on("POST", "/api/v1/agents/register", Reply.json(200, "{\"status\":\"registered\"}").always())
                .on("DELETE", "/api/v1/agents/.+", Reply.status(204).always())
                .on("GET", "/api/v1/agents/.+/jobs", request -> Reply.json(200,
                        "{\"pendingJobs\":[" + (offered.getAndSet(true) ? "" : job) + "]}"))
                .on("POST", STATUS, request -> {
                    JsonNode report = request.json();
                    reports.add(report);
                    if (rejection.equals("failed-ack") && "FAILED".equals(report.get("status").asText())
                            && !failedAckDropped.getAndSet(true)) {
                        return Reply.drop();
                    }
                    if ("FAILED".equals(report.get("status").asText())) terminal.complete(List.copyOf(reports));
                    return Reply.json(200, "{\"success\":true}");
                })
                .on("GET", "/in/settlement.dat", request -> {
                    fileRequests.incrementAndGet();
                    return Reply.json(200, "must-not-transfer");
                });
        agent = new QuorusAgent(new AgentConfiguration.Builder()
                .securityProfile("development").allowInsecure(true).controllerTlsEnabled(false)
                .agentId("payments-agent").tenantId("bank-a").agentPort(0)
                .controllerUrl(controller.url() + "/api/v1")
                .downloadRoot(downloadRoot).uploadRoot(root).agentPool("payments").networkZone("restricted")
                .jobPollingInitialDelayMs(1).jobPollingIntervalMs(20).build());
        agent.start();
        List<JsonNode> observed = terminal.get(10, TimeUnit.SECONDS);
        assertEquals(rejection.equals("failed-ack") ? List.of("ACCEPTED", "FAILED", "FAILED")
                : List.of("ACCEPTED", "FAILED"), observed.stream().map(r -> r.get("status").asText()).toList());
        if (rejection.equals("failed-ack")) assertEquals(observed.get(1), observed.get(2));
        JsonNode failed = observed.get(1);
        String code = switch (rejection) {
            case "path" -> "Q-LOCAL-PATH";
            case "policy" -> "Remote path is outside the approved path scope";
            case "revoked-secret" -> "Secret reference is revoked or expired";
            case "malformed-request" -> "Illegal character in path";
            default -> "Secret provider is not configured";
        };
        assertTrue(failed.get("errorMessage").asText().contains(code),
                () -> "Must exercise the intended rejection: " + failed);
        assertEquals("ACCEPTED", failed.get("expectedState").asText());
        assertEquals(2L, failed.get("reportSequence").asLong());
        assertEquals(1L, failed.get("fencingGeneration").asLong());
        assertEquals("settlement-attempt-1", failed.get("attemptId").asText());
        assertFalse(Files.exists(destination));
        assertEquals(0, fileRequests.get());
    }

    private static String connection() {
        String now = Instant.now().toString();
        return "{\"serviceConnectionId\":\"payments-sftp\",\"tenantId\":\"bank-a\",\"protocol\":\"SFTP\","
                + "\"endpoint\":\"sftp://127.0.0.1:22\",\"networkZone\":\"restricted\",\"allowedPaths\":[\"/in\"],"
                + "\"allowedDirections\":[\"DOWNLOAD\"],\"allowedAgentPools\":[\"payments\"],"
                + "\"owner\":\"payments-ops\",\"environment\":\"TEST\",\"classification\":\"CONFIDENTIAL\","
                + "\"secretReferenceId\":\"payments-key\",\"serviceIdentity\":\"payments-batch\","
                + "\"authenticationType\":\"PASSWORD\",\"policyVersion\":1,\"status\":\"ACTIVE\","
                + "\"createdAt\":\"" + now + "\",\"updatedAt\":\"" + now + "\","
                + "\"trustPolicy\":{\"tlsRequired\":false,\"hostnameVerification\":false,"
                + "\"sshHostKeyFingerprints\":[\"SHA256:synthetic\"],\"minimumTlsVersion\":\"TLSv1.3\","
                + "\"transportEncryptionRequired\":true},"
                + "\"egressPolicy\":{\"allowedHostnames\":[\"127.0.0.1\"],\"allowedCidrs\":[\"127.0.0.1/32\"],"
                + "\"allowedPorts\":[22],\"allowRedirects\":false,\"pinResolvedAddresses\":true}}";
    }

    @ParameterizedTest
    @CsvSource({"ACCEPTED,503", "IN_PROGRESS,503", "COMPLETED,503", "IN_PROGRESS,0",
            "IN_PROGRESS,403", "IN_PROGRESS,409", "IN_PROGRESS,-503", "IN_PROGRESS,202"})
    void uncertainAcknowledgementReplaysExactReportBeforeAdvancing(String uncertainStatus, int responseCode)
            throws Exception {
        Path destination = root.resolve("settlement.dat");
        List<JsonNode> reports = new CopyOnWriteArrayList<>();
        CompletableFuture<List<JsonNode>> terminal = new CompletableFuture<>();
        AtomicBoolean offered = new AtomicBoolean();
        AtomicBoolean dropped = new AtomicBoolean();
        AtomicInteger fileRequests = new AtomicInteger();
        boolean unresolved = responseCode == 403 || responseCode == 409 || responseCode < 0;
        boolean repeatedPoll = responseCode == 202;
        controller = FakeController.start();
        String job = "{\"jobId\":\"settlement\",\"agentId\":\"payments-agent\","
                + "\"attemptId\":\"settlement-attempt-1\",\"fencingGeneration\":1,"
                + "\"leaseExpiresAt\":\"" + Instant.now().plusSeconds(60) + "\","
                + "\"sourceUri\":\"" + controller.url() + "/in/settlement.dat\","
                + "\"destinationUri\":\"" + destination.toUri() + "\",\"totalBytes\":7}";
        controller.on("POST", "/api/v1/agents/register", Reply.json(200, "{\"status\":\"registered\"}").always())
                .on("DELETE", "/api/v1/agents/.+", Reply.status(204).always())
                .on("GET", JOBS, request -> Reply.json(200, "{\"pendingJobs\":["
                        + (offered.getAndSet(true) && !repeatedPoll ? "" : job) + "]}"))
                .on("POST", STATUS, request -> {
                    JsonNode report = request.json();
                    reports.add(report);
                    if (!repeatedPoll && uncertainStatus.equals(report.get("status").asText())
                            && (unresolved || !dropped.getAndSet(true))) {
                        return responseCode == 0 ? Reply.drop() : Reply.status(Math.abs(responseCode));
                    }
                    if ("COMPLETED".equals(report.get("status").asText())) terminal.complete(List.copyOf(reports));
                    return Reply.json(200, "{\"success\":true}");
                })
                .onExchange("GET", "/in/settlement.dat", exchange -> {
                    fileRequests.incrementAndGet();
                    try (exchange) {
                        if (repeatedPoll) {
                            // Hold the transfer until three more polls have offered the same attempt.
                            int polls = controller.requests("GET", JOBS).size();
                            try {
                                controller.awaitRequests("GET", JOBS, polls + 3, java.time.Duration.ofSeconds(10));
                            } catch (InterruptedException e) {
                                return;
                            }
                        }
                        byte[] body = "payment".getBytes(StandardCharsets.UTF_8);
                        exchange.sendResponseHeaders(200, body.length);
                        try (OutputStream out = exchange.getResponseBody()) {
                            out.write(body);
                        }
                    }
                });
        agent = new QuorusAgent(new AgentConfiguration.Builder()
                .securityProfile("development").allowInsecure(true).controllerTlsEnabled(false)
                .agentId("payments-agent").tenantId("bank-a").agentPort(0)
                .controllerUrl(controller.url() + "/api/v1")
                .jobPollingInitialDelayMs(1).jobPollingIntervalMs(20).httpIdleTimeout(500)
                .build());
        agent.start();
        List<JsonNode> observed;
        if (unresolved) {
            // ACCEPTED, then one rejected start report or three unresolved sends; once they have arrived,
            // the job thread's end shows that nothing more will be reported.
            controller.awaitRequests("POST", STATUS, responseCode < 0 ? 4 : 2, java.time.Duration.ofSeconds(10));
            assertTrue(agent.awaitJobs(java.time.Duration.ofSeconds(10)), "the job should end");
            observed = List.copyOf(reports);
        } else {
            observed = terminal.get(10, TimeUnit.SECONDS);
        }
        if (unresolved) {
            assertEquals(responseCode < 0 ? 4 : 2, observed.size(), "Only ACCEPTED and the rejected/unresolved start report(s)");
            assertTrue(observed.stream().noneMatch(r -> "FAILED".equals(r.get("status").asText())),
                    "An uncertain start must not invent an expected state or consume the next sequence");
            assertEquals(1L, observed.stream().filter(r -> "IN_PROGRESS".equals(r.get("status").asText())).distinct().count());
            assertEquals(0, fileRequests.get());
            assertFalse(Files.exists(destination));
            return;
        }
        assertEquals(repeatedPoll ? 3 : 4, observed.size());
        List<JsonNode> retries = observed.stream().filter(r -> uncertainStatus.equals(r.get("status").asText())).toList();
        assertEquals(repeatedPoll ? 1 : 2, retries.size());
        if (!repeatedPoll) assertEquals(retries.get(0), retries.get(1), "Replay must not allocate another sequence or change expected state");
        List<Long> sequences = new ArrayList<>(observed.stream().map(r -> r.get("reportSequence").asLong()).distinct().toList());
        assertEquals(List.of(1L, 2L, 3L), sequences);
        assertEquals(1, fileRequests.get());
        assertEquals("payment", Files.readString(destination));
    }
}
