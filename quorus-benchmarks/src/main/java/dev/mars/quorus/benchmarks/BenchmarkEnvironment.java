/*
 * Copyright 2026 Mark Andrew Ray-Smith Cityline Ltd
 * Licensed under the Apache License, Version 2.0.
 */
package dev.mars.quorus.benchmarks;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

/**
 * The environment of a run, recorded with every result (Architecture Specification §13): the commit, JDK,
 * JVM flags, OS and hardware. Storage and network are described by the caller, because the JVM cannot
 * observe them reliably.
 */
public record BenchmarkEnvironment(String commit, boolean workingTreeClean, String capturedAt, String javaVersion,
                                   String javaVm, List<String> jvmArguments, String osName, String osVersion,
                                   String osArch, int availableProcessors, long maxHeapBytes) {

    /** Captures the environment of this JVM; the commit comes from git in {@code repository}. */
    public static BenchmarkEnvironment capture(Path repository) {
        Runtime runtime = Runtime.getRuntime();
        return new BenchmarkEnvironment(git(repository, "rev-parse", "HEAD"),
                git(repository, "status", "--porcelain").isEmpty(), Instant.now().toString(),
                System.getProperty("java.version"),
                System.getProperty("java.vm.name") + " " + System.getProperty("java.vm.version"),
                ManagementFactory.getRuntimeMXBean().getInputArguments(),
                System.getProperty("os.name"), System.getProperty("os.version"), System.getProperty("os.arch"),
                runtime.availableProcessors(), runtime.maxMemory());
    }

    private static String git(Path repository, String... arguments) {
        try {
            List<String> command = new java.util.ArrayList<>(List.of("git", "-C", repository.toString()));
            command.addAll(List.of(arguments));
            Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
            return process.waitFor() == 0 ? output : "unknown";
        } catch (IOException e) {
            return "unknown";
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return "unknown";
        }
    }
}
