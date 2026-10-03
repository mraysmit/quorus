<img src="../../docs/quorus-logo.png" alt="Quorus" width="120"/>

# ADR-0002: Transfer Progress Checkpointing

**Version:** 1.1  
**Date:** 2026-10-03  
**Author:** Mark Ray-Smith — Cityline Ltd  
**License:** Apache 2.0

**Status:** Accepted

## Context

Technology operations require timely transfer progress, but committing every byte or chunk through Raft would create avoidable consensus load. Progress must remain monotonic and terminal outcomes must be durable.

## Decision

Agents emit high-frequency progress as telemetry and submit bounded authoritative checkpoints. A checkpoint is committed when its byte/time threshold is reached, before a lifecycle transition, and at terminal completion or failure. State application rejects regressions, values above the declared total and updates to terminal transfers. Terminal state application requires the final progress checkpoint first.

Phase 2 adds attempt sequence numbers and fencing. Until then, Phase 0 progress is suitable only for the trusted, single-active-assignment baseline.

**Status update, 2026-10-03:** attempt identity and fencing are implemented. Every report carries an attempt ID, fencing generation and report sequence (`TransferAttemptCommand.Report`), and state application rejects a stale fence or an out-of-order sequence. Agents report progress while a transfer runs. Lease expiry and automatic reassignment are still Phase 2 work (register items `P2-01` to `P2-03`).

## Consequences

Dashboards may show telemetry newer than the last durable checkpoint and must label that distinction. Recovery resumes from the committed checkpoint and reconciles against destination/protocol capability; it must not infer successful publication from a progress value alone.
