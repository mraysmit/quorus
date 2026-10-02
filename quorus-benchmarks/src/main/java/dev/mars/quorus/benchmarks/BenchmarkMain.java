/*
 * Copyright 2026 Mark Andrew Ray-Smith Cityline Ltd
 * Licensed under the Apache License, Version 2.0.
 */
package dev.mars.quorus.benchmarks;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Runs one benchmark from {@code docs-design/performance/QUORUS_PERFORMANCE_BENCHMARKS.md} and writes its
 * result, with the environment record, as JSON under {@code target/benchmark-results/}.
 *
 * <pre>
 * mvn -B -Pbenchmarks -pl quorus-benchmarks -am install -DskipTests
 * mvn -B -Pbenchmarks -pl quorus-benchmarks exec:java -Dexec.args="B-09 --commands 2000 --concurrency 1,10,50"
 * </pre>
 *
 * Options for B-08: {@code --nodes}, {@code --warmup-seconds}, {@code --duration-seconds},
 * {@code --concurrency}, {@code --scenarios} (submit, heartbeat, poll, read), {@code --fsync},
 * {@code --controller-heap}, {@code --controller-jar} (default: the host-built controller jar).
 *
 * Options for B-09: {@code --nodes}, {@code --warmup}, {@code --commands} (per concurrency level),
 * {@code --concurrency} (comma-separated), {@code --value-bytes}, {@code --fsync}, {@code --election-timeout-ms},
 * {@code --heartbeat-ms}, {@code --storage} and {@code --network} (free-text descriptions for the record).
 */
public final class BenchmarkMain {

    private BenchmarkMain() { }

    public static void main(String[] args) throws Exception {
        Path output = run(args, Path.of("target", "benchmark-results"), repositoryRoot());
        System.out.println("Result written to " + output.toAbsolutePath());
    }

    /** Runs the benchmark named by {@code args[0]} and returns the path of the result file. */
    static Path run(String[] args, Path outputDirectory, Path repository) throws Exception {
        if (args.length == 0) {
            throw new IllegalArgumentException("usage: <benchmark-id> [--option value ...], e.g. B-09 --commands 2000");
        }
        Map<String, String> options = options(Arrays.copyOfRange(args, 1, args.length));
        BenchmarkEnvironment environment = BenchmarkEnvironment.capture(repository);
        Object result = switch (args[0]) {
            case "B-08" -> {
                Path work = Files.createTempDirectory("quorus-b08-");
                Path jar = options.containsKey("controller-jar") ? Path.of(options.get("controller-jar"))
                        : ControllerApiBenchmark.defaultControllerJar(repository);
                yield new ControllerApiBenchmark(b08Settings(options), work, jar).run();
            }
            case "B-09" -> {
                Path work = Files.createTempDirectory("quorus-b09-");
                yield new RaftCommitBenchmark(b09Settings(options), work).run();
            }
            default -> throw new IllegalArgumentException("unknown or not yet implemented benchmark: " + args[0]);
        };
        Map<String, Object> record = new LinkedHashMap<>();
        record.put("environment", environment);
        record.put("storage", options.getOrDefault("storage", "not described"));
        record.put("network", options.getOrDefault("network", "loopback"));
        record.put("invocation", String.join(" ", args));
        record.put("result", result);
        Files.createDirectories(outputDirectory);
        Path file = outputDirectory.resolve(args[0] + "-" + Instant.now().toString().replace(':', '-') + ".json");
        new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT).writeValue(file.toFile(), record);
        return file;
    }

    static ControllerApiBenchmark.Settings b08Settings(Map<String, String> options) {
        ControllerApiBenchmark.Settings defaults = ControllerApiBenchmark.Settings.defaults();
        return new ControllerApiBenchmark.Settings(
                integer(options, "nodes", defaults.nodes()),
                integer(options, "warmup-seconds", defaults.warmupSeconds()),
                integer(options, "duration-seconds", defaults.durationSeconds()),
                options.containsKey("concurrency") ? integers(options.get("concurrency")) : defaults.concurrencyLevels(),
                options.containsKey("scenarios")
                        ? Arrays.stream(options.get("scenarios").split(",")).map(String::trim).toList()
                        : defaults.scenarios(),
                Boolean.parseBoolean(options.getOrDefault("fsync", String.valueOf(defaults.fsync()))),
                options.getOrDefault("controller-heap", defaults.controllerHeap()));
    }

    private static List<Integer> integers(String csv) {
        return Arrays.stream(csv.split(",")).map(String::trim).map(Integer::valueOf).toList();
    }

    static RaftCommitBenchmark.Settings b09Settings(Map<String, String> options) {
        RaftCommitBenchmark.Settings defaults = RaftCommitBenchmark.Settings.defaults();
        return new RaftCommitBenchmark.Settings(
                integer(options, "nodes", defaults.nodes()),
                integer(options, "warmup", defaults.warmupCommands()),
                integer(options, "commands", defaults.commandsPerLevel()),
                options.containsKey("concurrency")
                        ? Arrays.stream(options.get("concurrency").split(",")).map(String::trim).map(Integer::valueOf).toList()
                        : defaults.concurrencyLevels(),
                integer(options, "value-bytes", defaults.valueBytes()),
                Boolean.parseBoolean(options.getOrDefault("fsync", String.valueOf(defaults.fsync()))),
                integer(options, "election-timeout-ms", (int) defaults.electionTimeoutMs()),
                integer(options, "heartbeat-ms", (int) defaults.heartbeatIntervalMs()));
    }

    static Map<String, String> options(String[] args) {
        Map<String, String> options = new LinkedHashMap<>();
        for (int i = 0; i < args.length; i += 2) {
            if (!args[i].startsWith("--") || i + 1 >= args.length) {
                throw new IllegalArgumentException("options are --name value pairs: " + List.of(args));
            }
            options.put(args[i].substring(2), args[i + 1]);
        }
        return options;
    }

    private static int integer(Map<String, String> options, String name, int fallback) {
        return options.containsKey(name) ? Integer.parseInt(options.get(name)) : fallback;
    }

    /** The git working tree containing the current directory, or the current directory. */
    private static Path repositoryRoot() {
        Path directory = Path.of("").toAbsolutePath();
        for (Path candidate = directory; candidate != null; candidate = candidate.getParent()) {
            if (Files.exists(candidate.resolve(".git"))) {
                return candidate;
            }
        }
        return directory;
    }
}
