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

package dev.mars.quorus.tenant.service;

import dev.mars.quorus.tenant.model.Tenant;
import dev.mars.quorus.tenant.model.TenantConfiguration;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Simple in-memory implementation of TenantService.
 * This implementation is suitable for development and testing.
 * For production use, consider implementing a persistent storage backend.
 *
 * <p>Vert.x 5 Migration (Phase 3): Removed synchronized(lock) blocks.
 * ConcurrentHashMap provides thread-safety for individual operations.
 * For compound operations, this service should be deployed as a Verticle
 * which runs on a single event loop thread, eliminating the need for
 * explicit synchronization.</p>
 *
 * @author Mark Andrew Ray-Smith Cityline Ltd
 * @since 1.0
 */
public class SimpleTenantService implements TenantService {

    private static final Logger logger = Logger.getLogger(SimpleTenantService.class.getName());

    // ConcurrentHashMap provides thread-safety for individual operations
    // When deployed as a Verticle, all operations run on single event loop thread
    private final Map<String, Tenant> tenants = new ConcurrentHashMap<>();
    
    @Override
    public Tenant createTenant(Tenant tenant) throws TenantServiceException {
        // Validate tenant
        validateTenantForCreation(tenant);

        // Check if tenant ID already exists (putIfAbsent provides atomicity)
        Tenant existingCheck = tenants.get(tenant.getTenantId());
        if (existingCheck != null) {
            throw new TenantServiceException("Tenant with ID '" + tenant.getTenantId() + "' already exists");
        }

        // Validate hierarchy constraints
        validateTenantHierarchy(tenant);

        // Create tenant with timestamps
        Tenant newTenant = tenant.toBuilder()
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        // Store tenant atomically
        Tenant previous = tenants.putIfAbsent(newTenant.getTenantId(), newTenant);
        if (previous != null) {
            throw new TenantServiceException("Tenant with ID '" + newTenant.getTenantId() + "' already exists");
        }

        // Update parent's child list if this is not a root tenant
        if (newTenant.getParentTenantId() != null) {
            updateParentChildList(newTenant.getParentTenantId(), newTenant.getTenantId(), true);
        }

        logger.info("Created tenant: " + newTenant.getTenantId() + " (" + newTenant.getName() + ")");
        return newTenant;
    }
    
    @Override
    public Optional<Tenant> getTenant(String tenantId) {
        Tenant tenant = tenants.get(tenantId);
        if (tenant != null && tenant.getStatus() != Tenant.TenantStatus.DELETED) {
            return Optional.of(tenant);
        }
        return Optional.empty();
    }
    
    @Override
    public Tenant updateTenant(Tenant tenant) throws TenantServiceException {
        if (!tenants.containsKey(tenant.getTenantId())) {
            throw new TenantServiceException("Tenant not found: " + tenant.getTenantId());
        }

        Tenant existingTenant = tenants.get(tenant.getTenantId());
        if (existingTenant.getStatus() == Tenant.TenantStatus.DELETED) {
            throw new TenantServiceException("Cannot update deleted tenant: " + tenant.getTenantId());
        }

        // Validate hierarchy constraints if parent changed
        if (!Objects.equals(existingTenant.getParentTenantId(), tenant.getParentTenantId())) {
            validateTenantHierarchy(tenant);

            // Update old parent's child list
            if (existingTenant.getParentTenantId() != null) {
                updateParentChildList(existingTenant.getParentTenantId(), tenant.getTenantId(), false);
            }

            // Update new parent's child list
            if (tenant.getParentTenantId() != null) {
                updateParentChildList(tenant.getParentTenantId(), tenant.getTenantId(), true);
            }
        }

        // Update tenant with new timestamp
        Tenant updatedTenant = tenant.toBuilder()
                .createdAt(existingTenant.getCreatedAt()) // Preserve creation time
                .updatedAt(Instant.now())
                .build();

        tenants.put(updatedTenant.getTenantId(), updatedTenant);

        logger.info("Updated tenant: " + updatedTenant.getTenantId());
        return updatedTenant;
    }
    
