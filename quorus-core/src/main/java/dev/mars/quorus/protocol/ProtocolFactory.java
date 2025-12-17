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


import io.vertx.core.Vertx;

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

public class ProtocolFactory {
    private static final Logger logger = Logger.getLogger(ProtocolFactory.class.getName());

    private final Map<String, TransferProtocol> protocols;
    private final Vertx vertx;

    /**
     * Constructor without Vertx for backward compatibility.
     * @deprecated Use {@link #ProtocolFactory(Vertx)} instead
     */
    @Deprecated
    public ProtocolFactory() {
        this(null);
        logger.warning("Using deprecated constructor - HTTP protocol will use blocking I/O");
    }

    /**
     * Constructor with Vert.x dependency injection (recommended).
     * @param vertx Vert.x instance for reactive HTTP protocol
     */
    public ProtocolFactory(Vertx vertx) {
        this.vertx = vertx;
        this.protocols = new HashMap<>();
        registerDefaultProtocols();
    }

    private void registerDefaultProtocols() {
        // Register HTTP protocol for both http and https schemes
        HttpTransferProtocol httpProtocol = vertx != null
            ? new HttpTransferProtocol(vertx)
            : new HttpTransferProtocol();
        registerProtocol(httpProtocol);
        registerProtocolAlias("https", httpProtocol);

        // Register SMB protocol for both smb and cifs schemes
        SmbTransferProtocol smbProtocol = new SmbTransferProtocol();
        registerProtocol(smbProtocol);
        registerProtocolAlias("cifs", smbProtocol);

        // Register FTP and SFTP protocols
        registerProtocol(new FtpTransferProtocol());
        registerProtocol(new SftpTransferProtocol());

        logger.info("Registered default transfer protocols: HTTP/HTTPS, SMB/CIFS, FTP, SFTP");
    }
    
    public void registerProtocol(TransferProtocol protocol) {
        protocols.put(protocol.getProtocolName().toLowerCase(), protocol);
        logger.info("Registered protocol: " + protocol.getProtocolName());
    }

    /**
     * Register a protocol under an alias scheme name
     */
    public void registerProtocolAlias(String alias, TransferProtocol protocol) {
        protocols.put(alias.toLowerCase(), protocol);
        logger.info("Registered protocol alias: " + alias + " -> " + protocol.getProtocolName());
    }
    
    public TransferProtocol getProtocol(String protocolName) {
        if (protocolName == null) {
            return null;
        }
        return protocols.get(protocolName.toLowerCase());
    }
    
    public boolean isProtocolSupported(String protocolName) {
        return protocolName != null && protocols.containsKey(protocolName.toLowerCase());
    }
    
    public String[] getSupportedProtocols() {
        return protocols.keySet().toArray(new String[0]);
    }
    
    /**
     * Remove a protocol from the registry
     */
    public void unregisterProtocol(String protocolName) {
        if (protocolName != null) {
            protocols.remove(protocolName.toLowerCase());
            logger.info("Unregistered protocol: " + protocolName);
        }
    }
}
