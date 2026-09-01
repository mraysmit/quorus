<img src="../../docs/quorus-logo.png" alt="Quorus" width="120"/>

# ADR-0001: Event Storage

**Version:** 1.0  
**Date:** 2026-09-01  
**Author:** Mark Ray-Smith — Cityline Ltd  
**License:** Apache 2.0

**Status:** Accepted for Phase 0 foundation

## Context

Raft state is authoritative operational state, but enterprise audit, transfer timelines and high-volume telemetry have different retention, query and immutability needs. Storing every event in the Raft state would enlarge snapshots and couple operational queries to consensus.

## Decision

Raft stores authoritative current state and the commands needed to reproduce it. Domain events are emitted from committed state application through a durable outbox boundary introduced in a later phase. Audit events use an append-only, independently retained evidence store. Metrics, logs and traces use the observability platform and are never authoritative. Every event carries tenant, transfer, attempt, correlation, causation, schema version and committed Raft position when applicable.

Phase 0 does not claim a durable external event store. It establishes identifiers, schema/version policy and prevents telemetry from becoming an alternate source of truth.

## Consequences

Consumers can rebuild projections without querying Raft internals. Delivery is at least once, so event IDs and consumer idempotency are mandatory. A committed state change is never rolled back because an external telemetry or event sink is unavailable.
