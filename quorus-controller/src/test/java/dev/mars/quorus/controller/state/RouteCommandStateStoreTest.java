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

package dev.mars.quorus.controller.state;

import dev.mars.quorus.core.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for RouteCommand operations in the QuorusStateStore.
 * Verifies the full lifecycle: create → update → suspend → resume → delete,
 * plus snapshot/restore preservation of route state.
 *
 * @author Mark Andrew Ray-Smith Cityline Ltd
 * @version 1.0
 * @since 2026-02-19
 */
class RouteCommandStateStoreTest {

    private QuorusStateStore stateMachine;

    @BeforeEach
    void setUp() {
        stateMachine = new QuorusStateStore();
    }

    // ========== Helper factories ==========

    private RouteConfiguration createRoute(String routeId, String name) {
        return new RouteConfiguration(
                routeId,
                name,
                "Test route description",
                "agent-source-01",
                "/data/source/",
                "agent-dest-01",
                "/data/dest/",
                TriggerConfiguration.interval(60),
                RouteStatus.CONFIGURED,
                Map.of("retryCount", "3"),
                Instant.parse("2026-02-19T10:00:00Z"),
                Instant.parse("2026-02-19T10:00:00Z"));
    }

    private RouteConfiguration createRouteWithEventTrigger(String routeId) {
        return new RouteConfiguration(
                routeId,
                "event-route",
                "Event-triggered route",
                "agent-crm-01",
                "/corporate-data/crm/export/",
                "agent-warehouse-01",
                "/corporate-data/warehouse/import/",
                TriggerConfiguration.event(List.of("*.csv"), List.of(), 0),
                RouteStatus.CONFIGURED,
                null,
                Instant.now(),
                Instant.now());
    }

    // ========== CREATE ==========

    @Nested
    @DisplayName("Route CREATE operations")
    class CreateTests {

        @Test
        @DisplayName("Create route stores it in state machine")
        void createRouteStoresInStateMachine() {
            RouteConfiguration route = createRoute("route-001", "test-route");
            RouteCommand command = RouteCommand.create(route);

            Object result = stateMachine.apply(command);

            assertNotNull(result);
            assertTrue(result instanceof RouteConfiguration);
            assertEquals("route-001", ((RouteConfiguration) result).getRouteId());
            assertEquals(1, stateMachine.getRouteCount());
            assertTrue(stateMachine.hasRoute("route-001"));
        }

        @Test
        @DisplayName("Create route returns the created configuration")
        void createRouteReturnsCopy() {
            RouteConfiguration route = createRoute("route-002", "another-route");
            RouteCommand command = RouteCommand.create(route);

            RouteConfiguration result = (RouteConfiguration) stateMachine.apply(command);

            assertEquals("route-002", result.getRouteId());
            assertEquals("another-route", result.getName());
            assertEquals(RouteStatus.CONFIGURED, result.getStatus());
            assertEquals("agent-source-01", result.getSourceAgentId());
            assertEquals("/data/source/", result.getSourceLocation());
            assertEquals("agent-dest-01", result.getDestinationAgentId());
            assertEquals("/data/dest/", result.getDestinationLocation());
        }

        @Test
        @DisplayName("Create duplicate route overwrites existing")
        void createDuplicateRouteOverwrites() {
            RouteConfiguration route = createRoute("route-dup", "dup-route");
            stateMachine.apply(RouteCommand.create(route));

            RouteConfiguration duplicate = createRoute("route-dup", "dup-route-v2");
            Object result = stateMachine.apply(RouteCommand.create(duplicate));

            assertNotNull(result);
            assertEquals(1, stateMachine.getRouteCount());
            // Verify new version replaces old
            assertEquals("dup-route-v2", stateMachine.getRoute("route-dup").getName());
        }

