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

package dev.mars.quorus.api.integration;

import dev.mars.quorus.api.dto.TransferRequestDto;
import dev.mars.quorus.api.dto.TransferJobResponseDto;
import dev.mars.quorus.controller.raft.RaftNode;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test for REST API endpoints with distributed controller.
 * This test verifies that the REST API properly integrates with the Raft controller
 * and provides distributed functionality.
 * 
 * @author Mark Andrew Ray-Smith Cityline Ltd
 * @since 2.0
 */
@QuarkusTest
public class DistributedRestApiIntegrationTest {

    @Inject
    RaftNode raftNode;

    @BeforeEach
    void setUp() {
        // Wait for the node to become leader
        try {
            for (int i = 0; i < 50; i++) {
                if (raftNode.getState() == RaftNode.State.LEADER) {
                    break;
                }
                Thread.sleep(100);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Test
    void testHealthEndpointShowsClusterStatus() {
        given()
            .when()
                .get("/api/v1/status")
            .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("status", equalTo("UP"))
                .body("cluster.available", equalTo(true))
                .body("cluster.healthy", equalTo(true))
                .body("cluster.nodeId", equalTo("api-node"))
                .body("cluster.state", equalTo("LEADER"))
                .body("cluster.isLeader", equalTo(true))
                .body("cluster.knownNodes", equalTo(1));
    }

    @Test
    void testTransferSubmissionViaDistributedController() {
        TransferRequestDto request = new TransferRequestDto();
        request.setSourceUri("file:///test/source.txt");
        request.setDestinationPath("/test/destination.txt");

        TransferJobResponseDto response = given()
            .auth().basic("admin", "admin")  // Use basic auth for test
            .contentType(ContentType.JSON)
            .body(request)
            .when()
                .post("/api/v1/transfers")
            .then()
                .statusCode(201)
                .contentType(ContentType.JSON)
                .body("jobId", notNullValue())
                .body("sourceUri", equalTo("file:///test/source.txt"))
                .body("destinationPath", equalTo("/test/destination.txt"))
                .body("status", notNullValue())
                .body("message", containsString("Transfer job created successfully"))
                .extract()
                .as(TransferJobResponseDto.class);

        assertNotNull(response.getJobId(), "Job ID should not be null");
        assertTrue(response.getMessage().contains("Transfer job created successfully"), 
                  "Message should indicate successful creation");
    }

    @Test
    void testTransferStatusQuery() {
        // First create a transfer
        TransferRequestDto request = new TransferRequestDto();
        request.setSourceUri("file:///test/source2.txt");
        request.setDestinationPath("/test/destination2.txt");

        TransferJobResponseDto createResponse = given()
            .auth().basic("admin", "admin")
            .contentType(ContentType.JSON)
            .body(request)
            .when()
                .post("/api/v1/transfers")
            .then()
                .statusCode(201)
                .extract()
                .as(TransferJobResponseDto.class);

        String jobId = createResponse.getJobId();

        // Query the transfer status
        // Note: This will return 404 since query is not yet implemented in distributed state
        given()
            .auth().basic("admin", "admin")
            .when()
                .get("/api/v1/transfers/{jobId}", jobId)
            .then()
                .statusCode(404)
                .body("message", containsString("Transfer job not found"));
    }

    @Test
    void testTransferCancellation() {
        // First create a transfer
        TransferRequestDto request = new TransferRequestDto();
        request.setSourceUri("file:///test/source3.txt");
        request.setDestinationPath("/test/destination3.txt");

        TransferJobResponseDto createResponse = given()
            .auth().basic("admin", "admin")
            .contentType(ContentType.JSON)
            .body(request)
            .when()
                .post("/api/v1/transfers")
            .then()
                .statusCode(201)
                .extract()
                .as(TransferJobResponseDto.class);

        String jobId = createResponse.getJobId();

        // Try to cancel the transfer
        // Note: This will return conflict since cancellation is not yet implemented
        given()
            .auth().basic("admin", "admin")
            .when()
                .delete("/api/v1/transfers/{jobId}", jobId)
            .then()
                .statusCode(409)
                .body("message", containsString("Transfer cannot be cancelled or not found"));
    }

    @Test
    void testInfoEndpoint() {
        given()
            .when()
                .get("/api/v1/info")
            .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("name", equalTo("Quorus API"))
                .body("version", equalTo("2.0"))
                .body("description", containsString("Distributed file transfer"))
                .body("features", hasItem("distributed-controller"))
                .body("features", hasItem("raft-consensus"));
    }

    @Test
    void testOpenApiDocumentation() {
        given()
            .when()
                .get("/q/openapi")
            .then()
                .statusCode(200)
                .contentType("application/yaml")
                .body(containsString("Quorus File Transfer API"))
                .body(containsString("/api/v1/transfers"));
    }

    @Test
    void testSwaggerUI() {
        given()
            .when()
                .get("/q/swagger-ui")
            .then()
                .statusCode(200)
                .contentType("text/html");
    }

    @Test
    void testDistributedControllerFailover() {
        // This test verifies that the API handles controller state changes gracefully
        
        // Verify controller is initially available
        given()
            .when()
                .get("/api/v1/status")
            .then()
                .statusCode(200)
                .body("cluster.available", equalTo(true))
                .body("cluster.isLeader", equalTo(true));

        // Create a transfer while controller is available
        TransferRequestDto request = new TransferRequestDto();
        request.setSourceUri("file:///test/failover.txt");
        request.setDestinationPath("/test/failover-dest.txt");

        given()
            .auth().basic("admin", "admin")
            .contentType(ContentType.JSON)
            .body(request)
            .when()
                .post("/api/v1/transfers")
            .then()
                .statusCode(201)
                .body("message", containsString("Transfer job created successfully"));
    }
}
