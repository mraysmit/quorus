/*
 * Copyright 2026 Mark Andrew Ray-Smith Cityline Ltd
 * Licensed under the Apache License, Version 2.0.
 */
package dev.mars.quorus.benchmarks;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.mars.quorus.security.PemTls;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLParameters;
import java.io.IOException;
import java.io.InputStream;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Benchmark B-08: controller HTTP API throughput and latency, with real controller processes.
 *
 * <p>The harness starts {@code nodes} controller processes from the host-built controller jar, each with
 * HTTP and Raft over TLS 1.3 with required client certificates, request authentication, authorization and
 * audit, and a durable raftlog WAL. It uses the controller's {@code development} security profile: every
 * control is on, but transfers may be submitted without a governed service connection, so the measured path
 * is the HTTP stack and the Raft write, not service-connection and DNS policy. Clients authenticate as a
 * trusted gateway and assert an operator or agent identity in the gateway headers.
 *
 * <p>Scenarios, each run against the leader at every concurrency level for a fixed duration, closed loop:
 * <ul>
 *   <li>{@code submit}: {@code POST /api/v1/transfers} (a Raft write);</li>
 *   <li>{@code heartbeat}: {@code POST /api/v1/agents/heartbeat} from a registered agent (a Raft write);</li>
 *   <li>{@code poll}: {@code GET /api/v1/agents/{id}/jobs} (a local read);</li>
 *   <li>{@code read}: {@code GET /api/v1/transfers/{jobId}} (a local read).</li>
 * </ul>
 * Agent status reports need the assignment and attempt lifecycle first and are a later slice.
 */
public final class ControllerApiBenchmark {

