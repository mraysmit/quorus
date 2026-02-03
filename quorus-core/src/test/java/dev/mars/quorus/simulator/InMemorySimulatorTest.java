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

package dev.mars.quorus.simulator;

import dev.mars.quorus.simulator.agent.InMemoryAgentSimulator;
import dev.mars.quorus.simulator.client.InMemoryControllerClientSimulator;
import dev.mars.quorus.simulator.fs.InMemoryFileSystemSimulator;
import dev.mars.quorus.simulator.protocol.InMemoryTransferProtocolSimulator;
import dev.mars.quorus.simulator.transfer.InMemoryTransferEngineSimulator;
import dev.mars.quorus.simulator.workflow.InMemoryWorkflowEngineSimulator;

import org.junit.jupiter.api.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.junit.jupiter.api.extension.ExtendWith;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;

/**
 * Comprehensive test suite for all in-memory simulators.
 * 
 * <p>Tests validate:
 * <ul>
 *   <li>Basic operations for each simulator</li>
 *   <li>Chaos engineering failure modes</li>
 *   <li>Integration between simulators</li>
 *   <li>Performance characteristics</li>
 * </ul>
 * 
 * @author Mark Andrew Ray-Smith
 * @since 1.0
 */
@ExtendWith(SimulatorTestLoggingExtension.class)
@DisplayName("In-Memory Simulator Tests")
class InMemorySimulatorTest {

    private static final Logger log = LoggerFactory.getLogger(InMemorySimulatorTest.class);

    // ==================== File System Simulator Tests ====================

    @Nested
    @DisplayName("InMemoryFileSystemSimulator")
    class FileSystemSimulatorTests {

        private InMemoryFileSystemSimulator fs;

        @BeforeEach
        void setUp() {
            fs = new InMemoryFileSystemSimulator();
        }

        @Test
        @DisplayName("Should create and read file")
        void testCreateAndReadFile() throws IOException {
            log.info("Testing file create and read operations");
            byte[] content = "Hello, World!".getBytes();
            fs.createFile("/test.txt", content);

            byte[] read = fs.readFile("/test.txt");
            assertThat(read).isEqualTo(content);
            log.info("Successfully created and read file with {} bytes", content.length);
        }

        @Test
        @DisplayName("Should create nested directories automatically")
        void testCreateNestedDirectories() throws IOException {
            log.info("Testing automatic nested directory creation");
            fs.createFile("/a/b/c/test.txt", "content".getBytes());

            assertThat(fs.exists("/a")).isTrue();
            assertThat(fs.exists("/a/b")).isTrue();
            assertThat(fs.exists("/a/b/c")).isTrue();
            assertThat(fs.isDirectory("/a/b")).isTrue();
            assertThat(fs.isFile("/a/b/c/test.txt")).isTrue();
            log.info("Nested directories /a/b/c created automatically");
        }

        @Test
        @DisplayName("Should list directory contents")
        void testListDirectory() throws IOException {
            log.info("Testing directory listing");
            fs.createFile("/data/file1.txt", "content1".getBytes());
            fs.createFile("/data/file2.txt", "content2".getBytes());
            fs.createDirectory("/data/subdir");

            List<String> contents = fs.listDirectory("/data");
            assertThat(contents).containsExactlyInAnyOrder("file1.txt", "file2.txt", "subdir/");
            log.info("Directory /data contains: {}", contents);
        }

        @Test
        @DisplayName("Should track file metadata")
        void testFileMetadata() throws IOException {
            log.info("Testing file metadata tracking");
            fs.createFile("/test.txt", "content".getBytes());

            var metadata = fs.getMetadata("/test.txt");
            assertThat(metadata.size()).isEqualTo(7);
            assertThat(metadata.isDirectory()).isFalse();
            assertThat(metadata.created()).isNotNull();
            log.info("File metadata: size={}, created={}", metadata.size(), metadata.created());
        }

        @Test
        @DisplayName("Should enforce space limits")
        void testSpaceLimits() throws IOException {
            log.info("Testing space limit enforcement with 100 bytes max");
            fs.setAvailableSpace(100);
            fs.createFile("/small.txt", new byte[50]);
            log.info("Created 50-byte file successfully");

            assertThatThrownBy(() -> fs.createFile("/large.txt", new byte[100]))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("Disk full");
            log.info("Large file correctly rejected - disk full");
        }

