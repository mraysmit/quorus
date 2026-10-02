/*
 * Copyright 2026 Mark Andrew Ray-Smith Cityline Ltd
 * Licensed under the Apache License, Version 2.0.
 */
package dev.mars.quorus.controller.http;

import dev.mars.quorus.agent.AgentStatus;
import dev.mars.quorus.controller.security.AuthorizationPolicyEngine;
import dev.mars.quorus.controller.security.PublicEndpoints;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The bundled OpenAPI contract is the only reference for the current HTTP API (register decision
 * DR-Q7), so it must state what the server enforces, not only which routes exist.
 * {@link OpenApiContractTest} covers the route set. These tests cover the rest of the contract
 * that clients rely on: the scope each operation requires, the agent status values, the failure
 * responses of DNS-authorizing operations, and agreement with the REST API Specification's
 * "Current" rows.
 */
@DisplayName("OpenAPI as the current API reference")
class OpenApiReferenceContractTest {

    private static final Set<String> HTTP_METHODS = Set.of("get", "post", "put", "delete", "patch");
    private static final Pattern PATH_PARAMETER = Pattern.compile("\\{[^}]+}");
    private static final Pattern SPEC_ENDPOINT_ROW = Pattern.compile(
            "^\\| `(GET|POST|PUT|DELETE|PATCH)` \\| `([^`]+)` \\| (Current|Required|Planned) \\|");

    private static Map<String, Object> document;

    @BeforeAll
    static void loadContract() throws Exception {
        try (InputStream input = OpenApiReferenceContractTest.class
                .getResourceAsStream("/openapi/quorus-controller-v1.yaml")) {
            assertNotNull(input, "Bundled OpenAPI contract must exist");
            document = new Yaml().load(input);
        }
    }

    @Test
    @DisplayName("Every protected operation declares the scope the policy engine enforces")
    void everyOperationDeclaresTheScopeThePolicyEngineEnforces() {
        AuthorizationPolicyEngine engine = new AuthorizationPolicyEngine();
        List<String> mismatches = new ArrayList<>();
        forEachOperation((method, path, operation) -> {
            Object declared = operation.get("security");
            Object expected = PublicEndpoints.isPublic(path)
                    ? List.of()
                    : List.of(Map.of("mutualTls", List.of(engine.requiredScope(method, samplePath(path)))));
            if (!expected.equals(declared)) {
                mismatches.add(method + " " + path + ": expected security " + expected + ", declared " + declared);
            }
        });
        assertTrue(mismatches.isEmpty(), () -> String.join("\n", mismatches));
    }

    @Test
    @DisplayName("Only the public endpoints are declared without security")
    void onlyThePublicEndpointsAreDeclaredWithoutSecurity() {
        Set<String> unsecured = new TreeSet<>();
        forEachOperation((method, path, operation) -> {
            if (List.of().equals(operation.get("security"))) {
                unsecured.add(path);
            }
        });
        assertEquals(new TreeSet<>(PublicEndpoints.paths()), unsecured);
    }

    @Test
    @DisplayName("The agent status schema lists the values the server writes")
    void agentStatusSchemaListsTheValuesTheServerWrites() {
        List<String> serverValues = Arrays.stream(AgentStatus.values()).map(AgentStatus::getValue).toList();
        assertEquals(serverValues, schema("AgentStatus").get("enum"));
    }

    @Test
    @DisplayName("Operations that authorize DNS declare the conflict, capacity and deadline failures")
    void dnsAuthorizingOperationsDeclareTheirFailureResponses() {
        // Both operations call ControllerConnectionAuthorizer: 409 when the connection changes during
        // resolution, 503 when admission capacity is exhausted, 504 when the deadline expires.
        for (String[] operation : new String[][]{
                {"/api/v1/transfers", "post"},
                {"/api/v1/service-connections/{serviceConnectionId}/validate", "post"}}) {
            Map<String, Object> responses = responses(operation[0], operation[1]);
            for (String status : List.of("409", "503", "504")) {
                assertEquals(Map.of("$ref", "#/components/responses/Problem"), responses.get(status),
                        () -> operation[1] + " " + operation[0] + " must declare " + status + " as a problem response");
            }
        }
    }

    @Test
    @DisplayName("The API information response points to the contract instead of listing endpoints")
    void apiInfoResponsePointsToTheContract() {
        Map<String, Object> content = map(map(responses("/api/v1/info", "get").get("200")).get("content"));
        Map<String, Object> schema = map(map(content.get("application/json")).get("schema"));
        Map<String, Object> properties = map(schema.get("properties"));
        assertNotNull(properties.get("openApi"), "getApiInfo must declare the openApi link");
        assertFalse(properties.containsKey("endpoints"), "getApiInfo must not declare an endpoint inventory");
    }

    @Test
    @DisplayName("REST API Specification rows marked Current are exactly the declared operations")
    void restSpecificationCurrentRowsMatchTheContract() throws Exception {
        Path specification = Path.of("../docs/QUORUS_REST_API_SPECIFICATION.md");
        if (!Files.exists(specification)) {
            specification = Path.of("docs/QUORUS_REST_API_SPECIFICATION.md");
        }
        Set<String> current = new TreeSet<>();
        for (String line : Files.readAllLines(specification)) {
            Matcher row = SPEC_ENDPOINT_ROW.matcher(line);
            if (row.find() && row.group(3).equals("Current")) {
                current.add(row.group(1) + " " + normalize(row.group(2)));
            }
        }
        Set<String> declared = new TreeSet<>();
        forEachOperation((method, path, operation) -> declared.add(method + " " + normalize(path)));

        Set<String> notDeclared = new TreeSet<>(current);
        notDeclared.removeAll(declared);
        Set<String> notMarkedCurrent = new TreeSet<>(declared);
        notMarkedCurrent.removeAll(current);
        assertTrue(notDeclared.isEmpty() && notMarkedCurrent.isEmpty(),
                () -> "Marked Current but not in the contract: " + notDeclared
                        + "\nIn the contract but not marked Current: " + notMarkedCurrent);
    }

    private interface OperationVisitor {
        void visit(String method, String path, Map<String, Object> operation);
    }

    private static void forEachOperation(OperationVisitor visitor) {
        Map<String, Object> paths = map(document.get("paths"));
        paths.forEach((path, item) -> map(item).forEach((method, operation) -> {
            if (HTTP_METHODS.contains(method)) {
                visitor.visit(method.toUpperCase(Locale.ROOT), path, map(operation));
            }
        }));
    }

    private static Map<String, Object> responses(String path, String method) {
        Map<String, Object> operation = map(map(map(document.get("paths")).get(path)).get(method));
        assertFalse(operation.isEmpty(), () -> method + " " + path + " must be declared");
        return map(operation.get("responses"));
    }

    private static Map<String, Object> schema(String name) {
        return map(map(map(document.get("components")).get("schemas")).get(name));
    }

    /** A concrete path the policy engine can evaluate, with each parameter replaced by a sample value. */
    private static String samplePath(String path) {
        return PATH_PARAMETER.matcher(path).replaceAll("sample");
    }

    /** Parameter names differ between the specification and the contract; only their positions matter. */
    private static String normalize(String path) {
        return PATH_PARAMETER.matcher(path).replaceAll("{}");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Object value) {
        return value == null ? Map.of() : (Map<String, Object>) value;
    }
}
