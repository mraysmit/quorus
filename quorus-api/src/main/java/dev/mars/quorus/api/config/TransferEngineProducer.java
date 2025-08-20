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

package dev.mars.quorus.api.config;

import dev.mars.quorus.transfer.SimpleTransferEngine;
import dev.mars.quorus.transfer.TransferEngine;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Singleton;
import java.util.logging.Logger;

@ApplicationScoped
public class TransferEngineProducer {

    private static final Logger logger = Logger.getLogger(TransferEngineProducer.class.getName());

    @ConfigProperty(name = "quorus.transfer.max-concurrent-transfers", defaultValue = "10")
    int maxConcurrentTransfers;

    @ConfigProperty(name = "quorus.transfer.max-retry-attempts", defaultValue = "3")
    int maxRetryAttempts;

    @ConfigProperty(name = "quorus.transfer.retry-delay-ms", defaultValue = "1000")
    long retryDelayMs;

    /**
     * Produces a singleton TransferEngine instance.
     */
    @Produces
    @Singleton
    public TransferEngine createTransferEngine() {
        logger.info("Creating TransferEngine with maxConcurrentTransfers=" + maxConcurrentTransfers + 
                   ", maxRetryAttempts=" + maxRetryAttempts + 
                   ", retryDelayMs=" + retryDelayMs);
        
        return new SimpleTransferEngine(maxConcurrentTransfers, maxRetryAttempts, retryDelayMs);
    }
}
