/*
 * Copyright 2025 Mark Andrew Ray-Smith Cityline Ltd
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */
package dev.mars.quorus.controller.raft;

import dev.mars.quorus.controller.raft.storage.RaftStorage;
import dev.mars.quorus.controller.raft.storage.file.FileRaftStorage;
import dev.mars.quorus.controller.state.CommandResult;
import dev.mars.quorus.controller.state.QuorusStateStore;
import dev.mars.quorus.controller.state.TransferJobCommand;
import dev.mars.quorus.core.TransferJob;
import dev.mars.quorus.core.TransferRequest;
import dev.mars.quorus.core.TransferStatus;
import io.vertx.core.Vertx;
import io.vertx.core.WorkerExecutor;
import io.vertx.junit5.VertxExtension;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;

import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static dev.mars.quorus.testing.TestFutureUtils.awaitSuccess;
import static dev.mars.quorus.testing.TestFutureUtils.eventually;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

@ExtendWith(VertxExtension.class)
@DisplayName("Three-controller durable restart")
class ThreeControllerDurableRestartTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(15);
    private static final Set<String> NODE_IDS = Set.of("phase0-a", "phase0-b", "phase0-c");
    private static final String JOB_ID = "phase0-three-controller-transfer";
    private static final String TENANT_ID = "regulated-bank-a";

    @TempDir
    Path tempDir;

    @AfterEach
    void clearNetwork() {
        InMemoryTransportSimulator.clearAllTransports();
    }

    @Test
    @DisplayName("All controllers recover the same committed transfer after a full-cluster restart")
    void fullClusterRestartRecoversCommittedAuthoritativeState(Vertx vertx) {
        Cluster first = startCluster(vertx, "first");
        Cluster recovered = null;
        try {
            RaftNode leader = awaitLeader(vertx, first.nodes());
            awaitSuccess(eventually(vertx, () -> leader.getCommitIndex() >= 1, TIMEOUT), TIMEOUT.plusSeconds(1));

            TransferRequest request = TransferRequest.builder()
                    .requestId(JOB_ID)
                    .sourceUri(URI.create("https://payments.example.test/closing-balance.dat"))
                    .destinationPath(tempDir.resolve("closing-balance.dat"))
                    .expectedSize(8192)
                    .build();
            assertInstanceOf(CommandResult.Success.class,
                    awaitSuccess(leader.submitCommand(
                            TransferJobCommand.create(new TransferJob(request), TENANT_ID)), TIMEOUT));

            Cluster initialCluster = first;
            awaitSuccess(eventually(vertx, () -> initialCluster.states().stream().allMatch(state ->
                    state.findTransferJob(JOB_ID)
                            .map(job -> TENANT_ID.equals(job.getTenantId()) && job.getStatus() == TransferStatus.PENDING)
                            .orElse(false)), TIMEOUT), TIMEOUT.plusSeconds(1));
            long committedIndex = leader.getCommitIndex();
            int durableLogSize = leader.getLogSize();
            awaitSuccess(eventually(vertx, () -> initialCluster.nodes().stream().allMatch(node ->
                    node.getCommitIndex() >= committedIndex && node.getLogSize() == durableLogSize), TIMEOUT),
                    TIMEOUT.plusSeconds(1));

            stopCluster(first);
            first = null;
            InMemoryTransportSimulator.clearAllTransports();

            recovered = startCluster(vertx, "recovered");
            awaitLeader(vertx, recovered.nodes());
            Cluster recoveredCluster = recovered;
            awaitSuccess(eventually(vertx, () -> recoveredCluster.states().stream().allMatch(state ->
                    state.findTransferJob(JOB_ID).isPresent()), TIMEOUT), TIMEOUT.plusSeconds(1));

            for (QuorusStateStore state : recovered.states()) {
                var transfer = state.findTransferJob(JOB_ID).orElseThrow();
                assertEquals(TENANT_ID, transfer.getTenantId());
                assertEquals(TransferStatus.PENDING, transfer.getStatus());
                assertEquals(8192, transfer.getTotalBytes());
            }
        } finally {
            if (recovered != null) {
                stopCluster(recovered);
            }
            if (first != null) {
                stopCluster(first);
            }
        }
    }

    private Cluster startCluster(Vertx vertx, String generation) {
        List<RaftNode> nodes = new ArrayList<>();
        List<QuorusStateStore> states = new ArrayList<>();
        List<WorkerExecutor> executors = new ArrayList<>();
        int nodePosition = 0;
        for (String nodeId : NODE_IDS.stream().sorted().toList()) {
            WorkerExecutor executor = vertx.createSharedWorkerExecutor(
                    "phase0-three-controller-" + generation + "-" + nodeId, 1);
            executors.add(executor);
            RaftStorage storage = new FileRaftStorage(vertx, executor);
            awaitSuccess(storage.open(tempDir.resolve(nodeId).resolve("raft")), TIMEOUT);
            QuorusStateStore state = new QuorusStateStore();
            states.add(state);
            RaftNode node = RaftNode.builder()
                    .vertx(vertx)
                    .nodeId(nodeId)
                    .clusterNodes(NODE_IDS)
                    .transport(new InMemoryTransportSimulator(nodeId))
                    .stateMachine(state)
                    .mode(RaftNodeMode.durable(storage))
                    .electionTimeout(switch (nodePosition) {
                        case 0 -> 250;
                        case 1 -> 3_000;
                        default -> 5_000;
                    })
                    .heartbeatInterval(75)
                    .build();
            nodes.add(node);
            nodePosition++;
        }
        nodes.forEach(node -> awaitSuccess(node.start(), TIMEOUT));
        return new Cluster(nodes, states, executors);
    }

    private static RaftNode awaitLeader(Vertx vertx, List<RaftNode> nodes) {
        awaitSuccess(eventually(vertx, () -> nodes.stream().filter(RaftNode::isLeader).count() == 1, TIMEOUT),
                TIMEOUT.plusSeconds(1));
        return nodes.stream().filter(RaftNode::isLeader).findFirst().orElseThrow();
    }

    private static void stopCluster(Cluster cluster) {
        cluster.nodes().forEach(node -> awaitSuccess(node.stop(), TIMEOUT));
        cluster.executors().forEach(executor -> awaitSuccess(executor.close(), TIMEOUT));
    }

    private record Cluster(List<RaftNode> nodes,
                           List<QuorusStateStore> states,
                           List<WorkerExecutor> executors) {
    }
}
