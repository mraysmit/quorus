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

package dev.mars.quorus.controller.http.handlers;

import dev.mars.quorus.controller.http.ErrorCode;
import dev.mars.quorus.controller.http.QuorusApiException;
import dev.mars.quorus.controller.raft.RaftNode;
import dev.mars.quorus.controller.state.CommandResult;
import dev.mars.quorus.controller.state.QuorusStateStore;
import dev.mars.quorus.controller.state.RouteCommand;
import dev.mars.quorus.core.RouteConfiguration;
import dev.mars.quorus.core.RouteStatus;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * HTTP handler for route operations.
 *
 * <p>Endpoints:
 * <ul>
 *   <li>{@code POST /api/v1/routes} — Create a new route</li>
 *   <li>{@code GET /api/v1/routes} — List all routes</li>
 *   <li>{@code GET /api/v1/routes/:routeId} — Get a specific route</li>
 *   <li>{@code PUT /api/v1/routes/:routeId} — Update a route</li>
 *   <li>{@code DELETE /api/v1/routes/:routeId} — Delete a route</li>
 *   <li>{@code PUT /api/v1/routes/:routeId/suspend} — Suspend a route</li>
 *   <li>{@code PUT /api/v1/routes/:routeId/resume} — Resume a suspended route</li>
 * </ul>
 *
 * <p>All write operations are submitted to Raft for distributed consensus.
 * Read operations query the local state machine directly.
 *
 * @author Mark Andrew Ray-Smith Cityline Ltd
 * @version 1.0
 * @since 2026-02-19
 */
public class RouteHandler {

    private static final Logger logger = LoggerFactory.getLogger(RouteHandler.class);
    private final RaftNode raftNode;
    private final QuorusStateStore stateStore;

    public RouteHandler(RaftNode raftNode, QuorusStateStore stateStore) {
        this.raftNode = raftNode;
        this.stateStore = stateStore;
    }

    /**
     * Handles {@code POST /api/v1/routes} — creates a new route.
     */
    public Handler<RoutingContext> handleCreate() {
        return ctx -> {
            try {
                JsonObject body = ctx.body().asJsonObject();
                if (body == null) {
                    throw QuorusApiException.badRequest(ErrorCode.BAD_REQUEST, "Request body is required");
                }

                RouteConfiguration route = body.mapTo(RouteConfiguration.class);
                validateRouteConfiguration(route);

                // Ensure route is always created with CONFIGURED status regardless of client input
                RouteConfiguration routeToStore = (route.getStatus() != RouteStatus.CONFIGURED)
                        ? route.withStatus(RouteStatus.CONFIGURED)
                        : route;

                QuorusStateStore stateMachine = this.stateStore;
                if (stateMachine.hasRoute(routeToStore.getRouteId())) {
                    throw QuorusApiException.conflict(ErrorCode.ROUTE_DUPLICATE, routeToStore.getRouteId());
                }

                RouteCommand command = RouteCommand.create(routeToStore);
                logger.info("Creating route: routeId={}, name={}",
                        routeToStore.getRouteId(), routeToStore.getName());
                raftNode.submitCommand(command)
                        .onSuccess(result -> {
                            if (result instanceof CommandResult.NotFound<?> nf) {
                                logger.warn("Route disappeared during creation (race condition): routeId={}", nf.id());
                                ctx.fail(QuorusApiException.notFound(ErrorCode.ROUTE_NOT_FOUND, nf.id()));
                            } else {
                                logger.info("Route created: routeId={}", routeToStore.getRouteId());
                                ctx.response().setStatusCode(201);
                                ctx.json(new JsonObject()
                                        .put("success", true)
                                        .put("routeId", routeToStore.getRouteId())
                                        .put("status", RouteStatus.CONFIGURED.name()));
                            }
                        })
                        .onFailure(ctx::fail);
            } catch (Exception e) {
                logger.error("Failed to create route: {}", e.getMessage());
                logger.debug("Stack trace for route creation failure", e);
                ctx.fail(e);
            }
        };
    }

