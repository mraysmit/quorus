/*
 * Copyright 2025 Mark Andrew Ray-Smith Cityline Ltd
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package dev.mars.quorus.controller.config;

import java.util.Properties;

/** Creates an isolated controller configuration for each test. */
public final class ControllerTestConfig {

    private ControllerTestConfig() {
    }

    public static AppConfig create() {
        return create(new Properties());
    }

    public static AppConfig create(Properties overrides) {
        Properties isolatedOverrides = new Properties();
        isolatedOverrides.putAll(overrides);
        return new AppConfig("test", isolatedOverrides);
    }
}
