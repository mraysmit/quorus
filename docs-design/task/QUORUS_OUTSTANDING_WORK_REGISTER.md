<img src="../../docs/quorus-logo.png" alt="Quorus" width="120"/>

# Quorus Outstanding Work Register

**Version:** 1.1
**Date:** 2026-09-07
**Author:** Mark Ray-Smith — Cityline Ltd
**License:** Apache 2.0
**Status:** Active — consolidated view of every open task across the current and archived planning documents

---

## 1. Purpose and Authority

This register is the single consolidated list of outstanding work drawn from all five planning
documents that existed in [docs-design/task/](.) on 2026-09-07. It exists so that open work can
be found in one place rather than reconstructed from five documents written at different times
under different status vocabularies.

Following that consolidation, `task/` holds only the two current documents — the enterprise plan
and this register. The other three plans and the sealed-record design were moved to
[../archive/](../archive/) on 2026-09-07: their open work is carried here, and they are retained
for provenance and technical reference rather than as live backlogs.

**This register is derivative, not normative.** Precedence is unchanged:

1. [Quorus Architecture Specification](../../docs/QUORUS_ARCHITECTURE_SPECIFICATION.md) and
   [Quorus REST API Specification](../../docs/QUORUS_REST_API_SPECIFICATION.md) remain the
   canonical requirements.
2. [QUORUS_ENTERPRISE_IMPLEMENTATION_PLAN.md](QUORUS_ENTERPRISE_IMPLEMENTATION_PLAN.md)
   remains the controlling delivery plan: phase sequencing, exit gates, the §6 Definition of
   Done, and the §6.1 mandatory TDD protocol are defined there and are **not** restated or
   weakened here.
3. This register lists and identifies the open items. Where it disagrees with a source
   document, the source document's requirement wins and this register is corrected.

Closing an item here does not close a phase exit gate. Phase closure follows
[§23 Plan Governance](QUORUS_ENTERPRISE_IMPLEMENTATION_PLAN.md) of the enterprise plan.

### Source documents

| Document | Role | Contribution to this register |
|---|---|---|
| [QUORUS_ENTERPRISE_IMPLEMENTATION_PLAN.md](QUORUS_ENTERPRISE_IMPLEMENTATION_PLAN.md) v1.25 | Controlling roadmap | Sections A–D, F |
| [QUORUS_OPENTELEMETRY_INTEGRATION_TESTING_PLAN.md](../archive/QUORUS_OPENTELEMETRY_INTEGRATION_TESTING_PLAN.md) v2.6 — archived | Observability backlog and collector/test reference | Section E |
| [QUORUS_TLS_SECURITY_ROUTES.md](../archive/QUORUS_TLS_SECURITY_ROUTES.md) v1.0 — archived | Historical Stage 6 detail | Section F (absorbed), route detail for Phase 7 |
| [QUORUS_ALPHA_IMPLEMENTATION_PLAN.md](../archive/QUORUS_ALPHA_IMPLEMENTATION_PLAN.md) v1.9 — archived | Historical alpha evidence | No open items; see §8 |
| [QUORUS_SEALED_RECORD_COMMANDS_DESIGN.md](../archive/QUORUS_SEALED_RECORD_COMMANDS_DESIGN.md) v1.0 — archived | Completed refactoring design | Section G (one deferred item) |

Because four of the five sources are now archived, this register is the working list. Archived
documents are not edited to add new work: anything newly discovered goes to the enterprise plan
or, for telemetry items, directly into Section E here.

### Status vocabulary

| Marker | Meaning |
|---|---|
| 🔴 **Release blocker** | Blocks the enterprise release claim; a critical or applicable high canonical gap |
| 🟡 **Phase blocker** | Prevents its own phase exit gate |
| 🟠 **Backlog** | Required for the phase but not currently blocking progress on it |
| 🟢 **Deferred** | Explicitly out of scope for the first enterprise release; see enterprise plan §22 |
| 🔵 **Doc correction** | The code is correct; a planning document is stale and misleads |

Every implementation item below is delivered under the §6.1 mandatory TDD protocol:
preserved behavioral red through a real HTTP, agent, protocol, or cluster boundary before
implementation, then green, refactor, and regression, with retained evidence. Awaitility,
sleeps as synchronization, and non-Vert.x polling are not permitted in new or remediated tests.

---

## 2. Summary