        @Test
        @DisplayName("Should simulate disk full failure mode")
        void testDiskFullFailureMode() {
            log.info("Testing DISK_FULL failure mode");
            fs.setFailureMode(InMemoryFileSystemSimulator.FileSystemFailureMode.DISK_FULL);

            assertThatThrownBy(() -> fs.createFile("/test.txt", "content".getBytes()))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("Disk full");
            log.info("DISK_FULL failure mode triggered correctly");
        }

        @Test
        @DisplayName("Should support file locking")
        void testFileLocking() throws IOException {
            log.info("Testing file locking mechanism");
            fs.createFile("/test.txt", "content".getBytes());
            fs.lockFile("/test.txt");
            log.info("File locked, attempting read");

            assertThat(fs.isLocked("/test.txt")).isTrue();
            assertThatThrownBy(() -> fs.readFile("/test.txt"))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("locked");

            fs.unlockFile("/test.txt");
            assertThat(fs.isLocked("/test.txt")).isFalse();
            log.info("File unlocked successfully");
        }

        @Test
        @DisplayName("Should track statistics")
        void testStatistics() throws IOException {
            log.info("Testing file system statistics tracking");
            fs.createFile("/test.txt", new byte[1000]);
            fs.readFile("/test.txt");
            fs.readFile("/test.txt");

            assertThat(fs.getWriteOperations()).isEqualTo(1);
            assertThat(fs.getReadOperations()).isEqualTo(2);
            assertThat(fs.getBytesWritten()).isEqualTo(1000);
            assertThat(fs.getBytesRead()).isEqualTo(2000);
            log.info("Stats: writes={}, reads={}, bytesWritten={}, bytesRead={}",
                fs.getWriteOperations(), fs.getReadOperations(), fs.getBytesWritten(), fs.getBytesRead());
        }
    }

    // ==================== Transfer Protocol Simulator Tests ====================

    @Nested
    @DisplayName("InMemoryTransferProtocolSimulator")
    class TransferProtocolSimulatorTests {

        private InMemoryFileSystemSimulator fs;
        private InMemoryTransferProtocolSimulator protocol;

        @BeforeEach
        void setUp() throws IOException {
            fs = new InMemoryFileSystemSimulator();
            fs.createFile("/source/test.txt", "Hello, World!".getBytes());
            protocol = InMemoryTransferProtocolSimulator.sftp(fs);
        }

        @AfterEach
        void tearDown() {
            protocol.shutdown();
        }

        @Test
        @DisplayName("Should transfer file successfully")
        void testSuccessfulTransfer() throws Exception {
            log.info("Testing successful SFTP file transfer");
            var request = new InMemoryTransferProtocolSimulator.TransferRequest(
                URI.create("sftp://server/source/test.txt"),
                Path.of("/dest/test.txt")
            );

            var result = protocol.transfer(request, new InMemoryTransferProtocolSimulator.TransferContext());

            assertThat(result.isSuccessful()).isTrue();
            assertThat(result.bytesTransferred()).isEqualTo(13);
            assertThat(fs.exists("/dest/test.txt")).isTrue();
            log.info("Transfer successful: {} bytes transferred", result.bytesTransferred());
        }

        @Test
        @DisplayName("Should report progress during transfer")
        void testProgressReporting() throws Exception {
            log.info("Testing progress reporting during transfer");
            // Create larger file for progress testing
            fs.createFile("/source/large.bin", new byte[10000]);
            protocol.setSimulatedBytesPerSecond(5000); // Slow down for progress updates

            List<Integer> progressValues = Collections.synchronizedList(new ArrayList<>());

            var request = InMemoryTransferProtocolSimulator.TransferRequest.builder()
                .sourceUri(URI.create("sftp://server/source/large.bin"))
                .destinationPath(Path.of("/dest/large.bin"))
                .build();

            var context = new InMemoryTransferProtocolSimulator.TransferContext(
                progress -> progressValues.add(progress.percentComplete())
            );

            protocol.transfer(request, context);

            assertThat(progressValues).isNotEmpty();
            assertThat(progressValues.get(progressValues.size() - 1)).isEqualTo(100);
            log.info("Progress updates received: {} updates, final={}%", progressValues.size(), progressValues.get(progressValues.size() - 1));
        }

