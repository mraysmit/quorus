package dev.mars.quorus.core;

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


import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
/**
 * Description for TransferStatusTest
 *
 * @author Mark Andrew Ray-Smith Cityline Ltd
 * @version 1.0
 * @since 2025-08-17
 */

class TransferStatusTest {
    
    @Test
    void testIsTerminal() {
        assertTrue(TransferStatus.COMPLETED.isTerminal());
        assertTrue(TransferStatus.FAILED.isTerminal());
        assertTrue(TransferStatus.CANCELLED.isTerminal());
        
        assertFalse(TransferStatus.PENDING.isTerminal());
        assertFalse(TransferStatus.IN_PROGRESS.isTerminal());
        assertFalse(TransferStatus.PAUSED.isTerminal());
    }
    
    @Test
    void testIsActive() {
        assertTrue(TransferStatus.IN_PROGRESS.isActive());
        assertTrue(TransferStatus.PAUSED.isActive());
        
        assertFalse(TransferStatus.PENDING.isActive());
        assertFalse(TransferStatus.COMPLETED.isActive());
        assertFalse(TransferStatus.FAILED.isActive());
        assertFalse(TransferStatus.CANCELLED.isActive());
    }
    
    @Test
    void testCanResume() {
        assertTrue(TransferStatus.PAUSED.canResume());
        assertTrue(TransferStatus.FAILED.canResume());
        
        assertFalse(TransferStatus.PENDING.canResume());
        assertFalse(TransferStatus.IN_PROGRESS.canResume());
        assertFalse(TransferStatus.COMPLETED.canResume());
        assertFalse(TransferStatus.CANCELLED.canResume());
    }
}
