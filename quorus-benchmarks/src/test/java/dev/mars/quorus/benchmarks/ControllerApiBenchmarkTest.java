/*
 * Copyright 2026 Mark Andrew Ray-Smith Cityline Ltd
 * Licensed under the Apache License, Version 2.0.
 */
package dev.mars.quorus.benchmarks;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.jupiter.api.Assertions.*;

/**
 * The B-08 harness end to end at a tiny size: a real controller process from the host-built jar, with TLS
 * 1.3 and required client certificates, serves every scenario without an error.
 */
@Timeout(value = 240, unit = SECONDS, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
class ControllerApiBenchmarkTest {

    @TempDir
    Path directory;

    @Test
    void everyScenarioSucceedsAgainstARealController() throws Exception {
        ControllerApiBenchmark.Settings settings = new ControllerApiBenchmark.Settings(1, 0, 1, List.of(2),
                List.of("submit", "heartbeat", "poll", "read"), true, "512m");
        Path jar = ControllerApiBenchmark.defaultControllerJar(Path.of("").toAbsolutePath().getParent());

        ControllerApiBenchmark.Result result = new ControllerApiBenchmark(settings, directory, jar).run();

        assertEquals("B-08", result.benchmark());
        assertEquals(4, result.levels().size());
        for (ControllerApiBenchmark.LevelResult level : result.levels()) {
            assertEquals(0, level.errors(), () -> "every request should succeed: " + level);
            assertTrue(level.latency().operations() > 0, () -> "the scenario made requests: " + level);
        }
        assertTrue(ProcessHandle.current().children().noneMatch(ProcessHandle::isAlive),
                "the harness stops every controller it started");
    }

    @Test
    void b08OptionsMapToSettings() {
        ControllerApiBenchmark.Settings settings = BenchmarkMain.b08Settings(BenchmarkMain.options(new String[]{
                "--nodes", "1", "--duration-seconds", "3", "--concurrency", "1,5", "--scenarios", "poll,read"}));

        assertEquals(1, settings.nodes());
        assertEquals(3, settings.durationSeconds());
        assertEquals(List.of(1, 5), settings.concurrencyLevels());
        assertEquals(List.of("poll", "read"), settings.scenarios());
        assertThrows(IllegalArgumentException.class, () -> BenchmarkMain.b08Settings(
                BenchmarkMain.options(new String[]{"--scenarios", "delete-everything"})));
    }
}