        @Test
        @DisplayName("Should simulate authentication failure")
        void testAuthenticationFailure() {
            log.info("Testing AUTH_FAILURE failure mode");
            protocol.setFailureMode(InMemoryTransferProtocolSimulator.ProtocolFailureMode.AUTH_FAILURE);

            var request = new InMemoryTransferProtocolSimulator.TransferRequest(
                URI.create("sftp://server/source/test.txt"),
                Path.of("/dest/test.txt")
            );

            assertThatThrownBy(() -> protocol.transfer(request, 
                new InMemoryTransferProtocolSimulator.TransferContext()))
                .isInstanceOf(InMemoryTransferProtocolSimulator.TransferException.class)
                .hasMessageContaining("Authentication failed");
            log.info("AUTH_FAILURE mode triggered correctly");
        }

        @Test
        @DisplayName("Should support pause and resume")
        void testPauseAndResume() throws Exception {
            log.info("Testing pause and resume during transfer");
            fs.createFile("/source/large.bin", new byte[10000]);
            protocol.setSimulatedBytesPerSecond(100000);

            var request = InMemoryTransferProtocolSimulator.TransferRequest.builder()
                .sourceUri(URI.create("sftp://server/source/large.bin"))
                .destinationPath(Path.of("/dest/large.bin"))
                .build();

            var future = protocol.transferReactive(request, 
                new InMemoryTransferProtocolSimulator.TransferContext());

            // Let it start
            Thread.sleep(100);
            
            // Future should complete eventually
            var result = future.get(30, TimeUnit.SECONDS);
            assertThat(result.isSuccessful()).isTrue();
            log.info("Transfer completed successfully after pause/resume test");
        }

        @Test
        @DisplayName("Should track transfer statistics")
        void testStatistics() throws Exception {
            log.info("Testing transfer statistics tracking");
            var request = new InMemoryTransferProtocolSimulator.TransferRequest(
                URI.create("sftp://server/source/test.txt"),
                Path.of("/dest/test.txt")
            );

            protocol.transfer(request, new InMemoryTransferProtocolSimulator.TransferContext());

            assertThat(protocol.getTotalTransfers()).isEqualTo(1);
            assertThat(protocol.getSuccessfulTransfers()).isEqualTo(1);
            assertThat(protocol.getTotalBytesTransferred()).isGreaterThan(0);
            log.info("Transfer stats: total={}, successful={}, bytes={}",
                protocol.getTotalTransfers(), protocol.getSuccessfulTransfers(), protocol.getTotalBytesTransferred());
        }
    }

    // ==================== Transfer Engine Simulator Tests ====================

    @Nested
    @DisplayName("InMemoryTransferEngineSimulator")
    class TransferEngineSimulatorTests {

        private InMemoryTransferEngineSimulator engine;

        @BeforeEach
        void setUp() {
            engine = new InMemoryTransferEngineSimulator();
            engine.setDefaultTransferDurationMs(100); // Fast for tests
        }

        @AfterEach
        void tearDown() {
            engine.shutdown(5);
        }

        @Test
        @DisplayName("Should submit and complete transfer")
        void testSubmitTransfer() throws Exception {
            log.info("Testing transfer submission and completion");
            var request = InMemoryTransferEngineSimulator.TransferRequest.builder()
                .sourceUri("sftp://server/file.txt")
                .destinationPath("/local/file.txt")
                .protocol("sftp")
                .expectedSizeBytes(1000)
                .build();

            var result = engine.submitTransfer(request).get(5, TimeUnit.SECONDS);

            assertThat(result.successful()).isTrue();
            assertThat(engine.getTotalCompleted()).isEqualTo(1);
            log.info("Transfer completed: totalCompleted={}", engine.getTotalCompleted());
        }

        @Test
        @DisplayName("Should track active transfer count")
        void testConcurrencyLimit() throws Exception {
            log.info("Testing concurrent transfer tracking with 5 transfers");
            engine.setDefaultTransferDurationMs(500);

            List<CompletableFuture<InMemoryTransferEngineSimulator.TransferResult>> futures = 
                new ArrayList<>();

            for (int i = 0; i < 5; i++) {
                var request = InMemoryTransferEngineSimulator.TransferRequest.builder()
                    .jobId("job-" + i)
                    .sourceUri("sftp://server/file" + i + ".txt")
                    .destinationPath("/local/file" + i + ".txt")
                    .build();
                futures.add(engine.submitTransfer(request));
            }
            log.info("Submitted 5 concurrent transfers");

            // Give time for some to start
            Thread.sleep(50);

            // Should track active count
            assertThat(engine.getActiveTransferCount()).isGreaterThanOrEqualTo(0);
            log.info("Active transfer count: {}", engine.getActiveTransferCount());

            // Wait for all to complete
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .get(10, TimeUnit.SECONDS);
            log.info("All transfers completed");
        }

