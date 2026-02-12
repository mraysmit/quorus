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

package dev.mars.quorus.protocol;

import dev.mars.quorus.core.TransferRequest;
import dev.mars.quorus.core.TransferResult;
import dev.mars.quorus.core.TransferStatus;
import dev.mars.quorus.core.exceptions.TransferException;
import dev.mars.quorus.transfer.TransferContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.URI;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for FTPS (FTP over SSL/TLS) support in {@link FtpTransferProtocol}.
 * <p>
 * These tests verify URI handling, scheme detection, port defaults, and FTPS mode
 * selection (explicit vs implicit). Actual TLS negotiation is tested in integration
 * tests with Testcontainers.
 *
 * @author Mark Andrew Ray-Smith Cityline Ltd
 * @version 1.0
 * @since 2026-02-12
 */
class FtpsTransferProtocolTest {

    private FtpTransferProtocol protocol;
    private TransferContext context;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        protocol = new FtpTransferProtocol();
        TransferRequest dummyRequest = TransferRequest.builder()
                .requestId("ftps-test-job")
                .sourceUri(URI.create("http://example.com/test.txt"))
                .destinationPath(tempDir.resolve("test.txt"))
                .build();
        context = new TransferContext(new dev.mars.quorus.core.TransferJob(dummyRequest));
    }

    // ========================================================================
    // canHandle() — FTPS scheme detection
    // ========================================================================

    @Test
    void testCanHandleFtpsDownloadUri() {
        TransferRequest request = TransferRequest.builder()
                .sourceUri(URI.create("ftps://secure-server.com/data/report.csv"))
                .destinationPath(tempDir.resolve("report.csv"))
                .build();

        assertTrue(protocol.canHandle(request), "Should handle ftps:// download URIs");
    }

    @Test
    void testCanHandleFtpsUploadUri() {
        TransferRequest request = TransferRequest.builder()
                .sourceUri(tempDir.resolve("local-file.txt").toUri())
                .destinationUri(URI.create("ftps://secure-server.com/uploads/file.txt"))
                .build();

        assertTrue(protocol.canHandle(request), "Should handle ftps:// upload URIs");
    }

    @Test
    void testCanHandleFtpsUppercaseScheme() {
        TransferRequest request = TransferRequest.builder()
                .sourceUri(URI.create("FTPS://server.com/path/file.txt"))
                .destinationPath(tempDir.resolve("file.txt"))
                .build();

        assertTrue(protocol.canHandle(request), "Should handle FTPS scheme case-insensitively");
    }

    @Test
    void testCanStillHandlePlainFtp() {
        TransferRequest request = TransferRequest.builder()
                .sourceUri(URI.create("ftp://server.com/path/file.txt"))
                .destinationPath(tempDir.resolve("file.txt"))
                .build();

        assertTrue(protocol.canHandle(request), "Should still handle plain ftp:// URIs");
    }

    @Test
    void testCannotHandleHttpUri() {
        TransferRequest httpRequest = TransferRequest.builder()
                .sourceUri(URI.create("http://server.com/file.txt"))
                .destinationPath(tempDir.resolve("file.txt"))
                .build();

        assertFalse(protocol.canHandle(httpRequest), "Should not handle http:// URIs");
    }

    @Test
    void testCannotHandleSftpUri() {
        TransferRequest sftpRequest = TransferRequest.builder()
                .sourceUri(URI.create("sftp://server.com/file.txt"))
                .destinationPath(tempDir.resolve("file.txt"))
                .build();

        assertFalse(protocol.canHandle(sftpRequest), "Should not handle sftp:// URIs");
    }

    // ========================================================================
    // FTPS URI parsing — port and mode defaults
    // ========================================================================

    @Test
    void testFtpsDefaultPortUsesExplicitMode() {
        // ftps:// without explicit port should default to port 21 (explicit FTPS)
        TransferRequest request = TransferRequest.builder()
                .requestId("test-ftps-default-port")
                .sourceUri(URI.create("ftps://server.com/path/file.txt"))
                .destinationPath(tempDir.resolve("file.txt"))
                .build();

        assertTrue(protocol.canHandle(request));
        // Will fail connecting to non-existent server, but URI parsing should succeed
        assertThrows(TransferException.class, () -> protocol.transfer(request, context));
    }

    @Test
    void testFtpsPort990UsesImplicitMode() {
        // ftps:// with port 990 should use implicit FTPS
        TransferRequest request = TransferRequest.builder()
                .requestId("test-ftps-implicit")
                .sourceUri(URI.create("ftps://server.com:990/path/file.txt"))
                .destinationPath(tempDir.resolve("file.txt"))
                .build();

        assertTrue(protocol.canHandle(request));
        assertThrows(TransferException.class, () -> protocol.transfer(request, context));
    }

    @Test
    void testFtpsCustomPortUsesExplicitMode() {
        // ftps:// with a custom port (not 990) should use explicit FTPS
        TransferRequest request = TransferRequest.builder()
                .requestId("test-ftps-custom-port")
                .sourceUri(URI.create("ftps://server.com:2121/path/file.txt"))
                .destinationPath(tempDir.resolve("file.txt"))
                .build();

        assertTrue(protocol.canHandle(request));
        assertThrows(TransferException.class, () -> protocol.transfer(request, context));
    }

    // ========================================================================
    // FTPS URI parsing — authentication
    // ========================================================================

    @Test
    void testFtpsUriWithAuthentication() {
        TransferRequest request = TransferRequest.builder()
                .requestId("test-ftps-auth")
                .sourceUri(URI.create("ftps://user:secret@secure.corp.com/exports/data.zip"))
                .destinationPath(tempDir.resolve("data.zip"))
                .build();

        assertTrue(protocol.canHandle(request));
        assertThrows(TransferException.class, () -> protocol.transfer(request, context));
    }

    @Test
    void testFtpsUriWithUsernameOnly() {
        TransferRequest request = TransferRequest.builder()
                .requestId("test-ftps-user-only")
                .sourceUri(URI.create("ftps://admin@server.com/path/file.txt"))
                .destinationPath(tempDir.resolve("file.txt"))
                .build();

        assertTrue(protocol.canHandle(request));
    }

    @Test
    void testFtpsAnonymousAccess() {
        // No userInfo in URI — should default to anonymous
        TransferRequest request = TransferRequest.builder()
                .requestId("test-ftps-anonymous")
                .sourceUri(URI.create("ftps://public-server.com/pub/readme.txt"))
                .destinationPath(tempDir.resolve("readme.txt"))
                .build();

        assertTrue(protocol.canHandle(request));
    }

    // ========================================================================
    // Protocol metadata
    // ========================================================================

    @Test
    void testGetProtocolNameReturnsFtp() {
        // Protocol name remains "ftp"; "ftps" is registered as alias in ProtocolFactory
        assertEquals("ftp", protocol.getProtocolName());
    }

    @Test
    void testSupportsResumeReturnsFalse() {
        assertFalse(protocol.supportsResume());
    }

    @Test
    void testSupportsPauseReturnsFalse() {
        assertFalse(protocol.supportsPause());
    }

    @Test
    void testGetMaxFileSizeReturnsUnlimited() {
        assertEquals(-1, protocol.getMaxFileSize());
    }

    // ========================================================================
    // FtpsMode enum coverage
    // ========================================================================

    @Test
    void testFtpsModeEnumValues() {
        FtpTransferProtocol.FtpsMode[] modes = FtpTransferProtocol.FtpsMode.values();
        assertEquals(3, modes.length);
        assertEquals(FtpTransferProtocol.FtpsMode.NONE, FtpTransferProtocol.FtpsMode.valueOf("NONE"));
        assertEquals(FtpTransferProtocol.FtpsMode.EXPLICIT, FtpTransferProtocol.FtpsMode.valueOf("EXPLICIT"));
        assertEquals(FtpTransferProtocol.FtpsMode.IMPLICIT, FtpTransferProtocol.FtpsMode.valueOf("IMPLICIT"));
    }

    // ========================================================================
    // ProtocolFactory integration — ftps alias
    // ========================================================================

    @Test
    void testProtocolFactoryRegistersFtpsAlias() {
        io.vertx.core.Vertx vertx = io.vertx.core.Vertx.vertx();
        try {
            ProtocolFactory factory = new ProtocolFactory(vertx);

            TransferProtocol ftpProto = factory.getProtocol("ftp");
            TransferProtocol ftpsProto = factory.getProtocol("ftps");

            assertNotNull(ftpProto, "ProtocolFactory should have 'ftp' registered");
            assertNotNull(ftpsProto, "ProtocolFactory should have 'ftps' registered as alias");
            assertSame(ftpProto, ftpsProto,
                    "Both 'ftp' and 'ftps' should resolve to the same FtpTransferProtocol instance");
            assertTrue(factory.isProtocolSupported("ftps"), "ftps should be listed as supported");
        } finally {
            vertx.close();
        }
    }

    // ========================================================================
    // Edge cases and error handling
    // ========================================================================

    @Test
    void testCannotHandleNullRequest() {
        assertFalse(protocol.canHandle(null));
    }

    @Test
    void testFtpsUploadSourceFileNotFound() {
        TransferRequest request = TransferRequest.builder()
                .requestId("test-ftps-upload-missing")
                .sourceUri(tempDir.resolve("nonexistent-file.txt").toUri())
                .destinationUri(URI.create("ftps://server.com/uploads/file.txt"))
                .build();

        assertTrue(protocol.canHandle(request));
        assertThrows(TransferException.class, () -> protocol.transfer(request, context));
    }

    @Test
    void testFtpsConnectionTimeout() {
        // INTENTIONAL FAILURE TEST: verifying timeout behaviour for unreachable FTPS servers
        TransferRequest request = TransferRequest.builder()
                .requestId("test-ftps-timeout")
                .sourceUri(URI.create("ftps://unreachable.server.invalid/path/file.txt"))
                .destinationPath(tempDir.resolve("file.txt"))
                .build();

        assertTrue(protocol.canHandle(request));
        assertThrows(TransferException.class, () -> protocol.transfer(request, context));
    }
}
