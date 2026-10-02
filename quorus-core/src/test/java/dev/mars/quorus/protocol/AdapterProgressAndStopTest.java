/*
 * Copyright 2026 Mark Andrew Ray-Smith Cityline Ltd
 * Licensed under the Apache License, Version 2.0.
 */
package dev.mars.quorus.protocol;

import dev.mars.quorus.connection.RuntimeCredential;
import dev.mars.quorus.connection.ServiceConnection;
import dev.mars.quorus.connection.SftpHostKeyPolicy;
import dev.mars.quorus.core.TransferJob;
import dev.mars.quorus.core.TransferRequest;
import dev.mars.quorus.core.TransferResult;
import dev.mars.quorus.core.exceptions.TransferException;
import dev.mars.quorus.transfer.TransferContext;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Random;
import java.util.Set;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Register item ENG-10: every protocol adapter reports progress to the transfer's job and stops
 * between buffers when the context is cancelled or paused, as the HTTP adapter does. Before ENG-10
 * the FTP, SFTP, SMB and NFS adapters kept progress in a private tracker nothing read, and ignored
 * the context, so no test observed either.
 *
 * <p>Each case moves a file of many buffers through the real adapter. Stopping is triggered from
 * the context itself (once the job reports progress), so no thread, sleep or timing is involved.
 * SMB has no server fixture: it shares the NFS copy loop's approach, and its wiring is covered by
 * {@code ProgressTrackerTest}.
 */
