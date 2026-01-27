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
import java.nio.file.Paths;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Represents a file transfer request with bidirectional support.
 * 
 * <p>Supports bidirectional transfers (uploads and downloads).
 * The transfer direction is automatically determined from the URI schemes:</p>
 * <ul>
 *   <li><b>DOWNLOAD:</b> Remote source → Local file:// destination</li>
 *   <li><b>UPLOAD:</b> Local file:// source → Remote destination</li>
 *   <li><b>REMOTE_TO_REMOTE:</b> Remote → Remote (not yet supported)</li>
 * </ul>
 * 
 * <p><b>Note:</b> Pre-production - no backward compatibility constraints.</p>
 * 
 * @author Mark Andrew Ray-Smith Cityline Ltd
 * @version 2.0
 * @since 2026-01-27
 */
@JsonDeserialize(builder = TransferRequest.Builder.class)
public final class TransferRequest implements Serializable {

    private static final long serialVersionUID = 2L; // Incremented for bidirectional support

    private final String requestId;

    private final URI sourceUri;

    private final String destinationPath;

    private final URI destinationUri;

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
        
        // Support both old destinationPath and new destinationUri for backward compatibility
        if (builder.destinationUri != null) {
            this.destinationUri = builder.destinationUri;
            this.destinationPath = null; // Not used anymore
        } else if (builder.destinationPath != null) {
            // Convert Path to file:// URI for backward compatibility
            this.destinationUri = builder.destinationPath.toUri();
            this.destinationPath = builder.destinationPath.toString(); // Keep for serialization compatibility
        } else {
            throw new NullPointerException("Destination URI or Path cannot be null");
        }
        
        // Validate URI combinations
        validateUriCombination(this.sourceUri, this.destinationUri);

