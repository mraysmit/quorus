package dev.mars.quorus.core;

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


import java.io.Serializable;
import java.net.URI;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
/**
 * Description for TransferRequest
 *
 * @author Mark Andrew Ray-Smith Cityline Ltd
 * @version 1.0
 * @since 2025-08-17
 */

public final class TransferRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    private final String requestId;

    private final URI sourceUri;

    private final String destinationPath;

    private final String protocol;

    private final Map<String, String> metadata;

    private final Instant createdAt;

    private final long expectedSize;

    private final String expectedChecksum;

    private TransferRequest(Builder builder) {
        // Generate unique request ID if not provided
        this.requestId = builder.requestId != null ? builder.requestId : UUID.randomUUID().toString();

        // Validate and set required fields
        this.sourceUri = Objects.requireNonNull(builder.sourceUri, "Source URI cannot be null");
        this.destinationPath = Objects.requireNonNull(builder.destinationPath, "Destination path cannot be null").toString();

        // Set optional fields with defaults
        this.protocol = builder.protocol != null ? builder.protocol : "http";
        this.metadata = Map.copyOf(builder.metadata != null ? builder.metadata : Map.of());
        this.createdAt = builder.createdAt != null ? builder.createdAt : Instant.now();
        this.expectedSize = builder.expectedSize;
        this.expectedChecksum = builder.expectedChecksum;
    }

    public String getRequestId() { return requestId; }

    public URI getSourceUri() { return sourceUri; }

    public Path getDestinationPath() { return Path.of(destinationPath); }

    public String getProtocol() { return protocol; }

    public Map<String, String> getMetadata() { return metadata; }

    public Instant getCreatedAt() { return createdAt; }

    public long getExpectedSize() { return expectedSize; }

    public String getExpectedChecksum() { return expectedChecksum; }
    
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        /** Unique identifier for the transfer request (auto-generated if not set) */
        private String requestId;

        /** Source URI where the file will be transferred from (required) */
        private URI sourceUri;

        /** Destination path where the file will be stored (required) */
        private Path destinationPath;

        /** Transfer protocol to use (defaults to "http") */
        private String protocol;

        /** Metadata key-value pairs for transfer customization */
        private Map<String, String> metadata;

        /** Timestamp when the request was created (defaults to current time) */
        private Instant createdAt;

        /** Expected file size in bytes (defaults to -1 for unknown) */
        private long expectedSize = -1;

        /** Expected checksum for integrity verification (optional) */
        private String expectedChecksum;

        public Builder requestId(String requestId) {
            this.requestId = requestId;
            return this;
        }

        public Builder sourceUri(URI sourceUri) {
            this.sourceUri = sourceUri;
            return this;
        }

        public Builder destinationPath(Path destinationPath) {
            this.destinationPath = destinationPath;
            return this;
        }

        public Builder protocol(String protocol) {
            this.protocol = protocol;
            return this;
        }

        public Builder metadata(Map<String, String> metadata) {
            this.metadata = metadata;
            return this;
        }

        public Builder metadata(String key, String value) {
            if (this.metadata == null) {
                this.metadata = new java.util.HashMap<>();
            } else if (!(this.metadata instanceof java.util.HashMap)) {
                this.metadata = new java.util.HashMap<>(this.metadata);
            }
            this.metadata.put(key, value);
            return this;
        }

        public Builder createdAt(Instant createdAt) {
            this.createdAt = createdAt;
            return this;
        }

        public Builder expectedSize(long expectedSize) {
            this.expectedSize = expectedSize;
            return this;
        }

        public Builder expectedChecksum(String expectedChecksum) {
            this.expectedChecksum = expectedChecksum;
            return this;
        }

        public TransferRequest build() {
            return new TransferRequest(this);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TransferRequest that = (TransferRequest) o;
        return Objects.equals(requestId, that.requestId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(requestId);
    }

    /**
     * Returns a string representation of this TransferRequest.
     *
     * <p>The string includes the key fields for debugging and logging purposes.
     * Sensitive information like authentication credentials in metadata are
     * not included in the string representation.</p>
     *
     * @return a string representation of this transfer request
     */
    @Override
    public String toString() {
        return "TransferRequest{" +
                "requestId='" + requestId + '\'' +
                ", sourceUri=" + sourceUri +
                ", destinationPath=" + destinationPath +
                ", protocol='" + protocol + '\'' +
                ", expectedSize=" + expectedSize +
                ", metadataKeys=" + (metadata != null ? metadata.keySet() : "[]") +
                ", createdAt=" + createdAt +
                '}';
    }
}
