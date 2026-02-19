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

import dev.mars.quorus.controller.state.StateMachineCommand;

/**
 * Description for RaftStateMachine
 *
 * @author Mark Andrew Ray-Smith Cityline Ltd
 * @version 1.0
 * @since 2025-08-20
 */

public interface RaftStateMachine {

    Object apply(StateMachineCommand command);

    byte[] takeSnapshot();

    void restoreSnapshot(byte[] snapshot);

    long getLastAppliedIndex();

    void setLastAppliedIndex(long index);

    /**
     * Reset the state machine to initial state.
     */
    void reset();
}
