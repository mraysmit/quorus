/*
 * Copyright (c) 2025 Cityline Ltd.
 * All rights reserved.
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

package dev.mars.quorus.simulator.fs;

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.io.*;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.NoSuchFileException;
import java.util.List;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link InMemoryFileSystemSimulator}.
 */
@DisplayName("InMemoryFileSystemSimulator Tests")
class InMemoryFileSystemSimulatorTest {

    private InMemoryFileSystemSimulator fs;

    @BeforeEach
    void setUp() {
        fs = new InMemoryFileSystemSimulator();
    }

    // ==================== File Operations ====================

    @Nested
    @DisplayName("File Operations")
    class FileOperationsTests {

        @Test
        @DisplayName("Should create file with content")
        void testCreateFile() throws IOException {
            byte[] content = "Hello, World!".getBytes();
            fs.createFile("/test.txt", content);

            assertThat(fs.exists("/test.txt")).isTrue();
            assertThat(fs.isFile("/test.txt")).isTrue();
        }

        @Test
        @DisplayName("Should read file content")
        void testReadFile() throws IOException {
            byte[] content = "Test content".getBytes();
            fs.createFile("/data.txt", content);

            byte[] readContent = fs.readFile("/data.txt");
            assertThat(readContent).isEqualTo(content);
        }

        @Test
        @DisplayName("Should throw on duplicate file creation")
        void testDuplicateFileCreation() throws IOException {
            fs.createFile("/test.txt", "content".getBytes());

            assertThatThrownBy(() -> fs.createFile("/test.txt", "other".getBytes()))
                .isInstanceOf(FileAlreadyExistsException.class);
        }

        @Test
        @DisplayName("Should throw on reading non-existent file")
        void testReadNonExistentFile() {
            assertThatThrownBy(() -> fs.readFile("/nonexistent.txt"))
                .isInstanceOf(NoSuchFileException.class);
        }

        @Test
        @DisplayName("Should write to existing file")
        void testWriteFile() throws IOException {
            fs.createFile("/test.txt", "original".getBytes());
            fs.writeFile("/test.txt", "updated".getBytes());

            assertThat(new String(fs.readFile("/test.txt"))).isEqualTo("updated");
        }

        @Test
        @DisplayName("Should append to file")
        void testAppendFile() throws IOException {
            fs.createFile("/test.txt", "Hello".getBytes());
            fs.appendFile("/test.txt", " World".getBytes());

            assertThat(new String(fs.readFile("/test.txt"))).isEqualTo("Hello World");
        }

        @Test
        @DisplayName("Should delete file")
        void testDeleteFile() throws IOException {
            fs.createFile("/test.txt", "content".getBytes());
            fs.deleteFile("/test.txt");

            assertThat(fs.exists("/test.txt")).isFalse();
        }

        @Test
        @DisplayName("Should throw on deleting non-existent file")
        void testDeleteNonExistentFile() {
            assertThatThrownBy(() -> fs.deleteFile("/nonexistent.txt"))
                .isInstanceOf(NoSuchFileException.class);
        }

        @Test
        @DisplayName("Should open input stream")
        void testOpenInputStream() throws IOException {
            fs.createFile("/test.txt", "content".getBytes());

            try (InputStream is = fs.openInputStream("/test.txt")) {
                byte[] buffer = is.readAllBytes();
                assertThat(new String(buffer)).isEqualTo("content");
            }
        }

        @Test
        @DisplayName("Should open output stream")
        void testOpenOutputStream() throws IOException {
            try (OutputStream os = fs.openOutputStream("/test.txt")) {
                os.write("streamed content".getBytes());
            }

            assertThat(new String(fs.readFile("/test.txt"))).isEqualTo("streamed content");
        }
    }

    // ==================== Directory Operations ====================

    @Nested
    @DisplayName("Directory Operations")
    class DirectoryOperationsTests {

        @Test
        @DisplayName("Should create directory")
        void testCreateDirectory() throws IOException {
            fs.createDirectory("/mydir");

            assertThat(fs.exists("/mydir")).isTrue();
            assertThat(fs.isDirectory("/mydir")).isTrue();
        }

