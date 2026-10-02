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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.mars.quorus.agent.config.AgentConfiguration;
import dev.mars.quorus.core.TransferAttemptStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Objects;

/**
 * Service for reporting job status updates to the controller. Calls block the calling thread
 * (RT-05a); each returns once the controller has acknowledged the report, and throws
 * {@link StatusReportException} if it rejected the report or its acknowledgement never arrived.
 *
 * @author Mark Andrew Ray-Smith Cityline Ltd
 * @since 2025-12-11
 * @version 3.0
 */
public class JobStatusReportingService {

    private static final Logger logger = LoggerFactory.getLogger(JobStatusReportingService.class);
    private static final ObjectMapper JSON = new ObjectMapper();

    private final AgentConfiguration config;
    private final ControllerClient client;
    private volatile boolean closed;
    private static final int MAX_REPORT_SENDS = 3;
    private static final long RETRY_DELAY_MS = 100;

    public JobStatusReportingService(ControllerClient client, AgentConfiguration config) {
        this.client = Objects.requireNonNull(client, "client");
        this.config = Objects.requireNonNull(config, "config");
    }

    /** Report that a job has been accepted. */
    public void reportAccepted(String jobId) throws InterruptedException {
        reportStatus(jobId, "ACCEPTED", null, null);
    }

    public void reportAccepted(String jobId, String attemptId,
                               long fencingGeneration, long reportSequence) throws InterruptedException {
        reportStatus(jobId, "ACCEPTED", null, null,
                attemptId, fencingGeneration, reportSequence);
    }

    /** Report that a job is in progress. */
    public void reportInProgress(String jobId, long bytesTransferred) throws InterruptedException {
        reportStatus(jobId, "IN_PROGRESS", bytesTransferred, null);
    }

    public void reportInProgress(String jobId, long bytesTransferred, String attemptId,
                                 long fencingGeneration, long reportSequence) throws InterruptedException {
        reportStatus(jobId, "IN_PROGRESS", bytesTransferred, null,
                attemptId, fencingGeneration, reportSequence);
    }

    /**
     * Reports a running transfer's progress (ENG-10). It follows the start report, so the attempt is
     * expected to be IN_PROGRESS already; the controller requires the byte count not to decrease.
     */
    public void reportProgress(String jobId, long bytesTransferred, String attemptId,
                               long fencingGeneration, long reportSequence) throws InterruptedException {
        reportStatus(jobId, "IN_PROGRESS", bytesTransferred, null,
                attemptId, fencingGeneration, reportSequence, "IN_PROGRESS");
    }

    /** Report that a job has completed successfully. */
    public void reportCompleted(String jobId, long bytesTransferred) throws InterruptedException {
        reportStatus(jobId, "COMPLETED", bytesTransferred, null);
    }

    public void reportCompleted(String jobId, long bytesTransferred, String attemptId,
                                long fencingGeneration, long reportSequence) throws InterruptedException {
        reportStatus(jobId, "COMPLETED", bytesTransferred, null,
                attemptId, fencingGeneration, reportSequence);
    }

    /** Report that a job has failed. */
    public void reportFailed(String jobId, String errorMessage) throws InterruptedException {
        reportStatus(jobId, "FAILED", null, errorMessage);
    }

    /**
     * @throws IllegalArgumentException unless {@code expectedState} is ACCEPTED or IN_PROGRESS
     */
    public void reportFailed(String jobId, String errorMessage, String attemptId, long fencingGeneration,
                             long reportSequence, TransferAttemptStatus expectedState) throws InterruptedException {
        if (expectedState != TransferAttemptStatus.ACCEPTED && expectedState != TransferAttemptStatus.IN_PROGRESS) {
            throw new IllegalArgumentException("FAILED reports require an acknowledged ACCEPTED or IN_PROGRESS state");
        }
        reportStatus(jobId, "FAILED", null, errorMessage,
                attemptId, fencingGeneration, reportSequence, expectedState.name());
    }

    private void reportStatus(String jobId, String status, Long bytesTransferred, String errorMessage)
            throws InterruptedException {
        reportStatus(jobId, status, bytesTransferred, errorMessage, null, 0, 0);
    }

    private void reportStatus(String jobId, String status, Long bytesTransferred, String errorMessage,
                              String attemptId, long fencingGeneration, long reportSequence)
            throws InterruptedException {
        reportStatus(jobId, status, bytesTransferred, errorMessage, attemptId,
                fencingGeneration, reportSequence, attemptId == null ? null : expectedAttemptState(status));
    }

    private void reportStatus(String jobId, String status, Long bytesTransferred, String errorMessage,
                              String attemptId, long fencingGeneration, long reportSequence, String expectedState)
            throws InterruptedException {
        ObjectNode request = JSON.createObjectNode()
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
        try {
            sendReport(url, toJson(request), attemptId == null ? 1 : MAX_REPORT_SENDS);
            logger.debug("Job status reported: {} -> {}", jobId, status);
        } catch (StatusReportException e) {
            logger.error("Status report unresolved or rejected: jobId={}, status={}, attemptId={}, sequence={}: {}",
                    jobId, status, attemptId, reportSequence, e.getMessage());
            throw e;
        }
    }

    private void sendReport(String url, String body, int maxSends) throws InterruptedException {
        for (int send = 1; ; send++) {
            if (closed) {
                throw new StatusReportException("Q-REPORT-CLOSED", false);
            }
            StatusReportException failure;
            try {
                ControllerClient.Response response = client.postJson(url, body);
                if (response.isSuccess()) {
                    return;
                }
                int statusCode = response.status();
                boolean retryable = statusCode >= 500 || statusCode == 408 || statusCode == 429;
                failure = new StatusReportException(
                        "Q-REPORT-" + (retryable ? "UNRESOLVED" : "REJECTED") + ": HTTP " + statusCode, retryable);
            } catch (IOException e) {
                failure = new StatusReportException("Q-REPORT-UNRESOLVED: transport acknowledgement unavailable", true);
            }
            if (!failure.retryable || send >= maxSends || closed) {
                throw failure;
            }
            Thread.sleep(RETRY_DELAY_MS * send);
        }
    }

    private static String toJson(ObjectNode request) {
        try {
            return JSON.writeValueAsString(request);
        } catch (JsonProcessingException e) {
            throw new UncheckedIOException(e);
        }
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

    /** Refuses further reports: a report started after this fails with {@code Q-REPORT-CLOSED}. */
    public void shutdown() {
        closed = true;
        logger.debug("JobStatusReportingService closed");
    }
}
