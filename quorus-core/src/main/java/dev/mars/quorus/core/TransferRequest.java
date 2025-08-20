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
 * Immutable value object representing a complete file transfer request in the Quorus system.
 *
 * <p>This class encapsulates all the information necessary to initiate, execute, and track
 * a file transfer operation. It serves as the primary input to the transfer engine and
 * contains both required parameters (source, destination) and optional metadata for
 * advanced transfer features.</p>
 *
 * <h3>Core Components:</h3>
 * <ul>
 *   <li><strong>Source URI:</strong> Location of the file to transfer (supports multiple protocols)</li>
 *   <li><strong>Destination Path:</strong> Local file system path where file will be stored</li>
 *   <li><strong>Transfer Metadata:</strong> Additional parameters for transfer customization</li>
 *   <li><strong>Validation Data:</strong> Expected size and checksum for integrity verification</li>
 * </ul>
 *
 * <h3>Supported Protocols:</h3>
 * <ul>
 *   <li><strong>HTTP/HTTPS:</strong> Web-based file downloads with authentication support</li>
 *   <li><strong>FTP/FTPS:</strong> Traditional file transfer protocol</li>
 *   <li><strong>SFTP:</strong> Secure file transfer over SSH</li>
 *   <li><strong>File:</strong> Local file system copies</li>
 *   <li><strong>Custom:</strong> Extensible protocol support via plugins</li>
 * </ul>
 *
 * <h3>Immutability and Thread Safety:</h3>
 * <p>This class is immutable and thread-safe. All fields are final and collections are
 * defensively copied. Once created, a TransferRequest cannot be modified, ensuring
 * safe sharing across threads and components in the distributed system.</p>
 *
 * <h3>Builder Pattern:</h3>
 * <p>Instances are created using the Builder pattern to provide a fluent API and
 * ensure proper validation of required fields. The builder validates inputs and
 * provides sensible defaults for optional parameters.</p>
 *
 * <h3>Usage Example:</h3>
 * <pre>{@code
 * TransferRequest request = TransferRequest.builder()
 *     .sourceUri(URI.create("https://example.com/file.zip"))
 *     .destinationPath(Paths.get("/downloads/file.zip"))
 *     .expectedSize(1024 * 1024)  // 1MB
 *     .expectedChecksum("sha256:abc123...")
 *     .metadata("priority", "high")
 *     .metadata("retry-count", "3")
 *     .build();
 * }</pre>
 *
 * @author Quorus Development Team
 * @since 1.0
 * @see TransferJob
 * @see TransferResult
 * @see dev.mars.quorus.core.TransferEngine
 */
public final class TransferRequest {

    /**
     * Unique identifier for this transfer request.
     * Generated automatically if not provided. Used for tracking and correlation
     * across distributed components and log aggregation.
     */
    private final String requestId;

    /**
     * Source URI specifying the location of the file to transfer.
     * Supports multiple protocols (HTTP, FTP, SFTP, file://, etc.).
     * Must be a valid, accessible URI that the transfer engine can resolve.
     */
    private final URI sourceUri;

    /**
     * Destination path on the local file system where the transferred file will be stored.
     * Must be a valid path with appropriate write permissions. Parent directories
     * will be created automatically if they don't exist.
     */
    private final Path destinationPath;

    /**
     * Transfer protocol to use for this operation.
     * Defaults to "http" if not specified. Used by the transfer engine to select
     * the appropriate protocol handler and connection strategy.
     */
    private final String protocol;

    /**
     * Immutable map of metadata key-value pairs for transfer customization.
     * Common metadata includes: priority, retry-count, timeout, authentication,
     * compression settings, and workflow correlation identifiers.
     */
    private final Map<String, String> metadata;

    /**
     * Timestamp when this transfer request was created.
     * Used for tracking request age, timeout calculations, and audit trails.
     * Automatically set to current time if not provided.
     */
    private final Instant createdAt;

    /**
     * Expected size of the file to be transferred in bytes.
     * Used for progress calculation, bandwidth estimation, and integrity verification.
     * Set to -1 if size is unknown (streaming transfers).
     */
    private final long expectedSize;

    /**
     * Expected checksum of the file for integrity verification.
     * Format: "algorithm:hash" (e.g., "sha256:abc123...").
     * If provided, the transferred file will be verified against this checksum.
     */
    private final String expectedChecksum;

