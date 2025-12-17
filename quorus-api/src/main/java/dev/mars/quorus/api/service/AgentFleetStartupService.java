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
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Service responsible for starting and stopping agent fleet management services.
 * This service ensures that the heartbeat processor and other fleet management
 * components are properly initialized when the application starts.
 *
 * Converted from Quarkus lifecycle events to explicit lifecycle methods for Vert.x 5.x migration.
 *
 * @author Mark Andrew Ray-Smith Cityline Ltd
 * @since 2.0
 */
@ApplicationScoped
public class AgentFleetStartupService {

    private static final Logger logger = LoggerFactory.getLogger(AgentFleetStartupService.class);

    @Inject
    HeartbeatProcessor heartbeatProcessor;

    @Inject
    RaftNode raftNode;

    /**
     * Start agent fleet management services.
     * This method should be called explicitly during application startup.
     */
    public void start() {
        logger.info("Starting Agent Fleet Management services...");

        try {
            // Wait for Raft node to be ready
            waitForRaftNodeReady();

            // Start heartbeat processor
            heartbeatProcessor.start();

            logger.info("Agent Fleet Management services started successfully");

        } catch (Exception e) {
            logger.error("Failed to start Agent Fleet Management services: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to start Agent Fleet Management services", e);
        }
    }

    /**
     * Stop agent fleet management services.
     * This method should be called explicitly during application shutdown.
     */
    public void stop() {
        logger.info("Stopping Agent Fleet Management services...");

        try {
            // Stop heartbeat processor
            heartbeatProcessor.stop();

            logger.info("Agent Fleet Management services stopped successfully");

        } catch (Exception e) {
            logger.warn("Error during Agent Fleet Management services shutdown: {}", e.getMessage(), e);
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
                logger.debug("Raft node not ready yet: {}", e.getMessage());
            }
            
            Thread.sleep(1000); // Wait 1 second
            attempts++;
        }
        
        throw new RuntimeException("Raft node did not become ready within timeout period");
    }
}
