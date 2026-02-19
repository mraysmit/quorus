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

package dev.mars.quorus.tenant.observability;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.LongCounter;
import io.opentelemetry.api.metrics.Meter;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * OpenTelemetry metrics for Quorus Tenant module.
 * Phase 7 of the OpenTelemetry migration.
 * 
 * Provides 10 tenant-specific metrics:
 * - quorus.tenant.total (gauge) - Total number of tenants
 * - quorus.tenant.active (gauge) - Number of active tenants
 * - quorus.tenant.created (counter) - Total tenants created
 * - quorus.tenant.deleted (counter) - Total tenants deleted
 * - quorus.tenant.quota.violations (counter) - Quota violation attempts
 * - quorus.tenant.resource.reservations (counter) - Resource reservation attempts
 * - quorus.tenant.resource.releases (counter) - Resource releases
 * - quorus.tenant.resource.operations (counter) - Total resource management operations
 * - quorus.tenant.resource.concurrent_transfers (gauge) - Current concurrent transfers per tenant
 * - quorus.tenant.resource.bandwidth (gauge) - Current bandwidth usage per tenant
 *
 * @author Mark Andrew Ray-Smith Cityline Ltd
 * @since 2026-01-27
 * @version 1.0 (OpenTelemetry)
 */
public class TenantMetrics {

    private static final Logger logger = LoggerFactory.getLogger(TenantMetrics.class);
    private static final String METER_NAME = "quorus-tenant";

    // Singleton instance
    private static TenantMetrics instance;

    // Counters
    private final LongCounter tenantsCreated;
    private final LongCounter tenantsDeleted;
    private final LongCounter quotaViolations;
    private final LongCounter resourceReservations;
    private final LongCounter resourceReleases;
    private final LongCounter resourceOperations;

    // Gauges (backed by AtomicLong for thread-safe updates)
    private final AtomicLong totalTenants = new AtomicLong(0);
    private final AtomicLong activeTenants = new AtomicLong(0);

    // Resource usage gauge suppliers - registered by ResourceManagementService
    private final ConcurrentHashMap<String, Supplier<Long>> concurrentTransfersSuppliers = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Supplier<Long>> bandwidthSuppliers = new ConcurrentHashMap<>();

    // Attribute keys
    private static final AttributeKey<String> TENANT_ID_KEY = AttributeKey.stringKey("tenant.id");
    private static final AttributeKey<String> RESOURCE_TYPE_KEY = AttributeKey.stringKey("resource.type");
    private static final AttributeKey<String> VIOLATION_TYPE_KEY = AttributeKey.stringKey("violation.type");

    private TenantMetrics() {
        Meter meter = GlobalOpenTelemetry.getMeter(METER_NAME);

        // Initialize counters
        tenantsCreated = meter.counterBuilder("quorus.tenant.created")
                .setDescription("Total number of tenants created")
                .setUnit("1")
                .build();

        tenantsDeleted = meter.counterBuilder("quorus.tenant.deleted")
                .setDescription("Total number of tenants deleted")
                .setUnit("1")
                .build();

        quotaViolations = meter.counterBuilder("quorus.tenant.quota.violations")
                .setDescription("Number of quota violation attempts")
                .setUnit("1")
                .build();

        resourceReservations = meter.counterBuilder("quorus.tenant.resource.reservations")
                .setDescription("Number of resource reservation attempts")
                .setUnit("1")
                .build();

        resourceReleases = meter.counterBuilder("quorus.tenant.resource.releases")
                .setDescription("Number of resource releases")
                .setUnit("1")
                .build();

        resourceOperations = meter.counterBuilder("quorus.tenant.resource.operations")
                .setDescription("Total resource management operations processed")
                .setUnit("1")
                .build();

        // Initialize gauges
        meter.gaugeBuilder("quorus.tenant.total")
                .setDescription("Total number of tenants in the system")
                .ofLongs()
                .buildWithCallback(measurement -> measurement.record(totalTenants.get()));

        meter.gaugeBuilder("quorus.tenant.active")
                .setDescription("Number of active tenants")
                .ofLongs()
                .buildWithCallback(measurement -> measurement.record(activeTenants.get()));

        // Per-tenant resource gauges — populated via registerTenantGauges()
        meter.gaugeBuilder("quorus.tenant.resource.concurrent_transfers")
                .setDescription("Current concurrent transfers per tenant")
                .ofLongs()
                .buildWithCallback(measurement -> {
                    for (Map.Entry<String, Supplier<Long>> entry : concurrentTransfersSuppliers.entrySet()) {
                        measurement.record(entry.getValue().get(),
                                Attributes.of(TENANT_ID_KEY, entry.getKey()));
                    }
                });

        meter.gaugeBuilder("quorus.tenant.resource.bandwidth")
                .setDescription("Current bandwidth usage per tenant (bytes/sec)")
                .setUnit("By/s")
                .ofLongs()
                .buildWithCallback(measurement -> {
                    for (Map.Entry<String, Supplier<Long>> entry : bandwidthSuppliers.entrySet()) {
                        measurement.record(entry.getValue().get(),
                                Attributes.of(TENANT_ID_KEY, entry.getKey()));
                    }
                });

        logger.info("TenantMetrics initialized");
    }