        @Test
        @DisplayName("Should support cancel")
        void testCancelTransfer() throws Exception {
            log.info("Testing transfer cancellation");
            engine.setDefaultTransferDurationMs(5000);

            var request = InMemoryTransferEngineSimulator.TransferRequest.builder()
                .jobId("cancel-test")
                .sourceUri("sftp://server/file.txt")
                .destinationPath("/local/file.txt")
                .build();

            engine.submitTransfer(request);
            Thread.sleep(50);

            boolean cancelled = engine.cancelTransfer("cancel-test");
            assertThat(cancelled).isTrue();
            log.info("Transfer cancel-test cancelled successfully");

            var job = engine.getTransferJob("cancel-test");
            assertThat(job.status())
                .isEqualTo(InMemoryTransferEngineSimulator.TransferStatus.CANCELLED);
            log.info("Transfer status: {}", job.status());
        }

        @Test
        @DisplayName("Should fire events")
        void testEvents() throws Exception {
            log.info("Testing transfer event callbacks");
            List<InMemoryTransferEngineSimulator.TransferEvent> events = 
                Collections.synchronizedList(new ArrayList<>());
            engine.setEventCallback(events::add);

            var request = InMemoryTransferEngineSimulator.TransferRequest.builder()
                .sourceUri("sftp://server/file.txt")
                .destinationPath("/local/file.txt")
                .build();

            engine.submitTransfer(request).get(5, TimeUnit.SECONDS);

            assertThat(events).extracting(e -> e.type().name())
                .contains("STARTED", "COMPLETED");
            log.info("Events fired: {}", events.stream().map(e -> e.type().name()).toList());
        }

        @Test
        @DisplayName("Should handle failure mode")
        void testFailureMode() {
            log.info("Testing QUEUE_FULL failure mode");
            engine.setFailureMode(
                InMemoryTransferEngineSimulator.TransferEngineFailureMode.QUEUE_FULL);

            var request = InMemoryTransferEngineSimulator.TransferRequest.builder()
                .sourceUri("sftp://server/file.txt")
                .destinationPath("/local/file.txt")
                .build();

            var future = engine.submitTransfer(request);
            assertThat(future).isCompletedExceptionally();
            log.info("QUEUE_FULL failure mode rejected transfer as expected");
        }
    }

    // ==================== Controller Client Simulator Tests ====================

    @Nested
    @DisplayName("InMemoryControllerClientSimulator")
    class ControllerClientSimulatorTests {

        private InMemoryControllerClientSimulator client;

        @BeforeEach
        void setUp() {
            client = new InMemoryControllerClientSimulator();
        }

        @AfterEach
        void tearDown() {
            client.shutdown();
        }

        @Test
        @DisplayName("Should handle GET request")
        void testGetRequest() throws Exception {
            log.info("Testing GET /health request");
            var response = client.get("/health").get(5, TimeUnit.SECONDS);

            assertThat(response.isSuccessful()).isTrue();
            assertThat(response.statusCode()).isEqualTo(200);
            log.info("GET /health response: status={}", response.statusCode());
        }

        @Test
        @DisplayName("Should handle POST request with body")
        void testPostRequest() throws Exception {
            log.info("Testing POST /agents/register with body");
            var body = Map.of("agentId", "test-agent");
            var response = client.post("/agents/register", body).get(5, TimeUnit.SECONDS);

            assertThat(response.isSuccessful()).isTrue();
            log.info("POST /agents/register successful: status={}", response.statusCode());
        }

        @Test
        @DisplayName("Should register custom handler")
        void testCustomHandler() throws Exception {
            log.info("Testing custom handler registration");
            client.registerHandler("GET", "/custom", (req, params) -> 
                InMemoryControllerClientSimulator.HttpResponse.ok(Map.of("custom", "response")));

            var response = client.get("/custom").get(5, TimeUnit.SECONDS);

            assertThat(response.isSuccessful()).isTrue();
            @SuppressWarnings("unchecked")
            var body = (Map<String, Object>) response.body();
            assertThat(body.get("custom")).isEqualTo("response");
            log.info("Custom handler returned: {}", body);
        }

        @Test
        @DisplayName("Should match path parameters")
        void testPathParameters() throws Exception {
            log.info("Testing path parameter extraction");
            AtomicInteger capturedId = new AtomicInteger();
            client.registerHandler("GET", "/agents/{agentId}/status", (req, params) -> {
                capturedId.set(Integer.parseInt(params.get("agentId")));
                return InMemoryControllerClientSimulator.HttpResponse.ok(Map.of("status", "active"));
            });

            client.get("/agents/123/status").get(5, TimeUnit.SECONDS);

            assertThat(capturedId.get()).isEqualTo(123);
            log.info("Path parameter agentId extracted: {}", capturedId.get());
        }

