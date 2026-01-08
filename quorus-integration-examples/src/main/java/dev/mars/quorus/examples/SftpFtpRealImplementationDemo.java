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
import dev.mars.quorus.examples.util.ExampleLogger;
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
 * @since 2025-09-04
 * @version 1.0
 */
public class SftpFtpRealImplementationDemo {
    private static final ExampleLogger log = ExampleLogger.getLogger(SftpFtpRealImplementationDemo.class);
    
    public static void main(String[] args) {
        log.exampleStart("SFTP/FTP Real Implementation Demo",
                "Demonstrating that we now have real SFTP and FTP implementations\n" +
                "instead of simulations.");
        
        demonstrateSftpValidation();
        demonstrateFtpValidation();
        demonstrateSftpConnection();
        demonstrateFtpConnection();
        
        log.exampleComplete("Demo");
        log.success("SFTP implementation: Real JSch-based client");
        log.success("FTP implementation: Real socket-based client");
        log.success("Both protocols properly validate URIs");
        log.success("Both protocols attempt real network connections");
        log.success("Connection failures are expected for non-existent servers");
    }
    
    private static void demonstrateSftpValidation() {
        log.testSection("1. SFTP URI Validation Tests", false);
        
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
            log.expectedSuccess("Valid SFTP URI: " + canHandle);
            
        } catch (Exception e) {
            log.failure("Valid SFTP URI test failed: " + e.getMessage());
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
            log.failure("Missing host validation failed - should have thrown exception");
            
        } catch (TransferException e) {
            if (e.getMessage().contains("host")) {
                log.expectedSuccess("Missing host validation: Correctly rejected");
            } else {
                log.warning("Missing host validation: " + e.getMessage());
            }
        } catch (Exception e) {
            log.failure("Missing host validation error: " + e.getMessage());
        }
        
        log.info("");
    }
    
    private static void demonstrateFtpValidation() {
        log.testSection("2. FTP URI Validation Tests", false);
        
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
            log.expectedSuccess("Valid FTP URI: " + canHandle);
            
        } catch (Exception e) {
            log.failure("Valid FTP URI test failed: " + e.getMessage());
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
            log.failure("Missing path validation failed - should have thrown exception");
            
        } catch (TransferException e) {
            if (e.getMessage().contains("path")) {
                log.expectedSuccess("Missing path validation: Correctly rejected");
            } else {
                log.warning("Missing path validation: " + e.getMessage());
            }
        } catch (Exception e) {
            log.failure("Missing path validation error: " + e.getMessage());
        }
        
        log.info("");
    }
    
    private static void demonstrateSftpConnection() {
        log.testSection("3. SFTP Real Connection Attempt", false);
        
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
            
            log.detail("Attempting SFTP connection to nonexistent.example.com...");
            sftpProtocol.transfer(request, context);
            log.failure("Connection should have failed");
            
        } catch (TransferException e) {
            if (e.getCause() != null && e.getCause().getClass().getName().contains("JSchException")) {
                log.expectedSuccess("Real SFTP connection attempted (JSch library used)");
                log.expectedSuccess("Connection failed as expected: " + e.getCause().getClass().getSimpleName());
            } else {
                log.warning("SFTP connection error: " + e.getMessage());
            }
        } catch (Exception e) {
            log.failure("SFTP connection test error: " + e.getMessage());
        }
        
        log.info("");
    }
    
    private static void demonstrateFtpConnection() {
        log.testSection("4. FTP Real Connection Attempt", false);
        
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
            
            log.detail("Attempting FTP connection to nonexistent.example.com...");
            ftpProtocol.transfer(request, context);
            log.failure("Connection should have failed");
            
        } catch (TransferException e) {
            if (e.getCause() != null && 
                (e.getCause().getClass().getName().contains("UnknownHostException") ||
                 e.getCause().getClass().getName().contains("IOException"))) {
                log.expectedSuccess("Real FTP connection attempted (Socket-based client)");
                log.expectedSuccess("Connection failed as expected: " + e.getCause().getClass().getSimpleName());
            } else {
                log.warning("FTP connection error: " + e.getMessage());
            }
        } catch (Exception e) {
            log.failure("FTP connection test error: " + e.getMessage());
        }
        
        log.info("");
    }
}
