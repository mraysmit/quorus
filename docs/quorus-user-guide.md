# Quorus User Guide

**Version:** 1.0
**Date:** 2025-04-14
**Author:** Mark Andrew Ray-Smith Cityline Ltd


## Table of Contents

1. [Introduction](#introduction)
2. [System Architecture & Design](#system-architecture--design)
3. [Core Functional Concepts](#core-functional-concepts)
4. [Getting Started](#getting-started)
5. [Basic File Transfers](#basic-file-transfers)
6. [YAML Workflows](#yaml-workflows)
7. [Multi-Tenant Operations](#multi-tenant-operations)
8. [REST API Usage](#rest-api-usage)
9. [Administration Guide](#administration-guide)
10. [Troubleshooting](#troubleshooting)
11. [Best Practices](#best-practices)

## Introduction

Quorus is an enterprise-grade file transfer system designed for internal corporate networks. It provides secure, reliable, and scalable file transfer capabilities with advanced workflow orchestration and multi-tenant support.

### Key Features

#### **Secure File Transfers: Enterprise-Grade Protocol Support**
Quorus provides comprehensive support for multiple transfer protocols, each optimized for specific enterprise use cases:

- **HTTP/HTTPS**: Modern web-based transfers with OAuth2, Bearer tokens, and custom headers. Supports range requests for resume capability, automatic compression (gzip/deflate), and corporate proxy integration. Ideal for API-based data exchanges and cloud service integration.

- **FTP/FTPS**: Traditional file transfer protocol with both active and passive modes. Automatic binary/ASCII mode detection, directory synchronization, and SSL/TLS encryption (FTPS). Perfect for legacy system integration and bulk data transfers.

- **SFTP**: Secure file transfer over SSH with key-based authentication, compression, and multiple concurrent streams. Preserves Unix file permissions and supports directory operations. Essential for secure transfers to Linux/Unix systems.

- **SMB**: Windows file sharing protocol with NTLM/Kerberos authentication, DFS support, and proper file locking. Maintains Windows file attributes and integrates seamlessly with Active Directory. Critical for Windows-based corporate environments.

**Security Features**: All protocols support enterprise authentication methods, certificate validation, and encryption both in transit and at rest. Integration with corporate PKI infrastructure ensures compliance with security policies.

#### **YAML Workflows: Declarative Orchestration Engine**
Transform complex file transfer operations into maintainable, version-controlled workflow definitions:

- **Declarative Syntax**: Define what you want to achieve, not how to achieve it. YAML-based workflows are human-readable, version-controllable, and self-documenting. No programming knowledge required for basic workflows.

- **Complex Dependencies**: Build sophisticated dependency graphs with parallel execution, conditional logic, and error handling. Support for sequential, parallel, and mixed execution strategies with automatic optimization.

- **Variable Substitution**: Powerful templating system with built-in functions for dates, environment variables, and dynamic values. Enables workflow reuse across different environments and tenants.

- **Execution Modes**:
  - *Normal Mode*: Full execution with actual file transfers
  - *Dry Run*: Validation and planning without executing transfers
  - *Virtual Run*: Simulation mode for testing and training

- **Error Handling**: Configurable error handling strategies including fail-fast, continue-on-error, and custom retry policies. Automatic notification and escalation procedures.

**Business Value**: Reduce operational complexity, improve reliability, and enable self-service file transfer operations for business users.

#### **Multi-Tenant Architecture: Enterprise Isolation and Governance**
Comprehensive multi-tenancy designed for large organizations with complex hierarchical structures:

- **Hierarchical Tenants**: Support for enterprise → company → department → team structures with inheritance of policies and quotas. Each level can override parent settings while maintaining governance.

- **Resource Isolation**: Multiple isolation strategies:
  - *Logical Isolation*: Shared infrastructure with namespace separation and access controls
  - *Physical Isolation*: Dedicated infrastructure for sensitive tenants
  - *Hybrid Isolation*: Mix of logical and physical based on data sensitivity

- **Quota Management**: Comprehensive resource quotas including concurrent transfers, bandwidth allocation, storage limits, and API rate limits. Support for burst capacity and time-based quotas (daily/monthly limits).

- **Cross-Tenant Operations**: Controlled data sharing between tenants with approval workflows and audit trails. Essential for partner data exchanges and inter-departmental collaboration.

- **Tenant-Specific Configuration**: Custom authentication providers, storage locations, workflow templates, and security policies per tenant. Enables different business units to operate independently while maintaining central governance.

**Governance Benefits**: Centralized management with distributed control, comprehensive audit trails, and compliance with corporate data governance policies.

#### **High Availability: Fault-Tolerant Distributed Architecture**
Enterprise-grade availability designed to meet stringent SLA requirements:

- **Raft Consensus Clustering**: Distributed consensus algorithm ensuring strong consistency and automatic leader election. Tolerates (N-1)/2 node failures while maintaining service availability.

- **Geographic Distribution**: Multi-availability zone deployment with automatic failover. Supports disaster recovery scenarios including entire data center failures.

- **Zero-Downtime Operations**: Rolling updates, configuration changes, and maintenance operations without service interruption. Graceful node draining and automatic workload redistribution.

- **Split-Brain Prevention**: Quorum-based decision making prevents inconsistent state during network partitions. Minority partitions automatically become read-only to preserve data consistency.

- **Virtual IP Failover**: Seamless client redirection during leader changes with automatic DNS updates and load balancer integration. Clients experience no interruption during failover events.

**Availability Guarantees**: 99.9% uptime SLA with automatic recovery from common failure scenarios and comprehensive monitoring with predictive failure detection.

#### **Progress Tracking: Real-Time Visibility and Analytics**
Comprehensive monitoring and observability for all transfer operations:

- **Real-Time Metrics**: Sub-second progress updates showing bytes transferred, transfer rates, and completion percentages. Live dashboards for operations teams and end users.

- **Predictive Analytics**: Machine learning-enhanced ETA calculations based on historical transfer patterns, network conditions, and file characteristics. Anomaly detection for unusual transfer behavior.

- **Multi-Level Monitoring**:
  - *Transfer Level*: Individual file transfer progress and statistics
  - *Workflow Level*: Overall workflow progress with stage-by-stage breakdown
  - *System Level*: Cluster health, resource utilization, and performance metrics

- **Performance Insights**: Detailed analysis of transfer performance including network latency, throughput optimization recommendations, and bottleneck identification.

- **Historical Analytics**: Long-term trend analysis for capacity planning, performance optimization, and SLA reporting. Integration with enterprise monitoring and alerting systems.

**Operational Benefits**: Proactive issue detection, performance optimization, and comprehensive reporting for business stakeholders and technical teams.

#### **Enterprise Integration: Seamless Corporate Infrastructure Integration**
Deep integration with existing enterprise infrastructure and processes:

- **Corporate Directory Integration**: Native support for Active Directory, LDAP, and other enterprise directory services. Automatic user provisioning and group-based access control.

- **Single Sign-On (SSO)**: Integration with corporate identity providers including SAML, OAuth2, OpenID Connect, and Kerberos. Users authenticate once and access all authorized resources.

- **Compliance Frameworks**: Built-in support for SOX, GDPR, HIPAA, PCI-DSS, and other regulatory requirements. Automated compliance reporting and audit trail generation.

- **Security Integration**:
  - *Certificate Management*: Integration with corporate PKI infrastructure
  - *Key Management*: Support for enterprise key management systems (KMS)
  - *Network Security*: Corporate firewall and proxy integration
  - *Data Classification*: Automatic data classification and handling based on corporate policies

- **Monitoring Integration**: Native integration with enterprise monitoring systems including Prometheus, Grafana, Splunk, and SIEM platforms. Custom metrics and alerting for business-specific requirements.

- **API Integration**: RESTful APIs with OpenAPI documentation for integration with existing business applications, workflow systems, and automation platforms.

**Integration Benefits**: Leverage existing enterprise investments, maintain security and compliance posture, and provide familiar user experience consistent with other corporate applications.

### System Architecture

```mermaid
graph TB
    UI[Web UI / CLI] --> API[Quorus API]
    API --> Controller[Controller Cluster]
    Controller --> Agent1[Transfer Agent 1]
    Controller --> Agent2[Transfer Agent 2]
    Controller --> AgentN[Transfer Agent n]
    
    Controller -.->|Raft Consensus| Controller2[Controller 2]
    Controller -.->|Raft Consensus| Controller3[Controller 3]
    
    Agent1 --> Storage1[Corporate Storage]
    Agent2 --> Storage2[Department Shares]
    AgentN --> StorageN[Data Warehouse]
```

## System Architecture & Design

### Distributed System Architecture

Quorus is built as a **distributed, fault-tolerant system** designed for enterprise-scale file transfer operations. The architecture follows microservices principles with clear separation of concerns and horizontal scalability.

#### Core Components

```mermaid
graph TB
    subgraph "Control Plane"
        LB[Load Balancer/VIP]
        C1[Controller Leader]
        C2[Controller Follower 1]
        C3[Controller Follower 2]
        C1 -.->|Raft Consensus| C2
        C1 -.->|Raft Consensus| C3
    end

    subgraph "Data Plane"
        A1[Transfer Agent 1]
        A2[Transfer Agent 2]
        A3[Transfer Agent 3]
        AN[Transfer Agent N]
    end

    subgraph "API Layer"
        API[Quorus API Gateway]
        WF[Workflow Engine]
        MT[Multi-Tenant Manager]
    end

    subgraph "Storage Layer"
        CS[Corporate Storage]
        DS[Department Shares]
        DW[Data Warehouse]
        BS[Backup Systems]
    end

    LB --> C1
    LB --> C2
    LB --> C3

    API --> C1
    WF --> C1
    MT --> C1

    C1 --> A1
    C1 --> A2
    C1 --> A3
    C1 --> AN

    A1 --> CS
    A2 --> DS
    A3 --> DW
    AN --> BS
```

#### 1. Controller Cluster (Control Plane)

The **Controller Cluster** provides centralized coordination and state management using the Raft consensus algorithm. It serves as the brain of the Quorus system, making all critical decisions and maintaining global state consistency across the distributed infrastructure.

##### Key Responsibilities

**Job Scheduling: Assigns transfer jobs to available agents**

The Job Scheduler is responsible for intelligent assignment of transfer jobs to the most appropriate agents in the fleet:

```java
public class JobScheduler {
    // Scheduling strategies
    public enum SchedulingStrategy {
        ROUND_ROBIN,        // Simple round-robin assignment
        LEAST_LOADED,       // Assign to agent with lowest current load
        CAPABILITY_BASED,   // Match job requirements to agent capabilities
        LOCALITY_AWARE,     // Prefer agents closer to source/destination
        TENANT_AFFINITY     // Prefer agents dedicated to specific tenants
    }

    // Scheduling decision factors
    private SchedulingDecision scheduleJob(TransferJob job) {
        List<Agent> candidateAgents = findEligibleAgents(job);

        // Filter by capabilities
        candidateAgents = filterByCapabilities(candidateAgents, job.getRequiredCapabilities());

        // Filter by tenant quotas
        candidateAgents = filterByTenantQuotas(candidateAgents, job.getTenant());

        // Apply scheduling strategy
        Agent selectedAgent = applySchedulingStrategy(candidateAgents, job);

        return new SchedulingDecision(selectedAgent, job, calculatePriority(job));
    }
}
```

**Scheduling Factors:**
- **Agent Capacity**: Current load, available bandwidth, concurrent transfer limits
- **Protocol Compatibility**: Agent must support the required transfer protocol
- **Geographic Proximity**: Network latency and bandwidth between agent and endpoints
- **Tenant Affinity**: Some agents may be dedicated to specific tenants
- **Resource Quotas**: Ensure tenant quotas are not exceeded
- **Job Priority**: High-priority jobs get preferential scheduling
- **Failure History**: Avoid agents with recent failures for the same job type

**Load Balancing Algorithms:**
```mermaid
graph LR
    subgraph "Scheduling Strategies"
        RR[Round Robin]
        LL[Least Loaded]
        CB[Capability Based]
        LA[Locality Aware]
        TA[Tenant Affinity]
    end

    subgraph "Decision Factors"
        AC[Agent Capacity]
        PC[Protocol Compatibility]
        GP[Geographic Proximity]
        TQ[Tenant Quotas]
        JP[Job Priority]
    end

    RR --> AC
    LL --> AC
    CB --> PC
    LA --> GP
    TA --> TQ
```

**State Management: Maintains cluster-wide state consistency**

The State Management system ensures all controllers have a consistent view of the system state:

```java
public class ClusterStateManager {
    // Core state components
    private final Map<String, Agent> registeredAgents;
    private final Map<String, TransferJob> activeJobs;
    private final Map<String, WorkflowExecution> runningWorkflows;
    private final Map<String, TenantConfiguration> tenantConfigs;
    private final Map<String, ResourceUsage> resourceUsage;

    // State synchronization
    public void applyStateChange(StateChangeEvent event) {
        // Apply change locally
        applyChangeLocally(event);

        // Replicate to followers via Raft
        raftNode.appendEntry(event);

        // Update state version
        incrementStateVersion();

        // Notify state change listeners
        notifyStateChangeListeners(event);
    }

    // State consistency checks
    public StateConsistencyReport checkConsistency() {
        return StateConsistencyReport.builder()
            .agentCount(registeredAgents.size())
            .activeJobCount(activeJobs.size())
            .workflowCount(runningWorkflows.size())
            .stateVersion(getCurrentStateVersion())
            .lastUpdateTime(getLastUpdateTime())
            .build();
    }
}
```

**State Components:**
- **Agent Registry**: All registered agents with their capabilities and status
- **Job Queue**: Pending, active, and completed transfer jobs
- **Workflow State**: Current state of all running workflows
- **Tenant Configuration**: Multi-tenant settings and quotas
- **Resource Usage**: Real-time resource consumption tracking
- **System Metadata**: Configuration, policies, and operational parameters

**State Consistency Guarantees:**
- **Strong Consistency**: All reads return the most recent write
- **Linearizability**: Operations appear to execute atomically
- **Durability**: State changes are persisted and survive failures
- **Partition Tolerance**: System remains consistent during network partitions

**Agent Fleet Management: Monitors and manages transfer agents**

The Agent Fleet Manager maintains a real-time view of all transfer agents and their health:

```java
public class AgentFleetManager {
    // Agent lifecycle management
    public void registerAgent(AgentRegistration registration) {
        Agent agent = createAgentFromRegistration(registration);

        // Validate agent capabilities
        validateAgentCapabilities(agent);

        // Add to fleet
        agentRegistry.register(agent);

        // Start health monitoring
        healthMonitor.startMonitoring(agent);

        // Notify scheduling system
        jobScheduler.onAgentAdded(agent);

        logger.info("Agent registered: {} with capabilities: {}",
                   agent.getId(), agent.getCapabilities());
    }

    // Health monitoring
    public void processHealthReport(String agentId, HealthReport report) {
        Agent agent = agentRegistry.getAgent(agentId);

        // Update agent status
        agent.updateHealth(report);

        // Check for degraded performance
        if (report.getPerformanceScore() < DEGRADED_THRESHOLD) {
            handleDegradedAgent(agent);
        }

        // Check for failure conditions
        if (report.getStatus() == AgentStatus.FAILED) {
            handleFailedAgent(agent);
        }

        // Update load balancing weights
        loadBalancer.updateAgentWeight(agentId, report.getCapacityScore());
    }
}
```

**Agent Monitoring Metrics:**
- **Health Status**: Online, degraded, offline, failed
- **Performance Metrics**: CPU usage, memory usage, network utilization
- **Transfer Statistics**: Active transfers, success rate, average duration
- **Capacity Metrics**: Available bandwidth, concurrent transfer slots
- **Error Rates**: Failed transfers, timeout rates, retry frequencies

**Fleet Management Operations:**
- **Auto-scaling**: Automatically add/remove agents based on demand
- **Load Redistribution**: Move jobs from overloaded to underutilized agents
- **Maintenance Mode**: Gracefully drain agents for maintenance
- **Failure Recovery**: Reassign jobs from failed agents to healthy ones

**Workflow Orchestration: Coordinates complex multi-step workflows**

The Workflow Orchestrator manages the execution of complex, multi-step transfer workflows:

```java
public class WorkflowOrchestrator {
    // Workflow execution engine
    public String executeWorkflow(WorkflowDefinition definition, ExecutionContext context) {
        // Parse and validate workflow
        WorkflowExecutionPlan plan = createExecutionPlan(definition);

        // Create execution instance
        WorkflowExecution execution = new WorkflowExecution(
            generateExecutionId(),
            definition,
            context,
            plan
        );

        // Start execution
        return startExecution(execution);
    }

    // Dependency resolution and execution planning
    private WorkflowExecutionPlan createExecutionPlan(WorkflowDefinition definition) {
        // Build dependency graph
        DependencyGraph graph = dependencyResolver.buildGraph(definition);

        // Check for circular dependencies
        if (graph.hasCycles()) {
            throw new WorkflowValidationException("Circular dependencies detected");
        }

        // Create execution stages
        List<ExecutionStage> stages = graph.topologicalSort()
            .stream()
            .map(this::createExecutionStage)
            .collect(Collectors.toList());

        return new WorkflowExecutionPlan(stages);
    }
}
```

**Workflow Execution Phases:**
1. **Parsing & Validation**: YAML parsing, schema validation, dependency analysis
2. **Planning**: Dependency resolution, execution stage creation, resource allocation
3. **Execution**: Stage-by-stage execution with progress tracking
4. **Monitoring**: Real-time status updates and error handling
5. **Completion**: Final status determination and cleanup

**Orchestration Features:**
- **Dependency Management**: Complex dependency graphs with parallel execution
- **Conditional Execution**: Execute stages based on runtime conditions
- **Error Handling**: Configurable error handling strategies (fail-fast, continue-on-error)
- **Progress Tracking**: Real-time workflow and stage-level progress
- **Resource Management**: Ensure workflows don't exceed tenant quotas

**Resource Allocation: Manages tenant quotas and resource distribution**

The Resource Allocator ensures fair distribution of system resources across tenants:

```java
public class ResourceAllocator {
    // Quota enforcement
    public boolean checkQuotaAvailability(String tenantId, ResourceType resourceType, long requestedAmount) {
        TenantQuota quota = quotaManager.getQuota(tenantId, resourceType);
        ResourceUsage currentUsage = usageTracker.getCurrentUsage(tenantId, resourceType);

        long availableQuota = quota.getLimit() - currentUsage.getAmount();

        if (requestedAmount > availableQuota) {
            // Check for burst capacity
            if (quota.hasBurstCapacity() && requestedAmount <= quota.getBurstLimit()) {
                return authorizeBurstUsage(tenantId, resourceType, requestedAmount);
            }
            return false;
        }

        return true;
    }

    // Resource reservation
    public ResourceReservation reserveResources(String tenantId, ResourceRequest request) {
        // Check quota availability
        if (!checkQuotaAvailability(tenantId, request.getResourceType(), request.getAmount())) {
            throw new QuotaExceededException("Insufficient quota for tenant: " + tenantId);
        }

        // Reserve resources
        ResourceReservation reservation = createReservation(tenantId, request);
        reservationManager.addReservation(reservation);

        // Update usage tracking
        usageTracker.recordReservation(reservation);

        return reservation;
    }
}
```

**Resource Types Managed:**
- **Compute Resources**: CPU cores, memory allocation for processing
- **Network Resources**: Bandwidth allocation, concurrent connections
- **Storage Resources**: Storage quotas, file count limits
- **Transfer Resources**: Concurrent transfers, transfer volume limits
- **API Resources**: Request rate limits, workflow execution limits

##### Raft Consensus Implementation

**Leader Election: Automatic leader selection with (N-1)/2 fault tolerance**

The Raft leader election ensures exactly one leader exists at any time:

```java
public class RaftLeaderElection {
    // Election process
    public void startElection() {
        currentTerm++;
        state = NodeState.CANDIDATE;
        votedFor = nodeId;

        // Vote for self
        int votes = 1;

        // Request votes from other nodes
        for (String nodeId : clusterNodes) {
            if (!nodeId.equals(this.nodeId)) {
                CompletableFuture<VoteResponse> voteResponse =
                    sendVoteRequest(nodeId, currentTerm, lastLogIndex, lastLogTerm);

                voteResponse.thenAccept(response -> {
                    if (response.isVoteGranted()) {
                        votes++;
                        if (votes > clusterSize / 2) {
                            becomeLeader();
                        }
                    }
                });
            }
        }
    }

    // Leader responsibilities
    private void becomeLeader() {
        state = NodeState.LEADER;

        // Initialize leader state
        initializeLeaderState();

        // Start sending heartbeats
        startHeartbeats();

        // Begin processing client requests
        enableClientRequestProcessing();

        logger.info("Node {} became leader for term {}", nodeId, currentTerm);
    }
}
```

**Election Triggers:**
- **Startup**: When a node starts and no leader exists
- **Leader Failure**: When followers stop receiving heartbeats
- **Network Partition**: When a partition loses contact with the leader
- **Manual Trigger**: Administrative command to force re-election

**Election Guarantees:**
- **Safety**: At most one leader per term
- **Liveness**: Eventually a leader will be elected
- **Fault Tolerance**: Tolerates (N-1)/2 node failures
- **Network Partition**: Majority partition can elect a leader

**Log Replication: Ensures all state changes are consistently replicated**

The Raft log replication mechanism ensures all state changes are durably stored and consistently applied:

```java
public class RaftLogReplication {
    // Append entries to followers
    public void replicateEntry(LogEntry entry) {
        // Add entry to local log
        log.append(entry);

        // Replicate to all followers
        for (String followerId : followers) {
            CompletableFuture<AppendEntriesResponse> response =
                sendAppendEntries(followerId, entry);

            response.thenAccept(resp -> {
                if (resp.isSuccess()) {
                    updateFollowerProgress(followerId, entry.getIndex());
                } else {
                    handleReplicationFailure(followerId, resp);
                }
            });
        }
    }

    // Commit entries when majority acknowledges
    private void checkForCommit() {
        for (long index = commitIndex + 1; index <= log.getLastIndex(); index++) {
            int acknowledgments = 1; // Leader counts as acknowledgment

            for (String followerId : followers) {
                if (getFollowerMatchIndex(followerId) >= index) {
                    acknowledgments++;
                }
            }

            // Commit if majority acknowledges
            if (acknowledgments > clusterSize / 2) {
                commitIndex = index;
                applyToStateMachine(log.getEntry(index));
            }
        }
    }
}
```

**Replication Process:**
1. **Entry Creation**: Leader creates log entry for state change
2. **Parallel Replication**: Send entry to all followers simultaneously
3. **Acknowledgment Collection**: Wait for majority acknowledgment
4. **Commit Decision**: Commit entry when majority acknowledges
5. **State Application**: Apply committed entry to state machine
6. **Client Response**: Respond to client after successful commit

**Replication Guarantees:**
- **Durability**: Committed entries survive node failures
- **Consistency**: All nodes apply entries in the same order
- **Majority Agreement**: Entries committed only with majority consent
- **Monotonicity**: Log entries are never removed or reordered

**Split-Brain Prevention: Quorum-based decision making prevents inconsistencies**

Raft prevents split-brain scenarios through strict quorum requirements:

```java
public class QuorumManager {
    // Quorum calculation
    public boolean hasQuorum(Set<String> availableNodes) {
        int requiredQuorum = (clusterSize / 2) + 1;
        return availableNodes.size() >= requiredQuorum;
    }

    // Leadership validation
    public boolean canMaintainLeadership() {
        Set<String> reachableNodes = getReachableNodes();
        reachableNodes.add(nodeId); // Include self

        if (!hasQuorum(reachableNodes)) {
            // Step down as leader
            stepDownAsLeader();
            return false;
        }

        return true;
    }

    // Split-brain detection
    public void detectSplitBrain() {
        if (state == NodeState.LEADER) {
            // Continuously verify quorum
            if (!canMaintainLeadership()) {
                logger.warn("Lost quorum, stepping down as leader");
                state = NodeState.FOLLOWER;
                stopClientRequestProcessing();
            }
        }
    }
}
```

**Split-Brain Prevention Mechanisms:**
- **Quorum Requirements**: All decisions require majority agreement
- **Leadership Validation**: Leaders continuously verify they have quorum
- **Automatic Step-Down**: Leaders step down when they lose quorum
- **Client Request Blocking**: Minority partitions reject client requests

**Network Partition Tolerance: Maintains availability during network issues**

The system handles network partitions gracefully while maintaining consistency:

```java
public class NetworkPartitionHandler {
    // Partition detection
    public void handleNetworkPartition() {
        Set<String> reachableNodes = detectReachableNodes();

        if (hasQuorum(reachableNodes)) {
            // Majority partition - continue operations
            continueAsActivePartition();

            // Mark unreachable nodes as suspected failed
            markNodesAsSuspected(getUnreachableNodes());

        } else {
            // Minority partition - become read-only
            enterReadOnlyMode();

            // Stop accepting write requests
            stopWriteOperations();

            // Continue serving read requests from local state
            enableReadOnlyOperations();
        }
    }

    // Partition recovery
    public void handlePartitionRecovery(Set<String> recoveredNodes) {
        // Verify recovered nodes are legitimate
        authenticateRecoveredNodes(recoveredNodes);

        // Synchronize state with recovered nodes
        synchronizeState(recoveredNodes);

        // Resume normal operations if quorum restored
        if (hasQuorum(getAllNodes())) {
            resumeNormalOperations();
        }
    }
}
```

**Partition Handling Strategies:**
- **Majority Partition**: Continues normal operations
- **Minority Partition**: Becomes read-only, rejects writes
- **Partition Recovery**: Automatic state synchronization
- **Consistency Preservation**: No conflicting state changes

##### High Availability Features

**Virtual IP Failover: Seamless client redirection during leader changes**

Virtual IP failover provides transparent leader changes to clients:

```java
public class VirtualIPManager {
    // VIP management
    public void promoteToLeader() {
        try {
            // Acquire virtual IP
            acquireVirtualIP();

            // Update DNS records
            updateDNSRecords();

            // Start accepting client connections
            startClientListener();

            // Notify load balancer
            notifyLoadBalancer();

            logger.info("Virtual IP acquired, now serving client requests");

        } catch (Exception e) {
            logger.error("Failed to acquire virtual IP", e);
            throw new LeadershipException("Cannot serve as leader without VIP");
        }
    }

    // Graceful failover
    public void stepDownAsLeader() {
        try {
            // Stop accepting new requests
            stopAcceptingNewRequests();

            // Complete in-flight requests
            waitForInFlightRequests();

            // Release virtual IP
            releaseVirtualIP();

            // Update DNS records
            updateDNSRecords();

        } catch (Exception e) {
            logger.error("Error during leader step-down", e);
        }
    }
}
```

**VIP Failover Features:**
- **Automatic Acquisition**: New leader automatically acquires VIP
- **Graceful Release**: Outgoing leader gracefully releases VIP
- **DNS Integration**: Automatic DNS record updates
- **Load Balancer Integration**: Seamless load balancer updates
- **Client Transparency**: Clients see no interruption

**Geographic Distribution: Multi-AZ deployment for disaster recovery**

Geographic distribution provides disaster recovery across multiple availability zones:

```java
public class GeographicDistribution {
    // Multi-AZ deployment
    public void deployMultiAZ() {
        // Deploy controllers across availability zones
        Map<String, String> nodeToAZ = Map.of(
            "controller1", "us-east-1a",
            "controller2", "us-east-1b",
            "controller3", "us-east-1c"
        );

        // Configure cross-AZ networking
        configureCrossAZNetworking(nodeToAZ);

        // Set up data replication
        configureDataReplication(nodeToAZ);

        // Configure disaster recovery procedures
        setupDisasterRecovery(nodeToAZ);
    }

    // Disaster recovery
    public void handleAZFailure(String failedAZ) {
        Set<String> affectedNodes = getNodesInAZ(failedAZ);

        // Mark affected nodes as failed
        markNodesAsFailed(affectedNodes);

        // Trigger leader election if leader was affected
        if (isLeaderInFailedAZ(failedAZ)) {
            triggerLeaderElection();
        }

        // Redistribute workload
        redistributeWorkload(affectedNodes);

        // Notify operations team
        sendDisasterRecoveryAlert(failedAZ);
    }
}
```

**Geographic Distribution Benefits:**
- **Disaster Recovery**: Survive entire availability zone failures
- **Reduced Latency**: Deploy closer to data sources and destinations
- **Regulatory Compliance**: Meet data residency requirements
- **Load Distribution**: Distribute load across geographic regions

**Rolling Updates: Zero-downtime upgrades and maintenance**

Rolling updates enable zero-downtime system upgrades:

```java
public class RollingUpdateManager {
    // Rolling update process
    public void performRollingUpdate(String newVersion) {
        List<String> updateOrder = calculateUpdateOrder();

        for (String nodeId : updateOrder) {
            // Ensure cluster has quorum without this node
            if (!canSafelyUpdateNode(nodeId)) {
                throw new UpdateException("Cannot safely update node " + nodeId);
            }

            // Gracefully drain node
            drainNode(nodeId);

            // Update node
            updateNode(nodeId, newVersion);

            // Wait for node to rejoin cluster
            waitForNodeRejoin(nodeId);

            // Verify cluster health
            verifyClusterHealth();
        }
    }

    // Safe update validation
    private boolean canSafelyUpdateNode(String nodeId) {
        Set<String> remainingNodes = new HashSet<>(allNodes);
        remainingNodes.remove(nodeId);

        // Ensure remaining nodes form quorum
        return hasQuorum(remainingNodes);
    }
}
```

**Rolling Update Process:**
1. **Update Planning**: Calculate safe update order
2. **Node Draining**: Gracefully stop processing on target node
3. **Software Update**: Update node software/configuration
4. **Rejoin Verification**: Ensure node successfully rejoins cluster
5. **Health Validation**: Verify cluster health before next update

**Health Monitoring: Continuous health checks and automatic recovery**

Comprehensive health monitoring enables proactive issue detection and automatic recovery:

```java
public class HealthMonitoringSystem {
    // Health check types
    public enum HealthCheckType {
        BASIC_CONNECTIVITY,     // Basic network connectivity
        RAFT_PARTICIPATION,     // Raft protocol participation
        STATE_CONSISTENCY,      // State machine consistency
        PERFORMANCE_METRICS,    // Performance and resource usage
        EXTERNAL_DEPENDENCIES   // External system dependencies
    }

    // Comprehensive health assessment
    public HealthStatus performHealthCheck(String nodeId) {
        HealthStatus.Builder status = HealthStatus.builder(nodeId);

        // Basic connectivity check
        status.addCheck(BASIC_CONNECTIVITY, checkConnectivity(nodeId));

        // Raft participation check
        status.addCheck(RAFT_PARTICIPATION, checkRaftParticipation(nodeId));

        // State consistency check
        status.addCheck(STATE_CONSISTENCY, checkStateConsistency(nodeId));

        // Performance metrics check
        status.addCheck(PERFORMANCE_METRICS, checkPerformanceMetrics(nodeId));

        // External dependencies check
        status.addCheck(EXTERNAL_DEPENDENCIES, checkExternalDependencies(nodeId));

        return status.build();
    }

    // Automatic recovery actions
    public void handleUnhealthyNode(String nodeId, HealthStatus status) {
        if (status.isCritical()) {
            // Critical failure - immediate action required
            handleCriticalFailure(nodeId, status);
        } else if (status.isDegraded()) {
            // Degraded performance - gradual remediation
            handleDegradedPerformance(nodeId, status);
        }
    }
}
```

**Health Monitoring Features:**
- **Multi-Level Checks**: Basic connectivity to complex state validation
- **Automated Recovery**: Automatic remediation for common issues
- **Escalation Procedures**: Graduated response based on severity
- **Predictive Analytics**: Trend analysis for proactive maintenance
- **Integration**: SNMP, Prometheus, and enterprise monitoring systems

#### 2. Transfer Agents (Data Plane)

**Transfer Agents** are the distributed workhorses that perform actual file transfer operations. They form the data plane of the Quorus architecture, executing transfer jobs assigned by the controller cluster while providing real-time feedback and maintaining high reliability.

##### Agent Capabilities

**Protocol Support: HTTP/HTTPS, FTP, SFTP, SMB, and extensible protocol framework**

The protocol support system provides a unified interface for different transfer protocols while allowing protocol-specific optimizations:

```java
public class ProtocolManager {
    // Protocol registry and factory
    private final Map<String, ProtocolHandler> protocolHandlers;

    public void registerProtocol(String scheme, ProtocolHandler handler) {
        protocolHandlers.put(scheme.toLowerCase(), handler);
        logger.info("Registered protocol handler for scheme: {}", scheme);
    }

    // Protocol selection and optimization
    public ProtocolHandler selectOptimalProtocol(TransferRequest request) {
        String scheme = extractScheme(request.getSourceUri());
        ProtocolHandler handler = protocolHandlers.get(scheme);

        if (handler == null) {
            throw new UnsupportedProtocolException("No handler for scheme: " + scheme);
        }

        // Apply protocol-specific optimizations
        return optimizeForTransfer(handler, request);
    }
}

// HTTP/HTTPS Protocol Implementation
public class HttpProtocolHandler implements ProtocolHandler {
    @Override
    public TransferResult executeTransfer(TransferRequest request) {
        HttpTransferContext context = createTransferContext(request);

        // Configure HTTP client with optimizations
        HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

        // Support for range requests (resume capability)
        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
            .uri(URI.create(request.getSourceUri()))
            .timeout(Duration.ofSeconds(request.getTimeout()));

        // Add range header for resume support
        if (request.getResumeOffset() > 0) {
            requestBuilder.header("Range", "bytes=" + request.getResumeOffset() + "-");
        }

        // Execute transfer with progress tracking
        return executeWithProgressTracking(client, requestBuilder.build(), context);
    }

    // HTTP-specific features
    private void configureHttpOptimizations(HttpClient.Builder builder, TransferRequest request) {
        // Connection pooling
        builder.connectionTimeout(Duration.ofSeconds(30));

        // Compression support
        if (request.isCompressionEnabled()) {
            builder.header("Accept-Encoding", "gzip, deflate");
        }

        // Authentication
        if (request.hasCredentials()) {
            configureAuthentication(builder, request.getCredentials());
        }
    }
}
```

**Protocol-Specific Features:**

*HTTP/HTTPS Protocol:*
- **Range Requests**: Support for partial content and resume capability
- **Compression**: Automatic gzip/deflate compression handling
- **Authentication**: OAuth2, Basic Auth, Bearer tokens, custom headers
- **Redirects**: Automatic redirect following with loop detection
- **Connection Pooling**: Efficient connection reuse for multiple transfers
- **Proxy Support**: Corporate proxy integration with authentication

*FTP Protocol:*
- **Active/Passive Mode**: Automatic mode selection based on network topology
- **Binary/ASCII Transfer**: Automatic mode detection based on content type
- **Directory Operations**: Recursive directory transfers and synchronization
- **Resume Support**: Restart interrupted transfers from last position
- **Secure FTP**: FTPS (FTP over SSL/TLS) support

*SFTP Protocol:*
- **Key-Based Authentication**: SSH key authentication with passphrase support
- **Compression**: SSH compression for improved transfer speeds
- **Concurrent Streams**: Multiple parallel streams within single SSH connection
- **Directory Synchronization**: Efficient directory comparison and sync
- **File Permissions**: Preserve Unix file permissions and ownership

*SMB Protocol:*
- **Windows Authentication**: NTLM, Kerberos, and domain authentication
- **Share Enumeration**: Automatic discovery of available shares
- **File Locking**: Proper file locking to prevent corruption
- **Extended Attributes**: Preserve Windows file attributes and metadata
- **DFS Support**: Distributed File System path resolution

**Concurrent Transfers: Configurable parallelism based on agent capacity**

The concurrent transfer system manages multiple simultaneous transfers while respecting resource constraints:

```java
public class ConcurrentTransferManager {
    private final ExecutorService transferExecutor;
    private final Semaphore transferSlots;
    private final Map<String, TransferExecution> activeTransfers;
    private final TransferCapacityManager capacityManager;

    public ConcurrentTransferManager(AgentConfiguration config) {
        // Configure thread pool based on agent capacity
        int maxConcurrentTransfers = config.getMaxConcurrentTransfers();
        this.transferExecutor = Executors.newFixedThreadPool(
            maxConcurrentTransfers,
            new ThreadFactoryBuilder()
                .setNameFormat("transfer-worker-%d")
                .setDaemon(true)
                .build()
        );

        // Semaphore to control concurrent transfers
        this.transferSlots = new Semaphore(maxConcurrentTransfers);
        this.activeTransfers = new ConcurrentHashMap<>();
        this.capacityManager = new TransferCapacityManager(config);
    }

    // Submit transfer for execution
    public CompletableFuture<TransferResult> submitTransfer(TransferJob job) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // Acquire transfer slot
                transferSlots.acquire();

                // Check capacity constraints
                if (!capacityManager.canAcceptTransfer(job)) {
                    throw new CapacityExceededException("Agent at capacity");
                }

                // Execute transfer
                return executeTransfer(job);

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new TransferException("Transfer interrupted", e);
            } finally {
                // Release transfer slot
                transferSlots.release();
                activeTransfers.remove(job.getId());
            }
        }, transferExecutor);
    }

    // Dynamic capacity management
    public void adjustCapacity(CapacityAdjustment adjustment) {
        int currentCapacity = transferSlots.availablePermits() + activeTransfers.size();
        int newCapacity = adjustment.getNewCapacity();

        if (newCapacity > currentCapacity) {
            // Increase capacity
            transferSlots.release(newCapacity - currentCapacity);
        } else if (newCapacity < currentCapacity) {
            // Decrease capacity (gracefully)
            int toReduce = currentCapacity - newCapacity;
            transferSlots.acquireUninterruptibly(toReduce);
        }

        logger.info("Adjusted transfer capacity from {} to {}", currentCapacity, newCapacity);
    }
}
```

**Capacity Management Features:**
- **Dynamic Scaling**: Adjust concurrent transfer limits based on performance
- **Resource Monitoring**: Monitor CPU, memory, and network utilization
- **Backpressure Handling**: Gracefully handle capacity constraints
- **Priority Queuing**: High-priority transfers get preferential treatment
- **Load Balancing**: Distribute transfers across available resources

**Capacity Calculation Factors:**
```java
public class TransferCapacityCalculator {
    public int calculateOptimalCapacity(AgentResources resources) {
        // Base capacity on available resources
        int cpuBasedCapacity = resources.getAvailableCpuCores() * 2;
        int memoryBasedCapacity = (int) (resources.getAvailableMemoryMB() / 256);
        int networkBasedCapacity = (int) (resources.getNetworkBandwidthMbps() / 10);

        // Take the minimum to avoid resource exhaustion
        int baseCapacity = Math.min(cpuBasedCapacity,
                          Math.min(memoryBasedCapacity, networkBasedCapacity));

        // Apply safety factor
        return (int) (baseCapacity * 0.8); // 80% utilization target
    }
}
```

**Progress Tracking: Real-time transfer monitoring with rate calculation**

The progress tracking system provides comprehensive real-time monitoring of transfer operations:

```java
public class ProgressTracker {
    private final Map<String, TransferProgress> activeTransfers;
    private final ScheduledExecutorService progressReporter;
    private final MetricsCollector metricsCollector;

    // Progress tracking for a transfer
    public void trackTransfer(String jobId, TransferJob job) {
        TransferProgress progress = new TransferProgress(jobId, job);
        activeTransfers.put(jobId, progress);

        // Start periodic progress reporting
        ScheduledFuture<?> reportingTask = progressReporter.scheduleAtFixedRate(
            () -> reportProgress(jobId),
            1, 1, TimeUnit.SECONDS
        );

        progress.setReportingTask(reportingTask);
    }

    // Update progress metrics
    public void updateProgress(String jobId, long bytesTransferred) {
        TransferProgress progress = activeTransfers.get(jobId);
        if (progress != null) {
            progress.updateBytesTransferred(bytesTransferred);

            // Calculate transfer rate
            long elapsedMs = progress.getElapsedTimeMs();
            if (elapsedMs > 0) {
                double transferRate = (double) bytesTransferred / elapsedMs * 1000; // bytes/sec
                progress.setTransferRate(transferRate);

                // Calculate ETA
                long remainingBytes = progress.getTotalBytes() - bytesTransferred;
                if (transferRate > 0) {
                    long etaSeconds = (long) (remainingBytes / transferRate);
                    progress.setEstimatedTimeRemaining(Duration.ofSeconds(etaSeconds));
                }
            }

            // Update metrics
            metricsCollector.recordProgress(progress);
        }
    }
}

public class TransferProgress {
    private final String jobId;
    private final long totalBytes;
    private final Instant startTime;

    private volatile long bytesTransferred;
    private volatile double transferRate; // bytes per second
    private volatile Duration estimatedTimeRemaining;
    private volatile TransferPhase currentPhase;

    // Progress calculations
    public double getCompletionPercentage() {
        if (totalBytes == 0) return 0.0;
        return (double) bytesTransferred / totalBytes * 100.0;
    }

    public Duration getElapsedTime() {
        return Duration.between(startTime, Instant.now());
    }

    public double getAverageTransferRate() {
        long elapsedMs = getElapsedTime().toMillis();
        if (elapsedMs == 0) return 0.0;
        return (double) bytesTransferred / elapsedMs * 1000;
    }
}
```

**Progress Metrics Collected:**
- **Transfer Volume**: Bytes transferred, total bytes, completion percentage
- **Transfer Rate**: Current rate, average rate, peak rate
- **Time Metrics**: Elapsed time, estimated time remaining (ETA)
- **Phase Tracking**: Connection, transfer, validation phases
- **Network Metrics**: Latency, throughput, packet loss (where available)
- **Error Metrics**: Retry attempts, error rates, failure reasons

**Progress Reporting Features:**
- **Real-Time Updates**: Sub-second progress updates for large transfers
- **Rate Smoothing**: Moving averages to smooth out rate fluctuations
- **Predictive ETA**: Machine learning-enhanced ETA calculations
- **Phase Detection**: Automatic detection of transfer phases
- **Anomaly Detection**: Identify unusual transfer patterns

**Integrity Verification: SHA-256 checksum validation and corruption detection**

The integrity verification system ensures data integrity throughout the transfer process:

```java
public class IntegrityVerifier {
    private final Map<String, ChecksumCalculator> activeCalculators;
    private final ChecksumValidationService validationService;

    // Start checksum calculation during transfer
    public void startChecksumCalculation(String jobId, TransferJob job) {
        if (job.isChecksumValidationEnabled()) {
            ChecksumCalculator calculator = new ChecksumCalculator(
                job.getChecksumAlgorithm(),
                job.getExpectedChecksum()
            );

            activeCalculators.put(jobId, calculator);
        }
    }

    // Update checksum with transferred data
    public void updateChecksum(String jobId, byte[] data, int offset, int length) {
        ChecksumCalculator calculator = activeCalculators.get(jobId);
        if (calculator != null) {
            calculator.update(data, offset, length);
        }
    }

    // Validate checksum after transfer completion
    public ChecksumValidationResult validateChecksum(String jobId) {
        ChecksumCalculator calculator = activeCalculators.get(jobId);
        if (calculator == null) {
            return ChecksumValidationResult.skipped("Checksum validation disabled");
        }

        String calculatedChecksum = calculator.getChecksum();
        String expectedChecksum = calculator.getExpectedChecksum();

        if (expectedChecksum != null) {
            // Compare with expected checksum
            boolean isValid = calculatedChecksum.equals(expectedChecksum);
            return new ChecksumValidationResult(isValid, calculatedChecksum, expectedChecksum);
        } else {
            // No expected checksum - just record calculated value
            return ChecksumValidationResult.calculated(calculatedChecksum);
        }
    }
}

public class ChecksumCalculator {
    private final MessageDigest digest;
    private final String expectedChecksum;

    public ChecksumCalculator(ChecksumAlgorithm algorithm, String expectedChecksum) {
        this.digest = createDigest(algorithm);
        this.expectedChecksum = expectedChecksum;
    }

    // Streaming checksum calculation
    public void update(byte[] data, int offset, int length) {
        digest.update(data, offset, length);
    }

    public String getChecksum() {
        byte[] hash = digest.digest();
        return bytesToHex(hash);
    }

    // Support for different algorithms
    private MessageDigest createDigest(ChecksumAlgorithm algorithm) {
        try {
            switch (algorithm) {
                case SHA256:
                    return MessageDigest.getInstance("SHA-256");
                case SHA1:
                    return MessageDigest.getInstance("SHA-1");
                case MD5:
                    return MessageDigest.getInstance("MD5");
                default:
                    throw new UnsupportedOperationException("Unsupported algorithm: " + algorithm);
            }
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Checksum algorithm not available", e);
        }
    }
}
```

**Integrity Verification Features:**
- **Multiple Algorithms**: SHA-256, SHA-1, MD5, CRC32 support
- **Streaming Calculation**: Calculate checksum during transfer (no additional I/O)
- **Source Verification**: Compare against source-provided checksums
- **Corruption Detection**: Detect data corruption during transfer
- **Partial Verification**: Verify chunks for large file transfers
- **Performance Optimization**: Hardware-accelerated checksums where available

**Checksum Sources:**
- **HTTP Headers**: Content-MD5, ETag headers
- **File Metadata**: Extended attributes, sidecar files
- **API Responses**: Checksum provided by source API
- **Pre-calculated**: Checksums calculated before transfer
- **On-demand**: Calculate if not provided by source

**Retry Logic: Exponential backoff with configurable retry policies**

The retry system provides intelligent retry logic for handling transient failures:

```java
public class RetryManager {
    // Retry policy configuration
    public static class RetryPolicy {
        private final int maxAttempts;
        private final Duration initialDelay;
        private final double backoffMultiplier;
        private final Duration maxDelay;
        private final Set<Class<? extends Exception>> retryableExceptions;
        private final Set<Class<? extends Exception>> nonRetryableExceptions;

        // Retry decision logic
        public boolean shouldRetry(Exception exception, int attemptNumber) {
            if (attemptNumber >= maxAttempts) {
                return false;
            }

            // Check if exception is explicitly non-retryable
            if (isNonRetryableException(exception)) {
                return false;
            }

            // Check if exception is retryable
            return isRetryableException(exception);
        }

        public Duration calculateDelay(int attemptNumber) {
            double delay = initialDelay.toMillis() * Math.pow(backoffMultiplier, attemptNumber - 1);

            // Add jitter to prevent thundering herd
            delay = delay * (0.5 + Math.random() * 0.5);

            // Cap at maximum delay
            return Duration.ofMillis(Math.min((long) delay, maxDelay.toMillis()));
        }
    }

    // Execute with retry logic
    public <T> T executeWithRetry(Supplier<T> operation, RetryPolicy policy) {
        Exception lastException = null;

        for (int attempt = 1; attempt <= policy.getMaxAttempts(); attempt++) {
            try {
                return operation.get();
            } catch (Exception e) {
                lastException = e;

                if (!policy.shouldRetry(e, attempt)) {
                    break;
                }

                // Calculate delay and wait
                Duration delay = policy.calculateDelay(attempt);
                logger.info("Transfer attempt {} failed, retrying in {}: {}",
                           attempt, delay, e.getMessage());

                try {
                    Thread.sleep(delay.toMillis());
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new TransferException("Retry interrupted", ie);
                }
            }
        }

        throw new TransferException("Transfer failed after " + policy.getMaxAttempts() + " attempts", lastException);
    }
}
```

**Retry Policy Features:**
- **Exponential Backoff**: Progressively longer delays between retries
- **Jitter**: Random variation to prevent thundering herd problems
- **Exception Classification**: Different retry behavior for different error types
- **Circuit Breaker**: Temporarily disable retries for consistently failing endpoints
- **Adaptive Policies**: Adjust retry behavior based on historical success rates

**Retryable vs Non-Retryable Exceptions:**
```java
// Retryable exceptions (transient failures)
- NetworkTimeoutException
- ConnectionResetException
- TemporaryServerErrorException (5xx HTTP errors)
- RateLimitExceededException
- ServiceUnavailableException

// Non-retryable exceptions (permanent failures)
- AuthenticationException
- AuthorizationException
- FileNotFoundException
- InvalidChecksumException
- QuotaExceededException
```

This comprehensive expansion provides deep technical insight into how the Controller Cluster and Transfer Agents operate, showing the sophisticated engineering that makes Quorus a robust, enterprise-grade file transfer system.

**Agent Architecture:**
```mermaid
graph LR
    subgraph "Transfer Agent"
        TM[Transfer Manager]
        PE[Protocol Engine]
        PM[Progress Monitor]
        CM[Checksum Manager]

        TM --> PE
        TM --> PM
        TM --> CM

        subgraph "Protocol Handlers"
            HTTP[HTTP Handler]
            FTP[FTP Handler]
            SFTP[SFTP Handler]
            SMB[SMB Handler]
        end

        PE --> HTTP
        PE --> FTP
        PE --> SFTP
        PE --> SMB
    end
```

**Agent Registration and Discovery:**
- **Automatic Registration**: Agents self-register with the controller cluster
- **Capability Advertisement**: Agents advertise their supported protocols and capacity
- **Health Reporting**: Continuous health and performance metrics reporting
- **Dynamic Scaling**: Agents can be added/removed without service interruption

#### 3. API Gateway Layer

The **API Gateway** provides unified access to all Quorus functionality:

**Gateway Features:**
- **Authentication & Authorization**: Enterprise SSO integration and RBAC
- **Rate Limiting**: Tenant-based request throttling and quota enforcement
- **Request Routing**: Intelligent routing to healthy controller nodes
- **Protocol Translation**: REST API to internal protocol conversion
- **Audit Logging**: Comprehensive request/response logging for compliance

**API Design Principles:**
- **RESTful Design**: Standard HTTP methods and status codes
- **OpenAPI Specification**: Complete API documentation and client generation
- **Versioning**: Backward-compatible API evolution
- **Idempotency**: Safe retry semantics for critical operations

### Data Flow Architecture

#### Transfer Request Lifecycle

```mermaid
sequenceDiagram
    participant Client
    participant API as API Gateway
    participant Controller as Controller Leader
    participant Agent as Transfer Agent
    participant Storage as Target Storage

    Client->>API: Submit Transfer Request
    API->>API: Authenticate & Authorize
    API->>Controller: Forward Request
    Controller->>Controller: Schedule Transfer
    Controller->>Agent: Assign Transfer Job
    Agent->>Storage: Execute Transfer
    Agent->>Controller: Report Progress
    Controller->>API: Update Status
    API->>Client: Return Progress
    Agent->>Controller: Report Completion
    Controller->>API: Final Status
    API->>Client: Transfer Complete
```

#### Workflow Execution Flow

```mermaid
sequenceDiagram
    participant Client
    participant WE as Workflow Engine
    participant Controller as Controller
    participant Agent1 as Agent 1
    participant Agent2 as Agent 2

    Client->>WE: Submit Workflow
    WE->>WE: Parse & Validate YAML
    WE->>WE: Build Dependency Graph
    WE->>WE: Create Execution Plan

    loop For Each Transfer Group
        WE->>Controller: Submit Transfer Jobs
        Controller->>Agent1: Assign Job 1
        Controller->>Agent2: Assign Job 2
        Agent1->>WE: Report Progress
        Agent2->>WE: Report Progress
        Agent1->>WE: Job Complete
        Agent2->>WE: Job Complete
    end

    WE->>Client: Workflow Complete
```

### Fault Tolerance and Resilience

#### Controller Fault Tolerance

**Raft Consensus Guarantees:**
- **Consistency**: All nodes agree on the same state
- **Availability**: System remains operational with majority of nodes
- **Partition Tolerance**: Handles network splits gracefully

**Failure Scenarios:**
1. **Leader Failure**: Automatic re-election within election timeout
2. **Follower Failure**: Continues operation with remaining nodes
3. **Network Partition**: Majority partition maintains service
4. **Split-Brain Prevention**: Minority partitions become read-only

#### Agent Fault Tolerance

**Agent Failure Handling:**
- **Job Reassignment**: Failed jobs automatically reassigned to healthy agents
- **Partial Transfer Recovery**: Resume transfers from last checkpoint
- **Circuit Breaker**: Temporarily disable failing agents
- **Graceful Degradation**: Reduce capacity rather than complete failure

**Network Resilience:**
- **Connection Pooling**: Efficient connection reuse and management
- **Timeout Management**: Configurable timeouts for different scenarios
- **Retry Strategies**: Exponential backoff with jitter
- **Bandwidth Adaptation**: Dynamic rate limiting based on network conditions

### Security Architecture

#### Defense in Depth

```mermaid
graph TB
    subgraph "Network Security"
        FW[Corporate Firewall]
        VPN[VPN/Private Network]
        TLS[TLS Encryption]
    end

    subgraph "Application Security"
        AUTH[Authentication]
        AUTHZ[Authorization]
        RBAC[Role-Based Access Control]
    end

    subgraph "Data Security"
        EAR[Encryption at Rest]
        EIT[Encryption in Transit]
        CS[Checksum Validation]
    end

    subgraph "Operational Security"
        AUDIT[Audit Logging]
        MON[Security Monitoring]
        COMP[Compliance Controls]
    end

    FW --> AUTH
    VPN --> AUTHZ
    TLS --> RBAC

    AUTH --> EAR
    AUTHZ --> EIT
    RBAC --> CS

    EAR --> AUDIT
    EIT --> MON
    CS --> COMP
```

#### Security Layers

**1. Network Security:**
- **Corporate Firewall Integration**: Leverages existing network security
- **Private Network Communication**: Internal-only communication paths
- **TLS 1.3 Encryption**: Modern encryption for all external communications
- **Certificate Management**: Automated certificate lifecycle management

**2. Application Security:**
- **Enterprise SSO Integration**: Active Directory, LDAP, SAML, OAuth2
- **Multi-Factor Authentication**: Support for corporate MFA requirements
- **Role-Based Access Control**: Fine-grained permission management
- **API Security**: Rate limiting, input validation, and injection prevention

**3. Data Security:**
- **Encryption at Rest**: AES-256 encryption for stored data
- **Encryption in Transit**: TLS for all network communications
- **Key Management**: Integration with corporate key management systems
- **Data Classification**: Support for corporate data classification policies

**4. Operational Security:**
- **Comprehensive Audit Logging**: All operations logged for compliance
- **Security Event Monitoring**: Integration with SIEM systems
- **Compliance Frameworks**: SOX, GDPR, HIPAA, PCI-DSS support
- **Incident Response**: Automated alerting and response procedures

## Core Functional Concepts

### Transfer Engine Concepts

#### Transfer Job Lifecycle

A **Transfer Job** represents a single file transfer operation with the following lifecycle:

```mermaid
stateDiagram-v2
    [*] --> PENDING: Job Created
    PENDING --> QUEUED: Scheduled
    QUEUED --> ASSIGNED: Agent Selected
    ASSIGNED --> IN_PROGRESS: Transfer Started
    IN_PROGRESS --> VALIDATING: Transfer Complete
    VALIDATING --> COMPLETED: Checksum Valid
    VALIDATING --> FAILED: Checksum Invalid
    IN_PROGRESS --> FAILED: Transfer Error
    IN_PROGRESS --> CANCELLED: User Cancelled
    FAILED --> RETRYING: Retry Attempt
    RETRYING --> IN_PROGRESS: Retry Started
    RETRYING --> FAILED: Max Retries Exceeded
    COMPLETED --> [*]
    FAILED --> [*]
    CANCELLED --> [*]
```

**Transfer Job Properties:**
- **Job ID**: Unique identifier for tracking and management
- **Source URI**: Complete source location with protocol and credentials
- **Destination Path**: Target location within the tenant's storage space
- **Protocol**: Transfer protocol (HTTP, HTTPS, FTP, SFTP, SMB)
- **Options**: Timeout, retry policy, checksum validation, chunk size
- **Metadata**: File size, content type, creation time, tags
- **Progress**: Bytes transferred, transfer rate, ETA, completion percentage

#### Protocol Abstraction

The **Protocol Engine** provides a unified interface for different transfer protocols:

```java
public interface TransferProtocol {
    // Protocol identification
    String getProtocolName();
    List<String> getSupportedSchemes();

    // Transfer operations
    CompletableFuture<TransferResult> transfer(TransferRequest request);
    CompletableFuture<Void> cancel(String jobId);

    // Capability queries
    boolean supportsResume();
    boolean supportsChecksum();
    long getMaxFileSize();

    // Configuration
    void configure(ProtocolConfiguration config);
    ProtocolCapabilities getCapabilities();
}
```

**Protocol-Specific Features:**
- **HTTP/HTTPS**: Range requests, compression, custom headers, OAuth2
- **FTP**: Active/passive mode, binary/ASCII transfer, directory operations
- **SFTP**: Key-based authentication, compression, concurrent streams
- **SMB**: Windows authentication, share enumeration, file locking

#### Progress Tracking System

The **Progress Tracking System** provides real-time visibility into transfer operations:

**Progress Metrics:**
- **Bytes Transferred**: Current and total bytes
- **Transfer Rate**: Current, average, and peak transfer rates
- **Time Estimates**: Elapsed time, estimated time remaining (ETA)
- **Completion Percentage**: Visual progress indicator
- **Network Statistics**: Latency, packet loss, connection quality

**Progress Events:**
```java
public class ProgressEvent {
    private String jobId;
    private long bytesTransferred;
    private long totalBytes;
    private double transferRate;
    private Duration elapsedTime;
    private Duration estimatedTimeRemaining;
    private TransferPhase phase; // CONNECTING, TRANSFERRING, VALIDATING
    private Map<String, Object> metadata;
}
```

### Workflow Engine Concepts

#### Workflow Definition Model

A **Workflow Definition** is a declarative specification that orchestrates file transfer operations. Let's start with simple examples and gradually build to complex enterprise scenarios, explaining each configuration line by line.

##### Basic Workflow Structure - Simple Example

Let's begin with the simplest possible workflow that transfers a single file:

```yaml
# Line 1: API version - specifies the Quorus workflow schema version
apiVersion: quorus.dev/v1

# Line 2: Resource type - tells Quorus this is a transfer workflow
kind: TransferWorkflow

# Lines 3-6: Basic metadata - minimum required information
metadata:
  name: simple-file-transfer          # Unique identifier for this workflow
  description: Transfer a single file # Human-readable description

# Lines 7-15: Workflow specification - the actual work to be done
spec:
  transferGroups:                     # Container for groups of related transfers
    - name: single-transfer           # Name of this transfer group
      transfers:                      # List of individual file transfers
        - name: copy-data-file        # Name of this specific transfer
          source: "https://api.example.com/data.csv"    # Where to get the file
          destination: "/storage/data.csv"              # Where to put the file
```

**Line-by-Line Explanation:**
- **Line 1**: `apiVersion` declares which version of the Quorus workflow schema to use
- **Line 2**: `kind` specifies this is a TransferWorkflow (vs other resource types)
- **Line 4**: `name` must be unique within the tenant/namespace and DNS-compliant
- **Line 5**: `description` provides human-readable documentation
- **Line 8**: `transferGroups` contains one or more groups of related transfers
- **Line 9**: Each group has a `name` for identification and dependency management
- **Line 10**: `transfers` lists the actual file transfer operations
- **Line 11**: Each transfer has a unique `name` within the group
- **Line 12**: `source` specifies where to retrieve the file (supports various protocols)
- **Line 13**: `destination` specifies where to store the file

##### Adding Basic Configuration - Intermediate Example

Now let's add some basic configuration options:

```yaml
apiVersion: quorus.dev/v1
kind: TransferWorkflow

metadata:
  name: configured-file-transfer
  description: Transfer with basic configuration options
  # Line 6: Version helps track workflow changes over time
  version: "1.0.0"

spec:
  # Lines 9-11: Global variables that can be reused throughout the workflow
  variables:
    sourceUrl: "https://api.example.com"      # Base URL for API calls
    targetDir: "/storage/daily-imports"       # Base directory for files

  # Lines 13-16: Execution configuration controls how transfers run
  execution:
    strategy: sequential    # Run transfers one after another (vs parallel)
    timeout: 1800          # Maximum time in seconds (30 minutes)
    parallelism: 1         # Maximum concurrent transfers

  transferGroups:
    - name: daily-data-import
      transfers:
        - name: import-customers
          # Lines 22-23: Using variables with {{variable}} syntax
          source: "{{sourceUrl}}/customers/export.csv"
          destination: "{{targetDir}}/customers.csv"
          # Lines 24-28: Transfer-specific options
          options:
            timeout: 600              # Override global timeout (10 minutes)
            checksumValidation: true  # Verify file integrity after transfer
            retryAttempts: 3         # Retry failed transfers up to 3 times
```

**New Configuration Explained:**
- **Line 6**: `version` enables tracking of workflow definition changes
- **Lines 9-11**: `variables` section defines reusable values
- **Line 10**: Variables can be referenced later using `{{variableName}}` syntax
- **Lines 13-16**: `execution` section controls workflow-level behavior
- **Line 14**: `strategy: sequential` means transfers run one at a time
- **Line 15**: `timeout: 1800` sets maximum workflow execution time (30 minutes)
- **Line 16**: `parallelism: 1` limits concurrent transfers
- **Lines 22-23**: `{{sourceUrl}}` and `{{targetDir}}` are replaced with variable values
- **Lines 24-28**: `options` section provides transfer-specific configuration
- **Line 26**: `checksumValidation: true` enables integrity verification
- **Line 27**: `retryAttempts: 3` automatically retries failed transfers

##### Adding Dependencies - Multi-Stage Example

Let's create a workflow with multiple stages that depend on each other:

```yaml
apiVersion: quorus.dev/v1
kind: TransferWorkflow

metadata:
  name: multi-stage-pipeline
  description: Multi-stage data processing pipeline
  version: "1.1.0"
  # Lines 7-10: Labels help categorize and filter workflows
  labels:
    environment: production    # Environment designation
    department: finance       # Owning department
    schedule: daily          # Execution frequency

spec:
  variables:
    # Line 14: Built-in function that returns current date (e.g., "2025-01-20")
    processingDate: "{{today}}"
    sourceSystem: "https://erp.company.com"
    stagingArea: "/staging/finance"
    finalDestination: "/warehouse/finance"

  execution:
    # Line 19: Mixed strategy allows some parallel execution within constraints
    strategy: mixed
    parallelism: 3
    timeout: 3600    # 1 hour total

  transferGroups:
    # Stage 1: Extract raw data from source systems
    - name: extract-raw-data
      # Line 26: Description documents the purpose of this stage
      description: "Extract customer and order data from ERP system"
      transfers:
        - name: extract-customers
          source: "{{sourceSystem}}/api/customers/export?date={{processingDate}}"
          destination: "{{stagingArea}}/raw-customers-{{processingDate}}.json"

        - name: extract-orders
          source: "{{sourceSystem}}/api/orders/export?date={{processingDate}}"
          destination: "{{stagingArea}}/raw-orders-{{processingDate}}.json"

    # Stage 2: Process the extracted data
    - name: process-data
      description: "Clean and validate the extracted data"
      # Line 38: This stage depends on the previous stage completing first
      dependsOn: [extract-raw-data]
      transfers:
        - name: clean-customer-data
          # Line 41: Using output from previous stage as input
          source: "{{stagingArea}}/raw-customers-{{processingDate}}.json"
          destination: "{{stagingArea}}/clean-customers-{{processingDate}}.json"

        - name: clean-order-data
          source: "{{stagingArea}}/raw-orders-{{processingDate}}.json"
          destination: "{{stagingArea}}/clean-orders-{{processingDate}}.json"

    # Stage 3: Load processed data to final destination
    - name: load-final-data
      description: "Load processed data into data warehouse"
      # Line 50: This stage depends on data processing completing
      dependsOn: [process-data]
      transfers:
        - name: load-customers
          source: "{{stagingArea}}/clean-customers-{{processingDate}}.json"
          destination: "{{finalDestination}}/customers/{{processingDate}}.json"

        - name: load-orders
          source: "{{stagingArea}}/clean-orders-{{processingDate}}.json"
          destination: "{{finalDestination}}/orders/{{processingDate}}.json"
```

**New Concepts Explained:**
- **Lines 7-10**: `labels` provide metadata for categorization and filtering
- **Line 14**: `{{today}}` is a built-in function that returns the current date
- **Line 19**: `strategy: mixed` allows intelligent parallel execution within dependencies
- **Line 26**: `description` at the group level documents the stage's purpose
- **Line 38**: `dependsOn: [extract-raw-data]` creates a dependency relationship
- **Line 41**: Using output from one stage as input to the next creates a data pipeline
- **Line 50**: Multiple dependencies ensure proper execution order

##### Adding Conditional Logic - Advanced Example

Now let's add conditional execution based on business rules:

```yaml
apiVersion: quorus.dev/v1
kind: TransferWorkflow

metadata:
  name: conditional-business-pipeline
  description: Business pipeline with conditional processing
  version: "2.0.0"
  labels:
    environment: production
    criticality: high
    compliance: required

spec:
  variables:
    processingDate: "{{today}}"
    environment: "production"
    # Line 16: Built-in function that returns day of month (1-31)
    dayOfMonth: "{{dayOfMonth}}"
    # Line 18: Built-in function that returns day of week (1=Monday, 7=Sunday)
    dayOfWeek: "{{dayOfWeek}}"
    sourceERP: "https://erp.company.com"
    stagingArea: "/staging/finance"

  # Lines 22-32: Global conditions that can be referenced by transfer groups
  conditions:
    - name: is-month-end
      # Line 24: Expression evaluates to true if day of month >= 28
      expression: "{{dayOfMonth}} >= 28"
      description: "True during the last few days of the month"

    - name: is-business-day
      # Line 28: Expression checks if it's Monday through Friday
      expression: "{{dayOfWeek}} >= 1 && {{dayOfWeek}} <= 5"
      description: "True on weekdays (Monday-Friday)"

    - name: is-production
      expression: "{{environment}} == 'production'"
      description: "True when running in production environment"

  execution:
    strategy: mixed
    parallelism: 5
    timeout: 7200    # 2 hours

  transferGroups:
    # Always run: Daily data extraction
    - name: daily-extraction
      description: "Extract daily transaction data"
      # Line 43: Only run if it's a business day AND we're in production
      condition: "is-business-day && is-production"
      transfers:
        - name: extract-daily-transactions
          source: "{{sourceERP}}/api/transactions/daily?date={{processingDate}}"
          destination: "{{stagingArea}}/daily-transactions-{{processingDate}}.json"

    # Conditional: Month-end processing
    - name: month-end-processing
      description: "Additional processing required at month-end"
      dependsOn: [daily-extraction]
      # Line 52: Only run during month-end periods
      condition: "is-month-end && is-production"
      transfers:
        - name: extract-month-end-adjustments
          source: "{{sourceERP}}/api/adjustments/month-end?date={{processingDate}}"
          destination: "{{stagingArea}}/month-end-adjustments-{{processingDate}}.json"

        - name: generate-monthly-report
          source: "{{stagingArea}}/daily-transactions-{{processingDate}}.json"
          destination: "/reports/monthly-summary-{{processingDate}}.pdf"
          # Lines 60-64: Transfer-specific options for report generation
          options:
            timeout: 1800        # 30 minutes for report generation
            reportFormat: "pdf"  # Custom option for report type
            includeCharts: true  # Custom option for visual elements
```

**Conditional Logic Explained:**
- **Lines 22-32**: `conditions` section defines reusable boolean expressions
- **Line 24**: `{{dayOfMonth}} >= 28` uses a built-in function in an expression
- **Line 28**: `&&` is the logical AND operator for combining conditions
- **Line 43**: `condition` field references defined conditions with logical operators
- **Line 52**: Conditions can be combined with `&&` (AND) and `||` (OR)
- **Lines 60-64**: Custom options can be passed to specialized transfer handlers

##### Adding Error Handling and Notifications - Production Example

Finally, let's add comprehensive error handling and notifications for production use:

```yaml
apiVersion: quorus.dev/v1
kind: TransferWorkflow

metadata:
  name: production-financial-pipeline
  description: Production-ready financial data pipeline with full error handling
  version: "3.0.0"
  # Line 8: Tenant specifies which organization owns this workflow
  tenant: acme-corporation
  # Line 10: Namespace provides additional isolation within the tenant
  namespace: finance-operations
  labels:
    environment: production
    criticality: high
    compliance: sox-required
    # Line 15: Data classification affects security and retention policies
    dataClassification: confidential
  # Lines 16-24: Annotations provide extended metadata for governance
  annotations:
    owner: "finance-data-team@acme-corp.com"
    businessOwner: "cfo@acme-corp.com"
    documentation: "https://wiki.acme-corp.com/finance/daily-pipeline"
    approvedBy: "finance-director@acme-corp.com"
    approvalDate: "2025-01-15"
    complianceReviewed: "2025-01-10"

spec:
  variables:
    processingDate: "{{today}}"
    environment: "production"
    sourceERP: "https://erp.acme-corp.internal"
    stagingArea: "/corporate-data/staging/finance"
    warehouseArea: "/corporate-data/warehouse/finance"
    # Line 32: Email addresses for different types of notifications
    opsTeamEmail: "finance-ops@acme-corp.com"
    managementEmail: "finance-director@acme-corp.com"

  conditions:
    - name: is-production
      expression: "{{environment}} == 'production'"
    - name: is-business-day
      expression: "{{dayOfWeek}} >= 1 && {{dayOfWeek}} <= 5"

  execution:
    strategy: mixed
    parallelism: 3
    timeout: 14400    # 4 hours maximum
    # Lines 44-49: Workflow-level retry policy
    retryPolicy:
      maxAttempts: 2           # Retry entire workflow up to 2 times
      backoffMultiplier: 2.0   # Double the wait time between retries
      initialDelay: 300        # Wait 5 minutes before first retry
      jitterEnabled: true      # Add randomness to prevent thundering herd

  transferGroups:
    - name: extract-financial-data
      description: "Extract daily financial data from ERP system"
      condition: "is-production && is-business-day"
      # Line 54: Continue processing other groups even if this one fails
      continueOnError: false    # Stop workflow if extraction fails (critical data)
      # Line 56: Maximum parallel transfers within this group
      maxParallelTransfers: 2
      transfers:
        - name: extract-transactions
          source: "{{sourceERP}}/api/transactions/export?date={{processingDate}}"
          destination: "{{stagingArea}}/transactions-{{processingDate}}.json"
          # Lines 61-70: Comprehensive transfer options
          options:
            timeout: 1800                    # 30 minutes for this transfer
            checksumValidation: true         # Verify file integrity
            checksumAlgorithm: "SHA256"     # Use SHA-256 for checksums
            retryAttempts: 5                # Retry this transfer up to 5 times
            retryBackoffMultiplier: 2.0     # Double wait time between retries
            initialRetryDelay: 60           # Wait 1 minute before first retry
            compressionEnabled: true        # Enable compression for large files
          # Lines 68-74: Authentication configuration for secure API access
          credentials:
            type: "oauth2"                  # Use OAuth 2.0 authentication
            tokenEndpoint: "{{sourceERP}}/oauth/token"
            clientId: "quorus-finance-client"
            # Line 72: Reference to securely stored client secret
            clientSecret: "{{vault:erp-client-secret}}"
            scope: "transactions:read"      # Minimal required permissions

        - name: extract-accounts
          source: "{{sourceERP}}/api/accounts/export?date={{processingDate}}"
          destination: "{{stagingArea}}/accounts-{{processingDate}}.json"
          # Line 78: This transfer depends on transactions completing first
          dependsOn: [extract-transactions]
          options:
            timeout: 900        # 15 minutes (smaller dataset)
            checksumValidation: true
            retryAttempts: 3

    - name: validate-and-process
      description: "Validate data integrity and process for warehouse loading"
      dependsOn: [extract-financial-data]
      # Line 87: Continue with other groups even if validation has issues
      continueOnError: true
      transfers:
        - name: validate-data-integrity
          source: "{{stagingArea}}/transactions-{{processingDate}}.json"
          destination: "{{stagingArea}}/validation-report-{{processingDate}}.json"
          options:
            # Line 93: Custom validation script execution
            executeScript: true
            scriptPath: "/scripts/validate-financial-data.py"
            scriptArguments: ["--date", "{{processingDate}}", "--strict-mode"]
            timeout: 600        # 10 minutes for validation
            # Lines 97-102: Error handling for validation failures
            onError:
              action: "continue"              # Continue workflow even if validation fails
              logLevel: "WARN"               # Log validation failures as warnings
              notification: "{{opsTeamEmail}}" # Notify ops team of validation issues
              createTicket: true             # Automatically create support ticket
              ticketPriority: "medium"       # Set ticket priority level

    - name: load-to-warehouse
      description: "Load processed data into corporate data warehouse"
      dependsOn: [validate-and-process]
      continueOnError: false    # Critical final step - must succeed
      transfers:
        - name: load-transactions
          source: "{{stagingArea}}/transactions-{{processingDate}}.json"
          destination: "{{warehouseArea}}/transactions/{{processingDate}}.parquet"
          options:
            timeout: 2700       # 45 minutes for warehouse loading
            # Line 113: Convert JSON to Parquet format for analytics
            outputFormat: "parquet"
            compressionCodec: "snappy"      # Efficient compression for analytics
            # Line 116: Partition data by date for query performance
            partitionBy: ["transaction_date"]
            # Lines 117-122: Warehouse-specific options
            warehouseOptions:
              updateMode: "append"          # Add new data without replacing existing
              createIndexes: true          # Create database indexes for performance
              updateStatistics: true      # Update table statistics for query optimizer
              validateSchema: true        # Ensure data matches expected schema

  # Lines 123-150: Comprehensive notification configuration
  notifications:
    # Success notifications - sent when workflow completes successfully
    onSuccess:
      - type: email
        # Line 127: Send success notification to operations team
        recipients: ["{{opsTeamEmail}}"]
        subject: "✅ Financial Data Pipeline Completed - {{processingDate}}"
        # Line 130: Use predefined email template
        template: "workflow-success-template"
        # Line 132: Include performance metrics in the email
        includeMetrics: true
        attachments:
          - "{{stagingArea}}/validation-report-{{processingDate}}.json"

      - type: slack
        # Line 137: Also notify via Slack for immediate visibility
        channel: "#finance-data-ops"
        message: "✅ Daily financial pipeline completed successfully for {{processingDate}}"
        # Line 140: Include key metrics in Slack message
        includeMetrics: true

    # Failure notifications - sent when workflow fails
    onFailure:
      - type: email
        # Line 145: Escalate failures to both ops and management
        recipients: ["{{opsTeamEmail}}", "{{managementEmail}}"]
        subject: "🚨 URGENT: Financial Pipeline Failed - {{processingDate}}"
        template: "workflow-failure-template"
        # Line 149: Include detailed error information and logs
        includeErrorDetails: true
        includeLogs: true
        # Line 152: Mark as high priority for email filtering
        priority: "high"

      - type: slack
        channel: "#finance-data-ops"
        message: "🚨 CRITICAL: Financial pipeline FAILED for {{processingDate}} - immediate attention required!"
        # Line 157: Mention specific people for urgent issues
        mentions: ["@finance-ops-oncall", "@data-team-lead"]

      - type: webhook
        # Line 160: Integrate with incident management system
        url: "https://alerts.acme-corp.com/api/incidents"
        method: "POST"
        headers:
          Authorization: "Bearer {{vault:alerting-system-token}}"
          Content-Type: "application/json"
        # Lines 165-171: Structured incident data
        payload:
          severity: "high"
          service: "finance-data-pipeline"
          environment: "{{environment}}"
          description: "Financial data pipeline execution failed"
          impact: "Financial reporting may be delayed"
          urgency: "high"

    # Progress notifications - optional periodic updates
    onProgress:
      # Line 174: Enable progress notifications every 30 minutes
      enabled: true
      intervalMinutes: 30
      recipients: ["{{opsTeamEmail}}"]
      # Line 178: Only send progress updates for long-running workflows
      minimumDurationMinutes: 60

  # Lines 180-190: Cleanup and maintenance configuration
  cleanup:
    enabled: true
    # Line 183: Keep staging files for 7 days for troubleshooting
    retainStagingDays: 7
    # Line 185: Keep detailed logs for 30 days for audit purposes
    retainLogsDays: 30
    # Line 187: Automatically archive completed workflow metadata
    archiveEnabled: true
    archiveLocation: "/corporate-data/archive/workflows"
    # Line 190: Compress archived data to save storage space
    compressionEnabled: true

  # Lines 191-196: Service Level Agreement definitions
  sla:
    # Line 193: Maximum acceptable execution time
    maxExecutionTime: "PT4H"        # 4 hours (ISO 8601 duration format)
    # Line 195: Target execution time for performance monitoring
    targetExecutionTime: "PT2H"     # 2 hours target
    # Line 197: Minimum acceptable success rate
    availabilityTarget: 99.5        # 99.5% success rate required
```

**Production Features Explained:**
- **Lines 8-10**: `tenant` and `namespace` provide multi-tenant isolation
- **Lines 16-24**: `annotations` provide governance metadata for enterprise compliance
- **Line 32**: Variables can reference email addresses for dynamic notifications
- **Lines 44-49**: Workflow-level `retryPolicy` handles transient failures
- **Line 54**: `continueOnError: false` stops workflow if critical steps fail
- **Line 56**: `maxParallelTransfers` controls resource usage within groups
- **Lines 68-74**: `credentials` section handles secure authentication
- **Line 78**: Transfer-level `dependsOn` creates fine-grained dependencies
- **Lines 97-102**: `onError` configuration provides granular error handling
- **Lines 123-150**: Comprehensive `notifications` for different scenarios
- **Lines 180-190**: `cleanup` configuration manages storage and maintenance
- **Lines 191-196**: `sla` definitions enable performance monitoring

##### YAML Configuration Learning Path Summary

We've progressed through increasingly complex YAML configurations:

**1. Simple Example (15 lines)**
- Basic `apiVersion`, `kind`, `metadata`
- Single transfer group with one transfer
- Minimal required fields only

**2. Intermediate Example (28 lines)**
- Added `version` and `variables`
- Introduced `execution` configuration
- Transfer `options` for customization
- Variable substitution with `{{variable}}` syntax

**3. Multi-Stage Example (58 lines)**
- Multiple transfer groups with `dependsOn` relationships
- Built-in functions like `{{today}}`
- `labels` for categorization
- Data pipeline pattern (extract → process → load)

**4. Conditional Example (64 lines)**
- Global `conditions` with boolean expressions
- Conditional execution using `condition` fields
- Logical operators (`&&`, `||`) in expressions
- Business rule implementation

**5. Production Example (197 lines)**
- Complete enterprise metadata with `tenant`, `namespace`, `annotations`
- Comprehensive error handling with `continueOnError`, `onError`
- Authentication with `credentials`
- Multi-channel `notifications` (email, Slack, webhook)
- Resource management and `cleanup` policies
- Service Level Agreements (`sla`)

##### Key YAML Configuration Patterns

**Essential Structure Pattern:**
```yaml
apiVersion: quorus.dev/v1    # Always required - schema version
kind: TransferWorkflow       # Always required - resource type
metadata:                    # Always required - identification
  name: workflow-name        # Always required - unique identifier
spec:                        # Always required - workflow definition
  transferGroups:            # Always required - contains transfers
    - name: group-name       # Always required - group identifier
      transfers:             # Always required - list of transfers
        - name: transfer-name # Always required - transfer identifier
          source: "..."       # Always required - source location
          destination: "..."  # Always required - destination location
```

**Variable Usage Pattern:**
```yaml
spec:
  variables:                 # Define reusable values
    baseUrl: "https://api.example.com"
    targetDir: "/storage"
    date: "{{today}}"        # Built-in functions
  transferGroups:
    - name: example
      transfers:
        - name: transfer
          source: "{{baseUrl}}/data.csv"     # Reference variables
          destination: "{{targetDir}}/{{date}}.csv"  # Combine variables
```

**Dependency Pattern:**
```yaml
transferGroups:
  - name: stage-1            # First stage - no dependencies
    transfers: [...]

  - name: stage-2
    dependsOn: [stage-1]     # Explicit dependency
    transfers: [...]

  - name: stage-3
    dependsOn: [stage-1, stage-2]  # Multiple dependencies
    transfers: [...]
```

**Conditional Execution Pattern:**
```yaml
conditions:                  # Define reusable conditions
  - name: is-production
    expression: "{{environment}} == 'production'"

transferGroups:
  - name: prod-only-group
    condition: is-production  # Reference condition
    transfers: [...]

  - name: complex-condition
    condition: "is-production && {{dayOfWeek}} <= 5"  # Combine conditions
    transfers: [...]
```

**Error Handling Pattern:**
```yaml
transferGroups:
  - name: critical-group
    continueOnError: false   # Stop workflow if this fails
    transfers:
      - name: critical-transfer
        source: "..."
        destination: "..."
        options:
          retryAttempts: 5   # Transfer-level retries
          onError:           # Transfer-level error handling
            action: "fail"
            notification: "admin@company.com"

  - name: optional-group
    continueOnError: true    # Continue workflow even if this fails
    transfers: [...]
```

**Notification Pattern:**
```yaml
notifications:
  onSuccess:                 # Workflow succeeded
    - type: email
      recipients: ["ops@company.com"]
      subject: "Workflow completed"

  onFailure:                 # Workflow failed
    - type: email
      recipients: ["ops@company.com", "manager@company.com"]
      subject: "URGENT: Workflow failed"
      priority: "high"

    - type: slack
      channel: "#alerts"
      message: "Workflow failed - immediate attention required"
```

##### Best Practices for YAML Configuration

**1. Start Simple, Add Complexity Gradually**
- Begin with basic transfer requirements
- Add configuration options as needed
- Implement error handling and notifications last

**2. Use Meaningful Names**
```yaml
# Good - descriptive names
- name: extract-customer-data
- name: validate-financial-records
- name: load-to-data-warehouse

# Bad - generic names
- name: step1
- name: transfer
- name: process
```

**3. Leverage Variables for Reusability**
```yaml
# Good - reusable configuration
variables:
  apiBase: "https://api.company.com"
  targetDir: "/data/{{department}}"

# Bad - hardcoded values repeated
source: "https://api.company.com/customers"
source: "https://api.company.com/orders"
```

**4. Document with Descriptions**
```yaml
# Good - well documented
- name: month-end-processing
  description: "Additional financial processing required at month-end for regulatory compliance"
  condition: "{{dayOfMonth}} >= 28"

# Bad - no documentation
- name: month-end-processing
  condition: "{{dayOfMonth}} >= 28"
```

**5. Use Appropriate Error Handling**
```yaml
# Critical data - fail fast
- name: financial-data-extract
  continueOnError: false

# Optional data - continue on error
- name: supplementary-data-extract
  continueOnError: true
```

This progressive approach helps users understand YAML configuration by building from simple concepts to complex enterprise scenarios, with each example building on the previous one while introducing new concepts gradually.

```yaml
# API version and resource type declaration
apiVersion: quorus.dev/v1
kind: TransferWorkflow

# Metadata section - workflow identification and classification
metadata:
  # Unique workflow identifier (must be DNS-compliant)
  name: enterprise-financial-data-pipeline

  # Human-readable description for documentation
  description: |
    Comprehensive daily financial data synchronization pipeline that extracts
    data from multiple ERP systems, validates data integrity, transforms data
    for analytics, and loads into the corporate data warehouse with full
    audit trail and compliance reporting.

  # Semantic version for workflow evolution tracking
  version: "2.3.1"

  # Tenant and namespace for multi-tenant isolation
  tenant: acme-corporation
  namespace: finance-operations

  # Labels for categorization and selection (key-value pairs)
  labels:
    environment: production           # Environment designation
    criticality: high                # Business criticality level
    schedule: daily                  # Execution frequency
    department: finance              # Owning department
    compliance: sox-required         # Compliance requirements
    dataClassification: confidential # Data sensitivity level
    automationLevel: full           # Level of automation

  # Annotations for extended metadata (not used for selection)
  annotations:
    # Business ownership and contact information
    owner: "finance-data-team@acme-corp.com"
    businessOwner: "cfo@acme-corp.com"
    technicalContact: "data-ops@acme-corp.com"

    # Documentation and reference links
    documentation: "https://wiki.acme-corp.com/finance/data-pipeline"
    runbook: "https://runbooks.acme-corp.com/finance/daily-sync"
    architecture: "https://confluence.acme-corp.com/finance/data-architecture"

    # Approval and governance information
    approvedBy: "finance-director@acme-corp.com"
    approvalDate: "2025-01-15"
    nextReview: "2025-07-15"
    complianceReviewed: "2025-01-10"

    # Change management
    lastModified: "2025-01-20T10:30:00Z"
    modifiedBy: "data-engineer@acme-corp.com"
    changeTicket: "CHG-2025-001234"

# Workflow specification - the core workflow definition
spec:
  # Global variables available to all transfers and conditions
  variables:
    # System endpoints and configurations
    sourceERP: "https://erp.acme-corp.internal"
    sourceCRM: "https://crm.acme-corp.internal"
    targetWarehouse: "/corporate-data-warehouse/finance"
    stagingArea: "/staging/finance"
    archiveLocation: "/archive/finance"

    # Date and time variables with formatting
    processingDate: "{{today}}"                    # 2025-01-20
    processingTimestamp: "{{timestamp}}"           # 2025-01-20T06:00:00Z
    yearMonth: "{{format(today, 'yyyy-MM')}}"     # 2025-01
    fiscalQuarter: "{{fiscalQuarter(today)}}"     # Q1-2025

    # Environment and configuration
    environment: "production"
    region: "us-east-1"
    dataCenter: "primary"

    # Business logic variables
    batchSize: 10000
    maxRetries: 5
    timeoutMinutes: 30

    # Notification and alerting
    primaryNotificationEmail: "finance-ops@acme-corp.com"
    escalationEmail: "finance-director@acme-corp.com"
    slackChannel: "#finance-data-ops"

    # Compliance and audit
    auditRequired: true
    retentionPeriodDays: 2555  # 7 years for financial data
    encryptionRequired: true

  # Execution configuration
  execution:
    # Execution strategy determines how transfer groups are executed
    strategy: mixed  # Options: sequential, parallel, mixed

    # Maximum number of parallel transfers across all groups
    parallelism: 8

    # Global timeout for entire workflow (in seconds)
    timeout: 14400  # 4 hours

    # Workflow-level retry policy
    retryPolicy:
      maxAttempts: 3
      backoffMultiplier: 2.0
      initialDelay: 300        # 5 minutes
      maxDelay: 1800          # 30 minutes
      jitterEnabled: true     # Add randomness to prevent thundering herd

    # Resource constraints
    resources:
      maxMemoryMB: 2048
      maxCpuCores: 4
      maxNetworkBandwidthMbps: 100

    # Execution preferences
    preferences:
      preferredAgents: ["agent-finance-01", "agent-finance-02"]
      avoidAgents: ["agent-maintenance"]
      requireDedicatedAgents: false

  # Global conditions that can be referenced by transfer groups
  conditions:
    # Environment-based conditions
    - name: production-only
      description: "Execute only in production environment"
      expression: "{{environment}} == 'production'"

    - name: business-hours
      description: "Execute only during business hours (6 AM - 10 PM EST)"
      expression: "{{hour}} >= 6 && {{hour}} <= 22"

    - name: weekday-only
      description: "Execute only on weekdays"
      expression: "{{dayOfWeek}} >= 1 && {{dayOfWeek}} <= 5"

    - name: month-end
      description: "Execute additional processing at month end"
      expression: "{{dayOfMonth}} >= 28"

    - name: quarter-end
      description: "Execute quarterly processing"
      expression: "{{isQuarterEnd(today)}}"

    # Data availability conditions
    - name: source-data-available
      description: "Check if source data is available"
      expression: "{{checkDataAvailability(sourceERP, processingDate)}}"

  # Transfer groups - logical groupings of related transfers
  transferGroups:
    # Stage 1: Data Extraction from Source Systems
    - name: extract-master-data
      description: |
        Extract master data from ERP and CRM systems including customer
        information, product catalogs, and organizational hierarchies.
        This data forms the foundation for all subsequent processing.

      # Execution conditions for this group
      condition: production-only

      # Group-level configuration
      continueOnError: false  # Fail entire group if any transfer fails
      maxParallelTransfers: 3

      # Individual transfers within the group
      transfers:
        - name: extract-customer-master
          description: "Extract customer master data from ERP system"
          source: "{{sourceERP}}/api/v2/customers/export"
          destination: "{{stagingArea}}/raw/customers/{{processingDate}}.json"
          protocol: https

          # Transfer-specific options
          options:
            timeout: 1800  # 30 minutes
            checksumValidation: true
            checksumAlgorithm: SHA256
            retryAttempts: 3
            chunkSize: 10485760  # 10MB chunks
            compressionEnabled: true

          # Authentication for this specific transfer
          credentials:
            type: oauth2
            tokenEndpoint: "{{sourceERP}}/oauth/token"
            clientId: "quorus-finance-client"
            clientSecret: "{{vault:erp-client-secret}}"
            scope: "customers:read"

          # Transfer-specific metadata
          metadata:
            dataType: "customer-master"
            sensitivity: "confidential"
            retention: "{{retentionPeriodDays}}"

        - name: extract-product-catalog
          description: "Extract product catalog from ERP system"
          source: "{{sourceERP}}/api/v2/products/export?includeInactive=false"
          destination: "{{stagingArea}}/raw/products/{{processingDate}}.json"
          protocol: https

          # This transfer can run in parallel with customer extraction
          dependsOn: []  # No dependencies within this group

          options:
            timeout: 900   # 15 minutes (smaller dataset)
            checksumValidation: true
            retryAttempts: 3

        - name: extract-chart-of-accounts
          description: "Extract chart of accounts and GL structure"
          source: "{{sourceERP}}/api/v2/accounting/coa/export"
          destination: "{{stagingArea}}/raw/coa/{{processingDate}}.json"
          protocol: https

          # This should run after customer master for referential integrity
          dependsOn: [extract-customer-master]

          options:
            timeout: 600   # 10 minutes
            checksumValidation: true

    # Stage 2: Transactional Data Extraction
    - name: extract-transactional-data
      description: |
        Extract transactional data including sales orders, invoices, payments,
        and journal entries. This data depends on master data for referential
        integrity validation.

      # This group depends on master data extraction
      dependsOn: [extract-master-data]
      condition: production-only
      continueOnError: false
      maxParallelTransfers: 4

      transfers:
        - name: extract-sales-orders
          description: "Extract sales orders for processing date"
          source: "{{sourceERP}}/api/v2/sales/orders/export?date={{processingDate}}"
          destination: "{{stagingArea}}/raw/sales-orders/{{processingDate}}.json"
          protocol: https

          options:
            timeout: 2700  # 45 minutes (large dataset)
            checksumValidation: true
            retryAttempts: 5  # Higher retry for critical data

        - name: extract-invoices
          description: "Extract invoice data for processing date"
          source: "{{sourceERP}}/api/v2/accounting/invoices/export?date={{processingDate}}"
          destination: "{{stagingArea}}/raw/invoices/{{processingDate}}.json"
          protocol: https

          # Can run in parallel with sales orders
          dependsOn: []

          options:
            timeout: 1800  # 30 minutes
            checksumValidation: true

        - name: extract-payments
          description: "Extract payment transactions"
          source: "{{sourceERP}}/api/v2/accounting/payments/export?date={{processingDate}}"
          destination: "{{stagingArea}}/raw/payments/{{processingDate}}.json"
          protocol: https

          # Payments should be extracted after invoices for reconciliation
          dependsOn: [extract-invoices]

        - name: extract-journal-entries
          description: "Extract general ledger journal entries"
          source: "{{sourceERP}}/api/v2/accounting/journal/export?date={{processingDate}}"
          destination: "{{stagingArea}}/raw/journal/{{processingDate}}.json"
          protocol: https

          # Only extract if month-end processing is required
          condition: month-end

    # Stage 3: Data Validation and Quality Checks
    - name: data-validation
      description: |
        Perform comprehensive data validation including referential integrity
        checks, business rule validation, and data quality assessments.
        Generate validation reports for audit purposes.

      dependsOn: [extract-master-data, extract-transactional-data]
      condition: production-only
      continueOnError: true  # Continue even if some validations fail

      transfers:
        - name: validate-referential-integrity
          description: "Validate referential integrity between datasets"
          source: "{{stagingArea}}/scripts/validate-integrity.py"
          destination: "{{stagingArea}}/validation/integrity-{{processingDate}}.log"
          protocol: file

          # Custom validation script execution
          options:
            executeScript: true
            scriptArguments:
              - "--date={{processingDate}}"
              - "--input-dir={{stagingArea}}/raw"
              - "--output-dir={{stagingArea}}/validation"

        - name: data-quality-assessment
          description: "Perform data quality checks and generate metrics"
          source: "{{stagingArea}}/scripts/quality-check.py"
          destination: "{{stagingArea}}/quality/quality-report-{{processingDate}}.json"
          protocol: file

          dependsOn: [validate-referential-integrity]

        - name: generate-audit-report
          description: "Generate audit report for compliance"
          source: "{{stagingArea}}/validation"
          destination: "{{archiveLocation}}/audit/audit-report-{{processingDate}}.pdf"
          protocol: file

          # Only generate if audit is required
          condition: "{{auditRequired}} == true"

          options:
            reportFormat: "pdf"
            includeMetrics: true
            digitalSignature: true

    # Stage 4: Data Transformation and Processing
    - name: data-transformation
      description: |
        Transform raw data into analytics-ready format including data
        cleansing, standardization, enrichment, and aggregation.

      dependsOn: [data-validation]
      condition: business-hours  # Only during business hours for resource management
      continueOnError: false
      maxParallelTransfers: 2  # Resource-intensive operations

      transfers:
        - name: transform-customer-data
          description: "Transform and enrich customer data"
          source: "{{stagingArea}}/raw/customers/{{processingDate}}.json"
          destination: "{{targetWarehouse}}/processed/customers/{{processingDate}}.parquet"
          protocol: file

          options:
            transformationScript: "{{stagingArea}}/transforms/customer-transform.sql"
            outputFormat: "parquet"
            compressionCodec: "snappy"
            partitionBy: ["region", "customer_type"]

        - name: transform-financial-data
          description: "Transform financial transactions"
          source: "{{stagingArea}}/raw/invoices/{{processingDate}}.json"
          destination: "{{targetWarehouse}}/processed/financials/{{processingDate}}.parquet"
          protocol: file

          dependsOn: [transform-customer-data]  # Needs customer data for enrichment

          options:
            transformationScript: "{{stagingArea}}/transforms/financial-transform.sql"
            outputFormat: "parquet"

    # Stage 5: Data Loading and Publishing
    - name: data-loading
      description: |
        Load processed data into analytics systems and publish to
        downstream consumers including BI tools and reporting systems.

      dependsOn: [data-transformation]
      condition: business-hours
      continueOnError: true  # Some downstream systems may be temporarily unavailable

      transfers:
        - name: load-data-warehouse
          description: "Load data into primary data warehouse"
          source: "{{targetWarehouse}}/processed"
          destination: "analytics://warehouse/finance/{{processingDate}}"
          protocol: analytics

          options:
            loadStrategy: "upsert"  # Update existing, insert new
            indexing: true
            statisticsUpdate: true

        - name: publish-to-bi-system
          description: "Publish data to business intelligence system"
          source: "{{targetWarehouse}}/processed"
          destination: "bi://finance-mart/{{processingDate}}"
          protocol: bi

          # Can run in parallel with data warehouse loading
          dependsOn: []

        - name: update-data-catalog
          description: "Update enterprise data catalog with new datasets"
          source: "{{targetWarehouse}}/processed"
          destination: "catalog://finance/datasets/{{processingDate}}"
          protocol: catalog

          options:
            updateMetadata: true
            generateLineage: true

  # Workflow-level notifications
  notifications:
    # Success notifications
    onSuccess:
      - type: email
        recipients: ["{{primaryNotificationEmail}}"]
        subject: "Finance Data Pipeline Completed Successfully - {{processingDate}}"
        template: "workflow-success"
        includeMetrics: true

      - type: slack
        channel: "{{slackChannel}}"
        message: "✅ Finance data pipeline completed successfully for {{processingDate}}"

    # Failure notifications
    onFailure:
      - type: email
        recipients: ["{{primaryNotificationEmail}}", "{{escalationEmail}}"]
        subject: "URGENT: Finance Data Pipeline Failed - {{processingDate}}"
        template: "workflow-failure"
        includeErrorDetails: true
        includeLogs: true

      - type: slack
        channel: "{{slackChannel}}"
        message: "🚨 Finance data pipeline FAILED for {{processingDate}} - immediate attention required"

      - type: webhook
        url: "https://alerts.acme-corp.com/api/incidents"
        method: POST
        headers:
          Authorization: "Bearer {{vault:alerting-token}}"
        payload:
          severity: "high"
          service: "finance-data-pipeline"
          description: "Workflow execution failed"

    # Progress notifications (optional)
    onProgress:
      enabled: true
      intervalMinutes: 30
      recipients: ["{{primaryNotificationEmail}}"]

  # Cleanup and maintenance
  cleanup:
    # Automatic cleanup of staging data
    enabled: true
    retainStagingDays: 7
    retainLogsDays: 30

    # Archive completed workflows
    archiveEnabled: true
    archiveLocation: "{{archiveLocation}}/workflows"

  # Service Level Agreements
  sla:
    maxExecutionTime: "PT4H"      # 4 hours maximum
    targetExecutionTime: "PT2H"   # 2 hours target
    availabilityTarget: 99.5      # 99.5% success rate

  # Monitoring and observability
  monitoring:
    metricsEnabled: true
    tracingEnabled: true
    customMetrics:
      - name: "records_processed"
        type: "counter"
        description: "Number of records processed"

      - name: "data_quality_score"
        type: "gauge"
        description: "Overall data quality score"
```

#### Dependency Resolution Engine

The **Dependency Resolution Engine** is a sophisticated system that analyzes workflow definitions, builds dependency graphs, and creates optimal execution plans. It handles complex dependency scenarios while ensuring data consistency and resource optimization.

##### Dependency Types and Analysis

**1. Explicit Dependencies**
Explicitly defined via `dependsOn` clauses in the workflow definition:

```yaml
transferGroups:
  - name: extract-customers
    transfers:
      - name: customer-data
        source: "{{sourceSystem}}/customers"
        destination: "{{staging}}/customers.json"

  - name: extract-orders
    # Explicit dependency - orders need customer data for validation
    dependsOn: [extract-customers]
    transfers:
      - name: order-data
        source: "{{sourceSystem}}/orders"
        destination: "{{staging}}/orders.json"

  - name: generate-reports
    # Multiple explicit dependencies
    dependsOn: [extract-customers, extract-orders]
    transfers:
      - name: customer-order-report
        source: "{{staging}}/customers.json,{{staging}}/orders.json"
        destination: "{{reports}}/customer-orders.pdf"
```

**2. Implicit Dependencies**
Automatically inferred from data flow patterns:

```yaml
transferGroups:
  - name: stage-1
    transfers:
      - name: raw-data-extract
        source: "{{source}}/raw-data.csv"
        destination: "{{staging}}/raw-data.csv"

  - name: stage-2
    transfers:
      - name: process-data
        # Implicit dependency inferred from source path
        source: "{{staging}}/raw-data.csv"  # Uses output from stage-1
        destination: "{{processed}}/clean-data.csv"

  - name: stage-3
    transfers:
      - name: final-report
        # Implicit dependency on processed data
        source: "{{processed}}/clean-data.csv"  # Uses output from stage-2
        destination: "{{reports}}/final-report.pdf"
```

**3. Conditional Dependencies**
Dependencies that apply only under certain conditions:

```yaml
conditions:
  - name: month-end
    expression: "{{dayOfMonth}} >= 28"
  - name: quarter-end
    expression: "{{isQuarterEnd(today)}}"

transferGroups:
  - name: daily-processing
    transfers:
      - name: daily-sales
        source: "{{source}}/daily-sales.csv"
        destination: "{{staging}}/daily-sales.csv"

  - name: monthly-processing
    # Conditional dependency - only runs at month-end
    dependsOn: [daily-processing]
    condition: month-end
    transfers:
      - name: monthly-aggregation
        source: "{{staging}}/daily-sales.csv"
        destination: "{{reports}}/monthly-summary.csv"

  - name: quarterly-processing
    # Conditional dependency with multiple conditions
    dependsOn: [daily-processing, monthly-processing]
    condition: quarter-end
    transfers:
      - name: quarterly-report
        source: "{{reports}}/monthly-summary.csv"
        destination: "{{reports}}/quarterly-report.pdf"
```

**4. Resource Dependencies**
Dependencies based on shared resources or capacity constraints:

```yaml
transferGroups:
  - name: high-bandwidth-group-1
    transfers:
      - name: large-file-1
        source: "{{source}}/large-file-1.zip"
        destination: "{{target}}/large-file-1.zip"
        options:
          bandwidthMbps: 500  # Requires significant bandwidth

  - name: high-bandwidth-group-2
    # Resource dependency - can't run simultaneously due to bandwidth limits
    resourceDependencies:
      - type: bandwidth
        constraint: "total_bandwidth < 1000"
        conflictsWith: [high-bandwidth-group-1]
    transfers:
      - name: large-file-2
        source: "{{source}}/large-file-2.zip"
        destination: "{{target}}/large-file-2.zip"
        options:
          bandwidthMbps: 600
```

##### Advanced Dependency Graph Analysis

```java
public class AdvancedDependencyGraph {
    private final Map<String, TransferGroup> groups;
    private final Map<String, Set<String>> dependencies;
    private final Map<String, Set<String>> conditionalDependencies;
    private final Map<String, ResourceConstraint> resourceConstraints;

    // Graph construction with validation
    public void addTransferGroup(TransferGroup group) {
        validateGroupDefinition(group);
        groups.put(group.getName(), group);

        // Build explicit dependencies
        for (String dependency : group.getDependsOn()) {
            addDependency(dependency, group.getName(), DependencyType.EXPLICIT);
        }

        // Infer implicit dependencies from data flow
        inferImplicitDependencies(group);

        // Process conditional dependencies
        processConditionalDependencies(group);

        // Analyze resource dependencies
        analyzeResourceDependencies(group);
    }

    // Implicit dependency inference
    private void inferImplicitDependencies(TransferGroup group) {
        for (Transfer transfer : group.getTransfers()) {
            String sourcePath = transfer.getSource();

            // Find groups that produce this source path
            for (TransferGroup otherGroup : groups.values()) {
                if (otherGroup.getName().equals(group.getName())) continue;

                for (Transfer otherTransfer : otherGroup.getTransfers()) {
                    if (sourcePath.equals(otherTransfer.getDestination())) {
                        // Implicit dependency found
                        addDependency(otherGroup.getName(), group.getName(),
                                    DependencyType.IMPLICIT);
                        logger.info("Inferred dependency: {} -> {} (data flow)",
                                  otherGroup.getName(), group.getName());
                    }
                }
            }
        }
    }

    // Advanced cycle detection with detailed reporting
    public CycleAnalysisResult detectCycles() {
        Set<String> visited = new HashSet<>();
        Set<String> recursionStack = new HashSet<>();
        List<List<String>> cycles = new ArrayList<>();

        for (String group : groups.keySet()) {
            if (!visited.contains(group)) {
                List<String> currentPath = new ArrayList<>();
                if (detectCyclesDFS(group, visited, recursionStack, currentPath, cycles)) {
                    // Cycle detected
                }
            }
        }

        return new CycleAnalysisResult(cycles.isEmpty(), cycles,
                                     generateCycleResolutionSuggestions(cycles));
    }

    // Critical path analysis for performance optimization
    public CriticalPathAnalysis calculateCriticalPath() {
        Map<String, Integer> distances = new HashMap<>();
        Map<String, String> predecessors = new HashMap<>();

        // Initialize distances
        for (String group : groups.keySet()) {
            distances.put(group, 0);
        }

        // Calculate longest path (critical path)
        List<String> topologicalOrder = topologicalSort();
        for (String group : topologicalOrder) {
            TransferGroup transferGroup = groups.get(group);
            int groupDuration = estimateGroupDuration(transferGroup);

            for (String dependent : getDependents(group)) {
                int newDistance = distances.get(group) + groupDuration;
                if (newDistance > distances.get(dependent)) {
                    distances.put(dependent, newDistance);
                    predecessors.put(dependent, group);
                }
            }
        }

        // Build critical path
        List<String> criticalPath = buildCriticalPath(distances, predecessors);
        int totalDuration = distances.values().stream().mapToInt(Integer::intValue).max().orElse(0);

        return new CriticalPathAnalysis(criticalPath, totalDuration,
                                      identifyBottlenecks(criticalPath));
    }

    // Intelligent execution planning
    public ExecutionPlan createOptimalExecutionPlan(ExecutionStrategy strategy,
                                                   ResourceConstraints constraints) {
        // Validate dependencies
        CycleAnalysisResult cycleAnalysis = detectCycles();
        if (cycleAnalysis.hasCycles()) {
            throw new DependencyException("Circular dependencies detected: " +
                                        cycleAnalysis.getCycles());
        }

        // Calculate critical path
        CriticalPathAnalysis criticalPath = calculateCriticalPath();

        // Create execution stages based on strategy
        List<ExecutionStage> stages = switch (strategy) {
            case SEQUENTIAL -> createSequentialPlan();
            case PARALLEL -> createParallelPlan(constraints);
            case MIXED -> createMixedPlan(constraints, criticalPath);
            case ADAPTIVE -> createAdaptivePlan(constraints, criticalPath);
        };

        return new ExecutionPlan(stages, criticalPath, estimateTotalDuration(stages));
    }

    // Mixed execution strategy with intelligent optimization
    private List<ExecutionStage> createMixedPlan(ResourceConstraints constraints,
                                               CriticalPathAnalysis criticalPath) {
        List<ExecutionStage> stages = new ArrayList<>();
        Set<String> completed = new HashSet<>();

        while (completed.size() < groups.size()) {
            ExecutionStage stage = new ExecutionStage();

            // Find groups ready for execution
            Set<String> readyGroups = findReadyGroups(completed);

            // Prioritize critical path groups
            List<String> prioritizedGroups = prioritizeGroups(readyGroups, criticalPath);

            // Add groups to stage respecting resource constraints
            for (String group : prioritizedGroups) {
                if (stage.canAddGroup(groups.get(group), constraints)) {
                    stage.addGroup(groups.get(group));
                    completed.add(group);
                }
            }

            if (!stage.isEmpty()) {
                stages.add(stage);
            } else {
                // Deadlock detection
                throw new ExecutionPlanningException("Unable to create execution plan - " +
                                                   "possible resource deadlock");
            }
        }

        return optimizeStages(stages, constraints);
    }
}

// Supporting classes for dependency analysis
public class CycleAnalysisResult {
    private final boolean hasCycles;
    private final List<List<String>> cycles;
    private final List<String> resolutionSuggestions;

    // Methods for cycle analysis and resolution
}

public class CriticalPathAnalysis {
    private final List<String> criticalPath;
    private final int totalDuration;
    private final List<String> bottlenecks;
    private final Map<String, Integer> slackTimes;

    // Methods for critical path optimization
}

public class ExecutionStage {
    private final List<TransferGroup> groups;
    private final ResourceRequirements requirements;
    private final int estimatedDuration;

    public boolean canAddGroup(TransferGroup group, ResourceConstraints constraints) {
        ResourceRequirements newRequirements = requirements.add(group.getResourceRequirements());
        return constraints.canAccommodate(newRequirements);
    }
}
```

##### Execution Strategies with Detailed Examples

**1. Sequential Execution**
Execute transfer groups one at a time in dependency order:

```yaml
# Workflow configuration
execution:
  strategy: sequential

# Results in execution plan:
# Stage 1: [extract-customers]
# Stage 2: [extract-orders]
# Stage 3: [generate-reports]
```

**Benefits:**
- Predictable resource usage
- Simple error handling
- Guaranteed dependency satisfaction

**Use Cases:**
- Resource-constrained environments
- Critical workflows requiring careful control
- Debugging and testing scenarios

**2. Parallel Execution**
Execute all independent groups simultaneously:

```yaml
# Workflow configuration
execution:
  strategy: parallel
  parallelism: 10

# Results in execution plan:
# Stage 1: [extract-customers, extract-products, extract-vendors]
# Stage 2: [extract-orders, process-customers, process-products]
# Stage 3: [generate-reports, update-warehouse]
```

**Benefits:**
- Maximum throughput
- Optimal resource utilization
- Shortest total execution time

**Use Cases:**
- High-performance environments
- Independent data processing
- Batch processing scenarios

**3. Mixed Execution (Recommended)**
Intelligent optimization based on dependencies and resources:

```yaml
# Workflow configuration
execution:
  strategy: mixed
  parallelism: 5
  resources:
    maxMemoryMB: 4096
    maxCpuCores: 8
    maxNetworkBandwidthMbps: 1000

# Results in optimized execution plan:
# Stage 1: [extract-customers, extract-products] (parallel, independent)
# Stage 2: [extract-orders] (depends on customers)
# Stage 3: [process-customers, process-products] (parallel, sufficient resources)
# Stage 4: [generate-reports] (depends on all processing)
```

**Benefits:**
- Balanced performance and resource usage
- Intelligent dependency handling
- Adaptive to resource constraints

**Use Cases:**
- Production environments
- Complex workflows with mixed dependencies
- Resource-aware execution

**4. Adaptive Execution (Advanced)**
Dynamic optimization based on runtime conditions:

```yaml
# Workflow configuration
execution:
  strategy: adaptive
  adaptiveSettings:
    monitorPerformance: true
    adjustParallelism: true
    resourceThresholds:
      cpuUtilization: 80
      memoryUtilization: 75
      networkUtilization: 70
```

**Benefits:**
- Runtime optimization
- Self-tuning performance
- Automatic resource management

**Use Cases:**
- Long-running workflows
- Variable resource environments
- Performance-critical applications

#### Variable Resolution System

The **Variable Resolution System** is a sophisticated templating engine that provides dynamic configuration management, enabling workflows to adapt to different environments, dates, and runtime conditions. It supports complex expressions, built-in functions, and hierarchical variable resolution.

##### Variable Resolution Hierarchy

Variables are resolved in the following precedence order (highest to lowest):

**1. Runtime Context Variables**
Variables provided at workflow execution time:

```bash
# Command line execution with runtime variables
quorus-workflow execute daily-sync.yaml \
  --variable processingDate=2025-01-20 \
  --variable environment=production \
  --variable urgentMode=true

# REST API execution with runtime variables
POST /api/v1/workflows
{
  "workflowDefinition": "...",
  "runtimeVariables": {
    "processingDate": "2025-01-20",
    "environment": "production",
    "urgentMode": true,
    "customBatchSize": 50000
  }
}
```

**2. Workflow Definition Variables**
Variables defined in the workflow spec:

```yaml
spec:
  variables:
    # Static configuration
    sourceSystem: "https://erp.corp.internal"
    targetWarehouse: "/data-warehouse/finance"

    # Dynamic variables using built-in functions
    processingDate: "{{today}}"
    processingTimestamp: "{{timestamp}}"

    # Computed variables using other variables
    archivePath: "{{targetWarehouse}}/archive/{{processingDate}}"
    logFile: "{{targetWarehouse}}/logs/workflow-{{processingTimestamp}}.log"
```

**3. Tenant Configuration Variables**
Tenant-specific default values:

```yaml
# Tenant configuration (managed separately)
tenantDefaults:
  acme-corp:
    defaultTimeout: 1800
    maxRetries: 5
    notificationEmail: "ops@acme-corp.com"
    dataRetentionDays: 2555
    encryptionRequired: true
```

**4. System Default Variables**
Global system defaults:

```yaml
# System defaults (built into Quorus)
systemDefaults:
  defaultTimeout: 300
  maxRetries: 3
  checksumValidation: true
  compressionEnabled: false
  parallelism: 3
```

**5. Built-in Functions and System Variables**
System-provided functions and runtime values.

##### Comprehensive Built-in Functions

**Date and Time Functions:**
```yaml
variables:
  # Basic date functions
  today: "{{today}}"                           # 2025-01-20
  yesterday: "{{yesterday}}"                   # 2025-01-19
  tomorrow: "{{tomorrow}}"                     # 2025-01-21

  # Formatted dates
  todayFormatted: "{{format(today, 'yyyy-MM-dd')}}"      # 2025-01-20
  monthYear: "{{format(today, 'yyyy-MM')}}"              # 2025-01
  yearOnly: "{{format(today, 'yyyy')}}"                  # 2025

  # Date arithmetic
  lastWeek: "{{addDays(today, -7)}}"                     # 2025-01-13
  nextMonth: "{{addMonths(today, 1)}}"                   # 2025-02-20
  quarterStart: "{{startOfQuarter(today)}}"              # 2025-01-01
  monthEnd: "{{endOfMonth(today)}}"                      # 2025-01-31

  # Time components
  currentHour: "{{hour}}"                      # 14 (2 PM)
  currentMinute: "{{minute}}"                  # 30
  dayOfWeek: "{{dayOfWeek}}"                   # 1 (Monday)
  dayOfMonth: "{{dayOfMonth}}"                 # 20
  dayOfYear: "{{dayOfYear}}"                   # 20
  weekOfYear: "{{weekOfYear}}"                 # 3

  # Timestamps
  timestamp: "{{timestamp}}"                   # 2025-01-20T14:30:00Z
  timestampMillis: "{{timestampMillis}}"       # 1737379800000
  isoTimestamp: "{{isoTimestamp}}"             # 2025-01-20T14:30:00.000Z

  # Business date functions
  isBusinessDay: "{{isBusinessDay(today)}}"    # true/false
  nextBusinessDay: "{{nextBusinessDay(today)}}" # 2025-01-21
  businessDaysInMonth: "{{businessDaysInMonth(today)}}" # 22

  # Fiscal calendar functions (configurable fiscal year start)
  fiscalYear: "{{fiscalYear(today)}}"          # 2025 (if fiscal year starts in January)
  fiscalQuarter: "{{fiscalQuarter(today)}}"    # Q1-2025
  fiscalMonth: "{{fiscalMonth(today)}}"        # 1 (first month of fiscal year)
  isQuarterEnd: "{{isQuarterEnd(today)}}"      # true/false
  isYearEnd: "{{isYearEnd(today)}}"            # true/false
```

**System and Environment Functions:**
```yaml
variables:
  # System information
  hostname: "{{hostname}}"                     # quorus-controller-01
  ipAddress: "{{ipAddress}}"                   # 10.0.1.100
  region: "{{region}}"                         # us-east-1
  availabilityZone: "{{availabilityZone}}"     # us-east-1a

  # Execution context
  tenant: "{{tenant}}"                         # acme-corp
  namespace: "{{namespace}}"                   # finance
  workflowName: "{{workflowName}}"             # daily-financial-sync
  executionId: "{{executionId}}"               # wf-exec-20250120-001

  # User context
  executedBy: "{{executedBy}}"                 # user@corp.com
  executionMode: "{{executionMode}}"           # normal/dry-run/virtual-run

  # Environment detection
  environment: "{{environment}}"               # production/staging/development
  isProduction: "{{environment == 'production'}}" # true/false
  isDevelopment: "{{environment == 'development'}}" # true/false
```

**Utility and Generation Functions:**
```yaml
variables:
  # Unique identifiers
  uuid: "{{uuid}}"                             # 550e8400-e29b-41d4-a716-446655440000
  shortUuid: "{{shortUuid}}"                   # a1b2c3d4
  correlationId: "{{correlationId}}"           # Unique per workflow execution

  # Random values
  randomNumber: "{{random(1000)}}"             # 0-999
  randomString: "{{randomString(8)}}"          # 8-character random string
  randomAlphanumeric: "{{randomAlphanumeric(12)}}" # 12-character alphanumeric

  # Encoding functions
  base64Encode: "{{base64Encode('hello world')}}" # aGVsbG8gd29ybGQ=
  base64Decode: "{{base64Decode('aGVsbG8gd29ybGQ=')}}" # hello world
  urlEncode: "{{urlEncode('hello world')}}"    # hello%20world

  # Hash functions
  md5Hash: "{{md5('hello world')}}"            # 5d41402abc4b2a76b9719d911017c592
  sha256Hash: "{{sha256('hello world')}}"      # b94d27b9934d3e08a52e52d7da7dabfac484efe37a5380ee9088f7ace2efcde9
```

**String Manipulation Functions:**
```yaml
variables:
  # Case conversion
  upperCase: "{{upper(department)}}"           # FINANCE
  lowerCase: "{{lower(department)}}"           # finance
  titleCase: "{{title(department)}}"           # Finance
  camelCase: "{{camel(department_name)}}"      # departmentName

  # String operations
  substring: "{{substring(sourceFile, 0, 10)}}" # First 10 characters
  length: "{{length(sourceFile)}}"             # String length
  trim: "{{trim(inputString)}}"                # Remove whitespace

  # String replacement
  replace: "{{replace(sourceFile, '.csv', '.json')}}" # Replace extension
  replaceRegex: "{{replaceRegex(sourceFile, '[0-9]+', 'XXX')}}" # Replace numbers

  # String splitting and joining
  split: "{{split(csvList, ',')}}"             # Split into array
  join: "{{join(arrayVar, '|')}}"              # Join array with delimiter

  # String validation
  contains: "{{contains(sourceFile, 'customer')}}" # true/false
  startsWith: "{{startsWith(sourceFile, 'data')}}" # true/false
  endsWith: "{{endsWith(sourceFile, '.csv')}}"     # true/false
  matches: "{{matches(sourceFile, '[a-z]+\\.csv')}}" # Regex match
```

**Mathematical and Logical Functions:**
```yaml
variables:
  # Arithmetic operations
  sum: "{{add(batchSize, bufferSize)}}"        # Addition
  difference: "{{subtract(totalRecords, processedRecords)}}" # Subtraction
  product: "{{multiply(batchSize, parallelism)}}" # Multiplication
  quotient: "{{divide(totalSize, chunkSize)}}" # Division
  remainder: "{{mod(totalRecords, batchSize)}}" # Modulo

  # Mathematical functions
  absolute: "{{abs(difference)}}"              # Absolute value
  minimum: "{{min(timeout1, timeout2)}}"      # Minimum value
  maximum: "{{max(retries1, retries2)}}"      # Maximum value
  round: "{{round(averageSize)}}"              # Round to nearest integer
  ceiling: "{{ceil(averageSize)}}"             # Round up
  floor: "{{floor(averageSize)}}"              # Round down

  # Logical operations
  and: "{{and(isProduction, isBusinessDay)}}" # Logical AND
  or: "{{or(isUrgent, isHighPriority)}}"      # Logical OR
  not: "{{not(isTestMode)}}"                   # Logical NOT

  # Comparison functions
  equals: "{{eq(environment, 'production')}}" # Equality
  notEquals: "{{ne(status, 'failed')}}"       # Inequality
  greaterThan: "{{gt(fileSize, maxSize)}}"    # Greater than
  lessThan: "{{lt(retryCount, maxRetries)}}"  # Less than
  greaterEqual: "{{gte(progress, 50)}}"       # Greater than or equal
  lessEqual: "{{lte(errorRate, threshold)}}"  # Less than or equal
```

**Conditional and Control Flow Functions:**
```yaml
variables:
  # Simple conditional
  databaseUrl: "{{if(isProduction, 'prod-db.corp.com', 'test-db.corp.com')}}"

  # Multi-condition switch
  logLevel: "{{switch(environment,
              'production', 'WARN',
              'staging', 'INFO',
              'development', 'DEBUG',
              'INFO')}}"  # default value

  # Null coalescing
  timeout: "{{coalesce(customTimeout, defaultTimeout, 300)}}" # First non-null value

  # Default value assignment
  batchSize: "{{default(configuredBatchSize, 1000)}}" # Use default if null/empty

  # Conditional execution
  enableAudit: "{{if(and(isProduction, isFinancialData), true, false)}}"

  # Complex conditional logic
  processingMode: "{{if(isUrgent, 'fast',
                       if(isLargeDataset, 'batch', 'standard'))}}"
```

**Data Structure and Collection Functions:**
```yaml
variables:
  # Array operations
  arrayLength: "{{size(fileList)}}"            # Array size
  firstItem: "{{first(fileList)}}"             # First element
  lastItem: "{{last(fileList)}}"               # Last element
  arrayContains: "{{contains(fileList, 'data.csv')}}" # Contains check

  # Array manipulation
  sortedArray: "{{sort(fileList)}}"            # Sort array
  uniqueArray: "{{unique(fileList)}}"          # Remove duplicates
  reverseArray: "{{reverse(fileList)}}"        # Reverse order

  # Map/object operations
  mapKeys: "{{keys(configMap)}}"               # Get all keys
  mapValues: "{{values(configMap)}}"           # Get all values
  hasKey: "{{hasKey(configMap, 'timeout')}}"   # Check key existence
  getValue: "{{get(configMap, 'timeout', 300)}}" # Get value with default
```

**File and Path Functions:**
```yaml
variables:
  # Path manipulation
  fileName: "{{basename(sourceFile)}}"         # Extract filename
  directory: "{{dirname(sourceFile)}}"         # Extract directory
  extension: "{{extension(sourceFile)}}"       # Extract file extension
  nameWithoutExt: "{{nameWithoutExtension(sourceFile)}}" # Filename without extension

  # Path construction
  fullPath: "{{joinPath(baseDir, subDir, fileName)}}" # Join path components
  relativePath: "{{relativePath(basePath, fullPath)}}" # Make relative path

  # File information (if accessible)
  fileExists: "{{fileExists(sourceFile)}}"    # Check file existence
  fileSize: "{{fileSize(sourceFile)}}"         # Get file size
  lastModified: "{{lastModified(sourceFile)}}" # Last modification time
```

##### Advanced Variable Resolution Examples

**Complex Business Logic:**
```yaml
variables:
  # Dynamic source selection based on business rules
  primarySource: "{{if(isBusinessDay(today),
                      'https://erp.corp.com/api/daily',
                      'https://erp.corp.com/api/weekend')}}"

  # Conditional batch sizing based on data volume
  batchSize: "{{if(gt(estimatedRecords, 1000000), 50000,
                if(gt(estimatedRecords, 100000), 10000, 1000))}}"

  # Dynamic timeout based on file size and network conditions
  transferTimeout: "{{multiply(divide(fileSizeBytes, 1048576),
                              if(eq(networkQuality, 'high'), 2,
                                if(eq(networkQuality, 'medium'), 5, 10)))}}"

  # Compliance-based retention calculation
  retentionDays: "{{if(contains(dataClassification, 'financial'), 2555,
                    if(contains(dataClassification, 'personal'), 1095, 365)}}"
```

**Environment-Specific Configuration:**
```yaml
variables:
  # Environment-specific endpoints
  apiEndpoint: "{{switch(environment,
                  'production', 'https://api.corp.com',
                  'staging', 'https://api-staging.corp.com',
                  'development', 'https://api-dev.corp.com',
                  'http://localhost:8080')}}"

  # Environment-specific resource limits
  maxConcurrentTransfers: "{{switch(environment,
                            'production', 50,
                            'staging', 20,
                            'development', 5,
                            3)}}"

  # Environment-specific notification settings
  notificationLevel: "{{switch(environment,
                        'production', 'ERROR',
                        'staging', 'WARN',
                        'development', 'DEBUG',
                        'INFO')}}"
```

**Date-Based Processing Logic:**
```yaml
variables:
  # Month-end processing flag
  isMonthEnd: "{{gte(dayOfMonth, 28)}}"

  # Quarter-end additional processing
  includeQuarterlyReports: "{{isQuarterEnd(today)}}"

  # Business day processing schedule
  processingSchedule: "{{if(isBusinessDay(today),
                          if(eq(dayOfWeek, 1), 'monday-full',
                            if(eq(dayOfWeek, 5), 'friday-eod', 'daily-standard')),
                          'weekend-maintenance')}}"

  # Fiscal year-based archival
  archiveLocation: "{{joinPath(baseArchive, fiscalYear(today),
                               format(today, 'MM'), format(today, 'dd'))}}"
```

##### Variable Validation and Error Handling

**Type Validation:**
```java
public class VariableValidator {
    public ValidationResult validateVariable(String name, Object value, VariableDefinition definition) {
        // Type checking
        if (!isCorrectType(value, definition.getType())) {
            return ValidationResult.error("Variable '" + name + "' expected type " +
                                        definition.getType() + " but got " + value.getClass());
        }

        // Range validation for numbers
        if (definition.getType() == VariableType.NUMBER) {
            Number numValue = (Number) value;
            if (definition.hasMinValue() && numValue.doubleValue() < definition.getMinValue()) {
                return ValidationResult.error("Variable '" + name + "' value " + numValue +
                                            " is below minimum " + definition.getMinValue());
            }
            if (definition.hasMaxValue() && numValue.doubleValue() > definition.getMaxValue()) {
                return ValidationResult.error("Variable '" + name + "' value " + numValue +
                                            " exceeds maximum " + definition.getMaxValue());
            }
        }

        // Pattern validation for strings
        if (definition.getType() == VariableType.STRING && definition.hasPattern()) {
            String strValue = (String) value;
            if (!strValue.matches(definition.getPattern())) {
                return ValidationResult.error("Variable '" + name + "' value '" + strValue +
                                            "' does not match pattern " + definition.getPattern());
            }
        }

        return ValidationResult.success();
    }
}
```

**Circular Reference Detection:**
```java
public class CircularReferenceDetector {
    public void detectCircularReferences(Map<String, String> variables) {
        Set<String> visiting = new HashSet<>();
        Set<String> visited = new HashSet<>();

        for (String variable : variables.keySet()) {
            if (!visited.contains(variable)) {
                detectCircularReferenceDFS(variable, variables, visiting, visited);
            }
        }
    }

    private void detectCircularReferenceDFS(String variable, Map<String, String> variables,
                                          Set<String> visiting, Set<String> visited) {
        if (visiting.contains(variable)) {
            throw new CircularReferenceException("Circular reference detected involving variable: " + variable);
        }

        if (visited.contains(variable)) {
            return;
        }

        visiting.add(variable);

        // Find all variables referenced by this variable
        Set<String> referencedVariables = extractVariableReferences(variables.get(variable));
        for (String referenced : referencedVariables) {
            if (variables.containsKey(referenced)) {
                detectCircularReferenceDFS(referenced, variables, visiting, visited);
            }
        }

        visiting.remove(variable);
        visited.add(variable);
    }
}
```

This comprehensive variable resolution system enables workflows to be highly dynamic and adaptable while maintaining type safety and preventing common configuration errors.

### Multi-Tenancy Concepts

#### Tenant Hierarchy Model

The **Tenant Hierarchy** provides organizational structure and inheritance:

```mermaid
graph TB
    subgraph "Enterprise Tenant"
        ET[Enterprise Root]

        subgraph "Corporate Tenants"
            ACME[ACME Corp]
            PARTNER[Partner Corp]
        end

        subgraph "ACME Departments"
            FIN[Finance Dept]
            HR[HR Dept]
            IT[IT Dept]
        end

        subgraph "Finance Teams"
            ACC[Accounting Team]
            BUD[Budget Team]
            AUD[Audit Team]
        end
    end

    ET --> ACME
    ET --> PARTNER
    ACME --> FIN
    ACME --> HR
    ACME --> IT
    FIN --> ACC
    FIN --> BUD
    FIN --> AUD
```

**Tenant Properties:**
- **Identity**: Unique tenant ID and display name
- **Hierarchy**: Parent-child relationships with inheritance
- **Resources**: Quotas, limits, and usage tracking
- **Security**: Authentication providers and authorization policies
- **Storage**: Isolation strategy and storage configuration
- **Workflows**: Tenant-specific workflow definitions and templates

#### Resource Management System

The **Resource Management System** enforces quotas and tracks usage across the tenant hierarchy:

**Resource Types:**
```java
public enum ResourceType {
    // Transfer resources
    CONCURRENT_TRANSFERS,    // Number of simultaneous transfers
    TRANSFER_BANDWIDTH,      // Total bandwidth allocation
    TRANSFER_VOLUME,         // Data volume per time period

    // Storage resources
    STORAGE_QUOTA,           // Total storage allocation
    FILE_COUNT,              // Maximum number of files
    FILE_SIZE,               // Maximum individual file size

    // Compute resources
    CPU_ALLOCATION,          // CPU cores for processing
    MEMORY_ALLOCATION,       // Memory allocation

    // API resources
    API_REQUESTS,            // API calls per time period
    WORKFLOW_EXECUTIONS      // Workflow executions per time period
}
```

**Quota Enforcement:**
- **Hard Limits**: Strict enforcement that blocks operations
- **Soft Limits**: Warning thresholds with notification
- **Burst Capacity**: Temporary allowance above normal limits
- **Time-based Quotas**: Quotas that reset over time periods

**Usage Tracking:**
```java
public class ResourceUsage {
    private String tenantId;
    private ResourceType resourceType;
    private long currentUsage;
    private long quotaLimit;
    private Instant lastUpdated;
    private Map<String, Long> usageBreakdown; // by namespace, user, etc.

    // Usage calculations
    public double getUtilizationPercentage();
    public long getRemainingQuota();
    public boolean isOverQuota();
    public Duration getTimeUntilReset();
}
```

#### Isolation Strategies

The **Isolation System** provides different levels of tenant separation:

**1. Logical Isolation:**
- **Shared Infrastructure**: All tenants share the same physical resources
- **Namespace Separation**: Logical separation using tenant prefixes
- **Access Control**: RBAC-based access restrictions
- **Data Tagging**: Metadata-based tenant identification

**2. Physical Isolation:**
- **Dedicated Resources**: Separate infrastructure per tenant
- **Network Isolation**: VLANs or separate network segments
- **Storage Isolation**: Dedicated storage systems or volumes
- **Compute Isolation**: Dedicated servers or containers

**3. Hybrid Isolation:**
- **Sensitive Data**: Physical isolation for critical tenants
- **Regular Data**: Logical isolation for standard tenants
- **Flexible Migration**: Ability to move between isolation levels
- **Cost Optimization**: Balance security and cost requirements

This enhanced system design and functional concepts section provides deep technical insight into how Quorus operates internally, helping users understand not just how to use the system, but how it works under the hood to provide enterprise-grade file transfer capabilities.

## Getting Started

### Prerequisites

- **Java 21+** - Required for running Quorus components
- **Docker** (optional) - For containerized deployments
- **Network Access** - Internal corporate network connectivity
- **Storage Access** - Permissions to source and destination locations

### Quick Start

#### 1. Basic Installation

```bash
# Download and extract Quorus
wget https://releases.quorus.dev/v2.0/quorus-2.0.tar.gz
tar -xzf quorus-2.0.tar.gz
cd quorus-2.0

# Start the API service
java -jar quorus-api-2.0.jar

# Verify installation
curl http://localhost:8080/api/v1/info
```

#### 2. Docker Deployment

```bash
# Clone the repository
git clone https://github.com/your-org/quorus.git
cd quorus

# Start with Docker Compose
docker-compose up -d

# Check cluster status
curl http://localhost:8081/health
curl http://localhost:8082/health
curl http://localhost:8083/health
```

#### 3. First Transfer

```bash
# Create a simple transfer job
curl -X POST http://localhost:8080/api/v1/transfers \
  -H "Content-Type: application/json" \
  -d '{
    "sourceUri": "https://httpbin.org/json",
    "destinationPath": "/tmp/test-download.json",
    "protocol": "http"
  }'
```

## Basic File Transfers

### Supported Protocols

| Protocol | Description | Use Case |
|----------|-------------|----------|
| HTTP/HTTPS | Web-based transfers | API downloads, web content |
| FTP | File Transfer Protocol | Legacy system integration |
| SFTP | Secure FTP | Secure file transfers |
| SMB | Server Message Block | Windows file shares |

### Simple Transfer Examples

#### HTTP Download

```java
// Java API Example
TransferEngine engine = new SimpleTransferEngine();

TransferRequest request = TransferRequest.builder()
    .sourceUri("https://api.internal.corp/data/export.csv")
    .destinationPath("/corporate-storage/imports/daily-export.csv")
    .protocol(TransferProtocol.HTTP)
    .checksumValidation(true)
    .build();

CompletableFuture<TransferResult> future = engine.submitTransfer(request);
TransferResult result = future.get();

if (result.isSuccessful()) {
    System.out.println("Transfer completed: " + result.getBytesTransferred() + " bytes");
} else {
    System.err.println("Transfer failed: " + result.getErrorMessage());
}
```

#### REST API Transfer

```bash
# Submit transfer job
curl -X POST http://localhost:8080/api/v1/transfers \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer YOUR_TOKEN" \
  -d '{
    "sourceUri": "sftp://fileserver.corp.local/exports/data.zip",
    "destinationPath": "/data-warehouse/incoming/data.zip",
    "protocol": "sftp",
    "credentials": {
      "username": "transfer-user",
      "keyPath": "/etc/quorus/keys/transfer-key"
    },
    "options": {
      "checksumValidation": true,
      "retryAttempts": 3,
      "timeout": 300
    }
  }'

# Check transfer status
curl http://localhost:8080/api/v1/transfers/{job-id}/status
```

### Progress Monitoring

```bash
# Monitor transfer progress
curl http://localhost:8080/api/v1/transfers/{job-id}/progress

# Example response
{
  "jobId": "transfer-123",
  "status": "IN_PROGRESS",
  "bytesTransferred": 1048576,
  "totalBytes": 10485760,
  "progressPercentage": 10.0,
  "transferRate": 2097152,
  "estimatedTimeRemaining": 4.5,
  "startTime": "2025-01-20T10:00:00Z",
  "lastUpdate": "2025-01-20T10:00:30Z"
}
```

## YAML Workflows

### Basic Workflow Structure

```yaml
apiVersion: quorus.dev/v1
kind: TransferWorkflow
metadata:
  name: daily-data-sync
  description: Daily synchronization of corporate data
  tenant: acme-corp
  namespace: finance

spec:
  variables:
    sourceApi: "https://crm.internal.corp"
    targetStorage: "/corporate-storage/data-warehouse"
    date: "{{today}}"
    
  execution:
    strategy: sequential  # or parallel, mixed
    parallelism: 3
    timeout: 3600
    
  transferGroups:
    - name: data-extraction
      description: Extract data from CRM system
      transfers:
        - name: customer-export
          source: "{{sourceApi}}/customers/export?date={{date}}"
          destination: "{{targetStorage}}/customers/{{date}}.json"
          protocol: http
          
        - name: orders-export
          source: "{{sourceApi}}/orders/export?date={{date}}"
          destination: "{{targetStorage}}/orders/{{date}}.json"
          protocol: http
          dependsOn: [customer-export]
```

### Advanced Workflow Features

#### Conditional Execution

```yaml
transferGroups:
  - name: conditional-processing
    condition: "{{environment}} == 'production'"
    transfers:
      - name: production-backup
        source: "/production/data"
        destination: "/backup/production/{{timestamp}}"
        protocol: file
```

#### Error Handling

```yaml
transferGroups:
  - name: resilient-transfers
    continueOnError: true
    retryPolicy:
      maxAttempts: 3
      backoffMultiplier: 2
      initialDelay: 5
    transfers:
      - name: critical-transfer
        source: "{{criticalSource}}"
        destination: "{{criticalDest}}"
        onError: 
          action: retry
          notification: "admin@corp.com"
```

#### Complex Dependencies

```yaml
transferGroups:
  - name: stage-1
    transfers:
      - name: extract-customers
      - name: extract-products
        
  - name: stage-2
    dependsOn: [stage-1]
    transfers:
      - name: process-data
        dependsOn: [extract-customers, extract-products]
        
  - name: stage-3
    dependsOn: [stage-2]
    transfers:
      - name: generate-reports
      - name: send-notifications
```

### Workflow Execution

#### Command Line

```bash
# Execute workflow
java -jar quorus-workflow-2.0.jar execute \
  --workflow-file daily-sync.yaml \
  --mode normal \
  --tenant acme-corp \
  --namespace finance

# Dry run (validation only)
java -jar quorus-workflow-2.0.jar execute \
  --workflow-file daily-sync.yaml \
  --mode dry-run

# Virtual run (simulation)
java -jar quorus-workflow-2.0.jar execute \
  --workflow-file daily-sync.yaml \
  --mode virtual-run
```

#### REST API

```bash
# Submit workflow
curl -X POST http://localhost:8080/api/v1/workflows \
  -H "Content-Type: application/yaml" \
  -H "X-Tenant: acme-corp" \
  -H "X-Namespace: finance" \
  --data-binary @daily-sync.yaml

# Monitor workflow execution
curl http://localhost:8080/api/v1/workflows/{execution-id}/status
```

#### Java API

```java
// Load and execute workflow
WorkflowDefinitionParser parser = new WorkflowDefinitionParser();
WorkflowDefinition workflow = parser.parseFromFile("daily-sync.yaml");

ExecutionContext context = ExecutionContext.builder()
    .mode(ExecutionContext.ExecutionMode.NORMAL)
    .tenant("acme-corp")
    .namespace("finance")
    .variable("today", LocalDate.now().toString())
    .build();

SimpleWorkflowEngine engine = new SimpleWorkflowEngine();
String executionId = engine.executeWorkflow(workflow, context);

// Monitor execution
WorkflowExecution execution = engine.getExecution(executionId);
System.out.println("Status: " + execution.getStatus());
```

## Multi-Tenant Operations

### Tenant Hierarchy

```mermaid
graph TB
    Enterprise[Enterprise Tenant]
    Enterprise --> Corp1[ACME Corp]
    Enterprise --> Corp2[Partner Corp]
    
    Corp1 --> Dept1[Finance Department]
    Corp1 --> Dept2[HR Department]
    Corp1 --> Dept3[IT Department]
    
    Dept1 --> Team1[Accounting Team]
    Dept1 --> Team2[Budgeting Team]
```

### Tenant Configuration

```yaml
# tenant-config.yaml
apiVersion: quorus.dev/v1
kind: TenantConfiguration
metadata:
  name: acme-corp
  parent: enterprise-tenant

spec:
  displayName: "ACME Corporation"
  description: "Primary corporate tenant for ACME Corp"
  
  resources:
    quotas:
      maxConcurrentTransfers: 50
      maxStorageGB: 1000
      maxBandwidthMbps: 100
    limits:
      maxFileSize: "10GB"
      maxTransferDuration: "24h"
      
  security:
    authentication:
      provider: "active-directory"
      domain: "corp.acme.com"
    authorization:
      defaultRole: "user"
      adminRole: "admin"
      
  storage:
    isolation: "logical"  # logical, physical, hybrid
    basePath: "/corporate-storage/acme-corp"
    encryption: true
    
  namespaces:
    - name: finance
      quotas:
        maxConcurrentTransfers: 20
        maxStorageGB: 500
    - name: hr
      quotas:
        maxConcurrentTransfers: 10
        maxStorageGB: 200
```

### Tenant Operations

#### Creating Tenants

```bash
# Create tenant via API
curl -X POST http://localhost:8080/api/v1/tenants \
  -H "Content-Type: application/yaml" \
  -H "Authorization: Bearer ADMIN_TOKEN" \
  --data-binary @tenant-config.yaml
```

#### Tenant-Aware Transfers

```bash
# Transfer within tenant context
curl -X POST http://localhost:8080/api/v1/transfers \
  -H "Content-Type: application/json" \
  -H "X-Tenant: acme-corp" \
  -H "X-Namespace: finance" \
  -d '{
    "sourceUri": "https://finance-api.corp/reports/monthly.pdf",
    "destinationPath": "/finance/reports/monthly-{{month}}.pdf"
  }'
```

#### Resource Monitoring

```bash
# Check tenant resource usage
curl http://localhost:8080/api/v1/tenants/acme-corp/usage

# Example response
{
  "tenant": "acme-corp",
  "currentUsage": {
    "concurrentTransfers": 15,
    "storageUsedGB": 450.5,
    "bandwidthUsedMbps": 25.3
  },
  "quotas": {
    "maxConcurrentTransfers": 50,
    "maxStorageGB": 1000,
    "maxBandwidthMbps": 100
  },
  "utilizationPercentage": {
    "transfers": 30.0,
    "storage": 45.1,
    "bandwidth": 25.3
  }
}
```

## REST API Usage

The Quorus REST API provides comprehensive programmatic access to all system functionality. Built on OpenAPI 3.0 specifications, it offers consistent, well-documented endpoints for integration with enterprise applications, automation systems, and custom tooling.

### API Architecture and Design Principles

#### RESTful Design Standards
The Quorus API follows REST architectural principles with:
- **Resource-Based URLs**: Clear, hierarchical resource paths (`/api/v1/transfers/{id}`)
- **HTTP Method Semantics**: Proper use of GET, POST, PUT, DELETE, PATCH
- **Stateless Operations**: Each request contains all necessary information
- **Consistent Response Formats**: Standardized JSON responses with predictable structure
- **HATEOAS Support**: Hypermedia links for resource navigation and discovery

#### API Versioning Strategy
```bash
# Current API version
GET /api/v1/transfers

# Future versions maintain backward compatibility
GET /api/v2/transfers  # Enhanced features
GET /api/v1/transfers  # Still supported for existing integrations
```

**Versioning Approach:**
- **URL Path Versioning**: Version specified in URL path for clarity
- **Backward Compatibility**: Previous versions supported for minimum 2 years
- **Deprecation Notices**: 6-month advance notice for version deprecation
- **Migration Guides**: Comprehensive guides for version upgrades

### Authentication and Authorization

Quorus supports multiple authentication methods designed for enterprise security requirements:

#### OAuth 2.0 / OpenID Connect Integration

```bash
# Step 1: Obtain access token from corporate identity provider
curl -X POST https://sso.corp.com/oauth/token \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=client_credentials" \
  -d "client_id=quorus-api-client" \
  -d "client_secret=your-client-secret" \
  -d "scope=quorus:transfers:read quorus:transfers:write quorus:workflows:execute"

# Response
{
  "access_token": "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9...",
  "token_type": "Bearer",
  "expires_in": 3600,
  "scope": "quorus:transfers:read quorus:transfers:write quorus:workflows:execute"
}

# Step 2: Use token in API requests
curl -X GET http://localhost:8080/api/v1/transfers \
  -H "Authorization: Bearer eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9..." \
  -H "Accept: application/json"
```

**OAuth 2.0 Grant Types Supported:**
- **Client Credentials**: Service-to-service authentication
- **Authorization Code**: Interactive user authentication
- **Resource Owner Password**: Legacy system integration (discouraged)
- **JWT Bearer**: Token exchange for federated scenarios

#### API Key Authentication

```bash
# API key in header (recommended)
curl -X GET http://localhost:8080/api/v1/transfers \
  -H "X-API-Key: qrs_live_1234567890abcdef" \
  -H "Accept: application/json"

# API key in query parameter (less secure, for testing only)
curl -X GET "http://localhost:8080/api/v1/transfers?api_key=qrs_live_1234567890abcdef"
```

**API Key Management:**
- **Key Rotation**: Automatic key rotation with configurable intervals
- **Scope Limitation**: Keys can be restricted to specific operations
- **IP Restrictions**: Limit key usage to specific IP ranges
- **Usage Monitoring**: Track API key usage and detect anomalies

#### Certificate-Based Authentication (mTLS)

```bash
# Mutual TLS authentication for high-security environments
curl -X GET https://localhost:8443/api/v1/transfers \
  --cert /path/to/client.crt \
  --key /path/to/client.key \
  --cacert /path/to/ca.crt \
  -H "Accept: application/json"
```

**Certificate Authentication Features:**
- **Client Certificate Validation**: Verify client certificates against corporate CA
- **Certificate Revocation**: Support for CRL and OCSP validation
- **Subject DN Mapping**: Map certificate subjects to user identities
- **Hardware Security Module**: Support for HSM-stored private keys

#### Basic Authentication (Development/Testing)

```bash
# Basic authentication for development environments
curl -X GET http://localhost:8080/api/v1/transfers \
  -u "username:password" \
  -H "Accept: application/json"

# With base64 encoding
curl -X GET http://localhost:8080/api/v1/transfers \
  -H "Authorization: Basic dXNlcm5hbWU6cGFzc3dvcmQ=" \
  -H "Accept: application/json"
```

### Authorization and Role-Based Access Control

#### Permission Model
```json
{
  "permissions": [
    "transfers:create",
    "transfers:read:own",
    "transfers:read:tenant",
    "transfers:cancel:own",
    "workflows:execute",
    "workflows:read",
    "tenants:read:own",
    "system:health:read"
  ]
}
```

**Permission Hierarchy:**
- **System Level**: `system:*` - Administrative operations
- **Tenant Level**: `tenants:*` - Tenant management operations
- **Workflow Level**: `workflows:*` - Workflow operations
- **Transfer Level**: `transfers:*` - Transfer operations
- **Scope Modifiers**: `:own`, `:tenant`, `:all` - Access scope limitations

#### Role Definitions
```yaml
# Standard roles with permission mappings
roles:
  admin:
    permissions: ["*"]
    description: "Full system administration"

  tenant-admin:
    permissions:
      - "tenants:*:own"
      - "transfers:*:tenant"
      - "workflows:*:tenant"
    description: "Tenant administration"

  operator:
    permissions:
      - "transfers:create"
      - "transfers:read:tenant"
      - "workflows:execute"
      - "workflows:read"
    description: "Transfer operations"

  user:
    permissions:
      - "transfers:create:own"
      - "transfers:read:own"
      - "workflows:execute:own"
    description: "Basic user operations"
```

### Core API Endpoints

#### Transfer Management

The Transfer Management API provides comprehensive control over file transfer operations with detailed configuration options and real-time monitoring capabilities.

##### Create Transfer Job

```bash
POST /api/v1/transfers
Content-Type: application/json
Authorization: Bearer {token}
X-Tenant: acme-corp
X-Namespace: finance

{
  "sourceUri": "https://api.corp.internal/exports/financial-data.zip",
  "destinationPath": "/corporate-storage/finance/imports/financial-data-2025-01-20.zip",
  "protocol": "https",
  "metadata": {
    "description": "Daily financial data import",
    "tags": ["finance", "daily", "automated"],
    "businessOwner": "finance-team@corp.com",
    "priority": "high",
    "dataClassification": "confidential"
  },
  "options": {
    "checksumValidation": true,
    "checksumAlgorithm": "SHA256",
    "expectedChecksum": "a665a45920422f9d417e4867efdc4fb8a04a1f3fff1fa07e998e86f7f7a27ae3",
    "retryAttempts": 5,
    "retryBackoffMultiplier": 2.0,
    "initialRetryDelay": "PT30S",
    "maxRetryDelay": "PT10M",
    "timeout": 1800,
    "chunkSize": 10485760,
    "compressionEnabled": true,
    "resumeSupported": true
  },
  "credentials": {
    "type": "oauth2",
    "tokenEndpoint": "https://api.corp.internal/oauth/token",
    "clientId": "quorus-client",
    "clientSecret": "encrypted:vault:secret-key",
    "scope": "data:read"
  },
  "notifications": {
    "onSuccess": ["finance-team@corp.com"],
    "onFailure": ["finance-team@corp.com", "ops-team@corp.com"],
    "onProgress": {
      "enabled": true,
      "intervalSeconds": 300,
      "recipients": ["finance-ops@corp.com"]
    }
  },
  "scheduling": {
    "startAfter": "2025-01-20T06:00:00Z",
    "deadline": "2025-01-20T18:00:00Z",
    "priority": 8
  }
}
```

**Response:**
```json
{
  "jobId": "transfer-20250120-001",
  "status": "PENDING",
  "createdAt": "2025-01-20T05:45:00Z",
  "estimatedStartTime": "2025-01-20T06:00:00Z",
  "queuePosition": 3,
  "assignedAgent": null,
  "links": {
    "self": "/api/v1/transfers/transfer-20250120-001",
    "progress": "/api/v1/transfers/transfer-20250120-001/progress",
    "logs": "/api/v1/transfers/transfer-20250120-001/logs",
    "cancel": "/api/v1/transfers/transfer-20250120-001"
  }
}
```

##### Get Transfer Status

```bash
GET /api/v1/transfers/{jobId}
Authorization: Bearer {token}
Accept: application/json

# Example: GET /api/v1/transfers/transfer-20250120-001
```

**Response:**
```json
{
  "jobId": "transfer-20250120-001",
  "status": "IN_PROGRESS",
  "phase": "TRANSFERRING",
  "createdAt": "2025-01-20T05:45:00Z",
  "startedAt": "2025-01-20T06:00:00Z",
  "lastUpdated": "2025-01-20T06:15:30Z",
  "assignedAgent": "agent-east-01",
  "sourceUri": "https://api.corp.internal/exports/financial-data.zip",
  "destinationPath": "/corporate-storage/finance/imports/financial-data-2025-01-20.zip",
  "fileSize": 157286400,
  "bytesTransferred": 78643200,
  "progressPercentage": 50.0,
  "transferRate": 5242880,
  "averageTransferRate": 4718592,
  "estimatedTimeRemaining": "PT2M30S",
  "checksumStatus": "CALCULATING",
  "retryAttempts": 0,
  "lastError": null,
  "metadata": {
    "description": "Daily financial data import",
    "tags": ["finance", "daily", "automated"],
    "businessOwner": "finance-team@corp.com",
    "priority": "high"
  },
  "links": {
    "progress": "/api/v1/transfers/transfer-20250120-001/progress",
    "logs": "/api/v1/transfers/transfer-20250120-001/logs",
    "cancel": "/api/v1/transfers/transfer-20250120-001"
  }
}
```

##### Get Transfer Progress (Real-time)

```bash
GET /api/v1/transfers/{jobId}/progress
Authorization: Bearer {token}
Accept: application/json

# Server-Sent Events for real-time updates
GET /api/v1/transfers/{jobId}/progress/stream
Accept: text/event-stream
```

**JSON Response:**
```json
{
  "jobId": "transfer-20250120-001",
  "timestamp": "2025-01-20T06:15:30Z",
  "status": "IN_PROGRESS",
  "phase": "TRANSFERRING",
  "progress": {
    "bytesTransferred": 78643200,
    "totalBytes": 157286400,
    "progressPercentage": 50.0,
    "chunksCompleted": 75,
    "totalChunks": 150
  },
  "performance": {
    "currentTransferRate": 5242880,
    "averageTransferRate": 4718592,
    "peakTransferRate": 6291456,
    "networkLatency": 15,
    "connectionQuality": "GOOD"
  },
  "timing": {
    "elapsedTime": "PT15M30S",
    "estimatedTimeRemaining": "PT2M30S",
    "estimatedCompletionTime": "2025-01-20T06:18:00Z"
  },
  "validation": {
    "checksumStatus": "CALCULATING",
    "checksumProgress": 50.0,
    "integrityChecks": {
      "chunksValidated": 75,
      "validationErrors": 0
    }
  }
}
```

**Server-Sent Events Stream:**
```
data: {"jobId":"transfer-20250120-001","bytesTransferred":78643200,"progressPercentage":50.0,"transferRate":5242880}

data: {"jobId":"transfer-20250120-001","bytesTransferred":83886080,"progressPercentage":53.3,"transferRate":5242880}

data: {"jobId":"transfer-20250120-001","status":"COMPLETED","bytesTransferred":157286400,"progressPercentage":100.0}
```

##### Cancel Transfer

```bash
DELETE /api/v1/transfers/{jobId}
Authorization: Bearer {token}
X-Reason: "Business requirement changed"

# Graceful cancellation with cleanup
DELETE /api/v1/transfers/{jobId}?graceful=true&cleanup=true
```

**Response:**
```json
{
  "jobId": "transfer-20250120-001",
  "status": "CANCELLING",
  "message": "Transfer cancellation initiated",
  "gracefulShutdown": true,
  "cleanupRequested": true,
  "estimatedCancellationTime": "2025-01-20T06:16:00Z"
}
```

##### List Transfers with Advanced Filtering

```bash
# Basic listing
GET /api/v1/transfers
Authorization: Bearer {token}

# Advanced filtering and pagination
GET /api/v1/transfers?status=RUNNING,COMPLETED&tenant=acme-corp&namespace=finance&limit=50&offset=0&sort=createdAt:desc

# Filter by date range
GET /api/v1/transfers?createdAfter=2025-01-20T00:00:00Z&createdBefore=2025-01-20T23:59:59Z

# Filter by metadata tags
GET /api/v1/transfers?tags=finance,daily&priority=high

# Search by description or source
GET /api/v1/transfers?search=financial-data&searchFields=description,sourceUri
```

**Query Parameters:**
- **status**: Filter by transfer status (PENDING, RUNNING, COMPLETED, FAILED, CANCELLED)
- **tenant**: Filter by tenant ID
- **namespace**: Filter by namespace
- **assignedAgent**: Filter by assigned agent
- **priority**: Filter by priority level (1-10)
- **tags**: Filter by metadata tags
- **createdAfter/createdBefore**: Date range filtering
- **search**: Full-text search across specified fields
- **limit/offset**: Pagination controls
- **sort**: Sort order (field:asc/desc)

**Response:**
```json
{
  "transfers": [
    {
      "jobId": "transfer-20250120-001",
      "status": "COMPLETED",
      "sourceUri": "https://api.corp.internal/exports/financial-data.zip",
      "destinationPath": "/corporate-storage/finance/imports/financial-data-2025-01-20.zip",
      "createdAt": "2025-01-20T05:45:00Z",
      "completedAt": "2025-01-20T06:18:00Z",
      "fileSize": 157286400,
      "duration": "PT33M",
      "averageTransferRate": 4718592,
      "priority": "high",
      "tags": ["finance", "daily", "automated"]
    }
  ],
  "pagination": {
    "total": 1250,
    "limit": 50,
    "offset": 0,
    "hasNext": true,
    "hasPrevious": false,
    "nextOffset": 50,
    "previousOffset": null
  },
  "links": {
    "self": "/api/v1/transfers?limit=50&offset=0",
    "next": "/api/v1/transfers?limit=50&offset=50",
    "first": "/api/v1/transfers?limit=50&offset=0",
    "last": "/api/v1/transfers?limit=50&offset=1200"
  }
}
```

##### Transfer Logs and Diagnostics

```bash
# Get transfer logs
GET /api/v1/transfers/{jobId}/logs
Authorization: Bearer {token}

# Get logs with filtering
GET /api/v1/transfers/{jobId}/logs?level=ERROR,WARN&since=2025-01-20T06:00:00Z

# Stream logs in real-time
GET /api/v1/transfers/{jobId}/logs/stream
Accept: text/event-stream
```

**Response:**
```json
{
  "jobId": "transfer-20250120-001",
  "logs": [
    {
      "timestamp": "2025-01-20T06:00:00Z",
      "level": "INFO",
      "message": "Transfer started",
      "component": "TransferManager",
      "agent": "agent-east-01"
    },
    {
      "timestamp": "2025-01-20T06:00:15Z",
      "level": "DEBUG",
      "message": "Connected to source: https://api.corp.internal/exports/financial-data.zip",
      "component": "HttpProtocolHandler",
      "details": {
        "responseCode": 200,
        "contentLength": 157286400,
        "contentType": "application/zip"
      }
    },
    {
      "timestamp": "2025-01-20T06:18:00Z",
      "level": "INFO",
      "message": "Transfer completed successfully",
      "component": "TransferManager",
      "details": {
        "bytesTransferred": 157286400,
        "duration": "PT18M",
        "averageRate": 4718592,
        "checksumValid": true
      }
    }
  ],
  "pagination": {
    "total": 156,
    "limit": 100,
    "offset": 0
  }
}
```

#### Workflow Management

The Workflow Management API enables submission, monitoring, and control of complex multi-step transfer workflows with comprehensive execution tracking and management capabilities.

##### Submit Workflow

```bash
POST /api/v1/workflows
Content-Type: application/yaml
Authorization: Bearer {token}
X-Tenant: acme-corp
X-Namespace: finance
X-Execution-Mode: normal  # normal, dry-run, virtual-run

# YAML workflow definition
apiVersion: quorus.dev/v1
kind: TransferWorkflow
metadata:
  name: daily-financial-sync
  description: Daily synchronization of financial data across systems
  version: "2.1.0"
  tenant: acme-corp
  namespace: finance
  labels:
    environment: production
    schedule: daily
    criticality: high
  annotations:
    owner: "finance-team@corp.com"
    documentation: "https://wiki.corp.com/finance/data-sync"
    approvedBy: "finance-director@corp.com"

spec:
  variables:
    sourceSystem: "https://erp.corp.internal"
    targetWarehouse: "/data-warehouse/finance"
    processingDate: "{{today}}"
    environment: "production"
    notificationEmail: "finance-ops@corp.com"

  execution:
    strategy: mixed
    parallelism: 3
    timeout: 7200
    retryPolicy:
      maxAttempts: 3
      backoffMultiplier: 2
      initialDelay: 60

  conditions:
    - name: production-only
      expression: "{{environment}} == 'production'"
    - name: business-hours
      expression: "{{hour}} >= 6 && {{hour}} <= 22"

  transferGroups:
    - name: extract-master-data
      description: Extract master data from ERP system
      condition: production-only
      transfers:
        - name: customer-master
          source: "{{sourceSystem}}/api/customers/export?date={{processingDate}}"
          destination: "{{targetWarehouse}}/raw/customers/{{processingDate}}.json"
          options:
            checksumValidation: true
            timeout: 1800

    - name: extract-transactional-data
      description: Extract transactional data
      dependsOn: [extract-master-data]
      transfers:
        - name: sales-transactions
          source: "{{sourceSystem}}/api/sales/export?date={{processingDate}}"
          destination: "{{targetWarehouse}}/raw/sales/{{processingDate}}.json"

    - name: data-validation
      description: Validate extracted data
      dependsOn: [extract-master-data, extract-transactional-data]
      condition: business-hours
      transfers:
        - name: run-validation
          source: "{{targetWarehouse}}/scripts/validate-data.py"
          destination: "{{targetWarehouse}}/validation/{{processingDate}}.log"

  notifications:
    onSuccess:
      - email: "{{notificationEmail}}"
        subject: "Daily financial sync completed successfully"
    onFailure:
      - email: "{{notificationEmail}}"
        subject: "Daily financial sync failed"
      - webhook: "https://alerts.corp.com/quorus/workflow-failed"
```

**Response:**
```json
{
  "executionId": "wf-exec-20250120-001",
  "workflowName": "daily-financial-sync",
  "version": "2.1.0",
  "status": "PENDING",
  "executionMode": "normal",
  "createdAt": "2025-01-20T05:30:00Z",
  "scheduledStartTime": "2025-01-20T06:00:00Z",
  "tenant": "acme-corp",
  "namespace": "finance",
  "executionPlan": {
    "totalStages": 3,
    "estimatedDuration": "PT45M",
    "parallelizable": true,
    "criticalPath": ["extract-master-data", "extract-transactional-data", "data-validation"]
  },
  "variables": {
    "sourceSystem": "https://erp.corp.internal",
    "targetWarehouse": "/data-warehouse/finance",
    "processingDate": "2025-01-20",
    "environment": "production",
    "notificationEmail": "finance-ops@corp.com"
  },
  "links": {
    "self": "/api/v1/workflows/wf-exec-20250120-001",
    "status": "/api/v1/workflows/wf-exec-20250120-001/status",
    "progress": "/api/v1/workflows/wf-exec-20250120-001/progress",
    "logs": "/api/v1/workflows/wf-exec-20250120-001/logs",
    "cancel": "/api/v1/workflows/wf-exec-20250120-001"
  }
}
```

##### Get Workflow Execution Status

```bash
GET /api/v1/workflows/{executionId}
Authorization: Bearer {token}
Accept: application/json

# Example: GET /api/v1/workflows/wf-exec-20250120-001
```

**Response:**
```json
{
  "executionId": "wf-exec-20250120-001",
  "workflowName": "daily-financial-sync",
  "version": "2.1.0",
  "status": "IN_PROGRESS",
  "currentStage": "extract-transactional-data",
  "createdAt": "2025-01-20T05:30:00Z",
  "startedAt": "2025-01-20T06:00:00Z",
  "lastUpdated": "2025-01-20T06:25:00Z",
  "executionMode": "normal",
  "tenant": "acme-corp",
  "namespace": "finance",
  "progress": {
    "completedStages": 1,
    "totalStages": 3,
    "progressPercentage": 33.3,
    "currentStageProgress": 75.0
  },
  "timing": {
    "elapsedTime": "PT25M",
    "estimatedTimeRemaining": "PT20M",
    "estimatedCompletionTime": "2025-01-20T06:45:00Z"
  },
  "stages": [
    {
      "name": "extract-master-data",
      "status": "COMPLETED",
      "startedAt": "2025-01-20T06:00:00Z",
      "completedAt": "2025-01-20T06:15:00Z",
      "duration": "PT15M",
      "transfers": [
        {
          "name": "customer-master",
          "jobId": "transfer-20250120-002",
          "status": "COMPLETED",
          "bytesTransferred": 52428800,
          "duration": "PT15M"
        }
      ]
    },
    {
      "name": "extract-transactional-data",
      "status": "IN_PROGRESS",
      "startedAt": "2025-01-20T06:15:00Z",
      "estimatedCompletionTime": "2025-01-20T06:35:00Z",
      "transfers": [
        {
          "name": "sales-transactions",
          "jobId": "transfer-20250120-003",
          "status": "IN_PROGRESS",
          "bytesTransferred": 78643200,
          "progressPercentage": 75.0
        }
      ]
    },
    {
      "name": "data-validation",
      "status": "PENDING",
      "dependsOn": ["extract-master-data", "extract-transactional-data"],
      "estimatedStartTime": "2025-01-20T06:35:00Z"
    }
  ],
  "variables": {
    "sourceSystem": "https://erp.corp.internal",
    "targetWarehouse": "/data-warehouse/finance",
    "processingDate": "2025-01-20",
    "environment": "production"
  },
  "links": {
    "progress": "/api/v1/workflows/wf-exec-20250120-001/progress",
    "logs": "/api/v1/workflows/wf-exec-20250120-001/logs",
    "cancel": "/api/v1/workflows/wf-exec-20250120-001"
  }
}
```

##### Get Workflow Progress (Real-time)

```bash
GET /api/v1/workflows/{executionId}/progress
Authorization: Bearer {token}

# Real-time progress stream
GET /api/v1/workflows/{executionId}/progress/stream
Accept: text/event-stream
```

**Response:**
```json
{
  "executionId": "wf-exec-20250120-001",
  "timestamp": "2025-01-20T06:25:00Z",
  "status": "IN_PROGRESS",
  "currentStage": "extract-transactional-data",
  "overallProgress": {
    "completedStages": 1,
    "totalStages": 3,
    "progressPercentage": 33.3,
    "completedTransfers": 1,
    "totalTransfers": 3,
    "transferProgressPercentage": 58.3
  },
  "stageProgress": [
    {
      "stageName": "extract-master-data",
      "status": "COMPLETED",
      "progressPercentage": 100.0,
      "transfers": [
        {
          "transferName": "customer-master",
          "status": "COMPLETED",
          "progressPercentage": 100.0,
          "bytesTransferred": 52428800
        }
      ]
    },
    {
      "stageName": "extract-transactional-data",
      "status": "IN_PROGRESS",
      "progressPercentage": 75.0,
      "transfers": [
        {
          "transferName": "sales-transactions",
          "status": "IN_PROGRESS",
          "progressPercentage": 75.0,
          "bytesTransferred": 78643200,
          "transferRate": 5242880
        }
      ]
    }
  ],
  "performance": {
    "totalBytesTransferred": 131072000,
    "averageTransferRate": 4718592,
    "parallelTransfers": 1,
    "resourceUtilization": {
      "cpu": 45.2,
      "memory": 62.1,
      "network": 38.7
    }
  }
}
```

##### List Workflow Executions

```bash
# Basic listing
GET /api/v1/workflows
Authorization: Bearer {token}

# Advanced filtering
GET /api/v1/workflows?status=RUNNING,COMPLETED&tenant=acme-corp&namespace=finance&workflowName=daily-financial-sync&limit=25&sort=createdAt:desc

# Filter by execution date range
GET /api/v1/workflows?executedAfter=2025-01-20T00:00:00Z&executedBefore=2025-01-20T23:59:59Z

# Filter by duration and performance
GET /api/v1/workflows?minDuration=PT30M&maxDuration=PT2H&status=COMPLETED
```

**Response:**
```json
{
  "executions": [
    {
      "executionId": "wf-exec-20250120-001",
      "workflowName": "daily-financial-sync",
      "version": "2.1.0",
      "status": "COMPLETED",
      "tenant": "acme-corp",
      "namespace": "finance",
      "createdAt": "2025-01-20T05:30:00Z",
      "startedAt": "2025-01-20T06:00:00Z",
      "completedAt": "2025-01-20T06:42:00Z",
      "duration": "PT42M",
      "totalTransfers": 3,
      "successfulTransfers": 3,
      "failedTransfers": 0,
      "totalBytesTransferred": 157286400,
      "averageTransferRate": 4718592,
      "executionMode": "normal"
    }
  ],
  "pagination": {
    "total": 847,
    "limit": 25,
    "offset": 0,
    "hasNext": true
  },
  "aggregations": {
    "statusCounts": {
      "COMPLETED": 756,
      "FAILED": 45,
      "IN_PROGRESS": 12,
      "PENDING": 34
    },
    "averageDuration": "PT38M",
    "successRate": 94.3,
    "totalBytesTransferred": 1073741824000
  }
}
```

##### Cancel Workflow Execution

```bash
DELETE /api/v1/workflows/{executionId}
Authorization: Bearer {token}
X-Reason: "Emergency maintenance required"

# Graceful cancellation
DELETE /api/v1/workflows/{executionId}?graceful=true&waitForCurrentTransfers=true
```

**Response:**
```json
{
  "executionId": "wf-exec-20250120-001",
  "status": "CANCELLING",
  "message": "Workflow cancellation initiated",
  "gracefulShutdown": true,
  "waitForCurrentTransfers": true,
  "currentTransfers": [
    {
      "transferName": "sales-transactions",
      "jobId": "transfer-20250120-003",
      "status": "IN_PROGRESS",
      "action": "COMPLETING"
    }
  ],
  "estimatedCancellationTime": "2025-01-20T06:30:00Z"
}
```

#### System Information and Monitoring

The System Information API provides comprehensive visibility into system health, performance, and operational status.

##### Service Information

```bash
GET /api/v1/info
Authorization: Bearer {token}
Accept: application/json
```

**Response:**
```json
{
  "service": {
    "name": "Quorus File Transfer System",
    "version": "2.0.1",
    "buildTime": "2025-01-15T10:30:00Z",
    "gitCommit": "a1b2c3d4e5f6",
    "environment": "production"
  },
  "api": {
    "version": "v1",
    "supportedVersions": ["v1"],
    "deprecatedVersions": [],
    "documentation": "https://docs.quorus.dev/api/v1"
  },
  "features": {
    "protocols": ["HTTP", "HTTPS", "FTP", "SFTP", "SMB"],
    "authentication": ["OAuth2", "API-Key", "mTLS", "Basic"],
    "multiTenant": true,
    "clustering": true,
    "workflows": true
  },
  "limits": {
    "maxFileSize": "10GB",
    "maxConcurrentTransfers": 1000,
    "maxWorkflowStages": 50,
    "apiRateLimit": 10000
  }
}
```

##### Health Check (Comprehensive)

```bash
# Basic health check
GET /health
Accept: application/json

# Detailed health check with dependencies
GET /health/detailed
Authorization: Bearer {token}

# Readiness probe (Kubernetes-compatible)
GET /health/ready

# Liveness probe (Kubernetes-compatible)
GET /health/live
```

**Basic Health Response:**
```json
{
  "status": "UP",
  "timestamp": "2025-01-20T06:30:00Z",
  "responseTime": "15ms"
}
```

**Detailed Health Response:**
```json
{
  "status": "UP",
  "timestamp": "2025-01-20T06:30:00Z",
  "checks": [
    {
      "name": "database",
      "status": "UP",
      "responseTime": "5ms",
      "details": {
        "connectionPool": {
          "active": 8,
          "idle": 12,
          "max": 20
        }
      }
    },
    {
      "name": "raft-cluster",
      "status": "UP",
      "details": {
        "role": "LEADER",
        "term": 42,
        "nodes": 3,
        "healthyNodes": 3,
        "lastHeartbeat": "2025-01-20T06:29:58Z"
      }
    },
    {
      "name": "transfer-agents",
      "status": "UP",
      "details": {
        "totalAgents": 15,
        "healthyAgents": 14,
        "degradedAgents": 1,
        "failedAgents": 0,
        "averageLoad": 0.65
      }
    },
    {
      "name": "storage-systems",
      "status": "UP",
      "details": {
        "primaryStorage": "UP",
        "backupStorage": "UP",
        "availableSpace": "2.5TB",
        "usedSpace": "1.2TB"
      }
    },
    {
      "name": "external-dependencies",
      "status": "DEGRADED",
      "details": {
        "corporateSSO": "UP",
        "monitoringSystem": "UP",
        "notificationService": "DEGRADED",
        "backupService": "UP"
      }
    }
  ],
  "performance": {
    "activeTransfers": 45,
    "queuedTransfers": 12,
    "transfersPerSecond": 2.3,
    "averageTransferDuration": "PT5M30S",
    "systemLoad": {
      "cpu": 45.2,
      "memory": 67.8,
      "disk": 23.1,
      "network": 34.5
    }
  }
}
```

##### Metrics (Prometheus-compatible)

```bash
# Prometheus metrics endpoint
GET /metrics
Accept: text/plain

# JSON metrics for custom integrations
GET /api/v1/metrics
Authorization: Bearer {token}
Accept: application/json

# Specific metric categories
GET /api/v1/metrics/transfers
GET /api/v1/metrics/workflows
GET /api/v1/metrics/system
```

**Prometheus Metrics Sample:**
```
# HELP quorus_transfers_total Total number of transfers
# TYPE quorus_transfers_total counter
quorus_transfers_total{status="completed",tenant="acme-corp"} 1250
quorus_transfers_total{status="failed",tenant="acme-corp"} 23

# HELP quorus_transfer_duration_seconds Transfer duration in seconds
# TYPE quorus_transfer_duration_seconds histogram
quorus_transfer_duration_seconds_bucket{le="30"} 145
quorus_transfer_duration_seconds_bucket{le="60"} 234
quorus_transfer_duration_seconds_bucket{le="300"} 567
quorus_transfer_duration_seconds_bucket{le="+Inf"} 1250

# HELP quorus_cluster_nodes Number of cluster nodes by status
# TYPE quorus_cluster_nodes gauge
quorus_cluster_nodes{status="healthy"} 3
quorus_cluster_nodes{status="degraded"} 0
quorus_cluster_nodes{status="failed"} 0

# HELP quorus_agent_capacity_utilization Agent capacity utilization
# TYPE quorus_agent_capacity_utilization gauge
quorus_agent_capacity_utilization{agent="agent-east-01"} 0.75
quorus_agent_capacity_utilization{agent="agent-west-01"} 0.45
```

##### Cluster Status

```bash
GET /api/v1/cluster/status
Authorization: Bearer {token}
Accept: application/json
```

**Response:**
```json
{
  "cluster": {
    "name": "quorus-production",
    "size": 3,
    "healthyNodes": 3,
    "status": "HEALTHY"
  },
  "raft": {
    "currentTerm": 42,
    "leader": "controller-1",
    "role": "LEADER",
    "lastElection": "2025-01-19T14:22:00Z",
    "logIndex": 15847,
    "commitIndex": 15847
  },
  "nodes": [
    {
      "nodeId": "controller-1",
      "role": "LEADER",
      "status": "HEALTHY",
      "address": "controller-1:8080",
      "lastHeartbeat": "2025-01-20T06:29:58Z",
      "uptime": "P5DT14H22M",
      "version": "2.0.1"
    },
    {
      "nodeId": "controller-2",
      "role": "FOLLOWER",
      "status": "HEALTHY",
      "address": "controller-2:8080",
      "lastHeartbeat": "2025-01-20T06:29:57Z",
      "uptime": "P5DT14H20M",
      "version": "2.0.1"
    },
    {
      "nodeId": "controller-3",
      "role": "FOLLOWER",
      "status": "HEALTHY",
      "address": "controller-3:8080",
      "lastHeartbeat": "2025-01-20T06:29:59Z",
      "uptime": "P5DT14H18M",
      "version": "2.0.1"
    }
  ],
  "performance": {
    "requestsPerSecond": 125.7,
    "averageResponseTime": "45ms",
    "leaderElections": 3,
    "lastLeaderElection": "2025-01-19T14:22:00Z"
  }
}
```

### Error Handling and Response Codes

The Quorus API uses standard HTTP status codes and provides comprehensive error information to help with debugging and automated error handling.

#### HTTP Status Codes

| Status Code | Meaning | Usage |
|-------------|---------|-------|
| 200 | OK | Successful GET, PUT, PATCH requests |
| 201 | Created | Successful POST requests (resource created) |
| 202 | Accepted | Request accepted for async processing |
| 204 | No Content | Successful DELETE requests |
| 400 | Bad Request | Invalid request syntax or parameters |
| 401 | Unauthorized | Authentication required or failed |
| 403 | Forbidden | Insufficient permissions |
| 404 | Not Found | Resource not found |
| 409 | Conflict | Resource conflict (duplicate, state mismatch) |
| 422 | Unprocessable Entity | Valid syntax but semantic errors |
| 429 | Too Many Requests | Rate limit exceeded |
| 500 | Internal Server Error | Unexpected server error |
| 502 | Bad Gateway | Upstream service error |
| 503 | Service Unavailable | Service temporarily unavailable |

#### Error Response Format

All error responses follow a consistent format:

```json
{
  "error": {
    "code": "TRANSFER_FAILED",
    "message": "Transfer failed due to network timeout",
    "timestamp": "2025-01-20T10:30:00Z",
    "requestId": "req-20250120-12345",
    "details": {
      "jobId": "transfer-123",
      "sourceUri": "https://api.corp/data.zip",
      "errorType": "NETWORK_TIMEOUT",
      "retryable": true,
      "retryAfter": 300,
      "supportReference": "ERR-2025-001234"
    },
    "context": {
      "tenant": "acme-corp",
      "namespace": "finance",
      "agent": "agent-east-01",
      "operation": "file-transfer"
    },
    "suggestions": [
      "Check network connectivity to source system",
      "Verify firewall rules allow outbound connections",
      "Consider increasing timeout value for large files"
    ]
  }
}
```

#### Common Error Codes

**Authentication and Authorization Errors:**
```json
{
  "error": {
    "code": "INVALID_TOKEN",
    "message": "The provided authentication token is invalid or expired",
    "details": {
      "tokenType": "Bearer",
      "expiresAt": "2025-01-20T09:00:00Z",
      "retryable": false
    }
  }
}
```

**Validation Errors:**
```json
{
  "error": {
    "code": "VALIDATION_FAILED",
    "message": "Request validation failed",
    "details": {
      "violations": [
        {
          "field": "sourceUri",
          "message": "Invalid URI format",
          "rejectedValue": "not-a-valid-uri"
        },
        {
          "field": "options.timeout",
          "message": "Timeout must be between 30 and 7200 seconds",
          "rejectedValue": 10
        }
      ]
    }
  }
}
```

**Resource Errors:**
```json
{
  "error": {
    "code": "QUOTA_EXCEEDED",
    "message": "Tenant quota exceeded for concurrent transfers",
    "details": {
      "quotaType": "CONCURRENT_TRANSFERS",
      "currentUsage": 50,
      "quotaLimit": 50,
      "retryable": true,
      "retryAfter": 300
    }
  }
}
```

**System Errors:**
```json
{
  "error": {
    "code": "SERVICE_UNAVAILABLE",
    "message": "Transfer service temporarily unavailable",
    "details": {
      "reason": "CLUSTER_LEADER_ELECTION",
      "retryable": true,
      "retryAfter": 30,
      "estimatedRecoveryTime": "2025-01-20T10:32:00Z"
    }
  }
}
```

### Rate Limiting and Quotas

The API implements comprehensive rate limiting and quota management to ensure fair resource usage and system stability.

#### Rate Limiting Headers

All API responses include rate limiting information:

```bash
# Response headers
HTTP/1.1 200 OK
X-RateLimit-Limit: 1000
X-RateLimit-Remaining: 999
X-RateLimit-Reset: 1642694400
X-RateLimit-Window: 3600
X-RateLimit-Policy: sliding-window
X-Quota-Transfers-Limit: 50
X-Quota-Transfers-Remaining: 35
X-Quota-Transfers-Reset: 1642694400
```

#### Rate Limiting Policies

**API Request Limits:**
- **Per User**: 1,000 requests per hour
- **Per Tenant**: 10,000 requests per hour
- **Per IP Address**: 5,000 requests per hour
- **Global**: 100,000 requests per hour

**Transfer Operation Limits:**
- **Concurrent Transfers**: Based on tenant quota
- **Transfer Volume**: Based on tenant quota
- **Workflow Executions**: Based on tenant quota

#### Rate Limit Exceeded Response

```json
{
  "error": {
    "code": "RATE_LIMIT_EXCEEDED",
    "message": "API rate limit exceeded",
    "details": {
      "limitType": "API_REQUESTS",
      "limit": 1000,
      "window": "1 hour",
      "retryAfter": 1800,
      "resetTime": "2025-01-20T11:00:00Z"
    }
  }
}
```

#### Quota Management

```bash
# Check current quota usage
GET /api/v1/quotas/current
Authorization: Bearer {token}

# Get quota details for specific resource
GET /api/v1/quotas/transfers
GET /api/v1/quotas/storage
GET /api/v1/quotas/bandwidth
```

**Quota Response:**
```json
{
  "tenant": "acme-corp",
  "namespace": "finance",
  "quotas": {
    "concurrentTransfers": {
      "limit": 50,
      "used": 35,
      "remaining": 15,
      "utilizationPercentage": 70.0
    },
    "monthlyTransferVolume": {
      "limit": "1TB",
      "used": "750GB",
      "remaining": "250GB",
      "utilizationPercentage": 75.0,
      "resetDate": "2025-02-01T00:00:00Z"
    },
    "storageQuota": {
      "limit": "500GB",
      "used": "325GB",
      "remaining": "175GB",
      "utilizationPercentage": 65.0
    }
  }
}
```

## Administration Guide

### Cluster Management

#### Controller Cluster Setup

```bash
# Start 3-node controller cluster
docker-compose -f docker-compose-network-test.yml up -d

# Check cluster health
curl http://localhost:8081/health
curl http://localhost:8082/health
curl http://localhost:8083/health

# Check Raft status
curl http://localhost:8081/raft/status
```

#### Adding/Removing Nodes

```bash
# Add new controller node
curl -X POST http://localhost:8081/api/v1/cluster/nodes \
  -H "Content-Type: application/json" \
  -d '{
    "nodeId": "controller4",
    "address": "controller4:8080"
  }'

# Remove node
curl -X DELETE http://localhost:8081/api/v1/cluster/nodes/controller4
```

### Monitoring and Logging

#### Health Monitoring

```bash
# Comprehensive health check
curl http://localhost:8080/health/detailed

# Example response
{
  "status": "UP",
  "checks": [
    {
      "name": "transfer-engine",
      "status": "UP",
      "data": {
        "activeTransfers": 5,
        "queuedTransfers": 2
      }
    },
    {
      "name": "cluster",
      "status": "UP",
      "data": {
        "role": "LEADER",
        "term": 15,
        "nodes": 3
      }
    }
  ]
}
```

#### Metrics Collection

```bash
# Prometheus metrics
curl http://localhost:8080/metrics

# Key metrics
quorus_transfers_total{status="completed"} 1250
quorus_transfers_total{status="failed"} 23
quorus_transfer_duration_seconds{quantile="0.5"} 45.2
quorus_cluster_leader_elections_total 3
quorus_tenant_quota_usage{tenant="acme-corp",resource="storage"} 0.45
```

#### Log Configuration

```properties
# logging.properties
# Root logger
.level=INFO
handlers=java.util.logging.ConsoleHandler,java.util.logging.FileHandler

# Console handler
java.util.logging.ConsoleHandler.level=INFO
java.util.logging.ConsoleHandler.formatter=java.util.logging.SimpleFormatter

# File handler
java.util.logging.FileHandler.pattern=/var/log/quorus/quorus.%g.log
java.util.logging.FileHandler.limit=10485760
java.util.logging.FileHandler.count=10
java.util.logging.FileHandler.formatter=java.util.logging.SimpleFormatter

# Package-specific levels
dev.mars.quorus.transfer.level=DEBUG
dev.mars.quorus.workflow.level=INFO
dev.mars.quorus.controller.raft.level=WARNING
```

### Backup and Recovery

#### Configuration Backup

```bash
# Backup tenant configurations
curl http://localhost:8080/api/v1/admin/backup/tenants > tenants-backup.json

# Backup workflow definitions
curl http://localhost:8080/api/v1/admin/backup/workflows > workflows-backup.yaml

# Backup cluster state
curl http://localhost:8081/api/v1/cluster/backup > cluster-backup.json
```

#### Disaster Recovery

```bash
# Restore from backup
curl -X POST http://localhost:8080/api/v1/admin/restore/tenants \
  -H "Content-Type: application/json" \
  --data-binary @tenants-backup.json

# Rebuild cluster from surviving nodes
./scripts/cluster-recovery.sh --surviving-nodes controller1,controller2
```

### Security Configuration

#### TLS Configuration

```yaml
# application.yml
quarkus:
  http:
    ssl:
      certificate:
        files: /etc/ssl/certs/quorus.crt
        key-files: /etc/ssl/private/quorus.key
    ssl-port: 8443

  oidc:
    auth-server-url: https://sso.corp.com/auth/realms/corporate
    client-id: quorus-api
    credentials:
      secret: your-client-secret
```

#### Access Control

```yaml
# rbac-config.yaml
apiVersion: quorus.dev/v1
kind: RoleBasedAccessControl

roles:
  - name: admin
    permissions:
      - "transfers:*"
      - "workflows:*"
      - "tenants:*"
      - "cluster:*"

  - name: operator
    permissions:
      - "transfers:read"
      - "transfers:create"
      - "workflows:read"
      - "workflows:execute"

  - name: user
    permissions:
      - "transfers:read:own"
      - "transfers:create:own"

bindings:
  - role: admin
    subjects:
      - "group:quorus-admins"
  - role: operator
    subjects:
      - "group:transfer-operators"
  - role: user
    subjects:
      - "group:all-users"
```

## Troubleshooting

### Common Issues

#### Transfer Failures

**Problem**: Transfers failing with network timeouts

```bash
# Check network connectivity
curl -v https://source-server.corp/test-file

# Increase timeout in transfer request
{
  "sourceUri": "https://slow-server.corp/large-file.zip",
  "destinationPath": "/storage/large-file.zip",
  "options": {
    "timeout": 1800,  # 30 minutes
    "retryAttempts": 5
  }
}
```

**Problem**: Checksum validation failures

```bash
# Verify source file integrity
curl -I https://source-server.corp/file.zip
# Check Content-MD5 or ETag headers

# Disable checksum validation if source is unreliable
{
  "sourceUri": "https://legacy-system.corp/data.csv",
  "destinationPath": "/storage/data.csv",
  "options": {
    "checksumValidation": false
  }
}
```

#### Cluster Issues

**Problem**: Leader election failures

```bash
# Check cluster status
curl http://localhost:8081/raft/status
curl http://localhost:8082/raft/status
curl http://localhost:8083/raft/status

# Check network connectivity between nodes
docker exec quorus-controller1 ping controller2
docker exec quorus-controller1 ping controller3

# Review election timeout settings
# Increase if network latency is high
ELECTION_TIMEOUT_MS=10000
```

**Problem**: Split-brain scenarios

```bash
# Check cluster quorum
curl http://localhost:8081/api/v1/cluster/quorum

# Manually trigger leader election
curl -X POST http://localhost:8081/api/v1/cluster/election

# Restart minority nodes if needed
docker restart quorus-controller3
```

#### Workflow Issues

**Problem**: Workflow validation errors

```bash
# Validate workflow syntax
java -jar quorus-workflow-2.0.jar validate --file workflow.yaml

# Check dependency cycles
java -jar quorus-workflow-2.0.jar analyze --file workflow.yaml

# Dry run to test execution plan
java -jar quorus-workflow-2.0.jar execute --file workflow.yaml --mode dry-run
```

**Problem**: Variable resolution failures

```yaml
# Ensure all variables are defined
spec:
  variables:
    sourceApi: "https://api.corp"
    targetPath: "/storage"
    # Add missing variables
    environment: "production"
    timestamp: "{{now}}"
```

### Diagnostic Commands

#### System Diagnostics

```bash
# Generate diagnostic report
curl http://localhost:8080/api/v1/admin/diagnostics > diagnostics.json

# Check system resources
curl http://localhost:8080/api/v1/admin/system/resources

# Thread dump for performance issues
curl http://localhost:8080/api/v1/admin/system/threads > threads.txt
```

#### Network Diagnostics

```bash
# Test network connectivity
./scripts/network-test-helper.sh status

# Check DNS resolution
nslookup source-server.corp
nslookup destination-server.corp

# Test bandwidth
iperf3 -c destination-server.corp -p 5201
```

### Log Analysis

#### Common Log Patterns

```bash
# Transfer failures
grep "Transfer failed" /var/log/quorus/quorus.log

# Cluster events
grep "Leader election" /var/log/quorus/quorus.log
grep "Node joined\|Node left" /var/log/quorus/quorus.log

# Performance issues
grep "Transfer rate below threshold" /var/log/quorus/quorus.log
grep "Queue full" /var/log/quorus/quorus.log
```

## Best Practices

### Transfer Optimization

#### File Size Considerations

```yaml
# For large files (>1GB)
transfers:
  - name: large-file-transfer
    source: "https://api.corp/large-dataset.zip"
    destination: "/storage/large-dataset.zip"
    options:
      chunkSize: 10485760  # 10MB chunks
      timeout: 3600        # 1 hour timeout
      retryAttempts: 5
      checksumValidation: true
```

#### Concurrent Transfers

```yaml
# Optimize parallelism based on network capacity
execution:
  strategy: parallel
  parallelism: 5  # Adjust based on bandwidth and server capacity

# Use sequential for bandwidth-limited environments
execution:
  strategy: sequential
```

#### Network Optimization

```yaml
# For high-latency networks
options:
  timeout: 1800
  retryAttempts: 3
  retryDelay: 30

# For unreliable networks
options:
  retryAttempts: 5
  retryBackoffMultiplier: 2
  checksumValidation: true
```

### Workflow Design

#### Dependency Management

```yaml
# Good: Clear dependency chains
transferGroups:
  - name: extract
    transfers: [extract-customers, extract-orders]
  - name: transform
    dependsOn: [extract]
    transfers: [transform-data]
  - name: load
    dependsOn: [transform]
    transfers: [load-warehouse]

# Avoid: Complex cross-dependencies
# This creates unnecessary complexity and potential deadlocks
```

#### Error Handling Strategy

```yaml
# Critical transfers: Fail fast
transferGroups:
  - name: critical-data
    continueOnError: false
    transfers:
      - name: financial-data
        onError:
          action: fail
          notification: "finance-team@corp.com"

# Non-critical transfers: Continue on error
transferGroups:
  - name: optional-data
    continueOnError: true
    transfers:
      - name: marketing-data
        onError:
          action: log
          notification: "marketing-team@corp.com"
```

### Security Best Practices

#### Credential Management

```yaml
# Use secure credential storage
transfers:
  - name: secure-transfer
    source: "sftp://secure-server.corp/data.zip"
    credentials:
      type: "key-based"
      keyPath: "/etc/quorus/keys/transfer-key"
      # Avoid embedding passwords in YAML
```

#### Network Security

```yaml
# Use HTTPS for external transfers
transfers:
  - name: external-api
    source: "https://partner-api.external.com/data"
    options:
      tlsVerification: true
      certificatePath: "/etc/ssl/certs/partner-ca.crt"

# Use internal protocols for internal transfers
transfers:
  - name: internal-transfer
    source: "smb://fileserver.corp.local/share/data"
    protocol: smb
```

### Monitoring and Alerting

#### Key Metrics to Monitor

```yaml
# Transfer success rate
alert: transfer_success_rate < 0.95
action: notify_operations_team

# Cluster health
alert: cluster_nodes_down > 1
action: page_on_call_engineer

# Resource utilization
alert: tenant_quota_usage > 0.9
action: notify_tenant_admin

# Performance degradation
alert: avg_transfer_duration > baseline * 2
action: investigate_performance
```

#### Operational Procedures

```bash
# Daily health check
./scripts/daily-health-check.sh

# Weekly capacity planning
./scripts/capacity-report.sh --tenant all --period week

# Monthly tenant review
./scripts/tenant-usage-report.sh --month $(date +%Y-%m)
```

### Capacity Planning

#### Sizing Guidelines

| Environment | Controllers | Agents | Concurrent Transfers |
|-------------|-------------|--------|---------------------|
| Development | 1 | 1-2 | 10 |
| Testing | 3 | 3-5 | 50 |
| Production | 3-5 | 10-50 | 500+ |
| Enterprise | 5+ | 50+ | 1000+ |

#### Resource Requirements

```yaml
# Controller nodes
resources:
  cpu: 2-4 cores
  memory: 4-8 GB
  storage: 100 GB SSD
  network: 1 Gbps

# Agent nodes
resources:
  cpu: 1-2 cores
  memory: 2-4 GB
  storage: 50 GB
  network: 1 Gbps
```

---

## Support and Resources

### Documentation
- [System Design](quorus-comprehensive-system-design.md)
- [Implementation Plan](quorus-implementation-plan.md)
- [Workflow Examples](README-WORKFLOWS.md)
- [Docker Testing](README-DOCKER-TESTING.md)

### Community
- **GitHub**: https://github.com/your-org/quorus
- **Issues**: https://github.com/your-org/quorus/issues
- **Discussions**: https://github.com/your-org/quorus/discussions

### Enterprise Support
- **Email**: support@quorus.dev
- **Documentation**: https://docs.quorus.dev
- **Training**: https://training.quorus.dev

---

**Quorus v2.0** - Enterprise File Transfer System
Copyright © 2025 Your Organization. All rights reserved.
