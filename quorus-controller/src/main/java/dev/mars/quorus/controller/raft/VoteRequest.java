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
 * Vote request message used in Raft leader election.
 * Sent by candidates to request votes from other nodess.
 */
public class VoteRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    private final long term;
    private final String candidateId;
    private final long lastLogIndex;
    private final long lastLogTerm;

    /**
     * Create a new vote request.
     * 
     * @param term Candidate's current term
     * @param candidateId Candidate requesting the vote
     * @param lastLogIndex Index of candidate's last log entry
     * @param lastLogTerm Term of candidate's last log entry
     */
    public VoteRequest(long term, String candidateId, long lastLogIndex, long lastLogTerm) {
        this.term = term;
        this.candidateId = candidateId;
        this.lastLogIndex = lastLogIndex;
        this.lastLogTerm = lastLogTerm;
    }

    /**
     * Get the candidate's current term.
     */
    public long getTerm() {
        return term;
    }

    /**
     * Get the candidate's ID.
     */
    public String getCandidateId() {
        return candidateId;
    }

    /**
     * Get the index of the candidate's last log entry.
     */
    public long getLastLogIndex() {
        return lastLogIndex;
    }

    /**
     * Get the term of the candidate's last log entry.
     */
    public long getLastLogTerm() {
        return lastLogTerm;
    }

    @Override
    public String toString() {
        return "VoteRequest{" +
                "term=" + term +
                ", candidateId='" + candidateId + '\'' +
                ", lastLogIndex=" + lastLogIndex +
                ", lastLogTerm=" + lastLogTerm +
                '}';
    }
}
