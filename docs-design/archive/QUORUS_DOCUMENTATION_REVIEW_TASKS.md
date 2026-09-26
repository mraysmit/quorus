<img src="../../docs/quorus-logo.png" alt="Quorus" width="120"/>

# Quorus Documentation Review — Task List

**Version:** 1.7
**Date:** 2026-09-26
**Author:** Mark Ray-Smith — Cityline Ltd
**License:** Apache 2.0
**Status:** Archived 2026-09-26 — merged into the [Outstanding Work Register](../task/QUORUS_OUTSTANDING_WORK_REGISTER.md)

> **Archived.** This list was merged into the register, which is the project's single task list: documentation tasks are [Section H](../task/QUORUS_OUTSTANDING_WORK_REGISTER.md#11-section-h--documentation-remediation), code defects are Section I, and decisions are in the §3 decision log. Task IDs are unchanged. Do not update this file; its progress notes are kept as history.

---

## 1. Purpose

This list turns the findings of the
[Quorus Documentation Review — 2026-09-24](../reviews/QUORUS_DOCUMENTATION_REVIEW_2026-09-24.md) into
tasks with stable IDs. It covers every action in the review's §9 remediation plan, every code or
configuration defect in §6, and the per-document fixes in §3 and §7 that §9 does not name.

**This list is derivative, like the
[Outstanding Work Register](../task/QUORUS_OUTSTANDING_WORK_REGISTER.md).** Where a task is delivery work
rather than a documentation correction, it goes into the
[enterprise plan](../task/QUORUS_ENTERPRISE_IMPLEMENTATION_PLAN.md) first and is then reflected in the
register (register §14.3). Tasks marked **→ Register** need that step.

The review was done by static analysis at HEAD `216348a` plus the working tree of 2026-09-24.
Re-check each finding against the current tree before acting on it.

### ID scheme

| Prefix | Source in the review |
|---|---|
| `DR-A*` … `DR-D*` | §9 remediation plan, Phases A–D (numbers kept) |
| `DR-X*` | §6 code and configuration defects that no §9 action covers (number = §6 row; `DR-X24` is the §11 open item) |
| `DR-F*` | §3 and §7 document fixes that no §9 action covers |
| `DR-Q*` | Decisions that must be made before the dependent tasks can finish |

### State vocabulary

⬜ Open · 🟨 In progress · ✅ Done · ⏸ Blocked (the dependency is named in the row)

Severity (**H** / **M** / **L**) is the review's own rating, where it gave one.

---

## 2. Summary

| Section | Area | Tasks | ✅ / 🟨 / ⬜ (2026-09-26) | Effort (review estimate) |
|---|---|---|---|---|
| 3 | Decisions needed first | 6 | 2 / 0 / 4 | — |
| 4 | Phase A — correctness and safety | 7 | 5 / 2 / 0 | 2–3 days |
| 5 | Phase B — reconcile the canonical set | 7 | 1 / 2 / 4 | 3–5 days |
| 6 | Phase C — consolidate and archive | 10 | 0 / 1 / 9 | about 2 days |
| 7 | Phase D — automation and governance | 5 | 0 / 0 / 5 | ongoing |
| 8 | Code defects outside Phases A–D | 11 | 0 / 0 / 11 | not estimated |
| 9 | Document fixes outside Phases A–D | 17 | 3 / 1 / 13 | not estimated |

**Recommended order:** DR-A1 and DR-A2 first (evidence integrity, before any `git gc`), then the
rest of Phase A, then DR-Q1 to DR-Q5, then Phases B and C. Phase D can start at any point, and
DR-D1 is best done before DR-B3 and DR-F13.

### Progress update — 2026-09-26

