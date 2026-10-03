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

import java.util.Map;
import java.util.Set;

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
 * <p>Two API writes never touch replicated state and are served by every node (register item
 * SEC-09): the runtime revocation update, which changes this node's own trust state (decision
 * DR-Q2: the operator sends it to every controller), and the authorization check, which only
 * evaluates policy.</p>
 *
 * <p>Dependency Inversion: depends on {@link RaftNode} abstraction for leader checks,
 * not on specific Raft implementation details.</p>
 *
 * @author Mark Andrew Ray-Smith Cityline Ltd
 * @since 2026-02-19
 */
public class LeaderGuardHandler implements Handler<RoutingContext> {

    private static final Logger logger = LoggerFactory.getLogger(LeaderGuardHandler.class);

    /** API writes that change only node-local state or nothing at all, as "METHOD path". */
    private static final Set<String> NODE_LOCAL_WRITES = Set.of(
            "PUT /api/v1/security/trust/revocations",
            "POST /api/v1/security/authorization/check");

    /** Response header naming the leader's HTTP API base URL. */
    public static final String LEADER_HEADER = "X-Quorus-Leader";
    private static final String RETRY_AFTER = "Retry-After";
    private static final String RETRY_AFTER_SECONDS = "1";

    private final RaftNode raftNode;
    private final Map<String, String> apiEndpoints;

    /**
     * @param apiEndpoints the HTTP API base URL of each controller by node ID; a node missing from it
     *                     is never named as leader
     */
    public LeaderGuardHandler(RaftNode raftNode, Map<String, String> apiEndpoints) {
        this.raftNode = raftNode;
        this.apiEndpoints = Map.copyOf(apiEndpoints);
    }

    @Override
    public void handle(RoutingContext ctx) {
        String path = ctx.request().path();

        // Only guard API write paths — let health, metrics, raft status, and reads through
        if (!isWriteMethod(ctx) || !isApiPath(path) || isNodeLocalWrite(ctx, path)) {
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

        // REST Spec §3.8: say when to retry and, when known, where the leader is. No redirect.
        ctx.response().putHeader(RETRY_AFTER, RETRY_AFTER_SECONDS);
        if (leaderId != null && !leaderId.isEmpty()) {
            String leaderEndpoint = apiEndpoints.get(leaderId);
            if (leaderEndpoint != null) {
                ctx.response().putHeader(LEADER_HEADER, leaderEndpoint);
            }
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

    private static boolean isNodeLocalWrite(RoutingContext ctx, String path) {
        return NODE_LOCAL_WRITES.contains(ctx.request().method().name() + " " + path);
    }
}
