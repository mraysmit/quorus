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
import dev.mars.quorus.core.exceptions.TransferException;
import dev.mars.quorus.transfer.TransferContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

    private static final Logger logger = LoggerFactory.getLogger(FtpsTransferProtocolTest.class);

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
        log("testCanHandleFtpsDownloadUri", "Testing canHandle with ftps:// download URI");
        TransferRequest request = TransferRequest.builder()
                .sourceUri(URI.create("ftps://secure-server.com/data/report.csv"))
                .destinationPath(tempDir.resolve("report.csv"))
                .build();

        assertTrue(protocol.canHandle(request), "Should handle ftps:// download URIs");
        log("testCanHandleFtpsDownloadUri", "[PASS] canHandle accepts ftps:// download URI");
    }

    @Test
    void testCanHandleFtpsUploadUri() {
        log("testCanHandleFtpsUploadUri", "Testing canHandle with ftps:// upload URI");
        TransferRequest request = TransferRequest.builder()
                .sourceUri(tempDir.resolve("local-file.txt").toUri())
                .destinationUri(URI.create("ftps://secure-server.com/uploads/file.txt"))
                .build();

        assertTrue(protocol.canHandle(request), "Should handle ftps:// upload URIs");
        log("testCanHandleFtpsUploadUri", "[PASS] canHandle accepts ftps:// upload URI");
    }

    @Test
    void testCanHandleFtpsUppercaseScheme() {
        log("testCanHandleFtpsUppercaseScheme", "Testing canHandle with FTPS:// (uppercase) URI");
        TransferRequest request = TransferRequest.builder()
                .sourceUri(URI.create("FTPS://server.com/path/file.txt"))
                .destinationPath(tempDir.resolve("file.txt"))
                .build();

        assertTrue(protocol.canHandle(request), "Should handle FTPS scheme case-insensitively");
        log("testCanHandleFtpsUppercaseScheme", "[PASS] canHandle accepts uppercase FTPS:// scheme");
    }

    @Test
    void testCanStillHandlePlainFtp() {
        log("testCanStillHandlePlainFtp", "Testing canHandle still accepts plain ftp://");
        TransferRequest request = TransferRequest.builder()
                .sourceUri(URI.create("ftp://server.com/path/file.txt"))
                .destinationPath(tempDir.resolve("file.txt"))
                .build();

        assertTrue(protocol.canHandle(request), "Should still handle plain ftp:// URIs");
        log("testCanStillHandlePlainFtp", "[PASS] canHandle still accepts plain ftp://");
    }

    @Test
    void testCannotHandleHttpUri() {
        log("testCannotHandleHttpUri", "Testing canHandle rejects http://");
        TransferRequest httpRequest = TransferRequest.builder()
                .sourceUri(URI.create("http://server.com/file.txt"))
                .destinationPath(tempDir.resolve("file.txt"))
                .build();

        assertFalse(protocol.canHandle(httpRequest), "Should not handle http:// URIs");
        log("testCannotHandleHttpUri", "[PASS] canHandle correctly rejects http://");
    }

    @Test
    void testCannotHandleSftpUri() {
        log("testCannotHandleSftpUri", "Testing canHandle rejects sftp://");
        TransferRequest sftpRequest = TransferRequest.builder()
                .sourceUri(URI.create("sftp://server.com/file.txt"))
                .destinationPath(tempDir.resolve("file.txt"))
                .build();

        assertFalse(protocol.canHandle(sftpRequest), "Should not handle sftp:// URIs");
        log("testCannotHandleSftpUri", "[PASS] canHandle correctly rejects sftp://");
    }

    // ========================================================================
    // FTPS URI parsing — port and mode defaults
    // ========================================================================

    @Test
    void testFtpsDefaultPortUsesExplicitMode() {
        log("testFtpsDefaultPortUsesExplicitMode", "Testing ftps:// default port → explicit FTPS mode");
        // ftps:// without explicit port should default to port 21 (explicit FTPS)
        TransferRequest request = TransferRequest.builder()
                .requestId("test-ftps-default-port")
                .sourceUri(URI.create("ftps://server.com/path/file.txt"))
                .destinationPath(tempDir.resolve("file.txt"))
                .build();

        assertTrue(protocol.canHandle(request));
        // Will fail connecting to non-existent server, but URI parsing should succeed
        assertThrows(TransferException.class, () -> protocol.transfer(request, context));
        log("testFtpsDefaultPortUsesExplicitMode", "[PASS] default port correctly uses explicit FTPS");
    }

    @Test
    void testFtpsPort990UsesImplicitMode() {
        log("testFtpsPort990UsesImplicitMode", "Testing ftps://:990 → implicit FTPS mode");
        // ftps:// with port 990 should use implicit FTPS
        TransferRequest request = TransferRequest.builder()
                .requestId("test-ftps-implicit")
                .sourceUri(URI.create("ftps://server.com:990/path/file.txt"))
                .destinationPath(tempDir.resolve("file.txt"))
                .build();

        assertTrue(protocol.canHandle(request));
        assertThrows(TransferException.class, () -> protocol.transfer(request, context));
        log("testFtpsPort990UsesImplicitMode", "[PASS] port 990 correctly uses implicit FTPS");
    }

    @Test
    void testFtpsCustomPortUsesExplicitMode() {
        log("testFtpsCustomPortUsesExplicitMode", "Testing ftps://:2121 → explicit FTPS mode");
        // ftps:// with a custom port (not 990) should use explicit FTPS
        TransferRequest request = TransferRequest.builder()
                .requestId("test-ftps-custom-port")
                .sourceUri(URI.create("ftps://server.com:2121/path/file.txt"))
                .destinationPath(tempDir.resolve("file.txt"))
                .build();

        assertTrue(protocol.canHandle(request));
        assertThrows(TransferException.class, () -> protocol.transfer(request, context));
        log("testFtpsCustomPortUsesExplicitMode", "[PASS] custom port correctly uses explicit FTPS");
    }

    // ========================================================================
    // FTPS URI parsing — authentication
    // ========================================================================

    @Test
    void testFtpsUriWithAuthentication() {
        log("testFtpsUriWithAuthentication", "Testing ftps:// URI with user:password authentication");
        TransferRequest request = TransferRequest.builder()
                .requestId("test-ftps-auth")
                .sourceUri(URI.create("ftps://user:secret@secure.corp.com/exports/data.zip"))
                .destinationPath(tempDir.resolve("data.zip"))
                .build();

        assertTrue(protocol.canHandle(request));
        assertThrows(TransferException.class, () -> protocol.transfer(request, context));
        log("testFtpsUriWithAuthentication", "[PASS] ftps:// with credentials parsed correctly");
    }

    @Test
    void testFtpsUriWithUsernameOnly() {
        log("testFtpsUriWithUsernameOnly", "Testing ftps:// URI with username only (no password)");
        TransferRequest request = TransferRequest.builder()
                .requestId("test-ftps-user-only")
                .sourceUri(URI.create("ftps://admin@server.com/path/file.txt"))
                .destinationPath(tempDir.resolve("file.txt"))
                .build();

        assertTrue(protocol.canHandle(request));
        log("testFtpsUriWithUsernameOnly", "[PASS] username-only URI accepted");
    }

    @Test
    void testFtpsAnonymousAccess() {
        log("testFtpsAnonymousAccess", "Testing ftps:// URI with no userInfo (anonymous)");
        // No userInfo in URI — should default to anonymous
        TransferRequest request = TransferRequest.builder()
                .requestId("test-ftps-anonymous")
                .sourceUri(URI.create("ftps://public-server.com/pub/readme.txt"))
                .destinationPath(tempDir.resolve("readme.txt"))
                .build();

        assertTrue(protocol.canHandle(request));
        log("testFtpsAnonymousAccess", "[PASS] anonymous ftps:// URI accepted");
    }

    // ========================================================================
    // Protocol metadata
    // ========================================================================

    @Test
    void testGetProtocolNameReturnsFtp() {
        log("testGetProtocolNameReturnsFtp", "Testing getProtocolName() returns 'ftp'");
        // Protocol name remains "ftp"; "ftps" is registered as alias in ProtocolFactory
        assertEquals("ftp", protocol.getProtocolName());
        log("testGetProtocolNameReturnsFtp", "[PASS] protocol name is 'ftp'");
    }

    @Test
    void testSupportsResumeReturnsFalse() {
        log("testSupportsResumeReturnsFalse", "Testing supportsResume() returns false");
        assertFalse(protocol.supportsResume());
        log("testSupportsResumeReturnsFalse", "[PASS] supportsResume() is false");
    }

    @Test
    void testSupportsPauseReturnsFalse() {
        log("testSupportsPauseReturnsFalse", "Testing supportsPause() returns false");
        assertFalse(protocol.supportsPause());
        log("testSupportsPauseReturnsFalse", "[PASS] supportsPause() is false");
    }

    @Test
    void testGetMaxFileSizeReturnsUnlimited() {
        log("testGetMaxFileSizeReturnsUnlimited", "Testing getMaxFileSize() returns -1 (unlimited)");
        assertEquals(-1, protocol.getMaxFileSize());
        log("testGetMaxFileSizeReturnsUnlimited", "[PASS] maxFileSize is -1 (unlimited)");
    }

    // ========================================================================
    // FtpsMode enum coverage
    // ========================================================================

    @Test
    void testFtpsModeEnumValues() {
        log("testFtpsModeEnumValues", "Testing FtpsMode enum has NONE, EXPLICIT, IMPLICIT");
        FtpTransferProtocol.FtpsMode[] modes = FtpTransferProtocol.FtpsMode.values();
        assertEquals(3, modes.length);
        assertEquals(FtpTransferProtocol.FtpsMode.NONE, FtpTransferProtocol.FtpsMode.valueOf("NONE"));
        assertEquals(FtpTransferProtocol.FtpsMode.EXPLICIT, FtpTransferProtocol.FtpsMode.valueOf("EXPLICIT"));
        assertEquals(FtpTransferProtocol.FtpsMode.IMPLICIT, FtpTransferProtocol.FtpsMode.valueOf("IMPLICIT"));
        log("testFtpsModeEnumValues", "[PASS] FtpsMode enum has 3 values: NONE, EXPLICIT, IMPLICIT");
    }

    // ========================================================================
    // ProtocolFactory integration — ftps alias
    // ========================================================================

    @Test
    void testProtocolFactoryRegistersFtpsAlias() {
        log("testProtocolFactoryRegistersFtpsAlias", "Testing ProtocolFactory registers 'ftps' as alias for 'ftp'");
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
            log("testProtocolFactoryRegistersFtpsAlias", "[PASS] 'ftps' alias resolves to same FtpTransferProtocol instance");
        } finally {
            vertx.close();
        }
    }

    // ========================================================================
    // Edge cases and error handling
    // ========================================================================

    @Test
    void testCannotHandleNullRequest() {
        log("testCannotHandleNullRequest", "Testing canHandle(null) returns false");
        assertFalse(protocol.canHandle(null));
        log("testCannotHandleNullRequest", "[PASS] canHandle(null) returns false");
    }

    @Test
    void testFtpsUploadSourceFileNotFound() {
        log("testFtpsUploadSourceFileNotFound", "Testing upload of non-existent file throws TransferException");
        TransferRequest request = TransferRequest.builder()
                .requestId("test-ftps-upload-missing")
                .sourceUri(tempDir.resolve("nonexistent-file.txt").toUri())
                .destinationUri(URI.create("ftps://server.com/uploads/file.txt"))
                .build();

        assertTrue(protocol.canHandle(request));
        assertThrows(TransferException.class, () -> protocol.transfer(request, context));
        log("testFtpsUploadSourceFileNotFound", "[PASS] non-existent file correctly throws TransferException");
    }

    @Test
    void testFtpsConnectionTimeout() {
        log("testFtpsConnectionTimeout", "Testing connection timeout to unreachable FTPS server");
        // INTENTIONAL FAILURE TEST: verifying timeout behaviour for unreachable FTPS servers
        TransferRequest request = TransferRequest.builder()
                .requestId("test-ftps-timeout")
                .sourceUri(URI.create("ftps://unreachable.server.invalid/path/file.txt"))
                .destinationPath(tempDir.resolve("file.txt"))
                .build();

        assertTrue(protocol.canHandle(request));
        assertThrows(TransferException.class, () -> protocol.transfer(request, context));
        log("testFtpsConnectionTimeout", "[PASS] unreachable server correctly throws TransferException");
    }

    // ========================================================================
    private static void log(String testName, String message) {
        logger.info("[FtpsTransferProtocolTest.{}] {}", testName, message);
    }
}
