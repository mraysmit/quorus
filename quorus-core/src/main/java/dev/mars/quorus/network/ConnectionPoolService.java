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

package dev.mars.quorus.network;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

/**
 * Connection pooling service for optimizing network connections.
 * 
 * This service manages connection pools for different protocols and hosts,
 * providing connection reuse, load balancing, and performance optimization.
 * 
 * Features:
 * - Protocol-specific connection pools
 * - Connection lifecycle management
 * - Load balancing across connections
 * - Connection health monitoring
 * - Automatic connection cleanup
 * - Corporate network optimization
 */
public class ConnectionPoolService {
    
    private static final Logger logger = Logger.getLogger(ConnectionPoolService.class.getName());
    
    private final ConcurrentHashMap<String, ConnectionPool> connectionPools = new ConcurrentHashMap<>();
    private final ScheduledExecutorService cleanupExecutor = Executors.newScheduledThreadPool(2);
    private final ConnectionPoolConfig config;
    
    public ConnectionPoolService() {
        this(ConnectionPoolConfig.defaultConfig());
    }
    
    public ConnectionPoolService(ConnectionPoolConfig config) {
        this.config = config;
        startCleanupTask();
    }
    
    /**
     * Get or create a connection pool for the specified URI
     */
    public ConnectionPool getConnectionPool(URI uri) {
        String poolKey = createPoolKey(uri);
        
        return connectionPools.computeIfAbsent(poolKey, key -> {
            logger.info("Creating new connection pool for: " + key);
            return new ConnectionPool(uri, config);
        });
    }
    
    /**
     * Get a connection from the pool
     */
    public CompletableFuture<PooledConnection> getConnection(URI uri) {
        ConnectionPool pool = getConnectionPool(uri);
        return pool.getConnection();
    }
    
    /**
     * Return a connection to the pool
     */
    public void returnConnection(PooledConnection connection) {
        if (connection != null) {
            connection.getPool().returnConnection(connection);
        }
    }
    
    /**
     * Get connection pool statistics
     */
    public ConnectionPoolStatistics getStatistics() {
        int totalPools = connectionPools.size();
        int totalConnections = connectionPools.values().stream()
                .mapToInt(pool -> pool.getTotalConnections())
                .sum();
        int activeConnections = connectionPools.values().stream()
                .mapToInt(pool -> pool.getActiveConnections())
                .sum();
        int idleConnections = connectionPools.values().stream()
                .mapToInt(pool -> pool.getIdleConnections())
                .sum();
        
        return new ConnectionPoolStatistics(totalPools, totalConnections, activeConnections, idleConnections);
    }
    
