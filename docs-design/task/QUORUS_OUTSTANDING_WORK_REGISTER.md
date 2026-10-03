<img src="../../docs/quorus-logo.png" alt="Quorus" width="120"/>

# Quorus Outstanding Work Register

**Version:** 1.27  
**Date:** 2026-10-03  
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
| [QUORUS_ENTERPRISE_IMPLEMENTATION_PLAN.md](QUORUS_ENTERPRISE_IMPLEMENTATION_PLAN.md) v1.44 | Controlling roadmap | Sections A–D, F, I, J |
| Documentation review of 2026-10-02 (recorded in this register only; no separate review document) | Re-review of every live document against the tree at `6a8acb1` | Section H rows marked "2026-10-02 review" and Section I rows `SEC-09` to `SEC-11` and `ENG-17` to `ENG-20`, with `ENG-21` to `ENG-25` found while the documents were being fixed |
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
| 🟨 **In progress** | Work has started; the row says what remains |
| ✅ **Done** | The exit criterion is met; the row cites the commit once one exists |

Every implementation item below is delivered under the §6.1 mandatory TDD protocol:
preserved behavioral red through a real HTTP, agent, protocol, or cluster boundary before
implementation, then green, refactor, and regression, recorded in the commit message. Asynchronous
tests follow the rules in plan §6.1. Code that has left Vert.x uses the test standard in
[concurrency conventions §6](../dev/QUORUS_CONCURRENCY_CONVENTIONS.md#6-asynchronous-test-standard).
While CI is red (`ENG-07`, deferred by `SEQ-01`), a slice's regression evidence is its local
full-reactor run.

---

## 2. Summary

| Section | Area | Open items | Blocking level |
|---|---|---|---|
| §3 | Decision log | 28 taken, 5 open (CE-Q1 to CE-Q5) | Open decisions block named tasks |
| A | R1 durability acceptance and related process items | 4 open (R1-2, R1-3, R1-4, PROC-01), 1 closed | 🔴 Release blocker |
| B | Phase 2 — attempts, integrity, reconciliation | 13 | 🟡 Phase blocker |
| C | Phase 3 — transfer operations telemetry | 12 | 🟡 Phase blocker |
| D | Phases 5–12 — not started | 8 phases | 🔴 / 🟡 by phase |
| E | Observability and logging backlog | 10 open (OBS-04, -05, -07, -08, -14 closed) | 🟠 Backlog |
| F | Absorbed and superseded historical tasks | 10 | — reference only |
| G | Deferred and research | 8 deferred, 1 superseded | 🟢 Deferred |
| H | Documentation remediation (from the 2026-09-24 and 2026-10-02 reviews) | 41 listed: 4 open, 9 in progress, 28 done on 2026-10-03 and awaiting commit (they move to the revision history one revision after that). 17 more, done or superseded earlier, are already in the revision history | 🔵 Documentation |
| I | Configuration and documentation-review delivery items | 25 open (one, `ENG-15`, in progress), 20 closed (ten of them on 2026-10-03) | 🟠 Backlog; 🟡 `ENG-01`, `ENG-07` |
| J | Platform migration — QRaft consensus and Vert.x exit | 15 open (11 CE; 4 RT), 6 RT done. Decisions `RT-Q1`–`RT-Q5` are in §3 | 🟡 / 🔴 by item |

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
| **DR-Q6** | Record of a slice | A slice's record is its commit message: red and green results, mutation checks, regression totals and characterization labels | Plan §6.1 | All implementation slices | ✅ 2026-09-27; restated 2026-10-02, when `docs-design/evidence/` was deleted and the commit message became the only record of earlier slices too |
| **STATUS-01** | Phase 0 and Phase 4 status wording | Phase 0 functionally complete, with durability acceptance reopened until R1-2 and R1-3; Phase 4 complete, its 2026-09-04 reopening restored by R2–R6 | Plan header, §7, §11 | Section 2 phase position | ✅ 2026-09-26 |
| **RT-Q4** | Java 27 container images | **Amazon Corretto 27** (amd64 and arm64, published 2026-09-18). Temurin had no Java 27 images, and the official `openjdk` image offers only non-production `27-rc` tags (checked 2026-09-26). Runtime variant: option A, `amazoncorretto:27.0.0-alpine3.24`, because Corretto 27 has no JRE-only Alpine image. The images are single-stage and copy jars built on the host. No image contains Maven or a builder stage | ADR-0012 v1.2 | `RT-01b`, Docker-tagged lanes, `RT-09` | ✅ 2026-09-26 |
| **RT-Q5** | HTTP client for the HTTP transfer adapter | Apache HttpClient 5 (classic API). `java.net.http` cannot connect to a pinned IP while enforcing hostname verification and sending the correct `Host` (measured 2026-09-26) | ADR-0012 | `RT-03b` | ✅ 2026-09-26 |
| **SEQ-01** | When to repair CI, which has never passed (`ENG-07`) | Defer the repair and continue platform work first. Until then, each slice's regression evidence is its local full-reactor run. No phase can close while `ENG-07` is open, because plan §6.1 step 5 requires every applicable lane to pass | Plan §4 (2026-09-27), Phase 0 status | `ENG-07`, DR-D2 | ✅ 2026-09-27 |
| **SEQ-02** | Where the governed TLS trust-anchor gap (`SEC-07`) is delivered | A Phase 4 hardening follow-up: trust-anchor certificates configured and audited by Quorus, narrowed by the existing approved-CA fingerprints. It must close before any production service connection relies on a private CA | Plan §4 and §11 (2026-09-27) | `SEC-07` | ✅ 2026-09-27 |
| **CE-Q1** | Style of the QRaft engine API | Open: `CompletableFuture`, or blocking calls with a timeout for virtual threads (the Quorus conventions favour blocking) | [QRaft integration assessment](../design/QUORUS_QRAFT_INTEGRATION_ASSESSMENT.md) §5 | `CE-02`, `CE-07` | ⬜ |
| **CE-Q2** | Linearizable reads in the first engine version | Open: a ReadIndex or leader-lease read in `CE-02`, or leader-only writes with stale follower reads accepted for now (agent polling after a failover is the case to judge) | Assessment §5 | `CE-02`, `CE-07` | ⬜ |
| **CE-Q3** | Dynamic membership | Open: out of scope for the first QRaft version? Quorus `ARCH-10` needs it eventually | Assessment §5 | `CE-02`, `ARCH-10` | ⬜ |
| **CE-Q4** | raftlog 1.2.0 files under 1.4.0 | Open: readable as they are, or converted? Decides whether `CE-09` is a proof or a migration | Assessment §5 | `CE-09` | ⬜ |
| **CE-Q5** | Engine packaging and the genericity proof | Open: a standalone, unshaded `qraft-raft-engine` artifact, with QRaft's own key/value and catalog state machines moved onto the same public API first (ADR-0011 decision 3) | Assessment §5 | `CE-01`, `CE-05`, `CE-06` | ⬜ |
| **DR-Q7** | Where the current HTTP API is documented | The bundled OpenAPI contract only: it is self-documenting and served at `GET /api/v1/openapi.yaml`. The hand-written API Reference is deleted. The REST API Specification holds requirements only, and a test checks its "Current" rows against the contract. `/api/v1/info` links to the contract instead of listing endpoints | REST API Specification v2.6 §1; plan §4 (2026-09-27) | DR-B2, DR-B3, DR-D1, DR-F03, `ENG-08` | ✅ 2026-09-27 |
| **DR-Q1** | Workflow YAML semantics (review §4.3) | Implement (b): a defined, documented set of transfer `options` is passed through to the transfer request and adapters, unknown keys fail validation, and nested variable references resolve recursively with a depth limit. Accepting and silently dropping options is not acceptable. The rest of (b) was already delivered: `execution.timeout` and `parallelism` by `RT-04`/`ENG-12`, and `dryRun`, `virtualRun`, group `retryCount` and `strategy` validation by `ENG-13` | ADR-0010 (to be written) | DR-D4, DR-B4 (revise the guide when delivered); delivery item `ENG-26` | ✅ 2026-10-03 |
| **ENG-Q1** | Workflow conditions (`ENG-14`) | Implement a small grammar: `success(name)`, `failure(name)`, `file_exists(path)` and `and`/`or`/`not`; anything outside the grammar fails validation | ADR-0010 (to be written) | `ENG-14` | ✅ 2026-10-03 |
| **DR-Q3** | Elevation for direct mTLS identities (review §6 #8) | (b), in a restricted form: a separately configured break-glass certificate binding may elevate, with a short elevation window and mandatory audit, for incidents when the gateway is unavailable. Ordinary certificate bindings never elevate. Needs a short design first | ADR-0009 or its own ADR | DR-B5, `SEC-06` | ✅ 2026-10-03 |
| **DR-Q4** | How `*IT` and `*Benchmark` classes run (review §6 #18) | (b): rename `ProtocolServersLifecycleIT` to `ProtocolServersLifecycleIntegrationTest`, tag it `docker` so it runs in the Docker lane, and do not use the `*IT` suffix. No Failsafe plugin | Testing README | `ENG-02`, DR-F16, `ENG-24` | ✅ 2026-10-03. **Delivered the same day.** Running the class for the first time showed it was broken as well as unrun: two of five tests failed. Fixed: it connects to `127.0.0.1` (the ports are published there only and `localhost` can resolve to `::1`), waits for the services' health checks (`up -d --wait`) instead of an open port, and the compose file's FTP health check, which had always failed for the same `localhost` reason, uses `127.0.0.1`. `quorus-core` now excludes the `docker` tag by default, as the controller does. The teardown no longer deletes volumes, which have fixed names shared with a hand-started stack. Five of five pass in the Docker lane |
| **DR-Q5** | Authoritative product version (review §4.10, §6 #13) | The root pom version is the product version, filtered into the controller and agent at build time; `/api/v1/info`, `/health`, startup logs and Raft metadata read it; `quorus.version` and `quorus.agent.version` stop being settings. The OpenAPI `info.version` stays the API contract version. A test fails if the reported versions diverge from the pom. **Delivered 2026-10-03**, test-first: one filtered resource and `ProductVersion` in `quorus-core`, which the controller and agent both use; `quorus.version`, `quorus.agent.version`, `AGENT_VERSION`, `HttpApiServer.VERSION` and the agent builder's `version` are removed. One departure from the decision: the product version is **not** written into Raft metadata. That metadata is replicated state, and a per-build value there would make nodes on different builds hold different state during an upgrade; the controller stops seeding it and the state machine's `version` key keeps its own default | Versioning policy v1.3 | DR-B6 | ✅ 2026-10-03 |
| **ENG-Q2** | Agent deregistration (`ENG-25`) | Add `DELETE /api/v1/agents/{agentId}`: self-only for an `AGENT` identity or an operator scope, leader-only, Raft-committed, audited, in OpenAPI, refused while the agent holds active assignments. The agent stops treating `404` as success. As delivered, an agent whose finished work still names it is kept with status `DEREGISTERED` rather than removed, so the history keeps a valid reference | — | `ENG-25` | ✅ 2026-10-03 |
| **ENG-Q3** | How agents reach the leader in multi-controller topologies (`ENG-19`) | The agent accepts a list of controller URLs and follows leader hints: on `503 NOT_LEADER` it retries against the named leader, and it keeps retrying registration instead of exiting. The controller sends the leader's address in `X-Quorus-Leader`, as REST Spec §3.8 requires | REST Spec §3.8 | `ENG-19`, `ENG-27`, API-12 | ✅ 2026-10-03 |
| **SEQ-05** | When `ENG-21` (possible double vote during recovery) is handled, and the order of the work decided on 2026-10-03 | Verify `ENG-21` now with a failing-test attempt; if real, fix it in the QRaft adapter (`CE-07`), not the in-repository engine. Revised the same day, after the test confirmed it: fix it now in the in-repository engine as well. Order: the `vertx-grpc` removal and DR-C10 cleanup; `ENG-21` verification; `ENG-25`; `ENG-27`; DR-Q5; DR-Q4; `ENG-26`; `ENG-14`; DR-Q3 (design first). Each slice is reported on completion | — | `ENG-21`, Section I | ✅ 2026-10-03 |
| **DR-Q11** | Working-copy cleanup scope (DR-C10) | All of it: untrack `temp/*.txt`; delete the `temp/` worktrees after checking none holds uncommitted work; delete `.history/` and the `hs_err_pid*.log` files; add `* text=auto` in its own commit; replace NOTICE and OPEN_SOURCE_USAGE upkeep with one generated inventory | — | DR-C10 | ✅ 2026-10-03 |
| **RT-Q6** | How the agent's cancellation limitation on Vert.x worker threads is resolved | Remove Vert.x from the agent (`RT-05`) rather than add an interim bridge | ADR-0012; plan §20 | `RT-05` | ✅ 2026-09-27 (entered in this log 2026-10-03) |
| **SEQ-03** | Order of the controller HTTP migration and QRaft adoption | `RT-06` proceeds before QRaft, after the `ENG-15` baselines, in slices `RT-06a` to `RT-06d`; only the in-repository Raft engine stays on Vert.x until `CE-10` | Plan v1.42 §20 | `RT-06`, `CE-07` | ✅ 2026-09-28 (entered in this log 2026-10-03) |
| **SEQ-04** | When the audit write-path fix lands | `ENG-16` lands before `RT-06b`, so that the controller HTTP migration is measured against an uncapped API | Plan §20 | `ENG-16`, `RT-06b` | ✅ 2026-09-28 (entered in this log 2026-10-03) |
| **DR-Q8** | Fate of the Vert.x-era benchmark documents | Keep and adapt them: the benchmark document becomes the benchmark specification, the results document becomes its results log, and a complete benchmark module is built | Plan §19; `QUORUS_PERFORMANCE_BENCHMARKS.md` v2.0 | `ENG-15`, DR-F11, DR-C2 | ✅ 2026-09-28 (entered in this log 2026-10-03) |
| **DR-Q9** | Where the PeeGeeQ-derived `vertx5-advice` documents go | Keep them archived in Quorus with an "Archived" banner, instead of moving them to the PeeGeeQ repository | — | DR-C1 | ✅ 2026-09-28 (entered in this log 2026-10-03) |
| **DR-Q10** | How the documentation lane's header check (`ENG-07` part 2) is satisfied | Add the two trailing spaces to the header lines. Markdown needs them as line breaks: without them the Version, Date and Author lines render as one run-on paragraph, so the check is right and the documents are wrong | `scripts/verify-phase0-docs.ps1` | `ENG-07` | ✅ 2026-10-03 |

---

## 4. Section A — R1 Durability Acceptance (Release Blockers)

Remediation slices R2–R6 are implementation-complete. R6 final acceptance was verified from a
clean detached worktree at revision `dc447d4`: 2,437 tests, zero failures or errors, two
existing explicit skips, and all five configured JaCoCo gates.

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
storage class and host kernel are not.

A material fixture gap was found and is recorded there: before this slice,
`docker-compose-3node-prebuilt.yml` declared no volumes and no `QUORUS_RAFT_STORAGE_PATH`, so
every earlier containerised test ran Raft state on the container's ephemeral layer and could not
have detected a container-level durability regression. The fixtures were corrected in register
v1.3: containerised tests now write Raft state to named volumes at the deployed path.

| ID | Related open item | Detail | Level |
|---|---|---|---|
| **R1-4** | Persistent-environment storage inventory | Inventory existing persistent environments and preserve their storage before any recovery or rollback attempt (constraint below) | 🔴 |
| **PROC-01** | Disposition of the two Raft regression cases | The two Raft regression cases without a preserved red stage need an explicit, recorded process-deviation disposition (constraint below) | 🟡 |

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
- The two Raft regression cases without a preserved red stage remain historical process
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
| **OBS-11** | Service-level tracing for `AgentRegistrationService`, `HeartbeatService`, `JobPollingService` (since `RT-05b` each controller call is a client span from `ControllerClient`; there is still no span per service operation) | agent | 🟢 LOW |
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
| Replace Apache HttpClient with Vert.x WebClient (agent) | Complete — alpha plan T3.1. The direction has since been reversed by ADR-0012: `RT-05` replaced the agent's `WebClient` with `java.net.http`, and the HTTP transfer adapter now uses Apache HttpClient 5 (`RT-Q5`) |
| Replace Java Serialization with Protobuf | Complete — alpha plan T5.4, now at version 2 command/snapshot contracts |
| Add gRPC TLS encryption | Complete — Phase 1 delivered TLS 1.3 mutual authentication for Raft server and peer clients |
| Add `TransferProtocol.abort()` | Superseded: added, then removed from `TransferProtocol` and every adapter by `ENG-09` (2026-09-27), because cancellation interrupts the named transfer instead |
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

The documentation review of 2026-10-02 re-checked every live document against the tree at
`6a8acb1`. Its findings were added to the existing rows (each addition says "2026-10-02 review")
or as new rows DR-A8 to DR-A11, DR-B8, DR-B9 and DR-F18 to DR-F23; its code and configuration
defects are Section I rows `SEC-09` to `SEC-11` and `ENG-17` to `ENG-20`, with `ENG-21` to `ENG-25` found while the documents were being fixed. `ENG-29` was found on 2026-10-03 while delivering DR-Q5.

**Recommended order (2026-10-03):** DR-A8 to DR-A11 are done. Next, DR-A4 with ADR-0009 (DR-D4),
then the open decisions DR-Q1, DR-Q3, DR-Q4 and DR-Q5, which unblock the rest of DR-B4, DR-B5,
DR-B6 and `ENG-24`. The remaining parts of DR-B1, DR-B2 and DR-C8 can go at any time, and so can
Phase D and DR-C10. In Section I, `ENG-21` (a consensus-safety defect) was confirmed and fixed on 2026-10-03; Section I continues in the `SEQ-05` order.

Done and superseded tasks move to the revision history one revision after they are marked (§15.5): DR-A2,
DR-A3, DR-A5, DR-A6, DR-A7, DR-B7, DR-C3, DR-F07, DR-F08, DR-F13 and DR-F15 in v1.9, and DR-A1, DR-C11,
DR-F14 and DR-F17 in v1.10, DR-B3 in v1.11, and DR-F11 in v1.27. DR-F10 (fixes to files under
`docs-design/evidence/`) was removed in v1.26 as obsolete when that directory was deleted.

### H.1 Phase A — Correctness and safety

| ID | Sev. | Task | Files | Done when | State |
|---|---|---|---|---|---|
| **DR-A4** | H | Fix revocation-serial normalisation in `CertificateTrustState` (compare `BigInteger` values, or strip leading zeros on both sides) and add a test that uses an openssl-formatted, zero-padded serial. Update Security Guide §4.2 and Certificate Incident Runbook §4.1, §4.2 and §4.4: send the revocation to every controller, add it to configuration before any restart, and state that Raft has no CRL. | `CertificateTrustState.java:78,127`, `docs/QUORUS_SECURITY_DEPLOYMENT_GUIDE.md`, `docs/QUORUS_CERTIFICATE_INCIDENT_RUNBOOK.md` | Code, test and operating guidance complete; 17/17 focused tests pass. Register entries `SEC-01` and `SEC-02` added 2026-09-26. ADR-0009 remains (DR-D4). | 🟨 |
| **DR-A8** | H | 2026-10-02 review. Restore the document classification. `docs-design/README.md`, which defined each `docs-design` directory's status and the interpretation rules, was deleted by commit `2b93940` (subject: "Add comprehensive Vert.x 5 migration guide…", which does not mention it) on the day DR-F08 updated it. The root README still links to it (`README.md:206`) and describes a classification (`README.md:190`) that is now written down nowhere. Restore it from `2b93940^`, updated: no API Reference (DR-Q7), no `evidence/` (DR-Q6), `reviews/` and `reference/` listed. Extend the link check in `scripts/verify-phase0-docs.ps1` to the root README, `docker/README.md` and every `docs-design` directory except `archive/`, so a broken link like this one fails the documentation lane. | `docs-design/README.md`, `README.md`, `scripts/verify-phase0-docs.ps1` | The file exists and is linked; the extended check passes | ✅ 2026-10-03, uncommitted: `docs-design/README.md` v1.2 restored and updated; the documentation check now link-checks the root README, `docker/README.md` and every live `docs-design` directory, and a planted broken link in `testing/` fails it |
| **DR-A9** | H | 2026-10-02 review. The documents present the unwired assignment scheduler as live. Architecture Spec §7 steps 1–3 (`:301-303`) say the leader commits the job *and its assignment*; §10.3 (`:426`) says selection requires pool and zone. Quickstart's enforcement path step 3 (`:163-171`) names `AgentSelectionService` as a running tenant gate. Neither `JobAssignmentService` nor `AgentSelectionService` is constructed in production code (`ENG-01`): a submitted transfer gets an assignment only through `POST /api/v1/assignments`, and `JobAssignmentHandler` checks no pool or zone. State this until `ENG-01` is delivered. | `docs/QUORUS_ARCHITECTURE_SPECIFICATION.md`, `docs/QUORUS_ARCHITECTURE_QUICKSTART.md` | Both documents describe administrative assignment as the only path | ✅ 2026-10-03, uncommitted: Architecture Spec §7 and §10.3, Quickstart, README, User Guide and Copilot instructions state that assignment is administrative only |
| **DR-A10** | H | 2026-10-02 review. The revocation procedure cannot be carried out as written. The Certificate Incident Runbook (`:47`, `:59`, step 4.1.3) and Security Guide (`:120`, `:191`) send `PUT /api/v1/security/trust/revocations` to every controller, but followers reject it with 503 (`SEC-09`). Also: Raft revocation is enforced only on inbound RPCs, so "can no longer join" (runbook `:84`) and "evaluated on every … Raft RPC" (guide `:71`) overstate it (`SEC-10`); an omitted `revokedCertificateSerials` clears every revocation, and the audit event records only counts (`SEC-11`). Update both documents when `SEC-09` lands, and state the `SEC-10` and `SEC-11` limits until they are fixed. | `docs/QUORUS_CERTIFICATE_INCIDENT_RUNBOOK.md`, `docs/QUORUS_SECURITY_DEPLOYMENT_GUIDE.md` | The documented procedure works on a three-node cluster, and the remaining limits are stated | ✅ 2026-10-03, uncommitted, with `SEC-09` and `SEC-11` fixed: runbook §4.1, §4.4 and new §4.5 (request example, elevation, audit-before-apply), Security Guide §4.2 and §10. The `SEC-10` limit is stated in both |
| **DR-A11** | H | 2026-10-02 review. Multi-node health checks prove nothing. The root README (`:114-128`) and Cluster Startup Guide (`:62-64`, `:86-95`) verify the `controller-first` topology with `curl localhost:8080/health…`. Port 8080 is nginx, whose `location /health` prefix match answers `/health`, `/health/live` and `/health/ready` itself (`docker/compose/nginx/nginx.conf:48`), so the checks return 200 with every controller down. Verify against controller ports 8081–8083; `ENG-18` fixes the nginx rule. | `README.md`, `docs/QUORUS_CLUSTER_STARTUP_GUIDE.md` | The documented checks reach a controller | ✅ 2026-10-03, uncommitted, with `ENG-18` fixed: the README and the Docker guide check each controller on 8081–8083 |

### H.2 Phase B — Reconcile the canonical set

| ID | Task | Files | State |
|---|---|---|---|
| **DR-B1** | **Architecture Spec.** ARCH-03 and ARCH-06 are already Closed in the spec; narrow ARCH-12 (only an escalation policy is missing) and ARCH-13. Fix the §3 telemetry row (five events and a `STALLED` boundary) and the §13 lifecycle gate (QR-01 is fixed). Define "durable default" using `quorus.raft.storage.path`. Move "Closed" out of the Priority column. ARCH-09 (HTTP buffering) is done: closed in spec v2.10 on 2026-09-27 after `RT-03b`, which also changed §2 to Java 27. Also: add the missing ARCH-01 or renumber, reorder the IDs, fix the §7 opening, make the untestable requirements in §7.1 measurable, and note that the SFTP direct-URI path does not meet §10.4's "visibly logged" rule (see DR-X06). 2026-10-02 review adds: §13's lifecycle gate (`:743`) still gives the `IN_PROGRESS` gap as its reason, but the agent reports `ACCEPTED` then `IN_PROGRESS`; `:623` and `:744` say business service, deadline and ownership are absent, but `TransferOperationalContext` carries them; `:77` calls the stall boundary required and the event ledger "the first ordered submission event", while `:651` and `:668` say five events and an implemented stall boundary; the schema-3 text sits under §5.4 "Read consistency" (`:181-199`) and the agent replay paragraph opens §7 (`:294-297`); Related Documents (`:835-836`) points to the archived reviews. The module tables are DR-F23 and the §7 and §10.3 assignment text is DR-A9. | `docs/QUORUS_ARCHITECTURE_SPECIFICATION.md` | 🟨 2026-10-03, uncommitted (spec v2.13): ARCH-12 and ARCH-13 narrowed; the §3 telemetry row, §12.2 business context, §13 lifecycle, critical-context and revocation gates, and the §13 evidence rule corrected; ARCH-01 added as Closed; registry and schema text moved out of §5.4; the replay paragraph moved after the §7 steps; Related Documents repointed. Remaining: define "durable default" with `quorus.raft.storage.path`, make the §7.1 requirements measurable, note the SFTP direct-URI logging gap against §10.4 (`SEC-03`), reorder the gap IDs |
| **DR-B2** | **REST Spec.** Label §3.2, §3.4, §3.5, §3.8, §4.2, §6.3 and §16 as Current, Required or Planned. Add mapping tables from `ErrorCode` Q-codes to target codes and from colon scopes to dotted scopes. Close API-01 by citing `OpenApiContractTest`, and rewrite API-02. List `GET /api/v1/openapi.yaml` as Current. Fix the `DELETE /transfers/{id}` purpose text (it returns `{jobId, message}`), the §6.1 events row, the path-parameter names, the agent "search" and route "conditional update" claims, and the §3.1 unknown-fields rule. Also: §3.2 names `X-Correlation-ID`, but the server and the OpenAPI contract use `X-Request-ID`. 2026-10-02 review adds: none of the §4.2 scope names exist (the enforced scopes are colon-form, `AuthorizationPolicyEngine.java:51-74`); §3.8 (`:158`) says a follower sends `Retry-After` and `X-Quorus-Leader`, but `LeaderGuardHandler` sets neither and nothing reads `consistency` or `maxStaleness`; of §16's 21 "stable" codes only `NOT_LEADER` exists, and `NO_LEADER`, which the Architecture Spec names, is missing; §3.4's problem fields differ from the contract's closed `Problem` schema; `:72` requires an `Authorization` header that nothing reads (the contract declares mutual TLS and gateway `X-Quorus-*` headers only); `:65` says enum values are upper snake case, but `AgentStatus` is lower case; §6.2's nested request example is not the flat current schema. | `docs/QUORUS_REST_API_SPECIFICATION.md` | 🟨 Spec v2.6 (2026-09-27, DR-Q7): `/api/v1/openapi.yaml` is listed as Current; the "current implementation" paragraphs are replaced by pointers to the OpenAPI contract; API-12 is corrected to Partial; `OpenApiReferenceContractTest` now fails if a Current row and the contract disagree. API-01 and API-02 were already closed. Remaining: the labels, mapping tables, `DELETE` purpose text, path-parameter names, the search and conditional-update claims, the unknown-fields rule, and the correlation header. 2026-10-03, uncommitted (spec v2.7): every §3 subsection, §4.2, §6.2, §6.3 and §16 carries a Current position; scope and error-code mapping tables added; the state mapping added; the correlation header, `DELETE` purpose, events row, unknown-fields position, API-04, API-12 and API-13 corrected; node-local writes stated in §3.8. Remaining: the path-parameter names (`transferId` against the contract's `jobId`) and the agent "search" and route "conditional update" claims |
| **DR-B4** | **YAML Syntax Guide** (no longer ⏸ DR-Q1: the guide describes current behaviour now, and is revised if DR-Q1 chooses implementation). Add a "Validation requirements" section listing the seven metadata fields and `spec.execution`: the guide's "minimal workflow" (`:43-58`), "only `name` is required" (`:74-85`) and the legacy root-level form (`:37`) all fail validation. `execution.*` and `retryCount` are now applied (`RT-04`, `ENG-13`); the `retryCount` upper bound of 10 (`:214`) is not enforced. Fix the options claim (`:312`, `:331`, `:353`: `TransferGroup.toTransferRequest()` drops every option) and the nesting claim (`:134-145`, `:427-445`: one pass, so a nested reference stays literal with no error), advise quoting `created`, add runtime `ExecutionContext` variables to the precedence list, and fix the examples table (`batchSize` location, `ecommerce-order-processing.yaml`, the `file` protocol and the `mode` option). Merge the correct rules from `YAML-VALIDATION-GUIDE.md` (names cannot contain spaces; no `kind` warnings, JSON Schema or streaming validation), then delete that guide. Update the Workflows README to match. | `docs/QUORUS_YAML_SYNTAX_GUIDE.md`, `docs/QUORUS_WORKFLOWS_README.md`, `quorus-integration-examples/.../docs/YAML-VALIDATION-GUIDE.md` | ✅ 2026-10-03, uncommitted: YAML Syntax Guide v2.4 (new Validation Requirements section, options and nesting limits, precedence, examples); Workflows README v2.2; `YAML-VALIDATION-GUIDE.md` deleted after its correct rules were merged |
| **DR-B5** | **Security Guide** (DR-Q3 decided 2026-10-03: a break-glass binding may elevate; document it when delivered). Move §11–14 into the Service Connection Runbook and a new `docs/QUORUS_UPGRADE_NOTES.md`. Document gateway-only elevation, that `/api/v1/openapi.yaml` is public, which headers are actually required (`X-Quorus-Roles` and `X-Quorus-Scopes` default to empty), and that Raft peers are not bound to node IDs. Update §11 for R1-1. Replace `CONTROLLER_URL` with the current name. Note that the "audit path configured" and "trust-bundle version" checks are always satisfied by the packaged defaults. | `docs/QUORUS_SECURITY_DEPLOYMENT_GUIDE.md`, `docs/QUORUS_SERVICE_CONNECTION_OPERATIONS_RUNBOOK.md` | 🟨 2026-10-03, uncommitted: the service-connection parts of §13 and all of §14 moved to the runbook (DR-F05); §11 updated for R1-1; `CONTROLLER_URL` replaced; the public OpenAPI path, unbound Raft peers and the audit-path defaults are documented (DR-B8); current gateway-only elevation is described as fact, without settling DR-Q3. Remaining: move §11–12 and the rest of §13 to an upgrade-notes document, and state which assertion headers may be empty |
| **DR-B6** | **Versioning Policy** (the product-version part ⏸ DR-Q5; the schema correction is not blocked). Record the command and snapshot schema as version 3, readable from 0 (`SchemaVersionRegistry.java:40-41`); the policy, dated 2026-10-02, still says 1. Record the configuration-contract break in `b35fb25`. Define one product version and use it in the pom, `HttpApiServer.VERSION`, OpenAPI `info.version`, `quorus.version` and `quorus.agent.version` (§6 #13). Also record the Java release policy (`RT-Q3`: follow each six-monthly release) and the rule that QRaft's minimum Java version must not exceed Quorus's. | `docs-design/reference/QUORUS_VERSIONING_AND_COMPATIBILITY_POLICY.md`, poms, `HttpApiServer.java`, `quorus-controller-v1.yaml`, properties files | ✅ 2026-10-03 (policy v1.3): schema version 3 readable from 0, the `b35fb25` configuration break, the schema-3 upgrade and the Java release rule recorded; the product-version rule recorded with the delivery of DR-Q5 |
| **DR-B8** | **Security Guide, parts not waiting for DR-Q3** (2026-10-02 review). §7 (`:156`) says every audit append is durable before the request proceeds; since `ENG-16`, authentication and authorization records are group-committed and awaited, completion records are written after the response and not awaited, and a write or sync failure latches the log so every later authenticated request fails. §5 (`:124-134`) omits the required agent tenant ID and, for the image, `AGENT_ID`; with its production settings the agent image cannot start (`ENG-17`). §4 never mentions `quorus.http.host`, which defaults to `127.0.0.1`. `:167` says to probe health "without identity", but the listener requires a client certificate at the handshake. `:16` says the controller will not start without an audit path, but packaged defaults always satisfy that check; the enforced rule (the two paths must differ) is not stated. `:25` overstates Raft identity as "node membership" (`SEC-04`). `:136` uses the Vert.x term `trustAll`. `:267` describes controller entrypoint precedence for a script the image does not run (`ENG-20`). | `docs/QUORUS_SECURITY_DEPLOYMENT_GUIDE.md` | ✅ 2026-10-03, uncommitted: Security Guide v1.9 |
| **DR-B9** | **Enterprise plan corrections** (2026-10-02 review). The §20 status line (`:1163`) says `RT-06` waits for `CE-07` to `CE-11`; `SEQ-03` reversed that. §3 (`:60`) lists "uncontrolled service connectivity" as a blocker; Phase 4 closed it. The `CE-07` bridge wording (`:1180`, `:1209`, `:1210`) predates `SEQ-03`. Phase 2 "Primary gaps" (`:534`) lists the closed `ARCH-06` and `ARCH-17`. Phases 1 and 4 are shown Complete although `SEQ-01` says no phase closes while `ENG-07` is open; state which applies. `ENG-13`, `ENG-14`, `ENG-15`, `ENG-16`, `OBS-15` and `STATUS-01` are not in the plan (governance rule 3). The v1.43 revision row calls a scope change (a Definition of Done bullet and a Phase 0 deliverable removed) "simplified". §4 opens with 220 lines of dated checkpoints before the milestone table. | `docs-design/task/QUORUS_ENTERPRISE_IMPLEMENTATION_PLAN.md` | ✅ 2026-10-03, uncommitted: plan v1.44 |

### H.3 Phase C — Consolidate and archive

| ID | Task | State |
|---|---|---|
| **DR-C1** | Move the 12 `docs-design/dev/vertx5-advice/` files to the PeeGeeQ repository. Delete `performance/CRITICAL_PERFORMANCE_REFACTORING_GUIDELINES.md` (APEX). | ✅ Decided 2026-09-28: the `vertx5-advice` files stay archived in Quorus (commit `2b93940` moved them to `docs-design/archive/vertx5-advice/`), each with an "Archived" banner, instead of moving to PeeGeeQ (`DR-Q9`). ✅ 2026-10-03, uncommitted: the APEX guideline is deleted |
| **DR-C2** | Move to `docs-design/archive/`, each with a one-line "why archived" banner: the three `CONNECTION_POOL_*` documents; all six `vertx-migration/` files (keep SUMMARY as the one migration record); `testing/FTPS_INTEGRATION_TEST_INVESTIGATION.md` (mark it resolved first); and the Configuration Handover (⏸ extract §2.1 and §8 into the configuration reference first, see DR-D1). | 🟨 Configuration Handover archived 2026-09-26 (extraction still pending under DR-D1); the six `vertx-migration/` files were archived by commit `2b93940` and given banners on 2026-09-28; `VERTX5_PERFORMANCE_BENCHMARKS.md` is no longer to be archived: by decision of 2026-09-28 it was kept, renamed `QUORUS_PERFORMANCE_BENCHMARKS.md` and rewritten as the benchmark specification (`ENG-15`); the rest remain. Since `RT-03a` (2026-09-26), the three `CONNECTION_POOL_*` documents describe code that no longer exists. 2026-10-03, uncommitted: the three `CONNECTION_POOL_*` documents moved to `archive/performance/` and the FTPS investigation to `archive/testing/`, each with a banner. Remaining: only the Configuration Handover extraction (DR-D1) |
| **DR-C4** | Merge `docs/QUORUS_CLUSTER_STARTUP_GUIDE.md`, `docs/QUORUS-DOCKER-TESTING-README.md` and `quorus-controller/DOCKER_BUILD_OPTIMIZATION.md` into `docker/README.md`. The result has one table giving each Compose file's topology, host ports, required environment and status, and it states that no `m2cache` build context or `M2_REPO` is needed (DR-C11 is done) and that images package host-built jars. Fix the Quick Start port (8080 is not mapped), use `docker compose` throughout, and fix the last link label. 2026-10-02 review adds: `DOCKER_BUILD_OPTIMIZATION.md` describes BuildKit cache mounts and an in-image Maven build that no Dockerfile has, so delete it rather than merge it. `docker/README.md` never says to run `docker/build-runtime` first, so a clean checkout fails at the jar `COPY`; its full-network procedure fails (`ENG-19`); its Loki section gives two port sets (`:119-128` against `:199-206`; the compose file maps 3010, 3110 and 9091) and the wrong promtail container name; it claims an SMB server in the full network, which has none; its compose inventory lists 7 of 14 files. The Cluster Startup Guide lists 11 of 14 and gives `AGENT_TENANT_ID` priority (DR-F02). The Docker Testing README's inventory is the one correct list. | ✅ 2026-10-03, uncommitted: `docker/README.md` v3.0 is the single Docker guide; the Cluster Startup Guide, Docker Testing README and `DOCKER_BUILD_OPTIMIZATION.md` are deleted and their links repointed |
| **DR-C5** | Split `QUORUS_SYSTEM_DESIGN.md`. Move the enterprise requirements to the Architecture Spec (or delete them and link). Archive the PostgreSQL/Redis/etcd, Kubernetes, SQL, changelog, duplicated and file-organisation sections. Badge what remains. Rename `QuorusStateMachine` to `QuorusStateStore` throughout. Fix the environment names (`QUORUS_RAFT_*`), the `AppConfig` loading description, the tech-stack versions, the health JSON and the metric names. 2026-10-02 review adds: about 25–30% of the body is current. Also false in the present tense: the controller embedding the workflow, transfer and tenant engines (`:404-429`; controller main imports none of them); a singleton `AppConfig.get()` (`:562-581`) and file-system config locations (`:618-625`); the 320-line route-trigger section (`:686-1010`); `TransferMetrics`, `getProtocolMetrics` and an implemented circuit breaker (`:2104-2149`); `TenantSecurityService`, `TenantAwareStorageService`, `DependencyResolver` and `${env:…}`-style variable built-ins (`:2463-2480`, `:3105-3122`); database-level tenant isolation with no caveat (`:3392-3416`); `quorus-workflow-examples` and `docker/agents` (`:505`, `:539`, `:4563`). Keep the enterprise capability requirements (`:76-370`) and the leader-election walkthrough (`:1389-1566`) only if the Architecture Spec does not already hold them. The spec (`:24`, `:837`) and the plan link here. |✅ 2026-10-03, uncommitted: System Design v4.0, 4,673 to 2,225 lines. Every kept section is badged Current, Partly current or Target and corrected against the code; 23 superseded, duplicated or foreign sections are verbatim in `archive/QUORUS_SYSTEM_DESIGN_ARCHIVED_SECTIONS.md`. The enterprise requirements the spec does not hold stay here, badged Target, rather than moving into the spec. Found `ENG-25` |
| **DR-C6** | Extract `docs-design/reference/QUORUS_RAFT_STORAGE_REFERENCE.md` from the Raft WAL design, with the contents listed in review §10: coordinates, layering, method contract, on-disk layout, every storage and snapshot key, recovery order, InstallSnapshot, operator rules, test map, and the unproven power-loss case. Archive the remainder. | ✅ Scope narrowed on 2026-09-26: under ADR-0011 the in-repository engine and sidecar are replaced by QRaft (`CE-10`), so the reference should cover the current design briefly and link to QRaft's storage documentation rather than duplicate it. 2026-10-02 review: about 10% of the document is current; its "✅ Complete" tables (`:1919-1932`, `:2241-2254`) name `FileRaftWAL` and a Quorus `AppendPlan`, which do not exist, while its own roadmap (`:2262-2366`) is unchecked; `:343-365` describes a soft limit, an exception and a metric that do not exist; `:2024-2078` describes Temurin images and a fat jar. The Architecture Spec links to Appendix F.5 (`:217`); repoint it when extracting. ✅ 2026-10-03, uncommitted: `reference/QUORUS_RAFT_STORAGE_REFERENCE.md` v1.0 holds the current, code-checked content; the WAL design is moved to `archive/` with a banner; the spec and the `RaftStorage` Javadoc link to the reference. The extraction found possible engine defects, recorded as `ENG-21` and `ENG-22` |
| **DR-C7** | Trim the Simulators design. Rewrite §1 against current code. Relabel §2–7 as standalone test doubles. Restore the links to `RaftChaosTest`, `RaftFailureTest` and `InfrastructureSmokeTest`. Delete Appendix C. Document `MockRaftTransport`, update the `RaftTransport` listing, mark the DSL and full-stack examples as proposals, and tick the delivered Appendix D items. 2026-10-02 review adds: `QuorusStateMachine` is `QuorusStateStore` (`:95`, `:185`, `:1021`); `RaftNode` is built with `RaftNode.builder()`, not the constructor shown (`:648-650`, `:669`, `:944`, `:1130`); `transferReactive` and the Vert.x `Future` signatures (`:1214-1223`, `:1762-1776`, `:1882-1893`) are now blocking; `HttpRaftTransport` (`:168`) does not exist; the banner should say the six core simulators are standalone test doubles that implement no production interface. | ✅ 2026-10-03, uncommitted: Simulators design v2.1 (§1 rewritten from code, §2–7 relabelled as standalone test doubles); the "VALIDATED" appendix and the invented excerpts are in `archive/QUORUS_IN_MEMORY_SIMULATORS_ARCHIVED_SECTIONS.md`. Defects found in the doubles are `ENG-23` |
| **DR-C8** | Rewrite `QUORUS_NEGATIVE_TESTING_STRATEGY.md` around `@ExpectsError` / `ExpectsErrorExtension` (negative tests have run by default since `8864c2f`). Align `QUORUS_LOG_STYLE.md` with the code: ASCII markers, the TRACE levels, the real `logback-test.xml`, no `-Dtest.loglevel`, and no personal hostname, username or IP. Update `QUORUS_PROTOCOL_SERVERS_TESTING.md` (images, environment, the Testcontainers tests, `*IT` never runs) and `QUORUS_RAFT_CLUSTER_TESTING.md` (no `m2cache` context after DR-C11, `QUORUS_RAFT_*`, 5000/1000, raftlog 1.2.0, `quorus-loadbalancer`, `raft` read from the top level of `/health` rather than `checks.raft.state`, the JUnit Docker suites, the header). Add `ContainerRecreationDurabilityTest` to `DOCKER_TEST_PERFORMANCE.md` and record CPU and RAM. Have LOG_STYLE and NEGATIVE_TESTING link to the Testing README instead of carrying their own logback samples. 2026-10-02 review adds: `QUORUS_RAFT_CLUSTER_TESTING.md` still requires `M2_REPO` and describes an image that compiles Java (`:34-65`, `:393`, `:614-629`), and every leader-detection snippet reads `checks.raft.state` (`:101`, `:286-298`); `prove-metadata-persistence.ps1` targets five controllers and reads the same missing field (`ENG-19`). LOG_STYLE claims to cover all modules, but only the eight simulator test classes use its extension. `DOCKER_TEST_PERFORMANCE.md` P1 (skip the image build when it exists) and P2 (entrypoint wait loop) are reversed or moot: the image is rebuilt once per test JVM from the host-built jar, and the controller image runs no entrypoint script; its "How to run" needs `docker/build-runtime` first. `FTPS_INTEGRATION_TEST_INVESTIGATION.md` describes the superseded pure-ftpd fixture (DR-C2). | 🟨 The testing documents also move from the Vert.x test standard to the `RT-02` standard as each module migrates. 2026-10-03, uncommitted: LOG_STYLE, NEGATIVE_TESTING_STRATEGY, PROTOCOL_SERVERS_TESTING and RAFT_CLUSTER_TESTING rewritten against the code and linked to the Testing README; DOCKER_TEST_PERFORMANCE corrected for P1 and P2 with `ContainerRecreationDurabilityTest` added; the FTPS investigation archived. Remaining: record host CPU and RAM with the next measured Docker run |
| **DR-C9** | Scrub the PeeGeeQ references from `QuorusConfiguration.java:30`, `AppConfigNodeIdentityTest.java:82`, `VertxPerformanceBenchmark.java:45`, `scripts/add-license-headers.sh` and `scripts/setup-git-hooks.sh` (the hook checks `peegeeq-*` paths, so it does nothing in Quorus). Remove `vertx-pg-client` and `ConnectionPoolService`, and the unused `vertx-grpc-*` dependencies, or document why they stay (§6 #15, #22). | 🟨 `ConnectionPoolService` and `vertx-pg-client` removed by `RT-03a` on 2026-09-26. `VertxPerformanceBenchmark` deleted by `RT-03f` on 2026-09-27; the performance validation results document still cites it. Remaining: the other PeeGeeQ references in code and scripts, and the unused `vertx-grpc-*` dependencies. 2026-10-03, uncommitted: no PeeGeeQ reference remains outside the archive (comment, test name and license-script wording fixed); `scripts/setup-git-hooks.sh` deleted, because it was PeeGeeQ's and, if installed, its secret-word check would block almost any Quorus commit; the stale tracked `module-info.java.bak` deleted. Remaining: remove `vertx-grpc-server` and `vertx-grpc-client` from `quorus-controller/pom.xml`. Nothing imports them, but the change was not made in this pass and needs owner approval. ✅ 2026-10-03 (approved by the owner): both removed; `mvn clean verify` 2,519 tests pass and the `-Pbenchmarks` module compiles |
| **DR-C10** | Clean the working copy: delete the local `temp/` worktrees, `.history/` and `hs_err_pid*.log`, and untrack the five `temp/*.txt` files still in git. Normalise line endings with a `* text=auto` rule, committed on its own. Merge NOTICE and OPEN_SOURCE_USAGE into one generated inventory (see DR-F01). | ✅ Still present on 2026-09-27: three full source-tree worktrees under `temp/` (they clutter every repository-wide search), the five tracked `temp/*.txt` files, `.history/` and two `hs_err_pid*.log` files. Absorbs DR-F17. 2026-10-03 (`DR-Q11`): the five `temp/*.txt` files untracked; worktrees `temp/r6-final-worktree` and `-2` (clean, at `0fefecb` and `b604505`) removed; `.history/` (2,246 files) and both `hs_err` logs deleted; `* text=auto` added (the index already held LF only, so no file changes); `THIRD-PARTY.txt` is now generated by `scripts/generate-third-party-inventory.ps1` (with a `-Check` mode), OPEN_SOURCE_USAGE points to it, and NOTICE keeps the required attributions. `temp/remediation-20260905/verify-worktree` (at `28f0530`) held 30 uncommitted changes; by owner decision its diff (checked to reverse-apply cleanly) and its 8 untracked files were saved to `temp/remediation-20260905/verify-worktree-uncommitted-2026-10-03/`, git-ignored and kept on disk, and the worktree was removed. No worktree remains |

### H.4 Phase D — Keep it accurate

| ID | Task | State |
|---|---|---|
| **DR-D1** | **Generate, don't copy.** Generate `docs/QUORUS_CONFIGURATION_REFERENCE.md` from the properties files and the `AppConfig` / `AgentConfig` key constants, seeded from Configuration Handover §2.1 and §8. | ⬜ The endpoint half was resolved on 2026-09-27 by DR-Q7 without generation: the OpenAPI contract is the reference, the REST spec's Current rows are checked against it by test, and `/api/v1/info` links to it |
| **DR-D2** | **CI documentation checks:** a ban on personal Windows user-profile paths; `docker compose config` on every `docker/compose/*.yml`; and a smoke job that starts the single-controller topology. The relative-link checker and header linter this task first asked for already exist in `scripts/verify-phase0-docs.ps1` and run in CI. They fail, and making them pass is part of `ENG-07`. | ⬜ Re-scoped 2026-09-27; follows `ENG-07` (deferred, `SEQ-01`). 2026-10-03: the existing checks now pass locally (DR-Q10), and the link check covers every live document (DR-A8) |
| **DR-D3** | **One status vocabulary.** Implemented / Partial / Planned for capabilities; Current / Required / Planned for API items. Remove the seven ad-hoc values in Arch Spec §13. | ⬜ |
| **DR-D4** | **ADR hygiene** (ADR-0010 ⏸ DR-Q1; ADR-0009 is no longer blocked, because DR-Q2 was decided on 2026-09-25). Add ADR-0006 (raftlog-core WAL and snapshot sidecar), ADR-0007 (layered configuration, no system properties), ADR-0008 (schema-3 coordinated upgrade), ADR-0009 (trust-state scope), ADR-0010 (YAML semantics), and consider one for Raft over grpc-java rather than Vert.x gRPC. Add an index, Supersedes / Superseded-by fields and an Alternatives section. Fix ADR-0002's fencing statement, which is now out of date. 2026-10-02 review adds: ADR-0003 (`:24`) still lists expiry monitoring as required, but expiry observation and warning are implemented (`CertificateTrustState`), so only propagation, enrollment and automated rotation remain; ADR-0012's context (`:14`) is frozen at Java 25 and the pre-migration file counts, and should say what is delivered (`RT-01` to `RT-05`) and what remains (`RT-06` to `RT-09`). | 🟨 ADR-0011 and ADR-0012 added 2026-09-26. ADR-0012 holds decisions `RT-Q1` to `RT-Q5`; v1.2 (2026-09-27) corrects `RT-Q4` and adds a revision history. ADR-0006 should record only the current raftlog-and-sidecar design and name ADR-0011 as its planned successor. ADR-0009 and ADR-0010 remain. 2026-10-03, uncommitted: ADR-0002 v1.1 and ADR-0003 v1.2 carry dated status updates, and ADR-0012 v1.4 a delivery status. Remaining: ADR-0006 to ADR-0010, the index, Supersedes fields and Alternatives sections |
| **DR-D5** | **Definition of done:** any change to a public contract (endpoint, key, environment variable, Compose file or status) updates its canonical document in the same commit, and plans and registers cite a SHA only after the commit exists. Add this to plan §6 and to `.github/copilot-instructions.md`. |✅ 2026-10-03, uncommitted: plan §6 Definition of Done and Copilot instructions "Contract Changes Update Their Document" |

### H.5 Document fixes outside Phases A–D

| ID | Document | Fix | State |
|---|---|---|---|
| **DR-F01** | `NOTICE`, `OPEN_SOURCE_USAGE.md` | Until DR-C10 merges them: list only shipped runtime components in NOTICE, and add Netty, `jackson-dataformat-yaml`, `javax.annotation-api` (CDDL) and Apache HttpClient 5 with HttpCore 5 (shipped since `RT-03b`; Apache 2.0, which carries its own NOTICE). Do not add `vertx-pg-client`, which `RT-03a` removed. State that the container images ship Amazon Corretto (GPLv2 with the Classpath Exception) as their base. In OPEN_SOURCE_USAGE, correct RaftLog Core to 1.2.0, remove RocksDB JNI, add `javax.annotation-api` and Apache HttpClient 5, and fix the `LICENSE-HEADER.txt` reference. Confirm that the "licenses directory" exists. | ✅ 2026-10-03, uncommitted: NOTICE lists only shipped runtime components, adds HttpClient 5, Netty, `jackson-dataformat-yaml`, `javax.annotation-api` and the Corretto base image, and no longer claims a licenses directory; OPEN_SOURCE_USAGE corrected the same way, with Commons Net and jCIFS-ng moved to test scope. The merge into one generated inventory stays with DR-C10 |
| **DR-F02** | `docs/QUORUS_USER_GUIDE.md` | Remove progress, events and attempts from the gaps list. `QUORUS_AGENT_TENANT_ID` takes priority over the legacy `AGENT_TENANT_ID`. State that the agent defaults to the production profile with TLS. Add an NFS section. (DR-A7 covers the authentication statement.) 2026-10-02 review adds: `:123` says custom request options are carried through transfer definitions, but the workflow path drops them (DR-B4); `quorus-agent.properties` is read only from the jar's classpath, so "set it in `quorus-agent.properties`" is not an operator setting. The same tenant-variable error is in the Cluster Startup Guide (`:112-122`). | ✅ 2026-10-03, uncommitted: User Guide v2.6, including an NFS section |
| **DR-F03** | `docs/QUORUS_ARCHITECTURE_QUICKSTART.md` | Link to the OpenAPI contract instead of listing endpoints (its related-documents entry was repointed on 2026-09-27). State the packaged `127.0.0.1` and production-profile defaults. Use `maven.compiler.release`. 2026-10-02 review adds: the HTTP surface list (`:118-131`) omits about 20 routes and calls create, get and delete "transfer CRUD"; `:52` says the reported version is `2.0-ext`, but `/api/v1/info` and `/health` report the hard-coded `1.0.0-alpha` (DR-Q5); `:143` contrasts HTTP with "the blocking adapters", but every adapter is now blocking; the replicated-state list (`:92-98`) omits attempts, fences, the job queue, the event ledger and the service-connection registry; Recommended Reading (`:192`) points to the archived alpha plan, not the current plan and register. The §7 enforcement path is DR-A9. | ✅ 2026-10-03, uncommitted: Quickstart v2.7 |
| **DR-F04** | `docs/QUORUS_INTEGRATION_EXAMPLES_README.md` | Add an `mvn install` step (with `-pl` and no `-am`, the reactor SNAPSHOTs must already be installed), mention the default `mainClass` (`SftpFtpRealImplementationDemo`), and add `CrossModuleIntegrationTest` (renamed from `IntegrationTestSuite` by `RT-04`). | ✅ 2026-10-03, uncommitted: Integration Examples README v2.3, including the validation CLI |
| **DR-F05** | `docs/QUORUS_SERVICE_CONNECTION_OPERATIONS_RUNBOOK.md` | Add the R4 and R5 behaviour (DNS 503/504/409, FTPS 21 vs 990, partial updates, event paging) and the elevation requirement. Correct the "must set" statement for agent pool and roots, which are not enforced. Coordinate with DR-B5. | ✅ 2026-10-03, uncommitted: runbook v1.1 §3.1–§3.3; the service-connection parts of Security Guide §13 and all of §14 moved there |
| **DR-F06** | `docs/QUORUS_CERTIFICATE_INCIDENT_RUNBOOK.md` | Add an example request body and elevation header, and explain how to start a new audit chain by repointing `quorus.security.audit.evidence-path`. |🟨 2026-10-03, uncommitted: runbook §4.5 gives the request body and elevation header. Remaining: how to start a new audit chain by repointing `quorus.security.audit.evidence-path` |
| **DR-F09** | `reference/QUORUS_REPRODUCIBLE_BUILD.md` | Add `project.build.outputTimestamp` to the poms, or say the build is repeatable rather than byte-reproducible. Update the locked baseline from Java 25 to Java 27. 2026-10-02 review adds: CI uses `actions/setup-java` with Corretto 27, not a `maven:…-temurin-25` image, and no Maven version is pinned (no wrapper or enforcer rule), so "Maven 3.9.11 in CI" is unsupported; CI's "clean reproducible build" job builds twice without comparing artifacts. | ✅ 2026-10-03, uncommitted: documented as repeatable, not byte-reproducible; baseline Java 27 and Corretto CI |
| **DR-F12** | `testing/QUORUS_TESTING_README.md` | Remove `quorus-api` from the quick-build `-pl` list and the consolidated-log module list. Remove `-Dgroups='!flaky'`. Say that `*IT` classes do not run in a default build (see DR-Q4). Describe the Testcontainers-based upload tests. 2026-10-02 review adds: `:365` says all integration tests use `VertxExtension`, which only the controller still does; `:311` and `:318` describe `static @Container` fields and a compose stack started before the suite, but the tests use the lazy `SharedTestContainers` singleton and `ProtocolServersLifecycleIT` manages its own stack; add `quorus-benchmarks` to the module list. | ✅ 2026-10-03, uncommitted: Testing README rewritten as the testing hub |
| **DR-F16** | Test classification | Record in the Testing README that six Testcontainers tests run in default builds without a `docker` tag, and add `ContainerRecreationDurabilityTest` and `docker-compose-3node-durable.yml` to the testing documents. | ✅ 2026-10-03, uncommitted: the six default-lane Testcontainers classes, `ContainerRecreationDurabilityTest` and `docker-compose-3node-durable.yml` are in the Testing README |
| **DR-F18** | `performance/QUORUS_PERFORMANCE_BENCHMARKS.md`, `performance/QUORUS_PERFORMANCE_VALIDATION_RESULTS.md` | 2026-10-02 review. B-08's workload says "60 s steady state", but the harness default and the recorded baselines are 30 s after a 5 s warm-up (`ControllerApiBenchmark.java:77`). The component-benchmark text promises an HDR histogram and heap, RSS and thread sampling; B-09 keeps samples in a sorted `long[]` and samples none of them. The results log still calls the benchmark module, the B-08 harness and the `ENG-16` fix "uncommitted"; cite `23bcb6f`, `d3ceb67` and `7b7eb3d`. | ✅ 2026-10-03, uncommitted: benchmark specification v2.3, results log v2.4 |
| **DR-F19** | `.github/copilot-instructions.md` | 2026-10-02 review. `:47` says every exception extends `QuorusException`; `WorkflowParseException`, `ConnectionPolicyException`, `TenantServiceException`, `ResourceManagementException` and `QuorusApiException` do not. `:119` and `:142` forbid sleeps and Awaitility outside the controller with no note that existing core and tenant tests still use both. `:368` names the plan as the roadmap but never mentions the Outstanding Work Register, the single task list, and gives no path for the archived alpha plan. `:214` says status reports carry `tenantId`; they do not. `:53` says each module has `<module>.properties` (only the controller and agent do; core's is `quorus.properties`). `:264` names the metric `quorus.transfer.bytes`; it is `quorus.transfer.bytes.total`. `:95` says `.dockerignore` admits only the jars; it also admits `quorus-agent/target/lib/`. The module table is DR-F23. | ✅ 2026-10-03, uncommitted |
| **DR-F20** | `design/QUORUS_QRAFT_INTEGRATION_ASSESSMENT.md` | 2026-10-02 review. The QRaft half is a snapshot of `1bab473`; QRaft's `main` has since added membership changes (`MembershipService`, `RaftConfigurationCodec`), so "membership: static in both engines" (`:28`, `:43`, `:90`) is out of date and `CE-Q3` should be re-read against it. §6 lists the B-08 and B-09 baselines as preparation still to do; both were recorded on 2026-09-28 (`ENG-15a`, `ENG-15b`). | ✅ 2026-10-03, uncommitted: assessment v1.1 adds a dated update (QRaft at `caf101b`: membership implemented, raftlog 1.4.1, unshaded controller; transport still plaintext) |
| **DR-F21** | `dev/QUORUS_CONCURRENCY_CONVENTIONS.md` | 2026-10-02 review. Accurate against `TaskScope`. Add that existing core and tenant tests predate §6 and still use sleeps and Awaitility, and that no production code binds a `ScopedValue` yet. | ✅ 2026-10-03, uncommitted: conventions v1.4 |
| **DR-F22** | `reference/QUORUS_COMMIT_HISTORY_REWRITE_MAP.md` | 2026-10-02 review. All eight pairs have identical trees and every replacement is an ancestor of HEAD, but `43cdd20` (listed as historical) and the anchor `6942fc5` are themselves reachable from `master`, while `b604505` and `0fefecb` are on no ref. Correct the "historical ID to reachable replacement" framing. Add the standard header (its header is one of the 11 `ENG-07` failures). | ✅ 2026-10-03, uncommitted: rewrite map v1.1 with the standard header. All eight pairs re-verified; five originals are reachable again through merge `e302e41` |
| **DR-F23** | Module tables | 2026-10-02 review. The root README (`:58`), Architecture Spec (`:30`, `:135-142`), Quickstart (`:20-26`, which also omits `quorus-integration-examples`) and Copilot instructions (`:21-29`) omit `quorus-benchmarks`, a profile-only module (`-Pbenchmarks`) whose `RaftCommitBenchmark` imports Vert.x. So "only `quorus-controller` uses Vert.x" needs "among the default-build modules". | ✅ 2026-10-03, uncommitted |

**Out of scope:** claims about the separate raftlog repository (library SHAs and the "41
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
`CE-08`; `DR-X19` is `ENG-04`, closed as obsolete after `RT-04`; `DR-X24` is `ENG-03`, closed as obsolete after `RT-05`). Rows marked *reported* were confirmed by the
review on 2026-09-25 but have not been re-checked since; re-verify each against the current tree
before implementation.

Phase assignment is still outstanding for most rows. Plan v1.28 added them without phases, and
no revision since has assigned any. The exceptions are:

- `SEC-07` is a Phase 4 hardening follow-up (`SEQ-02`).
- `ENG-07` belongs to Phase 0's CI controls, and its repair is deferred (`SEQ-01`).
- `ENG-01` goes with `P2-01`.
- `SEC-04` closes through `CE-08`.
- `ENG-03` and `ENG-04` were closed on 2026-09-28 as obsolete after `RT-05` and `RT-04`.

| ID | Item | Task | Evidence state | Level |
|---|---|---|---|---|
| **CFG-01** | Make repository Compose security posture explicit, remove unsupported environment settings and duplicate topology, fix image health probing, separate logging-stack names/ports, and provide a generated-certificate mTLS example | `DR-A5` | ✅ **Closed 2026-09-25** — all 14 Compose models validate; the TLS example is healthy, accepts its generated gateway identity, and rejects a client without a certificate. Repository-local validation, not production accreditation. | ✅ |
| **CFG-02** | `AgentConfig.getForeignAssignmentMismatchThreshold()` defaults to 3 while the packaged value is 1, so builder- or override-based configurations can diverge from production | `DR-X16` (review §6 #16) | Re-verified 2026-09-28 (`AgentConfig.java:178-179`, `quorus-agent.properties:101`) | 🟢 |
| **CFG-03** | Invalid numeric configuration values fall back to the accessor default with a WARN instead of failing validation | review §6 #16 | Verified 2026-09-26 (`LayeredProperties.java:54-67`); the review's "silently" is corrected — a warning is logged | 🟢 |
| **CFG-04** | A blank environment value cannot clear a packaged value, because blank overrides are skipped | review §6 #16 | Verified 2026-09-26 (`LayeredProperties.java:85`) | 🟢 |
| **CFG-05** | `QuorusConfiguration` reads `System.getenv()` directly, so its environment layer cannot be injected in tests the way `AppConfig`'s can | review §6 #16 | Verified 2026-09-26 (`QuorusConfiguration.java:211`) | 🟢 |
| **SEC-01** | Revocation serials with leading zeros never matched | `DR-A4` | Code fixed: `CertificateTrustState.normalize` strips leading zeros (verified 2026-09-26); the task list records 17/17 focused `SecurityBoundaryIntegrationTest` passes and updated operating guidance. Closure awaits ADR-0009. | 🟠 |
| **SEC-02** | Runtime revocation is node-local and volatile | `DR-Q2`, `DR-A4`, `DR-D4` | Decision recorded 2026-09-25: keep node-local; operators update every controller and persist the set in configuration before restart. ADR-0009 outstanding. | 🟠 |
| **SEC-03** | Direct-URI SFTP disables host-key checking without logging (residual of `QR-03`) | `DR-X06` | Re-verified 2026-09-28 (`SftpTransferProtocol.java:382`) | 🟠 |
| **SEC-04** | Raft peer certificates are not bound to the `QUORUS_CLUSTER_NODES` identity; any cluster-CA certificate can make Raft RPCs | `DR-X07` | Reported (`RaftPeerAuthorizationInterceptor`) | 🟠 |
| **SEC-05** | `roleAllows` returns on the first matching role, so multi-role identities can be denied scopes another role grants | `DR-X09` | Verified still present 2026-09-26 (`AuthorizationPolicyEngine.java:78-107`) | 🟠 |
| **SEC-06** | Direct mTLS identities cannot hold elevation; only gateway-asserted identities can perform elevated operations | `DR-Q3`, `DR-B5` | Reported; decision pending | 🟠 |
| **SEC-07** | Governed TLS trusts only the JVM default trust store (`TlsPeerPolicy` uses `TrustManagerFactory.init(null)`), so endpoints issued by a private corporate CA work only if that CA is added to the JVM `cacerts`; there is no Quorus configuration for trust anchors | found by `RT-03b` | Verified 2026-09-26 (`TlsPeerPolicy.createTrustManager`). The rewritten HTTP adapter accepts an injected base trust for tests; production still uses the JVM default. Assigned 2026-09-27 to Phase 4 as a hardening follow-up with acceptance criteria (plan §11, `SEQ-02`); must close before any production service connection relies on a private CA | 🟠 |
| **ENG-01** | `JobAssignmentService`, which owns the assignment timeout monitor, is constructed only by its test. 2026-10-02 review: neither it nor `AgentSelectionService` (the tenant, pool and zone gate) is constructed anywhere in production code, so the controller runs no scheduler at all: submitting a transfer commits only the job, and an assignment exists only if a caller uses `POST /api/v1/assignments`, whose handler checks no pool or zone. The documents present the scheduler as live (DR-A9) | `DR-X11` | Re-verified 2026-10-03 (no `new JobAssignmentService` or `new AgentSelectionService` under `quorus-*/src/main`; `QuorusControllerVerticle` starts only gRPC, Raft and HTTP). Settle with `P2-01` | 🟡 |
| **ENG-02** | `*IT` and `*Benchmark` classes never run: no Failsafe plugin and no Surefire includes | `DR-Q4`, `DR-X18` | Reported. ✅ **Closed 2026-10-03** by delivering `DR-Q4`: the one `*IT` class is renamed and tagged, and `TestNamingConventionTest` fails the build if a class that declares a test has a name Surefire does not select. The two `*Benchmark` classes are harnesses under `quorus-benchmarks/src/main`, not tests; their tests are `*BenchmarkTest` | ✅ |
| **ENG-03** | `QuorusAgent.java:372` called `.join()`; whether it could run on an event loop was untraced | `DR-X24` | ✅ **Closed 2026-09-28**: obsolete since `RT-05`. The agent has no event loop; its only joins are on threads it owns (`QuorusAgent` shutdown and progress reporter) | ✅ |
| **ENG-04** | `SimpleWorkflowEngine` public constructor called `Vertx.vertx()` | `DR-X19` | ✅ **Closed 2026-09-28**: obsolete since `RT-04`. The engine has no Vert.x; its only constructor takes a `TransferEngine` | ✅ |
| **ENG-05** | `workflow-schema.json` is never loaded although `json-schema-validator` is a dependency | `DR-X21` | Reported | 🟢 |
| **ENG-06** | Small code-comment corrections: the `mvn test -Dgroups=docker,slow` pom comment, and Javadoc mentioning the removed `memory` storage type and "blocking mode". 2026-10-03 additions: the same no-op `-Dgroups=docker` command (it selects nothing without `'-Dtest.excludedGroups='`) in the Javadoc of `ContainerRecreationDurabilityTest`, `ConfigurableRaftClusterTest`, `AdvancedNetworkTest`, `DockerRaftClusterTest`, `NetworkPartitionTest`, `MetadataPersistenceTest` and `RaftChaosTest`; `AppConfig` says the snapshot threshold triggers when "exceeded" but the code uses greater-or-equal; `ProtocolErrorHandlingTestBase` claims log suppression for `test-` IDs that no main code does; `SharedTestContainers` mentions pure-ftpd; `docker-compose-sftp-abort-test.yml` names a missing `SftpAbortIntegrationTest`; `SharedDockerCluster.buildImageIfAbsent()` always builds; `SimulatorTestLoggingExtension` labels a DEBUG line `[TRACE]` | `DR-X17`, `DR-X20` | Reported; 2026-10-03 additions from the testing-document and storage-reference rewrites | 🟢 |
| **ENG-07** | CI has never passed: all 21 GitHub Actions runs from 2026-09-01 to 2026-09-26 failed. (1) Unit and clean-build lanes: `FtpsDefaultPortBoundaryTest` listens on port 21, which a non-root process cannot bind on the Linux runner (it passes on Windows). The build stops in `quorus-core`, so the other modules have never been tested in CI. (2) Documentation lane: `scripts/verify-phase0-docs.ps1` rejects 11 document headers that lack the two trailing spaces its pattern requires. Trailing spaces are easily stripped by editors; decided 2026-10-03 to add them (`DR-Q10`). The fix needs a test-first redesign of the FTPS default-port test | found 2026-09-27 during the register review | Verified 2026-09-27 from the logs of runs `36275459592` (Java 27) and `36192430573` (Java 25). **Repair deferred by `SEQ-01`.** Blocks every phase exit (plan §6.1 step 5), not current slices. 2026-10-03: part (2) is fixed locally, uncommitted (`DR-Q10`): all 28 headed documents pass `verify-phase0-docs.ps1`. Part (1), the FTPS port-21 test, remains | 🟡 |
| **ENG-08** | The current HTTP API was described four times by hand, and the copies had drifted: the API Reference (dated 2026-09-05), the REST spec's Current rows and "current implementation" paragraphs, and the endpoint list in `/api/v1/info` (35 of 52 routes). The OpenAPI contract itself declared no per-operation scopes, a wholly wrong `AgentStatus` enum, and no 504 for DNS authorization | DR-Q7, DR-B3 | ✅ **Closed 2026-09-27** (commit `6a2864c`). The contract is the only current-API reference. `OpenApiReferenceContractTest` checks each operation's scope against `AuthorizationPolicyEngine`, the public operations against the new `PublicEndpoints`, `AgentStatus` against the server's values, the 409/503/504 responses of the two DNS-authorizing operations, and the REST spec's Current rows. `/api/v1/info` links to the contract. The API Reference is deleted | ✅ |
| **ENG-09** | The FTP and SFTP adapters each keep one `activeClient` field shared by every transfer they run, so `TransferProtocol.abort()` closes only the most recently started connection, and one transfer finishing clears another's reference. Before RT-03c the engine called `abort()` on cancel, which aborted the wrong or every transfer of that protocol (for HTTP, every in-flight client). Since RT-03c cancellation interrupts only the named transfer and `abort()` has no production caller | found by `RT-03c` | Verified 2026-09-27 (`FtpTransferProtocol.java:89`, `SftpTransferProtocol` `abort`). ✅ **Closed 2026-09-27** (commit `fcd29fe`): `abort()` is removed from `TransferProtocol` and every adapter, with the shared `activeClient` fields, the HTTP in-flight registry and the dead `forceDisconnect` methods. `TransferProtocolCancellationContractTest` fails if an adapter-wide abort or per-transfer connection state returns | ✅ |
| **ENG-10** | The FTP, SFTP, SMB and NFS adapters never reported progress to the transfer's job and never checked the context: each kept progress in a private `ProgressTracker` that nothing read. So the engine saw no progress for them, pause had no effect, and only interruption stopped them. Separately, the agent reported `IN_PROGRESS` once with 0 bytes and then only the final result, for every protocol, so the controller's progress, freshness and stall views were never fed by a real agent. No test covered either | found 2026-09-27 during `RT-03c` follow-up | ✅ **Closed 2026-09-27** (commit `fcd29fe`). A `ProgressTracker` built from the context records progress on the job and exposes `stopRequested()`; the four adapters use it, and SFTP now fails a transfer JSch stopped early. The agent sends a progress report whenever the byte count has grown, at `quorus.agent.jobs.progress-report-interval-ms` (15 s), one report at a time, and resends an unresolved report exactly before the final one. Tests: `AdapterProgressAndStopTest` (NFS locally, FTP and SFTP on Docker) and `TransferProgressReportingIntegrationTest`. SMB has no server fixture; it shares the tested tracker. A failed non-HTTP download can still leave a partial destination file, which is `P2-07` | ✅ |
| **SEC-08** | `FtpTransferProtocol` had a public `setSslSocketFactory` that replaced TLS verification on the shared adapter; only a test used it, to trust any certificate | found 2026-09-27 | ✅ **Closed 2026-09-27** (commit `fcd29fe`): the setter is removed; the test factory is passed to a package-private constructor and fixed at construction | ✅ |
| **ENG-11** | `TransferTelemetryMetrics` is a JVM-wide singleton, so an engine's health check reports the protocol statistics of every engine in the process. Harmless in production (one engine per agent), but engines sharing a JVM, as in tests, see each other's protocols; one exact-count test failed this way on 2026-09-27 and now asserts the engine's own protocols by name | found 2026-09-27 | Recorded. The earlier trigger (metrics moving off Vert.x under `RT-07`) has passed: the metrics are in `quorus-core`, which has no Vert.x. Needs an owner and a decision on per-engine health | 🟢 |
| **ENG-12** | `SimpleWorkflowEngine.cancel` recorded a cancellation metric and returned `true` but stopped nothing: the workflow ran on. `pause` and `resume` always returned `false`, and the workflow `execution.parallelism` setting (documented as the maximum number of groups running at once) was ignored | found by `RT-04` | ✅ **Closed 2026-09-27 in `RT-04`** (commit `7fb4587`). `cancel` interrupts the run: its transfers stop, no further group starts, and the execution ends `CANCELLED`; the interrupt does not leak to the caller. Independent groups run up to `parallelism` at a time, in dependency order; the default of one keeps groups sequential. `pause` and `resume` are removed from `WorkflowEngine`: nothing called them and they could never work. Tests: `SimpleWorkflowEngineTest` cancel, parallelism and dependency cases | ✅ |
| **ENG-13** | The workflow engine ignored the definition's `execution.dryRun` and `execution.virtualRun`, so a workflow declared `dryRun: true` ran real transfers when executed; it also ignored the group `retryCount` and accepted any `strategy` value, while the YAML guide described all four as working | found by the review of 2026-09-28 | ✅ **Closed 2026-09-28** (commit `8946faa`): the declared flags now make every run of the workflow a dry or virtual run (a dry run wins); each failed transfer of a group runs again up to `retryCount` times; `strategy` must be `sequential` or `parallel` and is documented as not changing scheduling (YAML Syntax Guide v2.3). Tests first: five `SimpleWorkflowEngineTest` cases and one `YamlWorkflowDefinitionParserTest` case failed before the change | ✅ |
| **ENG-14** | Group and transfer `condition` expressions are parsed and variable-resolved but never evaluated, so a transfer guarded by, for example, `success(download-base-files)` runs regardless. The YAML Syntax Guide states this | found by the review of 2026-09-28 | Verified 2026-09-28 (`VariableResolver.java:93-114` is the only reader). Needs a decision: evaluate conditions, or reject workflows that declare one | 🟠 |
| **ENG-15** | Quorus has no benchmark or performance test: the only benchmark class (`VertxPerformanceBenchmark`, which timed an empty Vert.x task) was deleted in `RT-03f`. A `quorus-benchmarks` module is needed, outside the default build, implementing the catalogue in [QUORUS_PERFORMANCE_BENCHMARKS.md](../performance/QUORUS_PERFORMANCE_BENCHMARKS.md) (B-01 micro to B-11 soak) under the Architecture Specification §13 publication rules | decided 2026-09-28 (keep and adapt the benchmark documents; build a complete benchmark module) | Specified 2026-09-28. Sequencing: the controller baselines B-08 (HTTP API) and B-09 (Raft) must be measured on the Vert.x controller before `RT-06` and `CE-07`, whose acceptance compares against them (`RT-Q2`); B-02, B-10 and B-11 serve Phase 12. **Slice ENG-15a done 2026-09-28** (commits `c96c46a` and `23bcb6f`): the `quorus-benchmarks` module (root profile `benchmarks`, outside the default build) with B-09 commit latency; baseline recorded (about 270 commits/s at every concurrency: the engine commits one command at a time). **Slice ENG-15b done 2026-09-28** (commit `d3ceb67`): B-08 for submit, heartbeat, poll and read, with real controller processes under the production TLS and request-security configuration; baseline recorded (about 100 requests/s for reads and 70 for writes at every concurrency, found `ENG-16`). Next: B-09 leader failover, B-08 status reports | 🟨 |
| **ENG-16** | The security audit write path caps the controller API at about 100 requests per second: each request records at least two audit events, each written to two hash-chained logs, and `HashChainedAuditLog.append` is `synchronized` and calls `FileChannel.force(true)` per write, on the Vert.x event loop. So every request performs at least four disk syncs on the thread serving all requests | found by benchmark B-08 on 2026-09-28 (results §2.2) | Verified 2026-09-28 (`HashChainedAuditLog.java:65-91`, `HttpApiServer.java:295-298`, `AuthenticationHandler`). ✅ **Closed 2026-09-28** (commit `7b7eb3d`; decided to land before `RT-06b`, `SEQ-04`): `HashChainedAuditLog` group-commits (records are written in chain order at once; one sync thread syncs everything written before each sync began; a failed write or sync fails every waiting record and every later append). `AuditSink.appendAsync` added; the authentication, authorization and revocation-update handlers continue only after their records are durable, without blocking the event loop, pausing the request so a body that arrives meanwhile is kept; the completion audit is written after the response, as before. B-08 (results §2.3): reads from about 100 to about 8,000 requests/s at 500 clients; writes from about 70 to about 170, now bounded by the serial Raft commit (`CE-07`) | ✅ |
| **SEC-09** | Runtime revocation cannot be applied to a follower. `PUT /api/v1/security/trust/revocations` changes node-local, volatile trust state (`DR-Q2`), and the operating procedure sends it to every controller, but `LeaderGuardHandler` rejects every `POST`/`PUT`/`DELETE`/`PATCH` under `/api/` on a non-leader with 503. So followers keep accepting a revoked certificate (for example for agent polling), and the revocation is lost on leader change | 2026-10-02 review | Verified 2026-10-03 (`LeaderGuardHandler.java:56-98` exempts only non-`/api/` paths; `HttpApiServer.java:156` installs it before the route at `:182`). Fix: exempt the node-local revocation route from the leader guard, test-first through a real follower. Documentation: DR-A10. ✅ **Fixed 2026-10-03, uncommitted:** `LeaderGuardHandler` passes two API writes on every node: the revocation update and the authorization check, which changes nothing and was also refused by followers. Red: `SecurityBoundaryIntegrationTest.revocationUpdateAppliesOnAFollower` and `authorizationCheckIsAnsweredByAFollower`, on an mTLS server on a real follower of a three-node cluster, got 503. Green: 35 focused tests. Mutation: removing the revocation entry fails its test again | ✅ |
| **SEC-10** | Raft revocation is enforced on inbound RPCs only. The server interceptor checks the caller's certificate against the trust state, but the outbound channel never consults it, so a leader keeps sending `AppendEntries` to a peer whose certificate is revoked and accepts its responses | 2026-10-02 review | Reported (`RaftPeerAuthorizationInterceptor.java:33-41`; `GrpcRaftTransport.java:291-299` has no trust-state reference). Related to `SEC-04`; expected to close through `CE-08` with it | 🟠 |
| **SEC-11** | The revocation update is unsafe to get slightly wrong. An omitted or misspelt `revokedCertificateSerials` defaults to an empty array, so the request clears every runtime and configured revocation and returns 200. The state is replaced before the audit record is durable, so an audit failure returns an error after the change has applied. The audit event records only the bundle versions and counts, so it cannot show which serials were revoked | 2026-10-02 review | Verified 2026-10-03 (`SecurityHandler.java:104-120`). ✅ **Fixed 2026-10-03, uncommitted:** the serial list is required (`400` otherwise); the change is validated and audited first and applied only once its record is durable (`CertificateTrustState.prepare`/`apply`); the audit event lists the sorted, normalized serials. Red: three new `SecurityBoundaryIntegrationTest` cases (missing list got 200; serials attribute null; a failed audit left the revocation applied). Green: 42 focused tests. Mutation: applying before the audit fails the audit-failure test | ✅ |
| **ENG-17** | The agent image cannot start against a controller. Its entrypoint waits up to 60 s for `curl -f "$CONTROLLER_URL/health"`; `CONTROLLER_URL` is the API base (`…/api/v1`), and the controller serves health only at the root, so the probe never succeeds and the container exits. Against a TLS controller the probe also presents no client certificate, which the handshake requires | 2026-10-02 review | Verified 2026-10-03 (`quorus-agent/docker-entrypoint.sh:58`; `HttpApiServer.java:166-168`). The wait is needed: the agent shuts down if registration fails (`QuorusAgent.java:214-224`). Fix: probe the controller's root `/health/live`, presenting the agent's client certificate when TLS is configured. ✅ **Fixed 2026-10-03, uncommitted:** the entrypoint strips `/api/v1` from the controller URL, probes `/health/live` with a 5 s limit, passes the `QUORUS_AGENT_TLS_*` certificate, key and trust bundle for `https`, and reads `QUORUS_AGENT_ID`/`QUORUS_AGENT_CONTROLLER_URL` ahead of the legacy names. Red/green: new `scripts/test-agent-entrypoint.sh` (stub `curl` and `java`) showed the old probe `…/api/v1/health`; four cases pass after. Not yet run in a container. Superseded the same day by `ENG-27`: the entrypoint no longer probes the controller, because the agent retries registration itself | ✅ |
| **ENG-18** | The `controller-first` load balancer answers health checks itself. nginx's `location /health { return 200 … }` is a prefix match, so `/health/live` and `/health/ready` through port 8080 return 200 even with every controller down | 2026-10-02 review | Verified 2026-10-03 (`docker/compose/nginx/nginx.conf:48`). Fix: an exact-match `location = /health` for the balancer's own check, so `/health/*` reaches a controller. Documentation: DR-A11. ✅ **Fixed 2026-10-03, uncommitted:** red/green against a real `nginx:alpine` container with the controller hosts resolving to nothing: before, `/health/live` and `/health/ready` returned 200; after, 502, while `/health` still returns 200 | ✅ |
| **ENG-19** | The Docker helper scripts do not work. `docker/scripts/start-full-network.ps1` starts an `api` service the compose file does not define; `test-transfers.ps1`, `demo-logging.ps1`, `docker/test-data/send-heartbeat.ps1` and `check-agents.ps1` target port 8080, which the full-network topology does not map; `docker/test-data/test-registration.json` has no `tenantId`, so registration returns 400; the logging demos query Loki on 3100 (the compose file maps 3110); `scripts/prove-metadata-persistence.ps1` targets five controllers and reads `checks.raft.state`, which `/health` does not return | 2026-10-02 review | Verified 2026-10-03 by reading the scripts against `docker-compose-full-network.yml`, `docker-compose-loki.yml` and `HealthHandler`. Fix or delete each; the documents that cite them are DR-C4 and DR-C8. Also: the full network's HTTP server mounted a `docker/compose/test-data/nginx.conf` that does not exist; `scripts/view-raft-logs.ps1` read `checks.raft`; and the full network's agents register with `controller1` only, so they fail and restart until it is the leader. 🟨 **2026-10-03, uncommitted:** `start-full-network.ps1` builds the jars first and no longer starts `api`; `test-transfers.ps1` rewritten (finds the leader, uses the agents' tenant and in-network URLs, assigns each transfer explicitly); `check-agents.ps1`, `send-heartbeat.ps1` and the sample payloads take a base URL and a tenant; the logging demos use 3110 and 8081; the nginx mount is fixed (`docker compose config` passes); `prove-metadata-persistence.ps1` and `view-raft-logs.ps1` read the real fields. Remaining: an end-to-end run of the full network. Its agents now list all three controllers and follow the leader (`ENG-27`) | 🟢 |
| **ENG-20** | `quorus-controller/docker-entrypoint.sh` is never run: the controller image has no `ENTRYPOINT` and runs `java -jar` directly, so the script's mapping of legacy unprefixed variable names has no effect in any shipped image. The Security Guide (`:267`) describes that precedence as live | 2026-10-02 review | Verified 2026-10-03 (`quorus-controller/Dockerfile` ends in `CMD java … -jar app.jar`; only `scripts/test-controller-entrypoint.sh` references the script). Fix: delete the script and its test helper, or wire it in. ✅ **Done 2026-10-03, uncommitted:** both deleted (no compose file used a legacy controller name); the Security Guide says the legacy names have no effect | ✅ |
| **ENG-21** | Raft peer RPCs may be served before recovery completes. The controller starts the gRPC server before `RaftNode.start()`, and the vote and append handlers call `RaftNode` with no recovery guard. A vote request handled while `currentTerm` is still 0 might grant a second vote in a term in which the node had already voted, which would break Raft's election safety | found 2026-10-03 while extracting the Raft storage reference (DR-C6) | Reported (`QuorusControllerVerticle.java:175-182`; `GrpcRaftServer.java:180,218,256`). **Confirmed 2026-10-03** by a failing test: a node that had voted for `candidate-a` in term 3 granted `candidate-b` a term-3 vote received while its metadata was still loading, and then recovered `votedFor=candidate-b`. ✅ **Fixed 2026-10-03** (owner's decision, `SEQ-05` revised): `RaftNode` defers vote, `AppendEntries` and `InstallSnapshot` requests until its first recovery from storage has completed, and fails them if recovery fails; a volatile node has nothing to recover and is not gated. Red: `ConcurrentVoteBoundaryTest.aVoteArrivingDuringRecoveryCannotGrantASecondVoteInARecoveredTerm`. Green: 52 focused Raft tests. Mutation: removing the gate from the vote handler fails the test again. `CE-07` must keep this property | ✅ |
| **ENG-22** | Raft engine gaps found while extracting the storage reference. Followers never take snapshots: `checkAndTakeSnapshot` runs only on the leader, so a follower's WAL shrinks only when it installs one. The log hard limit has no test. `RaftNode.java:1429-1437` skips matching entries after the first conflict, where the archived design's F.5 said to append them; Raft's log-matching property makes the case unreachable with a correct leader, but the difference should be confirmed. Lowering raftlog's maximum payload size on an existing directory would truncate the WAL at the first larger record on replay (now an operator rule in the reference) | found 2026-10-03 (DR-C6) | Reported, not verified. Decide which to fix in the in-repository engine and which to carry to `CE-07`/`CE-09` | 🟠 |
| **ENG-23** | Test-double defects and a licence conflict. `InMemoryTransferEngineSimulator.setMaxConcurrentTransfers` does nothing (fixed at 10) and a paused transfer still completes on time; `InMemoryTransferProtocolSimulator`'s `SLOW_TRANSFER` does not slow transfers; `InMemoryWorkflowEngineSimulator` never uses `DEPENDENCY_FAILURE` or `TIMEOUT` and ignores `dependsOn` order; `InMemoryAgentSimulator.setProgressUpdateIntervalMs` is unused and a partition set before `start()` makes it throw; `InMemoryTransportSimulator.stop()` never shuts down its send pool; `MockRaftTransport` grants votes or reports success at random (90% and 95%) when the target node is missing, instead of failing. `MockRaftTransport` also carries a "confidential and proprietary" header that conflicts with the Apache 2.0 licence | found 2026-10-03 (DR-C7) | Reported. The licence header should be fixed first | 🟢 |
| **ENG-24** | Test-lane defects. The plan's recorded full-suite command `mvn --fail-at-end clean verify '-Dtest.excludedGroups='` now fails, because `clean` deletes the host-built jar the Docker suites package. `AgentTelemetryIntegrationTest` and `InfrastructureWithTelemetryTest` fail rather than skip without Docker. The `negative-tests` and `all-tests` profiles override the Surefire configuration and may drop `testRunTimestamp` (unverified); `all-tests` is now the same as the default. `ProtocolServersLifecycleIT` calls the legacy `docker-compose` binary, says the stack must already be running though it starts it, and its `down -v` removes a manually started stack's volumes | found 2026-10-03 (DR-C8) | Reported. Settle with DR-Q4 and `ENG-07`. 2026-10-03: the `ProtocolServersLifecycleIT` part is fixed with `DR-Q4` (renamed, `docker compose`, corrected description, teardown no longer deletes volumes). The other parts remain | 🟠 |
| **ENG-25** | Agent deregistration does nothing. On shutdown the agent calls `DELETE /api/v1/agents/{agentId}`; the controller registers no such route, and the agent treats the `404` as "already gone", so the agent record stays in replicated state indefinitely. The Copilot instructions and the System Design already say there is no deregistration route | found 2026-10-03 (DR-C5) | Verified 2026-10-03 (`AgentRegistrationService.java:77-88`; no `router.delete("/api/v1/agents…")` in `HttpApiServer`). Decided 2026-10-03 (`ENG-Q2`): add the route. ✅ **Fixed 2026-10-03:** `DELETE /api/v1/agents/{agentId}` (`AgentDeregistrationHandler`), scope `agents:deregister` held by the `AGENT` and `OPERATOR` roles, self-only for an agent identity, tenant-checked, leader-only, Raft-committed, in the completion audit and in OpenAPI and the REST spec. The state machine refused deregistration while any assignment or attempt named the agent, finished or not, so an agent that had ever worked could never leave; it now refuses only for active ones (`409`). An agent that finished work still names is kept with status `DEREGISTERED`, is refused heartbeats (`404`) and new assignments (`AGENT_DEREGISTERED`, `409`) until it registers again; an agent nothing names is removed. The agent reports a `404` as a failure. Red: eight tests (`AgentDeregistrationHttpIntegrationTest`, three `AuthoritativeStateInvariantTest` cases, one each in `SecurityBoundaryIntegrationTest`, `AuthorizationPolicyEngineTest` and `AgentRegistrationServiceTest`). Mutation: removing the active-work refusal fails three tests. The mutation of the self-only check was not run (blocked as a security weakening by the tooling); the `403` it guards is asserted by `SecurityBoundaryIntegrationTest` for an identity the policy engine allows | ✅ |
| **ENG-26** | Deliver decision `DR-Q1`: pass a defined, documented set of workflow transfer `options` through to the transfer request and adapters, fail validation on unknown option keys, and resolve nested variable references recursively with a depth limit. Today options are parsed, resolved and dropped (`TransferGroup.toTransferRequest()`), and a nested reference stays literal | decision `DR-Q1`, 2026-10-03 | To do. Define the option set per protocol first; then revise the YAML Syntax Guide and the example workflows key by key | 🟠 |
| **ENG-28** | Jackson version skew. The root pom imports the `vertx-dependencies` BOM, which manages Jackson 2.18.2, while some modules pin 2.19.4. Resolved per module: controller, workflow and examples run 2.18.2; core and tenant 2.19.4; the agent mixes `jackson-databind` 2.19.4 with `jackson-dataformat-yaml` 2.18.2, which Jackson does not support | found 2026-10-03 by the generated inventory (DR-C10) | Verified 2026-10-03 with `mvn dependency:tree`. Fix: import `jackson-bom` at one version in the root `dependencyManagement`, ahead of the Vert.x BOM, and remove the per-module Jackson versions; needs owner approval (root pom) and a full regression. ✅ **Fixed 2026-10-03** (approved by the owner): `jackson.version` 2.19.4 and a `jackson-bom` import ahead of the Vert.x BOM in the root pom; the per-module Jackson versions removed. Every module now resolves 2.19.4 only (`dependency:tree`), THIRD-PARTY.txt lists one Jackson version, and `mvn clean verify` passes 2,519 tests | ✅ |
| **ENG-27** | Deliver decision `ENG-Q3`: the agent accepts several controller URLs, follows the leader hint on `503 NOT_LEADER` and keeps retrying registration instead of exiting; the controller sends `X-Quorus-Leader` (REST Spec §3.8). Today an agent bound to a follower stops at its first registration | decision `ENG-Q3`, 2026-10-03 | ✅ **Done 2026-10-03**, test-first in seven red/green cycles. **Controller:** `quorus.cluster.api-endpoints` maps node IDs to API base URLs (validated at startup); a follower's refusal carries `Retry-After: 1` and, when the leader's endpoint is configured, `X-Quorus-Leader`; `NO_LEADER` carries `Retry-After` only. **Agent:** `quorus.agent.controller.url` is a comma-separated list (all `https` in production). `ControllerClient` takes paths relative to the API base and sends a write refused with `503 NOT_LEADER` to the named leader only if it is a configured controller, otherwise to the next one; each controller is asked at most once per request; `NO_LEADER` and other 503s are not resent; a transport failure is not resent, but the next request uses the next controller. Registration is retried every `quorus.agent.registration.retry-interval-ms` (5000) while no controller can take it, and the agent stops only on a `4xx`. The agent image's entrypoint no longer waits for a controller: with the agent retrying, the wait only made the container exit after 60 s, and it could not handle a list (this replaces the `ENG-17` probe). The full-network compose file lists all three controllers for each agent and sets the endpoints on each controller. Coverage check afterwards (JaCoCo, changed classes): gaps closed with tests for the no-leader response, a cross-tenant deregistration, an unknown agent and an active attempt at the state machine, malformed and self-naming hints, and the 408/429 classification; the dead `AgentConfiguration.getControllerUrl()` removed. Not covered: the handler branch for an agent removed between its lookup and the commit (`AgentDeregistrationHandler`), and the retry wait ending by the stop signal rather than the interrupt. Not yet run against real containers. Closes the header part of API-12 | ✅ |
| **ENG-29** | The agent image ships test-only libraries. The agent runs as a thin jar with `lib/`, and `copy-dependencies` in `quorus-agent/pom.xml` sets no scope, so `target/lib` (copied whole into the image) includes JUnit, Testcontainers, docker-java and the `quorus-core` tests jar: 11 jars that the agent never loads but that enlarge the image and its vulnerability surface, and that THIRD-PARTY.txt does not list as shipped | found 2026-10-03 while checking the built jars for DR-Q5 | Verified 2026-10-03 (`quorus-agent/target/lib` after `mvn clean verify`). Fix: `includeScope` `runtime` on the `copy-dependencies` execution, with a check that the image's `lib/` holds no test artifact | 🟠 |

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
| **RT-01a** | Quorus | Java 27 compile and test baseline (root pom, `.java-version`), proven by `JavaPlatformBaselineTest` | ✅ 2026-09-26: red, green and a 2,387-test regression on JDK 27. Commit `a9b1ace`, with `RT-01b` |
| **RT-01b** | Quorus | Java 27 controller and agent images and CI toolchain on Amazon Corretto 27 | ✅ 2026-09-26: single-stage images packaging host-built jars on `amazoncorretto:27.0.0-alpine3.24`; no Java or Maven inside Docker. Red, green and a Docker+slow regression of 2,421 tests with 0 failures and 2 pre-existing skips. Commit `a9b1ace`. CI now runs on Corretto 27, and its setup and Java 27 checks pass. CI has never passed as a whole, for reasons that predate this item (`ENG-07`) |
| **RT-02** | Quorus | Concurrency conventions, the task-scope abstraction and the post-Vert.x test standard | ✅ 2026-09-26: RT-02a TaskScope core; RT-02b tracing, MDC and `ScopedValue` propagation; RT-02c [concurrency conventions](../dev/QUORUS_CONCURRENCY_CONVENTIONS.md) with the post-Vert.x test standard, referenced from plan §6.1 and the Copilot instructions; RT-02d StructuredTaskScope structure rules and migration mapping; TaskScope 163/163 lines and 70/70 branches. Commits `341a509` (a), `3e4ec99` (b), `95ed659` (c), `a39206e` (d) |
| **RT-03** | Quorus | `quorus-core` off Vert.x; streaming HTTP adapter closes `ARCH-09` | ✅ RT-03a done 2026-09-26: dead pool code removed (`5a4274f`). RT-03b done 2026-09-26: the HTTP adapter on Apache HttpClient 5 (`RT-Q5`), blocking and streaming, governed pinning with correct SNI, `Host` and hostname verification, closing `ARCH-09` (`fadbb29`, `336ec37`). RT-03c done 2026-09-27 (`fc0feb0`): `TransferEngine` is blocking (`transfer`, `shutdown(Duration)`) with no Vert.x; the engine runs on the caller's thread with a semaphore limit, retries, and cancellation by interrupting only the named transfer; the agent, workflow and examples call it; `OBS-07` closed and `ENG-09` found. RT-03d done 2026-09-27 (`46a5c63`): `TransferProtocol` has no `transferReactive` and its blocking `transfer` is no longer deprecated; the adapters' event-loop guards and `ProtocolFactory`'s Vert.x constructors are gone; `ProtocolContractIsVertxFreeTest` keeps Vert.x out of the protocol and engine contracts. RT-03e done 2026-09-27 (`3230dc4`): `ServiceConnectionJsonCodec` is on Jackson with a JSON-text API and byte-identical output (golden `ServiceConnectionJsonCodecTest`), and `NetworkTopologyService` is blocking, discovering path ends in parallel in a `TaskScope`; no `quorus-core` main code uses Vert.x. The controller registry and the agent policy service convert at their own `JsonObject` boundary until `RT-06` and `RT-05`. RT-03f done 2026-09-27 (commit `829b189`; its message does not carry the §6.1 red/green record), completing RT-03: `quorus-core` has no Vert.x dependency in any scope, and `CoreIsVertxFreeTest` fails if Vert.x returns to its classpath or sources. Its FTPS and remote-path boundary tests use JDK servers; the remote-path test now drives the real HTTP adapter. `TestFutureUtils` moved to the agent and controller test trees until `RT-05` and `RT-06`. Modules that had received Vert.x only through core now declare it: `vertx-core` in `quorus-workflow` and `quorus-integration-examples` until `RT-04`, and `vertx-web-client` in `quorus-controller` until `RT-06`. Removed: `VertxPerformanceBenchmark` (see `DR-C9`) and `PinnedEndpoint`, which had no production caller after RT-03b replaced the Vert.x HTTP adapter. Slices are defined in plan §20 |
| **RT-04** | Quorus | `quorus-workflow` and `quorus-integration-examples` off Vert.x | ✅ 2026-09-27 (commits `18471b6` and `7fb4587`; the §6.1 record is in `7fb4587`'s message): `WorkflowEngine` is blocking (`execute`, `dryRun` and `virtualRun` return the finished execution). Groups run in order and each group's transfers run in parallel in a `TaskScope`. The workflow's `execution.timeout`, previously ignored, now bounds the run: overrunning transfers are interrupted and the execution fails. Interrupting the caller stops the run. The examples call the engine directly and shut their transfer engine down. Neither module depends on Vert.x; `WorkflowIsVertxFreeTest` and `ExamplesAreVertxFreeTest` guard this. The examples' `IntegrationTestSuite` never ran because its name did not match the test runner's patterns; renamed `CrossModuleIntegrationTest`, its 9 tests run and pass. `ENG-12` closed in the same slice: `cancel` stops a run, `parallelism` limits concurrent groups, and `pause`/`resume` are removed. The examples module skips the parent coverage gate, which never applied before (no test ran, so there was no coverage data) and does not fit demo programs; the five product-module gates are unchanged |
| **RT-05** | Quorus | `quorus-agent` off Vert.x. Until then the agent ran transfers on Vert.x worker (platform) threads, where an interrupt cannot break a blocked socket read, so cancellation waits for the adapter's next check or socket timeout; decided 2026-09-27 to resolve this by removing Vert.x, not with an interim bridge | ✅ Started 2026-09-27 after `RT-04`; it does not wait for QRaft (plan v1.38 §20). RT-05a done 2026-09-27 (commits `43b1ba0` and `c66df42`; the §6.1 record is in `c66df42`'s message): the controller client is `java.net.http` (`ControllerClient`) with TLS 1.3 only, mutual TLS, hostname verification and the documented response deadline, over a JDK PEM loader in core (`PemTls`, unencrypted PKCS#8 keys only; the Security Deployment Guide v1.8 says how to convert a PKCS#1 key). Registration, heartbeat, polling and status reporting are blocking and use Jackson; assignments carry the governed connection as codec JSON text. `ControllerClientTlsTest` covers the Phase 1 trust boundary, now with a hostname test that only hostname verification can fail and a TLS 1.2 refusal. `QuorusAgent` called the services on Vert.x workers until RT-05b. RT-05b done 2026-09-28 (commits `9eb820a` and `4cbc1f5`), completing RT-05: the agent runs on virtual threads it owns (a runtime thread that registers, heartbeat and polling loops, a thread per job and a progress-reporter thread per transfer) with a bounded shutdown that cancels running transfers and waits for them; `AgentShutdownStopsTransfersTest` shows a transfer blocked in a socket read now stops at once (red first: the old agent reported itself stopped while the transfer ran on; a platform-thread mutant fails it), which resolves the cancellation limitation above. The health endpoint is on the JDK HTTP server; the controller client makes its own OpenTelemetry client spans and sends W3C trace context (`ControllerClientTracingTest`), replacing the Vert.x tracing integration; `AgentTelemetryConfig` returns the SDK so shutdown flushes it. No Vert.x in the agent pom or sources (`AgentIsVertxFreeTest`); its tests run against the JDK `FakeController`. The Phase 1 trust tests (`ControllerClientTlsTest`) and the R3 reporting tests (`PreExecutionFailureIntegrationTest`, `TransferProgressReportingIntegrationTest`) pass |
| **RT-06** | Quorus | `quorus-controller` HTTP API off Vert.x; removes the `CE-07` bridge | 🟡 Re-sequenced 2026-09-28 (plan v1.42 §20): proceeds before QRaft, after the `ENG-15` baselines, in slices `RT-06a` (consensus interface) to `RT-06d`; only the in-repository Raft engine stays on Vert.x until `CE-10` |
| **RT-07** | Quorus | OpenTelemetry instrumentation without Vert.x integration | 🟠 The agent part was delivered in `RT-05b` (client spans and W3C context from `ControllerClient`). Remaining: the controller's HTTP server and outbound clients, and log correlation (plan v1.39 §20) |
| **RT-08** | Quorus | Vert.x removal gate enforced in the build | 🟡 Partly in place: guard tests fail if Vert.x returns to core, workflow, tenant (`TenantIsVertxFreeTest`, added 2026-09-28), the examples or the agent. Remaining: the controller after `RT-06`, and removing the `vertx-dependencies` BOM import from the root pom |
| **RT-09** | Quorus | Recurring: adopt each six-monthly Java release within its update window | 🟠 |

**Sequencing that affects other sections:** R1-2 and R1-3 (Section A) and Phase 8 should run
after `CE-11`, so their evidence describes the engine that ships. `RT-06` should precede the bulk
of Phase 6. `SEC-04` (Section I) is expected to close through `CE-08` rather than as a change to
the in-repository engine.

---

## 14. Gap-to-Section Traceability

| Gap | Status | Where the remaining work lives |
|---|---|---|
| `ARCH-01` Agent omits `IN_PROGRESS` | Closed (the Architecture Spec's gap table has no ARCH-01 row; DR-B1) | Phase 2 structural delivery |
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
| `ARCH-12` Operational business context absent | Partly open: only an escalation policy is missing (corrected 2026-10-03; the spec says Partial) | Phase 3 first slice delivered the context; escalation policy with P3 alerting |
| `ARCH-13` TLS/mTLS boundary incomplete | Partly open (corrected 2026-10-03; the spec says Partial): enrollment, rotation, deployment evidence, telemetry transport policy and peer-to-node binding (`SEC-04`), plus follower revocation (`SEC-09`) and outbound Raft revocation (`SEC-10`) | Phases 1 and 5; `CE-08` |
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
2. An item is removed only when the source plan's exit criterion is met under §6.1 — not when
   the code merely exists.
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
| 1.27 | 2026-10-03 | Documentation review of 2026-10-02 recorded and its remediation carried out, uncommitted pending review. **Delivered:** `SEC-09`, `SEC-11`, `ENG-17`, `ENG-18`, `ENG-20`; DR-A8 to DR-A11, DR-B4, DR-B8, DR-B9, DR-C1, DR-C4 to DR-C7, DR-D5, DR-F01 to DR-F05, DR-F09, DR-F12, DR-F16, DR-F18 to DR-F23; partial progress on DR-B1, DR-B2, DR-B5, DR-B6, DR-C2, DR-C8, DR-C9, DR-D4, DR-F06, `ENG-07` (documentation lane) and `ENG-19`. Full-reactor regression after the code changes: `mvn clean verify` on JDK 27, 2,519 tests, no failures. **Found while fixing:** `ENG-21` to `ENG-25`; `ENG-21` was then confirmed by a failing test and fixed, and `ENG-25` (`ENG-Q2`) and `ENG-27` (`ENG-Q3`) delivered, and decisions `DR-Q5` (DR-B6 done) and `DR-Q4` (`ENG-02` closed) delivered. **Decisions taken with the owner on 2026-10-03:** `DR-Q1`, `DR-Q3`, `DR-Q4`, `DR-Q5`, `ENG-Q1` to `ENG-Q3`, `SEQ-05` and `DR-Q11`; the four calls made during the pass (`DR-Q10`, deleting the controller entrypoint and the PeeGeeQ hook installer, "repeatable" build wording) were confirmed; removal of the unused `vertx-grpc-*` dependencies approved. New delivery items `ENG-26` and `ENG-27`. **New:** DR-A8 to DR-A11, DR-B8, DR-B9, DR-F18 to DR-F23 (Section H); `SEC-09` to `SEC-11` and `ENG-17` to `ENG-25` (Section I, also in plan v1.44 §4). **Extended** with the review's findings: DR-B1, DR-B2, DR-B4 (no longer waits for DR-Q1), DR-B6 (schema part not blocked), DR-C4 to DR-C8, DR-D4, DR-F02 to DR-F04, DR-F09, DR-F12, `ENG-01`, DR-Q1 (most of option (b) is delivered). **Decision log:** `RT-Q6`, `SEQ-03`, `SEQ-04`, `DR-Q8` and `DR-Q9` entered for decisions taken on 2026-09-27 and 2026-09-28 but recorded only inline; new `DR-Q10` (add the header spaces, `ENG-07`); `DR-Q6` restated. **Corrected:** §14 `ARCH-12` and `ARCH-13` are Partial, as in the spec; §2 counts (Section H was 22 open and 7 in progress, not 23 and 6; Section I lists four 🟡 items, not one); the source plan version; the E.2 `abort()` row (removed by `ENG-09`); commit SHAs added to the closed `ENG-08` to `ENG-16`, `SEC-08` and RT-03c to RT-03e rows; the v1.19 and Section J statements that RT-04 and RT-05a lack the §6.1 record (`7fb4587` and `c66df42` carry it; only `829b189` does not); DR-F04's test class name. 🟨 and ✅ added to the status vocabulary. DR-F11 moved here (done 2026-09-28: the validation results became the results log of the new benchmark specification). DR-F10 was removed in v1.26 without a note: it fixed files under `docs-design/evidence/`, deleted that day |
| 1.26 | 2026-10-02 | `DR-Q6`, DR-F09, DR-C2, DR-C10 and governance rule 2 reworded; §2 Section H counts updated. Plan v1.43 is cited |
| 1.25 | 2026-09-28 | `ENG-16` closed: group-committed audit, handlers off the event loop; B-08 re-measured (results §2.3) |
| 1.24 | 2026-09-28 | `ENG-15b`: B-08 baseline on the Vert.x controller; new `ENG-16` (the audit write path caps the API at about 100 requests/s) |
| 1.23 | 2026-09-28 | `ENG-15a`: benchmark module and the B-09 commit-latency baseline on the in-repository engine |
| 1.22 | 2026-09-28 | `RT-06` re-sequenced to proceed before QRaft (slices `RT-06a` to `RT-06d`), after the `ENG-15` baselines. Plan v1.42 cited |
| 1.21 | 2026-09-28 | QRaft integration assessment recorded (`docs-design/design/QUORUS_QRAFT_INTEGRATION_ASSESSMENT.md`, ADR-0011 v1.1); open decisions `CE-Q1` to `CE-Q5` added. Plan v1.41 cited |
| 1.20 | 2026-09-28 | Decision: keep and adapt the benchmark documents, and build a complete benchmark module. New `ENG-15` (benchmark module, specified in `QUORUS_PERFORMANCE_BENCHMARKS.md` v2.0); `DR-F11` done (results log v2.0 with the old figures as labelled history); `DR-C2` no longer archives the benchmarks document. Plan v1.40 cited |
| 1.19 | 2026-09-28 | Review after `RT-05`. Closed `ENG-03` and `ENG-04` (obsolete); new and closed `ENG-13` (workflow dry-run flags, group retries and strategy); new `ENG-14` (conditions not evaluated). `RT-07` and `RT-08` show what is already delivered. `DR-C1` decided (archive in Quorus), `DR-C2` updated, `DR-F11` re-scoped, `ENG-11` needs an owner, `SEC-03` and `CFG-02` re-verified. Section J cites the RT-03f to RT-05 commits and notes the three whose messages lack the §6.1 record. Header date and plan version (v1.39) corrected |
| 1.18 | 2026-09-28 | `RT-05b` done, completing `RT-05`: the agent runs on virtual threads with no Vert.x, and agent shutdown stops a transfer blocked in socket I/O at once. User Guide v2.4, Architecture Quickstart v2.5 and Architecture Specification v2.11 no longer describe transfer execution through Vert.x |
| 1.17 | 2026-09-27 | `RT-05a` done: the agent's controller client and controller services without Vert.x. Plan v1.38 cited: `RT-05` no longer waits for QRaft |
| 1.16 | 2026-09-27 | `RT-04` done: workflow and examples off Vert.x, with a blocking workflow engine that enforces the workflow timeout, cancels for real and limits concurrent groups to `parallelism`. `ENG-12` found and closed; `pause`/`resume` removed from `WorkflowEngine` |
| 1.15 | 2026-09-27 | `RT-03f` done, completing `RT-03`: no Vert.x in `quorus-core`; modules that had received Vert.x only through core now declare it; `DR-C9` updated |
| 1.14 | 2026-09-27 | `RT-03e` done: the connection codec on Jackson and a blocking `NetworkTopologyService`; no Vert.x in `quorus-core` main code |
| 1.13 | 2026-09-27 | `RT-03d` done: no Vert.x in the protocol or engine contracts |
| 1.12 | 2026-09-27 | `DR-Q6`: a slice's record is its commit message. `RT-03c` follow-up: `ENG-09` closed (adapter-wide abort and shared connection state removed); new and closed `ENG-10` (adapters and agent now report in-flight progress and honour stop requests) and `SEC-08` (FTP trust-all setter removed); new `ENG-11` (metrics singleton). The agent's deprecated owned-Vert.x constructor is removed. `RT-05` records the agent cancellation limitation and the decision to resolve it by removing Vert.x. Plan v1.37 is cited |
| 1.11 | 2026-09-27 | `RT-03c` done: blocking `TransferEngine` on the caller's thread, with no Vert.x. `OBS-07` closed by the rewrite (37 DEBUG statements to 3). New `ENG-09`: the FTP and SFTP adapters share one abort target across transfers. Plan v1.36 is cited. **Moved from Section H under §15.5:** DR-B3 (superseded by DR-Q7; its fixes were made in the OpenAPI contract under `ENG-08`) |
| 1.10 | 2026-09-27 | API documentation consolidated (decision DR-Q7): the OpenAPI contract is the only current-API reference, and the API Reference is deleted. New and closed: `ENG-08`, with `OpenApiReferenceContractTest`. DR-B3 is superseded, DR-B2 has progressed, and DR-D1 and DR-F03 are updated. §14 now agrees with REST spec §20 for API-01, -04, -05, -10 and -13. Plan v1.35 is cited. **Moved from Section H under §15.5:** DR-A1 (R1-1 committed), DR-C11 (the `m2cache`/`M2_REPO`/`m2-repo` machinery removed by `RT-01b`), DR-F14 (personal paths removed from live documents; `QUORUS_LOG_STYLE.md` remains under DR-C8), all done; and DR-F17 (merged into DR-C10) |
| 1.9 | 2026-09-27 | Review of v1.8 against the repository and CI. **New:** `ENG-07`, because CI has never passed (FTPS test binds privileged port 21 on Linux; 11 document headers fail the documentation check). Decisions `SEQ-01` (defer the CI repair; local full-reactor runs are regression evidence meanwhile; no phase closes while `ENG-07` is open) and `SEQ-02` (`SEC-07` becomes a Phase 4 hardening follow-up). **Corrected:** `RT-Q4` (runtime option A, single-stage images, no builder or Maven; ADR-0012 v1.2); the Section G `ARCH-09` paragraph, which contradicted §14 (the Architecture Specification v2.10 now also closes it); RT-01b's "CI not yet executed"; DR-D2, re-scoped because the header and link checks already exist; DR-D4, whose ADR-0009 is no longer blocked; DR-F01, updated for `RT-03a`, `RT-03b` and `RT-01b`; the E.2 tenant-lock and WebClient rows; the Section I phase-assignment statement; the §1 test rule, which now points to plan §6.1; governance rule 1 and §1 precedence item 3, which now agree; §14 `ARCH-07`, which no longer lists the closed R1-1; DR-C4's DR-C11 wording; the plan version (v1.34) and §2 counts. **Closed:** DR-A1 and DR-F14. DR-F17 merged into DR-C10. **Sequencing:** `OBS-07` goes into `RT-03c`; `OBS-15` waits for `CE-07`/`CE-10`. Section J cites commits for completed work, and its duplicate `RT-Q1`–`RT-Q3` rows are removed. **Moved from Section H under §15.5:** DR-A2 (orphaned SHAs preserved via the rewrite map), DR-A3 (prompt file removed, Copilot instructions reconciled), DR-A5 (explicit Compose development profile, mTLS example, health probe; `CFG-01`), DR-A6 (README quick start and HTTPie tenant fields), DR-A7 (authentication statements point to Architecture Spec §3), DR-B7 (register v1.5 / plan v1.28 pass), DR-F07 (reviews moved to `docs-design/reviews/`), DR-F08 (`docs-design/README.md` v1.1), DR-F13 (Configuration Handover link and path fixed, archived), all done; DR-C3 (superseded by `RT-02` conventions) and DR-F15 (superseded by ADR-0011 and `CE-10`). Also records changes made after v1.8 without a version: `RT-Q5`, `SEC-07`, DR-C11 done, DR-C9 in progress, and the `RT-01`–`RT-03` states |
| 1.8 | 2026-09-26 | Became the single task list: merged the documentation review task list (archived) as Section H and its code defects as aliases in Section I; added the §3 decision log with ADR-0011, ADR-0012, `RT-Q1`–`RT-Q3`, DR-Q1–DR-Q6 and the Phase 0/4 status decision; applied the decisions to tasks (DR-C3 and DR-F15 superseded, DR-C4, DR-C8, DR-B6 and DR-D4 updated, new DR-C11 to remove the `m2cache`/`M2_REPO`/`m2-repo` machinery); renumbered sections after §2; updated governance |
| 1.7 | 2026-09-26 | Recorded decisions `RT-Q1` (no preview; task-scope abstraction), `RT-Q2` (JDK `HttpsServer`) and `RT-Q3` (six-monthly Java releases); added recurring `RT-09`; cites plan v1.30 |
| 1.6 | 2026-09-26 | Added Section J for plan §20 platform migration workstreams (`CE-01`–`CE-11`, `RT-Q1`–`RT-Q3`, `RT-01`–`RT-08`) under ADR-0011 and ADR-0012; updated the raftlog constraint (Maven Central coordinates, reached through QRaft after `CE-10`); routed `ARCH-09` to `RT-03` and `ARCH-07` through `CE-11`; renumbered traceability and governance to §13 and §14; updated plan section references |
| 1.5 | 2026-09-26 | Documentation-review pass (`DR-B7`): closed OBS-04, OBS-05 and OBS-14 as already satisfied and corrected OBS-07 to 37 statements; fixed the section counts, plan and OTel versions and revision order; settled the Phase 0 and Phase 4 status statement; past-tensed the fixed fixture-volume statement; added `R1-4`, `PROC-01` and `P2-13` for plan items without IDs; added Section I for `CFG-01` (moved from D.6), the four configuration residuals, and the security and engineering defects from the documentation review; collapsed Section H after its retention revision; renumbered traceability and governance to §12 and §13 |
| 1.4 | 2026-09-25 | Recorded `CFG-01` complete after validation of the explicit development posture, Compose cleanup, corrected health probing and generated-certificate mTLS example |
| 1.3 | 2026-09-07 | Remediated the three findings from the R1-1 slice: containerised test fixtures now write Raft state to named volumes at the deployed path, orphaned `TransferMetrics` deleted (`OBS-08`), and the `LeaderGuardHandlerTest` startup flake root-caused and fixed; recorded the unswept discarded-`start()`-future pattern as `OBS-15` |
| 1.2 | 2026-09-07 | Closed `R1-1` container-recreation acceptance with four containerised tests and a controller regression of 601 tests; recorded the non-durable default Docker test fixture found during the work; classified the recovery tests as retrospective characterization because no product defect was found |
| 1.1 | 2026-09-07 | Applied all six Section H corrections to the OTel plan (v2.5 → v2.6), including removal of three production-readiness claims it should not have made; archived the alpha plan, Stage 6 security/routes plan, OTel plan and sealed-record design, leaving `task/` holding only the enterprise plan and this register; repaired every cross-reference broken by the move |
| 1.0 | 2026-09-07 | Initial consolidation of all outstanding tasks from the five `docs-design/task/` planning documents, with live-source verification of eleven stale OTel grid claims and the sealed-record transition phases |
