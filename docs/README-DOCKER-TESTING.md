# Docker-Based Raft Cluster Testing

This document describes the Docker-based testing infrastructure for the Quorus Raft cluster manager, which provides realistic network testing scenarios using actual containers and HTTP communication.

## Overview

The Docker testing infrastructure replaces in-memory testing with real containerized nodes that communicate over HTTP, providing:

- **Real network communication** between Raft nodes
- **Container lifecycle management** (start/stop/restart scenarios)
- **Network partition simulation** using Docker network manipulation
- **Configurable test scenarios** for different cluster sizes and conditions
- **Integration testing** with TestContainers
- **Centralized log aggregation** using Grafana Loki stack
- **Real-time monitoring and alerting** across all distributed components

## Architecture

### Components

1. **HttpRaftTransport** - HTTP-based transport implementation
2. **Docker Compose Configurations** - 3-node and 5-node cluster setups with custom networks
3. **TestContainers Integration** - Automated container orchestration
4. **Network Testing Utils** - Network partition and failure simulation using Docker networks
5. **Test Configuration Management** - Predefined test scenarios
6. **Custom Docker Networks** - Realistic network topology with static IP addresses
7. **Network Test Helper Script** - Command-line utilities for network manipulation
8. **Log Aggregation Stack** - Grafana Loki for centralized logging and monitoring
9. **Real-time Observability** - Structured logging, metrics, and alerting capabilities

### File Structure

```
quorus-controller/
├── Dockerfile                              # Container image for controller
├── docker-entrypoint.sh                    # Container startup script
├── src/main/java/.../raft/
│   ├── HttpRaftTransport.java              # HTTP-based transport
│   └── RaftClusterConfig.java              # Cluster configuration
├── src/test/java/.../raft/
│   ├── DockerRaftClusterTest.java          # Basic Docker cluster tests
│   ├── NetworkPartitionTest.java          # Network partition testing
│   ├── AdvancedNetworkTest.java            # Advanced network scenarios
│   ├── ConfigurableRaftClusterTest.java    # Configurable test scenarios
│   ├── NetworkTestUtils.java              # Network manipulation utilities
│   └── TestClusterConfiguration.java      # Test configuration management
└── README-DOCKER-TESTING.md               # This documentation

docker/                                   # Docker infrastructure directory
├── compose/                              # Docker Compose configurations
│   ├── docker-compose.yml               # 3-node cluster with custom networks
│   ├── docker-compose-5node.yml         # 5-node cluster configuration
│   ├── docker-compose-network-test.yml  # Advanced network testing setup
│   └── docker-compose-loki.yml          # Log aggregation stack
├── logging/                              # Log aggregation configurations
│   ├── loki/config.yml                  # Loki storage configuration
│   ├── promtail/config.yml              # Log collection configuration
│   ├── grafana/provisioning/            # Grafana datasource setup
│   └── prometheus/prometheus.yml        # Metrics collection setup
├── scripts/                              # Docker automation scripts
│   ├── setup-logging.ps1               # Log aggregation setup script
│   └── demo-logging.ps1                # Log aggregation demo script
└── test-data/                            # Test data and utilities
    ├── test-heartbeat.json             # Sample heartbeat payload
    ├── test-registration.json          # Sample agent registration
    └── send-heartbeat.ps1              # Heartbeat testing script
scripts/network-test-helper.sh             # Network testing utilities
```

## Quick Start

### Prerequisites

- Docker and Docker Compose installed
- Java 21+ and Maven 3.9+
- At least 2GB RAM available for containers

### Running Basic Tests

1. **Build the project:**
   ```bash
   mvn clean package -DskipTests
   ```

2. **Run Docker cluster tests:**
   ```bash
   mvn test -Dtest=DockerRaftClusterTest
   ```

3. **Run network partition tests:**
   ```bash
   mvn test -Dtest=NetworkPartitionTest
   ```

4. **Run configurable tests:**
   ```bash
   mvn test -Dtest=ConfigurableRaftClusterTest
   ```

5. **Run advanced network tests:**
   ```bash
   mvn test -Dtest=AdvancedNetworkTest
   ```

### Manual Cluster Testing

1. **Start a 3-node cluster:**
   ```bash
   docker-compose -f docker/compose/docker-compose.yml up -d
   ```

2. **Check cluster health:**
   ```bash
   curl http://localhost:8081/health
   curl http://localhost:8082/health
   curl http://localhost:8083/health
   ```