    /**
     * Handles {@code GET /api/v1/routes} — lists all routes.
     */
    public Handler<RoutingContext> handleList() {
        return ctx -> {
            QuorusStateStore stateMachine = this.stateStore;
            Map<String, RouteConfiguration> routes = stateMachine.getRoutes();

            // Optional status filter
            String statusFilter = ctx.request().getParam("status");

            JsonArray routeArray = new JsonArray();
            for (RouteConfiguration route : routes.values()) {
                if (statusFilter != null && !route.getStatus().name().equalsIgnoreCase(statusFilter)) {
                    continue;
                }
                routeArray.add(routeToJson(route));
            }

            ctx.json(new JsonObject()
                    .put("routes", routeArray)
                    .put("total", routeArray.size()));
        };
    }

    /**
     * Handles {@code GET /api/v1/routes/:routeId} — gets a specific route.
     */
    public Handler<RoutingContext> handleGet() {
        return ctx -> {
            String routeId = ctx.pathParam("routeId");

            RouteConfiguration route = stateStore.findRoute(routeId)
                    .orElseThrow(() -> QuorusApiException.notFound(ErrorCode.ROUTE_NOT_FOUND, routeId));

            ctx.json(routeToJson(route));
        };
    }

    /**
     * Handles {@code PUT /api/v1/routes/:routeId} — updates an existing route.
     */
    public Handler<RoutingContext> handleUpdate() {
        return ctx -> {
            try {
                String routeId = ctx.pathParam("routeId");
                JsonObject body = ctx.body().asJsonObject();
                if (body == null) {
                    throw QuorusApiException.badRequest(ErrorCode.BAD_REQUEST, "Request body is required");
                }

                RouteConfiguration existing = stateStore.findRoute(routeId)
                        .orElseThrow(() -> QuorusApiException.notFound(ErrorCode.ROUTE_NOT_FOUND, routeId));

                // Cannot update routes that are actively transferring or deleted
                if (existing.getStatus() == RouteStatus.TRANSFERRING || existing.getStatus() == RouteStatus.DELETED) {
                    throw QuorusApiException.conflict(ErrorCode.ROUTE_STATE_CONFLICT,
                            routeId, existing.getStatus().name(), "update");
                }

                RouteConfiguration update = body.mapTo(RouteConfiguration.class);
                logger.info("Updating route: routeId={}", routeId);
                RouteCommand command = RouteCommand.update(routeId, update);
                raftNode.submitCommand(command)
                        .onSuccess(result -> {
                            if (result instanceof CommandResult.NotFound<?> nf) {
                                logger.warn("Route disappeared during update (race condition): routeId={}", nf.id());
                                ctx.fail(QuorusApiException.notFound(ErrorCode.ROUTE_NOT_FOUND, nf.id()));
                            } else if (result instanceof CommandResult.CasMismatch<?>) {
                                logger.warn("Route state conflict during update: routeId={}", routeId);
                                ctx.fail(QuorusApiException.conflict(ErrorCode.ROUTE_STATE_CONFLICT,
                                        routeId, "unknown", "update (concurrent modification)"));
                            } else {
                                logger.info("Route updated: routeId={}", routeId);
                                ctx.json(new JsonObject()
                                        .put("success", true)
                                        .put("routeId", routeId)
                                        .put("message", "Route updated successfully"));
                            }
                        })
                        .onFailure(ctx::fail);
            } catch (Exception e) {
                logger.error("Failed to update route '{}': {}", ctx.pathParam("routeId"), e.getMessage());
                logger.debug("Stack trace for route update failure", e);
                ctx.fail(e);
            }
        };
    }

