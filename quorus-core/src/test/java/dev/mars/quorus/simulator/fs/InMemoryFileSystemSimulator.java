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

import java.io.*;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.NoSuchFileException;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * In-memory file system simulator for testing file transfer operations.
 * 
 * <p>This simulator provides a virtual file system that operates entirely in memory,
 * enabling fast, deterministic testing without touching real disk. It supports:
 * <ul>
 *   <li>File and directory operations (create, read, write, delete, list)</li>
 *   <li>File metadata (size, timestamps, permissions)</li>
 *   <li>Space management (configurable available space)</li>
 *   <li>I/O performance simulation (configurable delays and throughput)</li>
 *   <li>Chaos engineering (failure injection, corruption simulation)</li>
 * </ul>
 * 
 * <h2>Usage Example:</h2>
 * <pre>{@code
 * InMemoryFileSystemSimulator fs = new InMemoryFileSystemSimulator();
 * 
 * // Create files
 * fs.createFile("/data/test.txt", "Hello, World!".getBytes());
 * 
 * // Read files
 * byte[] content = fs.readFile("/data/test.txt");
 * 
 * // Simulate disk full
 * fs.setAvailableSpace(1000);
 * fs.setFailureMode(FileSystemFailureMode.DISK_FULL);
 * }</pre>
 * 
 * @author Mark Andrew Ray-Smith
 * @since 1.0
 */
public class InMemoryFileSystemSimulator {

    // Virtual file system storage
    private final Map<String, VirtualFile> files = new ConcurrentHashMap<>();
    private final Map<String, VirtualDirectory> directories = new ConcurrentHashMap<>();
    private final Set<String> lockedFiles = ConcurrentHashMap.newKeySet();
    
    // Space management
    private final AtomicLong availableSpace = new AtomicLong(Long.MAX_VALUE);
    private final AtomicLong usedSpace = new AtomicLong(0);
    
    // Performance simulation
    private volatile long readDelayMs = 0;
    private volatile long writeDelayMs = 0;
    private volatile long readBytesPerSecond = Long.MAX_VALUE;
    private volatile long writeBytesPerSecond = Long.MAX_VALUE;
    
    // Chaos engineering
    private volatile FileSystemFailureMode failureMode = FileSystemFailureMode.NONE;
    private volatile double failureRate = 0.0;
    private final Map<String, FileSystemFailureMode> fileSpecificFailures = new ConcurrentHashMap<>();
    
    // Statistics
    private final AtomicLong readOperations = new AtomicLong(0);
    private final AtomicLong writeOperations = new AtomicLong(0);
    private final AtomicLong bytesRead = new AtomicLong(0);
    private final AtomicLong bytesWritten = new AtomicLong(0);

    /**
     * Failure modes for chaos engineering.
     */
    public enum FileSystemFailureMode {
        /** Normal operation */
        NONE,
        /** Disk is full - writes fail */
        DISK_FULL,
        /** File system is read-only */
        READ_ONLY,
        /** Permission denied for operation */
        PERMISSION_DENIED,
        /** Generic I/O error */
        IO_ERROR,
        /** File is locked by another process */
        FILE_LOCKED,
        /** Data corruption on read */
        CORRUPTED_DATA,
        /** Random failures based on failure rate */
        RANDOM_FAILURE
    }

    /**
     * Creates a new in-memory file system simulator with default settings.
     */
    public InMemoryFileSystemSimulator() {
        // Create root directory
        directories.put("/", new VirtualDirectory("/", Instant.now()));
    }

    // ==================== File Operations ====================

    /**
     * Creates a file with the specified content.
     *
     * @param path the file path
     * @param content the file content
     * @throws IOException if the operation fails
     */
    public void createFile(String path, byte[] content) throws IOException {
        String normalizedPath = normalizePath(path);
        checkFailure(normalizedPath);
        checkWriteAccess();
        
        if (files.containsKey(normalizedPath)) {
            throw new FileAlreadyExistsException(normalizedPath);
        }
        
        // Check space
        if (content.length > availableSpace.get() - usedSpace.get()) {
            throw new IOException("Disk full: not enough space for " + content.length + " bytes");
        }
        
        // Create parent directories
        createParentDirectories(normalizedPath);
        
        // Simulate write delay
        simulateWriteDelay(content.length);
        
        // Create file
        VirtualFile file = new VirtualFile(normalizedPath, content);
        files.put(normalizedPath, file);
        usedSpace.addAndGet(content.length);
        
        // Update statistics
        writeOperations.incrementAndGet();
        bytesWritten.addAndGet(content.length);
    }

