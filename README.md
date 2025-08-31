# Quorus File Transfer System

[![Java](https://img.shields.io/badge/Java-21+-orange.svg)](https://openjdk.java.net/projects/jdk/21/)
[![Docker](https://img.shields.io/badge/Docker-Ready-blue.svg)](https://www.docker.com/)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)

**Version:** 1.0
**Date:** 2025-08-27
**Author:** Mark Andrew Ray-Smith Cityline Ltd

🚀 **Enterprise-grade distributed file transfer system** with YAML workflows, Raft consensus clustering, real-time monitoring, and mathematical data persistence guarantees.

## 🌟 Key Features

### 🏗️ **Distributed Architecture**
- **5-Node Raft Cluster** with automatic leader election and failover
- **Mathematical data persistence guarantees** during leader changes
- **Sub-second failover** with zero data loss
- **Docker containerized** deployment with health monitoring

### 📊 **Real-time Monitoring & Logging**
- **Grafana dashboards** for live cluster visualization
- **Loki log aggregation** with real-time Raft consensus tracking
- **Comprehensive log integrity validation** (92.3% test success rate)
- **Container-specific log isolation** for troubleshooting

### 🔄 **YAML Workflow Engine**
- **Declarative workflows** with metadata headers and dependency management
- **Variable substitution** with `{{variable}}` syntax
- **Multiple execution modes**: normal, dry run, virtual run
- **Dependency graph validation** and topological sorting

### 🌐 **Enterprise REST API**
- **OpenAPI 3.0 specification** with Swagger UI
- **Role-based access control** (RBAC)
- **Health monitoring** and metrics endpoints
- **Fast Quarkus runtime** with low memory footprint

### 🔒 **Security & Integrity**
- **SHA-256 integrity verification** for all transfers
- **Retry mechanisms** with exponential backoff
- **Thread-safe concurrent operations**
- **Comprehensive error handling** with custom exception hierarchy

## 🚀 Quick Start

### Prerequisites
- **Java 21+** and Docker
- **8GB RAM** recommended for full cluster deployment

### 🐳 **Docker Deployment (Recommended)**
```bash
# Start the complete stack (5-node cluster + monitoring)
docker-compose -f docker/compose/docker-compose.yml up -d
docker-compose -f docker/compose/docker-compose-loki.yml up -d

# Access services
# API: http://localhost:8081
# Grafana: http://localhost:3000 (admin/admin)
# Swagger UI: http://localhost:8081/q/swagger-ui
```

### 📊 **Monitoring & Validation**
```bash
# View real-time Raft logs
./scripts/loki-realtime-viewer.ps1 -RaftOnly -RealTime

# Run comprehensive validation tests
./scripts/test-logging-validation.ps1
./scripts/test-log-integrity.ps1

# Prove metadata persistence during leader changes
./scripts/prove-metadata-persistence.ps1
```

### 💻 **Development Mode**
```bash
# Build and test
mvn clean compile test

# Start API service
mvn quarkus:dev -pl quorus-api

# Run examples
mvn exec:java -pl quorus-integration-examples
```

## 📖 Usage Examples

### 🔄 **YAML Workflow**
```yaml
# enterprise-sync.yaml
metadata:
  name: "Enterprise Data Sync"
  version: "1.0.0"
  description: "Daily synchronization of critical business data"
  type: "enterprise-workflow"
  author: "data-ops@company.com"

spec:
  variables:
    date: "{{TODAY}}"
    source_host: "secure.company.com"
    dest_path: "/data/warehouse"

  transferGroups:
    - name: financial-data
      description: "Download financial reports"
      dependsOn: []
      transfers:
        - name: daily-revenue
          source: "https://{{source_host}}/reports/revenue-{{date}}.csv"
          destination: "{{dest_path}}/revenue-{{date}}.csv"
          protocol: https
        - name: expense-report
          source: "https://{{source_host}}/reports/expenses-{{date}}.csv"
          destination: "{{dest_path}}/expenses-{{date}}.csv"
          protocol: https

    - name: analytics-processing
      description: "Process analytics data"
      dependsOn: ["financial-data"]
      transfers:
        - name: customer-metrics
          source: "https://{{source_host}}/analytics/customers-{{date}}.json"
          destination: "{{dest_path}}/analytics/customers-{{date}}.json"
          protocol: https
```

## Usage

### 🌐 **REST API Usage**
```bash
# Submit a transfer job
curl -X POST http://localhost:8081/api/v1/transfers \
  -H "Content-Type: application/json" \
  -d '{
    "source": "https://example.com/data.zip",
    "destination": "/tmp/data.zip",
    "protocol": "https"
  }'

# Check transfer status
curl http://localhost:8081/api/v1/transfers/{transfer-id}

# Submit YAML workflow
curl -X POST http://localhost:8081/api/v1/workflows \
  -H "Content-Type: application/yaml" \
  --data-binary @enterprise-sync.yaml

# Health check with Raft status
curl http://localhost:8081/health
```

### 🔍 **Monitoring Raft Consensus**
```bash
# View live Raft events with color coding
./scripts/loki-realtime-viewer.ps1 -RaftOnly -RealTime

# Capture leader change evidence
./scripts/capture-leader-change-logs.ps1

# Run comprehensive validation (94.4% success rate)
./scripts/test-logging-validation.ps1 -Verbose
```

## 🏛️ Architecture

### **Distributed Controller Cluster**
```
┌─────────────────┐    ┌─────────────────┐    ┌─────────────────┐
│   Controller 1  │    │   Controller 2  │    │   Controller 3  │
│   (Follower)    │◄──►│    (Leader)     │◄──►│   (Follower)    │
└─────────────────┘    └─────────────────┘    └─────────────────┘
         ▲                        ▲                        ▲
         │                        │                        │
         ▼                        ▼                        ▼
┌─────────────────┐    ┌─────────────────┐    ┌─────────────────┐
│   Controller 4  │◄──►│   Controller 5  │    │   API Gateway   │
│   (Follower)    │    │   (Follower)    │    │   (Quarkus)     │
└─────────────────┘    └─────────────────┘    └─────────────────┘
```

### **Key Components**
- **🗳️ Raft Consensus**: 5-node cluster with automatic leader election
- **📊 Loki + Grafana**: Real-time log aggregation and visualization
- **🔄 Workflow Engine**: YAML-based transfer orchestration
- **🌐 REST API**: Enterprise-grade API with OpenAPI specification
- **🔒 Security**: SHA-256 integrity verification and RBAC

### REST API Usage
```bash
# Create a transfer job
curl -X POST http://localhost:8080/api/v1/transfers \
  -H "Content-Type: application/json" \
  -d '{
    "sourceUri": "https://httpbin.org/bytes/1024",
    "destinationPath": "/tmp/test-file.bin",
    "protocol": "http"
  }'

# Get transfer status
curl http://localhost:8080/api/v1/transfers/{jobId}

# Cancel a transfer
curl -X DELETE http://localhost:8080/api/v1/transfers/{jobId}

# Get service info
curl http://localhost:8080/api/v1/info
```

### Progress Monitoring
```java
// Monitor transfer progress
String jobId = request.getRequestId();
while (true) {
    TransferJob job = engine.getTransferJob(jobId);
    if (job == null || job.getStatus().isTerminal()) {
        break;
    }
    
    System.out.printf("Progress: %.1f%% (%d/%d bytes)%n",
        job.getProgressPercentage() * 100,
        job.getBytesTransferred(),
        job.getTotalBytes());
    
    Thread.sleep(1000);
}
```

## Architecture

### Design Principles
- **Modular Architecture**: Clean separation between core engine and examples
- **Interface-Based Design**: Extensible protocol and storage abstractions
- **Thread Safety**: Concurrent operations with atomic updates
- **Immutable Domain Objects**: Builder patterns for request/result objects
- **Comprehensive Error Handling**: Custom exception hierarchy with retry logic

### Key Components
- **TransferEngine**: Main interface for file transfer operations
- **TransferProtocol**: Pluggable protocol implementations (HTTP/HTTPS)
- **ProgressTracker**: Real-time progress monitoring with rate calculation
- **ChecksumCalculator**: File integrity verification (SHA-256)
- **QuorusConfiguration**: Flexible configuration management
- **WorkflowEngine**: YAML workflow execution and orchestration
- **DependencyGraph**: Transfer group dependency management
- **ExecutionContext**: Workflow execution context with multiple modes
- **RaftNode**: Distributed consensus for controller clustering
- **TransferResource**: REST API endpoints for programmatic access

## Testing

The project includes comprehensive testing across all modules:
- **150+ tests** with high success rate
- **Unit tests** for all core components
- **Integration tests** with real HTTP transfers
- **Workflow validation tests** with YAML parsing
- **Error scenario testing** including retry mechanisms
- **Concurrent operation testing**
- **API endpoint testing** with REST assured
- **Raft consensus testing** for distributed scenarios

## Configuration

The system supports multiple configuration approaches:

### Core Engine Configuration
- Property files (`quorus.properties`)
- System properties (`-Dquorus.transfer.max.concurrent=5`)
- Environment variables
- Programmatic configuration

### YAML Workflow Configuration
- Declarative workflow definitions with metadata headers
- Variable substitution with `{{variable}}` syntax
- Environment variable integration
- Multi-level variable precedence (context > group > global > environment)

### API Service Configuration
- Quarkus application properties
- Environment-specific profiles
- Docker environment variables
- Health check and metrics configuration

### Key Configuration Options
- `quorus.transfer.max.concurrent` - Maximum concurrent transfers (default: 10)
- `quorus.transfer.max.retries` - Maximum retry attempts (default: 3)
- `quorus.transfer.retry.delay.ms` - Retry delay in milliseconds (default: 1000)
- `quorus.file.checksum.algorithm` - Checksum algorithm (default: SHA-256)
- `quarkus.http.port` - API service port (default: 8080)
- `quorus.raft.node.id` - Raft node identifier for clustering
- `quorus.cluster.nodes` - Cluster member configuration

## Contributing

1. Fork the repository
2. Create a feature branch
3. Make your changes
4. Add tests for new functionality
5. Ensure all tests pass
6. Submit a pull request

## License

Licensed under the Apache License, Version 2.0. See LICENSE file for details.

## Support

- Check the examples in `quorus-integration-examples/`
- Review the comprehensive test suite in `quorus-core/src/test/`
- See implementation documentation in `docs/`