    /**
     * Private constructor that builds a TransferRequest from a Builder instance.
     *
     * <p>This constructor performs validation of required fields and applies default
     * values for optional parameters. It ensures the created instance is in a valid
     * state and ready for use by the transfer engine.</p>
     *
     * <p><strong>Validation Rules:</strong></p>
     * <ul>
     *   <li>sourceUri and destinationPath are required (null check)</li>
     *   <li>requestId is auto-generated if not provided</li>
     *   <li>protocol defaults to "http" if not specified</li>
     *   <li>metadata is defensively copied to ensure immutability</li>
     *   <li>createdAt defaults to current timestamp</li>
     * </ul>
     *
     * @param builder the Builder instance containing the transfer request parameters
     * @throws NullPointerException if sourceUri or destinationPath is null
     * @throws IllegalArgumentException if any parameter values are invalid
     */
    private TransferRequest(Builder builder) {
        // Generate unique request ID if not provided
        this.requestId = builder.requestId != null ? builder.requestId : UUID.randomUUID().toString();

        // Validate and set required fields
        this.sourceUri = Objects.requireNonNull(builder.sourceUri, "Source URI cannot be null");
        this.destinationPath = Objects.requireNonNull(builder.destinationPath, "Destination path cannot be null");

        // Set optional fields with defaults
        this.protocol = builder.protocol != null ? builder.protocol : "http";
        this.metadata = Map.copyOf(builder.metadata != null ? builder.metadata : Map.of());
        this.createdAt = builder.createdAt != null ? builder.createdAt : Instant.now();
        this.expectedSize = builder.expectedSize;
        this.expectedChecksum = builder.expectedChecksum;
    }

    /**
     * Returns the unique identifier for this transfer request.
     *
     * @return the request ID, never null
     */
    public String getRequestId() { return requestId; }

    /**
     * Returns the source URI specifying the location of the file to transfer.
     *
     * @return the source URI, never null
     */
    public URI getSourceUri() { return sourceUri; }

    /**
     * Returns the destination path where the transferred file will be stored.
     *
     * @return the destination path, never null
     */
    public Path getDestinationPath() { return destinationPath; }

    /**
     * Returns the transfer protocol to use for this operation.
     *
     * @return the protocol name (e.g., "http", "ftp", "sftp"), never null
     */
    public String getProtocol() { return protocol; }

    /**
     * Returns an immutable map of metadata key-value pairs.
     *
     * <p>The returned map is immutable and cannot be modified. Common metadata
     * keys include priority, retry-count, timeout, and authentication parameters.</p>
     *
     * @return immutable metadata map, never null but may be empty
     */
    public Map<String, String> getMetadata() { return metadata; }

    /**
     * Returns the timestamp when this transfer request was created.
     *
     * @return the creation timestamp, never null
     */
    public Instant getCreatedAt() { return createdAt; }

    /**
     * Returns the expected size of the file to be transferred in bytes.
     *
     * @return the expected file size in bytes, or -1 if unknown
     */
    public long getExpectedSize() { return expectedSize; }

    /**
     * Returns the expected checksum for integrity verification.
     *
     * @return the expected checksum in "algorithm:hash" format, or null if not specified
     */
    public String getExpectedChecksum() { return expectedChecksum; }
    
    /**
     * Creates a new Builder instance for constructing TransferRequest objects.
     *
     * <p>The Builder pattern provides a fluent API for creating TransferRequest instances
     * with proper validation and default values. This is the recommended way to create
     * TransferRequest objects.</p>
     *
     * @return a new Builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder class for constructing TransferRequest instances using the Builder pattern.
     *
     * <p>This builder provides a fluent API for setting transfer request parameters and
     * ensures proper validation before creating the immutable TransferRequest instance.
     * All methods return the builder instance to enable method chaining.</p>
     *
     * <h3>Usage Example:</h3>
     * <pre>{@code
     * TransferRequest request = TransferRequest.builder()
     *     .sourceUri(URI.create("https://example.com/file.zip"))
     *     .destinationPath(Paths.get("/downloads/file.zip"))
     *     .protocol("https")
     *     .expectedSize(1048576)
     *     .metadata("priority", "high")
     *     .build();
     * }</pre>
     *
     * @see TransferRequest#builder()
     */
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

        /**
         * Sets the unique identifier for this transfer request.
         *
         * <p>If not provided, a UUID will be automatically generated. The request ID
         * is used for tracking and correlation across distributed components.</p>
         *
         * @param requestId the unique request identifier
         * @return this builder instance for method chaining
         */
        public Builder requestId(String requestId) {
            this.requestId = requestId;
            return this;
        }

        /**
         * Sets the source URI specifying the location of the file to transfer.
         *
         * <p>This is a required field. The URI must be valid and accessible by the
         * transfer engine. Supported schemes include http, https, ftp, ftps, sftp, and file.</p>
         *
         * @param sourceUri the source URI, must not be null
         * @return this builder instance for method chaining
         */
        public Builder sourceUri(URI sourceUri) {
            this.sourceUri = sourceUri;
            return this;
        }

