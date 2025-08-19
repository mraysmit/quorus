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

import dev.mars.quorus.api.dto.TransferRequestDto;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.*;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.anyOf;

/**
 * Comprehensive integration test for the Quorus API.
 * Tests the complete workflow from service discovery to transfer operations.
 */
@QuarkusTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class QuorusApiIntegrationTest {

    private static final String ADMIN_AUTH = "Basic YWRtaW46YWRtaW4xMjM="; // admin:admin123
    private static String createdJobId;

    @Test
    @Order(1)
    void testServiceDiscovery() {
        // Test service info endpoint
        given()
        .when()
            .get("/api/v1/info")
        .then()
            .statusCode(200)
            .body("name", equalTo("quorus-api"))
            .body("version", equalTo("2.0"))
            .body("framework", equalTo("Quarkus"))
            .body("capabilities", hasItem("file-transfer"))
            .body("endpoints", hasItem("/api/v1/transfers"));
    }

    @Test
    @Order(2)
    void testHealthChecks() {
        // Test main health endpoint
        given()
        .when()
            .get("/health")
        .then()
            .statusCode(200)
            .body("status", equalTo("UP"))
            .body("checks.find { it.name == 'transfer-engine' }.status", equalTo("UP"));

        // Test readiness
        given()
        .when()
            .get("/health/ready")
        .then()
            .statusCode(200)
            .body("status", equalTo("UP"));

        // Test liveness
        given()
        .when()
            .get("/health/live")
        .then()
            .statusCode(200)
            .body("status", equalTo("UP"));
    }

    @Test
    @Order(3)
    void testServiceStatus() {
        given()
        .when()
            .get("/api/v1/status")
        .then()
            .statusCode(200)
            .body("service", equalTo("quorus-api"))
            .body("transferEngine.engineStatus", equalTo("operational"))
            .body("transferEngine.activeTransfers", notNullValue())
            .body("system.availableProcessors", greaterThan(0));
    }

    @Test
    @Order(4)
    void testInitialTransferCount() {
        given()
            .header("Authorization", ADMIN_AUTH)
        .when()
            .get("/api/v1/transfers/count")
        .then()
            .statusCode(200)
            .body("count", greaterThanOrEqualTo(0))
            .body("timestamp", notNullValue());
    }

    @Test
    @Order(5)
    void testCreateTransfer() {
        TransferRequestDto request = new TransferRequestDto();
        request.setSourceUri("https://httpbin.org/bytes/1024");
        request.setDestinationPath("/tmp/integration-test.bin");
        request.setDescription("Integration test transfer");
        request.setPriority(5);
        request.setMaxRetries(3);
        request.setTimeoutSeconds(300);

        createdJobId = given()
            .header("Authorization", ADMIN_AUTH)
            .contentType(ContentType.JSON)
            .body(request)
        .when()
            .post("/api/v1/transfers")
        .then()
            .statusCode(201)
            .body("jobId", notNullValue())
            .body("sourceUri", equalTo("https://httpbin.org/bytes/1024"))
            .body("destinationPath", equalTo("/tmp/integration-test.bin"))
            .body("status", equalTo("PENDING"))
            .body("message", equalTo("Transfer job created successfully"))
            .extract()
            .path("jobId");

        // Verify job ID is not null
        assertNotNull(createdJobId);
    }

    @Test
    @Order(6)
    void testGetTransferStatus() {
        // Use the job ID from the previous test
        given()
            .header("Authorization", ADMIN_AUTH)
        .when()
            .get("/api/v1/transfers/" + createdJobId)
        .then()
            .statusCode(200)
            .body("jobId", equalTo(createdJobId))
            .body("sourceUri", equalTo("https://httpbin.org/bytes/1024"))
            .body("destinationPath", equalTo("/tmp/integration-test.bin"))
            .body("status", anyOf(equalTo("PENDING"), equalTo("RUNNING"), equalTo("COMPLETED"), equalTo("FAILED")))
            .body("progressPercentage", notNullValue())
            .body("bytesTransferred", notNullValue())
            .body("createdAt", notNullValue());
    }

    @Test
    @Order(7)
    void testTransferCountAfterCreation() {
        // The count might be higher now due to the created transfer
        given()
            .header("Authorization", ADMIN_AUTH)
        .when()
            .get("/api/v1/transfers/count")
        .then()
            .statusCode(200)
            .body("count", greaterThanOrEqualTo(0))
            .body("timestamp", notNullValue());
    }

    @Test
    @Order(8)
    void testSecurityValidation() {
        // Test without authentication
        given()
            .contentType(ContentType.JSON)
            .body(new TransferRequestDto("https://example.com/file", "/tmp/test"))
        .when()
            .post("/api/v1/transfers")
        .then()
            .statusCode(401);

        // Test with invalid credentials
        given()
            .header("Authorization", "Basic aW52YWxpZDppbnZhbGlk") // invalid:invalid
            .contentType(ContentType.JSON)
            .body(new TransferRequestDto("https://example.com/file", "/tmp/test"))
        .when()
            .post("/api/v1/transfers")
        .then()
            .statusCode(401);
    }

    @Test
    @Order(9)
    void testErrorHandling() {
        // Test with invalid request data
        TransferRequestDto invalidRequest = new TransferRequestDto();
        // Missing required fields

        given()
            .header("Authorization", ADMIN_AUTH)
            .contentType(ContentType.JSON)
            .body(invalidRequest)
        .when()
            .post("/api/v1/transfers")
        .then()
            .statusCode(400)
            .body("error", notNullValue())
            .body("message", containsString("Invalid transfer request"))
            .body("timestamp", notNullValue());

        // Test getting non-existent transfer
        given()
            .header("Authorization", ADMIN_AUTH)
        .when()
            .get("/api/v1/transfers/non-existent-job-id")
        .then()
            .statusCode(404)
            .body("error", notNullValue())
            .body("message", containsString("Transfer job not found"));
    }

    @Test
    @Order(10)
    void testOpenApiDocumentation() {
        // Test that OpenAPI documentation is available
        given()
        .when()
            .get("/q/openapi")
        .then()
            .statusCode(200)
            .body(containsString("Quorus File Transfer API"))
            .body(containsString("/api/v1/transfers"));
    }

    private void assertNotNull(String value) {
        if (value == null) {
            throw new AssertionError("Expected non-null value");
        }
    }
}
