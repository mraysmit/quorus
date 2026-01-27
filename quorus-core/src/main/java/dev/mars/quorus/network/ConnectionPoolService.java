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

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.pgclient.PgBuilder;
import io.vertx.pgclient.PgConnectOptions;
import io.vertx.sqlclient.Pool;
import io.vertx.sqlclient.PoolOptions;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
/**
 * Description for ConnectionPoolService
 *
 * @author Mark Andrew Ray-Smith Cityline Ltd
 * @version 1.0
 * @since 2025-08-18
 */

/**
 * Reactive Database Connection Pool Service using Vert.x 5.
 * Replaces the legacy custom connection pool implementation.
 * 
 * @since 1.1
 */
public class ConnectionPoolService {
    private static final Logger logger = LoggerFactory.getLogger(ConnectionPoolService.class);

    private final Vertx vertx;
    private final ConcurrentHashMap<String, Pool> pools = new ConcurrentHashMap<>();

    public ConnectionPoolService(Vertx vertx) {
        this.vertx = Objects.requireNonNull(vertx, "Vertx cannot be null");
        logger.debug("ConnectionPoolService initialized with external Vert.x instance");
    }

    /**
     * Default constructor for backward compatibility (creates internal Vert.x instance).
     * @deprecated Use {@link #ConnectionPoolService(Vertx)} instead.
     */
    @Deprecated
    public ConnectionPoolService() {
        this(Vertx.vertx());
        logger.warn("ConnectionPoolService created with internal Vert.x instance (deprecated)");
    }

    public Pool getOrCreatePool(String serviceId, PgConnectionConfig config, PgPoolConfig poolConfig) {
        logger.debug("Requesting pool for service: serviceId={}, host={}, port={}, database={}",
                    serviceId, config.getHost(), config.getPort(), config.getDatabase());
        return pools.computeIfAbsent(serviceId, id -> {
            try {
                logger.debug("Creating new reactive pool for service: serviceId={}, maxSize={}, maxWaitQueueSize={}",
                            id, poolConfig.getMaxSize(), poolConfig.getMaxWaitQueueSize());
                PgConnectOptions connectOptions = new PgConnectOptions()
                    .setHost(config.getHost())
                    .setPort(config.getPort())
                    .setDatabase(config.getDatabase())
                    .setUser(config.getUsername())
                    .setPassword(config.getPassword())
                    .setPipeliningLimit(256)
                    .setCachePreparedStatements(true)
                    .setPreparedStatementCacheMaxSize(256);

                PoolOptions poolOptions = new PoolOptions()
                    .setMaxSize(poolConfig.getMaxSize())
                    .setShared(true)
                    .setName(serviceId + "-pool")
                    .setMaxWaitQueueSize(poolConfig.getMaxWaitQueueSize())
                    .setConnectionTimeout(poolConfig.getConnectionTimeout())
                    .setIdleTimeout(poolConfig.getIdleTimeout());

                Pool pool = PgBuilder.pool()
                    .with(poolOptions)
                    .connectingTo(connectOptions)
                    .using(vertx)
                    .build();

                logger.info("Created reactive pool for service '{}': maxSize={}, idleTimeout={}", 
                           id, poolConfig.getMaxSize(), poolConfig.getIdleTimeout());
                return pool;
            } catch (Exception e) {
                logger.error("Failed to create pool for service '{}': {}", id, e.getMessage());
                pools.remove(id);
                throw new RuntimeException("Failed to create pool for service: " + id, e);
            }
        });
    }

    public Future<Void> removePoolAsync(String serviceId) {
        logger.debug("Removing pool for service: serviceId={}", serviceId);
        Pool pool = pools.remove(serviceId);
        if (pool == null) {
            logger.debug("No pool found for service '{}', nothing to remove", serviceId);
            return Future.succeededFuture();
        }

        return pool.close()
            .onSuccess(v -> logger.info("Closed pool for service '{}'", serviceId))
            .onFailure(err -> logger.warn("Error closing pool for service '{}': {}", serviceId, err.getMessage()))
            .mapEmpty();
    }

    public Future<Void> closeAllAsync() {
        logger.debug("Closing all connection pools: count={}", pools.size());
        var futures = pools.keySet().stream()
            .map(this::removePoolAsync)
            .toList();

        pools.clear();
        logger.debug("All pools cleared, waiting for {} close operations", futures.size());
        return Future.all(futures).mapEmpty();
    }
    
    public void shutdown() {
        logger.debug("Initiating shutdown of ConnectionPoolService");
        closeAllAsync().onFailure(err -> logger.warn("Error shutting down pools: {}", err.getMessage()));
    }

    public Future<Boolean> checkHealth(String serviceId) {
        logger.debug("Checking health for service: serviceId={}", serviceId);
        Pool pool = pools.get(serviceId);
        if (pool == null) {
            logger.debug("Health check: no pool found for service '{}'", serviceId);
            return Future.succeededFuture(false);
        }

        return pool.withConnection(conn ->
            conn.query("SELECT 1").execute().map(rs -> {
                logger.trace("Health check passed for service '{}'", serviceId);
                return true;
            })
        ).recover(err -> {
            logger.warn("Health check failed for '{}': {}", serviceId, err.getMessage());
            return Future.succeededFuture(false);
        });
    }

    public static class PgConnectionConfig {
        private final String host;
        private final int port;
        private final String database;
        private final String username;
        private final String password;

        public PgConnectionConfig(String host, int port, String database, String username, String password) {
            this.host = host;
            this.port = port;
            this.database = database;
            this.username = username;
            this.password = password;
        }
        
        public String getHost() { return host; }
        public int getPort() { return port; }
        public String getDatabase() { return database; }
        public String getUsername() { return username; }
        public String getPassword() { return password; }
    }

    public static class PgPoolConfig {
        private int maxSize = 100;
        private int maxWaitQueueSize = 1000;
        private int connectionTimeout = 30000;
        private int idleTimeout = 600000;

        public PgPoolConfig() {}

        public PgPoolConfig(int maxSize, int maxWaitQueueSize, int connectionTimeout, int idleTimeout) {
            this.maxSize = maxSize;
            this.maxWaitQueueSize = maxWaitQueueSize;
            this.connectionTimeout = connectionTimeout;
            this.idleTimeout = idleTimeout;
        }
        
        public int getMaxSize() { return maxSize; }
        public int getMaxWaitQueueSize() { return maxWaitQueueSize; }
        public int getConnectionTimeout() { return connectionTimeout; }
        public int getIdleTimeout() { return idleTimeout; }
    }
}
