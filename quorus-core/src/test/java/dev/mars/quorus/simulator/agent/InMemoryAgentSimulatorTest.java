/*
 * Copyright (c) 2025 Cityline Ltd.
 * All rights reserved.
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

package dev.mars.quorus.simulator.agent;

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;
import static org.awaitility.Awaitility.*;
import static java.util.concurrent.TimeUnit.*;

/**
 * Unit tests for {@link InMemoryAgentSimulator}.
 */
@DisplayName("InMemoryAgentSimulator Tests")
class InMemoryAgentSimulatorTest {

    private InMemoryAgentSimulator agent;
    private MockControllerConnection controller;

    @BeforeEach
    void setUp() {
        controller = new MockControllerConnection();
        agent = new InMemoryAgentSimulator("test-agent")
            .withHostname("test-host")
            .withRegion("us-east-1")
            .withDatacenter("dc-1")
            .withCapabilities(new InMemoryAgentSimulator.AgentCapabilities()
                .maxConcurrentTransfers(3)
                .supportedProtocols(Set.of("sftp", "http")))
            .connectToController(controller);
        
        // Fast execution for tests
        agent.setJobExecutionDelayMs(100);
        agent.setHeartbeatIntervalMs(200);
        agent.setPollingIntervalMs(100);
    }

    @AfterEach
    void tearDown() {
        if (agent != null) {
            agent.shutdown();
        }
    }

    // ==================== Lifecycle Tests ====================

    @Nested
    @DisplayName("Lifecycle")
    class LifecycleTests {

        @Test
        @DisplayName("Should start agent successfully")
        void testStartAgent() throws Exception {
            agent.start();

            assertThat(agent.getState()).isEqualTo(InMemoryAgentSimulator.AgentState.ACTIVE);
            assertThat(agent.getStartTime()).isNotNull();
            assertThat(controller.isRegistered("test-agent")).isTrue();
        }

        @Test
        @DisplayName("Should throw if started twice")
        void testDoubleStart() throws Exception {
            agent.start();

            assertThatThrownBy(() -> agent.start())
                .isInstanceOf(InMemoryAgentSimulator.AgentException.class)
                .hasMessageContaining("already running");
        }

        @Test
        @DisplayName("Should throw if no controller connection")
        void testStartWithoutController() {
            var agentNoController = new InMemoryAgentSimulator("no-controller");

            assertThatThrownBy(() -> agentNoController.start())
                .isInstanceOf(InMemoryAgentSimulator.AgentException.class)
                .hasMessageContaining("No controller connection");
        }

        @Test
        @DisplayName("Should stop agent gracefully")
        void testStopAgent() throws Exception {
            agent.start();
            agent.stop();

            assertThat(agent.getState()).isEqualTo(InMemoryAgentSimulator.AgentState.STOPPED);
            assertThat(controller.isRegistered("test-agent")).isFalse();
        }

        @Test
        @DisplayName("Should crash agent")
        void testCrashAgent() throws Exception {
            agent.start();
            agent.crash();

            assertThat(agent.getState()).isEqualTo(InMemoryAgentSimulator.AgentState.CRASHED);
        }

        @Test
        @DisplayName("Should recover from crash")
        void testRecoverFromCrash() throws Exception {
            agent.start();
            agent.crash();
            agent.recover();

            assertThat(agent.getState()).isEqualTo(InMemoryAgentSimulator.AgentState.ACTIVE);
        }

        @Test
        @DisplayName("Should not recover if not crashed")
        void testCannotRecoverIfNotCrashed() throws Exception {
            agent.start();

            assertThatThrownBy(() -> agent.recover())
                .isInstanceOf(InMemoryAgentSimulator.AgentException.class)
                .hasMessageContaining("not crashed");
        }
    }

    // ==================== Builder Tests ====================

    @Nested
    @DisplayName("Builder Methods")
    class BuilderTests {

        @Test
        @DisplayName("Should set hostname")
        void testWithHostname() {
            var customAgent = new InMemoryAgentSimulator("custom")
                .withHostname("custom-host");
            // Just verify no exception
        }

        @Test
        @DisplayName("Should set region")
        void testWithRegion() throws Exception {
            agent.start();
            assertThat(agent.getRegion()).isEqualTo("us-east-1");
        }

        @Test
        @DisplayName("Should set datacenter")
        void testWithDatacenter() throws Exception {
            agent.start();
            assertThat(agent.getDatacenter()).isEqualTo("dc-1");
        }