| Section | Area | Open items | Blocking level |
|---|---|---|---|
| A | R1 durability acceptance | 3 | 🔴 Release blocker |
| B | Phase 2 — attempts, integrity, reconciliation | 12 | 🟡 Phase blocker |
| C | Phase 3 — transfer operations telemetry | 12 | 🟡 Phase blocker |
| D | Phases 5–12 — not started | 8 phases | 🔴 / 🟡 by phase |
| E | Observability and logging backlog | 14 | 🟠 Backlog |
| F | Absorbed and superseded historical tasks | 8 | — reference only |
| G | Deferred and research | 8 | 🟢 Deferred |
| H | Documentation corrections | 6 | 🔵 Doc correction |

**Phase position:** Phases 0, 1 and 4 complete. Phases 2 and 3 in progress. Phases 5–12 not
started. The R1 remediation slice is complete in code but its acceptance gates are open.

---

## 3. Section A — R1 Durability Acceptance (Release Blockers)

Remediation slices R2–R6 are implementation-complete. R6 final acceptance was verified from a
clean detached worktree at revision `b604505`: 2,437 tests, zero failures or errors, two
existing explicit skips, and all five configured JaCoCo gates
([R6 evidence](../evidence/r6-final-acceptance-2026-09-05.md)).

R1 durable snapshots is verified in code on Windows. Its acceptance gates are **not** closed,
and they are the reason no enterprise release claim can currently be made.

| ID | Item | Detail | Level |
|---|---|---|---|
| **R1-1** | Container-recreation acceptance | Prove durable snapshot and coordinate recovery across controller container destruction and recreation against a persistent volume, not a local working tree | 🔴 |
| **R1-2** | Production-filesystem acceptance | Repeat R1 recovery, retained-tail, corruption and concurrent-mutation cases on the supported production filesystem and storage class, not the Windows development host | 🔴 |
| **R1-3** | Machine power-loss acceptance | Prove committed state and snapshot/WAL coordinates survive unclean host power loss; establish that interrupted publication cannot silently replace good state | 🔴 |

**Constraints carried from the enterprise plan:**

- `raftlog-core` (external RaftLog 1.2.0, sister project at `../raftlog`) is the only WAL.
  Internal RocksDB and memory backends and the RocksDB JNI dependency are removed; configuration
  accepts only `raftlog`. Do not reintroduce an internal backend to satisfy a test.
- Existing persistent environments have **not** been inventoried. Preserve their storage before
  any recovery or rollback attempt. Code rollback cannot recover already-deleted WAL records.
- The two Raft regression cases without preserved red evidence remain historical process
  deviations requiring explicit disposition. They cannot be relabelled as historical TDD.

---

## 4. Section B — Phase 2 Open Items

**Phase 2 — Transfer Attempts, Fencing, Integrity, and Reconciliation.** Milestone M1.
Gaps `ARCH-02`, `ARCH-05`, `ARCH-06`, `ARCH-17`, `API-03`, `API-07`, `API-12`.

Delivered on 2026-09-02: immutable authoritative attempts with lease and fencing generation,
atomic assignment creation, the fenced agent poll/report protocol, atomic multi-entity lifecycle
reporting, version 2 protobuf command and snapshot contracts with legacy readers, and the
tenant-checked attempt read resources. R3 additionally closed pre-execution failure reporting.

Open:

| ID | Item | Acceptance | Level |
|---|---|---|---|
| **P2-01** | Automatic lease expiry and safe reassignment | Expired lease is detected by an authoritative scheduler and the attempt is reassigned or terminated without a second live fence | 🟡 |
| **P2-02** | Lease renewal through the external agent protocol | A live agent renews its lease over the real agent boundary; renewal after expiry is rejected | 🟡 |
| **P2-03** | Mutation coverage for specialized assignment actions | Offer, accept, reject, start, progress, complete, fail, cancel, and lease-renew each have authoritative mutation and rejection coverage | 🟡 |
| **P2-04** | Submission idempotency keys | Idempotency store scoped to identity, tenant, request fingerprint and expiry; duplicate fingerprint returns the original result; key reuse with different content fails | 🟡 |
| **P2-05** | Retry classification and policy | Retriable versus terminal classification, maximum attempts, maximum elapsed time, backoff, jitter, and terminal conditions | 🟡 |
| **P2-06** | Integrity verification | Checksum/digest verification before success; an integrity failure can never become `SUCCEEDED` | 🔴 |
| **P2-07** | Destination staging and atomic publication | Staged publication abstraction with overwrite, partial-file and cleanup policy | 🔴 |
| **P2-08** | Reconciliation service and operator actions | Lease expiry, agent disappearance, controller failover, timeout, lost completion and ambiguous publication resolve to one authoritative outcome; uncertainty becomes `RECONCILIATION_REQUIRED`, never assumed success or blind retry | 🔴 |
| **P2-09** | Protocol capability enforcement | Protocol-specific retry and resume safety declared; unsupported capabilities fail **before** execution | 🟡 |
| **P2-10** | Migration tooling for the attempt model | Existing jobs, assignments and snapshots migrate to the versioned attempt model, with rollback | 🟡 |
| **P2-11** | Classified terminal reasons and complete attempt evidence | Every terminal transfer carries complete attempt and publication evidence | 🟡 |
| **P2-12** | Failure-path test lane | Crash, network partition, lost response, lease expiry, duplicate report, and failover-during-each-transition tests pass | 🟡 |

