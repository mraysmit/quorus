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
 * Typed, immutable runtime configuration assembled at the application boundary.
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
    private final String nfsMountRoot;
    private final boolean nfsMountSecurityVerified;
    private final boolean smbMountSecurityVerified;
    private final String agentPool;
    private final String networkZone;
    private final long jobPollingInitialDelayMs;
    private final long jobPollingIntervalMs;
    private final int foreignAssignmentMismatchThreshold;
    private final boolean telemetryEnabled;
    private final int prometheusPort;
    private final String otlpEndpoint;
    private final String vaultAddress;
    private final String vaultToken;
    
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
        this.nfsMountRoot = builder.nfsMountRoot;
        this.nfsMountSecurityVerified = builder.nfsMountSecurityVerified;
        this.smbMountSecurityVerified = builder.smbMountSecurityVerified;
        this.agentPool = builder.agentPool;
        this.networkZone = builder.networkZone;
        this.jobPollingInitialDelayMs = builder.jobPollingInitialDelayMs;
        this.jobPollingIntervalMs = builder.jobPollingIntervalMs;
        this.foreignAssignmentMismatchThreshold = builder.foreignAssignmentMismatchThreshold;
        this.telemetryEnabled = builder.telemetryEnabled;
        this.prometheusPort = builder.prometheusPort;
        this.otlpEndpoint = builder.otlpEndpoint;
        this.vaultAddress = builder.vaultAddress;
        this.vaultToken = builder.vaultToken;
    }
    
    public static AgentConfiguration from(AgentConfig config) {
        config.validate();
        Builder builder = new Builder(config);
        builder.agentId(config.getAgentId());
        builder.tenantId(config.getTenantId());
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
    public String getNfsMountRoot() { return nfsMountRoot; }
    public boolean isNfsMountSecurityVerified() { return nfsMountSecurityVerified; }
    public boolean isSmbMountSecurityVerified() { return smbMountSecurityVerified; }
    public String getAgentPool() { return agentPool; }
    public String getNetworkZone() { return networkZone; }
    public long getJobPollingInitialDelayMs() { return jobPollingInitialDelayMs; }
    public long getJobPollingIntervalMs() { return jobPollingIntervalMs; }
    public int getForeignAssignmentMismatchThreshold() { return foreignAssignmentMismatchThreshold; }
    public boolean isTelemetryEnabled() { return telemetryEnabled; }
    public int getPrometheusPort() { return prometheusPort; }
    public String getOtlpEndpoint() { return otlpEndpoint; }
    public String getVaultAddress() { return vaultAddress; }
    public String getVaultToken() { return vaultToken; }
    
    public static class Builder {
        private String agentId;
        private String tenantId;
        private String hostname;
        private String address;
        private int agentPort;
        private String region;
        private String datacenter;
        private String controllerUrl;
        private Set<String> supportedProtocols;
        private int maxConcurrentTransfers;
        private long heartbeatInterval;
        private int httpConnectionTimeout;
        private int httpIdleTimeout;
        private String version;
        private String securityProfile;
        private boolean allowInsecure;
        private boolean controllerTlsEnabled;
        private String tlsCertificatePath;
        private String tlsPrivateKeyPath;
        private String tlsTrustBundlePath;
        private Path uploadRoot;
        private Path downloadRoot;
        private String nfsMountRoot;
        private boolean nfsMountSecurityVerified;
        private boolean smbMountSecurityVerified;
        private String agentPool;
        private String networkZone;
        private long jobPollingInitialDelayMs;
        private long jobPollingIntervalMs;
        private int foreignAssignmentMismatchThreshold;
        private boolean telemetryEnabled;
        private int prometheusPort;
        private String otlpEndpoint;
        private String vaultAddress;
        private String vaultToken;
        
        /** Deterministic packaged defaults; environment is applied at the application boundary. */
        public Builder() {
            this(new AgentConfig("default", new java.util.Properties(), java.util.Map.of()));
        }

        private Builder(AgentConfig config) {
            this.controllerUrl(config.getControllerUrl());
            this.region(config.getRegion());
            this.datacenter(config.getDatacenter());
            this.agentPort(config.getAgentPort());
            this.maxConcurrentTransfers(config.getMaxConcurrentTransfers());
            this.heartbeatInterval(config.getHeartbeatIntervalMs());
            this.httpConnectionTimeout(config.getHttpConnectionTimeoutMs());
            this.httpIdleTimeout(config.getHttpIdleTimeoutMs());
            this.version(config.getVersion());
            this.securityProfile(config.getSecurityProfile());
            this.allowInsecure(config.isAllowInsecure());
            this.controllerTlsEnabled(config.isControllerTlsEnabled());
            this.tlsCertificatePath(config.getTlsCertificatePath());
            this.tlsPrivateKeyPath(config.getTlsPrivateKeyPath());
            this.tlsTrustBundlePath(config.getTlsTrustBundlePath());
            this.uploadRoot(Path.of(config.getUploadRoot()));
            this.downloadRoot(Path.of(config.getDownloadRoot()));
            this.nfsMountRoot(config.getNfsMountRoot());
            this.nfsMountSecurityVerified(config.isNfsMountSecurityVerified());
            this.smbMountSecurityVerified(config.isSmbMountSecurityVerified());
            this.agentPool(config.getAgentPool());
            this.networkZone(config.getNetworkZone());
            this.jobPollingInitialDelayMs(config.getJobPollingInitialDelayMs());
            this.jobPollingIntervalMs(config.getJobPollingIntervalMs());
            this.foreignAssignmentMismatchThreshold(config.getForeignAssignmentMismatchThreshold());
            this.telemetryEnabled(config.isTelemetryEnabled());
            this.prometheusPort(config.getPrometheusPort());
            this.otlpEndpoint(config.getOtlpEndpoint());
            this.vaultAddress(config.getVaultAddress());
            this.vaultToken(config.getVaultToken());

            String protocolsStr = config.getSupportedProtocols();
            Set<String> protocols = new HashSet<>(Arrays.asList(protocolsStr.split(",")));
            this.supportedProtocols(protocols);
        
        }

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
        public Builder nfsMountRoot(String value) { this.nfsMountRoot = value; return this; }
        public Builder nfsMountSecurityVerified(boolean value) { this.nfsMountSecurityVerified = value; return this; }
        public Builder smbMountSecurityVerified(boolean value) { this.smbMountSecurityVerified = value; return this; }
        public Builder agentPool(String value) { this.agentPool = value; return this; }
        public Builder networkZone(String value) { this.networkZone = value; return this; }
        public Builder jobPollingInitialDelayMs(long value) { this.jobPollingInitialDelayMs = value; return this; }
        public Builder jobPollingIntervalMs(long value) { this.jobPollingIntervalMs = value; return this; }
        public Builder foreignAssignmentMismatchThreshold(int value) { this.foreignAssignmentMismatchThreshold = value; return this; }
        public Builder telemetryEnabled(boolean value) { this.telemetryEnabled = value; return this; }
        public Builder prometheusPort(int value) { this.prometheusPort = value; return this; }
        public Builder otlpEndpoint(String value) { this.otlpEndpoint = value; return this; }
        public Builder vaultAddress(String value) { this.vaultAddress = value; return this; }
        public Builder vaultToken(String value) { this.vaultToken = value; return this; }
        
        public AgentConfiguration build() {
            if (agentId == null) throw new IllegalArgumentException("agentId is required");
            if (tenantId == null) throw new IllegalArgumentException("tenantId is required (AGENT_TENANT_ID)");
            if (controllerUrl == null) throw new IllegalArgumentException("controllerUrl is required");
            if (uploadRoot == null) throw new IllegalArgumentException("uploadRoot is required");
            if (downloadRoot == null) throw new IllegalArgumentException("downloadRoot is required");
            if (nfsMountRoot == null) throw new IllegalArgumentException("nfsMountRoot is required");
            if (agentPool == null || agentPool.isBlank()) throw new IllegalArgumentException("agentPool is required");
            if (networkZone == null || networkZone.isBlank()) throw new IllegalArgumentException("networkZone is required");
            if (jobPollingInitialDelayMs < 0) throw new IllegalArgumentException("jobPollingInitialDelayMs must not be negative");
            if (jobPollingIntervalMs <= 0) throw new IllegalArgumentException("jobPollingIntervalMs must be positive");
            if (foreignAssignmentMismatchThreshold <= 0) {
                throw new IllegalArgumentException("foreignAssignmentMismatchThreshold must be positive");
            }
            if (prometheusPort < 1 || prometheusPort > 65535) {
                throw new IllegalArgumentException("prometheusPort must be between 1 and 65535");
            }
            if (otlpEndpoint == null || otlpEndpoint.isBlank()) {
                throw new IllegalArgumentException("otlpEndpoint is required");
            }
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
