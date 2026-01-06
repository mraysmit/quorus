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
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
/**
 * Description for ChecksumCalculator
 *
 * @author Mark Andrew Ray-Smith Cityline Ltd
 * @version 1.0
 * @since 2025-08-17
 */

public class ChecksumCalculator {
    private static final String DEFAULT_ALGORITHM = "SHA-256";
    private static final int BUFFER_SIZE = 8192;
    
    private final MessageDigest digest;
    private final String algorithm;
    
    public ChecksumCalculator() {
        this(DEFAULT_ALGORITHM);
    }
    
    public ChecksumCalculator(String algorithm) {
        this.algorithm = algorithm;
        try {
            this.digest = MessageDigest.getInstance(algorithm);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Unsupported checksum algorithm: " + algorithm, e);
        }
    }
    
    public void update(byte[] data) {
        digest.update(data);
    }
    
    public void update(byte[] data, int offset, int length) {
        digest.update(data, offset, length);
    }
    
    public String getChecksum() {
        byte[] hash = digest.digest();
        return bytesToHex(hash);
    }
    
    public void reset() {
        digest.reset();
    }
    
    public static String calculateFileChecksum(Path filePath) throws IOException {
        return calculateFileChecksum(filePath, DEFAULT_ALGORITHM);
    }
    
    public static String calculateFileChecksum(Path filePath, String algorithm) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance(algorithm);
            
            try (InputStream inputStream = Files.newInputStream(filePath)) {
                byte[] buffer = new byte[BUFFER_SIZE];
                int bytesRead;
                
                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    digest.update(buffer, 0, bytesRead);
                }
            }
            
            return bytesToHex(digest.digest());
            
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Unsupported checksum algorithm: " + algorithm, e);
        }
    }
    
    public static boolean verifyFileChecksum(Path filePath, String expectedChecksum) throws IOException {
        return verifyFileChecksum(filePath, expectedChecksum, DEFAULT_ALGORITHM);
    }
    
    public static boolean verifyFileChecksum(Path filePath, String expectedChecksum, String algorithm) throws IOException {
        if (expectedChecksum == null || expectedChecksum.trim().isEmpty()) {
            return true; // No checksum to verify
        }
        
        String actualChecksum = calculateFileChecksum(filePath, algorithm);
        return expectedChecksum.equalsIgnoreCase(actualChecksum);
    }
    
    public String getAlgorithm() {
        return algorithm;
    }
    
    public static boolean isAlgorithmSupported(String algorithm) {
        try {
            MessageDigest.getInstance(algorithm);
            return true;
        } catch (NoSuchAlgorithmException e) {
            return false;
        }
    }
    
    public static String[] getSupportedAlgorithms() {
        return new String[]{"MD5", "SHA-1", "SHA-256", "SHA-512"};
    }
    
    private static String bytesToHex(byte[] bytes) {
        StringBuilder result = new StringBuilder();
        for (byte b : bytes) {
            result.append(String.format("%02x", b));
        }
        return result.toString();
    }
    
    /**
     * Create a new calculator instance for the same algorithm
     */
    public ChecksumCalculator newInstance() {
        return new ChecksumCalculator(algorithm);
    }
    
    @Override
    public String toString() {
        return "ChecksumCalculator{algorithm='" + algorithm + "'}";
    }
}
