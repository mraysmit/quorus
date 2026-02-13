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

import dev.mars.quorus.controller.raft.RaftNode;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;

/**
 * HTTP handler for cluster status.
 *
 * <p>Endpoint: {@code GET /raft/status}
 *
 * <p>Returns Raft cluster information including leader, term, and node state.
 *
 * @author Mark Andrew Ray-Smith Cityline Ltd
 * @version 2.0 (Vert.x reactive)
 * @since 2025-12-11
 */
public class ClusterHandler implements Handler<RoutingContext> {

    private final RaftNode raftNode;

    public ClusterHandler(RaftNode raftNode) {
        this.raftNode = raftNode;
    }

    @Override
    public void handle(RoutingContext ctx) {
        JsonObject status = new JsonObject()
                .put("nodeId", raftNode.getNodeId())
                .put("state", raftNode.getState().toString())
                .put("currentTerm", raftNode.getCurrentTerm())
                .put("isLeader", raftNode.isLeader())
                .put("isRunning", raftNode.isRunning());

        String leaderId = raftNode.getLeaderId();
        if (leaderId != null) {
            status.put("leaderId", leaderId);
        }

        ctx.json(status);
    }
}

