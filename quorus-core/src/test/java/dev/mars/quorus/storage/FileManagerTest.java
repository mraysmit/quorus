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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class FileManagerTest {

    @TempDir
    Path tempDir;

    @Test
    void testEnsureDirectoryExistsCreatesMissingParent() throws IOException {
        Path target = tempDir.resolve("a").resolve("b").resolve("file.txt");

        FileManager.ensureDirectoryExists(target);

        assertTrue(Files.exists(target.getParent()));
        assertTrue(Files.isDirectory(target.getParent()));
    }

    @Test
    void testFileAndDirectoryAccessibilityHelpers() throws IOException {
        Path file = tempDir.resolve("test.txt");
        Files.writeString(file, "data");

        assertTrue(FileManager.isFileAccessible(file));
        assertFalse(FileManager.isFileAccessible(tempDir));
        assertFalse(FileManager.isFileAccessible(tempDir.resolve("missing.txt")));

        assertTrue(FileManager.isDirectoryWritable(tempDir));
        assertFalse(FileManager.isDirectoryWritable(file));
    }

    @Test
    void testSizeAndModifiedTimeHelpers() throws IOException {
        Path file = tempDir.resolve("size.txt");
        Files.writeString(file, "12345");

        assertEquals(5L, FileManager.getFileSize(file));
        assertNotNull(FileManager.getLastModifiedTime(file));

        Path missing = tempDir.resolve("missing.bin");
        assertEquals(-1L, FileManager.getFileSize(missing));
        assertNull(FileManager.getLastModifiedTime(missing));
    }

    @Test
    void testCreateDeleteMoveAndCopyFile() throws IOException {
        Path source = tempDir.resolve("source.txt");
        Files.writeString(source, "payload");

        Path tempFile = FileManager.createTempFile(source, ".tmp");
        assertTrue(Files.exists(tempFile));
        assertTrue(tempFile.getFileName().toString().contains("source_"));

        Path copyTarget = tempDir.resolve("nested").resolve("copy.txt");
        FileManager.copyFile(source, copyTarget);
        assertEquals("payload", Files.readString(copyTarget));

        Path moveTarget = tempDir.resolve("moved").resolve("final.txt");
        FileManager.moveFile(copyTarget, moveTarget);
        assertFalse(Files.exists(copyTarget));
        assertEquals("payload", Files.readString(moveTarget));

        assertTrue(FileManager.deleteFile(moveTarget));
        assertFalse(FileManager.deleteFile(moveTarget));
    }

    @Test
    void testPathSafetyAndSpaceHelpers() {
        assertFalse(FileManager.isPathSafe(null));
        assertFalse(FileManager.isPathSafe(Path.of("..", "escape.txt")));
        assertFalse(FileManager.isPathSafe(Path.of("~", "home.txt")));
        assertTrue(FileManager.isPathSafe(Path.of("safe", "file.txt")));

        long available = FileManager.getAvailableSpace(tempDir.resolve("space-check.txt"));
        assertTrue(available > 0);
        assertTrue(FileManager.hasEnoughSpace(tempDir.resolve("space-check.txt"), 1));
    }

    @Test
    void testGetFileInfoAndInnerFileInfoToString() throws IOException {
        Path file = tempDir.resolve("info.txt");
        Files.writeString(file, "abc");

        FileManager.FileInfo info = FileManager.getFileInfo(file);
        assertNotNull(info);
        assertEquals(file, info.getPath());
        assertEquals(3L, info.getSize());
        assertTrue(info.isFile());
        assertFalse(info.isDirectory());
        assertNotNull(info.getCreationTime());
        assertNotNull(info.getLastModifiedTime());
        assertTrue(info.toString().contains("FileInfo"));

        assertNull(FileManager.getFileInfo(tempDir.resolve("does-not-exist")));
    }
}
