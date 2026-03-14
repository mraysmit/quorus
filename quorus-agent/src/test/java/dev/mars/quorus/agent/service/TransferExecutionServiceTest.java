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

package dev.mars.quorus.agent.service;

import dev.mars.quorus.agent.config.AgentConfiguration;
import io.vertx.core.Vertx;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.lang.reflect.Field;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

@ExtendWith(VertxExtension.class)
class TransferExecutionServiceTest {

    @Test
    void testShutdownDoesNotCloseInjectedVertx(Vertx sharedVertx, VertxTestContext testContext) {
        TransferExecutionService service = new TransferExecutionService(sharedVertx, createConfig());

        service.start();
        service.shutdown().onComplete(testContext.succeeding(v -> testContext.verify(() -> {
            assertDoesNotThrow(() -> sharedVertx.setTimer(10, id -> {}),
                    "Shutdown should not close externally managed Vert.x");
            testContext.completeNow();
        })));
    }

    @Test
    @SuppressWarnings("deprecation")
    void testDeprecatedConstructorClosesOwnedVertx(VertxTestContext testContext) {
        TransferExecutionService service = new TransferExecutionService(createConfig());
        Vertx ownedVertx = extractVertx(service);

        service.start();
        service.shutdown().onComplete(testContext.succeeding(v -> testContext.verify(() -> {
            assertThrows(RuntimeException.class,
                    () -> ownedVertx.setTimer(10, id -> {}),
                    "Deprecated constructor should close internally managed Vert.x");
            testContext.completeNow();
        })));
    }

    private static AgentConfiguration createConfig() {
        return new AgentConfiguration.Builder()
                .agentId("test-agent")
                .controllerUrl("http://localhost:8080/api/v1")
                .maxConcurrentTransfers(2)
                .heartbeatInterval(1000L)
                .version("1.0.0-TEST")
                .build();
    }

    private static Vertx extractVertx(TransferExecutionService service) {
        try {
            Field vertxField = TransferExecutionService.class.getDeclaredField("vertx");
            vertxField.setAccessible(true);
            return (Vertx) vertxField.get(service);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Failed to extract Vert.x from TransferExecutionService", e);
        }
    }
}
