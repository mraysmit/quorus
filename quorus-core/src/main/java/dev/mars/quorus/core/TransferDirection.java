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

/**
 * Enumeration representing the direction of a file transfer operation.
 * 
 * <p>The transfer direction is determined by the URI schemes of the source
 * and destination endpoints in a {@link TransferRequest}.</p>
 * 
 * <h2>Transfer Direction Types:</h2>
 * <ul>
 *   <li><b>DOWNLOAD:</b> Transfer from a remote source to a local file:// destination</li>
 *   <li><b>UPLOAD:</b> Transfer from a local file:// source to a remote destination</li>
 *   <li><b>REMOTE_TO_REMOTE:</b> Transfer between two remote endpoints (relay/proxy)</li>
 * </ul>
 * 
 * <h2>Examples:</h2>
 * <pre>
 * // DOWNLOAD: Remote → Local
 * sourceUri: sftp://server/file.dat
 * destinationUri: file:///local/file.dat
 * direction: DOWNLOAD
 * 
 * // UPLOAD: Local → Remote
 * sourceUri: file:///local/file.dat
 * destinationUri: sftp://server/file.dat
 * direction: UPLOAD
 * 
 * // REMOTE_TO_REMOTE: Remote → Remote (not yet supported)
 * sourceUri: sftp://server1/file.dat
 * destinationUri: ftp://server2/file.dat
 * direction: REMOTE_TO_REMOTE
 * </pre>
 * 
 * @author Mark Andrew Ray-Smith Cityline Ltd
 * @version 2.0
 * @since 2026-01-27
 * @see TransferRequest
 */
public enum TransferDirection {
    
    /**
     * Transfer from a remote source to a local filesystem destination.
     * 
     * <p>Example: Downloading a file from an SFTP server to local disk.</p>
     * <pre>
     * sourceUri: sftp://server/data.csv
     * destinationUri: file:///tmp/data.csv
     * </pre>
     */
    DOWNLOAD("Remote -> Local"),
    
    /**
     * Transfer from a local filesystem source to a remote destination.
     * 
     * <p>Example: Uploading a file from local disk to an FTP server.</p>
     * <pre>
     * sourceUri: file:///tmp/report.pdf
     * destinationUri: ftp://server/reports/report.pdf
     * </pre>
     */
    UPLOAD("Local -> Remote"),
    
    /**
     * Transfer between two remote endpoints (relay or proxy transfer).
     * 
     * <p><b>Note:</b> This direction is not yet supported in the current implementation.
     * At least one endpoint must be a local file:// URI.</p>
     * <pre>
     * sourceUri: sftp://server1/file.dat
     * destinationUri: s3://bucket/file.dat
     * </pre>
     */
    REMOTE_TO_REMOTE("Remote -> Remote");
    
    private final String description;
    
    TransferDirection(String description) {
        this.description = description;
    }
    
    /**
     * Returns a human-readable description of this transfer direction.
     * 
     * @return description string (e.g., "Remote -> Local")
     */
    public String getDescription() {
        return description;
    }
    
    /**
     * Returns true if this direction represents a download operation.
     * 
     * @return true if this is DOWNLOAD, false otherwise
     */
    public boolean isDownload() {
        return this == DOWNLOAD;
    }
    
    /**
     * Returns true if this direction represents an upload operation.
     * 
     * @return true if this is UPLOAD, false otherwise
     */
    public boolean isUpload() {
        return this == UPLOAD;
    }
    
    /**
     * Returns true if this direction represents a remote-to-remote transfer.
     * 
     * @return true if this is REMOTE_TO_REMOTE, false otherwise
     */
    public boolean isRemoteToRemote() {
        return this == REMOTE_TO_REMOTE;
    }
    
    @Override
    public String toString() {
        return name() + " (" + description + ")";
    }
}
