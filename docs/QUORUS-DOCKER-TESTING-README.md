# Docker-Based Testing Infrastructure


**Version:** 2.1
**Date:** 2025-01-11
**Author:** Mark Andrew Ray-Smith Cityline Ltd
**Updated:** 2026-01-11

This document describes the comprehensive Docker-based testing infrastructure for the Quorus distributed file transfer system, including Raft cluster testing, full network simulation with agents, and realistic file transfer scenarios.

> **Note:** This document was updated to reflect changes from the Vert.x 5 migration and the addition of new agent services including JobPollingService and JobStatusReportingService.

## Overview

The Docker testing infrastructure provides comprehensive testing capabilities for the Quorus distributed file transfer system, including:

### Core Testing Capabilities

- **Raft Cluster Testing** - Real containerized nodes with HTTP communication
- **Route Configuration Testing** - Validate route-based transfer orchestration
- **Full Network Simulation** - Complete file transfer network with agents and servers
- **Multi-Protocol Support** - Real SFTP, FTP, HTTP, and SMB implementations
- **Agent Network Testing** - Multi-region agents with different capabilities
- **Trigger Mechanism Testing** - Event-based, time-based, interval-based, batch-based triggers
- **Container lifecycle management** (start/stop/restart scenarios)
- **Network partition simulation** using Docker network manipulation
- **Configurable test scenarios** for different cluster sizes and conditions
- **Integration testing** with TestContainers
- **Centralized log aggregation** using Grafana Loki stack
- **Real-time monitoring and alerting** across all distributed components

### Test Environments

1. **Raft Cluster Testing** - Controller cluster validation
2. **Route Configuration Testing** - Route validation, trigger testing, agent coordination
3. **Full Network Testing** - Complete file transfer network simulation
4. **Protocol Testing** - Real file transfer server implementations
5. **Agent Testing** - Multi-region agent deployment and coordination

## Architecture

### Components

#### Core Infrastructure
1. **HttpRaftTransport** - HTTP-based transport implementation
2. **Docker Compose Configurations** - Multiple environment setups
3. **TestContainers Integration** - Automated container orchestration
4. **Network Testing Utils** - Network partition and failure simulation
5. **Custom Docker Networks** - Realistic network topology with static IP addresses

#### Full Network Components
6. **Quorus Agents** - Standalone agent implementation with real protocol support
7. **File Transfer Servers** - Real FTP, SFTP, HTTP, and SMB servers
8. **Multi-Region Simulation** - Agents deployed across different geographic regions
9. **Protocol Testing Infrastructure** - Real network file transfer testing

#### Monitoring and Observability
10. **Log Aggregation Stack** - Grafana Loki for centralized logging and monitoring
11. **Real-time Observability** - Structured logging, metrics, and alerting capabilities
12. **Health Monitoring** - Service health checks and status reporting

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
│   ├── docker-compose-full-network.yml  # Complete test network with agents and servers
│   ├── docker-compose-5node.yml         # 5-node cluster configuration
│   ├── docker-compose-network-test.yml  # Advanced network testing setup
│   └── docker-compose-loki.yml          # Log aggregation stack
├── agents/                               # Quorus Agent implementation
│   ├── Dockerfile                       # Agent container image
│   ├── pom.xml                         # Agent Maven configuration
│   ├── docker-entrypoint.sh           # Agent startup script
│   ├── src/                            # Agent source code
│   └── config/                         # Agent configuration files
├── logging/                              # Log aggregation configurations
│   ├── loki/config.yml                  # Loki storage configuration
│   ├── promtail/config.yml              # Log collection configuration
│   ├── grafana/provisioning/            # Grafana datasource setup
│   └── prometheus/prometheus.yml        # Metrics collection setup
├── scripts/                              # Docker automation scripts
│   ├── start-full-network.ps1          # Start complete test environment
│   ├── test-transfers.ps1              # Test file transfer scenarios
│   ├── setup-logging.ps1               # Log aggregation setup script
│   └── demo-logging.ps1                # Log aggregation demo script
└── test-data/                            # Test data and utilities
    ├── nginx.conf                      # HTTP server configuration
    ├── test-heartbeat.json             # Sample heartbeat payload
    ├── test-registration.json          # Sample agent registration
    └── send-heartbeat.ps1              # Heartbeat testing script
scripts/network-test-helper.sh             # Network testing utilities
```

## 🚀 Full Network Test Environment

## 🔀 Route Configuration Testing

The route testing environment validates the core route-based transfer orchestration capabilities:

### Route Test Scenarios

#### 1. Controller Startup Route Validation
Tests that routes are properly validated at controller startup:

```powershell
# Start controller cluster with predefined routes
.\docker\scripts\test-route-validation.ps1