3. **Check Raft status:**
   ```bash
   curl http://localhost:8081/raft/status
   curl http://localhost:8082/raft/status
   curl http://localhost:8083/raft/status
   ```

4. **Stop the cluster:**
   ```bash
   docker-compose -f docker/compose/docker-compose.yml down
   ```

### Advanced Network Testing

1. **Start advanced network test environment:**
   ```bash
   docker-compose -f docker/compose/docker-compose-network-test.yml up -d
   ```

2. **Use network test helper (Linux/Mac):**
   ```bash
   # Set up networks
   ./scripts/network-test-helper.sh setup

   # Create network partition (nodes 1,2,3 vs 4,5)
   ./scripts/network-test-helper.sh partition 1,2,3 4,5

   # Add network latency
   ./scripts/network-test-helper.sh latency controller1 100ms

   # Add packet loss
   ./scripts/network-test-helper.sh packet-loss controller2 5%

   # Monitor cluster status
   ./scripts/network-test-helper.sh monitor

   # Restore connectivity
   ./scripts/network-test-helper.sh restore
   ```

3. **Manual network manipulation:**
   ```bash
   # Create network partition using Docker networks
   docker network disconnect quorus_raft-cluster quorus-controller4
   docker network disconnect quorus_raft-cluster quorus-controller5
   docker network connect --ip 172.21.0.14 quorus_raft-partition-a quorus-controller4
   docker network connect --ip 172.21.0.15 quorus_raft-partition-a quorus-controller5

   # Add network latency
   docker exec quorus-controller1 tc qdisc add dev eth0 root netem delay 100ms

   # Add packet loss
   docker exec quorus-controller2 tc qdisc add dev eth0 root netem loss 5%

   # Restore network
   docker network disconnect quorus_raft-partition-a quorus-controller4
   docker network connect --ip 172.20.0.13 quorus_raft-cluster quorus-controller4
   ```

## Test Configurations

### Predefined Configurations

The system provides several predefined test configurations:

- **fastTest()** - Quick tests with minimal timeouts
- **standardTest()** - Standard integration testing
- **largeClusterTest()** - 5-node cluster testing
- **partitionTest()** - Network partition testing
- **stressTest()** - High latency and packet loss
- **ciTest()** - Minimal resources for CI/CD
- **developmentTest()** - Balanced for development

### Example Usage

```java
@Test
void testWithCustomConfiguration() {
    TestClusterConfiguration config = new TestClusterConfiguration.Builder()
            .nodeCount(5)
            .electionTimeout(Duration.ofSeconds(4))
            .heartbeatInterval(Duration.ofMillis(800))
            .composeFile("docker-compose-5node.yml")
            .addEnvironmentVariable("JAVA_OPTS", "-Xmx256m")
            .networkConfig(new NetworkConfiguration(
                Duration.ofMillis(50), 2, true, Duration.ofMinutes(2)))
            .build();
    
    setupWithConfiguration(config, testInfo);
    // Run tests...
}
```

## Network Architecture

### Custom Docker Networks

The testing infrastructure uses multiple Docker networks to simulate realistic network conditions:

1. **raft-cluster (172.20.0.0/16)** - Primary cluster network for normal operations
2. **raft-partition-a (172.21.0.0/16)** - Partition network A for split-brain testing
3. **raft-partition-b (172.22.0.0/16)** - Partition network B for complex scenarios

### Static IP Addresses

Each controller has a predictable IP address:
- controller1: 172.20.0.10
- controller2: 172.20.0.11
- controller3: 172.20.0.12
- controller4: 172.20.0.13
- controller5: 172.20.0.14

### Network Capabilities

Containers are configured with `NET_ADMIN` capability to enable:
- Traffic control (tc) for latency and packet loss simulation
- iptables rules for traffic blocking
- Network interface manipulation

## Network Testing

### Partition Testing

The infrastructure supports various network partition scenarios:

1. **Docker Network Partitions** - True network isolation using separate Docker networks
2. **iptables-based Partitions** - Traffic blocking using firewall rules
3. **Majority Partition (3-2)** - Tests quorum maintenance
4. **Split Brain Prevention (2-2-1)** - Tests split-brain prevention
5. **Network Recovery** - Tests cluster recovery after partitions
6. **Geographic Distribution** - Simulates latency between distant nodes

### Network Manipulation

Use `NetworkTestUtils` for advanced network testing:

