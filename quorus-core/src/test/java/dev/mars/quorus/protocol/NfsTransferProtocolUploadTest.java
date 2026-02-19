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

import dev.mars.quorus.core.TransferJob;
import dev.mars.quorus.core.TransferRequest;
import dev.mars.quorus.core.TransferResult;
import dev.mars.quorus.core.TransferStatus;
import dev.mars.quorus.core.exceptions.TransferException;
import dev.mars.quorus.transfer.TransferContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Upload-specific tests for NfsTransferProtocol.
 *
 * @author Mark Andrew Ray-Smith Cityline Ltd
 * @since 2026-02-14
 */
class NfsTransferProtocolUploadTest {

    private NfsTransferProtocol protocol;
    private TransferContext context;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        protocol = new NfsTransferProtocol(tempDir.toString());
        TransferRequest dummyRequest = TransferRequest.builder()
                .requestId("test-job-nfs-upload")
                .sourceUri(URI.create("http://example.com/test.txt"))
                .destinationPath(tempDir.resolve("test.txt"))
                .build();
        context = new TransferContext(new TransferJob(dummyRequest));
    }

    @Test
    void canHandleNfsUpload() {
        TransferRequest request = TransferRequest.builder()
                .sourceUri(tempDir.resolve("source.txt").toUri())
                .destinationUri(URI.create("nfs://server/export/dest.txt"))
                .build();
        assertTrue(protocol.canHandle(request));
    }

    @Test
    void cannotHandleNonNfsUpload() {
        TransferRequest request = TransferRequest.builder()
                .sourceUri(tempDir.resolve("source.txt").toUri())
                .destinationUri(URI.create("smb://server/share/dest.txt"))
                .build();
        assertFalse(protocol.canHandle(request));
    }

    @Test
    void uploadToSimulatedServer() throws Exception {
        Path sourceFile = tempDir.resolve("upload-source.txt");
        Files.writeString(sourceFile, "Upload content via simulated NFS");

        TransferRequest request = TransferRequest.builder()
                .requestId("nfs-sim-upload")
                .sourceUri(sourceFile.toUri())
                .destinationUri(URI.create("nfs://simulated-nfs-server/data/upload-target.txt"))
                .build();

        TransferResult result = protocol.transfer(request, context);

        assertEquals(TransferStatus.COMPLETED, result.getFinalStatus());
        assertEquals("Upload content via simulated NFS".length(), result.getBytesTransferred());
        assertNotNull(result.getRequestId());
        assertEquals("nfs-sim-upload", result.getRequestId());
    }

    @Test
    void uploadToSimulatedTestServer() throws Exception {
        Path sourceFile = tempDir.resolve("test-upload.bin");
        byte[] data = new byte[1024];
        java.util.Arrays.fill(data, (byte) 0x42);
        Files.write(sourceFile, data);

        TransferRequest request = TransferRequest.builder()
                .requestId("nfs-test-upload")
                .sourceUri(sourceFile.toUri())
                .destinationUri(URI.create("nfs://testserver/export/test-upload.bin"))
                .build();

        TransferResult result = protocol.transfer(request, context);

        assertEquals(TransferStatus.COMPLETED, result.getFinalStatus());
        assertEquals(1024, result.getBytesTransferred());
    }

    @Test
    void uploadToLocalhostTestServer() throws Exception {
        Path sourceFile = tempDir.resolve("localhost-upload.txt");
        Files.writeString(sourceFile, "localhost test content");

        TransferRequest request = TransferRequest.builder()
                .requestId("nfs-localhost-upload")
                .sourceUri(sourceFile.toUri())
                .destinationUri(URI.create("nfs://localhost.test/share/file.txt"))
                .build();

        TransferResult result = protocol.transfer(request, context);

        assertEquals(TransferStatus.COMPLETED, result.getFinalStatus());
        assertTrue(result.getBytesTransferred() > 0);
    }

    @Test
    void uploadToMountedPath() throws Exception {
        Path sourceFile = tempDir.resolve("mounted-upload-src.dat");
        Files.writeString(sourceFile, "content for mounted upload");

        // Create the "mounted" NFS directory
        Path mountDir = tempDir.resolve("nfshost").resolve("share");
        Files.createDirectories(mountDir);

        TransferRequest request = TransferRequest.builder()
                .requestId("nfs-mount-upload")
                .sourceUri(sourceFile.toUri())
                .destinationUri(URI.create("nfs://nfshost/share/mounted-upload.dat"))
                .build();

        TransferResult result = protocol.transfer(request, context);

        assertEquals(TransferStatus.COMPLETED, result.getFinalStatus());
        Path uploadedFile = mountDir.resolve("mounted-upload.dat");
        assertTrue(Files.exists(uploadedFile));
        assertEquals("content for mounted upload", Files.readString(uploadedFile));
    }

    @Test
    void uploadNonExistentSourceThrows() {
        Path nonExistent = tempDir.resolve("missing-source.txt");

        TransferRequest request = TransferRequest.builder()
                .requestId("nfs-upload-no-src")
                .sourceUri(nonExistent.toUri())
                .destinationUri(URI.create("nfs://server/export/dest.txt"))
                .build();

        TransferException ex = assertThrows(TransferException.class,
                () -> protocol.transfer(request, context));
        assertTrue(ex.getMessage().contains("NFS upload failed") || ex.getMessage().contains("does not exist"));
    }

    @Test
    void uploadWithChecksumCalculation() throws Exception {
        Path sourceFile = tempDir.resolve("checksum-upload.bin");
        Files.write(sourceFile, new byte[]{10, 20, 30, 40, 50});

        Path mountDir = tempDir.resolve("server").resolve("export");
        Files.createDirectories(mountDir);

        TransferRequest request = TransferRequest.builder()
                .requestId("nfs-upload-checksum")
                .sourceUri(sourceFile.toUri())
                .destinationUri(URI.create("nfs://server/export/checksum-upload.bin"))
                .expectedChecksum("expected-sha256")
                .build();

        TransferResult result = protocol.transfer(request, context);

        assertEquals(TransferStatus.COMPLETED, result.getFinalStatus());
        assertTrue(result.getActualChecksum().isPresent());
        assertEquals(64, result.getActualChecksum().get().length()); // SHA-256 hex string
    }
}