# Expected behavior:
# 1. Controller loads routes from configuration repository
# 2. Controller validates each source agent is reachable
# 3. Controller validates each destination agent is reachable
# 4. Routes transition to ACTIVE status only if both agents validated
# 5. Failed validations result in FAILED status with clear error messages
```

**Test Configuration** (`docker/test-data/routes/test-validation.yaml`):
```yaml
apiVersion: v1
kind: RouteConfiguration
metadata:
  name: test-startup-validation
  
spec:
  source:
    agent: agent-test-001  # Must be running
    location: /data/source/
    
  destination:
    agent: agent-test-002  # Must be running
    location: /data/dest/
    
  trigger:
    type: MANUAL
```

#### 2. Event-Based Trigger Testing
Validates file system event detection and automatic transfers:

```powershell
# Start environment with event-based route
.\docker\scripts\test-event-triggers.ps1

# Test creates files and monitors automatic transfers
```

**Test Scenario:**
1. Agent monitors `/data/watched/` directory
2. Test script creates file `test-file-001.txt`
3. Agent detects `FILE_CREATED` event
4. Agent notifies controller
5. Controller evaluates route trigger conditions
6. Controller initiates transfer to destination agent
7. Destination agent writes file to `/data/dest/`
8. Transfer completion logged and route statistics updated

#### 3. Time-Based Trigger Testing
Validates scheduled transfers with cron expressions:

```yaml
apiVersion: v1
kind: RouteConfiguration
metadata:
  name: test-scheduled-transfer
  
spec:
  source:
    agent: agent-test-001
    location: /data/scheduled/
    
  destination:
    agent: agent-test-002
    location: /data/dest/scheduled/
    
  trigger:
    type: TIME_BASED
    schedule:
      cron: "*/5 * * * *"  # Every 5 minutes
      timezone: "UTC"
```

**Test validates:**
- Cron expression parsing and scheduling
- Accurate trigger timing
- Timezone handling
- Multiple scheduled routes don't interfere

#### 4. Batch-Based Trigger Testing
Validates accumulation-based transfers:

```yaml
apiVersion: v1
kind: RouteConfiguration
metadata:
  name: test-batch-transfer
  
spec:
  source:
    agent: agent-test-001
    location: /data/batch/
    
  destination:
    agent: agent-test-002
    location: /data/dest/batch/
    
  trigger:
    type: BATCH_BASED
    batch:
      fileCount: 10
      maxWaitTime: 5M
```

**Test Scenario:**
1. Create 9 files - no transfer triggered
2. Create 10th file - batch transfer triggered immediately
3. Reset and create 5 files
4. Wait 5 minutes - timeout triggers transfer of 5 files

#### 5. Agent Failover Testing
Validates route behavior when agents become unavailable:

```powershell
# Start route with primary and backup agents
.\docker\scripts\test-route-failover.ps1

# Test scenario:
# 1. Route active with agent-primary
# 2. Stop agent-primary container
# 3. Route status changes to DEGRADED
# 4. Controller attempts failover to agent-backup
# 5. Route status returns to ACTIVE with backup agent
# 6. Transfers continue without data loss
```

#### 6. Multi-Route Coordination Testing
Validates multiple routes operating simultaneously:

```powershell
# Start environment with 10 concurrent routes
.\docker\scripts\test-multi-route.ps1

# Validates:
# - 10 routes with different trigger types
# - No interference between routes
# - Resource allocation and scheduling
# - Proper isolation of route state
```

### Route Testing Tools

**Route Status Viewer:**
```powershell
# View all routes and their status
.\docker\scripts\view-routes.ps1

# Output:
# ROUTE                    STATUS         SOURCE           DESTINATION      TRIGGERS
# crm-to-warehouse        ACTIVE         agent-crm-001    agent-wh-001     15
# backup-nightly          ACTIVE         agent-app-001    agent-bk-001     1
# log-aggregation         DEGRADED       agent-web-*      agent-log-001    142
```

**Route Trigger Simulator:**
```powershell
# Manually trigger a route for testing
.\docker\scripts\trigger-route.ps1 -routeName "crm-to-warehouse"

# Simulate batch accumulation
.\docker\scripts\simulate-batch.ps1 -routeName "batch-route" -fileCount 50
```

**Route Performance Testing:**
```powershell
# Test route throughput
.\docker\scripts\test-route-performance.ps1 `
    -routeName "test-route" `
    -fileCount 1000 `
    -fileSizeRange "1MB-10MB"

# Measures:
# - Files per second throughput
# - Average transfer latency
# - Trigger detection time
# - Route overhead
```

## 🚀 Full Network Test Environment

### Complete File Transfer Network

The `docker-compose-full-network.yml` configuration provides a comprehensive test environment that simulates a realistic Quorus file transfer network:

**Architecture:**
- **Control Plane (172.20.0.0/16)**: 3 Raft controllers + API service
- **Agent Network (172.21.0.0/16)**: 3 agents in different regions (NYC, London, Tokyo)
- **File Transfer Servers (172.22.0.0/16)**: FTP, SFTP, HTTP servers with test data
- **Test Infrastructure**: File generators, monitoring, and health checks