```java
// Create network partition
NetworkTestUtils.createNetworkPartition(environment, 
    List.of("controller1", "controller2", "controller3"),
    List.of("controller4", "controller5"));

// Add network latency
NetworkTestUtils.addNetworkLatency(environment, "controller1", 100);

// Simulate packet loss
NetworkTestUtils.addPacketLoss(environment, "controller2", 5);

// Isolate a node completely
NetworkTestUtils.isolateNode(environment, "controller3");

// Restore connectivity
NetworkTestUtils.restoreNode(environment, "controller3");
```

## Container Configuration

### Environment Variables

Each controller container supports these environment variables:

- `NODE_ID` - Unique node identifier (e.g., "controller1")
- `RAFT_HOST` - Host to bind to (default: "0.0.0.0")
- `RAFT_PORT` - Port to listen on (default: 8080)
- `CLUSTER_NODES` - Comma-separated list of cluster nodes
- `ELECTION_TIMEOUT_MS` - Election timeout in milliseconds
- `HEARTBEAT_INTERVAL_MS` - Heartbeat interval in milliseconds
- `JAVA_OPTS` - JVM options

### Example Docker Compose Service

```yaml
controller1:
  build:
    context: .
    dockerfile: quorus-controller/Dockerfile
  environment:
    - NODE_ID=controller1
    - RAFT_HOST=0.0.0.0
    - RAFT_PORT=8080
    - CLUSTER_NODES=controller1=controller1:8080,controller2=controller2:8080,controller3=controller3:8080
    - ELECTION_TIMEOUT_MS=3000
    - HEARTBEAT_INTERVAL_MS=500
    - JAVA_OPTS=-Xmx256m -Xms128m
  ports:
    - "8081:8080"
  networks:
    - raft-cluster
```

## Monitoring and Debugging

### Health Checks

Each node provides health check endpoints:

- `GET /health` - Basic health status
- `GET /raft/status` - Raft node status (state, term, leader)

### Logs

Container logs provide detailed information:

```bash
# View logs for specific node
docker-compose logs controller1

# Follow logs for all nodes
docker-compose logs -f

# View logs with timestamps
docker-compose logs -t controller1
```

### Debugging Network Issues

1. **Check container connectivity:**
   ```bash
   docker exec quorus-controller1 ping controller2
   ```

2. **Inspect network configuration:**
   ```bash
   docker network inspect quorus_raft-cluster
   ```

3. **Check iptables rules:**
   ```bash
   docker exec quorus-controller1 iptables -L
   ```

## Performance Considerations

### Resource Usage

- Each controller container uses ~256MB RAM by default
- 3-node cluster: ~768MB total
- 5-node cluster: ~1.28GB total

### Timeouts

Adjust timeouts based on your environment:

- **Fast local testing**: 1-3 second election timeout
- **CI/CD environments**: 2-5 second election timeout
- **Network partition testing**: 5-10 second election timeout

## Troubleshooting

### Common Issues

1. **Containers fail to start:**
   - Check Docker daemon is running
   - Verify sufficient memory available
   - Check port conflicts

2. **Leader election fails:**
   - Verify all nodes can communicate
   - Check election timeout settings
   - Review container logs for errors

3. **Network partition tests fail:**
   - Ensure containers have required network capabilities
   - Check if iptables/tc commands are available
   - Verify TestContainers has proper permissions

### Debug Commands

```bash
# Check container status
docker-compose ps

# Inspect container configuration
docker inspect quorus-controller1

# Execute commands in container
docker exec -it quorus-controller1 /bin/sh

# Check network connectivity
docker exec quorus-controller1 nc -zv controller2 8080
```

## Integration with CI/CD

### GitHub Actions Example

```yaml
- name: Run Docker Raft Tests
  run: |
    mvn test -Dtest=DockerRaftClusterTest
    mvn test -Dtest=ConfigurableRaftClusterTest
  env:
    TESTCONTAINERS_RYUK_DISABLED: true
```

### Resource Limits for CI

Use the `ciTest()` configuration for resource-constrained environments:

```java
TestClusterConfiguration.ciTest()  // Uses minimal memory settings
```

This infrastructure provides comprehensive testing capabilities for the Raft cluster manager, enabling realistic testing scenarios that closely match production environments.

## Log Aggregation and Monitoring

### Overview

The Quorus Docker infrastructure includes a comprehensive log aggregation system using the **Grafana Loki stack**, providing centralized logging, real-time monitoring, and powerful querying capabilities across all distributed components.

### Log Aggregation Architecture

The log aggregation pipeline follows a multi-layer approach:

