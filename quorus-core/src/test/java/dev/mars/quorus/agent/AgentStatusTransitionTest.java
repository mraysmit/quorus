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

package dev.mars.quorus.agent;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Parameterized tests for AgentStatus transition validation.
 * Covers every (source, target) pair to ensure complete transition map coverage.
 */
class AgentStatusTransitionTest {

    // --- Valid transition map ---

    private static EnumSet<AgentStatus> validTargets(AgentStatus from) {
        return switch (from) {
            case REGISTERING -> EnumSet.of(AgentStatus.HEALTHY, AgentStatus.FAILED);
            case HEALTHY -> EnumSet.of(AgentStatus.ACTIVE, AgentStatus.IDLE, AgentStatus.DEGRADED,
                    AgentStatus.OVERLOADED, AgentStatus.MAINTENANCE, AgentStatus.DRAINING,
                    AgentStatus.UNREACHABLE, AgentStatus.FAILED, AgentStatus.DEREGISTERED);
            case ACTIVE -> EnumSet.of(AgentStatus.HEALTHY, AgentStatus.IDLE, AgentStatus.DEGRADED,
                    AgentStatus.OVERLOADED, AgentStatus.DRAINING, AgentStatus.UNREACHABLE,
                    AgentStatus.FAILED, AgentStatus.DEREGISTERED);
            case IDLE -> EnumSet.of(AgentStatus.HEALTHY, AgentStatus.ACTIVE, AgentStatus.DEGRADED,
                    AgentStatus.MAINTENANCE, AgentStatus.DRAINING, AgentStatus.UNREACHABLE,
                    AgentStatus.FAILED, AgentStatus.DEREGISTERED);
            case DEGRADED -> EnumSet.of(AgentStatus.HEALTHY, AgentStatus.ACTIVE, AgentStatus.IDLE,
                    AgentStatus.OVERLOADED, AgentStatus.MAINTENANCE, AgentStatus.DRAINING,
                    AgentStatus.UNREACHABLE, AgentStatus.FAILED, AgentStatus.DEREGISTERED);
            case OVERLOADED -> EnumSet.of(AgentStatus.HEALTHY, AgentStatus.ACTIVE, AgentStatus.IDLE,
                    AgentStatus.DEGRADED, AgentStatus.DRAINING, AgentStatus.UNREACHABLE,
                    AgentStatus.FAILED, AgentStatus.DEREGISTERED);
            case MAINTENANCE -> EnumSet.of(AgentStatus.HEALTHY, AgentStatus.DRAINING,
                    AgentStatus.UNREACHABLE, AgentStatus.FAILED, AgentStatus.DEREGISTERED);
            case DRAINING -> EnumSet.of(AgentStatus.HEALTHY, AgentStatus.UNREACHABLE,
                    AgentStatus.FAILED, AgentStatus.DEREGISTERED);
            case UNREACHABLE -> EnumSet.of(AgentStatus.HEALTHY, AgentStatus.FAILED,
                    AgentStatus.DEREGISTERED);
            case FAILED -> EnumSet.of(AgentStatus.DEREGISTERED);
            case DEREGISTERED -> EnumSet.noneOf(AgentStatus.class);
        };
    }

    // --- Parameterized test for every pair ---

    static Stream<Arguments> allAgentStatusPairs() {
        List<Arguments> pairs = new ArrayList<>();
        for (AgentStatus from : AgentStatus.values()) {
            Set<AgentStatus> valid = validTargets(from);
            for (AgentStatus to : AgentStatus.values()) {
                pairs.add(Arguments.of(from, to, valid.contains(to)));
            }
        }
        return pairs.stream();
    }

    @ParameterizedTest(name = "{0} → {1} should be {2}")
    @MethodSource("allAgentStatusPairs")
    void canTransitionTo_coversAllPairs(AgentStatus from, AgentStatus to, boolean expected) {
        assertEquals(expected, from.canTransitionTo(to),
                () -> String.format("%s → %s should be %s", from, to, expected ? "valid" : "invalid"));
    }

    // --- getValidTransitions consistency ---

    @ParameterizedTest(name = "getValidTransitions consistent for {0}")
    @MethodSource("allAgentStatuses")
    void getValidTransitions_matchesCanTransitionTo(AgentStatus from) {
        Set<AgentStatus> fromMethod = EnumSet.noneOf(AgentStatus.class);
        fromMethod.addAll(Arrays.asList(from.getValidTransitions()));

        Set<AgentStatus> fromCanTransition = EnumSet.noneOf(AgentStatus.class);
        for (AgentStatus to : AgentStatus.values()) {
            if (from.canTransitionTo(to)) {
                fromCanTransition.add(to);
            }
        }

        assertEquals(fromCanTransition, fromMethod,
                () -> String.format("getValidTransitions() and canTransitionTo() disagree for %s", from));
    }

    static Stream<AgentStatus> allAgentStatuses() {
        return Arrays.stream(AgentStatus.values());
    }

    // --- Self-transition is never valid ---

    @ParameterizedTest(name = "{0} → {0} self-transition should be invalid")
    @MethodSource("allAgentStatuses")
    void selfTransition_isNeverValid(AgentStatus status) {
        assertFalse(status.canTransitionTo(status),
                () -> String.format("Self-transition %s → %s should not be valid", status, status));
    }

    // --- Terminal state: DEREGISTERED has no transitions ---

    @Test
    void deregistered_hasNoTransitions() {
        assertEquals(0, AgentStatus.DEREGISTERED.getValidTransitions().length);
        for (AgentStatus target : AgentStatus.values()) {
            assertFalse(AgentStatus.DEREGISTERED.canTransitionTo(target),
                    () -> String.format("DEREGISTERED should not transition to %s", target));
        }
    }

    // --- FAILED can only transition to DEREGISTERED ---

    @Test
    void failed_canOnlyTransitionToDeregistered() {
        AgentStatus[] validFromFailed = AgentStatus.FAILED.getValidTransitions();
        assertEquals(1, validFromFailed.length);
        assertEquals(AgentStatus.DEREGISTERED, validFromFailed[0]);
    }

    // --- REGISTERING can only go to HEALTHY or FAILED ---

    @Test
    void registering_hasLimitedTransitions() {
        AgentStatus[] valid = AgentStatus.REGISTERING.getValidTransitions();
        assertEquals(2, valid.length);
        Set<AgentStatus> validSet = EnumSet.noneOf(AgentStatus.class);
        validSet.addAll(Arrays.asList(valid));
        assertTrue(validSet.contains(AgentStatus.HEALTHY));
        assertTrue(validSet.contains(AgentStatus.FAILED));
    }

    // --- Non-terminal states have at least one transition ---

    @Test
    void nonTerminalStates_haveAtLeastOneTransition() {
        for (AgentStatus status : AgentStatus.values()) {
            if (!status.isTerminal()) {
                assertTrue(status.getValidTransitions().length > 0,
                        () -> String.format("Non-terminal state %s should have at least one transition", status));
            }
        }
    }
}
