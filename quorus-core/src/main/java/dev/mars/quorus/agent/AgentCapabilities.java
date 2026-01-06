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

package dev.mars.quorus.agent;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Represents the capabilities and constraints of a Quorus agent.
 * This information is used for intelligent job assignment and load balancing.
 * 
 * @author Mark Andrew Ray-Smith Cityline Ltd
 * @since 2025-08-26
 * @version 1.0
 */
public class AgentCapabilities {

    @JsonProperty("supportedProtocols")
    private Set<String> supportedProtocols;

    @JsonProperty("maxConcurrentTransfers")
    private int maxConcurrentTransfers;

    @JsonProperty("maxTransferSize")
    private long maxTransferSize;

    @JsonProperty("maxBandwidth")
    private long maxBandwidth; // bytes per second

    @JsonProperty("availableRegions")
    private Set<String> availableRegions;

    @JsonProperty("supportedCompressionTypes")
    private Set<String> supportedCompressionTypes;

    @JsonProperty("supportedEncryptionTypes")
    private Set<String> supportedEncryptionTypes;

    @JsonProperty("customCapabilities")
    private Map<String, Object> customCapabilities;

    @JsonProperty("systemInfo")
    private AgentSystemInfo systemInfo;

    @JsonProperty("networkInfo")
    private AgentNetworkInfo networkInfo;

    /**
     * Default constructor.
     */
    public AgentCapabilities() {
        this.supportedProtocols = new HashSet<>();
        this.availableRegions = new HashSet<>();
        this.supportedCompressionTypes = new HashSet<>();
        this.supportedEncryptionTypes = new HashSet<>();
        this.customCapabilities = new HashMap<>();
        this.maxConcurrentTransfers = 10; // Default value
        this.maxTransferSize = Long.MAX_VALUE; // No limit by default
        this.maxBandwidth = Long.MAX_VALUE; // No limit by default
    }

    /**
     * Get the supported transfer protocols.
     * 
     * @return set of supported protocols (e.g., "HTTP", "FTP", "SMB", "S3")
     */
    public Set<String> getSupportedProtocols() {
        return supportedProtocols;
    }

    /**
     * Set the supported transfer protocols.
     * 
     * @param supportedProtocols the supported protocols
     */
    public void setSupportedProtocols(Set<String> supportedProtocols) {
        this.supportedProtocols = supportedProtocols != null ? supportedProtocols : new HashSet<>();
    }

    /**
     * Add a supported protocol.
     * 
     * @param protocol the protocol to add
     */
    public void addSupportedProtocol(String protocol) {
        if (this.supportedProtocols == null) {
            this.supportedProtocols = new HashSet<>();
        }
        this.supportedProtocols.add(protocol);
    }

    /**
     * Check if a protocol is supported.
     * 
     * @param protocol the protocol to check
     * @return true if the protocol is supported
     */
    public boolean supportsProtocol(String protocol) {
        return supportedProtocols != null && supportedProtocols.contains(protocol);
    }

    /**
     * Get the maximum number of concurrent transfers.
     * 
     * @return the maximum concurrent transfers
     */
    public int getMaxConcurrentTransfers() {
        return maxConcurrentTransfers;
    }

    /**
     * Set the maximum number of concurrent transfers.
     * 
     * @param maxConcurrentTransfers the maximum concurrent transfers
     */
    public void setMaxConcurrentTransfers(int maxConcurrentTransfers) {
        this.maxConcurrentTransfers = maxConcurrentTransfers;
    }

    /**
     * Get the maximum transfer size in bytes.
     * 
     * @return the maximum transfer size
     */
    public long getMaxTransferSize() {
        return maxTransferSize;
    }

    /**
     * Set the maximum transfer size in bytes.
     * 
     * @param maxTransferSize the maximum transfer size
     */
    public void setMaxTransferSize(long maxTransferSize) {
        this.maxTransferSize = maxTransferSize;
    }

    /**
     * Get the maximum bandwidth in bytes per second.
     * 
     * @return the maximum bandwidth
     */
    public long getMaxBandwidth() {
        return maxBandwidth;
    }

    /**
     * Set the maximum bandwidth in bytes per second.
     * 
     * @param maxBandwidth the maximum bandwidth
     */
    public void setMaxBandwidth(long maxBandwidth) {
        this.maxBandwidth = maxBandwidth;
    }

    /**
     * Get the available regions for this agent.
     * 
     * @return set of available regions
     */
    public Set<String> getAvailableRegions() {
        return availableRegions;
    }

