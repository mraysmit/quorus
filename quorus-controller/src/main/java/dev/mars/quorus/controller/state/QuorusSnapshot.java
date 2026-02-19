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
import dev.mars.quorus.core.JobAssignment;
import dev.mars.quorus.core.QueuedJob;
import dev.mars.quorus.core.RouteConfiguration;

import java.io.Serializable;
import java.time.Instant;
import java.util.Map;
/**
 * Description for QuorusSnapshot
 *
 * @author Mark Andrew Ray-Smith Cityline Ltd
 * @version 1.0
 * @since 2025-08-20
 */

public class QuorusSnapshot implements Serializable {

    private static final long serialVersionUID = 1L;

    private Map<String, TransferJobSnapshot> transferJobs;
    private Map<String, AgentInfo> agents;
    private Map<String, String> systemMetadata;
    private Map<String, JobAssignment> jobAssignments;
    private Map<String, QueuedJob> jobQueue;
    private Map<String, RouteConfiguration> routes;
    private long lastAppliedIndex;
    private Instant timestamp;

    public QuorusSnapshot() {
        this.timestamp = Instant.now();
    }

    @JsonCreator
    public QuorusSnapshot(@JsonProperty("transferJobs") Map<String, TransferJobSnapshot> transferJobs,
                         @JsonProperty("agents") Map<String, AgentInfo> agents,
                         @JsonProperty("systemMetadata") Map<String, String> systemMetadata,
                         @JsonProperty("jobAssignments") Map<String, JobAssignment> jobAssignments,
                         @JsonProperty("jobQueue") Map<String, QueuedJob> jobQueue,
                         @JsonProperty("routes") Map<String, RouteConfiguration> routes,
                         @JsonProperty("lastAppliedIndex") long lastAppliedIndex,
                         @JsonProperty("timestamp") Instant timestamp) {
        this.transferJobs = transferJobs;
        this.agents = agents;
        this.systemMetadata = systemMetadata;
        this.jobAssignments = jobAssignments;
        this.jobQueue = jobQueue;
        this.routes = routes;
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

    public Map<String, JobAssignment> getJobAssignments() {
        return jobAssignments;
    }

    public void setJobAssignments(Map<String, JobAssignment> jobAssignments) {
        this.jobAssignments = jobAssignments;
    }

    public Map<String, QueuedJob> getJobQueue() {
        return jobQueue;
    }

    public void setJobQueue(Map<String, QueuedJob> jobQueue) {
        this.jobQueue = jobQueue;
    }

    public Map<String, RouteConfiguration> getRoutes() {
        return routes;
    }

    public void setRoutes(Map<String, RouteConfiguration> routes) {
        this.routes = routes;
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
                ", jobAssignments=" + (jobAssignments != null ? jobAssignments.size() : 0) + " assignments" +
                ", jobQueue=" + (jobQueue != null ? jobQueue.size() : 0) + " queued" +
                ", routes=" + (routes != null ? routes.size() : 0) + " routes" +
                ", lastAppliedIndex=" + lastAppliedIndex +
                ", timestamp=" + timestamp +
                '}';
    }
}
