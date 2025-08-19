/*
 * Copyright 2024 Quorus Project
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

package dev.mars.quorus.controller.raft;

import java.io.Serializable;

/**
 * Append entries response message used in Raft log replication.
 * Sent by followers in response to append entries requests.
 */
public class AppendEntriesResponse implements Serializable {

    private static final long serialVersionUID = 1L;

    private final long term;
    private final boolean success;
    private final String nodeId;
    private final long matchIndex;

    /**
     * Create a new append entries response.
     * 
     * @param term Current term of the responding node
     * @param success Whether the append entries request was successful
     * @param nodeId ID of the responding node
     * @param matchIndex Index of the highest log entry known to be replicated
     */
    public AppendEntriesResponse(long term, boolean success, String nodeId, long matchIndex) {
        this.term = term;
        this.success = success;
        this.nodeId = nodeId;
        this.matchIndex = matchIndex;
    }

    /**
     * Get the current term of the responding node.
     */
    public long getTerm() {
        return term;
    }

    /**
     * Check if the append entries request was successful.
     */
    public boolean isSuccess() {
        return success;
    }

    /**
     * Get the ID of the responding node.
     */
    public String getNodeId() {
        return nodeId;
    }

    /**
     * Get the index of the highest log entry known to be replicated.
     */
    public long getMatchIndex() {
        return matchIndex;
    }

    @Override
    public String toString() {
        return "AppendEntriesResponse{" +
                "term=" + term +
                ", success=" + success +
                ", nodeId='" + nodeId + '\'' +
                ", matchIndex=" + matchIndex +
                '}';
    }
}
