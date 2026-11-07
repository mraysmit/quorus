<img src="../../docs/quorus-logo.png" alt="Quorus" width="120"/>

# ADR-0008: Schema 3 Requires a Coordinated Upgrade

**Version:** 1.0  
**Date:** 2026-11-03  
**Author:** Mark Ray-Smith — Cityline Ltd  
**License:** Apache 2.0

**Status:** Accepted. It records the upgrade model delivered with the R2 registry isolation on 2026-09-04 (commit `23a258c0`), written down under register item DR-D4 in the [Outstanding Work Register](../task/QUORUS_OUTSTANDING_WORK_REGISTER_2026-11-07.md).

**Relates to:** the [Versioning and Compatibility Policy](../reference/QUORUS_VERSIONING_AND_COMPATIBILITY_POLICY.md), which holds the change rules for every versioned contract; the operator procedure in [Security Deployment Guide §11](../../docs/QUORUS_SECURITY_DEPLOYMENT_GUIDE.md#11-registry-isolation-upgrade-and-recovery); [Architecture Specification §5.3](../../docs/QUORUS_ARCHITECTURE_SPECIFICATION.md#53-command-invariants); [ADR-0006](ADR-0006-RAFT-STORAGE-RAFTLOG-AND-SNAPSHOT-SIDECAR.md), whose WAL and snapshot files hold the versioned data.

## Context

Service connections, secret references and security events were stored in replicated state under keys that joined the tenant and the resource ID with a dot. A dotted tenant or resource ID made the split ambiguous, so one tenant's record could be read, or overwritten, under another tenant's key. R2 replaced these with versioned keys that hold the tenant and the resource separately, and moved the stored records to the new keys.

Every replicated command and every snapshot carries a schema version: a Protobuf `schema_version` field and a JSON `schemaVersion` field. `SchemaVersionRegistry` holds, for each contract, the oldest version a reader accepts and the one version writers emit. Before R2 both were 2.

A controller that applies a command or snapshot with the new keys, but reads them with the old rules, would not fail. It would interpret the state wrongly. It could even write a legacy key after the cluster had migrated, and recreate the ambiguity R2 removed.

## Decision

1. **The Raft command envelope and the state snapshot move to version 3.** Writers emit only 3. Readers accept 0 to 3, so a schema-3 binary recovers every older WAL and snapshot.

2. **A reader rejects a version newer than its own before applying anything.** The command codec and the snapshot loader call `SchemaVersionRegistry.requireReadable` first. A schema-2 binary therefore refuses a version-3 entry or snapshot instead of misreading it. The version bump is what fences the older binaries out.

3. **The upgrade is coordinated, not rolling.** All controllers stop, their data directories are preserved, and all start on the schema-3 release before requests resume. A cluster never runs schema-2 and schema-3 controllers together.

4. **Migration happens through Raft, on the first registry mutation.** The first successful schema-3 registry change migrates every validated legacy record, together with itself, in one replicated state application, and sets the replicated marker `phase4.registry.schema=2`. Every replica applies the same entry, so every replica migrates identically. Reads do not migrate. Ownership comes from the stored payload and the complete legacy address, never from splitting a key at a dot.

5. **Ambiguity fails closed.** Missing or mismatched ownership, or old and new records that conflict, fail the mutation with no partial write. Once the marker is set, a legacy-key mutation is refused (`REGISTRY_SCHEMA_CONFLICT`). There is no automatic repair: an operator preserves the evidence and escalates.

6. **Rolling back only the binaries is unsupported once schema 3 has been written.** Restoring the preserved pre-upgrade directories is a recovery decision that must reconcile every operation since; it is not a lossless rollback.

## Alternatives considered

- **A rolling upgrade behind a cluster feature flag.** Upgraded controllers would keep writing version 2 until every member reported support, then switch. Rejected for now: the controllers do not exchange or agree a cluster version, so there is nothing to gate the switch on. Building that is a larger change than the registry fix it would enable.
- **Migrate each controller's state at startup.** Rejected: a startup migration is local to one node. Replicas could migrate differently, or at different points in the log. A migration that is a replicated entry is applied at the same index on every replica.
- **Keep the legacy keys readable and writable alongside the new ones.** Rejected: the ambiguity of the dotted key was the defect. Keeping it writable would keep the defect.
- **Infer ownership by splitting the legacy key.** Rejected: the split is exactly what was ambiguous. Ownership comes from the stored payload, or the migration fails.

## Consequences

- Upgrading to schema 3 needs a maintenance window: quiesce mutations and agent traffic, stop every controller, preserve every directory, then start all on the new release (Security Deployment Guide §11).
- An old binary started against migrated state fails at recovery rather than serving wrong data.
- A record already overwritten through a legacy key collision before the upgrade cannot be reconstructed from the row that survived.
- The next breaking change to these contracts follows the same pattern under the policy's change rules: a new version, a migration, a rollback plan, a mixed-version test and a release note. A rolling upgrade becomes possible only once the cluster can agree a version.
- QRaft takes over the log under [ADR-0011](ADR-0011-CONSENSUS-VIA-QRAFT-GENERIC-ENGINE.md), but the payload versions stay Quorus's: QRaft treats commands and snapshots as opaque, and Quorus keeps its codec and schema version.

## Revision history

| Version | Date | Changes |
|---|---|---|
| 1.0 | 2026-11-03 | Records the schema-3 upgrade model delivered with R2 on 2026-09-04, under register item DR-D4 |
