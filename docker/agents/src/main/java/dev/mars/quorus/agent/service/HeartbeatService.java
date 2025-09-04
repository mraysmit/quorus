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
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Service for sending heartbeats to the Quorus controller.
 * 
 * @author Mark Andrew Ray-Smith Cityline Ltd
 * @since 1.0
 */
public class HeartbeatService {
    
    private static final Logger logger = LoggerFactory.getLogger(HeartbeatService.class);
    
    private final AgentConfiguration config;
    private final AgentRegistrationService registrationService;
    private final ObjectMapper objectMapper;
    private final CloseableHttpClient httpClient;
    private final AtomicLong sequenceNumber = new AtomicLong(0);
    
    public HeartbeatService(AgentConfiguration config, AgentRegistrationService registrationService) {
        this.config = config;
        this.registrationService = registrationService;
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
        this.httpClient = HttpClients.createDefault();
    }
    
    public void sendHeartbeat() {
        if (!registrationService.isRegistered()) {
            logger.debug("Agent not registered, skipping heartbeat");
            return;
        }
        
        try {
            // Create heartbeat request
            Map<String, Object> request = createHeartbeatRequest();
            String requestJson = objectMapper.writeValueAsString(request);
            
            // Send heartbeat
            String url = config.getControllerUrl() + "/agents/heartbeat";
            HttpPost post = new HttpPost(url);
            post.setEntity(new StringEntity(requestJson, ContentType.APPLICATION_JSON));
            post.setHeader("Content-Type", "application/json");
            
            httpClient.execute(post, response -> {
                int statusCode = response.getCode();
                if (statusCode == 200) {
                    logger.debug("Heartbeat sent successfully for agent {}", config.getAgentId());
                } else {
                    logger.warn("Heartbeat failed for agent {}: HTTP {}", 
                              config.getAgentId(), statusCode);
                }
                return null;
            });
            
        } catch (Exception e) {
            logger.error("Error sending heartbeat for agent " + config.getAgentId(), e);
        }
    }
    
    private Map<String, Object> createHeartbeatRequest() {
        Map<String, Object> request = new HashMap<>();
        request.put("agentId", config.getAgentId());
        request.put("timestamp", Instant.now());
        request.put("sequenceNumber", sequenceNumber.incrementAndGet());
        request.put("status", "active");
        request.put("currentJobs", 0); // TODO: Get actual job count
        request.put("availableCapacity", config.getMaxConcurrentTransfers());
        
        // Add system metrics
        Runtime runtime = Runtime.getRuntime();
        Map<String, Object> metrics = new HashMap<>();
        metrics.put("memoryUsed", runtime.totalMemory() - runtime.freeMemory());
        metrics.put("memoryTotal", runtime.totalMemory());
        metrics.put("memoryMax", runtime.maxMemory());
        metrics.put("cpuCores", runtime.availableProcessors());
        request.put("metrics", metrics);
        
        return request;
    }
    
    public void shutdown() {
        try {
            if (httpClient != null) {
                httpClient.close();
            }
        } catch (Exception e) {
            logger.warn("Error closing HTTP client", e);
        }
    }
}
