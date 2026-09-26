<img src="../../docs/quorus-logo.png" alt="Quorus" width="120"/>

# Quorus Documentation Review — 2026-09-24

**Version:** 1.0  
**Date:** 2026-09-24 (completed 2026-09-25)  
**Prepared for:** Mark Ray-Smith — Cityline Ltd  
**Prepared by:** Claude (Cowork), documentation and code cross-check  
**License:** Apache 2.0  
**Status:** Point-in-time review. Findings apply to HEAD `216348a` (2026-09-07) plus the uncommitted working tree as observed on 2026-09-24. Remediation progress is tracked in the [Outstanding Work Register](../task/QUORUS_OUTSTANDING_WORK_REGISTER.md#11-section-h--documentation-remediation) (Section H, and Section I for code defects), not here.  
**Scope:** Live documentation. That is `docs/`, `docs-design/` except `archive/`, the root and module READMEs, ADRs, runbooks and repo-meta documents.

---

## Contents

1. [Purpose and method](#1-purpose-and-method)
2. [Executive summary](#2-executive-summary)
3. [Document scorecard](#3-document-scorecard)
4. [Cross-cutting findings](#4-cross-cutting-findings)
5. [Status of the 2026-08-31 review findings (QR-01 to QR-11)](#5-status-of-the-2026-08-31-review-findings)
6. [Code and configuration defects found during the review](#6-code-and-configuration-defects-found-during-the-review)
7. [Detailed findings by area](#7-detailed-findings-by-area)
8. [Project state as the documents describe it](#8-project-state-as-the-documents-describe-it)
9. [Recommended remediation plan](#9-recommended-remediation-plan)
10. [Proposed documentation structure](#10-proposed-documentation-structure)
11. [Limitations and open review items](#11-limitations-and-open-review-items)
- [Appendix A — Orphaned commit references](#appendix-a--orphaned-commit-references)
- [Appendix B — Endpoint inventory reconciliation](#appendix-b--endpoint-inventory-reconciliation)
- [Appendix C — Selected contradicted claims](#appendix-c--selected-contradicted-claims)

---

## 1. Purpose and method

This review answers three questions about the Quorus documentation:

- Can a reader trust what each document says about the current system?
- Do the documents agree with each other, and with the precedence rules in `docs-design/README.md`?
- What should be fixed, merged, archived or removed?

**Documents in scope.** 75 live documents, about 1.6 MB of text:

| Area | Count | Approx. size |
|---|---|---|
| `docs/` (canonical plus user and operator guides) | 15 | 310 KB |
| `docs-design/` excluding `archive/` | 52 | 1,185 KB |
| Root, `docker/`, module and script docs | 8 | 63 KB |

**Excluded:**

- `docs-design/archive/`, which is historical by declaration.
- `.history/`, which holds about 250 editor snapshots.
- `temp/`, which holds three complete worktree copies and several hundred MB of build logs.
- The sample PDFs under `corporate-data/`, which are test fixtures.

**Method.**

- **Reading.** Each document was read in full by one of eight parallel reviewers, grouped by area: canonical API/architecture, user and operator guides, security/configuration/ADRs, planning and evidence, system design, Raft/simulator design, `dev/`, and performance/testing.
- **Links.** A script checked every relative Markdown link in the 71 Markdown files in scope (§4.11).
- **Checking.** About 260 specific claims were checked against source at HEAD and in the working tree. Priority went to claims a reader would act on: endpoints, configuration keys, environment variables, commands, file paths, class names, versions and "implemented/closed" status. About half were confirmed; the rest were contradicted, only partly true, or stale. The checks deliberately targeted places where drift was likely, so this is not a random-sample accuracy rate.
- **Safety.** All access was read-only. Git was used only through `--no-optional-locks` log/show/cat-file commands after an early `git status` left a lock file, which has since been removed.

**Grades.**

| Grade | Meaning |
|---|---|
| **A** | Accurate and current |
| **B** | Mostly accurate, minor drift |
| **C** | Useful, but materially stale or contradicts itself |
| **D** | Misleading as written, or not about Quorus |

---

## 2. Executive summary

The best parts of the documentation set are unusually disciplined:

- the precedence model in `docs-design/README.md`;
- the Security Deployment Guide;
- the dated evidence records with reconciled test counts;
- the Outstanding Work Register with stable IDs.

The set has not kept pace with the pace of change in September 2026. In the space of a week the code gained:

- Phase 1 mTLS and policy enforcement (2026-09-02);
- configuration isolation (09-03 and 09-04);
- Phase 4 governed service connections (09-03);
- raftlog-only storage with snapshots (09-04);
- the R1–R6 remediation (09-04 to 09-07).

As a result, the canonical documents now contradict the code, and each other, in places that matter. The operator path (README → Compose → cluster guide → HTTPie runbook) also does not work as written.

### Ten key findings

1. **At least eight documents misstate the security status.**
   - These say there is no authenticated boundary, or that service-connection governance is still outstanding:
     - Architecture Spec gap ARCH-03 and gate §13
     - REST Spec gap API-02
     - User Guide
     - Architecture Quickstart
     - Cluster Startup Guide
     - `docker/README.md`
     - the System Design capability table
     - the Security Deployment Guide's own §1
   - The code does have that boundary:
     - TLS 1.3 with required client certificates on both HTTP and Raft;
     - authentication, authorization and audit middleware;
     - a SHA-256 hash-chained audit log;
     - a production profile that refuses to start without certificates.

2. **The documented quick start cannot start.**
   - Every file in `docker/compose/` runs the controller on the packaged `production` profile, with TLS enabled and empty certificate paths. `SecurityConfig.validate()` rejects that combination, and no Compose file sets a development profile.
   - Agents in the full-network topology have no tenant ID and use `http://` URLs.
   - The HTTPie runbook's register and create steps omit `tenantId`, so they receive `400`.
   - Prior-review finding QR-04 is therefore still open, in a new form.

3. **The canonical YAML guide describes a looser and more capable parser than the one that exists.**
   - Its minimal example fails the engine's pre-execution validation, which requires seven metadata fields. The schema validator used by the validation CLI also requires a `spec.execution` block.
   - `execution.dryRun`, `parallelism`, `timeout`, `strategy` and group `retryCount` are parsed but never used.
   - `options` are not passed to protocol adapters.
   - Nested variables are resolved in a single pass.
   - The README example fails validation for the same reason.

4. **The REST API Specification mixes delivered and target behaviour without saying which is which.**
   - All 51 "Current" endpoint rows are correct.
   - Several normative sections are target-state but carry no status label: the headers, problem-detail fields, pagination, scopes, state names, and the 21 "stable" error codes. Of those error codes, only `NOT_LEADER` exists in `ErrorCode.java`.
   - Its gap table says there is no OpenAPI contract. The repository has an OpenAPI 3.1 contract and a test asserting that registered and declared operations are equal.

5. **The document review uncovered two security defects.**
   - **Revocation serials:** matching compares against `BigInteger.toString(16)`, which drops leading zeros. The documented example `01AF44`, and any openssl-formatted serial with a leading zero, therefore never matches.
   - **Revocation scope:** runtime revocation is held in memory per controller. It is not replicated and is lost on restart, yet three documents describe it as cluster-wide. The certificate incident runbook relies on both behaviours.

6. **A history rewrite broke evidence traceability.**
   - Eight commit SHAs cited in the plan, register, configuration handover and evidence records are no longer reachable from any ref. One of them is `b604505`, the R6 acceptance revision.
   - Each has a tree-identical replacement on master (see Appendix A). All eight were confirmed.
   - No branch or tag names the originals, so clones from `origin` cannot resolve them. The local copies should not be relied on either.

7. **Closures recorded on 2026-09-07 depend on uncommitted work.**
   - The following exist only in the working tree, 17 days later:
     - the R1-1 container-recreation closure;
     - OBS-08;
     - the `LeaderGuardHandlerTest` fix;
     - the Compose fixture hardening;
     - the plan v1.26 and register v1.3 edits.
   - HEAD still shows R1-1 as open.
   - The plan and register also disagree on whether Phase 4 is complete or "reopened".

8. **The three design documents are mostly out of date.**
   - `QUORUS_SYSTEM_DESIGN.md` (196 KB, grade D):
     - names the removed `QuorusStateMachine` 16 times;
     - places the workflow, tenant and transfer engines inside the controller;
     - describes route triggers and agent-to-agent streaming as operating;
     - retains a PostgreSQL/Redis/etcd architecture.
   - `QUORUS_RAFT_WAL_DESIGN.md`: about 90% has been superseded by raftlog-core, although its opening banner is accurate.
   - `QUORUS_IN_MEMORY_SIMULATORS_DESIGN.md`: claims the simulators implement production interfaces, which they do not, and says three existing Raft test classes are missing.
   - `.github/copilot-instructions.md` directs AI coding assistants to `QUORUS_SYSTEM_DESIGN.md` as the "comprehensive architecture documentation", instead of the canonical Architecture Specification. It also still names `QuorusStateMachine` and the removed `quorus-api` module.

9. **About a fifth of `docs-design/` by volume (roughly 260 KB) is not about Quorus.**
   - 10 of the 12 files in `dev/vertx5-advice/` are PeeGeeQ code reviews and plans. The other two are generic guides built from PeeGeeQ material.
   - `performance/CRITICAL_PERFORMANCE_REFACTORING_GUIDELINES.md` and `dev/prompts.txt` come from the APEX project. `prompts.txt` is a collection of AI-assistant prompts that never mentions Quorus. It contains no credentials, hostnames, e-mail addresses or personal paths.
   - Separately, the three `CONNECTION_POOL_*` documents describe a Quorus API that was deleted in January.
   - PeeGeeQ traces have also leaked into code and scripts. `quorus-core` still carries an unused `vertx-pg-client` dependency.

10. **Duplication is the main source of drift.**
    - The endpoint list appears in five places, and none of the five is complete.
    - The DNS-admission and FTPS paragraphs appear in three or four places.
    - There are four different Compose inventories.
    - There are four conflicting accounts of the Vert.x 5 migration. The headline improvement is given as 388%, 483% and 335,061%.

### What to do first

The full plan is in §9. The highest-value actions are:

1. Give the Compose files an explicit, clearly labelled development profile. Then fix the README quick start, the cluster and Docker guides, and the HTTPie runbook so that one development path works end to end.
2. Correct the security-status statements in the eight documents. Fix the revocation serial normalisation and document that runtime revocation is node-local, or replicate it.
3. Commit the 2026-09-07 work. Map the orphaned SHAs to their master equivalents, or tag them.
4. Reconcile the gap tables in the Architecture and REST specifications, and give every normative REST section a Current, Required or Planned label.
5. Fix the YAML Syntax Guide (or the parser) so that the documented minimal workflow runs.
6. Move the PeeGeeQ and APEX material out of the repository. Archive about 20 superseded documents and consolidate the four Docker guides into one.

### Grade distribution

| Grade | A / A− | B+ / B / B− | C+ / C / C− | D+ / D | Foreign (not graded) |
|---|---|---|---|---|---|
| Documents | 1 | 20 | 29 | 14 | 11 |

---

## 3. Document scorecard

**Currency** labels: Current / Partly stale / Stale / Historical / Foreign.  
**Action** labels: Keep / Fix / Rewrite / Merge / Archive / Move out / Delete.  
Sizes are on-disk sizes. "Header" gives the version and date in the document's own header block.

### 3.1 Root, Docker and repo-meta documents

| Document | Size | Header | Grade | Currency | Action and main reason |
|---|---|---|---|---|---|
| `README.md` | 6.5 KB | none | B− | Partly stale | **Fix.** Capability claims are accurate. However, the Compose quick start fails production validation, the example workflow fails the validator, `mvn verify` should be `mvn install` before `exec`, and a personal `JAVA_HOME` path is hard-coded. |
| `NOTICE` | 4.3 KB | none | B | Current | **Fix.** All 20 listed versions match the poms. However, it lists test-scope artifacts as shipped components and leaves out shipped runtime dependencies (`vertx-pg-client`, Netty, `jackson-dataformat-yaml`, `javax.annotation-api` under CDDL). |
| `OPEN_SOURCE_USAGE.md` | 7.0 KB | none | B− | Partly stale | **Fix, or merge with NOTICE.** It separates runtime and test dependencies correctly (NOTICE does not) and lists `vertx-pg-client` (NOTICE does not). However, it gives RaftLog Core as 1.1.0 (the project uses 1.2.0), still lists RocksDB JNI (no longer a dependency), omits `javax.annotation-api`, and its sample plugin configuration references a `LICENSE-HEADER.txt` that does not exist. |
| `.github/copilot-instructions.md` | 20.5 KB | none | C+ | Partly stale | **Fix.** This file steers AI coding assistants, so stale guidance spreads. The configuration precedence section and the Vert.x-only test rules are current. Stale: it points to `QUORUS_SYSTEM_DESIGN.md` as the architecture reference; it names `QuorusStateMachine`; the module table lists `quorus-api` and omits `quorus-integration-examples`; it says blocking adapters use `WorkerExecutor`; it describes an agent lifecycle with `POST /agents/{id}/status` and `DELETE /agents/{id}`, neither of which exists; and its workflow example fails validation. See §7.2. |
| `docker/README.md` | 9.1 KB | v2.0, 09-01 | D | Stale | **Rewrite** as the single Docker guide. Several procedures fail: full-network agents have no tenant, and Quick Start §3 posts to port 8080, which `docker-compose.yml` does not map. It also says "no authentication". |
| `quorus-controller/DOCKER_BUILD_OPTIMIZATION.md` | 2.2 KB | v1.0, 09-01 | D | Stale | **Merge** into the Docker guide. The cache mounts and the `m2cache` build-context requirement do not match the Dockerfile, and the performance figures have no evidence. |
| `scripts/httpie/RUNBOOK.txt` | 2.4 KB | none | D | Stale | **Fix.** The register and create-transfer payloads have no `tenantId` (both get `400`). There is no TLS or development-profile setup. |
| `quorus-integration-examples/.../docs/YAML-VALIDATION-GUIDE.md` | 11.4 KB | v1.1, 09-01 | D+ | Partly stale | **Merge** its correct rules into the YAML guide, then delete it. It says names may contain spaces (the validator rejects them) and describes `kind` warnings, JSON Schema and streaming validation, none of which exist. It also ships inside the examples jar. |

### 3.2 Canonical documents (`docs/`)

| Document | Size | Header | Grade | Currency | Action and main reason |
|---|---|---|---|---|---|
| `QUORUS_ARCHITECTURE_SPECIFICATION.md` | 68 KB | v2.7, 09-04 | C | Partly stale | **Fix.** Its capability table and security section contradict its own gate table (§13) and gap table (§14). ARCH-03, ARCH-06, ARCH-12 and ARCH-13 are stale. |
| `QUORUS_REST_API_SPECIFICATION.md` | 53 KB | v2.4, 09-05 | C | Partly stale | **Fix.** Several normative sections have no status label (§3.2 headers, §3.4 problem format, §3.5 pagination, §4.2 scopes, §6.3 states, §16 error codes). Gap rows API-01 and API-02 are false. |
| `QUORUS_API_REFERENCE.md` | 25 KB | v3.8, 09-05 | B | Mostly current | **Fix.** Every documented endpoint exists. It leaves out 13 live routes, lists `BUSY` and `AT_RISK` values the code never produces, and has no error-envelope section. |
| `QUORUS_YAML_SYNTAX_GUIDE.md` | 20.5 KB | v2.2, 09-01 | C | Partly stale | **Fix.** Its required fields, `execution` semantics, options pass-through and nesting behaviour contradict the validator and the engine. |
| `QUORUS_SECURITY_DEPLOYMENT_GUIDE.md` | 22.8 KB | v1.6, 09-06 | B | Partly stale | **Fix.** The keys and fail-closed checks are accurate. The revocation-serial example never matches, and runtime revocation is described as shared. §1 still lists service connections as release work. §11–14 are release notes. |
| `QUORUS_CERTIFICATE_INCIDENT_RUNBOOK.md` | 8.6 KB | v1.1, 09-01 | B | Partly stale | **Fix.** Revocation must be sent to every controller *and* added to configuration before any restart. Raft has no CRL. |

### 3.3 Other `docs/` documents

| Document | Size | Header | Grade | Currency | Action and main reason |
|---|---|---|---|---|---|
| `QUORUS_SERVICE_CONNECTION_OPERATIONS_RUNBOOK.md` | 8.1 KB | v1.0, 09-03 | B | Partly stale | **Fix.** All environment names are correct. It is missing the R4 and R5 behaviour (DNS 503/504/409, FTPS 21 vs 990, partial updates, elevation, event paging), which currently lives only in the Security Guide's §13–14. |
| `QUORUS_USER_GUIDE.md` | 8.4 KB | v2.2, 09-01 | C+ | Partly stale | **Fix.** It says "the controller does not authenticate the caller". It lists progress, events and attempts as gaps although they exist. It gives `AGENT_TENANT_ID` priority when `QUORUS_AGENT_TENANT_ID` actually wins. It has no NFS section. |
| `QUORUS_WORKFLOWS_README.md` | 3.9 KB | v2.1, 09-01 | B | Current | **Fix (small).** It should say which fields are required and which are ignored. |
| `QUORUS_INTEGRATION_EXAMPLES_README.md` | 2.7 KB | v2.1, 09-01 | B+ | Current | **Fix (small).** Add an `mvn install` step, and mention the pom's default `mainClass`. |
| `QUORUS_ARCHITECTURE_QUICKSTART.md` | 7.4 KB | v2.3, 09-04 | B− | Partly stale | **Fix.** All ten `AppConfig` defaults are correct. It says "no built-in authentication", and its HTTP endpoint list leaves out security, service-connection and telemetry routes. Link to the API reference instead of repeating the list. |
| `QUORUS_CLUSTER_STARTUP_GUIDE.md` | 4.1 KB | v2.2, 09-01 | D | Stale | **Merge** into the Docker guide. Its commands start controllers that fail validation. It never mentions `M2_REPO`. The observability stack sets an `OTEL_*` variable that the code does not read. |
| `QUORUS-DOCKER-TESTING-README.md` | 3.3 KB | v2.1, 09-01 | C | Partly stale | **Merge** into the Docker guide. It is an inventory only, and its validation pattern fails at step 2. |
| `QUORUS_CONFIGURATION_ISOLATION_HANDOVER_2026-09-03.md` | 47.5 KB | v1.9, "Superseded" | C | Historical | **Archive.** First extract §2.1 and §8 into a canonical configuration reference. It cites six orphaned SHAs, includes a personal path, and has four untracked residual defects. |
| `QUORUS_CODEBASE_AND_DOCUMENTATION_REVIEW_2026-08-31.md` | 25.5 KB | v1.0, 08-31 | B | Historical | **Keep and annotate.** Append the QR status table from §5, then move it to a `reviews/` folder. |

### 3.4 `docs-design/` governance, ADRs and reference

| Document | Size | Header | Grade | Currency | Action and main reason |
|---|---|---|---|---|---|
| `docs-design/README.md` | 4.0 KB | v1.0, 09-01 | B | Current | **Fix.** The precedence rules are accurate. Bump the header, which is still 09-01 although the body says 09-07. Add `evidence/`, `architecture-decisions/` and `reference/` to the directory table. |
| `ADR-0001-EVENT-STORAGE.md` | 1.5 KB | v1.0, 09-01 | C | Current | **Keep.** Add an Alternatives section. |
| `ADR-0002-PROGRESS-CHECKPOINTING.md` | 1.4 KB | v1.0, 09-01 | C | Partly stale | **Fix.** It still says "Phase 2 adds … fencing. Until then …", but fencing is implemented. |
| `ADR-0003-IDENTITY-BOUNDARY.md` | 2.1 KB | v1.0, 09-01 | C | Partly stale | **Fix.** Its note that "revocation propagation remains required" is the only accurate statement of the node-local limitation anywhere in the set, so keep it and update the rest. |
| `ADR-0004-SECRET-PROVIDERS.md` | 1.9 KB | v1.1, 09-03 | C | Current | **Keep.** |
| `ADR-0005-DEPLOYMENT-OWNERSHIP.md` | 1.2 KB | v1.0, 09-01 | C | Current | **Keep.** The whole ADR set has no index, no supersession fields, and no ADRs for raftlog-core, configuration isolation, schema 3 or revocation scope. |
| `reference/QUORUS_REPRODUCIBLE_BUILD_AND_EVIDENCE.md` | 2.1 KB | v1.0, 09-01 | C | Partly stale | **Fix.** There is no `project.build.outputTimestamp`, so the build is repeatable but not byte-reproducible. Its evidence figures are frozen at M0. |
| `reference/QUORUS_VERSIONING_AND_COMPATIBILITY_POLICY.md` | 2.7 KB | v1.0, 09-01 | D | Stale | **Fix.** It says the command and snapshot schema is version 1; the code is `VersionRange(0, 3)`. It does not record the configuration-contract break from `b35fb25`, and it has no product-version rule. |

### 3.5 `docs-design/task/` and `evidence/`

| Document | Size | Header | Grade | Currency | Action and main reason |
|---|---|---|---|---|---|
| `task/QUORUS_ENTERPRISE_IMPLEMENTATION_PLAN.md` | 89.6 KB | v1.26, 09-07 (uncommitted) | C+ | Partly stale | **Fix.** The header says Phase 4 is "reopened" while §11 says Complete. It cites five orphaned SHAs, lacks the revision history that its §23 requires, and presents a present-tense "no volumes" statement that has since been fixed. |
| `task/QUORUS_OUTSTANDING_WORK_REGISTER.md` | 39.2 KB | v1.3, 09-07 (uncommitted) | B− | Partly stale | **Fix.** OBS-04, OBS-05, OBS-07 and OBS-14 are stale against the code. The section counts are wrong (F: 8 vs 10 rows; H: 6 open vs 0 open). It cites plan v1.25 instead of v1.26. Three plan items have no ID. |
| `evidence/full-suite-error-remediation-2026-09-05.md` | 4.0 KB | none | B+ | Historical | **Fix (small).** Cite the fix commit (`a0103a0`). |
| `evidence/r1-container-recreation-2026-09-07.md` | 13.2 KB | 09-07, **untracked** | B− | Current (uncommitted) | **Commit it**, then add revision identity, timestamps and log hashes. It contradicts itself on fixture durability (§3 and §10.4 vs §12.1). |
| `evidence/r4-dns-remediation-2026-09-05.md` | 6.1 KB | none | A− | Historical | **Fix (small).** "Changes are uncommitted" should now read `8b3cf5c`. |
| `evidence/r5-closure-2026-09-05.md` | 3.9 KB | none | B+ | Historical | **Fix (small).** Add the closure commit. |
| `evidence/r6-final-acceptance-2026-09-05.md` | 4.0 KB | none | B+ | Historical | **Fix.** Map `b604505` to `dc447d4`. The claim that the transport failure is "retained" is inaccurate (`RaftNode.java:602-616`). |
| `evidence/raftlog-validation-handover-2026-09-05.md` | 9.4 KB | none | B− | Historical | **Fix.** Add a supersession pointer to R1-1. It is written as instructions for a session that has already taken place. |
| `evidence/remediation-r4-r6-2026-09-05.md` | 16.4 KB | none | C | Superseded | **Archive.** Only the banner reflects later events; the body still says "R4 is temporarily blocked". |

### 3.6 `docs-design/design/`

| Document | Size | Header | Grade | Currency | Action and main reason |
|---|---|---|---|---|---|
| `QUORUS_SYSTEM_DESIGN.md` | 195.8 KB | v3.6, 09-04 | D | Mixed. The body is about 7 months stale | **Split, then archive about 60%.** Stale class names, embedded engines, route triggers and streaming described as current, PostgreSQL/Redis/etcd architecture, wrong environment names and metrics, and seven duplicated sections. |
| `QUORUS_RAFT_WAL_DESIGN.md` | 112.7 KB | v1.1, 09-04 | C+ | About 10% current | **Extract** a Raft storage reference (Status block, §14, §16.1 invariants, §19 checkpoint, Appendix F), then archive the rest. The "✅ Complete" tables and "no snapshots" claims read as current. |
| `QUORUS_IN_MEMORY_SIMULATORS_DESIGN.md` | 105.8 KB | v2.0, 01-28 | C− | Stale since February | **Trim and correct.** It falsely claims interface conformance and shows "REAL PRODUCTION CODE" excerpts that do not exist. It says RaftChaosTest, RaftFailureTest and InfrastructureSmokeTest are missing, but all three exist. Appendix C "All Validated" is also false. |

### 3.7 `docs-design/dev/`

| Document | Size | Grade | Relevant to Quorus? | Action and main reason |
|---|---|---|---|---|
| `dev/prompts.txt` | 38.2 KB | Not graded | No (APEX) | **Move out.** A collection of AI-assistant prompts and lessons-learned notes for the APEX rules-engine project: 53 APEX mentions, 0 Quorus mentions. A pattern scan and a read-through found no credentials, tokens, keys, hostnames, IP addresses, e-mail addresses or personal paths. It is tracked in git (`c58bb3c`). |
| `vertx-migration/QUORUS_VERTX5_AUDIT_REPORT.md` | 32.5 KB | C | Yes (historical) | **Archive with corrections.** It cites `module-info.java`, pool presets, VertxProducer/CDI, WorkerExecutor, TransferMetrics and dual-mode HTTP, none of which exist. It contradicts itself on phase status. |
| `vertx-migration/QUORUS_VERTX5_IMPLEMENTATION_PLAN.md` | 41.0 KB | D | Yes (historical) | **Archive unlinked, or delete.** Claims "+335,061% throughput" and gives conflicting test counts within the file. |
| `vertx-migration/QUORUS_VERTX5_MIGRATION_GUIDE.md` | 39.6 KB | C | Partly | **Replace** with a short "Vert.x 5 conventions for Quorus" page, then archive. Vert.x gRPC and streaming HTTP were never built as described. |
| `vertx-migration/VERTX5_DEPLOYMENT_GUIDE.md` | 8.1 KB | D | Superseded | **Delete or archive.** Its PostgreSQL, Java 21 and `quorus-api` jar instructions are wrong, and the environment variables it uses are not read. |
| `vertx-migration/VERTX5_LESSONS_LEARNED.md` | 10.3 KB | C− | Partly | **Archive.** It claims virtual threads, CDI and pool presets are implemented. |
| `vertx-migration/VERTX5_MIGRATION_SUMMARY.md` | 5.9 KB | C | Yes (historical) | **Archive** as the one migration record. Its future items (OpenTelemetry, Prometheus) are now done. |
| `vertx5-advice/Vertx-5x-Patterns-Guide.md` | 65.5 KB | C | Partly (PeeGeeQ-based, Vert.x 5.0.4) | **Move out.** Keep §1 (Future composition) in the conventions page. The document's tail is structurally broken. |
| `vertx5-advice/vertx5-migration-general-guide.md` | 19.8 KB | C | Partly (PeeGeeQ examples) | **Move out.** Keep the anti-pattern list in the conventions page. |
| `vertx5-advice/` — 10 PeeGeeQ documents: Legacy-JDBC plan; reviews of OutboxFactory, PeeGeeQManager, PgClientFactory, PgConnectionConfig, PgConnectionManager, PgPoolConfig; PeeGeeQ-Shutdown-Guide; Vert.x-Instance consolidation plan; peegeeq-db-review | about 165 KB | Not graded | No | **Move to the PeeGeeQ repository.** Their own "See Also" links point there. Several are chat transcripts ("Mark, solid scaffolding…"). The PeeGeeQManager review recommends the Vert.x 4 `executeBlocking(promise -> …)` signature, which was removed in Vert.x 5. |

### 3.8 `docs-design/performance/` and `testing/`

| Document | Size | Grade | Currency | Action and main reason |
|---|---|---|---|---|
| `performance/CONNECTION_POOL_BENCHMARK_RESULTS.md` | 5.3 KB | D | Obsolete | **Archive or delete.** The API and the benchmark test were deleted on 2026-01-06, and the benchmark measured `Thread.sleep(1)` against an in-process pool. |
| `performance/CONNECTION_POOL_OPTIMIZATION_GUIDE.md` | 7.1 KB | D | Obsolete | **Archive or delete.** None of its samples compile against the current code. |
| `performance/CONNECTION_POOL_OPTIMIZATION_SUMMARY.md` | 6.5 KB | D | Obsolete | **Archive or delete.** "No Breaking Changes" is false. |
| `performance/CRITICAL_PERFORMANCE_REFACTORING_GUIDELINES.md` | 10.6 KB | D | Foreign (APEX) | **Delete.** It refers to `apex-core` classes that are not in Quorus. |
| `performance/DOCKER_TEST_PERFORMANCE.md` | 6.6 KB | B | Mostly current | **Fix (small).** Add the fifth Docker-tagged class (`ContainerRecreationDurabilityTest`), and record CPU and RAM. |
| `performance/QUORUS_PERFORMANCE_VALIDATION_RESULTS.md` | 7.7 KB | C | Stale | **Relabel** it as a Vert.x `executeBlocking` micro-benchmark. Remove the "Phase 4 PostgreSQL" and "quorus-api 7 tests" claims. Its targets do not match the test's assertions. |
| `performance/VERTX5_PERFORMANCE_BENCHMARKS.md` | 7.3 KB | D | Unsupported | **Archive or delete.** It presents the connection-pool numbers relabelled as blocking vs reactive. There is no JMH or `benchmarks/` directory. |
| `testing/FTPS_INTEGRATION_TEST_INVESTIGATION.md` | 12.5 KB | C | Resolved | **Mark resolved and archive.** The fix was delfer vsftpd, dynamic ports and a warm-up delay. |
| `testing/QUORUS_LOG_STYLE.md` | 18.5 KB | C | Partly stale | **Fix.** Use ASCII markers rather than Unicode. Several items log at TRACE, not DEBUG. The real logback file differs, and `-Dtest.loglevel` does not exist. The "all modules" claim is wrong. Remove the personal hostname and username. |
| `testing/QUORUS_NEGATIVE_TESTING_STRATEGY.md` | 6.8 KB | C | Core claim false since 03-12 | **Rewrite.** Negative tests now run by default. Describe `@ExpectsError` / `ExpectsErrorExtension`. |
| `testing/QUORUS_PROTOCOL_SERVERS_TESTING.md` | 13.0 KB | C | Stale | **Fix.** The images and environment variables have changed. The Testcontainers-based tests are not described. `*IT` classes never run under the default Surefire includes. |
| `testing/QUORUS_RAFT_CLUSTER_TESTING.md` | 28.7 KB | C | Partly stale | **Fix.** The `docker build` command fails without `--build-context m2cache`. It uses unprefixed environment names, a wrong default of 3000/500, a non-existent metric and raftlog 1.0. Its PowerShell reads `checks.raft.state`, which the `/health` response does not have. |
| `testing/QUORUS_TESTING_README.md` | 12.0 KB | B− | Partly stale | **Fix.** Its description of consolidated test logging matches the real `logback-test.xml` and parent pom, unlike LOG_STYLE. Its quick-build commands include `-pl …,quorus-api,…`, which fails because the module is not in the reactor. `-Dgroups='!flaky'` refers to a tag no test uses. It says integration tests use the `*IT` suffix without noting that `*IT` classes never run in a default build. Its protocol-server practice predates the Testcontainers-based upload tests. |

---

## 4. Cross-cutting findings

These patterns span several documents. Each has a root cause that is worth fixing once, rather than document by document.

### 4.1 Security status is misreported across the set

**What the code does.** The following are all confirmed:

| Behaviour | Code evidence |
|---|---|
| HTTP requires TLS 1.3 with a client certificate (`ClientAuth.REQUIRED`) | `HttpApiServer.java:250-261` |
| Raft uses mTLS | `GrpcRaftServer.java:102-110`, `GrpcRaftTransport.java:297` |
| Authentication, authorization and audit middleware are installed | `HttpApiServer.java:149-152` |
| Scope and tenant policy is enforced | `AuthorizationPolicyEngine.java:47-113` |
| The audit log is SHA-256 hash-chained and fsync'd, and verified at startup | `HashChainedAuditLog.java:22,87,105` |
| The production profile refuses to start without readable certificates | `SecurityConfig.java:83-100`, called from `QuorusControllerApplication.java:64-66` |

**What the documents say instead:**

| Document | Statement |
|---|---|
| Architecture Spec §14 ARCH-03; §13 "Authentication boundary" | "No authenticated API/agent identity boundary" (Critical); "Blocked by external/built-in authentication integration" |
| REST API Spec §20 API-02 | "No built-in authenticated identity, tenant derivation, or scope enforcement" |
| User Guide, "Tenant Isolation" | "The controller does not authenticate the caller" |
| Architecture Quickstart, line 171 | "The API has no built-in authentication" |
| Cluster Startup Guide, line 13; `docker/README.md`, line 13 | No authentication |
| System Design, Capability Summary (~line 84) and Network Architecture (~line 2428) | "no built-in authenticated identity boundary"; encryption "not complete" |
| Security Deployment Guide §1 | Lists "service-connection governance" as remaining release work, contradicting its own §13–14 and ADR-0004 |

**Why it matters:**

- Operators reading the guides will not expect the fail-closed startup behaviour described in §4.2.
- Reviewers reading the canonical gap tables will under-rate the work already delivered.

**Root cause.** The Phase 1 delivery (2026-09-02) updated the Security Deployment Guide and parts of the Architecture Specification, but not the gap tables or the non-canonical guides. The capability status is stated in too many places.

**Fix.**

- Keep one capability-status table, in Architecture Spec §3.
- In every other document, replace the status text with a one-line pointer to that table.
- Close or narrow ARCH-03 and API-02.

### 4.2 The operator path does not work as written

A new reader following the README hits these failures in order:

1. **Controllers refuse to start.** The steps are `docker compose -f docker/compose/docker-compose-single-controller.yml up` or `-controller-first.yml up`.
   - Packaged `quorus-controller.properties` sets `quorus.security.profile=production`, `quorus.security.http.tls.enabled=true` and an empty `quorus.security.http.tls.certificate=`.
   - No file in `docker/compose/` sets any `QUORUS_SECURITY_*` variable.
   - `SecurityConfig.validate()` therefore rejects the configuration at startup.
   - Only the *test* Compose files include an explicit development opt-in, e.g. `quorus-controller/src/test/resources/docker-compose-3node-durable.yml:22-26`.
2. **The health check fails.** `curl http://localhost:8080/health` would fail even if the controller started, because TLS is on and `/health` is not a public path (only `/health/live`, `/health/ready` and `/api/v1/openapi.yaml` are public: `AuthenticationHandler.java:154-157`).
   - The controller Dockerfile `HEALTHCHECK` has the same defect (`quorus-controller/Dockerfile:99-100`).
3. **The full-network topology has no working agents.**
   - Its agents set `CONTROLLER_URL=http://…` and no tenant variable.
   - The agent fails fast with "Tenant ID not configured" (`AgentConfig.java:114`), and its production profile rejects plaintext URLs.
4. **`docker/README.md` Quick Start uses the wrong port.** It starts `docker-compose.yml`, which maps only ports 8081–8085, then posts to `localhost:8080`.
5. **The HTTPie runbook's first steps return `400`.**
   - `10-register-agent.httpie`, `payloads/agent-register.json` and `20-create-transfer.httpie` omit `tenantId`, which is required (`AgentRegistrationHandler.java:80`, `TransferHandler.java:119`).
   - Every later step depends on these two.
6. **The observability stack receives no controller telemetry.**
   - `docker-compose-observability-cluster.yml:161-165` sets `OTEL_EXPORTER_OTLP_ENDPOINT`.
   - The controller only reads `quorus.telemetry.otlp.endpoint` (`AppConfig.java:229`), so traces go to `localhost:4317` inside the container.
7. **Builds need an undocumented context.**
   - The controller image needs `--build-context m2cache=…` (`Dockerfile:6,38`).
   - `M2_REPO` is commented out in `docker/compose/.env`.
   - None of the Docker guides says it must be set.

Other problems in the same area:

- `docker-compose-corrected.yml` is a leftover copy of the single-controller file.
- `docker-compose-loki.yml` and `docker-compose-observability.yml` clash on container names and ports (3000, 3100, 9090).
- `QUORUS_RAFT_HOST` is set in the Compose files but never read.

**Root cause.** The Compose files were last changed on 2026-09-01, before the security (09-02), configuration (09-03/04) and R4/R5 (09-05) changes. The test fixtures were updated; the user-facing Compose files were not. Four documents describe these files independently, and none of them was re-run.

**Fix.**

- Add an explicit, loudly labelled development profile to every Compose service:
  - `QUORUS_SECURITY_PROFILE=development`
  - `QUORUS_SECURITY_ALLOW_INSECURE=true`
  - TLS disabled
  - the agent equivalents, plus `QUORUS_AGENT_TENANT_ID`
- Separately, provide one TLS-enabled example that uses generated certificates.
- Use `/health/live` for health checks.
- Collapse the four Docker documents into `docker/README.md`, with one table giving each file's topology, host ports, required environment and status.
- Add a CI smoke job that runs `docker compose config` and starts the single-controller topology.

### 4.3 The canonical YAML guide contradicts the parser and engine

| Guide says | Code does | Evidence |
|---|---|---|
| Only `metadata.name` is required; `spec.execution` is optional | The engine calls `parser.validate()` before every execution (`SimpleWorkflowEngine.java:201`), and that requires seven metadata fields (`name`, `version`, `description`, `type`, `author`, `created`, `tags`) with format rules. Separately, `validateSchema()`, which the validation CLI uses, treats a missing `spec.execution` as an error. | `WorkflowSchemaValidator.java:146-158, 283-285`; `YamlWorkflowDefinitionParser.java:425-446` |
| `execution.dryRun` skips transfers; `parallelism` limits concurrent groups | None of `dryRun`, `virtualRun`, `parallelism`, `timeout` or `strategy` is read. The mode comes from which method is called. Groups always run sequentially. | `SimpleWorkflowEngine.java:93-104, ~344-372` |
| Group `retryCount` retries | It is stored but never used | `TransferGroup.java:53` |
| `options` are passed to the protocol adapter | Only source, destination and protocol are passed. Destination is forced to a local `Path`. | `TransferGroup.java:168-174` |
| Nested variables resolve | Resolution is a single pass | `VariableResolver.java:63-78` |
| (not stated) | Unquoted `created` dates become `java.util.Date` under `SafeConstructor` and then fail the ISO check | — |

Every field the guide names does exist in the parser, and the six example YAML files use only supported keys. The problem is semantics and required fields, not vocabulary.

The companion `YAML-VALIDATION-GUIDE.md` gets the required fields right, but says names may contain spaces (`NAME_PATTERN` rejects them: `WorkflowSchemaValidator.java:43,173`). It also describes `kind` deprecation warnings, JSON Schema validation and streaming validation, none of which exist in main code. `workflow-schema.json` is present but never loaded.

**Decision needed.** Either change the documentation to match the code, or implement `execution.*`, `retryCount`, option pass-through and recursive substitution. Record the choice in an ADR. Until then, mark these fields "parsed, not applied" in the guide.

### 4.4 The REST API Specification needs status labels throughout

The endpoint tables are reliable:

- All 51 "Current" rows are registered in `HttpApiServer.java:161-247`.
- The registered router and `quorus-controller-v1.yaml` both contain 52 operations.
- `OpenApiContractTest.java:73` asserts that they are equal.

The problem is the normative sections outside the tables, which carry no status label. The table below lists each one against current code.

| Section | Specification says | Current code |
|---|---|---|
| §3.2 headers | `Idempotency-Key`, `If-Match`, `ETag`, `X-Quorus-Leader`, `X-Quorus-Read-Consistency` | None present. `Retry-After` appears only in `DrainModeHandler.java:76`. |
| §3.4 problem details | `correlationId`, `retryable`, `errors[]` | `type: urn:quorus:problem:…`, `shortCode`, `timestamp`, `requestId`, `traceId` (`ErrorResponse.java:152-166`) |
| §3.5 pagination | `items` / `page` | `assignments`/`total`, `serviceConnections`/`total`, `events`/`total`/`nextCursor` |
| §3.8 follower behaviour | `Retry-After` plus `X-Quorus-Leader` | Neither is sent. The leader ID appears only in the message text. |
| §4.2 scopes | Dotted (`transfers.read`, `routes.manage`) | Colon form (`transfers:read`, `transfers:write`, `security:trust:write`) (`AuthorizationPolicyEngine.java:47-76`) |
| §6.3 states | `SUBMITTED`, `RUNNING`, `SUCCEEDED` | `PENDING`, `IN_PROGRESS`, `COMPLETED`. §3.6 of the same document uses these. |
| §16 error codes | 21 "stable" codes | Only `NOT_LEADER` matches. Code uses `UNAUTHORIZED`, `FORBIDDEN`, `VALIDATION_ERROR`, `TENANT_QUOTA_EXCEEDED` and `NO_LEADER`. `LEASE_EXPIRED`, `STALE_FENCE` and `INVALID_STATE_TRANSITION` are all returned as `CONFLICT`. |
| §20 API-01 | "No canonical OpenAPI 3.1 contract" | `openapi: 3.1.0`, with the equality test above |
| §5 | `GET /api/v1/openapi` is Required | `GET /api/v1/openapi.yaml` is live and public |

**Fix.**

- Give every normative section a Current, Required or Planned label.
- Add mapping tables from `ErrorCode` Q-codes to the target codes, and from colon scopes to the target scopes.
- Generate the "Current" endpoint table from the OpenAPI YAML, as REST §19 already requires.
- Name the OpenAPI file and its route in all three API documents.

### 4.5 Security documents and code: revocation

Two findings affect the Certificate Incident Runbook, which is the document an operator will open under pressure.

1. **Serial matching drops leading zeros.**
   - `CertificateTrustState.java:78` compares against `certificate.getSerialNumber().toString(16)`, which drops leading zeros.
   - The normaliser at `:127-128` only strips `:` and upper-cases.
   - As a result, the documented example `quorus.security.revoked-certificate-serials=01AF44,09BC20` can never match, and nor can openssl's zero-padded serial output.
   - The tests build serials the same way the code does (`SecurityBoundaryIntegrationTest.java:473`), so the defect is invisible to them.
2. **Runtime revocation is per-process and volatile.**
   - `CertificateTrustState` is an in-memory `AtomicReference` created per verticle (`QuorusControllerVerticle.java:80`). It is not replicated through Raft and not persisted.
   - A `PUT /api/v1/security/trust/revocations` to one controller leaves the other controllers accepting the certificate, and a restart reverts the change.
   - The Security Guide §4.2 ("shared by controller HTTP and Raft enforcement"), the Architecture Spec (~line 372) and the runbook §4.2.5 ("already active without restart") all imply cluster-wide effect.
   - ADR-0003's "revocation propagation remains required" is the only accurate statement.

Related limits that no document states:

- Raft has no CRL option (only HTTP: `HttpApiServer.java:259-260`).
- Raft peer authorisation does not bind a certificate subject to a node ID (`RaftPeerAuthorizationInterceptor`). Any certificate issued by the cluster CA can make Raft RPCs.
- Direct mTLS bindings are created with `elevationExpiresAt=null`. Only gateway-asserted identities can therefore perform elevated operations (revocation, service-connection writes, secret-reference writes).

### 4.6 Planning and evidence integrity

1. **History rewrite.**
   - Eight SHAs cited in the plan, register, handover and evidence records (Markdown and JSON manifests) are not ancestors of HEAD or `origin/master`, and no branch or tag names them (`git name-rev` gives `undefined`). The objects still exist locally.
   - Each has a tree-identical commit on master; the mapping is in Appendix A. All eight tree matches were re-checked on 2026-09-25.
   - `temp/pre-claude-rewrite-status.txt` and `temp/post-claude-rewrite-status.txt` (2026-09-11) suggest a rewrite that removed commit trailers.
   - Until refs are added or the citations are updated, clones from `origin` cannot resolve these SHAs.
2. **Uncommitted closures.** These exist only in the working tree:
   - the plan v1.26 and register v1.3 edits;
   - the untracked R1-1 evidence file and its two new test classes;
   - the durable Compose fixture;
   - the `LeaderGuardHandlerTest` fix;
   - the staged `TransferMetrics` deletion.

   HEAD still has plan v1.25 and register v1.1, where R1-1 is 🔴.
3. **Conflicting phase status.**
   - The plan header says "M0 durability and Phase 4 acceptance reopened".
   - Plan §11 says "Complete — delivered on 2026-09-03".
   - The register says "Phases 0, 1 and 4 complete".
   - The reopening is never explained.
4. **Present-tense statements already out of date.**
   - Plan line 150, register lines 119–122 and r1 §3/§10.4 say the container fixture "declares no volumes".
   - r1 §12.1 and the working tree show that it now does.
5. **Stale backlog items.**
   - OBS-04 and OBS-05: the handlers, parser and validator already declare and use loggers.
   - OBS-07: the backlog says 53 DEBUG statements; there are 37.
   - OBS-14: `RaftLogStorageAdapter` already uses `logger`, and `FileRaftStorage` is an external class.

Where a result was checked, it held up. The test counts reconcile between plan and evidence (2,414 → 2,429 → 2,437; controller 597 + 4 = 601; core 1,541 − 24 = 1,517). All 38 cited test classes exist.

### 4.7 Design documents present superseded or target-state material as current

`docs-design/README.md` warns that `design/` is non-normative. The three design documents still undercut that warning. They use present tense, "✅ Complete" tables, and "REAL PRODUCTION CODE" labels on code that no longer exists.

| Document | Most misleading content | Evidence |
|---|---|---|
| System Design | `QuorusStateMachine` appears 16 times, including the "concrete implementation" mapping. | Only `QuorusStateStore implements RaftLogApplicator` exists (`QuorusStateStore.java:60`); the old class was removed in 782a770/722077e. |
| System Design | The controller embeds the workflow engine, `SimpleTransferEngine` and `SimpleTenantService`. | Controller main code imports nothing from `workflow` or `tenant`. `SimpleTransferEngine` is agent-only (`TransferExecutionService.java:77`). |
| System Design | Routes load from YAML at startup; file watchers and a trigger engine run. | No `WatchService` or evaluator exists. Routes are created via REST only (`HttpApiServer.java:241-247`). |
| System Design | Agent-to-agent streaming via `/initiate-transfer`, `/transfer-complete` and similar. | None of these endpoints exists. |
| System Design | Workflow definitions and tenant configuration are Raft state; heartbeats are "gossip". | `RaftCommand` permits no workflow or tenant commands (`RaftCommand.java:44-46`). `AgentCommand.Heartbeat` is a Raft command. |
| System Design | `ELECTION_TIMEOUT_MS`, `HEARTBEAT_INTERVAL_MS`; `AppConfig.get()` singleton; loading from `~/.quorus` and `/etc/quorus`. | The variables are `QUORUS_RAFT_*`. `AppConfig` has a public constructor and no static accessor. The loading order is packaged → profile → environment → overrides (`AppConfig.java:30-50, 300-310`). |
| Raft WAL Design | "No snapshots or compaction"; "raft.log will grow indefinitely"; "Core WAL ✅ Complete". | `FileSnapshotStore`, prefix compaction (`RaftLogStorageAdapter.java:202-207`), and snapshot-first recovery (`RaftNode.java:443-462`). |
| Raft WAL Design | Appendix C: `getDataDir()` with default `data/raft`, and `isSyncOnWrite()`. | `getRaftStoragePath()` with default `./data/raft/{nodeId}`, and `getRaftStorageFsync()` (`AppConfig.java:170-180`). |
| Simulators Design | The simulators "implement the same interfaces as production components". | They are plain classes with their own records, returning `CompletableFuture`. Production returns a Vert.x `Future`. |
| Simulators Design | RaftChaosTest, RaftFailureTest and InfrastructureSmokeTest are "not currently present". | All three are tracked in git and use the simulator. The error was introduced by doc commit 1c24495. |

Also missing from these documents:

- The snapshot configuration keys (`quorus.raft.snapshot.enabled`, `.threshold`, `.check-interval-ms`) and `quorus.raft.storage.path`.
- The InstallSnapshot RPC (`raft.proto:12`).

**Fix.**

- Extract the current material into short reference documents (§10).
- Archive the rest behind a "not implemented as written" banner.
- Add a per-section status badge to anything kept in `design/`.

### 4.8 Material that is not about Quorus, and what it has left behind

**Foreign or obsolete documents** (about 280 KB):

- 10 PeeGeeQ reviews and plans, plus 2 PeeGeeQ-derived guides, in `dev/vertx5-advice/`;
- the APEX refactoring summary in `performance/`;
- three `CONNECTION_POOL_*` documents for an API deleted in 99ead9a on 2026-01-06.

**PeeGeeQ leakage into Quorus code and scripts:**

- `QuorusConfiguration.java:30` ("PeeGeeQ precedence contract")
- `AppConfigNodeIdentityTest.java:82` (`configurationSourcesHavePeeGeeQPrecedence`)
- `VertxPerformanceBenchmark.java:45`
- `scripts/add-license-headers.sh:4,53`
- `scripts/setup-git-hooks.sh:3,12,64-71`, which checks for `peegeeq-api/`, `peegeeq-db/` and `peegeeq-rest/` and so does nothing in Quorus

**Vestigial dependency.**

- `quorus-core/pom.xml:32-36` declares `vertx-pg-client`.
- Its only user is `ConnectionPoolService`, whose only caller is `EnterpriseProtocolExample.java:122`, and that call just prints a message.
- `NOTICE` does not list it.

**Misleading performance figures.**

- The "670,322 ops/s" and "670x" figures time `vertx.executeBlocking(() -> "result")` (`VertxPerformanceBenchmark.java:266-270`), which is a no-op.
- The "+388%" figure came from a synthetic pool benchmark (`Thread.sleep(1)`).
- `VERTX5_PERFORMANCE_BENCHMARKS.md` then relabels those same numbers as blocking vs reactive.

**Fix.** Move the PeeGeeQ material to its own repository. Delete the APEX document. Archive the pool trio and the unsupported benchmark documents. Remove the `vertx-pg-client` dependency and `ConnectionPoolService`, or document why they stay. Scrub the PeeGeeQ wording from code and scripts.

### 4.9 Duplication drives drift

| Content | Where it is repeated | Symptom |
|---|---|---|
| HTTP endpoint list | README, User Guide, Architecture Quickstart, Cluster Startup, Docker Testing README; `InfoHandler` also lists 35 | All five document copies lack the security, service-connection and telemetry routes |
| DNS admission, remotePath encoding, FTPS 21/990, partial updates, event paging | Architecture Spec §10.4, REST §9, API Reference, Security Guide §13–14 | The Service Connection Runbook, which is the operator document, has none of it |
| Compose inventory | README (2 files), Cluster Startup (10), Docker Testing (13), `docker/README` tree (6) | Four inconsistent lists; none flags the `-corrected` leftover or the loki/observability clash |
| Capability status ("routes not autonomous", "no resume", auth) | README, User Guide, Quickstart, Architecture Spec §3, System Design | Authentication status disagrees across them |
| Vert.x 5 migration record | Audit Report, Implementation Plan, Summary, Lessons Learned | "COMPLETE" on 12-17 vs "IN PROGRESS" on 01-08; 388% / 483% / 335,061% |
| Logback test configuration | LOG_STYLE, NEGATIVE_TESTING | Two different samples, neither matching the real `logback-test.xml` |
| Configuration keys and environment variables | Configuration Handover §2.1 and §8 (superseded), Security Guide §4, Service Connection Runbook §2, Quickstart | No canonical configuration reference exists |

### 4.10 Metadata, versioning and hygiene

**Header dates are not maintained.**

- `docs-design/README.md` says 09-01 but its body says 09-07.
- `docker/README.md` says 09-01, but it was last committed 09-05.
- `QUORUS_RAFT_CLUSTER_TESTING.md` says v1.0 / 2026-02-01 but has been edited since.
- The six 2026-09-05 evidence files have no header block.

**Product version numbers disagree:**

| Location | Value |
|---|---|
| pom | `1.0-SNAPSHOT` |
| `HttpApiServer.VERSION`, served by `/api/v1/info` | `1.0.0-alpha` |
| OpenAPI `info.version` | `1.3.2-alpha` |
| `quorus.version` | `2.0-ext` |
| `quorus.agent.version` | `1.0.0` |

The Versioning Policy does not say which is authoritative.

**Status vocabularies differ between documents:**

- Architecture Spec §3: Implemented / Partial / Planned
- REST Spec: Current / Required / Planned
- Architecture Spec §13: seven ad-hoc values
- Gap tables: "Closed" placed in the Priority column

**Personal and machine-specific details appear in documents:**

- `C:\Users\mraysmit\…` paths in the README, plan, Configuration Handover and Raft Cluster Testing guide.
- A real hostname, username and private IP in the `QUORUS_LOG_STYLE.md` environment example.

**Local clutter interferes with review and search.** `temp/`, `.history/` and `hs_err_pid*` are correctly listed in `.gitignore`, so this is a working-copy problem rather than a repository problem:

- `temp/` holds three complete worktree copies, including stale `AppConfig` and `SchemaVersionRegistry` sources, plus about 40 multi-MB logs. Five files under `temp/` are still tracked from before the ignore rule: `build-output.txt`, `ctrl-output.txt`, `sftp-file.txt`, `test-baseline.txt` and `test-output.txt`.
- `.history/` holds about 250 editor snapshots.
- Two `hs_err_pid*.log` JVM crash logs sit at the root.
- About 600 files show as modified only through CRLF churn. `.gitattributes` covers only `*.sh`.

A plain `grep -r` over the working copy returns stale code and duplicated documents.

### 4.11 Link health

This part of the documentation is in good shape. The 71 Markdown files in scope contain 283 relative links (fenced code excluded):

- **1 broken target:** `../../raftlog/pom.xml` in the Configuration Handover, which points into a sibling repository.
- **4 GitHub line anchors** (`#L23`, `#L142`, `#L49-L53`, `#L3642`) in the 2026-08-31 review and the handover. These only resolve in GitHub's source view, not in rendered Markdown.

Some documents mention stale paths as plain text rather than links, for example `docs/design/...` in the Vert.x migration documents. These are covered under the relevant documents.

---

## 5. Status of the 2026-08-31 review findings

`docs/QUORUS_CODEBASE_AND_DOCUMENTATION_REVIEW_2026-08-31.md` has no status follow-up. The table below records the status against HEAD `216348a` plus the working tree. Append it to that review, or link to it from there.

| ID | Finding | Status | Evidence |
|---|---|---|---|
| QR-01 | Agent skips the `IN_PROGRESS` transition | **Fixed** | `QuorusAgent.java:433-441` reports `ACCEPTED`, then `IN_PROGRESS`, before executing. Arch Spec §13 still lists this as a gap. |
| QR-02 | Default Raft storage path is outside the persisted volume | **Fixed for containers** | The Dockerfile and Compose files set `QUORUS_RAFT_STORAGE_PATH=/app/data/raft`, and a blank value now means "use the default". The code default is still the relative `./data/raft/{nodeId}`. The R1-1 container evidence has not been committed. |
| QR-03 | SFTP host-key verification disabled | **Partly fixed** | Governed SFTP requires SHA-256 pins, and production rejects non-governed jobs. Direct URIs (library and development use) still set `StrictHostKeyChecking=no` silently (`SftpTransferProtocol.java:406-408`). |
| QR-04 | Advertised Compose deployments are obsolete or incomplete | **Open, changed form** | Controller variables now use `QUORUS_*` names. However, the Compose files now fail production security validation, and the full-network agents have no tenant (§4.2). |
| QR-05 | Agent tests do not compile | **Fixed** | `failMessage` is no longer present in `quorus-agent/src/test`. |
| QR-06 | Assignment creation lacks referential and tenant validation | **Fixed** | The handler checks at `JobAssignmentHandler.java:95-118`. On apply, `QuorusStateStore.validateAssignmentReferences` checks at `:1096-1113` and is called on assign (`:444`) and on every transition (`:509-590`). Arch Spec ARCH-06 overstates the remaining gap. |
| QR-07 | Assignment transition checks are not atomic with state application | **Fixed (not re-tested)** | The handler passes `expectedStatus` (`JobAssignmentHandler.java:251`), and the state store validates transitions on apply. |
| QR-08 | HTTP transfers buffer whole files in the heap | **Open** | `HttpTransferProtocol.java:199-250` buffers `response.body()` and then writes it, with files allowed up to 10 GB (`:71`). The Arch Spec ARCH-09 statement is accurate. |
| QR-09 | HTTP bind-host setting ignored | **Fixed** | `HttpApiServer.java:269` calls `listen(port, host)`. |
| QR-10 | RocksDB documented but rejected at startup | **Fixed** | The backends were removed, and validation accepts only `raftlog`. The `AppConfig.java:160` Javadoc still mentions `memory`. |
| QR-11 | Broken links and stale guidance | **Partly fixed** | Copilot instructions were updated (per the handover's F8). Much of the guidance is still stale (this report). Relative links are healthy: 1 of 283 is broken (§4.11). |

---

## 6. Code and configuration defects found during the review

These defects are in code or configuration, not only in documentation. They came up while checking documentation claims, and each one was re-checked against the source on 2026-09-25.

| # | Severity | Location | Defect | Suggested fix |
|---|---|---|---|---|
| 1 | High | `CertificateTrustState.java:78,127` | Serial comparison uses `BigInteger.toString(16)`, which drops leading zeros. Configured or PUT serials with a leading zero never match. | Compare `BigInteger` values, or strip leading zeros on both sides. Add a test using an openssl-formatted serial. |
| 2 | High | `QuorusControllerVerticle.java:80`; `CertificateTrustState` | Runtime revocation is node-local and lost on restart. | Replicate the revocation set through Raft and persist it, or document the limitation in the runbook and the Security Guide. Record the decision in an ADR. |
| 3 | High | `docker/compose/*.yml`; `quorus-controller.properties` | Every published topology that includes a controller starts it in the production profile with TLS enabled and no certificates, so startup fails. The full-network agents have no tenant. | Add an explicit development opt-in to each service, plus one TLS example. |
| 4 | Medium | `quorus-controller/Dockerfile:99-100` | `HEALTHCHECK` sends plaintext `curl http://…/health` to a TLS, authenticated endpoint. It always fails in production. | Use `/health/live` over the configured scheme, with TLS handling. |
| 5 | Medium | `HttpTransferProtocol.java:71,199-250` | The HTTP adapter buffers the whole body, up to 10 GB (QR-08). | Stream to `AsyncFile`, for example with `BodyCodec.pipe`. |
| 6 | Medium | `SftpTransferProtocol.java:406-408` | Direct-URI SFTP connections turn off host-key checking without logging a warning. | Log a WARN, and refuse unless an explicit development flag is set. |
| 7 | Medium | `RaftPeerAuthorizationInterceptor` | The Raft peer certificate is not bound to a node ID. Any certificate from the cluster CA can make Raft RPCs. | Bind the certificate subject or SAN to the `QUORUS_CLUSTER_NODES` identity. |
| 8 | Medium | `SecurityConfig` parseBindings; `AuthorizationPolicyEngine.requiresElevation` | Direct mTLS identities can never hold elevation. Only gateway-asserted identities can revoke certificates or write connections and secrets. | Document this, or add a direct-binding elevation mechanism. |
| 9 | Medium | `AuthorizationPolicyEngine.java:78-107` (`roleAllows`) | Each role branch returns immediately, so only the first matching role is evaluated. A multi-role identity such as {SECURITY, OPERATOR} is denied `transfers:*` unless that scope is also granted explicitly. | Evaluate the union of all roles, and add a test. |
| 10 | Medium | `quorus-controller-v1.yaml:1105,1130-1132` | The OpenAPI `AgentStatus` enum does not match the code's 11 values. The `port` minimum of 1 conflicts with the documented default of 0. | Generate the enum from the code, or fix it by hand. Settle the `port` rule. |
| 11 | Medium | `JobAssignmentService` | No main-source code constructs it; only `JobAssignmentServiceTest.java:67` does, so the runtime never creates it. It owns the timeout monitor relevant to P2-01 (lease expiry). | Confirm this. Then wire the service in, or document where assignment timeouts are handled. |
| 12 | Low | `observability` Compose files | Set `OTEL_EXPORTER_OTLP_ENDPOINT`, which is never read. `QUORUS_RAFT_HOST` is also never read. | Use `QUORUS_TELEMETRY_OTLP_ENDPOINT`, and remove the dead variables. |
| 13 | Low | Version constants | pom `1.0-SNAPSHOT`, `HttpApiServer.VERSION` `1.0.0-alpha`, OpenAPI `1.3.2-alpha`, `quorus.version` `2.0-ext` and agent `1.0.0` all disagree. | Take a single version from the pom, via resource filtering. |
| 14 | Low | `InfoHandler` (~line 125) | The `/api/v1/info` endpoint list leaves out 17 security and connection routes. | Derive the list from the router or from OpenAPI. |
| 15 | Low | `quorus-core/pom.xml:32-36`; `ConnectionPoolService` | `vertx-pg-client` is an unused dependency. The `vertx-grpc-*` dependencies are declared but not imported in controller main (Raft uses grpc-java). | Remove both, or document why they stay. |
| 16 | Low | `AgentConfig.java:174`; `LayeredProperties` | The foreign-assignment threshold accessor defaults to 3, but the packaged value is 1. Invalid numeric values fall back silently. A blank environment value cannot clear a packaged one. `QuorusConfiguration` reads `System.getenv()` directly (`:211`). None of these are in the register. | Add them to the register, then fix. |
| 17 | Low | `quorus-controller/pom.xml:263` | The comment says `mvn test -Dgroups=docker,slow`, which does not work while `excludedGroups=docker,slow` still applies. | Correct the comment. The command in DOCKER_TEST_PERFORMANCE is right. |
| 18 | Low | poms | There is no Failsafe plugin and no `<includes>`, so `*IT` and `*Benchmark` classes never run in a default build. | Add Failsafe, or rename these classes and tag them. |
| 19 | Low | `SimpleWorkflowEngine.java:88-89` | A public, non-deprecated constructor calls `Vertx.vertx()`. | Deprecate it, or remove it. |
| 20 | Low | Javadoc: `AppConfig.java:160`, `HttpTransferProtocol.java:58` | They still mention the `memory` storage type and a "blocking mode", neither of which exists. | Update the Javadoc. |
| 21 | Low | `quorus-workflow/src/main/resources/schema/workflow-schema.json` | Present but never loaded, although `json-schema-validator` is a dependency. | Use it, or remove both. |
| 22 | Low | `QuorusConfiguration.java:30`, `AppConfigNodeIdentityTest.java:82`, `VertxPerformanceBenchmark.java:45`, `scripts/setup-git-hooks.sh`, `scripts/add-license-headers.sh` | PeeGeeQ references. The git-hooks script checks PeeGeeQ paths. | Scrub the references and fix the hook script. |
| 23 | Low | `docker/compose/docker-compose-corrected.yml`; `-loki` vs `-observability` | A leftover file, and container-name and port collisions between two topologies. | Delete the leftover; rename the containers or document the conflict. |

---

## 7. Detailed findings by area

Findings already covered in §4 are only referenced here. Each issue is tagged **H**, **M** or **L** for severity.

### 7.1 Canonical architecture and API contracts

#### Architecture Specification (v2.7)

Strengths:
- The capability table has explicit Implemented / Partial / Planned definitions. Most rows match the code: the adapter set (`ProtocolFactory.java:67-94`), no route trigger evaluator, no S3 adapter, and static Raft membership.
- The release gates in §13 are measurable, for example "1,000 consecutive…" and "2 × election timeout + retry interval".
- The delivery-semantics section (§6.2–6.4) is candid: "MUST NOT claim exactly-once".
- The §15 decisions match the leader-guard behaviour.

Issues:
- **H** — §13 and §14 have not caught up with delivered work:
  - ARCH-03 and the §13 authentication gate: see §4.1.
  - The §13 "End-to-end lifecycle: Not achieved; `IN_PROGRESS` gap" entry: QR-01 is fixed.
  - ARCH-12 and §12.2 say the job model lacks operational context. `TransferJob.java:81-90` already accepts `businessService`, `owner`, `criticality`, `expectedStartAt`, `requiredCompletionAt`, `runbookUrl` and `labels`. Only an escalation policy is missing.
- **M** — The §3 telemetry row says "the first ordered submission event is queryable". §12.3 and the code have five events (`SUBMITTED`, `ASSIGNED`, `ACCEPTED`, `STARTED`, `PROGRESS`) and a `STALLED` boundary (`TransferProgressHandler.java:153-155`).
- **M** — ARCH-06 overstates the gap. Apply-time reference and tenant checks exist (§5, QR-06).
- **M** — §10.4 says insecure development behaviour must be "visibly logged". The SFTP direct-URI path fails open silently (§6 #6).
- **L** — Some requirements cannot be tested as written: "in real time", "enough samples", "configurable cadence appropriate to its criticality", and "the documented durable default" (not defined anywhere; the code default is relative). §5.5 ties release to a transient "reopened remediation checkpoint".
- **L** — Table hygiene. ARCH-01 is missing and the IDs are out of order. "Closed in Phase 4" appears in the Priority column. §7 opens with a paragraph about status replay before its own introduction.

#### REST API Specification (v2.4)

Strengths:
- Every endpoint row carries a state.
- All 51 "Current" rows are accurate.
- The "current implementation boundary" notes in §6.2, §6.5 and §9 are useful.
- The §3.6 retry contract matches the agent: three sends, and retries on 408, 429 and 5xx (`JobStatusReportingService.java:157,174-185`).

Issues (in addition to §4.4):
- **M** — The purpose text for `DELETE /transfers/{id}` says it "returns the resulting transfer or operation". The code returns `{jobId, message}` (`TransferHandler.java:397-409`).
- **M** — The §6.1 events row ("initial ordered submission-event ledger") contradicts §6.5, which lists five events.
- **L** — Path parameters are inconsistent: `{transferId}` against the code's `:jobId`, and `{serviceConnectionId}` against `{connectionId}`.
- **L** — The "Current" purpose text claims agent "search" and route "conditional update". Neither exists; there is no ETag or If-Match support.
- **L** — §3.1 says unknown fields are rejected. Agent registration ignores them (`AgentInfo.java:36`, `@JsonIgnoreProperties(ignoreUnknown = true)`).

#### HTTP API Reference (v3.8)

Strengths:
- Every documented endpoint is registered.
- The tenant-isolation rules match the handlers (`HeartbeatHandler.java:92-99`, `JobStatusHandler.java:112-119`, `AgentRegistrationHandler.java:83-89`).
- The configuration keys and defaults it cites are correct: DNS admission 8 / 5000 ms, and the telemetry windows.

Issues:
- **M** — 13 live routes have no entry: `GET /api/v1/openapi.yaml`, plus 12 service-connection, secret-reference and security-event routes. These are covered only by one prose paragraph. The validate request body, including `probeTimeoutMillis` (clamped to 100–10,000, default 3,000), is undocumented.
- **M** — The heartbeat section:
  - lists `BUSY`, which does not exist (`AgentStatus.java:65-129`);
  - does not say that an invalid status is silently ignored (`HeartbeatHandler.java:104-110`);
  - shows `"ACTIVE"` in the example, but the code returns lowercase `"active"`;
  - omits the `message` field.
- **M** — Registration omits `agentPool` and `networkZone` (`AgentInfo.java:78-82`). The doc gives the `port` default as 0, while OpenAPI requires a minimum of 1.
- **M** — The progress condition list includes `AT_RISK`, which the code never produces (`TransferProgressHandler.java:146-163`).
- **M** — There is no error-envelope or `ErrorCode` section, although the reference cites `VALIDATION_ERROR`, `CONFLICT` and others.
- **L** — The request tables mark optional fields as required (`tenantId`), leave out transfer `metadata`, `runbook` and `labels`, and do not document the assignment or route request bodies. They also do not say that `POST /security/authorization/check` is leader-only, which follows from `LeaderGuardHandler` guarding every POST.

### 7.2 User, operator and repo-meta documents

**README.md.** Accurate module table, protocol list and stated limits. However:
- The quick start fails (§4.2) (**H**).
- The example workflow fails the validator (§4.3) (**H**).
- `mvn clean verify` does not install modules, so `-pl quorus-integration-examples exec:java` cannot resolve `quorus-core` (**M**).
- It hard-codes a personal `JAVA_HOME` and gives PowerShell only (**L**).

**User Guide.**
- The tenant enforcement table and the pause/resume statements are accurate.
- It is wrong on authentication (**H**).
- It lists progress, events and attempts as gaps (**M**).
- It says `AGENT_TENANT_ID` takes priority. It is a legacy alias, and `QUORUS_AGENT_TENANT_ID` wins (`AgentConfig.java:46-52`) (**M**).
- It never mentions that the agent defaults to the production profile with TLS (**M**).
- It has no NFS section (**L**).

**YAML Syntax Guide.** See §4.3. Also:
- The precedence list leaves out runtime `ExecutionContext` variables. `spec.variables` override those (`SimpleWorkflowEngine.java:217-219`) (**L**).
- The legacy root-level form passes `parse()` but fails `validateSchema()` (**L**).
- It places `batchSize` in the wrong example file. `ecommerce-order-processing.yaml` is missing from the examples table, and the `file` protocol and `mode` option used in `data-pipeline.yaml` are not listed (**L**).

**Workflows README.** Its field lists match the parser exactly. It gives no required-field rules and presents `parallelism` and `timeout` as if they have an effect (**M**).

**YAML-VALIDATION-GUIDE.** See §4.3. Also, the `java WorkflowValidationCLI …` command gives no package or classpath (**L**).

**Integration Examples README.**
- Its class list matches the source; only `IntegrationTestSuite` is missing.
- Its run command needs a prior `mvn install` (**M**).
- It does not mention the pom's default `mainClass`, `SftpFtpRealImplementationDemo` (**L**).

**Architecture Quickstart.**
- All ten `AppConfig` defaults are correct (`AppConfig.java:122-295`).
- It says there is no authentication (**H**).
- Its HTTP surface list is incomplete (**M**).
- It does not state the packaged defaults `127.0.0.1` and production profile (**M**).
- It says the pom sets `maven.compiler.source/target`. It actually sets `maven.compiler.release` (**L**).

**Cluster Startup Guide, Docker Testing README, docker/README, DOCKER_BUILD_OPTIMIZATION.** See §4.2 for the failing procedures.
- `docker/README` mixes `docker-compose` with `docker compose`, and its last link label does not match its target (**L**).
- `DOCKER_BUILD_OPTIMIZATION`:
  - describes a single `/root/.m2` cache mount, where the Dockerfile uses per-namespace mounts plus an `m2cache` context (**M**);
  - says raftlog-core comes from `dev/mars`, but its groupId is `io.github.mraysmit` (**L**);
  - gives performance figures with no measurement behind them (**L**).

**HTTPie RUNBOOK.txt.**
- It has no header, owner or date.
- `32-update-assignment-status.httpie` exists but the runbook never references it (**L**).
- See §4.2 for the `400` errors.

**NOTICE.** See §3.1. It also refers to a "licenses directory in this distribution", which has not been confirmed to exist (**L**).

**OPEN_SOURCE_USAGE.md.**
- It groups runtime and test dependencies correctly, and its Vert.x, Jackson, gRPC, OpenTelemetry, logging and test-framework versions match the poms.
- It gives RaftLog Core as 1.1.0; `pom.xml:32` has 1.2.0 (**M**).
- It still lists RocksDB JNI 9.11.2, although no pom declares `rocksdbjni` (**M**).
- It omits `javax.annotation-api` (declared in `quorus-controller/pom.xml`, CDDL/GPL+CE) (**M**).
- It lists the Vert.x gRPC server and client, which are declared but unused (§6 #15) (**L**).
- Its sample `license-maven-plugin` configuration references `LICENSE-HEADER.txt`, which does not exist. The header scripts it names (`scripts/update-java-headers.ps1`) do exist (**L**).
- NOTICE and OPEN_SOURCE_USAGE disagree with each other: `vertx-pg-client` appears in one and not the other, and test scope is handled differently. Keep one inventory and generate the other, or merge them (**M**).

**.github/copilot-instructions.md.** This file is loaded into AI coding assistants, so each stale line is repeated into new code and documents.

Strengths:
- The configuration section (precedence order, no `-D` system properties, per-instance config classes) matches `AppConfig`, `AgentConfig` and `QuorusConfiguration`.
- The Raft storage rule ("only the external `raftlog-core` … do not add internal WAL, RocksDB or memory backends") is current.
- The "Code Validation Requirements" and "Process Rules" sections set a high evidence bar that this review agrees with.

Issues:
- **H** — "Key Files" presents `docs-design/design/QUORUS_SYSTEM_DESIGN.md` as the "comprehensive architecture documentation". That is the grade-D document in §4.7. It should point to `docs/QUORUS_ARCHITECTURE_SPECIFICATION.md` and the other canonical documents listed in `docs-design/README.md`.
- **M** — It says "`QuorusStateMachine` applies committed log entries". No such class exists in main code; `QuorusStateStore` implements `RaftLogApplicator`.
- **M** — The module table lists `quorus-api` (the directory holds only `target/` and no tracked files) and omits `quorus-integration-examples`. The `quorus-core` entry lists HTTP/FTP/SFTP/SMB but not FTPS or NFS.
- **M** — It says SFTP, FTP and SMB adapters "use WorkerExecutor". The only `WorkerExecutor` mention in main code is a comment in `TransferExecutionService.java:235`. Blocking adapters run through `transferReactive()` and `executeBlocking`.
- **M** — The agent lifecycle lists `POST /agents/{id}/status` and `DELETE /agents/{id}`. The real routes are `POST /api/v1/jobs/:jobId/status`, and there is no deregistration route (`HttpApiServer.java:205-237`). The registration fields it lists (region, datacenter, protocols, capacity) omit the required `tenantId`.
- **M** — The workflow YAML example has only `name` and `version` in `metadata`, so it fails the engine's validation (§4.3). Assistants will copy it.
- **M** — The "Docker testing" commands start `docker-compose.yml`, which fails production security validation (§4.2).
- **M** — The test rules forbid `CompletableFuture`, `ExecutorService`, `CountDownLatch` and bare `Thread.sleep` in tests with "zero tolerance". The test tree still has `CompletableFuture` in 9 files, `ExecutorService`/`Executors.new*` in 14, `CountDownLatch` in 9 and `Thread.sleep` in 26; some `Thread.sleep` uses may be the allowed kind inside `executeBlocking`. Either finish the migration or state the rule as the target.
- **L** — The generated-classes list omits the InstallSnapshot messages. The example SFTP image (`atmoz/sftp`) is not what the tests use.

### 7.3 Security, configuration, ADRs and reference policies

**Security Deployment Guide (v1.6).** Strengths:
- Every `quorus.security.*` key in §4 exists in the properties file and in `SecurityConfig.from()`.
- The fail-closed checks and the audit-chain description are exact.

Issues (in addition to §4.5):
- **M** — §11 still says the container-recreation gates "remain required". The register closed R1-1 on 2026-09-07, although that closure is uncommitted.
- **L** — §4.1 marks `X-Quorus-Roles` and `X-Quorus-Scopes` as required. A missing header is treated as an empty set (`AuthenticationHandler.java:131-133`).
- **L** — §8 does not list `/api/v1/openapi.yaml` as unauthenticated.
- **L** — §5 uses the legacy `CONTROLLER_URL` name.
- **L** — The "audit path configured" and "trust-bundle version" production checks are always satisfied by packaged defaults (the relative path `./data/audit/...` and the version `configuration`).
- **L** — §11–14 are release notes and go beyond the document's stated scope.

**Certificate Incident Runbook (v1.1).** Strengths: clear triggers; a quorum-preserving one-node-at-a-time restart (§4.4); an honest "no hot reload" statement; a concrete list of closure evidence.

Issues:
- **H** — §4.1, §4.2 and §4.4 depend on the revocation behaviour in §4.5. The runbook must say:
  - PUT the revocation to every controller;
  - add the serial to configuration before any restart;
  - use the zero-stripped serial format until the code is fixed.
- **L** — It gives no example request body or elevation header, and does not explain how to start a new audit chain (by repointing `quorus.security.audit.evidence-path`).

**Service Connection Operations Runbook (v1.0).**
- All eight environment variables are correct, and the two-sided enforcement model is explained well.
- It is missing the R4 and R5 behaviour and the elevation requirement (**M**).
- It says agent pool and roots "must" be set, but nothing enforces that; they default to `default` and relative paths (**L**).

**Configuration Isolation Handover (v1.9, "Superseded").**
- Its §2.1 precedence table and §2.2 migration tables are the best description of the configuration contract in the repository.
- It sits among the canonical documents despite being superseded (**M**).
- It has three dated status layers, one of which contradicts the layer above it (**M**).
- It cites six orphaned SHAs (**M**).
- It records verification on JDK 26.0.1 and Maven 3.9.16, whereas the baseline is JDK 25 and Maven 3.9.11 (**L**).
- Handover findings F1 to F8 are fixed.
- Four residuals are untracked (§6 #16).

**ADRs 0001–0005.**
- All five have Context, Decision, Status and Consequences.
- ADR-0002 is stale on fencing (**M**).
- None has an Alternatives section, and there is no index or supersession field (**L**).
- These decisions have no ADR (**M**):
  - external raftlog-core as the only WAL, with a Quorus snapshot sidecar;
  - layered configuration with the `-D` channel removed;
  - the schema-3 coordinated upgrade with no mixed versions;
  - runtime trust-state scope;
  - Raft transport over grpc-java rather than Vert.x gRPC.

**Reproducible Build and Evidence.**
- Confirmed: `.java-version` 25, CI on `maven:3.9.11-eclipse-temurin-25`, two clean `verify` builds in `quorus-ci.yml`.
- There is no `project.build.outputTimestamp`, so artifacts are not byte-reproducible (**M**).
- The evidence figures are frozen at M0 (2,212 tests); R6 had 2,437 (**L**).

**Versioning and Compatibility Policy.**
- Schema versions are wrong: it says 1, the code says 3 (**H**).
- It does not record the configuration-contract break (**M**).
- It has no product-version rule (**M**).

### 7.4 Plan, register and evidence

See §4.6 for the cross-document problems. Additional points:

- **Plan.**
  - The §6.1 TDD protocol is rigorous, and every checkpoint cites evidence with reconciled counts.
  - The remediation checkpoint (lines 62–191) is a reverse-chronological append log that keeps superseding itself. Line 116 is a single line of about 900 characters and includes a machine path (**M**).
  - Line 384 still says coverage "exercises … RocksDB storage" (**L**).
  - Phases 5–12 have no `**Status:**` line (**L**).
  - The plan's §23 requires a revision history, but the plan has none (**M**).
- **Register.**
  - It has good IDs, a clear status vocabulary, dated verification and gap-to-section traceability. The Phase 2 and Phase 3 open lists match the plan one for one.
  - Its revision history is out of order (1.0, 1.3, 1.2, 1.1) (**L**).
  - DEF-09 still asks for a change that DOC-06 records as applied (**L**).
  - OBS-08 is marked closed on the strength of a deletion that is only staged (**L**).
  - The OTel plan is cited as both v2.5 and v2.6 (**L**).
  - Three plan items have no register ID (**M**): the durable agent-report outbox (plan line 188), two Raft regression cases needing disposition (line 157), and the persistent-environment inventory (line 125).
- **Evidence.**
  - **r4** is the strongest record: reconciled counts, and code claims that check out.
  - **r6** has an exact command, log SHA-256, image digest and an isolated worktree, but its revision is orphaned.
  - **r1** is honest (a negative-control test, and "no product defect found"), but it is untracked and has no revision identity, which the plan's §6.1 requires (**H**).
  - **remediation-r4-r6** and the **raftlog handover** need supersession pointers.
  - The 2026-09-05 files have no header block (**L**).

### 7.5 Design documents

Covered in §4.7. Further detail:

- **System Design** (4,651 lines):
  - The Security Architecture section appears twice (lines 3520 and 4080).
  - Configuration Management, Monitoring, Scalability and Performance Optimisation are each repeated.
  - The controller-first description appears at both line 373 and line 663.
  - Figure 6 uses an undeclared `REPO` participant.
  - The election diagrams show 5 nodes while the text describes 3.
  - The Data Isolation SQL mixes MySQL `INDEX` syntax with PostgreSQL `CREATE POLICY`.
  - The Kubernetes example uses a `Deployment` with `replicas: 3` and a `:latest` image for static-membership Raft nodes.
  - YAML examples put an `Authorization: ${AUTH_TOKEN}` header and `s3://` targets into workflow definitions, against the document's own secrets rule.
  - The tech-stack versions are stale: JUnit 5.10.1 (actually 5.14.3), Testcontainers 2.0.2 (actually 2.0.3), and raftlog is not mentioned.
  - The health JSON, metric names and "Circuit Breaker Pattern: Implemented" do not match the code.
  - After trimming, the useful remainder is estimated at 60–80 KB.
- **Raft WAL Design:**
  - The §16 code uses method names that no longer exist (`persistTermAndVote`, `truncateFrom`, `appendBatch`, `AppendPlan`). The real interface is `updateMetadata`, `appendEntries`, `truncateSuffix` and `sync` (`RaftStorage.java:89-162`).
  - §13.9's soft limit, `LogCapacityExceededException`, utilisation metric and follower NACK are not implemented. Only a leader-side hard limit exists (`RaftNode.java:645-649`).
  - The §15.6 and C.3 listings use the Vert.x 4 `executeBlocking(Handler<Promise>…)` form.
  - §16.1 cites raftlog 1.1.0; the project uses 1.2.0.
  - The D.2 comment says "JRE 21" directly above a `25-jre` image.
- **In-Memory Simulators Design:**
  - Only `InMemoryTransportSimulator` exercises product code, and 33 controller test classes use it.
  - The six `quorus-core` simulators are exercised only by their own tests.
  - `MockRaftTransport`, which three files use, is undocumented.
  - The RaftTransport listing is stale: `stop()` now returns `Future<Void>`, `sendInstallSnapshot` exists, and `HttpRaftTransport` does not.
  - The protocol builder DSL, the `agent.withTransferEngine` wiring and the full-stack examples are proposals, not implemented features.
  - Appendix D's backlog was partly delivered (the FS simulator now has 88 tests), but its boxes remain unticked.

### 7.6 `docs-design/dev/`

Covered in §3.7 and §4.8. Further points:

- **`prompts.txt`** is a collection of AI-assistant prompts and "lessons learned" notes for the APEX rules-engine project: test-writing instructions, YAML validation methodology, error-handling principles and anti-hallucination rules for APEX YAML. It mentions APEX 53 times and Quorus not at all. A pattern scan (passwords, secrets, tokens, API keys, private keys, cloud key formats, IP addresses, e-mail addresses, Windows user paths, JDBC URLs, URLs) and a read-through found nothing sensitive. It belongs with APEX, or in personal notes.
- The migration is described as "Vert.x 4.x → 5.x". Before `9402f65` (2025-12-16), the root pom had no Vert.x at all; Vert.x 4 came in only through Quarkus in `quorus-api`. The work was therefore Vert.x 5 adoption plus Quarkus removal.
- The Audit Report justifies removing locks from `SimpleTenantService` because it is "deployed as a Verticle". It is not. It is only instantiated in `MultiTenantExample.java:79`.
- Code still contains Vert.x 4 style that the guides discourage:
  - `QuorusControllerVerticle` uses `AbstractVerticle.start(Promise)`, which is legal in Vert.x 5;
  - there are eight `.onComplete(ar -> …)` calls;
  - `QuorusAgent.java:372` blocks with `.join()`. Whether that can run on an event loop has not been checked.
- None of `CompositeFuture`, the old `executeBlocking(promise -> …)` signature, `ScheduledExecutorService` or virtual threads were found.
- Broken internal links point to `docs/design/...`, and the Implementation Plan cites a non-existent `docker/agents/src/...` path.
- The Patterns Guide has an empty "Key Takeaways" heading, orphaned "### 3." and "### 4." sections, and two "Configuration Best Practices" sections. The Migration Guide has two §7s and no §9.

### 7.7 Performance and testing

Covered in §3.8 and §4.8. Further points:

- **DOCKER_TEST_PERFORMANCE** is the one performance document worth keeping. C1–C2 and P1–P7 are all confirmed in code, and its measurements are dated with the environment stated.
- **LOG_STYLE:**
  - The extension, logger name, MDC keys and stack-trace policy match the code; 66 `debug("Stack trace…", err)` calls and no `error(…, e)` calls were found.
  - The `methodName:` prefix convention is followed in the simulators and in 32 of 54 `SimpleTransferEngine` log calls, but in 0 of 107 in `RaftNode`, 0 of 55 in `QuorusAgent` and 0 of 23 in `SimpleWorkflowEngine`.
- **NEGATIVE_TESTING:**
  - The `errorhandling/` package, the 27 `@Tag("negative")` tests and both profiles are correct.
  - `<excludedGroups>negative</excludedGroups>` was removed in `8864c2f` on 2026-03-12. As a result, `-Pall-tests` now does the same as the default build.
- **PROTOCOL_SERVERS:**
  - `ProtocolServersLifecycleIT` has the five methods listed.
  - The FTP image is now `delfer/alpine-ftp-server` and the SMB image is `ghcr.io/servercontainers/samba`, with ports bound to `127.0.0.1`.
- **RAFT_CLUSTER_TESTING:**
  - The load balancer container is named `quorus-loadbalancer`, not `nginx`.
  - It does not mention the automated JUnit Docker suites.
  - Its PowerShell scripts read `checks.raft.state` from `/health` (lines 428, 441, 534). The response has `raft` as a top-level object (`HealthHandler.java:103-117`), so the scripts read nothing (**M**).
- **QUORUS_TESTING_README:**
  - The consolidated-logging section is accurate: a shared `testRunTimestamp` from the parent pom, `../test-logs/quorus-test-${testRunTimestamp}.log`, `prudent` and `append` mode, and `test-logs/` in `.gitignore`. It is the document LOG_STYLE and NEGATIVE_TESTING should link to instead of carrying their own logback samples.
  - Its examples (`RaftNodeTest`, `DependencyGraphTest`, `InfrastructureWithTelemetryTest`, `ProtocolServersLifecycleIT`) all exist.
  - "Quick Build Without Tests" uses `-pl quorus-core,quorus-controller,quorus-api,quorus-agent`, which fails because `quorus-api` is not in the reactor (**M**). The consolidated-log module list also still includes `quorus-api` (**L**).
  - "Excluding Flaky Tests" uses `-Dgroups='!flaky'`, but no test carries a `flaky` tag (**L**).
  - The test-classification table says integration tests use `*IT.java`. Without Failsafe or `<includes>`, those classes never run in a default build. Most real integration tests (for example `FtpUploadIntegrationTest`) are named `*Test` (**M**).
  - "Protocol server tests use externally-managed Docker Compose" describes `ProtocolServersLifecycleIT` only. The FTP, FTPS and SFTP upload tests use Testcontainers-managed Compose files through `SharedTestContainers` (**L**).
- **Test inventory:**
  - JUnit 5.14.3, Testcontainers 2.0.3, Surefire 3.2.2, JaCoCo 0.8.15.
  - The `docker` tag is on 5 classes, all in `quorus-controller/raft`. The `slow` tag is on 3 classes; `negative` on 4 files.
  - `quorus-core` has 1,125 test-method annotations.
  - Six Testcontainers-based tests carry no `docker` tag and run in default builds: the FTP, FTPS, SFTP and SFTP-abort upload tests, `AgentTelemetryIntegrationTest` and `InfrastructureWithTelemetryTest`.
  - `ContainerRecreationDurabilityTest` and `docker-compose-3node-durable.yml` appear in no testing document.

---

## 8. Project state as the documents describe it

This summary is drawn from the plan (v1.26) and the register (v1.3), both uncommitted, and checked against code where possible.

- **Roadmap shape.** 13 phases (0–12) grouped into milestones M0–M5, with no calendar dates. No milestone is claimed to be production-ready.
- **Phase 0 (baseline).** Complete, with an approved TDD process deviation (2026-09-02). Its durability acceptance was reopened by R1.
- **Phase 1 (identity and trust).** Complete (2026-09-02):
  - TLS 1.3 on HTTP, and mTLS for Raft and agents;
  - scope and tenant policy;
  - revocation (node-local; see §4.5);
  - hash-chained audit.

  Corporate PKI accreditation is deferred to Phase 12.
- **Phase 4 (governed service connections and secrets).** Delivered 2026-09-03: Vault KV v2, egress policy, DNS pinning, and SFTP host-key pins. Its status is contradictory, because the plan header says "acceptance reopened" (§4.6).
- **Phase 2 (attempts and fencing).** In progress. Delivered: attempts, fencing, atomic reporting and the attempt APIs (confirmed in code). Ten register items (P2-01 to P2-10) remain open, including:
  - lease expiry and reassignment;
  - idempotency keys (confirmed absent);
  - retry policy;
  - integrity, staged publication and reconciliation. P2-06, P2-07 and P2-08 are release blockers.
- **Phase 3 (operations telemetry).** In progress. The progress API, five lifecycle event types and the stall boundary are delivered and confirmed. Twelve register items (P3-01 to P3-12) remain open: the full event vocabulary, alerts, SSE, timelines, retention and SLA reports. There is no COMPLETED or FAILED event yet.
- **Phases 5–12.** Not started. Phases 5, 6, 8 and 12 are flagged 🔴.
- **Remediation R2–R6.** Complete. R6 local acceptance: 2,437 tests, 0 failures, 2 explicit skips and five JaCoCo gates, at the orphaned `b604505`, which is tree-identical to `dc447d4`.
- **Storage.** The external `raftlog-core` 1.2.0 is the only WAL, with durable snapshots and prefix compaction. The in-repo WAL and RocksDB have been removed. All of this is confirmed in code.
- **R1 (durability).**
  - R1-1 (container recreation) was closed on 2026-09-07 on Docker Desktop for Windows (4 tests and a 601-test controller regression), but the closure is not committed.
  - R1-2 (production Linux filesystem and storage class) and R1-3 (machine power loss) remain open. They are release blockers.

---

## 9. Recommended remediation plan

The plan is ordered by risk to readers and operators. Effort estimates are rough and assume one person familiar with the code.

### Phase A — Correctness and safety (about 2–3 days)

| # | Action | Documents or code | Closes |
|---|---|---|---|
| A1 | Commit the 2026-09-07 work: R1-1 tests and evidence, the durable fixture, the `LeaderGuardHandlerTest` fix, the `TransferMetrics` deletion, and plan v1.26 / register v1.3. Then add the commit SHA, timestamps and log hashes to the r1 evidence. | task/, evidence/, controller tests | §4.6 (2) |
| A2 | Preserve the orphaned SHAs. Either add refs such as `refs/evidence/r6-b604505`, or annotate every citation with its master equivalent (Appendix A). Do this before any `git gc`. | plan, register, handover, evidence (`.md` and `.json`) | §4.6 (1) |
| A3 | Move `docs-design/dev/prompts.txt` out of the repository (APEX prompts; nothing sensitive found, so no history rewrite is needed). Point `.github/copilot-instructions.md` at the canonical Architecture Specification, and fix its stale module, class, route and example content. | dev/, .github/ | §7.2, §7.6 |
| A4 | Fix revocation-serial normalisation and add an openssl-format test. Update Security Guide §4.2 and Runbook §4.1, §4.2 and §4.4 so that revocation is sent to every controller and added to configuration before any restart. | `CertificateTrustState`, SDG, runbook | §4.5, §6 #1–2 |
| A5 | Give every `docker/compose/*.yml` service an explicit development profile, and add `QUORUS_AGENT_TENANT_ID` for agents. Fix the Dockerfile `HEALTHCHECK`, the OTel variable and `M2_REPO`. Delete `-corrected.yml`. | docker/, controller Dockerfile | §4.2, QR-04 |
| A6 | Fix the README quick start and example workflow, and the HTTPie runbook payloads. | README, scripts/httpie | §4.2, §4.3 |
| A7 | Replace every "no authentication" statement with a pointer to Arch Spec §3 and the Security Guide. Remove "service-connection governance" from SDG §1. | 8 documents (§4.1) | §4.1 |

### Phase B — Reconcile the canonical set (about 3–5 days)

| # | Action |
|---|---|
| B1 | **Architecture Spec.** Close or narrow ARCH-03, ARCH-06, ARCH-12 and ARCH-13. Fix the §3 telemetry row and the §13 lifecycle gate. Define "durable default" using `quorus.raft.storage.path`. Move "Closed" out of the Priority column. Add ARCH-09 (HTTP buffering) as still open. |
| B2 | **REST Spec.** Give every normative section a Current, Required or Planned label. Add mapping tables from Q-codes to target codes and from colon scopes to target scopes. Close API-01 by citing `OpenApiContractTest`. Rewrite API-02. List `GET /api/v1/openapi.yaml` as Current. Fix the `DELETE /transfers` and events rows. |
| B3 | **API Reference.** Add the 13 missing routes. Fix the heartbeat statuses, casing and `message` field. Add `agentPool` and `networkZone`, and settle the `port` rule. Remove `AT_RISK`. Add an error-envelope section. Fix the OpenAPI `AgentStatus` enum. |
| B4 | **YAML Syntax Guide.** Decide between changing the docs and changing the code (§4.3), and record the decision in an ADR. Add a "Validation requirements" section. Mark `execution.*` and `retryCount` "parsed, not applied", or implement them. Fix the options and nesting claims. Advise quoting `created`. Then merge the correct parts of YAML-VALIDATION-GUIDE into it and delete that guide. |
| B5 | **Security Guide.** Move §11–14 into the Service Connection Runbook and an upgrade-notes document. Document that elevation is available only through the gateway, that `openapi.yaml` is public, which headers are actually required, and that Raft peers are not bound to node IDs. |
| B6 | **Versioning Policy.** Record schema 3 as readable from 0, and the configuration-contract break in `b35fb25`. Define one product version and use it in the pom, `HttpApiServer.VERSION`, OpenAPI `info.version` and `quorus.version`. |
| B7 | **Plan and register.** Settle the Phase 0 and Phase 4 status in one place. Correct the present-tense fixture statements. Re-verify OBS-04, -05, -07 and -14. Fix the section counts and the revision order. Add IDs for the three orphan plan items and for the four configuration residuals (§6 #16). Add a revision history to the plan. |

### Phase C — Consolidate and archive (about 2 days)

| # | Action |
|---|---|
| C1 | Move the 12 `dev/vertx5-advice/` files to the PeeGeeQ repository. Delete `performance/CRITICAL_PERFORMANCE_REFACTORING_GUIDELINES.md`. |
| C2 | Move these documents to `docs-design/archive/`, each with a one-line "why archived" banner: the three `CONNECTION_POOL_*` documents, `VERTX5_PERFORMANCE_BENCHMARKS.md`, all six `vertx-migration/` files (keep SUMMARY as the record), `FTPS_INTEGRATION_TEST_INVESTIGATION.md` (after marking it resolved), `evidence/remediation-r4-r6-2026-09-05.md`, and the Configuration Handover (after extracting the configuration reference). |
| C3 | Write one short `docs-design/dev/QUORUS_VERTX5_CONVENTIONS.md` based on what the code actually does. Link it from `.github/copilot-instructions.md`. |
| C4 | Merge the Cluster Startup Guide, the Docker Testing README and DOCKER_BUILD_OPTIMIZATION into `docker/README.md`. |
| C5 | Split `QUORUS_SYSTEM_DESIGN.md`. Move the enterprise requirements to the Architecture Spec (or delete them and link). Archive the PostgreSQL/Redis/etcd, Kubernetes, SQL, changelog, duplicated and file-organisation sections. Badge what remains. Globally rename `QuorusStateMachine` to `QuorusStateStore`. |
| C6 | Extract a `QUORUS_RAFT_STORAGE_REFERENCE.md` from the Raft WAL design, with contents as listed in §10. Archive the remainder. |
| C7 | Trim the Simulators design. Rewrite §1 against current code. Relabel §2–7 as standalone doubles. Restore the three test links. Delete Appendix C. |
| C8 | Rewrite NEGATIVE_TESTING around `@ExpectsError`. Align LOG_STYLE with the code. Update PROTOCOL_SERVERS and RAFT_CLUSTER_TESTING. Add the fifth Docker class to DOCKER_TEST_PERFORMANCE. |
| C9 | Scrub the PeeGeeQ references from code and scripts. Remove `vertx-pg-client` and `ConnectionPoolService`, or document why they stay. |
| C10 | Clean up the working copy: delete the local `temp/` worktrees, `.history/` and `hs_err_pid*.log` (all already git-ignored), and untrack the five `temp/*.txt` files still in git. Normalise line endings with a `* text=auto` rule, committed on its own. Merge NOTICE and OPEN_SOURCE_USAGE into one generated inventory. |

### Phase D — Keep it accurate (automation and governance)

| # | Action |
|---|---|
| D1 | **Generate, don't copy.** Produce the "Current" endpoint table (REST §5–15, API Reference skeleton, `InfoHandler`) from `quorus-controller-v1.yaml`. Produce a configuration reference from the properties files and `AppConfig`/`AgentConfig` key constants. |
| D2 | **CI documentation checks:** a relative-link checker; a header linter for Version, Date and Status; a ban on `C:\Users\` and similar personal paths; and a check that every `docker/compose/*.yml` passes `docker compose config`. |
| D3 | **A single status vocabulary.** Use Implemented / Partial / Planned for capabilities and Current / Required / Planned for API items, and nothing else. |
| D4 | **ADR hygiene.** Add ADR-0006 (raftlog-core WAL and snapshot sidecar), ADR-0007 (layered configuration with no system properties), ADR-0008 (schema-3 coordinated upgrade), ADR-0009 (trust-state scope) and ADR-0010 (YAML semantics decision). Add an index, and Supersedes / Superseded-by fields. |
| D5 | **Definition of done for code changes:** any change to a public contract (endpoint, key, environment variable, Compose file or status) updates its canonical document in the same commit, and the plan and register cite commit SHAs only after the commit exists. |

---

## 10. Proposed documentation structure

After Phases B and C, the live set would look like this. Items marked ★ are new.

```
README.md                               entry point: what, build, one working dev quick start, links
NOTICE, OPEN_SOURCE_USAGE.md            attribution (shipped vs test-only)
docker/README.md                        the only Docker guide (topologies, ports, env, dev vs TLS)
docs/
  QUORUS_ARCHITECTURE_SPECIFICATION.md  canonical — single capability-status table
  QUORUS_REST_API_SPECIFICATION.md      canonical — every section labelled
  QUORUS_API_REFERENCE.md               canonical — generated skeleton from OpenAPI
  QUORUS_YAML_SYNTAX_GUIDE.md           canonical — includes validation rules
  QUORUS_SECURITY_DEPLOYMENT_GUIDE.md   canonical — setup only; release notes moved out
  QUORUS_CERTIFICATE_INCIDENT_RUNBOOK.md canonical
  QUORUS_CONFIGURATION_REFERENCE.md   ★ generated key/env/default/precedence table
  QUORUS_SERVICE_CONNECTION_OPERATIONS_RUNBOOK.md  absorbs SDG §13–14
  QUORUS_USER_GUIDE.md, QUORUS_ARCHITECTURE_QUICKSTART.md, QUORUS_WORKFLOWS_README.md,
  QUORUS_INTEGRATION_EXAMPLES_README.md  thin; link rather than repeat
  QUORUS_UPGRADE_NOTES.md             ★ schema-3 upgrade, compatibility notes (from SDG §11–13)
docs-design/
  README.md                             precedence + full directory table
  architecture-decisions/               ADR-0001…0010 + index ★
  task/                                 plan + register
  evidence/                             dated records with revision identity
  reference/                            versioning, reproducible build, QUORUS_RAFT_STORAGE_REFERENCE.md ★
  design/                               trimmed, section-badged SYSTEM_DESIGN; SIMULATORS
  testing/                              testing README, log style, negative testing, protocol servers, Raft cluster
  performance/                          DOCKER_TEST_PERFORMANCE, relabelled micro-benchmark
  dev/                                  QUORUS_VERTX5_CONVENTIONS.md ★
  reviews/                            ★ 2026-08-31 review (+ status annex), this review
  archive/                              everything superseded, each with a "why archived" banner
```

**The Raft storage reference ★ should cover:**

- the dependency coordinates and provenance;
- the layering: RaftNode → `RaftStorage` → `RaftLogStorageAdapter` → `FileRaftStorage` + `FileSnapshotStore`;
- the method contract (what is durable only after `sync`, and the `truncatePrefix` preconditions);
- the on-disk layout: the WAL, `snapshot.dat`, `snapshot.required` and `.tmp` files;
- every storage, snapshot and hard-limit key with its default;
- the recovery order and the snapshot/InstallSnapshot flow;
- operator rules: back up the whole stopped-node directory; there is no migration path;
- a test map, and what is still unproven (power loss).

---

## 11. Limitations and open review items

- **Coverage.** All 75 documents in scope were assessed. The connection to your computer dropped during the first pass. The four documents it missed (`prompts.txt`, `OPEN_SOURCE_USAGE.md`, `.github/copilot-instructions.md`, `QUORUS_TESTING_README.md`) and the link check were completed on 2026-09-25, when the headline findings were also re-verified against the source. These remain partial:

  | Item | Status |
  |---|---|
  | `vertx5-advice/` bodies | Classified from headers, openings and structure only. They are recommended for removal regardless. |
  | Per-module test-class counts | Not collected. `quorus-core` test-method annotations (1,125) and tag usage were collected. |
  | Overlap of `testing/` documents with `docs/QUORUS-DOCKER-TESTING-README.md` | Assessed only through §4.9. |
  | `QuorusAgent.java:372` `.join()` | Whether it can run on an event loop was not traced. |

- **Re-verified on 2026-09-25:**
  - the Compose security gap (no `QUORUS_SECURITY*` in any `docker/compose/*.yml` file or the Dockerfile, and the production checks in `SecurityConfig.validate()`);
  - revocation-serial normalisation;
  - node-local trust state;
  - all eight orphaned SHAs, with tree identity and where each is cited;
  - `OpenApiContractTest`;
  - the workflow validation path;
  - `roleAllows`;
  - `JobAssignmentService` construction;
  - the `/health` JSON shape;
  - the `.gitignore` coverage.
- **Static analysis only.** No builds, tests or containers were run. "Fails at startup" findings (§4.2) come from reading the configuration and the validation code, not from running them.
- **Working-tree dependence.** Several statements describe uncommitted changes as observed on 2026-09-24. If those changes are discarded rather than committed, §4.6 and §8 change.
- **Evidence outside the repository.** RaftLog library SHAs (`1c5af80`, `7a3bd3a`, `872a8c0`, `db59859`) and the "41 storage tests / 319 library tests" claims belong to the separate raftlog repository and were not verified.

---

## Appendix A — Orphaned commit references

These SHAs are cited in live documents. Each exists locally as a commit object, but none is an ancestor of HEAD or `origin/master`, and `git name-rev` resolves none of them to a branch or tag. Each has a master commit with an identical tree. All eight tree matches, and the citation lists below, were re-checked on 2026-09-25 with `git rev-parse <sha>^{tree}` and a search of `docs/` and `docs-design/` (excluding `archive/`).

| Cited SHA | Master equivalent (same tree) | Where cited |
|---|---|---|
| `b604505` | `dc447d4` fix(raft): always release storage after transport shutdown | Plan; register; Configuration Handover; r5-closure; r6-final-acceptance (`.md` and `.json`); raftlog-validation-handover; remediation-r4-r6 |
| `0fefecb` | `8b3cf5c` fix(security): complete R4 and R5 handover remediation | r6-final-acceptance (`.md` and `.json`) |
| `f8fb15e` | `a0103a0` fix(raft): serialize vote decisions and repair Docker test startup | r4-dns-remediation (`.md` and `.json`), as the "base revision" |
| `28f0530` | `1a8f2b3` (R2 + R3 changes) | Plan; Configuration Handover; remediation-r4-r6 (`.md` and `.json`) |
| `ffc3e64` | `db532fb` docs(evidence): record the full reactor verify with raftlog-core 1.2.0 | Plan; Configuration Handover; `raft-log-tdd-evidence-2026-09-04.json` |
| `038da9f` | `e7c9dbc` fix(raft): persist snapshots and recover safely after compaction | Configuration Handover; `raft-log-tdd-evidence-2026-09-04.json` |
| `2d8ed83` | `7b07825` refactor(raft): enforce external raftlog-only storage | Configuration Handover; `phase4-tdd-evidence-2026-09-03.json` |
| `43cdd20` | `067bb45` fix(raft): RaftLogStorageAdapter prefix truncation | Configuration Handover |

The rewrite starts at `6942fc5`, which became `381a242`; the only difference in the commit object is a removed trailer. Because the JSON evidence manifests also cite these SHAs, fix them in the same pass as the Markdown.

---

## Appendix B — Endpoint inventory reconciliation

| Source | Operations | Notes |
|---|---|---|
| `HttpApiServer.java:161-247` (registered routes) | 52 | Authoritative runtime |
| `quorus-controller-v1.yaml` (OpenAPI 3.1.0, `info.version` 1.3.2-alpha) | 52 | Equal to the router; asserted by `OpenApiContractTest.java:73` |
| REST API Specification, rows marked Current | 51 | All correct. Leaves out `GET /api/v1/openapi.yaml`, which it lists as Required under a different path |
| HTTP API Reference, endpoint headings | 39 | All exist. Missing: `openapi.yaml` plus 12 service-connection, secret-reference and security-event routes |
| `InfoHandler` endpoint list (`/api/v1/info`) | 35 | Leaves out 17 security and connection routes |
| README, User Guide, Quickstart, Cluster Startup, Docker Testing README | Various | All lack the security, connection and telemetry routes |

---

## Appendix C — Selected contradicted claims

This appendix lists the claims most likely to cause a wrong action. Full per-area tables are summarised in §7.

| # | Claim | Where | Actual | Evidence |
|---|---|---|---|---|
| 1 | No authenticated identity boundary | Arch ARCH-03; REST API-02; User Guide; Quickstart; Cluster Startup; docker/README; System Design | mTLS on HTTP and Raft, policy, audit | `HttpApiServer.java:149-152,250-261`; `GrpcRaftServer.java:102-110` |
| 2 | Compose files start working controllers | README; Cluster Startup | Production profile with no certificates, so validation fails | `quorus-controller.properties`; `SecurityConfig.java:83-91` |
| 3 | Only `metadata.name` is required | YAML guide | Engine requires seven metadata fields; schema validation also requires `execution` | `SimpleWorkflowEngine.java:201`; `WorkflowSchemaValidator.java:146,283-285` |
| 4 | `execution.parallelism` / `dryRun` control execution | YAML guide; Workflows README | Ignored; groups run sequentially | `SimpleWorkflowEngine.java:93-104,~344-372` |
| 5 | Options are passed to the adapter | YAML guide | Dropped | `TransferGroup.java:168-174` |
| 6 | Revoked serial `01AF44` (example) | Security Guide §4.2 | Can never match | `CertificateTrustState.java:78,127` |
| 7 | Runtime revocation is shared across enforcement | Security Guide; Arch Spec; runbook | Per-process and volatile | `QuorusControllerVerticle.java:80` |
| 8 | No OpenAPI 3.1 contract | REST API-01 | Present and tested | `quorus-controller-v1.yaml:1`; `OpenApiContractTest.java:73` |
| 9 | 21 stable error codes | REST §16 | 1 of 21 exists | `ErrorCode.java` |
| 10 | Dotted scopes (`transfers.read`) | REST §4.2 | Colon scopes | `AuthorizationPolicyEngine.java:47-76` |
| 11 | Snapshot and command schema version 1 | Versioning Policy | `VersionRange(0, 3)` | `SchemaVersionRegistry.java:40-41` |
| 12 | `IN_PROGRESS` gap blocks end-to-end lifecycle | Arch §13 | Fixed | `QuorusAgent.java:433-441` |
| 13 | Job model lacks operational context | Arch ARCH-12, §12.2 | Present except escalation policy | `TransferJob.java:81-90` |
| 14 | `ELECTION_TIMEOUT_MS` / `HEARTBEAT_INTERVAL_MS`; default 3000/500 | System Design; Raft Cluster Testing | `QUORUS_RAFT_*`; default 5000/1000 | `AppConfig.java:140,144,307` |
| 15 | `QuorusStateMachine` holds replicated state | System Design (×16); Simulators design | Class removed; `QuorusStateStore` | `QuorusStateStore.java:60` |
| 16 | Controller embeds workflow, tenant and transfer engines | System Design | No imports in controller main | `TransferExecutionService.java:77` (agent) |
| 17 | Route triggers and file watchers run | System Design | Not implemented | No `WatchService` or evaluator |
| 18 | No snapshots; log grows forever | Raft WAL design §3, §13.4, §19.8 | Snapshots and prefix compaction | `RaftLogStorageAdapter.java:202-207`; `RaftNode.java:443-462` |
| 19 | Simulators implement production interfaces | Simulators design | Standalone classes | `InMemoryTransferProtocolSimulator.java:60` |
| 20 | Three Raft test classes are missing | Simulators design, Related Components | Present and tracked | git ls-files |
| 21 | Negative tests are excluded from the default build | Negative Testing Strategy | Included since `8864c2f` | `quorus-core/pom.xml:150-153` |
| 22 | Heartbeat status `BUSY`; response `"ACTIVE"` | API Reference | No `BUSY`; lowercase output | `AgentStatus.java:65-129,349-351` |
| 23 | Progress condition `AT_RISK` | API Reference | Never produced | `TransferProgressHandler.java:146-163` |
| 24 | Names may contain spaces; `kind` gives a deprecation warning | YAML-VALIDATION-GUIDE | Rejected; no check exists | `WorkflowSchemaValidator.java:43,173` |
| 25 | `AGENT_TENANT_ID` takes priority | User Guide | `QUORUS_AGENT_TENANT_ID` wins | `AgentConfig.java:46-52` |
| 26 | Observability stack receives telemetry via `OTEL_EXPORTER_OTLP_ENDPOINT` | Cluster Startup | Variable is not read | `AppConfig.java:229` |
| 27 | `docker build -f quorus-controller/Dockerfile .` works | Raft Cluster Testing | Needs `--build-context m2cache` | `Dockerfile:6,38` |
| 28 | Pool presets `ConnectionPoolConfig.productionConfig()` | Connection-pool docs; Audit Report | Deleted 2026-01-06 | `99ead9a` |
| 29 | JMH benchmarks in `quorus-integration-examples/benchmarks/` | VERTX5_PERFORMANCE_BENCHMARKS | No JMH; no directory | poms |
| 30 | `quorus-api` has 7 passing tests | Performance Validation Results | Module removed from the reactor | `pom.xml:15-22` |
| 31 | Raft state is at `checks.raft.state` in `/health` | Raft Cluster Testing (PowerShell, lines 428, 441, 534) | `raft` is a top-level object; `checks` holds `raftCluster`, `diskSpace`, `memory` | `HealthHandler.java:103-117` |
| 32 | System Design is the "comprehensive architecture documentation" | `.github/copilot-instructions.md` | Non-normative, grade D; the canonical source is the Architecture Specification | `docs-design/README.md` precedence list |
| 33 | Agents report status at `POST /agents/{id}/status` and deregister with `DELETE /agents/{id}` | `.github/copilot-instructions.md` | `POST /api/v1/jobs/:jobId/status`; no deregistration route | `HttpApiServer.java:205-237` |
| 34 | RaftLog Core 1.1.0 and RocksDB JNI are dependencies | OPEN_SOURCE_USAGE.md | raftlog 1.2.0; no RocksDB | `pom.xml:32`; no `rocksdbjni` in any pom |

---

*End of report.*
