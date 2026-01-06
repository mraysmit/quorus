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
 * Description for TenantService
 *
 * @author Mark Andrew Ray-Smith Cityline Ltd
 * @version 1.0
 * @since 2025-08-18
 */

public interface TenantService {
    
    Tenant createTenant(Tenant tenant) throws TenantServiceException;
    
    Optional<Tenant> getTenant(String tenantId);
    
    Tenant updateTenant(Tenant tenant) throws TenantServiceException;
    
    void deleteTenant(String tenantId) throws TenantServiceException;
    
    List<Tenant> getChildTenants(String parentTenantId);
    
    List<Tenant> getRootTenants();
    
    TenantHierarchy getTenantHierarchy(String rootTenantId);
    
    List<Tenant> getTenantPath(String tenantId);
    
    boolean tenantExists(String tenantId);
    
    boolean isTenantActive(String tenantId);
    
    Tenant updateTenantConfiguration(String tenantId, TenantConfiguration configuration) 
            throws TenantServiceException;
    
    void suspendTenant(String tenantId) throws TenantServiceException;
    
    void activateTenant(String tenantId) throws TenantServiceException;
    
    TenantConfiguration getEffectiveConfiguration(String tenantId);
    
    void validateTenantHierarchy(Tenant tenant) throws TenantServiceException;
    
    List<Tenant> searchTenantsByName(String namePattern);
    
    List<Tenant> getActiveTenants();
    
    class TenantServiceException extends Exception {
        public TenantServiceException(String message) {
            super(message);
        }
        
        public TenantServiceException(String message, Throwable cause) {
            super(message, cause);
        }
    }
    
    class TenantHierarchy {
        private final Tenant rootTenant;
        private final List<TenantHierarchy> children;
        
        public TenantHierarchy(Tenant rootTenant, List<TenantHierarchy> children) {
            this.rootTenant = rootTenant;
            this.children = children;
        }
        
        public Tenant getRootTenant() { return rootTenant; }
        public List<TenantHierarchy> getChildren() { return children; }
        
        public int getTotalTenantCount() {
            return 1 + children.stream().mapToInt(TenantHierarchy::getTotalTenantCount).sum();
        }
        
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