- **Completed:** DR-B7. Register v1.5 and plan v1.28 fix the section counts, versions and revision order; settle the Phase 0 and Phase 4 status; close OBS-04, OBS-05 and OBS-14 and correct OBS-07 after checking the source; give IDs to the three unnumbered plan items (`R1-4`, `PROC-01`, `P2-13`); and add register Section I for the four configuration residuals (`CFG-02` to `CFG-05`) and the **→ Register** items from §8 and DR-A4.
- **Completed:** DR-F07, DR-F08 and DR-F13. Both reviews now live in `docs-design/reviews/`, and the 2026-08-31 review has a finding-status annex. The configuration handover is in `archive/` with a "why archived" banner, repaired links and no personal path. `docs-design/README.md` lists `task/`, `evidence/`, `reviews/`, `architecture-decisions/` and `reference/`. DR-C2 is in progress because the handover is archived; its §2.1 and §8 remain the configuration reference until DR-D1.
- **Decision completed:** DR-Q6. `temp/` is scratch space and can never hold evidence. Cited raw output now goes directly to `docs-design/evidence/raw/<slice-id>/`, is committed with its record, and is hashed in the manifest. The rule is in plan §6.1 and in the Copilot build-command guidance, which had been telling assistants to tee output into `temp\`. Of 220 cited `temp/` paths, 153 were already gone; the 62 surviving logs were copied unchanged to `evidence/raw/` (47 match their recorded SHA-256, 15 had none), and [the index](../evidence/raw/INDEX.md) maps old paths to copies.

### Progress update — 2026-09-25

- **Completed:** DR-A2. All live citations now use reachable replacement commits, evidence JSON remains valid, and the eight historical IDs are preserved in `reference/QUORUS_COMMIT_HISTORY_REWRITE_MAP.md`.
- **Completed:** DR-A3. The unrelated APEX prompt file is removed from the working tree. Copilot guidance now names the real modules, protocols, state store, Compose files, agent routes and tenant-aware payloads; its workflow example matches the schema, and its test-concurrency policy is accurately stated as a migration target.
- **Decision completed:** DR-Q2 selects node-local, volatile runtime revocation. Operators must update every controller and persist the complete set in configuration before restart; ADR-0009 remains outstanding under DR-D4.
- **In progress:** DR-A1. The 2026-09-07 work is already committed as `804e11d` and `3343785`, and the R1 evidence now cites `804e11d`; retained raw-log hashes still need recording if available.
- **In progress:** DR-A4. Leading-zero/colon serial normalization, an OpenSSL-formatted integration test, and node-local operating guidance are implemented. The focused `SecurityBoundaryIntegrationTest` passed 17/17; the delivery work still needs its register entry and ADR-0009.
- **Completed:** DR-A5. All 28 controller services in the eight retained development controller Compose files declare the insecure development posture explicitly; all three agents declare a tenant and matching development posture; health checks use `/health/live`; and all 14 Compose models validate. Unsupported `QUORUS_RAFT_HOST` settings and the duplicate corrected topology are gone, the Quorus OTLP setting is used, standalone logging names/ports no longer collide, and `CFG-01` records the work in the plan/register. The generated-certificate TLS example starts in the production profile, becomes healthy, accepts its gateway certificate and rejects a client without a certificate.
- **Completed:** DR-A6. The README quick start now uses the development topology, documents `M2_REPO` and `mvn install` for PowerShell and bash, avoids a personal `JAVA_HOME`, and contains a workflow that passes `WorkflowValidationCLI`. The HTTPie examples now carry the tenant and authoritative attempt/fence fields, use valid lowercase agent statuses, and document the complete development lifecycle. A clean image build and live single-controller smoke test reached `COMPLETED` with 100% progress.
- **Completed:** DR-A7. ARCH-03 and API-02 remain closed, the Architecture authentication gate now reflects the implemented boundary, and the User Guide, Architecture Quickstart, Cluster Startup Guide, Docker README, and System Design no longer claim that controller authentication is absent. Each operational summary points to Architecture Specification §3 and the Security Deployment Guide; Security Guide §1 no longer lists delivered service-connection governance as missing.
- **In progress:** DR-B1 and DR-B2. The stale Architecture and REST conformance-gap tables now distinguish Closed, Partial and Open work; ARCH-03/06 and API-01/02 are closed, ARCH-12/13 are narrowed, and the OpenAPI route/test are cited. The remaining section-level reconciliation is still open.

---

## 3. Decisions needed first

| ID | Decision | Options | Blocks | State |
|---|---|---|---|---|
| **DR-Q1** | Workflow YAML semantics (review §4.3) | (a) Change the guides to match the parser and engine; (b) implement `execution.dryRun` / `parallelism` / `timeout` / `strategy`, group `retryCount`, `options` pass-through and recursive variable substitution. Record the choice as ADR-0010. | DR-B4, DR-D4 | ⬜ |
| **DR-Q2** | Runtime revocation scope (§4.5, §6 #2) | **Chosen:** keep runtime updates node-local and volatile; send the full set to every controller and persist it in configuration before restart. ADR-0009 remains part of DR-D4. | DR-D4 | ✅ |
| **DR-Q3** | Elevation for direct mTLS identities (§6 #8) | (a) Document that only gateway-asserted identities can hold elevation; (b) add a direct-binding elevation mechanism. | DR-B5 | ⬜ |
| **DR-Q4** | How `*IT` / `*Benchmark` classes run (§6 #18) | (a) Add the Failsafe plugin; (b) rename the classes and tag them. | DR-X18, DR-F16 | ⬜ |
| **DR-Q5** | Authoritative product version (§4.10, §6 #13) | Choose one version and one source (the pom, via resource filtering). | DR-B6 | ⬜ |
| **DR-Q6** | Raw evidence retention (found 2026-09-26; not in the review) | **Chosen:** cited raw output is committed under `docs-design/evidence/raw/<slice-id>/` with its SHA-256 in the manifest; `temp/` never holds evidence. Historical `temp/` citations stay as written and resolve through `evidence/raw/INDEX.md`. | DR-C10 (`temp/` deletion), DR-A1 (log hashes), plan §6.1 | ✅ |

---

## 4. Phase A — Correctness and safety

| ID | Sev. | Task | Files | Done when | State |
|---|---|---|---|---|---|
| **DR-A1** | H | Commit the 2026-09-07 work: R1-1 tests (`ContainerRecreationDurabilityTest`, `DockerComposeCluster`) and evidence, `docker-compose-3node-durable.yml` and the other hardened fixtures, the `LeaderGuardHandlerTest` fix, the staged `TransferMetrics` deletion, plan v1.26 and register v1.3. Then add the commit SHA, timestamps and log hashes to the r1 evidence, and resolve its fixture-durability contradiction (§3 and §10.4 against §12.1). | `docs-design/task/`, `docs-design/evidence/r1-container-recreation-2026-09-07.md`, `quorus-controller/src/test/` | Committed as `804e11d` (R1-1, evidence, fixtures, plan/register and test correction) and `3343785` (`TransferMetrics` removal); the R1 evidence cites the reachable revision. Retained raw-log hashes remain to be recorded if available. Surviving logs are in `evidence/raw/` with their hashes checked (DR-Q6); record the R1-1 log hashes there if they can be found. | 🟨 |
| **DR-A2** | H | Preserve the eight orphaned SHAs (Appendix A). Either add refs such as `refs/evidence/r6-b604505` and push them, or annotate every citation with its master equivalent. Fix the `.json` manifests in the same pass as the `.md` files. **Do this before any `git gc`.** | plan, register, Configuration Handover, `evidence/*.md` and `*.json` | Live citations use reachable replacements and `reference/QUORUS_COMMIT_HISTORY_REWRITE_MAP.md` preserves all eight old-to-new mappings. | ✅ |
| **DR-A3** | H | Move `docs-design/dev/prompts.txt` out of the repository (APEX material; nothing sensitive, so no history rewrite). In `.github/copilot-instructions.md`, point "Key Files" at the Architecture Specification, replace `QuorusStateMachine` with `QuorusStateStore`, fix the module table (remove `quorus-api`, add `quorus-integration-examples`, add FTPS and NFS), fix the `WorkerExecutor` claim, the agent lifecycle routes, the registration fields (add `tenantId`), the workflow example, and the Docker commands. Restate the test-concurrency rules as a target, or finish the migration. | `docs-design/dev/prompts.txt`, `.github/copilot-instructions.md` | Unrelated prompt file removed; every §7.2 Copilot-instructions issue is reconciled with the current source, schema and Compose files. | ✅ |
| **DR-A4** | H | Fix revocation-serial normalisation in `CertificateTrustState` (compare `BigInteger` values, or strip leading zeros on both sides) and add a test that uses an openssl-formatted, zero-padded serial. Update Security Guide §4.2 and Certificate Incident Runbook §4.1, §4.2 and §4.4: send the revocation to every controller, add it to configuration before any restart, and state that Raft has no CRL. | `CertificateTrustState.java:78,127`, `docs/QUORUS_SECURITY_DEPLOYMENT_GUIDE.md`, `docs/QUORUS_CERTIFICATE_INCIDENT_RUNBOOK.md` | Code, test and operating guidance complete; 17/17 focused tests pass. Register entries `SEC-01` and `SEC-02` added 2026-09-26. ADR-0009 remains (DR-D4). | 🟨 |
| **DR-A5** | H | Give every `docker/compose/*.yml` service an explicit, clearly labelled development profile (`QUORUS_SECURITY_PROFILE=development`, `QUORUS_SECURITY_ALLOW_INSECURE=true`, TLS off, and the agent equivalents plus `QUORUS_AGENT_TENANT_ID`). Add one TLS example that uses generated certificates. Fix the Dockerfile `HEALTHCHECK` (use `/health/live` with the configured scheme). Replace `OTEL_EXPORTER_OTLP_ENDPOINT` with `QUORUS_TELEMETRY_OTLP_ENDPOINT`, remove `QUORUS_RAFT_HOST`, and document or default `M2_REPO`. Delete `docker-compose-corrected.yml`, and resolve the loki / observability container-name and port clash. | `docker/compose/`, `quorus-controller/Dockerfile:99-100` | Explicit development settings, generated-certificate mTLS example, scheme-aware `/health/live` image probe, configuration cleanup, distinct logging topology, `M2_REPO` guidance and `CFG-01` plan/register entry complete. All 14 Compose models validate and the live TLS positive/negative checks pass. | ✅ |
| **DR-A6** | H | Fix the README quick start: use a development topology, use `mvn install` before `exec:java`, remove the personal `JAVA_HOME`, and give a bash equivalent. Make the example workflow pass validation. Add `tenantId` to `10-register-agent.httpie`, `payloads/agent-register.json` and `20-create-transfer.httpie`, add development-profile setup, give the runbook a header, and reference `32-update-assignment-status.httpie`. | `README.md`, `scripts/httpie/` | README workflow validation passes; HTTPie agent and transfer requests match the live tenant, status and attempt/fencing contracts; clean Compose build plus the documented lifecycle smoke test completed successfully. | ✅ |
| **DR-A7** | H | Replace every "no authentication" statement with a one-line pointer to Architecture Spec §3 and the Security Guide. Remove "service-connection governance" from Security Guide §1. | Arch Spec ARCH-03 and §13; REST API-02; User Guide; Architecture Quickstart; Cluster Startup Guide; `docker/README.md`; System Design capability table and Network Architecture; Security Guide §1 | All named documents describe the implemented production mTLS/trusted-gateway boundary, distinguish intentionally insecure development profiles, and point to Architecture Specification §3 and the Security Deployment Guide. | ✅ |

---

## 5. Phase B — Reconcile the canonical set

| ID | Task | Files | State |
|---|---|---|---|
| **DR-B1** | **Architecture Spec.** Close or narrow ARCH-03, ARCH-06, ARCH-12 (only an escalation policy is missing) and ARCH-13. Fix the §3 telemetry row (five events and a `STALLED` boundary) and the §13 lifecycle gate (QR-01 is fixed). Define "durable default" using `quorus.raft.storage.path`. Move "Closed" out of the Priority column. Keep ARCH-09 (HTTP buffering) open. Also: add the missing ARCH-01 or renumber, reorder the IDs, fix the §7 opening, make the untestable requirements in §7.1 measurable, and note that the SFTP direct-URI path does not meet §10.4's "visibly logged" rule (see DR-X06). | `docs/QUORUS_ARCHITECTURE_SPECIFICATION.md` | 🟨 |
| **DR-B2** | **REST Spec.** Label §3.2, §3.4, §3.5, §3.8, §4.2, §6.3 and §16 as Current, Required or Planned. Add mapping tables from `ErrorCode` Q-codes to target codes and from colon scopes to dotted scopes. Close API-01 by citing `OpenApiContractTest`, and rewrite API-02. List `GET /api/v1/openapi.yaml` as Current. Fix the `DELETE /transfers/{id}` purpose text (it returns `{jobId, message}`), the §6.1 events row, the path-parameter names, the agent "search" and route "conditional update" claims, and the §3.1 unknown-fields rule. | `docs/QUORUS_REST_API_SPECIFICATION.md` | 🟨 |
| **DR-B3** | **API Reference.** Add the 13 missing routes, including the validate body and `probeTimeoutMillis`. Fix the heartbeat section: remove `BUSY`, use lowercase status output, say that an invalid status is ignored, and add the `message` field. Add `agentPool` and `networkZone`, and settle the `port` rule. Remove `AT_RISK`. Add an error-envelope and `ErrorCode` section. Fix the required-field markings, add transfer `metadata`, `runbook` and `labels`, document the assignment and route bodies, and note that `POST /security/authorization/check` is leader-only. Fix the OpenAPI `AgentStatus` enum (§6 #10). | `docs/QUORUS_API_REFERENCE.md`, `quorus-controller-v1.yaml:1105,1130-1132` | ⬜ |
| **DR-B4** | **YAML Syntax Guide** (⏸ DR-Q1). Add a "Validation requirements" section listing the seven metadata fields and `spec.execution`. Mark `execution.*` and `retryCount` "parsed, not applied", or implement them. Fix the options and nesting claims, advise quoting `created`, add runtime `ExecutionContext` variables to the precedence list, and fix the examples table (`batchSize` location, `ecommerce-order-processing.yaml`, the `file` protocol and the `mode` option). Merge the correct rules from `YAML-VALIDATION-GUIDE.md` (names cannot contain spaces; no `kind` warnings, JSON Schema or streaming validation), then delete that guide. Update the Workflows README to match. | `docs/QUORUS_YAML_SYNTAX_GUIDE.md`, `docs/QUORUS_WORKFLOWS_README.md`, `quorus-integration-examples/.../docs/YAML-VALIDATION-GUIDE.md` | ⬜ |
| **DR-B5** | **Security Guide** (⏸ DR-Q3). Move §11–14 into the Service Connection Runbook and a new `docs/QUORUS_UPGRADE_NOTES.md`. Document gateway-only elevation, that `/api/v1/openapi.yaml` is public, which headers are actually required (`X-Quorus-Roles` and `X-Quorus-Scopes` default to empty), and that Raft peers are not bound to node IDs. Update §11 for R1-1. Replace `CONTROLLER_URL` with the current name. Note that the "audit path configured" and "trust-bundle version" checks are always satisfied by the packaged defaults. | `docs/QUORUS_SECURITY_DEPLOYMENT_GUIDE.md`, `docs/QUORUS_SERVICE_CONNECTION_OPERATIONS_RUNBOOK.md` | ⬜ |
| **DR-B6** | **Versioning Policy** (⏸ DR-Q5). Record the command and snapshot schema as version 3, readable from 0. Record the configuration-contract break in `b35fb25`. Define one product version and use it in the pom, `HttpApiServer.VERSION`, OpenAPI `info.version`, `quorus.version` and `quorus.agent.version` (§6 #13). | `docs-design/reference/QUORUS_VERSIONING_AND_COMPATIBILITY_POLICY.md`, poms, `HttpApiServer.java`, `quorus-controller-v1.yaml`, properties files | ⬜ |
| **DR-B7** | **Plan and register.** Settle the Phase 0 and Phase 4 status, correct the present-tense fixture statements, re-verify OBS-04/05/07/14, fix counts and revision order, cite the current plan, add IDs for the orphan plan items and the four configuration residuals, add the plan revision history and Phase 5–12 status lines, remove the machine path, handle the RocksDB coverage claim, and reconcile DEF-09/DOC-06, the OTel version and OBS-08. | `docs-design/task/` | ✅ Register v1.5, plan v1.28 (2026-09-26). The plan's revision history before v1.26 is noted as unrecorded rather than reconstructed. |

---

## 6. Phase C — Consolidate and archive

| ID | Task | State |
|---|---|---|
| **DR-C1** | Move the 12 `docs-design/dev/vertx5-advice/` files to the PeeGeeQ repository. Keep §1 of the Patterns Guide and the anti-pattern list of the general guide for DR-C3. Delete `performance/CRITICAL_PERFORMANCE_REFACTORING_GUIDELINES.md` (APEX). | ⬜ |
| **DR-C2** | Move to `docs-design/archive/`, each with a one-line "why archived" banner: the three `CONNECTION_POOL_*` documents; `VERTX5_PERFORMANCE_BENCHMARKS.md`; all six `vertx-migration/` files (keep SUMMARY as the one migration record); `testing/FTPS_INTEGRATION_TEST_INVESTIGATION.md` (mark it resolved first); `evidence/remediation-r4-r6-2026-09-05.md`; and the Configuration Handover (⏸ extract §2.1 and §8 into the configuration reference first, see DR-D1). | 🟨 Configuration Handover archived 2026-09-26 (extraction still pending under DR-D1); the rest remain. |
| **DR-C3** | Write `docs-design/dev/QUORUS_VERTX5_CONVENTIONS.md` based on what the code actually does, and link it from `.github/copilot-instructions.md`. | ⬜ |
| **DR-C4** | Merge `docs/QUORUS_CLUSTER_STARTUP_GUIDE.md`, `docs/QUORUS-DOCKER-TESTING-README.md` and `quorus-controller/DOCKER_BUILD_OPTIMIZATION.md` into `docker/README.md`. The result has one table giving each Compose file's topology, host ports, required environment and status, and it documents `--build-context m2cache`. Fix the Quick Start port (8080 is not mapped), use `docker compose` throughout, and fix the last link label. | ⬜ |
| **DR-C5** | Split `QUORUS_SYSTEM_DESIGN.md`. Move the enterprise requirements to the Architecture Spec (or delete them and link). Archive the PostgreSQL/Redis/etcd, Kubernetes, SQL, changelog, duplicated and file-organisation sections. Badge what remains. Rename `QuorusStateMachine` to `QuorusStateStore` throughout. Fix the environment names (`QUORUS_RAFT_*`), the `AppConfig` loading description, the tech-stack versions, the health JSON and the metric names. | ⬜ |
| **DR-C6** | Extract `docs-design/reference/QUORUS_RAFT_STORAGE_REFERENCE.md` from the Raft WAL design, with the contents listed in review §10: coordinates, layering, method contract, on-disk layout, every storage and snapshot key, recovery order, InstallSnapshot, operator rules, test map, and the unproven power-loss case. Archive the remainder. | ⬜ Scope narrowed on 2026-09-26: under ADR-0011 the in-repository engine and sidecar are replaced by QRaft (`CE-10`), so the reference should cover the current design briefly and link to QRaft's storage documentation rather than duplicate it |
| **DR-C7** | Trim the Simulators design. Rewrite §1 against current code. Relabel §2–7 as standalone test doubles. Restore the links to `RaftChaosTest`, `RaftFailureTest` and `InfrastructureSmokeTest`. Delete Appendix C. Document `MockRaftTransport`, update the `RaftTransport` listing, mark the DSL and full-stack examples as proposals, and tick the delivered Appendix D items. | ⬜ |
| **DR-C8** | Rewrite `QUORUS_NEGATIVE_TESTING_STRATEGY.md` around `@ExpectsError` / `ExpectsErrorExtension` (negative tests have run by default since `8864c2f`). Align `QUORUS_LOG_STYLE.md` with the code: ASCII markers, the TRACE levels, the real `logback-test.xml`, no `-Dtest.loglevel`, and no personal hostname, username or IP. Update `QUORUS_PROTOCOL_SERVERS_TESTING.md` (images, environment, the Testcontainers tests, `*IT` never runs) and `QUORUS_RAFT_CLUSTER_TESTING.md` (`--build-context m2cache`, `QUORUS_RAFT_*`, 5000/1000, raftlog 1.2.0, `quorus-loadbalancer`, `raft` read from the top level of `/health` rather than `checks.raft.state`, the JUnit Docker suites, the header). Add `ContainerRecreationDurabilityTest` to `DOCKER_TEST_PERFORMANCE.md` and record CPU and RAM. Have LOG_STYLE and NEGATIVE_TESTING link to the Testing README instead of carrying their own logback samples. | ⬜ |
| **DR-C9** | Scrub the PeeGeeQ references from `QuorusConfiguration.java:30`, `AppConfigNodeIdentityTest.java:82`, `VertxPerformanceBenchmark.java:45`, `scripts/add-license-headers.sh` and `scripts/setup-git-hooks.sh` (the hook checks `peegeeq-*` paths, so it does nothing in Quorus). Remove `vertx-pg-client` and `ConnectionPoolService`, and the unused `vertx-grpc-*` dependencies, or document why they stay (§6 #15, #22). | ⬜ |
| **DR-C10** | Clean the working copy: delete the local `temp/` worktrees, `.history/` and `hs_err_pid*.log`, and untrack the five `temp/*.txt` files still in git. Normalise line endings with a `* text=auto` rule, committed on its own. Merge NOTICE and OPEN_SOURCE_USAGE into one generated inventory (see DR-F01). | ⬜ DR-Q6 is decided. Delete `temp/` only after `docs-design/evidence/raw/` is committed, because until then the rescued copies are the only other copies. |

---

## 7. Phase D — Keep it accurate

| ID | Task | State |
|---|---|---|
| **DR-D1** | **Generate, don't copy.** Generate the "Current" endpoint table (REST §5–15, the API Reference skeleton and `InfoHandler`, §6 #14) from `quorus-controller-v1.yaml`. Generate `docs/QUORUS_CONFIGURATION_REFERENCE.md` from the properties files and the `AppConfig` / `AgentConfig` key constants, seeded from Configuration Handover §2.1 and §8. | ⬜ |
| **DR-D2** | **CI documentation checks:** relative-link checker; header linter for Version, Date and Status; ban on `C:\Users\` and similar personal paths; `docker compose config` on every `docker/compose/*.yml`; and a smoke job that starts the single-controller topology. | ⬜ |
| **DR-D3** | **One status vocabulary.** Implemented / Partial / Planned for capabilities; Current / Required / Planned for API items. Remove the seven ad-hoc values in Arch Spec §13. | ⬜ |
| **DR-D4** | **ADR hygiene** (⏸ DR-Q1, DR-Q2). Add ADR-0006 (raftlog-core WAL and snapshot sidecar), ADR-0007 (layered configuration, no system properties), ADR-0008 (schema-3 coordinated upgrade), ADR-0009 (trust-state scope), ADR-0010 (YAML semantics), and consider one for Raft over grpc-java rather than Vert.x gRPC. Add an index, Supersedes / Superseded-by fields and an Alternatives section. Fix ADR-0002's fencing statement, which is now out of date. | ⬜ ADR-0011 (consensus through QRaft) and ADR-0012 (leave Vert.x for Java 27) were added on 2026-09-26 outside this list. ADR-0006 should record only the current raftlog-and-sidecar design and point to ADR-0011 as its planned successor |
| **DR-D5** | **Definition of done:** any change to a public contract (endpoint, key, environment variable, Compose file or status) updates its canonical document in the same commit, and plans and registers cite a SHA only after the commit exists. Add this to plan §6 and to `.github/copilot-instructions.md`. | ⬜ |

---

## 8. Code defects outside Phases A–D

These §6 defects are not covered by any §9 action. Each is delivery work, delivered under the
plan's §6.1 TDD protocol. As of 2026-09-26 each is recorded in plan v1.28 and register Section I:
X06 → `SEC-03`, X07 → `SEC-04`, X09 → `SEC-05`, X11 → `ENG-01`, X17 and X20 → `ENG-06`,
X18 → `ENG-02`, X19 → `ENG-04`, X21 → `ENG-05`, X24 → `ENG-03`. X05 is the existing `ARCH-09`.

| ID | Sev. | Location | Task | State |
|---|---|---|---|---|
| **DR-X05** | M | `HttpTransferProtocol.java:71,199-250` | Stream HTTP downloads to an `AsyncFile` (for example `BodyCodec.pipe`) instead of buffering up to 10 GB on the heap. Same as QR-08 and ARCH-09. | ⬜ |
| **DR-X06** | M | `SftpTransferProtocol.java:406-408` | Direct-URI SFTP turns off host-key checking without any log output. Log a WARN, and refuse unless an explicit development flag is set. Closes the QR-03 residual. | ⬜ |
| **DR-X07** | M | `RaftPeerAuthorizationInterceptor` | Bind the Raft peer certificate subject or SAN to the `QUORUS_CLUSTER_NODES` identity, so that not every cluster-CA certificate can make Raft RPCs. | ⬜ |
| **DR-X09** | M | `AuthorizationPolicyEngine.java:78-107` (`roleAllows`) | Evaluate the union of all roles. Currently the first matching role returns, so {SECURITY, OPERATOR} is denied `transfers:*`. Add a multi-role test. | ⬜ |
| **DR-X11** | M | `JobAssignmentService` | Confirm that no main code constructs it. Then wire it in, or document where assignment timeouts are handled (relevant to P2-01). | ⬜ |
| **DR-X17** | L | `quorus-controller/pom.xml:263` | Correct the `mvn test -Dgroups=docker,slow` comment, which does not work while `excludedGroups` applies. | ⬜ |
| **DR-X18** | L | poms (⏸ DR-Q4) | Make `*IT` and `*Benchmark` classes run: add Failsafe, or rename and tag them. | ⬜ |
| **DR-X19** | L | `SimpleWorkflowEngine.java:88-89` | Deprecate or remove the public constructor that calls `Vertx.vertx()`. | ⬜ |
| **DR-X20** | L | `AppConfig.java:160`, `HttpTransferProtocol.java:58` | Remove the Javadoc mentions of the `memory` storage type and "blocking mode". | ⬜ |
| **DR-X21** | L | `quorus-workflow/src/main/resources/schema/workflow-schema.json` | Load it with `json-schema-validator`, or remove both the file and the dependency. | ⬜ |
| **DR-X24** | L | `QuorusAgent.java:372` | Check whether the `.join()` can run on an event loop (review §11 open item), and fix it if it can. | ⬜ |

---

## 9. Document fixes outside Phases A–D

| ID | Document | Fix | State |
|---|---|---|---|
| **DR-F01** | `NOTICE`, `OPEN_SOURCE_USAGE.md` | Until DR-C10 merges them: list only shipped runtime components in NOTICE, and add `vertx-pg-client`, Netty, `jackson-dataformat-yaml` and `javax.annotation-api` (CDDL). In OPEN_SOURCE_USAGE, correct RaftLog Core to 1.2.0, remove RocksDB JNI, add `javax.annotation-api`, and fix the `LICENSE-HEADER.txt` reference. Confirm that the "licenses directory" exists. | ⬜ |
| **DR-F02** | `docs/QUORUS_USER_GUIDE.md` | Remove progress, events and attempts from the gaps list. `QUORUS_AGENT_TENANT_ID` takes priority over the legacy `AGENT_TENANT_ID`. State that the agent defaults to the production profile with TLS. Add an NFS section. (DR-A7 covers the authentication statement.) | ⬜ |
| **DR-F03** | `docs/QUORUS_ARCHITECTURE_QUICKSTART.md` | Link to the API Reference instead of listing endpoints. State the packaged `127.0.0.1` and production-profile defaults. Use `maven.compiler.release`. | ⬜ |
| **DR-F04** | `docs/QUORUS_INTEGRATION_EXAMPLES_README.md` | Add an `mvn install` step, mention the default `mainClass` (`SftpFtpRealImplementationDemo`), and add `IntegrationTestSuite`. | ⬜ |
| **DR-F05** | `docs/QUORUS_SERVICE_CONNECTION_OPERATIONS_RUNBOOK.md` | Add the R4 and R5 behaviour (DNS 503/504/409, FTPS 21 vs 990, partial updates, event paging) and the elevation requirement. Correct the "must set" statement for agent pool and roots, which are not enforced. Coordinate with DR-B5. | ⬜ |
| **DR-F06** | `docs/QUORUS_CERTIFICATE_INCIDENT_RUNBOOK.md` | Add an example request body and elevation header, and explain how to start a new audit chain by repointing `quorus.security.audit.evidence-path`. | ⬜ |
| **DR-F07** | `docs/QUORUS_CODEBASE_AND_DOCUMENTATION_REVIEW_2026-08-31.md` | Append the QR-01 to QR-11 status table from review §5. Create `docs-design/reviews/` and move this review and the 2026-09-24 review into it. Fix the GitHub line anchors, which do not resolve in rendered Markdown. | ✅ 2026-09-26 — moved to `docs-design/reviews/`, status annex appended, Markdown line anchors removed. |
| **DR-F08** | `docs-design/README.md` | Bump the header date to match the body, add `evidence/`, `architecture-decisions/`, `reference/` and `reviews/` to the directory table, and add this task list. | ✅ 2026-09-26 — header v1.1; directory table and task list added. |
| **DR-F09** | `reference/QUORUS_REPRODUCIBLE_BUILD_AND_EVIDENCE.md` | Add `project.build.outputTimestamp` to the poms, or say the build is repeatable rather than byte-reproducible. Update the evidence figures from M0 (2,212) to R6 (2,437). | ⬜ |
| **DR-F10** | `evidence/` small fixes | `full-suite-error-remediation`: cite `a0103a0`. `r4-dns-remediation`: replace "changes are uncommitted" with `8b3cf5c`. `r5-closure`: add the closure commit. `r6-final-acceptance`: map `b604505` to `dc447d4` and correct the "transport failure retained" claim (`RaftNode.java:602-616`). `raftlog-validation-handover`: add a supersession pointer to R1-1. Add header blocks to the 2026-09-05 files. | ⬜ |
| **DR-F11** | `performance/QUORUS_PERFORMANCE_VALIDATION_RESULTS.md` | Relabel it as a Vert.x `executeBlocking` micro-benchmark. Remove the "Phase 4 PostgreSQL" and "quorus-api 7 tests" claims, and align its targets with the test's assertions. | ⬜ |
| **DR-F12** | `testing/QUORUS_TESTING_README.md` | Remove `quorus-api` from the quick-build `-pl` list and the consolidated-log module list. Remove `-Dgroups='!flaky'`. Say that `*IT` classes do not run in a default build (see DR-Q4). Describe the Testcontainers-based upload tests. | ⬜ |
| **DR-F13** | `QUORUS_CONFIGURATION_ISOLATION_HANDOVER_2026-09-03.md` | Before archiving (DR-C2): fix the broken `../../raftlog/pom.xml` link and remove the personal path. The six orphaned SHAs are covered by DR-A2. | ✅ 2026-09-26 — link replaced with plain text, personal path removed, archived with a banner. |
| **DR-F14** | All live documents | Remove `C:\Users\mraysmit\…` paths (README, plan, Configuration Handover, Raft Cluster Testing). | 🟨 Removed from the plan and the Configuration Handover on 2026-09-26; README and Raft Cluster Testing remain. |
| **DR-F15** | `docs-design/design/QUORUS_RAFT_WAL_DESIGN.md` (if not fully archived by DR-C6) | Replace the stale method names with `updateMetadata`, `appendEntries`, `truncateSuffix` and `sync`. Mark §13.9's soft limit as not implemented. Replace the Vert.x 4 `executeBlocking` listings. Cite raftlog 1.2.0, and fix the "JRE 21" comment. | ⬜ |
| **DR-F16** | Test classification | Record in the Testing README that six Testcontainers tests run in default builds without a `docker` tag, and add `ContainerRecreationDurabilityTest` and `docker-compose-3node-durable.yml` to the testing documents. | ⬜ |
| **DR-F17** | `.gitattributes` / working tree | Covered by DR-C10: about 600 files show as modified only because of CRLF churn. Commit the `* text=auto` normalisation on its own so that it does not hide real changes. | ⬜ |

---

## 10. Out of scope for this list

- Evidence held in the separate raftlog repository (library SHAs, the "41 storage tests / 319 library tests" claims). The review did not verify these.
- The review's §11 partial items (per-module test-class counts; the overlap between the `testing/` documents and the Docker Testing README). DR-C4 and DR-C8 absorb the overlap.

### Revision history

| Version | Date | Changes |
|---|---|---|
| 1.7 | 2026-09-26 | Noted ADR-0011 and ADR-0012 against DR-D4 and narrowed DR-C6 because the in-repository Raft storage is due for replacement by QRaft |
| 1.6 | 2026-09-26 | Completed DR-B7, DR-F07, DR-F08 and DR-F13; DR-C2 and DR-F14 in progress; recorded the §8 and DR-A4 register IDs; added and decided DR-Q6: cited raw evidence is committed under `docs-design/evidence/raw/`, never kept in `temp/`, and the surviving historical logs were rescued there; added per-section state counts; repointed links after the reviews moved to `docs-design/reviews/` |
| 1.5 | 2026-09-25 | Marked DR-A3 complete after removing the APEX prompt material and reconciling Copilot guidance with the current modules, schema, routes, protocol execution and test-concurrency migration state |
| 1.4 | 2026-09-25 | Marked DR-A7 complete after reconciling the remaining stale authentication statements and the Architecture authentication verification gate |
| 1.3 | 2026-09-25 | Marked DR-A5 complete after all Compose models validated and the generated-certificate mTLS topology passed authenticated readiness and missing-certificate rejection checks |
| 1.2 | 2026-09-25 | Marked DR-A6 complete after workflow validation and a clean Compose/REST lifecycle smoke test; recorded the corrected agent status and attempt/fencing examples |
| 1.1 | 2026-09-25 | Recorded DR-Q2 and DR-A2 complete; DR-A1, DR-A4–A7 and DR-B1–B2 in progress; added verification results and remaining work |
| 1.0 | 2026-09-25 | Initial task list from the 2026-09-24 documentation review: 5 decisions, 29 phased actions, 11 code defects and 17 document fixes outside the phases |
