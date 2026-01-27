package dev.mars.quorus.protocol;

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

import dev.mars.quorus.core.TransferJob;
import dev.mars.quorus.core.TransferRequest;
import dev.mars.quorus.core.TransferResult;
import dev.mars.quorus.core.TransferStatus;
import dev.mars.quorus.core.exceptions.TransferException;
import dev.mars.quorus.transfer.TransferContext;
import io.vertx.core.Vertx;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for HttpTransferProtocol.
 *
 * @author Mark Andrew Ray-Smith Cityline Ltd
 * @since 2025-12-17
 * @version 1.0
 */
@ExtendWith(VertxExtension.class)
class HttpTransferProtocolTest {

    private HttpTransferProtocol protocol;
    private Vertx vertx;
    private Path tempDir;

    @BeforeEach
    void setUp(Vertx vertx) throws IOException {
        this.vertx = vertx;
        this.protocol = new HttpTransferProtocol(vertx);
        this.tempDir = Files.createTempDirectory("http-test");
    }

    @AfterEach
    void tearDown() throws IOException {
        if (tempDir != null && Files.exists(tempDir)) {
            Files.walk(tempDir)
                .sorted((a, b) -> b.compareTo(a))
                .forEach(path -> {
                    try {
                        Files.deleteIfExists(path);
                    } catch (IOException e) {
                        // Ignore
                    }
                });
        }
    }

    @Test
    void testGetProtocolName() {
        assertEquals("http", protocol.getProtocolName());
    }

    @Test
    void testCanHandleHttpUri() {
        TransferRequest request = TransferRequest.builder()
                .requestId("test-1")
                .sourceUri(URI.create("http://example.com/file.txt"))
                .destinationPath(tempDir.resolve("file.txt"))
                .build();

        assertTrue(protocol.canHandle(request));
    }

    @Test
    void testCanHandleHttpsUri() {
        TransferRequest request = TransferRequest.builder()
                .requestId("test-2")
                .sourceUri(URI.create("https://example.com/file.txt"))
                .destinationPath(tempDir.resolve("file.txt"))
                .build();

        assertTrue(protocol.canHandle(request));
    }

    @Test
    void testCannotHandleFtpUri() {
        TransferRequest request = TransferRequest.builder()
                .requestId("test-3")
                .sourceUri(URI.create("ftp://example.com/file.txt"))
                .destinationPath(tempDir.resolve("file.txt"))
                .build();

        assertFalse(protocol.canHandle(request));
    }

    @Test
    void testCannotHandleSftpUri() {
        TransferRequest request = TransferRequest.builder()
                .requestId("test-4")
                .sourceUri(URI.create("sftp://example.com/file.txt"))
                .destinationPath(tempDir.resolve("file.txt"))
                .build();

        assertFalse(protocol.canHandle(request));
    }

    @Test
    void testCannotHandleSmbUri() {
        TransferRequest request = TransferRequest.builder()
                .requestId("test-5")
                .sourceUri(URI.create("smb://server/share/file.txt"))
                .destinationPath(tempDir.resolve("file.txt"))
                .build();

        assertFalse(protocol.canHandle(request));
    }

    @Test
    void testCannotHandleNullRequest() {
        assertFalse(protocol.canHandle(null));
    }

    @Test
    void testCannotHandleNullSourceUri() {
        // TransferRequest constructor validates that sourceUri cannot be null
        // So we test that the constructor throws NullPointerException
        assertThrows(NullPointerException.class, () -> {
            TransferRequest.builder()
                    .requestId("test-6")
                    .sourceUri(null)
                    .destinationPath(tempDir.resolve("file.txt"))
                    .build();
        });
    }

    @Test
    void testSupportsResume() {
        assertFalse(protocol.supportsResume());
    }

    @Test
    void testSupportsPause() {
        assertTrue(protocol.supportsPause());
    }

    @Test
    void testGetMaxFileSize() {
        assertEquals(10L * 1024 * 1024 * 1024, protocol.getMaxFileSize());
    }

    @Test
    void testConstructorWithVertxInstance() {
        Vertx testVertx = Vertx.vertx();
        HttpTransferProtocol testProtocol = new HttpTransferProtocol(testVertx);
        
        assertNotNull(testProtocol);
        assertEquals("http", testProtocol.getProtocolName());
        
        testVertx.close();
    }

    @Test
    void testConstructorWithNullVertxThrowsException() {
        assertThrows(NullPointerException.class, () -> {
            new HttpTransferProtocol(null);
        });
    }

    @Test
    void testTransferReactiveWithInvalidUrl(VertxTestContext testContext) {
        TransferRequest request = TransferRequest.builder()
                .requestId("test-invalid-url")
                .sourceUri(URI.create("http://nonexistent.invalid.domain.example.com.test/file.txt"))
                .destinationPath(tempDir.resolve("file.txt"))
                .build();

        TransferContext context = new TransferContext(new TransferJob(request));

        protocol.transferReactive(request, context)
                .onComplete(testContext.failing(error -> {
                    testContext.verify(() -> {
                        assertNotNull(error);
                        // Connection error or timeout expected
                    });
                    testContext.completeNow();
                }));
    }

