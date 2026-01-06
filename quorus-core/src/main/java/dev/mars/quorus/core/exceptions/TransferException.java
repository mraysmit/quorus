package dev.mars.quorus.core.exceptions;

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


/**
 * Exception thrown when a file transfer operation fails.
 * This includes network errors, file system errors, and protocol-specific failures.
 *
 * @author Mark Andrew Ray-Smith Cityline Ltd
 * @since 2025-08-17
 * @version 1.0
 */
public class TransferException extends QuorusException {
    
    private final String transferId;
    
    public TransferException(String transferId, String message) {
        super(message);
        this.transferId = transferId;
    }
    
    public TransferException(String transferId, String message, Throwable cause) {
        super(message, cause);
        this.transferId = transferId;
    }
    
    public TransferException(String transferId, Throwable cause) {
        super(cause);
        this.transferId = transferId;
    }
    
    public String getTransferId() {
        return transferId;
    }
    
    @Override
    public String getMessage() {
        return String.format("Transfer %s failed: %s", transferId, super.getMessage());
    }
}