        @Test
        @DisplayName("Should create parent directories")
        void testCreateDirectories() throws IOException {
            fs.createDirectories("/a/b/c/d");

            assertThat(fs.isDirectory("/a")).isTrue();
            assertThat(fs.isDirectory("/a/b")).isTrue();
            assertThat(fs.isDirectory("/a/b/c")).isTrue();
            assertThat(fs.isDirectory("/a/b/c/d")).isTrue();
        }

        @Test
        @DisplayName("Should list directory contents")
        void testListDirectory() throws IOException {
            fs.createFile("/dir/file1.txt", "a".getBytes());
            fs.createFile("/dir/file2.txt", "b".getBytes());
            fs.createDirectory("/dir/subdir");

            List<String> contents = fs.listDirectory("/dir");
            assertThat(contents).containsExactlyInAnyOrder("file1.txt", "file2.txt", "subdir/");
        }

        @Test
        @DisplayName("Should delete empty directory")
        void testDeleteDirectory() throws IOException {
            fs.createDirectory("/emptydir");
            fs.deleteDirectory("/emptydir");

            assertThat(fs.exists("/emptydir")).isFalse();
        }

        @Test
        @DisplayName("Should throw on deleting non-empty directory")
        void testDeleteNonEmptyDirectory() throws IOException {
            fs.createFile("/dir/file.txt", "content".getBytes());

            assertThatThrownBy(() -> fs.deleteDirectory("/dir"))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("not empty");
        }

        @Test
        @DisplayName("Should auto-create parent directories when creating file")
        void testAutoCreateParentDirectories() throws IOException {
            fs.createFile("/a/b/c/file.txt", "content".getBytes());

            assertThat(fs.isDirectory("/a")).isTrue();
            assertThat(fs.isDirectory("/a/b")).isTrue();
            assertThat(fs.isDirectory("/a/b/c")).isTrue();
            assertThat(fs.isFile("/a/b/c/file.txt")).isTrue();
        }
    }

    // ==================== File Metadata ====================

    @Nested
    @DisplayName("File Metadata")
    class FileMetadataTests {

        @Test
        @DisplayName("Should get file metadata")
        void testGetFileMetadata() throws IOException {
            byte[] content = "Test content".getBytes();
            fs.createFile("/test.txt", content);

            var metadata = fs.getMetadata("/test.txt");
            assertThat(metadata.path()).isEqualTo("/test.txt");
            assertThat(metadata.size()).isEqualTo(content.length);
            assertThat(metadata.isDirectory()).isFalse();
            assertThat(metadata.created()).isNotNull();
        }

        @Test
        @DisplayName("Should get directory metadata")
        void testGetDirectoryMetadata() throws IOException {
            fs.createDirectory("/mydir");

            var metadata = fs.getMetadata("/mydir");
            assertThat(metadata.path()).isEqualTo("/mydir");
            assertThat(metadata.isDirectory()).isTrue();
        }

        @Test
        @DisplayName("Should set file permissions")
        void testSetPermissions() throws IOException {
            fs.createFile("/test.txt", "content".getBytes());
            fs.setPermissions("/test.txt", 
                Set.of(InMemoryFileSystemSimulator.FilePermission.READ));

            var metadata = fs.getMetadata("/test.txt");
            assertThat(metadata.permissions())
                .containsExactly(InMemoryFileSystemSimulator.FilePermission.READ);
        }
    }

    // ==================== File Locking ====================

    @Nested
    @DisplayName("File Locking")
    class FileLockingTests {

        @Test
        @DisplayName("Should lock file")
        void testLockFile() throws IOException {
            fs.createFile("/test.txt", "content".getBytes());
            fs.lockFile("/test.txt");

            assertThat(fs.isLocked("/test.txt")).isTrue();
        }

        @Test
        @DisplayName("Should unlock file")
        void testUnlockFile() throws IOException {
            fs.createFile("/test.txt", "content".getBytes());
            fs.lockFile("/test.txt");
            fs.unlockFile("/test.txt");

            assertThat(fs.isLocked("/test.txt")).isFalse();
        }

        @Test
        @DisplayName("Should prevent reading locked file")
        void testReadLockedFile() throws IOException {
            fs.createFile("/test.txt", "content".getBytes());
            fs.lockFile("/test.txt");

            assertThatThrownBy(() -> fs.readFile("/test.txt"))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("locked");
        }

