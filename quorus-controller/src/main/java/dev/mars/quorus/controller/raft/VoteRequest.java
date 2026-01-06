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
 * Description for VoteRequest
 *
 * @author Mark Andrew Ray-Smith Cityline Ltd
 * @version 1.0
 * @since 2025-08-20
 */

public class VoteRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    private final long term;
    private final String candidateId;
    private final long lastLogIndex;
    private final long lastLogTerm;

    // Default constructor for Jackson
    public VoteRequest() {
        this.term = 0;
        this.candidateId = "";
        this.lastLogIndex = 0;
        this.lastLogTerm = 0;
    }

    @JsonCreator
    public VoteRequest(@JsonProperty("term") long term,
                      @JsonProperty("candidateId") String candidateId,
                      @JsonProperty("lastLogIndex") long lastLogIndex,
                      @JsonProperty("lastLogTerm") long lastLogTerm) {
        this.term = term;
        this.candidateId = candidateId;
        this.lastLogIndex = lastLogIndex;
        this.lastLogTerm = lastLogTerm;
    }

    public long getTerm() {
        return term;
    }

    public String getCandidateId() {
        return candidateId;
    }

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
