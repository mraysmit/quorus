/*
 * Copyright 2025 Mark Andrew Ray-Smith Cityline Ltd
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package dev.mars.quorus.api.config;

import dev.mars.quorus.transfer.SimpleTransferEngine;
import dev.mars.quorus.transfer.TransferEngine;
import io.vertx.core.Vertx;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * CDI producer for TransferEngine.
 * Converted from MicroProfile Config to simple configuration for Vert.x 5.x migration.
 * Updated to inject Vertx (Phase 1.5 - Dec 2025).
 */
@ApplicationScoped
public class TransferEngineProducer {

    private static final Logger logger = LoggerFactory.getLogger(TransferEngineProducer.class);

    @Inject
    Vertx vertx;

    // Configuration values - can be made configurable via system properties or environment variables
    private static final int MAX_CONCURRENT_TRANSFERS = getIntProperty("quorus.transfer.max-concurrent-transfers", 10);
    private static final int MAX_RETRY_ATTEMPTS = getIntProperty("quorus.transfer.max-retry-attempts", 3);
    private static final long RETRY_DELAY_MS = getLongProperty("quorus.transfer.retry-delay-ms", 1000L);

    /**
     * Produces a singleton TransferEngine instance with Vert.x dependency injection.
     */
    @Produces
    @Singleton
    public TransferEngine createTransferEngine() {
        logger.info("Creating TransferEngine with Vertx, maxConcurrentTransfers={}, maxRetryAttempts={}, retryDelayMs={}",
                   MAX_CONCURRENT_TRANSFERS, MAX_RETRY_ATTEMPTS, RETRY_DELAY_MS);

        return new SimpleTransferEngine(vertx, MAX_CONCURRENT_TRANSFERS, MAX_RETRY_ATTEMPTS, RETRY_DELAY_MS);
    }

    /**
     * Get integer property from system properties or environment variables.
     */
    private static int getIntProperty(String key, int defaultValue) {
        String value = System.getProperty(key, System.getenv(key.replace('.', '_').toUpperCase()));
        if (value != null) {
            try {
                return Integer.parseInt(value);
            } catch (NumberFormatException e) {
                logger.warn("Invalid integer value for {}: {}, using default: {}", key, value, defaultValue);
            }
        }
        return defaultValue;
    }

    /**
     * Get long property from system properties or environment variables.
     */
    private static long getLongProperty(String key, long defaultValue) {
        String value = System.getProperty(key, System.getenv(key.replace('.', '_').toUpperCase()));
        if (value != null) {
            try {
                return Long.parseLong(value);
            } catch (NumberFormatException e) {
                logger.warn("Invalid long value for {}: {}, using default: {}", key, value, defaultValue);
            }
        }
        return defaultValue;
    }
}
