/*
 * Copyright 2025 Mark Andrew Ray-Smith Cityline Ltd
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package dev.mars.quorus.agent.service;

import com.fasterxml.jackson.databind.JsonNode;
import dev.mars.quorus.agent.config.AgentConfiguration;
import dev.mars.quorus.agent.testing.FakeController;
import dev.mars.quorus.agent.testing.FakeController.Reply;
import dev.mars.quorus.core.TransferAttemptStatus;
import org.junit.jupiter.api.*;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for JobStatusReportingService, against a real HTTP controller stand-in (no mocking).
 *
 * @author Mark Andrew Ray-Smith Cityline Ltd
 * @since 2026-02-05
 * @version 2.0
 */
@Timeout(value = 30, unit = SECONDS, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
class JobStatusReportingServiceTest {

    private static final String STATUS = "/jobs/[^/]+/status";

    private FakeController controller;
    private ControllerClient client;
    private AgentConfiguration config;
    private final AtomicInteger responseStatus = new AtomicInteger(200);

    @BeforeEach
    void setUp() throws Exception {
        controller = FakeController.start()
                .on("POST", STATUS, request -> Reply.json(responseStatus.get(), "{\"status\":\"received\"}"));
        config = new AgentConfiguration.Builder()
                .securityProfile("development").allowInsecure(true).controllerTlsEnabled(false)
                .agentId("test-agent-status")
                .tenantId("test-tenant")
                .controllerUrl(controller.url())
                .region("test-region")
                .datacenter("test-dc")
                .build();
        client = ControllerClient.create(config);
    }

    @AfterEach
    void tearDown() {
        client.close();
        controller.close();
    }

    @Test
    @DisplayName("Should report ACCEPTED status")
    void testReportAccepted() throws Exception {
        service().reportAccepted("job-123");

        assertEquals(1, reports().size(), "One report should be sent");
        assertEquals("/jobs/job-123/status", reports().getFirst().path());
        JsonNode request = lastReport();
        assertEquals("test-agent-status", request.get("agentId").asText());
        assertEquals("ACCEPTED", request.get("status").asText());
        assertFalse(request.has("bytesTransferred"));
        assertFalse(request.has("errorMessage"));
    }

    @Test
    @DisplayName("Should report authoritative attempt identity, fence, and sequence")
    void testReportAcceptedWithAttemptFence() throws Exception {
        service().reportAccepted("job-fenced", "attempt-007", 7L, 4L);

        JsonNode request = lastReport();
        assertEquals("test-agent-status", request.get("agentId").asText());
        assertEquals("ACCEPTED", request.get("status").asText());
        assertEquals("attempt-007", request.get("attemptId").asText());
        assertEquals("OFFERED", request.get("expectedState").asText());
        assertEquals(7L, request.get("fencingGeneration").asLong());
        assertEquals(4L, request.get("reportSequence").asLong());
    }

    @Test
    @DisplayName("Should report IN_PROGRESS status with bytes transferred")
    void testReportInProgress() throws Exception {
        service().reportInProgress("job-456", 512000L);

        assertEquals(1, reports().size());
        assertEquals("/jobs/job-456/status", reports().getFirst().path());
        JsonNode request = lastReport();
        assertEquals("IN_PROGRESS", request.get("status").asText());
        assertEquals(512000L, request.get("bytesTransferred").asLong());
        assertFalse(request.has("errorMessage"));
    }

    @Test
    @DisplayName("Should report progress as IN_PROGRESS expecting IN_PROGRESS")
    void testReportProgress() throws Exception {
        service().reportProgress("job-p", 2048L, "attempt-1", 3L, 6L);

        JsonNode request = lastReport();
        assertEquals("IN_PROGRESS", request.get("status").asText());
        assertEquals("IN_PROGRESS", request.get("expectedState").asText());
        assertEquals(2048L, request.get("bytesTransferred").asLong());
        assertEquals(6L, request.get("reportSequence").asLong());
    }

    @Test
    @DisplayName("Should report COMPLETED status with bytes transferred")
    void testReportCompleted() throws Exception {
        service().reportCompleted("job-789", 1048576L);

        assertEquals(1, reports().size());
        JsonNode request = lastReport();
        assertEquals("COMPLETED", request.get("status").asText());
        assertEquals(1048576L, request.get("bytesTransferred").asLong());
        assertFalse(request.has("errorMessage"));
    }

    @Test
    @DisplayName("Should report FAILED status with error message")
    void testReportFailed() throws Exception {
        service().reportFailed("job-err", "Connection timeout");

        assertEquals(1, reports().size());
        JsonNode request = lastReport();
        assertEquals("FAILED", request.get("status").asText());
        assertEquals("Connection timeout", request.get("errorMessage").asText());
        assertFalse(request.has("bytesTransferred"));
    }

    @Test
    @DisplayName("Should refuse an attempt-aware FAILED report from an unacknowledged state")
    void testReportFailedRequiresAnAcknowledgedState() {
        assertThrows(IllegalArgumentException.class, () -> service().reportFailed("job", "reason", "attempt",
                1L, 2L, TransferAttemptStatus.OFFERED));
        assertTrue(reports().isEmpty());
    }

    @Test
    @DisplayName("A legacy report is sent once, and an HTTP 500 is unresolved")
    void testHttpErrorGraceful() {
        responseStatus.set(500);

        JobStatusReportingService.StatusReportException failure = assertThrows(
                JobStatusReportingService.StatusReportException.class,
                () -> service().reportCompleted("job-fail", 1000L));

        assertTrue(failure.getMessage().startsWith("Q-REPORT-UNRESOLVED"), failure.getMessage());
        assertEquals(1, reports().size(), "a report without attempt identity is not idempotent, so not retried");
    }

    @Test
    @DisplayName("Should fail on HTTP 404 response")
    void testHttp404Graceful() {
        responseStatus.set(404);

        assertThrows(JobStatusReportingService.StatusReportException.class,
                () -> service().reportFailed("nonexistent-job", "Some error"));
        assertEquals(1, reports().size());
    }

    @Test
    @DisplayName("Should fail on connection error")
    void testConnectionErrorGraceful() {
        controller.close();

        JobStatusReportingService.StatusReportException failure = assertThrows(
                JobStatusReportingService.StatusReportException.class,
                () -> service().reportCompleted("job-conn-err", 500L));

        assertTrue(failure.getMessage().startsWith("Q-REPORT-UNRESOLVED"), failure.getMessage());
    }

    @Test
    @DisplayName("An attempt-aware report is sent at most three times, as an exact replay")
    void testAttemptReportRetriesExactly() {
        responseStatus.set(503);

        assertThrows(JobStatusReportingService.StatusReportException.class,
                () -> service().reportCompleted("job-r", 10L, "attempt-r", 2L, 5L));

        List<FakeController.Request> sends = reports();
        assertEquals(3, sends.size());
        assertEquals(sends.get(0).body(), sends.get(1).body(), "a retry replays the original payload");
        assertEquals(sends.get(0).body(), sends.get(2).body());
    }

    @Test
    @DisplayName("An attempt-aware report stops retrying once acknowledged")
    void testAttemptReportRetriesUntilAcknowledged() throws Exception {
        AtomicInteger sends = new AtomicInteger();
        controller.on("POST", STATUS, request -> Reply.status(sends.incrementAndGet() == 1 ? 429 : 200));

        service().reportAccepted("job-a", "attempt-a", 1L, 1L);

        assertEquals(2, reports().size());
    }

    @Test
    @DisplayName("A rejected attempt-aware report is not retried")
    void testRejectedAttemptReportIsNotRetried() {
        responseStatus.set(409);

        JobStatusReportingService.StatusReportException failure = assertThrows(
                JobStatusReportingService.StatusReportException.class,
                () -> service().reportAccepted("job-x", "attempt-x", 1L, 1L));

        assertTrue(failure.getMessage().startsWith("Q-REPORT-REJECTED"), failure.getMessage());
        assertEquals(1, reports().size());
    }

    @Test
    @DisplayName("Should send multiple status reports for same job")
    void testMultipleReportsForSameJob() throws Exception {
        JobStatusReportingService service = service();

        service.reportAccepted("job-multi");
        service.reportInProgress("job-multi", 500L);
        service.reportInProgress("job-multi", 1000L);
        service.reportCompleted("job-multi", 1500L);

        assertEquals(4, reports().size(), "Should send 4 status reports");
        assertEquals("COMPLETED", lastReport().get("status").asText());
    }

    @Test
    @DisplayName("Should include agent ID in all requests")
    void testAgentIdIncluded() throws Exception {
        JobStatusReportingService service = service();

        service.reportAccepted("job-agent");
        service.reportInProgress("job-agent", 100L);
        service.reportFailed("job-agent", "error");

        reports().forEach(report -> assertEquals("test-agent-status", report.json().get("agentId").asText()));
    }

    @Test
    @DisplayName("After shutdown, reports fail without being sent")
    void testShutdown() {
        JobStatusReportingService service = service();

        service.shutdown();

        JobStatusReportingService.StatusReportException failure = assertThrows(
                JobStatusReportingService.StatusReportException.class, () -> service.reportAccepted("job-closed"));
        assertEquals("Q-REPORT-CLOSED", failure.getMessage());
        assertTrue(reports().isEmpty());
    }

    private JobStatusReportingService service() {
        return new JobStatusReportingService(client, config);
    }

    private List<FakeController.Request> reports() {
        return controller.requests("POST", STATUS);
    }

    private JsonNode lastReport() {
        return reports().getLast().json();
    }
}
