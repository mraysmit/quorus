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

package dev.mars.quorus.controller.raft;

import dev.mars.quorus.agent.AgentInfo;
import dev.mars.quorus.agent.AgentStatus;
import dev.mars.quorus.controller.http.HttpApiServer;
import dev.mars.quorus.controller.raft.storage.RaftStorage;
import dev.mars.quorus.controller.raft.storage.file.FileRaftStorage;
import dev.mars.quorus.controller.state.AgentCommand;
import dev.mars.quorus.controller.state.CommandResult;
import dev.mars.quorus.controller.state.JobAssignmentCommand;
import dev.mars.quorus.controller.state.QuorusStateStore;
import dev.mars.quorus.controller.state.TransferJobCommand;
import dev.mars.quorus.controller.state.TransferJobSnapshot;
import dev.mars.quorus.core.TransferJob;
import dev.mars.quorus.core.JobAssignment;
import dev.mars.quorus.core.JobAssignmentStatus;
import dev.mars.quorus.core.TransferRequest;
import dev.mars.quorus.core.TransferStatus;
import io.vertx.core.Vertx;
import io.vertx.core.WorkerExecutor;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.core.buffer.Buffer;
import io.vertx.junit5.VertxExtension;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;

import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;

import static dev.mars.quorus.testing.TestFutureUtils.awaitSuccess;
import static dev.mars.quorus.testing.TestFutureUtils.eventually;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

