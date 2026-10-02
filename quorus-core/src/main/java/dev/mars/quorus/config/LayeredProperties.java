/* Copyright 2026 Mark Andrew Ray-Smith Cityline Ltd. Licensed under Apache-2.0. */
package dev.mars.quorus.config;

import org.slf4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;

/** Shared mechanics for isolated packaged/profile/environment/override configuration layers. */
public final class LayeredProperties {
    private LayeredProperties() { }

    public static boolean loadResource(Properties target, ClassLoader loader, String resourceName) {
        Objects.requireNonNull(target);
        Objects.requireNonNull(loader);
        try (InputStream input = loader.getResourceAsStream(resourceName)) {
            if (input == null) return false;
            target.load(input);
            return true;
        } catch (IOException error) {
            throw new IllegalStateException("Unable to load configuration resource " + resourceName, error);
        }
    }

    /** Applies legacy aliases first, then canonical names for every declared property. */
    public static void applyEnvironment(Properties target, Map<String, String> environment,
                                        Map<String, String> legacyNames) {
        Objects.requireNonNull(target);
        Objects.requireNonNull(environment);
        Objects.requireNonNull(legacyNames);
        legacyNames.forEach((property, variable) -> apply(target, environment, property, variable));
        for (String property : target.stringPropertyNames()) {
            apply(target, environment, property, environmentKey(property));
        }
    }

    public static String getString(Properties source, Map<String, String> environment,
                                   String key, String defaultValue) {
        String value = source.getProperty(key);
        if (value == null && environment != null) value = environment.get(environmentKey(key));
        return value == null || value.isBlank() ? defaultValue : value;
    }

    public static int getInt(Properties source, Map<String, String> environment, String key,
                             int defaultValue, Logger logger) {
        String value = getString(source, environment, key, null);
        if (value == null) return defaultValue;
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException error) {
            logger.warn("Invalid integer value for {}: '{}', using default {}", key, value, defaultValue);
            return defaultValue;
        }
    }

    public static long getLong(Properties source, Map<String, String> environment, String key,
                               long defaultValue, Logger logger) {
        String value = getString(source, environment, key, null);
        if (value == null) return defaultValue;
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException error) {
            logger.warn("Invalid long value for {}: '{}', using default {}", key, value, defaultValue);
            return defaultValue;
        }
    }

    public static boolean getBoolean(Properties source, Map<String, String> environment,
                                     String key, boolean defaultValue) {
        String value = getString(source, environment, key, null);
        return value == null ? defaultValue : Boolean.parseBoolean(value.trim());
    }

    public static String environmentKey(String propertyKey) {
        return propertyKey.toUpperCase(Locale.ROOT).replace('.', '_').replace('-', '_');
    }

    private static void apply(Properties target, Map<String, String> environment,
                              String property, String variable) {
        String value = environment.get(variable);
        if (value != null && !value.isBlank()) target.setProperty(property, value.trim());
    }
}
