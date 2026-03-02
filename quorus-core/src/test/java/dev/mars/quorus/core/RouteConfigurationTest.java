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

package dev.mars.quorus.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for RouteConfiguration validation.
 */
@DisplayName("RouteConfiguration Validation Tests")
class RouteConfigurationTest {

    private TriggerConfiguration createValidTrigger() {
        return TriggerConfiguration.interval(5);
    }
    
    @Test
    @DisplayName("validate() should accept valid configuration")
    void testValidateAcceptsValidConfig() {
        RouteConfiguration config = new RouteConfiguration(
            "route-001",
            "test-route",
            "description",
            "agent-source",
            "/source/path",
            "agent-dest",
            "/dest/path",
            createValidTrigger(),
            RouteStatus.CONFIGURED,
            Map.of(),
            Instant.now(),
            Instant.now()
        );
        
        assertDoesNotThrow(config::validate);
    }
    
    @Test
    @DisplayName("validate() should reject null routeId")
    void testValidateRejectsNullRouteId() {
        RouteConfiguration config = new RouteConfiguration(
            null,  // null routeId
            "test-route",
            "description",
            "agent-source",
            "/source/path",
            "agent-dest",
            "/dest/path",
            createValidTrigger(),
            RouteStatus.CONFIGURED,
            Map.of(),
            null,
            null
        );
        
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, config::validate);
        assertTrue(ex.getMessage().contains("routeId"));
    }
    
    @Test
    @DisplayName("validate() should reject blank routeId")
    void testValidateRejectsBlankRouteId() {
        RouteConfiguration config = new RouteConfiguration(
            "   ",  // blank routeId
            "test-route",
            "description",
            "agent-source",
            "/source/path",
            "agent-dest",
            "/dest/path",
            createValidTrigger(),
            RouteStatus.CONFIGURED,
            Map.of(),
            null,
            null
        );
        
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, config::validate);
        assertTrue(ex.getMessage().contains("routeId"));
    }
    
    @Test
    @DisplayName("validate() should reject null name")
    void testValidateRejectsNullName() {
        RouteConfiguration config = new RouteConfiguration(
            "route-001",
            null,  // null name
            "description",
            "agent-source",
            "/source/path",
            "agent-dest",
            "/dest/path",
            createValidTrigger(),
            RouteStatus.CONFIGURED,
            Map.of(),
            null,
            null
        );
        
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, config::validate);
        assertTrue(ex.getMessage().contains("name"));
    }
    
    @Test
    @DisplayName("validate() should reject null trigger")
    void testValidateRejectsNullTrigger() {
        RouteConfiguration config = new RouteConfiguration(
            "route-001",
            "test-route",
            "description",
            "agent-source",
            "/source/path",
            "agent-dest",
            "/dest/path",
            null,  // null trigger
            RouteStatus.CONFIGURED,
            Map.of(),
            null,
            null
        );
        
        NullPointerException ex = assertThrows(NullPointerException.class, config::validate);
        assertTrue(ex.getMessage().contains("trigger"));
    }
    
    @Test
    @DisplayName("validated() factory should create valid configuration")
    void testValidatedFactoryCreatesValidConfig() {
        RouteConfiguration config = RouteConfiguration.validated(
            "route-001",
            "test-route",
            "description",
            "agent-source",
            "/source/path",
            "agent-dest",
            "/dest/path",
            createValidTrigger(),
            RouteStatus.CONFIGURED,
            Map.of(),
            null,
            null
        );
        
        assertEquals("route-001", config.getRouteId());
        assertEquals("test-route", config.getName());
    }
    
    @Test
    @DisplayName("validated() factory should reject null sourceAgentId")
    void testValidatedFactoryRejectsNullSourceAgentId() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
            RouteConfiguration.validated(
                "route-001",
                "test-route",
                "description",
                null,  // null sourceAgentId
                "/source/path",
                "agent-dest",
                "/dest/path",
                createValidTrigger(),
                RouteStatus.CONFIGURED,
                Map.of(),
                null,
                null
            )
        );
        assertTrue(ex.getMessage().contains("sourceAgentId"));
    }
    
    @Test
    @DisplayName("validated() factory should reject blank destinationLocation")
    void testValidatedFactoryRejectsBlankDestinationLocation() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
            RouteConfiguration.validated(
                "route-001",
                "test-route",
                "description",
                "agent-source",
                "/source/path",
                "agent-dest",
                "",  // blank destinationLocation
                createValidTrigger(),
                RouteStatus.CONFIGURED,
                Map.of(),
                null,
                null
            )
        );
        assertTrue(ex.getMessage().contains("destinationLocation"));
    }
}