    /**
     * Reads the content of a file.
     *
     * @param path the file path
     * @return the file content
     * @throws IOException if the file doesn't exist or read fails
     */
    public byte[] readFile(String path) throws IOException {
        String normalizedPath = normalizePath(path);
        checkFailure(normalizedPath);
        
        VirtualFile file = files.get(normalizedPath);
        if (file == null) {
            throw new NoSuchFileException(normalizedPath);
        }
        
        if (lockedFiles.contains(normalizedPath)) {
            throw new IOException("File locked: " + normalizedPath);
        }
        
        // Simulate read delay
        simulateReadDelay(file.content.length);
        
        // Check for corruption
        if (failureMode == FileSystemFailureMode.CORRUPTED_DATA ||
            fileSpecificFailures.get(normalizedPath) == FileSystemFailureMode.CORRUPTED_DATA) {
            return corruptData(file.content.clone());
        }
        
        // Update statistics
        readOperations.incrementAndGet();
        bytesRead.addAndGet(file.content.length);
        
        file.lastAccessed = Instant.now();
        return file.content.clone();
    }

    /**
     * Writes content to a file, creating or overwriting as needed.
     *
     * @param path the file path
     * @param content the content to write
     * @throws IOException if the operation fails
     */
    public void writeFile(String path, byte[] content) throws IOException {
        String normalizedPath = normalizePath(path);
        checkFailure(normalizedPath);
        checkWriteAccess();
        
        if (lockedFiles.contains(normalizedPath)) {
            throw new IOException("File locked: " + normalizedPath);
        }
        
        VirtualFile existingFile = files.get(normalizedPath);
        long additionalSpace = content.length - (existingFile != null ? existingFile.content.length : 0);
        
        if (additionalSpace > availableSpace.get() - usedSpace.get()) {
            throw new IOException("Disk full: not enough space");
        }
        
        createParentDirectories(normalizedPath);
        simulateWriteDelay(content.length);
        
        if (existingFile != null) {
            usedSpace.addAndGet(-existingFile.content.length);
        }
        
        VirtualFile file = new VirtualFile(normalizedPath, content);
        files.put(normalizedPath, file);
        usedSpace.addAndGet(content.length);
        
        writeOperations.incrementAndGet();
        bytesWritten.addAndGet(content.length);
    }

    /**
     * Appends content to an existing file.
     *
     * @param path the file path
     * @param content the content to append
     * @throws IOException if the operation fails
     */
    public void appendFile(String path, byte[] content) throws IOException {
        String normalizedPath = normalizePath(path);
        checkFailure(normalizedPath);
        checkWriteAccess();
        
        VirtualFile file = files.get(normalizedPath);
        if (file == null) {
            throw new NoSuchFileException(normalizedPath);
        }
        
        if (lockedFiles.contains(normalizedPath)) {
            throw new IOException("File locked: " + normalizedPath);
        }
        
        if (content.length > availableSpace.get() - usedSpace.get()) {
            throw new IOException("Disk full: not enough space");
        }
        
        simulateWriteDelay(content.length);
        
        byte[] newContent = new byte[file.content.length + content.length];
        System.arraycopy(file.content, 0, newContent, 0, file.content.length);
        System.arraycopy(content, 0, newContent, file.content.length, content.length);
        
        file.content = newContent;
        file.modified = Instant.now();
        usedSpace.addAndGet(content.length);
        
        writeOperations.incrementAndGet();
        bytesWritten.addAndGet(content.length);
    }

    /**
     * Deletes a file.
     *
     * @param path the file path
     * @throws IOException if the file doesn't exist or delete fails
     */
    public void deleteFile(String path) throws IOException {
        String normalizedPath = normalizePath(path);
        checkFailure(normalizedPath);
        checkWriteAccess();
        
        if (lockedFiles.contains(normalizedPath)) {
            throw new IOException("File locked: " + normalizedPath);
        }
        
        VirtualFile file = files.remove(normalizedPath);
        if (file == null) {
            throw new NoSuchFileException(normalizedPath);
        }
        
        usedSpace.addAndGet(-file.content.length);
    }