**Network Topology:**
```
Control Plane (172.20.0.0/16)
├── Controller 1-3 (Raft cluster)
└── API Service (agent communication)

Agent Network (172.21.0.0/16)
├── Agent NYC (US East) - HTTP,HTTPS,FTP,SFTP
├── Agent London (EU West) - HTTP,HTTPS,SFTP,SMB
└── Agent Tokyo (AP Northeast) - HTTP,HTTPS,FTP

Transfer Servers (172.22.0.0/16)
├── FTP Server (port 21) - testuser/testpass
├── SFTP Server (port 2222) - testuser/testpass
├── HTTP Server (port 8090)
└── File Generator (test data)
```

### Quick Start - Full Network

```powershell
# Start the complete test environment
.\docker\scripts\start-full-network.ps1 -Build

# Test agent registration and transfers
.\docker\scripts\test-transfers.ps1

# Monitor the environment
docker-compose -f docker/compose/docker-compose-full-network.yml logs -f

# Stop the environment
docker-compose -f docker/compose/docker-compose-full-network.yml down
```

### Service Endpoints

| Service | URL | Credentials | Purpose |
|---------|-----|-------------|---------|
| API Service | http://localhost:8080 | - | Agent registration and job management |
| Controller 1 | http://localhost:8081 | - | Raft cluster node |
| Controller 2 | http://localhost:8082 | - | Raft cluster node |
| Controller 3 | http://localhost:8083 | - | Raft cluster node |
| HTTP Server | http://localhost:8090 | - | File download testing |
| FTP Server | ftp://localhost:21 | testuser/testpass | FTP transfer testing |
| SFTP Server | sftp://localhost:2222 | testuser/testpass | SFTP transfer testing |

### API Endpoints (v2.0)

> **Updated**: New endpoints added for job polling and status reporting

| Endpoint | Method | Purpose |
|----------|--------|---------|
| `/api/v1/agents/register` | POST | Register a new agent |
| `/api/v1/agents/heartbeat` | POST | Process agent heartbeat |
| `/api/v1/agents` | GET | List all registered agents |
| `/api/v1/agents/{agentId}/jobs` | GET | Get pending jobs for an agent |
| `/api/v1/jobs/{jobId}/status` | POST | Update job status |
| `/api/v1/transfers` | POST | Submit a transfer request |
| `/api/v1/cluster` | GET | Get cluster status |
| `/api/v1/metrics` | GET | Get system metrics |
| `/health` | GET | Health check endpoint |

### Agent Configuration

| Agent | Region | Protocols | Max Transfers | IP Address |
|-------|--------|-----------|---------------|------------|
| NYC | us-east-1 | HTTP,HTTPS,FTP,SFTP | 5 | 172.21.0.10 |
| London | eu-west-1 | HTTP,HTTPS,SFTP,SMB | 3 | 172.21.0.11 |
| Tokyo | ap-northeast-1 | HTTP,HTTPS,FTP | 4 | 172.21.0.12 |

## Quick Start

### Prerequisites

- Docker and Docker Compose installed
- Java 21+ and Maven 3.9+
- At least 4GB RAM available for containers (full network) or 2GB (basic cluster)

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

### Full Network Testing Scenarios

#### 1. Agent Registration Testing
```powershell
# Start the full network
.\docker\scripts\start-full-network.ps1

# Check agent registration
curl http://localhost:8080/api/v1/agents

# Expected: 3 agents registered with different capabilities
```

#### 2. Multi-Protocol Transfer Testing
```powershell
# Test HTTP transfer
curl -X POST http://localhost:8080/api/v1/transfers \
  -H "Content-Type: application/json" \
  -d '{
    "sourceUri": "http://http-server:8090/shared/timestamp.txt",
    "destinationPath": "/tmp/downloads/http-test.txt",
    "protocol": "http"
  }'

# Test SFTP transfer
curl -X POST http://localhost:8080/api/v1/transfers \
  -H "Content-Type: application/json" \
  -d '{
    "sourceUri": "sftp://testuser:testpass@sftp-server/shared/timestamp.txt",
    "destinationPath": "/tmp/downloads/sftp-test.txt",
    "protocol": "sftp"
  }'

# Test FTP transfer
curl -X POST http://localhost:8080/api/v1/transfers \
  -H "Content-Type: application/json" \
  -d '{
    "sourceUri": "ftp://testuser:testpass@ftp-server/shared/timestamp.txt",
    "destinationPath": "/tmp/downloads/ftp-test.txt",
    "protocol": "ftp"
  }'
```

#### 3. Load Balancing and Regional Testing
```powershell
# Submit multiple transfers to test load distribution
.\docker\scripts\test-transfers.ps1

# Monitor which agents handle which transfers
docker logs quorus-agent-nyc -f
docker logs quorus-agent-london -f
docker logs quorus-agent-tokyo -f
```