    /**
     * Get the singleton instance of TenantMetrics.
     */
    public static synchronized TenantMetrics getInstance() {
        if (instance == null) {
            instance = new TenantMetrics();
        }
        return instance;
    }

    // Tenant lifecycle methods
    public void recordTenantCreated(String tenantId) {
        tenantsCreated.add(1, Attributes.of(TENANT_ID_KEY, tenantId));
        totalTenants.incrementAndGet();
        activeTenants.incrementAndGet();
    }

    public void recordTenantDeleted(String tenantId) {
        tenantsDeleted.add(1, Attributes.of(TENANT_ID_KEY, tenantId));
        activeTenants.decrementAndGet();
    }

    public void recordTenantSuspended(String tenantId) {
        activeTenants.decrementAndGet();
    }

    public void recordTenantActivated(String tenantId) {
        activeTenants.incrementAndGet();
    }

    // Quota methods
    public void recordQuotaViolation(String tenantId, String violationType) {
        Attributes attrs = Attributes.builder()
                .put(TENANT_ID_KEY, tenantId)
                .put(VIOLATION_TYPE_KEY, violationType)
                .build();
        quotaViolations.add(1, attrs);
    }

    // Resource management methods
    public void recordResourceReservation(String tenantId, String resourceType, boolean success) {
        Attributes attrs = Attributes.builder()
                .put(TENANT_ID_KEY, tenantId)
                .put(RESOURCE_TYPE_KEY, resourceType)
                .build();
        resourceReservations.add(1, attrs);
    }

    public void recordResourceRelease(String tenantId, String resourceType) {
        Attributes attrs = Attributes.builder()
                .put(TENANT_ID_KEY, tenantId)
                .put(RESOURCE_TYPE_KEY, resourceType)
                .build();
        resourceReleases.add(1, attrs);
    }

    // Bulk update methods (for service initialization)
    public void setTenantCounts(long total, long active) {
        totalTenants.set(total);
        activeTenants.set(active);
    }

    // Resource tracking methods
    
    /**
     * Record a resource management operation (any counter update, reservation, etc.).
     */
    public void recordResourceOperation(String tenantId) {
        resourceOperations.add(1, Attributes.of(TENANT_ID_KEY, tenantId));
    }

    /**
     * Register per-tenant gauge suppliers for observable resource metrics.
     * Call this when a tenant first appears in the resource management service.
     *
     * @param tenantId the tenant identifier
     * @param concurrentTransfers supplier returning current concurrent transfers count
     * @param bandwidth supplier returning current bandwidth in bytes/sec
     */
    public void registerTenantGauges(String tenantId, 
                                      Supplier<Long> concurrentTransfers,
                                      Supplier<Long> bandwidth) {
        concurrentTransfersSuppliers.put(tenantId, concurrentTransfers);
        bandwidthSuppliers.put(tenantId, bandwidth);
    }


}