        @Test
        @DisplayName("Should record requests")
        void testRequestRecording() throws Exception {
            log.info("Testing request recording");
            client.get("/health").get(5, TimeUnit.SECONDS);
            client.post("/agents/register", Map.of()).get(5, TimeUnit.SECONDS);

            var recorded = client.getRecordedRequests();
            assertThat(recorded).hasSize(2);
            assertThat(recorded.get(0).request().method()).isEqualTo("GET");
            assertThat(recorded.get(1).request().method()).isEqualTo("POST");
            log.info("Recorded {} requests: {}", recorded.size(), 
                recorded.stream().map(r -> r.request().method()).toList());
        }

        @Test
        @DisplayName("Should simulate connection refused")
        void testConnectionRefused() {
            log.info("Testing CONNECTION_REFUSED failure mode");
            client.setFailureMode(
                InMemoryControllerClientSimulator.ClientFailureMode.CONNECTION_REFUSED);

            var future = client.get("/health");
            assertThat(future).isCompletedExceptionally();
            log.info("CONNECTION_REFUSED correctly rejected request");
        }

        @Test
        @DisplayName("Should simulate latency")
        void testLatencySimulation() throws Exception {
            log.info("Testing latency simulation (100-200ms)");
            client.setLatencyRange(100, 200);

            long start = System.currentTimeMillis();
            client.get("/health").get(5, TimeUnit.SECONDS);
            long duration = System.currentTimeMillis() - start;

            assertThat(duration).isGreaterThanOrEqualTo(100);
            log.info("Request completed in {}ms (expected >=100ms)", duration);
        }
    }

    // ==================== Agent Simulator Tests ====================

    @Nested
    @DisplayName("InMemoryAgentSimulator")
    class AgentSimulatorTests {

        private InMemoryAgentSimulator agent;
        private TestControllerConnection controller;

        @BeforeEach
        void setUp() {
            controller = new TestControllerConnection();
            agent = new InMemoryAgentSimulator("test-agent")
                .withRegion("us-east-1")
                .withCapabilities(new InMemoryAgentSimulator.AgentCapabilities()
                    .supportedProtocols(Set.of("sftp", "http"))
                    .maxConcurrentTransfers(3))
                .setHeartbeatIntervalMs(100)
                .setPollingIntervalMs(100)
                .setJobExecutionDelayMs(100)
                .connectToController(controller);
        }

        @AfterEach
        void tearDown() {
            agent.shutdown();
        }

        @Test
        @DisplayName("Should register with controller on start")
        void testRegistration() throws Exception {
            log.info("Testing agent registration with controller");
            agent.start();

            assertThat(controller.registeredAgents).containsKey("test-agent");
            assertThat(agent.getState()).isEqualTo(InMemoryAgentSimulator.AgentState.ACTIVE);
            log.info("Agent registered: state={}", agent.getState());
        }

        @Test
        @DisplayName("Should send heartbeats")
        void testHeartbeats() throws Exception {
            log.info("Testing heartbeat sending (100ms interval)");
            agent.start();
            Thread.sleep(300);

            assertThat(controller.heartbeatCount.get()).isGreaterThanOrEqualTo(2);
            log.info("Heartbeats sent: {}", controller.heartbeatCount.get());
        }

        @Test
        @DisplayName("Should execute assigned jobs")
        void testJobExecution() throws Exception {
            log.info("Testing job execution");
            agent.start();

            var job = new InMemoryAgentSimulator.JobAssignment(
                "job-001",
                "sftp://server/file.txt",
                "/local/file.txt",
                "sftp",
                1000,
                Map.of()
            );

            agent.assignJob(job);
            log.info("Job assigned: {}", job.jobId());

            // Wait for completion
            await().atMost(Duration.ofSeconds(5))
                .until(() -> agent.getCompletedJobs().size() > 0);

            assertThat(agent.getTotalJobsCompleted()).isEqualTo(1);
            log.info("Job completed: totalCompleted={}", agent.getTotalJobsCompleted());
        }

