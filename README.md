# Quorus File Transfer System

[![Java](https://img.shields.io/badge/Java-23-orange.svg)](https://openjdk.java.net/projects/jdk/23/)
[![Maven](https://img.shields.io/badge/Maven-3.8+-blue.svg)](https://maven.apache.org/)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)

**Version:** 0.9
**Date:** 2025-08-25
**Author:** Mark Andrew Ray-Smith Cityline Ltd

Enterprise-grade file transfer system with YAML workflows, REST API, distributed architecture, progress tracking, integrity verification, and robust error handling.

## Project Structure

This is a multi-module Maven project with the following structure:

```
quorus/
├── quorus-core/                    # Core transfer engine implementation
│   ├── src/main/java/dev/mars/quorus/
│   │   ├── core/                   # Domain models (TransferJob, TransferRequest, etc.)
│   │   ├── transfer/               # Transfer engine and progress tracking
│   │   ├── protocol/               # Protocol implementations (HTTP/HTTPS)
│   │   ├── storage/                # File management and checksum calculation
│   │   ├── config/                 # Configuration management
│   │   └── core/exceptions/        # Exception hierarchy
│   └── src/test/java/              # Comprehensive unit and integration tests
├── quorus-workflow/                # YAML workflow system
│   ├── src/main/java/dev/mars/quorus/workflow/
│   │   ├── WorkflowEngine.java     # Workflow execution engine
│   │   ├── TransferGroup.java      # Transfer group definitions
│   │   ├── DependencyGraph.java    # Dependency management
│   │   └── YamlWorkflowDefinitionParser.java  # YAML parsing
│   └── src/test/java/              # Workflow system tests
├── quorus-api/                     # REST API service (Quarkus)
│   ├── src/main/java/dev/mars/quorus/api/
│   │   ├── TransferResource.java   # Transfer API endpoints
│   │   ├── HealthResource.java     # Health and status endpoints
│   │   └── dto/                    # Data transfer objects
│   └── src/main/resources/         # API configuration and OpenAPI specs
├── quorus-controller/              # Distributed controller with Raft consensus
│   ├── src/main/java/dev/mars/quorus/controller/
│   │   ├── raft/                   # Raft consensus implementation
│   │   ├── state/                  # Distributed state management
│   │   └── cluster/                # Cluster management
│   └── Dockerfile                  # Container configuration
├── quorus-tenant/                  # Multi-tenant support
├── quorus-integration-examples/    # Self-contained usage examples
│   ├── src/main/java/dev/mars/quorus/examples/
│   │   ├── BasicTransferExample.java
│   │   ├── BasicWorkflowExample.java
│   │   ├── ComplexWorkflowExample.java
│   │   ├── WorkflowValidationExample.java
│   │   └── InternalNetworkTransferExample.java
│   └── README.md                   # Examples documentation
├── docs/                           # Project documentation
├── scripts/                        # Utility scripts
├── docker-compose.yml              # Multi-node deployment
└── pom.xml                         # Parent POM
```

## Modules

### quorus-core
The core implementation of the Quorus file transfer system.

**Key Features:**
-  HTTP/HTTPS file transfers
-  Progress tracking with rate calculation and ETA
-  SHA-256 integrity verification
-  Concurrent transfer support (configurable)
-  Retry mechanisms with exponential backoff
-  Comprehensive error handling
-  Thread-safe operations
-  Extensible protocol architecture

**Dependencies:** None (pure Java implementation)

### quorus-workflow
YAML-based workflow system for complex file transfer orchestration.

**Key Features:**
-  YAML workflow definitions with metadata headers
-  Transfer groups with dependency management
-  Variable substitution with `{{variable}}` syntax
-  Multiple execution modes (normal, dry run, virtual run)
-  Dependency graph validation and topological sorting
-  Error handling with continue-on-error logic
-  Progress tracking and monitoring
-  Schema validation and semantic validation

**Dependencies:** quorus-core, SnakeYAML

### quorus-api
REST API service built with Quarkus for enterprise integration.

**Key Features:**
-  RESTful API with OpenAPI 3.0 specification
-  Role-based access control (RBAC)
-  Health monitoring and metrics endpoints
-  JSON request/response handling
-  Comprehensive error responses
-  API versioning support
-  Fast startup and low memory footprint

**Dependencies:** quorus-core, Quarkus, JAX-RS

### quorus-controller
Distributed controller with Raft consensus for high availability.

**Key Features:**
-  Raft consensus algorithm implementation
-  Leader election and failover mechanisms
-  Distributed state management
-  Cluster membership management
-  Strong consistency guarantees
-  Automatic recovery and healing
-  Docker containerization support

**Dependencies:** quorus-core, Jackson

### quorus-tenant
Multi-tenant support for enterprise deployments.

**Key Features:**
-  Tenant isolation and resource management
-  Namespace-based organization
-  Per-tenant configuration and quotas

**Dependencies:** quorus-core

### quorus-integration-examples
Self-contained examples demonstrating system usage.

**Includes:**
- BasicTransferExample - Comprehensive demonstration of core features
- BasicWorkflowExample - YAML workflow parsing and execution
- ComplexWorkflowExample - Advanced workflow features and dependency management
- WorkflowValidationExample - Comprehensive validation with intentional failure tests
- InternalNetworkTransferExample - Corporate network transfer scenarios
- Clear error handling with distinction between expected and unexpected failures

**Dependencies:** quorus-core, quorus-workflow

## Quick Start

### Prerequisites
- Java 21 or higher
- Maven 3.6 or higher
- Internet connection (for examples)

### Build the Project
```bash
# Clone and build
git clone <repository-url>
cd quorus
mvn clean compile
```

### Run Tests
```bash
# Run all tests
mvn test

# Run tests for specific module
mvn test -pl quorus-core
```

### Run Examples
```bash
# Run the basic transfer example (default)
mvn exec:java -pl quorus-integration-examples

# Or run specific examples:
mvn exec:java -pl quorus-integration-examples -Dexec.mainClass="dev.mars.quorus.examples.BasicTransferExample"
mvn exec:java -pl quorus-integration-examples -Dexec.mainClass="dev.mars.quorus.examples.BasicWorkflowExample"
mvn exec:java -pl quorus-integration-examples -Dexec.mainClass="dev.mars.quorus.examples.ComplexWorkflowExample"
mvn exec:java -pl quorus-integration-examples -Dexec.mainClass="dev.mars.quorus.examples.WorkflowValidationExample"
mvn exec:java -pl quorus-integration-examples -Dexec.mainClass="dev.mars.quorus.examples.InternalNetworkTransferExample"

# Or with compilation
mvn compile exec:java -pl quorus-integration-examples
```

### Start REST API Service
```bash
# Start the API service (Quarkus dev mode)
mvn quarkus:dev -pl quorus-api

# Or build and run
mvn package -pl quorus-api
java -jar quorus-api/target/quarkus-app/quarkus-run.jar

# API will be available at http://localhost:8080
# OpenAPI documentation at http://localhost:8080/q/swagger-ui
```

### Docker Deployment
```bash
# Start multi-node cluster with Docker Compose
docker-compose up -d

# Scale the cluster
docker-compose up -d --scale quorus-controller=3

# View logs
docker-compose logs -f quorus-controller
```

## Usage

### Basic Programmatic Usage
```java
import dev.mars.quorus.config.QuorusConfiguration;
import dev.mars.quorus.core.TransferRequest;
import dev.mars.quorus.core.TransferResult;
import dev.mars.quorus.transfer.SimpleTransferEngine;
import dev.mars.quorus.transfer.TransferEngine;

// Initialize
QuorusConfiguration config = new QuorusConfiguration();
TransferEngine engine = new SimpleTransferEngine(
    config.getMaxConcurrentTransfers(),
    config.getMaxRetryAttempts(),
    config.getRetryDelayMs()
);

// Create transfer request
TransferRequest request = TransferRequest.builder()
    .sourceUri(URI.create("https://example.com/file.zip"))
    .destinationPath(Paths.get("downloads/file.zip"))
    .protocol("http")
    .build();

// Execute transfer
CompletableFuture<TransferResult> future = engine.submitTransfer(request);
TransferResult result = future.get();

// Check result
if (result.isSuccessful()) {
    System.out.println("Transfer completed: " + result.getBytesTransferred() + " bytes");
} else {
    System.err.println("Transfer failed: " + result.getErrorMessage().orElse("Unknown error"));
}

// Cleanup
engine.shutdown(10);
```

### YAML Workflow Usage
```yaml
# simple-workflow.yaml
metadata:
  name: "Daily Data Sync"
  version: "1.0.0"
  description: "Synchronize daily reports from FTP to local storage"
  type: "data-sync-workflow"
  author: "data-team@company.com"

spec:
  variables:
    date: "{{TODAY}}"
    source_host: "ftp.company.com"
    dest_path: "/data/reports"

  transferGroups:
    - name: download-reports
      description: "Download daily reports"
      transfers:
        - name: sales-report
          source: "ftp://{{source_host}}/reports/sales-{{date}}.csv"
          destination: "{{dest_path}}/sales-{{date}}.csv"
          protocol: ftp
        - name: inventory-report
          source: "ftp://{{source_host}}/reports/inventory-{{date}}.csv"
          destination: "{{dest_path}}/inventory-{{date}}.csv"
          protocol: ftp
```

```java
// Execute workflow
WorkflowDefinitionParser parser = new YamlWorkflowDefinitionParser();
WorkflowDefinition workflow = parser.parse(Paths.get("simple-workflow.yaml"));

WorkflowEngine workflowEngine = new SimpleWorkflowEngine(transferEngine);
ExecutionContext context = ExecutionContext.builder()
    .mode(ExecutionContext.ExecutionMode.NORMAL)
    .variables(Map.of("TODAY", "2025-08-25"))
    .build();

CompletableFuture<WorkflowExecution> execution = workflowEngine.execute(workflow, context);
WorkflowExecution result = execution.get();
```

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