```
┌─────────────────┐    ┌─────────────────┐    ┌─────────────────┐
│   Quorus API    │    │ Quorus Controller│    │  Other Services │
│                 │    │                 │    │                 │
│ Java Logging    │    │ Java Logging    │    │ Java Logging    │
│ ↓ STDOUT/STDERR │    │ ↓ STDOUT/STDERR │    │ ↓ STDOUT/STDERR │
└─────────────────┘    └─────────────────┘    └─────────────────┘
         │                       │                       │
         └───────────────────────┼───────────────────────┘
                                 │
                    ┌─────────────────┐
                    │ Docker JSON     │
                    │ Log Driver      │
                    │                 │
                    │ /var/lib/docker/│
                    │ containers/*/   │
                    │ *.log           │
                    └─────────────────┘
                                 │
                    ┌─────────────────┐
                    │   Promtail      │
                    │                 │
                    │ • Monitors logs │
                    │ • Parses JSON   │
                    │ • Adds labels   │
                    │ • Ships to Loki │
                    └─────────────────┘
                                 │
                    ┌─────────────────┐
                    │     Loki        │
                    │                 │
                    │ • Stores logs   │
                    │ • Indexes labels│
                    │ • Provides API  │
                    └─────────────────┘
                                 │
                    ┌─────────────────┐
                    │    Grafana      │
                    │                 │
                    │ • Visualizes    │
                    │ • Dashboards    │
                    │ • Alerts        │
                    └─────────────────┘
```

### Log Extraction Pipeline

#### 1. Application Layer (Java → STDOUT/STDERR)

Quorus applications use **Quarkus logging** configured to output structured logs to standard streams:

```properties
# quorus-api/src/main/resources/application.properties
quarkus.log.level=INFO
quarkus.log.category."dev.mars.quorus".level=DEBUG
quarkus.log.console.enable=true
quarkus.log.console.format=%d{yyyy-MM-dd HH:mm:ss,SSS} %-5p [%c{3.}] (%t) %s%e%n
```

**Example log output:**
```
2025-08-26 10:36:39,337 INFO [dev.mar.quo.api.ser.HeartbeatProcessor] (pool-8-thread-2) Agent degraded: test-agent-002
```

#### 2. Docker Container Layer (JSON File Driver)

Docker captures STDOUT/STDERR using the **JSON file logging driver**:

```yaml
# docker-compose.yml
logging:
  driver: "json-file"
  options:
    max-size: "10m"
    max-file: "3"
labels:
  - "logging=promtail"
  - "service=quorus-api"
```

**Docker stores logs in:** `/var/lib/docker/containers/{container-id}/{container-id}-json.log`

**JSON format:**
```json
{"log":"2025-08-26 10:36:39,337 INFO [dev.mar.quo.api] Message\n","stream":"stdout","time":"2025-08-26T10:36:39.337Z"}
```

#### 3. Promtail Collection Layer

**Promtail** monitors Docker logs and ships them to Loki:

```yaml
# promtail/config.yml
scrape_configs:
  - job_name: quorus-api
    docker_sd_configs:
      - host: unix:///var/run/docker.sock
        filters:
          - name: label
            values: ["logging=promtail"]
    pipeline_stages:
      - json:
          expressions:
            output: log
            stream: stream
            time: time
      - regex:
          expression: '^(?P<timestamp>\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2},\d{3}) (?P<level>\w+)\s+\[(?P<logger>[^\]]+)\] \((?P<thread>[^)]+)\) (?P<message>.*)'
          source: output
      - labels:
          level:
          logger:
          thread:
          service:
          container:
```

### Log Aggregation Components

| Component | Purpose | Port | Configuration |
|-----------|---------|------|---------------|
| **Loki** | Log storage and indexing | 3100 | `loki/config.yml` |
| **Promtail** | Log collection agent | - | `promtail/config.yml` |
| **Grafana** | Log visualization and dashboards | 3000 | `grafana/provisioning/` |
| **Prometheus** | Metrics collection (optional) | 9090 | `prometheus/prometheus.yml` |

### Setting Up Log Aggregation

#### Quick Start

1. **Start the logging stack:**
   ```bash
   # Run the setup script
   cd docker/scripts
   powershell -ExecutionPolicy Bypass -File setup-logging.ps1

   # Or manually start services
   docker-compose -f docker/compose/docker-compose-loki.yml up -d
   ```

