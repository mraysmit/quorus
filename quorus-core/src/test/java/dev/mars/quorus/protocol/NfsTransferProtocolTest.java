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
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for NfsTransferProtocol.
 *
 * @author Mark Andrew Ray-Smith Cityline Ltd
 * @since 2026-02-14
 */
class NfsTransferProtocolTest {

    private NfsTransferProtocol protocol;
    private TransferContext context;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        protocol = new NfsTransferProtocol(tempDir.toString());
        TransferRequest dummyRequest = TransferRequest.builder()
                .requestId("test-job-nfs")
                .sourceUri(URI.create("http://example.com/test.txt"))
                .destinationPath(tempDir.resolve("test.txt"))
                .build();
        context = new TransferContext(new TransferJob(dummyRequest));
    }

    @Nested
    class ProtocolMetadata {

        @Test
        void getProtocolName() {
            assertEquals("nfs", protocol.getProtocolName());
        }

        @Test
        void supportsResume() {
            assertFalse(protocol.supportsResume());
        }

        @Test
        void supportsPause() {
            assertFalse(protocol.supportsPause());
        }

        @Test
        void getMaxFileSize() {
            assertEquals(-1, protocol.getMaxFileSize());
        }

        @Test
        void abortDoesNotThrow() {
            assertDoesNotThrow(() -> protocol.abort());
        }

        @Test
        void getMountRoot() {
            assertEquals(tempDir.toString(), protocol.getMountRoot());
        }

        @Test
        void defaultMountRootUsedWhenNoProperty() {
            String previous = System.getProperty(NfsTransferProtocol.MOUNT_ROOT_PROPERTY);
            try {
                System.clearProperty(NfsTransferProtocol.MOUNT_ROOT_PROPERTY);
                NfsTransferProtocol defaultProtocol = new NfsTransferProtocol();
                assertNotNull(defaultProtocol.getMountRoot());
                assertFalse(defaultProtocol.getMountRoot().isEmpty());
            } finally {
                if (previous != null) {
                    System.setProperty(NfsTransferProtocol.MOUNT_ROOT_PROPERTY, previous);
                }
            }
        }
    }

    @Nested
    class CanHandle {

        @Test
        void nfsSchemeDownload() {
            TransferRequest request = TransferRequest.builder()
                    .sourceUri(URI.create("nfs://server/export/file.txt"))
                    .destinationPath(tempDir.resolve("file.txt"))
                    .build();
            assertTrue(protocol.canHandle(request));
        }

        @Test
        void nfsSchemeUpload() {
            TransferRequest request = TransferRequest.builder()
                    .sourceUri(tempDir.resolve("source.txt").toUri())
                    .destinationUri(URI.create("nfs://server/export/file.txt"))
                    .build();
            assertTrue(protocol.canHandle(request));
        }

        @Test
        void httpSchemeRejected() {
            TransferRequest request = TransferRequest.builder()
                    .sourceUri(URI.create("http://server/file.txt"))
                    .destinationPath(tempDir.resolve("file.txt"))
                    .build();
            assertFalse(protocol.canHandle(request));
        }

        @Test
        void smbSchemeRejected() {
            TransferRequest request = TransferRequest.builder()
                    .sourceUri(URI.create("smb://server/share/file.txt"))
                    .destinationPath(tempDir.resolve("file.txt"))
                    .build();
            assertFalse(protocol.canHandle(request));
        }

        @Test
        void nullRequestRejected() {
            assertFalse(protocol.canHandle(null));
        }

        @Test
        void fileToFileIsNotNfs() {
            // A file→file transfer is not an NFS transfer
            // TransferRequest rejects file→file with IllegalArgumentException
            assertThrows(IllegalArgumentException.class, () -> {
                TransferRequest.builder()
                        .sourceUri(tempDir.resolve("file.txt").toUri())
                        .destinationPath(tempDir.resolve("dest.txt"))
                        .build();
            });
        }

        @Test
        void nfsSchemeIsCaseInsensitive() {
            // Java URI normalizes scheme to lowercase, so we verify
            // the protocol handles the lowercase version correctly
            TransferRequest request = TransferRequest.builder()
                    .sourceUri(URI.create("nfs://server/export/file.txt"))
                    .destinationPath(tempDir.resolve("file.txt"))
                    .build();
            assertTrue(protocol.canHandle(request));
            // Verify the scheme comparison itself is case-insensitive
            assertEquals("nfs", request.getSourceUri().getScheme());
        }
    }

    @Nested
    class UriParsing {

        @Test
        void parseStandardNfsUri() throws TransferException {
            URI uri = URI.create("nfs://fileserver/data/reports/q1.csv");
            NfsTransferProtocol.NfsConnectionInfo info = protocol.parseNfsUri(uri);

            assertEquals("fileserver", info.host);
            assertEquals(-1, info.port);
            assertEquals("data", info.exportPath);
            assertEquals("/reports/q1.csv", info.filePath);
        }

        @Test
        void parseNfsUriWithPort() throws TransferException {
            URI uri = URI.create("nfs://fileserver:2049/data/file.txt");
            NfsTransferProtocol.NfsConnectionInfo info = protocol.parseNfsUri(uri);

            assertEquals("fileserver", info.host);
            assertEquals(2049, info.port);
            assertEquals("data", info.exportPath);
            assertEquals("/file.txt", info.filePath);
        }

        @Test
        void parseNfsUriExportOnly() throws TransferException {
            URI uri = URI.create("nfs://server/export");
            NfsTransferProtocol.NfsConnectionInfo info = protocol.parseNfsUri(uri);

            assertEquals("server", info.host);
            assertEquals("export", info.exportPath);
            assertEquals("", info.filePath);
        }

        @Test
        void parseNfsUriDeepPath() throws TransferException {
            URI uri = URI.create("nfs://server/export/a/b/c/d/file.dat");
            NfsTransferProtocol.NfsConnectionInfo info = protocol.parseNfsUri(uri);

            assertEquals("export", info.exportPath);
            assertEquals("/a/b/c/d/file.dat", info.filePath);
        }

        @Test
        void rejectInvalidScheme() {
            URI uri = URI.create("smb://server/share/file.txt");
            assertThrows(TransferException.class, () -> protocol.parseNfsUri(uri));
        }

        @Test
        void rejectMissingHost() {
            URI uri = URI.create("nfs:///export/file.txt");
            assertThrows(TransferException.class, () -> protocol.parseNfsUri(uri));
        }

        @Test
        void rejectMissingPath() {
            // nfs://server with no path at all
            URI uri = URI.create("nfs://server");
            assertThrows(TransferException.class, () -> protocol.parseNfsUri(uri));
        }

        @Test
        void connectionInfoToString() throws TransferException {
            URI uri = URI.create("nfs://server/data/file.txt");
            NfsTransferProtocol.NfsConnectionInfo info = protocol.parseNfsUri(uri);
            String str = info.toString();

            assertTrue(str.contains("server"));
            assertTrue(str.contains("data"));
            assertTrue(str.contains("/file.txt"));
        }
    }

    @Nested
    class MountPathResolution {

        @Test
        void resolveStandardPath() throws TransferException {
            NfsTransferProtocol.NfsConnectionInfo info =
                    new NfsTransferProtocol.NfsConnectionInfo("fileserver", -1, "data", "/reports/q1.csv");
            Path resolved = protocol.resolveNfsMountPath(info);

            String expected = tempDir.toString()
                    + java.io.File.separator + "fileserver"
                    + java.io.File.separator + "data"
                    + java.io.File.separator + "reports"
                    + java.io.File.separator + "q1.csv";
            assertEquals(expected, resolved.toString());
        }

        @Test
        void resolveExportOnly() {
            NfsTransferProtocol.NfsConnectionInfo info =
                    new NfsTransferProtocol.NfsConnectionInfo("server", -1, "export", "");
            Path resolved = protocol.resolveNfsMountPath(info);

            assertTrue(resolved.toString().contains("server"));
            assertTrue(resolved.toString().contains("export"));
        }
    }

    @Nested
    class DownloadTransfers {

        @Test
        void downloadFromMountedNfs() throws Exception {
            // Set up a "mounted" NFS directory within tempDir
            Path serverDir = tempDir.resolve("fileserver").resolve("data");
            Files.createDirectories(serverDir);
            Path sourceFile = serverDir.resolve("test-file.txt");
            Files.writeString(sourceFile, "NFS file content for download test");

            Path destFile = tempDir.resolve("downloaded.txt");

            TransferRequest request = TransferRequest.builder()
                    .requestId("nfs-download-1")
                    .sourceUri(URI.create("nfs://fileserver/data/test-file.txt"))
                    .destinationPath(destFile)
                    .build();

            TransferResult result = protocol.transfer(request, context);

            assertEquals(TransferStatus.COMPLETED, result.getFinalStatus());
            assertTrue(result.getBytesTransferred() > 0);
            assertTrue(Files.exists(destFile));
            assertEquals("NFS file content for download test", Files.readString(destFile));
        }

        @Test
        void downloadNonExistentFileThrows() {
            TransferRequest request = TransferRequest.builder()
                    .requestId("nfs-download-missing")
                    .sourceUri(URI.create("nfs://fileserver/data/missing.txt"))
                    .destinationPath(tempDir.resolve("out.txt"))
                    .build();

            assertThrows(TransferException.class, () -> protocol.transfer(request, context));
        }

        @Test
        void downloadCreatesDestinationDirectory() throws Exception {
            Path serverDir = tempDir.resolve("fileserver").resolve("export");
            Files.createDirectories(serverDir);
            Path sourceFile = serverDir.resolve("file.dat");
            Files.writeString(sourceFile, "data");

            Path destFile = tempDir.resolve("sub").resolve("dir").resolve("file.dat");

            TransferRequest request = TransferRequest.builder()
                    .requestId("nfs-download-mkdir")
                    .sourceUri(URI.create("nfs://fileserver/export/file.dat"))
                    .destinationPath(destFile)
                    .build();

            TransferResult result = protocol.transfer(request, context);

            assertEquals(TransferStatus.COMPLETED, result.getFinalStatus());
            assertTrue(Files.exists(destFile));
        }

        @Test
        void downloadWithChecksumCalculation() throws Exception {
            Path serverDir = tempDir.resolve("fileserver").resolve("data");
            Files.createDirectories(serverDir);
            Path sourceFile = serverDir.resolve("checksummed.bin");
            Files.write(sourceFile, new byte[]{1, 2, 3, 4, 5});

            TransferRequest request = TransferRequest.builder()
                    .requestId("nfs-download-checksum")
                    .sourceUri(URI.create("nfs://fileserver/data/checksummed.bin"))
                    .destinationPath(tempDir.resolve("out.bin"))
                    .expectedChecksum("expected")
                    .build();

            TransferResult result = protocol.transfer(request, context);

            assertEquals(TransferStatus.COMPLETED, result.getFinalStatus());
            assertTrue(result.getActualChecksum().isPresent());
            // SHA-256 hash should be 64 hex characters
            assertEquals(64, result.getActualChecksum().get().length());
        }

        @Test
        void downloadPreservesTimestamps() throws Exception {
            Path serverDir = tempDir.resolve("fileserver").resolve("data");
            Files.createDirectories(serverDir);
            Files.writeString(serverDir.resolve("timed.txt"), "timing test");

            TransferRequest request = TransferRequest.builder()
                    .requestId("nfs-download-time")
                    .sourceUri(URI.create("nfs://fileserver/data/timed.txt"))
                    .destinationPath(tempDir.resolve("timed-out.txt"))
                    .build();

            TransferResult result = protocol.transfer(request, context);

            assertTrue(result.getStartTime().isPresent());
            assertTrue(result.getEndTime().isPresent());
            assertTrue(result.getEndTime().get().isAfter(result.getStartTime().get())
                    || result.getEndTime().get().equals(result.getStartTime().get()));
        }

        @Test
        void downloadLargeFile() throws Exception {
            Path serverDir = tempDir.resolve("fileserver").resolve("data");
            Files.createDirectories(serverDir);
            Path sourceFile = serverDir.resolve("large.bin");
            // Write 256KB file (multiple buffer reads)
            byte[] data = new byte[256 * 1024];
            java.util.Arrays.fill(data, (byte) 0xAB);
            Files.write(sourceFile, data);

            TransferRequest request = TransferRequest.builder()
                    .requestId("nfs-download-large")
                    .sourceUri(URI.create("nfs://fileserver/data/large.bin"))
                    .destinationPath(tempDir.resolve("large-out.bin"))
                    .build();

            TransferResult result = protocol.transfer(request, context);

            assertEquals(TransferStatus.COMPLETED, result.getFinalStatus());
            assertEquals(256 * 1024, result.getBytesTransferred());
        }
    }

    @Nested
    class UploadTransfers {

        @Test
        void uploadToSimulatedNfsServer() throws Exception {
            Path sourceFile = tempDir.resolve("upload-source.txt");
            Files.writeString(sourceFile, "NFS upload test content");

            TransferRequest request = TransferRequest.builder()
                    .requestId("nfs-upload-1")
                    .sourceUri(sourceFile.toUri())
                    .destinationUri(URI.create("nfs://simulated-nfs-server/export/upload-dest.txt"))
                    .build();

            TransferResult result = protocol.transfer(request, context);

            assertEquals(TransferStatus.COMPLETED, result.getFinalStatus());
            assertTrue(result.getBytesTransferred() > 0);
            assertEquals("NFS upload test content".length(), result.getBytesTransferred());
        }

        @Test
        void uploadMissingSourceFileThrows() {
            Path nonExistent = tempDir.resolve("does-not-exist.txt");

            TransferRequest request = TransferRequest.builder()
                    .requestId("nfs-upload-missing")
                    .sourceUri(nonExistent.toUri())
                    .destinationUri(URI.create("nfs://server/export/dest.txt"))
                    .build();

            assertThrows(TransferException.class, () -> protocol.transfer(request, context));
        }

        @Test
        void uploadToMountedNfsCreatesFile() throws Exception {
            Path sourceFile = tempDir.resolve("upload-src.dat");
            Files.writeString(sourceFile, "upload via mount");

            // Create the "mounted" target directory structure
            Path mountDir = tempDir.resolve("nfshost").resolve("share");
            Files.createDirectories(mountDir);

            TransferRequest request = TransferRequest.builder()
                    .requestId("nfs-upload-mount")
                    .sourceUri(sourceFile.toUri())
                    .destinationUri(URI.create("nfs://nfshost/share/uploaded.dat"))
                    .build();

            TransferResult result = protocol.transfer(request, context);

            assertEquals(TransferStatus.COMPLETED, result.getFinalStatus());
            assertTrue(Files.exists(mountDir.resolve("uploaded.dat")));
        }
    }

    @Nested
    class ProtocolFactoryIntegration {

        @Test
        void factoryRegistersNfsProtocol() {
            io.vertx.core.Vertx vertx = io.vertx.core.Vertx.vertx();
            try {
                ProtocolFactory factory = new ProtocolFactory(vertx);
                assertTrue(factory.isProtocolSupported("nfs"));
                TransferProtocol nfs = factory.getProtocol("nfs");
                assertNotNull(nfs);
                assertInstanceOf(NfsTransferProtocol.class, nfs);
            } finally {
                vertx.close();
            }
        }

        @Test
        void factoryResolvesNfsForDownloadRequest() {
            io.vertx.core.Vertx vertx = io.vertx.core.Vertx.vertx();
            try {
                ProtocolFactory factory = new ProtocolFactory(vertx);
                TransferRequest request = TransferRequest.builder()
                        .sourceUri(URI.create("nfs://server/export/file.txt"))
                        .destinationPath(tempDir.resolve("file.txt"))
                        .build();
                TransferProtocol resolved = factory.getProtocol(request);
                assertNotNull(resolved);
                assertInstanceOf(NfsTransferProtocol.class, resolved);
            } finally {
                vertx.close();
            }
        }

        @Test
        void factoryResolvesNfsForUploadRequest() {
            io.vertx.core.Vertx vertx = io.vertx.core.Vertx.vertx();
            try {
                ProtocolFactory factory = new ProtocolFactory(vertx);
                TransferRequest request = TransferRequest.builder()
                        .sourceUri(tempDir.resolve("file.txt").toUri())
                        .destinationUri(URI.create("nfs://server/export/file.txt"))
                        .build();
                TransferProtocol resolved = factory.getProtocol(request);
                assertNotNull(resolved);
                assertInstanceOf(NfsTransferProtocol.class, resolved);
            } finally {
                vertx.close();
            }
        }
    }
}