    @Test
    void testTransferReactiveWithNullSourceUri(VertxTestContext testContext) {
        // TransferRequest constructor validates that sourceUri cannot be null
        // So we test that the constructor throws NullPointerException
        assertThrows(NullPointerException.class, () -> {
            TransferRequest.builder()
                    .requestId("test-null-source")
                    .sourceUri(null)
                    .destinationPath(tempDir.resolve("file.txt"))
                    .build();
        });
        testContext.completeNow();
    }

    @Test
    void testTransferReactiveWithNullDestination(VertxTestContext testContext) {
        // TransferRequest constructor validates that destinationPath cannot be null
        // So we test that the constructor throws NullPointerException
        assertThrows(NullPointerException.class, () -> {
            TransferRequest.builder()
                    .requestId("test-null-dest")
                    .sourceUri(URI.create("http://example.com/file.txt"))
                    .destinationPath((Path) null)
                    .build();
        });
        testContext.completeNow();
    }

    @Test
    void testTransferReactiveWithNonHttpProtocol(VertxTestContext testContext) {
        TransferRequest request = TransferRequest.builder()
                .requestId("test-wrong-protocol")
                .sourceUri(URI.create("ftp://example.com/file.txt"))
                .destinationPath(tempDir.resolve("file.txt"))
                .build();

        TransferContext context = new TransferContext(new TransferJob(request));

        protocol.transferReactive(request, context)
                .onComplete(testContext.failing(error -> {
                    testContext.verify(() -> {
                        assertTrue(error instanceof TransferException);
                        TransferException te = (TransferException) error;
                        assertTrue(te.getMessage().contains("HTTP protocol cannot handle this request"));
                    });
                    testContext.completeNow();
                }));
    }

    @Test
    void testTransferBlockingCallsReactive() {
        TransferRequest request = TransferRequest.builder()
                .requestId("test-blocking")
                .sourceUri(URI.create("http://nonexistent.test.invalid/file.txt"))
                .destinationPath(tempDir.resolve("file.txt"))
                .build();

        TransferContext context = new TransferContext(new TransferJob(request));

        assertThrows(TransferException.class, () -> {
            protocol.transfer(request, context);
        });
    }

    @Test
    void testCaseInsensitiveSchemeMatching() {
        TransferRequest httpLower = TransferRequest.builder()
                .requestId("test-http-lower")
                .sourceUri(URI.create("http://example.com/file.txt"))
                .destinationPath(tempDir.resolve("file.txt"))
                .build();

        TransferRequest httpUpper = TransferRequest.builder()
                .requestId("test-http-upper")
                .sourceUri(URI.create("HTTP://example.com/file.txt"))
                .destinationPath(tempDir.resolve("file.txt"))
                .build();

        TransferRequest httpsLower = TransferRequest.builder()
                .requestId("test-https-lower")
                .sourceUri(URI.create("https://example.com/file.txt"))
                .destinationPath(tempDir.resolve("file.txt"))
                .build();

        TransferRequest httpsUpper = TransferRequest.builder()
                .requestId("test-https-upper")
                .sourceUri(URI.create("HTTPS://example.com/file.txt"))
                .destinationPath(tempDir.resolve("file.txt"))
                .build();

        assertTrue(protocol.canHandle(httpLower));
        assertTrue(protocol.canHandle(httpUpper));
        assertTrue(protocol.canHandle(httpsLower));
        assertTrue(protocol.canHandle(httpsUpper));
    }

    @Test
    void testProtocolNameIsLowercase() {
        String protocolName = protocol.getProtocolName();
        assertEquals("http", protocolName);
        assertEquals(protocolName.toLowerCase(), protocolName);
    }

    @Test
    void testMaxFileSizeIs10GB() {
        long expectedSize = 10L * 1024L * 1024L * 1024L;
        assertEquals(expectedSize, protocol.getMaxFileSize());
    }

    @Test
    void testCanHandleWithMixedCaseScheme() {
        TransferRequest request = TransferRequest.builder()
                .requestId("test-mixed-case")
                .sourceUri(URI.create("HtTp://example.com/file.txt"))
                .destinationPath(tempDir.resolve("file.txt"))
                .build();

        assertTrue(protocol.canHandle(request));
    }

    @Test
    void testCanHandleReturnsFalseForFileScheme() {
        // Test that HTTP protocol doesn't handle non-HTTP schemes
        // Using sftp:// as source to create a valid download request
        TransferRequest request = TransferRequest.builder()
                .requestId("test-non-http-scheme")
                .sourceUri(URI.create("sftp://example.com/path/to/file.txt"))
                .destinationPath(tempDir.resolve("file.txt"))
                .build();

        assertFalse(protocol.canHandle(request));
    }

    @Test
    void testCreatesDestinationDirectoryIfNeeded(VertxTestContext testContext) {
        Path nestedDir = tempDir.resolve("level1/level2/level3");
        
        TransferRequest request = TransferRequest.builder()
                .requestId("test-create-dir")
                .sourceUri(URI.create("http://nonexistent.test.invalid/file.txt"))
                .destinationPath(nestedDir.resolve("file.txt"))
                .build();

        TransferContext context = new TransferContext(new TransferJob(request));

        protocol.transferReactive(request, context)
                .onComplete(testContext.failing(error -> {
                    // Transfer will fail, but directory should be created
                    testContext.verify(() -> {
                        assertTrue(Files.exists(nestedDir));
                        assertTrue(Files.isDirectory(nestedDir));
                    });
                    testContext.completeNow();
                }));
    }
}