**Exit gate:** Quorus can safely explain what ran, where it ran, which attempt is authoritative,
what was published, and what requires reconciliation. It still does not claim exactly-once
external execution.

---

## 5. Section C — Phase 3 Open Items

**Phase 3 — Critical Transfer Operations Telemetry and Alerting.** Contributes to M2.
Gaps `ARCH-11`, `ARCH-12`, `API-03`, `API-04`.

Delivered across seven retained red/green cycles: operational business context on submission,
tenant-checked `GET /api/v1/transfers/{jobId}/progress` with honest `UNKNOWN` semantics for
missing telemetry, validated controller freshness/stall policy with disclosed effective windows,
the snapshot-included event ledger with `TRANSFER_SUBMITTED`, `TRANSFER_ASSIGNED`,
`TRANSFER_ACCEPTED`, `TRANSFER_STARTED` and `TRANSFER_PROGRESS` through
`GET /api/v1/transfers/{jobId}/events`, and the configured active-transfer stall boundary with
stable `conditionSince` and `stallDurationSeconds`.

> **Sequencing constraint.** Phase 3 was started by explicit direction while Phase 2 lease
> automation, publication, integrity, retry policy and reconciliation remain open. Phase 3 work
> **MUST NOT** claim or depend on those unfinished guarantees.

Open:

| ID | Item | Acceptance | Level |
|---|---|---|---|
| **P3-01** | Complete the lifecycle event vocabulary | Remaining ordered event types defined and emitted with schema; timeline order correct across retries, failover and agent restart | 🟡 |
| **P3-02** | Durable stall detection and event emission | Stall is detected and emitted as a durable event by the controller, not only computed on read | 🟡 |
| **P3-03** | Throughput windows | Windowed throughput distinct from cumulative average | 🟡 |
| **P3-04** | Calibrated ETA confidence | ETA carries an explicit confidence qualifier; unknown size and lost telemetry never yield a confident ETA | 🟡 |
| **P3-05** | Configurable deadline-risk prediction | At-risk and late conditions derived from configured policy, independent of lifecycle state | 🟡 |
| **P3-06** | Operational query collections | Critical, at-risk, late, stalled and degraded collection APIs with server pagination and filtering | 🟡 |
| **P3-07** | Transfer timeline read model | End-to-end timeline joining attempts, integrity, publication and reconciliation context | 🟡 |
| **P3-08** | Resumable filtered event stream | Server-sent events resume from the last acknowledged position and explicitly report retention gaps | 🟡 |
| **P3-09** | Alert policy and lifecycle | Deduplication, acknowledgement, suppression, escalation, resolution and notification-delivery evidence; every critical alert has owner, evidence, deadline impact and runbook | 🟡 |
| **P3-10** | Backpressure and cardinality control | Slow consumers cannot exhaust controller memory; metric cardinality is bounded | 🟡 |
| **P3-11** | Event and sample retention and archival | Separate retention for authoritative lifecycle events and high-frequency telemetry samples | 🟡 |
| **P3-12** | Service-level reporting | Timeliness, success, retry, integrity, publication, alert response and telemetry completeness reports | 🟡 |

**Exit gate:** Operations can detect, understand, own, and act on a critical transfer before its
deadline is missed. Infrastructure monitoring alone is not accepted as completion.

**Open defect note:** a non-reproducing three-node `LeaderGuardHandlerTest` fixture startup
timeout (15-second wait) was retained rather than hidden during Phase 3 verification. It has not
been root-caused. Track it with the Phase 3 lane; do not treat a passing retry as resolution.

---

## 6. Section D — Phases 5 to 12 (Not Started)

Each phase below is unstarted. Full scope, deliverables, verification and exit gates are in the
enterprise plan; this table gives the identity, dependency and blocking level so that the work is
visible in one list.

