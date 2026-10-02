/*
 * Copyright 2026 Mark Andrew Ray-Smith Cityline Ltd
 * Licensed under the Apache License, Version 2.0.
 */
package dev.mars.quorus.benchmarks;

import java.time.Duration;
import java.util.Arrays;

/**
 * Latency samples of one measured run, and their summary. Percentiles use the nearest-rank method on the
 * sorted samples, so every reported value is a latency that was actually observed.
 */
public final class Latencies {

    private final long[] nanos;
    private int count;

    public Latencies(int capacity) {
        this.nanos = new long[capacity];
    }

    /** Records one sample. Thread-safe. */
    public synchronized void record(long elapsedNanos) {
        if (count == nanos.length) {
            throw new IllegalStateException("more samples than the capacity of " + nanos.length);
        }
        nanos[count++] = elapsedNanos;
    }

    /** The summary of the samples recorded so far, over {@code wallTime}. */
    public synchronized Summary summarize(Duration wallTime) {
        if (count == 0) {
            throw new IllegalStateException("no samples recorded");
        }
        long[] sorted = Arrays.copyOf(nanos, count);
        Arrays.sort(sorted);
        double seconds = wallTime.toNanos() / 1e9;
        return new Summary(count, count / seconds, micros(percentile(sorted, 50)), micros(percentile(sorted, 95)),
                micros(percentile(sorted, 99)), micros(sorted[sorted.length - 1]));
    }

    /** Nearest rank: the smallest sample with at least {@code p} percent of samples at or below it. */
    static long percentile(long[] sorted, int p) {
        int rank = (int) Math.ceil(p / 100.0 * sorted.length);
        return sorted[Math.max(0, rank - 1)];
    }

    private static double micros(long nanos) {
        return nanos / 1_000.0;
    }

    /** Counts, throughput and latency percentiles in microseconds. */
    public record Summary(int operations, double operationsPerSecond, double p50Micros, double p95Micros,
                          double p99Micros, double maxMicros) {
    }
}