    @Override
    public void deleteTenant(String tenantId) throws TenantServiceException {
        Tenant tenant = tenants.get(tenantId);
        if (tenant == null) {
            throw new TenantServiceException("Tenant not found: " + tenantId);
        }

        if (tenant.getStatus() == Tenant.TenantStatus.DELETED) {
            return; // Already deleted
        }

        // Check if tenant has active children
        List<Tenant> children = getChildTenants(tenantId);
        if (!children.isEmpty()) {
            throw new TenantServiceException("Cannot delete tenant with active children: " + tenantId);
        }

        // Mark as deleted
        Tenant deletedTenant = tenant.withStatus(Tenant.TenantStatus.DELETED);
        tenants.put(tenantId, deletedTenant);

        // Remove from parent's child list
        if (tenant.getParentTenantId() != null) {
            updateParentChildList(tenant.getParentTenantId(), tenantId, false);
        }

        logger.info("Deleted tenant: " + tenantId);
    }
    
    @Override
    public List<Tenant> getChildTenants(String parentTenantId) {
        return tenants.values().stream()
                .filter(tenant -> Objects.equals(tenant.getParentTenantId(), parentTenantId))
                .filter(tenant -> tenant.getStatus() != Tenant.TenantStatus.DELETED)
                .collect(Collectors.toList());
    }
    
    @Override
    public List<Tenant> getRootTenants() {
        return tenants.values().stream()
                .filter(tenant -> tenant.getParentTenantId() == null)
                .filter(tenant -> tenant.getStatus() != Tenant.TenantStatus.DELETED)
                .collect(Collectors.toList());
    }
    
    @Override
    public TenantHierarchy getTenantHierarchy(String rootTenantId) {
        Tenant rootTenant = tenants.get(rootTenantId);
        if (rootTenant == null || rootTenant.getStatus() == Tenant.TenantStatus.DELETED) {
            return null;
        }
        
        List<TenantHierarchy> children = getChildTenants(rootTenantId).stream()
                .map(child -> getTenantHierarchy(child.getTenantId()))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        
        return new TenantHierarchy(rootTenant, children);
    }
    
    @Override
    public List<Tenant> getTenantPath(String tenantId) {
        List<Tenant> path = new ArrayList<>();
        String currentTenantId = tenantId;
        
        while (currentTenantId != null) {
            Tenant tenant = tenants.get(currentTenantId);
            if (tenant == null || tenant.getStatus() == Tenant.TenantStatus.DELETED) {
                break;
            }
            
            path.add(0, tenant); // Add to beginning to maintain root-to-target order
            currentTenantId = tenant.getParentTenantId();
        }
        
        return path;
    }
    
    @Override
    public boolean tenantExists(String tenantId) {
        Tenant tenant = tenants.get(tenantId);
        return tenant != null && tenant.getStatus() != Tenant.TenantStatus.DELETED;
    }
    
    @Override
    public boolean isTenantActive(String tenantId) {
        Tenant tenant = tenants.get(tenantId);
        return tenant != null && tenant.getStatus() == Tenant.TenantStatus.ACTIVE;
    }
    
    @Override
    public Tenant updateTenantConfiguration(String tenantId, TenantConfiguration configuration)
            throws TenantServiceException {
        Tenant tenant = tenants.get(tenantId);
        if (tenant == null) {
            throw new TenantServiceException("Tenant not found: " + tenantId);
        }

        if (tenant.getStatus() == Tenant.TenantStatus.DELETED) {
            throw new TenantServiceException("Cannot update configuration for deleted tenant: " + tenantId);
        }

        Tenant updatedTenant = tenant.withConfiguration(configuration);
        tenants.put(tenantId, updatedTenant);

        logger.info("Updated configuration for tenant: " + tenantId);
        return updatedTenant;
    }
    
    @Override
    public void suspendTenant(String tenantId) throws TenantServiceException {
        updateTenantStatus(tenantId, Tenant.TenantStatus.SUSPENDED);
    }
    
    @Override
    public void activateTenant(String tenantId) throws TenantServiceException {
        updateTenantStatus(tenantId, Tenant.TenantStatus.ACTIVE);
    }
    
    @Override
    public TenantConfiguration getEffectiveConfiguration(String tenantId) {
        List<Tenant> path = getTenantPath(tenantId);
        if (path.isEmpty()) {
            return null;
        }
        
        // Start with default configuration
        TenantConfiguration.Builder configBuilder = TenantConfiguration.builder();
        
        // Apply configurations from root to target (inheritance)
        for (Tenant tenant : path) {
            if (tenant.getConfiguration() != null) {
                // Merge configurations (child overrides parent)
                mergeConfiguration(configBuilder, tenant.getConfiguration());
            }
        }
        
        return configBuilder.build();
    }
    