        @Test
        @DisplayName("Should prevent writing to locked file")
        void testWriteLockedFile() throws IOException {
            fs.createFile("/test.txt", "content".getBytes());
            fs.lockFile("/test.txt");

            assertThatThrownBy(() -> fs.writeFile("/test.txt", "new".getBytes()))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("locked");
        }

        @Test
        @DisplayName("Should throw on locking already locked file")
        void testDoubleLock() throws IOException {
            fs.createFile("/test.txt", "content".getBytes());
            fs.lockFile("/test.txt");

            assertThatThrownBy(() -> fs.lockFile("/test.txt"))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("already locked");
        }
    }

    // ==================== Space Management ====================

    @Nested
    @DisplayName("Space Management")
    class SpaceManagementTests {

        @Test
        @DisplayName("Should track used space")
        void testUsedSpace() throws IOException {
            fs.createFile("/file1.txt", new byte[1000]);
            fs.createFile("/file2.txt", new byte[500]);

            assertThat(fs.getUsedSpace()).isEqualTo(1500);
        }

        @Test
        @DisplayName("Should report available space")
        void testAvailableSpace() throws IOException {
            fs.setAvailableSpace(10000);
            fs.createFile("/test.txt", new byte[3000]);

            assertThat(fs.getAvailableSpace()).isEqualTo(7000);
        }

        @Test
        @DisplayName("Should prevent write when disk is full")
        void testDiskFull() throws IOException {
            fs.setAvailableSpace(100);

            assertThatThrownBy(() -> fs.createFile("/large.txt", new byte[200]))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("Disk full");
        }

        @Test
        @DisplayName("Should free space on delete")
        void testFreeSpaceOnDelete() throws IOException {
            fs.createFile("/test.txt", new byte[1000]);
            long usedBefore = fs.getUsedSpace();
            
            fs.deleteFile("/test.txt");
            
            assertThat(fs.getUsedSpace()).isEqualTo(usedBefore - 1000);
        }
    }

    // ==================== Performance Simulation ====================

    @Nested
    @DisplayName("Performance Simulation")
    class PerformanceSimulationTests {

        @Test
        @DisplayName("Should simulate read delay")
        void testReadDelay() throws IOException {
            fs.createFile("/test.txt", "content".getBytes());
            fs.setReadDelayMs(100);

            long start = System.currentTimeMillis();
            fs.readFile("/test.txt");
            long elapsed = System.currentTimeMillis() - start;

            assertThat(elapsed).isGreaterThanOrEqualTo(100);
        }

        @Test
        @DisplayName("Should simulate write delay")
        void testWriteDelay() throws IOException {
            fs.setWriteDelayMs(100);

            long start = System.currentTimeMillis();
            fs.createFile("/test.txt", "content".getBytes());
            long elapsed = System.currentTimeMillis() - start;

            assertThat(elapsed).isGreaterThanOrEqualTo(100);
        }

        @Test
        @DisplayName("Should simulate read bandwidth")
        void testReadBandwidth() throws IOException {
            fs.createFile("/test.txt", new byte[10000]);
            fs.setReadBytesPerSecond(5000); // 5KB/s

            long start = System.currentTimeMillis();
            fs.readFile("/test.txt");
            long elapsed = System.currentTimeMillis() - start;

            // Should take at least 2 seconds for 10KB at 5KB/s
            assertThat(elapsed).isGreaterThanOrEqualTo(2000);
        }
    }

    // ==================== Chaos Engineering ====================

    @Nested
    @DisplayName("Chaos Engineering")
    class ChaosEngineeringTests {

        @ParameterizedTest
        @EnumSource(InMemoryFileSystemSimulator.FileSystemFailureMode.class)
        @DisplayName("Should support all failure modes")
        void testAllFailureModes(InMemoryFileSystemSimulator.FileSystemFailureMode mode) {
            fs.setFailureMode(mode);
            // Just verify no exception on setting
        }

        @Test
        @DisplayName("Should simulate DISK_FULL failure")
        void testDiskFullFailure() throws IOException {
            fs.setFailureMode(InMemoryFileSystemSimulator.FileSystemFailureMode.DISK_FULL);

            assertThatThrownBy(() -> fs.createFile("/test.txt", "content".getBytes()))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("Disk full");
        }

