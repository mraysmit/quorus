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

import dev.mars.quorus.agent.config.AgentConfiguration;
import dev.mars.quorus.agent.AgentCapabilities;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.WebClientOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Service for registering and deregistering the agent with the Quorus controller.
 * Uses Vert.x WebClient for non-blocking HTTP communication.
 * 
 * @author Mark Andrew Ray-Smith Cityline Ltd
 * @since 2025-09-04
 * @version 2.0 (Migrated to Vert.x WebClient - T3.1)
 */
public class AgentRegistrationService {
    
    private static final Logger logger = LoggerFactory.getLogger(AgentRegistrationService.class);
    
    private final AgentConfiguration config;
    private final WebClient webClient;
    
    private volatile boolean registered = false;
    
    public AgentRegistrationService(Vertx vertx, AgentConfiguration config) {
        this.config = config;
        this.webClient = WebClient.create(vertx, new WebClientOptions()
            .setConnectTimeout(config.getHttpConnectionTimeout())
            .setIdleTimeout(config.getHttpIdleTimeout())
            .setUserAgent("Quorus-Agent/1.0"));
        logger.debug("AgentRegistrationService initialized with Vert.x WebClient (connectTimeout={}ms, idleTimeout={}ms)",
            config.getHttpConnectionTimeout(), config.getHttpIdleTimeout());
    }
    
    /**
     * Registers the agent with the controller.
     * 
     * @return Future that completes with true if registration succeeded, false otherwise
     */
    public Future<Boolean> register() {
        logger.info("Registering agent {} with controller at {}", 
                   config.getAgentId(), config.getControllerUrl());
        
        JsonObject request = createRegistrationRequest();
        String url = config.getControllerUrl() + "/agents/register";
        
        return webClient.postAbs(url)
            .putHeader("Content-Type", "application/json")
            .sendJsonObject(request)
            .map(response -> {
                int statusCode = response.statusCode();
                if (statusCode == 201 || statusCode == 200) {
                    registered = true;
                    logger.info("Agent {} registered successfully", config.getAgentId());
                    return true;
                } else {
                    logger.error("Failed to register agent {}: HTTP {}", 
                               config.getAgentId(), statusCode);
                    return false;
                }
            })
            .recover(err -> {
                logger.error("Error registering agent {}: {}", config.getAgentId(), err.getMessage());
                return Future.succeededFuture(false);
            });
    }
    
    /**
     * Deregisters the agent from the controller.
     * 
     * @return Future that completes with true if deregistration succeeded, false otherwise
     */
    public Future<Boolean> deregister() {
        if (!registered) {
            return Future.succeededFuture(true);
        }
        
        logger.info("Deregistering agent {} from controller", config.getAgentId());
        String url = config.getControllerUrl() + "/agents/" + config.getAgentId();
        
        return webClient.deleteAbs(url)
            .send()
            .map(response -> {
                int statusCode = response.statusCode();
                if (statusCode == 200 || statusCode == 204 || statusCode == 404) {
                    registered = false;
                    logger.info("Agent {} deregistered successfully", config.getAgentId());
                    return true;
                } else {
                    logger.error("Failed to deregister agent {}: HTTP {}", 
                               config.getAgentId(), statusCode);
                    return false;
                }
            })
            .recover(err -> {
                logger.error("Error deregistering agent {}: {}", config.getAgentId(), err.getMessage());
                return Future.succeededFuture(false);
            });
    }
    
    private JsonObject createRegistrationRequest() {
        AgentCapabilities capabilities = config.createCapabilities();
        
        JsonObject request = new JsonObject()
            .put("agentId", config.getAgentId())
            .put("hostname", config.getHostname())
            .put("address", config.getAddress())
            .put("port", config.getAgentPort())
            .put("version", config.getVersion())
            .put("region", config.getRegion())
            .put("datacenter", config.getDatacenter())
            .put("capabilities", capabilitiesToJson(capabilities));
        
        return request;
    }
    
    private JsonObject capabilitiesToJson(AgentCapabilities capabilities) {
        JsonArray protocols = new JsonArray();
        capabilities.getSupportedProtocols().forEach(protocols::add);
        
        return new JsonObject()
            .put("supportedProtocols", protocols)
            .put("maxConcurrentTransfers", capabilities.getMaxConcurrentTransfers())
            .put("maxTransferSize", capabilities.getMaxTransferSize());
    }
    
    public boolean isRegistered() {
        return registered;
    }
    
    /**
     * Shuts down the WebClient.
     * 
     * @return Future that completes when shutdown is done
     */
    public Future<Void> shutdown() {
        logger.debug("Shutting down AgentRegistrationService WebClient");
        webClient.close();
        return Future.succeededFuture();
    }
}
