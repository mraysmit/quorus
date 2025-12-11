# Docker Infrastructure for Quorus

This directory contains all Docker-related configuration files, scripts, and test data for the Quorus distributed file transfer system.

## Directory Structure

```
docker/
├── compose/                          # Docker Compose configurations
│   ├── docker-compose.yml           # Main 3-node cluster
│   ├── docker-compose-5node.yml     # 5-node cluster for testing
│   ├── docker-compose-loki.yml      # Log aggregation stack (Grafana Loki)
│   ├── docker-compose-elk.yml       # ELK stack alternative
│   ├── docker-compose-fluentd.yml   # Fluentd alternative
│   └── docker-compose-network-test.yml # Advanced network testing
├── logging/                          # Log aggregation configurations
│   ├── loki/config.yml              # Loki storage configuration
│   ├── promtail/config.yml          # Log collection configuration
│   ├── grafana/provisioning/        # Grafana datasources and dashboards
│   └── prometheus/prometheus.yml    # Metrics collection configuration
├── scripts/                          # Docker-related automation scripts
│   ├── setup-logging.ps1           # Automated log aggregation setup
│   ├── demo-logging.ps1            # Log aggregation demonstration
│   ├── log-extraction-demo.ps1     # Detailed log pipeline demo
│   └── simple-log-demo.ps1         # Simple logging demonstration
└── test-data/                       # Test data and utilities
    ├── test-heartbeat.json         # Sample heartbeat payload
    ├── test-registration.json      # Sample agent registration
    ├── send-heartbeat.ps1          # Heartbeat testing script
    └── check-agents.ps1            # Agent status checking script
```

## 🚀 Complete Test Network Environment

### Full Network Test Environment

The `docker-compose-full-network.yml` configuration provides a comprehensive test environment that simulates a realistic Quorus file transfer network:

**Architecture:**
- **Control Plane**: 3 Raft controllers + API service
- **Agent Network**: 3 agents in different regions (NYC, London, Tokyo)
- **File Transfer Servers**: FTP, SFTP, HTTP, and SMB servers
- **Test Utilities**: File generators and monitoring tools

**Network Topology:**
```
Control Plane (172.20.0.0/16)
├── Controller 1-3 (Raft cluster)
└── API Service (agent communication)

Agent Network (172.21.0.0/16)
├── Agent NYC (US East)
├── Agent London (EU West)
└── Agent Tokyo (AP Northeast)

Transfer Servers (172.22.0.0/16)
├── FTP Server (port 21)
├── SFTP Server (port 2222)
├── HTTP Server (port 8090)
└── File Generator (test data)
```

### Quick Start - Full Network

```powershell
# Start the complete test environment
.\scripts\start-full-network.ps1 -Build

# Test agent registration and transfers
.\scripts\test-transfers.ps1

# Monitor the environment
docker-compose -f compose/docker-compose-full-network.yml logs -f

# Stop the environment
docker-compose -f compose/docker-compose-full-network.yml down
```

### Service Endpoints

| Service | URL | Credentials |
|---------|-----|-------------|
| API Service | http://localhost:8080 | - |
| Controller 1 | http://localhost:8081 | - |
| Controller 2 | http://localhost:8082 | - |
| Controller 3 | http://localhost:8083 | - |
| HTTP Server | http://localhost:8090 | - |
| FTP Server | ftp://localhost:21 | testuser/testpass |
| SFTP Server | sftp://localhost:2222 | testuser/testpass |

## Quick Start

### 1. Basic Cluster Setup

```bash
# Start the main 3-node cluster
cd docker/compose
docker-compose up -d

# Check cluster health
curl http://localhost:8081/health
curl http://localhost:8082/health
curl http://localhost:8083/health
```

### 2. Log Aggregation Setup

```bash
# Set up log aggregation (from project root)
cd docker/scripts
powershell -ExecutionPolicy Bypass -File setup-logging.ps1

# Access Grafana dashboard
# http://localhost:3000 (admin/admin)
```

