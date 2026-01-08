/*
 * Copyright 2025 Quorus Contributors
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

package dev.mars.quorus.network;

import io.vertx.core.Vertx;
import io.vertx.sqlclient.Pool;
import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test suite for ConnectionPoolService (Vert.x 5 Reactive Implementation).
 * @author Mark Andrew Ray-Smith Cityline Ltd
 * @version 1.0
 * @since 2025-12-17
 */
class ConnectionPoolServiceTest {

    private Vertx vertx;
    private ConnectionPoolService service;

    @BeforeEach
    void setUp() {
        vertx = Vertx.vertx();
        service = new ConnectionPoolService(vertx);
    }

    @AfterEach
    void tearDown() {
        if (service != null) {
            service.shutdown();
        }
        if (vertx != null) {
            vertx.close();
        }
    }

    @Test
    @DisplayName("Should create and retrieve pool")
    void testGetOrCreatePool() {
        ConnectionPoolService.PgConnectionConfig config = new ConnectionPoolService.PgConnectionConfig(
            "localhost", 5432, "testdb", "user", "pass"
        );
        ConnectionPoolService.PgPoolConfig poolConfig = new ConnectionPoolService.PgPoolConfig();

        Pool pool = service.getOrCreatePool("test-service", config, poolConfig);
        assertNotNull(pool);
        
        // Retrieve same pool
        Pool samePool = service.getOrCreatePool("test-service", config, poolConfig);
        assertSame(pool, samePool);
    }

    @Test
    @DisplayName("Should remove pool")
    void testRemovePool() {
        ConnectionPoolService.PgConnectionConfig config = new ConnectionPoolService.PgConnectionConfig(
            "localhost", 5432, "testdb", "user", "pass"
        );
        ConnectionPoolService.PgPoolConfig poolConfig = new ConnectionPoolService.PgPoolConfig();

        Pool pool = service.getOrCreatePool("test-service", config, poolConfig);
        assertNotNull(pool);

        service.removePoolAsync("test-service")
            .onComplete(ar -> {
                assertTrue(ar.succeeded());
            });
    }
}
