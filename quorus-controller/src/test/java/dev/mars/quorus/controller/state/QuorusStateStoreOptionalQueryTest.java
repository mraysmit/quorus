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

package dev.mars.quorus.controller.state;

import dev.mars.quorus.agent.AgentInfo;
import dev.mars.quorus.core.JobAssignment;
import dev.mars.quorus.core.JobPriority;
import dev.mars.quorus.core.QueuedJob;
import dev.mars.quorus.core.RouteConfiguration;
import dev.mars.quorus.core.RouteStatus;
import dev.mars.quorus.core.TransferJob;
import dev.mars.quorus.core.TransferRequest;
import dev.mars.quorus.core.TriggerConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the Optional-returning query methods on QuorusStateStore.
 * Each findXxx() method must return Optional.empty() when the entity does not exist,
 * and a present Optional wrapping the entity when it does.
 */
class QuorusStateStoreOptionalQueryTest {

    private QuorusStateStore stateStore;

    @BeforeEach
    void setUp() {
        stateStore = new QuorusStateStore();
    }

    // ── findTransferJob ────────────────────────────────────────────────────

    @Test
    void findTransferJob_returnsEmpty_whenNotFound() {
        Optional<TransferJobSnapshot> result = stateStore.findTransferJob("no-such-job");
        assertTrue(result.isEmpty());
    }

    @Test
    void findTransferJob_returnsPresent_whenExists() {
        TransferJob job = createTransferJob();
        stateStore.apply(TransferJobCommand.create(job));

        Optional<TransferJobSnapshot> result = stateStore.findTransferJob(job.getJobId());

        assertTrue(result.isPresent());
        assertEquals(job.getJobId(), result.get().getJobId());
    }

    @Test
    void findTransferJob_returnsEmpty_afterDeletion() {
        TransferJob job = createTransferJob();
        stateStore.apply(TransferJobCommand.create(job));
        stateStore.apply(TransferJobCommand.delete(job.getJobId()));

        assertTrue(stateStore.findTransferJob(job.getJobId()).isEmpty());
    }

    // ── findAgent ──────────────────────────────────────────────────────────

    @Test
    void findAgent_returnsEmpty_whenNotFound() {
        assertTrue(stateStore.findAgent("no-such-agent").isEmpty());
    }

    @Test
    void findAgent_returnsPresent_whenExists() {
        AgentInfo agent = new AgentInfo("agent-01", "host1.example.com", "10.0.0.1", 8080);
        stateStore.apply(AgentCommand.register(agent));

        Optional<AgentInfo> result = stateStore.findAgent("agent-01");

        assertTrue(result.isPresent());
        assertEquals("agent-01", result.get().getAgentId());
    }

    @Test
    void findAgent_returnsEmpty_afterDeregistration() {
        AgentInfo agent = new AgentInfo("agent-02", "host2.example.com", "10.0.0.2", 8080);
        stateStore.apply(AgentCommand.register(agent));
        stateStore.apply(AgentCommand.deregister("agent-02"));

        assertTrue(stateStore.findAgent("agent-02").isEmpty());
    }

    // ── findJobAssignment ──────────────────────────────────────────────────

    @Test
    void findJobAssignment_returnsEmpty_whenNotFound() {
        assertTrue(stateStore.findJobAssignment("no-such-assignment").isEmpty());
    }

    @Test
    void findJobAssignment_returnsPresent_whenExists() {
        TransferJob job = createTransferJob();
        stateStore.apply(TransferJobCommand.create(job));

        JobAssignment assignment = new JobAssignment.Builder()
                .jobId(job.getJobId())
                .agentId("agent-001")
                .build();
        stateStore.apply(JobAssignmentCommand.assign(assignment));

        String assignmentId = job.getJobId() + ":agent-001";
        Optional<JobAssignment> result = stateStore.findJobAssignment(assignmentId);

        assertTrue(result.isPresent());
        assertEquals("agent-001", result.get().getAgentId());
    }

    // ── findRoute ──────────────────────────────────────────────────────────

    @Test
    void findRoute_returnsEmpty_whenNotFound() {
        assertTrue(stateStore.findRoute("no-such-route").isEmpty());
    }