        @Test
        @DisplayName("Create multiple routes tracks all")
        void createMultipleRoutes() {
            stateMachine.apply(RouteCommand.create(createRoute("r1", "route-1")));
            stateMachine.apply(RouteCommand.create(createRoute("r2", "route-2")));
            stateMachine.apply(RouteCommand.create(createRoute("r3", "route-3")));

            assertEquals(3, stateMachine.getRouteCount());
            assertNotNull(stateMachine.getRoute("r1"));
            assertNotNull(stateMachine.getRoute("r2"));
            assertNotNull(stateMachine.getRoute("r3"));
        }
    }

    // ========== UPDATE ==========

    @Nested
    @DisplayName("Route UPDATE operations")
    class UpdateTests {

        @Test
        @DisplayName("Update route modifies stored configuration")
        void updateRouteModifiesConfiguration() {
            RouteConfiguration route = createRoute("route-upd", "original-name");
            stateMachine.apply(RouteCommand.create(route));

            RouteConfiguration updatedConfig = new RouteConfiguration(
                    "route-upd", "updated-name", "Updated description",
                    "agent-new-source", "/new/source/",
                    "agent-new-dest", "/new/dest/",
                    TriggerConfiguration.interval(30),
                    RouteStatus.CONFIGURED,
                    Map.of("retryCount", "5"),
                    null, null);

            Object result = stateMachine.apply(RouteCommand.update("route-upd", updatedConfig));

            assertNotNull(result);
            RouteConfiguration stored = stateMachine.getRoute("route-upd");
            assertEquals("updated-name", stored.getName());
            assertEquals("Updated description", stored.getDescription());
            assertEquals("agent-new-source", stored.getSourceAgentId());
        }

        @Test
        @DisplayName("Update non-existent route returns null")
        void updateNonExistentRouteReturnsNull() {
            RouteConfiguration update = createRoute("ghost-route", "ghost");
            Object result = stateMachine.apply(RouteCommand.update("ghost-route", update));

            assertNull(result);
            assertFalse(stateMachine.hasRoute("ghost-route"));
        }
    }

    // ========== DELETE ==========

    @Nested
    @DisplayName("Route DELETE operations")
    class DeleteTests {

        @Test
        @DisplayName("Delete route removes it from state machine")
        void deleteRouteRemovesFromStateMachine() {
            stateMachine.apply(RouteCommand.create(createRoute("route-del", "delete-me")));
            assertEquals(1, stateMachine.getRouteCount());

            Object result = stateMachine.apply(RouteCommand.delete("route-del"));

            assertNotNull(result);
            assertEquals(0, stateMachine.getRouteCount());
            assertFalse(stateMachine.hasRoute("route-del"));
            assertNull(stateMachine.getRoute("route-del"));
        }

        @Test
        @DisplayName("Delete non-existent route returns null")
        void deleteNonExistentRouteReturnsNull() {
            Object result = stateMachine.apply(RouteCommand.delete("no-such-route"));
            assertNull(result);
        }
    }

    // ========== SUSPEND / RESUME ==========

    @Nested
    @DisplayName("Route SUSPEND and RESUME operations")
    class SuspendResumeTests {

        @Test
        @DisplayName("Suspend route changes status to SUSPENDED")
        void suspendRouteSetsStatus() {
            stateMachine.apply(RouteCommand.create(createRoute("route-sus", "suspend-test")));

            Object result = stateMachine.apply(RouteCommand.suspend("route-sus", "maintenance window"));

            assertNotNull(result);
            RouteConfiguration stored = stateMachine.getRoute("route-sus");
            assertEquals(RouteStatus.SUSPENDED, stored.getStatus());
        }

        @Test
        @DisplayName("Resume route changes status to ACTIVE")
        void resumeRouteSetsStatus() {
            stateMachine.apply(RouteCommand.create(createRoute("route-res", "resume-test")));
            stateMachine.apply(RouteCommand.suspend("route-res", null));

            Object result = stateMachine.apply(RouteCommand.resume("route-res"));

            assertNotNull(result);
            RouteConfiguration stored = stateMachine.getRoute("route-res");
            assertEquals(RouteStatus.ACTIVE, stored.getStatus());
        }

