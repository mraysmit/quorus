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
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Configuration for the Quorus Agent.
 * Loads configuration from environment variables and system properties.
 * 
 * @author Mark Andrew Ray-Smith Cityline Ltd
 * @since 2025-09-04
 * @version 1.0
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
    private final int httpConnectionTimeout;
    private final int httpIdleTimeout;
    private final String version;
    private final String tenantId;
    private final String securityProfile;
    private final boolean allowInsecure;
    private final boolean controllerTlsEnabled;
    private final String tlsCertificatePath;
    private final String tlsPrivateKeyPath;
    private final String tlsTrustBundlePath;
    private final Path uploadRoot;
    private final Path downloadRoot;
    private final String agentPool;
    private final String networkZone;
    
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
        this.httpConnectionTimeout = builder.httpConnectionTimeout;
        this.httpIdleTimeout = builder.httpIdleTimeout;
        this.version = builder.version;
        this.tenantId = builder.tenantId;
        this.securityProfile = builder.securityProfile;
        this.allowInsecure = builder.allowInsecure;
        this.controllerTlsEnabled = builder.controllerTlsEnabled;
        this.tlsCertificatePath = builder.tlsCertificatePath;
        this.tlsPrivateKeyPath = builder.tlsPrivateKeyPath;
        this.tlsTrustBundlePath = builder.tlsTrustBundlePath;
        this.uploadRoot = builder.uploadRoot;
        this.downloadRoot = builder.downloadRoot;
        this.agentPool = builder.agentPool;
        this.networkZone = builder.networkZone;
    }
    
    public static AgentConfiguration fromEnvironment() {
        Builder builder = new Builder();
        
        // Required configuration
        builder.agentId(getEnvOrThrow("AGENT_ID"));
        builder.tenantId(getEnvOrThrow("AGENT_TENANT_ID"));
        builder.controllerUrl(getEnvOrDefault("CONTROLLER_URL", "https://localhost:8080/api/v1"));
        
        // Optional configuration with defaults
        builder.region(getEnvOrDefault("AGENT_REGION", "default"));
        builder.datacenter(getEnvOrDefault("AGENT_DATACENTER", "default"));
        builder.agentPort(Integer.parseInt(getEnvOrDefault("AGENT_PORT", "8080")));
        builder.maxConcurrentTransfers(Integer.parseInt(getEnvOrDefault("MAX_CONCURRENT_TRANSFERS", "5")));
        builder.heartbeatInterval(Long.parseLong(getEnvOrDefault("HEARTBEAT_INTERVAL", "30000")));
        builder.httpConnectionTimeout(Integer.parseInt(getEnvOrDefault("HTTP_CONNECTION_TIMEOUT_MS", "5000")));
        builder.httpIdleTimeout(Integer.parseInt(getEnvOrDefault("HTTP_IDLE_TIMEOUT_MS", "10000")));
        builder.version(getEnvOrDefault("AGENT_VERSION", "1.0.0"));
        builder.securityProfile(getEnvOrDefault("QUORUS_AGENT_SECURITY_PROFILE", "production"));
        builder.allowInsecure(Boolean.parseBoolean(getEnvOrDefault(
                "QUORUS_AGENT_SECURITY_ALLOW_INSECURE", "false")));
        builder.controllerTlsEnabled(Boolean.parseBoolean(getEnvOrDefault(
                "QUORUS_AGENT_TLS_ENABLED", "true")));
        builder.tlsCertificatePath(getEnvOrDefault("QUORUS_AGENT_TLS_CERTIFICATE", ""));
        builder.tlsPrivateKeyPath(getEnvOrDefault("QUORUS_AGENT_TLS_PRIVATE_KEY", ""));
        builder.tlsTrustBundlePath(getEnvOrDefault("QUORUS_AGENT_TLS_TRUST_BUNDLE", ""));
        builder.uploadRoot(Path.of(getEnvOrDefault("QUORUS_AGENT_UPLOAD_ROOT", "data/uploads")));
        builder.downloadRoot(Path.of(getEnvOrDefault("QUORUS_AGENT_DOWNLOAD_ROOT", "data/downloads")));
        builder.agentPool(getEnvOrDefault("QUORUS_AGENT_POOL", "default"));
        builder.networkZone(getEnvOrDefault("QUORUS_AGENT_NETWORK_ZONE", "default"));
        
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
        networkInfo.setPublicIpAddress(address);
        networkInfo.setPrivateIpAddress(address);
        capabilities.setNetworkInfo(networkInfo);
        
        return capabilities;
    }
    
    // Getters
    public String getAgentId() { return agentId; }
    public String getTenantId() { return tenantId; }
    public String getHostname() { return hostname; }
    public String getAddress() { return address; }
    public int getAgentPort() { return agentPort; }
    public String getRegion() { return region; }
    public String getDatacenter() { return datacenter; }
    public String getControllerUrl() { return controllerUrl; }
    public Set<String> getSupportedProtocols() { return supportedProtocols; }
    public int getMaxConcurrentTransfers() { return maxConcurrentTransfers; }
    public long getHeartbeatInterval() { return heartbeatInterval; }
    public int getHttpConnectionTimeout() { return httpConnectionTimeout; }
    public int getHttpIdleTimeout() { return httpIdleTimeout; }
    public String getVersion() { return version; }
    public String getSecurityProfile() { return securityProfile; }
    public boolean isAllowInsecure() { return allowInsecure; }
    public boolean isControllerTlsEnabled() { return controllerTlsEnabled; }
    public String getTlsCertificatePath() { return tlsCertificatePath; }
    public String getTlsPrivateKeyPath() { return tlsPrivateKeyPath; }
    public String getTlsTrustBundlePath() { return tlsTrustBundlePath; }
    public Path getUploadRoot() { return uploadRoot; }
    public Path getDownloadRoot() { return downloadRoot; }
    public String getAgentPool() { return agentPool; }
    public String getNetworkZone() { return networkZone; }
    
    public static class Builder {
        private String agentId;
        private String tenantId;
        private String hostname;
        private String address;
        private int agentPort = 8080;
        private String region = "default";
        private String datacenter = "default";
        private String controllerUrl;
        private Set<String> supportedProtocols = new HashSet<>();
        private int maxConcurrentTransfers = 5;
        private long heartbeatInterval = 30000;
        private int httpConnectionTimeout = 5000;
        private int httpIdleTimeout = 10000;
        private String version = "1.0.0";
        private String securityProfile = "development";
        private boolean allowInsecure = true;
        private boolean controllerTlsEnabled = false;
        private String tlsCertificatePath;
        private String tlsPrivateKeyPath;
        private String tlsTrustBundlePath;
        private Path uploadRoot = Path.of("data/uploads");
        private Path downloadRoot = Path.of("data/downloads");
        private String agentPool = "default";
        private String networkZone = "default";
        
        public Builder agentId(String agentId) { this.agentId = agentId; return this; }
        public Builder tenantId(String tenantId) { this.tenantId = tenantId; return this; }
        public Builder hostname(String hostname) { this.hostname = hostname; return this; }
        public Builder address(String address) { this.address = address; return this; }
        public Builder agentPort(int agentPort) { this.agentPort = agentPort; return this; }
        public Builder region(String region) { this.region = region; return this; }
        public Builder datacenter(String datacenter) { this.datacenter = datacenter; return this; }
        public Builder controllerUrl(String controllerUrl) { this.controllerUrl = controllerUrl; return this; }
        public Builder supportedProtocols(Set<String> supportedProtocols) { this.supportedProtocols = supportedProtocols; return this; }
        public Builder maxConcurrentTransfers(int maxConcurrentTransfers) { this.maxConcurrentTransfers = maxConcurrentTransfers; return this; }
        public Builder heartbeatInterval(long heartbeatInterval) { this.heartbeatInterval = heartbeatInterval; return this; }
        public Builder httpConnectionTimeout(int httpConnectionTimeout) { this.httpConnectionTimeout = httpConnectionTimeout; return this; }
        public Builder httpIdleTimeout(int httpIdleTimeout) { this.httpIdleTimeout = httpIdleTimeout; return this; }
        public Builder version(String version) { this.version = version; return this; }
        public Builder securityProfile(String securityProfile) { this.securityProfile = securityProfile; return this; }
        public Builder allowInsecure(boolean allowInsecure) { this.allowInsecure = allowInsecure; return this; }
        public Builder controllerTlsEnabled(boolean enabled) { this.controllerTlsEnabled = enabled; return this; }
        public Builder tlsCertificatePath(String path) { this.tlsCertificatePath = path; return this; }
        public Builder tlsPrivateKeyPath(String path) { this.tlsPrivateKeyPath = path; return this; }
        public Builder tlsTrustBundlePath(String path) { this.tlsTrustBundlePath = path; return this; }
        public Builder uploadRoot(Path path) { this.uploadRoot = path; return this; }
        public Builder downloadRoot(Path path) { this.downloadRoot = path; return this; }
        public Builder agentPool(String value) { this.agentPool = value; return this; }
        public Builder networkZone(String value) { this.networkZone = value; return this; }
        
        public AgentConfiguration build() {
            if (agentId == null) throw new IllegalArgumentException("agentId is required");
            if (tenantId == null) throw new IllegalArgumentException("tenantId is required (AGENT_TENANT_ID)");
            if (controllerUrl == null) throw new IllegalArgumentException("controllerUrl is required");
            if (uploadRoot == null) throw new IllegalArgumentException("uploadRoot is required");
            if (downloadRoot == null) throw new IllegalArgumentException("downloadRoot is required");
            if (agentPool == null || agentPool.isBlank()) throw new IllegalArgumentException("agentPool is required");
            if (networkZone == null || networkZone.isBlank()) throw new IllegalArgumentException("networkZone is required");
            boolean production = "production".equalsIgnoreCase(securityProfile);
            if (!production && !"development".equalsIgnoreCase(securityProfile)) {
                throw new IllegalArgumentException("securityProfile must be development or production");
            }
            if (production) {
                if (allowInsecure || !controllerTlsEnabled || !controllerUrl.startsWith("https://")) {
                    throw new IllegalArgumentException(
                            "Production agents require HTTPS mutual TLS and forbid insecure transport");
                }
                requireReadable(tlsCertificatePath, "agent TLS certificate");
                requireReadable(tlsPrivateKeyPath, "agent TLS private key");
                requireReadable(tlsTrustBundlePath, "controller trust bundle");
            } else if (!controllerTlsEnabled && !allowInsecure) {
                throw new IllegalArgumentException(
                        "Plaintext development connections require allowInsecure=true");
            }
            return new AgentConfiguration(this);
        }

        private static void requireReadable(String value, String description) {
            if (value == null || value.isBlank() || !Files.isReadable(Path.of(value))) {
                throw new IllegalArgumentException(description + " must reference a readable file");
            }
        }
    }
}
