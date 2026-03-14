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

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import io.vertx.sqlclient.Pool;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;

import java.lang.reflect.Field;
import java.util.concurrent.RejectedExecutionException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test suite for ConnectionPoolService (Vert.x 5 Reactive Implementation).
 * @author Mark Andrew Ray-Smith Cityline Ltd
 * @version 1.0
 * @since 2025-12-17
 */
@ExtendWith(VertxExtension.class)
class ConnectionPoolServiceTest {

    private Vertx vertx;
    private ConnectionPoolService service;

    @BeforeEach
    void setUp(Vertx vertx) {
        this.vertx = vertx;
        service = new ConnectionPoolService(vertx);
    }

    @AfterEach
    void tearDown(VertxTestContext testContext) {
        if (service != null) {
            service.shutdownAsync().onComplete(testContext.succeeding(v -> testContext.completeNow()));
            return;
        }

        testContext.completeNow();
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
    void testRemovePool(VertxTestContext testContext) throws Exception {
        ConnectionPoolService.PgConnectionConfig config = new ConnectionPoolService.PgConnectionConfig(
            "localhost", 5432, "testdb", "user", "pass"
        );
        ConnectionPoolService.PgPoolConfig poolConfig = new ConnectionPoolService.PgPoolConfig();

        Pool pool = service.getOrCreatePool("test-service", config, poolConfig);
        assertNotNull(pool);

        service.removePoolAsync("test-service")
            .onComplete(testContext.succeeding(v -> testContext.completeNow()));
    }

    @Test
    @DisplayName("Shutdown should not close externally-managed Vert.x")
    void testShutdownDoesNotCloseExternalVertx(VertxTestContext testContext) {
        service.shutdownAsync().onComplete(testContext.succeeding(v -> testContext.verify(() -> {
            long timerId = vertx.setTimer(10, id -> testContext.completeNow());
            assertTrue(timerId >= 0);
        })));
    }

    @Test
    @DisplayName("Shutdown should close internally-managed Vert.x")
    void testShutdownClosesInternalVertx(VertxTestContext testContext) throws Exception {
        ConnectionPoolService internalService = new ConnectionPoolService();
        Vertx internalVertx = extractVertx(internalService);

        internalService.shutdownAsync().onComplete(testContext.succeeding(v -> testContext.verify(() -> {
            assertThrows(RuntimeException.class,
                () -> internalVertx.timer(10, java.util.concurrent.TimeUnit.MILLISECONDS));
            testContext.completeNow();
        })));
    }

    private static Vertx extractVertx(ConnectionPoolService service) throws Exception {
        Field field = ConnectionPoolService.class.getDeclaredField("vertx");
        field.setAccessible(true);
        return (Vertx) field.get(service);
    }
}
