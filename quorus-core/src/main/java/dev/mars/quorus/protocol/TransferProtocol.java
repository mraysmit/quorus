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
import io.vertx.core.Future;

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
     * Default implementation wraps the blocking transfer method.
     */
    default Future<TransferResult> transferReactive(TransferRequest request, TransferContext context) {
        return Future.future(promise -> {
            try {
                promise.complete(transfer(request, context));
            } catch (Exception e) {
                promise.fail(e);
            }
        });
    }
    
    boolean supportsResume();
    
    boolean supportsPause();
    
    /**
     * Get the maximum file size supported by this protocol (-1 for unlimited)
     */
    long getMaxFileSize();
}