        /**
         * Sets the destination path where the transferred file will be stored.
         *
         * <p>This is a required field. The path must be valid and the parent directory
         * must be writable. Parent directories will be created if they don't exist.</p>
         *
         * @param destinationPath the destination file path, must not be null
         * @return this builder instance for method chaining
         */
        public Builder destinationPath(Path destinationPath) {
            this.destinationPath = destinationPath;
            return this;
        }

        /**
         * Sets the transfer protocol to use for this operation.
         *
         * <p>If not specified, defaults to "http". The protocol determines which
         * transfer handler will be used by the transfer engine.</p>
         *
         * @param protocol the protocol name (e.g., "http", "ftp", "sftp")
         * @return this builder instance for method chaining
         */
        public Builder protocol(String protocol) {
            this.protocol = protocol;
            return this;
        }

        /**
         * Sets the metadata map for transfer customization.
         *
         * <p>Metadata provides a way to pass additional parameters to the transfer
         * engine. Common keys include priority, retry-count, timeout, authentication
         * credentials, and workflow correlation identifiers.</p>
         *
         * @param metadata the metadata map, will be defensively copied
         * @return this builder instance for method chaining
         */
        public Builder metadata(Map<String, String> metadata) {
            this.metadata = metadata;
            return this;
        }

        /**
         * Adds a single metadata key-value pair.
         *
         * <p>This is a convenience method for adding individual metadata entries
         * without creating a full map. Can be called multiple times to build
         * the metadata map incrementally.</p>
         *
         * @param key the metadata key
         * @param value the metadata value
         * @return this builder instance for method chaining
         */
        public Builder metadata(String key, String value) {
            if (this.metadata == null) {
                this.metadata = new java.util.HashMap<>();
            } else if (!(this.metadata instanceof java.util.HashMap)) {
                this.metadata = new java.util.HashMap<>(this.metadata);
            }
            this.metadata.put(key, value);
            return this;
        }

        /**
         * Sets the creation timestamp for this transfer request.
         *
         * <p>If not specified, the current timestamp will be used automatically.
         * This is primarily useful for testing or when recreating requests from
         * persistent storage.</p>
         *
         * @param createdAt the creation timestamp
         * @return this builder instance for method chaining
         */
        public Builder createdAt(Instant createdAt) {
            this.createdAt = createdAt;
            return this;
        }

        /**
         * Sets the expected size of the file to be transferred.
         *
         * <p>This is used for progress calculation and bandwidth estimation.
         * If not known, leave as -1 (the default). Some protocols may be able
         * to determine the size automatically.</p>
         *
         * @param expectedSize the expected file size in bytes, or -1 if unknown
         * @return this builder instance for method chaining
         */
        public Builder expectedSize(long expectedSize) {
            this.expectedSize = expectedSize;
            return this;
        }

        /**
         * Sets the expected checksum for integrity verification.
         *
         * <p>The checksum should be in the format "algorithm:hash" (e.g.,
         * "sha256:abc123..."). If provided, the transferred file will be
         * verified against this checksum after transfer completion.</p>
         *
         * @param expectedChecksum the expected checksum in "algorithm:hash" format
         * @return this builder instance for method chaining
         */
        public Builder expectedChecksum(String expectedChecksum) {
            this.expectedChecksum = expectedChecksum;
            return this;
        }

        /**
         * Builds and returns an immutable TransferRequest instance.
         *
         * <p>This method validates that all required fields are set and creates
         * the final TransferRequest object. Once built, the request cannot be
         * modified.</p>
         *
         * @return a new immutable TransferRequest instance
         * @throws NullPointerException if required fields (sourceUri, destinationPath) are null
         * @throws IllegalArgumentException if any field values are invalid
         */
        public TransferRequest build() {
            return new TransferRequest(this);
        }
    }

    /**
     * Compares this TransferRequest with another object for equality.
     *
     * <p>Two TransferRequest objects are considered equal if they have the same
     * request ID. This is because request IDs are unique identifiers that
     * distinguish one transfer request from another.</p>
     *
     * @param o the object to compare with
     * @return true if the objects are equal, false otherwise
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TransferRequest that = (TransferRequest) o;
        return Objects.equals(requestId, that.requestId);
    }

    /**
     * Returns the hash code for this TransferRequest.
     *
     * <p>The hash code is based solely on the request ID, which is consistent
     * with the equals() implementation.</p>
     *
     * @return the hash code value for this object
     */
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
