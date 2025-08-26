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

package dev.mars.quorus.api.service;

import dev.mars.quorus.controller.raft.RaftNode;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import io.quarkus.runtime.StartupEvent;
import io.quarkus.runtime.ShutdownEvent;
import java.util.logging.Logger;

/**
 * Service responsible for starting and stopping agent fleet management services.
 * This service ensures that the heartbeat processor and other fleet management
 * components are properly initialized when the application starts.
 * 
 * @author Mark Andrew Ray-Smith Cityline Ltd
 * @since 2.0
 */
@ApplicationScoped
public class AgentFleetStartupService {

    private static final Logger logger = Logger.getLogger(AgentFleetStartupService.class.getName());

    @Inject
    HeartbeatProcessor heartbeatProcessor;

    @Inject
    RaftNode raftNode;

    /**
     * Handle application startup.
     * Initialize agent fleet management services.
     */
    void onStart(@Observes StartupEvent ev) {
        logger.info("Starting Agent Fleet Management services...");
        
        try {
            // Wait for Raft node to be ready
            waitForRaftNodeReady();
            
            // Start heartbeat processor
            heartbeatProcessor.start();
            
            logger.info("Agent Fleet Management services started successfully");
            
        } catch (Exception e) {
            logger.severe("Failed to start Agent Fleet Management services: " + e.getMessage());
            throw new RuntimeException("Failed to start Agent Fleet Management services", e);
        }
    }

    /**
     * Handle application shutdown.
     * Gracefully stop agent fleet management services.
     */
    void onStop(@Observes ShutdownEvent ev) {
        logger.info("Stopping Agent Fleet Management services...");
        
        try {
            // Stop heartbeat processor
            heartbeatProcessor.stop();
            
            logger.info("Agent Fleet Management services stopped successfully");
            
        } catch (Exception e) {
            logger.warning("Error during Agent Fleet Management services shutdown: " + e.getMessage());
        }
    }

    /**
     * Wait for the Raft node to be ready.
     * This ensures the distributed state management is available before starting fleet services.
     */
    private void waitForRaftNodeReady() throws InterruptedException {
        logger.info("Waiting for Raft node to be ready...");
        
        int maxAttempts = 30; // 30 seconds timeout
        int attempts = 0;
        
        while (attempts < maxAttempts) {
            try {
                // Check if Raft node is running and has a state
                if (raftNode != null && raftNode.getState() != null) {
                    logger.info("Raft node is ready with state: " + raftNode.getState());
                    return;
                }
            } catch (Exception e) {
                logger.fine("Raft node not ready yet: " + e.getMessage());
            }
            
            Thread.sleep(1000); // Wait 1 second
            attempts++;
        }
        
        throw new RuntimeException("Raft node did not become ready within timeout period");
    }
}