        // Set optional fields with defaults
        this.protocol = builder.protocol != null ? builder.protocol : "http";
        this.metadata = Map.copyOf(builder.metadata != null ? builder.metadata : Map.of());
        this.createdAt = builder.createdAt != null ? builder.createdAt : Instant.now();
        this.expectedSize = builder.expectedSize;
        this.expectedChecksum = builder.expectedChecksum;
    }
    
    /**
     * Validates that URI combination is supported.
     * 
     * @throws IllegalArgumentException if both URIs are file:// (use Files.copy instead)
     * @throws UnsupportedOperationException if both URIs are remote (not yet supported)
     */
    private static void validateUriCombination(URI source, URI destination) {
        boolean sourceIsFile = "file".equalsIgnoreCase(source.getScheme());
        boolean destIsFile = "file".equalsIgnoreCase(destination.getScheme());
        
        if (sourceIsFile && destIsFile) {
            throw new IllegalArgumentException(
                "Both source and destination are local files. Use Files.copy() for local file-to-file operations.");
        }
        
        if (!sourceIsFile && !destIsFile) {
            throw new UnsupportedOperationException(
                "Remote-to-remote transfers not yet supported. At least one endpoint must be file:// (local filesystem).");
        }
    }

    public String getRequestId() { return requestId; }

    public URI getSourceUri() { return sourceUri; }

    /**
     * Returns the destination URI for this transfer.
     * 
     * <p>This is the new API introduced in version 2.0 for bidirectional support.</p>
     * 
     * @return the destination URI
     * @since 2.0
     */
    public URI getDestinationUri() { return destinationUri; }

    /**
     * Returns the destination path for this transfer.
     * 
     * <p>This method only works when the destination is a local file:// URI.
     * For upload operations (where destination is remote), this method throws
     * {@link UnsupportedOperationException}.</p>
     * 
     * @return the destination path
     * @throws UnsupportedOperationException if destination is not a file:// URI
     */
    public Path getDestinationPath() {
        if (!"file".equalsIgnoreCase(destinationUri.getScheme())) {
            throw new UnsupportedOperationException(
                "getDestinationPath() only valid for file:// destinations. Use getDestinationUri() instead. " +
                "Current destination: " + destinationUri);
        }
        return Paths.get(destinationUri);
    }

    public String getProtocol() { return protocol; }

    public Map<String, String> getMetadata() { return metadata; }

    public Instant getCreatedAt() { return createdAt; }

    public long getExpectedSize() { return expectedSize; }

    public String getExpectedChecksum() { return expectedChecksum; }
    
    /**
     * Returns the transfer direction (DOWNLOAD, UPLOAD, or REMOTE_TO_REMOTE).
     * 
     * <p>Direction is determined by URI schemes:</p>
     * <ul>
     *   <li>DOWNLOAD: source is remote, destination is file://</li>
     *   <li>UPLOAD: source is file://, destination is remote</li>
     *   <li>REMOTE_TO_REMOTE: both are remote (not yet supported)</li>
     * </ul>
     * 
     * @return the transfer direction
     * @since 2.0
     */
    public TransferDirection getDirection() {
        boolean sourceIsFile = "file".equalsIgnoreCase(sourceUri.getScheme());
        boolean destIsFile = "file".equalsIgnoreCase(destinationUri.getScheme());
        
        if (!sourceIsFile && destIsFile) {
            return TransferDirection.DOWNLOAD;
        } else if (sourceIsFile && !destIsFile) {
            return TransferDirection.UPLOAD;
        } else {
            return TransferDirection.REMOTE_TO_REMOTE;
        }
    }
    
    /**
     * Returns true if this is a download operation (remote → local).
     * 
     * @return true if downloading, false otherwise
     * @since 2.0
     */
    public boolean isDownload() {
        return getDirection() == TransferDirection.DOWNLOAD;
    }
    
    /**
     * Returns true if this is an upload operation (local → remote).
     * 
     * @return true if uploading, false otherwise
     * @since 2.0
     */
    public boolean isUpload() {
        return getDirection() == TransferDirection.UPLOAD;
    }
    
    /**
     * Returns true if this is a remote-to-remote operation.
     * 
     * <p><b>Note:</b> Remote-to-remote transfers are not yet supported.</p>
     * 
     * @return true if remote-to-remote, false otherwise
     * @since 2.0
     */
    public boolean isRemoteToRemote() {
        return getDirection() == TransferDirection.REMOTE_TO_REMOTE;
    }
    
    public static Builder builder() {
        return new Builder();
    }

    @JsonPOJOBuilder(withPrefix = "")
    public static class Builder {
        /** Unique identifier for the transfer request (auto-generated if not set) */
        private String requestId;

        /** Source URI where the file will be transferred from (required) */
        private URI sourceUri;

        /** 
         * Destination path where the file will be stored (backward compatibility only)
         * @deprecated Use destinationUri instead
         */
        @Deprecated(since = "2.0", forRemoval = true)
        private Path destinationPath;

        /** Destination URI where the file will be transferred to (required) */
        private URI destinationUri;

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

        /**
         * Sets the destination URI for the transfer (new API).
         * 
         * <p>Use this method for both downloads and uploads:</p>
         * <pre>
         * // Download
         * .destinationUri(URI.create("file:///tmp/file.dat"))
         * 
         * // Upload
         * .destinationUri(URI.create("sftp://server/file.dat"))
         * </pre>
         * 
         * @param destinationUri the destination URI
         * @return this builder
         * @since 2.0
         */
        public Builder destinationUri(URI destinationUri) {
            this.destinationUri = destinationUri;
            return this;
        }

        /**
         * Sets the destination path for the transfer.
         * 
         * <p>Path is automatically converted to file:// URI internally.</p>
         * 
         * @param destinationPath the destination path
         * @return this builder
         */
        public Builder destinationPath(Path destinationPath) {
            this.destinationPath = destinationPath;
            return this;
        }

        /**
         * Sets the destination path from string (JSON deserialization).
         * 
         * @param destinationPath the destination path as string
         * @return this builder
         */
        @JsonProperty("destinationPath")
        public Builder destinationPath(String destinationPath) {
            this.destinationPath = Paths.get(destinationPath);
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
                ", destinationUri=" + destinationUri +
                ", direction=" + getDirection() +
                ", protocol='" + protocol + '\'' +
                ", expectedSize=" + expectedSize +
                ", metadataKeys=" + (metadata != null ? metadata.keySet() : "[]") +
                ", createdAt=" + createdAt +
                '}';
    }
}