#### 4. Failure and Recovery Testing
```powershell
# Test agent failure
docker stop quorus-agent-nyc

# Submit transfers and verify other agents handle them
.\docker\scripts\test-transfers.ps1

# Restart agent and verify it re-registers
docker start quorus-agent-nyc
```

#### 5. Controller Failover Testing
```powershell
# Stop primary controller
docker stop quorus-controller1

# Verify agents continue working with remaining controllers
curl http://localhost:8080/api/v1/agents

# Submit transfers to verify system continues operating
.\docker\scripts\test-transfers.ps1
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

## Quorus Agent Integration Architecture

### Overview

The Docker test environment provides a complete integration between the Quorus control plane and distributed agents, simulating a realistic production deployment with multi-region agents, real protocol implementations, and comprehensive monitoring.

### Recent Architecture Updates (v2.0)

> **Important**: The Quorus agent architecture has been significantly updated with the Vert.x 5 migration and new service implementations.

#### Vert.x 5 Migration

The agent services have been migrated to use Vert.x 5 for improved reactive programming patterns:

- **TransferExecutionService**: Now uses Vert.x for asynchronous operations and non-blocking I/O
- **HeartbeatService**: Uses Vert.x timers instead of ScheduledExecutorService for better event loop integration
- **QuorusAgent**: Updated to use Vert.x instance for all async operations

**Key Changes:**
```java
// Old approach (blocking)
ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);
scheduler.scheduleAtFixedRate(() -> sendHeartbeat(), 0, interval, TimeUnit.MILLISECONDS);

// New approach (Vert.x reactive)
Vertx vertx = Vertx.vertx();
vertx.setPeriodic(interval, id -> sendHeartbeat());
```

#### New Agent Services

The agent now includes these additional services:

| Service | Purpose | Introduced |
|---------|---------|------------|
| **JobPollingService** | Polls controller for pending job assignments | v2.0 |
| **JobStatusReportingService** | Reports job status updates to controller | v2.0 |
| **AgentRegistrationService** | Handles agent registration with controller | v1.0 |
| **HeartbeatService** | Sends periodic heartbeats to controller | v1.0 |
| **TransferExecutionService** | Executes file transfers using protocol handlers | v1.0 |
| **HealthService** | Provides health check endpoints | v1.0 |

#### Controller HTTP Handlers

New HTTP handlers have been added to the controller for agent management:

| Handler | Endpoint | Purpose |
|---------|----------|---------|
| **AgentRegistrationHandler** | POST /agents/register | Agent registration |
| **HeartbeatHandler** | POST /agents/heartbeat | Agent heartbeat processing |
| **AgentJobsHandler** | GET /agents/{agentId}/jobs | Job polling for agents |
| **AgentListHandler** | GET /agents | List all registered agents |
| **JobStatusHandler** | POST /jobs/{jobId}/status | Job status updates |
| **TransferHandler** | POST /transfers | Submit transfer requests |
| **ClusterHandler** | GET /cluster | Cluster status information |
| **MetricsHandler** | GET /metrics | System metrics |

### Agent Integration Components

#### 1. Agent Registration & Discovery

**Agent Startup Process:**
```bash
# Agent reads environment configuration
AGENT_ID=agent-nyc-001
AGENT_REGION=us-east-1
AGENT_DATACENTER=nyc-dc1
CONTROLLER_URL=http://api:8080/api/v1
SUPPORTED_PROTOCOLS=HTTP,HTTPS,FTP,SFTP
MAX_CONCURRENT_TRANSFERS=5
HEARTBEAT_INTERVAL=30000
```

**Registration Flow:**
1. **Agent → API Service**: `POST /agents/register`
2. **API → Raft Controllers**: Submit `AgentCommand.register()`
3. **Raft Consensus**: Agent info replicated across controller cluster
4. **API → Agent**: Registration confirmation with heartbeat interval

**Registration Payload:**
```json
{
  "agentId": "agent-nyc-001",
  "hostname": "agent-nyc",
  "address": "172.21.0.10",
  "port": 8080,
  "version": "1.0.0",
  "region": "us-east-1",
  "datacenter": "nyc-dc1",
  "capabilities": {
    "supportedProtocols": ["HTTP", "HTTPS", "FTP", "SFTP"],
    "maxConcurrentTransfers": 5,
    "maxTransferSize": 9223372036854775807,
    "systemInfo": {
      "operatingSystem": "Linux",
      "architecture": "amd64",
      "javaVersion": "21.0.1",
      "cpuCores": 4,
      "totalMemory": 268435456,
      "availableMemory": 134217728
    },
    "networkInfo": {
      "hostname": "agent-nyc",
      "ipAddress": "172.21.0.10",
      "port": 8080
    }
  }
}
```

#### 2. Heartbeat & Health Monitoring

**Continuous Health Monitoring:**
```json
{
  "agentId": "agent-nyc-001",
  "timestamp": "2025-09-04T14:30:00Z",
  "sequenceNumber": 1234,
  "status": "active",
  "currentJobs": 2,
  "availableCapacity": 3,
  "metrics": {
    "memoryUsed": 134217728,
    "memoryTotal": 268435456,
    "memoryMax": 536870912,
    "cpuCores": 4
  }
}
```

**Agent Health States:**
- ✅ **HEALTHY**: Ready for new jobs (priority: 8)
- 🟢 **IDLE**: No active jobs (priority: 10)
- 🟡 **ACTIVE**: Currently executing transfers (priority: 6)
- ⚠️ **DEGRADED**: Limited capacity (priority: 3)
- 🔴 **OVERLOADED**: At maximum capacity (priority: 0)
- ❌ **UNREACHABLE**: No heartbeat received (priority: 0)
- 🔄 **MAINTENANCE**: Temporarily unavailable (priority: 0)

#### 3. Job Assignment & Load Balancing

**Agent Capability Matrix:**
| Agent | Region | Protocols | Max Transfers | IP Address |
|-------|--------|-----------|---------------|------------|
| NYC | us-east-1 | HTTP,HTTPS,FTP,SFTP | 5 | 172.21.0.10 |
| London | eu-west-1 | HTTP,HTTPS,SFTP,SMB | 3 | 172.21.0.11 |
| Tokyo | ap-northeast-1 | HTTP,HTTPS,FTP | 4 | 172.21.0.12 |

**Job Assignment Logic:**
```java
// Job assignment considers:
// 1. Protocol compatibility
// 2. Current load (available capacity)
// 3. Geographic proximity (region matching)
// 4. Agent health status (priority scoring)
// 5. Historical performance metrics

