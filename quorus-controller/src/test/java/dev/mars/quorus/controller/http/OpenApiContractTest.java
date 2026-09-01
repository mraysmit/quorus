/*
 * Copyright 2025 Mark Andrew Ray-Smith Cityline Ltd
 * Licensed under the Apache License, Version 2.0.
 */
package dev.mars.quorus.controller.http;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("OpenAPI route conformance")
class OpenApiContractTest {

    private static final Pattern REGISTERED_ROUTE = Pattern.compile(
            "router\\.(get|post|put|delete)\\(\"([^\"]+)\"\\)");
    private static final Set<String> HTTP_METHODS = Set.of("get", "post", "put", "delete", "patch");

    @Test
    @DisplayName("Every registered route and every declared operation match exactly")
    @SuppressWarnings("unchecked")
    void registeredRoutesMatchOpenApi() throws Exception {
        Map<String, Object> document;
        try (InputStream input = getClass().getResourceAsStream("/openapi/quorus-controller-v1.yaml")) {
            assertNotNull(input, "Bundled OpenAPI contract must exist");
            document = new Yaml().load(input);
        }

        assertEquals("3.1.0", document.get("openapi"));
        Map<String, Map<String, Object>> paths = (Map<String, Map<String, Object>>) document.get("paths");
        assertNotNull(paths);

        Set<String> declared = new LinkedHashSet<>();
        Set<String> operationIds = new HashSet<>();
        paths.forEach((path, item) -> item.forEach((method, operationValue) -> {
            if (!HTTP_METHODS.contains(method)) {
                return;
            }
            declared.add(method.toUpperCase(Locale.ROOT) + " " + path);
            Map<String, Object> operation = (Map<String, Object>) operationValue;
            String operationId = (String) operation.get("operationId");
            assertNotNull(operationId, () -> method + " " + path + " must define operationId");
            assertTrue(operationIds.add(operationId), () -> "Duplicate operationId: " + operationId);
            assertNotNull(operation.get("responses"), () -> method + " " + path + " must define responses");
        }));

        Path source = Path.of("src/main/java/dev/mars/quorus/controller/http/HttpApiServer.java");
        if (!Files.exists(source)) {
            source = Path.of("quorus-controller").resolve(source);
        }
        String serverSource = Files.readString(source);
        Matcher matcher = REGISTERED_ROUTE.matcher(serverSource);
        Set<String> registered = new LinkedHashSet<>();
        while (matcher.find()) {
            String openApiPath = matcher.group(2).replaceAll(":([A-Za-z][A-Za-z0-9]*)", "{$1}");
            registered.add(matcher.group(1).toUpperCase(Locale.ROOT) + " " + openApiPath);
        }

        assertEquals(registered, declared,
                () -> "Registered/OpenAPI mismatch. Registered=" + registered + ", declared=" + declared);
    }
}
