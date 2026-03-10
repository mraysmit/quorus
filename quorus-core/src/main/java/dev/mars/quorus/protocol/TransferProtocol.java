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
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Vertx;

/**
 * Protocol interface for file transfers.
 *
 * @author Mark Andrew Ray-Smith Cityline Ltd
 * @version 2.0
 * @since 2025-08-17
 */
public interface TransferProtocol {
    
    String getProtocolName();
    
    boolean canHandle(TransferRequest request);
    
    /**
     * Execute transfer synchronously (blocking).
     * @deprecated Use {@link #transferReactive(TransferRequest, TransferContext)} instead.
     */
    @Deprecated
    TransferResult transfer(TransferRequest request, TransferContext context) throws TransferException;

    /**
     * Execute transfer asynchronously (reactive).
     * Default implementation offloads the deprecated blocking transfer method to
     * a Vert.x worker thread, preserving event-loop non-blocking behavior.
     */
    default Future<TransferResult> transferReactive(TransferRequest request, TransferContext context) {
        Context vertxContext = Vertx.currentContext();
        if (vertxContext == null) {
            return Future.failedFuture(new TransferException(context.getJobId(),
                    "No Vert.x context available for reactive protocol execution"));
        }

        return vertxContext.owner().executeBlocking(() -> transfer(request, context), false);
    }
    
    boolean supportsResume();
    
    boolean supportsPause();
    
    /**
     * Get the maximum file size supported by this protocol (-1 for unlimited)
     */
    long getMaxFileSize();
    
    /**
     * Abort an in-progress transfer immediately by closing underlying resources.
     * This method should forcibly terminate any active connections, sockets, or streams.
     * <p>
     * For blocking protocols (FTP, SFTP, SMB), this will close the socket, causing
     * the blocking read/write to throw an exception.
     * <p>
     * For reactive protocols (HTTP), this is typically a no-op as cancellation is
     * handled through the Future cancellation mechanism.
     * <p>
     * Default implementation does nothing (no resources to abort).
     * Protocol implementations with active resources should override this method.
     *
     * @since 2.1
     */
    default void abort() {
        // Default: no-op (no resources to abort)
    }
}
