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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import dev.mars.quorus.agent.AgentInfo;

import java.io.Serializable;
import java.time.Instant;
import java.util.Map;

public class QuorusSnapshot implements Serializable {

    private static final long serialVersionUID = 1L;

    private Map<String, TransferJobSnapshot> transferJobs;
    private Map<String, AgentInfo> agents;
    private Map<String, String> systemMetadata;
    private long lastAppliedIndex;
    private Instant timestamp;

    public QuorusSnapshot() {
        this.timestamp = Instant.now();
    }

    @JsonCreator
    public QuorusSnapshot(@JsonProperty("transferJobs") Map<String, TransferJobSnapshot> transferJobs,
                         @JsonProperty("agents") Map<String, AgentInfo> agents,
                         @JsonProperty("systemMetadata") Map<String, String> systemMetadata,
                         @JsonProperty("lastAppliedIndex") long lastAppliedIndex,
                         @JsonProperty("timestamp") Instant timestamp) {
        this.transferJobs = transferJobs;
        this.agents = agents;
        this.systemMetadata = systemMetadata;
        this.lastAppliedIndex = lastAppliedIndex;
        this.timestamp = timestamp != null ? timestamp : Instant.now();
    }

    public Map<String, TransferJobSnapshot> getTransferJobs() {
        return transferJobs;
    }

    public void setTransferJobs(Map<String, TransferJobSnapshot> transferJobs) {
        this.transferJobs = transferJobs;
    }

    public Map<String, AgentInfo> getAgents() {
        return agents;
    }

    public void setAgents(Map<String, AgentInfo> agents) {
        this.agents = agents;
    }

    public Map<String, String> getSystemMetadata() {
        return systemMetadata;
    }

    public void setSystemMetadata(Map<String, String> systemMetadata) {
        this.systemMetadata = systemMetadata;
    }

    public long getLastAppliedIndex() {
        return lastAppliedIndex;
    }

    public void setLastAppliedIndex(long lastAppliedIndex) {
        this.lastAppliedIndex = lastAppliedIndex;
    }

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
                ", agents=" + (agents != null ? agents.size() : 0) + " agents" +
                ", systemMetadata=" + (systemMetadata != null ? systemMetadata.size() : 0) + " entries" +
                ", lastAppliedIndex=" + lastAppliedIndex +
                ", timestamp=" + timestamp +
                '}';
    }
}
