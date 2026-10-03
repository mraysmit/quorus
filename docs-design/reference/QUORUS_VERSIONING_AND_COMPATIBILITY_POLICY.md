<img src="../../docs/quorus-logo.png" alt="Quorus" width="120"/>

# Quorus Versioning and Compatibility Policy

**Version:** 1.3  
**Date:** 2026-10-03  
**Author:** Mark Ray-Smith — Cityline Ltd  
**License:** Apache 2.0  
**Status:** Current

## Purpose

This policy governs every contract that can outlive one process or be consumed by another component. The executable registry is `SchemaVersionRegistry`; this document defines how its values may change.

## Product version

The product version is the root `pom.xml` version (register decision `DR-Q5`). There is one, and it is not a setting:

- The build writes it into `quorus-build.properties` in `quorus-core`, the only filtered resource, and `ProductVersion.get()` reads it. A build that did not filter the resource fails at first use instead of reporting a placeholder.
- The controller reports it at `/api/v1/info` (`api.quorusVersion`) and `/health` (`version`) and logs it at startup. The agent reports it in its registration, at its `/status` endpoint and in its startup log.
- `quorus.version`, `quorus.agent.version` and `AGENT_VERSION` are no longer read. Setting them has no effect.
- It is not written into replicated state. The state machine's `version` metadata key keeps its own default and is a state value, not the version of any node's binary: nodes of one cluster may run different builds during an upgrade, and they must still hold identical state.
- The OpenAPI `info.version` is the version of the API contract, a different thing, and changes only when the contract does.

Tests compare what each component reports with the pom (`ProductVersionTest`, `ProductVersionReportingTest`, `AgentProductVersionTest`).

## Controlled contracts

| Contract | Current | Compatibility rule | Phase 0 representation |
|---|---:|---|---|
| Raft command envelope | 3 | Readers accept versions 0 to 3; writers emit 3 | Protobuf `schema_version` |
| State snapshot | 3 | Readers accept missing/0 to 3; writers emit 3 | JSON `schemaVersion` |
| REST API | 1 | Additive changes remain in `/api/v1`; breaking changes require a new major path | OpenAPI 3.1 |
| Configuration | 1 | Additive keys require safe defaults; renamed keys require an explicit migration window | properties and `QUORUS_*` environment variables |
| Workflow definition | 1 | New optional fields are additive; changed meaning or required fields require migration | workflow schema/version field in the next workflow change |
| Agent protocol | 1 | Controller and agent must negotiate compatible major versions before assignment | registration version/capability metadata |

The table states the values in `SchemaVersionRegistry`; if they differ, the registry is right and this table is corrected.

## Contract changes so far

| Contract | Change | Commit | Upgrade consequence |
|---|---|---|---|
| Configuration | Layered configuration: packaged defaults, profile resource, `QUORUS_*` environment, explicit overrides. `AppConfig.get()`, `AgentConfig.get()` and JVM system properties as a configuration channel were removed | `b35fb25` (2026-09-03) | Breaking: a deployment that set Quorus configuration through `-D` system properties must move to `QUORUS_*` environment variables or properties files |
| Raft command envelope, state snapshot | Version 3: the service-connection, secret-reference and security-event key format changed (R2 registry isolation) | `1a8f2b3` (2026-09-04) | Coordinated upgrade, not a rolling one: a version-2 binary rejects version-3 entries. See [Security Deployment Guide §11](../../docs/QUORUS_SECURITY_DEPLOYMENT_GUIDE.md#11-registry-isolation-upgrade-and-recovery) |

## Change rules

1. Persisted writers emit only the registry's current writable version.
2. Readers reject versions newer than their current version before applying authoritative state.
3. Removal or reinterpretation of a field is breaking. A new version, migration, rollback plan, mixed-version test and release note are mandatory.
4. Unknown Protobuf fields must be preserved by supported read/write paths where the library permits it. Field numbers are never reused.
5. API additions require OpenAPI and path-parity tests in the same change. Breaking REST changes require a new major API path and an overlap period.
6. Configuration values must have one canonical property, one canonical environment mapping and a documented precedence order.
7. Compatibility evidence includes previous-reader/current-writer, current-reader/previous-writer, future-version rejection and restart recovery tests.
8. Downgrade is allowed only when the target release can read every stored command and snapshot version present in the cluster.
9. Quorus follows each six-monthly Java feature release within its update window (decision `RT-Q3`). A consensus engine that Quorus consumes, such as QRaft, must not require a newer Java release than Quorus itself.

## Ownership

The control-plane maintainers own the registry. Protocol, workflow, agent and deployment owners approve changes to their contracts. Release approval must record all registry changes in the release notes.
