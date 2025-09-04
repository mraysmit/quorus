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

package dev.mars.quorus.agent.config;

import dev.mars.quorus.agent.AgentCapabilities;
import dev.mars.quorus.agent.AgentSystemInfo;
import dev.mars.quorus.agent.AgentNetworkInfo;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Configuration for the Quorus Agent.
 * Loads configuration from environment variables and system properties.
 * 
 * @author Mark Andrew Ray-Smith Cityline Ltd
 * @since 1.0
 */
public class AgentConfiguration {
    
    private final String agentId;
    private final String hostname;
    private final String address;
    private final int agentPort;
    private final String region;
    private final String datacenter;
    private final String controllerUrl;
    private final Set<String> supportedProtocols;
    private final int maxConcurrentTransfers;
    private final long heartbeatInterval;
    private final String version;
    
    private AgentConfiguration(Builder builder) {
        this.agentId = builder.agentId;
        this.hostname = builder.hostname;
        this.address = builder.address;
        this.agentPort = builder.agentPort;
        this.region = builder.region;
        this.datacenter = builder.datacenter;
        this.controllerUrl = builder.controllerUrl;
        this.supportedProtocols = builder.supportedProtocols;
        this.maxConcurrentTransfers = builder.maxConcurrentTransfers;
        this.heartbeatInterval = builder.heartbeatInterval;
        this.version = builder.version;
    }
    
    public static AgentConfiguration fromEnvironment() {
        Builder builder = new Builder();
        
        // Required configuration
        builder.agentId(getEnvOrThrow("AGENT_ID"));
        builder.controllerUrl(getEnvOrDefault("CONTROLLER_URL", "http://localhost:8080/api/v1"));
        
        // Optional configuration with defaults
        builder.region(getEnvOrDefault("AGENT_REGION", "default"));
        builder.datacenter(getEnvOrDefault("AGENT_DATACENTER", "default"));
        builder.agentPort(Integer.parseInt(getEnvOrDefault("AGENT_PORT", "8080")));
        builder.maxConcurrentTransfers(Integer.parseInt(getEnvOrDefault("MAX_CONCURRENT_TRANSFERS", "5")));
        builder.heartbeatInterval(Long.parseLong(getEnvOrDefault("HEARTBEAT_INTERVAL", "30000")));
        builder.version(getEnvOrDefault("AGENT_VERSION", "1.0.0"));
        
        // Parse supported protocols
        String protocolsStr = getEnvOrDefault("SUPPORTED_PROTOCOLS", "HTTP,HTTPS");
        Set<String> protocols = new HashSet<>(Arrays.asList(protocolsStr.split(",")));
        builder.supportedProtocols(protocols);
        
        // Auto-detect hostname and IP
        try {
            InetAddress localHost = InetAddress.getLocalHost();
            builder.hostname(localHost.getHostName());
            builder.address(localHost.getHostAddress());
        } catch (UnknownHostException e) {
            builder.hostname("unknown");
            builder.address("127.0.0.1");
        }
        
        return builder.build();
    }
    
    private static String getEnvOrThrow(String name) {
        String value = System.getenv(name);
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException("Required environment variable not set: " + name);
        }
        return value.trim();
    }
    
    private static String getEnvOrDefault(String name, String defaultValue) {
        String value = System.getenv(name);
        return (value != null && !value.trim().isEmpty()) ? value.trim() : defaultValue;
    }
    
    public AgentCapabilities createCapabilities() {
        AgentCapabilities capabilities = new AgentCapabilities();
        capabilities.setSupportedProtocols(supportedProtocols);
        capabilities.setMaxConcurrentTransfers(maxConcurrentTransfers);
        capabilities.setMaxTransferSize(Long.MAX_VALUE); // No limit
        capabilities.setMaxBandwidth(Long.MAX_VALUE); // No limit
        
        // Set system info
        AgentSystemInfo systemInfo = new AgentSystemInfo();
        systemInfo.setOperatingSystem(System.getProperty("os.name"));
        systemInfo.setArchitecture(System.getProperty("os.arch"));
        systemInfo.setJavaVersion(System.getProperty("java.version"));
        systemInfo.setCpuCores(Runtime.getRuntime().availableProcessors());
        systemInfo.setTotalMemory(Runtime.getRuntime().totalMemory());
        systemInfo.setAvailableMemory(Runtime.getRuntime().freeMemory());
        capabilities.setSystemInfo(systemInfo);
        
        // Set network info
        AgentNetworkInfo networkInfo = new AgentNetworkInfo();
        networkInfo.setHostname(hostname);
        networkInfo.setIpAddress(address);
        networkInfo.setPort(agentPort);
        capabilities.setNetworkInfo(networkInfo);
        
        return capabilities;
    }
    
    // Getters
    public String getAgentId() { return agentId; }
    public String getHostname() { return hostname; }
    public String getAddress() { return address; }
    public int getAgentPort() { return agentPort; }
    public String getRegion() { return region; }
    public String getDatacenter() { return datacenter; }
    public String getControllerUrl() { return controllerUrl; }
    public Set<String> getSupportedProtocols() { return supportedProtocols; }
    public int getMaxConcurrentTransfers() { return maxConcurrentTransfers; }
    public long getHeartbeatInterval() { return heartbeatInterval; }
    public String getVersion() { return version; }
    
    public static class Builder {
        private String agentId;
        private String hostname;
        private String address;
        private int agentPort = 8080;
        private String region = "default";
        private String datacenter = "default";
        private String controllerUrl;
        private Set<String> supportedProtocols = new HashSet<>();
        private int maxConcurrentTransfers = 5;
        private long heartbeatInterval = 30000;
        private String version = "1.0.0";
        
        public Builder agentId(String agentId) { this.agentId = agentId; return this; }
        public Builder hostname(String hostname) { this.hostname = hostname; return this; }
        public Builder address(String address) { this.address = address; return this; }
        public Builder agentPort(int agentPort) { this.agentPort = agentPort; return this; }
        public Builder region(String region) { this.region = region; return this; }
        public Builder datacenter(String datacenter) { this.datacenter = datacenter; return this; }
        public Builder controllerUrl(String controllerUrl) { this.controllerUrl = controllerUrl; return this; }
        public Builder supportedProtocols(Set<String> supportedProtocols) { this.supportedProtocols = supportedProtocols; return this; }
        public Builder maxConcurrentTransfers(int maxConcurrentTransfers) { this.maxConcurrentTransfers = maxConcurrentTransfers; return this; }
        public Builder heartbeatInterval(long heartbeatInterval) { this.heartbeatInterval = heartbeatInterval; return this; }
        public Builder version(String version) { this.version = version; return this; }
        
        public AgentConfiguration build() {
            if (agentId == null) throw new IllegalArgumentException("agentId is required");
            if (controllerUrl == null) throw new IllegalArgumentException("controllerUrl is required");
            return new AgentConfiguration(this);
        }
    }
}
