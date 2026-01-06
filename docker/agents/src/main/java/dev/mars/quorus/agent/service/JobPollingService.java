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
import dev.mars.quorus.core.TransferRequest;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Service for polling the controller for new job assignments.
 * 
 * @author Mark Andrew Ray-Smith Cityline Ltd
 * @since 2025-12-11
 * @version 1.0
 */
public class JobPollingService {

    private static final Logger logger = LoggerFactory.getLogger(JobPollingService.class);
    
    private final AgentConfiguration config;
    private final ObjectMapper objectMapper;
    private final CloseableHttpClient httpClient;

    public JobPollingService(AgentConfiguration config) {
        this.config = config;
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
        this.httpClient = HttpClients.createDefault();
    }

    /**
     * Poll the controller for pending job assignments.
     * 
     * @return list of pending jobs
     */
    public List<PendingJob> pollForJobs() {
        List<PendingJob> pendingJobs = new ArrayList<>();
        
        try {
            String url = config.getControllerUrl() + "/agents/" + config.getAgentId() + "/jobs";
            HttpGet get = new HttpGet(url);
            get.setHeader("Accept", "application/json");
            
            httpClient.execute(get, response -> {
                int statusCode = response.getCode();
                if (statusCode == 200) {
                    String responseBody = EntityUtils.toString(response.getEntity());
                    Map<String, Object> responseMap = objectMapper.readValue(responseBody, Map.class);
                    
                    List<Map<String, Object>> jobs = (List<Map<String, Object>>) responseMap.get("pendingJobs");
                    if (jobs != null) {
                        for (Map<String, Object> jobData : jobs) {
                            try {
                                PendingJob job = parsePendingJob(jobData);
                                pendingJobs.add(job);
                            } catch (Exception e) {
                                logger.warn("Failed to parse pending job: {}", e.getMessage());
                            }
                        }
                    }
                    
                    logger.debug("Polled for jobs: found {} pending jobs", pendingJobs.size());
                } else {
                    logger.warn("Failed to poll for jobs: HTTP {}", statusCode);
                }
                return null;
            });
            
        } catch (Exception e) {
            logger.error("Error polling for jobs", e);
        }
        
        return pendingJobs;
    }

    private PendingJob parsePendingJob(Map<String, Object> jobData) {
        String assignmentId = (String) jobData.get("assignmentId");
        String jobId = (String) jobData.get("jobId");
        String agentId = (String) jobData.get("agentId");
        String sourceUri = (String) jobData.get("sourceUri");
        String destinationPath = (String) jobData.get("destinationPath");
        Long totalBytes = jobData.get("totalBytes") != null ? 
                ((Number) jobData.get("totalBytes")).longValue() : 0L;
        String description = (String) jobData.get("description");
        
        return new PendingJob(assignmentId, jobId, agentId, sourceUri, destinationPath, totalBytes, description);
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

    /**
     * Represents a pending job assignment.
     */
    public static class PendingJob {
        private final String assignmentId;
        private final String jobId;
        private final String agentId;
        private final String sourceUri;
        private final String destinationPath;
        private final long totalBytes;
        private final String description;

        public PendingJob(String assignmentId, String jobId, String agentId, String sourceUri, 
                         String destinationPath, long totalBytes, String description) {
            this.assignmentId = assignmentId;
            this.jobId = jobId;
            this.agentId = agentId;
            this.sourceUri = sourceUri;
            this.destinationPath = destinationPath;
            this.totalBytes = totalBytes;
            this.description = description;
        }

        public String getAssignmentId() { return assignmentId; }
        public String getJobId() { return jobId; }
        public String getAgentId() { return agentId; }
        public String getSourceUri() { return sourceUri; }
        public String getDestinationPath() { return destinationPath; }
        public long getTotalBytes() { return totalBytes; }
        public String getDescription() { return description; }

        public TransferRequest toTransferRequest() {
            return TransferRequest.builder()
                    .requestId(jobId)
                    .sourceUri(URI.create(sourceUri))
                    .destinationPath(Paths.get(destinationPath))
                    .expectedSize(totalBytes)
                    .build();
        }
    }
}

