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

package dev.mars.quorus.protocol.errorhandling;

import dev.mars.quorus.core.TransferJob;
import dev.mars.quorus.core.TransferRequest;
import dev.mars.quorus.transfer.TransferContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.io.TempDir;

import java.net.URI;
import java.nio.file.Path;

/**
 * Base class for protocol error handling tests.
 * 
 * <p>These tests verify that protocols correctly reject invalid inputs and 
 * handle error conditions gracefully. They are separated from the main protocol
 * tests to keep the test output clean - these tests intentionally trigger
 * error conditions that would otherwise clutter logs with ERROR messages
 * and stack traces.</p>
 * 
 * <p>All tests in this package use request IDs with the "test-" prefix followed
 * by error-specific patterns (e.g., "test-missing-host") which allows the 
 * protocol implementations to suppress verbose error logging for expected 
 * test failures.</p>
 *
 * @author Mark Andrew Ray-Smith Cityline Ltd
 * @version 1.0
 * @since 2025-08-18
 */
@Tag("negative")
public abstract class ProtocolErrorHandlingTestBase {

    @TempDir
    protected Path tempDir;
    
    protected TransferContext context;

    @BeforeEach
    void setUpBase() {
        TransferRequest dummyRequest = TransferRequest.builder()
                .requestId("test-job-setup")
                .sourceUri(URI.create("http://example.com/test.txt"))
                .destinationPath(tempDir.resolve("test.txt"))
                .build();
        context = new TransferContext(new TransferJob(dummyRequest));
    }

    /**
     * Helper method to extract full exception message including causes.
     */
    protected String getFullExceptionMessage(Throwable e) {
        StringBuilder sb = new StringBuilder();
        Throwable current = e;
        while (current != null) {
            if (sb.length() > 0) {
                sb.append(" -> ");
            }
            sb.append(current.getMessage());
            current = current.getCause();
        }
        return sb.toString();
    }
}
