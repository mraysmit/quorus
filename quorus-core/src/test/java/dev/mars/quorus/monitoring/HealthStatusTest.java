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

package dev.mars.quorus.monitoring;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for HealthStatus enum.
 */
@DisplayName("HealthStatus Enum Tests")
class HealthStatusTest {

    @Test
    @DisplayName("UP status should be healthy")
    void testUpIsHealthy() {
        assertTrue(HealthStatus.UP.isHealthy());
    }
    
    @Test
    @DisplayName("DOWN status should not be healthy")
    void testDownIsNotHealthy() {
        assertFalse(HealthStatus.DOWN.isHealthy());
    }
    
    @Test
    @DisplayName("DEGRADED status should not be healthy")
    void testDegradedIsNotHealthy() {
        assertFalse(HealthStatus.DEGRADED.isHealthy());
    }
    
    @Test
    @DisplayName("UP status should be operational")
    void testUpIsOperational() {
        assertTrue(HealthStatus.UP.isOperational());
    }
    
    @Test
    @DisplayName("DEGRADED status should be operational")
    void testDegradedIsOperational() {
        assertTrue(HealthStatus.DEGRADED.isOperational());
    }
    
    @Test
    @DisplayName("DOWN status should not be operational")
    void testDownIsNotOperational() {
        assertFalse(HealthStatus.DOWN.isOperational());
    }
    
    @Test
    @DisplayName("should have exactly 3 values")
    void testEnumValues() {
        assertEquals(3, HealthStatus.values().length);
        assertNotNull(HealthStatus.valueOf("UP"));
        assertNotNull(HealthStatus.valueOf("DOWN"));
        assertNotNull(HealthStatus.valueOf("DEGRADED"));
    }
}