        @Test
        @DisplayName("Should set capabilities")
        void testWithCapabilities() {
            assertThat(agent.getCapabilities().maxConcurrentTransfers()).isEqualTo(3);
            assertThat(agent.getCapabilities().supportedProtocols())
                .containsExactlyInAnyOrder("sftp", "http");
        }

        @Test
        @DisplayName("Should add tags")
        void testWithTag() throws Exception {
            agent.withTag("env", "test")
                 .withTag("tier", "gold");
            agent.start();
            // Verify tags were passed to registration
            assertThat(controller.getLastRegistration().tags())
                .containsEntry("env", "test")
                .containsEntry("tier", "gold");
        }
    }

    // ==================== Job Execution Tests ====================

    @Nested
    @DisplayName("Job Execution")
    class JobExecutionTests {

        @Test
        @DisplayName("Should accept and execute job")
        void testExecuteJob() throws Exception {
            agent.start();

            var assignment = new InMemoryAgentSimulator.JobAssignment(
                "job-1",
                "sftp://host/source.txt",
                "/dest/file.txt",
                "sftp",
                10000,
                Map.of()
            );

            agent.assignJob(assignment);

            await().atMost(5, SECONDS).until(() -> 
                agent.getTotalJobsCompleted() == 1);

            assertThat(agent.getCompletedJobs()).hasSize(1);
            assertThat(agent.getCompletedJobs().get(0).status())
                .isEqualTo(InMemoryAgentSimulator.JobStatus.COMPLETED);
        }

        @Test
        @DisplayName("Should track active jobs")
        void testTrackActiveJobs() throws Exception {
            agent.setJobExecutionDelayMs(2000); // Long execution
            agent.start();

            var assignment = new InMemoryAgentSimulator.JobAssignment(
                "job-1", "sftp://host/file.txt", "/dest/file.txt", "sftp", 10000, Map.of()
            );

            agent.assignJob(assignment);
            Thread.sleep(100); // Let job start

            assertThat(agent.getActiveJobCount()).isEqualTo(1);
            assertThat(agent.getActiveJobs()).containsKey("job-1");
        }

        @Test
        @DisplayName("Should reject job when at capacity")
        void testRejectAtCapacity() throws Exception {
            agent.setJobExecutionDelayMs(5000);
            agent.start();

            // Fill capacity (3 jobs)
            for (int i = 0; i < 3; i++) {
                agent.assignJob(new InMemoryAgentSimulator.JobAssignment(
                    "job-" + i, "sftp://host/file.txt", "/dest/file.txt", "sftp", 10000, Map.of()
                ));
            }

            Thread.sleep(100);

            // Fourth job should be rejected
            List<InMemoryAgentSimulator.AgentEvent> events = new CopyOnWriteArrayList<>();
            agent.withEventCallback(events::add);

            agent.assignJob(new InMemoryAgentSimulator.JobAssignment(
                "job-extra", "sftp://host/file.txt", "/dest/file.txt", "sftp", 10000, Map.of()
            ));

            await().atMost(1, SECONDS).until(() -> 
                events.stream().anyMatch(e -> 
                    e.type() == InMemoryAgentSimulator.AgentEvent.EventType.JOB_REJECTED));
        }

        @Test
        @DisplayName("Should filter jobs")
        void testJobFilter() throws Exception {
            agent.withJobAcceptanceFilter(job -> job.protocol().equals("sftp"));
            agent.start();

            List<InMemoryAgentSimulator.AgentEvent> events = new CopyOnWriteArrayList<>();
            agent.withEventCallback(events::add);

            // HTTP job should be filtered
            agent.assignJob(new InMemoryAgentSimulator.JobAssignment(
                "http-job", "http://host/file.txt", "/dest/file.txt", "http", 10000, Map.of()
            ));

            await().atMost(1, SECONDS).until(() -> 
                events.stream().anyMatch(e -> 
                    e.type() == InMemoryAgentSimulator.AgentEvent.EventType.JOB_REJECTED));
        }
    }

    // ==================== Heartbeat Tests ====================

    @Nested
    @DisplayName("Heartbeat")
    class HeartbeatTests {

        @Test
        @DisplayName("Should send heartbeats")
        void testSendHeartbeats() throws Exception {
            agent.setHeartbeatIntervalMs(100);
            agent.start();

            await().atMost(2, SECONDS).until(() -> 
                controller.getHeartbeatCount() >= 3);

            assertThat(agent.getLastHeartbeat()).isNotNull();
        }

