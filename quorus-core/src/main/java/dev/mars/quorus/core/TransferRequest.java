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


import java.net.URI;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Represents a request to transfer a file from source to destination.
 * This is an immutable value object that contains all necessary information
 * to initiate a file transfer operation.
 */
public final class TransferRequest {
    private final String requestId;
    private final URI sourceUri;
    private final Path destinationPath;
    private final String protocol;
    private final Map<String, String> metadata;
    private final Instant createdAt;
    private final long expectedSize;
    private final String expectedChecksum;
    
    private TransferRequest(Builder builder) {
        this.requestId = builder.requestId != null ? builder.requestId : UUID.randomUUID().toString();
        this.sourceUri = Objects.requireNonNull(builder.sourceUri, "Source URI cannot be null");
        this.destinationPath = Objects.requireNonNull(builder.destinationPath, "Destination path cannot be null");
        this.protocol = builder.protocol != null ? builder.protocol : "http";
        this.metadata = Map.copyOf(builder.metadata != null ? builder.metadata : Map.of());
        this.createdAt = builder.createdAt != null ? builder.createdAt : Instant.now();
        this.expectedSize = builder.expectedSize;
        this.expectedChecksum = builder.expectedChecksum;
    }
    
    public String getRequestId() { return requestId; }
    public URI getSourceUri() { return sourceUri; }
    public Path getDestinationPath() { return destinationPath; }
    public String getProtocol() { return protocol; }
    public Map<String, String> getMetadata() { return metadata; }
    public Instant getCreatedAt() { return createdAt; }
    public long getExpectedSize() { return expectedSize; }
    public String getExpectedChecksum() { return expectedChecksum; }
    
    public static Builder builder() {
        return new Builder();
    }
    
    public static class Builder {
        private String requestId;
        private URI sourceUri;
        private Path destinationPath;
        private String protocol;
        private Map<String, String> metadata;
        private Instant createdAt;
        private long expectedSize = -1;
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
    
    @Override
    public String toString() {
        return "TransferRequest{" +
                "requestId='" + requestId + '\'' +
                ", sourceUri=" + sourceUri +
                ", destinationPath=" + destinationPath +
                ", protocol='" + protocol + '\'' +
                ", expectedSize=" + expectedSize +
                '}';
    }
}
