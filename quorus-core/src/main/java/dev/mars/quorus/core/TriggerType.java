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
 * Types of triggers that can activate a route.
 * <p>
 * Each trigger type has different configuration parameters and evaluation logic.
 * The {@link TriggerType#COMPOSITE} type allows combining multiple triggers
 * with AND/OR logic.
 *
 * @author Mark Andrew Ray-Smith Cityline Ltd
 * @version 1.0
 * @since 2026-02-19
 */
public enum TriggerType {

    /**
     * File system event-based trigger.
     * Activates when file events (CREATE, MODIFY, DELETE) are detected
     * on the source agent matching configured patterns.
     */
    EVENT,

    /**
     * Cron-based time trigger.
     * Activates according to a cron expression and timezone.
     */
    TIME,

    /**
     * Periodic interval trigger.
     * Activates at regular intervals (e.g., every 15 minutes).
     */
    INTERVAL,

    /**
     * Batch file count trigger.
     * Activates when a minimum number of files accumulate,
     * or when a maximum wait time elapses.
     */
    BATCH,

    /**
     * Cumulative file size trigger.
     * Activates when total file size exceeds a threshold,
     * or when a maximum wait time elapses.
     */
    SIZE,

    /**
     * Composite trigger combining multiple conditions.
     * Uses AND/OR logic to combine child triggers.
     */
    COMPOSITE
}