    @Override
    public void validateTenantHierarchy(Tenant tenant) throws TenantServiceException {
        if (tenant.getParentTenantId() == null) {
            return; // Root tenant is always valid
        }
        
        // Check if parent exists
        Tenant parent = tenants.get(tenant.getParentTenantId());
        if (parent == null || parent.getStatus() == Tenant.TenantStatus.DELETED) {
            throw new TenantServiceException("Parent tenant not found: " + tenant.getParentTenantId());
        }
        
        // Check for circular references
        if (wouldCreateCircularReference(tenant.getTenantId(), tenant.getParentTenantId())) {
            throw new TenantServiceException("Circular reference detected in tenant hierarchy");
        }
    }
    
    @Override
    public List<Tenant> searchTenantsByName(String namePattern) {
        String pattern = namePattern.toLowerCase().replace("*", ".*");
        return tenants.values().stream()
                .filter(tenant -> tenant.getStatus() != Tenant.TenantStatus.DELETED)
                .filter(tenant -> tenant.getName().toLowerCase().matches(pattern))
                .collect(Collectors.toList());
    }
    
    @Override
    public List<Tenant> getActiveTenants() {
        return tenants.values().stream()
                .filter(tenant -> tenant.getStatus() == Tenant.TenantStatus.ACTIVE)
                .collect(Collectors.toList());
    }
    
    // Helper methods
    
    private void validateTenantForCreation(Tenant tenant) throws TenantServiceException {
        if (tenant.getTenantId() == null || tenant.getTenantId().trim().isEmpty()) {
            throw new TenantServiceException("Tenant ID cannot be null or empty");
        }
        
        if (tenant.getName() == null || tenant.getName().trim().isEmpty()) {
            throw new TenantServiceException("Tenant name cannot be null or empty");
        }
    }
    
    private void updateParentChildList(String parentTenantId, String childTenantId, boolean add) {
        Tenant parent = tenants.get(parentTenantId);
        if (parent != null) {
            Set<String> children = new HashSet<>(parent.getChildTenantIds());
            if (add) {
                children.add(childTenantId);
            } else {
                children.remove(childTenantId);
            }
            
            Tenant updatedParent = parent.toBuilder()
                    .childTenantIds(children)
                    .updatedAt(Instant.now())
                    .build();
            
            tenants.put(parentTenantId, updatedParent);
        }
    }
    
    private void updateTenantStatus(String tenantId, Tenant.TenantStatus status) throws TenantServiceException {
        Tenant tenant = tenants.get(tenantId);
        if (tenant == null) {
            throw new TenantServiceException("Tenant not found: " + tenantId);
        }

        if (tenant.getStatus() == Tenant.TenantStatus.DELETED) {
            throw new TenantServiceException("Cannot update status for deleted tenant: " + tenantId);
        }

        Tenant updatedTenant = tenant.withStatus(status);
        tenants.put(tenantId, updatedTenant);

        logger.info("Updated tenant status: " + tenantId + " -> " + status);
    }
    
    private boolean wouldCreateCircularReference(String tenantId, String parentTenantId) {
        String currentParent = parentTenantId;
        Set<String> visited = new HashSet<>();
        
        while (currentParent != null) {
            if (currentParent.equals(tenantId)) {
                return true; // Circular reference detected
            }
            
            if (visited.contains(currentParent)) {
                return true; // Already visited, indicates a cycle
            }
            
            visited.add(currentParent);
            Tenant parent = tenants.get(currentParent);
            if (parent == null) {
                break;
            }
            
            currentParent = parent.getParentTenantId();
        }
        
        return false;
    }
    
    private void mergeConfiguration(TenantConfiguration.Builder builder, TenantConfiguration config) {
        // This is a simplified merge - in a real implementation, you'd want more sophisticated merging
        if (config.getResourceLimits() != null) {
            builder.resourceLimits(config.getResourceLimits());
        }
        if (config.getTransferPolicies() != null) {
            builder.transferPolicies(config.getTransferPolicies());
        }
        if (config.getSecuritySettings() != null) {
            builder.securitySettings(config.getSecuritySettings());
        }
        if (config.getCustomSettings() != null && !config.getCustomSettings().isEmpty()) {
            config.getCustomSettings().forEach(builder::addCustomSetting);
        }
    }
}
