<img src="../../docs/quorus-logo.png" alt="Quorus" width="120"/>

# Quorus Architecture Decision Records

**Version:** 1.0  
**Date:** 2026-11-03  
**Author:** Mark Ray-Smith — Cityline Ltd  
**License:** Apache 2.0  
**Status:** Index of every ADR in this directory

An ADR records one decision, why it was taken and what it rules out. A decision stands until a later ADR supersedes it. The open decisions, and the tasks that deliver each ADR, are in the [Outstanding Work Register](../task/QUORUS_OUTSTANDING_WORK_REGISTER_2026-11-07.md#3-decision-log).

| ADR | Decision | Status | Last revised | Supersedes / superseded by |
|---|---|---|---|---|
| [ADR-0001](ADR-0001-EVENT-STORAGE.md) | Event storage | Accepted for the Phase 0 foundation | 2026-09-01 | — |
| [ADR-0002](ADR-0002-PROGRESS-CHECKPOINTING.md) | Transfer progress checkpointing | Accepted | 2026-10-03 | — |
| [ADR-0003](ADR-0003-IDENTITY-BOUNDARY.md) | Enterprise identity boundary | Accepted; Phase 1 foundation implemented | 2026-10-03 | — |
| [ADR-0004](ADR-0004-SECRET-PROVIDERS.md) | Secret provider boundary | Accepted; implemented in Phase 4 | 2026-09-01 | — |
| [ADR-0005](ADR-0005-DEPLOYMENT-OWNERSHIP.md) | Agent deployment ownership | Accepted direction; implementation begins in Phase 5 | 2026-09-01 | — |
| [ADR-0006](ADR-0006-RAFT-STORAGE-RAFTLOG-AND-SNAPSHOT-SIDECAR.md) | Raft storage on raftlog-core with a Quorus snapshot sidecar | Accepted; records the current design | 2026-11-03 | To be superseded by ADR-0011 at `CE-10` |
| [ADR-0007](ADR-0007-LAYERED-CONFIGURATION.md) | Layered configuration without system properties | Accepted | 2026-11-07 | — |
| [ADR-0008](ADR-0008-SCHEMA-3-COORDINATED-UPGRADE.md) | Schema 3 requires a coordinated upgrade | Accepted | 2026-11-03 | — |
| [ADR-0009](ADR-0009-RUNTIME-TRUST-STATE-SCOPE.md) | Runtime trust state is node-local | Accepted | 2026-11-03 | — |
| [ADR-0010](ADR-0010-WORKFLOW-YAML-SEMANTICS.md) | Workflow YAML semantics | Accepted | 2026-11-03 | — |
| [ADR-0011](ADR-0011-CONSENSUS-VIA-QRAFT-GENERIC-ENGINE.md) | Consensus through the generic QRaft engine | Accepted as direction; delivery is workstream `CE` | 2026-11-03 | Will supersede the in-repository Raft engine and ADR-0006 at `CE-10` |
| [ADR-0012](ADR-0012-JAVA-RUNTIME-AND-STRUCTURED-CONCURRENCY.md) | Leave Vert.x for Java 27 structured concurrency | Accepted; delivery is workstream `RT` | 2026-10-03 | — |
| [ADR-0013](ADR-0013-BREAK-GLASS-ELEVATION.md) | Break-glass elevation for direct mTLS identities | Accepted; delivered by `SEC-06` | 2026-11-03 | — |

ADR-0001 to ADR-0005 predate the Supersedes and Alternatives conventions, and do not yet carry those fields (register item DR-D4).

## Writing an ADR

- Take the next number, and name the file `ADR-NNNN-SHORT-TITLE.md` with the standard header.
- Give a **Status** line saying whether the decision is proposed, accepted or superseded, and which register decision or task it records.
- Use the sections Context, Decision, Alternatives considered, Consequences and Revision history.
- Record why the decision was taken, and link to the specification or reference that holds the detail rather than repeating it.
- When a decision replaces an earlier one, say so in both ADRs, and update this index in the same change.
