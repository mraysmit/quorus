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

package dev.mars.quorus.core.exceptions;

/**
 * Structured error codes for Quorus operations. Each code uniquely identifies a failure
 * scenario for DevOps/observability tooling (alerting, dashboards, log queries).
 *
 * <p>Error code format: {@code QUORUS-XXYY} where:
 * <ul>
 *   <li>XX = category (10=FTP, 11=SFTP, 12=HTTP, 13=SMB, 14=NFS, 90=protocol routing)</li>
 *   <li>YY = specific error within category</li>
 * </ul>
 *
 * <p>Usage pattern in protocol adapters:
 * <pre>
 * logger.error("[{}] FTP download failed: requestId={}, host={}, error={}",
 *     QUORUS_1001.code(), requestId, host, e.getMessage());
 * logger.debug("FTP download exception details for request: {}", requestId, e);
 * </pre>
 *
 * @author Mark Andrew Ray-Smith Cityline Ltd
 * @since 2025-08-17
 * @version 1.0
 */
public enum QuorusErrorCode {

    // ── FTP/FTPS errors (10xx) ──────────────────────────────────────────────────
    /** FTP/FTPS transfer failed at top-level transfer() method */
    QUORUS_1000("QUORUS-1000", "FTP transfer failed"),
    /** FTP/FTPS download failed (network, I/O, protocol error) */
    QUORUS_1001("QUORUS-1001", "FTP download failed"),
    /** FTP/FTPS upload failed */
    QUORUS_1002("QUORUS-1002", "FTP upload failed"),
    /** Source file does not exist for FTP upload */
    QUORUS_1003("QUORUS-1003", "FTP upload source file not found"),
    /** FTP server rejected connection (non-220 welcome) */
    QUORUS_1004("QUORUS-1004", "FTP connection rejected by server"),
    /** FTP authentication failed (non-230 response) */
    QUORUS_1005("QUORUS-1005", "FTP authentication failed"),
    /** Failed to set FTP binary transfer mode */
    QUORUS_1006("QUORUS-1006", "FTP binary mode setup failed"),
    /** FTP destination URI missing required host */
    QUORUS_1007("QUORUS-1007", "FTP destination URI missing host"),
    /** FTP destination URI missing required path */
    QUORUS_1008("QUORUS-1008", "FTP destination URI missing path"),
    /** FTP server closed connection unexpectedly (EOF on readline) */
    QUORUS_1009("QUORUS-1009", "FTP server closed connection (EOF)"),

    // ── SFTP errors (11xx) ──────────────────────────────────────────────────────
    /** SFTP transfer failed at top-level transfer() method */
    QUORUS_1100("QUORUS-1100", "SFTP transfer failed"),
    /** SFTP transfer routing failed (in performSftpTransfer) */
    QUORUS_1101("QUORUS-1101", "SFTP transfer routing failed"),
    /** SFTP download failed */
    QUORUS_1102("QUORUS-1102", "SFTP download failed"),
    /** SFTP upload failed */
    QUORUS_1103("QUORUS-1103", "SFTP upload failed"),

    // ── HTTP/HTTPS errors (12xx) ────────────────────────────────────────────────
    /** HTTP transfer failed at sync wrapper */
    QUORUS_1200("QUORUS-1200", "HTTP transfer failed"),
    /** HTTP request validation failed */
    QUORUS_1201("QUORUS-1201", "HTTP request validation failed"),
    /** HTTP destination directory creation failed */
    QUORUS_1202("QUORUS-1202", "HTTP destination directory creation failed"),
    /** HTTP download received non-200 response */
    QUORUS_1203("QUORUS-1203", "HTTP download failed: non-200 response"),
    /** HTTP download received empty response body */
    QUORUS_1204("QUORUS-1204", "HTTP download failed: empty response"),
    /** HTTP download checksum mismatch */
    QUORUS_1205("QUORUS-1205", "HTTP download checksum mismatch"),
    /** HTTP download pipeline error */
    QUORUS_1206("QUORUS-1206", "HTTP download pipeline failed"),
    /** HTTP download setup error (before request sent) */
    QUORUS_1207("QUORUS-1207", "HTTP download setup failed"),
    /** HTTP upload source file not found */
    QUORUS_1208("QUORUS-1208", "HTTP upload source file not found"),
    /** HTTP upload pre-upload checksum mismatch */
    QUORUS_1209("QUORUS-1209", "HTTP upload checksum mismatch"),
    /** HTTP PUT received non-success response */
    QUORUS_1210("QUORUS-1210", "HTTP PUT failed: non-success response"),
    /** HTTP upload pipeline error */
    QUORUS_1211("QUORUS-1211", "HTTP upload pipeline failed"),
    /** HTTP upload setup error (before request sent) */
    QUORUS_1212("QUORUS-1212", "HTTP upload setup failed"),
    /** HTTP validation: source URI is null */
    QUORUS_1213("QUORUS-1213", "HTTP validation: source URI is null"),
    /** HTTP validation: destination path is null for download */
    QUORUS_1214("QUORUS-1214", "HTTP validation: destination path is null"),
    /** HTTP validation: destination URI is null for upload */
    QUORUS_1215("QUORUS-1215", "HTTP validation: destination URI is null"),
    /** HTTP validation: protocol cannot handle request type */
    QUORUS_1216("QUORUS-1216", "HTTP validation: unsupported request type"),

    // ── SMB errors (13xx) ───────────────────────────────────────────────────────
    /** SMB transfer failed at top-level transfer() method */
    QUORUS_1300("QUORUS-1300", "SMB transfer failed"),
    /** SMB download failed */
    QUORUS_1301("QUORUS-1301", "SMB download failed"),
    /** SMB upload failed */
    QUORUS_1302("QUORUS-1302", "SMB upload failed"),
    /** Source file does not exist for SMB upload */
    QUORUS_1303("QUORUS-1303", "SMB upload source file not found"),

    // ── NFS errors (14xx) ───────────────────────────────────────────────────────
    /** NFS transfer failed at top-level transfer() method */
    QUORUS_1400("QUORUS-1400", "NFS transfer failed"),
    /** NFS download failed */
    QUORUS_1401("QUORUS-1401", "NFS download failed"),
    /** NFS upload failed */
    QUORUS_1402("QUORUS-1402", "NFS upload failed"),
    /** Source file does not exist for NFS upload */
    QUORUS_1403("QUORUS-1403", "NFS upload source file not found"),

    // ── Protocol routing errors (90xx) ──────────────────────────────────────────
    /** Remote-to-remote transfers not supported */
    QUORUS_9001("QUORUS-9001", "Remote-to-remote transfer not supported"),
    /** Unknown transfer direction */
    QUORUS_9002("QUORUS-9002", "Unknown transfer direction");

    private final String code;
    private final String description;

    QuorusErrorCode(String code, String description) {
        this.code = code;
        this.description = description;
    }

    /** Returns the error code string, e.g. {@code "QUORUS-1001"} */
    public String code() {
        return code;
    }

    /** Returns a human-readable description of the error */
    public String description() {
        return description;
    }

    @Override
    public String toString() {
        return code + ": " + description;
    }
}
