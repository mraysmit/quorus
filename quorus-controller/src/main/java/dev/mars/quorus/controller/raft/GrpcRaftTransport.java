package dev.mars.quorus.controller.raft;

import dev.mars.quorus.controller.raft.grpc.RaftProto;
import dev.mars.quorus.controller.raft.grpc.RaftServiceGrpc;
import dev.mars.quorus.controller.raft.grpc.VoteRequest;
import dev.mars.quorus.controller.raft.grpc.VoteResponse;
import dev.mars.quorus.controller.raft.grpc.AppendEntriesRequest;
import dev.mars.quorus.controller.raft.grpc.AppendEntriesResponse;
import dev.mars.quorus.controller.raft.grpc.LogEntry;
import io.grpc.ManagedChannel;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.grpc.client.GrpcClient;
import io.vertx.grpc.client.GrpcClientChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.SocketAddress;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * gRPC implementation of RaftTransport using Vert.x 5 gRPC client.
 */
public class GrpcRaftTransport implements RaftTransport {

    private static final Logger logger = LoggerFactory.getLogger(GrpcRaftTransport.class);

    private final Vertx vertx;
    private final String selfId;
    private final Map<String, SocketAddress> clusterNodes;
    private final Map<String, RaftServiceGrpc.RaftServiceFutureStub> clients = new ConcurrentHashMap<>();
    private final GrpcClient grpcClient;

    private RaftNode raftNode; // Circular dependency injection

    public GrpcRaftTransport(Vertx vertx, String selfId, Map<String, SocketAddress> clusterNodes) {
        this.vertx = vertx;
        this.selfId = selfId;
        this.clusterNodes = clusterNodes;
        this.grpcClient = GrpcClient.client(vertx);
    }

    public void setRaftNode(RaftNode raftNode) {
        this.raftNode = raftNode;
    }

    @Override
    public void start(java.util.function.Consumer<Object> messageHandler) {
        // Server side should be started in the Verticle separately (GrpcRaftServer)
        // Client side just needs to be ready
        logger.info("GrpcRaftTransport initialized for node: {}", selfId);
    }

    @Override
    public void stop() {
        // Clean up clients
        clients.clear();
    }

    @Override
    public java.util.concurrent.CompletableFuture<dev.mars.quorus.controller.raft.VoteResponse> sendVoteRequest(
            String targetId, dev.mars.quorus.controller.raft.VoteRequest request) {

        // Convert domain object to Proto
        VoteRequest protoReq = VoteRequest.newBuilder()
                .setTerm(request.getTerm())
                .setCandidateId(request.getCandidateId())
                .setLastLogIndex(request.getLastLogIndex())
                .setLastLogTerm(request.getLastLogTerm())
                .build();

        return getStub(targetId)
                .requestVote(protoReq)
                .toCompletableFuture() // Bridge to legacy CompletableFuture for now, or refactor RaftNode to use
                                       // Future
                .thenApply(this::fromProto);
    }

    @Override
    public java.util.concurrent.CompletableFuture<dev.mars.quorus.controller.raft.AppendEntriesResponse> sendAppendEntries(
            String targetId, dev.mars.quorus.controller.raft.AppendEntriesRequest request) {

        AppendEntriesRequest.Builder builder = AppendEntriesRequest.newBuilder()
                .setTerm(request.getTerm())
                .setLeaderId(request.getLeaderId())
                .setPrevLogIndex(request.getPrevLogIndex())
                .setPrevLogTerm(request.getPrevLogTerm())
                .setLeaderCommit(request.getLeaderCommit());

        if (request.getEntries() != null) {
            for (dev.mars.quorus.controller.raft.LogEntry entry : request.getEntries()) {
                builder.addEntries(LogEntry.newBuilder()
                        .setTerm(entry.getTerm())
                        .setIndex(entry.getIndex())
                        // .setData(...) // Serialize command payload
                        .build());
            }
        }

        return getStub(targetId)
                .appendEntries(builder.build())
                .toCompletableFuture()
                .thenApply(this::fromProto);
    }

    private RaftServiceGrpc.RaftServiceFutureStub getStub(String targetId) {
        return clients.computeIfAbsent(targetId, id -> {
            SocketAddress addr = clusterNodes.get(id);
            if (addr == null) {
                throw new IllegalArgumentException("Unknown node: " + id);
            }
            // Create a channel using Vert.x gRPC client
            ManagedChannel channel = new GrpcClientChannel(grpcClient, addr);
            return RaftServiceGrpc.newFutureStub(channel);
        });
    }

    // Converters

    private dev.mars.quorus.controller.raft.VoteResponse fromProto(VoteResponse proto) {
        return new dev.mars.quorus.controller.raft.VoteResponse(
                proto.getTerm(),
                proto.getVoteGranted(),
                selfId // Note: The response doesn't carry the sender ID in standard Raft, implied by
                       // connection
        );
    }

    private dev.mars.quorus.controller.raft.AppendEntriesResponse fromProto(AppendEntriesResponse proto) {
        return new dev.mars.quorus.controller.raft.AppendEntriesResponse(
                proto.getTerm(),
                proto.getSuccess(),
                selfId,
                proto.getMatchIndex());
    }
}
