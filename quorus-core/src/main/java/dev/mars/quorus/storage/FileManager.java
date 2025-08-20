package dev.mars.quorus.storage;

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


import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.util.logging.Logger;

public class FileManager {
    private static final Logger logger = Logger.getLogger(FileManager.class.getName());
    
    public static void ensureDirectoryExists(Path filePath) throws IOException {
        Path parentDir = filePath.getParent();
        if (parentDir != null && !Files.exists(parentDir)) {
            Files.createDirectories(parentDir);
            logger.info("Created directory: " + parentDir);
        }
    }
    
    public static boolean isFileAccessible(Path filePath) {
        return Files.exists(filePath) && Files.isReadable(filePath) && Files.isRegularFile(filePath);
    }
    
    public static boolean isDirectoryWritable(Path dirPath) {
        return Files.exists(dirPath) && Files.isDirectory(dirPath) && Files.isWritable(dirPath);
    }
    
    public static long getFileSize(Path filePath) {
        try {
            return Files.size(filePath);
        } catch (IOException e) {
            logger.warning("Could not get size for file: " + filePath + " - " + e.getMessage());
            return -1;
        }
    }
    
    public static Instant getLastModifiedTime(Path filePath) {
        try {
            return Files.getLastModifiedTime(filePath).toInstant();
        } catch (IOException e) {
            logger.warning("Could not get last modified time for file: " + filePath + " - " + e.getMessage());
            return null;
        }
    }
    
    public static Path createTempFile(Path targetFile, String suffix) throws IOException {
        Path parentDir = targetFile.getParent();
        if (parentDir == null) {
            parentDir = Paths.get(".");
        }
        
        ensureDirectoryExists(parentDir.resolve("dummy")); // Ensure parent exists
        
        String fileName = targetFile.getFileName().toString();
        String baseName = fileName.contains(".") ? 
                fileName.substring(0, fileName.lastIndexOf('.')) : fileName;
        
        return Files.createTempFile(parentDir, baseName + "_", suffix);
    }
    
    public static boolean deleteFile(Path filePath) {
        try {
            boolean deleted = Files.deleteIfExists(filePath);
            if (deleted) {
                logger.info("Deleted file: " + filePath);
            }
            return deleted;
        } catch (IOException e) {
            logger.warning("Failed to delete file: " + filePath + " - " + e.getMessage());
            return false;
        }
    }
    
    public static void moveFile(Path source, Path target) throws IOException {
        ensureDirectoryExists(target);
        Files.move(source, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        logger.info("Moved file from " + source + " to " + target);
    }
    
    public static void copyFile(Path source, Path target) throws IOException {
        ensureDirectoryExists(target);
        Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
        logger.info("Copied file from " + source + " to " + target);
    }
    
    public static boolean isPathSafe(Path path) {
        if (path == null) {
            return false;
        }
        
        String pathString = path.normalize().toString();
        
        // Check for path traversal attempts
        if (pathString.contains("..") || pathString.contains("~")) {
            logger.warning("Potentially unsafe path detected: " + pathString);
            return false;
        }
        
        return true;
    }
    
    public static long getAvailableSpace(Path path) {
        try {
            FileStore store = Files.getFileStore(path.getParent() != null ? path.getParent() : path);
            return store.getUsableSpace();
        } catch (IOException e) {
            logger.warning("Could not get available space for path: " + path + " - " + e.getMessage());
            return -1;
        }
    }
    
    public static boolean hasEnoughSpace(Path path, long requiredBytes) {
        long availableSpace = getAvailableSpace(path);
        return availableSpace >= 0 && availableSpace >= requiredBytes;
    }
    
    public static FileInfo getFileInfo(Path filePath) {
        try {
            if (!Files.exists(filePath)) {
                return null;
            }
            
            BasicFileAttributes attrs = Files.readAttributes(filePath, BasicFileAttributes.class);
            return new FileInfo(
                    filePath,
                    attrs.size(),
                    attrs.creationTime().toInstant(),
                    attrs.lastModifiedTime().toInstant(),
                    attrs.isRegularFile(),
                    attrs.isDirectory()
            );
        } catch (IOException e) {
            logger.warning("Could not get file info for: " + filePath + " - " + e.getMessage());
            return null;
        }
    }
    
    /**
     * Simple data class for file information
     */
    public static class FileInfo {
        private final Path path;
        private final long size;
        private final Instant creationTime;
        private final Instant lastModifiedTime;
        private final boolean isFile;
        private final boolean isDirectory;
        
        public FileInfo(Path path, long size, Instant creationTime, Instant lastModifiedTime, 
                       boolean isFile, boolean isDirectory) {
            this.path = path;
            this.size = size;
            this.creationTime = creationTime;
            this.lastModifiedTime = lastModifiedTime;
            this.isFile = isFile;
            this.isDirectory = isDirectory;
        }
        
        public Path getPath() { return path; }
        public long getSize() { return size; }
        public Instant getCreationTime() { return creationTime; }
        public Instant getLastModifiedTime() { return lastModifiedTime; }
        public boolean isFile() { return isFile; }
        public boolean isDirectory() { return isDirectory; }
        
        @Override
        public String toString() {
            return "FileInfo{path=" + path + ", size=" + size + ", isFile=" + isFile + "}";
        }
    }
}
