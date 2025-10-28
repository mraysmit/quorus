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

/**
 * Represents the priority level of a job in the assignment queue.
 * Higher priority jobs are assigned to agents before lower priority jobs.
 * Within the same priority level, jobs are processed in FIFO order.
 * 
 * @author Quorus Team
 * @since 1.0.0
 */
public enum JobPriority {
    
    /**
     * Low priority jobs - processed when no higher priority jobs are available.
     * Typically used for background transfers, cleanup operations, or non-urgent tasks.
     */
    LOW(1, "Low priority - background processing"),
    
    /**
     * Normal priority jobs - the default priority level.
     * Used for standard file transfers and routine operations.
     */
    NORMAL(5, "Normal priority - standard processing"),
    
    /**
     * High priority jobs - processed before normal and low priority jobs.
     * Used for important transfers that should be completed quickly.
     */
    HIGH(8, "High priority - expedited processing"),
    
    /**
     * Critical priority jobs - highest priority, processed immediately.
     * Used for emergency transfers, system recovery, or time-sensitive operations.
     */
    CRITICAL(10, "Critical priority - immediate processing");
    
    private final int value;
    private final String description;
    
    JobPriority(int value, String description) {
        this.value = value;
        this.description = description;
    }
    
    /**
     * Get the numeric value of this priority.
     * Higher values indicate higher priority.
     */
    public int getValue() {
        return value;
    }
    
    /**
     * Get a human-readable description of this priority level.
     */
    public String getDescription() {
        return description;
    }
    
    /**
     * Check if this priority is higher than another priority.
     * 
     * @param other the priority to compare against
     * @return true if this priority is higher
     */
    public boolean isHigherThan(JobPriority other) {
        return this.value > other.value;
    }
    
    /**
     * Check if this priority is lower than another priority.
     * 
     * @param other the priority to compare against
     * @return true if this priority is lower
     */
    public boolean isLowerThan(JobPriority other) {
        return this.value < other.value;
    }
    
    /**
     * Get the maximum wait time in milliseconds for jobs of this priority.
     * After this time, the priority may be automatically escalated.
     * 
     * @return maximum wait time in milliseconds
     */
    public long getMaxWaitTimeMs() {
        switch (this) {
            case CRITICAL:
                return 30_000;      // 30 seconds
            case HIGH:
                return 300_000;     // 5 minutes
            case NORMAL:
                return 1_800_000;   // 30 minutes
            case LOW:
                return 7_200_000;   // 2 hours
            default:
                return Long.MAX_VALUE;
        }
    }
    
    /**
     * Get the next higher priority level for escalation.
     * Returns null if this is already the highest priority.
     * 
     * @return the next higher priority, or null if already highest
     */
    public JobPriority getNextHigherPriority() {
        switch (this) {
            case LOW:
                return NORMAL;
            case NORMAL:
                return HIGH;
            case HIGH:
                return CRITICAL;
            case CRITICAL:
                return null; // Already highest
            default:
                return null;
        }
    }
    
    /**
     * Get the timeout for agent assignment in milliseconds.
     * Higher priority jobs have shorter timeouts to ensure quick assignment.
     * 
     * @return assignment timeout in milliseconds
     */
    public long getAssignmentTimeoutMs() {
        switch (this) {
            case CRITICAL:
                return 5_000;       // 5 seconds
            case HIGH:
                return 15_000;      // 15 seconds
            case NORMAL:
                return 30_000;      // 30 seconds
            case LOW:
                return 60_000;      // 1 minute
            default:
                return 30_000;
        }
    }
    
    /**
     * Get the maximum number of retry attempts for jobs of this priority.
     * Higher priority jobs get more retry attempts.
     * 
     * @return maximum retry attempts
     */
    public int getMaxRetryAttempts() {
        switch (this) {
            case CRITICAL:
                return 5;   // Critical jobs get the most retries
            case HIGH:
                return 3;   // High priority gets moderate retries
            case NORMAL:
                return 2;   // Normal priority gets standard retries
            case LOW:
                return 1;   // Low priority gets minimal retries
            default:
                return 2;
        }
    }
    
    /**
     * Parse a priority from a string value.
     * Case-insensitive and supports both name and numeric values.
     * 
     * @param value the string value to parse
     * @return the corresponding JobPriority
     * @throws IllegalArgumentException if the value is not recognized
     */
    public static JobPriority fromString(String value) {
        if (value == null || value.trim().isEmpty()) {
            return NORMAL; // Default priority
        }
        
        String trimmed = value.trim().toUpperCase();
        
        // Try to parse as enum name
        try {
            return JobPriority.valueOf(trimmed);
        } catch (IllegalArgumentException e) {
            // Try to parse as numeric value
            try {
                int numericValue = Integer.parseInt(trimmed);
                return fromValue(numericValue);
            } catch (NumberFormatException ex) {
                throw new IllegalArgumentException("Invalid job priority: " + value);
            }
        }
    }
    
    /**
     * Get the priority that corresponds to the given numeric value.
     * Returns the priority with the closest value if exact match is not found.
     * 
     * @param value the numeric value
     * @return the corresponding JobPriority
     */
    public static JobPriority fromValue(int value) {
        JobPriority closest = NORMAL;
        int minDifference = Math.abs(value - NORMAL.value);
        
        for (JobPriority priority : values()) {
            int difference = Math.abs(value - priority.value);
            if (difference < minDifference) {
                minDifference = difference;
                closest = priority;
            }
        }
        
        return closest;
    }
    
    @Override
    public String toString() {
        return name() + " (" + value + ")";
    }
}