        @Test
        @DisplayName("Suspend non-existent route returns null")
        void suspendNonExistentRouteReturnsNull() {
            Object result = stateMachine.apply(RouteCommand.suspend("ghost", "reason"));
            assertNull(result);
        }

        @Test
        @DisplayName("Resume non-existent route returns null")
        void resumeNonExistentRouteReturnsNull() {
            Object result = stateMachine.apply(RouteCommand.resume("ghost"));
            assertNull(result);
        }
    }

    // ========== UPDATE_STATUS ==========

    @Nested
    @DisplayName("Route UPDATE_STATUS operations")
    class UpdateStatusTests {

        @Test
        @DisplayName("Update status transitions correctly")
        void updateStatusTransitions() {
            stateMachine.apply(RouteCommand.create(createRoute("route-st", "status-test")));

            stateMachine.apply(RouteCommand.updateStatus("route-st", RouteStatus.ACTIVE, "activated"));
            assertEquals(RouteStatus.ACTIVE, stateMachine.getRoute("route-st").getStatus());

            stateMachine.apply(RouteCommand.updateStatus("route-st", RouteStatus.TRIGGERED, "file arrived"));
            assertEquals(RouteStatus.TRIGGERED, stateMachine.getRoute("route-st").getStatus());

            stateMachine.apply(RouteCommand.updateStatus("route-st", RouteStatus.TRANSFERRING, "transfer started"));
            assertEquals(RouteStatus.TRANSFERRING, stateMachine.getRoute("route-st").getStatus());

            stateMachine.apply(RouteCommand.updateStatus("route-st", RouteStatus.ACTIVE, "transfer completed"));
            assertEquals(RouteStatus.ACTIVE, stateMachine.getRoute("route-st").getStatus());
        }

        @Test
        @DisplayName("Update status on non-existent route returns null")
        void updateStatusNonExistent() {
            Object result = stateMachine.apply(
                    RouteCommand.updateStatus("ghost", RouteStatus.ACTIVE, "nope"));
            assertNull(result);
        }

        @Test
        @DisplayName("Degraded and Failed statuses apply correctly")
        void degradedAndFailedStatuses() {
            stateMachine.apply(RouteCommand.create(createRoute("route-fail", "fail-test")));

            stateMachine.apply(RouteCommand.updateStatus("route-fail", RouteStatus.DEGRADED, "partial failure"));
            assertEquals(RouteStatus.DEGRADED, stateMachine.getRoute("route-fail").getStatus());

            stateMachine.apply(RouteCommand.updateStatus("route-fail", RouteStatus.FAILED, "permanent failure"));
            assertEquals(RouteStatus.FAILED, stateMachine.getRoute("route-fail").getStatus());
        }
    }

    // ========== Snapshot / Restore ==========

    @Nested
    @DisplayName("Route snapshot and restore")
    class SnapshotTests {

        @Test
        @DisplayName("Routes survive snapshot and restore")
        void routesSurviveSnapshotRestore() {
            // Create some routes
            stateMachine.apply(RouteCommand.create(createRoute("r1", "route-1")));
            stateMachine.apply(RouteCommand.create(createRouteWithEventTrigger("r2")));
            stateMachine.apply(RouteCommand.suspend("r1", "maintenance"));
            stateMachine.setLastAppliedIndex(5);

            // Take snapshot
            byte[] snapshot = stateMachine.takeSnapshot();
            assertNotNull(snapshot);
            assertTrue(snapshot.length > 0);

            // Create fresh state machine and restore
            QuorusStateStore restored = new QuorusStateStore();
            restored.restoreSnapshot(snapshot);

            // Verify routes restored
            assertEquals(2, restored.getRouteCount());
            assertTrue(restored.hasRoute("r1"));
            assertTrue(restored.hasRoute("r2"));

            RouteConfiguration r1 = restored.getRoute("r1");
            assertEquals("route-1", r1.getName());
            assertEquals(RouteStatus.SUSPENDED, r1.getStatus());

            RouteConfiguration r2 = restored.getRoute("r2");
            assertEquals("event-route", r2.getName());
            assertEquals(RouteStatus.CONFIGURED, r2.getStatus());
            assertNotNull(r2.getTrigger());
            assertEquals(TriggerType.EVENT, r2.getTrigger().getType());
        }

