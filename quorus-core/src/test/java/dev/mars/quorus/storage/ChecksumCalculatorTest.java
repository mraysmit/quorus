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
import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

class ChecksumCalculatorTest {
    
    @TempDir
    Path tempDir;
    
    @Test
    void testDefaultAlgorithm() {
        ChecksumCalculator calculator = new ChecksumCalculator();
        assertEquals("SHA-256", calculator.getAlgorithm());
    }
    
    @Test
    void testCustomAlgorithm() {
        ChecksumCalculator calculator = new ChecksumCalculator("MD5");
        assertEquals("MD5", calculator.getAlgorithm());
    }
    
    @Test
    void testUnsupportedAlgorithm() {
        assertThrows(RuntimeException.class, () -> new ChecksumCalculator("INVALID"));
    }
    
    @Test
    void testUpdateAndGetChecksum() {
        ChecksumCalculator calculator = new ChecksumCalculator();
        
        byte[] data = "Hello, World!".getBytes();
        calculator.update(data);
        
        String checksum = calculator.getChecksum();
        assertNotNull(checksum);
        assertFalse(checksum.isEmpty());
        assertEquals(64, checksum.length()); // SHA-256 produces 64 hex characters
    }
    
    @Test
    void testUpdateWithOffsetAndLength() {
        ChecksumCalculator calculator = new ChecksumCalculator();
        
        byte[] data = "Hello, World! Extra data".getBytes();
        calculator.update(data, 0, 13); // Only "Hello, World!"
        
        String checksum1 = calculator.getChecksum();
        
        ChecksumCalculator calculator2 = new ChecksumCalculator();
        calculator2.update("Hello, World!".getBytes());
        String checksum2 = calculator2.getChecksum();
        
        assertEquals(checksum2, checksum1);
    }
    
    @Test
    void testReset() {
        ChecksumCalculator calculator = new ChecksumCalculator();
        
        calculator.update("Hello".getBytes());
        String checksum1 = calculator.getChecksum();
        
        calculator.reset();
        calculator.update("World".getBytes());
        String checksum2 = calculator.getChecksum();
        
        assertNotEquals(checksum1, checksum2);
    }
    
    @Test
    void testCalculateFileChecksum() throws IOException {
        Path testFile = tempDir.resolve("test.txt");
        String content = "Hello, World!";
        Files.write(testFile, content.getBytes());
        
        String checksum = ChecksumCalculator.calculateFileChecksum(testFile);
        assertNotNull(checksum);
        assertFalse(checksum.isEmpty());
        
        // Verify it matches manual calculation
        ChecksumCalculator calculator = new ChecksumCalculator();
        calculator.update(content.getBytes());
        assertEquals(calculator.getChecksum(), checksum);
    }
    
    @Test
    void testCalculateFileChecksumWithAlgorithm() throws IOException {
        Path testFile = tempDir.resolve("test.txt");
        Files.write(testFile, "Hello, World!".getBytes());
        
        String sha256 = ChecksumCalculator.calculateFileChecksum(testFile, "SHA-256");
        String md5 = ChecksumCalculator.calculateFileChecksum(testFile, "MD5");
        
        assertNotEquals(sha256, md5);
        assertEquals(64, sha256.length()); // SHA-256
        assertEquals(32, md5.length());    // MD5
    }
    
    @Test
    void testVerifyFileChecksum() throws IOException {
        Path testFile = tempDir.resolve("test.txt");
        String content = "Hello, World!";
        Files.write(testFile, content.getBytes());
        
        String correctChecksum = ChecksumCalculator.calculateFileChecksum(testFile);
        String incorrectChecksum = "invalid";
        
        assertTrue(ChecksumCalculator.verifyFileChecksum(testFile, correctChecksum));
        assertFalse(ChecksumCalculator.verifyFileChecksum(testFile, incorrectChecksum));
        assertTrue(ChecksumCalculator.verifyFileChecksum(testFile, null)); // No checksum to verify
        assertTrue(ChecksumCalculator.verifyFileChecksum(testFile, "")); // Empty checksum
    }
    
    @Test
    void testIsAlgorithmSupported() {
        assertTrue(ChecksumCalculator.isAlgorithmSupported("SHA-256"));
        assertTrue(ChecksumCalculator.isAlgorithmSupported("MD5"));
        assertTrue(ChecksumCalculator.isAlgorithmSupported("SHA-1"));
        assertFalse(ChecksumCalculator.isAlgorithmSupported("INVALID"));
    }
    
    @Test
    void testGetSupportedAlgorithms() {
        String[] algorithms = ChecksumCalculator.getSupportedAlgorithms();
        assertNotNull(algorithms);
        assertTrue(algorithms.length > 0);
        
        // Check that common algorithms are included
        boolean hasSHA256 = false;
        boolean hasMD5 = false;
        for (String algorithm : algorithms) {
            if ("SHA-256".equals(algorithm)) hasSHA256 = true;
            if ("MD5".equals(algorithm)) hasMD5 = true;
        }
        assertTrue(hasSHA256);
        assertTrue(hasMD5);
    }
    
    @Test
    void testNewInstance() {
        ChecksumCalculator original = new ChecksumCalculator("MD5");
        ChecksumCalculator newInstance = original.newInstance();
        
        assertEquals(original.getAlgorithm(), newInstance.getAlgorithm());
        assertNotSame(original, newInstance);
    }
    
    @Test
    void testToString() {
        ChecksumCalculator calculator = new ChecksumCalculator("SHA-256");
        String toString = calculator.toString();
        
        assertTrue(toString.contains("ChecksumCalculator"));
        assertTrue(toString.contains("SHA-256"));
    }
    
    @Test
    void testConsistentResults() {
        // Same input should always produce same checksum
        byte[] data = "Test data for consistency".getBytes();
        
        ChecksumCalculator calc1 = new ChecksumCalculator();
        calc1.update(data);
        String checksum1 = calc1.getChecksum();
        
        ChecksumCalculator calc2 = new ChecksumCalculator();
        calc2.update(data);
        String checksum2 = calc2.getChecksum();
        
        assertEquals(checksum1, checksum2);
    }
}