        @Test
        @DisplayName("Should include status in heartbeat")
        void testHeartbeatContent() throws Exception {
            agent.setHeartbeatIntervalMs(50);
            agent.start();

            await().atMost(1, SECONDS).until(() -> 
                controller.getLastHeartbeat() != null);

            var heartbeat = controller.getLastHeartbeat();
            assertThat(heartbeat.agentId()).isEqualTo("test-agent");
            assertThat(heartbeat.state()).isEqualTo(InMemoryAgentSimulator.AgentState.ACTIVE);
        }
    }

    // ==================== Event Callback Tests ====================

    @Nested
    @DisplayName("Event Callbacks")
    class EventCallbackTests {

        @Test
        @DisplayName("Should fire state change events")
        void testStateChangeEvents() throws Exception {
            List<InMemoryAgentSimulator.AgentEvent> events = new CopyOnWriteArrayList<>();
            agent.withEventCallback(events::add);

            agent.start();

            await().atMost(1, SECONDS).until(() -> 
                events.stream().anyMatch(e -> 
                    e.type() == InMemoryAgentSimulator.AgentEvent.EventType.STATE_CHANGED));

            assertThat(events).extracting(InMemoryAgentSimulator.AgentEvent::type)
                .contains(InMemoryAgentSimulator.AgentEvent.EventType.STATE_CHANGED);
        }

        @Test
        @DisplayName("Should fire job events")
        void testJobEvents() throws Exception {
            List<InMemoryAgentSimulator.AgentEvent> events = new CopyOnWriteArrayList<>();
            agent.withEventCallback(events::add);
            agent.start();

            agent.assignJob(new InMemoryAgentSimulator.JobAssignment(
                "job-1", "sftp://host/file.txt", "/dest/file.txt", "sftp", 10000, Map.of()
            ));

            await().atMost(5, SECONDS).until(() -> 
                events.stream().anyMatch(e -> 
                    e.type() == InMemoryAgentSimulator.AgentEvent.EventType.JOB_COMPLETED));

            assertThat(events).extracting(InMemoryAgentSimulator.AgentEvent::type)
                .contains(
                    InMemoryAgentSimulator.AgentEvent.EventType.JOB_ACCEPTED,
                    InMemoryAgentSimulator.AgentEvent.EventType.JOB_STARTED,
                    InMemoryAgentSimulator.AgentEvent.EventType.JOB_COMPLETED
                );
        }

        @Test
        @DisplayName("Should fire crash event")
        void testCrashEvent() throws Exception {
            List<InMemoryAgentSimulator.AgentEvent> events = new CopyOnWriteArrayList<>();
            agent.withEventCallback(events::add);
            agent.start();

            agent.crash();

            assertThat(events).extracting(InMemoryAgentSimulator.AgentEvent::type)
                .contains(InMemoryAgentSimulator.AgentEvent.EventType.CRASHED);
        }
    }

    // ==================== Chaos Engineering Tests ====================

    @Nested
    @DisplayName("Chaos Engineering")
    class ChaosEngineeringTests {

        @ParameterizedTest
        @EnumSource(InMemoryAgentSimulator.AgentFailureMode.class)
        @DisplayName("Should support all failure modes")
        void testAllFailureModes(InMemoryAgentSimulator.AgentFailureMode mode) {
            agent.setFailureMode(mode);
            // Just verify setting doesn't throw
        }

        @Test
        @DisplayName("Should fail registration")
        void testRegistrationFailure() {
            agent.setFailureMode(InMemoryAgentSimulator.AgentFailureMode.REGISTRATION_FAILURE);

            assertThatThrownBy(() -> agent.start())
                .isInstanceOf(InMemoryAgentSimulator.AgentException.class)
                .hasMessageContaining("Registration failed");
        }

        @Test
        @DisplayName("Should stop heartbeats in timeout mode")
        void testHeartbeatTimeout() throws Exception {
            agent.start();
            int initialHeartbeats = controller.getHeartbeatCount();

            agent.setFailureMode(InMemoryAgentSimulator.AgentFailureMode.HEARTBEAT_TIMEOUT);

            Thread.sleep(500);

            // Heartbeats should have stopped
            int finalHeartbeats = controller.getHeartbeatCount();
            assertThat(finalHeartbeats - initialHeartbeats).isLessThanOrEqualTo(1);
        }

