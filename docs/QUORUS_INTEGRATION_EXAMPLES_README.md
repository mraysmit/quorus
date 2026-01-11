# Quorus Integration Examples

**Version:** 1.0  
**Date:** 2025-08-18  
**Author:** Mark Andrew Ray-Smith Cityline Ltd  
**Updated:** 2026-01-08  

This module contains self-contained examples demonstrating the Quorus file transfer system capabilities. Each example showcases different aspects of the system and can be run independently.

## System Architecture

Quorus is a **route-based distributed file transfer system** where predefined transfer routes are the primary configuration method. The central controller manages a configuration repository that defines routes between agents (e.g., "Agent A → Agent B"). Routes can be configured with various trigger mechanisms to initiate transfers automatically.

**Key Principles:**
- Routes are defined centrally in the controller's configuration repository
- Agents monitor specific locations (folders, endpoints) as defined in their routes
- At startup, the controller validates all agents in each route are active and accessible
- Multiple trigger types support different use cases:
  - **Event-based**: File appearance/modification in monitored locations
  - **Time-based**: Scheduled transfers (cron-like expressions)
  - **Interval-based**: Periodic transfers (every N minutes/hours)
  - **Batch-based**: Transfer when N files accumulate
  - **Size-based**: Transfer when cumulative file size reaches threshold
  - **Manual**: On-demand triggers via API or command
  - **External**: Triggered by external systems or events
- The controller orchestrates distributed transfers between agents according to route configurations

## Available Examples

### 1. RouteBasedTransferExample
**File:** `RouteBasedTransferExample.java`
**Main Class:** `dev.mars.quorus.examples.RouteBasedTransferExample`

Demonstrates the core route-based transfer functionality (PRIMARY USE CASE):
- Route configuration loaded from central repository
- Controller validation of agents at startup
- Multiple trigger types (event-based, time-based, interval-based, batch-based)
- Automatic transfer execution when trigger conditions are met
- Route health monitoring and failover

**Features Showcased:**
- ✅ Route definition and configuration management
- ✅ Controller-to-agent validation handshake at startup
- ✅ Event-based triggers (file appearance/modification)
- ✅ Time-based triggers (scheduled transfers with cron expressions)
- ✅ Interval-based triggers (periodic transfers every N minutes)
- ✅ Batch-based triggers (transfer when N files accumulate)
- ✅ Size-based triggers (transfer when threshold reached)
- ✅ Manual triggers (on-demand execution)
- ✅ Route status monitoring (ACTIVE, DEGRADED, FAILED)
- ✅ Agent health verification before route activation
- ✅ Multi-route configuration and coordination

### 2. BasicTransferExample
**File:** `BasicTransferExample.java`
**Main Class:** `dev.mars.quorus.examples.BasicTransferExample`

Demonstrates fundamental transfer operations within a route context:
- Direct transfer API usage (bypassing route configuration for testing)
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

### 3. InternalNetworkTransferExample
**File:** `InternalNetworkTransferExample.java`
**Main Class:** `dev.mars.quorus.examples.InternalNetworkTransferExample`

Demonstrates route-based corporate network transfer scenarios:
- Route from CRM agent to data warehouse agent
- Multi-department distribution routes (source → multiple destinations)
- High-throughput backup routes with monitoring
- Corporate network route optimization
- Enterprise monitoring and compliance for configured routes

**Features Showcased:**
- ✅ Corporate network optimized configurations
- ✅ Multi-department concurrent operations
- ✅ High-throughput data transfers
- ✅ Corporate security and compliance logging
- ✅ Internal network monitoring and reporting
- ✅ Enterprise-grade error handling and recovery

### 4. BasicWorkflowExample
**File:** `BasicWorkflowExample.java`
**Main Class:** `dev.mars.quorus.examples.BasicWorkflowExample`

Demonstrates YAML-based workflow execution for multi-step route definitions:
- YAML workflow parsing for complex route configurations
- Variable substitution with `{{variable}}` syntax
- Virtual run execution for safe demonstration
- Progress monitoring and result reporting

### 5. ComplexWorkflowExample
**File:** `ComplexWorkflowExample.java`
**Main Class:** `dev.mars.quorus.examples.ComplexWorkflowExample`

Shows advanced workflow features for complex route orchestration:
- Multi-stage data processing pipelines across multiple agents
- Complex route dependency graphs with parallel execution
- Multiple execution modes (dry run, virtual run)
- Route dependency analysis and visualization

### 6. WorkflowValidationExample
**File:** `WorkflowValidationExample.java`
**Main Class:** `dev.mars.quorus.examples.WorkflowValidationExample`

