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

/**
 * Service for reporting job status updates to the controller.
 * Uses Vert.x WebClient for non-blocking HTTP communication.
 * 
 * @author Mark Andrew Ray-Smith Cityline Ltd
 * @since 2025-12-11
 * @version 2.0 (Migrated to Vert.x WebClient - T3.1)
 */
public class JobStatusReportingService {

    private static final Logger logger = LoggerFactory.getLogger(JobStatusReportingService.class);
    
    private final AgentConfiguration config;
    private final WebClient webClient;

    public JobStatusReportingService(Vertx vertx, AgentConfiguration config) {
        this.config = config;
        this.webClient = WebClient.create(vertx, new WebClientOptions()
            .setConnectTimeout(config.getHttpConnectionTimeout())
            .setIdleTimeout(config.getHttpIdleTimeout())
            .setUserAgent("Quorus-Agent/1.0"));
        logger.debug("JobStatusReportingService initialized with Vert.x WebClient (connectTimeout={}ms, idleTimeout={}ms)",
            config.getHttpConnectionTimeout(), config.getHttpIdleTimeout());
    }

    /**
     * Report that a job has been accepted.
     * 
     * @return Future that completes when the report is sent
     */
    public Future<Void> reportAccepted(String jobId) {
        return reportStatus(jobId, "ACCEPTED", null, null);
    }

    /**
     * Report that a job is in progress.
     * 
     * @return Future that completes when the report is sent
     */
    public Future<Void> reportInProgress(String jobId, long bytesTransferred) {
        return reportStatus(jobId, "IN_PROGRESS", bytesTransferred, null);
    }

    /**
     * Report that a job has completed successfully.
     * 
     * @return Future that completes when the report is sent
     */
    public Future<Void> reportCompleted(String jobId, long bytesTransferred) {
        return reportStatus(jobId, "COMPLETED", bytesTransferred, null);
    }

    /**
     * Report that a job has failed.
     * 
     * @return Future that completes when the report is sent
     */
    public Future<Void> reportFailed(String jobId, String errorMessage) {
        return reportStatus(jobId, "FAILED", null, errorMessage);
    }

    /**
     * Report job status to the controller.
     * 
     * @return Future that completes when the report is sent
     */
    private Future<Void> reportStatus(String jobId, String status, Long bytesTransferred, String errorMessage) {
        JsonObject request = new JsonObject()
            .put("agentId", config.getAgentId())
            .put("status", status);
        
        if (bytesTransferred != null) {
            request.put("bytesTransferred", bytesTransferred);
        }
        if (errorMessage != null) {
            request.put("errorMessage", errorMessage);
        }
        
        String url = config.getControllerUrl() + "/jobs/" + jobId + "/status";
        
        return webClient.postAbs(url)
            .putHeader("Content-Type", "application/json")
            .sendJsonObject(request)
            .compose(response -> {
                int statusCode = response.statusCode();
                if (statusCode >= 200 && statusCode < 300) {
                    logger.debug("Job status reported: {} -> {}", jobId, status);
                    return Future.<Void>succeededFuture();
                } else {
                    String message = "Failed to report job status: " + jobId + " -> " + status + " (HTTP " + statusCode + ")";
                    logger.error(message);
                    return Future.<Void>failedFuture(message);
                }
            })
            .onFailure(err -> {
                logger.error("Error reporting job status: {} -> {}: {}", jobId, status, err.getMessage());
            });
    }

    /**
     * Shuts down the WebClient.
     * 
     * @return Future that completes when shutdown is done
     */
    public Future<Void> shutdown() {
        logger.debug("Shutting down JobStatusReportingService WebClient");
        webClient.close();
        return Future.succeededFuture();
    }
}

