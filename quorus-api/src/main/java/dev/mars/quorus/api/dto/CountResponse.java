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

package dev.mars.quorus.api.dto;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.time.LocalDateTime;

/**
 * Response containing a count value.
 */
@Schema(description = "Count response")
public class CountResponse {

    @Schema(description = "Count value", example = "5")
    private int count;

    @Schema(description = "Timestamp when the count was retrieved")
    private LocalDateTime timestamp;

    /**
     * Default constructor.
     */
    public CountResponse() {
        this.timestamp = LocalDateTime.now();
    }

    /**
     * Constructor with count.
     */
    public CountResponse(int count) {
        this();
        this.count = count;
    }

    // Getters and setters
    public int getCount() {
        return count;
    }

    public void setCount(int count) {
        this.count = count;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }
}
