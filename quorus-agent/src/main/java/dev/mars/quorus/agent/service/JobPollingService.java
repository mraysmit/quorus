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
import dev.mars.quorus.core.TransferRequest;
import dev.mars.quorus.connection.RuntimeCredential;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.WebClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Service for polling the controller for new job assignments.
 * Uses Vert.x WebClient for non-blocking HTTP communication.
 * 
 * @author Mark Andrew Ray-Smith Cityline Ltd
 * @since 2025-12-11
 * @version 2.0 (Migrated to Vert.x WebClient - T3.1)
 */
public class JobPollingService {

    private static final Logger logger = LoggerFactory.getLogger(JobPollingService.class);
    
    private final AgentConfiguration config;
    private final WebClient webClient;

    public JobPollingService(Vertx vertx, AgentConfiguration config) {
        this.config = config;
        this.webClient = ControllerWebClientFactory.create(vertx, config);
        logger.debug("JobPollingService initialized with Vert.x WebClient (connectTimeout={}ms, idleTimeout={}ms)",
            config.getHttpConnectionTimeout(), config.getHttpIdleTimeout());
    }

    /**
     * Poll the controller for pending job assignments.
     * 
     * @return Future containing list of pending jobs (empty list on failure)
     */
    public Future<List<PendingJob>> pollForJobs() {
        String url = config.getControllerUrl() + "/agents/" + config.getAgentId() + "/jobs";
        
        return webClient.getAbs(url)
            .putHeader("Accept", "application/json")
            .send()
            .map(response -> {
                int statusCode = response.statusCode();
                if (statusCode == 200) {
                    List<PendingJob> pendingJobs = new ArrayList<>();
                    JsonObject responseBody = response.bodyAsJsonObject();
                    
                    JsonArray jobs = responseBody.getJsonArray("pendingJobs");
                    if (jobs != null) {
                        for (int i = 0; i < jobs.size(); i++) {
                            try {
                                JsonObject jobData = jobs.getJsonObject(i);
                                PendingJob job = parsePendingJob(jobData);
                                pendingJobs.add(job);
                            } catch (Exception e) {
                                logger.warn("Failed to parse pending job: {}", e.getMessage());
                            }
                        }
                    }
                    
                    logger.debug("Polled for jobs: found {} pending jobs", pendingJobs.size());
                    return pendingJobs;
                } else {
                    logger.warn("Failed to poll for jobs: HTTP {}", statusCode);
                    return Collections.<PendingJob>emptyList();
                }
            })
            .recover(err -> {
                logger.error("Error polling for jobs: {}", err.getMessage());
                return Future.succeededFuture(Collections.emptyList());
            });
    }

    private PendingJob parsePendingJob(JsonObject jobData) {
        String assignmentId = jobData.getString("assignmentId");
        String jobId = jobData.getString("jobId");
        String agentId = jobData.getString("agentId");
        String attemptId = jobData.getString("attemptId");
        Long fencingGeneration = jobData.getLong("fencingGeneration");
        String leaseExpiresAt = jobData.getString("leaseExpiresAt");
        Long lastReportSequence = jobData.getLong("lastReportSequence");
        String sourceUri = jobData.getString("sourceUri");
        String destinationPath = jobData.getString("destinationUri", jobData.getString("destinationPath"));
        Long totalBytes = jobData.getLong("totalBytes", 0L);
        String description = jobData.getString("description");
        JsonObject serviceConnection = jobData.getJsonObject("serviceConnection");
        JsonObject secretReference = jobData.getJsonObject("secretReference");
        
        return new PendingJob(assignmentId, jobId, agentId, sourceUri, destinationPath, totalBytes, description,
                attemptId, fencingGeneration == null ? 0 : fencingGeneration,
                leaseExpiresAt == null ? null : Instant.parse(leaseExpiresAt),
                lastReportSequence == null ? 0 : lastReportSequence,
                jobData.getString("tenantId"), jobData.getString("remotePath"), jobData.getString("agentPool"),
                jobData.getJsonArray("controllerResolvedAddresses", new JsonArray()).stream()
                        .map(String::valueOf).toList(), serviceConnection, secretReference,
                jobData.getInteger("connectionPolicyVersion"), jobData.getString("connectionPolicyDigest"));
    }