// Example assignment decision:
TransferRequest sftpJob = {
  protocol: "sftp",
  sourceUri: "sftp://server/file.txt"
}

// Eligible agents: NYC (5 slots), London (3 slots)
// Tokyo excluded (no SFTP support)
// Assignment: Agent with highest priority score
```

**Job Polling Mechanism (JobPollingService):**

> **New in v2.0**: The `JobPollingService` is a dedicated service that polls the controller for pending job assignments.

```java
// JobPollingService implementation
public class JobPollingService {
    // Poll the controller for pending job assignments
    public List<PendingJob> pollForJobs() {
        String url = config.getControllerUrl() + "/agents/" + config.getAgentId() + "/jobs";
        // Returns list of pending jobs matching agent capabilities
    }
}

// PendingJob structure
public static class PendingJob {
    private final String assignmentId;
    private final String jobId;
    private final String agentId;
    private final String sourceUri;
    private final String destinationPath;
    private final long totalBytes;
    private final String description;

    // Convert to TransferRequest for execution
    public TransferRequest toTransferRequest() {
        return TransferRequest.builder()
                .requestId(jobId)
                .sourceUri(URI.create(sourceUri))
                .destinationPath(Paths.get(destinationPath))
                .expectedSize(totalBytes)
                .build();
    }
}
```

**API Endpoint:**
```bash
GET /api/v1/agents/{agentId}/jobs
→ Returns jobs matching agent capabilities
→ Agent evaluates job compatibility
→ Agent accepts suitable jobs
→ Executes transfers using real protocol implementations

# Example request with current capacity
GET /api/v1/agents/agent-nyc-001/jobs?capacity=3&protocols=HTTP,FTP,SFTP
```

**Job Status Reporting (JobStatusReportingService):**

> **New in v2.0**: The `JobStatusReportingService` reports job status updates back to the controller.

```java
// JobStatusReportingService implementation
public class JobStatusReportingService {
    // Report job status updates to the controller
    public void reportAccepted(String jobId);
    public void reportInProgress(String jobId, long bytesTransferred);
    public void reportCompleted(String jobId, long bytesTransferred);
    public void reportFailed(String jobId, String errorMessage);
}

// Status update payload
{
    "agentId": "agent-nyc-001",
    "status": "IN_PROGRESS",  // ACCEPTED, IN_PROGRESS, COMPLETED, FAILED
    "bytesTransferred": 1048576,
    "errorMessage": null
}
```

**API Endpoint:**
```bash
POST /api/v1/jobs/{jobId}/status
Content-Type: application/json

{
    "agentId": "agent-nyc-001",
    "status": "COMPLETED",
    "bytesTransferred": 10485760
}
```

#### 4. Transfer Execution Integration

**Real Protocol Implementations:**
```java
// Agent receives job and executes using real protocols
TransferRequest request = {
  requestId: "transfer-12345",
  sourceUri: "sftp://sftp-server:22/shared/file.txt",
  destinationPath: "/tmp/downloads/file.txt",
  protocol: "sftp"
}

// Agent uses real SFTP implementation (JSch library)
SftpTransferProtocol protocol = new SftpTransferProtocol();
TransferContext context = new TransferContext(job);
TransferResult result = protocol.transfer(request, context);

