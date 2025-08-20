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

package dev.mars.quorus.api.health;

import io.quarkus.test.junit.QuarkusTest;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.Readiness;
import org.junit.jupiter.api.Test;

import jakarta.inject.Inject;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for TransferEngineHealthCheck using Quarkus test framework.
 * Tests the health check logic without mocking.
 */
@QuarkusTest
class TransferEngineHealthCheckTest {

    @Inject
    @Readiness
    TransferEngineHealthCheck healthCheck;

    @Test
    void testHealthCheckReturnsUp() {
        HealthCheckResponse response = healthCheck.call();

        assertNotNull(response);
        assertEquals("transfer-engine", response.getName());
        assertEquals(HealthCheckResponse.Status.UP, response.getStatus());



        // Verify data is present
        assertTrue(response.getData().isPresent(), "Health check response should have data");
        assertTrue(response.getData().get().containsKey("activeTransfers"));
        assertTrue(response.getData().get().containsKey("status"));
        assertEquals("operational", response.getData().get().get("status"));

        // Verify activeTransfers is a valid number (can be Integer or Long)
        Object activeTransfers = response.getData().get().get("activeTransfers");
        assertNotNull(activeTransfers);
        assertTrue(activeTransfers instanceof Number, "Expected Number but got " + activeTransfers.getClass().getName());
        assertTrue(((Number) activeTransfers).longValue() >= 0);
    }

    @Test
    void testHealthCheckName() {
        HealthCheckResponse response = healthCheck.call();
        assertEquals("transfer-engine", response.getName());
    }

    @Test
    void testHealthCheckDataStructure() {
        HealthCheckResponse response = healthCheck.call();
        
        assertTrue(response.getData().isPresent());
        var data = response.getData().get();
        
        // Check required data fields
        assertTrue(data.containsKey("activeTransfers"));
        assertTrue(data.containsKey("status"));
        
        // Verify data types
        assertTrue(data.get("activeTransfers") instanceof Number);
        assertTrue(data.get("status") instanceof String);

        // Verify values
        assertEquals("operational", data.get("status"));
        Number activeTransfers = (Number) data.get("activeTransfers");
        assertTrue(activeTransfers.longValue() >= 0, "Active transfers should be non-negative");
    }

    @Test
    void testHealthCheckConsistency() {
        // Call health check multiple times to ensure consistency
        HealthCheckResponse response1 = healthCheck.call();
        HealthCheckResponse response2 = healthCheck.call();
        HealthCheckResponse response3 = healthCheck.call();
        
        // All should be UP
        assertEquals(HealthCheckResponse.Status.UP, response1.getStatus());
        assertEquals(HealthCheckResponse.Status.UP, response2.getStatus());
        assertEquals(HealthCheckResponse.Status.UP, response3.getStatus());
        
        // All should have the same name
        assertEquals("transfer-engine", response1.getName());
        assertEquals("transfer-engine", response2.getName());
        assertEquals("transfer-engine", response3.getName());
        
        // All should have operational status
        assertEquals("operational", response1.getData().get().get("status"));
        assertEquals("operational", response2.getData().get().get("status"));
        assertEquals("operational", response3.getData().get().get("status"));
    }
}
