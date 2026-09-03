/*
 * Copyright 2026 Mark Andrew Ray-Smith Cityline Ltd
 * Licensed under the Apache License, Version 2.0.
 */
package dev.mars.quorus.controller.http;

import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class Phase4OpenApiContractTest {
    @Test
    @SuppressWarnings("unchecked")
    void canonicalContractDescribesEveryPhase4OperationAndSchema() {
        InputStream input = getClass().getResourceAsStream("/openapi/quorus-controller-v1.yaml");
        assertNotNull(input);
        Map<String, Object> root = new Yaml().load(input);
        Map<String, Object> paths = (Map<String, Object>) root.get("paths");
        assertOperations(paths, "/api/v1/service-connections", "get", "post");
        assertOperations(paths, "/api/v1/service-connections/{serviceConnectionId}", "get", "put", "delete");
        assertOperations(paths, "/api/v1/service-connections/{serviceConnectionId}/validate", "post");
        assertOperations(paths, "/api/v1/secret-references", "get", "post");
        assertOperations(paths, "/api/v1/secret-references/{secretReferenceId}", "get", "put", "delete");
        assertOperations(paths, "/api/v1/security-events", "get");
        Map<String, Object> schemas = (Map<String, Object>) ((Map<String, Object>) root.get("components")).get("schemas");
        for (String schema : new String[]{"ServiceConnection", "SecretReference", "ConnectionValidation", "SecurityEvent"}) {
            assertTrue(schemas.containsKey(schema), "missing schema " + schema);
        }
        assertFalse(root.toString().contains("secretValue"));
    }

    @SuppressWarnings("unchecked")
    private static void assertOperations(Map<String, Object> paths, String path, String... methods) {
        assertTrue(paths.containsKey(path), "missing path " + path);
        Map<String, Object> operations = (Map<String, Object>) paths.get(path);
        for (String method : methods) assertTrue(operations.containsKey(method), "missing " + method + " " + path);
    }
}
