/*
 * Copyright 2024 Quorus Project
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

package dev.mars.quorus.api;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.*;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.greaterThan;

/**
 * Integration tests for HealthResource using Quarkus test framework.
 * Tests the health check and service discovery endpoints.
 */
@QuarkusTest
class HealthResourceTest {

    @Test
    void testServiceInfo() {
        given()
        .when()
            .get("/api/v1/info")
        .then()
            .statusCode(200)
            .body("name", equalTo("quorus-api"))
            .body("version", equalTo("2.0"))
            .body("description", containsString("Enterprise-grade file transfer system"))
            .body("phase", equalTo("2.1 - Basic Service Architecture"))
            .body("framework", equalTo("Quarkus"))
            .body("capabilities", hasItems("file-transfer", "rest-api", "health-monitoring"))
            .body("protocols", hasItems("http", "https", "ftp", "sftp", "smb"))
            .body("endpoints", hasItems("/api/v1/transfers", "/api/v1/info", "/health"));
    }

    @Test
    void testServiceStatus() {
        given()
        .when()
            .get("/api/v1/status")
        .then()
            .statusCode(200)
            .body("service", equalTo("quorus-api"))
            .body("version", equalTo("2.0"))
            .body("phase", equalTo("2.1 - Basic Service Architecture"))
            .body("framework", equalTo("Quarkus"))
            .body("timestamp", notNullValue())
            .body("startTime", notNullValue())
            .body("uptime", notNullValue())
            .body("transferEngine", notNullValue())
            .body("transferEngine.engineStatus", equalTo("operational"))
            .body("transferEngine.activeTransfers", notNullValue())
            .body("system", notNullValue())
            .body("system.totalMemory", notNullValue())
            .body("system.freeMemory", notNullValue())
            .body("system.availableProcessors", notNullValue());
    }

    @Test
    void testQuarkusHealthCheck() {
        given()
        .when()
            .get("/health")
        .then()
            .statusCode(200)
            .body("status", equalTo("UP"))
            .body("checks", notNullValue())
            .body("checks.find { it.name == 'transfer-engine' }.status", equalTo("UP"))
            .body("checks.find { it.name == 'transfer-engine' }.data.activeTransfers", notNullValue())
            .body("checks.find { it.name == 'transfer-engine' }.data.status", equalTo("operational"));
    }

    @Test
    void testQuarkusReadinessCheck() {
        given()
        .when()
            .get("/health/ready")
        .then()
            .statusCode(200)
            .body("status", equalTo("UP"))
            .body("checks", notNullValue());
    }

    @Test
    void testQuarkusLivenessCheck() {
        given()
        .when()
            .get("/health/live")
        .then()
            .statusCode(200)
            .body("status", equalTo("UP"));
    }

    @Test
    void testServiceInfoResponseStructure() {
        given()
        .when()
            .get("/api/v1/info")
        .then()
            .statusCode(200)
            .body("$", hasKey("name"))
            .body("$", hasKey("version"))
            .body("$", hasKey("description"))
            .body("$", hasKey("phase"))
            .body("$", hasKey("framework"))
            .body("$", hasKey("capabilities"))
            .body("$", hasKey("protocols"))
            .body("$", hasKey("endpoints"))
            .body("capabilities.size()", greaterThan(0))
            .body("protocols.size()", greaterThan(0))
            .body("endpoints.size()", greaterThan(0));
    }

    @Test
    void testServiceStatusResponseStructure() {
        given()
        .when()
            .get("/api/v1/status")
        .then()
            .statusCode(200)
            .body("$", hasKey("service"))
            .body("$", hasKey("version"))
            .body("$", hasKey("timestamp"))
            .body("$", hasKey("startTime"))
            .body("$", hasKey("uptime"))
            .body("$", hasKey("transferEngine"))
            .body("$", hasKey("system"))
            .body("transferEngine", hasKey("activeTransfers"))
            .body("transferEngine", hasKey("engineStatus"))
            .body("system", hasKey("totalMemory"))
            .body("system", hasKey("freeMemory"))
            .body("system", hasKey("usedMemory"))
            .body("system", hasKey("maxMemory"))
            .body("system", hasKey("availableProcessors"));
    }
}
