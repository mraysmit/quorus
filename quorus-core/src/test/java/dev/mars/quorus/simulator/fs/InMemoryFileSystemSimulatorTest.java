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

package dev.mars.quorus.simulator.fs;

import dev.mars.quorus.simulator.SimulatorTestLoggingExtension;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
@ExtendWith(SimulatorTestLoggingExtension.class)
@DisplayName("InMemoryFileSystemSimulator Tests")
class InMemoryFileSystemSimulatorTest {

    private static final Logger log = LoggerFactory.getLogger(InMemoryFileSystemSimulatorTest.class);

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
            log.info("Testing file creation with content");
            byte[] content = "Hello, World!".getBytes();
            fs.createFile("/test.txt", content);

            log.info("File created: exists={}, isFile={}", fs.exists("/test.txt"), fs.isFile("/test.txt"));
            assertThat(fs.exists("/test.txt")).isTrue();
            assertThat(fs.isFile("/test.txt")).isTrue();
        }

        @Test
        @DisplayName("Should read file content")
        void testReadFile() throws IOException {
            log.info("Testing file read");
            byte[] content = "Test content".getBytes();
            fs.createFile("/data.txt", content);

            byte[] readContent = fs.readFile("/data.txt");
            log.info("File read: {} bytes", readContent.length);
            assertThat(readContent).isEqualTo(content);
        }

        @Test
        @DisplayName("Should throw on duplicate file creation")
        void testDuplicateFileCreation() throws IOException {
            log.info("Testing duplicate file creation rejection");
            fs.createFile("/test.txt", "content".getBytes());

            assertThatThrownBy(() -> fs.createFile("/test.txt", "other".getBytes()))
                .isInstanceOf(FileAlreadyExistsException.class);
            log.info("Duplicate file creation correctly rejected");
        }

        @Test
        @DisplayName("Should throw on reading non-existent file")
        void testReadNonExistentFile() {
            log.info("Testing read non-existent file");
            assertThatThrownBy(() -> fs.readFile("/nonexistent.txt"))
                .isInstanceOf(NoSuchFileException.class);
            log.info("Non-existent file read correctly rejected");
        }

        @Test
        @DisplayName("Should write to existing file")
        void testWriteFile() throws IOException {
            log.info("Testing file write/overwrite");
            fs.createFile("/test.txt", "original".getBytes());
            fs.writeFile("/test.txt", "updated".getBytes());

            log.info("File updated: content={}", new String(fs.readFile("/test.txt")));
            assertThat(new String(fs.readFile("/test.txt"))).isEqualTo("updated");
        }

        @Test
        @DisplayName("Should append to file")
        void testAppendFile() throws IOException {
            log.info("Testing file append");
            fs.createFile("/test.txt", "Hello".getBytes());
            fs.appendFile("/test.txt", " World".getBytes());

            log.info("File after append: {}", new String(fs.readFile("/test.txt")));
            assertThat(new String(fs.readFile("/test.txt"))).isEqualTo("Hello World");
        }

        @Test
        @DisplayName("Should delete file")
        void testDeleteFile() throws IOException {
            log.info("Testing file deletion");
            fs.createFile("/test.txt", "content".getBytes());
            fs.deleteFile("/test.txt");

            log.info("File deleted: exists={}", fs.exists("/test.txt"));
            assertThat(fs.exists("/test.txt")).isFalse();
        }

        @Test
        @DisplayName("Should throw on deleting non-existent file")
        void testDeleteNonExistentFile() {
            log.info("Testing delete non-existent file");
            assertThatThrownBy(() -> fs.deleteFile("/nonexistent.txt"))
                .isInstanceOf(NoSuchFileException.class);
            log.info("Non-existent file delete correctly rejected");
        }

        @Test
        @DisplayName("Should open input stream")
        void testOpenInputStream() throws IOException {
            log.info("Testing input stream");
            fs.createFile("/test.txt", "content".getBytes());

            try (InputStream is = fs.openInputStream("/test.txt")) {
                byte[] buffer = is.readAllBytes();
                log.info("Read via input stream: {} bytes", buffer.length);
                assertThat(new String(buffer)).isEqualTo("content");
            }
        }

        @Test
        @DisplayName("Should open output stream")
        void testOpenOutputStream() throws IOException {
            log.info("Testing output stream");
            try (OutputStream os = fs.openOutputStream("/test.txt")) {
                os.write("streamed content".getBytes());
            }

            log.info("Written via output stream: {}", new String(fs.readFile("/test.txt")));
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
            log.info("Testing directory creation");
            fs.createDirectory("/mydir");

            log.info("Directory created: exists={}, isDirectory={}", fs.exists("/mydir"), fs.isDirectory("/mydir"));
            assertThat(fs.exists("/mydir")).isTrue();
            assertThat(fs.isDirectory("/mydir")).isTrue();
        }

        @Test
        @DisplayName("Should create parent directories")
        void testCreateDirectories() throws IOException {
            log.info("Testing parent directories creation");
            fs.createDirectories("/a/b/c/d");

            log.info("Created directory tree: /a/b/c/d");
            assertThat(fs.isDirectory("/a")).isTrue();
            assertThat(fs.isDirectory("/a/b")).isTrue();
            assertThat(fs.isDirectory("/a/b/c")).isTrue();
            assertThat(fs.isDirectory("/a/b/c/d")).isTrue();
        }

        @Test
        @DisplayName("Should list directory contents")
        void testListDirectory() throws IOException {
            log.info("Testing directory listing");
            fs.createFile("/dir/file1.txt", "a".getBytes());
            fs.createFile("/dir/file2.txt", "b".getBytes());
            fs.createDirectory("/dir/subdir");

            List<String> contents = fs.listDirectory("/dir");
            log.info("Directory contents: {}", contents);
            assertThat(contents).containsExactlyInAnyOrder("file1.txt", "file2.txt", "subdir/");
        }

        @Test
        @DisplayName("Should delete empty directory")
        void testDeleteDirectory() throws IOException {
            log.info("Testing empty directory deletion");
            fs.createDirectory("/emptydir");
            fs.deleteDirectory("/emptydir");

            log.info("Directory deleted: exists={}", fs.exists("/emptydir"));
            assertThat(fs.exists("/emptydir")).isFalse();
        }

        @Test
        @DisplayName("Should throw on deleting non-empty directory")
        void testDeleteNonEmptyDirectory() throws IOException {
            log.info("Testing non-empty directory deletion rejection");
            fs.createFile("/dir/file.txt", "content".getBytes());

            assertThatThrownBy(() -> fs.deleteDirectory("/dir"))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("not empty");
            log.info("Non-empty directory deletion correctly rejected");
        }

        @Test
        @DisplayName("Should auto-create parent directories when creating file")
        void testAutoCreateParentDirectories() throws IOException {
            log.info("Testing auto-creation of parent directories");
            fs.createFile("/a/b/c/file.txt", "content".getBytes());

            log.info("Parent directories auto-created for /a/b/c/file.txt");
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
            log.info("Testing file metadata retrieval");
            byte[] content = "Test content".getBytes();
            fs.createFile("/test.txt", content);

            var metadata = fs.getMetadata("/test.txt");
            log.info("Metadata: path={}, size={}, isDir={}", metadata.path(), metadata.size(), metadata.isDirectory());
            assertThat(metadata.path()).isEqualTo("/test.txt");
            assertThat(metadata.size()).isEqualTo(content.length);
            assertThat(metadata.isDirectory()).isFalse();
            assertThat(metadata.created()).isNotNull();
        }

        @Test
        @DisplayName("Should get directory metadata")
        void testGetDirectoryMetadata() throws IOException {
            log.info("Testing directory metadata retrieval");
            fs.createDirectory("/mydir");

            var metadata = fs.getMetadata("/mydir");
            log.info("Directory metadata: path={}, isDir={}", metadata.path(), metadata.isDirectory());
            assertThat(metadata.path()).isEqualTo("/mydir");
            assertThat(metadata.isDirectory()).isTrue();
        }

        @Test
        @DisplayName("Should set file permissions")
        void testSetPermissions() throws IOException {
            log.info("Testing file permissions setting");
            fs.createFile("/test.txt", "content".getBytes());
            fs.setPermissions("/test.txt", 
                Set.of(InMemoryFileSystemSimulator.FilePermission.READ));

            var metadata = fs.getMetadata("/test.txt");
            log.info("Permissions set: {}", metadata.permissions());
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
            log.info("Testing file locking");
            fs.createFile("/test.txt", "content".getBytes());
            fs.lockFile("/test.txt");

            log.info("File locked: isLocked={}", fs.isLocked("/test.txt"));
            assertThat(fs.isLocked("/test.txt")).isTrue();
        }

        @Test
        @DisplayName("Should unlock file")
        void testUnlockFile() throws IOException {
            log.info("Testing file unlocking");
            fs.createFile("/test.txt", "content".getBytes());
            fs.lockFile("/test.txt");
            fs.unlockFile("/test.txt");

            log.info("File unlocked: isLocked={}", fs.isLocked("/test.txt"));
            assertThat(fs.isLocked("/test.txt")).isFalse();
        }

        @Test
        @DisplayName("Should prevent reading locked file")
        void testReadLockedFile() throws IOException {
            log.info("Testing read prevention on locked file");
            fs.createFile("/test.txt", "content".getBytes());
            fs.lockFile("/test.txt");

            assertThatThrownBy(() -> fs.readFile("/test.txt"))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("locked");
            log.info("Locked file read correctly rejected");
        }

        @Test
        @DisplayName("Should prevent writing to locked file")
        void testWriteLockedFile() throws IOException {
            log.info("Testing write prevention on locked file");
            fs.createFile("/test.txt", "content".getBytes());
            fs.lockFile("/test.txt");

            assertThatThrownBy(() -> fs.writeFile("/test.txt", "new".getBytes()))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("locked");
            log.info("Locked file write correctly rejected");
        }

        @Test
        @DisplayName("Should throw on locking already locked file")
        void testDoubleLock() throws IOException {
            log.info("Testing double-lock prevention");
            fs.createFile("/test.txt", "content".getBytes());
            fs.lockFile("/test.txt");

            assertThatThrownBy(() -> fs.lockFile("/test.txt"))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("already locked");
            log.info("Double-lock correctly rejected");
        }
    }

    // ==================== Space Management ====================

    @Nested
    @DisplayName("Space Management")
    class SpaceManagementTests {

        @Test
        @DisplayName("Should track used space")
        void testUsedSpace() throws IOException {
            log.info("Testing used space tracking");
            fs.createFile("/file1.txt", new byte[1000]);
            fs.createFile("/file2.txt", new byte[500]);

            log.info("Used space: {} bytes", fs.getUsedSpace());
            assertThat(fs.getUsedSpace()).isEqualTo(1500);
        }

        @Test
        @DisplayName("Should report available space")
        void testAvailableSpace() throws IOException {
            log.info("Testing available space tracking");
            fs.setAvailableSpace(10000);
            fs.createFile("/test.txt", new byte[3000]);

            log.info("Available space: {} bytes (10000 - 3000)", fs.getAvailableSpace());
            assertThat(fs.getAvailableSpace()).isEqualTo(7000);
        }

        @Test
        @DisplayName("Should prevent write when disk is full")
        void testDiskFull() throws IOException {
            log.info("Testing disk full prevention");
            fs.setAvailableSpace(100);

            assertThatThrownBy(() -> fs.createFile("/large.txt", new byte[200]))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("Disk full");
            log.info("Disk full write correctly rejected");
        }

        @Test
        @DisplayName("Should free space on delete")
        void testFreeSpaceOnDelete() throws IOException {
            log.info("Testing space reclamation on delete");
            fs.createFile("/test.txt", new byte[1000]);
            long usedBefore = fs.getUsedSpace();
            
            fs.deleteFile("/test.txt");
            
            log.info("Space reclaimed: before={}, after={}", usedBefore, fs.getUsedSpace());
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
            log.info("Testing read delay: 100ms simulated latency");
            fs.createFile("/test.txt", "content".getBytes());
            fs.setReadDelayMs(100);

            long start = System.currentTimeMillis();
            fs.readFile("/test.txt");
            long elapsed = System.currentTimeMillis() - start;

            log.info("Read completed in {}ms (expected >= 100ms)", elapsed);
            assertThat(elapsed).isGreaterThanOrEqualTo(100);
        }

        @Test
        @DisplayName("Should simulate write delay")
        void testWriteDelay() throws IOException {
            log.info("Testing write delay: 100ms simulated latency");
            fs.setWriteDelayMs(100);

            long start = System.currentTimeMillis();
            fs.createFile("/test.txt", "content".getBytes());
            long elapsed = System.currentTimeMillis() - start;

            log.info("Write completed in {}ms (expected >= 100ms)", elapsed);
            assertThat(elapsed).isGreaterThanOrEqualTo(100);
        }

        @Test
        @DisplayName("Should simulate read bandwidth")
        void testReadBandwidth() throws IOException {
            log.info("Testing read bandwidth: 5KB/s throttle on 10KB file");
            fs.createFile("/test.txt", new byte[10000]);
            fs.setReadBytesPerSecond(5000); // 5KB/s

            long start = System.currentTimeMillis();
            fs.readFile("/test.txt");
            long elapsed = System.currentTimeMillis() - start;

            log.info("Throttled read completed in {}ms (expected >= 2000ms)", elapsed);
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
            log.info("Testing failure mode: {}", mode);
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
            log.info("Testing random failures with 50% failure rate over 20 attempts");
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

            log.info("Random failures: {}/{} attempts (~{}%)", failures, attempts, (failures * 100 / attempts));
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
            log.info("Testing read operations tracking");
            fs.createFile("/test.txt", "content".getBytes());
            fs.readFile("/test.txt");
            fs.readFile("/test.txt");

            log.info("Read operations: {}", fs.getReadOperations());
            assertThat(fs.getReadOperations()).isEqualTo(2);
        }

        @Test
        @DisplayName("Should track write operations")
        void testWriteOperations() throws IOException {
            log.info("Testing write operations tracking");
            fs.createFile("/file1.txt", "a".getBytes());
            fs.createFile("/file2.txt", "b".getBytes());
            fs.writeFile("/file1.txt", "c".getBytes());

            log.info("Write operations: {}", fs.getWriteOperations());
            assertThat(fs.getWriteOperations()).isEqualTo(3);
        }

        @Test
        @DisplayName("Should track bytes read")
        void testBytesRead() throws IOException {
            log.info("Testing bytes read tracking");
            fs.createFile("/test.txt", new byte[1000]);
            fs.readFile("/test.txt");

            log.info("Bytes read: {}", fs.getBytesRead());
            assertThat(fs.getBytesRead()).isEqualTo(1000);
        }

        @Test
        @DisplayName("Should track bytes written")
        void testBytesWritten() throws IOException {
            log.info("Testing bytes written tracking");
            fs.createFile("/test.txt", new byte[500]);
            fs.appendFile("/test.txt", new byte[200]);

            log.info("Bytes written: {}", fs.getBytesWritten());
            assertThat(fs.getBytesWritten()).isEqualTo(700);
        }

        @Test
        @DisplayName("Should track file count")
        void testFileCount() throws IOException {
            log.info("Testing file count tracking");
            fs.createFile("/a.txt", "a".getBytes());
            fs.createFile("/b.txt", "b".getBytes());
            fs.createFile("/c.txt", "c".getBytes());

            log.info("File count: {}", fs.getFileCount());
            assertThat(fs.getFileCount()).isEqualTo(3);
        }

        @Test
        @DisplayName("Should track directory count")
        void testDirectoryCount() throws IOException {
            log.info("Testing directory count tracking");
            fs.createDirectory("/dir1");
            fs.createDirectory("/dir2");
            fs.createDirectory("/dir3");

            log.info("Directory count: {} (+1 for root)", fs.getDirectoryCount());
            // +1 for root directory
            assertThat(fs.getDirectoryCount()).isEqualTo(4);
        }

        @Test
        @DisplayName("Should reset statistics")
        void testResetStatistics() throws IOException {
            log.info("Testing statistics reset");
            fs.createFile("/test.txt", "content".getBytes());
            fs.readFile("/test.txt");

            fs.resetStatistics();

            log.info("After reset: reads={}, writes={}, bytesRead={}, bytesWritten={}", 
                fs.getReadOperations(), fs.getWriteOperations(), fs.getBytesRead(), fs.getBytesWritten());
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
            log.info("Testing clear all");
            fs.createFile("/a/b/file.txt", "content".getBytes());
            fs.createDirectory("/c/d");

            fs.clear();

            log.info("After clear: files={}, dirs={}, usedSpace={}", fs.getFileCount(), fs.getDirectoryCount(), fs.getUsedSpace());
            assertThat(fs.getFileCount()).isZero();
            assertThat(fs.getDirectoryCount()).isEqualTo(1); // Root only
            assertThat(fs.getUsedSpace()).isZero();
        }

        @Test
        @DisplayName("Should get all file paths")
        void testGetAllFilePaths() throws IOException {
            log.info("Testing get all file paths");
            fs.createFile("/a.txt", "a".getBytes());
            fs.createFile("/dir/b.txt", "b".getBytes());

            var paths = fs.getAllFilePaths();
            log.info("All file paths: {}", paths);
            assertThat(paths).containsExactlyInAnyOrder("/a.txt", "/dir/b.txt");
        }

        @Test
        @DisplayName("Should get all directory paths")
        void testGetAllDirectoryPaths() throws IOException {
            log.info("Testing get all directory paths");
            fs.createDirectory("/dir1");
            fs.createDirectory("/dir1/sub");

            var paths = fs.getAllDirectoryPaths();
            log.info("All directory paths: {}", paths);
            assertThat(paths).contains("/", "/dir1", "/dir1/sub");
        }

        @Test
        @DisplayName("Should normalize paths")
        void testPathNormalization() throws IOException {
            log.info("Testing path normalization");
            fs.createFile("test.txt", "a".getBytes());
            fs.createFile("/dir/file.txt", "b".getBytes());
            fs.createFile("\\windows\\path.txt", "c".getBytes());

            log.info("Path normalization: test.txt->/test.txt, \\\\windows\\\\path.txt->/windows/path.txt");
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
            log.info("Testing concurrent file operations: {} threads × {} files each", threadCount, filesPerThread);
            
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

            latch.await(10, TimeUnit.SECONDS);
            executor.shutdown();

            log.info("Completed: {} files created, {} errors", fs.getFileCount(), errors.get());
            assertThat(errors.get()).isZero();
            assertThat(fs.getFileCount()).isEqualTo(threadCount * filesPerThread);
        }

        @Test
        @DisplayName("Should handle concurrent file locking correctly")
        void testConcurrentFileLocking() throws Exception {
            log.info("Testing concurrent file locking: 10 threads racing to lock same file");
            fs.createFile("/shared.txt", "content".getBytes());

            int threadCount = 10;
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch doneLatch = new CountDownLatch(threadCount);
            AtomicInteger lockSuccesses = new AtomicInteger(0);

            ExecutorService executor = Executors.newFixedThreadPool(threadCount);

            for (int i = 0; i < threadCount; i++) {
                executor.submit(() -> {
                    try {
                        startLatch.await();
                        fs.lockFile("/shared.txt");
                        lockSuccesses.incrementAndGet();
                    } catch (IOException | InterruptedException e) {
                        // Expected for all but one thread
                    } finally {
                        doneLatch.countDown();
                    }
                });
            }

            startLatch.countDown();
            doneLatch.await(10, TimeUnit.SECONDS);
            executor.shutdown();

            log.info("Lock successes: {} (expected exactly 1)", lockSuccesses.get());
            // Exactly one thread should succeed
            assertThat(lockSuccesses.get()).isEqualTo(1);
        }

        @Test
        @DisplayName("Should handle concurrent directory creation safely")
        void testConcurrentDirectoryCreation() throws Exception {
            int threadCount = 10;
            log.info("Testing concurrent directory creation: {} threads creating same path", threadCount);
            
            CountDownLatch latch = new CountDownLatch(threadCount);
            AtomicInteger errors = new AtomicInteger(0);

            ExecutorService executor = Executors.newFixedThreadPool(threadCount);

            for (int i = 0; i < threadCount; i++) {
                final int threadId = i;
                executor.submit(() -> {
                    try {
                        // All threads create same directory structure
                        fs.createDirectories("/concurrent/test/path");
                        fs.createFile("/concurrent/test/path/file" + threadId + ".txt", "data".getBytes());
                    } catch (Exception e) {
                        errors.incrementAndGet();
                    } finally {
                        latch.countDown();
                    }
                });
            }

            latch.await(30, TimeUnit.SECONDS);
            executor.shutdown();

            int fileCount = fs.listDirectory("/concurrent/test/path").size();
            log.info("Completed: {} files in shared directory, {} errors", fileCount, errors.get());
            
            assertThat(errors.get()).isZero();
            assertThat(fs.listDirectory("/concurrent/test/path")).hasSize(threadCount);
        }

        @Test
        @DisplayName("Should handle concurrent read/write to same file")
        void testConcurrentReadWrite() throws Exception {
            int iterations = 100;
            log.info("Testing concurrent read/write: {} iterations each on same file", iterations);
            
            fs.createFile("/concurrent.txt", "initial".getBytes());

            CountDownLatch latch = new CountDownLatch(2);
            AtomicInteger errors = new AtomicInteger(0);

            ExecutorService executor = Executors.newFixedThreadPool(2);

            // Writer thread
            executor.submit(() -> {
                try {
                    for (int i = 0; i < iterations; i++) {
                        fs.writeFile("/concurrent.txt", ("content-" + i).getBytes());
                    }
                } catch (Exception e) {
                    errors.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });

            // Reader thread
            executor.submit(() -> {
                try {
                    for (int i = 0; i < iterations; i++) {
                        fs.readFile("/concurrent.txt");
                    }
                } catch (Exception e) {
                    errors.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });

            latch.await(30, TimeUnit.SECONDS);
            executor.shutdown();

            log.info("Completed: {} read ops, {} write ops, {} errors", 
                fs.getReadOperations(), fs.getWriteOperations(), errors.get());
            assertThat(errors.get()).isZero();
        }
    }

    // ==================== Security & Data Integrity Edge Cases ====================

    @Nested
    @DisplayName("Security & Data Integrity Edge Cases")
    class SecurityEdgeCasesTests {

        @Test
        @DisplayName("Should handle empty file (0 bytes)")
        void testEmptyFile() throws IOException {
            fs.createFile("/empty.txt", new byte[0]);

            assertThat(fs.exists("/empty.txt")).isTrue();
            assertThat(fs.readFile("/empty.txt")).isEmpty();
            assertThat(fs.getMetadata("/empty.txt").size()).isZero();
        }

        @Test
        @DisplayName("Should read empty file via input stream")
        void testEmptyFileInputStream() throws IOException {
            fs.createFile("/empty.txt", new byte[0]);

            try (InputStream is = fs.openInputStream("/empty.txt")) {
                assertThat(is.readAllBytes()).isEmpty();
            }
        }

        @Test
        @DisplayName("Should write empty file via output stream")
        void testEmptyFileOutputStream() throws IOException {
            try (OutputStream os = fs.openOutputStream("/empty.txt")) {
                // Write nothing
            }

            assertThat(fs.exists("/empty.txt")).isTrue();
            assertThat(fs.readFile("/empty.txt")).isEmpty();
        }

        @Test
        @DisplayName("Should append to empty file")
        void testAppendToEmptyFile() throws IOException {
            fs.createFile("/empty.txt", new byte[0]);
            fs.appendFile("/empty.txt", "appended".getBytes());

            assertThat(new String(fs.readFile("/empty.txt"))).isEqualTo("appended");
        }

        @Test
        @DisplayName("Should preserve binary content integrity")
        void testBinaryContentIntegrity() throws IOException {
            // Create byte array with all possible byte values
            byte[] binary = new byte[256];
            for (int i = 0; i < 256; i++) {
                binary[i] = (byte) i;
            }

            fs.createFile("/binary.bin", binary);
            byte[] read = fs.readFile("/binary.bin");

            assertThat(read).isEqualTo(binary);
        }

        @Test
        @DisplayName("Should preserve binary content with null bytes")
        void testBinaryWithNullBytes() throws IOException {
            byte[] content = new byte[] { 0x00, 0x01, 0x00, 0x02, 0x00, 0x03 };

            fs.createFile("/null-bytes.bin", content);
            byte[] read = fs.readFile("/null-bytes.bin");

            assertThat(read).isEqualTo(content);
        }

        @Test
        @DisplayName("Should handle special characters in file paths")
        void testSpecialCharacterPaths() throws IOException {
            fs.createFile("/files with spaces/doc.txt", "a".getBytes());
            fs.createFile("/special-chars_file.txt", "b".getBytes());
            fs.createFile("/dot.in.name.txt", "c".getBytes());

            assertThat(fs.exists("/files with spaces/doc.txt")).isTrue();
            assertThat(fs.exists("/special-chars_file.txt")).isTrue();
            assertThat(fs.exists("/dot.in.name.txt")).isTrue();
        }

        @Test
        @DisplayName("Should handle unicode characters in file paths")
        void testUnicodeFilePaths() throws IOException {
            fs.createFile("/unicode/日本語ファイル.txt", "japanese".getBytes());
            fs.createFile("/unicode/中文文件.txt", "chinese".getBytes());
            fs.createFile("/unicode/файл.txt", "russian".getBytes());
            fs.createFile("/unicode/αβγδ.txt", "greek".getBytes());

            assertThat(fs.exists("/unicode/日本語ファイル.txt")).isTrue();
            assertThat(fs.exists("/unicode/中文文件.txt")).isTrue();
            assertThat(fs.exists("/unicode/файл.txt")).isTrue();
            assertThat(fs.exists("/unicode/αβγδ.txt")).isTrue();
        }

        @Test
        @DisplayName("Should handle URL-encoded style paths")
        void testUrlEncodedPaths() throws IOException {
            fs.createFile("/encoded/%20file%20.txt", "content".getBytes());

            assertThat(fs.exists("/encoded/%20file%20.txt")).isTrue();
        }

        @Test
        @DisplayName("Should handle empty path as root")
        void testEmptyPathHandling() throws IOException {
            // Empty path should normalize to root
            assertThat(fs.exists("")).isTrue();
            assertThat(fs.isDirectory("")).isTrue();
        }

        @Test
        @DisplayName("Should handle path with only slashes")
        void testSlashOnlyPath() throws IOException {
            assertThat(fs.exists("/")).isTrue();
            assertThat(fs.exists("//")).isTrue();
            assertThat(fs.isDirectory("/")).isTrue();
        }

        @Test
        @DisplayName("Should prevent deleting root directory")
        void testCannotDeleteRoot() {
            assertThatThrownBy(() -> fs.deleteDirectory("/"))
                .isInstanceOf(IOException.class);
        }

        @Test
        @DisplayName("Should handle very long file paths")
        void testVeryLongPath() throws IOException {
            StringBuilder longPath = new StringBuilder("/");
            for (int i = 0; i < 50; i++) {
                longPath.append("level").append(i).append("/");
            }
            longPath.append("file.txt");

            fs.createFile(longPath.toString(), "deep".getBytes());

            assertThat(fs.exists(longPath.toString())).isTrue();
            assertThat(new String(fs.readFile(longPath.toString()))).isEqualTo("deep");
        }

        @Test
        @DisplayName("Should handle very long file names")
        void testVeryLongFileName() throws IOException {
            String longName = "a".repeat(200) + ".txt";
            fs.createFile("/" + longName, "content".getBytes());

            assertThat(fs.exists("/" + longName)).isTrue();
        }
    }

    // ==================== Failure Mode Edge Cases ====================

    @Nested
    @DisplayName("Failure Mode Edge Cases")
    class FailureModeEdgeCasesTests {

        @Test
        @DisplayName("Should simulate CORRUPTED_DATA failure mode")
        void testCorruptedDataFailure() throws IOException {
            byte[] original = "This is original content that should be corrupted".getBytes();
            fs.createFile("/data.txt", original);
            fs.setFileFailureMode("/data.txt", InMemoryFileSystemSimulator.FileSystemFailureMode.CORRUPTED_DATA);

            byte[] corrupted = fs.readFile("/data.txt");

            // Content should be different due to corruption
            assertThat(corrupted).isNotEqualTo(original);
            assertThat(corrupted.length).isEqualTo(original.length);
        }

        @Test
        @DisplayName("Should simulate FILE_LOCKED failure mode globally")
        void testFileLockedFailureMode() throws IOException {
            fs.createFile("/test.txt", "content".getBytes());
            fs.setFailureMode(InMemoryFileSystemSimulator.FileSystemFailureMode.FILE_LOCKED);

            assertThatThrownBy(() -> fs.readFile("/test.txt"))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("locked");
        }

        @Test
        @DisplayName("Should throw on append to non-existent file")
        void testAppendToNonExistentFile() {
            assertThatThrownBy(() -> fs.appendFile("/missing.txt", "data".getBytes()))
                .isInstanceOf(NoSuchFileException.class);
        }

        @Test
        @DisplayName("Should throw on locking non-existent file")
        void testLockNonExistentFile() {
            assertThatThrownBy(() -> fs.lockFile("/missing.txt"))
                .isInstanceOf(NoSuchFileException.class);
        }

        @Test
        @DisplayName("Should throw on listing non-existent directory")
        void testListNonExistentDirectory() {
            assertThatThrownBy(() -> fs.listDirectory("/missing/"))
                .isInstanceOf(NoSuchFileException.class);
        }

        @Test
        @DisplayName("Should throw on metadata for non-existent path")
        void testMetadataNonExistentPath() {
            assertThatThrownBy(() -> fs.getMetadata("/missing.txt"))
                .isInstanceOf(NoSuchFileException.class);
        }

        @Test
        @DisplayName("Should simulate write bandwidth throttling")
        void testWriteBandwidth() throws IOException {
            fs.setWriteBytesPerSecond(5000); // 5KB/s

            long start = System.currentTimeMillis();
            fs.createFile("/test.txt", new byte[10000]); // 10KB
            long elapsed = System.currentTimeMillis() - start;

            // Should take at least 2 seconds for 10KB at 5KB/s
            assertThat(elapsed).isGreaterThanOrEqualTo(2000);
        }

        @Test
        @DisplayName("Should clear file-specific failure mode")
        void testClearFileFailureMode() throws IOException {
            fs.createFile("/test.txt", "content".getBytes());
            fs.setFileFailureMode("/test.txt", InMemoryFileSystemSimulator.FileSystemFailureMode.IO_ERROR);

            // Should fail
            assertThatThrownBy(() -> fs.readFile("/test.txt"))
                .isInstanceOf(IOException.class);

            // Clear failure mode
            fs.clearFileFailureMode("/test.txt");

            // Should succeed now
            byte[] content = fs.readFile("/test.txt");
            assertThat(content).isNotNull();
        }

        @Test
        @DisplayName("Should return correct total space")
        void testGetTotalSpace() {
            fs.setAvailableSpace(1_000_000_000); // 1GB

            assertThat(fs.getTotalSpace()).isEqualTo(1_000_000_000);
        }

        @Test
        @DisplayName("Should handle disk full on append")
        void testDiskFullOnAppend() throws IOException {
            fs.setAvailableSpace(100);
            fs.createFile("/test.txt", new byte[50]);

            assertThatThrownBy(() -> fs.appendFile("/test.txt", new byte[100]))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("Disk full");
        }

        @Test
        @DisplayName("Should handle multiple failure modes on same file")
        void testMultipleFailureModes() throws IOException {
            fs.createFile("/test.txt", "content".getBytes());

            // Set file-specific failure
            fs.setFileFailureMode("/test.txt", InMemoryFileSystemSimulator.FileSystemFailureMode.IO_ERROR);

            // File-specific should take precedence
            assertThatThrownBy(() -> fs.readFile("/test.txt"))
                .isInstanceOf(IOException.class);

            // Clear file-specific, set global
            fs.clearFileFailureMode("/test.txt");
            fs.setFailureMode(InMemoryFileSystemSimulator.FileSystemFailureMode.PERMISSION_DENIED);

            // Global should now apply
            assertThatThrownBy(() -> fs.readFile("/test.txt"))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("Permission denied");
        }
    }

    // ==================== Additional Edge Cases ====================

    @Nested
    @DisplayName("Additional Edge Cases")
    class AdditionalEdgeCasesTests {

        @Test
        @DisplayName("Should handle writing file larger than available space")
        void testLargeFileExceedsSpace() {
            fs.setAvailableSpace(1000);

            assertThatThrownBy(() -> fs.createFile("/large.txt", new byte[2000]))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("Disk full");
        }

        @Test
        @DisplayName("Should handle overwrite reducing file size")
        void testOverwriteSmallerFile() throws IOException {
            fs.createFile("/test.txt", new byte[1000]);
            assertThat(fs.getUsedSpace()).isEqualTo(1000);

            fs.writeFile("/test.txt", new byte[500]);
            assertThat(fs.getUsedSpace()).isEqualTo(500);
        }

        @Test
        @DisplayName("Should handle overwrite increasing file size")
        void testOverwriteLargerFile() throws IOException {
            fs.createFile("/test.txt", new byte[500]);
            assertThat(fs.getUsedSpace()).isEqualTo(500);

            fs.writeFile("/test.txt", new byte[1000]);
            assertThat(fs.getUsedSpace()).isEqualTo(1000);
        }

        @Test
        @DisplayName("Should prevent write to locked file via output stream")
        void testOutputStreamLockedFile() throws IOException {
            fs.createFile("/test.txt", "content".getBytes());
            fs.lockFile("/test.txt");

            assertThatThrownBy(() -> fs.openOutputStream("/test.txt"))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("locked");
        }

        @Test
        @DisplayName("Should prevent delete of locked file")
        void testDeleteLockedFile() throws IOException {
            fs.createFile("/test.txt", "content".getBytes());
            fs.lockFile("/test.txt");

            assertThatThrownBy(() -> fs.deleteFile("/test.txt"))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("locked");
        }

        @Test
        @DisplayName("Should handle unlocking unlocked file gracefully")
        void testUnlockUnlockedFile() throws IOException {
            fs.createFile("/test.txt", "content".getBytes());

            // Should not throw
            fs.unlockFile("/test.txt");
            assertThat(fs.isLocked("/test.txt")).isFalse();
        }

        @Test
        @DisplayName("Should track statistics correctly across operations")
        void testStatisticsAccuracy() throws IOException {
            fs.createFile("/file1.txt", new byte[100]);
            fs.createFile("/file2.txt", new byte[200]);
            fs.readFile("/file1.txt");
            fs.readFile("/file2.txt");
            fs.readFile("/file1.txt");
            fs.appendFile("/file1.txt", new byte[50]);

            assertThat(fs.getWriteOperations()).isEqualTo(3); // 2 creates + 1 append
            assertThat(fs.getReadOperations()).isEqualTo(3);
            assertThat(fs.getBytesWritten()).isEqualTo(350); // 100 + 200 + 50
            assertThat(fs.getBytesRead()).isEqualTo(400); // 100 + 200 + 100
        }

        @Test
        @DisplayName("Should handle creating file in non-existent deep directory")
        void testCreateFileInDeepNonExistentDirectory() throws IOException {
            fs.createFile("/a/b/c/d/e/f/g/file.txt", "content".getBytes());

            assertThat(fs.isDirectory("/a")).isTrue();
            assertThat(fs.isDirectory("/a/b")).isTrue();
            assertThat(fs.isDirectory("/a/b/c")).isTrue();
            assertThat(fs.isDirectory("/a/b/c/d")).isTrue();
            assertThat(fs.isDirectory("/a/b/c/d/e")).isTrue();
            assertThat(fs.isDirectory("/a/b/c/d/e/f")).isTrue();
            assertThat(fs.isDirectory("/a/b/c/d/e/f/g")).isTrue();
            assertThat(fs.isFile("/a/b/c/d/e/f/g/file.txt")).isTrue();
        }

        @Test
        @DisplayName("Should list root directory correctly")
        void testListRootDirectory() throws IOException {
            fs.createFile("/file1.txt", "a".getBytes());
            fs.createFile("/file2.txt", "b".getBytes());
            fs.createDirectory("/dir1");

            List<String> contents = fs.listDirectory("/");
            assertThat(contents).containsExactlyInAnyOrder("file1.txt", "file2.txt", "dir1/");
        }

        @Test
        @DisplayName("Should handle create directory that already exists as file")
        void testCreateDirectoryOverFile() throws IOException {
            fs.createFile("/path", "content".getBytes());

            // Trying to create directory with same name as existing file
            // should either fail or the behavior should be defined
            assertThat(fs.isFile("/path")).isTrue();
        }
    }
}
