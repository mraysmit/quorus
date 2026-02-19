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

package dev.mars.quorus.core;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

/**
 * Configuration for a route trigger.
 * <p>
 * Each trigger type uses a subset of the fields:
 * <ul>
 *   <li>{@link TriggerType#EVENT}: {@code eventPatterns}, {@code excludePatterns}, {@code debounceMs}</li>
 *   <li>{@link TriggerType#TIME}: {@code cronExpression}, {@code timezone}</li>
 *   <li>{@link TriggerType#INTERVAL}: {@code intervalMinutes}</li>
 *   <li>{@link TriggerType#BATCH}: {@code fileCountThreshold}, {@code maxWaitMinutes}</li>
 *   <li>{@link TriggerType#SIZE}: {@code sizeThresholdMb}, {@code maxWaitMinutes}</li>
 *   <li>{@link TriggerType#COMPOSITE}: {@code compositeOperator}, {@code childTriggers}</li>
 * </ul>
 *
 * @author Mark Andrew Ray-Smith Cityline Ltd
 * @version 1.0
 * @since 2026-02-19
 */
public class TriggerConfiguration implements Serializable {

    private static final long serialVersionUID = 1L;

    private final TriggerType type;

    // EVENT trigger fields
    private final List<String> eventPatterns;
    private final List<String> excludePatterns;
    private final long debounceMs;

    // TIME trigger fields
    private final String cronExpression;
    private final String timezone;

    // INTERVAL trigger fields
    private final int intervalMinutes;

    // BATCH trigger fields
    private final int fileCountThreshold;

    // SIZE trigger fields
    private final long sizeThresholdMb;

    // BATCH + SIZE shared field
    private final int maxWaitMinutes;

    // COMPOSITE trigger fields
    private final String compositeOperator; // "AND" or "OR"
    private final List<TriggerConfiguration> childTriggers;

    @JsonCreator
    public TriggerConfiguration(
            @JsonProperty("type") TriggerType type,
            @JsonProperty("eventPatterns") List<String> eventPatterns,
            @JsonProperty("excludePatterns") List<String> excludePatterns,
            @JsonProperty("debounceMs") long debounceMs,
            @JsonProperty("cronExpression") String cronExpression,
            @JsonProperty("timezone") String timezone,
            @JsonProperty("intervalMinutes") int intervalMinutes,
            @JsonProperty("fileCountThreshold") int fileCountThreshold,
            @JsonProperty("sizeThresholdMb") long sizeThresholdMb,
            @JsonProperty("maxWaitMinutes") int maxWaitMinutes,
            @JsonProperty("compositeOperator") String compositeOperator,
            @JsonProperty("childTriggers") List<TriggerConfiguration> childTriggers) {
        this.type = type;
        this.eventPatterns = eventPatterns;
        this.excludePatterns = excludePatterns;
        this.debounceMs = debounceMs;
        this.cronExpression = cronExpression;
        this.timezone = timezone;
        this.intervalMinutes = intervalMinutes;
        this.fileCountThreshold = fileCountThreshold;
        this.sizeThresholdMb = sizeThresholdMb;
        this.maxWaitMinutes = maxWaitMinutes;
        this.compositeOperator = compositeOperator;
        this.childTriggers = childTriggers;
    }

    /**
     * Creates an EVENT trigger configuration.
     */
    public static TriggerConfiguration event(List<String> patterns, List<String> excludePatterns, long debounceMs) {
        return new TriggerConfiguration(TriggerType.EVENT, patterns, excludePatterns, debounceMs,
                null, null, 0, 0, 0, 0, null, null);
    }

    /**
     * Creates a TIME (cron) trigger configuration.
     */
    public static TriggerConfiguration time(String cronExpression, String timezone) {
        return new TriggerConfiguration(TriggerType.TIME, null, null, 0,
                cronExpression, timezone, 0, 0, 0, 0, null, null);
    }

    /**
     * Creates an INTERVAL trigger configuration.
     */
    public static TriggerConfiguration interval(int intervalMinutes) {
        return new TriggerConfiguration(TriggerType.INTERVAL, null, null, 0,
                null, null, intervalMinutes, 0, 0, 0, null, null);
    }

    /**
     * Creates a BATCH trigger configuration.
     */
    public static TriggerConfiguration batch(int fileCountThreshold, int maxWaitMinutes) {
        return new TriggerConfiguration(TriggerType.BATCH, null, null, 0,
                null, null, 0, fileCountThreshold, 0, maxWaitMinutes, null, null);
    }

    /**
     * Creates a SIZE trigger configuration.
     */
    public static TriggerConfiguration size(long sizeThresholdMb, int maxWaitMinutes) {
        return new TriggerConfiguration(TriggerType.SIZE, null, null, 0,
                null, null, 0, 0, sizeThresholdMb, maxWaitMinutes, null, null);
    }

    /**
     * Creates a COMPOSITE trigger configuration.
     *
     * @param operator "AND" or "OR"
     * @param children the child triggers to combine
     */
    public static TriggerConfiguration composite(String operator, List<TriggerConfiguration> children) {
        return new TriggerConfiguration(TriggerType.COMPOSITE, null, null, 0,
                null, null, 0, 0, 0, 0, operator, children);
    }

    public TriggerType getType() {
        return type;
    }

    public List<String> getEventPatterns() {
        return eventPatterns;
    }

    public List<String> getExcludePatterns() {
        return excludePatterns;
    }

    public long getDebounceMs() {
        return debounceMs;
    }

    public String getCronExpression() {
        return cronExpression;
    }

    public String getTimezone() {
        return timezone;
    }

    public int getIntervalMinutes() {
        return intervalMinutes;
    }

    public int getFileCountThreshold() {
        return fileCountThreshold;
    }

    public long getSizeThresholdMb() {
        return sizeThresholdMb;
    }

    public int getMaxWaitMinutes() {
        return maxWaitMinutes;
    }

    public String getCompositeOperator() {
        return compositeOperator;
    }

    public List<TriggerConfiguration> getChildTriggers() {
        return childTriggers;
    }

    @Override
    public String toString() {
        return "TriggerConfiguration{" +
                "type=" + type +
                ", cronExpression='" + cronExpression + '\'' +
                ", intervalMinutes=" + intervalMinutes +
                ", fileCountThreshold=" + fileCountThreshold +
                ", sizeThresholdMb=" + sizeThresholdMb +
                '}';
    }
}
