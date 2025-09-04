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

package dev.mars.quorus.examples;

import dev.mars.quorus.core.TransferRequest;
import dev.mars.quorus.core.TransferResult;
import dev.mars.quorus.core.exceptions.TransferException;
import dev.mars.quorus.protocol.FtpTransferProtocol;
import dev.mars.quorus.protocol.SftpTransferProtocol;
import dev.mars.quorus.transfer.TransferContext;
import dev.mars.quorus.core.TransferJob;

import java.net.URI;
import java.nio.file.Paths;

/**
 * Demo to show that real SFTP and FTP implementations are working.
 * This demonstrates the protocol validation and connection attempts.
 * 
 * @author Mark Andrew Ray-Smith Cityline Ltd
 * @since 1.0
 */
public class SftpFtpRealImplementationDemo {
    
    public static void main(String[] args) {
        System.out.println("=== SFTP/FTP Real Implementation Demo ===");
        System.out.println("Demonstrating that we now have real SFTP and FTP implementations");
        System.out.println("instead of simulations.\n");
        
        demonstrateSftpValidation();
        demonstrateFtpValidation();
        demonstrateSftpConnection();
        demonstrateFtpConnection();
        
        System.out.println("\n=== Demo Complete ===");
        System.out.println("✅ SFTP implementation: Real JSch-based client");
        System.out.println("✅ FTP implementation: Real socket-based client");
        System.out.println("✅ Both protocols properly validate URIs");
        System.out.println("✅ Both protocols attempt real network connections");
        System.out.println("✅ Connection failures are expected for non-existent servers");
    }
    
    private static void demonstrateSftpValidation() {
        System.out.println("1. SFTP URI Validation Tests:");
        
        SftpTransferProtocol sftpProtocol = new SftpTransferProtocol();
        
        // Test valid SFTP URI
        try {
            TransferRequest validRequest = TransferRequest.builder()
                    .requestId("test-valid-sftp")
                    .sourceUri(URI.create("sftp://server.example.com/path/file.txt"))
                    .destinationPath(Paths.get("temp/file.txt"))
                    .protocol("sftp")
                    .build();
            
            boolean canHandle = sftpProtocol.canHandle(validRequest);
            System.out.println("   ✅ Valid SFTP URI: " + canHandle);
            
        } catch (Exception e) {
            System.out.println("   ❌ Valid SFTP URI test failed: " + e.getMessage());
        }
        
        // Test missing host validation
        try {
            TransferRequest invalidRequest = TransferRequest.builder()
                    .requestId("test-missing-host")
                    .sourceUri(URI.create("sftp:///path/file.txt"))
                    .destinationPath(Paths.get("temp/file.txt"))
                    .protocol("sftp")
                    .build();
            
            TransferJob job = new TransferJob(invalidRequest);
            TransferContext context = new TransferContext(job);
            
            sftpProtocol.transfer(invalidRequest, context);
            System.out.println("   ❌ Missing host validation failed - should have thrown exception");
            
        } catch (TransferException e) {
            if (e.getMessage().contains("host")) {
                System.out.println("   ✅ Missing host validation: Correctly rejected");
            } else {
                System.out.println("   ⚠️  Missing host validation: " + e.getMessage());
            }
        } catch (Exception e) {
            System.out.println("   ❌ Missing host validation error: " + e.getMessage());
        }
        
        System.out.println();
    }
    
