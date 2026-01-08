# Quorus Integration Examples

This module contains self-contained examples demonstrating the Quorus file transfer system capabilities. Each example showcases different aspects of the system and can be run independently.

## Available Examples

### 1. BasicTransferExample
**File:** `BasicTransferExample.java`
**Main Class:** `dev.mars.quorus.examples.BasicTransferExample`

Demonstrates fundamental Quorus capabilities:
- Basic HTTP file transfer
- Real-time progress monitoring
- Multiple concurrent transfers
- Error handling and retry mechanisms
- Performance metrics and checksum verification

**Features Showcased:**
- ✅ HTTP/HTTPS file downloads
- ✅ Progress tracking with rate calculation
- ✅ SHA-256 integrity verification
- ✅ Concurrent transfer handling
- ✅ Retry logic for failed transfers
- ✅ Comprehensive error reporting

### 2. InternalNetworkTransferExample
**File:** `InternalNetworkTransferExample.java`
**Main Class:** `dev.mars.quorus.examples.InternalNetworkTransferExample`

Demonstrates corporate network transfer scenarios (primary use case):
- CRM data synchronization to data warehouse
- Multi-department file distribution
- High-throughput backup operations
- Corporate network optimization
- Enterprise monitoring and compliance

**Features Showcased:**
- ✅ Corporate network optimized configurations
- ✅ Multi-department concurrent operations
- ✅ High-throughput data transfers
- ✅ Corporate security and compliance logging
- ✅ Internal network monitoring and reporting
- ✅ Enterprise-grade error handling and recovery

### 3. BasicWorkflowExample
**File:** `BasicWorkflowExample.java`
**Main Class:** `dev.mars.quorus.examples.BasicWorkflowExample`

Demonstrates YAML-based workflow execution:
- YAML workflow parsing and validation
- Variable substitution with `{{variable}}` syntax
- Virtual run execution for safe demonstration
- Progress monitoring and result reporting

### 4. ComplexWorkflowExample
**File:** `ComplexWorkflowExample.java`
**Main Class:** `dev.mars.quorus.examples.ComplexWorkflowExample`

Shows advanced workflow features:
- Multi-stage data processing pipeline
- Complex dependency graphs with parallel execution
- Multiple execution modes (dry run, virtual run)
- Dependency graph analysis and visualization

### 5. WorkflowValidationExample
**File:** `WorkflowValidationExample.java`
**Main Class:** `dev.mars.quorus.examples.WorkflowValidationExample`

Demonstrates comprehensive workflow validation:
- YAML schema validation
- Semantic workflow validation
- Dependency graph validation (circular dependencies, missing dependencies)
- Variable resolution validation
- **Intentional failure tests** to demonstrate validation capabilities

---

## Agent Examples

These examples demonstrate Quorus's distributed agent management capabilities for coordinating file transfers across multiple nodes in a cluster.

### 6. AgentDiscoveryExample
**File:** `AgentDiscoveryExample.java`
**Main Class:** `dev.mars.quorus.examples.AgentDiscoveryExample`

Demonstrates agent discovery and monitoring in a distributed Quorus cluster:
- Agent registration with capabilities and metadata
- Region-based and status-based filtering
- Capability-based agent discovery
- Real-time health monitoring and statistics
- Best agent selection algorithms

**Features Showcased:**
- ✅ Agent registration with full metadata (hostname, address, port, region, datacenter)
- ✅ Discovery by region (us-east, us-west, eu-west, ap-south)
- ✅ Discovery by status (HEALTHY, ACTIVE, DEGRADED, MAINTENANCE)
- ✅ Capability-based agent filtering
- ✅ Fleet-wide statistics and health metrics
- ✅ Best agent selection for optimal performance

### 7. AgentCapabilitiesExample
**File:** `AgentCapabilitiesExample.java`
**Main Class:** `dev.mars.quorus.examples.AgentCapabilitiesExample`

