/*
 * Copyright 2026 Mark Andrew Ray-Smith Cityline Ltd
 * Licensed under the Apache License, Version 2.0.
 */
package dev.mars.quorus.benchmarks;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LatenciesTest {

    @Test
    void percentilesAreNearestRankOfObservedSamples() {
        Latencies latencies = new Latencies(100);
        for (int i = 100; i >= 1; i--) {
            latencies.record(i * 1_000L);                      // 1 µs to 100 µs, recorded out of order
        }

        Latencies.Summary summary = latencies.summarize(Duration.ofSeconds(2));

        assertEquals(100, summary.operations());
        assertEquals(50.0, summary.operationsPerSecond());
        assertEquals(50.0, summary.p50Micros());
        assertEquals(95.0, summary.p95Micros());
        assertEquals(99.0, summary.p99Micros());
        assertEquals(100.0, summary.maxMicros());
    }

    @Test
    void aSingleSampleIsEveryPercentile() {
        Latencies latencies = new Latencies(1);
        latencies.record(7_000);

        Latencies.Summary summary = latencies.summarize(Duration.ofSeconds(1));

        assertEquals(7.0, summary.p50Micros());
        assertEquals(7.0, summary.p99Micros());
    }

    @Test
    void refusesMoreSamplesThanItsCapacityAndAnEmptySummary() {
        Latencies latencies = new Latencies(1);
        assertThrows(IllegalStateException.class, () -> latencies.summarize(Duration.ofSeconds(1)));
        latencies.record(1);
        assertThrows(IllegalStateException.class, () -> latencies.record(2));
    }
}
