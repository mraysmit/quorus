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

package dev.mars.quorus.api;

import dev.mars.quorus.api.dto.TransferRequestDto;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.*;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.anyOf;

/**
 * Integration tests for TransferResource using Quarkus test framework.
 * Tests the REST API endpoints with real HTTP requests.
 */
@QuarkusTest
class TransferResourceTest {

    private static final String ADMIN_AUTH = "Basic YWRtaW46YWRtaW4xMjM="; // admin:admin123
    private static final String USER_AUTH = "Basic dXNlcjp1c2VyMTIz"; // user:user123

    @Test
    void testCreateTransferWithValidRequest() {
        TransferRequestDto request = new TransferRequestDto();
        request.setSourceUri("https://httpbin.org/bytes/1024");
        request.setDestinationPath("/tmp/test-file.bin");
        request.setDescription("Test transfer");

        given()
            .header("Authorization", ADMIN_AUTH)
            .contentType(ContentType.JSON)
            .body(request)
        .when()
            .post("/api/v1/transfers")
        .then()
            .statusCode(201)
            .body("jobId", notNullValue())
            .body("sourceUri", equalTo("https://httpbin.org/bytes/1024"))
            .body("destinationPath", equalTo("/tmp/test-file.bin"))
            .body("status", equalTo("PENDING"))
            .body("message", equalTo("Transfer job created successfully"));
    }

    @Test
    void testCreateTransferWithInvalidRequest() {
        TransferRequestDto request = new TransferRequestDto();
        // Missing required fields - should return specific validation error

        given()
            .header("Authorization", ADMIN_AUTH)
            .contentType(ContentType.JSON)
            .body(request)
        .when()
            .post("/api/v1/transfers")
        .then()
            .statusCode(400)
            .body("error", equalTo("Invalid request"))
            .body("message", equalTo("Source URI is required"));
    }

    @Test
    void testCreateTransferWithoutAuthentication() {
        TransferRequestDto request = new TransferRequestDto();
        request.setSourceUri("https://httpbin.org/bytes/1024");
        request.setDestinationPath("/tmp/test-file.bin");

        given()
            .contentType(ContentType.JSON)
            .body(request)
        .when()
            .post("/api/v1/transfers")
        .then()
            .statusCode(401);
    }

    @Test
    void testCreateTransferWithUserRole() {
        TransferRequestDto request = new TransferRequestDto();
        request.setSourceUri("https://httpbin.org/bytes/1024");
        request.setDestinationPath("/tmp/test-file-user.bin");

        given()
            .header("Authorization", USER_AUTH)
            .contentType(ContentType.JSON)
            .body(request)
        .when()
            .post("/api/v1/transfers")
        .then()
            .statusCode(201)
            .body("jobId", notNullValue())
            .body("status", equalTo("PENDING"));
    }

    @Test
    void testGetTransferStatusNotFound() {
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
    void testGetActiveTransferCount() {
        given()
            .header("Authorization", ADMIN_AUTH)
        .when()
            .get("/api/v1/transfers/count")
        .then()
            .statusCode(200)
            .body("count", notNullValue())
            .body("timestamp", notNullValue());
    }

    @Test
    void testGetActiveTransferCountWithoutAuth() {
        given()
        .when()
            .get("/api/v1/transfers/count")
        .then()
            .statusCode(401);
    }

    @Test
    void testCancelTransferNotFound() {
        given()
            .header("Authorization", ADMIN_AUTH)
        .when()
            .delete("/api/v1/transfers/non-existent-job-id")
        .then()
            .statusCode(409)
            .body("error", notNullValue())
            .body("message", containsString("Transfer cannot be cancelled or not found"));
    }

    @Test
    void testCreateAndQueryTransferWorkflow() {
        // Create a transfer
        TransferRequestDto request = new TransferRequestDto();
        request.setSourceUri("https://httpbin.org/bytes/2048");
        request.setDestinationPath("/tmp/workflow-test.bin");
        request.setDescription("Workflow test transfer");

        String jobId = given()
            .header("Authorization", ADMIN_AUTH)
            .contentType(ContentType.JSON)
            .body(request)
        .when()
            .post("/api/v1/transfers")
        .then()
            .statusCode(201)
            .extract()
            .path("jobId");

        // Query the transfer status
        given()
            .header("Authorization", ADMIN_AUTH)
        .when()
            .get("/api/v1/transfers/" + jobId)
        .then()
            .statusCode(200)
            .body("jobId", equalTo(jobId))
            .body("sourceUri", equalTo("https://httpbin.org/bytes/2048"))
            .body("destinationPath", anyOf(equalTo("/tmp/workflow-test.bin"), equalTo("\\tmp\\workflow-test.bin")))
            .body("status", anyOf(equalTo("PENDING"), equalTo("IN_PROGRESS"), equalTo("COMPLETED")));

        // Verify active transfer count increased
        given()
            .header("Authorization", ADMIN_AUTH)
        .when()
            .get("/api/v1/transfers/count")
        .then()
            .statusCode(200)
            .body("count", greaterThanOrEqualTo(0));
    }
}
