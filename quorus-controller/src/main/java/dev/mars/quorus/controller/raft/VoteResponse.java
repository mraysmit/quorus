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
 * Vote response message used in Raft leader election.
 * Sent by nodes in response to vote requests.
 */
public class VoteResponse implements Serializable {

    private static final long serialVersionUID = 1L;

    private final long term;
    private final boolean voteGranted;
    private final String nodeId;

    /**
     * Create a new vote response.
     * 
     * @param term Current term of the responding node
     * @param voteGranted Whether the vote was granted
     * @param nodeId ID of the responding node
     */
    public VoteResponse(long term, boolean voteGranted, String nodeId) {
        this.term = term;
        this.voteGranted = voteGranted;
        this.nodeId = nodeId;
    }

    /**
     * Get the current term of the responding node.
     */
    public long getTerm() {
        return term;
    }

    /**
     * Check if the vote was granted.
     */
    public boolean isVoteGranted() {
        return voteGranted;
    }

    /**
     * Get the ID of the responding node.
     */
    public String getNodeId() {
        return nodeId;
    }

    @Override
    public String toString() {
        return "VoteResponse{" +
                "term=" + term +
                ", voteGranted=" + voteGranted +
                ", nodeId='" + nodeId + '\'' +
                '}';
    }
}
