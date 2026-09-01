/*
 * Copyright 2026 Mark Andrew Ray-Smith Cityline Ltd
 * Licensed under the Apache License, Version 2.0.
 */
package dev.mars.quorus.testing;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Objects;

/** Utilities for materialising test resources that may be packaged in a dependency JAR. */
public final class TestResourceUtils {
    private TestResourceUtils() {
    }

    public static Path copyResource(Class<?> anchor, String resourceName, Path targetDirectory) throws IOException {
        Objects.requireNonNull(anchor, "anchor");
        Objects.requireNonNull(resourceName, "resourceName");
        Objects.requireNonNull(targetDirectory, "targetDirectory");

        Path fileName = Path.of(resourceName).getFileName();
        if (fileName == null) {
            throw new IllegalArgumentException("Resource must identify a file: " + resourceName);
        }

        Files.createDirectories(targetDirectory);
        Path target = targetDirectory.resolve(fileName.toString());
        try (InputStream input = Objects.requireNonNull(anchor.getResourceAsStream(resourceName),
                "Missing test resource: " + resourceName)) {
            Files.copy(input, target, StandardCopyOption.REPLACE_EXISTING);
        }
        return target;
    }
}
