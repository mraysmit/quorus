<img src="../../docs/quorus-logo.png" alt="Quorus" width="120"/>

# Quorus Outstanding Work Register

**Version:** 1.12
**Date:** 2026-09-27
**Author:** Mark Ray-Smith — Cityline Ltd
**License:** Apache 2.0
**Status:** Active — the single task list: every open task and decision across the current and archived planning documents and reviews

---

## 1. Purpose and Authority

This register is the single consolidated list of outstanding work drawn from all five planning
documents that existed in [docs-design/task/](.) on 2026-09-07. It exists so that open work can
be found in one place rather than reconstructed from five documents written at different times
under different status vocabularies.

Following that consolidation, the other three plans and the sealed-record design were moved to
[../archive/](../archive/) on 2026-09-07: their open work is carried here, and they are retained
for provenance and technical reference rather than as live backlogs.

**This register is the project's single task list.** On 2026-09-26 the separate documentation
review task list was merged into it: its documentation tasks are Section H, its code and
configuration defects are Section I, and its decisions are in the §3 decision log. The file is
[archived](../archive/QUORUS_DOCUMENTATION_REVIEW_TASKS.md) with its dated progress notes. `task/`
now holds only the enterprise plan, which controls delivery, and this register. Do not start
another task list: add work here, following §15.

**This register is not normative.** It owns the identity and status of every task, the Section H
documentation tasks and the §3 decision log. It does not own requirements or acceptance
criteria. Precedence is unchanged:

1. [Quorus Architecture Specification](../../docs/QUORUS_ARCHITECTURE_SPECIFICATION.md) and
   [Quorus REST API Specification](../../docs/QUORUS_REST_API_SPECIFICATION.md) remain the
   canonical requirements.
2. [QUORUS_ENTERPRISE_IMPLEMENTATION_PLAN.md](QUORUS_ENTERPRISE_IMPLEMENTATION_PLAN.md)
   remains the controlling delivery plan: phase sequencing, exit gates, the §6 Definition of
   Done, and the §6.1 mandatory TDD protocol are defined there and are **not** restated or
   weakened here.
3. This register lists and identifies the open items. Where it disagrees with a source
   document's requirement, the requirement wins and this register is corrected. Where a source's
   status statement is out of date, the source is corrected (§15.1).

Closing an item here does not close a phase exit gate. Phase closure follows
[§24 Plan Governance](QUORUS_ENTERPRISE_IMPLEMENTATION_PLAN.md) of the enterprise plan.

### Source documents

| Document | Role | Contribution to this register |
|---|---|---|
| [QUORUS_ENTERPRISE_IMPLEMENTATION_PLAN.md](QUORUS_ENTERPRISE_IMPLEMENTATION_PLAN.md) v1.37 | Controlling roadmap | Sections A–D, F, I, J |
| [QUORUS_DOCUMENTATION_REVIEW_TASKS.md](../archive/QUORUS_DOCUMENTATION_REVIEW_TASKS.md) v1.7 — archived 2026-09-26, merged here | Documentation review remediation | Sections H and I, §3 decision log |
| [ADR-0011](../architecture-decisions/ADR-0011-CONSENSUS-VIA-QRAFT-GENERIC-ENGINE.md), [ADR-0012](../architecture-decisions/ADR-0012-JAVA-RUNTIME-AND-STRUCTURED-CONCURRENCY.md) | Platform decisions | Section J, §3 decision log |
| [QUORUS_OPENTELEMETRY_INTEGRATION_TESTING_PLAN.md](../archive/QUORUS_OPENTELEMETRY_INTEGRATION_TESTING_PLAN.md) v2.6 — archived | Observability backlog and collector/test reference | Section E |
| [QUORUS_TLS_SECURITY_ROUTES.md](../archive/QUORUS_TLS_SECURITY_ROUTES.md) v1.0 — archived | Historical Stage 6 detail | Section F (absorbed), route detail for Phase 7 |
| [QUORUS_ALPHA_IMPLEMENTATION_PLAN.md](../archive/QUORUS_ALPHA_IMPLEMENTATION_PLAN.md) v1.9 — archived | Historical alpha evidence | No open items; see §9 |
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
| 🟢 **Deferred** | Explicitly out of scope for the first enterprise release; see enterprise plan §23 |
| 🔵 **Documentation** | A documentation task; the code is correct, but a document is stale, missing or misleading |

