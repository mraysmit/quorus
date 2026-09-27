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

package dev.mars.quorus.agent.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.mars.quorus.agent.config.AgentConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Service for sending heartbeats to the Quorus controller. Calls block the calling thread (RT-05a).
 *
 * @author Mark Andrew Ray-Smith Cityline Ltd
 * @since 2025-09-04
 * @version 3.0
 */
public class HeartbeatService {

    private static final Logger logger = LoggerFactory.getLogger(HeartbeatService.class);
    private static final ObjectMapper JSON = new ObjectMapper();

    private final AgentConfiguration config;
    private final AgentRegistrationService registrationService;
    private final ControllerClient client;
    private final AtomicLong sequenceNumber = new AtomicLong(0);

    public HeartbeatService(ControllerClient client, AgentConfiguration config,
                            AgentRegistrationService registrationService) {
        this.client = Objects.requireNonNull(client, "client");
        this.config = Objects.requireNonNull(config, "config");
        this.registrationService = Objects.requireNonNull(registrationService, "registrationService");
    }

    /**
     * Sends a heartbeat to the controller. An agent that is not registered sends none.
     *
     * @return true if the controller acknowledged it; false if not registered, refused or unreachable
     * @throws InterruptedException if the calling thread is interrupted
     */
    public boolean sendHeartbeat() throws InterruptedException {
        if (!registrationService.isRegistered()) {
            logger.debug("Agent not registered, skipping heartbeat");
            return false;
        }
        try {
            ControllerClient.Response response = client.postJson(config.getControllerUrl() + "/agents/heartbeat",
                    JSON.writeValueAsString(createHeartbeatRequest()));
            if (response.status() == 200) {
                logger.debug("Heartbeat sent successfully for agent {}", config.getAgentId());
                return true;
            }
            logger.warn("Heartbeat failed for agent {}: HTTP {}", config.getAgentId(), response.status());
            return false;
        } catch (IOException e) {
            logger.error("Error sending heartbeat for agent {}: {}", config.getAgentId(), e.getMessage());
            return false;
        }
    }

    private ObjectNode createHeartbeatRequest() {
        Runtime runtime = Runtime.getRuntime();
        ObjectNode request = JSON.createObjectNode()
                .put("agentId", config.getAgentId())
                .put("timestamp", Instant.now().toString())
                .put("sequenceNumber", sequenceNumber.incrementAndGet())
                .put("status", "active")
                .put("currentJobs", 0) // TODO: Get actual job count
                .put("availableCapacity", config.getMaxConcurrentTransfers());
        request.putObject("metrics")
                .put("memoryUsed", runtime.totalMemory() - runtime.freeMemory())
                .put("memoryTotal", runtime.totalMemory())
                .put("memoryMax", runtime.maxMemory())
                .put("cpuCores", runtime.availableProcessors());
        return request;
    }
}
