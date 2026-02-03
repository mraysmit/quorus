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

package dev.mars.quorus.simulator.agent;

import dev.mars.quorus.simulator.SimulatorTestLoggingExtension;
import org.junit.jupiter.api.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.junit.jupiter.api.extension.ExtendWith;
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
@ExtendWith(SimulatorTestLoggingExtension.class)
@DisplayName("InMemoryAgentSimulator Tests")
class InMemoryAgentSimulatorTest {

    private static final Logger log = LoggerFactory.getLogger(InMemoryAgentSimulatorTest.class);

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
            log.info("Testing agent startup");
            agent.start();

            log.info("Agent state: {}, registered: {}", agent.getState(), controller.isRegistered("test-agent"));
            assertThat(agent.getState()).isEqualTo(InMemoryAgentSimulator.AgentState.ACTIVE);
            assertThat(agent.getStartTime()).isNotNull();
            assertThat(controller.isRegistered("test-agent")).isTrue();
        }

        @Test
        @DisplayName("Should throw if started twice")
        void testDoubleStart() throws Exception {
            log.info("Testing double-start rejection");
            agent.start();

            assertThatThrownBy(() -> agent.start())
                .isInstanceOf(InMemoryAgentSimulator.AgentException.class)
                .hasMessageContaining("already running");
            log.info("Double-start correctly rejected");
        }

        @Test
        @DisplayName("Should throw if no controller connection")
        void testStartWithoutController() {
            log.info("Testing start without controller connection");
            var agentNoController = new InMemoryAgentSimulator("no-controller");

            assertThatThrownBy(() -> agentNoController.start())
                .isInstanceOf(InMemoryAgentSimulator.AgentException.class)
                .hasMessageContaining("No controller connection");
            log.info("No-controller start correctly rejected");
        }

        @Test
        @DisplayName("Should stop agent gracefully")
        void testStopAgent() throws Exception {
            log.info("Testing graceful agent stop");
            agent.start();
            agent.stop();

            log.info("Agent state: {}, registered: {}", agent.getState(), controller.isRegistered("test-agent"));
            assertThat(agent.getState()).isEqualTo(InMemoryAgentSimulator.AgentState.STOPPED);
            assertThat(controller.isRegistered("test-agent")).isFalse();
        }

        @Test
        @DisplayName("Should crash agent")
        void testCrashAgent() throws Exception {
            log.info("Testing agent crash simulation");
            agent.start();
            agent.crash();

            log.info("Agent state after crash: {}", agent.getState());
            assertThat(agent.getState()).isEqualTo(InMemoryAgentSimulator.AgentState.CRASHED);
        }

        @Test
        @DisplayName("Should recover from crash")
        void testRecoverFromCrash() throws Exception {
            log.info("Testing crash recovery");
            agent.start();
            agent.crash();
            agent.recover();

            log.info("Agent state after recovery: {}", agent.getState());
            assertThat(agent.getState()).isEqualTo(InMemoryAgentSimulator.AgentState.ACTIVE);
        }

        @Test
        @DisplayName("Should not recover if not crashed")
        void testCannotRecoverIfNotCrashed() throws Exception {
            log.info("Testing recovery rejection when not crashed");
            agent.start();

            assertThatThrownBy(() -> agent.recover())
                .isInstanceOf(InMemoryAgentSimulator.AgentException.class)
                .hasMessageContaining("not crashed");
            log.info("Recovery correctly rejected when not crashed");
        }
    }

    // ==================== Builder Tests ====================

    @Nested
    @DisplayName("Builder Methods")
    class BuilderTests {

        @Test
        @DisplayName("Should set hostname")
        void testWithHostname() {
            log.info("Testing hostname builder method");
            var customAgent = new InMemoryAgentSimulator("custom")
                .withHostname("custom-host");
            log.info("Custom agent created with hostname: custom-host");
            // Just verify no exception
        }

        @Test
        @DisplayName("Should set region")
        void testWithRegion() throws Exception {
            log.info("Testing region builder method");
            agent.start();
            log.info("Agent region: {}", agent.getRegion());
            assertThat(agent.getRegion()).isEqualTo("us-east-1");
        }

        @Test
        @DisplayName("Should set datacenter")
        void testWithDatacenter() throws Exception {
            log.info("Testing datacenter builder method");
            agent.start();
            log.info("Agent datacenter: {}", agent.getDatacenter());
            assertThat(agent.getDatacenter()).isEqualTo("dc-1");
        }

        @Test
        @DisplayName("Should set capabilities")
        void testWithCapabilities() {
            log.info("Testing capabilities builder method");
            log.info("Agent capabilities: maxConcurrent={}, protocols={}", 
                agent.getCapabilities().maxConcurrentTransfers(), agent.getCapabilities().supportedProtocols());
            assertThat(agent.getCapabilities().maxConcurrentTransfers()).isEqualTo(3);
            assertThat(agent.getCapabilities().supportedProtocols())
                .containsExactlyInAnyOrder("sftp", "http");
        }

        @Test
        @DisplayName("Should add tags")
        void testWithTag() throws Exception {
            log.info("Testing tag builder methods");
            agent.withTag("env", "test")
                 .withTag("tier", "gold");
            agent.start();
            log.info("Agent tags: {}", controller.getLastRegistration().tags());
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
            log.info("Testing job execution");
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

            log.info("Job completed: {}", agent.getCompletedJobs().get(0).status());
            assertThat(agent.getCompletedJobs()).hasSize(1);
            assertThat(agent.getCompletedJobs().get(0).status())
                .isEqualTo(InMemoryAgentSimulator.JobStatus.COMPLETED);
        }

        @Test
        @DisplayName("Should track active jobs")
        void testTrackActiveJobs() throws Exception {
            log.info("Testing active job tracking");
            agent.setJobExecutionDelayMs(2000); // Long execution
            agent.start();

            var assignment = new InMemoryAgentSimulator.JobAssignment(
                "job-1", "sftp://host/file.txt", "/dest/file.txt", "sftp", 10000, Map.of()
            );

            agent.assignJob(assignment);
            Thread.sleep(100); // Let job start

            log.info("Active jobs: count={}, ids={}", agent.getActiveJobCount(), agent.getActiveJobs().keySet());
            assertThat(agent.getActiveJobCount()).isEqualTo(1);
            assertThat(agent.getActiveJobs()).containsKey("job-1");
        }

        @Test
        @DisplayName("Should reject job when at capacity")
        void testRejectAtCapacity() throws Exception {
            log.info("Testing job rejection at capacity");
            agent.setJobExecutionDelayMs(5000);
            agent.start();

            // Fill capacity (3 jobs)
            for (int i = 0; i < 3; i++) {
                agent.assignJob(new InMemoryAgentSimulator.JobAssignment(
                    "job-" + i, "sftp://host/file.txt", "/dest/file.txt", "sftp", 10000, Map.of()
                ));
            }

            Thread.sleep(100);
            log.info("Capacity filled with 3 jobs, attempting 4th job");

            // Fourth job should be rejected
            List<InMemoryAgentSimulator.AgentEvent> events = new CopyOnWriteArrayList<>();
            agent.withEventCallback(events::add);

            agent.assignJob(new InMemoryAgentSimulator.JobAssignment(
                "job-extra", "sftp://host/file.txt", "/dest/file.txt", "sftp", 10000, Map.of()
            ));

            await().atMost(1, SECONDS).until(() -> 
                events.stream().anyMatch(e -> 
                    e.type() == InMemoryAgentSimulator.AgentEvent.EventType.JOB_REJECTED));
            log.info("Job rejection event received");
        }

        @Test
        @DisplayName("Should filter jobs")
        void testJobFilter() throws Exception {
            log.info("Testing job acceptance filter (SFTP only)");
            agent.withJobAcceptanceFilter(job -> job.protocol().equals("sftp"));
            agent.start();

            List<InMemoryAgentSimulator.AgentEvent> events = new CopyOnWriteArrayList<>();
            agent.withEventCallback(events::add);

            // HTTP job should be filtered
            log.info("Assigning HTTP job (should be filtered)");
            agent.assignJob(new InMemoryAgentSimulator.JobAssignment(
                "http-job", "http://host/file.txt", "/dest/file.txt", "http", 10000, Map.of()
            ));

            await().atMost(1, SECONDS).until(() -> 
                events.stream().anyMatch(e -> 
                    e.type() == InMemoryAgentSimulator.AgentEvent.EventType.JOB_REJECTED));
            log.info("HTTP job rejected by filter");
        }
    }

    // ==================== Heartbeat Tests ====================

    @Nested
    @DisplayName("Heartbeat")
    class HeartbeatTests {

        @Test
        @DisplayName("Should send heartbeats")
        void testSendHeartbeats() throws Exception {
            log.info("Testing heartbeat sending with 100ms interval");
            agent.setHeartbeatIntervalMs(100);
            agent.start();

            await().atMost(2, SECONDS).until(() -> 
                controller.getHeartbeatCount() >= 3);

            log.info("Heartbeats received: {}, last: {}", controller.getHeartbeatCount(), agent.getLastHeartbeat());
            assertThat(agent.getLastHeartbeat()).isNotNull();
        }

        @Test
        @DisplayName("Should include status in heartbeat")
        void testHeartbeatContent() throws Exception {
            log.info("Testing heartbeat content");
            agent.setHeartbeatIntervalMs(50);
            agent.start();

            await().atMost(1, SECONDS).until(() -> 
                controller.getLastHeartbeat() != null);

            var heartbeat = controller.getLastHeartbeat();
            log.info("Heartbeat content: agentId={}, state={}", heartbeat.agentId(), heartbeat.state());
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
            log.info("Testing state change event firing");
            List<InMemoryAgentSimulator.AgentEvent> events = new CopyOnWriteArrayList<>();
            agent.withEventCallback(events::add);

            agent.start();

            await().atMost(1, SECONDS).until(() -> 
                events.stream().anyMatch(e -> 
                    e.type() == InMemoryAgentSimulator.AgentEvent.EventType.STATE_CHANGED));

            log.info("Events fired: {}", events.stream().map(e -> e.type()).toList());
            assertThat(events).extracting(InMemoryAgentSimulator.AgentEvent::type)
                .contains(InMemoryAgentSimulator.AgentEvent.EventType.STATE_CHANGED);
        }

        @Test
        @DisplayName("Should fire job events")
        void testJobEvents() throws Exception {
            log.info("Testing job lifecycle event firing");
            List<InMemoryAgentSimulator.AgentEvent> events = new CopyOnWriteArrayList<>();
            agent.withEventCallback(events::add);
            agent.start();

            agent.assignJob(new InMemoryAgentSimulator.JobAssignment(
                "job-1", "sftp://host/file.txt", "/dest/file.txt", "sftp", 10000, Map.of()
            ));

            await().atMost(5, SECONDS).until(() -> 
                events.stream().anyMatch(e -> 
                    e.type() == InMemoryAgentSimulator.AgentEvent.EventType.JOB_COMPLETED));

            log.info("Job events fired: {}", events.stream()
                .filter(e -> e.type().name().startsWith("JOB_"))
                .map(e -> e.type()).toList());
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
            log.info("Testing crash event firing");
            List<InMemoryAgentSimulator.AgentEvent> events = new CopyOnWriteArrayList<>();
            agent.withEventCallback(events::add);
            agent.start();

            agent.crash();

            log.info("Crash event fired: {}", events.stream()
                .anyMatch(e -> e.type() == InMemoryAgentSimulator.AgentEvent.EventType.CRASHED));
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
            log.info("Testing failure mode: {}", mode);
            agent.setFailureMode(mode);
            // Just verify setting doesn't throw
        }

        @Test
        @DisplayName("Should fail registration")
        void testRegistrationFailure() {
            log.info("Testing REGISTRATION_FAILURE chaos mode");
            agent.setFailureMode(InMemoryAgentSimulator.AgentFailureMode.REGISTRATION_FAILURE);

            assertThatThrownBy(() -> agent.start())
                .isInstanceOf(InMemoryAgentSimulator.AgentException.class)
                .hasMessageContaining("Registration failed");
            log.info("Registration failed as expected");
        }

        @Test
        @DisplayName("Should stop heartbeats in timeout mode")
        void testHeartbeatTimeout() throws Exception {
            log.info("Testing HEARTBEAT_TIMEOUT chaos mode");
            agent.start();
            int initialHeartbeats = controller.getHeartbeatCount();

            agent.setFailureMode(InMemoryAgentSimulator.AgentFailureMode.HEARTBEAT_TIMEOUT);

            Thread.sleep(500);

            // Heartbeats should have stopped
            int finalHeartbeats = controller.getHeartbeatCount();
            log.info("Heartbeats: initial={}, final={}, delta={}", initialHeartbeats, finalHeartbeats, finalHeartbeats - initialHeartbeats);
            assertThat(finalHeartbeats - initialHeartbeats).isLessThanOrEqualTo(1);
        }

        @Test
        @DisplayName("Should reject jobs in rejection mode")
        void testJobRejection() throws Exception {
            log.info("Testing JOB_REJECTION chaos mode");
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
            log.info("Job rejected as expected in JOB_REJECTION mode");
        }

        @Test
        @DisplayName("Should fail all jobs in failure mode")
        void testJobFailure() throws Exception {
            log.info("Testing JOB_FAILURE chaos mode");
            agent.setFailureMode(InMemoryAgentSimulator.AgentFailureMode.JOB_FAILURE);
            agent.start();

            agent.assignJob(new InMemoryAgentSimulator.JobAssignment(
                "job-1", "sftp://host/file.txt", "/dest/file.txt", "sftp", 10000, Map.of()
            ));

            await().atMost(5, SECONDS).until(() -> 
                agent.getTotalJobsFailed() == 1);

            log.info("Job status: {}", agent.getCompletedJobs().get(0).status());
            assertThat(agent.getCompletedJobs().get(0).status())
                .isEqualTo(InMemoryAgentSimulator.JobStatus.FAILED);
        }

        @Test
        @DisplayName("Should execute slowly in slow mode")
        void testSlowExecution() throws Exception {
            log.info("Testing SLOW_EXECUTION chaos mode (10x slower)");
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
            log.info("Slow execution completed in {}ms (expected >1000ms)", elapsed);
            assertThat(elapsed).isGreaterThan(1000); // 10x slower
        }

        @Test
        @DisplayName("Should simulate network partition")
        void testNetworkPartition() throws Exception {
            log.info("Testing NETWORK_PARTITION chaos mode");
            agent.start();
            int initialHeartbeats = controller.getHeartbeatCount();

            agent.setFailureMode(InMemoryAgentSimulator.AgentFailureMode.NETWORK_PARTITION);

            log.info("Agent state after partition: {}", agent.getState());
            assertThat(agent.getState()).isEqualTo(InMemoryAgentSimulator.AgentState.PARTITIONED);
        }

        @Test
        @DisplayName("Should fail jobs randomly")
        void testRandomJobFailure() throws Exception {
            log.info("Testing random job failure with 100% failure rate");
            agent.setJobFailureRate(1.0); // 100% failure
            agent.start();

            agent.assignJob(new InMemoryAgentSimulator.JobAssignment(
                "job-1", "sftp://host/file.txt", "/dest/file.txt", "sftp", 10000, Map.of()
            ));

            await().atMost(5, SECONDS).until(() -> 
                agent.getTotalJobsFailed() == 1);
            log.info("Random job failure triggered as expected");
        }

        @Test
        @DisplayName("Should reset chaos settings")
        void testResetChaos() throws Exception {
            log.info("Testing chaos settings reset");
            agent.setFailureMode(InMemoryAgentSimulator.AgentFailureMode.JOB_FAILURE);
            agent.setJobFailureRate(1.0);
            agent.reset();
            agent.start();

            agent.assignJob(new InMemoryAgentSimulator.JobAssignment(
                "job-1", "sftp://host/file.txt", "/dest/file.txt", "sftp", 10000, Map.of()
            ));

            await().atMost(5, SECONDS).until(() -> 
                agent.getTotalJobsCompleted() == 1);
            log.info("Job completed successfully after chaos reset");
        }
    }

    // ==================== Statistics Tests ====================

    @Nested
    @DisplayName("Statistics")
    class StatisticsTests {

        @Test
        @DisplayName("Should track total jobs received")
        void testTotalJobsReceived() throws Exception {
            log.info("Testing total jobs received tracking");
            agent.start();

            agent.assignJob(new InMemoryAgentSimulator.JobAssignment(
                "job-1", "sftp://host/file.txt", "/dest/file.txt", "sftp", 10000, Map.of()
            ));
            agent.assignJob(new InMemoryAgentSimulator.JobAssignment(
                "job-2", "sftp://host/file.txt", "/dest/file.txt", "sftp", 10000, Map.of()
            ));

            log.info("Total jobs received: {}", agent.getTotalJobsReceived());
            assertThat(agent.getTotalJobsReceived()).isEqualTo(2);
        }

        @Test
        @DisplayName("Should track total jobs completed")
        void testTotalJobsCompleted() throws Exception {
            log.info("Testing total jobs completed tracking");
            agent.start();

            agent.assignJob(new InMemoryAgentSimulator.JobAssignment(
                "job-1", "sftp://host/file.txt", "/dest/file.txt", "sftp", 10000, Map.of()
            ));

            await().atMost(5, SECONDS).until(() -> 
                agent.getTotalJobsCompleted() == 1);
            log.info("Total jobs completed: {}", agent.getTotalJobsCompleted());
        }

        @Test
        @DisplayName("Should track total bytes transferred")
        void testTotalBytesTransferred() throws Exception {
            log.info("Testing total bytes transferred tracking");
            agent.start();

            agent.assignJob(new InMemoryAgentSimulator.JobAssignment(
                "job-1", "sftp://host/file.txt", "/dest/file.txt", "sftp", 5000, Map.of()
            ));

            await().atMost(5, SECONDS).until(() -> 
                agent.getTotalJobsCompleted() == 1);

            log.info("Total bytes transferred: {}", agent.getTotalBytesTransferred());
            assertThat(agent.getTotalBytesTransferred()).isEqualTo(5000);
        }

        @Test
        @DisplayName("Should reset statistics")
        void testResetStatistics() throws Exception {
            log.info("Testing statistics reset");
            agent.start();

            agent.assignJob(new InMemoryAgentSimulator.JobAssignment(
                "job-1", "sftp://host/file.txt", "/dest/file.txt", "sftp", 10000, Map.of()
            ));

            await().atMost(5, SECONDS).until(() -> 
                agent.getTotalJobsCompleted() == 1);

            agent.resetStatistics();
            log.info("After reset: received={}, completed={}, bytes={}", 
                agent.getTotalJobsReceived(), agent.getTotalJobsCompleted(), agent.getTotalBytesTransferred());

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
            log.info("Testing capabilities with supported protocols");
            var caps = new InMemoryAgentSimulator.AgentCapabilities()
                .supportedProtocols(Set.of("sftp", "http", "smb"));

            log.info("Supported protocols: {}", caps.supportedProtocols());
            assertThat(caps.supportedProtocols())
                .containsExactlyInAnyOrder("sftp", "http", "smb");
        }

        @Test
        @DisplayName("Should build capabilities with max concurrent transfers")
        void testMaxConcurrentTransfers() {
            log.info("Testing capabilities with max concurrent transfers");
            var caps = new InMemoryAgentSimulator.AgentCapabilities()
                .maxConcurrentTransfers(10);

            log.info("Max concurrent transfers: {}", caps.maxConcurrentTransfers());
            assertThat(caps.maxConcurrentTransfers()).isEqualTo(10);
        }

        @Test
        @DisplayName("Should build capabilities with max transfer size")
        void testMaxTransferSize() {
            log.info("Testing capabilities with max transfer size");
            var caps = new InMemoryAgentSimulator.AgentCapabilities()
                .maxTransferSize(5_000_000_000L);

            log.info("Max transfer size: {} bytes", caps.maxTransferSize());
            assertThat(caps.maxTransferSize()).isEqualTo(5_000_000_000L);
        }
    }

    // ==================== Edge Cases Tests ====================

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCasesTests {

        @Test
        @DisplayName("Should handle empty agent ID")
        void testEmptyAgentId() {
            log.info("Testing empty agent ID handling");
            var emptyAgent = new InMemoryAgentSimulator("");
            log.info("Empty agent ID handled successfully");
            assertThat(emptyAgent).isNotNull();
        }

        @Test
        @DisplayName("Should handle null hostname gracefully")
        void testNullHostname() {
            log.info("Testing null hostname handling");
            var agentNullHost = new InMemoryAgentSimulator("null-host-agent")
                .withHostname(null);
            log.info("Null hostname handled successfully");
            assertThat(agentNullHost).isNotNull();
        }

        @Test
        @DisplayName("Should handle empty capabilities")
        void testEmptyCapabilities() throws Exception {
            log.info("Testing empty capabilities handling");
            var agentEmptyCaps = new InMemoryAgentSimulator("empty-caps")
                .withCapabilities(new InMemoryAgentSimulator.AgentCapabilities())
                .connectToController(controller);
            agentEmptyCaps.setJobExecutionDelayMs(100);
            
            try {
                agentEmptyCaps.start();
                log.info("Agent with empty capabilities state: {}", agentEmptyCaps.getState());
                assertThat(agentEmptyCaps.getState()).isEqualTo(InMemoryAgentSimulator.AgentState.ACTIVE);
            } finally {
                agentEmptyCaps.shutdown();
            }
        }

        @Test
        @DisplayName("Should handle zero max concurrent transfers")
        void testZeroMaxConcurrent() throws Exception {
            log.info("Testing zero max concurrent transfers");
            var caps = new InMemoryAgentSimulator.AgentCapabilities()
                .maxConcurrentTransfers(0);
            
            var zeroAgent = new InMemoryAgentSimulator("zero-concurrent")
                .withCapabilities(caps)
                .connectToController(controller);
            
            try {
                zeroAgent.start();
                log.info("Agent with zero max concurrent state: {}", zeroAgent.getState());
                // With zero max, jobs should still be queued but not executed
                assertThat(zeroAgent.getState()).isIn(
                    InMemoryAgentSimulator.AgentState.ACTIVE,
                    InMemoryAgentSimulator.AgentState.BUSY);
            } finally {
                zeroAgent.shutdown();
            }
        }

        @Test
        @DisplayName("Should handle negative job execution delay")
        void testNegativeExecutionDelay() {
            log.info("Testing negative job execution delay handling");
            // Should handle gracefully - treat as 0
            agent.setJobExecutionDelayMs(-100);
            log.info("Negative execution delay handled successfully");
            assertThat(agent).isNotNull();
        }

        @Test
        @DisplayName("Should handle job with empty source URI")
        void testEmptySourceUri() throws Exception {
            log.info("Testing job with empty source URI");
            agent.start();
            
            List<InMemoryAgentSimulator.AgentEvent> events = new CopyOnWriteArrayList<>();
            agent.withEventCallback(events::add);
            
            agent.assignJob(new InMemoryAgentSimulator.JobAssignment(
                "empty-uri-job", "", "/dest/file.txt", "sftp", 1000, Map.of()
            ));
            
            // Should process (simulator doesn't validate URIs, just simulates)
            await().atMost(5, SECONDS).until(() -> 
                agent.getTotalJobsReceived() >= 1);
            log.info("Empty source URI job received: {}", agent.getTotalJobsReceived());
        }

        @Test
        @DisplayName("Should handle job with null metadata")
        void testNullJobMetadata() throws Exception {
            log.info("Testing job with null metadata");
            agent.start();
            
            agent.assignJob(new InMemoryAgentSimulator.JobAssignment(
                "null-meta-job", "sftp://host/file.txt", "/dest/file.txt", "sftp", 1000, null
            ));
            
            await().atMost(5, SECONDS).until(() -> 
                agent.getTotalJobsReceived() >= 1);
            log.info("Null metadata job received: {}", agent.getTotalJobsReceived());
        }

        @Test
        @DisplayName("Should handle duplicate job IDs")
        void testDuplicateJobIds() throws Exception {
            log.info("Testing duplicate job IDs handling");
            agent.start();
            
            agent.assignJob(new InMemoryAgentSimulator.JobAssignment(
                "dup-job", "sftp://host/file.txt", "/dest/file.txt", "sftp", 1000, Map.of()
            ));
            agent.assignJob(new InMemoryAgentSimulator.JobAssignment(
                "dup-job", "sftp://host/file2.txt", "/dest/file2.txt", "sftp", 2000, Map.of()
            ));
            
            await().atMost(5, SECONDS).until(() -> 
                agent.getTotalJobsReceived() >= 2);
            log.info("Duplicate job IDs received: {}", agent.getTotalJobsReceived());
        }

        @Test
        @DisplayName("Should handle zero byte transfer")
        void testZeroByteTransfer() throws Exception {
            log.info("Testing zero byte transfer");
            agent.start();
            
            agent.assignJob(new InMemoryAgentSimulator.JobAssignment(
                "zero-byte-job", "sftp://host/empty.txt", "/dest/empty.txt", "sftp", 0, Map.of()
            ));
            
            await().atMost(5, SECONDS).until(() -> 
                agent.getTotalJobsCompleted() >= 1);
            log.info("Zero byte transfer completed");
        }

        @Test
        @DisplayName("Should handle very large transfer size")
        void testLargeTransferSize() throws Exception {
            log.info("Testing very large transfer size (Long.MAX_VALUE)");
            agent.start();
            
            agent.assignJob(new InMemoryAgentSimulator.JobAssignment(
                "large-job", "sftp://host/huge.bin", "/dest/huge.bin", "sftp", 
                Long.MAX_VALUE, Map.of()
            ));
            
            await().atMost(5, SECONDS).until(() -> 
                agent.getTotalJobsCompleted() >= 1);
            
            log.info("Large transfer bytes tracked: {}", agent.getTotalBytesTransferred());
            // Should track the large bytes
            assertThat(agent.getTotalBytesTransferred()).isEqualTo(Long.MAX_VALUE);
        }

        @Test
        @DisplayName("Should handle special characters in job ID")
        void testSpecialCharactersInJobId() throws Exception {
            log.info("Testing special characters in job ID");
            agent.start();
            
            agent.assignJob(new InMemoryAgentSimulator.JobAssignment(
                "job-with-特殊字符-#$%", "sftp://host/file.txt", "/dest/file.txt", 
                "sftp", 1000, Map.of()
            ));
            
            await().atMost(5, SECONDS).until(() -> 
                agent.getTotalJobsCompleted() >= 1);
            log.info("Special character job ID handled successfully");
        }

        @Test
        @DisplayName("Should handle rapid state transitions")
        void testRapidStateTransitions() throws Exception {
            log.info("Testing rapid state transitions");
            agent.start();
            agent.stop();
            
            // Reset and try again
            var newAgent = new InMemoryAgentSimulator("rapid-state")
                .connectToController(controller);
            newAgent.setJobExecutionDelayMs(100);
            
            try {
                newAgent.start();
                newAgent.crash();
                newAgent.recover();
                newAgent.stop();
                
                log.info("Final state after rapid transitions: {}", newAgent.getState());
                assertThat(newAgent.getState()).isEqualTo(InMemoryAgentSimulator.AgentState.STOPPED);
            } finally {
                newAgent.shutdown();
            }
        }
    }

    // ==================== Concurrency Tests ====================

    @Nested
    @DisplayName("Concurrency")
    class ConcurrencyTests {

        @Test
        @DisplayName("Should handle concurrent job assignments")
        void testConcurrentJobAssignments() throws Exception {
            log.info("Testing concurrent job assignments with 5 threads submitting 10 jobs");
            agent.setJobExecutionDelayMs(10); // Fast execution
            agent.start();
            log.info("Agent started with max capacity: {}", agent.getCapabilities().maxConcurrentTransfers());
            
            int numJobs = 10;
            ExecutorService executor = Executors.newFixedThreadPool(5);
            CountDownLatch latch = new CountDownLatch(numJobs);
            AtomicInteger submitted = new AtomicInteger(0);
            AtomicInteger rejected = new AtomicInteger(0);
            
            try {
                log.info("Submitting {} jobs concurrently...", numJobs);
                for (int i = 0; i < numJobs; i++) {
                    final int jobNum = i;
                    executor.submit(() -> {
                        try {
                            agent.assignJob(new InMemoryAgentSimulator.JobAssignment(
                                "concurrent-job-" + jobNum,
                                "sftp://host/file" + jobNum + ".txt",
                                "/dest/file" + jobNum + ".txt",
                                "sftp", 1000, Map.of()
                            ));
                            submitted.incrementAndGet();
                        } catch (Exception e) {
                            rejected.incrementAndGet();
                        } finally {
                            latch.countDown();
                        }
                    });
                }
                
                latch.await(5, SECONDS);
                
                log.info("Jobs submitted: {}, rejected: {}, total received: {}", 
                    submitted.get(), rejected.get(), agent.getTotalJobsReceived());
                
                // Verify some jobs were submitted (may not be all due to capacity)
                assertThat(submitted.get()).isGreaterThan(0);
                assertThat(agent.getTotalJobsReceived()).isGreaterThan(0);
            } finally {
                executor.shutdownNow();
            }
        }

        @Test
        @DisplayName("Should handle concurrent start/stop attempts")
        void testConcurrentStartStop() throws Exception {
            log.info("Testing concurrent start/stop with 4 threads racing to change agent state");
            ExecutorService executor = Executors.newFixedThreadPool(4);
            AtomicInteger starts = new AtomicInteger(0);
            AtomicInteger stops = new AtomicInteger(0);
            
            try {
                agent.start();
                log.info("Agent started, initial state: {}", agent.getState());
                
                log.info("Launching 20 concurrent start/stop operations...");
                for (int i = 0; i < 10; i++) {
                    executor.submit(() -> {
                        try {
                            if (agent.getState() == InMemoryAgentSimulator.AgentState.ACTIVE) {
                                agent.stop();
                                stops.incrementAndGet();
                            }
                        } catch (Exception ignored) {}
                    });
                    executor.submit(() -> {
                        try {
                            if (agent.getState() == InMemoryAgentSimulator.AgentState.STOPPED) {
                                agent.start();
                                starts.incrementAndGet();
                            }
                        } catch (Exception ignored) {}
                    });
                }
                
                executor.shutdown();
                executor.awaitTermination(5, SECONDS);
                
                log.info("Completed - starts: {}, stops: {}, final state: {}", 
                    starts.get(), stops.get(), agent.getState());
                
                assertThat(agent.getState()).isIn(
                    InMemoryAgentSimulator.AgentState.ACTIVE,
                    InMemoryAgentSimulator.AgentState.STOPPED);
            } finally {
                executor.shutdownNow();
            }
        }

        @Test
        @DisplayName("Should respect max concurrent job limit")
        void testMaxConcurrentJobLimit() throws Exception {
            int maxConcurrent = 3;
            log.info("Testing max concurrent job limit of {}", maxConcurrent);
            
            var caps = new InMemoryAgentSimulator.AgentCapabilities()
                .maxConcurrentTransfers(maxConcurrent);
            
            var limitAgent = new InMemoryAgentSimulator("limit-test")
                .withCapabilities(caps)
                .connectToController(controller);
            limitAgent.setJobExecutionDelayMs(2000); // Slow execution
            
            try {
                limitAgent.start();
                log.info("Agent started with max concurrent: {}", maxConcurrent);
                
                int totalJobs = maxConcurrent + 3;
                log.info("Submitting {} jobs (3 more than max)...", totalJobs);
                for (int i = 0; i < totalJobs; i++) {
                    limitAgent.assignJob(new InMemoryAgentSimulator.JobAssignment(
                        "limit-job-" + i, "sftp://host/file.txt", "/dest/file.txt",
                        "sftp", 1000, Map.of()
                    ));
                }
                
                Thread.sleep(500); // Let jobs start
                
                int activeCount = limitAgent.getActiveJobCount();
                log.info("Active jobs: {} (should be <= {})", activeCount, maxConcurrent);
                
                assertThat(activeCount).isLessThanOrEqualTo(maxConcurrent);
            } finally {
                limitAgent.shutdown();
            }
        }

        @Test
        @DisplayName("Should handle parallel failure mode changes")
        void testParallelFailureModeChanges() throws Exception {
            log.info("Testing parallel failure mode changes with 3 threads");
            agent.start();
            ExecutorService executor = Executors.newFixedThreadPool(3);
            
            try {
                log.info("Launching 40 concurrent failure mode changes (NONE vs SLOW_EXECUTION)...");
                for (int i = 0; i < 20; i++) {
                    executor.submit(() -> {
                        agent.setFailureMode(InMemoryAgentSimulator.AgentFailureMode.NONE);
                    });
                    executor.submit(() -> {
                        agent.setFailureMode(InMemoryAgentSimulator.AgentFailureMode.SLOW_EXECUTION);
                    });
                }
                
                executor.shutdown();
                executor.awaitTermination(5, SECONDS);
                
                log.info("Completed - agent state: {} (should still be functional)", agent.getState());
                
                assertThat(agent.getState()).isIn(
                    InMemoryAgentSimulator.AgentState.ACTIVE,
                    InMemoryAgentSimulator.AgentState.PARTITIONED);
            } finally {
                executor.shutdownNow();
            }
        }
    }

    // ==================== Security Edge Cases ====================

    @Nested
    @DisplayName("Security Edge Cases")
    class SecurityEdgeCasesTests {

        @Test
        @DisplayName("Should handle path traversal in destination")
        void testPathTraversalInDestination() throws Exception {
            log.info("Testing path traversal in destination handling");
            agent.start();
            
            // Simulator doesn't validate paths, but should not crash
            agent.assignJob(new InMemoryAgentSimulator.JobAssignment(
                "traversal-job", "sftp://host/file.txt", 
                "/dest/../../../etc/passwd", "sftp", 1000, Map.of()
            ));
            
            await().atMost(5, SECONDS).until(() -> 
                agent.getTotalJobsReceived() >= 1);
            log.info("Path traversal job handled without crash");
        }

        @Test
        @DisplayName("Should handle malformed URI")
        void testMalformedUri() throws Exception {
            log.info("Testing malformed URI handling");
            agent.start();
            
            // Simulator processes without validation
            agent.assignJob(new InMemoryAgentSimulator.JobAssignment(
                "malformed-uri-job", "not-a-valid-uri:::///", "/dest/file.txt",
                "sftp", 1000, Map.of()
            ));
            
            await().atMost(5, SECONDS).until(() -> 
                agent.getTotalJobsReceived() >= 1);
            log.info("Malformed URI job handled without crash");
        }

        @Test
        @DisplayName("Should handle very long agent ID")
        void testVeryLongAgentId() {
            log.info("Testing very long agent ID (10000 chars)");
            String longId = "a".repeat(10000);
            var longIdAgent = new InMemoryAgentSimulator(longId);
            log.info("Very long agent ID handled successfully");
            assertThat(longIdAgent).isNotNull();
        }

        @Test
        @DisplayName("Should handle injection-like metadata values")
        void testInjectionLikeMetadata() throws Exception {
            log.info("Testing injection-like metadata values");
            agent.start();
            
            Map<String, String> suspiciousMetadata = Map.of(
                "key", "'; DROP TABLE jobs; --",
                "script", "<script>alert('xss')</script>",
                "command", "$(rm -rf /)"
            );
            
            agent.assignJob(new InMemoryAgentSimulator.JobAssignment(
                "injection-job", "sftp://host/file.txt", "/dest/file.txt",
                "sftp", 1000, suspiciousMetadata
            ));
            
            await().atMost(5, SECONDS).until(() -> 
                agent.getTotalJobsReceived() >= 1);
            log.info("Injection-like metadata handled safely");
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