    /**
     * Handles {@code DELETE /api/v1/routes/:routeId} — deletes a route.
     */
    public Handler<RoutingContext> handleDelete() {
        return ctx -> {
            String routeId = ctx.pathParam("routeId");

            RouteConfiguration existing = stateStore.findRoute(routeId)
                    .orElseThrow(() -> QuorusApiException.notFound(ErrorCode.ROUTE_NOT_FOUND, routeId));

            // Pre-commit transition validation: can we transition to DELETED?
            validateTransition(existing, RouteStatus.DELETED, routeId, "delete");

            RouteCommand command = RouteCommand.delete(routeId);
            logger.info("Deleting route: routeId={}", routeId);
            raftNode.submitCommand(command)
                    .onSuccess(result -> {
                        if (result instanceof CommandResult.NotFound<?> nf) {
                            logger.warn("Route disappeared during deletion (race condition): routeId={}", nf.id());
                            ctx.fail(QuorusApiException.notFound(ErrorCode.ROUTE_NOT_FOUND, nf.id()));
                        } else {
                            logger.info("Route deleted: routeId={}", routeId);
                            ctx.json(new JsonObject()
                                    .put("routeId", routeId)
                                    .put("message", "Route deleted successfully"));
                        }
                    })
                    .onFailure(ctx::fail);
        };
    }

    /**
     * Handles {@code PUT /api/v1/routes/:routeId/suspend} — suspends a route.
     */
    public Handler<RoutingContext> handleSuspend() {
        return ctx -> {
            String routeId = ctx.pathParam("routeId");

            RouteConfiguration existing = stateStore.findRoute(routeId)
                    .orElseThrow(() -> QuorusApiException.notFound(ErrorCode.ROUTE_NOT_FOUND, routeId));

            // Pre-commit transition validation: can we transition to SUSPENDED?
            validateTransition(existing, RouteStatus.SUSPENDED, routeId, "suspend");

            // Optional reason from request body
            String reason = null;
            JsonObject body = ctx.body().asJsonObject();
            if (body != null && body.containsKey("reason")) {
                reason = body.getString("reason");
            }

            RouteCommand command = RouteCommand.suspend(routeId, reason);
            logger.info("Suspending route: routeId={}, reason={}", routeId, reason);
            raftNode.submitCommand(command)
                    .onSuccess(result -> {
                        if (result instanceof CommandResult.NotFound<?> nf) {
                            logger.warn("Route disappeared during suspend (race condition): routeId={}", nf.id());
                            ctx.fail(QuorusApiException.notFound(ErrorCode.ROUTE_NOT_FOUND, nf.id()));
                        } else if (result instanceof CommandResult.CasMismatch<?>) {
                            logger.warn("Route state conflict during suspend: routeId={}", routeId);
                            ctx.fail(QuorusApiException.conflict(ErrorCode.ROUTE_STATE_CONFLICT,
                                    routeId, "unknown", "suspend (concurrent modification)"));
                        } else {
                            logger.info("Route suspended: routeId={}", routeId);
                            ctx.json(new JsonObject()
                                    .put("success", true)
                                    .put("routeId", routeId)
                                    .put("status", RouteStatus.SUSPENDED.name())
                                    .put("message", "Route suspended successfully"));
                        }
                    })
                    .onFailure(ctx::fail);
        };
    }

