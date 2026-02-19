package dev.mars.quorus.controller.integration;

import dev.mars.quorus.controller.raft.RaftMessage;
import dev.mars.quorus.controller.raft.RaftNode;
import dev.mars.quorus.controller.raft.RaftStateMachine;
import dev.mars.quorus.controller.raft.RaftTransport;
import dev.mars.quorus.controller.state.StateMachineCommand;
import dev.mars.quorus.controller.raft.grpc.AppendEntriesRequest;
import dev.mars.quorus.controller.raft.grpc.AppendEntriesResponse;
import dev.mars.quorus.controller.raft.grpc.InstallSnapshotRequest;
import dev.mars.quorus.controller.raft.grpc.InstallSnapshotResponse;
import dev.mars.quorus.controller.raft.grpc.VoteRequest;
import dev.mars.quorus.controller.raft.grpc.VoteResponse;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.Set;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ExtendWith(VertxExtension.class)
public class RaftNodeIntegrationTest {

    private Vertx vertx;

    @BeforeAll
    void setUp(Vertx vertx) {
        this.vertx = vertx;
    }

    @Test
    void testRaftNodeStartStop(VertxTestContext testContext) {
        TestRaftTransport transport = new TestRaftTransport();
        TestRaftStateMachine stateMachine = new TestRaftStateMachine();
        RaftNode node = new RaftNode(vertx, "node1", Set.of("node1"), transport, stateMachine);

        node.start()
            .onComplete(testContext.succeeding(v -> {
                assertTrue(node.isRunning(), "Node should be running after start()");

                // Wait a bit to let timers fire (if any)
                vertx.setTimer(500, id -> {
                    node.stop()
                        .onComplete(testContext.succeeding(v2 -> {
                            assertFalse(node.isRunning(), "Node should not be running after stop()");
                            testContext.completeNow();
                        }));
                });
            }));
    }

    @Test
    void testTimerCleanup(VertxTestContext testContext) {
        TestRaftTransport transport = new TestRaftTransport();
        TestRaftStateMachine stateMachine = new TestRaftStateMachine();
        RaftNode node = new RaftNode(vertx, "node1", Set.of("node1"), transport, stateMachine);

        node.start()
            .onComplete(testContext.succeeding(v -> {
                assertTrue(node.isRunning());

                // Stop immediately
                node.stop()
                    .onComplete(testContext.succeeding(v2 -> {
                        assertFalse(node.isRunning());
                        
                        // Wait to ensure no late timer events cause issues (though hard to assert absence of events without spying)
                        vertx.setTimer(200, id -> {
                            testContext.completeNow();
                        });
                    }));
            }));
    }

    // Simple Test Implementations

    static class TestRaftTransport implements RaftTransport {
        @Override
        public void start(Consumer<RaftMessage> messageHandler) {
        }

        @Override
        public void stop() {
        }

        @Override
        public Future<VoteResponse> sendVoteRequest(String targetId, VoteRequest request) {
            return Future.succeededFuture(VoteResponse.newBuilder().setTerm(1).setVoteGranted(true).build());
        }

        @Override
        public Future<AppendEntriesResponse> sendAppendEntries(String targetId, AppendEntriesRequest request) {
            return Future.succeededFuture(AppendEntriesResponse.newBuilder().setTerm(1).setSuccess(true).build());
        }

        @Override
        public Future<InstallSnapshotResponse> sendInstallSnapshot(String targetId, InstallSnapshotRequest request) {
            return Future.succeededFuture(InstallSnapshotResponse.newBuilder()
                    .setTerm(1).setSuccess(true).setNextChunkIndex(request.getChunkIndex() + 1).build());
        }
    }

    static class TestRaftStateMachine implements RaftStateMachine {
        @Override
        public Object apply(StateMachineCommand command) {
            return null;
        }

        @Override
        public byte[] takeSnapshot() {
            return new byte[0];
        }

        @Override
        public void restoreSnapshot(byte[] snapshot) {
        }

        @Override
        public long getLastAppliedIndex() {
            return 0;
        }

        @Override
        public void setLastAppliedIndex(long index) {
        }

        @Override
        public void reset() {
        }
    }
}
