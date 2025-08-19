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
 * Standard message response for API endpoints.
 */
@Schema(description = "Success message response")
public class MessageResponse {

    @Schema(description = "Success message", example = "Transfer cancelled successfully")
    private String message;

    @Schema(description = "Timestamp when the response was created")
    private LocalDateTime timestamp;

    /**
     * Default constructor.
     */
    public MessageResponse() {
        this.timestamp = LocalDateTime.now();
    }

    /**
     * Constructor with message.
     */
    public MessageResponse(String message) {
        this();
        this.message = message;
    }

    // Getters and setters
    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }
}