2. **Access the services:**
   - **Grafana Dashboard**: http://localhost:3000 (admin/admin)
   - **Loki API**: http://localhost:3100
   - **Prometheus Metrics**: http://localhost:9090

3. **Start Quorus services with logging:**
   ```bash
   docker-compose -f docker/compose/docker-compose.yml up -d
   ```

#### Manual Configuration

1. **Create configuration directories:**
   ```bash
   mkdir -p loki promtail grafana/provisioning/datasources prometheus
   ```

2. **Configure Loki** (`loki/config.yml`):
   ```yaml
   auth_enabled: false
   server:
     http_listen_port: 3100
   common:
     path_prefix: /loki
     storage:
       filesystem:
         chunks_directory: /loki/chunks
         rules_directory: /loki/rules
     replication_factor: 1
   schema_config:
     configs:
       - from: 2020-10-24
         store: boltdb-shipper
         object_store: filesystem
         schema: v11
   ```

3. **Configure Grafana datasources** (`grafana/provisioning/datasources/datasources.yml`):
   ```yaml
   apiVersion: 1
   datasources:
     - name: Loki
       type: loki
       access: proxy
       url: http://loki:3100
       isDefault: true
   ```

### LogQL Queries for Quorus

#### Basic Queries

```logql
# All API logs
{container_name="quorus-api"}

# All controller logs
{container_name=~"quorus-controller.*"}

# Logs from specific service
{service="quorus-api"}
```

#### Filtered Queries

```logql
# Heartbeat processing logs
{container_name="quorus-api"} |= "heartbeat"

# Error logs across all services
{container_name=~"quorus-.*"} |= "ERROR"

# Agent registration events
{container_name="quorus-api"} |= "registration"

# Raft consensus logs
{container_name=~"quorus-.*"} |= "Raft"

# Failed operations
{container_name=~"quorus-.*"} |= "failed"

# Specific log levels
{container_name="quorus-api"} | json | level="ERROR"
```

#### Advanced Queries

```logql
# Rate of errors per minute
rate({container_name=~"quorus-.*"} |= "ERROR"[1m])

# Count of heartbeat failures
count_over_time({container_name="quorus-api"} |= "heartbeat" |= "failed"[5m])

# Logs with specific thread patterns
{container_name="quorus-api"} | regex "\\((?P<thread>[^)]+)\\)" | thread =~ "pool-.*"
```

### Log Processing Pipeline Details

| Stage | Input | Processing | Output |
|-------|-------|------------|--------|
| **Java App** | Application events | Quarkus logging framework | Formatted log lines to STDOUT |
| **Docker** | STDOUT/STDERR streams | JSON file driver | JSON log files with metadata |
| **Promtail** | Docker log files | Parse JSON, extract content, add labels | Structured log entries |
| **Loki** | Log streams from Promtail | Index by labels, store content | Queryable log database |
| **Grafana** | Loki queries | LogQL processing, visualization | Dashboards and alerts |

### Alternative Log Extraction Methods

#### Comparison of Approaches

| Method | Pros | Cons | Recommendation |
|--------|------|------|----------------|
| **Docker JSON Driver** (Current) | ✅ Decoupled, Standard, Reliable | ⚠️ Requires log shipping | ✅ **Recommended** |
| **Direct File Mounting** | ✅ Simple | ❌ File permissions, portability issues | ❌ Not recommended |
| **Sidecar Pattern** | ✅ Flexible | ❌ More containers to manage | ⚠️ For specific use cases |
| **Application-Level Shipping** | ✅ Direct control | ❌ Tight coupling to infrastructure | ❌ Avoid |
| **Docker Logging Drivers** | ✅ Built-in | ⚠️ Driver-specific limitations | ⚠️ Alternative option |

#### Current Implementation Benefits

✅ **Decoupled:** Applications don't know about log aggregation
✅ **Standard:** Uses Docker's built-in logging
✅ **Reliable:** JSON file driver is stable and performant
✅ **Flexible:** Easy to change log aggregation without app changes
✅ **Scalable:** Promtail can handle high log volumes
✅ **Observable:** Full pipeline visibility

### Monitoring and Alerting

#### Grafana Dashboard Setup

1. **Create custom dashboards for Quorus:**
   ```bash
   # Access Grafana
   open http://localhost:3000
   # Login: admin/admin
   ```

2. **Import dashboard templates:**
   - Go to **Dashboards** → **Import**
   - Use dashboard ID `13639` for Loki logs
   - Customize for Quorus-specific metrics

