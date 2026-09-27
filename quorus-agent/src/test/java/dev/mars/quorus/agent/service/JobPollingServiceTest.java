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

import dev.mars.quorus.agent.config.AgentConfiguration;
import dev.mars.quorus.agent.testing.FakeController;
import dev.mars.quorus.agent.testing.FakeController.Reply;
import org.junit.jupiter.api.*;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for JobPollingService, against a real HTTP controller stand-in (no mocking).
 *
 * @author Mark Andrew Ray-Smith Cityline Ltd
 * @since 2026-02-05
 * @version 2.0
 */
@Timeout(value = 30, unit = SECONDS, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
class JobPollingServiceTest {

    private static final String JOBS = "/agents/[^/]+/jobs";

    private FakeController controller;
    private ControllerClient client;
    private AgentConfiguration config;
    private final AtomicReference<Reply> reply = new AtomicReference<>(Reply.json(200, "{\"pendingJobs\":[]}"));

    @BeforeEach
    void setUp() throws Exception {
        controller = FakeController.start().on("GET", JOBS, request -> reply.get());
        config = new AgentConfiguration.Builder()
                .securityProfile("development").allowInsecure(true).controllerTlsEnabled(false)
                .agentId("test-agent-poll")
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
    @DisplayName("Should return empty list when no jobs pending")
    void testPollNoJobs() throws Exception {
        List<JobPollingService.PendingJob> jobs = new JobPollingService(client, config).pollForJobs();

        assertNotNull(jobs);
        assertTrue(jobs.isEmpty(), "Should return empty list");
        assertEquals(1, controller.requests("GET", JOBS).size(), "One poll request should be made");
        assertEquals("/agents/test-agent-poll/jobs", controller.requests().getFirst().path());
    }

    @Test
    @DisplayName("Should parse single pending job correctly")
    void testPollSingleJob() throws Exception {
        pendingJobs("""
                {"assignmentId":"assign-001","jobId":"job-001","agentId":"test-agent-poll",
                 "attemptId":"attempt-001","fencingGeneration":7,"leaseExpiresAt":"2026-09-02T04:00:00Z",
                 "lastReportSequence":3,"sourceUri":"https://example.com/file.txt",
                 "destinationPath":"/data/file.txt","totalBytes":1024,"description":"Test transfer"}""");

        List<JobPollingService.PendingJob> pendingJobs = new JobPollingService(client, config).pollForJobs();

        assertEquals(1, pendingJobs.size(), "Should return one job");
        JobPollingService.PendingJob job = pendingJobs.get(0);
        assertEquals("assign-001", job.getAssignmentId());
        assertEquals("job-001", job.getJobId());
        assertEquals("test-agent-poll", job.getAgentId());
        assertEquals("attempt-001", job.getAttemptId());
        assertEquals(7L, job.getFencingGeneration());
        assertEquals("2026-09-02T04:00:00Z", job.getLeaseExpiresAt().toString());
        assertEquals(4L, job.nextReportSequence());
        assertEquals("https://example.com/file.txt", job.getSourceUri());
        assertEquals("/data/file.txt", job.getDestinationPath());
        assertEquals(1024L, job.getTotalBytes());
        assertEquals("Test transfer", job.getDescription());
    }

    @Test
    @DisplayName("Should parse the governed fields of a job, keeping the connection as JSON text")
    void testPollGovernedJob() throws Exception {
        pendingJobs("""
                {"jobId":"job-g","agentId":"test-agent-poll","sourceUri":"sftp://h/in/a.dat",
                 "destinationUri":"file:///spool/a.dat","destinationPath":"/ignored",
                 "tenantId":"bank-a","remotePath":"/in/a.dat","agentPool":"pool-a",
                 "controllerResolvedAddresses":["10.0.0.5","10.0.0.6"],
                 "serviceConnection":{"serviceConnectionId":"payments"},
                 "secretReference":{"secretReferenceId":"key"},
                 "connectionPolicyVersion":3,"connectionPolicyDigest":"sha256:abc"}""");

        JobPollingService.PendingJob job = new JobPollingService(client, config).pollForJobs().getFirst();

        assertEquals("file:///spool/a.dat", job.getDestinationPath(), "destinationUri wins over destinationPath");
        assertEquals("bank-a", job.getTenantId());
        assertEquals("/in/a.dat", job.getRemotePath());
        assertEquals("pool-a", job.getAgentPool());
        assertEquals(List.of("10.0.0.5", "10.0.0.6"), job.getControllerResolvedAddresses());
        assertEquals("{\"serviceConnectionId\":\"payments\"}", job.getServiceConnection());
        assertEquals("{\"secretReferenceId\":\"key\"}", job.getSecretReference());
        assertTrue(job.isGoverned());
        assertEquals(3, job.getConnectionPolicyVersion());
        assertEquals("sha256:abc", job.getConnectionPolicyDigest());
    }

    @Test
    @DisplayName("Should parse multiple pending jobs correctly")
    void testPollMultipleJobs() throws Exception {
        pendingJobs(job("001", 1024), job("002", 2048), job("003", 4096));

        List<JobPollingService.PendingJob> pendingJobs = new JobPollingService(client, config).pollForJobs();

        assertEquals(3, pendingJobs.size(), "Should return three jobs");
        assertEquals("job-001", pendingJobs.get(0).getJobId());
        assertEquals("job-002", pendingJobs.get(1).getJobId());
        assertEquals("job-003", pendingJobs.get(2).getJobId());
    }

    @Test
    @DisplayName("Should handle malformed job JSON gracefully")
    void testPollMalformedJob() throws Exception {
        pendingJobs(job("001", 1), "\"not-a-json-object\"", job("003", 3));

        List<JobPollingService.PendingJob> pendingJobs = new JobPollingService(client, config).pollForJobs();

        assertEquals(2, pendingJobs.size(), "Should return two valid jobs");
        assertEquals("job-001", pendingJobs.get(0).getJobId());
        assertEquals("job-003", pendingJobs.get(1).getJobId());
    }

    @Test
    @DisplayName("Should return empty list on HTTP error")
    void testPollHttpError() throws Exception {
        reply.set(Reply.status(500));

        List<JobPollingService.PendingJob> jobs = new JobPollingService(client, config).pollForJobs();

        assertNotNull(jobs);
        assertTrue(jobs.isEmpty(), "Should return empty list on error");
        assertEquals(1, controller.requests("GET", JOBS).size(), "Request should still be made");
    }

    @Test
    @DisplayName("Should return empty list on HTTP 404")
    void testPollNotFound() throws Exception {
        reply.set(Reply.status(404));

        assertTrue(new JobPollingService(client, config).pollForJobs().isEmpty(), "Should return empty list on 404");
    }

    @Test
    @DisplayName("Should return empty list on connection error")
    void testPollConnectionError() throws Exception {
        controller.close();

        List<JobPollingService.PendingJob> jobs = new JobPollingService(client, config).pollForJobs();

        assertNotNull(jobs);
        assertTrue(jobs.isEmpty(), "Should return empty list on connection error");
    }

    @Test
    @DisplayName("Should return empty list on a body that is not JSON")
    void testPollUnreadableBody() throws Exception {
        reply.set(Reply.json(200, "not json"));

        assertTrue(new JobPollingService(client, config).pollForJobs().isEmpty());
    }

    @Test
    @DisplayName("Should handle null pendingJobs array")
    void testPollNullArray() throws Exception {
        reply.set(Reply.json(200, "{}"));

        assertTrue(new JobPollingService(client, config).pollForJobs().isEmpty(),
                "Should return empty list when array is null");
    }

    @Test
    @DisplayName("Should handle job with missing optional fields")
    void testPollJobWithMissingOptionalFields() throws Exception {
        pendingJobs("""
                {"assignmentId":"assign-001","jobId":"job-001","agentId":"test-agent-poll",
                 "sourceUri":"https://example.com/file.txt","destinationPath":"/data/file.txt"}""");

        List<JobPollingService.PendingJob> pendingJobs = new JobPollingService(client, config).pollForJobs();

        assertEquals(1, pendingJobs.size());
        JobPollingService.PendingJob job = pendingJobs.get(0);
        assertEquals(0L, job.getTotalBytes(), "Missing totalBytes should default to 0");
        assertNull(job.getDescription(), "Missing description should be null");
        assertFalse(job.hasAttemptContext());
        assertFalse(job.isGoverned());
    }

    @Test
    void governedRequestUsesOnlyTheAgentAuthorizedRemoteEndpoint() throws Exception {
        Path uploadRoot = Files.createTempDirectory("quorus-upload-root");
        Path downloadRoot = Files.createTempDirectory("quorus-download-root");
        Path localDestination = downloadRoot.resolve("received.dat");
        JobPollingService.PendingJob job = new JobPollingService.PendingJob(
                "assignment-1", "job-1", "agent-1",
                "https://attacker.invalid/stolen.dat", localDestination.toUri().toString(),
                10, "governed");
        AgentLocalPathPolicy localPathPolicy = new AgentLocalPathPolicy(uploadRoot, downloadRoot);

        var request = job.toAuthorizedTransferRequest(
                URI.create("https://approved.example.com/approved/statement.dat"), null, localPathPolicy);

        assertEquals(URI.create("https://approved.example.com/approved/statement.dat"), request.getSourceUri());
        assertEquals(localDestination.toUri(), request.getDestinationUri());
    }

    private void pendingJobs(String... jobs) {
        reply.set(Reply.json(200, "{\"pendingJobs\":[" + String.join(",", jobs) + "]}"));
    }

    private static String job(String number, long totalBytes) {
        return "{\"assignmentId\":\"assign-" + number + "\",\"jobId\":\"job-" + number
                + "\",\"agentId\":\"test-agent-poll\",\"sourceUri\":\"https://example.com/file" + number
                + ".txt\",\"destinationPath\":\"/data/file" + number + ".txt\",\"totalBytes\":" + totalBytes + "}";
    }
}