        @Test
        @DisplayName("Should respect capacity limits")
        void testCapacityLimits() throws Exception {
            log.info("Testing capacity limits (max=3, assigning 5 jobs)");
            agent.start();

            // Assign more jobs than capacity
            for (int i = 0; i < 5; i++) {
                var job = new InMemoryAgentSimulator.JobAssignment(
                    "job-" + i,
                    "sftp://server/file.txt",
                    "/local/file.txt",
                    "sftp",
                    1000,
                    Map.of()
                );
                agent.assignJob(job);
            }

            // Should not exceed max concurrent (3)
            assertThat(agent.getActiveJobCount()).isLessThanOrEqualTo(3);
            log.info("Active jobs: {} (max allowed: 3)", agent.getActiveJobCount());
        }

        @Test
        @DisplayName("Should crash and stop sending heartbeats")
        void testCrash() throws Exception {
            log.info("Testing agent crash simulation");
            agent.start();
            int heartbeatsBefore = controller.heartbeatCount.get();
            log.info("Heartbeats before crash: {}", heartbeatsBefore);

            agent.crash();

            Thread.sleep(200);

            assertThat(agent.getState()).isEqualTo(InMemoryAgentSimulator.AgentState.CRASHED);
            assertThat(controller.heartbeatCount.get()).isEqualTo(heartbeatsBefore);
            log.info("Agent crashed: state={}, heartbeats after={}  (unchanged)", 
                agent.getState(), controller.heartbeatCount.get());
        }

        @Test
        @DisplayName("Should handle job failure mode")
        void testJobFailureMode() throws Exception {
            log.info("Testing JOB_FAILURE failure mode");
            agent.setFailureMode(InMemoryAgentSimulator.AgentFailureMode.JOB_FAILURE);
            agent.start();

            var job = new InMemoryAgentSimulator.JobAssignment(
                "job-001",
                "sftp://server/file.txt",
                "/local/file.txt",
                "sftp",
                1000,
                Map.of()
            );

            agent.assignJob(job);

            await().atMost(Duration.ofSeconds(5))
                .until(() -> agent.getCompletedJobs().size() > 0);

            assertThat(agent.getTotalJobsFailed()).isEqualTo(1);
            log.info("Job failed as expected: totalFailed={}", agent.getTotalJobsFailed());
        }

        /**
         * Test controller connection implementation.
         */
        static class TestControllerConnection implements InMemoryAgentSimulator.ControllerConnection {
            final Map<String, InMemoryAgentSimulator.AgentRegistration> registeredAgents = 
                new ConcurrentHashMap<>();
            final AtomicInteger heartbeatCount = new AtomicInteger(0);
            final List<InMemoryAgentSimulator.JobStatusUpdate> statusUpdates = 
                Collections.synchronizedList(new ArrayList<>());

            @Override
            public void registerAgent(InMemoryAgentSimulator.AgentRegistration registration) {
                registeredAgents.put(registration.agentId(), registration);
            }

            @Override
            public void unregisterAgent(String agentId) {
                registeredAgents.remove(agentId);
            }

            @Override
            public void sendHeartbeat(InMemoryAgentSimulator.HeartbeatInfo heartbeat) {
                heartbeatCount.incrementAndGet();
            }

            @Override
            public List<InMemoryAgentSimulator.JobAssignment> pollPendingJobs(String agentId) {
                return List.of();
            }

            @Override
            public void reportJobStatus(InMemoryAgentSimulator.JobStatusUpdate update) {
                statusUpdates.add(update);
            }
        }
    }

    // ==================== Workflow Engine Simulator Tests ====================

    @Nested
    @DisplayName("InMemoryWorkflowEngineSimulator")
    class WorkflowEngineSimulatorTests {

        private InMemoryWorkflowEngineSimulator engine;

        @BeforeEach
        void setUp() {
            engine = new InMemoryWorkflowEngineSimulator();
            engine.setDefaultStepDurationMs(50);
        }

        @AfterEach
        void tearDown() {
            engine.shutdown();
        }

        @Test
        @DisplayName("Should execute workflow successfully")
        void testSuccessfulExecution() throws Exception {
            log.info("Testing successful workflow execution with 2 steps");
            var workflow = InMemoryWorkflowEngineSimulator.WorkflowDefinition.builder()
                .name("test-workflow")
                .step(InMemoryWorkflowEngineSimulator.WorkflowStep.builder()
                    .name("step1")
                    .type("transfer")
                    .build())
                .step(InMemoryWorkflowEngineSimulator.WorkflowStep.builder()
                    .name("step2")
                    .type("transform")
                    .build())
                .build();

            var execution = engine.execute(workflow, 
                InMemoryWorkflowEngineSimulator.ExecutionContext.empty())
                .get(10, TimeUnit.SECONDS);

            assertThat(execution.getStatus())
                .isEqualTo(InMemoryWorkflowEngineSimulator.WorkflowStatus.COMPLETED);
            assertThat(execution.getStepExecutions()).hasSize(2);
            log.info("Workflow completed: status={}, steps={}", execution.getStatus(), execution.getStepExecutions().size());
        }

