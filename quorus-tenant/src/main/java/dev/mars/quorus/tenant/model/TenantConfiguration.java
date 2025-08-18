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

package dev.mars.quorus.tenant.model;

import java.time.Duration;
import java.util.*;

/**
 * Configuration settings for a tenant, including resource limits,
 * transfer policies, and operational parameters.
 */
public class TenantConfiguration {
    
    private final ResourceLimits resourceLimits;
    private final TransferPolicies transferPolicies;
    private final SecuritySettings securitySettings;
    private final Map<String, Object> customSettings;
    
    private TenantConfiguration(Builder builder) {
        this.resourceLimits = builder.resourceLimits;
        this.transferPolicies = builder.transferPolicies;
        this.securitySettings = builder.securitySettings;
        this.customSettings = Collections.unmodifiableMap(new HashMap<>(builder.customSettings));
    }
    
    // Getters
    public ResourceLimits getResourceLimits() { return resourceLimits; }
    public TransferPolicies getTransferPolicies() { return transferPolicies; }
    public SecuritySettings getSecuritySettings() { return securitySettings; }
    public Map<String, Object> getCustomSettings() { return customSettings; }
    
    /**
     * Resource limits for the tenant
     */
    public static class ResourceLimits {
        private final long maxConcurrentTransfers;
        private final long maxBandwidthBytesPerSecond;
        private final long maxStorageBytes;
        private final long maxTransfersPerDay;
        private final long maxTransferSizeBytes;
        
        private ResourceLimits(Builder builder) {
            this.maxConcurrentTransfers = builder.maxConcurrentTransfers;
            this.maxBandwidthBytesPerSecond = builder.maxBandwidthBytesPerSecond;
            this.maxStorageBytes = builder.maxStorageBytes;
            this.maxTransfersPerDay = builder.maxTransfersPerDay;
            this.maxTransferSizeBytes = builder.maxTransferSizeBytes;
        }
        
        // Getters
        public long getMaxConcurrentTransfers() { return maxConcurrentTransfers; }
        public long getMaxBandwidthBytesPerSecond() { return maxBandwidthBytesPerSecond; }
        public long getMaxStorageBytes() { return maxStorageBytes; }
        public long getMaxTransfersPerDay() { return maxTransfersPerDay; }
        public long getMaxTransferSizeBytes() { return maxTransferSizeBytes; }
        
        public static Builder builder() { return new Builder(); }
        
        public static class Builder {
            private long maxConcurrentTransfers = 10;
            private long maxBandwidthBytesPerSecond = 100 * 1024 * 1024; // 100 MB/s
            private long maxStorageBytes = 10L * 1024 * 1024 * 1024; // 10 GB
            private long maxTransfersPerDay = 1000;
            private long maxTransferSizeBytes = 1024L * 1024 * 1024; // 1 GB
            
            public Builder maxConcurrentTransfers(long maxConcurrentTransfers) { 
                this.maxConcurrentTransfers = maxConcurrentTransfers; return this; 
            }
            public Builder maxBandwidthBytesPerSecond(long maxBandwidthBytesPerSecond) { 
                this.maxBandwidthBytesPerSecond = maxBandwidthBytesPerSecond; return this; 
            }
            public Builder maxStorageBytes(long maxStorageBytes) { 
                this.maxStorageBytes = maxStorageBytes; return this; 
            }
            public Builder maxTransfersPerDay(long maxTransfersPerDay) { 
                this.maxTransfersPerDay = maxTransfersPerDay; return this; 
            }
            public Builder maxTransferSizeBytes(long maxTransferSizeBytes) { 
                this.maxTransferSizeBytes = maxTransferSizeBytes; return this; 
            }
            
            public ResourceLimits build() { return new ResourceLimits(this); }
        }
    }
    
    /**
     * Transfer policies for the tenant
     */
    public static class TransferPolicies {
        private final Set<String> allowedProtocols;
        private final Set<String> allowedSourcePatterns;
        private final Set<String> allowedDestinationPatterns;
        private final Duration defaultTimeout;
        private final int defaultRetryAttempts;
        private final boolean requireChecksumVerification;
        
        private TransferPolicies(Builder builder) {
            this.allowedProtocols = Collections.unmodifiableSet(new HashSet<>(builder.allowedProtocols));
            this.allowedSourcePatterns = Collections.unmodifiableSet(new HashSet<>(builder.allowedSourcePatterns));
            this.allowedDestinationPatterns = Collections.unmodifiableSet(new HashSet<>(builder.allowedDestinationPatterns));
            this.defaultTimeout = builder.defaultTimeout;
            this.defaultRetryAttempts = builder.defaultRetryAttempts;
            this.requireChecksumVerification = builder.requireChecksumVerification;
        }
        
        // Getters
        public Set<String> getAllowedProtocols() { return allowedProtocols; }
        public Set<String> getAllowedSourcePatterns() { return allowedSourcePatterns; }
        public Set<String> getAllowedDestinationPatterns() { return allowedDestinationPatterns; }
        public Duration getDefaultTimeout() { return defaultTimeout; }
        public int getDefaultRetryAttempts() { return defaultRetryAttempts; }
        public boolean isRequireChecksumVerification() { return requireChecksumVerification; }
        