    /**
     * Checks if a file or directory exists.
     *
     * @param path the path to check
     * @return true if the path exists
     */
    public boolean exists(String path) {
        String normalizedPath = normalizePath(path);
        return files.containsKey(normalizedPath) || directories.containsKey(normalizedPath);
    }

    /**
     * Checks if a path is a file.
     *
     * @param path the path to check
     * @return true if the path is a file
     */
    public boolean isFile(String path) {
        return files.containsKey(normalizePath(path));
    }

    /**
     * Checks if a path is a directory.
     *
     * @param path the path to check
     * @return true if the path is a directory
     */
    public boolean isDirectory(String path) {
        return directories.containsKey(normalizePath(path));
    }

    /**
     * Opens an input stream for reading a file.
     *
     * @param path the file path
     * @return an input stream
     * @throws IOException if the file doesn't exist
     */
    public InputStream openInputStream(String path) throws IOException {
        byte[] content = readFile(path);
        return new ByteArrayInputStream(content);
    }

    /**
     * Opens an output stream for writing to a file.
     *
     * @param path the file path
     * @return an output stream
     * @throws IOException if the operation fails
     */
    public OutputStream openOutputStream(String path) throws IOException {
        String normalizedPath = normalizePath(path);
        checkFailure(normalizedPath);
        checkWriteAccess();
        
        if (lockedFiles.contains(normalizedPath)) {
            throw new IOException("File locked: " + normalizedPath);
        }
        
        return new VirtualFileOutputStream(normalizedPath);
    }

    // ==================== Directory Operations ====================

    /**
     * Creates a directory.
     *
     * @param path the directory path
     * @throws IOException if the operation fails
     */
    public void createDirectory(String path) throws IOException {
        String normalizedPath = normalizePath(path);
        checkFailure(normalizedPath);
        checkWriteAccess();
        
        if (directories.containsKey(normalizedPath)) {
            throw new FileAlreadyExistsException(normalizedPath);
        }
        
        createParentDirectories(normalizedPath);
        directories.put(normalizedPath, new VirtualDirectory(normalizedPath, Instant.now()));
    }

    /**
     * Creates a directory and all parent directories.
     *
     * @param path the directory path
     * @throws IOException if the operation fails
     */
    public void createDirectories(String path) throws IOException {
        String normalizedPath = normalizePath(path);
        checkFailure(normalizedPath);
        checkWriteAccess();
        createParentDirectories(normalizedPath);
        if (!directories.containsKey(normalizedPath)) {
            directories.put(normalizedPath, new VirtualDirectory(normalizedPath, Instant.now()));
        }
    }

    /**
     * Lists the contents of a directory.
     *
     * @param path the directory path
     * @return list of file/directory names in the directory
     * @throws IOException if the directory doesn't exist
     */
    public List<String> listDirectory(String path) throws IOException {
        String normalizedPath = normalizePath(path);
        checkFailure(normalizedPath);
        
        if (!directories.containsKey(normalizedPath)) {
            throw new NoSuchFileException("Directory not found: " + normalizedPath);
        }
        
        String prefix = normalizedPath.equals("/") ? "/" : normalizedPath + "/";
        
        Set<String> entries = new HashSet<>();
        
        // Find files in this directory
        for (String filePath : files.keySet()) {
            if (filePath.startsWith(prefix)) {
                String relativePath = filePath.substring(prefix.length());
                int slashIndex = relativePath.indexOf('/');
                if (slashIndex == -1) {
                    entries.add(relativePath);
                } else {
                    entries.add(relativePath.substring(0, slashIndex) + "/");
                }
            }
        }
        
        // Find subdirectories
        for (String dirPath : directories.keySet()) {
            if (dirPath.startsWith(prefix) && !dirPath.equals(normalizedPath)) {
                String relativePath = dirPath.substring(prefix.length());
                int slashIndex = relativePath.indexOf('/');
                if (slashIndex == -1) {
                    entries.add(relativePath + "/");
                } else {
                    entries.add(relativePath.substring(0, slashIndex) + "/");
                }
            }
        }
        
        return new ArrayList<>(entries);
    }