    /**
     * Handles {@code PUT /api/v1/routes/:routeId/resume} — resumes a suspended route.
     *
     * <p>Note: Resume specifically requires SUSPENDED state (semantic constraint),
     * not just any state that can transition to ACTIVE.
     */
    public Handler<RoutingContext> handleResume() {
        return ctx -> {
            String routeId = ctx.pathParam("routeId");

            RouteConfiguration existing = stateStore.findRoute(routeId)
                    .orElseThrow(() -> QuorusApiException.notFound(ErrorCode.ROUTE_NOT_FOUND, routeId));

            // Resume operation requires SUSPENDED state specifically (not just canTransitionTo ACTIVE)
            if (existing.getStatus() != RouteStatus.SUSPENDED) {
                String currentStatus = existing.getStatus() != null ? existing.getStatus().name() : "UNKNOWN";
                throw QuorusApiException.conflict(ErrorCode.ROUTE_STATE_CONFLICT,
                        routeId, currentStatus, "resume (not suspended)");
            }

            RouteCommand command = RouteCommand.resume(routeId);
            logger.info("Resuming route: routeId={}", routeId);
            raftNode.submitCommand(command)
                    .onSuccess(result -> {
                        if (result instanceof CommandResult.NotFound<?> nf) {
                            logger.warn("Route disappeared during resume (race condition): routeId={}", nf.id());
                            ctx.fail(QuorusApiException.notFound(ErrorCode.ROUTE_NOT_FOUND, nf.id()));
                        } else if (result instanceof CommandResult.CasMismatch<?>) {
                            logger.warn("Route state conflict during resume: routeId={}", routeId);
                            ctx.fail(QuorusApiException.conflict(ErrorCode.ROUTE_STATE_CONFLICT,
                                    routeId, "unknown", "resume (concurrent modification)"));
                        } else {
                            logger.info("Route resumed: routeId={}", routeId);
                            ctx.json(new JsonObject()
                                    .put("success", true)
                                    .put("routeId", routeId)
                                    .put("status", RouteStatus.ACTIVE.name())
                                    .put("message", "Route resumed successfully"));
                        }
                    })
                    .onFailure(ctx::fail);
        };
    }

    // ==================== Validation ====================

    /**
     * Validates that the route can transition from its current status to the target status.
     *
     * @throws QuorusApiException with ROUTE_STATE_CONFLICT if the transition is invalid
     */
    private void validateTransition(RouteConfiguration route, RouteStatus targetStatus,
                                     String routeId, String operation) {
        if (!route.getStatus().canTransitionTo(targetStatus)) {
            throw QuorusApiException.conflict(ErrorCode.ROUTE_STATE_CONFLICT,
                    routeId, route.getStatus().name(), operation);
        }
    }

    /**
     * Validates required fields in a RouteConfiguration.
     * Delegates to RouteConfiguration.validate() and converts exceptions to HTTP-specific errors.
     */
    private void validateRouteConfiguration(RouteConfiguration route) {
        try {
            route.validate();
        } catch (IllegalArgumentException e) {
            // Extract field name from message: "fieldName must not be null or blank"
            String fieldName = extractFieldName(e.getMessage());
            throw QuorusApiException.badRequest(ErrorCode.MISSING_REQUIRED_FIELD, fieldName);
        } catch (NullPointerException e) {
            // Extract field name from message: "fieldName must not be null"
            String fieldName = extractFieldName(e.getMessage());
            throw QuorusApiException.badRequest(ErrorCode.MISSING_REQUIRED_FIELD, fieldName);
        }
    }
    
    private String extractFieldName(String message) {
        if (message == null) {
            return "unknown";
        }
        // Message format: "fieldName must not be null or blank" or "fieldName must not be null"
        int spaceIndex = message.indexOf(' ');
        return spaceIndex > 0 ? message.substring(0, spaceIndex) : message;
    }

    // ==================== JSON Serialization ====================

    /**
     * Converts a RouteConfiguration to a JSON response object.
     */
    private JsonObject routeToJson(RouteConfiguration route) {
        JsonObject json = new JsonObject()
                .put("routeId", route.getRouteId())
                .put("name", route.getName())
                .put("status", route.getStatus().name())
                .put("sourceAgentId", route.getSourceAgentId())
                .put("sourceLocation", route.getSourceLocation())
                .put("destinationAgentId", route.getDestinationAgentId())
                .put("destinationLocation", route.getDestinationLocation());

        if (route.getDescription() != null) {
            json.put("description", route.getDescription());
        }
        if (route.getTrigger() != null) {
            json.put("trigger", JsonObject.mapFrom(route.getTrigger()));
        }
        if (route.getOptions() != null && !route.getOptions().isEmpty()) {
            json.put("options", new JsonObject(new java.util.HashMap<>(route.getOptions())));
        }
        if (route.getCreatedAt() != null) {
            json.put("createdAt", route.getCreatedAt().toString());
        }
        if (route.getUpdatedAt() != null) {
            json.put("updatedAt", route.getUpdatedAt().toString());
        }

        return json;
    }
}