        @Test
        @DisplayName("Should reject jobs in rejection mode")
        void testJobRejection() throws Exception {
            agent.start();
            agent.setFailureMode(InMemoryAgentSimulator.AgentFailureMode.JOB_REJECTION);

            List<InMemoryAgentSimulator.AgentEvent> events = new CopyOnWriteArrayList<>();
            agent.withEventCallback(events::add);

            agent.assignJob(new InMemoryAgentSimulator.JobAssignment(
                "job-1", "sftp://host/file.txt", "/dest/file.txt", "sftp", 10000, Map.of()
            ));

            await().atMost(1, SECONDS).until(() -> 
                events.stream().anyMatch(e -> 
                    e.type() == InMemoryAgentSimulator.AgentEvent.EventType.JOB_REJECTED));
        }

        @Test
        @DisplayName("Should fail all jobs in failure mode")
        void testJobFailure() throws Exception {
            agent.setFailureMode(InMemoryAgentSimulator.AgentFailureMode.JOB_FAILURE);
            agent.start();

            agent.assignJob(new InMemoryAgentSimulator.JobAssignment(
                "job-1", "sftp://host/file.txt", "/dest/file.txt", "sftp", 10000, Map.of()
            ));

            await().atMost(5, SECONDS).until(() -> 
                agent.getTotalJobsFailed() == 1);

            assertThat(agent.getCompletedJobs().get(0).status())
                .isEqualTo(InMemoryAgentSimulator.JobStatus.FAILED);
        }

        @Test
        @DisplayName("Should execute slowly in slow mode")
        void testSlowExecution() throws Exception {
            agent.setJobExecutionDelayMs(100);
            agent.setFailureMode(InMemoryAgentSimulator.AgentFailureMode.SLOW_EXECUTION);
            agent.start();

            long start = System.currentTimeMillis();
            agent.assignJob(new InMemoryAgentSimulator.JobAssignment(
                "job-1", "sftp://host/file.txt", "/dest/file.txt", "sftp", 10000, Map.of()
            ));

            await().atMost(15, SECONDS).until(() -> 
                agent.getTotalJobsCompleted() == 1);

            long elapsed = System.currentTimeMillis() - start;
            assertThat(elapsed).isGreaterThan(1000); // 10x slower
        }

        @Test
        @DisplayName("Should simulate network partition")
        void testNetworkPartition() throws Exception {
            agent.start();
            int initialHeartbeats = controller.getHeartbeatCount();

            agent.setFailureMode(InMemoryAgentSimulator.AgentFailureMode.NETWORK_PARTITION);

            assertThat(agent.getState()).isEqualTo(InMemoryAgentSimulator.AgentState.PARTITIONED);
        }

        @Test
        @DisplayName("Should fail jobs randomly")
        void testRandomJobFailure() throws Exception {
            agent.setJobFailureRate(1.0); // 100% failure
            agent.start();

            agent.assignJob(new InMemoryAgentSimulator.JobAssignment(
                "job-1", "sftp://host/file.txt", "/dest/file.txt", "sftp", 10000, Map.of()
            ));

            await().atMost(5, SECONDS).until(() -> 
                agent.getTotalJobsFailed() == 1);
        }

        @Test
        @DisplayName("Should reset chaos settings")
        void testResetChaos() throws Exception {
            agent.setFailureMode(InMemoryAgentSimulator.AgentFailureMode.JOB_FAILURE);
            agent.setJobFailureRate(1.0);
            agent.reset();
            agent.start();

            agent.assignJob(new InMemoryAgentSimulator.JobAssignment(
                "job-1", "sftp://host/file.txt", "/dest/file.txt", "sftp", 10000, Map.of()
            ));

            await().atMost(5, SECONDS).until(() -> 
                agent.getTotalJobsCompleted() == 1);
        }
    }

    // ==================== Statistics Tests ====================

    @Nested
    @DisplayName("Statistics")
    class StatisticsTests {

        @Test
        @DisplayName("Should track total jobs received")
        void testTotalJobsReceived() throws Exception {
            agent.start();

            agent.assignJob(new InMemoryAgentSimulator.JobAssignment(
                "job-1", "sftp://host/file.txt", "/dest/file.txt", "sftp", 10000, Map.of()
            ));
            agent.assignJob(new InMemoryAgentSimulator.JobAssignment(
                "job-2", "sftp://host/file.txt", "/dest/file.txt", "sftp", 10000, Map.of()
            ));

            assertThat(agent.getTotalJobsReceived()).isEqualTo(2);
        }

        @Test
        @DisplayName("Should track total jobs completed")
        void testTotalJobsCompleted() throws Exception {
            agent.start();

            agent.assignJob(new InMemoryAgentSimulator.JobAssignment(
                "job-1", "sftp://host/file.txt", "/dest/file.txt", "sftp", 10000, Map.of()
            ));

            await().atMost(5, SECONDS).until(() -> 
                agent.getTotalJobsCompleted() == 1);
        }

