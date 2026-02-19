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

package dev.mars.quorus.controller.http;

import dev.mars.quorus.controller.raft.RaftNode;
import io.vertx.core.Handler;
import io.vertx.ext.web.RoutingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * HTTP middleware that guards write endpoints against non-leader nodes.
 *
 * <p>In a Raft cluster, only the leader may process write operations. This handler
 * intercepts mutating HTTP methods ({@code POST}, {@code PUT}, {@code DELETE},
 * {@code PATCH}) on API paths and rejects them with an appropriate error if the
 * current node is not the leader.</p>
 *
 * <p>Read-only methods ({@code GET}, {@code HEAD}, {@code OPTIONS}) are always
 * passed through, as reads can be served by any node from its local state machine.</p>
 *
 * <p>Non-API paths (health probes, metrics, Raft status) are also passed through
 * regardless of HTTP method.</p>
 *
 * <p>Dependency Inversion: depends on {@link RaftNode} abstraction for leader checks,
 * not on specific Raft implementation details.</p>
 *
 * @author Mark Andrew Ray-Smith Cityline Ltd
 * @since 2026-02-19
 */
public class LeaderGuardHandler implements Handler<RoutingContext> {

    private static final Logger logger = LoggerFactory.getLogger(LeaderGuardHandler.class);

    private final RaftNode raftNode;

    public LeaderGuardHandler(RaftNode raftNode) {
        this.raftNode = raftNode;
    }

    @Override
    public void handle(RoutingContext ctx) {
        String path = ctx.request().path();

        // Only guard API write paths — let health, metrics, raft status, and reads through
        if (!isWriteMethod(ctx) || !isApiPath(path)) {
            ctx.next();
            return;
        }

        if (raftNode.isLeader()) {
            ctx.next();
            return;
        }

        // Not the leader — reject with the appropriate error
        String leaderId = raftNode.getLeaderId();
        logger.debug("Rejecting write request on non-leader node: {} {} (leader={})",
                ctx.request().method(), path, leaderId);

        if (leaderId != null && !leaderId.isEmpty()) {
            ctx.fail(QuorusApiException.notLeader(leaderId));
        } else {
            ctx.fail(QuorusApiException.noLeader());
        }
    }

    /**
     * Checks if the HTTP method is a write (mutating) method.
     */
    private static boolean isWriteMethod(RoutingContext ctx) {
        return switch (ctx.request().method().name()) {
            case "POST", "PUT", "DELETE", "PATCH" -> true;
            default -> false;
        };
    }

    /**
     * Checks if the path is an API path that requires leader enforcement.
     * Non-API paths (health, metrics, raft status) are exempt.
     */
    private static boolean isApiPath(String path) {
        return path.startsWith("/api/");
    }
}
