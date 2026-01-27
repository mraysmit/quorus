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

package dev.mars.quorus.agent.observability;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.LongCounter;
import io.opentelemetry.api.metrics.LongUpDownCounter;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.api.metrics.ObservableLongGauge;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

/**
 * OpenTelemetry metrics for Quorus Agent.
 * Phase 6 of the OpenTelemetry migration.
 * 
 * Provides 12 agent-specific metrics:
 * - quorus.agent.status (gauge) - Agent status (0=stopped, 1=running, 2=error)
 * - quorus.agent.heartbeats.total (counter) - Total heartbeats sent
 * - quorus.agent.heartbeats.failed (counter) - Failed heartbeats
 * - quorus.agent.registrations.total (counter) - Total registration attempts
 * - quorus.agent.registrations.success (counter) - Successful registrations
 * - quorus.agent.jobs.polled (counter) - Total jobs polled from controller
 * - quorus.agent.jobs.active (gauge) - Currently active jobs
 * - quorus.agent.jobs.completed (counter) - Completed jobs
 * - quorus.agent.jobs.failed (counter) - Failed jobs
 * - quorus.agent.transfers.bytes.total (counter) - Total bytes transferred
 * - quorus.agent.transfers.duration.seconds (counter) - Total transfer duration
 * - quorus.agent.uptime.seconds (gauge) - Agent uptime in seconds
 *
 * @author Mark Andrew Ray-Smith Cityline Ltd
 * @since 2026-01-27
 * @version 1.0 (OpenTelemetry)
 */
public class AgentMetrics {

    private static final Logger logger = LoggerFactory.getLogger(AgentMetrics.class);
    private static final String METER_NAME = "quorus-agent";

    // Counters
    private final LongCounter heartbeatsTotal;
    private final LongCounter heartbeatsFailed;
    private final LongCounter registrationsTotal;
    private final LongCounter registrationsSuccess;
    private final LongCounter jobsPolled;
    private final LongCounter jobsCompleted;
    private final LongCounter jobsFailed;
    private final LongCounter transferBytesTotal;
    private final LongCounter transferDurationSeconds;

    // Gauges (backed by AtomicLong for thread-safe updates)
    private final AtomicLong agentStatus = new AtomicLong(0);
    private final AtomicLong activeJobs = new AtomicLong(0);
    private final Supplier<Long> uptimeSupplier;

    // Attribute keys
    private static final AttributeKey<String> AGENT_ID_KEY = AttributeKey.stringKey("agent.id");
    private static final AttributeKey<String> PROTOCOL_KEY = AttributeKey.stringKey("protocol");
    private static final AttributeKey<String> DIRECTION_KEY = AttributeKey.stringKey("direction");

    private final String agentId;

    /**
     * Initialize agent metrics with the given agent ID.
     *
     * @param agentId the unique agent identifier
     * @param startTimeMillis the agent start time in milliseconds
     */
    public AgentMetrics(String agentId, long startTimeMillis) {
        this.agentId = agentId;
        this.uptimeSupplier = () -> (System.currentTimeMillis() - startTimeMillis) / 1000;

        Meter meter = GlobalOpenTelemetry.getMeter(METER_NAME);

        // Initialize counters
        heartbeatsTotal = meter.counterBuilder("quorus.agent.heartbeats.total")
                .setDescription("Total number of heartbeats sent to controller")
                .setUnit("1")
                .build();

        heartbeatsFailed = meter.counterBuilder("quorus.agent.heartbeats.failed")
                .setDescription("Number of failed heartbeat attempts")
                .setUnit("1")
                .build();

        registrationsTotal = meter.counterBuilder("quorus.agent.registrations.total")
                .setDescription("Total number of registration attempts")
                .setUnit("1")
                .build();

        registrationsSuccess = meter.counterBuilder("quorus.agent.registrations.success")
                .setDescription("Number of successful registrations")
                .setUnit("1")
                .build();

        jobsPolled = meter.counterBuilder("quorus.agent.jobs.polled")
                .setDescription("Total number of jobs polled from controller")
                .setUnit("1")
                .build();

        jobsCompleted = meter.counterBuilder("quorus.agent.jobs.completed")
                .setDescription("Total number of completed jobs")
                .setUnit("1")
                .build();

        jobsFailed = meter.counterBuilder("quorus.agent.jobs.failed")
                .setDescription("Total number of failed jobs")
                .setUnit("1")
                .build();

        transferBytesTotal = meter.counterBuilder("quorus.agent.transfers.bytes.total")
                .setDescription("Total bytes transferred")
                .setUnit("By")
                .build();

        transferDurationSeconds = meter.counterBuilder("quorus.agent.transfers.duration.seconds")
                .setDescription("Total transfer duration in seconds")
                .setUnit("s")
                .build();

        // Initialize gauges
        meter.gaugeBuilder("quorus.agent.status")
                .setDescription("Agent status (0=stopped, 1=running, 2=error)")
                .ofLongs()
                .buildWithCallback(measurement -> 
                        measurement.record(agentStatus.get(), 
                                Attributes.of(AGENT_ID_KEY, agentId)));

        meter.gaugeBuilder("quorus.agent.jobs.active")
                .setDescription("Number of currently active jobs")
                .ofLongs()
                .buildWithCallback(measurement -> 
                        measurement.record(activeJobs.get(), 
                                Attributes.of(AGENT_ID_KEY, agentId)));

        meter.gaugeBuilder("quorus.agent.uptime.seconds")
                .setDescription("Agent uptime in seconds")
                .ofLongs()
                .buildWithCallback(measurement -> 
                        measurement.record(uptimeSupplier.get(), 
                                Attributes.of(AGENT_ID_KEY, agentId)));

        logger.info("AgentMetrics initialized for agent: {}", agentId);
    }

    // Status methods
    public void setStatusRunning() {
        agentStatus.set(1);
    }

    public void setStatusStopped() {
        agentStatus.set(0);
    }

    public void setStatusError() {
        agentStatus.set(2);
    }

    // Heartbeat methods
    public void recordHeartbeat(boolean success) {
        Attributes attrs = Attributes.of(AGENT_ID_KEY, agentId);
        heartbeatsTotal.add(1, attrs);
        if (!success) {
            heartbeatsFailed.add(1, attrs);
        }
    }

    // Registration methods
    public void recordRegistration(boolean success) {
        Attributes attrs = Attributes.of(AGENT_ID_KEY, agentId);
        registrationsTotal.add(1, attrs);
        if (success) {
            registrationsSuccess.add(1, attrs);
        }
    }

    // Job methods
    public void recordJobPolled(int count) {
        jobsPolled.add(count, Attributes.of(AGENT_ID_KEY, agentId));
    }

    public void recordJobStarted() {
        activeJobs.incrementAndGet();
    }

    public void recordJobCompleted(boolean success, String protocol, String direction, 
                                   long bytesTransferred, long durationSeconds) {
        activeJobs.decrementAndGet();
        
        Attributes attrs = Attributes.builder()
                .put(AGENT_ID_KEY, agentId)
                .put(PROTOCOL_KEY, protocol)
                .put(DIRECTION_KEY, direction)
                .build();

        if (success) {
            jobsCompleted.add(1, attrs);
        } else {
            jobsFailed.add(1, attrs);
        }

        transferBytesTotal.add(bytesTransferred, attrs);
        transferDurationSeconds.add(durationSeconds, attrs);
    }
}