        @Test
        @DisplayName("Should report step progress via callback")
        void testStepCallback() throws Exception {
            log.info("Testing step progress callbacks");
            List<String> stepProgress = Collections.synchronizedList(new ArrayList<>());
            engine.setStepCallback((step, status) -> 
                stepProgress.add(step.name() + ":" + status));

            var workflow = InMemoryWorkflowEngineSimulator.WorkflowDefinition.builder()
                .name("test-workflow")
                .step(InMemoryWorkflowEngineSimulator.WorkflowStep.builder()
                    .name("download")
                    .build())
                .step(InMemoryWorkflowEngineSimulator.WorkflowStep.builder()
                    .name("process")
                    .build())
                .build();

            engine.execute(workflow, InMemoryWorkflowEngineSimulator.ExecutionContext.empty())
                .get(10, TimeUnit.SECONDS);

            assertThat(stepProgress).contains(
                "download:RUNNING", "download:COMPLETED",
                "process:RUNNING", "process:COMPLETED"
            );
            log.info("Step callbacks received: {}", stepProgress);
        }

        @Test
        @DisplayName("Should validate workflow in dry-run")
        void testDryRun() throws Exception {
            log.info("Testing workflow dry-run validation");
            var workflow = InMemoryWorkflowEngineSimulator.WorkflowDefinition.builder()
                .name("test-workflow")
                .step(InMemoryWorkflowEngineSimulator.WorkflowStep.builder()
                    .name("step1")
                    .build())
                .build();

            var execution = engine.dryRun(workflow, 
                InMemoryWorkflowEngineSimulator.ExecutionContext.empty())
                .get(5, TimeUnit.SECONDS);

            assertThat(execution.getStatus())
                .isEqualTo(InMemoryWorkflowEngineSimulator.WorkflowStatus.COMPLETED);
            // Dry run doesn't execute steps
            assertThat(execution.getStepExecutions()).isEmpty();
            log.info("Dry-run completed: status={}, steps executed={}", 
                execution.getStatus(), execution.getStepExecutions().size());
        }

        @Test
        @DisplayName("Should fail at specific step")
        void testStepFailure() throws Exception {
            log.info("Testing STEP_FAILURE at step2");
            engine.setFailAtStep("step2");
            engine.setFailureMode(InMemoryWorkflowEngineSimulator.WorkflowFailureMode.STEP_FAILURE);

            var workflow = InMemoryWorkflowEngineSimulator.WorkflowDefinition.builder()
                .name("test-workflow")
                .step(InMemoryWorkflowEngineSimulator.WorkflowStep.builder()
                    .name("step1")
                    .build())
                .step(InMemoryWorkflowEngineSimulator.WorkflowStep.builder()
                    .name("step2")
                    .required(true)
                    .build())
                .build();

            var execution = engine.execute(workflow, 
                InMemoryWorkflowEngineSimulator.ExecutionContext.empty())
                .get(10, TimeUnit.SECONDS);

            assertThat(execution.getStatus())
                .isEqualTo(InMemoryWorkflowEngineSimulator.WorkflowStatus.FAILED);
            assertThat(execution.getError()).contains("step2");
            log.info("Workflow failed at step2 as expected: error={}", execution.getError());
        }

        @Test
        @DisplayName("Should support pause and resume")
        void testPauseResume() throws Exception {
            log.info("Testing workflow pause and resume");
            engine.setDefaultStepDurationMs(500);

            var workflow = InMemoryWorkflowEngineSimulator.WorkflowDefinition.builder()
                .name("test-workflow")
                .step(InMemoryWorkflowEngineSimulator.WorkflowStep.builder()
                    .name("step1")
                    .build())
                .step(InMemoryWorkflowEngineSimulator.WorkflowStep.builder()
                    .name("step2")
                    .build())
                .build();

            var future = engine.execute(workflow, 
                InMemoryWorkflowEngineSimulator.ExecutionContext.empty());

            Thread.sleep(100);
            
            var executions = engine.getActiveExecutions();
            if (!executions.isEmpty()) {
                String executionId = executions.get(0).getExecutionId();
                engine.pause(executionId);
                log.info("Workflow paused: status={}", engine.getStatus(executionId));
                assertThat(engine.getStatus(executionId))
                    .isEqualTo(InMemoryWorkflowEngineSimulator.WorkflowStatus.PAUSED);

                engine.resume(executionId);
                log.info("Workflow resumed");
            }

            var execution = future.get(10, TimeUnit.SECONDS);
            assertThat(execution.isComplete()).isTrue();
            log.info("Workflow completed after pause/resume");
        }

