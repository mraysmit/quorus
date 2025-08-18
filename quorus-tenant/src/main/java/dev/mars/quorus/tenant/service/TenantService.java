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

import java.util.List;
import java.util.Optional;

/**
 * Service interface for tenant lifecycle management.
 * Provides operations for creating, updating, deleting, and querying tenants
 * with support for hierarchical tenant structures.
 */
public interface TenantService {
    
    /**
     * Create a new tenant
     * 
     * @param tenant the tenant to create
     * @return the created tenant with generated ID and timestamps
     * @throws TenantServiceException if creation fails
     */
    Tenant createTenant(Tenant tenant) throws TenantServiceException;
    
    /**
     * Get a tenant by ID
     * 
     * @param tenantId the tenant ID
     * @return the tenant if found
     */
    Optional<Tenant> getTenant(String tenantId);
    
    /**
     * Update an existing tenant
     * 
     * @param tenant the tenant to update
     * @return the updated tenant
     * @throws TenantServiceException if update fails or tenant not found
     */
    Tenant updateTenant(Tenant tenant) throws TenantServiceException;
    
    /**
     * Delete a tenant (soft delete - marks as DELETED)
     * 
     * @param tenantId the tenant ID to delete
     * @throws TenantServiceException if deletion fails or tenant not found
     */
    void deleteTenant(String tenantId) throws TenantServiceException;
    
    /**
     * Get all child tenants of a parent tenant
     * 
     * @param parentTenantId the parent tenant ID
     * @return list of child tenants
     */
    List<Tenant> getChildTenants(String parentTenantId);
    
    /**
     * Get all root tenants (tenants with no parent)
     * 
     * @return list of root tenants
     */
    List<Tenant> getRootTenants();
    
    /**
     * Get the complete tenant hierarchy starting from a root tenant
     * 
     * @param rootTenantId the root tenant ID
     * @return hierarchical structure of tenants
     */
    TenantHierarchy getTenantHierarchy(String rootTenantId);
    
    /**
     * Get all tenants in the hierarchy path from root to the specified tenant
     * 
     * @param tenantId the target tenant ID
     * @return list of tenants from root to target (inclusive)
     */
    List<Tenant> getTenantPath(String tenantId);
    
    /**
     * Check if a tenant exists
     * 
     * @param tenantId the tenant ID
     * @return true if tenant exists and is not deleted
     */
    boolean tenantExists(String tenantId);
    
    /**
     * Check if a tenant is active
     * 
     * @param tenantId the tenant ID
     * @return true if tenant exists and is active
     */
    boolean isTenantActive(String tenantId);
    
    /**
     * Update tenant configuration
     * 
     * @param tenantId the tenant ID
     * @param configuration the new configuration
     * @return the updated tenant
     * @throws TenantServiceException if update fails or tenant not found
     */
    Tenant updateTenantConfiguration(String tenantId, TenantConfiguration configuration) 
            throws TenantServiceException;
    
    /**
     * Suspend a tenant (sets status to SUSPENDED)
     * 
     * @param tenantId the tenant ID
     * @throws TenantServiceException if operation fails or tenant not found
     */
    void suspendTenant(String tenantId) throws TenantServiceException;
    
    /**
     * Activate a tenant (sets status to ACTIVE)
     * 
     * @param tenantId the tenant ID
     * @throws TenantServiceException if operation fails or tenant not found
     */
    void activateTenant(String tenantId) throws TenantServiceException;
    
    /**
     * Get effective configuration for a tenant, including inherited settings from parent tenants
     * 
     * @param tenantId the tenant ID
     * @return effective configuration with inheritance applied
     */
    TenantConfiguration getEffectiveConfiguration(String tenantId);
    
    /**
     * Validate tenant hierarchy constraints
     * 
     * @param tenant the tenant to validate
     * @throws TenantServiceException if validation fails
     */
    void validateTenantHierarchy(Tenant tenant) throws TenantServiceException;
    
    /**
     * Search tenants by name pattern
     * 
     * @param namePattern the name pattern (supports wildcards)
     * @return list of matching tenants
     */
    List<Tenant> searchTenantsByName(String namePattern);
    
    /**
     * Get all active tenants
     * 
     * @return list of active tenants
     */
    List<Tenant> getActiveTenants();
    
    /**
     * Exception thrown by tenant service operations
     */
    class TenantServiceException extends Exception {
        public TenantServiceException(String message) {
            super(message);
        }
        
        public TenantServiceException(String message, Throwable cause) {
            super(message, cause);
        }
    }
    
    /**
     * Represents a hierarchical structure of tenants
     */
    class TenantHierarchy {
        private final Tenant rootTenant;
        private final List<TenantHierarchy> children;
        
        public TenantHierarchy(Tenant rootTenant, List<TenantHierarchy> children) {
            this.rootTenant = rootTenant;
            this.children = children;
        }
        
        public Tenant getRootTenant() { return rootTenant; }
        public List<TenantHierarchy> getChildren() { return children; }
        
        /**
         * Get total number of tenants in this hierarchy
         */
        public int getTotalTenantCount() {
            return 1 + children.stream().mapToInt(TenantHierarchy::getTotalTenantCount).sum();
        }
        
        /**
         * Get maximum depth of this hierarchy
         */
        public int getMaxDepth() {
            if (children.isEmpty()) return 1;
            return 1 + children.stream().mapToInt(TenantHierarchy::getMaxDepth).max().orElse(0);
        }
        
        /**
         * Find a tenant in this hierarchy by ID
         */
        public Optional<Tenant> findTenant(String tenantId) {
            if (rootTenant.getTenantId().equals(tenantId)) {
                return Optional.of(rootTenant);
            }

            for (TenantHierarchy child : children) {
                Optional<Tenant> found = child.findTenant(tenantId);
                if (found.isPresent()) {
                    return found;
                }
            }

            return Optional.empty();
        }
    }
}