// Real network connection established:
// 1. SSH handshake with sftp-server:22
// 2. Authentication using testuser/testpass
// 3. SFTP session establishment
// 4. File download with progress tracking
// 5. Local file write to destination
```

**Multi-Protocol Support:**
- **SFTP**: Real SSH connections using JSch library
  - SSH key authentication support
  - Progress tracking and resume capability
  - Error handling and retry logic
- **FTP**: Socket-based FTP client implementation
  - Active and passive mode support
  - Binary and ASCII transfer modes
  - Connection pooling and reuse
- **HTTP/HTTPS**: Standard HTTP client for file downloads
  - Range request support for resume
  - Authentication headers
  - SSL/TLS certificate validation
- **SMB**: (Future) Windows file sharing protocol
  - NTLM authentication
  - Share enumeration and access

**Transfer Execution Flow:**
```java
// 1. Job acceptance
agent.acceptJob(jobId);

// 2. Protocol selection
TransferProtocol protocol = protocolFactory.getProtocol(request.getProtocol());

// 3. Transfer execution with retry logic
for (int attempt = 1; attempt <= maxRetries; attempt++) {
    try {
        TransferResult result = protocol.transfer(request, context);
        if (result.isSuccessful()) {
            agent.reportSuccess(jobId, result);
            break;
        }
    } catch (Exception e) {
        if (attempt == maxRetries) {
            agent.reportFailure(jobId, e);
        } else {
            Thread.sleep(retryDelay * attempt); // Exponential backoff
        }
    }
}
```

#### 5. Network Topology & Isolation

**Segmented Networks:**
```yaml
# Control Plane Network (172.20.0.0/16)
control-plane:
  - Controllers: Raft consensus and state management
  - API Service: Agent communication hub
  - Isolated from direct file server access

# Agent Network (172.21.0.0/16)
agent-network:
  - Agents: Distributed execution nodes
  - Regional deployment simulation
  - Cross-network communication to control plane and file servers

# Transfer Servers Network (172.22.0.0/16)
transfer-servers:
  - File servers: Real protocol endpoints (FTP, SFTP, HTTP)
  - Test data generation and management
  - Isolated from control plane for security
```

**Network Security & Communication:**
```yaml
# Agent network configuration
networks:
  agent-network:
    driver: bridge
    ipam:
      config:
        - subnet: 172.21.0.0/16
          gateway: 172.21.0.1

# Agents can communicate with:
# 1. Control plane (API service) - for registration, heartbeat, job polling
# 2. Transfer servers - for file transfer operations
# 3. Other agents - for future peer-to-peer capabilities
```

**Service Discovery:**
```bash
# Agents discover services through Docker DNS
API_SERVICE=http://api:8080/api/v1
FTP_SERVER=ftp://ftp-server:21
SFTP_SERVER=sftp://sftp-server:22
HTTP_SERVER=http://http-server:8090

# Static IP addressing for predictable networking
agent-nyc:     172.21.0.10
agent-london:  172.21.0.11
agent-tokyo:   172.21.0.12
```

#### 6. Monitoring & Observability

**Agent-Specific Logging:**
```bash
# Agent startup and registration
2025-01-11 14:30:15 INFO [AgentRegistrationService] Agent agent-nyc-001 registered successfully
2025-01-11 14:30:15 INFO [HealthService] Health service started on port 8080
2025-01-11 14:30:15 INFO [TransferExecutionService] Transfer execution service started with 5 max concurrent transfers
2025-01-11 14:30:15 INFO [JobPollingService] Job polling service initialized
2025-01-11 14:30:15 INFO [JobStatusReportingService] Job status reporting service initialized

# Heartbeat monitoring (using Vert.x timers)
2025-01-11 14:30:45 INFO [HeartbeatService] Heartbeat sent successfully for agent agent-nyc-001
2025-01-11 14:30:45 DEBUG [HeartbeatService] Agent metrics: memory=134MB, cpu=4 cores, jobs=2/5

# Job polling and execution
2025-01-11 14:31:00 INFO [JobPollingService] Polling for new transfer jobs...
2025-01-11 14:31:00 DEBUG [JobPollingService] Polled for jobs: found 2 pending jobs
2025-01-11 14:31:01 INFO [JobStatusReportingService] Job status reported: job-12345 -> ACCEPTED
2025-01-11 14:31:05 INFO [TransferExecutionService] Executing transfer: sftp://sftp-server/file.txt -> /tmp/downloads/file.txt
2025-01-11 14:31:06 INFO [JobStatusReportingService] Job status reported: job-12345 -> IN_PROGRESS
2025-01-11 14:31:07 INFO [SftpTransferProtocol] SFTP transfer completed successfully: 1024 bytes in 2.1s
2025-01-11 14:31:07 INFO [JobStatusReportingService] Job status reported: job-12345 -> COMPLETED
```

**Health Endpoints:**
```bash
# Agent health check
curl http://agent-nyc:8080/health
{
  "status": "UP",
  "timestamp": "2025-09-04T14:30:00Z",
  "agentId": "agent-nyc-001",
  "uptime": 3600000
}