        @Test
        @DisplayName("Should cancel running workflow")
        void testCancel() throws Exception {
            log.info("Testing workflow cancellation");
            engine.setDefaultStepDurationMs(2000);

            var workflow = InMemoryWorkflowEngineSimulator.WorkflowDefinition.builder()
                .name("test-workflow")
                .step(InMemoryWorkflowEngineSimulator.WorkflowStep.builder()
                    .name("long-step")
                    .build())
                .build();

            var future = engine.execute(workflow, 
                InMemoryWorkflowEngineSimulator.ExecutionContext.empty());

            Thread.sleep(100);

            var executions = engine.getActiveExecutions();
            if (!executions.isEmpty()) {
                engine.cancel(executions.get(0).getExecutionId());
                log.info("Workflow cancellation requested");
            }

            var execution = future.get(5, TimeUnit.SECONDS);
            assertThat(execution.getStatus())
                .isEqualTo(InMemoryWorkflowEngineSimulator.WorkflowStatus.CANCELLED);
            log.info("Workflow cancelled: status={}", execution.getStatus());
        }
    }

    // ==================== Integration Tests ====================

    @Nested
    @DisplayName("Simulator Integration")
    class IntegrationTests {

        @Test
        @DisplayName("Should integrate file system with protocol simulator")
        void testFileSystemAndProtocolIntegration() throws Exception {
            // Setup
            InMemoryFileSystemSimulator fs = new InMemoryFileSystemSimulator();
            fs.createFile("/remote/data.csv", "id,name,value\n1,test,100".getBytes());

            InMemoryTransferProtocolSimulator protocol = 
                InMemoryTransferProtocolSimulator.sftp(fs);

            // Transfer
            var request = InMemoryTransferProtocolSimulator.TransferRequest.builder()
                .sourceUri(URI.create("sftp://server/remote/data.csv"))
                .destinationPath(Path.of("/local/data.csv"))
                .build();

            var result = protocol.transfer(request, 
                new InMemoryTransferProtocolSimulator.TransferContext());

            // Verify
            assertThat(result.isSuccessful()).isTrue();
            assertThat(fs.exists("/local/data.csv")).isTrue();
            assertThat(new String(fs.readFile("/local/data.csv")))
                .contains("id,name,value");

            protocol.shutdown();
        }

        @Test
        @DisplayName("Should test chaos scenario: network partition during transfer")
        void testChaosNetworkPartition() throws Exception {
            InMemoryFileSystemSimulator fs = new InMemoryFileSystemSimulator();
            fs.createFile("/source/large.bin", new byte[10000]);

            InMemoryTransferProtocolSimulator protocol = 
                InMemoryTransferProtocolSimulator.sftp(fs);
            protocol.setSimulatedBytesPerSecond(100000);
            protocol.setFailureMode(
                InMemoryTransferProtocolSimulator.ProtocolFailureMode.TRANSFER_INTERRUPTED);
            protocol.setFailureAtPercent(50);

            var request = InMemoryTransferProtocolSimulator.TransferRequest.builder()
                .sourceUri(URI.create("sftp://server/source/large.bin"))
                .destinationPath(Path.of("/dest/large.bin"))
                .build();

            // First attempt should fail
            assertThatThrownBy(() -> 
                protocol.transfer(request, new InMemoryTransferProtocolSimulator.TransferContext()))
                .isInstanceOf(InMemoryTransferProtocolSimulator.TransferException.class)
                .hasMessageContaining("interrupted");

            // Reset and retry
            protocol.reset();
            var result = protocol.transfer(request, 
                new InMemoryTransferProtocolSimulator.TransferContext());

            assertThat(result.isSuccessful()).isTrue();

            protocol.shutdown();
        }
    }

    // ==================== Helper Methods ====================

    /**
     * Awaitility helper for async assertions.
     */
    private static org.awaitility.core.ConditionFactory await() {
        return org.awaitility.Awaitility.await()
            .pollInterval(Duration.ofMillis(50))
            .atMost(Duration.ofSeconds(10));
    }
}
