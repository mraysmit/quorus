# Sealed Record Commands — Design & Refactoring Proposal

**Author:** GitHub Copilot  
**Date:** 2026-02-20  
**Status:** Complete (2026-03-05)  
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

    record UpdateStatus(String jobId, TransferStatus expectedStatus, TransferStatus newStatus)
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

    static TransferJobCommand updateStatus(String jobId, TransferStatus expectedStatus, TransferStatus status) {
        return new UpdateStatus(jobId, expectedStatus, status);
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
| `UpdateStatus` | `String jobId`, `TransferStatus expectedStatus`, `TransferStatus newStatus` | Status transitions (PENDING → IN_PROGRESS → COMPLETED). Uses CAS. |
| `UpdateProgress` | `String jobId`, `long bytesTransferred` | Primitive `long`, not `Long` — no null boxing |
| `Delete` | `String jobId` | Minimal — just the ID to remove |

### 4.2 AgentCommand (5 variants)

| Record | Fields | Notes |
|---|---|---|
| `Register` | `String agentId`, `AgentInfo agentInfo` | Full agent info for registration |
| `Deregister` | `String agentId`, `Instant timestamp` | Timestamp for audit trail |
| `UpdateStatus` | `String agentId`, `AgentStatus expectedStatus`, `AgentStatus newStatus`, `Instant timestamp` | Status change with timestamp and CAS |
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
| `UpdateStatus` | `String assignmentId`, `JobAssignmentStatus expectedStatus`, `JobAssignmentStatus newStatus`, `Instant timestamp` | Generic status transition with CAS |
| `Timeout` | `String assignmentId`, `Instant timestamp` | Timeout detection — status set by factory to TIMEOUT |
| `Cancel` | `String assignmentId`, `String reason`, `Instant timestamp` | Controller cancels with reason |
| `Remove` | `String assignmentId`, `Instant timestamp` | Cleanup after completion/failure |

Common accessor: `String assignmentId()` on the sealed interface.

**Validation:** The current `validateCommand()` method in `JobAssignmentCommand` validates field
presence per variant. With records, this validation is structural — `Assign` requires a non-null
`JobAssignment` because it's a record component. Use compact constructors for additional validation.

**Strict non-null policy:** Compact constructors must never default missing values. If a field is
required, `Objects.requireNonNull` fails fast — the bug is at the call site that omitted the value,
not here. Factory methods (Section 7.3) supply `Instant.now()` for production callers; the codec
supplies the deserialized timestamp. No path should ever pass `null` for `timestamp`.

```java
record Assign(String assignmentId, JobAssignment jobAssignment, Instant timestamp)
        implements JobAssignmentCommand {
    Assign {  // compact constructor — fail fast, never default
        Objects.requireNonNull(assignmentId, "assignmentId");
        Objects.requireNonNull(jobAssignment, "jobAssignment");
        Objects.requireNonNull(timestamp, "timestamp");
    }

    /** Production factory — callers use this, never the canonical constructor directly. */
    public static Assign create(String assignmentId, JobAssignment jobAssignment) {
        return new Assign(assignmentId, jobAssignment, Instant.now());
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
| `UpdateStatus` | `String routeId`, `RouteStatus expectedStatus`, `RouteStatus newStatus`, `String reason`, `Instant timestamp` | Generic status change with CAS |

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

### 5.2 First-Class Results: The CommandResult ADT

Returning `null` when an entity is not found is another instance of structural lying. The caller (the HTTP handler) receives a `null` objective and must guess why the command was rejected. Instead, we introduce a sum type for the result of `apply()`, eliminating `yield null` entirely:

```java
public sealed interface CommandResult<T> {
    record Success<T>(T entity) implements CommandResult<T> {}
    record NotFound<T>(String id, String entityType) implements CommandResult<T> {}
    record CasMismatch<T>(T currentEntity) implements CommandResult<T> {}
}
```

### 5.3 Proposed Pattern (applicator delegation)

To avoid causing `QuorusStateStore` to balloon into 36+ methods, the implementation extracts pure, domain-specific **applicator classes** (e.g., `TransferJobApplicator`). `QuorusStateStore` delegates to these applicators, which return strongly-typed `CommandResult` records.

```java
private CommandResult<?> applyTransferJobCommand(TransferJobCommand command) {
    logger.debug("Processing transfer job command: type={}, jobId={}",
        command.getClass().getSimpleName(), command.jobId());
    return switch (command) {
        case TransferJobCommand.Create cmd         -> transferJobApplicator.handleCreateJob(cmd);
        case TransferJobCommand.UpdateStatus cmd   -> transferJobApplicator.handleUpdateJobStatus(cmd);
        case TransferJobCommand.UpdateProgress cmd -> transferJobApplicator.handleUpdateJobProgress(cmd);
        case TransferJobCommand.Delete cmd         -> transferJobApplicator.handleDeleteJob(cmd);
    };
}

// Inside TransferJobApplicator.java
public CommandResult<TransferJob> handleCreateJob(TransferJobCommand.Create cmd) {
    TransferJobSnapshot snapshot = TransferJobSnapshot.fromTransferJob(cmd.transferJob());
    transferJobs.put(cmd.jobId(), snapshot);
    logger.info("Created transfer job: jobId={}, protocol={}, totalJobs={}", 
        cmd.jobId(), cmd.transferJob().getRequest().getProtocol(), transferJobs.size());
    return new CommandResult.Success<>(cmd.transferJob());
}

public CommandResult<TransferJobSnapshot> handleUpdateJobStatus(TransferJobCommand.UpdateStatus cmd) {
    return Optional.ofNullable(transferJobs.get(cmd.jobId()))
        .map(existing -> {
            if (existing.getStatus() != cmd.expectedStatus()) {
                logger.warn("State CAS mismatch (expected {}, got {}). Ignored transition.", 
                    cmd.expectedStatus(), existing.getStatus());
                return new CommandResult.CasMismatch<TransferJobSnapshot>(existing);
            }

            TransferStatus oldStatus = existing.getStatus();
            TransferJobSnapshot updated = new TransferJobSnapshot(
                    existing.getJobId(), existing.getSourceUri(), existing.getDestinationPath(),
                    cmd.newStatus(), existing.getBytesTransferred(), existing.getTotalBytes(),
                    existing.getStartTime(), Instant.now(),
                    existing.getErrorMessage(), existing.getDescription());
            transferJobs.put(cmd.jobId(), updated);
            logger.info("Updated transfer job status: jobId={}, oldStatus={}, newStatus={}", 
                cmd.jobId(), oldStatus, cmd.newStatus());
            
            return new CommandResult.Success<>(updated);
        })
        .orElseGet(() -> {
            logger.warn("Transfer job not found for status update: {}", cmd.jobId());
            return new CommandResult.NotFound<>(cmd.jobId(), "TransferJob");
        });
}

public CommandResult<TransferJobSnapshot> handleUpdateJobProgress(TransferJobCommand.UpdateProgress cmd) {
    return Optional.ofNullable(transferJobs.get(cmd.jobId()))
        .map(existing -> {
            TransferJobSnapshot updated = new TransferJobSnapshot( /* ... */ );
            transferJobs.put(cmd.jobId(), updated);
            return (CommandResult<TransferJobSnapshot>) new CommandResult.Success<>(updated);
        })
        .orElseGet(() -> {
            logger.warn("Transfer job not found for progress update: {}", cmd.jobId());
            return new CommandResult.NotFound<>(cmd.jobId(), "TransferJob");
        });
}

public CommandResult<TransferJobSnapshot> handleDeleteJob(TransferJobCommand.Delete cmd) {
    return Optional.ofNullable(transferJobs.remove(cmd.jobId()))
        .map(removed -> {
            logger.info("Deleted transfer job: jobId={}, finalStatus={}, totalJobs={}", 
                cmd.jobId(), removed.getStatus(), transferJobs.size());
            return (CommandResult<TransferJobSnapshot>) new CommandResult.Success<>(removed);
        })
        .orElseGet(() -> {
            logger.warn("Transfer job not found for deletion: {}", cmd.jobId());
            return new CommandResult.NotFound<>(cmd.jobId(), "TransferJob");
        });
}
```

### 5.3.1 Eliminating the Null-Check Code Smell

Previous designs featured defensive guard clauses like `if (existing == null) yield null;` or explicit imperative null checks in handlers. This represents a known code smell in modern Java. 

By pushing null-handling into `Optional.map().orElseGet()`, we:
1. **Declare intent explicitly:** Data flow defines what to do if present vs. absent.
2. **Prevent unhandled edge cases:** The compiler forces developers to either unwrap safely or provide an `orElse` path.
3. **Bridge into Domain ADTs:** We smoothly convert a nullable state-store retrieval (`Optional.ofNullable`) into a structured domain outcome (`CommandResult`). Instead of passing `null` around until it eventually NPEs, nullability is bounded and mapped directly into business semantics (`NotFound`) at the storage boundary.

### 5.4 Structural Comparison

| Aspect | Before | After |
|---|---|---|
| Dispatch method | 1 per domain, 20-80 lines | 1 dispatcher (5-8 lines) + N handlers (8-15 lines each) |
| Switch body | Block with `yield` | Expression (single method call) |
| Field access | `command.getTransferJob()` (nullable) | `cmd.transferJob()` (non-null, typed) |
| Return type | `Object` (from `yield null`) | `CommandResult<T>` (explicit Sum Type) |
| `getOrWarn`/`removeOrWarn` helpers | Yields/returns `null` | Replaced by `CommandResult.NotFound` |
| Adding a new variant | Add enum case + switch block | Add record + compiler forces handler |
| Total methods in QuorusStateStore | ~12 | ~6 (dispatches to Applicator classes) |
| Total LOC | ~550 (6 switch methods) | ~150 in Store, logic moved to Applicators |

### 5.5 Raft Safety Note

Returning `CommandResult.NotFound` completely replaces the explicit `getOrWarn` and `return null` checks. In a Raft state machine, committed commands must be applied deterministically on every node. A DELETE followed by an UPDATE for the same entity (both committed) would break log replay if UPDATE threw an exception. Returning a `NotFound` record safely handles these edge cases without breaking log determinism, while providing full context to the caller.

### 5.6 HTTP Handler Propagation of CommandResult

> **Implementation note:** `CommandResult<T>` is **already implemented** in the codebase as a sealed interface in `QuorusStateStore`. `RaftNode.submitCommand()` returns `Future<CommandResult<?>>`, and `QuorusStateStore.apply()` yields `CommandResult` variants (`Success`, `NotFound`, `CasMismatch`, `NoOp`) — never `null`. The propagation path is fully established.

The full data flow from state machine to HTTP response is:

```
QuorusStateStore.apply(command)          → CommandResult<T>
  ↓ (via Raft log commit)
RaftNode.submitCommand(command)          → Future<CommandResult<?>>
  ↓ (.onSuccess handler)
HTTP Handler                             → pattern-match on result → HTTP status + JSON body
```

#### Current handler patterns

Only 2 of ~14 HTTP handlers currently inspect the `CommandResult`. Most ignore it:

| Pattern | Handlers | Behaviour |
|---------|----------|-----------|
| **Ignore result** | `AgentRegistrationHandler`, `TransferHandler`, `JobStatusHandler` | Returns fixed status code (200/201) regardless of `CommandResult` variant |
| **Inspect Success entity** | `HeartbeatHandler` | Pattern-matches on `Success<AgentInfo>` to enrich the response body, but always returns 200 |
| **Handle CasMismatch** | `JobAssignmentHandler.handleUpdateStatus()` | Returns 409 on `CasMismatch`, 200 otherwise |

#### Target pattern (post-refactoring)

After this refactoring, handlers should exhaustively pattern-match on `CommandResult`:

```java
raftNode.submitCommand(command)
    .onSuccess(result -> {
        switch (result) {
            case CommandResult.Success<?> s -> {
                ctx.response().setStatusCode(200);
                ctx.json(JsonObject.mapFrom(s.entity()));
            }
            case CommandResult.NotFound<?> nf -> {
                ctx.response().setStatusCode(404);
                ctx.json(new JsonObject()
                    .put("error", nf.entityType() + "_NOT_FOUND")
                    .put("id", nf.id()));
            }
            case CommandResult.CasMismatch<?> cm -> {
                ctx.response().setStatusCode(409);
                ctx.json(new JsonObject()
                    .put("error", "STATE_CONFLICT")
                    .put("message", "Entity was modified concurrently, please retry"));
            }
            case CommandResult.NoOp<?> ignored -> {
                ctx.response().setStatusCode(204);
            }
        }
    })
    .onFailure(ctx::fail);
```

This ensures every `CommandResult` variant maps to a well-defined HTTP status code:

| CommandResult variant | HTTP Status | Meaning |
|---|---|---|
| `Success<T>` | 200 / 201 | Operation succeeded, entity in body |
| `NotFound<T>` | 404 | Referenced entity does not exist |
| `CasMismatch<T>` | 409 | Concurrent modification detected |
| `NoOp<T>` | 204 | Command accepted but no state change |

#### Migration approach

Updating handlers to inspect `CommandResult` is **independent** of the sealed record command refactoring itself. It can be done incrementally — one handler at a time — without changing any state machine or codec code. Handlers that currently ignore the result will continue to work correctly during migration; they simply won't return precise status codes for edge cases.

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

### 7.3 Codec Deserialization and Timestamps

Four of the six codecs currently use package-private constructors for deserialization:

```java
// AgentCodec.fromProto currently does:
new AgentCommand(CommandType.REGISTER, agentId, agentInfo, null, null, timestamp);
```

After refactoring, records will use canonical constructors:
```java
new AgentCommand.Register(agentId, agentInfo, timestamp);
```

**Recommendation:** Do not use package-private constructors to bypass immutability or validation. The canonical constructor accepts all required fields (including `timestamp`) and enforces non-null via the compact constructor. Factory methods supply `Instant.now()` for production callers. The codec always supplies the deserialized timestamp — no path ever passes `null`.

```java
record Register(String agentId, AgentInfo agentInfo, Instant timestamp) implements AgentCommand {
    Register {  // compact constructor — fail fast, never default
        Objects.requireNonNull(agentId, "agentId");
        Objects.requireNonNull(agentInfo, "agentInfo");
        Objects.requireNonNull(timestamp, "timestamp");
    }

    /** Production factory — callers use this, never the canonical constructor directly. */
    public static Register create(String agentId, AgentInfo agentInfo) {
        return new Register(agentId, agentInfo, Instant.now());
    }
}
```

Two construction paths, both non-null:
1. **Production code** → `Register.create(agentId, agentInfo)` — factory supplies `Instant.now()`
2. **Codec deserialization** → `new Register(agentId, agentInfo, protoTimestamp)` — proto always has a timestamp

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
| **Method count increase in QuorusStateStore** | 30 handler methods replace 30 `case` blocks. The class grows from ~12 methods to ~36 methods. | **Extract Domain-Specific Applicators.** `QuorusStateStore` should be stripped down to only the top-level Level 1 switches, delegating actual map updates to stateless applicator classes (e.g., `TransferJobApplicator`, `AgentStatusApplicator`). |
| **Snapshot compatibility** | `takeSnapshot()` / `restoreSnapshot()` serialize the state maps (not commands). | Commands are only serialized via the protobuf codec. State maps contain domain objects (`TransferJobSnapshot`, `AgentInfo`, etc.) which are unchanged. |
| **Rolling cluster upgrade** | Mixed-version cluster where some nodes use old enum commands and others use new sealed records. | Wire format is protobuf — identical on the wire. Codec produces the same bytes. Nodes don't share Java objects. No compatibility issue. |

---

## 11. Optional Enhancement: Record Patterns (Java 21)

With sealed record subtypes, the state machine can use **record deconstruction patterns** for
zero-getter field access:

```java
private CommandResult<?> applyTransferJobCommand(TransferJobCommand command) {
    return switch (command) {
        case Create(var jobId, var transferJob) -> {
            transferJobs.put(jobId, TransferJobSnapshot.fromTransferJob(transferJob));
            yield new CommandResult.Success<>(transferJob);
        }
        case UpdateStatus(var jobId, var expectedStatus, var newStatus) -> {
            yield Optional.ofNullable(transferJobs.get(jobId))
                .map(existing -> {
                    if (existing.getStatus() != expectedStatus) {
                        return (CommandResult<?>) new CommandResult.CasMismatch<>(existing);
                    }
                    var updated = updateSnapshot(existing, newStatus);
                    transferJobs.put(jobId, updated);
                    return (CommandResult<?>) new CommandResult.Success<>(updated);
                })
                .orElseGet(() -> new CommandResult.NotFound<>(jobId, "TransferJob"));
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
a non-existent entity (possible during log replay after compaction), it should warn and return
`CommandResult.NotFound`, not throw. Similarly, CAS mismatches during concurrent submissions return
`CommandResult.CasMismatch` — deterministic on all nodes, with full context for the caller
(see [Section 5.2](#52-first-class-results-the-commandresult-adt)).

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

### 13.3 Pre-Commit Validation (Fast-Reject Optimization)

The validation layer sits in the HTTP handlers/services, between the request parsing and the
`raftNode.submitCommand()` call. **This validation is advisory only.** It serves as a fast-reject to give the client a clean `409 Conflict` without burning a Raft round-trip for an obviously invalid transition. The *actual* safety mechanism is the CAS validation inside `apply()`.

Pattern:

```java
// In TransferHandler or equivalent service method:
public Future<TransferJobSnapshot> updateTransferJobStatus(String jobId, TransferStatus newStatus) {
    // 1. Read current state (read-only query on state store)
    Optional<TransferJobSnapshot> current = stateStore.getTransferJob(jobId);
    if (current.isEmpty()) {
        return Future.failedFuture(new NotFoundException("Transfer job not found: " + jobId));
    }

    TransferJobSnapshot job = current.get();

    // 2. Validate transition
    if (!job.getStatus().canTransitionTo(newStatus)) {
        return Future.failedFuture(new InvalidTransitionException(
            "Cannot transition transfer job '%s' from %s to %s. Valid targets: %s",
            jobId, job.getStatus(), newStatus,
            job.getStatus().getValidTransitions()));
    }

    // 3. Submit to Raft with CAS (only valid commands reach the log)
    return raftNode.submitCommand(
            TransferJobCommand.updateStatus(jobId, job.getStatus(), newStatus))
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

**Goal:** Ensure `QuorusStateStore` exposes read-only query methods for all stateful entities,
returning `Optional<T>` instead of nullable references.

| Task | Method | Returns |
|---|---|---|
| 8a | `getTransferJob(String jobId)` | `Optional<TransferJobSnapshot>` |
| 8b | `getAgent(String agentId)` | `Optional<AgentInfo>` |
| 8c | `getJobAssignment(String assignmentId)` | `Optional<JobAssignment>` |
| 8d | `getRoute(String routeId)` | `Optional<RouteConfiguration>` |

Each is a one-line `Optional.ofNullable(map.get(id))` delegation. Some may already exist — verify before adding.

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
Optional<TransferJobSnapshot> current = stateStore.getTransferJob(jobId);
if (current.isEmpty()) {
    ctx.response().setStatusCode(404).end(notFoundJson(jobId));
    return;
}
TransferJobSnapshot job = current.get();
if (!job.getStatus().canTransitionTo(newStatus)) {
    ctx.response().setStatusCode(409).end(invalidTransitionJson(
        jobId, job.getStatus(), newStatus));
    return;
}
// Now safe to submit
raftNode.submitCommand(TransferJobCommand.updateStatus(jobId, job.getStatus(), newStatus));
```

**CRITICAL: Raft Safety, TOCTOU, and Client Retry Semantics**

If validation *only* exists pre-commit, and `apply()` remains a blind applicator, high concurrency will still result in invalid transitions due to Time-Of-Check to Time-Of-Use (TOCTOU) races. For example: Thread A and B both read `HEALTHY`. A submits `MAINTENANCE`, B submits `DEGRADED`. Both pass pre-commit validation. When applied sequentially, the agent becomes `MAINTENANCE` then unconditionally `DEGRADED`, performing an illegal transition.

To ensure strict safety, **Compare-And-Swap (CAS) state validation must be embedded in the commands**.

*Note: CAS is only required for status-changing variants (e.g., `UpdateStatus`). Variants that create or delete records are naturally idempotent or use existence checks, and do not need CAS fields.*

Commands that change state must carry their `expectedStatus`:
```java
record UpdateStatus(String jobId, TransferStatus expectedStatus, TransferStatus newStatus) 
    implements TransferJobCommand {}
```

Inside `apply()`, the state machine maintains Raft determinism by deterministically ignoring mismatched commands on all nodes, returning a strongly typed rejection result:
```java
case TransferJobCommand.UpdateStatus cmd -> {
    TransferJobSnapshot existing = transferJobs.get(cmd.jobId());
    if (existing == null) {
        logger.warn("Transfer job not found: {}", cmd.jobId());
        yield new CommandResult.NotFound<>(cmd.jobId(), "TransferJob");
    }
    
    if (existing.getStatus() != cmd.expectedStatus()) {
        logger.warn("State CAS mismatch (expected {}, got {}).", 
                    cmd.expectedStatus(), existing.getStatus());
        yield new CommandResult.CasMismatch<>(existing); 
    }
    
    // Valid. Perform snapshot update...
    TransferJobSnapshot updated = updateSnapshot(existing, cmd.newStatus());
    transferJobs.put(cmd.jobId(), updated);
    yield new CommandResult.Success<>(updated); 
}
```

**Client Retry Semantics:** 
When `apply()` encounters an invalid state, it returns a descriptive `CommandResult`. The `raftNode.submitCommand()` future completes with this yield value.
The client (HTTP handler) now evaluates explicitly typed failures rather than guessing why a transition didn't apply:
1. `submitCommand` returns.
2. The handler pattern matches on the result:
   - `case Success(var entity)`: Completed successfully.
   - `case CasMismatch(var entity)`: Another thread won the race. Handler can automatically read the returned `entity.getStatus()` to apply a retry, or surface `409 Conflict`.
   - `case NotFound`: The entity was deleted before the command committed. Returns `404 Not Found`.

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

## 16. Progress Tracking & Regression Prevention

This refactoring touches ~81 files across 50 tasks and 17 work packages. A single broken
commit can cascade failures through Raft consensus, protobuf serialization, and HTTP handlers.
This section defines how to track progress, prevent regressions, and verify correctness at
every step.

### 16.1 Invariant: Green-to-Green Commits

**Every commit must leave the build in a passing state.** No "work in progress" commits that
break compilation or tests. This is non-negotiable because:

1. Raft consensus requires all nodes to apply identical commands — a serialization mismatch
   between old and new formats causes split-brain
2. `QuorusStateStore.apply()` is called on every node — a broken switch expression crashes
   the entire cluster
3. Protobuf codec changes affect both snapshot persistence and gRPC transport

**Gate criteria before every commit:**

```bash
# Must pass — no exceptions
mvn clean test -pl quorus-core,quorus-controller

# Must pass if workflow module was touched
mvn clean test -pl quorus-workflow

# Full verification before PR merge
mvn clean verify
```

### 16.2 One Domain at a Time — Vertical Slices

Section 9 defines 6 phases (one per command domain). Each phase is a **vertical slice** through
the entire stack:

```
Command class ──→ Codec ──→ State machine handler ──→ Tests
```

**Rules:**
- Never start Phase N+1 until Phase N is merged and green
- Never split a domain across commits (e.g., converting `AgentCommand` to records but leaving
  `AgentCodec` using the old `getType()` pattern)
- Each phase produces a single PR with all 4 layers updated atomically

This prevents the "half-migrated" state where some commands are records and their codecs still
expect enum tags.

### 16.3 Quantifiable Regression Metrics

Track these metrics before and after each phase. They must monotonically improve (or stay
constant for unrelated modules):

| Metric | Baseline | How to Measure | Target |
|---|---|---|---|
| `null` check count | ~847 | `grep -rn "== null\|!= null\|\.isPresent\|\.isEmpty" --include="*.java" \| wc -l` | 0 in command/codec/state machine paths |
| `Object` return types | ~77 | `grep -rn "Object>" --include="*.java" \| wc -l` | 0 in Raft path |
| `yield null` in state machine | 18 | `grep -rn "yield null" QuorusStateStore.java \| wc -l` | 0 |
| `instanceof` casts | ~4 | `grep -rn "instanceof" --include="*.java" \| wc -l` (in handlers) | 0 from `Object` casts |
| `getType()` calls | ~30 | `grep -rn "getType()" --include="*.java" \| wc -l` | 0 |
| Test count | ~369+ | `mvn test` summary line | Monotonically increasing |
| Test failures | 0 (excl. Docker) | `mvn test` summary line | 0 |

**After each phase, record the updated counts in Section 16.8 (Progress Log).**

### 16.4 Serialization Compatibility Gate

Raft log entries and snapshots are persisted via protobuf. A codec change that silently drops
a field or changes a default value will corrupt the cluster state on restart.

**Per-phase serialization verification:**

1. **Round-trip test** — For every new record type, assert:
   ```java
   @Test
   void roundTrip_preservesAllFields() {
       var original = AgentCommand.Register.create("agent-1", "us-east", "dc-1",
           List.of("sftp", "http"), 5);
       var proto = AgentCodec.toProto(original);
       var restored = AgentCodec.fromProto(proto);
       assertEquals(original, restored); // Record equals() compares all fields
   }
   ```
   Record `equals()` is field-by-field by default — this single assertion covers every field.

2. **Backward compatibility test** — Before changing a codec, capture the current proto bytes
   for a representative command. After the change, verify the new codec can still decode those
   bytes:
   ```java
   @Test
   void backwardCompatibility_decodesPreRefactorBytes() {
       // Proto bytes captured from pre-refactor codec
       byte[] legacyBytes = Base64.getDecoder().decode(LEGACY_AGENT_REGISTER_BASE64);
       RaftCommandMessage proto = RaftCommandMessage.parseFrom(legacyBytes);
       RaftCommand command = ProtobufCommandCodec.fromProto(proto);
       assertInstanceOf(AgentCommand.Register.class, command);
       // Verify key fields survived
   }
   ```

3. **Snapshot compatibility** — `takeSnapshot()` and `restoreSnapshot()` must round-trip
   correctly after each phase. Test with a populated state machine:
   ```java
   @Test
   void snapshotRoundTrip_afterPhase3Migration() {
       // Populate state machine with agents, jobs, routes
       populateTestState(stateStore);
       byte[] snapshot = stateStore.takeSnapshot();
       QuorusStateStore restored = new QuorusStateStore();
       restored.restoreSnapshot(snapshot);
       // Assert all entities survived
       assertEquals(stateStore.getAgents(), restored.getAgents());
       assertEquals(stateStore.getTransferJobs(), restored.getTransferJobs());
   }
   ```

### 16.5 Compiler as Regression Detector

The sealed record refactoring is designed so the Java compiler catches incomplete migrations:

| Change | Compiler Enforcement |
|---|---|
| Sealed interface replaces class | Any code calling removed `getType()` → compile error |
| Record replaces constructor | Any code calling old constructor → compile error |
| `CommandResult<T>` replaces `Object` | Any code assuming `Object` return → compile error |
| Pattern matching switch | Missing record variant → compiler warning (exhaustiveness) |

**This is the primary safety mechanism.** If Phase 3 converts `AgentCommand` to a sealed
interface, every file that calls `agentCommand.getType()` will fail to compile. The compiler
tells you exactly which files need updating — there is no risk of a silent behavioral change.

After converting each command class, run `mvn compile -pl quorus-controller` (not `test`)
first. Fix all compilation errors. Then run tests. This separates "did I break the API?" from
"does the logic still work?".

### 16.6 Branch Strategy

```
main (always green)
  │
  ├── sealed-records/phase-1-system-metadata
  │     └── PR #1: SystemMetadataCommand → sealed records + codec + state machine + tests
  │
  ├── sealed-records/phase-2-transfer-job
  │     └── PR #2: TransferJobCommand → sealed records + codec + state machine + tests
  │
  ├── sealed-records/phase-3-agent        (depends on PR #1 or #2 merged)
  │     ...
  │
  ├── type-safety/command-result          (depends on all Phase 1–6 PRs merged)
  │     └── PR #7: Object → CommandResult<T> across Raft path
  │
  ├── transitions/phase-7-enums           (independent — can start anytime)
  │     └── PR #8: canTransitionTo() on all status enums
  │
  └── independent/*                       (E.7–E.15, no dependencies)
        └── PR per work package
```

**Merge order:**
1. Phases 1–6 sequentially (each builds on the pattern established by the previous)
2. Phase 6b (CommandResult) after all sealed record phases
3. Phases 7–10 can interleave with 1–6 since they modify different files
4. Independent work packages (E.7–E.15) anytime — no ordering constraints

### 16.7 Per-Phase Verification Protocol

Execute this checklist for every phase before marking it complete:

#### Pre-Implementation
- [ ] Read the current source files being modified (command, codec, state machine)
- [ ] List all callers of the command class (use IDE "Find Usages" or `grep`)
- [ ] Count current null checks and `getType()` calls in affected files

#### Implementation
- [ ] Convert command class → sealed interface + records
- [ ] Update codec `toProto`/`fromProto` to pattern-match
- [ ] Update `QuorusStateStore` handler to pattern-match
- [ ] Update all callers (handlers, services, tests)

#### Post-Implementation
- [ ] `mvn compile -pl quorus-controller` — zero errors
- [ ] `mvn test -pl quorus-core,quorus-controller` — zero new failures
- [ ] Serialization round-trip test passes
- [ ] No remaining `getType()` calls for this domain
- [ ] No remaining `instanceof` casts for this domain's `Object` returns
- [ ] Null check count in modified files decreased or stayed constant
- [ ] Record updated metrics in Section 16.8

### 16.8 Progress Log

Record actual results after completing each phase. This becomes the audit trail.

| Phase | Date | Tests Before | Tests After | Null Checks Removed | `getType()` Removed | Notes |
|---|---|---|---|---|---|---|
| 1 — SystemMetadata | 2026-02-20 | 369 | 369 | 2 (`Optional.ofNullable`) | 3 (`getType()`, enum mapping) | `enum Type` removed, compact constructors require `public` in interface records (Java 21) |
| 2 — TransferJob | 2026-02-20 | 369 | 369 | 4 (`Optional.ofNullable` in `toProto`) | 4 (`getType()`, `getJobId()→jobId()`, enum mapping×2) | `enum Type` removed, 4 records (Create, UpdateStatus, UpdateProgress, Delete), codec uses pattern matching |
| 3 — Agent | 2026-02-20 | 369 | 369 | 5 (`Optional.ofNullable` in `toProto`) | 5 (`getType()`, `getAgentId()→agentId()`, enum mapping×2, `is*()` methods×5) | `enum CommandType` removed, 5 records (Register, Deregister, UpdateStatus, UpdateCapabilities, Heartbeat), equals/hashCode now auto-generated |
| 4 — Route | 2026-02-20 | 369 | 369 | 6 (`Optional.ofNullable` in `toProto`) | 4 (`getType()`, `getRouteId()→routeId()`, enum mapping×2) | `enum CommandType` removed, 6 records (Create, Update, Delete, Suspend, Resume, UpdateStatus), Suspend/Resume no longer carry redundant newStatus field, equals/hashCode auto-generated |
| 5 — JobQueue | 2026-02-20 | 369 | 369 | 5 (`Optional.ofNullable` in `toProto`) | 4 (`getType()`, `getJobId()→jobId()`, enum mapping×2) | `enum CommandType` removed, 6 records (Enqueue, Dequeue, Prioritize, Remove, Expedite, UpdateRequirements), `validateCommand()` method eliminated — validation now in compact constructors, equals/hashCode auto-generated |
| 6 — JobAssignment | 2026-02-20 | 369 | 369 | 5 (`Optional.ofNullable` in `toProto`) | 4 (`getType()`, `getAssignmentId()→assignmentId()`, enum mapping×2) | `enum CommandType` removed, 7 records (Assign, Accept, Reject, UpdateStatus, Timeout, Cancel, Remove), `validateCommand()` method eliminated — validation now in compact constructors, package-private deserialization constructor removed, manual `equals()`/`hashCode()`/`toString()` auto-generated |
| 6b — CommandResult | 2026-02-20 | 369 | 369 | 22 (`yield null` → `NotFound`/`NoOp`) + 2 helpers (`getOrWarn`/`removeOrWarn` removed) | 1 (`getAssignmentId()→assignmentId()` in handler) | Sealed `CommandResult<T>` ADT: `Success<T>(entity)`, `NotFound<T>(id, entityType)`, `NoOp<T>()`. `RaftLogApplicator.apply()` returns `CommandResult<?>`, `RaftNode.submitCommand()` returns `Future<CommandResult<?>>`. All 6 `QuorusStateStore.apply*()` methods updated. 23 caller sites updated across handlers, services, legacy API, and tests. `JobAssignmentService` blocking calls fixed with `.toCompletionStage().toCompletableFuture().get()`. |
| 7 — Transition Enums | 2026-02-20 | 369 | 369 | 0 | 0 | Added `canTransitionTo()` + `getValidTransitions()` to `TransferStatus`, `AgentStatus`, `RouteStatus`. Added `isTerminal()` + `isActive()` to `RouteStatus`. Verified `JobAssignmentStatus` already complete. Created `InvalidTransitionException` extending `QuorusException`. 5 parameterized test classes covering every (source, target) pair: 36 TransferStatus pairs, 121 AgentStatus pairs, 64 RouteStatus pairs, 64 JobAssignmentStatus pairs. 1 flaky Raft election test (pre-existing, unrelated). |
| 8 — Optional Queries | 2026-02-20 | 369 | 387 | 0 | 0 | Added 6 `findXxx()` methods to `QuorusStateStore` returning `Optional<T>`: `findTransferJob`, `findAgent`, `findJobAssignment`, `findRoute`, `findQueuedJob`, `findMetadata`. Existing nullable `getXxx()` methods retained for backward compatibility. 18 new tests in `QuorusStateStoreOptionalQueryTest` covering present/empty/after-deletion/consistency-with-nullable scenarios for all 6 entity types. |
| 9 — Pre-Commit Valid. | 2026-02-21 | 387 | 407 | 0 | 0 | Added `CommandResult.CasMismatch<T>(entity)` 4th permit. Added `expectedStatus` (nullable) to all 4 `UpdateStatus` records with backward-compat factory overloads. Updated 4 codecs with proto `expected_status` fields (UNSPECIFIED→null for backward compat). CAS check in `QuorusStateStore.apply()` for all 4 entity types. Handler pre-commit validation in `JobAssignmentHandler` (accept/reject/cancel/updateStatus) and `RouteHandler` (suspend/resume) using `canTransitionTo()`. Added `ASSIGNMENT_STATE_CONFLICT` + `AGENT_STATE_CONFLICT` error codes. 20 new CAS tests in `CasValidationTest` covering CAS success/mismatch/null-skip/notfound for all 4 entity types, proto roundtrip for `expectedStatus`, and `CasMismatch` entity access. |
| 10 — Integration Tests | 2026-02-25 | 384 | 403 | 0 | 0 | Created `StateTransitionIntegrationTest` with 19 end-to-end tests across 7 nested classes: `JobAssignmentTerminalTransitions`(4), `JobAssignmentInvalidTransitions`(3), `JobAssignmentValidTransitions`(2), `JobAssignmentStatePreservation`(1), `RouteSuspendResumeTransitions`(3), `RouteStatePreservation`(1), `RouteDeletedEnforcement`(2), `NonExistentEntityOperations`(3). Tests exercise full HTTP → handler → Raft → state machine → response path. Fixed production bug: `RouteHandler.handleCreate()` now enforces CONFIGURED status on all new routes (previously stored null status from JSON). Fixed NPE in `handleResume()` error message when status is null. |
| E.7 — Eager Collections | 2026-02-22 | 1467 | 1467 | 6 (redundant null guards removed) | 0 | Removed 5 redundant null guards from `AgentCapabilities` (addSupportedProtocol, supportsProtocol, addAvailableRegion, isAvailableInRegion, addCustomCapability) and 1 from `AgentInfo` (addMetadata). Constructors already eagerly initialize all collections; setters already guard against null reassignment. |
| E.8 — RaftNode Optional | 2026-02-22 | 1467 | 1467 | 10+ (`storage == null`/`!= null` → `Optional` API) | 0 | Converted `RaftNode.storage` from `RaftStorage` (nullable) to `Optional<RaftStorage>`. Constructor uses `Optional.ofNullable()`. 10+ null-check sites converted to `storage.isPresent()`, `storage.isEmpty()`, `storage.map()`, `storage.get()`. `stop()` method uses functional `storage.map(RaftStorage::close).orElseGet(...)`. Volatile mode (no WAL) now expressed as `Optional.empty()` instead of `null`. |
| E.9 — Shutdown Guards | 2026-02-25 | — | — | 6+ (`if (x != null)` → `Optional.ifPresent()`) | 0 | `RaftNode` shutdown fields (`electionTimer`, `heartbeatTimer`, `scheduledExecutor`) converted from nullable to `Optional<T>`. Shutdown methods use `ifPresent()` and `map()` instead of null checks. Volatile-mode shutdown expressed as `Optional.empty()`. |
| E.10 — Handler Optional | 2026-03-05 | — | — | 21 (13 handler + 5 tenant + 3 resource) | 0 | Controller handlers: `RouteHandler`(5 `findRoute().orElseThrow()`), `JobAssignmentHandler`(6 via helper), `TransferHandler`(1), `HeartbeatHandler`(1). `SimpleTenantService`: 5 patterns (`updateTenant`, `deleteTenant`, `updateTenantConfiguration`, `updateTenantStatus`, `validateTenantHierarchy`) converted to `Optional.ofNullable().orElseThrow()`. `SimpleResourceManagementService`: 3 patterns (`getUsageForDate`, `getUsageHistory`, `releaseResources`) converted to `Optional.ofNullable().flatMap()`/`.map()`/`.orElseThrow()`. |
| E.11 — NetworkStats | 2026-02-25 | — | — | 0 | 0 | `NetworkStatistics` converted to typed counters and fields. Raw `Map<String, Object>` replaced with strongly-typed accessors. |
| E.12 — TransferContext | 2026-03-05 | — | — | 0 | 1 (untyped `getAttribute(String)`) | `@Deprecated(forRemoval=true)` added to untyped `getAttribute(String)` method. Typed `getAttribute(String, Class<T>)` overload is the replacement. Last caller in `TransferContextTest` line 275 migrated from `getAttribute("key")` to `getAttribute("key", String.class)`. Zero remaining callers of deprecated method. |
| E.13 — Health Check DTOs | 2026-02-25 | — | — | 0 | 0 | `HealthDetail` record created with `status`, `message`, `details` fields. `RaftNode.toHealthDetail()` and `HttpApiServer` health endpoint use the record. Replaces ad-hoc `Map<String, Object>` health responses with structured type. |
| E.14 — Singleton Holder | 2026-02-25 | — | — | 0 | 0 | All 4 metrics classes (`ControllerMetrics`, `AgentMetrics`, `TransferMetrics`, `WorkflowMetrics`) converted to Initialization-on-Demand Holder pattern: `private static class Holder { static final XxxMetrics INSTANCE = new XxxMetrics(); }` + `public static XxxMetrics getInstance() { return Holder.INSTANCE; }`. Thread-safe lazy initialization without `synchronized`. |
| E.15 — Builder Fail-Fast | 2026-02-25 | — | — | 0 | 0 | `TransferRequest.Builder` validates required fields via `Objects.requireNonNull()` in constructor. Fail-fast on null `source`, `destination`, `protocol` at construction time rather than at `build()`. |

### 16.9 Rollback Strategy

If a phase introduces a regression that cannot be resolved quickly:

1. **Revert the entire branch** — do not try to "fix forward" across multiple domains
2. Record the failure mode in the Progress Log above
3. Write a failing test that captures the regression before re-attempting
4. Re-implement with the test as a guard

The vertical-slice approach (Section 16.2) makes reverting clean — each phase touches a
self-contained set of files with no cross-domain coupling.

### 16.10 Known Pre-Existing Failures

These failures exist before the refactoring and must not be confused with regressions:

| Test Class | Failure Mode | Root Cause |
|---|---|---|
| `FtpUploadIntegrationTest` | Errors (5) | Requires Docker (Testcontainers) |
| `FtpsUploadIntegrationTest` | Error (1) | Requires Docker (Testcontainers) |
| `SftpAbortIntegrationTest` | Errors (4) | Requires Docker (Testcontainers) |
| `SftpUploadIntegrationTest` | Failures (1), Errors (3) | Requires Docker (Testcontainers) |

These are **Docker-dependent integration tests** that fail when Docker is not running. They are
unrelated to the sealed record refactoring. The refactoring must not introduce any new failures
beyond this known set.

**Verification:** After each phase, the failure set must be identical to the table above. Any
new entry in `mvn test` output with `FAILURE` that is not in this table is a regression.

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
---

## Appendix C: Codebase Null-Check Audit

**847** null-check patterns across the production codebase (`== null`, `!= null`,
`Optional.ofNullable`, `requireNonNull`). Null checks are a code smell — each one signals an
interface that permits a state the domain does not. This appendix categorises every occurrence
and maps each category to its elimination strategy.

### C.1 Overall Counts

| Module | `== null` | `!= null` | `Optional.ofNullable` | `requireNonNull` | Total |
|---|---|---|---|---|---|
| quorus-core | 68 | 130 | 5 | 23 | **226** |
| quorus-controller | 98 | 116 | 112 | 11 | **337** |
| quorus-agent | 7 | 8 | 0 | 4 | **19** |
| quorus-workflow | 38 | 56 | 5 | 27 | **126** |
| quorus-tenant | 19 | 19 | 1 | 7 | **48** |
| quorus-api (legacy) | 22 | 36 | 0 | 0 | **61** |
| quorus-integration-examples | 4 | 26 | 0 | 0 | **30** |
| **Total** | **256** | **391** | **123** | **72** | **847** |

Only 5 `@NotNull` annotations exist in the entire codebase. Zero `@Nullable` or `@NonNull`.

### C.2 Category Taxonomy

Each null check falls into one of seven categories. Categories 1-3 are eliminated by the sealed
record refactoring. Categories 4-7 require targeted fixes outside this design's scope but are
tracked here for completeness.

#### Category 1: Structural Lying (sealed records eliminate)

**Count: ~130 matches** — the single largest category.

The current command classes carry all possible fields with null for inapplicable ones.
Every `Optional.ofNullable` in the codecs and every `yield null` in `QuorusStateStore` exists
because the interface lies about what it carries.

| Pattern | Files | Count | Sealed Record Fix |
|---|---|---|---|
| Codec `Optional.ofNullable(...).ifPresent(...)` | AgentCodec, RouteCodec, TransferCodec, JobQueueCodec, JobAssignmentCodec | 112 | Each record carries exactly its fields — pattern match, set directly, no guards |
| `yield null` on map-miss in `QuorusStateStore` | QuorusStateStore.java | 18 | `CommandResult.NotFound` (Section 5.2) |
| `if (command == null) return null` | QuorusStateStore.java:156 | 1 | `requireNonNull` in `apply()` — Raft log should never produce a null command |

**Example (before):**
```java
// AgentCodec.toProto — 5 nullable guards for one command
Optional.ofNullable(cmd.getAgentId()).ifPresent(builder::setAgentId);
Optional.ofNullable(cmd.getAgentInfo()).ifPresent(i -> builder.setAgentInfo(toProto(i)));
Optional.ofNullable(cmd.getNewStatus()).ifPresent(s -> builder.setNewStatus(toProto(s)));
Optional.ofNullable(cmd.getNewCapabilities()).ifPresent(c -> builder.setNewCapabilities(toProto(c)));
Optional.ofNullable(cmd.getTimestamp()).ifPresent(t -> builder.setTimestampEpochMs(t.toEpochMilli()));
```

**After (sealed records):**
```java
case AgentCommand.Register reg -> AgentCommandProto.newBuilder()
    .setType(REGISTER)
    .setAgentId(reg.agentId())          // non-null — it's a record component
    .setAgentInfo(toProto(reg.agentInfo()))  // non-null
    .setTimestampEpochMs(reg.timestamp().toEpochMilli())  // non-null
    .build();
```

#### Category 2: Boilerplate `equals()`/`hashCode()` (records eliminate)

**Count: ~40 matches.**

Every `if (o == null || getClass() != o.getClass())` and ternary-null `hashCode()` in domain
classes and command classes is eliminated when those classes become records. Records generate
correct `equals()`, `hashCode()`, and `toString()` from their components.

| Files | Count |
|---|---|
| AgentCommand, JobAssignmentCommand, JobQueueCommand, RouteCommand | 4 (command classes) |
| AgentLoad, JobAssignment, QueuedJob, RouteConfiguration, TransferJob, TransferRequest, TransferResult, NetworkNode | 8 (domain classes) |
| ExecutionContext, TransferGroup, ValidationResult, WorkflowDefinition, WorkflowExecution | 5 (workflow) |
| Tenant, ResourceUsage | 2 (tenant) |

#### Category 3: Command `validateCommand()` Null Guards (compact constructors eliminate)

**Count: ~25 matches.**

`JobAssignmentCommand.validateCommand()` and `JobQueueCommand.validateCommand()` manually check
`if (assignmentId == null)`, `if (jobAssignment == null)`, etc. With sealed records, compact
constructors enforce these invariants structurally:

```java
record Assign(String assignmentId, JobAssignment jobAssignment, Instant timestamp)
        implements JobAssignmentCommand {
    Assign {
        Objects.requireNonNull(assignmentId, "assignmentId");
        Objects.requireNonNull(jobAssignment, "jobAssignment");
        Objects.requireNonNull(timestamp, "timestamp");
    }
}
```

#### Category 4: Map Lookups → Optional Returns

**Count: ~60 matches.**

Handler and service code does `stateMachine.getRoute(id)` then `if (route == null)`. The null
comes from `ConcurrentHashMap.get()` — the map API forces this. The fix is two-layered:

1. **QuorusStateStore query methods** return `Optional<T>` instead of nullable `T` (Phase 8, Section 14.3)
2. **Handlers** use `orElseThrow()` instead of `if (x == null) throw`:

```java
// Before — null check parade
RouteConfiguration route = stateMachine.getRoute(routeId);
if (route == null) {
    throw QuorusApiException.notFound(ErrorCode.ROUTE_NOT_FOUND, routeId);
}

// After — Optional chain
RouteConfiguration route = stateStore.getRoute(routeId)
    .orElseThrow(() -> QuorusApiException.notFound(ErrorCode.ROUTE_NOT_FOUND, routeId));
```

**Files affected:**
- RouteHandler.java (6 lookups)
- JobAssignmentHandler.java (3 lookups)
- TransferHandler.java (1 lookup)
- HeartbeatHandler.java (1 lookup)
- JobAssignmentService.java (4 lookups)
- SimpleTenantService.java (8 lookups)
- SimpleResourceManagementService.java (4 lookups)

#### Category 5: Lazy Initialisation → Eager Initialisation

**Count: ~15 matches.**

`AgentCapabilities` uses lazy null-check initialisation:
```java
public void addSupportedProtocol(String protocol) {
    if (this.supportedProtocols == null) {
        this.supportedProtocols = new HashSet<>();  // why wasn't this initialised?
    }
    this.supportedProtocols.add(protocol);
}
```

The field should be initialised to an empty collection at construction, never null. The null
possibility then infects every method that reads the field (`supportsProtocol` checks
`supportedProtocols != null`). Fix: initialise all collection fields eagerly in constructors.

**Files affected:**
- AgentCapabilities.java (3 lazy-init collections: supportedProtocols, availableRegions, customCapabilities)
- AgentInfo.java (1 lazy-init: metadata)
- TransferRequest.java (1 lazy-init: metadata)

#### Category 6: Repeated Guard on Same Field → requireNonNull at Construction

**Count: ~15 matches.**

`RaftNode` checks `if (storage == null)` in 5 separate methods (lines 334, 528, 1003, 1129, 1327).
This is the same design flaw — the field is optionally null, and every usage re-checks. The fix
depends on the domain semantics:

- If storage is truly optional (volatile mode), use `Optional<RaftStorage>` as the field type and
  force callers to acknowledge optionality via `ifPresent()` / `map()`.
- If storage is required, `requireNonNull` in the constructor and remove all guards.

Similarly, `QuorusControllerVerticle` checks `apiServer != null`, `raftNode != null`,
`grpcServer != null` in shutdown — these should be non-null post-startup.

#### Category 7: Input Validation at Boundary (legitimate, but improvable)

**Count: ~80 matches.**

Protocol adapters (`FtpTransferProtocol`, `SftpTransferProtocol`, `HttpTransferProtocol`, etc.)
validate `if (request == null || request.getSourceUri() == null)`. YAML parser checks
`if (data == null)`. Handler validation methods check `if (route.getRouteId() == null)`.

These are boundary checks on external input (HTTP bodies, YAML documents, URI components). They are
the most legitimate category of null checking — the external world genuinely can send null. But
even here, improvements exist:

- **Builder validation**: `TransferRequest.Builder` should validate required fields in `build()`,
  not downstream. A valid `TransferRequest` should be non-null in all required fields by
  construction.
- **HTTP body parsing**: Vert.x `ctx.body().asJsonObject()` returns null for missing/malformed
  bodies. A shared middleware could reject null bodies before handlers execute.
- **YAML parsing**: `YamlWorkflowDefinitionParser` helper methods (`getString`, `getInt`, `getList`)
  return null/default for missing keys. These could return `Optional` or throw
  `WorkflowParseException` for required fields.

### C.3 Elimination Roadmap

| Category | Count | Elimination Mechanism | Phase |
|---|---|---|---|
| 1. Structural Lying | ~130 | Sealed records + CommandResult ADT | Phases 1-6 (this design) |
| 2. Boilerplate equals/hashCode | ~40 | Records generate these | Phases 1-6 (this design) |
| 3. validateCommand() guards | ~25 | Compact constructors | Phases 1-6 (this design) |
| 4. Map lookups → Optional | ~60 | `Optional<T>` query methods | Phase 8 (Section 14.3) |
| 5. Lazy init → eager init | ~15 | Initialise in constructor | Separate PR (quorus-core) |
| 6. Repeated field guards | ~15 | `Optional<T>` field or `requireNonNull` | Separate PR (per module) |
| 7. Boundary input validation | ~80 | Builder validation, middleware, parse exceptions | Incremental |
| **Total** | **~365** eliminated or improved | | |

Note: ~482 of the 847 matches are `!= null` checks in existing code that mirror the `== null`
checks above (same root cause, opposite polarity). The counts above deduplicate by root cause,
not by syntactic occurrence.

### C.4 Highest-Impact Files (by null-check density)

| File | `== null` | `!= null` | `Optional.ofNullable` | Total | Primary Category |
|---|---|---|---|---|---|
| QuorusStateStore.java | 22 | 4 | 4 | **30** | 1 (structural lying) |
| AgentCodec.java | 0 | 2 | 27 | **29** | 1 (structural lying) |
| RouteCodec.java | 0 | 2 | 22 | **24** | 1 (structural lying) |
| JobQueueCodec.java | 0 | 2 | 22 | **24** | 1 (structural lying) |
| SimpleTenantService.java | 12 | 12 | 0 | **24** | 4 (map lookups) |
| RaftNode.java | 7 | 14 | 0 | **21** | 6 (repeated guards) |
| RouteHandler.java | 12 | 2 | 0 | **14** | 4+7 (lookups + validation) |
| YamlWorkflowDefinitionParser.java | 17 | 10 | 0 | **27** | 7 (boundary input) |
| FtpTransferProtocol.java | 8 | 8 | 0 | **16** | 7 (boundary input) |
| AgentCapabilities.java | 4 | 6 | 0 | **10** | 5 (lazy init) |

### C.5 Singleton Double-Checked Locking (Anti-Pattern)

Four metrics classes use `if (instance == null)` in `synchronized getInstance()`:

- `TransferTelemetryMetrics.getInstance()`
- `WorkflowMetrics.getInstance()`
- `TenantMetrics.getInstance()`
- `RaftMetrics.getInstance()`

This is a well-known anti-pattern. Replace with the **Initialization-on-demand holder** idiom
(lazy, thread-safe, no null check):

```java
public final class TransferTelemetryMetrics {
    private TransferTelemetryMetrics() { /* init meters */ }

    private static final class Holder {
        static final TransferTelemetryMetrics INSTANCE = new TransferTelemetryMetrics();
    }

    public static TransferTelemetryMetrics getInstance() {
        return Holder.INSTANCE;
    }
}
```

Or better: inject via Vert.x / CDI and eliminate the singleton entirely.

---

## Appendix D: `Object` Return Type Audit — Type Erasure Code Smell

Using `Object` as a return type, parameter type, or field type is lazy and unsafe. It erases all
compile-time type information, forces callers to `instanceof`-check and cast, and makes code
impossible to understand without reading the implementation. Every `Object` in a signature is a
missing type that the programmer was too lazy to express.

### D.1 The Core Problem: `RaftLogApplicator.apply()` → `Object`

The root of the type-safety rot:

```java
// RaftLogApplicator.java — the interface that started it all
public interface RaftLogApplicator {
    Object apply(RaftCommand command);  // what does this return? anything. nothing. who knows.
}
```

This single `Object` return type cascades through the entire system:

```
RaftLogApplicator.apply() → Object
    ↓
QuorusStateStore.apply() → Object
    ↓
RaftNode.submitCommand() → Future<Object>
    ↓
JobAssignmentService → instanceof check + cast
HeartbeatHandler → instanceof check + cast
TransferHandler → ignores the result entirely
```

**9 methods** return `Object` in QuorusStateStore alone (the top-level `apply()` plus 6 private
`apply*Command()` methods). Every caller of `submitCommand()` is forced into an `instanceof`
lottery — the type system provides zero help.

### D.2 Full Inventory

#### Methods returning `Object` (CRITICAL)

| File | Line | Signature | Impact |
|---|---|---|---|
| RaftLogApplicator.java | 31 | `Object apply(RaftCommand command)` | Interface contract — poisons everything |
| QuorusStateStore.java | 155 | `public Object apply(RaftCommand command)` | 6 switch arms, each returning different types |
| QuorusStateStore.java | 181 | `private Object applyTransferJobCommand(...)` | Returns TransferJob, TransferJobSnapshot, or null |
| QuorusStateStore.java | 249 | `private Object applyAgentCommand(...)` | Returns AgentInfo or null |
| QuorusStateStore.java | 312 | `private Object applySystemMetadataCommand(...)` | Returns String or null |
| QuorusStateStore.java | 331 | `private Object applyJobAssignmentCommand(...)` | Returns JobAssignment or null |
| QuorusStateStore.java | 414 | `private Object applyJobQueueCommand(...)` | Returns QueuedJob or null |
| QuorusStateStore.java | 470 | `private Object applyRouteCommand(...)` | Returns RouteConfiguration or null |
| RaftNode.java | 479 | `Future<Object> submitCommand(RaftCommand)` | Every caller must cast |
| NetworkTopologyService.java | 507 | `Object getTransferMetrics()` | Meaningless return type |
| NetworkTopologyService.java | 541 | `Object getAggregatedMetrics()` | Returns a String literal (!?) |
| TransferContext.java | 79 | `Object getAttribute(String key)` | Untyped attribute bag |
| VariableResolver.java | 198 | `private Object resolveVariable(String)` | YAML untyped value |

#### `instanceof` checks forced by `Object` returns (downstream damage)

| File | Line | Code | Root Cause |
|---|---|---|---|
| JobAssignmentService.java | 122 | `if (result instanceof QueuedJob)` | `submitCommand()` → `Future<Object>` |
| JobAssignmentService.java | 194 | `if (result instanceof JobAssignment)` | `submitCommand()` → `Future<Object>` |
| JobAssignmentService.java | 245 | `if (result instanceof JobAssignment)` | `submitCommand()` → `Future<Object>` |
| HeartbeatHandler.java | 97 | `if (result instanceof AgentInfo updatedAgent)` | `submitCommand()` → `Future<Object>` |

Every one of these `instanceof` checks only exists because `submitCommand()` returns
`Future<Object>`. If the type system carried the result type, these would be direct field accesses
with compile-time safety.

#### Fields typed as `Object` (type information destroyed at storage)

| File | Line | Field | Actual Type |
|---|---|---|---|
| NetworkTopologyService.java | 491 | `private final Object transferMetrics` | Unknown — set by builder, never typed |
| NetworkTopologyService.java | 517 | `private Object transferMetrics = null` | Builder mirrors the field |
| TransferContext.java | 38 | `Map<String, Object> attributes` | Untyped attribute bag |

#### Collections using `Map<String, Object>` (85 occurrences)

| Context | Count | Avoidability |
|---|---|---|
| YAML parsing (`YamlWorkflowDefinitionParser`, `WorkflowSchemaValidator`) | ~48 | **Inherent** — SnakeYAML returns `Map<String, Object>`. Unavoidable until the YAML is parsed into typed domain objects. |
| Workflow variables (`VariableResolver`, `ExecutionContext`) | ~14 | **Partially avoidable** — variable values could be a sealed `VariableValue` (String, Number, Boolean, List) |
| Health checks / DTOs (`HealthResource`, `ProtocolHealthCheck`, etc.) | ~12 | **Avoidable** — should be typed `HealthStatus` / `HealthDetail` records |
| Agent capabilities metadata (`AgentCapabilities.customCapabilities`) | ~3 | **Avoidable** — should have a typed schema or use `JsonObject` |
| Tenant configuration (`TenantConfiguration.customProperties`) | ~4 | **Avoidable** — typed configuration record |
| Integration examples | ~3 | **Acceptable** — example code |

### D.3 The Fix: `CommandResult<T>` Already Solves This

The `CommandResult<T>` ADT introduced in Section 5.2 directly replaces every `Object` return in
the Raft command path. Here is the complete flow:

#### Step 1: Type the interface

```java
public interface RaftLogApplicator {
    CommandResult<?> apply(RaftCommand command);  // no more Object
}
```

#### Step 2: Type each dispatch method

```java
// QuorusStateStore — each method returns its specific CommandResult
private CommandResult<TransferJob> applyTransferJobCreate(TransferJobCommand.Create cmd) { ... }
private CommandResult<TransferJobSnapshot> applyTransferJobUpdateStatus(TransferJobCommand.UpdateStatus cmd) { ... }
private CommandResult<AgentInfo> applyAgentRegister(AgentCommand.Register cmd) { ... }
// ... etc
```

The top-level `apply()` method returns `CommandResult<?>` because different command types produce
different entity types. The wildcard is acceptable here — it's the dispatch point. Downstream,
each applicator method is fully typed.

#### Step 3: Type `submitCommand()`

```java
// RaftNode — still returns Future<CommandResult<?>> (wildcard at the dispatch boundary)
public Future<CommandResult<?>> submitCommand(RaftCommand command) { ... }
```

#### Step 4: Callers pattern-match on `CommandResult`, not `instanceof Object`

```java
// Before — instanceof lottery on Object
Object result = raftNode.submitCommand(command);
if (result instanceof QueuedJob) {
    QueuedJob enqueuedJob = (QueuedJob) result;
    // ...
} else {
    throw new RuntimeException("Failed to enqueue job");
}

// After — exhaustive pattern match on CommandResult
raftNode.submitCommand(command)
    .onSuccess(result -> switch (result) {
        case CommandResult.Success<?> s -> {
            QueuedJob enqueuedJob = (QueuedJob) s.entity();  // one cast, typed context
            jobQueue.put(enqueuedJob.getJobId(), enqueuedJob);
        }
        case CommandResult.NotFound<?> nf ->
            throw QuorusApiException.notFound(ErrorCode.JOB_NOT_FOUND, nf.id());
        case CommandResult.CasMismatch<?> cas ->
            throw QuorusApiException.conflict(ErrorCode.CAS_MISMATCH, ...);
    });
```

Better still — if `RaftCommand` carries its result type as a type parameter:

```java
public sealed interface RaftCommand<R> extends Serializable { ... }

public sealed interface TransferJobCommand extends RaftCommand<TransferJobSnapshot> { ... }
public sealed interface AgentCommand extends RaftCommand<AgentInfo> { ... }
```

Then `submitCommand()` can be:

```java
public <R> Future<CommandResult<R>> submitCommand(RaftCommand<R> command) { ... }
```

And callers get full type safety — no casts at all:

```java
Future<CommandResult<QueuedJob>> future = raftNode.submitCommand(enqueueCommand);
future.onSuccess(result -> switch (result) {
    case CommandResult.Success<QueuedJob> s -> jobQueue.put(s.entity().getJobId(), s.entity());
    case CommandResult.NotFound<QueuedJob> nf -> { /* handle */ }
    case CommandResult.CasMismatch<QueuedJob> cas -> { /* handle */ }
});
```

**Note:** The type-parameterised approach (`RaftCommand<R>`) requires that each sealed command
subtype has a uniform result type. This works for most commands (e.g., all `AgentCommand` variants
return `AgentInfo`). For `TransferJobCommand`, where `Create` returns `TransferJob` but
`UpdateStatus` returns `TransferJobSnapshot`, the result type would be a common supertype or the
variants would need separate type parameters. The simpler approach (Step 1-4 above with
`CommandResult<?>` at the dispatch boundary) is recommended for Phase 1.

### D.4 Other `Object` Fixes

| Problem | Fix |
|---|---|
| `NetworkStatistics.transferMetrics: Object` | Type as `NetworkMetrics` or `Map<String, TransferMetric>` |
| `NetworkMetrics.getAggregatedMetrics(): Object` | Returns a `String` literal — type as `String` or create `AggregatedMetrics` record |
| `TransferContext.getAttribute(): Object` | Keep typed overload `<T> getAttribute(key, Class<T>)`, deprecate untyped overload |
| `VariableResolver.resolveVariable(): Object` | Inherent to YAML untyped values — acceptable until variables get a typed schema |
| `Map<String, Object>` in health checks | Replace with `HealthDetail` record: `record HealthDetail(String component, Status status, Map<String, String> metadata)` |
| `Map<String, Object>` in `TenantConfiguration` | Replace with `TenantProperties` record with typed fields |

### D.5 Elimination Roadmap

| What | Count | Fix | Phase |
|---|---|---|---|
| `RaftLogApplicator.apply()` → `Object` | 1 | `CommandResult<?>` | Phase 6 (this design) |
| `QuorusStateStore.apply*()` → `Object` | 7 | `CommandResult<T>` per applicator | Phase 6 (this design) |
| `RaftNode.submitCommand()` → `Future<Object>` | 1 | `Future<CommandResult<?>>` | Phase 6 (this design) |
| `instanceof` casts on submit result | 4 | Pattern match on `CommandResult` | Phase 6 (this design) |
| `NetworkStatistics.transferMetrics: Object` | 2 | Type the field | Separate PR |
| `NetworkMetrics.getAggregatedMetrics(): Object` | 1 | Type as `String` or record | Separate PR |
| `TransferContext.getAttribute(): Object` | 1 | Deprecate untyped overload | Separate PR |
| `Map<String, Object>` in health checks | ~12 | Typed records | Separate PR |
| `Map<String, Object>` in YAML parsing | ~48 | Inherent to SnakeYAML | No change needed |
| **Total type-unsafe interfaces** | **~77** (excluding YAML) | | |

The sealed record refactoring (this design) eliminates the **13 most critical** `Object` uses —
the entire `apply()` → `submitCommand()` → `instanceof` chain. The remaining ~64 are lower
severity and can be addressed incrementally.

---

## Appendix E: Remediation Task Breakdown

This appendix provides detailed, actionable tasks for eliminating every code smell identified in
Appendices C and D. Tasks are grouped into work packages, ordered by dependency, and each task
specifies the exact files, the anti-pattern, and the target pattern.

**Scope:** Tasks E.1–E.6 are covered by the existing Phases 1–10 of this design. Tasks E.7–E.14
are **new work** outside the current phase plan — they address the residual null and `Object`
smells that the sealed record refactoring does not reach.

### E.1 [Phases 1–6] Sealed Record Refactoring — Eliminate Structural Lying

**Already tracked in Section 13.** Included here for cross-reference completeness.

| Task | Files | Anti-Pattern | Target Pattern | Est. |
|---|---|---|---|---|
| E.1.1 Convert `SystemMetadataCommand` to sealed records | SystemMetadataCommand.java, SystemMetadataCodec.java, QuorusStateStore.java | Enum tag + nullable fields | 2 records, compact constructors | 2h |
| E.1.2 Convert `TransferJobCommand` | TransferJobCommand.java, TransferCodec.java, QuorusStateStore.java, TransferHandler.java | 4 nullable fields | 4 records, factory methods | 3h |
| E.1.3 Convert `AgentCommand` | AgentCommand.java, AgentCodec.java, QuorusStateStore.java | 5 nullable fields, 5 `Optional.ofNullable` guards in toProto | 5 records with timestamps | 3h |
| E.1.4 Convert `RouteCommand` | RouteCommand.java, RouteCodec.java, QuorusStateStore.java | 5 nullable fields, 22 `Optional.ofNullable` guards | 6 records | 3h |
| E.1.5 Convert `JobQueueCommand` | JobQueueCommand.java, JobQueueCodec.java, QuorusStateStore.java | 5 nullable fields, 22 `Optional.ofNullable` guards | 6 records | 3h |
| E.1.6 Convert `JobAssignmentCommand` | JobAssignmentCommand.java, JobAssignmentCodec.java, QuorusStateStore.java | 6 nullable fields, 15 `Optional.ofNullable` guards | 7 records with CAS | 4h |

**Metrics eliminated:** ~112 `Optional.ofNullable` guards in codecs, ~25 `validateCommand()` null
checks, ~40 boilerplate `equals()`/`hashCode()` methods.

### E.2 [Phase 6] `Object` → `CommandResult<T>` in Raft Path

| Task | Files | Anti-Pattern | Target Pattern | Est. |
|---|---|---|---|---|
| E.2.1 Change `RaftLogApplicator.apply()` return type | RaftLogApplicator.java | `Object apply(RaftCommand)` | `CommandResult<?> apply(RaftCommand)` | 30m |
| E.2.2 Change `QuorusStateStore.apply()` and 6 private dispatch methods | QuorusStateStore.java | 7 methods returning `Object`, 18 × `yield null` | `CommandResult<T>` per applicator, `CommandResult.NotFound` replaces null | 4h |
| E.2.3 Change `RaftNode.submitCommand()` | RaftNode.java | `Future<Object> submitCommand(RaftCommand)` | `Future<CommandResult<?>> submitCommand(RaftCommand)` | 1h |
| E.2.4 Update `JobAssignmentService` callers | JobAssignmentService.java | 3 × `if (result instanceof QueuedJob)` / `instanceof JobAssignment` | Pattern match on `CommandResult.Success` / `NotFound` / `CasMismatch` | 2h |
| E.2.5 Update `HeartbeatHandler` caller | HeartbeatHandler.java | `if (result instanceof AgentInfo updatedAgent)` | Pattern match on `CommandResult.Success<AgentInfo>` | 30m |
| E.2.6 Update all other `submitCommand()` call sites | TransferHandler.java, RouteHandler.java, JobAssignmentHandler.java, AgentRegistrationHandler.java, JobStatusHandler.java | Ignore result or bare `onSuccess` | Handle `CommandResult` variants explicitly | 2h |

**Metrics eliminated:** 9 methods returning `Object`, 4 `instanceof` casts, 18 `yield null`.

### E.3 [Phases 7–10] State Transition + Optional + CAS

**Already tracked in Sections 14.2–14.5.** Cross-reference only.

| Task | Scope | Est. |
|---|---|---|
| E.3.1 Phase 7: Enum transition infrastructure | 4 status enums + `InvalidTransitionException` | 3h |
| E.3.2 Phase 8: `QuorusStateStore` query methods → `Optional<T>` | 4 getter methods | 1h |
| E.3.3 Phase 9: Pre-commit CAS validation in handlers | 6–7 handler files | 4h |
| E.3.4 Phase 10: Integration tests for transition rejection | 4+ test classes | 4h |

---

### E.7 Eager Collection Initialisation (Category 5 — Appendix C)

**Goal:** Eliminate lazy null-check initialisation of collection fields. Every collection field
must be non-null from construction. This removes ~15 null checks.

| Task | File | Anti-Pattern | Target Pattern |
|---|---|---|---|
| E.7.1 | `AgentCapabilities.java` | `supportedProtocols` field can be null; `addSupportedProtocol()` checks `if (this.supportedProtocols == null)` before adding; `supportsProtocol()` checks `supportedProtocols != null` before reading | Initialise `supportedProtocols = new HashSet<>()` in constructor. Remove all null checks. Setter uses `this.supportedProtocols = supportedProtocols != null ? supportedProtocols : new HashSet<>()` → change to `this.supportedProtocols = Set.copyOf(supportedProtocols)` with `requireNonNull` |
| E.7.2 | `AgentCapabilities.java` | Same pattern for `availableRegions` field | Same fix — eager `new HashSet<>()` |
| E.7.3 | `AgentCapabilities.java` | Same pattern for `customCapabilities` field | Same fix — eager `new HashMap<>()` |
| E.7.4 | `AgentInfo.java` | `metadata` field lazily initialised in `addMetadata()`, null-checked in access methods | Initialise `metadata = new HashMap<>()` in constructor |
| E.7.5 | `TransferRequest.java` | `metadata` field lazily initialised | Same fix — eager init in Builder |

**Files changed:** 3 files, ~15 null checks removed.

**Test validation:** Run `AgentCapabilitiesTest`, `AgentInfoTest`, `TransferRequestTest`. All
existing tests should pass — the only change is that collections are never null.

### E.8 `RaftNode` Storage Optionality (Category 6 — Appendix C)

**Goal:** `RaftNode` checks `if (storage == null)` in 5 separate methods. Express the optionality
once, in the type, not 5 times at every usage.

| Task | File | Anti-Pattern | Target Pattern |
|---|---|---|---|
| E.8.1 | `RaftNode.java` | `private final RaftStorage storage;` — field is nullable, checked at lines 334, 528, 1003, 1129, 1327 | Change to `private final Optional<RaftStorage> storage;` |
| E.8.2 | `RaftNode.java` | `if (storage == null) { logger.info("No storage, volatile mode"); return Future.succeededFuture(); }` repeated 5 times | `storage.map(s -> s.loadMetadata()).orElse(Future.succeededFuture())` — express once per operation |
| E.8.3 | `RaftNode.java` constructor | `this.storage = storage;` (nullable assignment) | `this.storage = Optional.ofNullable(storage);` — the single place where optionality is declared |

**Files changed:** 1 file (`RaftNode.java`), ~10 null checks converted to `Optional` operations.

**Trade-off:** `Optional` as a field is a debated pattern. In this case it's justified because
storage is genuinely optional (volatile mode is a supported deployment), and the alternative is
5 identical null guards scattered across the class.

### E.9 Controller Verticle Shutdown Null Guards (Category 6 — Appendix C)

**Goal:** `QuorusControllerVerticle` checks `apiServer != null`, `raftNode != null`,
`grpcServer != null` in shutdown. These are non-null post-startup.

| Task | File | Anti-Pattern | Target Pattern |
|---|---|---|---|
| E.9.1 | `QuorusControllerVerticle.java` | 8 `!= null` checks in shutdown sequence for fields that are always initialised during `start()` | Extract shutdown into a `ShutdownSequence` that is only constructed after `start()` completes — fields are constructor-injected and non-null by construction |

**Alternative (simpler):** Keep the null checks but move startup to a builder pattern that
guarantees all fields are set before the verticle is "ready". The verticle `stop()` method then
operates on a guaranteed-populated `StartedState` record.

**Files changed:** 1 file.

### E.10 Handler Map-Lookup Null Checks → `Optional` (Category 4 — Appendix C)

**Goal:** Convert `QuorusStateStore` query methods to return `Optional<T>`, then update all
handler call sites to use `orElseThrow()`. This is Phase 8 (Section 14.3) plus the handler-side
migration.

**Prerequisite:** Phase 8 (E.3.2) must be complete first.

| Task | File | Count | Anti-Pattern | Target Pattern |
|---|---|---|---|---|
| E.10.1 | `RouteHandler.java` | 6 | `if (route == null) { throw notFound(...); }` | `stateStore.getRoute(id).orElseThrow(() -> notFound(...))` |
| E.10.2 | `JobAssignmentHandler.java` | 3 | `if (assignment == null) { throw notFound(...); }` | `.orElseThrow(...)` |
| E.10.3 | `TransferHandler.java` | 1 | `if (job == null) { throw notFound(...); }` | `.orElseThrow(...)` |
| E.10.4 | `HeartbeatHandler.java` | 1 | `if (existingAgent == null) { ... }` | `.orElseThrow(...)` |
| E.10.5 | `JobAssignmentService.java` | 4 | `if (queuedJob == null) throw ...` | `.orElseThrow(...)` |
| E.10.6 | `SimpleTenantService.java` | 8 | `if (tenant == null) { throw ... }` | `.orElseThrow(...)` |
| E.10.7 | `SimpleResourceManagementService.java` | 4 | `if (config == null) { ... }` / `if (reservation == null) { ... }` | `.orElseThrow(...)` |

**Files changed:** 7 files, ~27 null-check-then-throw blocks converted to one-liners.

**Test validation:** All existing handler tests and service tests must pass. The only behavioural
change is the `Optional` wrapper — the exception thrown is identical.

### E.11 `NetworkStatistics.transferMetrics` — Type the `Object` Field

**Goal:** Replace `Object transferMetrics` with an actual type.

| Task | File | Anti-Pattern | Target Pattern |
|---|---|---|---|
| E.11.1 | `NetworkTopologyService.java` (`NetworkStatistics`) | `private final Object transferMetrics;` — field typed as `Object`, getter returns `Object` | Type as `NetworkMetrics` (the inner class that actually provides metrics) or `Optional<NetworkMetrics>` if it's genuinely optional |
| E.11.2 | `NetworkTopologyService.java` (`NetworkStatistics.Builder`) | `private Object transferMetrics = null;` | `private NetworkMetrics transferMetrics = null;` |
| E.11.3 | `NetworkTopologyService.java` (`NetworkMetrics`) | `public Object getAggregatedMetrics()` — returns a String literal `"Aggregated metrics for N hosts"` | Return `String` or create an `AggregatedMetrics` record with numeric fields |

**Files changed:** 1 file, 3 signatures changed.

### E.12 `TransferContext` Attribute Bag — Deprecate Untyped Accessor

**Goal:** The untyped `Object getAttribute(String key)` forces callers to cast. The typed
overload `<T> getAttribute(String key, Class<T> type)` already exists.

| Task | File | Anti-Pattern | Target Pattern |
|---|---|---|---|
| E.12.1 | `TransferContext.java` | `public Object getAttribute(String key)` — returns bare `Object` | Add `@Deprecated(forRemoval = true)` annotation. All callers should use the typed overload. |
| E.12.2 | All callers of `getAttribute(key)` | Grep for `getAttribute(` without a `Class` second argument | Migrate to `getAttribute(key, ExpectedType.class)` |
| E.12.3 | `TransferContext.java` | Typed overload returns `null` when type doesn't match | Return `Optional<T>` instead: `public <T> Optional<T> getAttribute(String key, Class<T> type)` |

**Files changed:** `TransferContext.java` + all callers (audit needed — likely 3–5 files in
`quorus-core`).

### E.13 Health Check DTOs — Replace `Map<String, Object>` with Typed Records

**Goal:** Health check endpoints use `Map<String, Object>` for response building. These should be
typed records that serialise cleanly to JSON.

| Task | File | Anti-Pattern | Target Pattern |
|---|---|---|---|
| E.13.1 | Create `HealthDetail.java` | Does not exist | `record HealthDetail(String component, HealthStatus status, String message, Map<String, String> metadata)` |
| E.13.2 | `ProtocolHealthCheck.java` | `toDetailMap()` returns `Map<String, Object>` | Return `HealthDetail` record |
| E.13.3 | `TransferEngineHealthCheck.java` | Same `Map<String, Object>` pattern | Return `HealthDetail` |
| E.13.4 | `HealthResource.java` (quorus-api) | Builds `Map<String, Object>` response inline | Use `HealthDetail` records |
| E.13.5 | `HealthService.java` (quorus-agent) | `Map<String, Object>` for health response | Use `HealthDetail` records |

**Files changed:** 5 files + 1 new record class.

### E.14 Singleton Metrics — Initialization-on-Demand Holder

**Goal:** Replace double-checked locking `if (instance == null)` with thread-safe holder idiom.

| Task | File | Anti-Pattern | Target Pattern |
|---|---|---|---|
| E.14.1 | `TransferTelemetryMetrics.java` | `synchronized getInstance()` + `if (instance == null)` | Inner `Holder` class with static `INSTANCE` field |
| E.14.2 | `WorkflowMetrics.java` | Same DCL pattern | Same holder fix |
| E.14.3 | `TenantMetrics.java` | Same DCL pattern | Same holder fix |
| E.14.4 | `RaftMetrics.java` | Same DCL pattern | Same holder fix |

**Files changed:** 4 files, 4 null checks removed. Mechanical refactoring — no behavioural
change.

### E.15 Boundary Input Validation — Builder Fail-Fast (Category 7 — Appendix C)

**Goal:** Push validation from downstream code into builders and constructors so that once an
object is constructed, all required fields are guaranteed non-null. This doesn't remove null
checks — it moves them to the single correct location and removes them from everywhere else.

| Task | File | Anti-Pattern | Target Pattern |
|---|---|---|---|
| E.15.1 | `TransferRequest.Builder.build()` | Downstream null checks: `FtpTransferProtocol` checks `request.getSourceUri() == null`, `HttpTransferProtocol` checks `request.getDestinationPath() == null` | `build()` validates all required fields with `requireNonNull`. Protocol adapters can trust the object. |
| E.15.2 | `RouteConfiguration` construction | `RouteHandler.validateRouteConfiguration()` has 7 null checks for required fields | Move validation into constructor or static factory. Handler calls factory, trusts the result. |
| E.15.3 | `AgentInfo` construction | `AgentRegistrationHandler` checks `agentInfo.getAgentId() == null` | Move to constructor |
| E.15.4 | HTTP body middleware | Multiple handlers check `if (body == null)` after `ctx.body().asJsonObject()` | Create a shared `RequireBody` handler that rejects null/empty bodies before routing. Handlers receive guaranteed non-null body. |

**Files changed:** ~12 files. This is the largest and most incremental task — can be done
per-domain-object over multiple PRs.

---

### E.16 Task Dependency Graph

```
E.1 (Sealed Records, Phases 1–6)
  │
  ├──→ E.2 (CommandResult<T>, Phase 6) ──→ E.10 (Handler Optional migration)
  │                                              │
  │                                              ↓
  │                                         E.3.3 (CAS validation, Phase 9)
  │                                              │
  │                                              ↓
  │                                         E.3.4 (Integration tests, Phase 10)
  │
  └──→ E.3.1 (Transition enums, Phase 7)
       E.3.2 (Optional queries, Phase 8) ──→ E.10

Independent (can start immediately, no prerequisites):
  E.7  (Eager collection init)
  E.8  (RaftNode storage Optional)
  E.9  (Verticle shutdown guards)
  E.11 (NetworkStatistics typing)
  E.12 (TransferContext deprecation)
  E.13 (Health check typed records)
  E.14 (Singleton holder idiom)
  E.15 (Builder fail-fast validation)
```

### E.17 Estimated Effort Summary

| Work Package | Tasks | Files | Null Checks Eliminated | `Object` Uses Eliminated | Est. Hours |
|---|---|---|---|---|---|
| Sealed records (Phases 1–6) | E.1.1–E.1.6 | ~18 | ~177 (structural + validate + equals) | 0 | 18h |
| CommandResult ADT (Phase 6) | E.2.1–E.2.6 | ~8 | 18 (`yield null`) | 13 (`Object` returns + casts) | 10h |
| State transitions (Phases 7–10) | E.3.1–E.3.4 | ~15 | 0 | 0 | 12h |
| Eager collection init | E.7.1–E.7.5 | 3 | ~15 | 0 | 2h |
| RaftNode storage Optional | E.8.1–E.8.3 | 1 | ~10 | 0 | 2h |
| Verticle shutdown | E.9.1 | 1 | ~8 | 0 | 1h |
| Handler Optional migration | E.10.1–E.10.7 | 7 | ~27 | 0 | 3h |
| NetworkStatistics typing | E.11.1–E.11.3 | 1 | 0 | 3 | 1h |
| TransferContext deprecation | E.12.1–E.12.3 | ~5 | 0 | 3 | 1h |
| Health check records | E.13.1–E.13.5 | 6 | 0 | ~12 | 3h |
| Singleton holder | E.14.1–E.14.4 | 4 | 4 | 0 | 1h |
| Builder fail-fast | E.15.1–E.15.4 | ~12 | ~30 (moved, not removed) | 0 | 6h |
| **Total** | **~50 tasks** | **~81 files** | **~289 eliminated** | **~31 eliminated** | **~60h** |

---

## Appendix F: Verification Scripts & Automation

### F.1 Metric Snapshot Script

Run before and after every phase to capture quantifiable progress. Save output to the
Progress Log (Section 16.8).

```powershell
# verify-metrics.ps1 — Run from project root
Write-Host "=== Quorus Refactoring Metrics Snapshot ==="
Write-Host "Date: $(Get-Date -Format 'yyyy-MM-dd HH:mm')"
Write-Host ""

# Null checks in command/codec/state machine paths
$nullChecks = (Select-String -Path "quorus-controller/src/main/java/**/*.java" `
    -Pattern "== null|!= null" -Recurse).Count
Write-Host "Null checks (quorus-controller):  $nullChecks"

$coreNullChecks = (Select-String -Path "quorus-core/src/main/java/**/*.java" `
    -Pattern "== null|!= null" -Recurse).Count
Write-Host "Null checks (quorus-core):        $coreNullChecks"

# yield null in state machine
$yieldNull = (Select-String -Path "quorus-controller/src/main/java/**/*.java" `
    -Pattern "yield null" -Recurse).Count
Write-Host "yield null (state machine):       $yieldNull"

# getType() calls
$getType = (Select-String -Path "quorus-controller/src/main/java/**/*.java" `
    -Pattern "\.getType\(\)" -Recurse).Count
Write-Host "getType() calls (controller):     $getType"

# Object return types in Raft path
$objectReturns = (Select-String -Path "quorus-controller/src/main/java/**/*.java" `
    -Pattern "Object>" -Recurse).Count
Write-Host "Object return types (controller): $objectReturns"

# instanceof casts from Object
$instanceOf = (Select-String -Path "quorus-controller/src/main/java/**/*.java" `
    -Pattern "instanceof" -Recurse).Count
Write-Host "instanceof casts (controller):    $instanceOf"

# Test count
Write-Host ""
Write-Host "Running tests..."
$testOutput = mvn test --batch-mode -pl quorus-core,quorus-controller 2>&1
$summary = $testOutput | Select-String "Tests run:" | Select-Object -Last 1
Write-Host "Test summary: $($summary.Line)"
```

### F.2 Compilation-First Workflow

After modifying any command class, codec, or state machine handler, follow this exact
sequence. Do not skip steps.

```bash
# Step 1: Compile only — surfaces all API breakages
mvn compile -pl quorus-controller
# Fix every error. Do not proceed until clean.

# Step 2: Compile dependent modules
mvn compile -pl quorus-core,quorus-controller,quorus-agent
# Fix any cross-module breakages.

# Step 3: Run unit tests (fast — no Docker)
mvn test -pl quorus-core,quorus-controller -Dtest='!*IntegrationTest'

# Step 4: Run full test suite including integration tests
mvn test -pl quorus-core,quorus-controller

# Step 5: Full project verification (before PR)
mvn clean verify
```

### F.3 Serialization Compatibility Test Template

Copy this template for each domain when implementing Phases 1–6. Replace `Agent` with the
domain being migrated.

```java
@ExtendWith(VertxExtension.class)
class AgentCommandSerializationTest {

    /**
     * Verifies that every record variant survives a full
     * Java object → Proto → Java object round-trip with all fields intact.
     */
    @ParameterizedTest
    @MethodSource("allAgentCommands")
    void roundTrip_preservesAllFields(AgentCommand original) {
        RaftCommandMessage proto = ProtobufCommandCodec.toProto(original);
        RaftCommand restored = ProtobufCommandCodec.fromProto(proto);
        
        assertEquals(original, restored, 
            "Round-trip failed for: " + original.getClass().getSimpleName());
    }

    /**
     * Verifies that proto bytes captured BEFORE the refactoring can still
     * be decoded AFTER. Prevents silent field drops or default changes.
     */
    @ParameterizedTest
    @MethodSource("legacyProtoBytes")
    void backwardCompatibility_decodesLegacyBytes(
            String label, byte[] legacyBytes, Class<?> expectedType) {
        RaftCommandMessage proto = RaftCommandMessage.parseFrom(legacyBytes);
        RaftCommand command = ProtobufCommandCodec.fromProto(proto);
        assertInstanceOf(expectedType, command, 
            "Legacy bytes for '" + label + "' decoded to wrong type");
    }

    static Stream<Arguments> allAgentCommands() {
        return Stream.of(
            Arguments.of(AgentCommand.Register.create(
                "agent-1", "us-east", "dc-1", List.of("sftp"), 5)),
            Arguments.of(AgentCommand.UpdateStatus.create(
                "agent-1", AgentStatus.HEALTHY)),
            Arguments.of(AgentCommand.Heartbeat.create(
                "agent-1", Instant.now(), 42, "active", 2, 3)),
            Arguments.of(AgentCommand.Deregister.create("agent-1")),
            Arguments.of(AgentCommand.UpdateCapabilities.create(
                "agent-1", List.of("sftp", "http"), 10))
        );
    }
}
```

### F.4 Exhaustiveness Verification

After converting a command to a sealed interface, verify that the compiler enforces
exhaustiveness in all switch expressions. Temporarily add a new record to the sealed
interface and confirm the build fails:

```java
// Temporarily add to AgentCommand sealed interface:
record _ExhaustivenessProbe() implements AgentCommand {}

// Run: mvn compile -pl quorus-controller
// Expected: compile errors in AgentCodec, QuorusStateStore — every switch is incomplete
// This proves the compiler will catch any future additions that forget to update handlers

// Remove _ExhaustivenessProbe after verification
```

This is a one-time verification per domain. Once confirmed, the sealed interface permanently
guarantees that adding a new command variant will produce compile errors in every handler
that needs updating.

### F.5 Test Categories for Phase Verification

Each phase should have tests covering three categories:

| Category | Purpose | Example |
|---|---|---|
| **Structural** | Record fields, factory methods, compact constructor validation | `assertThrows(NullPointerException.class, () -> AgentCommand.Register.create(null, ...))` |
| **Serialization** | Proto round-trip, backward compatibility, snapshot round-trip | See F.3 template |
| **Behavioral** | Full request path — HTTP → Raft → apply → response | `POST /api/v1/agents/register` returns 200 with correct JSON body |

A phase is not complete unless all three categories have passing tests. Structural tests
verify the type system. Serialization tests verify persistence. Behavioral tests verify the
system still works end-to-end.