Demonstrates agent capability configuration and matching for intelligent transfer routing:
- Protocol support matching (HTTP, HTTPS, SFTP, S3, AZURE_BLOB, GCS)
- Size and bandwidth constraint handling
- Encryption and compression capability matching
- Custom capability extension
- Capability validation and conflict detection

**Features Showcased:**
- ✅ Protocol-based agent selection
- ✅ Transfer size and bandwidth constraints
- ✅ Encryption capabilities (AES_256, RSA, CHACHA20)
- ✅ Compression support (GZIP, LZ4, ZSTD, BROTLI, SNAPPY)
- ✅ Custom capability keys for specialized requirements
- ✅ Capability validation with detailed error reporting

### 8. DynamicAgentPoolExample
**File:** `DynamicAgentPoolExample.java`
**Main Class:** `dev.mars.quorus.examples.DynamicAgentPoolExample`

Demonstrates intelligent agent pool management with advanced selection strategies:
- Multiple agent selection strategies
- Dynamic pool scaling (scale-up/scale-down)
- Failover and recovery scenarios
- Load balancing with visual metrics
- Health-based agent rotation

**Features Showcased:**
- ✅ ROUND_ROBIN selection strategy
- ✅ LEAST_LOADED selection for optimal distribution
- ✅ CAPABILITY_BASED selection for specialized transfers
- ✅ LOCALITY_AWARE selection for network optimization
- ✅ WEIGHTED_SCORE selection combining multiple factors
- ✅ Dynamic scaling based on load
- ✅ Automatic failover and recovery
- ✅ Visual load distribution bars

---

## Running the Examples

### Prerequisites
- Java 21 or higher
- Maven 3.6 or higher
- Internet connection (examples use httpbin.org for test files)

### Understanding Example Output

The examples use clear visual indicators to help you understand what's happening:

- **✓ EXPECTED:** Indicates successful operations or intentional test failures (validation working correctly)
- **✗ UNEXPECTED:** Indicates actual problems that need investigation
- **INTENTIONAL FAILURE TEST:** Clearly marks tests that are supposed to fail to demonstrate validation

For more details, see [INTENTIONAL-FAILURES.md](INTENTIONAL-FAILURES.md).

### Running from Command Line

#### Run Examples:
```bash
# Basic Transfer Example (default)
mvn exec:java -pl quorus-integration-examples

# Or run specific examples:
mvn exec:java -pl quorus-integration-examples -Dexec.mainClass="dev.mars.quorus.examples.BasicTransferExample"
mvn exec:java -pl quorus-integration-examples -Dexec.mainClass="dev.mars.quorus.examples.InternalNetworkTransferExample"
mvn exec:java -pl quorus-integration-examples -Dexec.mainClass="dev.mars.quorus.examples.BasicWorkflowExample"
mvn exec:java -pl quorus-integration-examples -Dexec.mainClass="dev.mars.quorus.examples.ComplexWorkflowExample"
mvn exec:java -pl quorus-integration-examples -Dexec.mainClass="dev.mars.quorus.examples.WorkflowValidationExample"

# Agent Examples:
mvn exec:java -pl quorus-integration-examples -Dexec.mainClass="dev.mars.quorus.examples.AgentDiscoveryExample"
mvn exec:java -pl quorus-integration-examples -Dexec.mainClass="dev.mars.quorus.examples.AgentCapabilitiesExample"
mvn exec:java -pl quorus-integration-examples -Dexec.mainClass="dev.mars.quorus.examples.DynamicAgentPoolExample"
```

#### Run with Maven compile first:
```bash
mvn compile exec:java -pl quorus-integration-examples
```

### Running from IDE
1. Import the project as a Maven project
2. Navigate to `quorus-integration-examples/src/main/java/dev/mars/quorus/examples/`
3. Right-click on `BasicTransferExample.java`
4. Select "Run BasicTransferExample.main()"

## Example Output

When you run the BasicTransferExample, you'll see output similar to:

```
INFO: === Quorus Basic Transfer Example ===
INFO: Demonstrating fundamental file transfer capabilities
INFO: Configuration loaded: QuorusConfiguration{maxConcurrentTransfers=10, maxRetryAttempts=3, checksumAlgorithm='SHA-256', metricsEnabled=true}

INFO: --- Basic Transfer Example ---
INFO: Starting transfer: https://httpbin.org/bytes/2048
INFO: Monitoring transfer progress...
INFO:   Progress: 50.0% (1024/2048 bytes)
INFO:   Progress: 100.0% (2048/2048 bytes)
INFO: Transfer Results:
INFO:   Status: COMPLETED
INFO:   Success: ✓
INFO:   Bytes transferred: 2048
INFO:   Duration: 1.23s
INFO:   Average rate: 1.67 KB/s
INFO:   SHA-256 checksum: a1b2c3d4e5f6...

INFO: --- Multiple Files Example ---
INFO: Submitting transfer 1: 512 bytes
INFO: Submitting transfer 2: 1024 bytes
INFO: Submitting transfer 3: 4096 bytes
INFO: Waiting for all transfers to complete...
INFO: Transfer 1 result: SUCCESS (512 bytes)
INFO: Transfer 2 result: SUCCESS (1024 bytes)
INFO: Transfer 3 result: SUCCESS (4096 bytes)

INFO: --- Error Handling Example ---
INFO: Starting transfer that will fail (404 error)...
INFO: Error handling result:
INFO:   Status: FAILED
INFO:   Error: Transfer failed after 3 attempts
INFO:   Retry attempts were made as expected

INFO: Shutting down transfer engine...
INFO: === Example completed ===
```

### Agent Discovery Example Output

When you run the AgentDiscoveryExample, you'll see output similar to:

```
╔══════════════════════════════════════════════════════════════════════════════╗
║           QUORUS AGENT DISCOVERY EXAMPLE                                     ║
║           Demonstrating distributed agent discovery and monitoring           ║
╚══════════════════════════════════════════════════════════════════════════════╝

┌──────────────────────────────────────────────────────────────────────────────┐
│ EXAMPLE 1: Agent Registration                                                │
└──────────────────────────────────────────────────────────────────────────────┘
→ Registering agents across multiple regions...
  ✓ Registered: agent-us-east-001 in us-east-1a (HEALTHY)
  ✓ Registered: agent-us-east-002 in us-east-1b (ACTIVE)
  ✓ Registered: agent-us-west-001 in us-west-2a (HEALTHY)
  ✓ Registered: agent-eu-west-001 in eu-west-1a (IDLE)
  ✓ Registered: agent-ap-south-001 in ap-south-1a (DEGRADED)
→ Successfully registered 5 agents

┌──────────────────────────────────────────────────────────────────────────────┐
│ EXAMPLE 4: Fleet Statistics                                                  │
└──────────────────────────────────────────────────────────────────────────────┘
→ Calculating fleet-wide statistics...
  ═══════════════════════════════════════════════════════════════
  QUORUS AGENT FLEET STATISTICS
  ═══════════════════════════════════════════════════════════════
  Total Agents:     5
  By Status:
    HEALTHY:        2 (40.0%)
    ACTIVE:         1 (20.0%)
    IDLE:           1 (20.0%)
    DEGRADED:       1 (20.0%)
  By Region:
    us-east:        2
    us-west:        1
    eu-west:        1
    ap-south:       1
  ═══════════════════════════════════════════════════════════════
```

### Dynamic Agent Pool Example Output

When you run the DynamicAgentPoolExample, you'll see output similar to:

```
╔══════════════════════════════════════════════════════════════════════════════╗
║           QUORUS DYNAMIC AGENT POOL EXAMPLE                                  ║
║           Demonstrating intelligent agent pool management                    ║
╚══════════════════════════════════════════════════════════════════════════════╝

┌──────────────────────────────────────────────────────────────────────────────┐
│ EXAMPLE 3: Load Balancing Simulation                                         │
└──────────────────────────────────────────────────────────────────────────────┘
→ Simulating load distribution across agents...

  Current Load Distribution:
  ┌────────────────────────────────────────────────────────────────────────┐
  │ agent-pool-001: [████████░░░░░░░░░░░░] 40% (4/10 transfers)            │
  │ agent-pool-002: [██████░░░░░░░░░░░░░░] 30% (3/10 transfers)            │
  │ agent-pool-003: [████░░░░░░░░░░░░░░░░] 20% (2/10 transfers)            │
  │ agent-pool-004: [██░░░░░░░░░░░░░░░░░░] 10% (1/10 transfers)            │
  └────────────────────────────────────────────────────────────────────────┘

  Selection Statistics:
    Total Selections: 20
    Most Selected: agent-pool-001 (8 times)
    Least Selected: agent-pool-004 (2 times)
    Distribution Variance: 6.5%
```

## Generated Files

### BasicTransferExample
Creates a `downloads/` directory with:
- `basic-example.bin` - 2KB test file
- `multi-file-512b.bin` - 512 byte test file
- `multi-file-1024b.bin` - 1KB test file
- `multi-file-4096b.bin` - 4KB test file
- `slow-response-example.json` - Response from delay test

### InternalNetworkTransferExample
Creates a `corporate-data/` directory structure with:
```
corporate-data/
├── data-warehouse/
│   └── crm-customer-export.json - CRM data export
├── department-shares/
│   ├── finance-monthly-report.pdf - Finance department report
│   ├── hr-monthly-report.pdf - HR department report
│   └── sales-monthly-report.pdf - Sales department report
└── backup/
    └── production-backup-[timestamp].tar.gz - Backup operation result
```

## Understanding the Examples

### Basic Transfer Flow
1. **Configuration** - Load system configuration
2. **Engine Initialization** - Create transfer engine with specified parameters
3. **Request Creation** - Build transfer request with source URL and destination
4. **Transfer Submission** - Submit request and get future for result
5. **Progress Monitoring** - Monitor transfer progress in real-time
6. **Result Processing** - Handle completion and display metrics
7. **Cleanup** - Shutdown engine gracefully

### Key Components Demonstrated
- **TransferEngine** - Main interface for file transfers
- **TransferRequest** - Immutable request object with builder pattern
- **TransferResult** - Comprehensive result with metrics and status
- **QuorusConfiguration** - System configuration management
- **Progress Tracking** - Real-time transfer monitoring

### Agent Management Components
The agent examples demonstrate these key components from `quorus-core`:
- **AgentInfo** - Agent metadata including hostname, address, port, region, datacenter, capabilities, and status
- **AgentCapabilities** - Protocol support, bandwidth limits, encryption/compression types, custom capabilities
- **AgentStatus** - Agent lifecycle states: REGISTERING, HEALTHY, ACTIVE, IDLE, DEGRADED, OVERLOADED, MAINTENANCE, DRAINING, UNREACHABLE

### Agent Selection Strategies
The DynamicAgentPoolExample demonstrates these selection strategies:
- **ROUND_ROBIN** - Simple rotation through available agents
- **LEAST_LOADED** - Selects agent with lowest current load
- **CAPABILITY_BASED** - Matches agent capabilities to transfer requirements
- **LOCALITY_AWARE** - Prefers agents in same region/datacenter
- **WEIGHTED_SCORE** - Combines multiple factors (load, locality, capabilities)
- **PREFERRED_AGENT** - Sticky selection with fallback

## Next Steps

After running these examples, you can:
1. Explore the `quorus-core` module to understand the implementation
2. Modify the examples to transfer your own files
3. Experiment with different configuration parameters
4. Build your own applications using the Quorus API

## Troubleshooting

### Common Issues
- **Network connectivity**: Examples require internet access to httpbin.org
- **File permissions**: Ensure write permissions in the current directory
- **Java version**: Requires Java 21 or higher
- **Maven version**: Requires Maven 3.6 or higher

### Getting Help
- Check the logs for detailed error messages
- Verify network connectivity to httpbin.org
- Ensure proper Java and Maven versions
- Review the main project documentation in the parent directory