# Detailed agent status
curl http://agent-nyc:8080/status
{
  "agentId": "agent-nyc-001",
  "hostname": "agent-nyc",
  "region": "us-east-1",
  "datacenter": "nyc-dc1",
  "version": "1.0.0",
  "supportedProtocols": ["HTTP", "HTTPS", "FTP", "SFTP"],
  "maxConcurrentTransfers": 5,
  "startTime": "2025-09-04T14:00:00Z",
  "currentTime": "2025-09-04T14:30:00Z",
  "runtime": {
    "totalMemory": 268435456,
    "freeMemory": 134217728,
    "maxMemory": 536870912,
    "availableProcessors": 4
  }
}
```

**Centralized Agent Monitoring:**
```bash
# View all registered agents
curl http://localhost:8080/api/v1/agents
[
  {
    "agentId": "agent-nyc-001",
    "status": "HEALTHY",
    "region": "us-east-1",
    "capabilities": {...},
    "lastHeartbeat": "2025-09-04T14:30:00Z"
  },
  {
    "agentId": "agent-lon-001",
    "status": "ACTIVE",
    "region": "eu-west-1",
    "capabilities": {...},
    "lastHeartbeat": "2025-09-04T14:29:58Z"
  }
]
```

### Real-World Testing Scenarios

#### 1. Multi-Region Load Balancing

**Test Case: Protocol-Based Assignment**
```bash
# Submit SFTP transfer
curl -X POST http://localhost:8080/api/v1/transfers \
  -H "Content-Type: application/json" \
  -d '{
    "sourceUri": "sftp://sftp-server/shared/test.txt",
    "destinationPath": "/tmp/downloads/sftp-test.txt",
    "protocol": "sftp"
  }'

# Expected: Assigned to NYC or London agent (both support SFTP)
# Tokyo agent excluded (no SFTP capability)

# Submit FTP transfer
curl -X POST http://localhost:8080/api/v1/transfers \
  -H "Content-Type: application/json" \
  -d '{
    "sourceUri": "ftp://testuser:testpass@ftp-server/shared/test.txt",
    "destinationPath": "/tmp/downloads/ftp-test.txt",
    "protocol": "ftp"
  }'

# Expected: Assigned to NYC or Tokyo agent (both support FTP)
# London agent excluded (no FTP capability)
```

**Test Case: Capacity-Based Assignment**
```bash
# Submit multiple HTTP transfers to test load balancing
for i in {1..10}; do
  curl -X POST http://localhost:8080/api/v1/transfers \
    -H "Content-Type: application/json" \
    -d "{
      \"sourceUri\": \"http://http-server/shared/file-${i}.txt\",
      \"destinationPath\": \"/tmp/downloads/http-${i}.txt\",
      \"protocol\": \"http\"
    }"
done

# Expected distribution:
# - NYC agent: 5 transfers (max capacity)
# - London agent: 3 transfers (max capacity)
# - Tokyo agent: 2 transfers (remaining)
```

#### 2. Failure Recovery Testing

**Test Case: Agent Failure**
```bash
# Stop NYC agent
docker stop quorus-agent-nyc

# Submit new transfers
curl -X POST http://localhost:8080/api/v1/transfers \
  -H "Content-Type: application/json" \
  -d '{
    "sourceUri": "sftp://sftp-server/shared/test.txt",
    "destinationPath": "/tmp/downloads/test.txt",
    "protocol": "sftp"
  }'

# Expected: Job assigned to London agent (only remaining SFTP-capable agent)
# NYC agent marked as UNREACHABLE after heartbeat timeout

# Restart NYC agent
docker start quorus-agent-nyc

# Expected: Agent re-registers and becomes available for new jobs
```

**Test Case: Controller Failover**
```bash
# Stop primary controller
docker stop quorus-controller1

# Verify agents continue working
curl http://localhost:8080/api/v1/agents

# Submit transfers to verify system continues operating
.\docker\scripts\test-transfers.ps1

# Expected:
# - Remaining controllers elect new leader
# - Agents seamlessly switch to new leader
# - No transfer interruption
```

**Test Case: Network Partition**
```bash
# Create network partition (isolate one controller)
docker network disconnect quorus_control-plane quorus-controller3

# Monitor cluster behavior
curl http://localhost:8081/raft/status  # Controller 1
curl http://localhost:8082/raft/status  # Controller 2
curl http://localhost:8083/raft/status  # Controller 3 (isolated)

# Expected:
# - Controllers 1&2 maintain quorum and continue operating
# - Controller 3 becomes follower (no quorum)
# - Agents continue working with available controllers

# Restore network
docker network connect quorus_control-plane quorus-controller3

