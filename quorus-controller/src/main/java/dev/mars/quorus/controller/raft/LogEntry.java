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
import java.time.Instant;
import java.util.Objects;

/**
 * Represents a log entry in the Raft consensus algorithm.
 * Each entry contains a term, index, and command to be applied to the state machine.
 */
public class LogEntry implements Serializable {

    private static final long serialVersionUID = 1L;

    private final long term;
    private final long index;
    private final Object command;
    private final Instant timestamp;

    /**
     * Create a new log entry.
     * 
     * @param term The term when this entry was created
     * @param index The index of this entry in the log
     * @param command The command to be applied to the state machine
     */
    public LogEntry(long term, long index, Object command) {
        this.term = term;
        this.index = index;
        this.command = command;
        this.timestamp = Instant.now();
    }

    /**
     * Get the term when this entry was created.
     */
    public long getTerm() {
        return term;
    }

    /**
     * Get the index of this entry in the log.
     */
    public long getIndex() {
        return index;
    }

    /**
     * Get the command to be applied to the state machine.
     */
    public Object getCommand() {
        return command;
    }

    /**
     * Get the timestamp when this entry was created.
     */
    public Instant getTimestamp() {
        return timestamp;
    }

    /**
     * Check if this is a no-op entry (used for heartbeats).
     */
    public boolean isNoOp() {
        return command == null;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        LogEntry logEntry = (LogEntry) o;
        return term == logEntry.term &&
               index == logEntry.index &&
               Objects.equals(command, logEntry.command);
        // Note: timestamp is intentionally excluded from equality
    }

    @Override
    public int hashCode() {
        return Objects.hash(term, index, command);
        // Note: timestamp is intentionally excluded from hashCode
    }

    @Override
    public String toString() {
        return "LogEntry{" +
                "term=" + term +
                ", index=" + index +
                ", command=" + command +
                ", timestamp=" + timestamp +
                '}';
    }
}