        @Test
        @DisplayName("Should simulate READ_ONLY failure")
        void testReadOnlyFailure() throws IOException {
            fs.setFailureMode(InMemoryFileSystemSimulator.FileSystemFailureMode.READ_ONLY);

            assertThatThrownBy(() -> fs.createFile("/test.txt", "content".getBytes()))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("read-only");
        }

        @Test
        @DisplayName("Should simulate PERMISSION_DENIED failure")
        void testPermissionDeniedFailure() throws IOException {
            fs.setFailureMode(InMemoryFileSystemSimulator.FileSystemFailureMode.PERMISSION_DENIED);

            assertThatThrownBy(() -> fs.createFile("/test.txt", "content".getBytes()))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("Permission denied");
        }

        @Test
        @DisplayName("Should simulate IO_ERROR failure")
        void testIOErrorFailure() throws IOException {
            fs.setFailureMode(InMemoryFileSystemSimulator.FileSystemFailureMode.IO_ERROR);

            assertThatThrownBy(() -> fs.createFile("/test.txt", "content".getBytes()))
                .isInstanceOf(IOException.class);
        }

        @Test
        @DisplayName("Should set file-specific failure mode")
        void testFileSpecificFailure() throws IOException {
            fs.createFile("/normal.txt", "content".getBytes());
            fs.setFileFailureMode("/fail.txt", 
                InMemoryFileSystemSimulator.FileSystemFailureMode.IO_ERROR);

            // Normal file should work
            byte[] content = fs.readFile("/normal.txt");
            assertThat(content).isNotNull();

            // Failed file should throw
            assertThatThrownBy(() -> fs.createFile("/fail.txt", "content".getBytes()))
                .isInstanceOf(IOException.class);
        }

        @Test
        @DisplayName("Should simulate random failures")
        void testRandomFailure() throws IOException {
            fs.setFailureRate(0.5);
            fs.setFailureMode(InMemoryFileSystemSimulator.FileSystemFailureMode.RANDOM_FAILURE);

            int failures = 0;
            int attempts = 20;

            for (int i = 0; i < attempts; i++) {
                try {
                    fs.createFile("/test" + i + ".txt", "content".getBytes());
                } catch (IOException e) {
                    failures++;
                }
            }

            // Should have some failures (probabilistic)
            assertThat(failures).isGreaterThan(0);
        }

        @Test
        @DisplayName("Should reset chaos settings")
        void testResetChaos() throws IOException {
            fs.setFailureMode(InMemoryFileSystemSimulator.FileSystemFailureMode.DISK_FULL);
            fs.setFailureRate(1.0);

            fs.resetChaos();

            // Should work normally after reset
            fs.createFile("/test.txt", "content".getBytes());
            assertThat(fs.exists("/test.txt")).isTrue();
        }
    }

    // ==================== Statistics ====================

    @Nested
    @DisplayName("Statistics")
    class StatisticsTests {

        @Test
        @DisplayName("Should track read operations")
        void testReadOperations() throws IOException {
            fs.createFile("/test.txt", "content".getBytes());
            fs.readFile("/test.txt");
            fs.readFile("/test.txt");

            assertThat(fs.getReadOperations()).isEqualTo(2);
        }

        @Test
        @DisplayName("Should track write operations")
        void testWriteOperations() throws IOException {
            fs.createFile("/file1.txt", "a".getBytes());
            fs.createFile("/file2.txt", "b".getBytes());
            fs.writeFile("/file1.txt", "c".getBytes());

            assertThat(fs.getWriteOperations()).isEqualTo(3);
        }

        @Test
        @DisplayName("Should track bytes read")
        void testBytesRead() throws IOException {
            fs.createFile("/test.txt", new byte[1000]);
            fs.readFile("/test.txt");

            assertThat(fs.getBytesRead()).isEqualTo(1000);
        }

        @Test
        @DisplayName("Should track bytes written")
        void testBytesWritten() throws IOException {
            fs.createFile("/test.txt", new byte[500]);
            fs.appendFile("/test.txt", new byte[200]);

            assertThat(fs.getBytesWritten()).isEqualTo(700);
        }

