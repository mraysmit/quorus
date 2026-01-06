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
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.classic.methods.HttpDelete;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * Service for registering and deregistering the agent with the Quorus controller.
 * 
 * @author Mark Andrew Ray-Smith Cityline Ltd
 * @since 2025-09-04
 * @version 1.0
 */
public class AgentRegistrationService {
    
    private static final Logger logger = LoggerFactory.getLogger(AgentRegistrationService.class);
    
    private final AgentConfiguration config;
    private final ObjectMapper objectMapper;
    private final CloseableHttpClient httpClient;
    
    private volatile boolean registered = false;
    
    public AgentRegistrationService(AgentConfiguration config) {
        this.config = config;
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
        this.httpClient = HttpClients.createDefault();
    }
    
    public boolean register() {
        try {
            logger.info("Registering agent {} with controller at {}", 
                       config.getAgentId(), config.getControllerUrl());
            
            // Create registration request
            Map<String, Object> request = createRegistrationRequest();
            String requestJson = objectMapper.writeValueAsString(request);
            
            // Send registration request
            String url = config.getControllerUrl() + "/agents/register";
            HttpPost post = new HttpPost(url);
            post.setEntity(new StringEntity(requestJson, ContentType.APPLICATION_JSON));
            post.setHeader("Content-Type", "application/json");
            
            return httpClient.execute(post, response -> {
                int statusCode = response.getCode();
                if (statusCode == 201 || statusCode == 200) {
                    registered = true;
                    logger.info("Agent {} registered successfully", config.getAgentId());
                    return true;
                } else {
                    logger.error("Failed to register agent {}: HTTP {}", 
                               config.getAgentId(), statusCode);
                    return false;
                }
            });
            
        } catch (Exception e) {
            logger.error("Error registering agent " + config.getAgentId(), e);
            return false;
        }
    }
    
    public boolean deregister() {
        if (!registered) {
            return true;
        }
        
        try {
            logger.info("Deregistering agent {} from controller", config.getAgentId());
            
            String url = config.getControllerUrl() + "/agents/" + config.getAgentId();
            HttpDelete delete = new HttpDelete(url);
            
            return httpClient.execute(delete, response -> {
                int statusCode = response.getCode();
                if (statusCode == 200 || statusCode == 204 || statusCode == 404) {
                    registered = false;
                    logger.info("Agent {} deregistered successfully", config.getAgentId());
                    return true;
                } else {
                    logger.error("Failed to deregister agent {}: HTTP {}", 
                               config.getAgentId(), statusCode);
                    return false;
                }
            });
            
        } catch (Exception e) {
            logger.error("Error deregistering agent " + config.getAgentId(), e);
            return false;
        }
    }
    
    private Map<String, Object> createRegistrationRequest() {
        Map<String, Object> request = new HashMap<>();
        request.put("agentId", config.getAgentId());
        request.put("hostname", config.getHostname());
        request.put("address", config.getAddress());
        request.put("port", config.getAgentPort());
        request.put("version", config.getVersion());
        request.put("region", config.getRegion());
        request.put("datacenter", config.getDatacenter());
        request.put("capabilities", config.createCapabilities());
        
        return request;
    }
    
    public boolean isRegistered() {
        return registered;
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
