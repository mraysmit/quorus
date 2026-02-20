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

package dev.mars.quorus.core;

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
 * Parameterized tests for RouteStatus transition validation.
 * Covers every (source, target) pair to ensure complete transition map coverage.
 */
class RouteStatusTransitionTest {

    // --- Valid transition map ---

    private static EnumSet<RouteStatus> validTargets(RouteStatus from) {
        return switch (from) {
            case CONFIGURED -> EnumSet.of(RouteStatus.ACTIVE, RouteStatus.DELETED);
            case ACTIVE -> EnumSet.of(RouteStatus.TRIGGERED, RouteStatus.SUSPENDED,
                    RouteStatus.DEGRADED, RouteStatus.DELETED);
            case TRIGGERED -> EnumSet.of(RouteStatus.TRANSFERRING, RouteStatus.ACTIVE);
            case TRANSFERRING -> EnumSet.of(RouteStatus.ACTIVE, RouteStatus.FAILED);
            case SUSPENDED -> EnumSet.of(RouteStatus.ACTIVE, RouteStatus.DELETED);
            case DEGRADED -> EnumSet.of(RouteStatus.ACTIVE, RouteStatus.DELETED);
            case FAILED -> EnumSet.of(RouteStatus.CONFIGURED, RouteStatus.DELETED);
            case DELETED -> EnumSet.noneOf(RouteStatus.class);
        };
    }

    // --- Parameterized test for every pair ---

    static Stream<Arguments> allRouteStatusPairs() {
        List<Arguments> pairs = new ArrayList<>();
        for (RouteStatus from : RouteStatus.values()) {
            Set<RouteStatus> valid = validTargets(from);
            for (RouteStatus to : RouteStatus.values()) {
                pairs.add(Arguments.of(from, to, valid.contains(to)));
            }
        }
        return pairs.stream();
    }

    @ParameterizedTest(name = "{0} → {1} should be {2}")
    @MethodSource("allRouteStatusPairs")
    void canTransitionTo_coversAllPairs(RouteStatus from, RouteStatus to, boolean expected) {
        assertEquals(expected, from.canTransitionTo(to),
                () -> String.format("%s → %s should be %s", from, to, expected ? "valid" : "invalid"));
    }

    // --- getValidTransitions consistency ---

    @ParameterizedTest(name = "getValidTransitions consistent for {0}")
    @MethodSource("allRouteStatuses")
    void getValidTransitions_matchesCanTransitionTo(RouteStatus from) {
        Set<RouteStatus> fromMethod = EnumSet.noneOf(RouteStatus.class);
        fromMethod.addAll(Arrays.asList(from.getValidTransitions()));

        Set<RouteStatus> fromCanTransition = EnumSet.noneOf(RouteStatus.class);
        for (RouteStatus to : RouteStatus.values()) {
            if (from.canTransitionTo(to)) {
                fromCanTransition.add(to);
            }
        }

        assertEquals(fromCanTransition, fromMethod,
                () -> String.format("getValidTransitions() and canTransitionTo() disagree for %s", from));
    }

    static Stream<RouteStatus> allRouteStatuses() {
        return Arrays.stream(RouteStatus.values());
    }

    // --- Self-transition is never valid ---

    @ParameterizedTest(name = "{0} → {0} self-transition should be invalid")
    @MethodSource("allRouteStatuses")
    void selfTransition_isNeverValid(RouteStatus status) {
        assertFalse(status.canTransitionTo(status),
                () -> String.format("Self-transition %s → %s should not be valid", status, status));
    }

    // --- Terminal state: DELETED has no transitions ---

    @Test
    void deleted_hasNoTransitions() {
        assertTrue(RouteStatus.DELETED.isTerminal());
        assertEquals(0, RouteStatus.DELETED.getValidTransitions().length);
        for (RouteStatus target : RouteStatus.values()) {
            assertFalse(RouteStatus.DELETED.canTransitionTo(target));
        }
    }

    // --- isTerminal checks ---

    @Test
    void terminalStates() {
        assertTrue(RouteStatus.DELETED.isTerminal());
        // All other states are non-terminal
        for (RouteStatus status : RouteStatus.values()) {
            if (status != RouteStatus.DELETED) {
                assertFalse(status.isTerminal(),
                        () -> String.format("%s should not be terminal", status));
            }
        }
    }

    // --- isActive checks ---

    @Test
    void activeStates() {
        assertTrue(RouteStatus.ACTIVE.isActive());
        assertTrue(RouteStatus.TRIGGERED.isActive());
        assertTrue(RouteStatus.TRANSFERRING.isActive());

        assertFalse(RouteStatus.CONFIGURED.isActive());
        assertFalse(RouteStatus.SUSPENDED.isActive());
        assertFalse(RouteStatus.DEGRADED.isActive());
        assertFalse(RouteStatus.FAILED.isActive());
        assertFalse(RouteStatus.DELETED.isActive());
    }

    // --- FAILED can go back to CONFIGURED (reconfigure) ---

    @Test
    void failed_canReconfigure() {
        assertTrue(RouteStatus.FAILED.canTransitionTo(RouteStatus.CONFIGURED));
    }

    // --- Non-terminal states have at least one transition ---

    @Test
    void nonTerminalStates_haveAtLeastOneTransition() {
        for (RouteStatus status : RouteStatus.values()) {
            if (!status.isTerminal()) {
                assertTrue(status.getValidTransitions().length > 0,
                        () -> String.format("Non-terminal state %s should have at least one transition", status));
            }
        }
    }
}