        @Test
        @DisplayName("Should track file count")
        void testFileCount() throws IOException {
            fs.createFile("/a.txt", "a".getBytes());
            fs.createFile("/b.txt", "b".getBytes());
            fs.createFile("/c.txt", "c".getBytes());

            assertThat(fs.getFileCount()).isEqualTo(3);
        }

        @Test
        @DisplayName("Should track directory count")
        void testDirectoryCount() throws IOException {
            fs.createDirectory("/dir1");
            fs.createDirectory("/dir2");
            fs.createDirectory("/dir3");

            // +1 for root directory
            assertThat(fs.getDirectoryCount()).isEqualTo(4);
        }

        @Test
        @DisplayName("Should reset statistics")
        void testResetStatistics() throws IOException {
            fs.createFile("/test.txt", "content".getBytes());
            fs.readFile("/test.txt");

            fs.resetStatistics();

            assertThat(fs.getReadOperations()).isZero();
            assertThat(fs.getWriteOperations()).isZero();
            assertThat(fs.getBytesRead()).isZero();
            assertThat(fs.getBytesWritten()).isZero();
        }
    }

    // ==================== Utility Methods ====================

    @Nested
    @DisplayName("Utility Methods")
    class UtilityMethodsTests {

        @Test
        @DisplayName("Should clear all files and directories")
        void testClear() throws IOException {
            fs.createFile("/a/b/file.txt", "content".getBytes());
            fs.createDirectory("/c/d");

            fs.clear();

            assertThat(fs.getFileCount()).isZero();
            assertThat(fs.getDirectoryCount()).isEqualTo(1); // Root only
            assertThat(fs.getUsedSpace()).isZero();
        }

        @Test
        @DisplayName("Should get all file paths")
        void testGetAllFilePaths() throws IOException {
            fs.createFile("/a.txt", "a".getBytes());
            fs.createFile("/dir/b.txt", "b".getBytes());

            var paths = fs.getAllFilePaths();
            assertThat(paths).containsExactlyInAnyOrder("/a.txt", "/dir/b.txt");
        }

        @Test
        @DisplayName("Should get all directory paths")
        void testGetAllDirectoryPaths() throws IOException {
            fs.createDirectory("/dir1");
            fs.createDirectory("/dir1/sub");

            var paths = fs.getAllDirectoryPaths();
            assertThat(paths).contains("/", "/dir1", "/dir1/sub");
        }

        @Test
        @DisplayName("Should normalize paths")
        void testPathNormalization() throws IOException {
            fs.createFile("test.txt", "a".getBytes());
            fs.createFile("/dir/file.txt", "b".getBytes());
            fs.createFile("\\windows\\path.txt", "c".getBytes());

            assertThat(fs.exists("/test.txt")).isTrue();
            assertThat(fs.exists("/dir/file.txt")).isTrue();
            assertThat(fs.exists("/windows/path.txt")).isTrue();
        }
    }

    // ==================== Concurrent Access ====================

    @Nested
    @DisplayName("Concurrent Access")
    class ConcurrentAccessTests {

        @Test
        @DisplayName("Should handle concurrent file operations")
        void testConcurrentFileOperations() throws Exception {
            int threadCount = 10;
            int filesPerThread = 100;
            CountDownLatch latch = new CountDownLatch(threadCount);
            AtomicInteger errors = new AtomicInteger(0);

            ExecutorService executor = Executors.newFixedThreadPool(threadCount);

            for (int t = 0; t < threadCount; t++) {
                final int threadId = t;
                executor.submit(() -> {
                    try {
                        for (int i = 0; i < filesPerThread; i++) {
                            String path = "/thread" + threadId + "/file" + i + ".txt";
                            fs.createFile(path, ("content" + i).getBytes());
                            fs.readFile(path);
                        }
                    } catch (Exception e) {
                        errors.incrementAndGet();
                    } finally {
                        latch.countDown();
                    }
                });
            }

            latch.await(60, TimeUnit.SECONDS);
            executor.shutdown();

            assertThat(errors.get()).isZero();
            assertThat(fs.getFileCount()).isEqualTo(threadCount * filesPerThread);
        }
    }
}
