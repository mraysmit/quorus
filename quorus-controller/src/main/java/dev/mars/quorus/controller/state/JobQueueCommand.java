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

import dev.mars.quorus.core.QueuedJob;
import dev.mars.quorus.core.JobPriority;

import java.time.Instant;
import java.util.Objects;

/**
 * Sealed interface for job queue commands in the Raft state machine.
 *
 * <p>Each permitted subtype carries only the fields relevant to its operation,
 * eliminating nullable "bag-of-fields" patterns. Pattern matching in
 * {@code switch} expressions provides compile-time exhaustiveness.
 *
 * <h3>Permitted subtypes</h3>
 * <ul>
 *   <li>{@link Enqueue} — add a job to the queue</li>
 *   <li>{@link Dequeue} — remove a job from the queue for assignment</li>
 *   <li>{@link Prioritize} — change job priority</li>
 *   <li>{@link Remove} — remove a job from the queue (cancel/delete)</li>
 *   <li>{@link Expedite} — move job to front of queue</li>
 *   <li>{@link UpdateRequirements} — update job requirements</li>
 * </ul>
 *
 * @author Mark Andrew Ray-Smith Cityline Ltd
 * @version 2.0
 * @since 2025-10-28
 */
public sealed interface JobQueueCommand extends RaftCommand
        permits JobQueueCommand.Enqueue,
                JobQueueCommand.Dequeue,
                JobQueueCommand.Prioritize,
                JobQueueCommand.Remove,
                JobQueueCommand.Expedite,
                JobQueueCommand.UpdateRequirements {

    /** Common accessor: every subtype carries a job ID. */
    String jobId();

    /** Common accessor: every subtype carries a timestamp. */
    Instant timestamp();

    /**
     * Add a job to the queue.
     *
     * @param jobId     the job identifier
     * @param queuedJob the full queued job
     * @param timestamp the command timestamp
     */
    record Enqueue(String jobId, QueuedJob queuedJob, Instant timestamp) implements JobQueueCommand {
        private static final long serialVersionUID = 1L;

        public Enqueue {
            Objects.requireNonNull(jobId, "jobId");
            Objects.requireNonNull(queuedJob, "queuedJob");
            Objects.requireNonNull(timestamp, "timestamp");
        }
    }

    /**
     * Remove a job from the queue for assignment.
     *
     * @param jobId     the job identifier
     * @param reason    the reason for dequeue (may be null)
     * @param timestamp the command timestamp
     */
    record Dequeue(String jobId, String reason, Instant timestamp) implements JobQueueCommand {
        private static final long serialVersionUID = 1L;

        public Dequeue {
            Objects.requireNonNull(jobId, "jobId");
            Objects.requireNonNull(timestamp, "timestamp");
        }
    }

    /**
     * Change job priority.
     *
     * @param jobId       the job identifier
     * @param newPriority the new priority level
     * @param reason      optional reason for priority change (may be null)
     * @param timestamp   the command timestamp
     */
    record Prioritize(String jobId, JobPriority newPriority, String reason, Instant timestamp) implements JobQueueCommand {
        private static final long serialVersionUID = 1L;

        public Prioritize {
            Objects.requireNonNull(jobId, "jobId");
            Objects.requireNonNull(newPriority, "newPriority");
            Objects.requireNonNull(timestamp, "timestamp");
        }
    }

    /**
     * Remove a job from the queue (cancel/delete).
     *
     * @param jobId     the job identifier
     * @param reason    the reason for removal (may be null)
     * @param timestamp the command timestamp
     */
    record Remove(String jobId, String reason, Instant timestamp) implements JobQueueCommand {
        private static final long serialVersionUID = 1L;

        public Remove {
            Objects.requireNonNull(jobId, "jobId");
            Objects.requireNonNull(timestamp, "timestamp");
        }
    }

    /**
     * Move job to front of queue.
     *
     * @param jobId     the job identifier
     * @param reason    the reason for expediting (may be null)
     * @param timestamp the command timestamp
     */
    record Expedite(String jobId, String reason, Instant timestamp) implements JobQueueCommand {
        private static final long serialVersionUID = 1L;

        public Expedite {
            Objects.requireNonNull(jobId, "jobId");
            Objects.requireNonNull(timestamp, "timestamp");
        }
    }

    /**
     * Update job requirements.
     *
     * @param jobId     the job identifier
     * @param queuedJob the updated queued job with new requirements
     * @param timestamp the command timestamp
     */
    record UpdateRequirements(String jobId, QueuedJob queuedJob, Instant timestamp) implements JobQueueCommand {
        private static final long serialVersionUID = 1L;

        public UpdateRequirements {
            Objects.requireNonNull(jobId, "jobId");
            Objects.requireNonNull(queuedJob, "queuedJob");
            Objects.requireNonNull(timestamp, "timestamp");
        }
    }

    // ── Factory methods (preserve existing API) ─────────────────

    /**
     * Create a command to add a job to the queue.
     */
    static JobQueueCommand enqueue(QueuedJob queuedJob) {
        Objects.requireNonNull(queuedJob, "Queued job cannot be null");
        return new Enqueue(queuedJob.getJobId(), queuedJob, Instant.now());
    }

    /**
     * Create a command to remove a job from the queue for assignment.
     */
    static JobQueueCommand dequeue(String jobId) {
        return new Dequeue(jobId, "Job assigned to agent", Instant.now());
    }

    /**
     * Create a command to change job priority.
     */
    static JobQueueCommand prioritize(String jobId, JobPriority newPriority, String reason) {
        return new Prioritize(jobId, newPriority, reason, Instant.now());
    }

    /**
     * Create a command to remove a job from the queue (cancel/delete).
     */
    static JobQueueCommand remove(String jobId, String reason) {
        return new Remove(jobId, reason, Instant.now());
    }

    /**
     * Create a command to expedite a job (move to front of queue).
     */
    static JobQueueCommand expedite(String jobId, String reason) {
        return new Expedite(jobId, reason, Instant.now());
    }

    /**
     * Create a command to update job requirements.
     */
    static JobQueueCommand updateRequirements(QueuedJob queuedJob) {
        Objects.requireNonNull(queuedJob, "Queued job cannot be null");
        return new UpdateRequirements(queuedJob.getJobId(), queuedJob, Instant.now());
    }
}
