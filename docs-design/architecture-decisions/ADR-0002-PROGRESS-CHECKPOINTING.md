<img src="../../docs/quorus-logo.png" alt="Quorus" width="120"/>

# ADR-0002: Transfer Progress Checkpointing

**Version:** 1.0  
**Date:** 2026-09-01  
**Author:** Mark Ray-Smith — Cityline Ltd  
**License:** Apache 2.0

**Status:** Accepted

## Context

Technology operations require timely transfer progress, but committing every byte or chunk through Raft would create avoidable consensus load. Progress must remain monotonic and terminal outcomes must be durable.

## Decision

Agents emit high-frequency progress as telemetry and submit bounded authoritative checkpoints. A checkpoint is committed when its byte/time threshold is reached, before a lifecycle transition, and at terminal completion or failure. State application rejects regressions, values above the declared total and updates to terminal transfers. Terminal state application requires the final progress checkpoint first.

Phase 2 adds attempt sequence numbers and fencing. Until then, Phase 0 progress is suitable only for the trusted, single-active-assignment baseline.

## Consequences

Dashboards may show telemetry newer than the last durable checkpoint and must label that distinction. Recovery resumes from the committed checkpoint and reconciles against destination/protocol capability; it must not infer successful publication from a progress value alone.
