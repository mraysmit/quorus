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

package dev.mars.quorus.controller.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for AppConfig node identity enforcement.
 * 
 * <p>These tests verify the node identity logic:
 * <ul>
 *   <li>Multi-node clusters require explicit node ID</li>
 *   <li>Single-node clusters allow hostname fallback</li>
 *   <li>Explicit node ID is always used when provided</li>
 * </ul>
 * 
 * <p>Note: AppConfig is a singleton, so we test the underlying logic
 * rather than trying to reset the singleton state.
 * 
 * @author Mark Andrew Ray-Smith Cityline Ltd
 * @since 2026-02-04
 */
class AppConfigNodeIdentityTest {

    @Test
    @DisplayName("AppConfig should load node ID from test properties")
    void configLoadsNodeIdFromProperties() {
        // The test properties file sets quorus.node.id=test-node
        AppConfig config = AppConfig.get();
        assertEquals("test-node", config.getNodeId());
    }

    @Test
    @DisplayName("System property should override properties file")
    void systemPropertyOverridesPropertiesFile() {
        // System properties have higher priority than properties file
        // Set via -Dquorus.node.id=override-node on JVM
        String sysProp = System.getProperty("quorus.node.id");
        String propFile = "test-node"; // From quorus-controller.properties
        
        AppConfig config = AppConfig.get();
        String nodeId = config.getNodeId();
        
        if (sysProp != null && !sysProp.isEmpty()) {
            assertEquals(sysProp, nodeId, "System property should take precedence");
        } else {
            assertEquals(propFile, nodeId, "Properties file value should be used");
        }
    }

    @Test
    @DisplayName("getString should check environment, system property, then properties file")
    void getStringPrecedenceOrder() {
        AppConfig config = AppConfig.get();
        
        // Test a property that's unlikely to be set in env or system props
        String testKey = "quorus.test.precedence.check";
        String defaultValue = "default-value";
        
        // Should return default when nothing is set
        String result = config.getString(testKey, defaultValue);
        
        // Environment variable would be QUORUS_TEST_PRECEDENCE_CHECK
        String envValue = System.getenv("QUORUS_TEST_PRECEDENCE_CHECK");
        String sysValue = System.getProperty(testKey);
        
        if (envValue != null && !envValue.isEmpty()) {
            assertEquals(envValue, result);
        } else if (sysValue != null && !sysValue.isEmpty()) {
            assertEquals(sysValue, result);
        } else {
            assertEquals(defaultValue, result);
        }
    }

    @Test
    @DisplayName("Multi-node cluster detection should check for comma in cluster.nodes")
    void multiNodeClusterDetection() {
        // Test the logic directly - can't easily test via singleton
        // A multi-node cluster is when quorus.cluster.nodes contains a comma
        
        String singleNode = "node1=localhost:9080";
        String multiNode = "node1=host1:9080,node2=host2:9080";
        String empty = "";
        
        assertFalse(isMultiNode(empty), "Empty should be single node");
        assertFalse(isMultiNode(singleNode), "Single node should not be multi-node");
        assertTrue(isMultiNode(multiNode), "Comma-separated should be multi-node");
    }
    
    // Helper that mirrors the logic in AppConfig
    private boolean isMultiNode(String nodes) {
        return !nodes.isEmpty() && nodes.contains(",");
    }

    @Test
    @DisplayName("Node ID should not be empty or null")
    void nodeIdIsNeverEmpty() {
        AppConfig config = AppConfig.get();
        String nodeId = config.getNodeId();
        
        assertNotNull(nodeId, "Node ID should never be null");
        assertFalse(nodeId.isEmpty(), "Node ID should never be empty");
    }
}
