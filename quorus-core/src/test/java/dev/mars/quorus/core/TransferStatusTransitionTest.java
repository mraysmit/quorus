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
 * Parameterized tests for TransferStatus transition validation.
 * Covers every (source, target) pair to ensure complete transition map coverage.
 */
class TransferStatusTransitionTest {

    // --- Valid transition map ---

    private static final EnumSet<TransferStatus> FROM_PENDING =
            EnumSet.of(TransferStatus.IN_PROGRESS, TransferStatus.CANCELLED);

    private static final EnumSet<TransferStatus> FROM_IN_PROGRESS =
            EnumSet.of(TransferStatus.COMPLETED, TransferStatus.FAILED,
                       TransferStatus.CANCELLED, TransferStatus.PAUSED);

    private static final EnumSet<TransferStatus> FROM_PAUSED =
            EnumSet.of(TransferStatus.IN_PROGRESS, TransferStatus.CANCELLED);

    private static final EnumSet<TransferStatus> FROM_FAILED =
            EnumSet.of(TransferStatus.PENDING);

    private static final EnumSet<TransferStatus> FROM_COMPLETED = EnumSet.noneOf(TransferStatus.class);

    private static final EnumSet<TransferStatus> FROM_CANCELLED = EnumSet.noneOf(TransferStatus.class);

    private static EnumSet<TransferStatus> validTargets(TransferStatus from) {
        return switch (from) {
            case PENDING -> FROM_PENDING;
            case IN_PROGRESS -> FROM_IN_PROGRESS;
            case PAUSED -> FROM_PAUSED;
            case FAILED -> FROM_FAILED;
            case COMPLETED -> FROM_COMPLETED;
            case CANCELLED -> FROM_CANCELLED;
        };
    }

    // --- Parameterized test for every pair ---

    static Stream<Arguments> allTransferStatusPairs() {
        List<Arguments> pairs = new ArrayList<>();
        for (TransferStatus from : TransferStatus.values()) {
            Set<TransferStatus> valid = validTargets(from);
            for (TransferStatus to : TransferStatus.values()) {
                pairs.add(Arguments.of(from, to, valid.contains(to)));
            }
        }
        return pairs.stream();
    }

    @ParameterizedTest(name = "{0} → {1} should be {2}")
    @MethodSource("allTransferStatusPairs")
    void canTransitionTo_coversAllPairs(TransferStatus from, TransferStatus to, boolean expected) {
        assertEquals(expected, from.canTransitionTo(to),
                () -> String.format("%s → %s should be %s", from, to, expected ? "valid" : "invalid"));
    }

    // --- getValidTransitions consistency ---

    @ParameterizedTest(name = "getValidTransitions consistent for {0}")
    @MethodSource("allTransferStatuses")
    void getValidTransitions_matchesCanTransitionTo(TransferStatus from) {
        Set<TransferStatus> fromMethod = EnumSet.noneOf(TransferStatus.class);
        fromMethod.addAll(Arrays.asList(from.getValidTransitions()));

        Set<TransferStatus> fromCanTransition = EnumSet.noneOf(TransferStatus.class);
        for (TransferStatus to : TransferStatus.values()) {
            if (from.canTransitionTo(to)) {
                fromCanTransition.add(to);
            }
        }

        assertEquals(fromCanTransition, fromMethod,
                () -> String.format("getValidTransitions() and canTransitionTo() disagree for %s", from));
    }

    static Stream<TransferStatus> allTransferStatuses() {
        return Arrays.stream(TransferStatus.values());
    }

    // --- Self-transition is never valid ---

    @ParameterizedTest(name = "{0} → {0} self-transition should be invalid")
    @MethodSource("allTransferStatuses")
    void selfTransition_isNeverValid(TransferStatus status) {
        assertFalse(status.canTransitionTo(status),
                () -> String.format("Self-transition %s → %s should not be valid", status, status));
    }

    // --- Terminal states ---

    @Test
    void completedAndCancelled_haveNoTransitions() {
        for (TransferStatus status : new TransferStatus[]{TransferStatus.COMPLETED, TransferStatus.CANCELLED}) {
            assertTrue(status.isTerminal());
            assertEquals(0, status.getValidTransitions().length,
                    () -> String.format("Final state %s should have no transitions", status));
            for (TransferStatus target : TransferStatus.values()) {
                assertFalse(status.canTransitionTo(target),
                        () -> String.format("Final state %s should not transition to %s", status, target));
            }
        }
    }

    @Test
    void failed_isTerminalButCanRetry() {
        assertTrue(TransferStatus.FAILED.isTerminal(),
                "FAILED is terminal (no automatic progress)");
        assertArrayEquals(new TransferStatus[]{TransferStatus.PENDING},
                TransferStatus.FAILED.getValidTransitions(),
                "FAILED can only transition to PENDING (retry)");
    }

    // --- Non-terminal states have at least one transition ---

    @Test
    void nonTerminalStates_haveAtLeastOneTransition() {
        for (TransferStatus status : TransferStatus.values()) {
            if (!status.isTerminal()) {
                assertTrue(status.getValidTransitions().length > 0,
                        () -> String.format("Non-terminal state %s should have at least one transition", status));
            }
        }
    }
}
