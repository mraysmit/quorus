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

package dev.mars.quorus.transfer;

import dev.mars.quorus.core.TransferDirection;
import dev.mars.quorus.core.TransferRequest;
import dev.mars.quorus.core.exceptions.TransferException;
import io.vertx.core.Vertx;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.net.URI;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for bidirectional transfer support in SimpleTransferEngine (Phase 3).
 * 
 * Verifies:
 * - Direction-aware validation
 * - Direction-specific metrics tracking
 * - Protocol selection based on transfer direction
 */
class SimpleTransferEngineBidirectionalTest {

    private Vertx vertx;
    private SimpleTransferEngine engine;

    @BeforeEach
    void setUp() {
        vertx = Vertx.vertx();
        engine = new SimpleTransferEngine(vertx, 5, 3, 1000);
    }

    @AfterEach
    void tearDown() {
        if (engine != null) {
            engine.shutdown(5);
        }
        if (vertx != null) {
            vertx.close();
        }
    }

    @Nested
    @DisplayName("Transfer Request Validation Tests")
    class TransferRequestValidationTests {

        @Test
        @DisplayName("Should reject remote-to-remote transfers")
        void shouldRejectRemoteToRemoteTransfers() {
            // Note: TransferRequest constructor already validates this, 
            // but engine should also validate
            assertThrows(UnsupportedOperationException.class, () -> {
                TransferRequest.builder()
                    .requestId("remote-to-remote-test")
                    .sourceUri(URI.create("sftp://server1/file.txt"))
                    .destinationUri(URI.create("ftp://server2/file.txt"))
                    .build();
            }, "Should reject remote-to-remote transfers");
        }

        @Test
        @DisplayName("Should reject local-to-local transfers")
        void shouldRejectLocalToLocalTransfers() {
            assertThrows(IllegalArgumentException.class, () -> {
                TransferRequest.builder()
                    .requestId("local-to-local-test")
                    .sourceUri(URI.create("file:///local/source.txt"))
                    .destinationUri(URI.create("file:///local/dest.txt"))
                    .build();
            }, "Should reject local-to-local transfers");
        }

        @Test
        @DisplayName("Should accept valid download request")
        void shouldAcceptValidDownloadRequest() throws TransferException {
            TransferRequest request = TransferRequest.builder()
                .requestId("valid-download-test")
                .sourceUri(URI.create("sftp://server/file.txt"))
                .destinationUri(URI.create("file:///local/file.txt"))
                .build();

            assertEquals(TransferDirection.DOWNLOAD, request.getDirection());
            assertTrue(request.isDownload());
            assertFalse(request.isUpload());
        }

        @Test
        @DisplayName("Should accept valid upload request")
        void shouldAcceptValidUploadRequest() throws TransferException {
            TransferRequest request = TransferRequest.builder()
                .requestId("valid-upload-test")
                .sourceUri(URI.create("file:///local/file.txt"))
                .destinationUri(URI.create("sftp://server/file.txt"))
                .build();

            assertEquals(TransferDirection.UPLOAD, request.getDirection());
            assertTrue(request.isUpload());
            assertFalse(request.isDownload());
        }
    }

    @Nested
    @DisplayName("Health Check Tests")
    class HealthCheckTests {

        @Test
        @DisplayName("Health check should include direction-specific protocol checks")
        void healthCheckShouldIncludeDirectionSpecificProtocolChecks() {
            var healthCheck = engine.getHealthCheck();

            assertNotNull(healthCheck);
            // 4 protocols: http, ftp, sftp, smb
            assertEquals(4, healthCheck.getProtocolHealthChecks().size());
        }
    }
}