    /**
     * Shutdown the connection pool service
     */
    public void shutdown() {
        logger.info("Shutting down connection pool service");
        
        // Close all connection pools
        connectionPools.values().forEach(ConnectionPool::close);
        connectionPools.clear();
        
        // Shutdown cleanup executor
        cleanupExecutor.shutdown();
        try {
            if (!cleanupExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                cleanupExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            cleanupExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
    
    private String createPoolKey(URI uri) {
        return uri.getScheme() + "://" + uri.getHost() + ":" + 
               (uri.getPort() != -1 ? uri.getPort() : getDefaultPort(uri.getScheme()));
    }
    
    private int getDefaultPort(String scheme) {
        switch (scheme.toLowerCase()) {
            case "http": return 80;
            case "https": return 443;
            case "ftp": return 21;
            case "sftp": return 22;
            case "smb": case "cifs": return 445;
            default: return 80;
        }
    }
    
    private void startCleanupTask() {
        cleanupExecutor.scheduleWithFixedDelay(this::performCleanup, 
                config.getCleanupInterval().toMinutes(), 
                config.getCleanupInterval().toMinutes(), 
                TimeUnit.MINUTES);
    }
    
    private void performCleanup() {
        logger.fine("Performing connection pool cleanup");
        
        connectionPools.entrySet().removeIf(entry -> {
            ConnectionPool pool = entry.getValue();
            pool.cleanup();
            
            // Remove empty pools that haven't been used recently
            if (pool.isEmpty() && pool.isStale()) {
                logger.info("Removing stale connection pool: " + entry.getKey());
                pool.close();
                return true;
            }
            
            return false;
        });
    }
    
    /**
     * Connection pool for a specific host/protocol
     */
    public static class ConnectionPool {
        private final URI uri;
        private final ConnectionPoolConfig config;
        private final BlockingQueue<PooledConnection> availableConnections;
        private final AtomicInteger totalConnections = new AtomicInteger(0);
        private final AtomicInteger activeConnections = new AtomicInteger(0);
        private volatile Instant lastUsed = Instant.now();
        
        public ConnectionPool(URI uri, ConnectionPoolConfig config) {
            this.uri = uri;
            this.config = config;
            this.availableConnections = new LinkedBlockingQueue<>(config.getMaxPoolSize());
        }
        
        public CompletableFuture<PooledConnection> getConnection() {
            return CompletableFuture.supplyAsync(() -> {
                lastUsed = Instant.now();
                
                // Try to get an existing connection
                PooledConnection connection = availableConnections.poll();
                if (connection != null && connection.isValid()) {
                    activeConnections.incrementAndGet();
                    return connection;
                }
                
                // Create new connection if under limit
                if (totalConnections.get() < config.getMaxPoolSize()) {
                    connection = createNewConnection();
                    if (connection != null) {
                        totalConnections.incrementAndGet();
                        activeConnections.incrementAndGet();
                        return connection;
                    }
                }
                
                // Wait for available connection
                try {
                    connection = availableConnections.poll(config.getConnectionTimeout().toMillis(), TimeUnit.MILLISECONDS);
                    if (connection != null && connection.isValid()) {
                        activeConnections.incrementAndGet();
                        return connection;
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                
                throw new RuntimeException("Unable to obtain connection from pool for: " + uri);
            });
        }
        
        public void returnConnection(PooledConnection connection) {
            if (connection != null && connection.isValid()) {
                activeConnections.decrementAndGet();
                if (!availableConnections.offer(connection)) {
                    // Pool is full, close the connection
                    connection.close();
                    totalConnections.decrementAndGet();
                }
            } else {
                // Invalid connection, close it
                if (connection != null) {
                    connection.close();
                }
                totalConnections.decrementAndGet();
            }
        }
        
        public void cleanup() {
            // Remove stale connections
            availableConnections.removeIf(connection -> {
                if (!connection.isValid() || connection.isStale()) {
                    connection.close();
                    totalConnections.decrementAndGet();
                    return true;
                }
                return false;
            });
        }
        
        public void close() {
            // Close all connections
            PooledConnection connection;
            while ((connection = availableConnections.poll()) != null) {
                connection.close();
            }
            totalConnections.set(0);
            activeConnections.set(0);
        }
        
        public boolean isEmpty() {
            return totalConnections.get() == 0;
        }
        
        public boolean isStale() {
            return Instant.now().isAfter(lastUsed.plus(config.getPoolIdleTimeout()));
        }
        
        public int getTotalConnections() { return totalConnections.get(); }
        public int getActiveConnections() { return activeConnections.get(); }
        public int getIdleConnections() { return availableConnections.size(); }
        
        private PooledConnection createNewConnection() {
            try {
                logger.fine("Creating new connection for: " + uri);
                return new PooledConnection(uri, this);
            } catch (Exception e) {
                logger.warning("Failed to create connection for " + uri + ": " + e.getMessage());
                return null;
            }
        }
    }
    
    /**
     * Pooled connection wrapper
     */
    public static class PooledConnection {
        private final URI uri;
        private final ConnectionPool pool;
        private final Instant createdAt;
        private volatile Instant lastUsed;
        private volatile boolean valid = true;
        
        public PooledConnection(URI uri, ConnectionPool pool) {
            this.uri = uri;
            this.pool = pool;
            this.createdAt = Instant.now();
            this.lastUsed = Instant.now();
        }
        
        public URI getUri() { return uri; }
        public ConnectionPool getPool() { return pool; }
        public Instant getCreatedAt() { return createdAt; }
        public Instant getLastUsed() { return lastUsed; }
        
        public boolean isValid() {
            return valid && !isStale();
        }
        
        public boolean isStale() {
            Duration maxAge = Duration.ofMinutes(30); // 30 minute max age
            return Instant.now().isAfter(createdAt.plus(maxAge));
        }
        
        public void markUsed() {
            lastUsed = Instant.now();
        }
        
        public void close() {
            valid = false;
            // In a real implementation, this would close the actual network connection
            logger.fine("Closed connection for: " + uri);
        }
    }
    
    /**
     * Connection pool configuration
     */
    public static class ConnectionPoolConfig {
        private final int maxPoolSize;
        private final Duration connectionTimeout;
        private final Duration poolIdleTimeout;
        private final Duration cleanupInterval;
        
        public ConnectionPoolConfig(int maxPoolSize, Duration connectionTimeout, 
                                  Duration poolIdleTimeout, Duration cleanupInterval) {
            this.maxPoolSize = maxPoolSize;
            this.connectionTimeout = connectionTimeout;
            this.poolIdleTimeout = poolIdleTimeout;
            this.cleanupInterval = cleanupInterval;
        }
        
        public static ConnectionPoolConfig defaultConfig() {
            return new ConnectionPoolConfig(
                    10, // max 10 connections per pool
                    Duration.ofSeconds(30), // 30 second connection timeout
                    Duration.ofMinutes(10), // 10 minute pool idle timeout
                    Duration.ofMinutes(5) // 5 minute cleanup interval
            );
        }
        
        // Getters
        public int getMaxPoolSize() { return maxPoolSize; }
        public Duration getConnectionTimeout() { return connectionTimeout; }
        public Duration getPoolIdleTimeout() { return poolIdleTimeout; }
        public Duration getCleanupInterval() { return cleanupInterval; }
    }
    
    /**
     * Connection pool statistics
     */
    public static class ConnectionPoolStatistics {
        private final int totalPools;
        private final int totalConnections;
        private final int activeConnections;
        private final int idleConnections;
        
        public ConnectionPoolStatistics(int totalPools, int totalConnections, 
                                      int activeConnections, int idleConnections) {
            this.totalPools = totalPools;
            this.totalConnections = totalConnections;
            this.activeConnections = activeConnections;
            this.idleConnections = idleConnections;
        }
        
        // Getters
        public int getTotalPools() { return totalPools; }
        public int getTotalConnections() { return totalConnections; }
        public int getActiveConnections() { return activeConnections; }
        public int getIdleConnections() { return idleConnections; }
        
        @Override
        public String toString() {
            return "ConnectionPoolStatistics{" +
                    "pools=" + totalPools +
                    ", total=" + totalConnections +
                    ", active=" + activeConnections +
                    ", idle=" + idleConnections +
                    '}';
        }
    }
}
