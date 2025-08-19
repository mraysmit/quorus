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
import java.util.List;

/**
 * Append entries request message used in Raft log replication.
 * Sent by leaders to replicate log entries to followers.
 */
public class AppendEntriesRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    private final long term;
    private final String leaderId;
    private final long prevLogIndex;
    private final long prevLogTerm;
    private final List<LogEntry> entries;
    private final long leaderCommit;

    /**
     * Create a new append entries request.
     * 
     * @param term Leader's current term
     * @param leaderId Leader's ID
     * @param prevLogIndex Index of log entry immediately preceding new ones
     * @param prevLogTerm Term of prevLogIndex entry
     * @param entries Log entries to store (empty for heartbeat)
     * @param leaderCommit Leader's commit index
     */
    public AppendEntriesRequest(long term, String leaderId, long prevLogIndex, long prevLogTerm,
                               List<LogEntry> entries, long leaderCommit) {
        this.term = term;
        this.leaderId = leaderId;
        this.prevLogIndex = prevLogIndex;
        this.prevLogTerm = prevLogTerm;
        this.entries = entries;
        this.leaderCommit = leaderCommit;
    }

    /**
     * Get the leader's current term.
     */
    public long getTerm() {
        return term;
    }

    /**
     * Get the leader's ID.
     */
    public String getLeaderId() {
        return leaderId;
    }

    /**
     * Get the index of log entry immediately preceding new ones.
     */
    public long getPrevLogIndex() {
        return prevLogIndex;
    }

    /**
     * Get the term of prevLogIndex entry.
     */
    public long getPrevLogTerm() {
        return prevLogTerm;
    }

    /**
     * Get the log entries to store.
     */
    public List<LogEntry> getEntries() {
        return entries;
    }

    /**
     * Get the leader's commit index.
     */
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