    @Test
    void findRoute_returnsPresent_whenExists() {
        RouteConfiguration route = createRoute("route-find-01");
        stateStore.apply(RouteCommand.create(route));

        Optional<RouteConfiguration> result = stateStore.findRoute("route-find-01");

        assertTrue(result.isPresent());
        assertEquals("route-find-01", result.get().getRouteId());
    }

    @Test
    void findRoute_returnsEmpty_afterDeletion() {
        RouteConfiguration route = createRoute("route-find-02");
        stateStore.apply(RouteCommand.create(route));
        stateStore.apply(RouteCommand.delete("route-find-02"));

        assertTrue(stateStore.findRoute("route-find-02").isEmpty());
    }

    // ── findQueuedJob ──────────────────────────────────────────────────────

    @Test
    void findQueuedJob_returnsEmpty_whenNotFound() {
        assertTrue(stateStore.findQueuedJob("no-such-queued").isEmpty());
    }

    @Test
    void findQueuedJob_returnsPresent_whenExists() {
        TransferJob job = createTransferJob();
        QueuedJob queuedJob = new QueuedJob.Builder()
                .transferJob(job)
                .priority(JobPriority.HIGH)
                .submittedBy("test-user")
                .build();
        stateStore.apply(JobQueueCommand.enqueue(queuedJob));

        Optional<QueuedJob> result = stateStore.findQueuedJob(job.getJobId());

        assertTrue(result.isPresent());
        assertEquals(JobPriority.HIGH, result.get().getPriority());
    }

    // ── findMetadata ───────────────────────────────────────────────────────

    @Test
    void findMetadata_returnsEmpty_whenNotFound() {
        assertTrue(stateStore.findMetadata("no-such-key").isEmpty());
    }

    @Test
    void findMetadata_returnsPresent_whenExists() {
        stateStore.apply(SystemMetadataCommand.set("env", "production"));

        Optional<String> result = stateStore.findMetadata("env");

        assertTrue(result.isPresent());
        assertEquals("production", result.get());
    }

    @Test
    void findMetadata_returnsPresent_forDefaultMetadata() {
        // "version" is set during initialization
        Optional<String> result = stateStore.findMetadata("version");

        assertTrue(result.isPresent());
        assertEquals("2.0", result.get());
    }

    @Test
    void findMetadata_returnsEmpty_afterDeletion() {
        stateStore.apply(SystemMetadataCommand.set("temp-key", "temp-value"));
        stateStore.apply(SystemMetadataCommand.delete("temp-key"));

        assertTrue(stateStore.findMetadata("temp-key").isEmpty());
    }

    // ── Consistency with nullable getters ──────────────────────────────────

    @Test
    void findMethods_areConsistentWithNullableGetters() {
        // When null getter returns null, find returns empty
        assertNull(stateStore.getTransferJob("x"));
        assertTrue(stateStore.findTransferJob("x").isEmpty());

        assertNull(stateStore.getAgent("x"));
        assertTrue(stateStore.findAgent("x").isEmpty());

        assertNull(stateStore.getJobAssignment("x"));
        assertTrue(stateStore.findJobAssignment("x").isEmpty());

        assertNull(stateStore.getRoute("x"));
        assertTrue(stateStore.findRoute("x").isEmpty());

        assertNull(stateStore.getQueuedJob("x"));
        assertTrue(stateStore.findQueuedJob("x").isEmpty());

        assertNull(stateStore.getMetadata("x"));
        assertTrue(stateStore.findMetadata("x").isEmpty());

        // When nullable getter returns a value, find returns that value
        String version = stateStore.getMetadata("version");
        assertNotNull(version);
        assertEquals(version, stateStore.findMetadata("version").orElseThrow());
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    private TransferJob createTransferJob() {
        TransferRequest request = TransferRequest.builder()
                .sourceUri(URI.create("https://example.com/data.csv"))
                .destinationPath(Paths.get("/tmp/data.csv"))
                .build();
        return new TransferJob(request);
    }

    private RouteConfiguration createRoute(String routeId) {
        return new RouteConfiguration(
                routeId,
                "test-route",
                "Test route description",
                "agent-src",
                "/data/source/",
                "agent-dst",
                "/data/dest/",
                TriggerConfiguration.interval(60),
                RouteStatus.CONFIGURED,
                Map.of(),
                Instant.now(),
                Instant.now()
        );
    }
}