    private static final Logger logger = LoggerFactory.getLogger(ControllerApiBenchmark.class);
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String TENANT = "bench-tenant";
    private static final String ENVIRONMENT = "benchmark";
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);
    private static final Duration START_TIMEOUT = Duration.ofSeconds(120);
    private static final int READ_JOBS = 100;

    /** The workload and cluster settings of one run. */
    public record Settings(int nodes, int warmupSeconds, int durationSeconds, List<Integer> concurrencyLevels,
                           List<String> scenarios, boolean fsync, String controllerHeap) {
        public Settings {
            if (nodes < 1 || warmupSeconds < 0 || durationSeconds < 1 || concurrencyLevels.isEmpty()
                    || scenarios.isEmpty() || !List.of("submit", "heartbeat", "poll", "read").containsAll(scenarios)) {
                throw new IllegalArgumentException("invalid B-08 settings: " + this);
            }
            concurrencyLevels = List.copyOf(concurrencyLevels);
            scenarios = List.copyOf(scenarios);
        }

        public static Settings defaults() {
            return new Settings(3, 5, 30, List.of(10, 100, 500), List.of("submit", "heartbeat", "poll", "read"),
                    true, "1g");
        }
    }

    /** One scenario at one concurrency level. */
    public record LevelResult(String scenario, int concurrency, Latencies.Summary latency, int errors,
                              Map<String, Integer> statuses) {
    }

    /** The whole run. */
    public record Result(String benchmark, Settings settings, String controllerJar, List<LevelResult> levels) {
    }

    private final Settings settings;
    private final Path workDirectory;
    private final Path controllerJar;
    private final AtomicLong sequence = new AtomicLong();

    public ControllerApiBenchmark(Settings settings, Path workDirectory, Path controllerJar) {
        this.settings = settings;
        this.workDirectory = workDirectory;
        this.controllerJar = controllerJar;
    }

    public Result run() throws Exception {
        Path tls = Files.createDirectories(workDirectory.resolve("tls"));
        Path nodeCert = copyResource("/benchmark-tls/node-cert.pem", tls.resolve("node-cert.pem"));
        Path nodeKey = copyResource("/benchmark-tls/node-key.pem", tls.resolve("node-key.pem"));
        Path gatewayCert = copyResource("/benchmark-tls/gateway-cert.pem", tls.resolve("gateway-cert.pem"));
        Path gatewayKey = copyResource("/benchmark-tls/gateway-key.pem", tls.resolve("gateway-key.pem"));
        Path logback = copyResource("/benchmark-controller-logback.xml", workDirectory.resolve("controller-logback.xml"));

        List<Controller> controllers = startControllers(nodeCert, nodeKey, gatewayCert, logback);
        try (HttpClient client = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(10))
                .sslContext(PemTls.sslContext(gatewayCert, gatewayKey, nodeCert))
                .sslParameters(tls13())
                .build()) {
            for (Controller controller : controllers) {
                awaitLive(client, controller);
            }
            Controller leader = awaitLeader(client, controllers);
            logger.info("B-08: leader is {} on port {}", leader.nodeId(), leader.httpPort());

            int agents = settings.concurrencyLevels().stream().max(Integer::compare).orElseThrow();
            prepare(client, leader, agents);

            List<LevelResult> levels = new ArrayList<>();
            for (String scenario : settings.scenarios()) {
                for (int concurrency : settings.concurrencyLevels()) {
                    if (settings.warmupSeconds() > 0) {
                        measure(client, leader, scenario, concurrency, Duration.ofSeconds(settings.warmupSeconds()));
                    }
                    LevelResult level = measure(client, leader, scenario, concurrency,
                            Duration.ofSeconds(settings.durationSeconds()));
                    levels.add(level);
                    logger.info("B-08: {}", level);
                }
            }
            return new Result("B-08", settings, controllerJar.toString(), levels);
        } finally {
            for (Controller controller : controllers) {
                controller.stop();
            }
        }
    }

    // ---------------------------------------------------------------- workload

    private void prepare(HttpClient client, Controller leader, int agents) throws Exception {
        for (int i = 0; i < agents; i++) {
            expect(201, send(client, register(leader, i)), "register agent " + i);
        }
        for (int i = 0; i < READ_JOBS; i++) {
            expect(201, send(client, submit(leader, "bench-read-" + i)), "create read job " + i);
        }
        logger.info("B-08: registered {} agents and created {} jobs to read", agents, READ_JOBS);
    }

    private LevelResult measure(HttpClient client, Controller leader, String scenario, int concurrency,
                                Duration duration) throws Exception {
        int capacity = Math.max(1_000, (int) Math.min(20_000_000L, 5_000L * concurrency * Math.max(1, duration.toSeconds())));
        Latencies latencies = new Latencies(capacity);
        Map<Integer, AtomicInteger> statuses = new ConcurrentHashMap<>();
        AtomicInteger errors = new AtomicInteger();
        AtomicBoolean stop = new AtomicBoolean();
        List<Thread> clients = new ArrayList<>();
        long started = System.nanoTime();
        for (int c = 0; c < concurrency; c++) {
            int agent = c;
            clients.add(Thread.ofVirtual().name("b08-" + scenario + "-" + c).start(() -> {
                for (int n = 0; !stop.get(); n++) {
                    long submitted = System.nanoTime();
                    int status;
                    try {
                        status = send(client, request(leader, scenario, agent, n)).statusCode();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return;
                    } catch (Exception e) {
                        status = -1;
                    }
                    long elapsed = System.nanoTime() - submitted;
                    if (stop.get()) {
                        return;
                    }
                    statuses.computeIfAbsent(status, s -> new AtomicInteger()).incrementAndGet();
                    if (status < 200 || status >= 300) {
                        errors.incrementAndGet();
                    }
                    latencies.record(elapsed);
                }
            }));
        }
        Thread.sleep(duration);                      // the measurement window, not a synchronisation
        stop.set(true);
        Duration wallTime = Duration.ofNanos(System.nanoTime() - started);
        for (Thread thread : clients) {
            if (!thread.join(REQUEST_TIMEOUT.plusSeconds(5))) {
                logger.warn("B-08: client {} did not finish its last request", thread.getName());
            }
        }
        Map<String, Integer> statusCounts = new TreeMap<>();
        statuses.forEach((status, count) -> statusCounts.put(status == -1 ? "transport-error" : status.toString(), count.get()));
        return new LevelResult(scenario, concurrency, latencies.summarize(wallTime), errors.get(), statusCounts);
    }

    private HttpRequest request(Controller leader, String scenario, int agent, int n) throws IOException {
        return switch (scenario) {
            case "submit" -> submit(leader, "bench-" + agent + "-" + n + "-" + System.nanoTime());
            case "heartbeat" -> heartbeat(leader, agent);
            case "poll" -> agentRequest(leader, agent, "/api/v1/agents/" + agentId(agent) + "/jobs").GET().build();
            case "read" -> operatorRequest(leader, "/api/v1/transfers/bench-read-" + (n % READ_JOBS)).GET().build();
            default -> throw new IllegalArgumentException(scenario);
        };
    }

    private HttpRequest submit(Controller leader, String jobId) throws IOException {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("jobId", jobId);
        body.put("sourceUri", "https://files.example.test/" + jobId + ".dat");
        body.put("destinationPath", "/tmp/" + jobId + ".dat");
        body.put("totalBytes", 8_192L);
        body.put("tenantId", TENANT);
        return operatorRequest(leader, "/api/v1/transfers")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(body))).build();
    }

    private HttpRequest register(Controller leader, int agent) throws IOException {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("agentId", agentId(agent));
        body.put("tenantId", TENANT);
        body.put("hostname", "bench-host-" + agent);
        body.put("address", "127.0.0.1");
        body.put("port", 9_000 + agent);
        body.put("version", "benchmark");
        body.put("region", "local");
        body.put("datacenter", "local");
        body.put("capabilities", Map.of("supportedProtocols", List.of("HTTP"), "maxConcurrentTransfers", 4,
                "maxTransferSize", 1_073_741_824L));
        return agentRequest(leader, agent, "/api/v1/agents/register")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(body))).build();
    }

    private HttpRequest heartbeat(Controller leader, int agent) throws IOException {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("agentId", agentId(agent));
        body.put("tenantId", TENANT);
        body.put("timestamp", Instant.now().toString());
        body.put("sequenceNumber", sequence.incrementAndGet());
        body.put("status", "active");
        body.put("currentJobs", 0);
        body.put("availableCapacity", 4);
        return agentRequest(leader, agent, "/api/v1/agents/heartbeat")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(body))).build();
    }

    private HttpRequest.Builder operatorRequest(Controller leader, String path) {
        return identity(base(leader, path), "bench-operator", "HUMAN", "OPERATOR");
    }

    private HttpRequest.Builder agentRequest(Controller leader, int agent, String path) {
        return identity(base(leader, path), agentId(agent), "AGENT", "AGENT");
    }

    private static HttpRequest.Builder adminRequest(Controller controller, String path) {
        return identity(base(controller, path), "bench-admin", "HUMAN", "ADMINISTRATOR");
    }

    private static HttpRequest.Builder base(Controller controller, String path) {
        return HttpRequest.newBuilder(URI.create("https://localhost:" + controller.httpPort() + path))
                .timeout(REQUEST_TIMEOUT);
    }

    /** The trusted-gateway identity assertion (AuthenticationHandler's headers). */
    private static HttpRequest.Builder identity(HttpRequest.Builder request, String principal, String type, String role) {
        return request.header("X-Quorus-Principal", principal)
                .header("X-Quorus-Identity-Type", type)
                .header("X-Quorus-Tenant", TENANT)
                .header("X-Quorus-Environment", ENVIRONMENT)
                .header("X-Quorus-Roles", role)
                .header("X-Quorus-Expires-At", Instant.now().plus(Duration.ofDays(1)).toString());
    }

    private static String agentId(int agent) {
        return "bench-agent-" + agent;
    }

    // ---------------------------------------------------------------- controllers

    private record Controller(String nodeId, int httpPort, Process process, Path log) {
        void stop() {
            // Only the process this harness started, by its own handle.
            process.destroy();
            try {
                if (!process.waitFor(30, java.util.concurrent.TimeUnit.SECONDS)) {
                    process.destroyForcibly();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                process.destroyForcibly();
            }
        }
    }

    private List<Controller> startControllers(Path nodeCert, Path nodeKey, Path gatewayCert, Path logback)
            throws IOException {
        Map<String, int[]> ports = new LinkedHashMap<>();
        for (int n = 1; n <= settings.nodes(); n++) {
            ports.put("bench-controller-" + n, new int[]{freePort(), freePort()});
        }
        StringBuilder clusterNodes = new StringBuilder();
        ports.forEach((id, p) -> clusterNodes.append(clusterNodes.isEmpty() ? "" : ",")
                .append(id).append("=localhost:").append(p[1]));
        String java = ProcessHandle.current().info().command().orElse("java");
        List<Controller> controllers = new ArrayList<>();
        for (Map.Entry<String, int[]> entry : ports.entrySet()) {
            String nodeId = entry.getKey();
            Path data = Files.createDirectories(workDirectory.resolve(nodeId));
            Map<String, String> environment = new LinkedHashMap<>();
            environment.put("QUORUS_NODE_ID", nodeId);
            environment.put("QUORUS_HTTP_HOST", "127.0.0.1");
            environment.put("QUORUS_HTTP_PORT", String.valueOf(entry.getValue()[0]));
            environment.put("QUORUS_RAFT_PORT", String.valueOf(entry.getValue()[1]));
            environment.put("QUORUS_CLUSTER_NODES", clusterNodes.toString());
            environment.put("QUORUS_RAFT_STORAGE_PATH", data.resolve("raft").toString());
            environment.put("QUORUS_RAFT_STORAGE_FSYNC", String.valueOf(settings.fsync()));
            environment.put("QUORUS_SECURITY_PROFILE", "development");
            environment.put("QUORUS_SECURITY_ENABLED", "true");
            environment.put("QUORUS_SECURITY_ALLOW_INSECURE", "false");
            environment.put("QUORUS_SECURITY_HTTP_TLS_ENABLED", "true");
            environment.put("QUORUS_SECURITY_HTTP_TLS_CERTIFICATE", nodeCert.toString());
            environment.put("QUORUS_SECURITY_HTTP_TLS_PRIVATE_KEY", nodeKey.toString());
            environment.put("QUORUS_SECURITY_HTTP_TLS_TRUST_BUNDLE", gatewayCert.toString());
            environment.put("QUORUS_SECURITY_RAFT_TLS_ENABLED", "true");
            environment.put("QUORUS_SECURITY_RAFT_TLS_CERTIFICATE", nodeCert.toString());
            environment.put("QUORUS_SECURITY_RAFT_TLS_PRIVATE_KEY", nodeKey.toString());
            environment.put("QUORUS_SECURITY_RAFT_TLS_TRUST_BUNDLE", nodeCert.toString());
            environment.put("QUORUS_SECURITY_TRUSTED_GATEWAY_SUBJECTS", "CN=quorus-client");
            environment.put("QUORUS_SECURITY_TRUST_BUNDLE_VERSION", "benchmark-1");
            environment.put("QUORUS_SECURITY_AUDIT_PATH", data.resolve("audit/security-audit.jsonl").toString());
            environment.put("QUORUS_SECURITY_AUDIT_EVIDENCE_PATH",
                    data.resolve("audit/security-audit-evidence.jsonl").toString());
            environment.put("QUORUS_TELEMETRY_ENABLED", "false");
            Path log = data.resolve("controller.log");
            ProcessBuilder builder = new ProcessBuilder(java, "-Xmx" + settings.controllerHeap(),
                    "-Dlogback.configurationFile=" + logback, "-jar", controllerJar.toString())
                    .redirectErrorStream(true)
                    .redirectOutput(log.toFile());
            builder.environment().putAll(environment);
            controllers.add(new Controller(nodeId, entry.getValue()[0], builder.start(), log));
        }
        return controllers;
    }

    /** Waits for the process to serve its liveness probe; a process start-up wait, bounded. */
    private static void awaitLive(HttpClient client, Controller controller) throws Exception {
        long deadline = System.nanoTime() + START_TIMEOUT.toNanos();
        while (System.nanoTime() < deadline) {
            if (!controller.process().isAlive()) {
                throw new IllegalStateException("controller " + controller.nodeId() + " exited; see " + controller.log());
            }
            try {
                if (send(client, base(controller, "/health/live").GET().build()).statusCode() == 200) {
                    return;
                }
            } catch (IOException notYet) {
                // Not listening yet.
            }
            Thread.sleep(200);
        }
        throw new IllegalStateException("controller " + controller.nodeId() + " did not start; see " + controller.log());
    }

    private static Controller awaitLeader(HttpClient client, List<Controller> controllers) throws Exception {
        long deadline = System.nanoTime() + START_TIMEOUT.toNanos();
        while (System.nanoTime() < deadline) {
            for (Controller controller : controllers) {
                HttpResponse<String> response = send(client, adminRequest(controller, "/raft/status").GET().build());
                if (response.statusCode() == 200 && JSON.readTree(response.body()).path("isLeader").asBoolean()) {
                    return controller;
                }
            }
            Thread.sleep(200);
        }
        throw new IllegalStateException("no controller became leader");
    }

    // ---------------------------------------------------------------- helpers

    private static HttpResponse<String> send(HttpClient client, HttpRequest request)
            throws IOException, InterruptedException {
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private static void expect(int status, HttpResponse<String> response, String what) {
        if (response.statusCode() != status) {
            throw new IllegalStateException(what + " returned " + response.statusCode() + ": " + response.body());
        }
    }

    private static SSLParameters tls13() {
        SSLParameters parameters = new SSLParameters();
        parameters.setProtocols(new String[]{"TLSv1.3"});
        parameters.setEndpointIdentificationAlgorithm("HTTPS");
        return parameters;
    }

    private static Path copyResource(String resource, Path target) throws IOException {
        try (InputStream in = ControllerApiBenchmark.class.getResourceAsStream(resource)) {
            if (in == null) {
                throw new IllegalStateException("missing benchmark resource " + resource);
            }
            Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
        }
        return target;
    }

    private static int freePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    /** The host-built controller jar: the one on this classpath, or the reactor build output. */
    public static Path defaultControllerJar(Path repository) {
        try {
            Path onClasspath = Path.of(Class.forName("dev.mars.quorus.controller.QuorusControllerApplication")
                    .getProtectionDomain().getCodeSource().getLocation().toURI());
            if (Files.isRegularFile(onClasspath) && onClasspath.toString().endsWith(".jar")) {
                return onClasspath;
            }
        } catch (Exception ignored) {
            // Fall back to the build output.
        }
        try (var jars = Files.list(repository.resolve("quorus-controller/target"))) {
            return jars.filter(p -> p.getFileName().toString().matches("quorus-controller-.*\\.jar"))
                    .filter(p -> !p.getFileName().toString().contains("original"))
                    .findFirst().orElseThrow(() -> new IllegalStateException(
                            "no host-built controller jar; run mvn -pl quorus-controller -am package first"));
        } catch (IOException e) {
            throw new IllegalStateException("cannot find the controller jar", e);
        }
    }

}
