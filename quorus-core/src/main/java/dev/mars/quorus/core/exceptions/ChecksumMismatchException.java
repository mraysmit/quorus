package dev.mars.quorus.core.exceptions;

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


/**
 * Exception thrown when file integrity verification fails due to checksum mismatch.
 * This indicates potential data corruption during transfer.
 *
 * @author Mark Andrew Ray-Smith Cityline Ltd
 * @since 1.0
 */
public class ChecksumMismatchException extends TransferException {
    
    private final String expectedChecksum;
    private final String actualChecksum;
    
    public ChecksumMismatchException(String transferId, String expectedChecksum, String actualChecksum) {
        super(transferId, String.format("Checksum mismatch - expected: %s, actual: %s", 
                expectedChecksum, actualChecksum));
        this.expectedChecksum = expectedChecksum;
        this.actualChecksum = actualChecksum;
    }
    
    public String getExpectedChecksum() {
        return expectedChecksum;
    }
    
    public String getActualChecksum() {
        return actualChecksum;
    }
}