        public static Builder builder() { return new Builder(); }
        
        public static class Builder {
            private Set<String> allowedProtocols = new HashSet<>(Arrays.asList("http", "https"));
            private Set<String> allowedSourcePatterns = new HashSet<>();
            private Set<String> allowedDestinationPatterns = new HashSet<>();
            private Duration defaultTimeout = Duration.ofMinutes(30);
            private int defaultRetryAttempts = 3;
            private boolean requireChecksumVerification = true;
            
            public Builder allowedProtocols(Set<String> allowedProtocols) { 
                this.allowedProtocols = new HashSet<>(allowedProtocols); return this; 
            }
            public Builder addAllowedProtocol(String protocol) { 
                this.allowedProtocols.add(protocol); return this; 
            }
            public Builder allowedSourcePatterns(Set<String> allowedSourcePatterns) { 
                this.allowedSourcePatterns = new HashSet<>(allowedSourcePatterns); return this; 
            }
            public Builder addAllowedSourcePattern(String pattern) { 
                this.allowedSourcePatterns.add(pattern); return this; 
            }
            public Builder allowedDestinationPatterns(Set<String> allowedDestinationPatterns) { 
                this.allowedDestinationPatterns = new HashSet<>(allowedDestinationPatterns); return this; 
            }
            public Builder addAllowedDestinationPattern(String pattern) { 
                this.allowedDestinationPatterns.add(pattern); return this; 
            }
            public Builder defaultTimeout(Duration defaultTimeout) { 
                this.defaultTimeout = defaultTimeout; return this; 
            }
            public Builder defaultRetryAttempts(int defaultRetryAttempts) { 
                this.defaultRetryAttempts = defaultRetryAttempts; return this; 
            }
            public Builder requireChecksumVerification(boolean requireChecksumVerification) { 
                this.requireChecksumVerification = requireChecksumVerification; return this; 
            }
            
            public TransferPolicies build() { return new TransferPolicies(this); }
        }
    }
    
    /**
     * Security settings for the tenant
     */
    public static class SecuritySettings {
        private final boolean requireAuthentication;
        private final Set<String> allowedRoles;
        private final Map<String, String> authenticationProviders;
        private final boolean enableAuditLogging;
        
        private SecuritySettings(Builder builder) {
            this.requireAuthentication = builder.requireAuthentication;
            this.allowedRoles = Collections.unmodifiableSet(new HashSet<>(builder.allowedRoles));
            this.authenticationProviders = Collections.unmodifiableMap(new HashMap<>(builder.authenticationProviders));
            this.enableAuditLogging = builder.enableAuditLogging;
        }
        
        // Getters
        public boolean isRequireAuthentication() { return requireAuthentication; }
        public Set<String> getAllowedRoles() { return allowedRoles; }
        public Map<String, String> getAuthenticationProviders() { return authenticationProviders; }
        public boolean isEnableAuditLogging() { return enableAuditLogging; }
        
        public static Builder builder() { return new Builder(); }
        
        public static class Builder {
            private boolean requireAuthentication = true;
            private Set<String> allowedRoles = new HashSet<>();
            private Map<String, String> authenticationProviders = new HashMap<>();
            private boolean enableAuditLogging = true;
            
            public Builder requireAuthentication(boolean requireAuthentication) { 
                this.requireAuthentication = requireAuthentication; return this; 
            }
            public Builder allowedRoles(Set<String> allowedRoles) { 
                this.allowedRoles = new HashSet<>(allowedRoles); return this; 
            }
            public Builder addAllowedRole(String role) { 
                this.allowedRoles.add(role); return this; 
            }
            public Builder authenticationProviders(Map<String, String> authenticationProviders) { 
                this.authenticationProviders = new HashMap<>(authenticationProviders); return this; 
            }
            public Builder addAuthenticationProvider(String name, String config) { 
                this.authenticationProviders.put(name, config); return this; 
            }
            public Builder enableAuditLogging(boolean enableAuditLogging) { 
                this.enableAuditLogging = enableAuditLogging; return this; 
            }
            
            public SecuritySettings build() { return new SecuritySettings(this); }
        }
    }
    
    public static Builder builder() { return new Builder(); }
    
    public static class Builder {
        private ResourceLimits resourceLimits = ResourceLimits.builder().build();
        private TransferPolicies transferPolicies = TransferPolicies.builder().build();
        private SecuritySettings securitySettings = SecuritySettings.builder().build();
        private Map<String, Object> customSettings = new HashMap<>();
        
        public Builder resourceLimits(ResourceLimits resourceLimits) { 
            this.resourceLimits = resourceLimits; return this; 
        }
        public Builder transferPolicies(TransferPolicies transferPolicies) { 
            this.transferPolicies = transferPolicies; return this; 
        }
        public Builder securitySettings(SecuritySettings securitySettings) { 
            this.securitySettings = securitySettings; return this; 
        }
        public Builder customSettings(Map<String, Object> customSettings) { 
            this.customSettings = new HashMap<>(customSettings); return this; 
        }
        public Builder addCustomSetting(String key, Object value) { 
            this.customSettings.put(key, value); return this; 
        }
        
        public TenantConfiguration build() { return new TenantConfiguration(this); }
    }
}
