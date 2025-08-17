package dev.mars.quorus.protocol;

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


import dev.mars.quorus.core.TransferRequest;
import dev.mars.quorus.core.TransferResult;
import dev.mars.quorus.core.exceptions.TransferException;
import dev.mars.quorus.transfer.TransferContext;

/**
 * Interface for different file transfer protocols.
 * Implementations handle the specific details of transferring files
 * using different protocols (HTTP, FTP, SFTP, etc.).
 */
public interface TransferProtocol {
    
    /**
     * Get the protocol name/identifier
     */
    String getProtocolName();
    
    /**
     * Check if this protocol can handle the given request
     */
    boolean canHandle(TransferRequest request);
    
    /**
     * Execute the file transfer
     * 
     * @param request the transfer request
     * @param context the transfer context for progress tracking and control
     * @return the transfer result
     * @throws TransferException if the transfer fails
     */
    TransferResult transfer(TransferRequest request, TransferContext context) throws TransferException;
    
    /**
     * Check if the protocol supports resume functionality
     */
    boolean supportsResume();
    
    /**
     * Check if the protocol supports pause functionality
     */
    boolean supportsPause();
    
    /**
     * Get the maximum file size supported by this protocol (-1 for unlimited)
     */
    long getMaxFileSize();
}