@DisplayName("Protocol adapters - job progress and stop requests")
@Timeout(value = 60, unit = SECONDS, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
class AdapterProgressAndStopTest {

    private static final int SIZE = 1024 * 1024;   // many buffers for every adapter (32 or 64 KB)

    @TempDir
    Path directory;

    /** A context that asks to stop as soon as the job has reported any progress. */
    private static TransferContext stopAfterFirstProgress(TransferRequest request) {
        return new TransferContext(new TransferJob(request)) {
            @Override
            public boolean shouldContinue() {
                return getJob().getBytesTransferred() == 0 && super.shouldContinue();
            }
        };
    }

    private static TransferContext pausedContext(TransferRequest request) {
        TransferContext context = new TransferContext(new TransferJob(request));
        context.pause();
        return context;
    }

    private Path localFile(String name) throws Exception {
        byte[] data = new byte[SIZE];
        new Random(42).nextBytes(data);
        return Files.write(directory.resolve(name), data);
    }

    private static void assertJobProgress(TransferContext context, TransferResult result) {
        assertTrue(result.isSuccessful(), () -> "transfer failed: " + result.getErrorMessage());
        assertEquals(SIZE, context.getJob().getBytesTransferred(), "the job reports the bytes moved");
        assertEquals(SIZE, context.getJob().getTotalBytes(), "the job reports the file size");
    }

    private static void assertStoppedEarly(Path destination) throws Exception {
        assertTrue(!Files.exists(destination) || Files.size(destination) < SIZE,
                "a stopped download must not produce the complete file");
    }

    /** Runs a protocol's cases; each nested class supplies the URIs and the adapter. */
    abstract class Cases {
        abstract TransferProtocol protocol();

        abstract URI remote(String name);

        RuntimeCredential credential() {
            return null;
        }

        TransferRequest upload(String name, Path source) {
            return TransferRequest.builder().requestId("up-" + name).sourceUri(source.toUri())
                    .destinationUri(remote(name)).runtimeCredential(credential()).build();
        }

        TransferRequest download(String name, Path destination) {
            return TransferRequest.builder().requestId("down-" + name).sourceUri(remote(name))
                    .destinationPath(destination).runtimeCredential(credential()).build();
        }

        /** Puts a file of SIZE bytes on the remote side through the adapter itself. */
        void seed(String name) throws Exception {
            TransferRequest request = upload(name, localFile("seed-" + name));
            assertTrue(protocol().transfer(request, new TransferContext(new TransferJob(request))).isSuccessful());
        }

        @Test
        @DisplayName("An upload reports its progress and size to the job")
        void uploadReportsProgressToTheJob() throws Exception {
            TransferRequest request = upload("progress-up.bin", localFile("progress-up.bin"));
            TransferContext context = new TransferContext(new TransferJob(request));

            assertJobProgress(context, protocol().transfer(request, context));
        }

        @Test
        @DisplayName("A download reports its progress and size to the job")
        void downloadReportsProgressToTheJob() throws Exception {
            seed("progress-down.bin");
            TransferRequest request = download("progress-down.bin", directory.resolve("progress-down.out"));
            TransferContext context = new TransferContext(new TransferJob(request));

            assertJobProgress(context, protocol().transfer(request, context));
        }

        @Test
        @DisplayName("A download stops between buffers when the context asks it to")
        void downloadStopsWhenTheContextStops() throws Exception {
            seed("stop.bin");
            Path destination = directory.resolve("stop.out");
            TransferRequest request = download("stop.bin", destination);

            assertThrows(TransferException.class,
                    () -> protocol().transfer(request, stopAfterFirstProgress(request)));
            assertStoppedEarly(destination);
        }

        @Test
        @DisplayName("A paused download stops instead of running to completion")
        void pausedDownloadStops() throws Exception {
            seed("paused.bin");
            Path destination = directory.resolve("paused.out");
            TransferRequest request = download("paused.bin", destination);

            assertThrows(TransferException.class, () -> protocol().transfer(request, pausedContext(request)));
            assertStoppedEarly(destination);
        }
    }

    @Nested
    @DisplayName("NFS (local mount root)")
    class Nfs extends Cases {
        private NfsTransferProtocol protocol;

        @BeforeEach
        void setUp() throws Exception {
            Files.createDirectories(directory.resolve("mount/fileserver/export"));
            protocol = new NfsTransferProtocol(directory.resolve("mount").toString());
        }

        @Override TransferProtocol protocol() { return protocol; }

        @Override URI remote(String name) { return URI.create("nfs://fileserver/export/" + name); }
    }

    @Nested
    @DisplayName("FTP (Docker)")
    class Ftp extends Cases {
        private final FtpTransferProtocol protocol = new FtpTransferProtocol();

        @BeforeAll
        static void requireDocker() {
            assumeTrue(SharedTestContainers.isDockerAvailable(), "Docker is not available");
            SharedTestContainers.getFtpContainer();
        }

        @Override TransferProtocol protocol() { return protocol; }

        @Override URI remote(String name) {
            return URI.create("ftp://" + SharedTestContainers.getFtpHost() + ":" + SharedTestContainers.getFtpPort()
                    + "/" + name);
        }
    }

    @Nested
    @DisplayName("SFTP (Docker)")
    class Sftp extends Cases {
        private final SftpTransferProtocol protocol = new SftpTransferProtocol();
        private String hostKey;

        @BeforeAll
        static void requireDocker() {
            assumeTrue(SharedTestContainers.isDockerAvailable(), "Docker is not available");
            SharedTestContainers.getSftpContainer();
        }

        @BeforeEach
        void readHostKey() throws Exception {
            com.jcraft.jsch.Session session = new com.jcraft.jsch.JSch().getSession("testuser",
                    SharedTestContainers.getSftpHost(), SharedTestContainers.getSftpPort());
            session.setPassword("testpass");
            session.setConfig("StrictHostKeyChecking", "no");   // only to read the key that is then pinned
            session.connect(10000);
            try {
                hostKey = SftpHostKeyPolicy.sha256Fingerprint(Base64.getDecoder().decode(session.getHostKey().getKey()));
            } finally {
                session.disconnect();
            }
        }

        @Override TransferProtocol protocol() { return protocol; }

        @Override URI remote(String name) {
            return URI.create("sftp://" + SharedTestContainers.getSftpHost() + ":" + SharedTestContainers.getSftpPort()
                    + "/upload/" + name);
        }

        @Override RuntimeCredential credential() {
            return new RuntimeCredential("testuser", ServiceConnection.AuthenticationType.PASSWORD,
                    "testpass".toCharArray(), Set.of(hostKey), Set.of(), "TLSv1.3");
        }
    }
}
