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

package dev.mars.quorus.api.health;

import dev.mars.quorus.transfer.TransferEngine;
import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.Readiness;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@Readiness
@ApplicationScoped
/**
 * TransferEngineHealthCheck implementation for the Quorus file transfer system.
 * 
 * @author Mark Andrew Ray-Smith Cityline Ltd
 * @since 1.0
 */
public class TransferEngineHealthCheck implements HealthCheck {

    @Inject
    TransferEngine transferEngine;

    @Override
    public HealthCheckResponse call() {
        try {
            // Check if transfer engine is operational by getting active transfer count
            int activeTransfers = transferEngine.getActiveTransferCount();
            
            return HealthCheckResponse.named("transfer-engine")
                    .up()
                    .withData("activeTransfers", activeTransfers)
                    .withData("status", "operational")
                    .build();
                    
        } catch (Exception e) {
            return HealthCheckResponse.named("transfer-engine")
                    .down()
                    .withData("error", e.getMessage())
                    .withData("status", "failed")
                    .build();
        }
    }
}
