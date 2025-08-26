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

public class AppendEntriesResponse implements Serializable {

    private static final long serialVersionUID = 1L;

    private final long term;
    private final boolean success;
    private final String nodeId;
    private final long matchIndex;

    // Default constructor for Jackson
    public AppendEntriesResponse() {
        this.term = 0;
        this.success = false;
        this.nodeId = "";
        this.matchIndex = 0;
    }

    @JsonCreator
    public AppendEntriesResponse(@JsonProperty("term") long term,
                                @JsonProperty("success") boolean success,
                                @JsonProperty("nodeId") String nodeId,
                                @JsonProperty("matchIndex") long matchIndex) {
        this.term = term;
        this.success = success;
        this.nodeId = nodeId;
        this.matchIndex = matchIndex;
    }

    public long getTerm() {
        return term;
    }

    public boolean isSuccess() {
        return success;
    }

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