        @Test
        @DisplayName("Reset clears all routes")
        void resetClearsAllRoutes() {
            stateMachine.apply(RouteCommand.create(createRoute("r1", "route-1")));
            stateMachine.apply(RouteCommand.create(createRoute("r2", "route-2")));
            assertEquals(2, stateMachine.getRouteCount());

            stateMachine.reset();

            assertEquals(0, stateMachine.getRouteCount());
            assertFalse(stateMachine.hasRoute("r1"));
            assertFalse(stateMachine.hasRoute("r2"));
        }
    }

    // ========== Query / Getters ==========

    @Nested
    @DisplayName("Route query operations")
    class QueryTests {

        @Test
        @DisplayName("getRoutes returns defensive copy")
        void getRoutesReturnsDefensiveCopy() {
            stateMachine.apply(RouteCommand.create(createRoute("r1", "route-1")));

            Map<String, RouteConfiguration> routes = stateMachine.getRoutes();
            routes.clear(); // modify the copy

            // Original should be unaffected
            assertEquals(1, stateMachine.getRouteCount());
            assertTrue(stateMachine.hasRoute("r1"));
        }

        @Test
        @DisplayName("getRoute returns null for non-existent route")
        void getRouteReturnsNullForNonExistent() {
            assertNull(stateMachine.getRoute("no-such-route"));
        }

        @Test
        @DisplayName("hasRoute returns false for non-existent route")
        void hasRouteReturnsFalseForNonExistent() {
            assertFalse(stateMachine.hasRoute("no-such-route"));
        }
    }

    // ========== Full Lifecycle ==========

    @Test
    @DisplayName("Full route lifecycle: create → activate → trigger → transfer → complete → suspend → resume → delete")
    void fullRouteLifecycle() {
        String routeId = "lifecycle-route";

        // 1. Create
        stateMachine.apply(RouteCommand.create(createRoute(routeId, "lifecycle-test")));
        assertEquals(RouteStatus.CONFIGURED, stateMachine.getRoute(routeId).getStatus());

        // 2. Activate
        stateMachine.apply(RouteCommand.updateStatus(routeId, RouteStatus.ACTIVE, "activated by admin"));
        assertEquals(RouteStatus.ACTIVE, stateMachine.getRoute(routeId).getStatus());

        // 3. Trigger fires
        stateMachine.apply(RouteCommand.updateStatus(routeId, RouteStatus.TRIGGERED, "interval elapsed"));
        assertEquals(RouteStatus.TRIGGERED, stateMachine.getRoute(routeId).getStatus());

        // 4. Transfer starts
        stateMachine.apply(RouteCommand.updateStatus(routeId, RouteStatus.TRANSFERRING, "transfer initiated"));
        assertEquals(RouteStatus.TRANSFERRING, stateMachine.getRoute(routeId).getStatus());

        // 5. Transfer completes → back to ACTIVE
        stateMachine.apply(RouteCommand.updateStatus(routeId, RouteStatus.ACTIVE, "transfer completed"));
        assertEquals(RouteStatus.ACTIVE, stateMachine.getRoute(routeId).getStatus());

        // 6. Suspend for maintenance
        stateMachine.apply(RouteCommand.suspend(routeId, "planned maintenance"));
        assertEquals(RouteStatus.SUSPENDED, stateMachine.getRoute(routeId).getStatus());

        // 7. Resume
        stateMachine.apply(RouteCommand.resume(routeId));
        assertEquals(RouteStatus.ACTIVE, stateMachine.getRoute(routeId).getStatus());

        // 8. Delete
        Object deleted = stateMachine.apply(RouteCommand.delete(routeId));
        assertNotNull(deleted);
        assertEquals(0, stateMachine.getRouteCount());
    }
}
