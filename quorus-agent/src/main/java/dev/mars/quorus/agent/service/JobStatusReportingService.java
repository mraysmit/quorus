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
import dev.mars.quorus.core.TransferAttemptStatus;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.WebClient;
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
    private final Vertx vertx;
    private volatile boolean closed;
    private static final int MAX_REPORT_SENDS = 3;
    private static final long RETRY_DELAY_MS = 100;

    public JobStatusReportingService(Vertx vertx, AgentConfiguration config) {
        this.config = config;
        this.vertx = vertx;
        this.webClient = ControllerWebClientFactory.create(vertx, config);
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

    public Future<Void> reportAccepted(String jobId, String attemptId,
                                       long fencingGeneration, long reportSequence) {
        return reportStatus(jobId, "ACCEPTED", null, null,
                attemptId, fencingGeneration, reportSequence);
    }

    /**
     * Report that a job is in progress.
     * 
     * @return Future that completes when the report is sent
     */
    public Future<Void> reportInProgress(String jobId, long bytesTransferred) {
        return reportStatus(jobId, "IN_PROGRESS", bytesTransferred, null);
    }

    public Future<Void> reportInProgress(String jobId, long bytesTransferred, String attemptId,
                                         long fencingGeneration, long reportSequence) {
        return reportStatus(jobId, "IN_PROGRESS", bytesTransferred, null,
                attemptId, fencingGeneration, reportSequence);
    }

    /**
     * Report that a job has completed successfully.
     * 
     * @return Future that completes when the report is sent
     */
    public Future<Void> reportCompleted(String jobId, long bytesTransferred) {
        return reportStatus(jobId, "COMPLETED", bytesTransferred, null);
    }

    public Future<Void> reportCompleted(String jobId, long bytesTransferred, String attemptId,
                                        long fencingGeneration, long reportSequence) {
        return reportStatus(jobId, "COMPLETED", bytesTransferred, null,
                attemptId, fencingGeneration, reportSequence);
    }

    /**
     * Report that a job has failed.
     * 
     * @return Future that completes when the report is sent
     */
    public Future<Void> reportFailed(String jobId, String errorMessage) {
        return reportStatus(jobId, "FAILED", null, errorMessage);
    }

    public Future<Void> reportFailed(String jobId, String errorMessage, String attemptId,
                                     long fencingGeneration, long reportSequence, TransferAttemptStatus expectedState) {
        if (expectedState != TransferAttemptStatus.ACCEPTED && expectedState != TransferAttemptStatus.IN_PROGRESS) {
            return Future.failedFuture("FAILED reports require an acknowledged ACCEPTED or IN_PROGRESS state");
        }
        return reportStatus(jobId, "FAILED", null, errorMessage,
                attemptId, fencingGeneration, reportSequence, expectedState.name());
    }

    /**
     * Report job status to the controller.
     * 
     * @return Future that completes when the report is sent
     */
    private Future<Void> reportStatus(String jobId, String status, Long bytesTransferred, String errorMessage) {
        return reportStatus(jobId, status, bytesTransferred, errorMessage, null, 0, 0);
    }

    private Future<Void> reportStatus(String jobId, String status, Long bytesTransferred, String errorMessage,
                                      String attemptId, long fencingGeneration, long reportSequence) {
        return reportStatus(jobId, status, bytesTransferred, errorMessage, attemptId,
                fencingGeneration, reportSequence, attemptId == null ? null : expectedAttemptState(status));
    }

    private Future<Void> reportStatus(String jobId, String status, Long bytesTransferred, String errorMessage,
                                      String attemptId, long fencingGeneration, long reportSequence, String expectedState) {
        JsonObject request = new JsonObject()
            .put("agentId", config.getAgentId())
            .put("status", status);
        
        if (bytesTransferred != null) {
            request.put("bytesTransferred", bytesTransferred);
        }
        if (errorMessage != null) {
            request.put("errorMessage", errorMessage);
        }
        if (attemptId != null) {
            request.put("attemptId", attemptId)
                    .put("expectedState", expectedState)
                    .put("fencingGeneration", fencingGeneration)
                    .put("reportSequence", reportSequence);
        }
        
        String url = config.getControllerUrl() + "/jobs/" + jobId + "/status";
        
        // Reconciliation by exact replay: retries retain the original fence, sequence,
        // expected state and payload. Only attempt-aware reports are idempotent.
        return sendReport(url, request, attemptId == null ? 1 : MAX_REPORT_SENDS)
                .onSuccess(ignored -> logger.debug("Job status reported: {} -> {}", jobId, status))
                .onFailure(err -> logger.error("Status report unresolved or rejected: jobId={}, status={}, attemptId={}, sequence={}: {}",
                        jobId, status, attemptId, reportSequence, err.getMessage()));
    }

    private Future<Void> sendReport(String url, JsonObject request, int sendsRemaining) {
        if (closed) return Future.failedFuture(new StatusReportException("Q-REPORT-CLOSED", false));
        return webClient.postAbs(url)
            .timeout(config.getHttpIdleTimeout())
            .putHeader("Content-Type", "application/json")
            .sendJsonObject(request.copy())
            .compose(response -> {
                int statusCode = response.statusCode();
                if (statusCode >= 200 && statusCode < 300) {
                    return Future.<Void>succeededFuture();
                } else {
                    boolean retryable = statusCode >= 500 || statusCode == 408 || statusCode == 429;
                    return Future.<Void>failedFuture(new StatusReportException(
                            "Q-REPORT-" + (retryable ? "UNRESOLVED" : "REJECTED") + ": HTTP " + statusCode, retryable));
                }
            })
            .recover(err -> {
                StatusReportException failure = err instanceof StatusReportException reportFailure
                        ? reportFailure : new StatusReportException("Q-REPORT-UNRESOLVED: transport acknowledgement unavailable", true);
                if (!failure.retryable || sendsRemaining <= 1 || closed) return Future.failedFuture(failure);
                Promise<Void> delay = Promise.promise();
                vertx.setTimer(RETRY_DELAY_MS * (MAX_REPORT_SENDS - sendsRemaining + 1), id -> delay.complete());
                return delay.future().compose(ignored -> sendReport(url, request, sendsRemaining - 1));
            });
    }

    /** Reporting failure, not evidence of a transfer failure. No response body or credential is retained. */
    public static final class StatusReportException extends RuntimeException {
        private final boolean retryable;

        private StatusReportException(String message, boolean retryable) {
            super(message);
            this.retryable = retryable;
        }
    }

    private static String expectedAttemptState(String status) {
        return switch (status) {
            case "ACCEPTED" -> "OFFERED";
            case "IN_PROGRESS" -> "ACCEPTED";
            case "COMPLETED", "FAILED", "CANCELLED" -> "IN_PROGRESS";
            default -> throw new IllegalArgumentException("Unsupported attempt-aware status: " + status);
        };
    }

    /**
     * Shuts down the WebClient.
     * 
     * @return Future that completes when shutdown is done
     */
    public Future<Void> shutdown() {
        closed = true;
        logger.debug("Shutting down JobStatusReportingService WebClient");
        webClient.close();
        return Future.succeededFuture();
    }
}