        @Test
        @DisplayName("Should track total bytes transferred")
        void testTotalBytesTransferred() throws Exception {
            agent.start();

            agent.assignJob(new InMemoryAgentSimulator.JobAssignment(
                "job-1", "sftp://host/file.txt", "/dest/file.txt", "sftp", 5000, Map.of()
            ));

            await().atMost(5, SECONDS).until(() -> 
                agent.getTotalJobsCompleted() == 1);

            assertThat(agent.getTotalBytesTransferred()).isEqualTo(5000);
        }

        @Test
        @DisplayName("Should reset statistics")
        void testResetStatistics() throws Exception {
            agent.start();

            agent.assignJob(new InMemoryAgentSimulator.JobAssignment(
                "job-1", "sftp://host/file.txt", "/dest/file.txt", "sftp", 10000, Map.of()
            ));

            await().atMost(5, SECONDS).until(() -> 
                agent.getTotalJobsCompleted() == 1);

            agent.resetStatistics();

            assertThat(agent.getTotalJobsReceived()).isZero();
            assertThat(agent.getTotalJobsCompleted()).isZero();
            assertThat(agent.getTotalBytesTransferred()).isZero();
            assertThat(agent.getCompletedJobs()).isEmpty();
        }
    }

    // ==================== Capabilities Tests ====================

    @Nested
    @DisplayName("Agent Capabilities")
    class CapabilitiesTests {

        @Test
        @DisplayName("Should build capabilities with supported protocols")
        void testSupportedProtocols() {
            var caps = new InMemoryAgentSimulator.AgentCapabilities()
                .supportedProtocols(Set.of("sftp", "http", "smb"));

            assertThat(caps.supportedProtocols())
                .containsExactlyInAnyOrder("sftp", "http", "smb");
        }

        @Test
        @DisplayName("Should build capabilities with max concurrent transfers")
        void testMaxConcurrentTransfers() {
            var caps = new InMemoryAgentSimulator.AgentCapabilities()
                .maxConcurrentTransfers(10);

            assertThat(caps.maxConcurrentTransfers()).isEqualTo(10);
        }

        @Test
        @DisplayName("Should build capabilities with max transfer size")
        void testMaxTransferSize() {
            var caps = new InMemoryAgentSimulator.AgentCapabilities()
                .maxTransferSize(5_000_000_000L);

            assertThat(caps.maxTransferSize()).isEqualTo(5_000_000_000L);
        }
    }

    // ==================== Mock Controller ====================

    /**
     * Mock controller connection for testing.
     */
    private static class MockControllerConnection implements InMemoryAgentSimulator.ControllerConnection {
        private final Map<String, InMemoryAgentSimulator.AgentRegistration> registrations = 
            new ConcurrentHashMap<>();
        private final List<InMemoryAgentSimulator.HeartbeatInfo> heartbeats = 
            Collections.synchronizedList(new ArrayList<>());
        private final List<InMemoryAgentSimulator.JobStatusUpdate> statusUpdates = 
            Collections.synchronizedList(new ArrayList<>());
        private volatile InMemoryAgentSimulator.AgentRegistration lastRegistration;
        private volatile InMemoryAgentSimulator.HeartbeatInfo lastHeartbeat;

        @Override
        public void registerAgent(InMemoryAgentSimulator.AgentRegistration registration) {
            registrations.put(registration.agentId(), registration);
            lastRegistration = registration;
        }

        @Override
        public void unregisterAgent(String agentId) {
            registrations.remove(agentId);
        }

        @Override
        public void sendHeartbeat(InMemoryAgentSimulator.HeartbeatInfo heartbeat) {
            heartbeats.add(heartbeat);
            lastHeartbeat = heartbeat;
        }

        @Override
        public List<InMemoryAgentSimulator.JobAssignment> pollPendingJobs(String agentId) {
            return List.of(); // No pending jobs by default
        }

        @Override
        public void reportJobStatus(InMemoryAgentSimulator.JobStatusUpdate update) {
            statusUpdates.add(update);
        }

        boolean isRegistered(String agentId) {
            return registrations.containsKey(agentId);
        }

        InMemoryAgentSimulator.AgentRegistration getLastRegistration() {
            return lastRegistration;
        }

        InMemoryAgentSimulator.HeartbeatInfo getLastHeartbeat() {
            return lastHeartbeat;
        }

        int getHeartbeatCount() {
            return heartbeats.size();
        }
    }
}
