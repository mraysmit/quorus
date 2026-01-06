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

import io.vertx.core.Vertx;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * CDI producer for Vert.x instance.
 * Creates a single shared Vert.x instance for the entire application.
 * 
 * <p>Following Vert.x 5 best practices:
 * - Single Vert.x instance per application
 * - Proper lifecycle management (startup/shutdown)
 * - CDI integration for dependency injection
 * 
 * @author Mark Andrew Ray-Smith Cityline Ltd
 * @since 2025-12-16
 * @version 1.0
 */
@ApplicationScoped
public class VertxProducer {

    private static final Logger logger = LoggerFactory.getLogger(VertxProducer.class);

    private Vertx vertx;

    /**
     * Produces a singleton Vert.x instance.
     * This instance is shared across all services in the application.
     *
     * @return the Vert.x instance
     */
    @Produces
    @Singleton
    public Vertx vertx() {
        if (vertx == null) {
            logger.info("Creating shared Vert.x 5.x instance");
            vertx = Vertx.vertx();
            logger.info("Vert.x instance created: {}", System.identityHashCode(vertx));
        }
        return vertx;
    }

    /**
     * Shutdown method to be called when CDI container is destroyed.
     * This will be invoked by the application shutdown hook.
     */
    public void shutdown() {
        if (vertx != null) {
            logger.info("Closing Vert.x instance");
            vertx.close()
                    .onSuccess(v -> logger.info("Vert.x instance closed successfully"))
                    .onFailure(err -> logger.error("Error closing Vert.x", err));
            vertx = null;
        }
    }
}

