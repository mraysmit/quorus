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
import java.util.List;
import java.util.ArrayList;
/**
 * Description for AppendEntriesRequest
 *
 * @author Mark Andrew Ray-Smith Cityline Ltd
 * @version 1.0
 * @since 2025-08-20
 */

public class AppendEntriesRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    private final long term;
    private final String leaderId;
    private final long prevLogIndex;
    private final long prevLogTerm;
    private final List<LogEntry> entries;
    private final long leaderCommit;

    // Default constructor for Jackson
    public AppendEntriesRequest() {
        this.term = 0;
        this.leaderId = "";
        this.prevLogIndex = 0;
        this.prevLogTerm = 0;
        this.entries = new ArrayList<>();
        this.leaderCommit = 0;
    }

    @JsonCreator
    public AppendEntriesRequest(@JsonProperty("term") long term,
                               @JsonProperty("leaderId") String leaderId,
                               @JsonProperty("prevLogIndex") long prevLogIndex,
                               @JsonProperty("prevLogTerm") long prevLogTerm,
                               @JsonProperty("entries") List<LogEntry> entries,
                               @JsonProperty("leaderCommit") long leaderCommit) {
        this.term = term;
        this.leaderId = leaderId;
        this.prevLogIndex = prevLogIndex;
        this.prevLogTerm = prevLogTerm;
        this.entries = entries;
        this.leaderCommit = leaderCommit;
    }

    public long getTerm() {
        return term;
    }

    public String getLeaderId() {
        return leaderId;
    }

    public long getPrevLogIndex() {
        return prevLogIndex;
    }

    public long getPrevLogTerm() {
        return prevLogTerm;
    }

    public List<LogEntry> getEntries() {
        return entries;
    }

    public long getLeaderCommit() {
        return leaderCommit;
    }

    /**
     * Check if this is a heartbeat (no entries).
     */
    public boolean isHeartbeat() {
        return entries == null || entries.isEmpty();
    }

    @Override
    public String toString() {
        return "AppendEntriesRequest{" +
                "term=" + term +
                ", leaderId='" + leaderId + '\'' +
                ", prevLogIndex=" + prevLogIndex +
                ", prevLogTerm=" + prevLogTerm +
                ", entries=" + (entries != null ? entries.size() : 0) + " entries" +
                ", leaderCommit=" + leaderCommit +
                '}';
    }
}