### 3. Testing with Sample Data

```bash
# Register a test agent
cd docker/test-data
curl -X POST http://localhost:8080/api/v1/agents/register \
  -H "Content-Type: application/json" \
  -d @test-registration.json

# Send heartbeats
powershell -ExecutionPolicy Bypass -File send-heartbeat.ps1
```

## Available Configurations

### Cluster Configurations

| File | Description | Nodes | Use Case |
|------|-------------|-------|----------|
| `docker-compose.yml` | Main cluster | 3 | Development, basic testing |
| `docker-compose-5node.yml` | Extended cluster | 5 | Advanced testing, fault tolerance |
| `docker-compose-network-test.yml` | Network testing | 5 | Network partition testing |

### Log Aggregation Options

| File | Stack | Description |
|------|-------|-------------|
| `docker-compose-loki.yml` | Grafana Loki | **Recommended** - Lightweight, cost-effective |
| `docker-compose-elk.yml` | ELK Stack | Full-featured, resource intensive |
| `docker-compose-fluentd.yml` | Fluentd + ELK | CNCF standard, flexible |

## Services and Ports

### Core Services
- **Quorus API**: http://localhost:8080
- **Controller 1**: http://localhost:8081
- **Controller 2**: http://localhost:8082
- **Controller 3**: http://localhost:8083

### Log Aggregation Services
- **Grafana**: http://localhost:3000 (admin/admin)
- **Loki**: http://localhost:3100
- **Prometheus**: http://localhost:9090

### Extended Cluster (5-node)
- **Controller 4**: http://localhost:8084
- **Controller 5**: http://localhost:8085

## Common Commands

### Cluster Management
```bash
# Start cluster
docker-compose -f docker/compose/docker-compose.yml up -d

# Stop cluster
docker-compose -f docker/compose/docker-compose.yml down

# View logs
docker-compose -f docker/compose/docker-compose.yml logs -f

# Scale specific service
docker-compose -f docker/compose/docker-compose.yml up -d --scale controller1=2
```

### Log Aggregation
```bash
# Start logging stack
docker-compose -f docker/compose/docker-compose-loki.yml up -d

# Check log collection
docker logs quorus-promtail

# Query logs directly
curl "http://localhost:3100/loki/api/v1/query_range?query={container_name=\"quorus-api\"}"
```

### Testing and Debugging
```bash
# Check container status
docker ps --filter "name=quorus-"

# Inspect networks
docker network ls | grep quorus

# Execute commands in container
docker exec -it quorus-controller1 /bin/sh

# Check resource usage
docker stats --filter "name=quorus-"
```

## Integration with Development

### From Project Root
```bash
# Build and start everything
mvn clean package -DskipTests
docker-compose -f docker/compose/docker-compose.yml up -d
docker-compose -f docker/compose/docker-compose-loki.yml up -d

# Run integration tests
mvn test -Dtest=DockerRaftClusterTest
```

### Environment Variables
Set these in your shell for easier access:
```bash
export QUORUS_DOCKER_DIR="./docker"
export QUORUS_COMPOSE_DIR="$QUORUS_DOCKER_DIR/compose"
export QUORUS_SCRIPTS_DIR="$QUORUS_DOCKER_DIR/scripts"
```

## Troubleshooting

### Common Issues
1. **Port conflicts**: Check if ports 8080-8085, 3000, 3100, 9090 are available
2. **Memory issues**: Ensure at least 2GB RAM available for full stack
3. **Network issues**: Check Docker daemon and network connectivity

### Debug Commands
```bash
# Check Docker daemon
docker version

# Check available resources
docker system df
docker system prune  # Clean up if needed

# Check specific service logs
docker-compose -f docker/compose/docker-compose.yml logs controller1

# Network debugging
docker network inspect quorus_raft-cluster
```

For detailed documentation, see [README-DOCKER-TESTING.md](../docs/QUORUS-DOCKER-TESTING-README.md).
