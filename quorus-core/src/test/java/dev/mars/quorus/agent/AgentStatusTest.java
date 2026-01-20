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

package dev.mars.quorus.agent;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive test suite for AgentStatus enum.
 */
class AgentStatusTest {

    @Test
    void testAllStatusValues() {
        AgentStatus[] statuses = AgentStatus.values();
        assertEquals(11, statuses.length);
    }

    @Test
    void testStatusProperties() {
        // Test HEALTHY status
        assertEquals("healthy", AgentStatus.HEALTHY.getValue());
        assertEquals("Agent is healthy and available for work", AgentStatus.HEALTHY.getDescription());
        assertTrue(AgentStatus.HEALTHY.isOperational());
        assertTrue(AgentStatus.HEALTHY.isAvailableForWork());

        // Test REGISTERING status
        assertEquals("registering", AgentStatus.REGISTERING.getValue());
        assertFalse(AgentStatus.REGISTERING.isOperational());
        assertFalse(AgentStatus.REGISTERING.isAvailableForWork());

        // Test ACTIVE status
        assertEquals("active", AgentStatus.ACTIVE.getValue());
        assertTrue(AgentStatus.ACTIVE.isOperational());
        assertTrue(AgentStatus.ACTIVE.isAvailableForWork());

        // Test IDLE status
        assertEquals("idle", AgentStatus.IDLE.getValue());
        assertTrue(AgentStatus.IDLE.isOperational());
        assertTrue(AgentStatus.IDLE.isAvailableForWork());

        // Test DEGRADED status
        assertEquals("degraded", AgentStatus.DEGRADED.getValue());
        assertTrue(AgentStatus.DEGRADED.isOperational());
        assertFalse(AgentStatus.DEGRADED.isAvailableForWork());

        // Test OVERLOADED status
        assertEquals("overloaded", AgentStatus.OVERLOADED.getValue());
        assertTrue(AgentStatus.OVERLOADED.isOperational());
        assertFalse(AgentStatus.OVERLOADED.isAvailableForWork());

        // Test MAINTENANCE status
        assertEquals("maintenance", AgentStatus.MAINTENANCE.getValue());
        assertFalse(AgentStatus.MAINTENANCE.isOperational());
        assertFalse(AgentStatus.MAINTENANCE.isAvailableForWork());

        // Test DRAINING status
        assertEquals("draining", AgentStatus.DRAINING.getValue());
        assertFalse(AgentStatus.DRAINING.isOperational());
        assertFalse(AgentStatus.DRAINING.isAvailableForWork());

        // Test UNREACHABLE status
        assertEquals("unreachable", AgentStatus.UNREACHABLE.getValue());
        assertFalse(AgentStatus.UNREACHABLE.isOperational());
        assertFalse(AgentStatus.UNREACHABLE.isAvailableForWork());

        // Test FAILED status
        assertEquals("failed", AgentStatus.FAILED.getValue());
        assertFalse(AgentStatus.FAILED.isOperational());
        assertFalse(AgentStatus.FAILED.isAvailableForWork());

        // Test DEREGISTERED status
        assertEquals("deregistered", AgentStatus.DEREGISTERED.getValue());
        assertFalse(AgentStatus.DEREGISTERED.isOperational());
        assertFalse(AgentStatus.DEREGISTERED.isAvailableForWork());
    }

    @Test
    void testFromValue() {
        assertEquals(AgentStatus.HEALTHY, AgentStatus.fromValue("healthy"));
        assertEquals(AgentStatus.ACTIVE, AgentStatus.fromValue("active"));
        assertEquals(AgentStatus.IDLE, AgentStatus.fromValue("idle"));
        assertEquals(AgentStatus.FAILED, AgentStatus.fromValue("failed"));
        assertEquals(AgentStatus.MAINTENANCE, AgentStatus.fromValue("maintenance"));
    }

    @Test
    void testFromValueCaseInsensitive() {
        assertEquals(AgentStatus.HEALTHY, AgentStatus.fromValue("HEALTHY"));
        assertEquals(AgentStatus.ACTIVE, AgentStatus.fromValue("AcTiVe"));
        assertEquals(AgentStatus.OVERLOADED, AgentStatus.fromValue("OVERLOADED"));
    }

    @Test
    void testFromValueInvalid() {
        assertThrows(IllegalArgumentException.class, () -> {
            AgentStatus.fromValue("invalid-status");
        });
    }

    @Test
    void testFromValueNull() {
        assertThrows(IllegalArgumentException.class, () -> {
            AgentStatus.fromValue(null);
        });
    }

    @Test
    void testToString() {
        assertEquals("healthy", AgentStatus.HEALTHY.toString());
        assertEquals("active", AgentStatus.ACTIVE.toString());
        assertEquals("failed", AgentStatus.FAILED.toString());
    }

    @Test
    void testOperationalStatuses() {
        assertTrue(AgentStatus.HEALTHY.isOperational());
        assertTrue(AgentStatus.ACTIVE.isOperational());
        assertTrue(AgentStatus.IDLE.isOperational());
        assertTrue(AgentStatus.DEGRADED.isOperational());
        assertTrue(AgentStatus.OVERLOADED.isOperational());
    }

    @Test
    void testNonOperationalStatuses() {
        assertFalse(AgentStatus.REGISTERING.isOperational());
        assertFalse(AgentStatus.MAINTENANCE.isOperational());
        assertFalse(AgentStatus.DRAINING.isOperational());
        assertFalse(AgentStatus.UNREACHABLE.isOperational());
        assertFalse(AgentStatus.FAILED.isOperational());
        assertFalse(AgentStatus.DEREGISTERED.isOperational());
    }

    @Test
    void testAvailableForWork() {
        assertTrue(AgentStatus.HEALTHY.isAvailableForWork());
        assertTrue(AgentStatus.ACTIVE.isAvailableForWork());
        assertTrue(AgentStatus.IDLE.isAvailableForWork());
    }

    @Test
    void testNotAvailableForWork() {
        assertFalse(AgentStatus.REGISTERING.isAvailableForWork());
        assertFalse(AgentStatus.DEGRADED.isAvailableForWork());
        assertFalse(AgentStatus.OVERLOADED.isAvailableForWork());
        assertFalse(AgentStatus.MAINTENANCE.isAvailableForWork());
        assertFalse(AgentStatus.DRAINING.isAvailableForWork());
        assertFalse(AgentStatus.UNREACHABLE.isAvailableForWork());
        assertFalse(AgentStatus.FAILED.isAvailableForWork());
        assertFalse(AgentStatus.DEREGISTERED.isAvailableForWork());
    }

    @Test
    void testDescriptions() {
        assertNotNull(AgentStatus.HEALTHY.getDescription());
        assertFalse(AgentStatus.HEALTHY.getDescription().isEmpty());
        
        for (AgentStatus status : AgentStatus.values()) {
            assertNotNull(status.getDescription());
            assertFalse(status.getDescription().isEmpty());
        }
    }
}