| Phase | Title | Milestone | Gaps | Depends on | Level |
|---|---|---|---|---|---|
| **5** | Secure Agent Enrollment, Deployment, and Fleet Operations | M2 | `ARCH-15`, `ARCH-16`, `API-05` | Phases 1, 4 | 🔴 |
| **6** | Complete REST Control Plane and Integration Contract | M3 | `ARCH-18`, `API-01`, `API-03`, `API-08`, `API-09`, `API-11`, `API-12`, `API-13`, `API-14` | Phases 2, 3, 4, 5 | 🔴 |
| **7** | Route, Workflow, Scheduling, and Business-Calendar Automation | M3 | `ARCH-04`, `API-08`, `API-10` | Phase 6 | 🟡 |
| **8** | Durability, High Availability, Backup, and Disaster Recovery | M3 | `ARCH-07`, `ARCH-10`, `API-13`, `API-14` | Phases 0, 2 | 🔴 |
| **9** | Governance, Audit, Evidence, and Enterprise Integrations | M4 | `API-11` plus design governance requirements | Phases 1, 3, 6 | 🟡 |
| **10** | Configuration, Supportability, Capacity, and Service Management | M4 | — | Phases 3, 5, 6, 8 | 🟡 |
| **11** | Administration and Operations User Interfaces | M4 | — | Phases 7, 9, 10 | 🟡 |
| **12** | Enterprise Validation, Pilot, and Release Candidate | M5 | all remaining | Phases 7, 8, 11 | 🔴 |

### D.1 Phase 5 headline obligations

Signed reproducible agent artifacts with SBOM and provenance; artifact admission policy and
approved-version catalogue; replacement of unrestricted alpha registration with constrained
short-lived enrollment; identity bound to tenant, environment, pool, capabilities and effective
service policy; posture and expiry reporting; drain, resume, quarantine, rotation, revocation and
decommissioning; staged rollout with canary health gates, automatic pause and rollback; hardened
non-root minimal runtime with default-deny network policy; compatibility negotiation; and
compromised-agent emergency revocation.

> Phase 4 bound service policy attributes to the authenticated agent's **registered** record.
> Secure enrollment and deployment-authority binding of that record is Phase 5 work and is a
> prerequisite for trusting those attributes in production.

### D.2 Phase 6 headline obligations

Consolidate the APIs delivered in Phases 1–5; add workflow, tenant/quota, route validation and
history, audit query and export, and cluster/snapshot/effective-configuration resources; complete
the reliability conventions (idempotency, ETag, preconditions, pagination, filtering, sorting,
field selection, async operations, stable problem responses, rate limits, quotas); leader
discovery and explicit read consistency; version, deprecation, retention and replay contracts;
generated clients and consumer-driven contract tests. `ARCH-18` can only close at this gate.

### D.3 Phase 7 headline obligations — includes the historical route architecture

Phase 7 absorbs historical task **T6.7 Route Architecture** in full. The autonomous route
evaluator (`ARCH-04`) is not wired today. Required: evaluator lifecycle and readiness signal;
manual, schedule, interval, event and approved file-arrival triggers; idempotent trigger identity
and duplicate suppression; pre-activation validation of service connections, agent capabilities,
policies, variables, dependencies and evaluator readiness; immutable versioned route and workflow
repositories with executions pinned to exact versions; workflow execution records with step
dependencies, pause, cancel, retry and reconciliation; side-effect-free dry-run and virtual plan;
processing dates, market holidays, time zones, daylight-saving rules, cut-offs, blackout and
maintenance windows and exception calendars; and controlled backfill and reprocessing with
approval and publication protection.

The historical trigger vocabulary from [QUORUS_TLS_SECURITY_ROUTES.md](../archive/QUORUS_TLS_SECURITY_ROUTES.md)
— EVENT, TIME, INTERVAL, BATCH, SIZE, COMPOSITE, leader-only evaluation, and `RouteCommand`
replication — remains a useful design reference, but Phase 7's governed model supersedes it.
`ARCH-17` requires that route and workflow activation use the same governed service-alias and
secret-reference model as Phase 4, never credential-bearing URIs.

### D.4 Phase 8 headline obligations

