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

package dev.mars.quorus.transfer.observability;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.DoubleHistogram;
import io.opentelemetry.api.metrics.LongCounter;
import io.opentelemetry.api.metrics.Meter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicLong;

/**
 * OpenTelemetry metrics for Quorus Core Transfer Engine.
 * Phase 8 of the OpenTelemetry migration.
 * 
 * Replaces the manual TransferMetrics class with OpenTelemetry instrumentation.
 * Provides comprehensive transfer metrics:
 * - quorus.transfer.active (gauge) - Currently active transfers
 * - quorus.transfer.total (counter) - Total transfers initiated
 * - quorus.transfer.completed (counter) - Successfully completed transfers
 * - quorus.transfer.failed (counter) - Failed transfers
 * - quorus.transfer.cancelled (counter) - Cancelled transfers
 * - quorus.transfer.bytes.total (counter) - Total bytes transferred
 * - quorus.transfer.duration.seconds (histogram) - Transfer duration distribution
 * - quorus.transfer.throughput.bytes_per_second (histogram) - Throughput distribution
 * - quorus.transfer.retries (counter) - Number of retry attempts
 *
 * @author Mark Andrew Ray-Smith Cityline Ltd
 * @since 2026-01-27
 * @version 1.0 (OpenTelemetry)
 */
public class TransferTelemetryMetrics {

    private static final Logger logger = LoggerFactory.getLogger(TransferTelemetryMetrics.class);
    private static final String METER_NAME = "quorus-core";

    // Singleton instance
    private static TransferTelemetryMetrics instance;

    // Counters
    private final LongCounter transfersTotal;
    private final LongCounter transfersCompleted;
    private final LongCounter transfersFailed;
    private final LongCounter transfersCancelled;
    private final LongCounter bytesTransferred;
    private final LongCounter retryAttempts;

    // Histograms
    private final DoubleHistogram transferDuration;
    private final DoubleHistogram transferThroughput;

    // Gauges (backed by AtomicLong)
    private final AtomicLong activeTransfers = new AtomicLong(0);

    // Attribute keys
    private static final AttributeKey<String> PROTOCOL_KEY = AttributeKey.stringKey("protocol");
    private static final AttributeKey<String> DIRECTION_KEY = AttributeKey.stringKey("direction");
    private static final AttributeKey<String> STATUS_KEY = AttributeKey.stringKey("status");
    private static final AttributeKey<String> ERROR_TYPE_KEY = AttributeKey.stringKey("error.type");

    private TransferTelemetryMetrics() {
        Meter meter = GlobalOpenTelemetry.getMeter(METER_NAME);

        // Initialize counters
        transfersTotal = meter.counterBuilder("quorus.transfer.total")
                .setDescription("Total number of transfers initiated")
                .setUnit("1")
                .build();

        transfersCompleted = meter.counterBuilder("quorus.transfer.completed")
                .setDescription("Number of successfully completed transfers")
                .setUnit("1")
                .build();

        transfersFailed = meter.counterBuilder("quorus.transfer.failed")
                .setDescription("Number of failed transfers")
                .setUnit("1")
                .build();

        transfersCancelled = meter.counterBuilder("quorus.transfer.cancelled")
                .setDescription("Number of cancelled transfers")
                .setUnit("1")
                .build();

        bytesTransferred = meter.counterBuilder("quorus.transfer.bytes.total")
                .setDescription("Total bytes transferred")
                .setUnit("By")
                .build();

        retryAttempts = meter.counterBuilder("quorus.transfer.retries")
                .setDescription("Number of transfer retry attempts")
                .setUnit("1")
                .build();

        // Initialize histograms
        transferDuration = meter.histogramBuilder("quorus.transfer.duration.seconds")
                .setDescription("Transfer duration in seconds")
                .setUnit("s")
                .build();

        transferThroughput = meter.histogramBuilder("quorus.transfer.throughput.bytes_per_second")
                .setDescription("Transfer throughput in bytes per second")
                .setUnit("By/s")
                .build();

        // Initialize gauges
        meter.gaugeBuilder("quorus.transfer.active")
                .setDescription("Number of currently active transfers")
                .ofLongs()
                .buildWithCallback(measurement -> measurement.record(activeTransfers.get()));

        logger.info("TransferTelemetryMetrics initialized");
    }

    /**
     * Get the singleton instance of TransferTelemetryMetrics.
     */
    public static synchronized TransferTelemetryMetrics getInstance() {
        if (instance == null) {
            instance = new TransferTelemetryMetrics();
        }
        return instance;
    }

    /**
     * Record a transfer started.
     */
    public void recordTransferStarted(String protocol, String direction) {
        Attributes attrs = Attributes.builder()
                .put(PROTOCOL_KEY, protocol)
                .put(DIRECTION_KEY, direction)
                .build();
        transfersTotal.add(1, attrs);
        activeTransfers.incrementAndGet();
    }

    /**
     * Record a transfer completed successfully.
     */
    public void recordTransferCompleted(String protocol, String direction, 
                                        long bytes, double durationSeconds) {
        activeTransfers.decrementAndGet();
        
        Attributes attrs = Attributes.builder()
                .put(PROTOCOL_KEY, protocol)
                .put(DIRECTION_KEY, direction)
                .build();
        
        transfersCompleted.add(1, attrs);
        bytesTransferred.add(bytes, attrs);
        transferDuration.record(durationSeconds, attrs);
        
        if (durationSeconds > 0) {
            double throughput = bytes / durationSeconds;
            transferThroughput.record(throughput, attrs);
        }
    }

    /**
     * Record a transfer failed.
     */
    public void recordTransferFailed(String protocol, String direction, String errorType) {
        activeTransfers.decrementAndGet();
        
        Attributes attrs = Attributes.builder()
                .put(PROTOCOL_KEY, protocol)
                .put(DIRECTION_KEY, direction)
                .put(ERROR_TYPE_KEY, errorType != null ? errorType : "unknown")
                .build();
        
        transfersFailed.add(1, attrs);
    }

    /**
     * Record a transfer cancelled.
     */
    public void recordTransferCancelled(String protocol, String direction) {
        activeTransfers.decrementAndGet();
        
        Attributes attrs = Attributes.builder()
                .put(PROTOCOL_KEY, protocol)
                .put(DIRECTION_KEY, direction)
                .build();
        
        transfersCancelled.add(1, attrs);
    }

    /**
     * Record a retry attempt.
     */
    public void recordRetryAttempt(String protocol, String direction, int attemptNumber) {
        Attributes attrs = Attributes.builder()
                .put(PROTOCOL_KEY, protocol)
                .put(DIRECTION_KEY, direction)
                .build();
        
        retryAttempts.add(1, attrs);
    }

    /**
     * Get the current number of active transfers.
     */
    public long getActiveTransfers() {
        return activeTransfers.get();
    }
}
