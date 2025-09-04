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

package dev.mars.quorus.agent;

import dev.mars.quorus.agent.config.AgentConfiguration;
import dev.mars.quorus.agent.service.AgentRegistrationService;
import dev.mars.quorus.agent.service.HeartbeatService;
import dev.mars.quorus.agent.service.TransferExecutionService;
import dev.mars.quorus.agent.service.HealthService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Main class for the Quorus Agent.
 * This agent registers with the Quorus controller cluster and executes file transfer tasks.
 * 
 * @author Mark Andrew Ray-Smith Cityline Ltd
 * @since 1.0
 */
public class QuorusAgent {
    
    private static final Logger logger = LoggerFactory.getLogger(QuorusAgent.class);
    
    private final AgentConfiguration config;
    private final AgentRegistrationService registrationService;
    private final HeartbeatService heartbeatService;
    private final TransferExecutionService transferService;
    private final HealthService healthService;
    private final ScheduledExecutorService scheduler;
    
    private volatile boolean running = false;
    private final CountDownLatch shutdownLatch = new CountDownLatch(1);
    
    public QuorusAgent(AgentConfiguration config) {
        this.config = config;
        this.scheduler = Executors.newScheduledThreadPool(4);
        
        // Initialize services
        this.registrationService = new AgentRegistrationService(config);
        this.heartbeatService = new HeartbeatService(config, registrationService);
        this.transferService = new TransferExecutionService(config);
        this.healthService = new HealthService(config);
        
        logger.info("Quorus Agent initialized: {}", config.getAgentId());
    }
    
    public static void main(String[] args) {
        logger.info("Starting Quorus Agent...");
        
        try {
            // Load configuration from environment
            AgentConfiguration config = AgentConfiguration.fromEnvironment();
            
            // Create and start agent
            QuorusAgent agent = new QuorusAgent(config);
            
            // Setup shutdown hook
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                logger.info("Shutdown signal received");
                agent.shutdown();
            }));
            
            // Start the agent
            agent.start();
            
            // Wait for shutdown
            agent.awaitShutdown();
            
        } catch (Exception e) {
            logger.error("Failed to start Quorus Agent", e);
            System.exit(1);
        }
        
        logger.info("Quorus Agent stopped");
    }
    
    public void start() throws Exception {
        logger.info("Starting Quorus Agent services...");
        
        running = true;
        
        // Start health service first
        healthService.start();
        logger.info("Health service started on port {}", config.getAgentPort());
        
        // Register with controller
        boolean registered = registrationService.register();
        if (!registered) {
            throw new RuntimeException("Failed to register with controller");
        }
        logger.info("Agent registered successfully with controller");
        
        // Start heartbeat service
        scheduler.scheduleAtFixedRate(
            heartbeatService::sendHeartbeat,
            0,
            config.getHeartbeatInterval(),
            TimeUnit.MILLISECONDS
        );
        logger.info("Heartbeat service started (interval: {}ms)", config.getHeartbeatInterval());
        
        // Start transfer execution service
        transferService.start();
        logger.info("Transfer execution service started");
        
        // Start job polling
        scheduler.scheduleAtFixedRate(
            this::pollForJobs,
            5000, // Initial delay
            10000, // Poll every 10 seconds
            TimeUnit.MILLISECONDS
        );
        logger.info("Job polling started");
        
        logger.info("Quorus Agent started successfully");
    }
    
    public void shutdown() {
        if (!running) {
            return;
        }
        
        logger.info("Shutting down Quorus Agent...");
        running = false;
        
        try {
            // Stop job polling
            scheduler.shutdown();
            if (!scheduler.awaitTermination(10, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
            
            // Stop services
            transferService.shutdown();
            heartbeatService.shutdown();
            healthService.shutdown();
            
            // Deregister from controller
            registrationService.deregister();
            
            logger.info("Quorus Agent shutdown complete");
            
        } catch (Exception e) {
            logger.error("Error during shutdown", e);
        } finally {
            shutdownLatch.countDown();
        }
    }
    
    public void awaitShutdown() throws InterruptedException {
        shutdownLatch.await();
    }
    
    private void pollForJobs() {
        if (!running) {
            return;
        }
        
        try {
            // Poll controller for new jobs
            // This would typically make an HTTP request to the controller
            // For now, we'll just log that we're polling
            logger.debug("Polling for new transfer jobs...");
            
            // In a real implementation, this would:
            // 1. Make HTTP request to controller: GET /api/v1/agents/{agentId}/jobs
            // 2. Process any returned jobs
            // 3. Execute transfers using transferService
            
        } catch (Exception e) {
            logger.warn("Error polling for jobs", e);
        }
    }
    
    public boolean isRunning() {
        return running;
    }
    
    public AgentConfiguration getConfiguration() {
        return config;
    }
}
