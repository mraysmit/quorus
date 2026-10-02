/*
 * Copyright 2026 Mark Andrew Ray-Smith Cityline Ltd
 * Licensed under the Apache License, Version 2.0.
 */
package dev.mars.quorus.benchmarks;

import dev.mars.quorus.controller.raft.GrpcRaftServer;
import dev.mars.quorus.controller.raft.GrpcRaftTransport;
import dev.mars.quorus.controller.raft.RaftNode;
import dev.mars.quorus.controller.raft.RaftNodeMode;
import dev.mars.quorus.controller.raft.RaftTlsConfig;
import dev.mars.quorus.controller.raft.storage.RaftStorage;
import dev.mars.quorus.controller.raft.storage.RaftStorageFactory;
import dev.mars.quorus.controller.security.CertificateTrustState;
import dev.mars.quorus.controller.security.SecurityProfile;
import dev.mars.quorus.controller.state.CommandResult;
import dev.mars.quorus.controller.state.QuorusStateStore;
import dev.mars.quorus.controller.state.SystemMetadataCommand;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Benchmark B-09: Raft command commit latency of the controller's consensus engine.
 *
 * <p>Three durable engine nodes in one JVM talk to each other over real gRPC with TLS 1.3 mutual
 * authentication on loopback, each with its own raftlog WAL (fsync on by default) and the controller's real
 * state machine. The benchmark submits small replicated commands to the leader and measures the time from
 * submission to the leader's commit-and-apply acknowledgement, at each requested client concurrency. Running
 * the engine in one JVM isolates consensus from HTTP and process start-up; the process-level view of the
 * same path is B-10. Leader failover (the second half of B-09 in the specification) is a later slice.
 *
 * <p>The engine measured is whichever implements the controller's consensus today: the in-repository Vert.x
 * engine now, QRaft after `CE-07`. The same workload is the baseline for that comparison.
 */
public final class RaftCommitBenchmark {

    private static final Logger logger = LoggerFactory.getLogger(RaftCommitBenchmark.class);
    private static final Duration STEP_TIMEOUT = Duration.ofSeconds(60);

    /** The workload and engine settings of one run. */
    public record Settings(int nodes, int warmupCommands, int commandsPerLevel, List<Integer> concurrencyLevels,
                           int valueBytes, boolean fsync, long electionTimeoutMs, long heartbeatIntervalMs) {
        public Settings {
            if (nodes < 1 || warmupCommands < 0 || commandsPerLevel < 1 || concurrencyLevels.isEmpty()
                    || valueBytes < 1 || electionTimeoutMs < 1 || heartbeatIntervalMs < 1) {
                throw new IllegalArgumentException("invalid B-09 settings: " + this);
            }
            concurrencyLevels = List.copyOf(concurrencyLevels);
        }

        /** The controller's packaged Raft defaults and a moderate workload. */
        public static Settings defaults() {
            return new Settings(3, 500, 2_000, List.of(1, 10, 50), 128, true, 5_000, 1_000);
        }
    }

    /** One concurrency level's result. */
    public record LevelResult(int concurrency, Latencies.Summary latency, int errors) {
    }

    /** The whole run. */
    public record Result(String benchmark, Settings settings, List<LevelResult> levels) {
    }

    private final Settings settings;
    private final Path workDirectory;

    public RaftCommitBenchmark(Settings settings, Path workDirectory) {
        this.settings = settings;
        this.workDirectory = workDirectory;
    }

    public Result run() throws Exception {
        Vertx vertx = Vertx.vertx();
        Cluster cluster = null;
        try {
            cluster = startCluster(vertx);
            RaftNode leader = awaitLeader(cluster);
            logger.info("B-09: leader is {}; warming up with {} commands", leader.getNodeId(), settings.warmupCommands());
            measure(leader, "warmup", settings.warmupCommands(), 1);
            List<LevelResult> levels = new ArrayList<>();
            for (int concurrency : settings.concurrencyLevels()) {
                levels.add(measure(leader, "c" + concurrency, settings.commandsPerLevel(), concurrency));
                logger.info("B-09: concurrency {} -> {}", concurrency, levels.getLast());
            }
            return new Result("B-09", settings, levels);
        } finally {
            if (cluster != null) {
                cluster.stop();
            }
            await(vertx.close());
        }
    }