Supported static topologies, failure domains, quorum rules and storage classes; automated
snapshot creation, integrity verification, retention, encryption, replication and restore; backup
scope across Raft state, audit evidence, event data, configuration and deployment metadata;
one-node, leader, quorum, corrupt-log, corrupt-snapshot, full-cluster and accidental-deletion
failure tests; measured RPO and RTO per data class; post-recovery reconciliation of active
transfers; rolling upgrade, pause, rollback and mixed-version limits; disaster, maintenance and
degraded-mode runbooks; and scheduled restore exercises with evidence capture.

**Dynamic membership decision (`ARCH-10`):** the first enterprise release MAY retain documented
static membership. Live node add/remove MUST remain unavailable unless joint-consensus
membership, compatibility, recovery, audit and rollback are implemented and tested as a separate
Phase 8B workstream. Section A's R1 gates are the durability foundation this phase builds on.

### D.5 Phases 9, 10, 11, 12 headline obligations

- **Phase 9:** immutable audit search and signed export; classification, masking, residency,
  retention, legal hold and defensible deletion; four-eyes approval; time-bounded emergency
  access; at least one SIEM, one on-call/incident and one ITSM integration; governed signed
  webhooks with replay protection and dead-lettering; CMDB synchronization; compliance-control
  mappings without certification claims. **Security-event pruning, archive, legal hold and
  retention are explicitly Phase 9 work and were deliberately left open by R5.**
- **Phase 10:** configuration schema, candidate validation, redacted effective view, drift
  detection; configuration-as-code promotion without copying secrets; maintenance, emergency and
  degraded modes; redacted support bundles; capacity models and forecasts; service-level reports;
  tenant usage, forecasting and showback; certificate, secret-rotation and retention-expiry
  forecasts; and the full runbook catalogue.
- **Phase 11:** role-specific operator and administration interfaces built only on supported REST
  and event contracts, with server-side authorization, no secret rendering, visible read-consistency
  distinctions, precondition conflicts instead of silent overwrite, and accessibility targets.
- **Phase 12:** API and artifact freeze; end-to-end functional, security, isolation, performance,
  scale, soak, recovery, upgrade, rollback and disaster testing; threat model, scanning and
  penetration testing; the twelve reference financial-services pilot scenarios; operator game days
  without engineering intervention; and the recorded go/no-go release decision.

---

## 7. Section E — Observability and Logging Backlog

From [QUORUS_OPENTELEMETRY_INTEGRATION_TESTING_PLAN.md](../archive/QUORUS_OPENTELEMETRY_INTEGRATION_TESTING_PLAN.md) v2.5.

> **Scope boundary.** This backlog is telemetry *infrastructure and instrumentation*. It is not
> a substitute for Phase 3, which owns transfer-process operational outcomes. Completing this
> section does not advance the Phase 3 exit gate.

### E.1 Genuinely open

| ID | Item | Module | Priority |
|---|---|---|---|
| **OBS-01** | OTel integration test suite over the observability stack | controller test | 🟡 HIGH |
| **OBS-02** | Test execution script with reproducible pass/fail reporting | scripts | 🟠 MEDIUM |
| **OBS-03** | Bridge `requestId` ↔ OTel `traceId` end to end | controller | 🟡 HIGH |
| **OBS-04** | Add loggers to Status, Readiness, Liveness, Info and Cluster handlers | controller | 🟠 MEDIUM |
| **OBS-05** | Add loggers to `YamlWorkflowDefinitionParser` and `WorkflowSchemaValidator` | workflow | 🟠 MEDIUM |
| **OBS-06** | Add INFO success log to `JobStatusReportingService` | agent | 🟠 MEDIUM |
| **OBS-07** | Audit the 53 DEBUG statements in `SimpleTransferEngine` — promote, demote to TRACE, or remove | core | 🟠 MEDIUM |
| **OBS-08** | Remove orphaned `quorus-core/.../monitoring/TransferMetrics.java` and its test | core | 🟠 MEDIUM |
| **OBS-09** | Per-protocol adapter metrics | core | 🟠 MEDIUM |
| **OBS-10** | Tracing for HTTP, SFTP, FTP and SMB protocol adapters | core | 🟠 MEDIUM |
| **OBS-11** | Service-level tracing for `AgentRegistrationService`, `HeartbeatService`, `JobPollingService` | agent | 🟢 LOW |
| **OBS-12** | Workflow dependency-graph metrics (graph size, cycles detected, depth) | workflow | 🟢 LOW |
| **OBS-13** | Tracing for `SimpleWorkflowEngine` and `YamlWorkflowDefinitionParser` | workflow | 🟢 LOW |
| **OBS-14** | Standardize logger field naming (`LOG` → `logger`) in `RaftLogStorageAdapter` and `FileRaftStorage`; remove unused logger declarations in `MetricsHandler`, `ProtocolFactory`, `FileManager` | mixed | 🟢 LOW |

