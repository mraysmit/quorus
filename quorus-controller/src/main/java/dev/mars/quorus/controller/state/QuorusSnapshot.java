/*
 * Copyright 2024 Quorus Project
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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import dev.mars.quorus.core.TransferJob;

import java.io.Serializable;
import java.time.Instant;
import java.util.Map;

/**
 * Snapshot of the Quorus state machine for persistence and recovery.
 */
public class QuorusSnapshot implements Serializable {

    private static final long serialVersionUID = 1L;

    private Map<String, TransferJob> transferJobs;
    private Map<String, String> systemMetadata;
    private long lastAppliedIndex;
    private Instant timestamp;

    /**
     * Default constructor for JSON deserialization.
     */
    public QuorusSnapshot() {
        this.timestamp = Instant.now();
    }

    /**
     * Constructor for JSON deserialization.
     */
    @JsonCreator
    public QuorusSnapshot(@JsonProperty("transferJobs") Map<String, TransferJob> transferJobs,
                         @JsonProperty("systemMetadata") Map<String, String> systemMetadata,
                         @JsonProperty("lastAppliedIndex") long lastAppliedIndex,
                         @JsonProperty("timestamp") Instant timestamp) {
        this.transferJobs = transferJobs;
        this.systemMetadata = systemMetadata;
        this.lastAppliedIndex = lastAppliedIndex;
        this.timestamp = timestamp != null ? timestamp : Instant.now();
    }

    /**
     * Get the transfer jobs.
     */
    public Map<String, TransferJob> getTransferJobs() {
        return transferJobs;
    }

    /**
     * Set the transfer jobs.
     */
    public void setTransferJobs(Map<String, TransferJob> transferJobs) {
        this.transferJobs = transferJobs;
    }

    /**
     * Get the system metadata.
     */
    public Map<String, String> getSystemMetadata() {
        return systemMetadata;
    }

    /**
     * Set the system metadata.
     */
    public void setSystemMetadata(Map<String, String> systemMetadata) {
        this.systemMetadata = systemMetadata;
    }

    /**
     * Get the last applied index.
     */
    public long getLastAppliedIndex() {
        return lastAppliedIndex;
    }

    /**
     * Set the last applied index.
     */
    public void setLastAppliedIndex(long lastAppliedIndex) {
        this.lastAppliedIndex = lastAppliedIndex;
    }

    /**
     * Get the snapshot timestamp.
     */
    public Instant getTimestamp() {
        return timestamp;
    }

    /**
     * Set the snapshot timestamp.
     */
    public void setTimestamp(Instant timestamp) {
        this.timestamp = timestamp;
    }

    @Override
    public String toString() {
        return "QuorusSnapshot{" +
                "transferJobs=" + (transferJobs != null ? transferJobs.size() : 0) + " jobs" +
                ", systemMetadata=" + (systemMetadata != null ? systemMetadata.size() : 0) + " entries" +
                ", lastAppliedIndex=" + lastAppliedIndex +
                ", timestamp=" + timestamp +
                '}';
    }
}
