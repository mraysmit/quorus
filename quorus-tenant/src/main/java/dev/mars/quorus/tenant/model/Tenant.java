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

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.*;

public class Tenant {
    
    @NotBlank
    private final String tenantId;
    
    @NotBlank
    private final String name;
    
    private final String description;
    
    private final String parentTenantId;
    
    @NotNull
    private final TenantStatus status;
    
    @NotNull
    private final Instant createdAt;
    
    private final Instant updatedAt;
    
    private final Map<String, String> metadata;
    
    private final TenantConfiguration configuration;
    
    private final Set<String> childTenantIds;
    
    public enum TenantStatus {
        ACTIVE,
        SUSPENDED,
        INACTIVE,
        DELETED
    }
    
    private Tenant(Builder builder) {
        this.tenantId = builder.tenantId;
        this.name = builder.name;
        this.description = builder.description;
        this.parentTenantId = builder.parentTenantId;
        this.status = builder.status;
        this.createdAt = builder.createdAt;
        this.updatedAt = builder.updatedAt;
        this.metadata = Collections.unmodifiableMap(new HashMap<>(builder.metadata));
        this.configuration = builder.configuration;
        this.childTenantIds = Collections.unmodifiableSet(new HashSet<>(builder.childTenantIds));
    }
    
    // Getters
    public String getTenantId() { return tenantId; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public String getParentTenantId() { return parentTenantId; }
    public TenantStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Map<String, String> getMetadata() { return metadata; }
    public TenantConfiguration getConfiguration() { return configuration; }
    public Set<String> getChildTenantIds() { return childTenantIds; }
    
    public boolean isRootTenant() {
        return parentTenantId == null;
    }
    
    public boolean hasChildren() {
        return !childTenantIds.isEmpty();
    }
    
    public boolean isActive() {
        return status == TenantStatus.ACTIVE;
    }
    
    public Tenant withStatus(TenantStatus newStatus) {
        return toBuilder()
                .status(newStatus)
                .updatedAt(Instant.now())
                .build();
    }
    
    public Tenant withConfiguration(TenantConfiguration newConfiguration) {
        return toBuilder()
                .configuration(newConfiguration)
                .updatedAt(Instant.now())
                .build();
    }
    
    /**
     * Create a builder from this tenant
     */
    public Builder toBuilder() {
        return new Builder()
                .tenantId(tenantId)
                .name(name)
                .description(description)
                .parentTenantId(parentTenantId)
                .status(status)
                .createdAt(createdAt)
                .updatedAt(updatedAt)
                .metadata(metadata)
                .configuration(configuration)
                .childTenantIds(childTenantIds);
    }
    
    public static Builder builder() {
        return new Builder();
    }
    
    public static class Builder {
        private String tenantId;
        private String name;
        private String description;
        private String parentTenantId;
        private TenantStatus status = TenantStatus.ACTIVE;
        private Instant createdAt = Instant.now();
        private Instant updatedAt;
        private Map<String, String> metadata = new HashMap<>();
        private TenantConfiguration configuration;
        private Set<String> childTenantIds = new HashSet<>();
        
        public Builder tenantId(String tenantId) { this.tenantId = tenantId; return this; }
        public Builder name(String name) { this.name = name; return this; }
        public Builder description(String description) { this.description = description; return this; }
        public Builder parentTenantId(String parentTenantId) { this.parentTenantId = parentTenantId; return this; }
        public Builder status(TenantStatus status) { this.status = status; return this; }
        public Builder createdAt(Instant createdAt) { this.createdAt = createdAt; return this; }
        public Builder updatedAt(Instant updatedAt) { this.updatedAt = updatedAt; return this; }
        public Builder metadata(Map<String, String> metadata) { this.metadata = new HashMap<>(metadata); return this; }
        public Builder addMetadata(String key, String value) { this.metadata.put(key, value); return this; }
        public Builder configuration(TenantConfiguration configuration) { this.configuration = configuration; return this; }
        public Builder childTenantIds(Set<String> childTenantIds) { this.childTenantIds = new HashSet<>(childTenantIds); return this; }
        public Builder addChildTenant(String childTenantId) { this.childTenantIds.add(childTenantId); return this; }
        
        public Tenant build() {
            Objects.requireNonNull(tenantId, "tenantId cannot be null");
            Objects.requireNonNull(name, "name cannot be null");
            Objects.requireNonNull(status, "status cannot be null");
            Objects.requireNonNull(createdAt, "createdAt cannot be null");
            
            return new Tenant(this);
        }
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Tenant tenant = (Tenant) o;
        return Objects.equals(tenantId, tenant.tenantId);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(tenantId);
    }
    
    @Override
    public String toString() {
        return "Tenant{" +
                "tenantId='" + tenantId + '\'' +
                ", name='" + name + '\'' +
                ", status=" + status +
                ", parentTenantId='" + parentTenantId + '\'' +
                ", childrenCount=" + childTenantIds.size() +
                '}';
    }
}
