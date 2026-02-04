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
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.WebClientOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Service for sending heartbeats to the Quorus controller.
 * Uses Vert.x WebClient for non-blocking HTTP communication.
 * 
 * @author Mark Andrew Ray-Smith Cityline Ltd
 * @since 2025-09-04
 * @version 2.0 (Migrated to Vert.x WebClient - T3.1)
 */
public class HeartbeatService {
    
    private static final Logger logger = LoggerFactory.getLogger(HeartbeatService.class);
    
    private final AgentConfiguration config;
    private final AgentRegistrationService registrationService;
    private final WebClient webClient;
    private final AtomicLong sequenceNumber = new AtomicLong(0);
    
    public HeartbeatService(Vertx vertx, AgentConfiguration config, AgentRegistrationService registrationService) {
        this.config = config;
        this.registrationService = registrationService;
        this.webClient = WebClient.create(vertx, new WebClientOptions()
            .setConnectTimeout(config.getHttpConnectionTimeout())
            .setIdleTimeout(config.getHttpIdleTimeout())
            .setUserAgent("Quorus-Agent/1.0"));
        logger.debug("HeartbeatService initialized with Vert.x WebClient (connectTimeout={}ms, idleTimeout={}ms)",
            config.getHttpConnectionTimeout(), config.getHttpIdleTimeout());
    }
    
    /**
     * Sends a heartbeat to the controller.
     * 
     * @return Future that completes with true if successful, false otherwise
     */
    public Future<Boolean> sendHeartbeat() {
        if (!registrationService.isRegistered()) {
            // TODO: so agent is lost if not registered? what does this evern mean?
            logger.debug("Agent not registered, skipping heartbeat");
            return Future.succeededFuture(false);
        }
        
        JsonObject request = createHeartbeatRequest();
        String url = config.getControllerUrl() + "/agents/heartbeat";
        
        return webClient.postAbs(url)
            .putHeader("Content-Type", "application/json")
            .sendJsonObject(request)
            .map(response -> {
                int statusCode = response.statusCode();
                if (statusCode == 200) {
                    logger.debug("Heartbeat sent successfully for agent {}", config.getAgentId());
                    return true;
                } else {
                    logger.warn("Heartbeat failed for agent {}: HTTP {}", 
                              config.getAgentId(), statusCode);
                    return false;
                }
            })
            .recover(err -> {
                logger.error("Error sending heartbeat for agent {}: {}", 
                           config.getAgentId(), err.getMessage());
                return Future.succeededFuture(false);
            });
    }
    
    private JsonObject createHeartbeatRequest() {
        Runtime runtime = Runtime.getRuntime();
        
        JsonObject metrics = new JsonObject()
            .put("memoryUsed", runtime.totalMemory() - runtime.freeMemory())
            .put("memoryTotal", runtime.totalMemory())
            .put("memoryMax", runtime.maxMemory())
            .put("cpuCores", runtime.availableProcessors());
        
        return new JsonObject()
            .put("agentId", config.getAgentId())
            .put("timestamp", Instant.now().toString())
            .put("sequenceNumber", sequenceNumber.incrementAndGet())
            .put("status", "active")
            .put("currentJobs", 0) // TODO: Get actual job count
            .put("availableCapacity", config.getMaxConcurrentTransfers())
            .put("metrics", metrics);
    }
    
    /**
     * Shuts down the WebClient.
     * 
     * @return Future that completes when shutdown is done
     */
    public Future<Void> shutdown() {
        logger.debug("Shutting down HeartbeatService WebClient");
        webClient.close();
        return Future.succeededFuture();
    }
}
