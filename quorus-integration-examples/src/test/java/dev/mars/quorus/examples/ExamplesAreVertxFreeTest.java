/*
 * Copyright 2026 Mark Andrew Ray-Smith Cityline Ltd
 * Licensed under the Apache License, Version 2.0.
 */
package dev.mars.quorus.examples;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Plan item RT-04: {@code quorus-integration-examples} has no Vert.x dependency, direct or transitive, in main or
 * test scope (ADR-0012). The test classpath is the widest one the module has, so if Vert.x is not
 * loadable here, no scope of the pom brings it in.
 */
@DisplayName("quorus-integration-examples - no Vert.x")
class ExamplesAreVertxFreeTest {

    @Test
    @DisplayName("Vert.x is not on the module's classpath")
    void vertxIsNotOnTheClasspath() {
        assertThrows(ClassNotFoundException.class, () -> Class.forName("io." + "vertx.core.Vertx"));
    }

    @Test
    @DisplayName("No source or test resource names a Vert.x package")
    void noSourceNamesAVertxPackage() throws IOException {
        // Split so that this file does not match itself.
        String vertxPackage = "io." + "vertx";
        try (Stream<Path> files = Files.walk(Path.of("src"))) {
            List<String> offending = files.filter(Files::isRegularFile)
                    .filter(file -> file.toString().endsWith(".java") || file.toString().endsWith(".xml"))
                    .filter(file -> read(file).contains(vertxPackage))
                    .map(Path::toString)
                    .sorted()
                    .toList();
            assertEquals(List.of(), offending);
        }
    }

    private static String read(Path file) {
        try {
            return Files.readString(file);
        } catch (IOException e) {
            throw new IllegalStateException("cannot read " + file, e);
        }
    }
}
