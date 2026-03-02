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

package dev.mars.quorus.monitoring;

/**
 * Unified health status enum for all health check components.
 * Replaces duplicate inner Status enums in ProtocolHealthCheck and TransferEngineHealthCheck.
 *
 * @author Mark Andrew Ray-Smith Cityline Ltd
 * @since 2025-12-17
 * @version 1.0
 */
public enum HealthStatus {
    /**
     * Component is healthy and fully operational.
     */
    UP,
    
    /**
     * Component is not operational.
     */
    DOWN,
    
    /**
     * Component is operational but experiencing issues or reduced capacity.
     */
    DEGRADED;
    
    /**
     * Returns true if the status indicates the component is operational (UP or DEGRADED).
     */
    public boolean isOperational() {
        return this == UP || this == DEGRADED;
    }
    
    /**
     * Returns true if the status indicates the component is fully healthy.
     */
    public boolean isHealthy() {
        return this == UP;
    }
}
