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

import dev.mars.quorus.agent.AgentCapabilities;
import dev.mars.quorus.agent.AgentStatus;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Set;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

/**
 * REST API test for the Heartbeat Processing endpoints.
 * This test demonstrates the REST API integration for agent heartbeats.
 * 
 * @author Mark Andrew Ray-Smith Cityline Ltd
 * @since 2.0
 */
@QuarkusTest
public class HeartbeatRestApiTest {

    @Test
    void testAgentRegistrationAndHeartbeatViaRestApi() {
        System.out.println("\n🌐 TESTING HEARTBEAT REST API INTEGRATION");
        System.out.println("=========================================");

        String agentId = "rest-test-agent-" + System.currentTimeMillis();

        // 1. Register an agent first
        String registrationRequest = """
            {
                "agentId": "%s",
                "hostname": "rest-test-host",
                "address": "192.168.1.200",
                "port": 8080,
                "version": "1.0.0",
                "region": "test-region",
                "datacenter": "test-dc",
                "capabilities": {
                    "supportedProtocols": ["http", "https"],
                    "maxConcurrentTransfers": 5,
                    "maxTransferSize": 1073741824,
                    "maxBandwidth": 104857600
                }
            }
            """.formatted(agentId);

        given()
            .contentType(ContentType.JSON)
            .body(registrationRequest)
        .when()
            .post("/api/v1/agents/register")
        .then()
            .statusCode(201)
            .body("success", is(true))
            .body("agentId", is(agentId))
            .body("heartbeatInterval", is(30000));

        System.out.println(" 1. Agent registered via REST API: " + agentId);

        // 2. Send heartbeat via REST API
        String heartbeatRequest = """
            {
                "agentId": "%s",
                "timestamp": "%s",
                "sequenceNumber": 1,
                "status": "healthy",
                "currentJobs": 0,
                "availableCapacity": 5,
                "transferMetrics": {
                    "active": 0,
                    "completed": 50,
                    "failed": 1,
                    "successRate": 98.0
                },
                "healthStatus": {
                    "diskSpace": "healthy",
                    "networkConnectivity": "healthy",
                    "systemLoad": "normal",
                    "overallHealth": "healthy"
                }
            }
            """.formatted(agentId, Instant.now().toString());

        given()
            .contentType(ContentType.JSON)
            .body(heartbeatRequest)
        .when()
            .post("/api/v1/agents/heartbeat")
        .then()
            .statusCode(200)
            .body("success", is(true))
            .body("agentId", is(agentId))
            .body("acknowledgedSequenceNumber", is(1))
            .body("nextHeartbeatInterval", is(30000));

        System.out.println(" 2. Heartbeat processed via REST API");

        // 3. Send second heartbeat with different status
        String heartbeat2Request = """
            {
                "agentId": "%s",
                "timestamp": "%s",
                "sequenceNumber": 2,
                "status": "active",
                "currentJobs": 2,
                "availableCapacity": 3,
                "transferMetrics": {
                    "active": 2,
                    "completed": 52,
                    "failed": 1,
                    "successRate": 98.1
                },
                "healthStatus": {
                    "diskSpace": "healthy",
                    "networkConnectivity": "healthy",
                    "systemLoad": "normal",
                    "overallHealth": "healthy"
                }
            }
            """.formatted(agentId, Instant.now().toString());

        given()
            .contentType(ContentType.JSON)
            .body(heartbeat2Request)
        .when()
            .post("/api/v1/agents/heartbeat")
        .then()
            .statusCode(200)
            .body("success", is(true))
            .body("agentId", is(agentId))
            .body("acknowledgedSequenceNumber", is(2));

        System.out.println(" 3. Second heartbeat processed with status update");

        // 4. Test duplicate sequence number rejection
        String duplicateHeartbeatRequest = """
            {
                "agentId": "%s",
                "timestamp": "%s",
                "sequenceNumber": 2,
                "status": "healthy",
                "currentJobs": 0,
                "availableCapacity": 5
            }
            """.formatted(agentId, Instant.now().toString());

        given()
            .contentType(ContentType.JSON)
            .body(duplicateHeartbeatRequest)
        .when()
            .post("/api/v1/agents/heartbeat")
        .then()
            .statusCode(400)
            .body("success", is(false))
            .body("errorCode", is("INVALID_SEQUENCE"));

        System.out.println(" 4. Duplicate sequence number correctly rejected");

        // 5. Get heartbeat statistics
        given()
        .when()
            .get("/api/v1/agents/heartbeat/stats")
        .then()
            .statusCode(200)
            .body("totalAgents", greaterThanOrEqualTo(1))
            .body("healthyAgents", greaterThanOrEqualTo(1));

        System.out.println(" 5. Heartbeat statistics retrieved");

        System.out.println("\n HEARTBEAT REST API INTEGRATION SUCCESS!");
        System.out.println("==========================================");
        System.out.println(" Agent registration REST endpoint working");
        System.out.println(" Heartbeat processing REST endpoint working");
        System.out.println(" Sequence validation working via REST");
        System.out.println(" Statistics endpoint operational");
        System.out.println(" Full REST API integration complete");
    }

    @Test
    void testHeartbeatValidationErrors() {
        System.out.println("\n  TESTING HEARTBEAT VALIDATION ERRORS");
        System.out.println("======================================");

        // Test missing agent ID
        String invalidRequest1 = """
            {
                "timestamp": "%s",
                "sequenceNumber": 1,
                "status": "healthy"
            }
            """.formatted(Instant.now().toString());

        given()
            .contentType(ContentType.JSON)
            .body(invalidRequest1)
        .when()
            .post("/api/v1/agents/heartbeat")
        .then()
            .statusCode(400);

        System.out.println(" Missing agent ID correctly rejected");

        // Test unregistered agent
        String unregisteredAgentRequest = """
            {
                "agentId": "unregistered-agent-999",
                "timestamp": "%s",
                "sequenceNumber": 1,
                "status": "healthy",
                "currentJobs": 0,
                "availableCapacity": 5
            }
            """.formatted(Instant.now().toString());

        given()
            .contentType(ContentType.JSON)
            .body(unregisteredAgentRequest)
        .when()
            .post("/api/v1/agents/heartbeat")
        .then()
            .statusCode(400)
            .body("success", is(false))
            .body("errorCode", is("AGENT_NOT_REGISTERED"));

        System.out.println(" Unregistered agent correctly rejected");

        System.out.println("\n VALIDATION ERROR HANDLING WORKING!");
    }
}
