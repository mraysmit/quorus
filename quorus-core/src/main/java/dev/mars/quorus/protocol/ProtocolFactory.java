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


import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Factory for creating and managing transfer protocol implementations.
 * Provides a registry of available protocols and creates instances as needed.
 */
public class ProtocolFactory {
    private static final Logger logger = Logger.getLogger(ProtocolFactory.class.getName());
    
    private final Map<String, TransferProtocol> protocols;
    
    public ProtocolFactory() {
        this.protocols = new HashMap<>();
        registerDefaultProtocols();
    }
    
    /**
     * Register default protocol implementations
     */
    private void registerDefaultProtocols() {
        registerProtocol(new HttpTransferProtocol());
        logger.info("Registered default transfer protocols");
    }
    
    /**
     * Register a transfer protocol implementation
     */
    public void registerProtocol(TransferProtocol protocol) {
        protocols.put(protocol.getProtocolName().toLowerCase(), protocol);
        logger.info("Registered protocol: " + protocol.getProtocolName());
    }
    
    /**
     * Get a protocol implementation by name
     */
    public TransferProtocol getProtocol(String protocolName) {
        if (protocolName == null) {
            return null;
        }
        return protocols.get(protocolName.toLowerCase());
    }
    
    /**
     * Check if a protocol is supported
     */
    public boolean isProtocolSupported(String protocolName) {
        return protocolName != null && protocols.containsKey(protocolName.toLowerCase());
    }
    
    /**
     * Get all registered protocol names
     */
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