3. **Create alerts:**
   ```logql
   # Alert on high error rate
   rate({container_name=~"quorus-.*"} |= "ERROR"[5m]) > 0.1

   # Alert on agent failures
   count_over_time({container_name="quorus-api"} |= "Agent failed"[1m]) > 0
   ```

#### Key Metrics to Monitor

- **Error Rate:** `rate({container_name=~"quorus-.*"} |= "ERROR"[1m])`
- **Heartbeat Failures:** `{container_name="quorus-api"} |= "heartbeat" |= "failed"`
- **Raft Leader Elections:** `{container_name=~"quorus-.*"} |= "Leader elected"`
- **Agent Registrations:** `{container_name="quorus-api"} |= "Agent registered"`
- **Network Partitions:** `{container_name=~"quorus-.*"} |= "partition"`

### Verification and Testing

#### Verification Commands

```bash
# Check Docker log configuration
docker inspect quorus-api --format="{{.HostConfig.LogConfig}}"

# View raw Docker logs
docker logs quorus-api --tail 10

# Check Promtail is collecting
docker logs quorus-promtail --tail 5

# Query Loki directly
curl "http://localhost:3100/loki/api/v1/query_range?query={container_name=\"quorus-api\"}"

# Test log generation
powershell -ExecutionPolicy Bypass -File demo-logging.ps1
```

#### Log Generation for Testing

```bash
# Generate heartbeat logs
curl -X POST http://localhost:8080/api/v1/agents/heartbeat \
  -H "Content-Type: application/json" \
  -d '{"agentId":"test","timestamp":"2025-08-26T10:00:00Z","sequenceNumber":1,"status":"active"}'

# Generate registration logs
curl -X POST http://localhost:8080/api/v1/agents/register \
  -H "Content-Type: application/json" \
  -d @test-registration.json
```

### Production Considerations

#### Log Retention and Storage

```yaml
# loki/config.yml - Production settings
limits_config:
  retention_period: 168h  # 7 days
  max_query_length: 12000h
  max_query_parallelism: 32

table_manager:
  retention_deletes_enabled: true
  retention_period: 168h
```

#### Performance Tuning

1. **Loki Configuration:**
   ```yaml
   # Increase ingestion limits for high-volume environments
   limits_config:
     ingestion_rate_mb: 16
     ingestion_burst_size_mb: 32
     max_streams_per_user: 10000
   ```

2. **Promtail Configuration:**
   ```yaml
   # Batch settings for better performance
   client:
     batchwait: 1s
     batchsize: 1048576
   ```

3. **Docker Logging:**
   ```yaml
   # Optimize log rotation
   logging:
     driver: "json-file"
     options:
       max-size: "50m"
       max-file: "5"
   ```

#### Security Considerations

1. **Network Security:**
   ```yaml
   # Restrict access to logging services
   networks:
     logging:
       driver: bridge
       internal: true  # No external access
   ```

2. **Authentication:**
   ```yaml
   # Enable Grafana authentication
   environment:
     - GF_SECURITY_ADMIN_PASSWORD=${GRAFANA_PASSWORD}
     - GF_AUTH_ANONYMOUS_ENABLED=false
   ```

### Troubleshooting Log Aggregation

#### Common Issues

1. **Logs not appearing in Loki:**
   ```bash
   # Check Promtail is running and configured
   docker logs quorus-promtail

   # Verify Docker labels are set
   docker inspect quorus-api --format="{{.Config.Labels}}"

   # Test Loki connectivity
   curl http://localhost:3100/ready
   ```

2. **High memory usage:**
   ```bash
   # Check Loki memory usage
   docker stats quorus-loki

   # Reduce retention period
   # Edit loki/config.yml: retention_period: 24h
   ```

3. **Missing log parsing:**
   ```bash
   # Check Promtail pipeline configuration
   docker exec quorus-promtail cat /etc/promtail/config.yml

   # Test regex patterns
   echo "2025-08-26 10:36:39,337 INFO [test] (thread) message" | \
     grep -P '(?P<timestamp>\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2},\d{3})'
   ```

#### Debug Commands

```bash
# Check all logging containers
docker ps --filter "name=quorus-" --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}"

# Inspect log aggregation network
docker network inspect quorus_logging

# View Promtail targets
curl http://localhost:9080/targets

# Check Loki metrics
curl http://localhost:3100/metrics
```

This comprehensive log aggregation system provides enterprise-grade observability for the Quorus distributed system, enabling effective monitoring, debugging, and operational insights across all components.
