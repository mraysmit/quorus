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

/**
 * State machine interface for Raft consensus.
 * Defines how commands are applied to the replicated state machine.
 */
public interface RaftStateMachine {

    /**
     * Apply a command to the state machine.
     * This method is called when a log entry is committed.
     * 
     * @param command The command to apply
     * @return The result of applying the command
     */
    Object apply(Object command);

    /**
     * Take a snapshot of the current state.
     * Used for log compaction.
     * 
     * @return Snapshot data
     */
    byte[] takeSnapshot();

    /**
     * Restore state from a snapshot.
     * Used during recovery or when installing snapshots.
     * 
     * @param snapshot Snapshot data
     */
    void restoreSnapshot(byte[] snapshot);

    /**
     * Get the index of the last applied command.
     *
     * @return Last applied index
     */
    long getLastAppliedIndex();

    /**
     * Set the index of the last applied command.
     *
     * @param index Last applied index
     */
    void setLastAppliedIndex(long index);

    /**
     * Reset the state machine to initial state.
     */
    void reset();
}
