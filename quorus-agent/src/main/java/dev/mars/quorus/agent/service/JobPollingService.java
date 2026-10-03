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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.mars.quorus.agent.config.AgentConfiguration;
import dev.mars.quorus.core.TransferRequest;
import dev.mars.quorus.connection.RuntimeCredential;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Service for polling the controller for new job assignments. Calls block the calling thread (RT-05a).
 *
 * @author Mark Andrew Ray-Smith Cityline Ltd
 * @since 2025-12-11
 * @version 3.0
 */
public class JobPollingService {

    private static final Logger logger = LoggerFactory.getLogger(JobPollingService.class);
    private static final ObjectMapper JSON = new ObjectMapper();

    private final AgentConfiguration config;
    private final ControllerClient client;

    public JobPollingService(ControllerClient client, AgentConfiguration config) {
        this.client = Objects.requireNonNull(client, "client");
        this.config = Objects.requireNonNull(config, "config");
    }

    /**
     * Polls the controller for pending job assignments. An entry that cannot be parsed is skipped.
     *
     * @return the pending jobs; empty on an HTTP error or a transport failure
     * @throws InterruptedException if the calling thread is interrupted
     */
    public List<PendingJob> pollForJobs() throws InterruptedException {
        String url = "/agents/" + config.getAgentId() + "/jobs";
        try {
            ControllerClient.Response response = client.get(url);
            if (response.status() != 200) {
                logger.warn("Failed to poll for jobs: HTTP {}", response.status());
                return List.of();
            }
            List<PendingJob> pendingJobs = new ArrayList<>();
            JsonNode jobs = JSON.readTree(response.body()).path("pendingJobs");
            for (JsonNode jobData : jobs) {
                try {
                    pendingJobs.add(parsePendingJob(jobData));
                } catch (RuntimeException e) {
                    logger.warn("Failed to parse pending job: {}", e.getMessage());
                }
            }
            logger.debug("Polled for jobs: found {} pending jobs", pendingJobs.size());
            return pendingJobs;
        } catch (IOException e) {
            logger.error("Error polling for jobs: {}", e.getMessage());
            return List.of();
        }
    }

    private static PendingJob parsePendingJob(JsonNode jobData) {
        if (!jobData.isObject()) {
            throw new IllegalArgumentException("pending job entry is not an object");
        }
        String leaseExpiresAt = text(jobData, "leaseExpiresAt");
        String destinationPath = text(jobData, "destinationUri");
        List<String> controllerResolvedAddresses = new ArrayList<>();
        jobData.path("controllerResolvedAddresses").forEach(address -> controllerResolvedAddresses.add(address.asText()));
        return new PendingJob(text(jobData, "assignmentId"), text(jobData, "jobId"), text(jobData, "agentId"),
                text(jobData, "sourceUri"), destinationPath != null ? destinationPath : text(jobData, "destinationPath"),
                jobData.path("totalBytes").asLong(0L), text(jobData, "description"),
                text(jobData, "attemptId"), jobData.path("fencingGeneration").asLong(0L),
                leaseExpiresAt == null ? null : Instant.parse(leaseExpiresAt),
                jobData.path("lastReportSequence").asLong(0L),
                text(jobData, "tenantId"), text(jobData, "remotePath"), text(jobData, "agentPool"),
                controllerResolvedAddresses, json(jobData, "serviceConnection"), json(jobData, "secretReference"),
                jobData.hasNonNull("connectionPolicyVersion") ? jobData.get("connectionPolicyVersion").asInt() : null,
                text(jobData, "connectionPolicyDigest"));
    }

    private static String text(JsonNode json, String field) {
        JsonNode value = json.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }

    /** A nested object as JSON text, or null when absent. */
    private static String json(JsonNode json, String field) {
        JsonNode value = json.get(field);
        return value == null || !value.isObject() ? null : value.toString();
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
        private final String serviceConnection;
        private final String secretReference;
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
                          List<String> controllerResolvedAddresses, String serviceConnection,
                          String secretReference, Integer connectionPolicyVersion,
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
            this.serviceConnection = serviceConnection;
            this.secretReference = secretReference;
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
        /** The governed service connection as JSON text ({@code ServiceConnectionJsonCodec}), or null. */
        public String getServiceConnection() { return serviceConnection; }
        /** The secret reference as JSON text ({@code ServiceConnectionJsonCodec}), or null. */
        public String getSecretReference() { return secretReference; }
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