**OBS-08 note:** verified on 2026-09-07. `TransferMetrics.java` is still present in
`quorus-core/src/main/java/dev/mars/quorus/monitoring/` and is referenced only by its own
`TransferMetricsTest`. `SimpleTransferEngine` no longer uses it. It is dead code awaiting
removal, not an in-use dual-metrics system. The `NetworkTopologyService.getTransferMetrics()`
accessor is an unrelated name collision and must not be removed with it.

### E.2 Already satisfied — grid entries are stale

Verified against live source on 2026-09-07. See Section H for the corresponding document fix.

| Grid entry | Actual state |
|---|---|
| Test Phases 1–3: Docker Compose, OTel Collector, Prometheus config | Present: `docker/compose/docker-compose-observability.yml`, `otel-collector-config.yaml`, `prometheus-observability.yml`, `tempo-config.yaml`, `loki-config.yaml`, `scripts/start-cluster-with-observability.ps1` |
| Configure Logging-OTel Bridge (`OpenTelemetryAppender`) | Implemented in both controller and agent (`pom.xml` + `logback.xml`) |
| Implement Raft persistence (custom WAL) | Complete — now the external `raftlog-core` WAL, per enterprise plan §4 |
| Add Raft log compaction / snapshotting | Complete — RaftLog 1.2.0 prefix compaction after caller-owned durable snapshots |
| Implement InstallSnapshot RPC | Complete — alpha plan T5.3, chunked with `SnapshotChunkAssembler` |
| Replace Apache HttpClient with Vert.x WebClient (agent) | Complete — alpha plan T3.1 |
| Replace Java Serialization with Protobuf | Complete — alpha plan T5.4, now at version 2 command/snapshot contracts |
| Add gRPC TLS encryption | Complete — Phase 1 delivered TLS 1.3 mutual authentication for Raft server and peer clients |
| Add `TransferProtocol.abort()` | Present at `quorus-core/.../protocol/TransferProtocol.java:87` as a default method |
| Fix tenant module synchronized bottleneck | Substantially resolved — one `synchronized` occurrence remains in `SimpleTenantService`; re-scope as 🟢 LOW rather than 🟠 MEDIUM |

---

## 8. Section F — Absorbed and Superseded Historical Tasks

Recorded so that a reader of the historical documents does not reopen closed or relocated work.

| Historical task | Source | Disposition |
|---|---|---|
| T6.1 API Key Authentication | TLS/Security/Routes | **Superseded.** Phase 1 delivered certificate-authenticated identity, scope enforcement and uniform authorization middleware. Do not implement a shared-secret API key scheme. |
| T6.2 TLS for HTTP API | TLS/Security/Routes | **Complete in Phase 1** — TLS 1.3 client-certificate authentication for controller HTTP |
| T6.3 TLS for gRPC Raft | TLS/Security/Routes | **Complete in Phase 1** — TLS 1.3 mutual authentication for Raft server and peer clients |
| T6.4 TenantSecurityService | TLS/Security/Routes | **Superseded.** Phase 1 authenticated tenant derivation plus R2 versioned collision-free registry keys and fail-closed ownership replace the proposed interface. |
| T6.5 Request Rate Limiting | TLS/Security/Routes | **Relocated to Phase 6** as part of the standard reliability conventions (`API-12`) |
| T6.6 OAuth2/JWT Authentication | TLS/Security/Routes | **Open, scope-dependent.** Phase 1 selected the trusted-gateway plus protected-hop boundary (ADR-0003). Direct OAuth2/JWT termination is only required if the agreed enterprise identity provider integration demands it. Decide during Phase 6 planning; do not build speculatively. |
| T6.7 Route Architecture | TLS/Security/Routes | **Relocated to Phase 7** — see §6 D.3 |
| T6.8 TenantAwareStorageService | TLS/Security/Routes | **Partly delivered, partly relocated.** Phase 4 delivered agent-local root and path-escape enforcement. Storage quota enforcement and per-tenant usage accounting move to Phase 6 (`API-09`) and Phase 10. |
| Alpha plan Stages 1–5 (T1.1–T5.4) | Alpha plan | **Complete.** Retained as historical evidence only. Its completion markers do not establish current conformance — in particular, its tenant field checks are not authenticated tenant isolation. |
| Sealed record commands, Phases 1–10 | Sealed record design | **Complete.** Verified 2026-09-07: `canTransitionTo` is implemented across `TransferStatus`, `TransferAttemptStatus`, `JobAssignmentStatus`, `RouteStatus` and `AgentStatus`, consumed by `QuorusStateStore` and the HTTP handlers, and covered by `StateTransitionIntegrationTest`. |