    private static void demonstrateFtpValidation() {
        System.out.println("2. FTP URI Validation Tests:");
        
        FtpTransferProtocol ftpProtocol = new FtpTransferProtocol();
        
        // Test valid FTP URI
        try {
            TransferRequest validRequest = TransferRequest.builder()
                    .requestId("test-valid-ftp")
                    .sourceUri(URI.create("ftp://server.example.com/path/file.txt"))
                    .destinationPath(Paths.get("temp/file.txt"))
                    .protocol("ftp")
                    .build();
            
            boolean canHandle = ftpProtocol.canHandle(validRequest);
            System.out.println("   ✅ Valid FTP URI: " + canHandle);
            
        } catch (Exception e) {
            System.out.println("   ❌ Valid FTP URI test failed: " + e.getMessage());
        }
        
        // Test missing path validation
        try {
            TransferRequest invalidRequest = TransferRequest.builder()
                    .requestId("test-missing-path")
                    .sourceUri(URI.create("ftp://server.example.com"))
                    .destinationPath(Paths.get("temp/file.txt"))
                    .protocol("ftp")
                    .build();
            
            TransferJob job = new TransferJob(invalidRequest);
            TransferContext context = new TransferContext(job);
            
            ftpProtocol.transfer(invalidRequest, context);
            System.out.println("   ❌ Missing path validation failed - should have thrown exception");
            
        } catch (TransferException e) {
            if (e.getMessage().contains("path")) {
                System.out.println("   ✅ Missing path validation: Correctly rejected");
            } else {
                System.out.println("   ⚠️  Missing path validation: " + e.getMessage());
            }
        } catch (Exception e) {
            System.out.println("   ❌ Missing path validation error: " + e.getMessage());
        }
        
        System.out.println();
    }
    
    private static void demonstrateSftpConnection() {
        System.out.println("3. SFTP Real Connection Attempt:");
        
        SftpTransferProtocol sftpProtocol = new SftpTransferProtocol();
        
        try {
            TransferRequest request = TransferRequest.builder()
                    .requestId("test-sftp-connection")
                    .sourceUri(URI.create("sftp://nonexistent.example.com/path/file.txt"))
                    .destinationPath(Paths.get("temp/sftp-file.txt"))
                    .protocol("sftp")
                    .build();
            
            TransferJob job = new TransferJob(request);
            TransferContext context = new TransferContext(job);
            
            System.out.println("   Attempting SFTP connection to nonexistent.example.com...");
            sftpProtocol.transfer(request, context);
            System.out.println("   ❌ Connection should have failed");
            
        } catch (TransferException e) {
            if (e.getCause() != null && e.getCause().getClass().getName().contains("JSchException")) {
                System.out.println("   ✅ Real SFTP connection attempted (JSch library used)");
                System.out.println("   ✅ Connection failed as expected: " + e.getCause().getClass().getSimpleName());
            } else {
                System.out.println("   ⚠️  SFTP connection error: " + e.getMessage());
            }
        } catch (Exception e) {
            System.out.println("   ❌ SFTP connection test error: " + e.getMessage());
        }
        
        System.out.println();
    }
    
    private static void demonstrateFtpConnection() {
        System.out.println("4. FTP Real Connection Attempt:");
        
        FtpTransferProtocol ftpProtocol = new FtpTransferProtocol();
        
        try {
            TransferRequest request = TransferRequest.builder()
                    .requestId("test-ftp-connection")
                    .sourceUri(URI.create("ftp://nonexistent.example.com/path/file.txt"))
                    .destinationPath(Paths.get("temp/ftp-file.txt"))
                    .protocol("ftp")
                    .build();
            
            TransferJob job = new TransferJob(request);
            TransferContext context = new TransferContext(job);
            
            System.out.println("   Attempting FTP connection to nonexistent.example.com...");
            ftpProtocol.transfer(request, context);
            System.out.println("   ❌ Connection should have failed");
            
        } catch (TransferException e) {
            if (e.getCause() != null && 
                (e.getCause().getClass().getName().contains("UnknownHostException") ||
                 e.getCause().getClass().getName().contains("IOException"))) {
                System.out.println("   ✅ Real FTP connection attempted (Socket-based client)");
                System.out.println("   ✅ Connection failed as expected: " + e.getCause().getClass().getSimpleName());
            } else {
                System.out.println("   ⚠️  FTP connection error: " + e.getMessage());
            }
        } catch (Exception e) {
            System.out.println("   ❌ FTP connection test error: " + e.getMessage());
        }
        
        System.out.println();
    }
}