    /** Submits {@code commands} commands from {@code concurrency} virtual threads and records each latency. */
    private LevelResult measure(RaftNode leader, String label, int commands, int concurrency) throws Exception {
        Latencies latencies = new Latencies(commands);
        AtomicInteger next = new AtomicInteger();
        AtomicInteger errors = new AtomicInteger();
        String value = "v".repeat(settings.valueBytes());
        List<Thread> clients = new ArrayList<>();
        long started = System.nanoTime();
        for (int c = 0; c < concurrency; c++) {
            clients.add(Thread.ofVirtual().name("b09-client-" + c).start(() -> {
                for (int i = next.getAndIncrement(); i < commands; i = next.getAndIncrement()) {
                    long submitted = System.nanoTime();
                    try {
                        CommandResult<?> result = leader.submitCommand(
                                        new SystemMetadataCommand.Set("bench." + label + "." + i, value))
                                .toCompletionStage().toCompletableFuture()
                                .get(STEP_TIMEOUT.toSeconds(), TimeUnit.SECONDS);
                        if (!(result instanceof CommandResult.Success<?>)) {
                            errors.incrementAndGet();
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return;
                    } catch (Exception e) {
                        errors.incrementAndGet();
                    }
                    latencies.record(System.nanoTime() - submitted);
                }
            }));
        }
        for (Thread client : clients) {
            if (!client.join(STEP_TIMEOUT.multipliedBy(4))) {
                throw new IllegalStateException("B-09 client did not finish: " + client.getName());
            }
        }
        Duration wallTime = Duration.ofNanos(System.nanoTime() - started);
        return new LevelResult(concurrency, latencies.summarize(wallTime), errors.get());
    }

    private Cluster startCluster(Vertx vertx) throws Exception {
        Path tls = Files.createDirectories(workDirectory.resolve("tls"));
        Path certificate = copyResource("/benchmark-tls/node-cert.pem", tls.resolve("node-cert.pem"));
        Path privateKey = copyResource("/benchmark-tls/node-key.pem", tls.resolve("node-key.pem"));
        // Every node presents the same certificate and trusts only it: TLS 1.3 mutual authentication as in
        // production, without per-node identity (node binding is SEC-04 and does not change the data path).
        RaftTlsConfig tlsConfig = new RaftTlsConfig(SecurityProfile.PRODUCTION, true, false,
                certificate, privateKey, certificate);

        Map<String, Integer> ports = new LinkedHashMap<>();
        for (int n = 1; n <= settings.nodes(); n++) {
            ports.put("bench-node-" + n, freePort());
        }
        List<RaftNode> nodes = new ArrayList<>();
        List<GrpcRaftServer> servers = new ArrayList<>();
        List<GrpcRaftTransport> transports = new ArrayList<>();
        CompletableFuture<RaftNode> leader = new CompletableFuture<>();
        for (String nodeId : ports.keySet()) {
            Map<String, String> peers = new HashMap<>();
            ports.forEach((peerId, port) -> {
                if (!peerId.equals(nodeId)) {
                    peers.put(peerId, "localhost:" + port);
                }
            });
            GrpcRaftTransport transport = new GrpcRaftTransport(vertx, nodeId, peers, 10, 1_000, tlsConfig);
            RaftStorage storage = await(RaftStorageFactory.create(vertx, "raftlog",
                    workDirectory.resolve(nodeId).resolve("raft"), settings.fsync()));
            RaftNode node = RaftNode.builder()
                    .vertx(vertx)
                    .nodeId(nodeId)
                    .clusterNodes(Set.copyOf(ports.keySet()))
                    .transport(transport)
                    .stateMachine(new QuorusStateStore())
                    .mode(RaftNodeMode.durable(storage))
                    .electionTimeout(settings.electionTimeoutMs())
                    .heartbeatInterval(settings.heartbeatIntervalMs())
                    .build();
            transport.setRaftNode(node);
            node.addStateChangeListener(state -> {
                if (state == RaftNode.State.LEADER) {
                    leader.complete(node);
                }
            });
            GrpcRaftServer server = new GrpcRaftServer(vertx, ports.get(nodeId), node, tlsConfig,
                    new CertificateTrustState("benchmark", Set.of(), Duration.ofDays(30)));
            await(server.start());
            nodes.add(node);
            servers.add(server);
            transports.add(transport);
        }
        for (RaftNode node : nodes) {
            await(node.start());
        }
        return new Cluster(nodes, servers, transports, leader);
    }

    /** The leader, from the engine's state-change notifications (bounded by six election timeouts). */
    private RaftNode awaitLeader(Cluster cluster) throws Exception {
        try {
            return cluster.leader().get(settings.electionTimeoutMs() * 6, TimeUnit.MILLISECONDS);
        } catch (java.util.concurrent.TimeoutException e) {
            throw new IllegalStateException("no leader was elected", e);
        }
    }

    private record Cluster(List<RaftNode> nodes, List<GrpcRaftServer> servers, List<GrpcRaftTransport> transports,
                           CompletableFuture<RaftNode> leader) {
        void stop() {
            for (RaftNode node : nodes) {
                awaitQuietly(node.stop());
            }
            for (GrpcRaftServer server : servers) {
                awaitQuietly(server.stop());
            }
            for (GrpcRaftTransport transport : transports) {
                awaitQuietly(transport.stop());
            }
        }
    }

    private static Path copyResource(String resource, Path target) throws IOException {
        try (InputStream in = RaftCommitBenchmark.class.getResourceAsStream(resource)) {
            if (in == null) {
                throw new IllegalStateException("missing benchmark resource " + resource);
            }
            Files.copy(in, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
        return target;
    }

    private static int freePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    private static <T> T await(Future<T> future) throws Exception {
        return future.toCompletionStage().toCompletableFuture().get(STEP_TIMEOUT.toSeconds(), TimeUnit.SECONDS);
    }

    private static void awaitQuietly(Future<?> future) {
        try {
            await(future);
        } catch (Exception e) {
            logger.warn("B-09: stopping a component failed: {}", e.getMessage());
        }
    }
}
