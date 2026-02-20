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

package dev.mars.quorus.core.exceptions;

import dev.mars.quorus.core.TransferStatus;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for InvalidTransitionException.
 */
class InvalidTransitionExceptionTest {

    @Test
    void constructor_withEntityId_formatsMessage() {
        TransferStatus[] validTransitions = {TransferStatus.COMPLETED, TransferStatus.FAILED};

        InvalidTransitionException ex = new InvalidTransitionException(
                "job-123", TransferStatus.IN_PROGRESS, TransferStatus.PENDING, validTransitions);

        assertTrue(ex.getMessage().contains("job-123"));
        assertTrue(ex.getMessage().contains("IN_PROGRESS"));
        assertTrue(ex.getMessage().contains("PENDING"));
        assertTrue(ex.getMessage().contains("COMPLETED"));
        assertTrue(ex.getMessage().contains("FAILED"));
        assertEquals("job-123", ex.getEntityId());
        assertEquals(TransferStatus.IN_PROGRESS, ex.getCurrentState());
        assertEquals(TransferStatus.PENDING, ex.getRequestedState());
        assertEquals(2, ex.getValidTransitions().length);
    }

    @Test
    void constructor_withoutEntityId_usesUnknown() {
        TransferStatus[] validTransitions = {TransferStatus.IN_PROGRESS};

        InvalidTransitionException ex = new InvalidTransitionException(
                TransferStatus.PENDING, TransferStatus.COMPLETED, validTransitions);

        assertEquals("unknown", ex.getEntityId());
        assertTrue(ex.getMessage().contains("PENDING"));
        assertTrue(ex.getMessage().contains("COMPLETED"));
    }

    @Test
    void constructor_withEmptyTransitions_formatsEmptyArray() {
        InvalidTransitionException ex = new InvalidTransitionException(
                "job-456", TransferStatus.COMPLETED, TransferStatus.IN_PROGRESS, new TransferStatus[0]);

        assertTrue(ex.getMessage().contains("[]"));
        assertEquals(0, ex.getValidTransitions().length);
    }

    @Test
    void extendsQuorusException() {
        InvalidTransitionException ex = new InvalidTransitionException(
                TransferStatus.COMPLETED, TransferStatus.PENDING, new TransferStatus[0]);

        assertInstanceOf(QuorusException.class, ex);
        assertInstanceOf(Exception.class, ex);
    }
}