    /**
     * Deletes a directory (must be empty).
     *
     * @param path the directory path
     * @throws IOException if the directory is not empty or doesn't exist
     */
    public void deleteDirectory(String path) throws IOException {
        String normalizedPath = normalizePath(path);
        checkFailure(normalizedPath);
        checkWriteAccess();
        
        if (!directories.containsKey(normalizedPath)) {
            throw new NoSuchFileException("Directory not found: " + normalizedPath);
        }
        
        List<String> contents = listDirectory(normalizedPath);
        if (!contents.isEmpty()) {
            throw new IOException("Directory not empty: " + normalizedPath);
        }
        
        directories.remove(normalizedPath);
    }

    // ==================== File Metadata ====================

    /**
     * Gets metadata for a file.
     *
     * @param path the file path
     * @return file metadata
     * @throws IOException if the file doesn't exist
     */
    public FileMetadata getMetadata(String path) throws IOException {
        String normalizedPath = normalizePath(path);
        
        VirtualFile file = files.get(normalizedPath);
        if (file != null) {
            return new FileMetadata(
                normalizedPath,
                file.content.length,
                file.created,
                file.modified,
                file.lastAccessed,
                file.permissions,
                file.owner,
                false
            );
        }
        
        VirtualDirectory dir = directories.get(normalizedPath);
        if (dir != null) {
            return new FileMetadata(
                normalizedPath,
                0,
                dir.created,
                dir.created,
                dir.created,
                Set.of(FilePermission.READ, FilePermission.WRITE, FilePermission.EXECUTE),
                "system",
                true
            );
        }
        
        throw new NoSuchFileException(normalizedPath);
    }

    /**
     * Sets permissions on a file.
     *
     * @param path the file path
     * @param permissions the permissions to set
     * @throws IOException if the file doesn't exist
     */
    public void setPermissions(String path, Set<FilePermission> permissions) throws IOException {
        String normalizedPath = normalizePath(path);
        VirtualFile file = files.get(normalizedPath);
        if (file == null) {
            throw new NoSuchFileException(normalizedPath);
        }
        file.permissions = new HashSet<>(permissions);
    }

    // ==================== File Locking ====================

    /**
     * Locks a file for exclusive access.
     *
     * @param path the file path
     * @throws IOException if the file is already locked or doesn't exist
     */
    public void lockFile(String path) throws IOException {
        String normalizedPath = normalizePath(path);
        if (!files.containsKey(normalizedPath)) {
            throw new NoSuchFileException(normalizedPath);
        }
        if (!lockedFiles.add(normalizedPath)) {
            throw new IOException("File already locked: " + normalizedPath);
        }
    }

    /**
     * Unlocks a file.
     *
     * @param path the file path
     */
    public void unlockFile(String path) {
        lockedFiles.remove(normalizePath(path));
    }

    /**
     * Checks if a file is locked.
     *
     * @param path the file path
     * @return true if the file is locked
     */
    public boolean isLocked(String path) {
        return lockedFiles.contains(normalizePath(path));
    }

    // ==================== Space Management ====================

    /**
     * Sets the available space in the file system.
     *
     * @param bytes available space in bytes
     */
    public void setAvailableSpace(long bytes) {
        availableSpace.set(bytes);
    }

    /**
     * Gets the available space in the file system.
     *
     * @return available space in bytes
     */
    public long getAvailableSpace() {
        return Math.max(0, availableSpace.get() - usedSpace.get());
    }

    /**
     * Gets the total space in the file system.
     *
     * @return total space in bytes
     */
    public long getTotalSpace() {
        return availableSpace.get();
    }

    /**
     * Gets the used space in the file system.
     *
     * @return used space in bytes
     */
    public long getUsedSpace() {
        return usedSpace.get();
    }

    // ==================== Performance Simulation ====================

    /**
     * Sets the read delay per operation.
     *
     * @param delayMs delay in milliseconds
     */
    public void setReadDelayMs(long delayMs) {
        this.readDelayMs = delayMs;
    }

    /**
     * Sets the write delay per operation.
     *
     * @param delayMs delay in milliseconds
     */
    public void setWriteDelayMs(long delayMs) {
        this.writeDelayMs = delayMs;
    }

