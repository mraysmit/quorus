# Sealed Record Commands — Design & Refactoring Proposal

**Author:** GitHub Copilot  
**Date:** 2026-02-20  
**Status:** Proposed  
**Scope:** `quorus-controller` state package — all 6 command classes, 6 codecs, `QuorusStateStore`

---

## Table of Contents

1. [Problem Statement](#1-problem-statement)
2. [Current Architecture](#2-current-architecture)
3. [Proposed Architecture](#3-proposed-architecture)
4. [Full Variant Inventory](#4-full-variant-inventory)
5. [State Machine Transformation](#5-state-machine-transformation)
6. [Codec Layer Impact](#6-codec-layer-impact)
7. [Caller Impact Analysis](#7-caller-impact-analysis)
8. [Dead Code Removal](#8-dead-code-removal)
9. [Migration Strategy](#9-migration-strategy)
10. [Risk Assessment](#10-risk-assessment)
11. [Optional Enhancement: Record Patterns](#11-optional-enhancement-record-patterns-java-21)
12. [State Transition Model — Problem Analysis](#12-state-transition-model--problem-analysis)
13. [Proposed State Transition Model](#13-proposed-state-transition-model)
14. [Migration Strategy — State Transition Phases](#14-migration-strategy--state-transition-phases)
15. [Future Consideration: Type-Safe State Encoding](#15-future-consideration-type-safe-state-encoding)
16. [Appendix A: Current Command Class Structures](#appendix-a-current-command-class-structures)
17. [Appendix B: Caller Inventory](#appendix-b-caller-inventory)

---

## 1. Problem Statement

The state machine currently uses a **two-level dispatch pattern** where the compiler only enforces exhaustiveness at the first level:

```
Level 1 (compile-time safe):  switch (command) { case TransferJobCommand cmd -> ... }   ← sealed interface
Level 2 (enum, runtime risk):  switch (cmd.getType()) { case CREATE -> ... }            ← enum, needs yield
```

Level 1 uses Java 21 sealed interface pattern matching — adding a new `RaftCommand` subtype without
handling it in the switch produces a compile error. This is correct.

Level 2 uses an inner `enum` inside each command class. While Java 21 switch expressions on enums are
exhaustive, this level has three structural problems:

### 1.1 Tagged Union with Nullable Fields — Structural Lying

Each command variant is a single class carrying **all possible fields**, with `null` for inapplicable ones:

```java
// TransferJobCommand carries 5 fields, but DELETE only uses jobId
new TransferJobCommand(Type.DELETE, jobId, null/*transferJob*/, null/*status*/, null/*bytes*/)
```

Every `TransferJobCommand` instance has a `getTransferJob()` method. The type signature says
"I can give you a `TransferJob`." But for 3 out of 4 variants, that's a lie — it returns `null`.
The API *promises* data it doesn't have.

The developer must carry mental knowledge: "I can only call `getTransferJob()` after checking
`getType() == CREATE`." The compiler can't help — it sees a valid method call on a valid type.
The bug doesn't manifest until runtime, and it manifests as a `NullPointerException` somewhere
downstream, far from the actual mistake.

Across all 6 command classes there are **30 enum variants** sharing fields that are semantically
exclusive.

### 1.2 Yield-Based Switch Bodies

The `QuorusStateStore.apply*Command()` methods use `switch (cmd.getType())` with block bodies and
`yield` statements. Each case is 5–15 lines of mutable logic inside a `-> { ... yield result; }` block.
This prevents extracting cases into focused methods and makes the switch expression difficult to read.

### 1.3 No Compile-Time Safety at Level 2

Adding a new variant to an inner enum (e.g., `TransferJobCommand.Type.RETRY`) does not produce a
compile error in `QuorusStateStore` — the developer must remember to update the switch manually.
While Java detects missing enum cases in switch expressions, the real issue is that the new variant
shares the same field structure as all other variants, so it's impossible to enforce that the required
fields are present.

### 1.4 Core Principle: Making Impossible States Unrepresentable

The three problems above are symptoms of a single root cause: the current design is a **tagged union**
(one type, enum tag, nullable fields) where the type system makes impossible states representable.
A `DELETE` command physically carries a `transferJob` field — it's `null`, but the field *exists*.
Nothing prevents code from reading it, passing it downstream, or storing it.

The solution is a **sum type** (sealed interface + records) where each variant carries exactly its own
data and nothing else:

```java
record Delete(String jobId) implements TransferJobCommand {}
```

`Delete` has no `transferJob` field. Not a method that returns null — **no method at all**.
If you write `delete.transferJob()`, the compiler rejects it. The invalid operation isn't just
discouraged or documented — it's unrepresentable in the type system.

This is the difference between:
- **Runtime safety**: "Call this method and maybe you get null" — requires discipline
- **Compile-time safety**: "This method doesn't exist on this type" — requires nothing, the compiler enforces it

Pattern matching bridges the gap. When code needs variant-specific data, the `case` narrows the type:

```java
case TransferJobCommand.Create cmd -> {
    // cmd.transferJob() exists here — guaranteed non-null
    // cmd.status() does NOT exist — can't even write it
    store.put(cmd.jobId(), snapshot(cmd.transferJob()));
}
case TransferJobCommand.Delete cmd -> {
    // cmd.jobId() exists — that's all Delete carries
    // cmd.transferJob() — compile error, no such method
    store.remove(cmd.jobId());
}
```

Inside each branch, you have exactly the fields that variant carries — no more, no less. The compiler
has proven which variant you have, so it gives you precisely the API surface that makes sense.

The shared `jobId()` accessor on the `TransferJobCommand` interface captures what's *universally true*
about the type — every variant has a job ID. The records capture what's *specifically true* about each
variant. Code that only needs the ID (logging, routing, indexing) works with any `TransferJobCommand`
without pattern matching. You only pattern match when you need variant-specific data.

The null fields in the current design aren't a missing feature — they're impossible states that the
design makes representable. The sealed record approach makes those impossible states unrepresentable.
That is the fundamental win.

---

## 2. Current Architecture

### 2.1 Type Hierarchy

```
RaftCommand (sealed interface)
├── TransferJobCommand (final class, enum Type: CREATE | UPDATE_STATUS | UPDATE_PROGRESS | DELETE)
├── AgentCommand (final class, enum CommandType: REGISTER | DEREGISTER | UPDATE_STATUS | UPDATE_CAPABILITIES | HEARTBEAT)
├── SystemMetadataCommand (final class, enum Type: SET | DELETE)
├── JobAssignmentCommand (final class, enum CommandType: ASSIGN | ACCEPT | REJECT | UPDATE_STATUS | TIMEOUT | CANCEL | REMOVE)
├── JobQueueCommand (final class, enum CommandType: ENQUEUE | DEQUEUE | PRIORITIZE | REMOVE | EXPEDITE | UPDATE_REQUIREMENTS)
└── RouteCommand (final class, enum CommandType: CREATE | UPDATE | DELETE | SUSPEND | RESUME | UPDATE_STATUS)
```

### 2.2 Field Structure (TransferJobCommand example)

```java
public final class TransferJobCommand implements RaftCommand {
    public enum Type { CREATE, UPDATE_STATUS, UPDATE_PROGRESS, DELETE }
    
    private final Type type;
    private final String jobId;
    private final TransferJob transferJob;      // only non-null for CREATE
    private final TransferStatus status;        // only non-null for UPDATE_STATUS
    private final Long bytesTransferred;        // only non-null for UPDATE_PROGRESS
}
```

Every variant carries all 5 fields. The constructor sets unused fields to `null` — the type system
makes impossible states representable (see [Section 1.4](#14-core-principle-making-impossible-states-unrepresentable)).

### 2.3 State Machine Dispatch

```java
// Level 1: sealed interface pattern matching (compile-safe)
Object result = switch (command) {
    case TransferJobCommand cmd -> applyTransferJobCommand(cmd);
    case AgentCommand cmd -> applyAgentCommand(cmd);
    // ... 4 more
};

// Level 2: enum switch with yield (not compile-safe for field access)
private Object applyTransferJobCommand(TransferJobCommand command) {
    return switch (command.getType()) {
        case CREATE -> { ... yield job; }
        case UPDATE_STATUS -> { ... yield updatedJob; }
        case UPDATE_PROGRESS -> { ... yield updatedJob; }
        case DELETE -> { ... yield removedJob; }
    };
}
```

### 2.4 Codec Pattern

Each codec maps between the Java command and its protobuf representation:

```java
// TransferCodec.toProto (serialization)
builder.setType(toProto(cmd.getType()));                                          // enum mapping
Optional.ofNullable(cmd.getTransferJob()).ifPresent(job -> builder.setTransferJob(...));  // nullable guard
Optional.ofNullable(cmd.getStatus()).ifPresent(s -> builder.setStatus(...));              // nullable guard
Optional.ofNullable(cmd.getBytesTransferred()).ifPresent(builder::setBytesTransferred);   // nullable guard

// TransferCodec.fromProto (deserialization) 
return switch (fromProto(proto.getType())) {
    case CREATE -> TransferJobCommand.create(fromProtoJob(proto.getTransferJob()));
    case UPDATE_STATUS -> TransferJobCommand.updateStatus(proto.getJobId(), fromProtoStatus(proto.getStatus()));
    // ...
};
```

The `Optional.ofNullable` calls exist because every field might be null depending on the variant.

---

## 3. Proposed Architecture

Replace each command class with a **sealed interface whose permits are records**. Each record carries
exactly the fields its variant needs — no nullable optionals, no type tag enum.

### 3.1 Sealed Interface Structure

**Level 1** — unchanged:
```java
public sealed interface RaftCommand extends Serializable
        permits TransferJobCommand, AgentCommand, SystemMetadataCommand,
                JobAssignmentCommand, JobQueueCommand, RouteCommand {}
```

**Level 2** — each command becomes a sealed interface with record subtypes:

```java
public sealed interface TransferJobCommand extends RaftCommand {

    /** Common accessor — every variant identifies itself by jobId. */
    String jobId();

    record Create(String jobId, TransferJob transferJob)
            implements TransferJobCommand {
        private static final long serialVersionUID = 1L;
    }

    record UpdateStatus(String jobId, TransferStatus newStatus)
            implements TransferJobCommand {
        private static final long serialVersionUID = 1L;
    }

    record UpdateProgress(String jobId, long bytesTransferred)
            implements TransferJobCommand {
        private static final long serialVersionUID = 1L;
    }

    record Delete(String jobId)
            implements TransferJobCommand {
        private static final long serialVersionUID = 1L;
    }

    // ── Factory methods (preserves existing API) ─────────────────
    
    static TransferJobCommand create(TransferJob job) {
        return new Create(job.getJobId(), job);
    }

    static TransferJobCommand updateStatus(String jobId, TransferStatus status) {
        return new UpdateStatus(jobId, status);
    }

    static TransferJobCommand updateProgress(String jobId, long bytesTransferred) {
        return new UpdateProgress(jobId, bytesTransferred);
    }

    static TransferJobCommand delete(String jobId) {
        return new Delete(jobId);
    }
}
```

### 3.2 Type Hierarchy After Refactoring

```
RaftCommand (sealed interface)
├── TransferJobCommand (sealed interface)
│   ├── TransferJobCommand.Create (record)
│   ├── TransferJobCommand.UpdateStatus (record)
│   ├── TransferJobCommand.UpdateProgress (record)
│   └── TransferJobCommand.Delete (record)
├── AgentCommand (sealed interface)
│   ├── AgentCommand.Register (record)
│   ├── AgentCommand.Deregister (record)
│   ├── AgentCommand.UpdateStatus (record)
│   ├── AgentCommand.UpdateCapabilities (record)
│   └── AgentCommand.Heartbeat (record)
├── SystemMetadataCommand (sealed interface)
│   ├── SystemMetadataCommand.Set (record)
│   └── SystemMetadataCommand.Delete (record)
├── JobAssignmentCommand (sealed interface)
│   ├── JobAssignmentCommand.Assign (record)
│   ├── JobAssignmentCommand.Accept (record)
│   ├── JobAssignmentCommand.Reject (record)
│   ├── JobAssignmentCommand.UpdateStatus (record)
│   ├── JobAssignmentCommand.Timeout (record)
│   ├── JobAssignmentCommand.Cancel (record)
│   └── JobAssignmentCommand.Remove (record)
├── JobQueueCommand (sealed interface)
│   ├── JobQueueCommand.Enqueue (record)
│   ├── JobQueueCommand.Dequeue (record)
│   ├── JobQueueCommand.Prioritize (record)
│   ├── JobQueueCommand.Remove (record)
│   ├── JobQueueCommand.Expedite (record)
│   └── JobQueueCommand.UpdateRequirements (record)
└── RouteCommand (sealed interface)
    ├── RouteCommand.Create (record)
    ├── RouteCommand.Update (record)
    ├── RouteCommand.Delete (record)
    ├── RouteCommand.Suspend (record)
    ├── RouteCommand.Resume (record)
    └── RouteCommand.UpdateStatus (record)
```

### 3.3 Key Properties

| Property | Before (enum tag) | After (sealed records) |
|---|---|---|
| Exhaustiveness at level 2 | Enum switch — checked but fields not enforced | Sealed pattern match — type + fields enforced |
| Nullable fields | 3-5 nullable fields per class | Zero — each record has exactly its fields |
| Adding a new variant | Add enum value + update switch (manual) | Add record + compiler errors everywhere |
| Field access safety | `getTransferJob()` returns null on DELETE | `Delete` record has no `transferJob()` accessor |
| `yield` in state machine | Required (block bodies in switch) | Eliminated (expression bodies or handler methods) |
| Serialization | Java Serializable with null fields | Records with `serialVersionUID` + protobuf codec |

---

## 4. Full Variant Inventory

### 4.1 TransferJobCommand (4 variants)

| Record | Fields | Notes |
|---|---|---|
| `Create` | `String jobId`, `TransferJob transferJob` | Carries full transfer job for initial creation |
| `UpdateStatus` | `String jobId`, `TransferStatus newStatus` | Status transitions (PENDING → IN_PROGRESS → COMPLETED) |
| `UpdateProgress` | `String jobId`, `long bytesTransferred` | Primitive `long`, not `Long` — no null boxing |
| `Delete` | `String jobId` | Minimal — just the ID to remove |

### 4.2 AgentCommand (5 variants)

| Record | Fields | Notes |
|---|---|---|
| `Register` | `String agentId`, `AgentInfo agentInfo` | Full agent info for registration |
| `Deregister` | `String agentId`, `Instant timestamp` | Timestamp for audit trail |
| `UpdateStatus` | `String agentId`, `AgentStatus newStatus`, `Instant timestamp` | Status change with timestamp |
| `UpdateCapabilities` | `String agentId`, `AgentCapabilities newCapabilities`, `Instant timestamp` | Capability update with timestamp |
| `Heartbeat` | `String agentId`, `Instant timestamp` | Keep-alive signal |

Common accessor: `String agentId()` on the sealed interface.

### 4.3 SystemMetadataCommand (2 variants)

| Record | Fields | Notes |
|---|---|---|
| `Set` | `String key`, `String value` | Upsert a metadata key-value pair |
| `Delete` | `String key` | Remove a metadata key |

Common accessor: `String key()` on the sealed interface.

### 4.4 JobAssignmentCommand (7 variants)

| Record | Fields | Notes |
|---|---|---|
| `Assign` | `String assignmentId`, `JobAssignment jobAssignment`, `Instant timestamp` | Full assignment with generated ID |
| `Accept` | `String assignmentId`, `Instant timestamp` | Agent accepts — status set by factory to ACCEPTED |
| `Reject` | `String assignmentId`, `String reason`, `Instant timestamp` | Agent rejects with reason |
| `UpdateStatus` | `String assignmentId`, `JobAssignmentStatus newStatus`, `Instant timestamp` | Generic status transition |
| `Timeout` | `String assignmentId`, `Instant timestamp` | Timeout detection — status set by factory to TIMEOUT |
| `Cancel` | `String assignmentId`, `String reason`, `Instant timestamp` | Controller cancels with reason |
| `Remove` | `String assignmentId`, `Instant timestamp` | Cleanup after completion/failure |

Common accessor: `String assignmentId()` on the sealed interface.

**Validation:** The current `validateCommand()` method in `JobAssignmentCommand` validates field
presence per variant. With records, this validation is structural — `Assign` requires a non-null
`JobAssignment` because it's a record component. Use compact constructors for additional validation:

```java
record Assign(String assignmentId, JobAssignment jobAssignment, Instant timestamp)
        implements JobAssignmentCommand {
    Assign {  // compact constructor
        Objects.requireNonNull(jobAssignment, "Job assignment cannot be null");
        Objects.requireNonNull(assignmentId, "Assignment ID cannot be null");
        if (timestamp == null) timestamp = Instant.now();
    }
}
```

### 4.5 JobQueueCommand (6 variants)

| Record | Fields | Notes |
|---|---|---|
| `Enqueue` | `String jobId`, `QueuedJob queuedJob` | Add job to queue with priority |
| `Dequeue` | `String jobId`, `String reason` | Remove for assignment |
| `Prioritize` | `String jobId`, `JobPriority newPriority`, `String reason`, `Instant timestamp` | Change priority level |
| `Remove` | `String jobId`, `String reason`, `Instant timestamp` | Cancel/delete from queue |
| `Expedite` | `String jobId`, `String reason`, `Instant timestamp` | Move to front of queue |
| `UpdateRequirements` | `String jobId`, `QueuedJob queuedJob` | Update job requirements |

Common accessor: `String jobId()` on the sealed interface.

### 4.6 RouteCommand (6 variants)

| Record | Fields | Notes |
|---|---|---|
| `Create` | `String routeId`, `RouteConfiguration routeConfiguration` | Full config for new route |
| `Update` | `String routeId`, `RouteConfiguration routeConfiguration`, `Instant timestamp` | Config update |
| `Delete` | `String routeId`, `Instant timestamp` | Route removal |
| `Suspend` | `String routeId`, `String reason`, `Instant timestamp` | Pause with reason |
| `Resume` | `String routeId`, `Instant timestamp` | Reactivate |
| `UpdateStatus` | `String routeId`, `RouteStatus newStatus`, `String reason`, `Instant timestamp` | Generic status change |

Common accessor: `String routeId()` on the sealed interface.

---

## 5. State Machine Transformation

### 5.1 Current Pattern (yield-based)

```java
private Object applyTransferJobCommand(TransferJobCommand command) {
    String jobId = command.getJobId();
    return switch (command.getType()) {
        case CREATE -> {
            TransferJob job = command.getTransferJob();
            TransferJobSnapshot snapshot = TransferJobSnapshot.fromTransferJob(job);
            transferJobs.put(jobId, snapshot);
            logger.info("Created transfer job: jobId={}, totalJobs={}", jobId, transferJobs.size());
            yield job;
        }
        case UPDATE_STATUS -> {
            TransferJobSnapshot existingJob = getOrWarn(transferJobs, jobId, "Transfer job", "status update");
            if (existingJob == null) yield null;
            TransferStatus oldStatus = existingJob.getStatus();
            TransferJobSnapshot updatedJob = new TransferJobSnapshot(
                    existingJob.getJobId(), existingJob.getSourceUri(), existingJob.getDestinationPath(),
                    command.getStatus(), existingJob.getBytesTransferred(), existingJob.getTotalBytes(),
                    existingJob.getStartTime(), java.time.Instant.now(),
                    existingJob.getErrorMessage(), existingJob.getDescription());
            transferJobs.put(jobId, updatedJob);
            logger.info("Updated transfer job status: jobId={}, oldStatus={}, newStatus={}", 
                jobId, oldStatus, command.getStatus());
            yield updatedJob;
        }
        // ... UPDATE_PROGRESS, DELETE similarly verbose ...
    };
}
```

### 5.2 Proposed Pattern (handler delegation)

```java
private Object applyTransferJobCommand(TransferJobCommand command) {
    logger.debug("Processing transfer job command: type={}, jobId={}",
        command.getClass().getSimpleName(), command.jobId());
    return switch (command) {
        case TransferJobCommand.Create cmd        -> handleCreateJob(cmd);
        case TransferJobCommand.UpdateStatus cmd  -> handleUpdateJobStatus(cmd);
        case TransferJobCommand.UpdateProgress cmd -> handleUpdateJobProgress(cmd);
        case TransferJobCommand.Delete cmd        -> handleDeleteJob(cmd);
    };
}

private TransferJob handleCreateJob(TransferJobCommand.Create cmd) {
    TransferJobSnapshot snapshot = TransferJobSnapshot.fromTransferJob(cmd.transferJob());
    transferJobs.put(cmd.jobId(), snapshot);
    logger.info("Created transfer job: jobId={}, protocol={}, totalJobs={}", 
        cmd.jobId(), cmd.transferJob().getRequest().getProtocol(), transferJobs.size());
    return cmd.transferJob();
}

private TransferJobSnapshot handleUpdateJobStatus(TransferJobCommand.UpdateStatus cmd) {
    TransferJobSnapshot existing = getOrWarn(transferJobs, cmd.jobId(), "Transfer job", "status update");
    if (existing == null) return null;
    TransferStatus oldStatus = existing.getStatus();
    TransferJobSnapshot updated = new TransferJobSnapshot(
            existing.getJobId(), existing.getSourceUri(), existing.getDestinationPath(),
            cmd.newStatus(), existing.getBytesTransferred(), existing.getTotalBytes(),
            existing.getStartTime(), Instant.now(),
            existing.getErrorMessage(), existing.getDescription());
    transferJobs.put(cmd.jobId(), updated);
    logger.info("Updated transfer job status: jobId={}, oldStatus={}, newStatus={}", 
        cmd.jobId(), oldStatus, cmd.newStatus());
    return updated;
}

private TransferJobSnapshot handleUpdateJobProgress(TransferJobCommand.UpdateProgress cmd) {
    TransferJobSnapshot existing = getOrWarn(transferJobs, cmd.jobId(), "Transfer job", "progress update");
    if (existing == null) return null;
    TransferJobSnapshot updated = new TransferJobSnapshot(
            existing.getJobId(), existing.getSourceUri(), existing.getDestinationPath(),
            existing.getStatus(), cmd.bytesTransferred(), existing.getTotalBytes(),
            existing.getStartTime(), Instant.now(),
            existing.getErrorMessage(), existing.getDescription());
    transferJobs.put(cmd.jobId(), updated);
    return updated;
}

private TransferJobSnapshot handleDeleteJob(TransferJobCommand.Delete cmd) {
    TransferJobSnapshot removed = removeOrWarn(transferJobs, cmd.jobId(), "Transfer job", "deletion");
    if (removed == null) return null;
    logger.info("Deleted transfer job: jobId={}, finalStatus={}, totalJobs={}", 
        cmd.jobId(), removed.getStatus(), transferJobs.size());
    return removed;
}
```

### 5.3 Structural Comparison

| Aspect | Before | After |
|---|---|---|
| Dispatch method | 1 per domain, 20-80 lines | 1 dispatcher (5-8 lines) + N handlers (8-15 lines each) |
| Switch body | Block with `yield` | Expression (single method call) |
| Field access | `command.getTransferJob()` (nullable) | `cmd.transferJob()` (non-null, typed) |
| Return type | `Object` (from `yield`) | Specific type per handler |
| `getOrWarn`/`removeOrWarn` helpers | Still used, return null | Still used, return null (Raft safety) |
| Adding a new variant | Add enum case + switch block | Add record + compiler forces handler |
| Total methods in QuorusStateStore | ~12 | ~36 (6 dispatchers + 30 handlers) |
| Total LOC | ~550 (6 switch methods) | ~520 (more methods, less per method) |

### 5.4 Raft Safety Note

The `getOrWarn` / `removeOrWarn` helper pattern and null returns are **retained**. In a Raft state
machine, committed commands must be applied deterministically on every node. A DELETE followed by an
UPDATE for the same entity (both committed) would break log replay if UPDATE threw. Returning null
for "entity not found" is correct Raft behavior — the refactoring changes structure, not semantics.

---

## 6. Codec Layer Impact

### 6.1 Serialization (`toProto`)

**Before** — one method per command class, switches on enum, guards all nullable fields:

```java
public static dev.mars.quorus.controller.raft.grpc.TransferJobCommand toProto(TransferJobCommand cmd) {
    var builder = dev.mars.quorus.controller.raft.grpc.TransferJobCommand.newBuilder()
        .setType(toProto(cmd.getType()))      // enum → proto enum
        .setJobId(cmd.getJobId());
    Optional.ofNullable(cmd.getTransferJob()).ifPresent(job -> builder.setTransferJob(toProtoJob(job)));
    Optional.ofNullable(cmd.getStatus()).ifPresent(s -> builder.setStatus(toProtoStatus(s)));
    Optional.ofNullable(cmd.getBytesTransferred()).ifPresent(builder::setBytesTransferred);
    return builder.build();
}
```

**After** — pattern match on sealed subtypes, each case sets exactly its fields (no `Optional.ofNullable`):

```java
public static dev.mars.quorus.controller.raft.grpc.TransferJobCommand toProto(TransferJobCommand cmd) {
    var builder = dev.mars.quorus.controller.raft.grpc.TransferJobCommand.newBuilder()
        .setJobId(cmd.jobId());
    return switch (cmd) {
        case TransferJobCommand.Create c -> builder
            .setType(PROTO_CREATE)
            .setTransferJob(toProtoJob(c.transferJob()))
            .build();
        case TransferJobCommand.UpdateStatus c -> builder
            .setType(PROTO_UPDATE_STATUS)
            .setStatus(toProtoStatus(c.newStatus()))
            .build();
        case TransferJobCommand.UpdateProgress c -> builder
            .setType(PROTO_UPDATE_PROGRESS)
            .setBytesTransferred(c.bytesTransferred())
            .build();
        case TransferJobCommand.Delete c -> builder
            .setType(PROTO_DELETE)
            .build();
    };
}
```

### 6.2 Deserialization (`fromProto`)

**Before** — switches on proto enum type, calls factory methods:

```java
public static TransferJobCommand fromProto(dev.mars.quorus.controller.raft.grpc.TransferJobCommand proto) {
    return switch (fromProto(proto.getType())) {
        case CREATE -> TransferJobCommand.create(fromProtoJob(proto.getTransferJob()));
        case UPDATE_STATUS -> TransferJobCommand.updateStatus(proto.getJobId(), fromProtoStatus(proto.getStatus()));
        case UPDATE_PROGRESS -> TransferJobCommand.updateProgress(proto.getJobId(), proto.getBytesTransferred());
        case DELETE -> TransferJobCommand.delete(proto.getJobId());
    };
}
```

**After** — same structure, now constructs records (factory methods still work):

```java
public static TransferJobCommand fromProto(dev.mars.quorus.controller.raft.grpc.TransferJobCommand proto) {
    return switch (fromProtoType(proto.getType())) {
        case PROTO_CREATE -> TransferJobCommand.create(fromProtoJob(proto.getTransferJob()));
        case PROTO_UPDATE_STATUS -> TransferJobCommand.updateStatus(proto.getJobId(), fromProtoStatus(proto.getStatus()));
        case PROTO_UPDATE_PROGRESS -> TransferJobCommand.updateProgress(proto.getJobId(), proto.getBytesTransferred());
        case PROTO_DELETE -> TransferJobCommand.delete(proto.getJobId());
    };
}
```

The `fromProto` direction is essentially unchanged because it already uses factory methods.

### 6.3 Protobuf Schema

**No changes to `.proto` files.** The wire format is unchanged — the codec maps differently internally
but produces identical protobuf messages. This is critical for rolling upgrades in a Raft cluster.

### 6.4 Enum Mapping Methods

The private `toProto(CommandType)` / `fromProto(ProtoCommandType)` methods in each codec are retained
since they still need to map between Java-side knowledge and proto enum values. However, the Java-side
enum types (`TransferJobCommand.Type`, etc.) are removed. The mapping shifts from:

- **Before:** `Java enum Type` ↔ `Proto enum Type`
- **After:** `Java sealed subtype (class identity)` → `Proto enum Type` (in `toProto`),
  `Proto enum Type` → `Java factory method → record` (in `fromProto`)

The `toProto` codec will use a pattern match to determine which proto type to use:

```java
private static dev.mars.quorus.controller.raft.grpc.TransferJobCommandType resolveProtoType(TransferJobCommand cmd) {
    return switch (cmd) {
        case TransferJobCommand.Create _        -> PROTO_CREATE;
        case TransferJobCommand.UpdateStatus _  -> PROTO_UPDATE_STATUS;
        case TransferJobCommand.UpdateProgress _ -> PROTO_UPDATE_PROGRESS;
        case TransferJobCommand.Delete _        -> PROTO_DELETE;
    };
}
```

---

## 7. Caller Impact Analysis

### 7.1 Production Callers — Zero Changes Required

All production code creates commands via **static factory methods** on the command type:

```java
// TransferHandler.java (line 66)
TransferJobCommand.create(job);

// AgentRegistrationHandler.java (line 62)
AgentCommand.register(agentInfo);

// JobAssignmentHandler.java (line 81)
JobAssignmentCommand.assign(assignment);

// RouteHandler.java (line 84)
RouteCommand.create(route);
```

The factory methods are preserved on the sealed interfaces with identical signatures. They return
the interface type, so callers never see the record subtypes. **No production code changes.**

Full production caller inventory:

| Handler / Service | Factory Calls Used |
|---|---|
| `TransferHandler` | `create`, `delete` |
| `JobStatusHandler` | `TransferJobCommand.updateProgress`, `JobAssignmentCommand.updateStatus` |
| `AgentRegistrationHandler` | `register` |
| `HeartbeatHandler` | `heartbeat` (both overloads) |
| `JobAssignmentHandler` | `assign`, `accept`, `reject`, `updateStatus`, `cancel`, `remove` |
| `JobAssignmentService` | `assign`, `updateStatus`, `cancel`, `JobQueueCommand.enqueue`, `dequeue`, `remove` |
| `RouteHandler` | `create`, `update`, `delete` |
| `AgentRegistryService` (legacy) | `register`, `deregister`, `updateCapabilities` |
| `HeartbeatProcessor` (legacy) | `heartbeat`, `updateStatus` |

### 7.2 Test Callers — Mechanical Updates

Tests that assert on `.getType()` will need updating:

**Before:**
```java
assertEquals(TransferJobCommand.Type.CREATE, result.getType());
```

**After:**
```java
assertInstanceOf(TransferJobCommand.Create.class, result);
```

Or with pattern matching:
```java
if (result instanceof TransferJobCommand.Create create) {
    assertEquals("expected-job-id", create.jobId());
}
```

Estimated test assertions requiring update: ~150 across 6 test files.

### 7.3 Codec Package-Private Constructors

Four of the six codecs use package-private constructors for deserialization:

```java
// AgentCodec.fromProto currently does:
new AgentCommand(CommandType.REGISTER, agentId, agentInfo, null, null, timestamp);
```

After refactoring, these become record constructors:
```java
new AgentCommand.Register(agentId, agentInfo);
```

However, since factory methods can handle timestamp defaulting, using factory methods is preferred.
Codec-specific constructors (with explicit timestamp) can be added to records as secondary methods:

```java
record Register(String agentId, AgentInfo agentInfo) implements AgentCommand {
    // Used by codec for deserialization — agentInfo already contains timestamp
}
```

---

## 8. Dead Code Removal

The following code is removed as part of this refactoring:

| Dead Code | Location | Reason |
|---|---|---|
| `AgentCommand.isRegister()` | `AgentCommand.java` | 0 callers — replaced by `instanceof AgentCommand.Register` |
| `AgentCommand.isDeregister()` | `AgentCommand.java` | 0 callers — replaced by `instanceof AgentCommand.Deregister` |
| `AgentCommand.isUpdateStatus()` | `AgentCommand.java` | 0 callers — replaced by `instanceof AgentCommand.UpdateStatus` |
| `AgentCommand.isUpdateCapabilities()` | `AgentCommand.java` | 0 callers — replaced by `instanceof AgentCommand.UpdateCapabilities` |
| `AgentCommand.isHeartbeat()` | `AgentCommand.java` | 0 callers — replaced by `instanceof AgentCommand.Heartbeat` |
| `enum Type` / `enum CommandType` | All 6 command classes | Replaced by sealed record subtypes |
| `getType()` method | All 6 command classes | No type tag needed — the type IS the class |
| `toProto(CommandType)` enum mappers | All 6 codecs | Replaced by pattern-matching type resolution |
| `fromProto(ProtoCommandType)` enum mappers | All 6 codecs | Still needed for proto → Java direction |
| `validateCommand()` switch on type | `JobAssignmentCommand`, `JobQueueCommand` | Validation moves to compact constructors |

---

## 9. Migration Strategy

### 9.1 Phased Approach

Phase per domain to keep each PR reviewable and independently testable. Order from simplest to most
complex to establish the pattern early:

| Phase | Domain | Record Count | Files Changed | Estimated Test Assertions Updated |
|---|---|---|---|---|
| 1 | `SystemMetadataCommand` | 2 | 3 (command, codec, state machine) | ~15 |
| 2 | `TransferJobCommand` | 4 | 4 (command, codec, state machine, snapshot) | ~40 |
| 3 | `AgentCommand` | 5 | 3 (command, codec, state machine) | ~20 |
| 4 | `RouteCommand` | 6 | 3 (command, codec, state machine) + route tests | ~30 |
| 5 | `JobQueueCommand` | 6 | 3 (command, codec, state machine) | ~20 |
| 6 | `JobAssignmentCommand` | 7 | 4 (command, codec, state machine, validation) | ~25 |

**Phase 1 (`SystemMetadataCommand`) is the proving ground** — only 2 variants, simplest field
structure, fewest callers. Proves the full pattern: sealed interface → records → codec → state
machine handlers → tests.

### 9.2 Steps Within Each Phase

1. **Convert command class** → sealed interface + records
   - Keep static factory methods with identical signatures
   - Add compact constructors for validation where needed
   - Add `serialVersionUID` to each record
   - Remove `enum Type`/`CommandType`, `getType()`, convenience `is*()` methods
2. **Update codec** (`toProto` / `fromProto`)
   - `toProto`: pattern match on sealed subtypes instead of `cmd.getType()`
   - `fromProto`: use factory methods (already does this for some codecs)
   - Remove enum mapping methods where replaced by pattern matching
3. **Update `QuorusStateStore.applyXxxCommand()`**
   - Replace `switch (command.getType()) { case X -> { ... yield r; } }` with
     `switch (command) { case XxxCommand.X cmd -> handleX(cmd); }`
   - Extract each case body into a named handler method
4. **Update tests**
   - Replace `.getType()` assertions with `assertInstanceOf` or pattern matching
   - Factory method calls remain unchanged (no test setup changes)
5. **Run full `quorus-controller` test suite** — must be green before proceeding

### 9.3 Intermediate Compatibility

During the multi-phase rollout, the `apply()` method's top-level switch still works because
`RaftCommand` sealed interface is unchanged. A domain can be migrated independently
without affecting others.

---

## 10. Risk Assessment

### 10.1 High Severity

| Risk | Description | Mitigation |
|---|---|---|
| **Serialization break (Raft log replay)** | If existing Raft log entries use Java serialization, changing from `final class` to `sealed interface + records` could break deserialization of in-flight logs. | Quorus uses **protobuf encoding** (via `ProtobufCommandCodec`) for Raft log entries, not Java serialization. The codec roundtrip tests are the correctness boundary. Run full codec test suite after each phase. |

### 10.2 Medium Severity

| Risk | Description | Mitigation |
|---|---|---|
| **`JobAssignmentCommand.validateCommand()` complexity** | Current class has a non-trivial `validateCommand()` method with per-variant logic. Splitting into records scatters this validation. | Move validation into compact constructors on each record (e.g., `Assign { Objects.requireNonNull(jobAssignment); }`). Each record validates only its own invariants — simpler and co-located with the data. |
| **Test churn** | ~150 test assertions reference `.getType()`. Mechanical but tedious replacement across 6+ test files. | Purely mechanical — find `.getType()` → replace with `assertInstanceOf`. Factory method API preserved, so test setup code is unchanged. |
| **Package-private constructor access in codecs** | Codecs currently use package-private constructors (e.g., `new AgentCommand(type, ...)`) for explicit timestamp control. Records have a single canonical constructor. | Add secondary static factory methods (e.g., `AgentCommand.Register.withTimestamp(...)`) or use the compact constructor's timestamp defaulting. Since codecs are in the same package, they can call any public/package method. |

### 10.3 Low Severity

| Risk | Description | Mitigation |
|---|---|---|
| **Method count increase in QuorusStateStore** | 30 handler methods replace 30 `case` blocks. The class grows from ~12 methods to ~36 methods. | Same total logic, better organized. Consider grouping handlers into inner classes or extracting domain-specific handler classes (e.g., `TransferJobHandler`). |
| **Snapshot compatibility** | `takeSnapshot()` / `restoreSnapshot()` serialize the state maps (not commands). | Commands are only serialized via the protobuf codec. State maps contain domain objects (`TransferJobSnapshot`, `AgentInfo`, etc.) which are unchanged. |
| **Rolling cluster upgrade** | Mixed-version cluster where some nodes use old enum commands and others use new sealed records. | Wire format is protobuf — identical on the wire. Codec produces the same bytes. Nodes don't share Java objects. No compatibility issue. |

---

## 11. Optional Enhancement: Record Patterns (Java 21)

With sealed record subtypes, the state machine can use **record deconstruction patterns** for
zero-getter field access:

```java
private Object applyTransferJobCommand(TransferJobCommand command) {
    return switch (command) {
        case Create(var jobId, var transferJob) -> {
            transferJobs.put(jobId, TransferJobSnapshot.fromTransferJob(transferJob));
            yield transferJob;
        }
        case UpdateStatus(var jobId, var newStatus) -> {
            var existing = getOrWarn(transferJobs, jobId, "Transfer job", "status update");
            if (existing == null) yield null;
            // use newStatus directly — no getter call
            yield updateSnapshot(existing, newStatus);
        }
        case UpdateProgress(var jobId, var bytes) -> { /* ... */ }
        case Delete(var jobId) -> { /* ... */ }
    };
}
```

This is an **alternative** to the handler-delegation pattern in Section 5.2. It keeps the switch in
one method but uses destructuring instead of `cmd.fieldName()` accessors. The choice is a readability
preference:

| Style | Pros | Cons |
|---|---|---|
| **Handler delegation** (`case X cmd -> handleX(cmd)`) | Each handler is independently testable; switch is a pure dispatcher | More methods, handler naming overhead |
| **Record patterns** (`case X(var a, var b) -> { ... }`) | All logic visible in one method; compact | Longer switch body; `yield` still needed for block bodies |
| **Hybrid** (simple cases inline, complex cases delegate) | Best of both worlds | Inconsistent style in one switch |

**Recommendation:** Start with handler delegation (Section 5.2) for consistency. Record pattern
destructuring can be adopted per-handler later if preferred.

---

## 12. State Transition Model — Problem Analysis

The sealed record refactoring (Sections 1–11) fixes the **command side** — how operations are
represented and dispatched. But it does not address a deeper structural flaw: the state machine
has **no transition model**. It blindly applies any state change to any entity in any state.

### 12.1 What a State Machine Should Do

A proper state machine has three defining properties:
1. **Defined states** — a finite, enumerated set of states an entity can be in
2. **Guarded transitions** — rules declaring which (state, event) → state transitions are legal
3. **Illegal transitions are rejected** — attempting an invalid transition produces an error (or is simply not expressible in the type system)

`QuorusStateStore` has **none of these**. It is a CRUD dispatcher: receive a command, mutate a
`ConcurrentHashMap`, return the result. The name "state store" reflects its role as a Raft log
applicator (`RaftLogApplicator` — the component that applies committed log entries), not domain modeling.

### 12.2 Current Enforcement Audit

An exhaustive audit of all 4 stateful domains reveals that transition enforcement is almost
entirely absent from the state machine layer:

#### 12.2.1 TransferJob — Partially Enforced (Wrong Layer)

| Status Values | `PENDING`, `IN_PROGRESS`, `COMPLETED`, `FAILED`, `CANCELLED`, `PAUSED` |
|---|---|
| Terminal States | `COMPLETED`, `FAILED`, `CANCELLED` |
| Model-level guards | `TransferJob.start()` uses `compareAndSet(PENDING, IN_PROGRESS)` |
| | `TransferJob.pause()` checks `current == IN_PROGRESS` |
| | `TransferJob.resume()` uses `compareAndSet(PAUSED, IN_PROGRESS)` |
| Unconstrained | `complete()`, `fail()`, `cancel()` — unconditional `status.set()` from **any** state |
| State machine check | **None** — `applyTransferJobCommand(UPDATE_STATUS)` overwrites status unconditionally |

**Problem:** The guards exist on `TransferJob` (the core domain object), but the state machine
operates on `TransferJobSnapshot` (a serializable projection). The snapshot's status is overwritten
with whatever the command says — `TransferJob`'s CAS guards are bypassed entirely:

```java
// QuorusStateStore — no guard, just overwrite:
TransferJobSnapshot updatedJob = new TransferJobSnapshot(
    existing.getJobId(), existing.getSourceUri(), existing.getDestinationPath(),
    command.getStatus(),     // ← blindly accepted, could be COMPLETED → PENDING
    existing.getBytesTransferred(), ...);
```

You can `UPDATE_STATUS` a `COMPLETED` job back to `PENDING`. The state machine allows it.

#### 12.2.2 AgentStatus — Not Enforced Anywhere

| Status Values | `REGISTERING`, `HEALTHY`, `ACTIVE`, `IDLE`, `DEGRADED`, `OVERLOADED`, `MAINTENANCE`, `DRAINING`, `UNREACHABLE`, `FAILED`, `DEREGISTERED` (11 total) |
|---|---|
| Terminal States | `FAILED`, `DEREGISTERED` |
| Classification Methods | `isOperational()`, `isAvailableForWork()`, `isHealthy()`, `isProblematic()`, `isTransitional()`, `isTerminal()` |
| Transition Rules | **None at any layer** — `AgentInfo.setStatus()` is an unconstrained setter |
| State machine check | **None** — `applyAgentCommand(UPDATE_STATUS)` sets whatever status the command carries |

**Single exception:** The HEARTBEAT handler has one hard-coded rule: if status is `REGISTERING`,
transition to `HEALTHY`. This is the only "transition guard" in the entire state machine for agents:

```java
case HEARTBEAT -> {
    // ...
    if (agentForHeartbeat.getStatus() == AgentStatus.REGISTERING) {
        agentForHeartbeat.setStatus(AgentStatus.HEALTHY);
    }
    // ...
}
```

You can `UPDATE_STATUS` a `DEREGISTERED` agent to `HEALTHY`. The state machine allows it.

#### 12.2.3 JobAssignment — Defined but Not Checked

| Status Values | `ASSIGNED`, `ACCEPTED`, `IN_PROGRESS`, `COMPLETED`, `FAILED`, `REJECTED`, `TIMEOUT`, `CANCELLED` |
|---|---|
| Terminal States | `COMPLETED`, `FAILED`, `REJECTED`, `TIMEOUT`, `CANCELLED` |
| Transition Rules | **Fully encoded** in `JobAssignmentStatus.canTransitionTo()` |
| Valid Transitions | `ASSIGNED→{ACCEPTED,REJECTED,TIMEOUT,CANCELLED}`, `ACCEPTED→{IN_PROGRESS,CANCELLED,FAILED}`, `IN_PROGRESS→{COMPLETED,FAILED,CANCELLED}`, terminal→∅ |
| State machine check | **None** — `applyJobAssignmentCommand` never calls `canTransitionTo()` |

This is the most egregious case. The rules exist — `JobAssignmentStatus` has a proper
`canTransitionTo()` method with explicit valid-transition arrays — but **nobody calls it** in the
state machine. The state machine does `existing.withStatusAndTimestamp(command.getNewStatus(), ...)`,
passing whatever status the command carries without validation.

You can transition a `COMPLETED` assignment to `ASSIGNED`. The state machine allows it.

The `JobAssignmentService` uses `isTerminal()` for cleanup (removing from `activeAssignments`),
and `JobAssignmentHandler` uses `isActive()`/`isTerminal()` for JSON serialization — but neither
validates the transition itself.

#### 12.2.4 RouteStatus — Not Even Documented in Code

| Status Values | `CONFIGURED`, `ACTIVE`, `TRIGGERED`, `TRANSFERRING`, `SUSPENDED`, `DEGRADED`, `FAILED`, `DELETED` |
|---|---|
| Terminal States | `DELETED` (implied) |
| Transition Rules | Lifecycle documented in Javadoc comment only — not enforced anywhere |
| Classification Methods | **None** — bare enum with zero methods |
| State machine check | **None** — `applyRouteCommand(UPDATE_STATUS)` sets any status |

`RouteStatus` is the worst case: no `isTerminal()`, no `canTransitionTo()`, no classification
helpers at all. The Javadoc describes a lifecycle (`CONFIGURED→ACTIVE→TRIGGERED→TRANSFERRING→ACTIVE`)
but nothing enforces it.

You can `UPDATE_STATUS` a `DELETED` route to `ACTIVE`. The state machine allows it.

#### 12.2.5 Summary of Enforcement Gaps

| Domain | Transition Rules Exist? | State Machine Enforces Them? | Consequence |
|---|---|---|---|
| TransferJob | Partial (on `TransferJob`, not on snapshot) | **No** | Terminal jobs can be un-terminated |
| AgentStatus | No (only classification methods) | **No** | Deregistered agents can be resurrected |
| JobAssignment | **Yes** (`canTransitionTo()`) | **No** | Completed assignments can be reassigned |
| RouteStatus | Javadoc only | **No** | Deleted routes can be reactivated |

### 12.3 Raft Constraint — Why This Isn't Trivially Fixable

In a Raft consensus system, **every committed log entry must be applied deterministically on every
node**. If a command passes leader validation and is committed to the log, all nodes (including
future nodes replaying the log) must apply it without error.

This creates a tension:

1. **Validate before commit:** The leader validates the transition *before* proposing the command to
   the Raft log. If it's invalid, reject the HTTP request immediately — the invalid command never
   enters the log. This is the correct approach.

2. **Validate during apply:** The state machine checks during `apply()`. If invalid, what do you do?
   - **Throw** — breaks determinism (a node might throw on replay if its state diverged)
   - **Log and skip** — safe but silently drops a committed command
   - **Apply anyway** — where we are today

The correct architectural pattern is **pre-commit validation**:

```
HTTP request → validate(current state + proposed command) → if valid, submit to Raft
                                                          → if invalid, return 409 Conflict
```

The state machine's `apply()` method should be a **pure, unconditional applicator** of already-
validated commands. The transition rules belong in the **command submission layer** (HTTP handlers
or services), not in `apply()`.

However, `apply()` should still have **defensive guards** for Raft safety — if a command references
a non-existent entity (possible during log replay after compaction), it should warn and return null,
not throw. This is what the existing `getOrWarn()`/`removeOrWarn()` helpers do.

---

## 13. Proposed State Transition Model

### 13.1 Architecture — Where Validation Lives

```
┌──────────────────────────────────────────────────────────┐
│  HTTP Handler / Service Layer                            │
│                                                          │
│  1. Read current state from StateMachine (query)         │
│  2. Validate transition: currentState.canTransitionTo()  │
│  3. If invalid → return 409 Conflict                     │
│  4. If valid → raftNode.submitCommand(command)           │
└──────────────────────────────┬───────────────────────────┘
                               │
                    ┌──────────▼──────────┐
                    │  Raft Consensus     │
                    │  (replicate + commit)│
                    └──────────┬──────────┘
                               │
                    ┌──────────▼──────────┐
                    │  QuorusStateStore    │
                    │  apply() — pure     │
                    │  applicator, no     │
                    │  validation         │
                    │  (getOrWarn for     │
                    │  Raft safety only)  │
                    └─────────────────────┘
```

### 13.2 Transition Rules to Implement

#### 13.2.1 TransferStatus Transitions

```
                    ┌──────────┐
         ┌─────────│ PENDING  │─────────┐
         │         └────┬─────┘         │
         │              │ start()       │ cancel()
         │              ▼               ▼
         │         ┌──────────┐    ┌──────────┐
         │    ┌────│IN_PROGRESS│───►│CANCELLED │
         │    │    └──┬───┬───┘    └──────────┘
         │    │       │   │              ▲
         │    │pause()│   │complete()    │cancel()
         │    │       ▼   ▼              │
         │    │  ┌──────┐ ┌──────────┐   │
         │    │  │PAUSED│ │COMPLETED │   │
         │    │  └──┬───┘ └──────────┘   │
         │    │     │                    │
         │    │     │resume()            │
         │    │     ▼                    │
         │    └─────┘ (back to          │
         │         IN_PROGRESS)         │
         │                              │
         │    fail() from IN_PROGRESS   │
         │         ┌──────────┐         │
         └────────►│  FAILED  │─────────┘
                   └──────────┘    (cancel from any
                                   non-terminal)
```

Add `canTransitionTo()` to `TransferStatus`:

```java
public boolean canTransitionTo(TransferStatus target) {
    if (this.isTerminal()) return false;
    return switch (this) {
        case PENDING      -> target == IN_PROGRESS || target == CANCELLED;
        case IN_PROGRESS  -> target == COMPLETED || target == FAILED
                          || target == PAUSED || target == CANCELLED;
        case PAUSED       -> target == IN_PROGRESS || target == CANCELLED;
        // terminal states handled by guard above
        default           -> false;
    };
}
```

#### 13.2.2 AgentStatus Transitions

```
                    ┌────────────┐
                    │REGISTERING │
                    └──────┬─────┘
                           │ heartbeat (auto)
                           ▼
                    ┌────────────┐
              ┌─────│  HEALTHY   │◄──────────────────┐
              │     └──┬────┬────┘                    │
              │        │    │                         │ recovery
              │  work  │    │ degrade                 │
              │        ▼    ▼                         │
              │   ┌──────┐ ┌──────────┐         ┌────┴─────┐
              │   │ACTIVE│ │ DEGRADED │────────►│UNREACHABLE│
              │   └──┬───┘ └────┬─────┘         └────┬─────┘
              │      │          │                     │
              │ idle │          │ overload            │ fail
              │      ▼          ▼                     ▼
              │   ┌──────┐ ┌────────────┐       ┌──────────┐
              │   │ IDLE │ │OVERLOADED  │       │  FAILED  │ (terminal)
              │   └──────┘ └────────────┘       └──────────┘
              │
              │ maintenance              drain
              ▼                          │
         ┌────────────┐            ┌─────▼──────┐
         │MAINTENANCE │            │  DRAINING  │──────► DEREGISTERED (terminal)
         └────────────┘            └────────────┘
```

Add `canTransitionTo()` to `AgentStatus`:

```java
public boolean canTransitionTo(AgentStatus target) {
    if (this.isTerminal()) return false;
    return switch (this) {
        case REGISTERING  -> target == HEALTHY || target == FAILED || target == DEREGISTERED;
        case HEALTHY      -> target == ACTIVE || target == IDLE || target == DEGRADED
                          || target == MAINTENANCE || target == DRAINING
                          || target == UNREACHABLE || target == FAILED;
        case ACTIVE       -> target == HEALTHY || target == IDLE || target == DEGRADED
                          || target == OVERLOADED || target == UNREACHABLE || target == FAILED;
        case IDLE         -> target == HEALTHY || target == ACTIVE || target == DEGRADED
                          || target == MAINTENANCE || target == DRAINING
                          || target == UNREACHABLE || target == FAILED;
        case DEGRADED     -> target == HEALTHY || target == UNREACHABLE || target == FAILED;
        case OVERLOADED   -> target == ACTIVE || target == HEALTHY || target == DEGRADED
                          || target == UNREACHABLE || target == FAILED;
        case MAINTENANCE  -> target == HEALTHY || target == FAILED || target == DEREGISTERED;
        case DRAINING     -> target == DEREGISTERED || target == FAILED;
        case UNREACHABLE  -> target == HEALTHY || target == FAILED || target == DEREGISTERED;
        default           -> false; // FAILED, DEREGISTERED — terminal
    };
}
```

#### 13.2.3 JobAssignmentStatus Transitions (Already Exists)

`JobAssignmentStatus.canTransitionTo()` already encodes the correct rules:

```
ASSIGNED    → ACCEPTED, REJECTED, TIMEOUT, CANCELLED
ACCEPTED    → IN_PROGRESS, CANCELLED, FAILED
IN_PROGRESS → COMPLETED, FAILED, CANCELLED
Terminal    → ∅
```

**Action required:** Wire this existing method into the pre-commit validation layer. No new
transition logic needed — just call the method that already exists.

#### 13.2.4 RouteStatus Transitions

```
         ┌────────────┐
         │ CONFIGURED │
         └──────┬─────┘
                │ activate
                ▼
         ┌────────────┐◄──────────────────────────┐
    ┌────│   ACTIVE   │────────────┐               │
    │    └──┬────┬────┘            │               │
    │       │    │                 │ recover       │ complete
    │suspend│    │ trigger    ┌────┴─────┐         │
    │       │    │            │ DEGRADED │         │
    │       │    ▼            └──────────┘         │
    │       │ ┌───────────┐                        │
    │       │ │ TRIGGERED │                        │
    │       │ └─────┬─────┘                        │
    │       │       │ begin transfer               │
    │       │       ▼                              │
    │       │ ┌──────────────┐                     │
    │       │ │TRANSFERRING  │─────────────────────┘
    │       │ └──────┬───────┘
    │       │        │ fail (max retries)
    │       │        ▼
    │       │ ┌────────────┐
    │       │ │   FAILED   │──► CONFIGURED (reconfigure)
    │       │ └────────────┘
    │       ▼
    │  ┌───────────┐
    └─►│ SUSPENDED │──► ACTIVE (resume)
       └───────────┘

    Any non-terminal → DELETED (terminal)
```

Add `canTransitionTo()` to `RouteStatus`:

```java
public boolean canTransitionTo(RouteStatus target) {
    if (this == DELETED) return false;
    return switch (this) {
        case CONFIGURED   -> target == ACTIVE || target == DELETED;
        case ACTIVE       -> target == TRIGGERED || target == SUSPENDED
                          || target == DEGRADED || target == DELETED;
        case TRIGGERED    -> target == TRANSFERRING || target == ACTIVE
                          || target == DELETED;
        case TRANSFERRING -> target == ACTIVE || target == FAILED || target == DELETED;
        case SUSPENDED    -> target == ACTIVE || target == DELETED;
        case DEGRADED     -> target == ACTIVE || target == FAILED || target == DELETED;
        case FAILED       -> target == CONFIGURED || target == DELETED;
        default           -> false; // DELETED — terminal
    };
}
```

### 13.3 Pre-Commit Validation — Implementation Pattern

The validation layer sits in the HTTP handlers/services, between the request parsing and the
`raftNode.submitCommand()` call. Pattern:

```java
// In TransferHandler or equivalent service method:
public Future<TransferJobSnapshot> updateTransferJobStatus(String jobId, TransferStatus newStatus) {
    // 1. Read current state (read-only query on state machine)
    TransferJobSnapshot current = stateMachine.getTransferJob(jobId);
    if (current == null) {
        return Future.failedFuture(new NotFoundException("Transfer job not found: " + jobId));
    }

    // 2. Validate transition
    if (!current.getStatus().canTransitionTo(newStatus)) {
        return Future.failedFuture(new InvalidTransitionException(
            "Cannot transition transfer job '%s' from %s to %s. Valid targets: %s",
            jobId, current.getStatus(), newStatus,
            current.getStatus().getValidTransitions()));
    }

    // 3. Submit to Raft (only valid commands reach the log)
    return raftNode.submitCommand(TransferJobCommand.updateStatus(jobId, newStatus))
        .map(result -> (TransferJobSnapshot) result);
}
```

### 13.4 New Exception Type

```java
/**
 * Thrown when a requested state transition is not valid given the
 * entity's current state. Used for pre-commit validation — this
 * exception should never be thrown from within apply().
 */
public class InvalidTransitionException extends QuorusException {

    private final String entityId;
    private final String currentState;
    private final String requestedState;

    public InvalidTransitionException(String message, String entityId,
            String currentState, String requestedState) {
        super(message);
        this.entityId = entityId;
        this.currentState = currentState;
        this.requestedState = requestedState;
    }

    // HTTP handlers should map this to 409 Conflict
}
```

### 13.5 Classification Method Gaps

Two enums need additional classification methods to match `JobAssignmentStatus`'s completeness:

| Enum | Missing Methods | Purpose |
|---|---|---|
| `TransferStatus` | `getValidTransitions()` | Returns array of valid target states |
| `AgentStatus` | `canTransitionTo()`, `getValidTransitions()` | Currently has classification only |
| `RouteStatus` | `isTerminal()`, `isActive()`, `canTransitionTo()`, `getValidTransitions()` | Currently bare enum with zero methods |

### 13.6 State Machine Query Methods

Pre-commit validation requires reading the current state. `QuorusStateStore` already exposes
most state via getter methods. Verify coverage:

| Domain | Query Method | Exists? |
|---|---|---|
| TransferJob | `getTransferJob(jobId)` | Verify |
| Agent | `getAgent(agentId)` | Verify |
| JobAssignment | `getJobAssignment(assignmentId)` | Verify |
| Route | `getRoute(routeId)` | Verify |

If any are missing, they are trivial to add (single `Map.get()` delegation).

---

## 14. Migration Strategy — State Transition Phases

The original migration strategy (Section 9) covers the sealed record refactoring in 6 phases.
The state transition model is a **separate, subsequent effort** with its own phases. These phases
can be interleaved with or follow the sealed record phases.

### 14.1 Dependency Relationship

```
Phase 1–6 (Sealed Records)     Phase 7–10 (State Transitions)
─────────────────────────       ───────────────────────────────
Independent — can proceed       Requires current state readable
in parallel or sequentially     from QuorusStateStore
                                
Phases 1–6 change command       Phases 7–10 add validation to
representation & dispatch       the submission layer above
                                
No dependency between them      Phase 10 optionally uses sealed
(but Phase 7 pairs well with    record types for typed commands
Phase 2 for TransferJob)        that carry validated transitions
```

### 14.2 Phase 7: Enum Transition Infrastructure

**Goal:** Add `canTransitionTo()` and `getValidTransitions()` to all status enums.

| Task | File | Details |
|---|---|---|
| 7a | `TransferStatus.java` | Add `canTransitionTo(TransferStatus)` and `getValidTransitions()`. Existing `isTerminal()`, `isActive()`, `canResume()` remain. |
| 7b | `AgentStatus.java` | Add `canTransitionTo(AgentStatus)` and `getValidTransitions()`. Existing classification methods remain. |
| 7c | `RouteStatus.java` | Add `isTerminal()`, `isActive()`, `canTransitionTo(RouteStatus)`, `getValidTransitions()`. Currently a bare enum — needs full buildout. |
| 7d | `JobAssignmentStatus.java` | Already complete — verify `canTransitionTo()` covers all cases. |
| 7e | `InvalidTransitionException.java` | Create in `quorus-core/exceptions/`. Extends `QuorusException`. |
| 7f | Tests | Unit tests for every transition pair (valid and invalid) across all 4 enums. |

**Estimated effort:** Small — pure logic additions to existing enums. Each `canTransitionTo()` is
a single switch expression. Test matrix: `|states|² - |valid transitions|` invalid pairs to verify.

**Test approach:** Parameterized tests covering every (source, target) pair:

```java
@ParameterizedTest
@MethodSource("allTransferStatusPairs")
void canTransitionTo_coversAllPairs(TransferStatus from, TransferStatus to, boolean expected) {
    assertEquals(expected, from.canTransitionTo(to),
        () -> String.format("%s → %s should be %s", from, to, expected ? "valid" : "invalid"));
}
```

### 14.3 Phase 8: State Machine Query Methods

**Goal:** Ensure `QuorusStateStore` exposes read-only query methods for all stateful entities.

| Task | Method | Returns |
|---|---|---|
| 8a | `getTransferJob(String jobId)` | `TransferJobSnapshot` or `null` |
| 8b | `getAgent(String agentId)` | `AgentInfo` or `null` |
| 8c | `getJobAssignment(String assignmentId)` | `JobAssignment` or `null` |
| 8d | `getRoute(String routeId)` | `RouteConfiguration` or `null` |

Each is a one-line `Map.get()` delegation. Some may already exist — verify before adding.

### 14.4 Phase 9: Pre-Commit Validation in Handlers

**Goal:** Add transition validation to all HTTP handlers that submit status-changing commands.

| Task | Handler/Service | Validates |
|---|---|---|
| 9a | `TransferHandler` (or new `TransferService`) | `TransferStatus` transitions on `UPDATE_STATUS` |
| 9b | `HeartbeatHandler` | `AgentStatus` transitions (HEARTBEAT already has implicit `REGISTERING→HEALTHY` rule — move to handler) |
| 9c | Agent status updates | `AgentStatus` transitions on `UPDATE_STATUS` |
| 9d | `JobAssignmentHandler` | Wire existing `JobAssignmentStatus.canTransitionTo()` into ACCEPT, REJECT, UPDATE_STATUS, TIMEOUT, CANCEL |
| 9e | `JobAssignmentService` | Same validation for service-layer status updates |
| 9f | `RouteHandler` | `RouteStatus` transitions on UPDATE_STATUS, SUSPEND, RESUME |
| 9g | HTTP error mapping | Map `InvalidTransitionException` → 409 Conflict with JSON body: `{ "error": "invalid_transition", "currentState": "...", "requestedState": "...", "validTransitions": [...] }` |

**Pattern for each handler:**

```java
// Before submitting to Raft:
TransferJobSnapshot current = stateMachine.getTransferJob(jobId);
if (current == null) {
    ctx.response().setStatusCode(404).end(notFoundJson(jobId));
    return;
}
if (!current.getStatus().canTransitionTo(newStatus)) {
    ctx.response().setStatusCode(409).end(invalidTransitionJson(
        jobId, current.getStatus(), newStatus));
    return;
}
// Now safe to submit
raftNode.submitCommand(TransferJobCommand.updateStatus(jobId, newStatus));
```

**Raft safety note:** This validation is **best-effort**. Between reading the current state and
the command being committed, another command could change the state (TOCTOU race). This is
acceptable because:
- In a single-leader Raft cluster, the leader processes commands sequentially
- The `apply()` method is single-threaded on each node
- The worst case is a stale read leading to a redundant state change (idempotent operations)

For strict safety, a future enhancement could add a **conditional write** pattern:
`submitCommand(command, expectedCurrentStatus)` — but this is out of scope for this phase.

### 14.5 Phase 10: Integration Tests for Transition Enforcement

**Goal:** End-to-end tests proving that invalid transitions are rejected.

```java
@Test
void rejectCompletedJobTransitionToPending(Vertx vertx, VertxTestContext ctx) {
    // 1. Create job via HTTP
    // 2. Update status to COMPLETED via HTTP
    // 3. Attempt to update status to PENDING via HTTP
    // 4. Assert 409 Conflict with correct error body
    // 5. Assert job status is still COMPLETED
}

@Test  
void rejectDeregisteredAgentTransitionToHealthy(Vertx vertx, VertxTestContext ctx) {
    // Similar pattern for agents
}

@Test
void rejectCompletedAssignmentReassignment(Vertx vertx, VertxTestContext ctx) {
    // Similar pattern for job assignments  
}

@Test
void rejectDeletedRouteReactivation(Vertx vertx, VertxTestContext ctx) {
    // Similar pattern for routes
}
```

### 14.6 Migration Phase Summary (Updated)

| Phase | Domain | Type | Scope |
|---|---|---|---|
| **1** | `SystemMetadataCommand` | Sealed records | 2 records, 3 files |
| **2** | `TransferJobCommand` | Sealed records | 4 records, 4 files |
| **3** | `AgentCommand` | Sealed records | 5 records, 3 files |
| **4** | `RouteCommand` | Sealed records | 6 records, 3 files |
| **5** | `JobQueueCommand` | Sealed records | 6 records, 3 files |
| **6** | `JobAssignmentCommand` | Sealed records | 7 records, 4 files |
| **7** | All status enums | Transition infrastructure | 4 enums + exception class |
| **8** | `QuorusStateStore` | Query methods | 4 one-line methods |
| **9** | HTTP handlers/services | Pre-commit validation | 6-7 handler files |
| **10** | Integration tests | Transition rejection tests | 4+ test classes |

Phases 1–6 and 7–8 can proceed in parallel. Phase 9 depends on both 7 and 8.
Phase 10 depends on 9.

---

## 15. Future Consideration: Type-Safe State Encoding

Once sealed records are in place (Phases 1–6) and transition validation is enforced (Phases 7–10),
a further evolution is possible: encoding the state directly into the type system so that invalid
transitions are **impossible to express** at compile time.

### 15.1 Concept — Sealed State Types

Instead of a `TransferJobSnapshot` with a `TransferStatus` field that can be any value:

```java
public sealed interface TransferJobState {
    String jobId();

    record Pending(String jobId, TransferRequest request)
            implements TransferJobState {}

    record InProgress(String jobId, TransferRequest request, Instant startedAt)
            implements TransferJobState {}

    record Completed(String jobId, TransferRequest request, Instant startedAt,
                     Instant completedAt, String checksum)
            implements TransferJobState {}

    record Failed(String jobId, TransferRequest request, Instant startedAt,
                  Instant failedAt, String errorMessage, Throwable cause)
            implements TransferJobState {}

    record Paused(String jobId, TransferRequest request, Instant startedAt,
                  Instant pausedAt)
            implements TransferJobState {}

    record Cancelled(String jobId, TransferRequest request, Instant cancelledAt)
            implements TransferJobState {}
}
```

Transitions become methods that return the target type:

```java
record Pending(...) implements TransferJobState {
    InProgress start(Instant startedAt) {
        return new InProgress(jobId, request, startedAt);
    }
    Cancelled cancel(Instant cancelledAt) {
        return new Cancelled(jobId, request, cancelledAt);
    }
    // No complete() method — impossible to complete from Pending
}
```

### 15.2 Why This Is Deferred

This is a **major domain model change** that would cascade through:
- `QuorusStateStore` state maps (`Map<String, TransferJobState>` instead of `Map<String, TransferJobSnapshot>`)
- Snapshot serialization/deserialization
- Protobuf message structure (would need new proto messages)
- All HTTP response serialization
- All tests

The `canTransitionTo()` approach (Phase 7–10) achieves 90% of the safety with 10% of the effort.
Type-safe states are a v2 evolution if the runtime validation proves insufficient.

### 15.3 Scope Boundary

**In scope (Phases 7–10):** Runtime transition validation via `canTransitionTo()` + pre-commit
checks. Invalid transitions return 409 Conflict. Commands that pass validation are guaranteed
to be valid when applied.

**Out of scope (future):** Compile-time enforcement via sealed state types. The state machine
stores entities as enum-tagged snapshots, and transitions are validated at runtime. This is
the pragmatic boundary for the current refactoring effort.

---

## Appendix A: Current Command Class Structures

### A.1 TransferJobCommand

```
Fields: type (enum), jobId (String), transferJob (TransferJob|null), status (TransferStatus|null), bytesTransferred (Long|null)
Variants: CREATE, UPDATE_STATUS, UPDATE_PROGRESS, DELETE
Factory methods: create(), updateStatus(), updateProgress(), delete()
Constructor: private (5 params → sets unused to null)
```

### A.2 AgentCommand

```
Fields: type (enum), agentId (String), agentInfo (AgentInfo|null), newStatus (AgentStatus|null), newCapabilities (AgentCapabilities|null), timestamp (Instant)
Variants: REGISTER, DEREGISTER, UPDATE_STATUS, UPDATE_CAPABILITIES, HEARTBEAT  
Factory methods: register(), deregister(), updateStatus(), updateCapabilities(), heartbeat() (2 overloads)
Constructors: private (5 params), package-private (6 params with timestamp — used by codec)
Dead convenience methods: isRegister(), isDeregister(), isUpdateStatus(), isUpdateCapabilities(), isHeartbeat()
```

### A.3 SystemMetadataCommand

```
Fields: type (enum), key (String), value (String|null)
Variants: SET, DELETE
Factory methods: set(), delete()
Constructor: private (3 params)
```

### A.4 JobAssignmentCommand

```
Fields: type (enum), assignmentId (String), jobAssignment (JobAssignment|null), newStatus (JobAssignmentStatus|null), reason (String|null), timestamp (Instant)
Variants: ASSIGN, ACCEPT, REJECT, UPDATE_STATUS, TIMEOUT, CANCEL, REMOVE
Factory methods: assign(), accept(), reject(), updateStatus(), timeout(), cancel(), remove()
Constructors: private (5 params), package-private (6 params with timestamp — used by codec)
Validation: validateCommand() with per-variant switch
Helper: generateAssignmentId(jobId, agentId)
```

### A.5 JobQueueCommand

```
Fields: type (enum), jobId (String), queuedJob (QueuedJob|null), newPriority (JobPriority|null), reason (String|null), timestamp (Instant)
Variants: ENQUEUE, DEQUEUE, PRIORITIZE, REMOVE, EXPEDITE, UPDATE_REQUIREMENTS
Factory methods: enqueue(), dequeue(), prioritize(), remove(), expedite(), updateRequirements()
Constructors: private (5 params), package-private (6 params with timestamp — used by codec)
Validation: validateCommand() with per-variant switch
```

### A.6 RouteCommand

```
Fields: type (enum), routeId (String), routeConfiguration (RouteConfiguration|null), newStatus (RouteStatus|null), reason (String|null), timestamp (Instant)
Variants: CREATE, UPDATE, DELETE, SUSPEND, RESUME, UPDATE_STATUS
Factory methods: create(), update(), delete(), suspend(), resume(), updateStatus()
Constructors: private (5 params), package-private (6 params with timestamp — used by codec)
```

---

## Appendix B: Caller Inventory

### B.1 Production Callers (HTTP Handlers + Services)

| File | Factory Calls | Module |
|---|---|---|
| `TransferHandler.java:66,149` | `TransferJobCommand.create()`, `.delete()` | quorus-controller |
| `JobStatusHandler.java:72,77` | `JobAssignmentCommand.updateStatus()`, `TransferJobCommand.updateProgress()` | quorus-controller |
| `AgentRegistrationHandler.java:62` | `AgentCommand.register()` | quorus-controller |
| `HeartbeatHandler.java:87,88` | `AgentCommand.heartbeat()` (both overloads) | quorus-controller |
| `JobAssignmentHandler.java:81,143,164,196,220,238` | `JobAssignmentCommand.assign/accept/reject/updateStatus/cancel/remove()` | quorus-controller |
| `JobAssignmentService.java:119,191,199,240,286,295` | `JobQueueCommand.enqueue/dequeue/remove()`, `JobAssignmentCommand.assign/updateStatus/cancel()` | quorus-controller |
| `RouteHandler.java:84,171,208` | `RouteCommand.create/update/delete()` | quorus-controller |
| `AgentRegistryService.java:84,150,213` | `AgentCommand.register/deregister/updateCapabilities()` | quorus-api (legacy) |
| `HeartbeatProcessor.java:353,413` | `AgentCommand.heartbeat/updateStatus()` | quorus-api (legacy) |

### B.2 `.getType()` Usages (Must Be Removed/Updated)

| Location | Count | Context |
|---|---|---|
| `QuorusStateStore.java` (switch dispatch) | 6 | `switch (command.getType())` in each `apply*Command` |
| `QuorusStateStore.java` (logging) | 6 | `logger.debug("..., type={}", command.getType())` |
| `TransferCodec.java` | 2 | `toProto`/`fromProto` type mapping |
| `AgentCodec.java` | 2 | `toProto`/`fromProto` type mapping |
| `SystemMetadataCodec.java` | 2 | `toProto`/`fromProto` type mapping |
| `JobAssignmentCodec.java` | 2 | `toProto`/`fromProto` type mapping |
| `JobQueueCodec.java` | 2 | `toProto`/`fromProto` type mapping |
| `RouteCodec.java` | 2 | `toProto`/`fromProto` type mapping |
| Test files (assertions) | ~33 | `assertEquals(Type.X, result.getType())` |

### B.3 Package-Private Constructor Usages in Codecs

| Codec | Constructor Call | Purpose |
|---|---|---|
| `AgentCodec.fromProto()` | `new AgentCommand(type, agentId, agentInfo, newStatus, newCapabilities, timestamp)` | Deserialization with explicit timestamp |
| `JobAssignmentCodec.fromProto()` | `new JobAssignmentCommand(type, assignmentId, assignment, newStatus, reason, timestamp)` | Deserialization with explicit timestamp |
| `JobQueueCodec.fromProto()` | `new JobQueueCommand(type, jobId, queuedJob, newPriority, reason, timestamp)` | Deserialization with explicit timestamp |
| `RouteCodec.fromProto()` | `new RouteCommand(type, routeId, routeConfig, newStatus, reason, timestamp)` | Deserialization with explicit timestamp |
| `TransferCodec.fromProto()` | Uses factory methods | — |
| `SystemMetadataCodec.fromProto()` | Uses factory methods | — |
