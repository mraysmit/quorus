/*
 * Copyright 2026 Mark Andrew Ray-Smith Cityline Ltd
 * Licensed under the Apache License, Version 2.0.
 */
package dev.mars.quorus;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Register decision DR-Q4: every test class has a name the default build runs. Surefire selects
 * classes by name and no module configures Failsafe, so a class named {@code *IT} is compiled and then
 * silently never run. Heavy tests are kept out of the default build by a tag, not by their name.
 */
class TestNamingConventionTest {

    /** Surefire's default includes. */
    private static final Pattern RUN_BY_SUREFIRE = Pattern.compile("(Test.*|.*Test|.*Tests|.*TestCase)\\.java");
    private static final Pattern DECLARES_A_TEST = Pattern.compile(
            "(?m)^\\s*@(Test|ParameterizedTest|RepeatedTest|TestFactory|TestTemplate)\\b");

    @Test
    void everyClassThatDeclaresATestHasANameTheBuildRuns() throws IOException {
        List<Path> testRoots;
        try (Stream<Path> modules = Files.list(Path.of(".."))) {
            testRoots = modules.map(module -> module.resolve(Path.of("src", "test", "java")))
                    .filter(Files::isDirectory).toList();
        }
        assertFalse(testRoots.isEmpty(), "no test source roots found from " + Path.of("..").toAbsolutePath());

        List<String> neverRun;
        try (Stream<Path> files = testRoots.stream().flatMap(TestNamingConventionTest::walk)) {
            neverRun = files.filter(file -> file.toString().endsWith(".java"))
                    .filter(file -> !RUN_BY_SUREFIRE.matcher(file.getFileName().toString()).matches())
                    .filter(TestNamingConventionTest::declaresATest)
                    .map(file -> Path.of("..").relativize(file).toString().replace('\\', '/'))
                    .sorted().toList();
        }

        assertEquals(List.of(), neverRun,
                "These classes declare tests but Surefire does not select their names, so they never run");
    }

    private static Stream<Path> walk(Path root) {
        try {
            return Files.walk(root);
        } catch (IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }

    private static boolean declaresATest(Path file) {
        try {
            return DECLARES_A_TEST.matcher(Files.readString(file)).find();
        } catch (IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }
}