Demonstrates comprehensive route and workflow validation:
- YAML schema validation for route configurations
- Semantic workflow validation
- Route dependency graph validation (circular dependencies, missing agents)
- Variable resolution validation
- **Intentional failure tests** to demonstrate validation capabilities

---

## Agent Examples

These examples demonstrate Quorus's distributed agent management capabilities. Agents are the endpoints in route configurations, responsible for monitoring locations and executing transfers as defined in the controller's route repository.

### 7. AgentDiscoveryExample
**File:** `AgentDiscoveryExample.java`
**Main Class:** `dev.mars.quorus.examples.AgentDiscoveryExample`

Demonstrates agent discovery and validation in a distributed Quorus cluster:
- Agent registration and controller validation handshake
- Region-based and status-based filtering for route definition
- Capability-based agent discovery for route matching
- Real-time health monitoring for route activation decisions
- Agent selection algorithms for optimal route assignment

**Features Showcased:**
- ✅ Agent registration with full metadata (hostname, address, port, region, datacenter)
- ✅ Discovery by region (us-east, us-west, eu-west, ap-south)
- ✅ Discovery by status (HEALTHY, ACTIVE, DEGRADED, MAINTENANCE)
- ✅ Capability-based agent filtering
- ✅ Fleet-wide statistics and health metrics
- ✅ Best agent selection for optimal performance

### 8. AgentCapabilitiesExample
**File:** `AgentCapabilitiesExample.java`
**Main Class:** `dev.mars.quorus.examples.AgentCapabilitiesExample`

Demonstrates agent capability configuration for route compatibility validation:
- Protocol support matching for route endpoints (HTTP, HTTPS, SFTP, S3, AZURE_BLOB, GCS)
- Size and bandwidth constraint validation during route configuration
- Encryption and compression capability matching between route endpoints
- Custom capability extension for specialized route requirements
- Capability validation before route activation

**Features Showcased:**
- ✅ Protocol-based agent selection
- ✅ Transfer size and bandwidth constraints
- ✅ Encryption capabilities (AES_256, RSA, CHACHA20)
- ✅ Compression support (GZIP, LZ4, ZSTD, BROTLI, SNAPPY)
- ✅ Custom capability keys for specialized requirements
- ✅ Capability validation with detailed error reporting

### 9. DynamicAgentPoolExample
**File:** `DynamicAgentPoolExample.java`
**Main Class:** `dev.mars.quorus.examples.DynamicAgentPoolExample`

Demonstrates intelligent agent pool management for route failover and load distribution:
- Multiple agent selection strategies for route endpoints
- Dynamic pool scaling for high-volume routes
- Failover and recovery scenarios when route agents become unavailable
- Load balancing across multiple agents serving the same route endpoint
- Health-based agent rotation for route optimization

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
# Route-Based Transfer Example (PRIMARY - demonstrates core functionality)
mvn exec:java -pl quorus-integration-examples -Dexec.mainClass="dev.mars.quorus.examples.RouteBasedTransferExample"

# Other Transfer Examples:
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
3. Right-click on `RouteBasedTransferExample.java` (recommended) or `BasicTransferExample.java`
4. Select "Run RouteBasedTransferExample.main()" or "Run BasicTransferExample.main()"

## Example Output

### Route-Based Transfer Example Output

When you run the RouteBasedTransferExample, you'll see output similar to:

```
INFO: === Quorus Route-Based Transfer Example ===
INFO: Demonstrating core route-based file transfer functionality
INFO: Loading route configuration from repository...

INFO: --- Controller Startup & Agent Validation ---
INFO: Route loaded: CRM-to-Warehouse (agent-crm-001 → agent-warehouse-001)
INFO:   Trigger: EVENT_BASED (file creation in monitored folder)
INFO: Validating Agent A (agent-crm-001)...
INFO:   ✓ Agent A is ACTIVE and reachable at 192.168.1.10:8080
INFO:   ✓ Monitoring location: /corporate-data/crm/export/
INFO: Validating Agent B (agent-warehouse-001)...
INFO:   ✓ Agent B is ACTIVE and reachable at 192.168.1.20:8080
INFO:   ✓ Target location accessible: /corporate-data/warehouse/import/
INFO: ✓ Route CRM-to-Warehouse ACTIVATED

INFO: Route loaded: Backup-Nightly (agent-app-001 → agent-backup-001)
INFO:   Trigger: TIME_BASED (cron: 0 2 * * * - Daily at 2 AM)
INFO: ✓ Route Backup-Nightly ACTIVATED

INFO: Route loaded: Log-Aggregation (agent-web-* → agent-logs-001)
INFO:   Trigger: INTERVAL_BASED (every 15 minutes)
INFO: ✓ Route Log-Aggregation ACTIVATED

INFO: Route loaded: Batch-Reports (agent-reports-001 → agent-archive-001)
INFO:   Trigger: BATCH_BASED (100 files) OR SIZE_BASED (1 GB)
INFO: ✓ Route Batch-Reports ACTIVATED

INFO: --- Event-Based Transfer Example ---
INFO: Agent A detected new file: customer-export-20260109.json (2.4 MB)
INFO: Initiating route-based transfer: CRM-to-Warehouse
INFO: Transfer progress: 25% (600 KB)
INFO: Transfer progress: 50% (1.2 MB)
INFO: Transfer progress: 75% (1.8 MB)
INFO: Transfer progress: 100% (2.4 MB)
INFO: ✓ File successfully replicated to Agent B

INFO: --- Batch-Based Transfer Example ---
INFO: Batch trigger monitoring: 87/100 files accumulated
INFO: File detected: report-088.pdf
INFO: Batch trigger monitoring: 88/100 files accumulated
...
INFO: File detected: report-100.pdf
INFO: ✓ Batch threshold reached (100 files)
INFO: Initiating batch transfer: Batch-Reports
INFO: Transferring 100 files (245 MB total)
INFO: ✓ Batch transfer completed

INFO: Route statistics:
INFO:   Total routes active: 4
INFO:   Files transferred: 101
INFO:   Total bytes: 247.4 MB
INFO:   Average throughput: 15.2 MB/s

INFO: === Example completed ===
```

### Basic Transfer Example Output

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

### RouteBasedTransferExample
Demonstrates route configuration in `config/routes/` directory:
```
config/
└── routes/
    ├── crm-to-warehouse.yaml - CRM to data warehouse route
    ├── multi-department.yaml - Multi-destination distribution route
    └── backup-route.yaml - High-throughput backup route
```

Simulates file monitoring and transfers in `corporate-data/` directory:
```
corporate-data/
├── crm/export/ - Monitored by Agent A (source)
└── warehouse/import/ - Managed by Agent B (destination)
```

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

### Route-Based Transfer Flow (Primary Architecture)
1. **Route Configuration Loading** - Controller loads routes from central repository
2. **Agent Validation** - Controller validates all agents defined in routes are reachable
3. **Route Activation** - Routes are activated only if all agents pass validation
4. **Trigger Configuration** - Routes are configured with appropriate trigger type(s):
   - Event-based: Source agents monitor locations for file system events
   - Time-based: Scheduler evaluates cron expressions for scheduled transfers
   - Interval-based: Timer tracks elapsed time for periodic transfers
   - Batch-based: Counter tracks file accumulation until threshold met
   - Size-based: Accumulator tracks total file size until threshold met
   - Manual: Route waits for explicit trigger command
5. **Trigger Evaluation** - System continuously evaluates trigger conditions
6. **Automatic Transfer** - Files are transferred when trigger conditions are satisfied
7. **Route Health Monitoring** - Continuous monitoring of route status and agent health
8. **Failover & Recovery** - Automatic failover to backup agents if primary agents fail

### Direct Transfer Flow (Testing/API)
1. **Configuration** - Load system configuration
2. **Engine Initialization** - Create transfer engine with specified parameters
3. **Request Creation** - Build transfer request with source URL and destination
4. **Transfer Submission** - Submit request and get future for result
5. **Progress Monitoring** - Monitor transfer progress in real-time
6. **Result Processing** - Handle completion and display metrics
7. **Cleanup** - Shutdown engine gracefully

### Key Components Demonstrated

#### Route Configuration Components
- **RouteConfiguration** - Defines source and destination agents with trigger parameters
- **RouteRepository** - Central storage for route definitions (YAML-based)
- **RouteValidator** - Validates route configuration before activation
- **RouteStatus** - Tracks route state: CONFIGURED, VALIDATING, ACTIVE, DEGRADED, FAILED, SUSPENDED
- **RouteTrigger** - Defines trigger type and parameters:
  - **EventTrigger** - File system monitoring (create, modify, delete events)
  - **TimeTrigger** - Cron-based scheduling (e.g., "0 2 * * *" for 2 AM daily)
  - **IntervalTrigger** - Periodic execution (e.g., every 15 minutes)
  - **BatchTrigger** - File count thresholds (e.g., transfer every 100 files)
  - **SizeTrigger** - Cumulative size thresholds (e.g., transfer at 1 GB)
  - **ManualTrigger** - Explicit API or command invocation
  - **CompositeTrigger** - Multiple conditions (AND/OR logic)

#### Transfer Components
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
