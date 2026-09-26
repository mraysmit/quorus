/*
 * Copyright 2026 Mark Andrew Ray-Smith Cityline Ltd
 * Licensed under the Apache License, Version 2.0.
 */
package dev.mars.quorus.controller.deployment;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Container image baseline contract (plan workstream RT-01b; ADR-0012 decisions RT-Q3 and RT-Q4).
 *
 * <p>Images package the jar that the host built and tested; they never compile Java. Each image
 * is built fresh under a dedicated tag, so a cached image can never satisfy the test. It must run
 * the Amazon Corretto 27 JVM and ship byte-for-byte the host-built jar, whose classes target
 * Java 27.
 *
 * <p>Prerequisite: the host-built jars, for example
 * {@code mvn package -pl quorus-controller,quorus-agent -am -DskipTests}.
 */
@Tag("docker")
class ContainerImageBaselineTest {

    private static final int JAVA_BASELINE = 27;
    private static final int EXPECTED_CLASS_MAJOR = 44 + JAVA_BASELINE;
    private static final Path REPOSITORY_ROOT = Path.of("..").toAbsolutePath().normalize();
    private static final String SHIPPED_JAR = "/app/app.jar";
    private static final Pattern JAVA_VERSION = Pattern.compile("version \"(\\d+)");
    private static final Pattern CLASS_MAJOR = Pattern.compile("major version: (\\d+)");

    @Test
    @Timeout(value = 10, unit = TimeUnit.MINUTES)
    void controllerImagePackagesHostBuiltJarOnCorrettoBaseline() throws Exception {
        verifyImage("quorus-controller", "quorus-controller:rt01",
                "dev.mars.quorus.controller.QuorusControllerApplication");
    }

    @Test
    @Timeout(value = 10, unit = TimeUnit.MINUTES)
    void agentImagePackagesHostBuiltJarOnCorrettoBaseline() throws Exception {
        verifyImage("quorus-agent", "quorus-agent:rt01", "dev.mars.quorus.agent.QuorusAgent");
    }

    private static void verifyImage(String module, String image, String mainClass) throws Exception {
        Path hostJar = hostBuiltJar(module);

        build(image, REPOSITORY_ROOT.resolve(module).resolve("Dockerfile"));

        assertCorrettoBaselineRuntime(image);
        assertShipsHostBuiltJar(image, hostJar);
        assertShippedClassMajor(image, mainClass);
    }

    private static Path hostBuiltJar(String module) throws IOException {
        Path target = REPOSITORY_ROOT.resolve(module).resolve("target");
        List<Path> jars;
        try (Stream<Path> files = Files.exists(target) ? Files.list(target) : Stream.empty()) {
            jars = files.filter(path -> {
                String name = path.getFileName().toString();
                return name.startsWith(module + "-") && name.endsWith(".jar")
                        && !name.endsWith("-tests.jar") && !name.endsWith("-sources.jar");
            }).toList();
        }
        assertEquals(1, jars.size(), "expected exactly one host-built " + module + " jar in " + target
                + " but found " + jars + ". Build it on the host first, for example "
                + "mvn package -pl quorus-controller,quorus-agent -am -DskipTests");
        return jars.getFirst();
    }

    private static void build(String image, Path dockerfile) throws Exception {
        assertTrue(Files.isRegularFile(dockerfile), "Dockerfile not found: " + dockerfile);
        ProcessResult result = run(List.of("docker", "build", "-f", dockerfile.toString(),
                "-t", image, REPOSITORY_ROOT.toString()));

        assertEquals(0, result.exitCode(),
                "image " + image + " failed to build; last output:\n" + tail(result.output(), 40));
    }

    private static void assertCorrettoBaselineRuntime(String image) throws Exception {
        ProcessResult result = run(List.of("docker", "run", "--rm", "--entrypoint", "java", image, "-version"));

        assertEquals(0, result.exitCode(), "java -version failed in " + image + ":\n" + result.output());
        assertTrue(result.output().contains("Corretto"),
                image + " must run the Amazon Corretto JVM, but reported:\n" + result.output());
        Matcher version = JAVA_VERSION.matcher(result.output());
        assertTrue(version.find(), "no Java version reported by " + image + ":\n" + result.output());
        assertEquals(JAVA_BASELINE, Integer.parseInt(version.group(1)),
                image + " must run Java " + JAVA_BASELINE + ", but reported:\n" + result.output());
    }

    private static void assertShipsHostBuiltJar(String image, Path hostJar) throws Exception {
        ProcessResult result = run(List.of("docker", "run", "--rm", "--entrypoint", "sha256sum", image, SHIPPED_JAR));

        assertEquals(0, result.exitCode(), "cannot hash " + SHIPPED_JAR + " in " + image + ":\n" + result.output());
        String shipped = result.output().trim().split("\\s+")[0];
        assertEquals(sha256(hostJar), shipped,
                image + " must ship the host-built " + hostJar.getFileName() + " unchanged");
    }

    private static void assertShippedClassMajor(String image, String className) throws Exception {
        ProcessResult result = run(List.of("docker", "run", "--rm", "--entrypoint", "javap", image,
                "-cp", SHIPPED_JAR, "-v", className));

        assertEquals(0, result.exitCode(), "javap failed for " + className + " in " + image + ":\n"
                + tail(result.output(), 20));
        Matcher major = CLASS_MAJOR.matcher(result.output());
        assertTrue(major.find(), "no class-file version reported for " + className + " in " + image);
        assertEquals(EXPECTED_CLASS_MAJOR, Integer.parseInt(major.group(1)),
                className + " in " + image + " must be compiled for Java " + JAVA_BASELINE);
    }

    private static String sha256(Path file) throws IOException, NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream in = Files.newInputStream(file)) {
            byte[] buffer = new byte[64 * 1024];
            for (int read; (read = in.read(buffer)) != -1; ) {
                digest.update(buffer, 0, read);
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private static ProcessResult run(List<String> command) throws IOException, InterruptedException {
        ProcessBuilder builder = new ProcessBuilder(new ArrayList<>(command)).redirectErrorStream(true);
        builder.environment().put("DOCKER_BUILDKIT", "1");
        Process process = builder.start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        int exitCode = process.waitFor();
        System.out.println("[" + String.join(" ", command) + "] exit " + exitCode + "\n" + tail(output, 60));
        return new ProcessResult(exitCode, output);
    }

    private static String tail(String text, int lines) {
        String[] all = text.split("\\R");
        return String.join("\n", Arrays.copyOfRange(all, Math.max(0, all.length - lines), all.length));
    }

    private record ProcessResult(int exitCode, String output) {
    }
}