    /**
     * Sets the simulated read speed.
     *
     * @param bytesPerSecond bytes per second
     */
    public void setReadBytesPerSecond(long bytesPerSecond) {
        this.readBytesPerSecond = bytesPerSecond;
    }

    /**
     * Sets the simulated write speed.
     *
     * @param bytesPerSecond bytes per second
     */
    public void setWriteBytesPerSecond(long bytesPerSecond) {
        this.writeBytesPerSecond = bytesPerSecond;
    }

    // ==================== Chaos Engineering ====================

    /**
     * Sets the global failure mode.
     *
     * @param mode the failure mode
     */
    public void setFailureMode(FileSystemFailureMode mode) {
        this.failureMode = mode;
    }

    /**
     * Sets the failure mode for a specific file.
     *
     * @param path the file path
     * @param mode the failure mode
     */
    public void setFileFailureMode(String path, FileSystemFailureMode mode) {
        fileSpecificFailures.put(normalizePath(path), mode);
    }

    /**
     * Clears the failure mode for a specific file.
     *
     * @param path the file path
     */
    public void clearFileFailureMode(String path) {
        fileSpecificFailures.remove(normalizePath(path));
    }

    /**
     * Sets the random failure rate.
     *
     * @param rate failure rate (0.0 to 1.0)
     */
    public void setFailureRate(double rate) {
        this.failureRate = Math.max(0.0, Math.min(1.0, rate));
    }

    /**
     * Resets all chaos engineering settings.
     */
    public void resetChaos() {
        failureMode = FileSystemFailureMode.NONE;
        failureRate = 0.0;
        fileSpecificFailures.clear();
    }

    // ==================== Statistics ====================

    /**
     * Gets the number of read operations performed.
     *
     * @return read operation count
     */
    public long getReadOperations() {
        return readOperations.get();
    }

    /**
     * Gets the number of write operations performed.
     *
     * @return write operation count
     */
    public long getWriteOperations() {
        return writeOperations.get();
    }

    /**
     * Gets the total bytes read.
     *
     * @return bytes read
     */
    public long getBytesRead() {
        return bytesRead.get();
    }

    /**
     * Gets the total bytes written.
     *
     * @return bytes written
     */
    public long getBytesWritten() {
        return bytesWritten.get();
    }

    /**
     * Gets the number of files in the file system.
     *
     * @return file count
     */
    public int getFileCount() {
        return files.size();
    }

    /**
     * Gets the number of directories in the file system.
     *
     * @return directory count
     */
    public int getDirectoryCount() {
        return directories.size();
    }

    /**
     * Resets statistics.
     */
    public void resetStatistics() {
        readOperations.set(0);
        writeOperations.set(0);
        bytesRead.set(0);
        bytesWritten.set(0);
    }

    // ==================== Utility Methods ====================

    /**
     * Clears all files and directories.
     */
    public void clear() {
        files.clear();
        directories.clear();
        lockedFiles.clear();
        usedSpace.set(0);
        directories.put("/", new VirtualDirectory("/", Instant.now()));
    }

    /**
     * Gets a snapshot of all file paths.
     *
     * @return set of all file paths
     */
    public Set<String> getAllFilePaths() {
        return new HashSet<>(files.keySet());
    }

    /**
     * Gets a snapshot of all directory paths.
     *
     * @return set of all directory paths
     */
    public Set<String> getAllDirectoryPaths() {
        return new HashSet<>(directories.keySet());
    }

    // ==================== Private Helper Methods ====================

    private String normalizePath(String path) {
        if (path == null || path.isEmpty()) {
            return "/";
        }
        // Convert backslashes to forward slashes
        path = path.replace('\\', '/');
        // Ensure path starts with /
        if (!path.startsWith("/")) {
            path = "/" + path;
        }
        // Remove trailing slash (except for root)
        if (path.length() > 1 && path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }
        return path;
    }

    private void createParentDirectories(String path) {
        String[] parts = path.split("/");
        StringBuilder current = new StringBuilder();
        
        for (int i = 1; i < parts.length - 1; i++) {
            current.append("/").append(parts[i]);
            String dirPath = current.toString();
            if (!directories.containsKey(dirPath)) {
                directories.put(dirPath, new VirtualDirectory(dirPath, Instant.now()));
            }
        }
    }

