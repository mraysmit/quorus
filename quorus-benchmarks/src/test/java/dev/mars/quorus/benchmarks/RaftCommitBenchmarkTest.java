/*
 * Copyright 2026 Mark Andrew Ray-Smith Cityline Ltd
 * Licensed under the Apache License, Version 2.0.
 */
package dev.mars.quorus.benchmarks;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.jupiter.api.Assertions.*;

/**
 * The B-09 harness end to end at a tiny size: a real three-node engine over gRPC with mutual TLS and
 * durable storage commits every submitted command, and the result file carries the §13 record.
 */
@Timeout(value = 120, unit = SECONDS, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
class RaftCommitBenchmarkTest {

    @TempDir
    Path directory;

    @Test
    void aSmallRunCommitsEveryCommandAtEachConcurrency() throws Exception {
        RaftCommitBenchmark.Settings settings = new RaftCommitBenchmark.Settings(3, 5, 20, List.of(1, 4), 32,
                true, 1_000, 200);

        RaftCommitBenchmark.Result result = new RaftCommitBenchmark(settings, directory).run();

        assertEquals("B-09", result.benchmark());
        assertEquals(2, result.levels().size());
        for (RaftCommitBenchmark.LevelResult level : result.levels()) {
            assertEquals(0, level.errors(), () -> "every command should commit: " + level);
            assertEquals(20, level.latency().operations());
            assertTrue(level.latency().p50Micros() > 0 && level.latency().p50Micros() <= level.latency().p99Micros());
        }
    }

    @Test
    void theRunnerWritesTheResultWithItsEnvironmentAndInvocation() throws Exception {
        Path file = BenchmarkMain.run(new String[]{"B-09", "--warmup", "2", "--commands", "5", "--concurrency", "2",
                "--election-timeout-ms", "1000", "--heartbeat-ms", "200", "--storage", "temp directory"},
                directory.resolve("results"), Path.of("").toAbsolutePath());

        JsonNode record = new ObjectMapper().readTree(file.toFile());
        assertEquals("B-09", record.get("result").get("benchmark").asText());
        assertEquals("temp directory", record.get("storage").asText());
        assertTrue(record.get("invocation").asText().startsWith("B-09 --warmup 2"));
        assertFalse(record.get("environment").get("javaVersion").asText().isBlank());
        assertTrue(record.get("environment").get("availableProcessors").asInt() > 0);
    }

    @Test
    void optionsAreNamePairsAndUnknownBenchmarksAreRefused() {
        assertThrows(IllegalArgumentException.class, () -> BenchmarkMain.options(new String[]{"--commands"}));
        assertThrows(IllegalArgumentException.class,
                () -> BenchmarkMain.run(new String[]{"B-99"}, directory, directory));
        assertEquals(List.of(1, 10), BenchmarkMain.b09Settings(
                BenchmarkMain.options(new String[]{"--concurrency", "1, 10"})).concurrencyLevels());
    }
}