    /**
     * Shuts down the WebClient.
     * 
     * @return Future that completes when shutdown is done
     */
    public Future<Void> shutdown() {
        logger.debug("Shutting down JobPollingService WebClient");
        webClient.close();
        return Future.succeededFuture();
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
        private final String attemptId;
        private final long fencingGeneration;
        private final Instant leaseExpiresAt;
        private final AtomicLong reportSequence;
        private final String tenantId;
        private final String remotePath;
        private final String agentPool;
        private final List<String> controllerResolvedAddresses;
        private final JsonObject serviceConnection;
        private final JsonObject secretReference;
        private final Integer connectionPolicyVersion;
        private final String connectionPolicyDigest;

        public PendingJob(String assignmentId, String jobId, String agentId, String sourceUri, 
                         String destinationPath, long totalBytes, String description) {
            this(assignmentId, jobId, agentId, sourceUri, destinationPath, totalBytes, description,
                    null, 0, null, 0, null, null, null, List.of(), null, null, null, null);
        }

        public PendingJob(String assignmentId, String jobId, String agentId, String sourceUri,
                          String destinationPath, long totalBytes, String description,
                          String attemptId, long fencingGeneration, Instant leaseExpiresAt,
                          long lastReportSequence) {
            this(assignmentId, jobId, agentId, sourceUri, destinationPath, totalBytes, description,
                    attemptId, fencingGeneration, leaseExpiresAt, lastReportSequence,
                    null, null, null, List.of(), null, null, null, null);
        }

        public PendingJob(String assignmentId, String jobId, String agentId, String sourceUri,
                          String destinationPath, long totalBytes, String description,
                          String attemptId, long fencingGeneration, Instant leaseExpiresAt,
                          long lastReportSequence, String tenantId, String remotePath, String agentPool,
                          List<String> controllerResolvedAddresses, JsonObject serviceConnection,
                          JsonObject secretReference, Integer connectionPolicyVersion,
                          String connectionPolicyDigest) {
            this.assignmentId = assignmentId;
            this.jobId = jobId;
            this.agentId = agentId;
            this.sourceUri = sourceUri;
            this.destinationPath = destinationPath;
            this.totalBytes = totalBytes;
            this.description = description;
            this.attemptId = attemptId;
            this.fencingGeneration = fencingGeneration;
            this.leaseExpiresAt = leaseExpiresAt;
            this.reportSequence = new AtomicLong(lastReportSequence);
            this.tenantId = tenantId;
            this.remotePath = remotePath;
            this.agentPool = agentPool;
            this.controllerResolvedAddresses = List.copyOf(controllerResolvedAddresses);
            this.serviceConnection = serviceConnection == null ? null : serviceConnection.copy();
            this.secretReference = secretReference == null ? null : secretReference.copy();
            this.connectionPolicyVersion = connectionPolicyVersion;
            this.connectionPolicyDigest = connectionPolicyDigest;
        }

        public String getAssignmentId() { return assignmentId; }
        public String getJobId() { return jobId; }
        public String getAgentId() { return agentId; }
        public String getSourceUri() { return sourceUri; }
        public String getDestinationPath() { return destinationPath; }
        public long getTotalBytes() { return totalBytes; }
        public String getDescription() { return description; }
        public String getAttemptId() { return attemptId; }
        public long getFencingGeneration() { return fencingGeneration; }
        public Instant getLeaseExpiresAt() { return leaseExpiresAt; }
        public boolean hasAttemptContext() {
            return attemptId != null && !attemptId.isBlank() && fencingGeneration > 0 && leaseExpiresAt != null;
        }
        public long nextReportSequence() { return reportSequence.incrementAndGet(); }
        public boolean isGoverned() { return serviceConnection != null && secretReference != null; }
        public String getTenantId() { return tenantId; }
        public String getRemotePath() { return remotePath; }
        public String getAgentPool() { return agentPool; }
        public List<String> getControllerResolvedAddresses() { return controllerResolvedAddresses; }
        public JsonObject getServiceConnection() { return serviceConnection == null ? null : serviceConnection.copy(); }
        public JsonObject getSecretReference() { return secretReference == null ? null : secretReference.copy(); }
        public Integer getConnectionPolicyVersion() { return connectionPolicyVersion; }
        public String getConnectionPolicyDigest() { return connectionPolicyDigest; }

        public TransferRequest toTransferRequest() {
            return toTransferRequest(null);
        }

        public TransferRequest toTransferRequest(RuntimeCredential runtimeCredential) {
            return TransferRequest.builder()
                    .requestId(jobId)
                    .sourceUri(URI.create(sourceUri))
                    .destinationUri(destinationUri(destinationPath))
                    .expectedSize(totalBytes)
                    .runtimeCredential(runtimeCredential)
                    .build();
        }

        /** Builds the executable request from the agent authorization, never from a queued remote URI. */
        public TransferRequest toAuthorizedTransferRequest(URI authorizedRemoteEndpoint,
                                                            RuntimeCredential runtimeCredential,
                                                            AgentLocalPathPolicy localPathPolicy) {
            Objects.requireNonNull(authorizedRemoteEndpoint, "authorizedRemoteEndpoint");
            Objects.requireNonNull(localPathPolicy, "localPathPolicy");
            URI source = URI.create(sourceUri);
            URI destination = destinationUri(destinationPath);
            boolean sourceLocal = "file".equalsIgnoreCase(source.getScheme());
            boolean destinationLocal = "file".equalsIgnoreCase(destination.getScheme());
            if (sourceLocal == destinationLocal) {
                throw new SecurityException(
                        "Q-ASSIGNMENT-ENDPOINTS: governed assignment must have exactly one local endpoint");
            }
            dev.mars.quorus.core.TransferDirection direction = sourceLocal
                    ? dev.mars.quorus.core.TransferDirection.UPLOAD
                    : dev.mars.quorus.core.TransferDirection.DOWNLOAD;
            Path authorizedLocal = localPathPolicy.authorize(sourceLocal ? source : destination, direction);
            return TransferRequest.builder()
                    .requestId(jobId)
                    .sourceUri(sourceLocal ? authorizedLocal.toUri() : authorizedRemoteEndpoint)
                    .destinationUri(destinationLocal ? authorizedLocal.toUri() : authorizedRemoteEndpoint)
                    .expectedSize(totalBytes)
                    .runtimeCredential(runtimeCredential)
                    .build();
        }

        public dev.mars.quorus.core.TransferDirection direction() {
            URI source = URI.create(sourceUri);
            URI destination = destinationUri(destinationPath);
            boolean sourceLocal = "file".equalsIgnoreCase(source.getScheme());
            boolean destinationLocal = "file".equalsIgnoreCase(destination.getScheme());
            if (sourceLocal == destinationLocal) {
                throw new SecurityException(
                        "Q-ASSIGNMENT-ENDPOINTS: governed assignment must have exactly one local endpoint");
            }
            return sourceLocal ? dev.mars.quorus.core.TransferDirection.UPLOAD
                    : dev.mars.quorus.core.TransferDirection.DOWNLOAD;
        }

        private static URI destinationUri(String value) {
            try {
                URI uri = URI.create(value);
                if (uri.getScheme() != null && uri.getScheme().length() > 1) return uri;
            } catch (IllegalArgumentException ignored) {
                // Compatibility with controller snapshots created before destination URIs were canonical.
            }
            return Paths.get(value).toUri();
        }
    }
}

