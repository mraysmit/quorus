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
import dev.mars.quorus.core.TransferAttempt;

import java.io.Serializable;
import java.time.Instant;
import java.util.Map;
import java.util.List;
/**
 * Description for QuorusSnapshot
 *
 * @author Mark Andrew Ray-Smith Cityline Ltd
 * @version 1.0
 * @since 2025-08-20
 */

public class QuorusSnapshot implements Serializable {

    private static final long serialVersionUID = 1L;

    private int schemaVersion;

    private Map<String, TransferJobSnapshot> transferJobs;
    private Map<String, AgentInfo> agents;
    private Map<String, String> systemMetadata;
    private Map<String, JobAssignment> jobAssignments;
    private Map<String, QueuedJob> jobQueue;
    private Map<String, RouteConfiguration> routes;
    private Map<String, TransferAttempt> transferAttempts;
    private Map<String, String> activeAttemptByJob;
    private Map<String, List<TransferEvent>> transferEvents;
    private long lastAppliedIndex;
    private Instant timestamp;

    public QuorusSnapshot() {
        this.schemaVersion = SchemaVersionRegistry.current(SchemaVersionRegistry.Contract.STATE_SNAPSHOT);
        this.timestamp = Instant.now();
    }

    @JsonCreator
    public QuorusSnapshot(@JsonProperty("schemaVersion") Integer schemaVersion,
                         @JsonProperty("transferJobs") Map<String, TransferJobSnapshot> transferJobs,
                         @JsonProperty("agents") Map<String, AgentInfo> agents,
                         @JsonProperty("systemMetadata") Map<String, String> systemMetadata,
                         @JsonProperty("jobAssignments") Map<String, JobAssignment> jobAssignments,
                         @JsonProperty("jobQueue") Map<String, QueuedJob> jobQueue,
                         @JsonProperty("routes") Map<String, RouteConfiguration> routes,
                         @JsonProperty("transferAttempts") Map<String, TransferAttempt> transferAttempts,
                         @JsonProperty("activeAttemptByJob") Map<String, String> activeAttemptByJob,
                         @JsonProperty("transferEvents") Map<String, List<TransferEvent>> transferEvents,
                         @JsonProperty("lastAppliedIndex") long lastAppliedIndex,
                         @JsonProperty("timestamp") Instant timestamp) {
        this.schemaVersion = schemaVersion == null ? 0 : schemaVersion;
        this.transferJobs = transferJobs;
        this.agents = agents;
        this.systemMetadata = systemMetadata;
        this.jobAssignments = jobAssignments;
        this.jobQueue = jobQueue;
        this.routes = routes;
        this.transferAttempts = transferAttempts;
        this.activeAttemptByJob = activeAttemptByJob;
        this.transferEvents = transferEvents;
        this.lastAppliedIndex = lastAppliedIndex;
        this.timestamp = timestamp != null ? timestamp : Instant.now();
    }

    public int getSchemaVersion() {
        return schemaVersion;
    }

    public void setSchemaVersion(int schemaVersion) {
        this.schemaVersion = schemaVersion;
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

    public Map<String, TransferAttempt> getTransferAttempts() {
        return transferAttempts;
    }

    public void setTransferAttempts(Map<String, TransferAttempt> transferAttempts) {
        this.transferAttempts = transferAttempts;
    }

    public Map<String, String> getActiveAttemptByJob() {
        return activeAttemptByJob;
    }

    public void setActiveAttemptByJob(Map<String, String> activeAttemptByJob) {
        this.activeAttemptByJob = activeAttemptByJob;
    }

    public Map<String, List<TransferEvent>> getTransferEvents() { return transferEvents; }

    public void setTransferEvents(Map<String, List<TransferEvent>> transferEvents) {
        this.transferEvents = transferEvents;
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
                "schemaVersion=" + schemaVersion +
                ", " +
                "transferJobs=" + (transferJobs != null ? transferJobs.size() : 0) + " jobs" +
                ", agents=" + (agents != null ? agents.size() : 0) + " agents" +
                ", systemMetadata=" + (systemMetadata != null ? systemMetadata.size() : 0) + " entries" +
                ", jobAssignments=" + (jobAssignments != null ? jobAssignments.size() : 0) + " assignments" +
                ", jobQueue=" + (jobQueue != null ? jobQueue.size() : 0) + " queued" +
                ", routes=" + (routes != null ? routes.size() : 0) + " routes" +
                ", transferAttempts=" + (transferAttempts != null ? transferAttempts.size() : 0) + " attempts" +
                ", lastAppliedIndex=" + lastAppliedIndex +
                ", timestamp=" + timestamp +
                '}';
    }
}