@ExtendWith(VertxExtension.class)
@DisplayName("Durable tenant transfer restart contract")
class DurableTransferRestartTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(10);
    private static final String NODE_ID = "phase0-node";
    private static final String JOB_ID = "phase0-transfer";
    private static final String TENANT_ID = "regulated-bank-a";
    private static final String AGENT_ID = "phase0-agent";
    private static final String ASSIGNMENT_ID = JOB_ID + ":" + AGENT_ID;

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("Committed transfer lifecycle survives restart and remains available through REST")
    void committedTransferSurvivesRestartAndApiQuery(Vertx vertx) {
        Path storagePath = tempDir.resolve("mounted-data").resolve("raft");
        WorkerExecutor firstExecutor = vertx.createSharedWorkerExecutor("phase0-storage-first", 1);
        WorkerExecutor recoveredExecutor = vertx.createSharedWorkerExecutor("phase0-storage-recovered", 1);
        RaftNode firstNode = null;
        RaftNode recoveredNode = null;
        HttpApiServer apiServer = null;
        WebClient client = null;

        try {
            QuorusStateStore firstState = new QuorusStateStore();
            RaftStorage firstStorage = openStorage(vertx, firstExecutor, storagePath);
            firstNode = node(vertx, firstState, firstStorage);
            awaitSuccess(firstNode.start(), TIMEOUT);
            awaitSuccess(eventually(vertx, firstNode::isLeader, TIMEOUT), TIMEOUT.plusSeconds(1));
            RaftNode electedNode = firstNode;
            awaitSuccess(eventually(vertx, () -> electedNode.getCommitIndex() >= 1, TIMEOUT),
                    TIMEOUT.plusSeconds(1));

            TransferRequest request = TransferRequest.builder()
                    .requestId(JOB_ID)
                    .sourceUri(URI.create("https://payments.example.test/settlement.dat"))
                    .destinationPath(tempDir.resolve("settlement.dat"))
                    .expectedSize(4096)
                    .metadata("description", "Intraday settlement transfer")
                    .build();
            TransferJob job = new TransferJob(request);

            AgentInfo agent = new AgentInfo(AGENT_ID, "phase0-host", "127.0.0.1", 8081);
            agent.setTenantId(TENANT_ID);
            agent.setStatus(AgentStatus.HEALTHY);

            assertInstanceOf(CommandResult.Success.class,
                    awaitSuccess(firstNode.submitCommand(TransferJobCommand.create(job, TENANT_ID)), TIMEOUT));
            assertInstanceOf(CommandResult.Success.class,
                    awaitSuccess(firstNode.submitCommand(
                            new AgentCommand.Register(AGENT_ID, agent, Instant.now())), TIMEOUT));

            JobAssignment assignment = new JobAssignment.Builder()
                    .jobId(JOB_ID)
                    .agentId(AGENT_ID)
                    .tenantId(TENANT_ID)
                    .build();
            assertInstanceOf(CommandResult.Success.class,
                    awaitSuccess(firstNode.submitCommand(
                            new JobAssignmentCommand.Assign(ASSIGNMENT_ID, assignment, Instant.now())), TIMEOUT));
            assertInstanceOf(CommandResult.Success.class,
                    awaitSuccess(firstNode.submitCommand(
                            new JobAssignmentCommand.Accept(
                                    ASSIGNMENT_ID, JobAssignmentStatus.ACCEPTED, Instant.now())), TIMEOUT));
            assertInstanceOf(CommandResult.Success.class,
                    awaitSuccess(firstNode.submitCommand(
                            new JobAssignmentCommand.UpdateStatus(ASSIGNMENT_ID,
                                    JobAssignmentStatus.ACCEPTED,
                                    JobAssignmentStatus.IN_PROGRESS,
                                    Instant.now())), TIMEOUT));
            assertInstanceOf(CommandResult.Success.class,
                    awaitSuccess(firstNode.submitCommand(TransferJobCommand.updateStatus(
                            JOB_ID, TransferStatus.PENDING, TransferStatus.IN_PROGRESS)), TIMEOUT));
            assertInstanceOf(CommandResult.Success.class,
                    awaitSuccess(firstNode.submitCommand(TransferJobCommand.updateProgress(JOB_ID, 4096)), TIMEOUT));
            assertInstanceOf(CommandResult.Success.class,
                    awaitSuccess(firstNode.submitCommand(TransferJobCommand.updateStatus(
                            JOB_ID, TransferStatus.IN_PROGRESS, TransferStatus.COMPLETED)), TIMEOUT));
            assertInstanceOf(CommandResult.Success.class,
                    awaitSuccess(firstNode.submitCommand(
                            new JobAssignmentCommand.UpdateStatus(ASSIGNMENT_ID,
                                    JobAssignmentStatus.IN_PROGRESS,
                                    JobAssignmentStatus.COMPLETED,
                                    Instant.now())), TIMEOUT));

            awaitSuccess(firstNode.stop(), TIMEOUT);
            firstNode = null;

            QuorusStateStore recoveredState = new QuorusStateStore();
            RaftStorage recoveredStorage = openStorage(vertx, recoveredExecutor, storagePath);
            recoveredNode = node(vertx, recoveredState, recoveredStorage);
            awaitSuccess(recoveredNode.start(), TIMEOUT);

            TransferJobSnapshot recovered = recoveredState.findTransferJob(JOB_ID).orElseThrow();
            assertEquals(TENANT_ID, recovered.getTenantId());
            assertEquals(TransferStatus.COMPLETED, recovered.getStatus());
            assertEquals(4096, recovered.getBytesTransferred());
            assertEquals(JobAssignmentStatus.COMPLETED,
                    recoveredState.findJobAssignment(ASSIGNMENT_ID).orElseThrow().getStatus());
            assertEquals(TENANT_ID, recoveredState.findAgent(AGENT_ID).orElseThrow().getTenantId());

            apiServer = new HttpApiServer(vertx, 0, recoveredNode, recoveredState);
            awaitSuccess(apiServer.start(), TIMEOUT);
            client = WebClient.create(vertx);
            HttpResponse<Buffer> response = awaitSuccess(client
                    .get(apiServer.actualPort(), "localhost", "/api/v1/transfers/" + JOB_ID)
                    .send(), TIMEOUT);

            assertEquals(200, response.statusCode());
            JsonObject body = response.bodyAsJsonObject();
            assertEquals(JOB_ID, body.getString("jobId"));
            assertEquals("COMPLETED", body.getString("status"));
            assertEquals(4096L, body.getLong("bytesTransferred"));
        } finally {
            if (client != null) {
                client.close();
            }
            if (apiServer != null) {
                awaitSuccess(apiServer.stop(), TIMEOUT);
            }
            if (recoveredNode != null) {
                awaitSuccess(recoveredNode.stop(), TIMEOUT);
            }
            if (firstNode != null) {
                awaitSuccess(firstNode.stop(), TIMEOUT);
            }
            awaitSuccess(firstExecutor.close(), TIMEOUT);
            awaitSuccess(recoveredExecutor.close(), TIMEOUT);
        }
    }

    private static RaftStorage openStorage(Vertx vertx, WorkerExecutor executor, Path path) {
        RaftStorage storage = new FileRaftStorage(vertx, executor);
        awaitSuccess(storage.open(path), TIMEOUT);
        return storage;
    }

    private static RaftNode node(Vertx vertx, QuorusStateStore state, RaftStorage storage) {
        return RaftNode.builder()
                .vertx(vertx)
                .nodeId(NODE_ID)
                .clusterNodes(Set.of(NODE_ID))
                .transport(new InMemoryTransportSimulator(NODE_ID))
                .stateMachine(state)
                .mode(RaftNodeMode.durable(storage))
                .electionTimeout(250)
                .heartbeatInterval(50)
                .build();
    }
}
