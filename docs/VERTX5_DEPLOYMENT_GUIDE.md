# Vert.x 5.x Deployment Guide

**Project**: Quorus Distributed File Transfer System  
**Version**: 1.0-SNAPSHOT (Vert.x 5.x)  
**Date**: December 17, 2025  

---

## Overview

This guide provides step-by-step instructions for deploying the Vert.x 5.x version of Quorus to production environments.

---

## Prerequisites

### Required Software
- **Java**: OpenJDK 21 or later (Java 24 recommended for virtual threads)
- **Maven**: 3.9.0 or later
- **Database**: PostgreSQL 14+ (for distributed controller)
- **Container Runtime**: Docker 24+ or Podman 4+ (optional, for containerized deployment)

### System Requirements

| Environment | CPU Cores | Memory | Disk | Network |
|-------------|-----------|--------|------|---------|
| **Development** | 4+ | 4 GB | 10 GB | 100 Mbps |
| **Staging** | 8+ | 8 GB | 50 GB | 1 Gbps |
| **Production** | 16+ | 16 GB | 100 GB | 10 Gbps |

---

## Build Instructions

### 1. Build from Source

```bash
# Clone repository
git clone https://github.com/your-org/quorus.git
cd quorus

# Build all modules
mvn clean package -DskipTests

# Build with tests (recommended)
mvn clean package
```

### 2. Build Artifacts

After successful build, artifacts are located in:
- **quorus-core**: `quorus-core/target/quorus-core-1.0-SNAPSHOT.jar`
- **quorus-workflow**: `quorus-workflow/target/quorus-workflow-1.0-SNAPSHOT.jar`
- **quorus-tenant**: `quorus-tenant/target/quorus-tenant-1.0-SNAPSHOT.jar`
- **quorus-api**: `quorus-api/target/quorus-api-1.0-SNAPSHOT.jar`
- **quorus-controller**: `quorus-controller/target/quorus-controller-1.0-SNAPSHOT.jar`

---

## Configuration

### 1. Connection Pool Configuration

Choose a preset based on your workload:

**Default Configuration** (balanced):
```java
ConnectionPoolConfig config = ConnectionPoolConfig.defaultConfig();
```

**Production Configuration** (high reliability):
```java
ConnectionPoolConfig config = ConnectionPoolConfig.productionConfig();
// maxPoolSize: 100, maxWaitQueueSize: 500, connectionTimeout: 30s
```

**High-Throughput Configuration** (maximum performance):
```java
ConnectionPoolConfig config = ConnectionPoolConfig.highThroughputConfig();
// maxPoolSize: 200, maxWaitQueueSize: 1000, connectionTimeout: 10s
```

**Low-Latency Configuration** (fast response):
```java
ConnectionPoolConfig config = ConnectionPoolConfig.lowLatencyConfig();
// maxPoolSize: 50, maxWaitQueueSize: 200, connectionTimeout: 5s
```

### 2. Vert.x Configuration

**Recommended JVM Options**:
```bash
java \
  -XX:+UseZGC \
  -XX:+EnableDynamicAgentLoading \
  -Xms2g -Xmx4g \
  -Dio.netty.allocator.type=adaptive \
  -Dvertx.threadChecks=false \
  -Dvertx.disableContextTimings=true \
  -jar quorus-api-1.0-SNAPSHOT.jar
```

**Environment Variables**:
```bash
# Vert.x event loop threads (default: 2 * CPU cores)
export VERTX_EVENT_LOOP_POOL_SIZE=24

# Worker pool size for blocking operations
export VERTX_WORKER_POOL_SIZE=20

# Max worker execution time (milliseconds)
export VERTX_MAX_WORKER_EXECUTE_TIME=60000
```

### 3. Database Configuration (for quorus-controller)

**PostgreSQL Setup**:
```sql
-- Create database
CREATE DATABASE quorus;

-- Create user
CREATE USER quorus_user WITH PASSWORD 'your_secure_password';

-- Grant privileges
GRANT ALL PRIVILEGES ON DATABASE quorus TO quorus_user;
```

**Connection String**:
```bash
export DB_URL="postgresql://localhost:5432/quorus"
export DB_USER="quorus_user"
export DB_PASSWORD="your_secure_password"
```