---

## 9. Section G — Deferred and Research

Deferral is explicit. Documentation MUST NOT imply any of these is current
(enterprise plan §22).

| ID | Item | Classification |
|---|---|---|
| **DEF-01** | Dynamic Raft membership (`ARCH-10`) — only as Phase 8B with joint consensus, compatibility, recovery, audit and rollback | Research / Phase 8 decision |
| **DEF-02** | Type-safe sealed state encoding (sealed record design §15) — compile-time transition enforcement; the runtime `canTransitionTo` model achieves the practical safety and this is a v2 evolution | Research |
| **DEF-03** | Agent-to-agent streaming | Enterprise follow-on |
| **DEF-04** | S3, Azure Blob and Google Cloud Storage adapters | Enterprise follow-on |
| **DEF-05** | Multi-cluster federation | Enterprise follow-on |
| **DEF-06** | Automatic controller sharding | Enterprise follow-on |
| **DEF-07** | Additional secrets, SIEM, ITSM, scheduler and notification providers beyond the first supported integration in each category | Enterprise follow-on |
| **DEF-08** | Advanced chargeback and cost optimization | Enterprise follow-on |
| **DEF-09** | Admin UI build/buy decision as framed in the OTel plan (six months of operational feedback, then decide) | **Superseded** — Phase 11 makes the operator and administration interfaces a required M4 deliverable. Remove the deferral framing from the OTel plan. |

`ARCH-09` (HTTP adapter buffers the full payload) is not deferred: it is assigned to Phase 4
protocol hardening and Phase 12 scale validation. It remains open and unlisted in Phase 4's
completion checkpoint; confirm its disposition during Phase 6 or Phase 12 planning.

---

## 10. Section H — Documentation Corrections

The code is correct in each case; the document misleads a reader. Correcting these is
documentation hygiene, not a delivery gate.

**All six were applied to the OTel plan on 2026-09-07 (v2.5 → v2.6).** Rows are retained for one
revision so the correction is visible, then removed per §12.4.

| ID | Document | Correction | State |
|---|---|---|---|
| **DOC-01** | OTel plan, "Critical Pre-Production Fixes" grid | All eight entries are complete or substantially complete (Section E.2). The table presented delivered Raft persistence, compaction, InstallSnapshot, WebClient migration, Protobuf, gRPC TLS and `abort()` as 🔴 CRITICAL pending, contradicting the alpha and enterprise plans. | ✅ Applied — retitled "Former Critical Pre-Production Fixes", all rows marked delivered with their delivering plan, plus an explicit warning not to reintroduce a RocksDB-backed WAL and a durability caveat pointing at `R1-1`…`R1-3` |
| **DOC-02** | OTel plan, "Test Phases 1–5" grid | Test Phases 1–3 infrastructure exists in `docker/compose/`. Only the integration test suite and execution script are genuinely open. | ✅ Applied — Test Phases 1–3 marked complete with file paths; 4 and 5 mapped to `OBS-01` and `OBS-02` |
| **DOC-03** | OTel plan, "Logging & OTel Audit Fixes" grid | The Logging-OTel bridge is implemented in controller and agent; v2.4 already stated this in prose while the grid showed it pending. | ✅ Applied — marked complete; `requestId` ↔ `traceId` rescoped from pending to partial |
| **DOC-04** | OTel plan, `TransferMetrics` entries | v2.5 prose says removed; the file still exists as dead code. | ✅ Applied — recorded as orphaned pending deletion (`OBS-08`), with a warning that `NetworkTopologyService.getTransferMetrics()` is an unrelated name collision |
| **DOC-05** | OTel plan, target dates and "mid-2026 launch" | Every date is in the past and the enterprise plan deliberately assigns no calendar dates. | ✅ Applied — Target Date column replaced throughout by a Delivery reference column citing the owning phase or an `OBS-*` ID |
| **DOC-06** | OTel plan, "Admin UI Decision (POST-LAUNCH)" | Superseded by Phase 11. See `DEF-09`. | ✅ Applied — replaced by a superseded notice; the five decision rows removed from the progress table |

Two further corrections were made in the same pass, beyond the six identified above:

- the document header read v2.4 while its footer read v2.5; both now read v2.6;
- a header note now records that the narrative sections remain point-in-time analysis and that the
  task grid is authoritative for status, so the un-reconciled module gap analyses and the "Future
  Migration Phases" framing cannot be read as current.

---

## 11. Gap-to-Section Traceability

| Gap | Status | Where the remaining work lives |
|---|---|---|
| `ARCH-01` Agent omits `IN_PROGRESS` | Closed | Phase 2 structural delivery |
| `ARCH-02` No attempt lease or fencing | Partly open | P2-01, P2-02, P2-03 |
| `ARCH-03` No authenticated identity boundary | Closed | Phase 1 |
| `ARCH-04` Route trigger evaluator not wired | Open | Phase 7 (§6 D.3) |
| `ARCH-05` Retriable writes lack idempotency and leader discovery | Open | P2-04, Phase 6 |
| `ARCH-06` Assignment reference and tenant invariants incomplete | Closed | Phases 0, 1, R2 |
| `ARCH-07` Persistent controller path and volume not proven | Open | **R1-1, R1-2, R1-3**, Phase 8 |
| `ARCH-08` SFTP host-key verification disabled | Closed | Phase 4 |
| `ARCH-09` HTTP adapter buffers full payload | Open | Phase 4 hardening, Phase 12 scale |
| `ARCH-10` Dynamic membership absent | Deferred | DEF-01 |
| `ARCH-11` Transfer operations telemetry incomplete | Partly open | P3-01 … P3-12 |
| `ARCH-12` Operational business context absent | Closed | Phase 3 first slice |
| `ARCH-13` TLS/mTLS boundary incomplete | Closed | Phase 1 |
| `ARCH-14` Service alias, egress, verification, secret policy absent | Closed | Phase 4 |
| `ARCH-15` Agent identity lifecycle incomplete | Open | Phase 5 |
| `ARCH-16` Governed agent deployment absent | Open | Phase 5 |
| `ARCH-17` Credential-bearing production transfer paths | Closed for transfers | Route/workflow activation must reuse the governed model — Phase 7 |
| `ARCH-18` REST coverage incomplete | Open | Phase 6 |
| `API-01` OpenAPI and path coverage absent | Open | Phase 6 |
| `API-02` Authenticated scope enforcement absent | Closed | Phase 1 |
| `API-03` Transfer lifecycle and evidence resources absent | Partly open | P2-11, P3-06, P3-07, Phase 6 |
| `API-04` Operational risk and alert APIs absent | Open | P3-06, P3-09 |
| `API-05` Secure agent lifecycle API absent | Open | Phase 5 |
| `API-06` Service connection and secret-reference API absent | Closed | Phase 4 |
| `API-07` Assignment lease and fencing contract absent | Partly open | P2-02, P2-03 |
| `API-08` Workflow REST resources absent | Open | Phases 6, 7 |
| `API-09` Tenant and quota REST resources absent | Open | Phase 6 |
| `API-10` Route validation and execution history absent | Open | Phase 7 |
| `API-11` Audit query and export API absent | Open | Phases 6, 9 |
| `API-12` Standard reliability conventions absent | Partly open | P2-04, Phase 6 |
| `API-13` Cluster and configuration administration incomplete | Open | Phases 6, 8, 10 |
| `API-14` Compatibility, retention, export and replay incomplete | Open | P3-11, Phases 6, 8, 9 |

---

## 12. Register Governance

1. This register is regenerated from its source documents, never edited to disagree with them.
2. An item is removed only when the source plan's exit criterion is met with retained evidence
   under §6.1 — not when the code merely exists.
3. New outstanding work is added to the enterprise plan first, then reflected here. Archived
   sources are never reopened for new work; correct them only to fix a misleading statement.
4. Section H entries are cleared by fixing the source document, then deleting the row.
5. Verification claims in this register that were checked against live source are dated inline.
   Re-verify before relying on them; a claim dated 2026-09-07 is not evidence about a later tree.

### Revision history

| Version | Date | Changes |
|---|---|---|
| 1.0 | 2026-09-07 | Initial consolidation of all outstanding tasks from the five `docs-design/task/` planning documents, with live-source verification of eleven stale OTel grid claims and the sealed-record transition phases |
| 1.1 | 2026-09-07 | Applied all six Section H corrections to the OTel plan (v2.5 → v2.6), including removal of three production-readiness claims it should not have made; archived the alpha plan, Stage 6 security/routes plan, OTel plan and sealed-record design, leaving `task/` holding only the enterprise plan and this register; repaired every cross-reference broken by the move |
