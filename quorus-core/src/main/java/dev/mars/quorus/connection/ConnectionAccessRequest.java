/*
 * Copyright 2026 Mark Andrew Ray-Smith Cityline Ltd
 * Licensed under the Apache License, Version 2.0.
 */
package dev.mars.quorus.connection;

import java.util.List;

/** Runtime facts evaluated independently by the controller and executing agent. */
public record ConnectionAccessRequest(
        String tenantId,
        String remotePath,
        ServiceConnection.Direction direction,
        String agentPool,
        String networkZone,
        List<String> controllerResolvedAddresses) {
    public ConnectionAccessRequest(String tenantId, String remotePath, ServiceConnection.Direction direction,
                                   String agentPool, List<String> controllerResolvedAddresses) {
        this(tenantId, remotePath, direction, agentPool, null, controllerResolvedAddresses);
    }

    public ConnectionAccessRequest {
        controllerResolvedAddresses = List.copyOf(
                controllerResolvedAddresses == null ? List.of() : controllerResolvedAddresses);
    }
}
