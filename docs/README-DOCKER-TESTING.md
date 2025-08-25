# Docker-Based Raft Cluster Testing

This document describes the Docker-based testing infrastructure for the Quorus Raft cluster manager, which provides realistic network testing scenarios using actual containers and HTTP communication.

## Overview

The Docker testing infrastructure replaces in-memory testing with real containerized nodes that communicate over HTTP, providing:

- **Real network communication** between Raft nodes
- **Container lifecycle management** (start/stop/restart scenarios)
- **Network partition simulation** using Docker network manipulation
- **Configurable test scenarios** for different cluster sizes and conditions
- **Integration testing** with TestContainers

## Architecture

### Components

1. **HttpRaftTransport** - HTTP-based transport implementation
2. **Docker Compose Configurations** - 3-node and 5-node cluster setups with custom networks
3. **TestContainers Integration** - Automated container orchestration
4. **Network Testing Utils** - Network partition and failure simulation using Docker networks
5. **Test Configuration Management** - Predefined test scenarios
6. **Custom Docker Networks** - Realistic network topology with static IP addresses
7. **Network Test Helper Script** - Command-line utilities for network manipulation

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

docker-compose.yml                          # 3-node cluster with custom networks
docker-compose-5node.yml                   # 5-node cluster configuration
docker-compose-network-test.yml            # Advanced network testing setup
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
   docker-compose up -d
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
   docker-compose down
   ```

### Advanced Network Testing

1. **Start advanced network test environment:**
   ```bash
   docker-compose -f docker-compose-network-test.yml up -d
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
