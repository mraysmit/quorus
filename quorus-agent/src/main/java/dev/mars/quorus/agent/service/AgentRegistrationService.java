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
import dev.mars.quorus.agent.AgentCapabilities;
import dev.mars.quorus.agent.config.AgentConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Objects;

/**
 * Service for registering and deregistering the agent with the Quorus controller. Calls block the
 * calling thread (RT-05a).
 *
 * @author Mark Andrew Ray-Smith Cityline Ltd
 * @since 2025-09-04
 * @version 3.0
 */
public class AgentRegistrationService {

    private static final Logger logger = LoggerFactory.getLogger(AgentRegistrationService.class);
    private static final ObjectMapper JSON = new ObjectMapper();

    private final AgentConfiguration config;
    private final ControllerClient client;

    private volatile boolean registered = false;

    public AgentRegistrationService(ControllerClient client, AgentConfiguration config) {
        this.client = Objects.requireNonNull(client, "client");
        this.config = Objects.requireNonNull(config, "config");
    }

    /**
     * Registers the agent with the controller.
     *
     * @return true if the controller accepted the registration; false on a refusal or a transport failure
     * @throws InterruptedException if the calling thread is interrupted
     */
    public boolean register() throws InterruptedException {
        return tryRegister() == Registration.REGISTERED;
    }

    /** The outcome of one registration attempt. */
    public enum Registration {
        REGISTERED,
        /** The controller could not take the registration now: no leader, unreachable, or a server error. */
        UNAVAILABLE,
        /** The controller refused the registration; trying again cannot succeed. */
        REJECTED
    }

    /**
     * Makes one registration attempt.
     *
     * @throws InterruptedException if the calling thread is interrupted
     */
    public Registration tryRegister() throws InterruptedException {
        logger.info("Registering agent {} with controller at {}", config.getAgentId(), config.getControllerUrls());
        try {
            ControllerClient.Response response = client.postJson("/agents/register",
                    JSON.writeValueAsString(createRegistrationRequest()));
            int status = response.status();
            if (status == 201 || status == 200) {
                registered = true;
                logger.info("Agent {} registered successfully", config.getAgentId());
                return Registration.REGISTERED;
            }
            logger.error("Failed to register agent {}: HTTP {}", config.getAgentId(), status);
            return status >= 500 || status == 408 || status == 429 ? Registration.UNAVAILABLE : Registration.REJECTED;
        } catch (IOException e) {
            logger.error("Error registering agent {}: {}", config.getAgentId(), e.getMessage());
            return Registration.UNAVAILABLE;
        }
    }

    /**
     * Deregisters the agent from the controller. Does nothing, successfully, if it is not registered.
     *
     * @return true if the controller confirmed the deregistration; false on any other answer, including
     *         a 404, or a transport failure
     * @throws InterruptedException if the calling thread is interrupted
     */
    public boolean deregister() throws InterruptedException {
        if (!registered) {
            return true;
        }
        logger.info("Deregistering agent {} from controller", config.getAgentId());
        try {
            ControllerClient.Response response = client.delete("/agents/" + config.getAgentId());
            int statusCode = response.status();
            if (statusCode == 200 || statusCode == 204) {
                registered = false;
                logger.info("Agent {} deregistered successfully", config.getAgentId());
                return true;
            }
            logger.error("Failed to deregister agent {}: HTTP {}", config.getAgentId(), statusCode);
            return false;
        } catch (IOException e) {
            logger.error("Error deregistering agent {}: {}", config.getAgentId(), e.getMessage());
            return false;
        }
    }

    private ObjectNode createRegistrationRequest() {
        AgentCapabilities capabilities = config.createCapabilities();
        ObjectNode request = JSON.createObjectNode()
                .put("agentId", config.getAgentId())
                .put("tenantId", config.getTenantId())
                .put("hostname", config.getHostname())
                .put("address", config.getAddress())
                .put("port", config.getAgentPort())
                .put("version", config.getVersion())
                .put("region", config.getRegion())
                .put("datacenter", config.getDatacenter())
                .put("agentPool", config.getAgentPool())
                .put("networkZone", config.getNetworkZone());
        ObjectNode capabilitiesJson = request.putObject("capabilities");
        capabilities.getSupportedProtocols().forEach(capabilitiesJson.putArray("supportedProtocols")::add);
        capabilitiesJson.put("maxConcurrentTransfers", capabilities.getMaxConcurrentTransfers())
                .put("maxTransferSize", capabilities.getMaxTransferSize());
        return request;
    }

    public boolean isRegistered() {
        return registered;
    }
}
