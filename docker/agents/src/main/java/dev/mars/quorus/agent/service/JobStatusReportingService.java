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
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import dev.mars.quorus.agent.config.AgentConfiguration;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * Service for reporting job status updates to the controller.
 * 
 * @author Mark Andrew Ray-Smith Cityline Ltd
 * @since 2.0
 */
public class JobStatusReportingService {

    private static final Logger logger = LoggerFactory.getLogger(JobStatusReportingService.class);
    
    private final AgentConfiguration config;
    private final ObjectMapper objectMapper;
    private final CloseableHttpClient httpClient;

    public JobStatusReportingService(AgentConfiguration config) {
        this.config = config;
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
        this.httpClient = HttpClients.createDefault();
    }

    /**
     * Report that a job has been accepted.
     */
    public void reportAccepted(String jobId) {
        reportStatus(jobId, "ACCEPTED", null, null);
    }

    /**
     * Report that a job is in progress.
     */
    public void reportInProgress(String jobId, long bytesTransferred) {
        reportStatus(jobId, "IN_PROGRESS", bytesTransferred, null);
    }

    /**
     * Report that a job has completed successfully.
     */
    public void reportCompleted(String jobId, long bytesTransferred) {
        reportStatus(jobId, "COMPLETED", bytesTransferred, null);
    }

    /**
     * Report that a job has failed.
     */
    public void reportFailed(String jobId, String errorMessage) {
        reportStatus(jobId, "FAILED", null, errorMessage);
    }

    /**
     * Report job status to the controller.
     */
    private void reportStatus(String jobId, String status, Long bytesTransferred, String errorMessage) {
        try {
            Map<String, Object> request = new HashMap<>();
            request.put("agentId", config.getAgentId());
            request.put("status", status);
            if (bytesTransferred != null) {
                request.put("bytesTransferred", bytesTransferred);
            }
            if (errorMessage != null) {
                request.put("errorMessage", errorMessage);
            }
            
            String requestJson = objectMapper.writeValueAsString(request);
            
            String url = config.getControllerUrl() + "/jobs/" + jobId + "/status";
            HttpPost post = new HttpPost(url);
            post.setEntity(new StringEntity(requestJson, ContentType.APPLICATION_JSON));
            post.setHeader("Content-Type", "application/json");
            
            httpClient.execute(post, response -> {
                int statusCode = response.getCode();
                if (statusCode == 200) {
                    logger.debug("Job status reported: {} -> {}", jobId, status);
                } else {
                    logger.warn("Failed to report job status: {} -> {} (HTTP {})", 
                              jobId, status, statusCode);
                }
                return null;
            });
            
        } catch (Exception e) {
            logger.error("Error reporting job status: {} -> {}", jobId, status, e);
        }
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