    /**
     * Set the available regions for this agent.
     * 
     * @param availableRegions the available regions
     */
    public void setAvailableRegions(Set<String> availableRegions) {
        this.availableRegions = availableRegions != null ? availableRegions : new HashSet<>();
    }

    /**
     * Add an available region.
     * 
     * @param region the region to add
     */
    public void addAvailableRegion(String region) {
        if (this.availableRegions == null) {
            this.availableRegions = new HashSet<>();
        }
        this.availableRegions.add(region);
    }

    /**
     * Get the supported compression types.
     * 
     * @return set of supported compression types
     */
    public Set<String> getSupportedCompressionTypes() {
        return supportedCompressionTypes;
    }

    /**
     * Set the supported compression types.
     * 
     * @param supportedCompressionTypes the supported compression types
     */
    public void setSupportedCompressionTypes(Set<String> supportedCompressionTypes) {
        this.supportedCompressionTypes = supportedCompressionTypes != null ? supportedCompressionTypes : new HashSet<>();
    }

    /**
     * Get the supported encryption types.
     * 
     * @return set of supported encryption types
     */
    public Set<String> getSupportedEncryptionTypes() {
        return supportedEncryptionTypes;
    }

    /**
     * Set the supported encryption types.
     * 
     * @param supportedEncryptionTypes the supported encryption types
     */
    public void setSupportedEncryptionTypes(Set<String> supportedEncryptionTypes) {
        this.supportedEncryptionTypes = supportedEncryptionTypes != null ? supportedEncryptionTypes : new HashSet<>();
    }

    /**
     * Get custom capabilities.
     * 
     * @return map of custom capabilities
     */
    public Map<String, Object> getCustomCapabilities() {
        return customCapabilities;
    }

    /**
     * Set custom capabilities.
     * 
     * @param customCapabilities the custom capabilities
     */
    public void setCustomCapabilities(Map<String, Object> customCapabilities) {
        this.customCapabilities = customCapabilities != null ? customCapabilities : new HashMap<>();
    }

    /**
     * Add a custom capability.
     * 
     * @param key the capability key
     * @param value the capability value
     */
    public void addCustomCapability(String key, Object value) {
        if (this.customCapabilities == null) {
            this.customCapabilities = new HashMap<>();
        }
        this.customCapabilities.put(key, value);
    }

    /**
     * Get system information.
     * 
     * @return the system information
     */
    public AgentSystemInfo getSystemInfo() {
        return systemInfo;
    }

    /**
     * Set system information.
     * 
     * @param systemInfo the system information
     */
    public void setSystemInfo(AgentSystemInfo systemInfo) {
        this.systemInfo = systemInfo;
    }

    /**
     * Get network information.
     * 
     * @return the network information
     */
    public AgentNetworkInfo getNetworkInfo() {
        return networkInfo;
    }

    /**
     * Set network information.
     * 
     * @param networkInfo the network information
     */
    public void setNetworkInfo(AgentNetworkInfo networkInfo) {
        this.networkInfo = networkInfo;
    }

    /**
     * Check if the agent can handle a transfer of the given size.
     * 
     * @param transferSize the transfer size in bytes
     * @return true if the agent can handle the transfer
     */
    public boolean canHandleTransferSize(long transferSize) {
        return transferSize <= maxTransferSize;
    }

    /**
     * Check if the agent is available in the given region.
     * 
     * @param region the region to check
     * @return true if the agent is available in the region
     */
    public boolean isAvailableInRegion(String region) {
        return availableRegions == null || availableRegions.isEmpty() || availableRegions.contains(region);
    }

    /**
     * Calculate a compatibility score for a given transfer requirement.
     * 
     * @param requiredProtocol the required protocol
     * @param transferSize the transfer size
     * @param region the target region
     * @return compatibility score (0.0 to 1.0)
     */
    public double calculateCompatibilityScore(String requiredProtocol, long transferSize, String region) {
        double score = 0.0;
        
        // Protocol compatibility (40% weight)
        if (supportsProtocol(requiredProtocol)) {
            score += 0.4;
        }
        
        // Size compatibility (30% weight)
        if (canHandleTransferSize(transferSize)) {
            score += 0.3;
        }
        
        // Region compatibility (30% weight)
        if (isAvailableInRegion(region)) {
            score += 0.3;
        }
        
        return score;
    }

    @Override
    public String toString() {
        return "AgentCapabilities{" +
                "supportedProtocols=" + supportedProtocols +
                ", maxConcurrentTransfers=" + maxConcurrentTransfers +
                ", maxTransferSize=" + maxTransferSize +
                ", maxBandwidth=" + maxBandwidth +
                ", availableRegions=" + availableRegions +
                '}';
    }
}