    private void checkFailure(String path) throws IOException {
        // Check file-specific failure
        FileSystemFailureMode specificMode = fileSpecificFailures.get(path);
        if (specificMode != null && specificMode != FileSystemFailureMode.NONE) {
            throwFailure(specificMode, path);
        }
        
        // Check global failure
        if (failureMode != FileSystemFailureMode.NONE) {
            throwFailure(failureMode, path);
        }
        
        // Check random failure
        if (failureRate > 0 && ThreadLocalRandom.current().nextDouble() < failureRate) {
            throw new IOException("Random I/O failure on: " + path);
        }
    }

    private void throwFailure(FileSystemFailureMode mode, String path) throws IOException {
        switch (mode) {
            case DISK_FULL -> throw new IOException("Disk full");
            case READ_ONLY -> throw new IOException("File system is read-only");
            case PERMISSION_DENIED -> throw new IOException("Permission denied: " + path);
            case IO_ERROR -> throw new IOException("I/O error on: " + path);
            case FILE_LOCKED -> throw new IOException("File locked: " + path);
            case CORRUPTED_DATA -> {} // Handled during read
            case RANDOM_FAILURE -> {
                if (ThreadLocalRandom.current().nextBoolean()) {
                    throw new IOException("Random failure on: " + path);
                }
            }
            default -> {}
        }
    }

    private void checkWriteAccess() throws IOException {
        if (failureMode == FileSystemFailureMode.READ_ONLY) {
            throw new IOException("File system is read-only");
        }
    }

    private void simulateReadDelay(long bytes) {
        long delay = readDelayMs;
        if (readBytesPerSecond < Long.MAX_VALUE && bytes > 0) {
            delay += (bytes * 1000) / readBytesPerSecond;
        }
        if (delay > 0) {
            try {
                Thread.sleep(delay);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private void simulateWriteDelay(long bytes) {
        long delay = writeDelayMs;
        if (writeBytesPerSecond < Long.MAX_VALUE && bytes > 0) {
            delay += (bytes * 1000) / writeBytesPerSecond;
        }
        if (delay > 0) {
            try {
                Thread.sleep(delay);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private byte[] corruptData(byte[] data) {
        if (data.length == 0) return data;
        // Corrupt 1-5% of bytes
        int corruptCount = Math.max(1, data.length / 50);
        for (int i = 0; i < corruptCount; i++) {
            int index = ThreadLocalRandom.current().nextInt(data.length);
            data[index] = (byte) ThreadLocalRandom.current().nextInt(256);
        }
        return data;
    }

    // ==================== Inner Classes ====================

    /**
     * Represents a virtual file in the simulator.
     */
    private static class VirtualFile {
        final String path;
        byte[] content;
        final Instant created;
        Instant modified;
        Instant lastAccessed;
        Set<FilePermission> permissions;
        String owner;

        VirtualFile(String path, byte[] content) {
            this.path = path;
            this.content = content.clone();
            this.created = Instant.now();
            this.modified = this.created;
            this.lastAccessed = this.created;
            this.permissions = new HashSet<>(Set.of(FilePermission.READ, FilePermission.WRITE));
            this.owner = "test-user";
        }
    }

    /**
     * Represents a virtual directory in the simulator.
     */
    private static class VirtualDirectory {
        final String path;
        final Instant created;

        VirtualDirectory(String path, Instant created) {
            this.path = path;
            this.created = created;
        }
    }

    /**
     * Output stream that writes to the virtual file system.
     */
    private class VirtualFileOutputStream extends ByteArrayOutputStream {
        private final String path;

        VirtualFileOutputStream(String path) {
            this.path = path;
        }

        @Override
        public void close() throws IOException {
            super.close();
            writeFile(path, toByteArray());
        }
    }

    /**
     * File permission types.
     */
    public enum FilePermission {
        READ, WRITE, EXECUTE
    }

    /**
     * File metadata.
     */
    public record FileMetadata(
        String path,
        long size,
        Instant created,
        Instant modified,
        Instant lastAccessed,
        Set<FilePermission> permissions,
        String owner,
        boolean isDirectory
    ) {}
}
