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


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
/**
 * Description for FileManager
 *
 * @author Mark Andrew Ray-Smith Cityline Ltd
 * @version 1.0
 * @since 2025-08-17
 */

public class FileManager {
    private static final Logger logger = LoggerFactory.getLogger(FileManager.class);
    
    public static void ensureDirectoryExists(Path filePath) throws IOException {
        logger.debug("ensureDirectoryExists: checking path={}", filePath);
        Path parentDir = filePath.getParent();
        if (parentDir != null && !Files.exists(parentDir)) {
            Files.createDirectories(parentDir);
            logger.info("Created directory: {}", parentDir);
            logger.debug("ensureDirectoryExists: directory created successfully");
        } else {
            logger.debug("ensureDirectoryExists: directory already exists or parent is null");
        }
    }
    
    public static boolean isFileAccessible(Path filePath) {
        logger.debug("isFileAccessible: checking path={}", filePath);
        boolean exists = Files.exists(filePath);
        boolean readable = Files.isReadable(filePath);
        boolean regularFile = Files.isRegularFile(filePath);
        boolean result = exists && readable && regularFile;
        logger.debug("isFileAccessible: exists={}, readable={}, regularFile={}, result={}", 
            exists, readable, regularFile, result);
        return result;
    }
    
    public static boolean isDirectoryWritable(Path dirPath) {
        logger.debug("isDirectoryWritable: checking path={}", dirPath);
        boolean exists = Files.exists(dirPath);
        boolean isDir = Files.isDirectory(dirPath);
        boolean writable = Files.isWritable(dirPath);
        boolean result = exists && isDir && writable;
        logger.debug("isDirectoryWritable: exists={}, isDirectory={}, writable={}, result={}", 
            exists, isDir, writable, result);
        return result;
    }
    
    public static long getFileSize(Path filePath) {
        logger.debug("getFileSize: getting size for path={}", filePath);
        try {
            long size = Files.size(filePath);
            logger.debug("getFileSize: size={} bytes", size);
            return size;
        } catch (IOException e) {
            logger.warn("Could not get size for file: {} - {}", filePath, e.getMessage());
            return -1;
        }
    }
    
    public static Instant getLastModifiedTime(Path filePath) {
        logger.debug("getLastModifiedTime: getting modified time for path={}", filePath);
        try {
            Instant modified = Files.getLastModifiedTime(filePath).toInstant();
            logger.debug("getLastModifiedTime: modified={}", modified);
            return modified;
        } catch (IOException e) {
            logger.warn("Could not get last modified time for file: {} - {}", filePath, e.getMessage());
            return null;
        }
    }
    
    public static Path createTempFile(Path targetFile, String suffix) throws IOException {
        logger.debug("createTempFile: creating temp file for target={}, suffix={}", targetFile, suffix);
        Path parentDir = targetFile.getParent();
        if (parentDir == null) {
            parentDir = Paths.get(".");
            logger.debug("createTempFile: using current directory as parent");
        }
        
        ensureDirectoryExists(parentDir.resolve("dummy")); // Ensure parent exists
        
        String fileName = targetFile.getFileName().toString();
        String baseName = fileName.contains(".") ? 
                fileName.substring(0, fileName.lastIndexOf('.')) : fileName;
        
        Path tempFile = Files.createTempFile(parentDir, baseName + "_", suffix);
        logger.debug("createTempFile: created temp file at {}", tempFile);
        return tempFile;
    }
    
    public static boolean deleteFile(Path filePath) {
        logger.debug("deleteFile: attempting to delete path={}", filePath);
        try {
            boolean deleted = Files.deleteIfExists(filePath);
            if (deleted) {
                logger.info("Deleted file: {}", filePath);
            } else {
                logger.debug("deleteFile: file did not exist, nothing to delete");
            }
            return deleted;
        } catch (IOException e) {
            logger.warn("Failed to delete file: {} - {}", filePath, e.getMessage());
            return false;
        }
    }
    
    public static void moveFile(Path source, Path target) throws IOException {
        logger.debug("moveFile: moving from {} to {}", source, target);
        ensureDirectoryExists(target);
        Files.move(source, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        logger.info("Moved file from {} to {}", source, target);
        logger.debug("moveFile: move completed successfully");
    }
    
    public static void copyFile(Path source, Path target) throws IOException {
        logger.debug("copyFile: copying from {} to {}", source, target);
        ensureDirectoryExists(target);
        Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
        logger.info("Copied file from {} to {}", source, target);
        logger.debug("copyFile: copy completed successfully");
    }
    
    public static boolean isPathSafe(Path path) {
        logger.debug("isPathSafe: checking path={}", path);
        if (path == null) {
            logger.debug("isPathSafe: path is null, returning false");
            return false;
        }
        
        String pathString = path.normalize().toString();
        logger.debug("isPathSafe: normalized path={}", pathString);
        
        // Check for path traversal attempts
        if (pathString.contains("..") || pathString.contains("~")) {
            logger.warn("Potentially unsafe path detected: {}", pathString);
            return false;
        }
        
        logger.debug("isPathSafe: path is safe");
        return true;
    }
    
    public static long getAvailableSpace(Path path) {
        logger.debug("getAvailableSpace: checking path={}", path);
        try {
            FileStore store = Files.getFileStore(path.getParent() != null ? path.getParent() : path);
            long space = store.getUsableSpace();
            logger.debug("getAvailableSpace: usableSpace={} bytes ({} MB)", space, space / (1024 * 1024));
            return space;
        } catch (IOException e) {
            logger.warn("Could not get available space for path: {} - {}", path, e.getMessage());
            return -1;
        }
    }
    
    public static boolean hasEnoughSpace(Path path, long requiredBytes) {
        logger.debug("hasEnoughSpace: checking path={}, requiredBytes={}", path, requiredBytes);
        long availableSpace = getAvailableSpace(path);
        boolean hasSpace = availableSpace >= 0 && availableSpace >= requiredBytes;
        logger.debug("hasEnoughSpace: availableSpace={}, hasSpace={}", availableSpace, hasSpace);
        return hasSpace;
    }
    
    public static FileInfo getFileInfo(Path filePath) {
        logger.debug("getFileInfo: getting info for path={}", filePath);
        try {
            if (!Files.exists(filePath)) {
                logger.debug("getFileInfo: file does not exist, returning null");
                return null;
            }
            
            BasicFileAttributes attrs = Files.readAttributes(filePath, BasicFileAttributes.class);
            FileInfo info = new FileInfo(
                    filePath,
                    attrs.size(),
                    attrs.creationTime().toInstant(),
                    attrs.lastModifiedTime().toInstant(),
                    attrs.isRegularFile(),
                    attrs.isDirectory()
            );
            logger.debug("getFileInfo: retrieved info={}", info);
            return info;
        } catch (IOException e) {
            logger.warn("Could not get file info for: {} - {}", filePath, e.getMessage());
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
