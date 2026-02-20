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

package dev.mars.quorus.controller.state;

import dev.mars.quorus.core.TransferJob;
import dev.mars.quorus.core.TransferStatus;

import java.util.Objects;

/**
 * Sealed interface for transfer job lifecycle commands.
 *
 * <p>Each permitted subtype carries only the fields relevant to its operation,
 * eliminating nullable "bag-of-fields" patterns. Pattern matching in
 * {@code switch} expressions provides compile-time exhaustiveness.
 *
 * <h3>Permitted subtypes</h3>
 * <ul>
 *   <li>{@link Create} — create a new transfer job</li>
 *   <li>{@link UpdateStatus} — change job status</li>
 *   <li>{@link UpdateProgress} — update bytes transferred</li>
 *   <li>{@link Delete} — remove a transfer job</li>
 * </ul>
 *
 * @author Mark Andrew Ray-Smith Cityline Ltd
 * @version 2.0
 * @since 2025-08-20
 */
public sealed interface TransferJobCommand extends RaftCommand
        permits TransferJobCommand.Create,
                TransferJobCommand.UpdateStatus,
                TransferJobCommand.UpdateProgress,
                TransferJobCommand.Delete {

    /** Common accessor: every subtype carries a job ID. */
    String jobId();

    /**
     * Create a new transfer job.
     *
     * @param jobId       the job identifier (derived from transferJob)
     * @param transferJob the full transfer job object
     */
    record Create(String jobId, TransferJob transferJob) implements TransferJobCommand {
        private static final long serialVersionUID = 1L;

        public Create {
            Objects.requireNonNull(jobId, "jobId");
            Objects.requireNonNull(transferJob, "transferJob");
        }
    }

    /**
     * Update the status of an existing transfer job.
     *
     * @param jobId  the job identifier
     * @param status the new status
     */
    record UpdateStatus(String jobId, TransferStatus status) implements TransferJobCommand {
        private static final long serialVersionUID = 1L;

        public UpdateStatus {
            Objects.requireNonNull(jobId, "jobId");
            Objects.requireNonNull(status, "status");
        }
    }

    /**
     * Update the progress (bytes transferred) of an existing transfer job.
     *
     * @param jobId            the job identifier
     * @param bytesTransferred the number of bytes transferred so far
     */
    record UpdateProgress(String jobId, long bytesTransferred) implements TransferJobCommand {
        private static final long serialVersionUID = 1L;

        public UpdateProgress {
            Objects.requireNonNull(jobId, "jobId");
        }
    }

    /**
     * Delete a transfer job.
     *
     * @param jobId the job identifier
     */
    record Delete(String jobId) implements TransferJobCommand {
        private static final long serialVersionUID = 1L;

        public Delete {
            Objects.requireNonNull(jobId, "jobId");
        }
    }

    // ── Factory methods (preserve existing API) ─────────────────

    /**
     * Create a new transfer job command.
     */
    static TransferJobCommand create(TransferJob transferJob) {
        return new Create(transferJob.getJobId(), transferJob);
    }

    /**
     * Create an update-status command.
     */
    static TransferJobCommand updateStatus(String jobId, TransferStatus status) {
        return new UpdateStatus(jobId, status);
    }

    /**
     * Create an update-progress command.
     */
    static TransferJobCommand updateProgress(String jobId, long bytesTransferred) {
        return new UpdateProgress(jobId, bytesTransferred);
    }

    /**
     * Create a delete command.
     */
    static TransferJobCommand delete(String jobId) {
        return new Delete(jobId);
    }
}