Every implementation item below is delivered under the §6.1 mandatory TDD protocol:
preserved behavioral red through a real HTTP, agent, protocol, or cluster boundary before
implementation, then green, refactor, and regression, with retained evidence. Asynchronous
tests follow the rules in plan §6.1. Code that has left Vert.x uses the test standard in
[concurrency conventions §6](../dev/QUORUS_CONCURRENCY_CONVENTIONS.md#6-asynchronous-test-standard).
While CI is red (`ENG-07`, deferred by `SEQ-01`), a slice's regression evidence is its local
full-reactor run.

---

## 2. Summary

| Section | Area | Open items | Blocking level |
|---|---|---|---|
| §3 | Decision log | 13 taken, 4 open (DR-Q1, DR-Q3, DR-Q4, DR-Q5) | Open decisions block named tasks |
| A | R1 durability acceptance and related process items | 4 open (R1-2, R1-3, R1-4, PROC-01), 1 closed | 🔴 Release blocker |
| B | Phase 2 — attempts, integrity, reconciliation | 13 | 🟡 Phase blocker |
| C | Phase 3 — transfer operations telemetry | 12 | 🟡 Phase blocker |
| D | Phases 5–12 — not started | 8 phases | 🔴 / 🟡 by phase |
| E | Observability and logging backlog | 10 open (OBS-04, -05, -07, -08, -14 closed) | 🟠 Backlog |
| F | Absorbed and superseded historical tasks | 10 | — reference only |
| G | Deferred and research | 8 deferred, 1 superseded | 🟢 Deferred |
| H | Documentation remediation (from the 2026-09-24 review) | 31 listed: 25 open, 6 in progress. 16 more, done or superseded by v1.10, have moved to the revision history | 🔵 Documentation |
| I | Configuration and documentation-review delivery items | 19 open, 5 closed | 🟠 Backlog, one 🟡 (`ENG-07`) |
| J | Platform migration — QRaft consensus and Vert.x exit | 18 open (11 CE; 7 RT, of which `RT-03` is in progress), 3 RT done. Decisions `RT-Q1`–`RT-Q5` are in §3 | 🟡 / 🔴 by item |

**Phase position:** Phase 1 complete. Phase 0 is functionally complete, but its durability
acceptance stays reopened until `R1-2` and `R1-3` close. Phase 4 is complete: the acceptance
reopened by the 2026-09-04 remediation checkpoint was restored when R2–R6 completed on
2026-09-05. Phases 2 and 3 in progress. Phases 5–12 not started. The R1 remediation slice is
complete in code; `R1-1` container-recreation acceptance closed on 2026-09-07, and `R1-2` and
`R1-3` remain open and still block the release claim. Phase 4 has one hardening follow-up open
(`SEC-07`). The platform migration (Section J) is in progress. CI has never passed, and its
repair is deferred (`ENG-07`, `SEQ-01`); no phase can close until it is fixed.

---

## 3. Decision Log

Every project decision that governs open work in this register. Architecture decisions are
recorded in full in their ADR; this log holds the identity, the choice and what it unblocks.
An open decision names the work it blocks.

| ID | Decision | Choice | Record | Unblocks or governs | State |
|---|---|---|---|---|---|
| **ADR-0011** | How Quorus obtains consensus | Consume the generic QRaft engine only; no Quorus concept in QRaft; JDK-typed API; no direct `raftlog-core` after `CE-10` | [ADR-0011](../architecture-decisions/ADR-0011-CONSENSUS-VIA-QRAFT-GENERIC-ENGINE.md) | Section J `CE-*` | ✅ 2026-09-26 |
| **ADR-0012** | Runtime and concurrency model | Leave Vert.x completely for Java 27 virtual threads, `ScopedValue` and structured concurrency | [ADR-0012](../architecture-decisions/ADR-0012-JAVA-RUNTIME-AND-STRUCTURED-CONCURRENCY.md) | Section J `RT-*`; supersedes DR-C3 | ✅ 2026-09-26 |
| **RT-Q1** | Preview `StructuredTaskScope` in production | No preview in production. Quorus-owned task-scope abstraction on final APIs, switched to `StructuredTaskScope` when it is final in an adopted release | ADR-0012 | `RT-02` onward | ✅ 2026-09-26 |
| **RT-Q2** | Controller HTTP server | JDK `HttpsServer`, proven in `RT-06` or reopened with evidence | ADR-0012 | `RT-06` | ✅ 2026-09-26 |
| **RT-Q3** | Java support policy | Follow each six-monthly Java feature release within its update window | ADR-0012 | `RT-01`, `RT-09`, DR-B6 | ✅ 2026-09-26 |
| **DR-Q2** | Runtime revocation scope | Node-local and volatile: send the full set to every controller and persist it in configuration before restart | ADR-0009 (to be written, DR-D4) | `SEC-02`, DR-A4 | ✅ 2026-09-25 |
| **DR-Q6** | TDD evidence | Revised 2026-09-27: a slice's record is its commit message (red and green results, mutation checks, regression totals, characterization labels). Raw logs, JSON manifests, hashes and patches are no longer kept. Earlier: cited evidence lived under `docs-design/evidence/raw/<slice-id>/` (2026-09-26), which remains the record of the slices that produced it | Plan §6.1; [raw evidence index](../evidence/raw/INDEX.md) for earlier slices | DR-C10, all implementation slices | ✅ 2026-09-27 |
| **STATUS-01** | Phase 0 and Phase 4 status wording | Phase 0 functionally complete, with durability acceptance reopened until R1-2 and R1-3; Phase 4 complete, its 2026-09-04 reopening restored by R2–R6 | Plan header, §7, §11 | Section 2 phase position | ✅ 2026-09-26 |
| **RT-Q4** | Java 27 container images | **Amazon Corretto 27** (amd64 and arm64, published 2026-09-18). Temurin had no Java 27 images, and the official `openjdk` image offers only non-production `27-rc` tags (checked 2026-09-26). Runtime variant: option A, `amazoncorretto:27.0.0-alpine3.24`, because Corretto 27 has no JRE-only Alpine image. The images are single-stage and copy jars built on the host. No image contains Maven or a builder stage | ADR-0012 v1.2 | `RT-01b`, Docker-tagged lanes, `RT-09` | ✅ 2026-09-26 |
| **RT-Q5** | HTTP client for the HTTP transfer adapter | Apache HttpClient 5 (classic API). `java.net.http` cannot connect to a pinned IP while enforcing hostname verification and sending the correct `Host` (measured 2026-09-26) | ADR-0012 | `RT-03b` | ✅ 2026-09-26 |
| **SEQ-01** | When to repair CI, which has never passed (`ENG-07`) | Defer the repair and continue platform work first. Until then, each slice's regression evidence is its local full-reactor run. No phase can close while `ENG-07` is open, because plan §6.1 step 5 requires every applicable lane to pass | Plan §4 (2026-09-27), Phase 0 status | `ENG-07`, DR-D2 | ✅ 2026-09-27 |
| **SEQ-02** | Where the governed TLS trust-anchor gap (`SEC-07`) is delivered | A Phase 4 hardening follow-up: trust-anchor certificates configured and audited by Quorus, narrowed by the existing approved-CA fingerprints. It must close before any production service connection relies on a private CA | Plan §4 and §11 (2026-09-27) | `SEC-07` | ✅ 2026-09-27 |
| **DR-Q7** | Where the current HTTP API is documented | The bundled OpenAPI contract only: it is self-documenting and served at `GET /api/v1/openapi.yaml`. The hand-written API Reference is deleted. The REST API Specification holds requirements only, and a test checks its "Current" rows against the contract. `/api/v1/info` links to the contract instead of listing endpoints | REST API Specification v2.6 §1; plan §4 (2026-09-27) | DR-B2, DR-B3, DR-D1, DR-F03, `ENG-08` | ✅ 2026-09-27 |
| **DR-Q1** | Workflow YAML semantics (review §4.3) | (a) Change the guides to match the parser and engine; (b) implement `execution.dryRun`, `parallelism`, `timeout` and `strategy`, group `retryCount`, `options` pass-through and recursive variable substitution | ADR-0010 when decided | DR-B4, DR-D4 | ⬜ |
| **DR-Q3** | Elevation for direct mTLS identities (review §6 #8) | (a) Document that only gateway-asserted identities can hold elevation; (b) add a direct-binding elevation mechanism | — | DR-B5, `SEC-06` | ⬜ |
| **DR-Q4** | How `*IT` and `*Benchmark` classes run (review §6 #18) | (a) Add the Failsafe plugin; (b) rename the classes and tag them | — | `ENG-02`, DR-F16 | ⬜ |
| **DR-Q5** | Authoritative product version (review §4.10, §6 #13) | One version from one source (the pom, through resource filtering) | Versioning policy | DR-B6 | ⬜ |

---

## 4. Section A — R1 Durability Acceptance (Release Blockers)

Remediation slices R2–R6 are implementation-complete. R6 final acceptance was verified from a
clean detached worktree at revision `dc447d4`: 2,437 tests, zero failures or errors, two
existing explicit skips, and all five configured JaCoCo gates
([R6 evidence](../evidence/r6-final-acceptance-2026-09-05.md)).

R1 durable snapshots is verified in code on Windows, and container-recreation acceptance is now
proven (see below). The remaining two gates are **not** closed, and they are the reason no
enterprise release claim can currently be made.

| ID | Item | Detail | Level |
|---|---|---|---|
| **R1-1** | Container-recreation acceptance | Prove durable snapshot and coordinate recovery across controller container destruction and recreation against a persistent volume, not a local working tree | ✅ **Closed 2026-09-07** for the Docker container-recreation shape |
| **R1-2** | Production-filesystem acceptance | Repeat R1 recovery, retained-tail, corruption and concurrent-mutation cases on the supported production filesystem and storage class, not the Windows development host | 🔴 |
| **R1-3** | Machine power-loss acceptance | Prove committed state and snapshot/WAL coordinates survive unclean host power loss; establish that interrupted publication cannot silently replace good state | 🔴 |

**R1-1 result — 2026-09-07.** Four containerised acceptance tests pass through the real
cluster boundary: full-cluster destroy-and-recreate, recovery from a durable snapshot after
the WAL is compacted to zero bytes, rolling single-node recreation under retained quorum, and
a negative control proving the gate is not vacuous. Controller regression with Docker and slow
groups enabled passes 601 tests with zero failures or errors, two pre-existing explicit skips
and the JaCoCo gate. **No product defect was found**: both retained red failures were incorrect
assertions in the new test, so the two recovery tests are classified as retrospective
characterization under §6.1, not as historical TDD. Docker is a confirmed production target,
so the deployment shape is representative; the engine was Docker Desktop on Windows, so the
storage class and host kernel are not. See the
[R1-1 evidence](../evidence/r1-container-recreation-2026-09-07.md).

A material fixture gap was found and is recorded there: before this slice,
`docker-compose-3node-prebuilt.yml` declared no volumes and no `QUORUS_RAFT_STORAGE_PATH`, so
every earlier containerised test ran Raft state on the container's ephemeral layer and could not
have detected a container-level durability regression. The fixtures were corrected in register
v1.3: containerised tests now write Raft state to named volumes at the deployed path.

| ID | Related open item | Detail | Level |
|---|---|---|---|
| **R1-4** | Persistent-environment storage inventory | Inventory existing persistent environments and preserve their storage before any recovery or rollback attempt (constraint below) | 🔴 |
| **PROC-01** | Disposition of the two Raft regression cases | The two Raft regression cases without preserved red evidence need an explicit, recorded process-deviation disposition (constraint below) | 🟡 |

**Constraints carried from the enterprise plan:**

- `raftlog-core` (`io.github.mraysmit:raftlog-core` 1.2.0 on Maven Central; sister checkout at
  `../raftlog`) is the only WAL. Internal RocksDB and memory backends and the RocksDB JNI
  dependency are removed; configuration accepts only `raftlog`. Do not reintroduce an internal
  backend to satisfy a test. Under [ADR-0011](../architecture-decisions/ADR-0011-CONSENSUS-VIA-QRAFT-GENERIC-ENGINE.md),
  Quorus will reach raftlog only through the QRaft engine (Section J); until `CE-10`, the direct
  dependency remains.
- Existing persistent environments have **not** been inventoried (`R1-4`). Preserve their
  storage before any recovery or rollback attempt. Code rollback cannot recover already-deleted
  WAL records.
- The two Raft regression cases without preserved red evidence remain historical process
  deviations requiring explicit disposition (`PROC-01`). They cannot be relabelled as
  historical TDD.

---

## 5. Section B — Phase 2 Open Items

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
| **P2-13** | Durable agent-report outbox | Agent status reports survive agent restart and are recovered from a durable outbox; R3 provides only bounded in-memory replay (three sends) | 🟡 |

`ENG-01` in Section I (the uninstantiated `JobAssignmentService` timeout monitor) should be
settled before or during `P2-01`.

**Exit gate:** Quorus can safely explain what ran, where it ran, which attempt is authoritative,
what was published, and what requires reconciliation. It still does not claim exactly-once
external execution.

---

## 6. Section C — Phase 3 Open Items

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

**Open defect note — resolved 2026-09-07.** The non-reproducing three-node
`LeaderGuardHandlerTest` fixture startup timeout retained during Phase 3 verification has been
root-caused and fixed. `@BeforeAll` discarded all three `RaftNode.start()` futures and polled
for a leader while startup was still in flight, letting the deliberately fast-election node
campaign before its peers' in-memory transports were registered. The fix awaits the start
futures and starts the slow-election followers first. Setup now completes in about 1.5 s and
three consecutive runs pass. The same discarded-future pattern elsewhere is tracked as
`OBS-15`.

---

## 7. Section D — Phases 5 to 12 (Not Started)

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

## 8. Section E — Observability and Logging Backlog

From [QUORUS_OPENTELEMETRY_INTEGRATION_TESTING_PLAN.md](../archive/QUORUS_OPENTELEMETRY_INTEGRATION_TESTING_PLAN.md) v2.6.

> **Scope boundary.** This backlog is telemetry *infrastructure and instrumentation*. It is not
> a substitute for Phase 3, which owns transfer-process operational outcomes. Completing this
> section does not advance the Phase 3 exit gate.

### E.1 Genuinely open

| ID | Item | Module | Priority |
|---|---|---|---|
| **OBS-01** | OTel integration test suite over the observability stack | controller test | 🟡 HIGH |
| **OBS-02** | Test execution script with reproducible pass/fail reporting | scripts | 🟠 MEDIUM |
| **OBS-03** | Bridge `requestId` ↔ OTel `traceId` end to end | controller | 🟡 HIGH |
| **OBS-06** | Add INFO success log to `JobStatusReportingService` (re-verified open 2026-09-26: no INFO call) | agent | 🟠 MEDIUM |
| **OBS-15** | Await discarded `RaftNode.start()` / server `start()` futures in roughly twenty controller tests (`HttpApiServerHealthTest`, `JobAssignmentHandlerTest`, `StateTransitionIntegrationTest`, `GrpcRaftServerTest`, `RaftFailureTest` and others). Same latent race as the fixed `LeaderGuardHandlerTest` flake, but with no observed failures; needs a deliberate verified pass, not a blind sweep. Do not start before `CE-07`/`CE-10`: many of these tests exercise the in-repository engine that `CE-10` removes, so re-scope the list then | controller test | 🟠 MEDIUM |
| **OBS-09** | Per-protocol adapter metrics | core | 🟠 MEDIUM |
| **OBS-10** | Tracing for HTTP, SFTP, FTP and SMB protocol adapters | core | 🟠 MEDIUM |
| **OBS-11** | Service-level tracing for `AgentRegistrationService`, `HeartbeatService`, `JobPollingService` | agent | 🟢 LOW |
| **OBS-12** | Workflow dependency-graph metrics (graph size, cycles detected, depth) | workflow | 🟢 LOW |
| **OBS-13** | Tracing for `SimpleWorkflowEngine` and `YamlWorkflowDefinitionParser` | workflow | 🟢 LOW |

**OBS-04, OBS-05 and OBS-14 — closed 2026-09-26 as already satisfied.** Checked against live
source: the Status, Readiness, Liveness, Info and Cluster handlers, `YamlWorkflowDefinitionParser`
and `WorkflowSchemaValidator` all declare and use a logger. `RaftLogStorageAdapter` already
names its field `logger`, and `FileRaftStorage` is an external `raftlog-core` class. The
`MetricsHandler`, `ProtocolFactory` and `FileManager` loggers are all used.

**OBS-07 — closed 2026-09-27 by `RT-03c`.** The engine rewrite reduced `SimpleTransferEngine` from 37 DEBUG statements to 3: the redacted execution detail, the retry delay, and the failure stack trace. Entry and exit tracing was removed; lifecycle events (start, completion, cancellation, shutdown) are INFO, failed attempts WARN and permanent failures ERROR.

**OBS-08 — closed 2026-09-07.** `TransferMetrics.java` and `TransferMetricsTest` are deleted.
The class had no remaining production caller; `NetworkTopologyService.getTransferMetrics()` is
an unrelated name collision and was left in place. `quorus-core` clean verify passes 1,517
tests with zero failures, errors or skips and meets its JaCoCo gate.

**OBS-15 note:** discovered while root-causing the `LeaderGuardHandlerTest` timeout. Discarding
a Vert.x `Future` from `start()` means the test proceeds while startup is still in flight. In
`LeaderGuardHandlerTest` that let a 400 ms-election node campaign before its peers' transports
were registered. The other occurrences have no observed failures, so they are listed rather
than swept: changing twenty startup paths at once risks more than the latent flakiness it
removes.

### E.2 Already satisfied — grid entries are stale

Verified against live source on 2026-09-07. The corresponding OTel plan corrections were `DOC-01` to `DOC-06` (register v1.4).

| Grid entry | Actual state |
|---|---|
| Test Phases 1–3: Docker Compose, OTel Collector, Prometheus config | Present: `docker/compose/docker-compose-observability.yml`, `otel-collector-config.yaml`, `prometheus-observability.yml`, `tempo-config.yaml`, `loki-config.yaml`, `scripts/start-cluster-with-observability.ps1` |
| Configure Logging-OTel Bridge (`OpenTelemetryAppender`) | Implemented in both controller and agent (`pom.xml` + `logback.xml`) |
| Implement Raft persistence (custom WAL) | Complete — now the external `raftlog-core` WAL, per enterprise plan §4 |
| Add Raft log compaction / snapshotting | Complete — RaftLog 1.2.0 prefix compaction after caller-owned durable snapshots |
| Implement InstallSnapshot RPC | Complete — alpha plan T5.3, chunked with `SnapshotChunkAssembler` |
| Replace Apache HttpClient with Vert.x WebClient (agent) | Complete — alpha plan T3.1. The direction has since been reversed by ADR-0012: `RT-05` replaces the agent's `WebClient` with `java.net.http`, and the HTTP transfer adapter now uses Apache HttpClient 5 (`RT-Q5`) |
| Replace Java Serialization with Protobuf | Complete — alpha plan T5.4, now at version 2 command/snapshot contracts |
| Add gRPC TLS encryption | Complete — Phase 1 delivered TLS 1.3 mutual authentication for Raft server and peer clients |
| Add `TransferProtocol.abort()` | Present at `quorus-core/.../protocol/TransferProtocol.java:87` as a default method |
| Fix tenant module synchronized bottleneck | Resolved. The one remaining `synchronized` in `SimpleTenantService` is in a Javadoc comment recording its removal (re-verified 2026-09-27); no action |

---

## 9. Section F — Absorbed and Superseded Historical Tasks

Recorded so that a reader of the historical documents does not reopen closed or relocated work.

| Historical task | Source | Disposition |
|---|---|---|
| T6.1 API Key Authentication | TLS/Security/Routes | **Superseded.** Phase 1 delivered certificate-authenticated identity, scope enforcement and uniform authorization middleware. Do not implement a shared-secret API key scheme. |
| T6.2 TLS for HTTP API | TLS/Security/Routes | **Complete in Phase 1** — TLS 1.3 client-certificate authentication for controller HTTP |
| T6.3 TLS for gRPC Raft | TLS/Security/Routes | **Complete in Phase 1** — TLS 1.3 mutual authentication for Raft server and peer clients |
| T6.4 TenantSecurityService | TLS/Security/Routes | **Superseded.** Phase 1 authenticated tenant derivation plus R2 versioned collision-free registry keys and fail-closed ownership replace the proposed interface. |
| T6.5 Request Rate Limiting | TLS/Security/Routes | **Relocated to Phase 6** as part of the standard reliability conventions (`API-12`) |
| T6.6 OAuth2/JWT Authentication | TLS/Security/Routes | **Open, scope-dependent.** Phase 1 selected the trusted-gateway plus protected-hop boundary (ADR-0003). Direct OAuth2/JWT termination is only required if the agreed enterprise identity provider integration demands it. Decide during Phase 6 planning; do not build speculatively. |
| T6.7 Route Architecture | TLS/Security/Routes | **Relocated to Phase 7** — see §7 D.3 |
| T6.8 TenantAwareStorageService | TLS/Security/Routes | **Partly delivered, partly relocated.** Phase 4 delivered agent-local root and path-escape enforcement. Storage quota enforcement and per-tenant usage accounting move to Phase 6 (`API-09`) and Phase 10. |
| Alpha plan Stages 1–5 (T1.1–T5.4) | Alpha plan | **Complete.** Retained as historical evidence only. Its completion markers do not establish current conformance — in particular, its tenant field checks are not authenticated tenant isolation. |
| Sealed record commands, Phases 1–10 | Sealed record design | **Complete.** Verified 2026-09-07: `canTransitionTo` is implemented across `TransferStatus`, `TransferAttemptStatus`, `JobAssignmentStatus`, `RouteStatus` and `AgentStatus`, consumed by `QuorusStateStore` and the HTTP handlers, and covered by `StateTransitionIntegrationTest`. |

---

## 10. Section G — Deferred and Research

Deferral is explicit. Documentation MUST NOT imply any of these is current
(enterprise plan §23).

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
| **DEF-09** | Admin UI build/buy decision as framed in the OTel plan (six months of operational feedback, then decide) | **Superseded** — Phase 11 makes the operator and administration interfaces a required M4 deliverable. The OTel plan's deferral framing was removed on 2026-09-07 (`DOC-06`). |

`ARCH-09` (HTTP adapter buffers the full payload) was never deferred. It was closed on
2026-09-26 by `RT-03b` (§14), and Phase 12 scale validation still measures bounded memory.

---

## 11. Section H — Documentation Remediation

Documentation work from the [2026-09-24 documentation review](../reviews/QUORUS_DOCUMENTATION_REVIEW_2026-09-24.md),
merged here on 2026-09-26 from the separate task list (now
[archived](../archive/QUORUS_DOCUMENTATION_REVIEW_TASKS.md) with its dated progress notes). Task IDs
are unchanged. Unlike delivery work, these tasks are owned directly by this register (§15.3).
Code and configuration defects found by the same review are delivery items in Section I, where
the old `DR-X*` IDs are listed as aliases. The review's decisions are in the §3 decision log.

The review worked from HEAD `216348a` plus the 2026-09-24 working tree. Re-check a finding
against the current tree before acting on it.

State: ✅ done · 🟨 in progress · ⬜ open · ⏸ blocked (the dependency is named) · ➖ superseded.
Severity (**H**, **M**, **L**) is the review's own rating.

**Recommended order:** finish DR-A4, whose ADR-0009 is no longer blocked; then take the open
decisions DR-Q1, DR-Q3, DR-Q4 and DR-Q5; then Phases B and C. Phase D can start at any time,
and DR-C10 is no longer blocked, because
`docs-design/evidence/raw/` was committed in `d2514ec`.

Done and superseded tasks move to the revision history one revision after they are marked (§15.5): DR-A2,
DR-A3, DR-A5, DR-A6, DR-A7, DR-B7, DR-C3, DR-F07, DR-F08, DR-F13 and DR-F15 in v1.9, and DR-A1, DR-C11,
DR-F14 and DR-F17 in v1.10, and DR-B3 in v1.11.

### H.1 Phase A — Correctness and safety

| ID | Sev. | Task | Files | Done when | State |
|---|---|---|---|---|---|
| **DR-A4** | H | Fix revocation-serial normalisation in `CertificateTrustState` (compare `BigInteger` values, or strip leading zeros on both sides) and add a test that uses an openssl-formatted, zero-padded serial. Update Security Guide §4.2 and Certificate Incident Runbook §4.1, §4.2 and §4.4: send the revocation to every controller, add it to configuration before any restart, and state that Raft has no CRL. | `CertificateTrustState.java:78,127`, `docs/QUORUS_SECURITY_DEPLOYMENT_GUIDE.md`, `docs/QUORUS_CERTIFICATE_INCIDENT_RUNBOOK.md` | Code, test and operating guidance complete; 17/17 focused tests pass. Register entries `SEC-01` and `SEC-02` added 2026-09-26. ADR-0009 remains (DR-D4). | 🟨 |

### H.2 Phase B — Reconcile the canonical set

| ID | Task | Files | State |
|---|---|---|---|
| **DR-B1** | **Architecture Spec.** Close or narrow ARCH-03, ARCH-06, ARCH-12 (only an escalation policy is missing) and ARCH-13. Fix the §3 telemetry row (five events and a `STALLED` boundary) and the §13 lifecycle gate (QR-01 is fixed). Define "durable default" using `quorus.raft.storage.path`. Move "Closed" out of the Priority column. ARCH-09 (HTTP buffering) is done: closed in spec v2.10 on 2026-09-27 after `RT-03b`, which also changed §2 to Java 27. Also: add the missing ARCH-01 or renumber, reorder the IDs, fix the §7 opening, make the untestable requirements in §7.1 measurable, and note that the SFTP direct-URI path does not meet §10.4's "visibly logged" rule (see DR-X06). | `docs/QUORUS_ARCHITECTURE_SPECIFICATION.md` | 🟨 |
| **DR-B2** | **REST Spec.** Label §3.2, §3.4, §3.5, §3.8, §4.2, §6.3 and §16 as Current, Required or Planned. Add mapping tables from `ErrorCode` Q-codes to target codes and from colon scopes to dotted scopes. Close API-01 by citing `OpenApiContractTest`, and rewrite API-02. List `GET /api/v1/openapi.yaml` as Current. Fix the `DELETE /transfers/{id}` purpose text (it returns `{jobId, message}`), the §6.1 events row, the path-parameter names, the agent "search" and route "conditional update" claims, and the §3.1 unknown-fields rule. Also: §3.2 names `X-Correlation-ID`, but the server and the OpenAPI contract use `X-Request-ID`. | `docs/QUORUS_REST_API_SPECIFICATION.md` | 🟨 Spec v2.6 (2026-09-27, DR-Q7): `/api/v1/openapi.yaml` is listed as Current; the "current implementation" paragraphs are replaced by pointers to the OpenAPI contract; API-12 is corrected to Partial; `OpenApiReferenceContractTest` now fails if a Current row and the contract disagree. API-01 and API-02 were already closed. Remaining: the labels, mapping tables, `DELETE` purpose text, path-parameter names, the search and conditional-update claims, the unknown-fields rule, and the correlation header |
| **DR-B4** | **YAML Syntax Guide** (⏸ DR-Q1). Add a "Validation requirements" section listing the seven metadata fields and `spec.execution`. Mark `execution.*` and `retryCount` "parsed, not applied", or implement them. Fix the options and nesting claims, advise quoting `created`, add runtime `ExecutionContext` variables to the precedence list, and fix the examples table (`batchSize` location, `ecommerce-order-processing.yaml`, the `file` protocol and the `mode` option). Merge the correct rules from `YAML-VALIDATION-GUIDE.md` (names cannot contain spaces; no `kind` warnings, JSON Schema or streaming validation), then delete that guide. Update the Workflows README to match. | `docs/QUORUS_YAML_SYNTAX_GUIDE.md`, `docs/QUORUS_WORKFLOWS_README.md`, `quorus-integration-examples/.../docs/YAML-VALIDATION-GUIDE.md` | ⬜ |
| **DR-B5** | **Security Guide** (⏸ DR-Q3). Move §11–14 into the Service Connection Runbook and a new `docs/QUORUS_UPGRADE_NOTES.md`. Document gateway-only elevation, that `/api/v1/openapi.yaml` is public, which headers are actually required (`X-Quorus-Roles` and `X-Quorus-Scopes` default to empty), and that Raft peers are not bound to node IDs. Update §11 for R1-1. Replace `CONTROLLER_URL` with the current name. Note that the "audit path configured" and "trust-bundle version" checks are always satisfied by the packaged defaults. | `docs/QUORUS_SECURITY_DEPLOYMENT_GUIDE.md`, `docs/QUORUS_SERVICE_CONNECTION_OPERATIONS_RUNBOOK.md` | ⬜ |
| **DR-B6** | **Versioning Policy** (⏸ DR-Q5). Record the command and snapshot schema as version 3, readable from 0. Record the configuration-contract break in `b35fb25`. Define one product version and use it in the pom, `HttpApiServer.VERSION`, OpenAPI `info.version`, `quorus.version` and `quorus.agent.version` (§6 #13). Also record the Java release policy (`RT-Q3`: follow each six-monthly release) and the rule that QRaft's minimum Java version must not exceed Quorus's. | `docs-design/reference/QUORUS_VERSIONING_AND_COMPATIBILITY_POLICY.md`, poms, `HttpApiServer.java`, `quorus-controller-v1.yaml`, properties files | ⬜ |

### H.3 Phase C — Consolidate and archive

| ID | Task | State |
|---|---|---|
| **DR-C1** | Move the 12 `docs-design/dev/vertx5-advice/` files to the PeeGeeQ repository. Delete `performance/CRITICAL_PERFORMANCE_REFACTORING_GUIDELINES.md` (APEX). | ⬜ |
| **DR-C2** | Move to `docs-design/archive/`, each with a one-line "why archived" banner: the three `CONNECTION_POOL_*` documents; `VERTX5_PERFORMANCE_BENCHMARKS.md`; all six `vertx-migration/` files (keep SUMMARY as the one migration record); `testing/FTPS_INTEGRATION_TEST_INVESTIGATION.md` (mark it resolved first); `evidence/remediation-r4-r6-2026-09-05.md`; and the Configuration Handover (⏸ extract §2.1 and §8 into the configuration reference first, see DR-D1). | 🟨 Configuration Handover archived 2026-09-26 (extraction still pending under DR-D1); the rest remain. Since `RT-03a` (2026-09-26), the three `CONNECTION_POOL_*` documents describe code that no longer exists. |
| **DR-C4** | Merge `docs/QUORUS_CLUSTER_STARTUP_GUIDE.md`, `docs/QUORUS-DOCKER-TESTING-README.md` and `quorus-controller/DOCKER_BUILD_OPTIMIZATION.md` into `docker/README.md`. The result has one table giving each Compose file's topology, host ports, required environment and status, and it states that no `m2cache` build context or `M2_REPO` is needed (DR-C11 is done) and that images package host-built jars. Fix the Quick Start port (8080 is not mapped), use `docker compose` throughout, and fix the last link label. | ⬜ |
| **DR-C5** | Split `QUORUS_SYSTEM_DESIGN.md`. Move the enterprise requirements to the Architecture Spec (or delete them and link). Archive the PostgreSQL/Redis/etcd, Kubernetes, SQL, changelog, duplicated and file-organisation sections. Badge what remains. Rename `QuorusStateMachine` to `QuorusStateStore` throughout. Fix the environment names (`QUORUS_RAFT_*`), the `AppConfig` loading description, the tech-stack versions, the health JSON and the metric names. | ⬜ |
| **DR-C6** | Extract `docs-design/reference/QUORUS_RAFT_STORAGE_REFERENCE.md` from the Raft WAL design, with the contents listed in review §10: coordinates, layering, method contract, on-disk layout, every storage and snapshot key, recovery order, InstallSnapshot, operator rules, test map, and the unproven power-loss case. Archive the remainder. | ⬜ Scope narrowed on 2026-09-26: under ADR-0011 the in-repository engine and sidecar are replaced by QRaft (`CE-10`), so the reference should cover the current design briefly and link to QRaft's storage documentation rather than duplicate it |
| **DR-C7** | Trim the Simulators design. Rewrite §1 against current code. Relabel §2–7 as standalone test doubles. Restore the links to `RaftChaosTest`, `RaftFailureTest` and `InfrastructureSmokeTest`. Delete Appendix C. Document `MockRaftTransport`, update the `RaftTransport` listing, mark the DSL and full-stack examples as proposals, and tick the delivered Appendix D items. | ⬜ |
| **DR-C8** | Rewrite `QUORUS_NEGATIVE_TESTING_STRATEGY.md` around `@ExpectsError` / `ExpectsErrorExtension` (negative tests have run by default since `8864c2f`). Align `QUORUS_LOG_STYLE.md` with the code: ASCII markers, the TRACE levels, the real `logback-test.xml`, no `-Dtest.loglevel`, and no personal hostname, username or IP. Update `QUORUS_PROTOCOL_SERVERS_TESTING.md` (images, environment, the Testcontainers tests, `*IT` never runs) and `QUORUS_RAFT_CLUSTER_TESTING.md` (no `m2cache` context after DR-C11, `QUORUS_RAFT_*`, 5000/1000, raftlog 1.2.0, `quorus-loadbalancer`, `raft` read from the top level of `/health` rather than `checks.raft.state`, the JUnit Docker suites, the header). Add `ContainerRecreationDurabilityTest` to `DOCKER_TEST_PERFORMANCE.md` and record CPU and RAM. Have LOG_STYLE and NEGATIVE_TESTING link to the Testing README instead of carrying their own logback samples. | ⬜ The testing documents also move from the Vert.x test standard to the `RT-02` standard as each module migrates. |
| **DR-C9** | Scrub the PeeGeeQ references from `QuorusConfiguration.java:30`, `AppConfigNodeIdentityTest.java:82`, `VertxPerformanceBenchmark.java:45`, `scripts/add-license-headers.sh` and `scripts/setup-git-hooks.sh` (the hook checks `peegeeq-*` paths, so it does nothing in Quorus). Remove `vertx-pg-client` and `ConnectionPoolService`, and the unused `vertx-grpc-*` dependencies, or document why they stay (§6 #15, #22). | 🟨 `ConnectionPoolService` and `vertx-pg-client` removed by `RT-03a` on 2026-09-26 ([evidence](../evidence/rt-03a-remove-dead-pool-2026-09-26.json)). Remaining: PeeGeeQ references in code and scripts, and the unused `vertx-grpc-*` dependencies |
| **DR-C10** | Clean the working copy: delete the local `temp/` worktrees, `.history/` and `hs_err_pid*.log`, and untrack the five `temp/*.txt` files still in git. Normalise line endings with a `* text=auto` rule, committed on its own. Merge NOTICE and OPEN_SOURCE_USAGE into one generated inventory (see DR-F01). | ⬜ Unblocked 2026-09-27: `docs-design/evidence/raw/` is committed (`d2514ec`, 146 files). Still present on 2026-09-27: three full source-tree worktrees under `temp/` (they clutter every repository-wide search), the five tracked `temp/*.txt` files, `.history/` and two `hs_err_pid*.log` files. When adding `* text=auto`, keep the `docs-design/evidence/raw/** -text` rule after it. Otherwise git normalises line endings in evidence whose SHA-256 is recorded. Absorbs DR-F17. |

### H.4 Phase D — Keep it accurate

| ID | Task | State |
|---|---|---|
| **DR-D1** | **Generate, don't copy.** Generate `docs/QUORUS_CONFIGURATION_REFERENCE.md` from the properties files and the `AppConfig` / `AgentConfig` key constants, seeded from Configuration Handover §2.1 and §8. | ⬜ The endpoint half was resolved on 2026-09-27 by DR-Q7 without generation: the OpenAPI contract is the reference, the REST spec's Current rows are checked against it by test, and `/api/v1/info` links to it |
| **DR-D2** | **CI documentation checks:** a ban on personal Windows user-profile paths; `docker compose config` on every `docker/compose/*.yml`; and a smoke job that starts the single-controller topology. The relative-link checker and header linter this task first asked for already exist in `scripts/verify-phase0-docs.ps1` and run in CI. They fail, and making them pass is part of `ENG-07`. | ⬜ Re-scoped 2026-09-27; follows `ENG-07` (deferred, `SEQ-01`) |
| **DR-D3** | **One status vocabulary.** Implemented / Partial / Planned for capabilities; Current / Required / Planned for API items. Remove the seven ad-hoc values in Arch Spec §13. | ⬜ |
| **DR-D4** | **ADR hygiene** (ADR-0010 ⏸ DR-Q1; ADR-0009 is no longer blocked, because DR-Q2 was decided on 2026-09-25). Add ADR-0006 (raftlog-core WAL and snapshot sidecar), ADR-0007 (layered configuration, no system properties), ADR-0008 (schema-3 coordinated upgrade), ADR-0009 (trust-state scope), ADR-0010 (YAML semantics), and consider one for Raft over grpc-java rather than Vert.x gRPC. Add an index, Supersedes / Superseded-by fields and an Alternatives section. Fix ADR-0002's fencing statement, which is now out of date. | 🟨 ADR-0011 and ADR-0012 added 2026-09-26. ADR-0012 holds decisions `RT-Q1` to `RT-Q5`; v1.2 (2026-09-27) corrects `RT-Q4` and adds a revision history. ADR-0006 should record only the current raftlog-and-sidecar design and name ADR-0011 as its planned successor. ADR-0009 and ADR-0010 remain |
| **DR-D5** | **Definition of done:** any change to a public contract (endpoint, key, environment variable, Compose file or status) updates its canonical document in the same commit, and plans and registers cite a SHA only after the commit exists. Add this to plan §6 and to `.github/copilot-instructions.md`. | ⬜ |

### H.5 Document fixes outside Phases A–D

| ID | Document | Fix | State |
|---|---|---|---|
| **DR-F01** | `NOTICE`, `OPEN_SOURCE_USAGE.md` | Until DR-C10 merges them: list only shipped runtime components in NOTICE, and add Netty, `jackson-dataformat-yaml`, `javax.annotation-api` (CDDL) and Apache HttpClient 5 with HttpCore 5 (shipped since `RT-03b`; Apache 2.0, which carries its own NOTICE). Do not add `vertx-pg-client`, which `RT-03a` removed. State that the container images ship Amazon Corretto (GPLv2 with the Classpath Exception) as their base. In OPEN_SOURCE_USAGE, correct RaftLog Core to 1.2.0, remove RocksDB JNI, add `javax.annotation-api` and Apache HttpClient 5, and fix the `LICENSE-HEADER.txt` reference. Confirm that the "licenses directory" exists. | ⬜ Updated 2026-09-27 for `RT-03a`, `RT-03b` and `RT-01b` |
| **DR-F02** | `docs/QUORUS_USER_GUIDE.md` | Remove progress, events and attempts from the gaps list. `QUORUS_AGENT_TENANT_ID` takes priority over the legacy `AGENT_TENANT_ID`. State that the agent defaults to the production profile with TLS. Add an NFS section. (DR-A7 covers the authentication statement.) | ⬜ |
| **DR-F03** | `docs/QUORUS_ARCHITECTURE_QUICKSTART.md` | Link to the OpenAPI contract instead of listing endpoints (its related-documents entry was repointed on 2026-09-27). State the packaged `127.0.0.1` and production-profile defaults. Use `maven.compiler.release`. | ⬜ |
| **DR-F04** | `docs/QUORUS_INTEGRATION_EXAMPLES_README.md` | Add an `mvn install` step, mention the default `mainClass` (`SftpFtpRealImplementationDemo`), and add `IntegrationTestSuite`. | ⬜ |
| **DR-F05** | `docs/QUORUS_SERVICE_CONNECTION_OPERATIONS_RUNBOOK.md` | Add the R4 and R5 behaviour (DNS 503/504/409, FTPS 21 vs 990, partial updates, event paging) and the elevation requirement. Correct the "must set" statement for agent pool and roots, which are not enforced. Coordinate with DR-B5. | ⬜ |
| **DR-F06** | `docs/QUORUS_CERTIFICATE_INCIDENT_RUNBOOK.md` | Add an example request body and elevation header, and explain how to start a new audit chain by repointing `quorus.security.audit.evidence-path`. | ⬜ |
| **DR-F09** | `reference/QUORUS_REPRODUCIBLE_BUILD_AND_EVIDENCE.md` | Add `project.build.outputTimestamp` to the poms, or say the build is repeatable rather than byte-reproducible. Update the evidence figures from M0 (2,212) to R6 (2,437). | ⬜ |
| **DR-F10** | `evidence/` small fixes | `full-suite-error-remediation`: cite `a0103a0`. `r4-dns-remediation`: replace "changes are uncommitted" with `8b3cf5c`. `r5-closure`: add the closure commit. `r6-final-acceptance`: map `b604505` to `dc447d4` and correct the "transport failure retained" claim (`RaftNode.java:602-616`). `raftlog-validation-handover`: add a supersession pointer to R1-1. Add header blocks to the 2026-09-05 files. | ⬜ |
| **DR-F11** | `performance/QUORUS_PERFORMANCE_VALIDATION_RESULTS.md` | Relabel it as a Vert.x `executeBlocking` micro-benchmark. Remove the "Phase 4 PostgreSQL" and "quorus-api 7 tests" claims, and align its targets with the test's assertions. | ⬜ |
| **DR-F12** | `testing/QUORUS_TESTING_README.md` | Remove `quorus-api` from the quick-build `-pl` list and the consolidated-log module list. Remove `-Dgroups='!flaky'`. Say that `*IT` classes do not run in a default build (see DR-Q4). Describe the Testcontainers-based upload tests. | ⬜ |
| **DR-F16** | Test classification | Record in the Testing README that six Testcontainers tests run in default builds without a `docker` tag, and add `ContainerRecreationDurabilityTest` and `docker-compose-3node-durable.yml` to the testing documents. | ⬜ |

**Out of scope:** evidence held in the separate raftlog repository (library SHAs and the "41
storage tests / 319 library tests" claims), which the review did not verify; and the review's
§11 partial items, whose `testing/` overlap DR-C4 and DR-C8 absorb.

**Former documentation corrections `DOC-01` to `DOC-06`** were applied to the OTel plan on
2026-09-07 and removed from this section in v1.5; register v1.4 holds the detail.

---

## 12. Section I — Configuration and Documentation-Review Delivery Items

Delivery work identified by the configuration baseline remediation and by the
[2026-09-24 documentation review](../reviews/QUORUS_DOCUMENTATION_REVIEW_2026-09-24.md). The
Task column gives the review's original ID; `DR-X*` IDs from the former task list are aliases
for these rows (`DR-X05` is `ARCH-09`, closed by `RT-03b`; `DR-X07` is `SEC-04`, which will close through
`CE-08`; `DR-X19` is `ENG-04`, which `RT-04` subsumes; `DR-X24` is `ENG-03`, which `RT-05` subsumes). Rows marked *reported* were confirmed by the
review on 2026-09-25 but have not been re-checked since; re-verify each against the current tree
before implementation.

Phase assignment is still outstanding for most rows. Plan v1.28 added them without phases, and
no revision since has assigned any. The exceptions are:

- `SEC-07` is a Phase 4 hardening follow-up (`SEQ-02`).
- `ENG-07` belongs to Phase 0's CI controls, and its repair is deferred (`SEQ-01`).
- `ENG-01` goes with `P2-01`.
- `SEC-04` closes through `CE-08`.
- `ENG-03` and `ENG-04` are subsumed by `RT-05` and `RT-04`.

| ID | Item | Task | Evidence state | Level |
|---|---|---|---|---|
| **CFG-01** | Make repository Compose security posture explicit, remove unsupported environment settings and duplicate topology, fix image health probing, separate logging-stack names/ports, and provide a generated-certificate mTLS example | `DR-A5` | ✅ **Closed 2026-09-25** — all 14 Compose models validate; the TLS example is healthy, accepts its generated gateway identity, and rejects a client without a certificate. Repository-local validation, not production accreditation. | ✅ |
| **CFG-02** | `AgentConfig.getForeignAssignmentMismatchThreshold()` defaults to 3 while the packaged value is 1, so builder- or override-based configurations can diverge from production | `DR-X16` (review §6 #16) | Verified 2026-09-26 (`AgentConfig.java:174`, `quorus-agent.properties:96`) | 🟢 |
| **CFG-03** | Invalid numeric configuration values fall back to the accessor default with a WARN instead of failing validation | review §6 #16 | Verified 2026-09-26 (`LayeredProperties.java:54-67`); the review's "silently" is corrected — a warning is logged | 🟢 |
| **CFG-04** | A blank environment value cannot clear a packaged value, because blank overrides are skipped | review §6 #16 | Verified 2026-09-26 (`LayeredProperties.java:85`) | 🟢 |
| **CFG-05** | `QuorusConfiguration` reads `System.getenv()` directly, so its environment layer cannot be injected in tests the way `AppConfig`'s can | review §6 #16 | Verified 2026-09-26 (`QuorusConfiguration.java:211`) | 🟢 |
| **SEC-01** | Revocation serials with leading zeros never matched | `DR-A4` | Code fixed: `CertificateTrustState.normalize` strips leading zeros (verified 2026-09-26); the task list records 17/17 focused `SecurityBoundaryIntegrationTest` passes and updated operating guidance. Closure awaits ADR-0009 and retained evidence. | 🟠 |
| **SEC-02** | Runtime revocation is node-local and volatile | `DR-Q2`, `DR-A4`, `DR-D4` | Decision recorded 2026-09-25: keep node-local; operators update every controller and persist the set in configuration before restart. ADR-0009 outstanding. | 🟠 |
| **SEC-03** | Direct-URI SFTP disables host-key checking without logging (residual of `QR-03`) | `DR-X06` | Reported (`SftpTransferProtocol.java:406-408`) | 🟠 |
| **SEC-04** | Raft peer certificates are not bound to the `QUORUS_CLUSTER_NODES` identity; any cluster-CA certificate can make Raft RPCs | `DR-X07` | Reported (`RaftPeerAuthorizationInterceptor`) | 🟠 |
| **SEC-05** | `roleAllows` returns on the first matching role, so multi-role identities can be denied scopes another role grants | `DR-X09` | Verified still present 2026-09-26 (`AuthorizationPolicyEngine.java:78-107`) | 🟠 |
| **SEC-06** | Direct mTLS identities cannot hold elevation; only gateway-asserted identities can perform elevated operations | `DR-Q3`, `DR-B5` | Reported; decision pending | 🟠 |
| **SEC-07** | Governed TLS trusts only the JVM default trust store (`TlsPeerPolicy` uses `TrustManagerFactory.init(null)`), so endpoints issued by a private corporate CA work only if that CA is added to the JVM `cacerts`; there is no Quorus configuration for trust anchors | found by `RT-03b` | Verified 2026-09-26 (`TlsPeerPolicy.createTrustManager`). The rewritten HTTP adapter accepts an injected base trust for tests; production still uses the JVM default. Assigned 2026-09-27 to Phase 4 as a hardening follow-up with acceptance criteria (plan §11, `SEQ-02`); must close before any production service connection relies on a private CA | 🟠 |
| **ENG-01** | `JobAssignmentService`, which owns the assignment timeout monitor, is constructed only by its test | `DR-X11` | Reported; settle with `P2-01` | 🟡 |
| **ENG-02** | `*IT` and `*Benchmark` classes never run: no Failsafe plugin and no Surefire includes | `DR-Q4`, `DR-X18` | Reported; decision pending | 🟠 |
| **ENG-03** | `QuorusAgent.java:372` calls `.join()`; whether it can run on an event loop is untraced | `DR-X24` | Reported, not traced | 🟠 |
| **ENG-04** | `SimpleWorkflowEngine` public constructor calls `Vertx.vertx()` | `DR-X19` | Reported | 🟢 |
| **ENG-05** | `workflow-schema.json` is never loaded although `json-schema-validator` is a dependency | `DR-X21` | Reported | 🟢 |
| **ENG-06** | Small code-comment corrections: the `mvn test -Dgroups=docker,slow` pom comment, and Javadoc mentioning the removed `memory` storage type and "blocking mode" | `DR-X17`, `DR-X20` | Reported | 🟢 |
| **ENG-07** | CI has never passed: all 21 GitHub Actions runs from 2026-09-01 to 2026-09-26 failed. (1) Unit and clean-build lanes: `FtpsDefaultPortBoundaryTest` listens on port 21, which a non-root process cannot bind on the Linux runner (it passes on Windows). The build stops in `quorus-core`, so the other modules have never been tested in CI. (2) Documentation lane: `scripts/verify-phase0-docs.ps1` rejects 11 document headers that lack the two trailing spaces its pattern requires. Trailing spaces are easily stripped by editors, so decide whether to relax the check or add the spaces. The fix needs a test-first redesign of the FTPS default-port test | found 2026-09-27 during the register review | Verified 2026-09-27 from the logs of runs `36275459592` (Java 27) and `36192430573` (Java 25). **Repair deferred by `SEQ-01`.** Blocks every phase exit (plan §6.1 step 5), not current slices | 🟡 |
| **ENG-08** | The current HTTP API was described four times by hand, and the copies had drifted: the API Reference (dated 2026-09-05), the REST spec's Current rows and "current implementation" paragraphs, and the endpoint list in `/api/v1/info` (35 of 52 routes). The OpenAPI contract itself declared no per-operation scopes, a wholly wrong `AgentStatus` enum, and no 504 for DNS authorization | DR-Q7, DR-B3 | ✅ **Closed 2026-09-27** ([evidence](../evidence/eng-08-openapi-reference-2026-09-27.json)). The contract is the only current-API reference. `OpenApiReferenceContractTest` checks each operation's scope against `AuthorizationPolicyEngine`, the public operations against the new `PublicEndpoints`, `AgentStatus` against the server's values, the 409/503/504 responses of the two DNS-authorizing operations, and the REST spec's Current rows. `/api/v1/info` links to the contract. The API Reference is deleted | ✅ |
| **ENG-09** | The FTP and SFTP adapters each keep one `activeClient` field shared by every transfer they run, so `TransferProtocol.abort()` closes only the most recently started connection, and one transfer finishing clears another's reference. Before RT-03c the engine called `abort()` on cancel, which aborted the wrong or every transfer of that protocol (for HTTP, every in-flight client). Since RT-03c cancellation interrupts only the named transfer and `abort()` has no production caller | found by `RT-03c` | Verified 2026-09-27 (`FtpTransferProtocol.java:89`, `SftpTransferProtocol` `abort`). ✅ **Closed 2026-09-27** (recorded in the commit message): `abort()` is removed from `TransferProtocol` and every adapter, with the shared `activeClient` fields, the HTTP in-flight registry and the dead `forceDisconnect` methods. `TransferProtocolCancellationContractTest` fails if an adapter-wide abort or per-transfer connection state returns | ✅ |
| **ENG-10** | The FTP, SFTP, SMB and NFS adapters never reported progress to the transfer's job and never checked the context: each kept progress in a private `ProgressTracker` that nothing read. So the engine saw no progress for them, pause had no effect, and only interruption stopped them. Separately, the agent reported `IN_PROGRESS` once with 0 bytes and then only the final result, for every protocol, so the controller's progress, freshness and stall views were never fed by a real agent. No test covered either | found 2026-09-27 during `RT-03c` follow-up | ✅ **Closed 2026-09-27** (recorded in the commit message). A `ProgressTracker` built from the context records progress on the job and exposes `stopRequested()`; the four adapters use it, and SFTP now fails a transfer JSch stopped early. The agent sends a progress report whenever the byte count has grown, at `quorus.agent.jobs.progress-report-interval-ms` (15 s), one report at a time, and resends an unresolved report exactly before the final one. Tests: `AdapterProgressAndStopTest` (NFS locally, FTP and SFTP on Docker) and `TransferProgressReportingIntegrationTest`. SMB has no server fixture; it shares the tested tracker. A failed non-HTTP download can still leave a partial destination file, which is `P2-07` | ✅ |
| **SEC-08** | `FtpTransferProtocol` had a public `setSslSocketFactory` that replaced TLS verification on the shared adapter; only a test used it, to trust any certificate | found 2026-09-27 | ✅ **Closed 2026-09-27** (recorded in the commit message): the setter is removed; the test factory is passed to a package-private constructor and fixed at construction | ✅ |
| **ENG-11** | `TransferTelemetryMetrics` is a JVM-wide singleton, so an engine's health check reports the protocol statistics of every engine in the process. Harmless in production (one engine per agent), but engines sharing a JVM, as in tests, see each other's protocols; one exact-count test failed this way on 2026-09-27 and now asserts the engine's own protocols by name | found 2026-09-27 | Recorded; decide whether health should be per engine when the metrics move off Vert.x (`RT-07`) | 🟢 |

`DR-X05` (HTTP adapter buffering) is `ARCH-09`, closed by `RT-03b` on 2026-09-26, and is not duplicated here.

---

## 13. Section J — Platform Migration Workstreams

From enterprise plan [§20](QUORUS_ENTERPRISE_IMPLEMENTATION_PLAN.md#20-platform-migration-workstreams), with decisions in
[ADR-0011](../architecture-decisions/ADR-0011-CONSENSUS-VIA-QRAFT-GENERIC-ENGINE.md) and
[ADR-0012](../architecture-decisions/ADR-0012-JAVA-RUNTIME-AND-STRUCTURED-CONCURRENCY.md). The
plan holds the acceptance criteria; this section lists identity, owner and level. `CE-01` to
`CE-06` are QRaft-project deliverables that Quorus depends on. The workstream decisions
`RT-Q1` to `RT-Q5` are in the §3 decision log only (§15.4).

| ID | Owner | Item | Level |
|---|---|---|---|
| **CE-01** | QRaft | Extract the reusable engine from `qraft-controller` | 🟡 |
| **CE-02** | QRaft | Generic, JDK-typed public engine API | 🟡 |
| **CE-03** | QRaft | Raft transport security interfaces: TLS 1.3 mutual authentication and peer authorizer | 🔴 — blocks adoption without regressing Phase 1 |
| **CE-04** | QRaft | Generic observability interface | 🟠 |
| **CE-05** | QRaft | Genericity enforcement: no Quorus or Vert.x in the engine dependency tree; QRaft's own state machines use only the public API | 🟡 |
| **CE-06** | QRaft | Versioned, resolvable engine artifacts | 🟡 |
| **CE-07** | Quorus | Engine adapter (state machine, codec, error mapping, one temporary Vert.x bridge) | 🟡 |
| **CE-08** | Quorus | Phase 1 trust wiring through QRaft; closes `SEC-04` | 🔴 |
| **CE-09** | Quorus | Raft state migration and rollback (raftlog 1.2.0 → QRaft's version; snapshot formats) | 🔴 |
| **CE-10** | Quorus | Remove the in-repository engine and the direct `raftlog-core` dependency | 🟡 |
| **CE-11** | Quorus | Re-establish durability evidence on the QRaft build; R1-2 and R1-3 run against it | 🔴 |
| **RT-01a** | Quorus | Java 27 compile and test baseline (root pom, `.java-version`), proven by `JavaPlatformBaselineTest` | ✅ 2026-09-26: red, green and a 2,387-test regression on JDK 27 ([evidence](../evidence/rt-01a-java27-baseline-2026-09-26.json)). Commit `a9b1ace`, with `RT-01b` |
| **RT-01b** | Quorus | Java 27 controller and agent images and CI toolchain on Amazon Corretto 27 | ✅ 2026-09-26: single-stage images packaging host-built jars on `amazoncorretto:27.0.0-alpine3.24`; no Java or Maven inside Docker. Red, green and a Docker+slow regression of 2,421 tests with 0 failures and 2 pre-existing skips ([evidence](../evidence/rt-01b-java27-images-2026-09-26.json)). Commit `a9b1ace`. CI now runs on Corretto 27, and its setup and Java 27 checks pass. CI has never passed as a whole, for reasons that predate this item (`ENG-07`) |
| **RT-02** | Quorus | Concurrency conventions, the task-scope abstraction and the post-Vert.x test standard | ✅ 2026-09-26: RT-02a TaskScope core ([evidence](../evidence/rt-02a-task-scope-2026-09-26.json)); RT-02b tracing, MDC and `ScopedValue` propagation ([evidence](../evidence/rt-02b-context-propagation-2026-09-26.json)); RT-02c [concurrency conventions](../dev/QUORUS_CONCURRENCY_CONVENTIONS.md) with the post-Vert.x test standard, referenced from plan §6.1 and the Copilot instructions; RT-02d StructuredTaskScope structure rules and migration mapping ([evidence](../evidence/rt-02d-structure-rules-2026-09-26.json)); TaskScope 163/163 lines and 70/70 branches. Commits `341a509` (a), `3e4ec99` (b), `95ed659` (c), `a39206e` (d); raw evidence committed in `d2514ec` |
| **RT-03** | Quorus | `quorus-core` off Vert.x; streaming HTTP adapter closes `ARCH-09` | 🟨 RT-03a done 2026-09-26: dead pool code removed (`5a4274f`). RT-03b done 2026-09-26: the HTTP adapter on Apache HttpClient 5 (`RT-Q5`), blocking and streaming, governed pinning with correct SNI, `Host` and hostname verification, closing `ARCH-09` (`fadbb29`, `336ec37`; [evidence](../evidence/rt-03b-http-adapter-2026-09-26.json)). RT-03c done 2026-09-27 ([evidence](../evidence/rt-03c-blocking-engine-2026-09-27.json)): `TransferEngine` is blocking (`transfer`, `shutdown(Duration)`) with no Vert.x; the engine runs on the caller's thread with a semaphore limit, retries, and cancellation by interrupting only the named transfer; the agent, workflow and examples call it; `OBS-07` closed and `ENG-09` found. Next: RT-03d contract cleanup (with `ENG-09`), RT-03e topology and codec, and RT-03f removing `io.vertx` from the core pom. Slices are defined in plan §20 |
| **RT-04** | Quorus | `quorus-workflow` and `quorus-integration-examples` off Vert.x | 🟠 |
| **RT-05** | Quorus | `quorus-agent` off Vert.x. Until then the agent runs transfers on Vert.x worker (platform) threads, where an interrupt cannot break a blocked socket read, so cancellation waits for the adapter's next check or socket timeout; decided 2026-09-27 to resolve this by removing Vert.x, not with an interim bridge | 🟡 |
| **RT-06** | Quorus | `quorus-controller` HTTP API off Vert.x; removes the `CE-07` bridge | 🟡 |
| **RT-07** | Quorus | OpenTelemetry instrumentation without Vert.x integration | 🟠 |
| **RT-08** | Quorus | Vert.x removal gate enforced in the build | 🟡 |
| **RT-09** | Quorus | Recurring: adopt each six-monthly Java release within its update window | 🟠 |

**Sequencing that affects other sections:** R1-2 and R1-3 (Section A) and Phase 8 should run
after `CE-11`, so their evidence describes the engine that ships. `RT-06` should precede the bulk
of Phase 6. `SEC-04` (Section I) is expected to close through `CE-08` rather than as a change to
the in-repository engine.

---

## 14. Gap-to-Section Traceability

| Gap | Status | Where the remaining work lives |
|---|---|---|
| `ARCH-01` Agent omits `IN_PROGRESS` | Closed | Phase 2 structural delivery |
| `ARCH-02` No attempt lease or fencing | Partly open | P2-01, P2-02, P2-03 |
| `ARCH-03` No authenticated identity boundary | Closed | Phase 1 |
| `ARCH-04` Route trigger evaluator not wired | Open | Phase 7 (§7 D.3) |
| `ARCH-05` Retriable writes lack idempotency and leader discovery | Open | P2-04, Phase 6 |
| `ARCH-06` Assignment reference and tenant invariants incomplete | Closed | Phases 0, 1, R2 |
| `ARCH-07` Persistent controller path and volume not proven | Open | **R1-2, R1-3** (R1-1 closed 2026-09-07), `CE-11`, Phase 8 |
| `ARCH-08` SFTP host-key verification disabled | Closed | Phase 4 |
| `ARCH-09` HTTP adapter buffers full payload | Closed 2026-09-26 by `RT-03b` (streaming download and upload); Architecture Specification v2.10 updated 2026-09-27 | Phase 12 scale validation still measures it |
| `ARCH-10` Dynamic membership absent | Deferred | DEF-01 |
| `ARCH-11` Transfer operations telemetry incomplete | Partly open | P3-01 … P3-12 |
| `ARCH-12` Operational business context absent | Closed | Phase 3 first slice |
| `ARCH-13` TLS/mTLS boundary incomplete | Closed | Phase 1 |
| `ARCH-14` Service alias, egress, verification, secret policy absent | Closed | Phase 4 |
| `ARCH-15` Agent identity lifecycle incomplete | Open | Phase 5 |
| `ARCH-16` Governed agent deployment absent | Open | Phase 5 |
| `ARCH-17` Credential-bearing production transfer paths | Closed for transfers | Route/workflow activation must reuse the governed model — Phase 7 |
| `ARCH-18` REST coverage incomplete | Open | Phase 6 |
| `API-01` OpenAPI and path coverage absent | Closed | `OpenApiContractTest` and `OpenApiReferenceContractTest`; Phase 6 adds the Required endpoints |
| `API-02` Authenticated scope enforcement absent | Closed | Phase 1 |
| `API-03` Transfer lifecycle and evidence resources absent | Partly open | P2-11, P3-06, P3-07, Phase 6 |
| `API-04` Operational risk and alert APIs absent | Partly open | P3-06, P3-09 |
| `API-05` Secure agent lifecycle API absent | Partly open | Phase 5 |
| `API-06` Service connection and secret-reference API absent | Closed | Phase 4 |
| `API-07` Assignment lease and fencing contract absent | Partly open | P2-02, P2-03 |
| `API-08` Workflow REST resources absent | Open | Phases 6, 7 |
| `API-09` Tenant and quota REST resources absent | Open | Phase 6 |
| `API-10` Route validation and execution history absent | Partly open | Phase 7 |
| `API-11` Audit query and export API absent | Open | Phases 6, 9 |
| `API-12` Standard reliability conventions absent | Partly open | P2-04, Phase 6 |
| `API-13` Cluster and configuration administration incomplete | Partly open | Phases 6, 8, 10 |
| `API-14` Compatibility, retention, export and replay incomplete | Open | P3-11, Phases 6, 8, 9 |

---

## 15. Register Governance

1. This register is edited directly as the single task list, but it must never disagree with the
   canonical specifications or the plan's acceptance criteria. When a disagreement is found,
   correct whichever document is wrong in the same commit.
2. An item is removed only when the source plan's exit criterion is met with retained evidence
   under §6.1 — not when the code merely exists.
3. New delivery work is added to the enterprise plan first, then reflected here. Documentation
   tasks (Section H) are owned directly by this register and need no plan entry. Archived sources,
   including the former documentation task list, are never reopened for new work; correct them
   only to fix a misleading statement.
4. Every decision that governs open work is entered in the §3 decision log with its choice, its
   record (an ADR or plan section) and the work it unblocks. An open decision names what it blocks.
5. Section H tasks keep their `DR-*` IDs. A done or superseded task stays for one revision
   after it is marked, then moves to the revision history.
6. Verification claims in this register that were checked against live source are dated inline.
   Re-verify before relying on them; a claim dated 2026-09-07 is not evidence about a later tree.

### Revision history

| Version | Date | Changes |
|---|---|---|
| 1.12 | 2026-09-27 | `DR-Q6` revised: a slice's record is its commit message. `RT-03c` follow-up: `ENG-09` closed (adapter-wide abort and shared connection state removed); new and closed `ENG-10` (adapters and agent now report in-flight progress and honour stop requests) and `SEC-08` (FTP trust-all setter removed); new `ENG-11` (metrics singleton). The agent's deprecated owned-Vert.x constructor is removed. `RT-05` records the agent cancellation limitation and the decision to resolve it by removing Vert.x. Plan v1.37 is cited |
| 1.11 | 2026-09-27 | `RT-03c` done: blocking `TransferEngine` on the caller's thread, with no Vert.x. `OBS-07` closed by the rewrite (37 DEBUG statements to 3). New `ENG-09`: the FTP and SFTP adapters share one abort target across transfers. Plan v1.36 is cited. **Moved from Section H under §15.5:** DR-B3 (superseded by DR-Q7; its fixes were made in the OpenAPI contract under `ENG-08`) |
| 1.10 | 2026-09-27 | API documentation consolidated (decision DR-Q7): the OpenAPI contract is the only current-API reference, and the API Reference is deleted. New and closed: `ENG-08`, with `OpenApiReferenceContractTest`. DR-B3 is superseded, DR-B2 has progressed, and DR-D1 and DR-F03 are updated. §14 now agrees with REST spec §20 for API-01, -04, -05, -10 and -13. Plan v1.35 is cited. **Moved from Section H under §15.5:** DR-A1 (R1-1 committed; raw logs unrecoverable, limitation recorded in the evidence record), DR-C11 (the `m2cache`/`M2_REPO`/`m2-repo` machinery removed by `RT-01b`), DR-F14 (personal paths removed from live documents; `QUORUS_LOG_STYLE.md` remains under DR-C8), all done; and DR-F17 (merged into DR-C10) |
| 1.9 | 2026-09-27 | Review of v1.8 against the repository and CI. **New:** `ENG-07`, because CI has never passed (FTPS test binds privileged port 21 on Linux; 11 document headers fail the documentation check). Decisions `SEQ-01` (defer the CI repair; local full-reactor runs are regression evidence meanwhile; no phase closes while `ENG-07` is open) and `SEQ-02` (`SEC-07` becomes a Phase 4 hardening follow-up). **Corrected:** `RT-Q4` (runtime option A, single-stage images, no builder or Maven; ADR-0012 v1.2); the Section G `ARCH-09` paragraph, which contradicted §14 (the Architecture Specification v2.10 now also closes it); RT-01b's "CI not yet executed"; DR-D2, re-scoped because the header and link checks already exist; DR-D4, whose ADR-0009 is no longer blocked; DR-F01, updated for `RT-03a`, `RT-03b` and `RT-01b`; the E.2 tenant-lock and WebClient rows; the Section I phase-assignment statement; the §1 test rule, which now points to plan §6.1; governance rule 1 and §1 precedence item 3, which now agree; §14 `ARCH-07`, which no longer lists the closed R1-1; DR-C4's DR-C11 wording; the plan version (v1.34) and §2 counts. **Closed:** DR-A1 (R1-1 raw logs are unrecoverable; evidence §3 and §10.4 annotated and §13 added) and DR-F14. DR-F17 merged into DR-C10. **Sequencing:** `OBS-07` goes into `RT-03c`; `OBS-15` waits for `CE-07`/`CE-10`. Section J cites commits for completed work, and its duplicate `RT-Q1`–`RT-Q3` rows are removed. **Moved from Section H under §15.5:** DR-A2 (orphaned SHAs preserved via the rewrite map), DR-A3 (prompt file removed, Copilot instructions reconciled), DR-A5 (explicit Compose development profile, mTLS example, health probe; `CFG-01`), DR-A6 (README quick start and HTTPie tenant fields), DR-A7 (authentication statements point to Architecture Spec §3), DR-B7 (register v1.5 / plan v1.28 pass), DR-F07 (reviews moved to `docs-design/reviews/`), DR-F08 (`docs-design/README.md` v1.1), DR-F13 (Configuration Handover link and path fixed, archived), all done; DR-C3 (superseded by `RT-02` conventions) and DR-F15 (superseded by ADR-0011 and `CE-10`). Also records changes made after v1.8 without a version: `RT-Q5`, `SEC-07`, the DR-Q6 refinement, DR-C11 done, DR-C9 in progress, and the `RT-01`–`RT-03` states |
| 1.8 | 2026-09-26 | Became the single task list: merged the documentation review task list (archived) as Section H and its code defects as aliases in Section I; added the §3 decision log with ADR-0011, ADR-0012, `RT-Q1`–`RT-Q3`, DR-Q1–DR-Q6 and the Phase 0/4 status decision; applied the decisions to tasks (DR-C3 and DR-F15 superseded, DR-C4, DR-C8, DR-B6 and DR-D4 updated, new DR-C11 to remove the `m2cache`/`M2_REPO`/`m2-repo` machinery); renumbered sections after §2; updated governance |
| 1.7 | 2026-09-26 | Recorded decisions `RT-Q1` (no preview; task-scope abstraction), `RT-Q2` (JDK `HttpsServer`) and `RT-Q3` (six-monthly Java releases); added recurring `RT-09`; cites plan v1.30 |
| 1.6 | 2026-09-26 | Added Section J for plan §20 platform migration workstreams (`CE-01`–`CE-11`, `RT-Q1`–`RT-Q3`, `RT-01`–`RT-08`) under ADR-0011 and ADR-0012; updated the raftlog constraint (Maven Central coordinates, reached through QRaft after `CE-10`); routed `ARCH-09` to `RT-03` and `ARCH-07` through `CE-11`; renumbered traceability and governance to §13 and §14; updated plan section references |
| 1.5 | 2026-09-26 | Documentation-review pass (`DR-B7`): closed OBS-04, OBS-05 and OBS-14 as already satisfied and corrected OBS-07 to 37 statements; fixed the section counts, plan and OTel versions and revision order; settled the Phase 0 and Phase 4 status statement; past-tensed the fixed fixture-volume statement; added `R1-4`, `PROC-01` and `P2-13` for plan items without IDs; added Section I for `CFG-01` (moved from D.6), the four configuration residuals, and the security and engineering defects from the documentation review; collapsed Section H after its retention revision; renumbered traceability and governance to §12 and §13 |
| 1.4 | 2026-09-25 | Recorded `CFG-01` complete after validation of the explicit development posture, Compose cleanup, corrected health probing and generated-certificate mTLS example |
| 1.3 | 2026-09-07 | Remediated the three findings from the R1-1 slice: containerised test fixtures now write Raft state to named volumes at the deployed path, orphaned `TransferMetrics` deleted (`OBS-08`), and the `LeaderGuardHandlerTest` startup flake root-caused and fixed; recorded the unswept discarded-`start()`-future pattern as `OBS-15` |
| 1.2 | 2026-09-07 | Closed `R1-1` container-recreation acceptance with four containerised tests and a controller regression of 601 tests; recorded the non-durable default Docker test fixture found during the work; classified the recovery tests as retrospective characterization because no product defect was found |
| 1.1 | 2026-09-07 | Applied all six Section H corrections to the OTel plan (v2.5 → v2.6), including removal of three production-readiness claims it should not have made; archived the alpha plan, Stage 6 security/routes plan, OTel plan and sealed-record design, leaving `task/` holding only the enterprise plan and this register; repaired every cross-reference broken by the move |
| 1.0 | 2026-09-07 | Initial consolidation of all outstanding tasks from the five `docs-design/task/` planning documents, with live-source verification of eleven stale OTel grid claims and the sealed-record transition phases |
