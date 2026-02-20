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
 * Parameterized tests for JobAssignmentStatus transition validation.
 * Covers every (source, target) pair to ensure complete transition map coverage.
 */
class JobAssignmentStatusTransitionTest {

    // --- Valid transition map ---

    private static EnumSet<JobAssignmentStatus> validTargets(JobAssignmentStatus from) {
        return switch (from) {
            case ASSIGNED -> EnumSet.of(JobAssignmentStatus.ACCEPTED, JobAssignmentStatus.REJECTED,
                    JobAssignmentStatus.TIMEOUT, JobAssignmentStatus.CANCELLED);
            case ACCEPTED -> EnumSet.of(JobAssignmentStatus.IN_PROGRESS,
                    JobAssignmentStatus.CANCELLED, JobAssignmentStatus.FAILED);
            case IN_PROGRESS -> EnumSet.of(JobAssignmentStatus.COMPLETED,
                    JobAssignmentStatus.FAILED, JobAssignmentStatus.CANCELLED);
            case COMPLETED, FAILED, REJECTED, TIMEOUT, CANCELLED ->
                    EnumSet.noneOf(JobAssignmentStatus.class);
        };
    }

    // --- Parameterized test for every pair ---

    static Stream<Arguments> allJobAssignmentStatusPairs() {
        List<Arguments> pairs = new ArrayList<>();
        for (JobAssignmentStatus from : JobAssignmentStatus.values()) {
            Set<JobAssignmentStatus> valid = validTargets(from);
            for (JobAssignmentStatus to : JobAssignmentStatus.values()) {
                pairs.add(Arguments.of(from, to, valid.contains(to)));
            }
        }
        return pairs.stream();
    }

    @ParameterizedTest(name = "{0} → {1} should be {2}")
    @MethodSource("allJobAssignmentStatusPairs")
    void canTransitionTo_coversAllPairs(JobAssignmentStatus from, JobAssignmentStatus to, boolean expected) {
        assertEquals(expected, from.canTransitionTo(to),
                () -> String.format("%s → %s should be %s", from, to, expected ? "valid" : "invalid"));
    }

    // --- getValidTransitions consistency ---

    @ParameterizedTest(name = "getValidTransitions consistent for {0}")
    @MethodSource("allJobAssignmentStatuses")
    void getValidTransitions_matchesCanTransitionTo(JobAssignmentStatus from) {
        Set<JobAssignmentStatus> fromMethod = EnumSet.noneOf(JobAssignmentStatus.class);
        fromMethod.addAll(Arrays.asList(from.getValidTransitions()));

        Set<JobAssignmentStatus> fromCanTransition = EnumSet.noneOf(JobAssignmentStatus.class);
        for (JobAssignmentStatus to : JobAssignmentStatus.values()) {
            if (from.canTransitionTo(to)) {
                fromCanTransition.add(to);
            }
        }

        assertEquals(fromCanTransition, fromMethod,
                () -> String.format("getValidTransitions() and canTransitionTo() disagree for %s", from));
    }

    static Stream<JobAssignmentStatus> allJobAssignmentStatuses() {
        return Arrays.stream(JobAssignmentStatus.values());
    }

    // --- Self-transition is never valid ---

    @ParameterizedTest(name = "{0} → {0} self-transition should be invalid")
    @MethodSource("allJobAssignmentStatuses")
    void selfTransition_isNeverValid(JobAssignmentStatus status) {
        assertFalse(status.canTransitionTo(status),
                () -> String.format("Self-transition %s → %s should not be valid", status, status));
    }

    // --- Terminal states have no transitions ---

    @Test
    void terminalStates_haveNoTransitions() {
        for (JobAssignmentStatus status : JobAssignmentStatus.values()) {
            if (status.isTerminal()) {
                assertEquals(0, status.getValidTransitions().length,
                        () -> String.format("Terminal state %s should have no transitions", status));
                for (JobAssignmentStatus target : JobAssignmentStatus.values()) {
                    assertFalse(status.canTransitionTo(target),
                            () -> String.format("Terminal state %s should not transition to %s", status, target));
                }
            }
        }
    }

    // --- Non-terminal states have at least one transition ---

    @Test
    void nonTerminalStates_haveAtLeastOneTransition() {
        for (JobAssignmentStatus status : JobAssignmentStatus.values()) {
            if (!status.isTerminal()) {
                assertTrue(status.getValidTransitions().length > 0,
                        () -> String.format("Non-terminal state %s should have at least one transition", status));
            }
        }
    }
}