---

## Deployment Options

### Option 1: Standalone JAR Deployment

**Start quorus-api**:
```bash
java -jar quorus-api/target/quorus-api-1.0-SNAPSHOT.jar
```

**Start quorus-controller**:
```bash
java -jar quorus-controller/target/quorus-controller-1.0-SNAPSHOT.jar
```

### Option 2: Docker Deployment

**Build Docker Image**:
```dockerfile
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# Copy JAR
COPY quorus-api/target/quorus-api-1.0-SNAPSHOT.jar app.jar

# Expose port
EXPOSE 8080

# Run application
ENTRYPOINT ["java", \
  "-XX:+UseZGC", \
  "-Xms2g", "-Xmx4g", \
  "-jar", "app.jar"]
```

**Build and Run**:
```bash
# Build image
docker build -t quorus-api:1.0 .

# Run container
docker run -d \
  --name quorus-api \
  -p 8080:8080 \
  -e VERTX_EVENT_LOOP_POOL_SIZE=24 \
  quorus-api:1.0
```

### Option 3: Kubernetes Deployment

**Deployment YAML**:
```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: quorus-api
spec:
  replicas: 3
  selector:
    matchLabels:
      app: quorus-api
  template:
    metadata:
      labels:
        app: quorus-api
    spec:
      containers:
      - name: quorus-api
        image: quorus-api:1.0
        ports:
        - containerPort: 8080
        env:
        - name: VERTX_EVENT_LOOP_POOL_SIZE
          value: "24"
        resources:
          requests:
            memory: "2Gi"
            cpu: "2"
          limits:
            memory: "4Gi"
            cpu: "4"
        livenessProbe:
          httpGet:
            path: /health
            port: 8080
          initialDelaySeconds: 30
          periodSeconds: 10
        readinessProbe:
          httpGet:
            path: /health/ready
            port: 8080
          initialDelaySeconds: 10
          periodSeconds: 5
```

---

## Monitoring and Health Checks

### Health Check Endpoints

| Endpoint | Purpose | Expected Response |
|----------|---------|-------------------|
| `GET /health` | Overall health | `{"status": "UP"}` |
| `GET /health/ready` | Readiness probe | `{"status": "READY"}` |
| `GET /health/live` | Liveness probe | `{"status": "ALIVE"}` |

### Metrics Collection

**Enable Metrics**:
```java
// Get health check
TransferEngineHealthCheck healthCheck = transferEngine.getHealthCheck();

// Get metrics
TransferMetrics metrics = transferEngine.getMetrics();

// Check protocol health
ProtocolHealthCheck httpHealth = healthCheck.getProtocolHealth("http");
System.out.println("HTTP Status: " + httpHealth.getStatus());
System.out.println("Active Transfers: " + metrics.getActiveTransfers());
```

---

## Production Checklist

### Pre-Deployment
- [ ] Build passes all tests (190/190)
- [ ] Configuration reviewed and validated
- [ ] Database schema created and migrated
- [ ] SSL/TLS certificates configured
- [ ] Firewall rules configured
- [ ] Monitoring dashboards created

### Post-Deployment
- [ ] Health checks returning UP
- [ ] Metrics being collected
- [ ] Logs being aggregated
- [ ] Performance baseline established
- [ ] Backup and recovery tested
- [ ] Rollback plan documented

---

## Troubleshooting

### Common Issues

**Issue**: High memory usage
**Solution**: Reduce connection pool size or increase heap size

**Issue**: Slow response times
**Solution**: Increase event loop threads or use high-throughput config

**Issue**: Connection timeouts
**Solution**: Increase connection timeout or max wait queue size

---

## Next Steps

1. **Deploy to staging** - Validate in staging environment
2. **Load testing** - Run performance tests with production workloads
3. **Monitoring setup** - Configure Prometheus/Grafana
4. **Documentation** - Update runbooks and operational procedures

---

**Support**: See `docs/design/QUORUS_VERTX5_AUDIT_REPORT.md` for technical details  
**Performance**: See `docs/VERTX5_PERFORMANCE_BENCHMARKS.md` for benchmarks

