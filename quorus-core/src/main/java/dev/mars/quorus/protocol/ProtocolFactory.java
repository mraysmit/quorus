package dev.mars.quorus.protocol;

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

import dev.mars.quorus.core.TransferDirection;
import dev.mars.quorus.core.TransferRequest;
import io.vertx.core.Vertx;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
/**
 * Description for ProtocolFactory
 *
 * @author Mark Andrew Ray-Smith Cityline Ltd
 * @version 1.0
 * @since 2025-08-17
 */

public class ProtocolFactory {
    private static final Logger logger = LoggerFactory.getLogger(ProtocolFactory.class);

    private final Map<String, TransferProtocol> protocols;
    private final Vertx vertx;

    /**
     * Constructor with Vert.x dependency injection (recommended).
     * @param vertx Vert.x instance for reactive HTTP protocol
     */
    public ProtocolFactory(Vertx vertx) {
        this.vertx = java.util.Objects.requireNonNull(vertx, "Vertx instance cannot be null");
        this.protocols = new HashMap<>();
        logger.debug("Initializing ProtocolFactory with Vert.x instance");
        registerDefaultProtocols();
    }

    private void registerDefaultProtocols() {
        logger.debug("Registering default transfer protocols");
        
        // Register HTTP protocol for both http and https schemes
        logger.debug("Creating HttpTransferProtocol instance");
        HttpTransferProtocol httpProtocol = new HttpTransferProtocol(vertx);
        registerProtocol(httpProtocol);
        registerProtocolAlias("https", httpProtocol);

        // Register SMB protocol for both smb and cifs schemes
        logger.debug("Creating SmbTransferProtocol instance");
        SmbTransferProtocol smbProtocol = new SmbTransferProtocol();
        registerProtocol(smbProtocol);
        registerProtocolAlias("cifs", smbProtocol);

        // Register FTP protocol for both ftp and ftps schemes
        logger.debug("Creating FtpTransferProtocol instance");
        FtpTransferProtocol ftpProtocol = new FtpTransferProtocol();
        registerProtocol(ftpProtocol);
        registerProtocolAlias("ftps", ftpProtocol);

        // Register SFTP protocol
        logger.debug("Creating SftpTransferProtocol instance");
        registerProtocol(new SftpTransferProtocol());

        // Register NFS protocol
        logger.debug("Creating NfsTransferProtocol instance");
        registerProtocol(new NfsTransferProtocol());

        logger.info("Registered default transfer protocols: count={}, schemes={}", 
                   protocols.size(), String.join(", ", protocols.keySet()));
    }
    
    public void registerProtocol(TransferProtocol protocol) {
        String protocolName = protocol.getProtocolName().toLowerCase();
        protocols.put(protocolName, protocol);
        logger.info("Registered protocol: {}", protocolName);
        logger.debug("Protocol details: name={}, class={}, supportsResume={}, supportsPause={}",
                    protocolName, protocol.getClass().getSimpleName(), 
                    protocol.supportsResume(), protocol.supportsPause());
    }

    /**
     * Register a protocol under an alias scheme name
     */
    public void registerProtocolAlias(String alias, TransferProtocol protocol) {
        String aliasLower = alias.toLowerCase();
        protocols.put(aliasLower, protocol);
        logger.info("Registered protocol alias: {} -> {}", aliasLower, protocol.getProtocolName());
    }
    
    public TransferProtocol getProtocol(String protocolName) {
        if (protocolName == null) {
            logger.debug("getProtocol called with null protocol name");
            return null;
        }
        TransferProtocol protocol = protocols.get(protocolName.toLowerCase());
        if (protocol != null) {
            logger.debug("Protocol found: name={}, implementation={}", protocolName, protocol.getClass().getSimpleName());
        } else {
            logger.debug("Protocol not found: name={}", protocolName);
        }
        return protocol;
    }
    
    public boolean isProtocolSupported(String protocolName) {
        boolean supported = protocolName != null && protocols.containsKey(protocolName.toLowerCase());
        logger.debug("isProtocolSupported: protocol={}, supported={}", protocolName, supported);
        return supported;
    }
    
    public String[] getSupportedProtocols() {
        String[] supportedProtocols = protocols.keySet().toArray(new String[0]);
        logger.debug("getSupportedProtocols: count={}", supportedProtocols.length);
        return supportedProtocols;
    }
    
    /**
     * Get the appropriate protocol handler for a transfer request.
     * Determines the protocol based on the transfer direction:
     * <ul>
     *   <li>DOWNLOAD: Uses source URI scheme to determine protocol</li>
     *   <li>UPLOAD: Uses destination URI scheme to determine protocol</li>
     *   <li>REMOTE_TO_REMOTE: Not yet supported</li>
     * </ul>
     * 
     * @param request the transfer request
     * @return the protocol handler, or null if no matching protocol found
     * @throws UnsupportedOperationException if remote-to-remote transfers are attempted
     */
    public TransferProtocol getProtocol(TransferRequest request) {
        if (request == null) {
            logger.debug("getProtocol(TransferRequest): called with null request");
            return null;
        }
        
        TransferDirection direction = request.getDirection();
        String protocolScheme;
        
        switch (direction) {
            case DOWNLOAD:
                protocolScheme = request.getSourceUri().getScheme();
                logger.debug("getProtocol(TransferRequest): DOWNLOAD - using source scheme: {}", protocolScheme);
                break;
            case UPLOAD:
                protocolScheme = request.getDestinationUri().getScheme();
                logger.debug("getProtocol(TransferRequest): UPLOAD - using destination scheme: {}", protocolScheme);
                break;
            case REMOTE_TO_REMOTE:
                logger.error("getProtocol(TransferRequest): REMOTE_TO_REMOTE transfers not supported");
                throw new UnsupportedOperationException(
                    "Remote-to-remote transfers not yet implemented. " +
                    "At least one endpoint must be file:// (local filesystem).");
            default:
                logger.error("getProtocol(TransferRequest): Unknown direction: {}", direction);
                throw new IllegalArgumentException("Unknown transfer direction: " + direction);
        }
        
        TransferProtocol protocol = getProtocol(protocolScheme);
        if (protocol != null && protocol.canHandle(request)) {
            logger.debug("getProtocol(TransferRequest): found protocol {} for {} transfer", 
                protocol.getProtocolName(), direction);
            return protocol;
        }
        
        logger.warn("getProtocol(TransferRequest): no protocol found for scheme {} (direction={})", 
            protocolScheme, direction);
        return null;
    }
    
    /**
     * Remove a protocol from the registry
     */
    public void unregisterProtocol(String protocolName) {
        if (protocolName != null) {
            TransferProtocol removed = protocols.remove(protocolName.toLowerCase());
            if (removed != null) {
                logger.info("Unregistered protocol: {}", protocolName);
            } else {
                logger.debug("Protocol not found for unregistration: {}", protocolName);
            }
        } else {
            logger.debug("unregisterProtocol called with null protocol name");
        }
    }
}
