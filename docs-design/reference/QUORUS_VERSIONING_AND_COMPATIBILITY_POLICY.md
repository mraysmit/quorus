<img src="../../docs/quorus-logo.png" alt="Quorus" width="120"/>

# Quorus Versioning and Compatibility Policy

**Version:** 1.0  
**Date:** 2026-09-01  
**Author:** Mark Ray-Smith — Cityline Ltd  
**License:** Apache 2.0

## Purpose

This policy governs every contract that can outlive one process or be consumed by another component. The executable registry is `SchemaVersionRegistry`; this document defines how its values may change.

## Controlled contracts

| Contract | Current | Compatibility rule | Phase 0 representation |
|---|---:|---|---|
| Raft command envelope | 1 | Readers accept legacy version 0 and current version 1; writers emit 1 | Protobuf `schema_version` |
| State snapshot | 1 | Readers accept legacy missing/0 and current 1; writers emit 1 | JSON `schemaVersion` |
| REST API | 1 | Additive changes remain in `/api/v1`; breaking changes require a new major path | OpenAPI 3.1 |
| Configuration | 1 | Additive keys require safe defaults; renamed keys require an explicit migration window | properties and `QUORUS_*` environment variables |
| Workflow definition | 1 | New optional fields are additive; changed meaning or required fields require migration | workflow schema/version field in the next workflow change |
| Agent protocol | 1 | Controller and agent must negotiate compatible major versions before assignment | registration version/capability metadata |

## Change rules

1. Persisted writers emit only the registry's current writable version.
2. Readers reject versions newer than their current version before applying authoritative state.
3. Removal or reinterpretation of a field is breaking. A new version, migration, rollback plan, mixed-version test and release note are mandatory.
4. Unknown Protobuf fields must be preserved by supported read/write paths where the library permits it. Field numbers are never reused.
5. API additions require OpenAPI and path-parity tests in the same change. Breaking REST changes require a new major API path and an overlap period.
6. Configuration values must have one canonical property, one canonical environment mapping and a documented precedence order.
7. Compatibility evidence includes previous-reader/current-writer, current-reader/previous-writer, future-version rejection and restart recovery tests.
8. Downgrade is allowed only when the target release can read every stored command and snapshot version present in the cluster.

## Ownership

The control-plane maintainers own the registry. Protocol, workflow, agent and deployment owners approve changes to their contracts. Release approval must record all registry changes in the evidence manifest.
