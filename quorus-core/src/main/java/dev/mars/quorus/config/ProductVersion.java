/*
 * Copyright 2026 Mark Andrew Ray-Smith Cityline Ltd
 * Licensed under the Apache License, Version 2.0.
 */
package dev.mars.quorus.config;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * The Quorus product version: the root pom version, written into {@code quorus-build.properties} when
 * the build filters that resource (register decision DR-Q5). It is not a setting; nothing overrides it.
 */
public final class ProductVersion {

    private static final String RESOURCE = "quorus-build.properties";

    private ProductVersion() {
    }

    /**
     * The product version of this build.
     *
     * @throws IllegalStateException if the build did not write the version into the resource
     */
    public static String get() {
        return Holder.VERSION;
    }

    /** Loaded on first use, so a broken build fails where the version is first needed. */
    private static final class Holder {
        private static final String VERSION = load();
    }

    private static String load() {
        Properties properties = new Properties();
        try (InputStream in = ProductVersion.class.getClassLoader().getResourceAsStream(RESOURCE)) {
            if (in != null) {
                properties.load(in);
            }
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read " + RESOURCE + ": " + e.getMessage(), e);
        }
        return require(properties.getProperty("quorus.build.version"));
    }

    static String require(String value) {
        if (value == null || value.isBlank() || value.contains("${")) {
            throw new IllegalStateException("The build did not write the product version into " + RESOURCE
                    + " (found: " + value + "); build with Maven so that the resource is filtered");
        }
        return value.trim();
    }
}