# Expected: Controller 3 rejoins cluster and syncs state
```

#### 3. Performance and Scalability Testing

**Test Case: Concurrent Transfer Load**
```bash
# Generate high transfer load
for protocol in http ftp sftp; do
  for i in {1..20}; do
    curl -X POST http://localhost:8080/api/v1/transfers \
      -H "Content-Type: application/json" \
      -d "{
        \"sourceUri\": \"${protocol}://server/shared/load-test-${i}.txt\",
        \"destinationPath\": \"/tmp/downloads/${protocol}-${i}.txt\",
        \"protocol\": \"${protocol}\"
      }" &
  done
done

# Monitor agent performance
docker stats quorus-agent-nyc quorus-agent-london quorus-agent-tokyo

# Expected:
# - Jobs distributed based on protocol capabilities
# - Agents respect max concurrent transfer limits
# - Queue management for excess jobs
# - Performance metrics collected
```

**Test Case: Resource Monitoring**
```bash
# Monitor agent resource usage during load
watch -n 1 'curl -s http://agent-nyc:8080/status | jq .runtime'

# Monitor transfer queue depth
watch -n 1 'curl -s http://localhost:8080/api/v1/agents | jq ".[].currentJobs"'

# Expected:
# - Memory usage increases with active transfers
# - CPU utilization correlates with transfer activity
# - Queue depth managed within agent capacity
```

### Integration Benefits

#### Production-Like Behavior
- ✅ **Real Network Communication**: HTTP/REST between all components
- ✅ **Actual Protocol Implementations**: No mocks or simulations
- ✅ **Distributed State Management**: Raft consensus for agent registry
- ✅ **Load Balancing**: Intelligent job distribution based on capabilities
- ✅ **Fault Tolerance**: Agent and controller failure handling
- ✅ **Monitoring**: Complete observability stack with metrics and logs

#### Scalability Validation
- ✅ **Horizontal Scaling**: Add more agents easily with Docker Compose
- ✅ **Regional Distribution**: Multi-datacenter simulation with network isolation
- ✅ **Protocol Diversity**: Different agents support different protocol combinations
- ✅ **Capacity Planning**: Test various load scenarios and resource limits
- ✅ **Performance Profiling**: Real-world performance characteristics

#### Development Benefits
- ✅ **Rapid Iteration**: Quick environment startup and teardown
- ✅ **Isolated Testing**: Each test run uses fresh containers
- ✅ **Debugging Support**: Full logging and monitoring capabilities
- ✅ **Configuration Flexibility**: Easy environment variable changes
- ✅ **CI/CD Integration**: Automated testing in build pipelines

### Usage Examples

#### Quick Start
```powershell
# Start the complete environment
.\docker\scripts\start-full-network.ps1 -Build

# Wait for all services to be ready (about 2 minutes)
# Check service health
curl http://localhost:8080/health  # API service
curl http://localhost:8081/health  # Controller 1
curl http://localhost:8090/health  # HTTP server

# Verify agent registration
curl http://localhost:8080/api/v1/agents
```

#### Test Agent Integration
```powershell
# Run comprehensive transfer tests
.\docker\scripts\test-transfers.ps1

# Monitor agent logs in real-time
docker logs quorus-agent-nyc -f

# Check transfer status
curl http://localhost:8080/api/v1/transfers/{transferId}
```

#### Manual Testing
```bash
# Submit SFTP transfer
curl -X POST http://localhost:8080/api/v1/transfers \
  -H "Content-Type: application/json" \
  -d '{
    "sourceUri": "sftp://testuser:testpass@sftp-server/shared/timestamp.txt",
    "destinationPath": "/tmp/downloads/sftp-timestamp.txt",
    "protocol": "sftp"
  }'

# Submit FTP transfer
curl -X POST http://localhost:8080/api/v1/transfers \
  -H "Content-Type: application/json" \
  -d '{
    "sourceUri": "ftp://testuser:testpass@ftp-server/shared/timestamp.txt",
    "destinationPath": "/tmp/downloads/ftp-timestamp.txt",
    "protocol": "ftp"
  }'

# Submit HTTP transfer
curl -X POST http://localhost:8080/api/v1/transfers \
  -H "Content-Type: application/json" \
  -d '{
    "sourceUri": "http://http-server/shared/timestamp.txt",
    "destinationPath": "/tmp/downloads/http-timestamp.txt",
    "protocol": "http"
  }'
```

#### Environment Management
```powershell
# Stop the environment
docker-compose -f docker/compose/docker-compose-full-network.yml down

# Clean up volumes (fresh start)
docker-compose -f docker/compose/docker-compose-full-network.yml down -v

# View all container logs
docker-compose -f docker/compose/docker-compose-full-network.yml logs -f

# Scale agents (add more instances)
docker-compose -f docker/compose/docker-compose-full-network.yml up -d --scale agent-nyc=2
```

This comprehensive agent integration provides a **complete, production-like test environment** where you can validate the entire Quorus system end-to-end, from agent registration through job execution with real file transfer protocols, distributed state management, and fault tolerance capabilities.
