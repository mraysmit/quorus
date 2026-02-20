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

    public RouteHandler(RaftNode raftNode) {
        this.raftNode = raftNode;
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

                QuorusStateStore stateMachine = (QuorusStateStore) raftNode.getStateStore();
                if (stateMachine.hasRoute(route.getRouteId())) {
                    throw QuorusApiException.conflict(ErrorCode.ROUTE_DUPLICATE, route.getRouteId());
                }

                RouteCommand command = RouteCommand.create(route);
                raftNode.submitCommand(command)
                        .onSuccess(result -> {
                            ctx.response().setStatusCode(201);
                            ctx.json(new JsonObject()
                                    .put("success", true)
                                    .put("routeId", route.getRouteId())
                                    .put("status", RouteStatus.CONFIGURED.name()));
                        })
                        .onFailure(ctx::fail);
            } catch (QuorusApiException e) {
                throw e;
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
            QuorusStateStore stateMachine = (QuorusStateStore) raftNode.getStateStore();
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
            QuorusStateStore stateMachine = (QuorusStateStore) raftNode.getStateStore();

            RouteConfiguration route = stateMachine.getRoute(routeId);
            if (route == null) {
                throw QuorusApiException.notFound(ErrorCode.ROUTE_NOT_FOUND, routeId);
            }

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

                QuorusStateStore stateMachine = (QuorusStateStore) raftNode.getStateStore();
                RouteConfiguration existing = stateMachine.getRoute(routeId);
                if (existing == null) {
                    throw QuorusApiException.notFound(ErrorCode.ROUTE_NOT_FOUND, routeId);
                }

                // Cannot update routes that are actively transferring
                if (existing.getStatus() == RouteStatus.TRANSFERRING) {
                    throw QuorusApiException.conflict(ErrorCode.ROUTE_STATE_CONFLICT,
                            routeId, existing.getStatus().name(), "update");
                }

                RouteConfiguration update = body.mapTo(RouteConfiguration.class);
                RouteCommand command = RouteCommand.update(routeId, update);
                raftNode.submitCommand(command)
                        .onSuccess(result -> {
                            ctx.json(new JsonObject()
                                    .put("success", true)
                                    .put("routeId", routeId)
                                    .put("message", "Route updated successfully"));
                        })
                        .onFailure(ctx::fail);
            } catch (QuorusApiException e) {
                throw e;
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
            QuorusStateStore stateMachine = (QuorusStateStore) raftNode.getStateStore();

            if (!stateMachine.hasRoute(routeId)) {
                throw QuorusApiException.notFound(ErrorCode.ROUTE_NOT_FOUND, routeId);
            }

            RouteConfiguration existing = stateMachine.getRoute(routeId);
            if (existing.getStatus() == RouteStatus.TRANSFERRING) {
                throw QuorusApiException.conflict(ErrorCode.ROUTE_STATE_CONFLICT,
                        routeId, existing.getStatus().name(), "delete");
            }

            RouteCommand command = RouteCommand.delete(routeId);
            raftNode.submitCommand(command)
                    .onSuccess(result -> {
                        ctx.json(new JsonObject()
                                .put("routeId", routeId)
                                .put("message", "Route deleted successfully"));
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
            QuorusStateStore stateMachine = (QuorusStateStore) raftNode.getStateStore();

            RouteConfiguration existing = stateMachine.getRoute(routeId);
            if (existing == null) {
                throw QuorusApiException.notFound(ErrorCode.ROUTE_NOT_FOUND, routeId);
            }

            if (existing.getStatus() == RouteStatus.SUSPENDED) {
                throw QuorusApiException.conflict(ErrorCode.ROUTE_STATE_CONFLICT,
                        routeId, existing.getStatus().name(), "suspend (already suspended)");
            }
            if (existing.getStatus() == RouteStatus.DELETED) {
                throw QuorusApiException.conflict(ErrorCode.ROUTE_STATE_CONFLICT,
                        routeId, existing.getStatus().name(), "suspend");
            }

            // Optional reason from request body
            String reason = null;
            JsonObject body = ctx.body().asJsonObject();
            if (body != null && body.containsKey("reason")) {
                reason = body.getString("reason");
            }

            RouteCommand command = RouteCommand.suspend(routeId, reason);
            raftNode.submitCommand(command)
                    .onSuccess(result -> {
                        ctx.json(new JsonObject()
                                .put("success", true)
                                .put("routeId", routeId)
                                .put("status", RouteStatus.SUSPENDED.name())
                                .put("message", "Route suspended successfully"));
                    })
                    .onFailure(ctx::fail);
        };
    }

    /**
     * Handles {@code PUT /api/v1/routes/:routeId/resume} — resumes a suspended route.
     */
    public Handler<RoutingContext> handleResume() {
        return ctx -> {
            String routeId = ctx.pathParam("routeId");
            QuorusStateStore stateMachine = (QuorusStateStore) raftNode.getStateStore();

            RouteConfiguration existing = stateMachine.getRoute(routeId);
            if (existing == null) {
                throw QuorusApiException.notFound(ErrorCode.ROUTE_NOT_FOUND, routeId);
            }

            if (existing.getStatus() != RouteStatus.SUSPENDED) {
                throw QuorusApiException.conflict(ErrorCode.ROUTE_STATE_CONFLICT,
                        routeId, existing.getStatus().name(), "resume (not suspended)");
            }

            RouteCommand command = RouteCommand.resume(routeId);
            raftNode.submitCommand(command)
                    .onSuccess(result -> {
                        ctx.json(new JsonObject()
                                .put("success", true)
                                .put("routeId", routeId)
                                .put("status", RouteStatus.ACTIVE.name())
                                .put("message", "Route resumed successfully"));
                    })
                    .onFailure(ctx::fail);
        };
    }

    // ==================== Validation ====================

    /**
     * Validates required fields in a RouteConfiguration.
     */
    private void validateRouteConfiguration(RouteConfiguration route) {
        if (route.getRouteId() == null || route.getRouteId().isBlank()) {
            throw QuorusApiException.badRequest(ErrorCode.MISSING_REQUIRED_FIELD, "routeId");
        }
        if (route.getName() == null || route.getName().isBlank()) {
            throw QuorusApiException.badRequest(ErrorCode.MISSING_REQUIRED_FIELD, "name");
        }
        if (route.getSourceAgentId() == null || route.getSourceAgentId().isBlank()) {
            throw QuorusApiException.badRequest(ErrorCode.MISSING_REQUIRED_FIELD, "sourceAgentId");
        }
        if (route.getSourceLocation() == null || route.getSourceLocation().isBlank()) {
            throw QuorusApiException.badRequest(ErrorCode.MISSING_REQUIRED_FIELD, "sourceLocation");
        }
        if (route.getDestinationAgentId() == null || route.getDestinationAgentId().isBlank()) {
            throw QuorusApiException.badRequest(ErrorCode.MISSING_REQUIRED_FIELD, "destinationAgentId");
        }
        if (route.getDestinationLocation() == null || route.getDestinationLocation().isBlank()) {
            throw QuorusApiException.badRequest(ErrorCode.MISSING_REQUIRED_FIELD, "destinationLocation");
        }
        if (route.getTrigger() == null) {
            throw QuorusApiException.badRequest(ErrorCode.MISSING_REQUIRED_FIELD, "trigger");
        }
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
