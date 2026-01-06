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

package dev.mars.quorus.controller.raft;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.io.Serializable;
/**
 * Description for VoteResponse
 *
 * @author Mark Andrew Ray-Smith Cityline Ltd
 * @version 1.0
 * @since 2025-08-20
 */

public class VoteResponse implements Serializable {

    private static final long serialVersionUID = 1L;

    private final long term;
    private final boolean voteGranted;
    private final String nodeId;

    // Default constructor for Jackson
    public VoteResponse() {
        this.term = 0;
        this.voteGranted = false;
        this.nodeId = "";
    }

    @JsonCreator
    public VoteResponse(@JsonProperty("term") long term,
                       @JsonProperty("voteGranted") boolean voteGranted,
                       @JsonProperty("nodeId") String nodeId) {
        this.term = term;
        this.voteGranted = voteGranted;
        this.nodeId = nodeId;
    }

    public long getTerm() {
        return term;
    }

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
