<img src="../docs/quorus-logo.png" alt="Quorus" width="120"/>

# Quorus Design and Engineering Documents

**Version:** 1.2  
**Date:** 2026-10-03  
**Author:** Mark Ray-Smith — Cityline Ltd  
**License:** Apache 2.0  
**Status:** Documentation scope and precedence  
**Scope:** Material under `docs-design`

Documents under `docs-design` preserve design proposals, implementation plans, migration work, performance investigations, testing notes, and historical reviews. They are engineering working material; they are not collectively the current Quorus runtime contract.

The controlling documents are:

1. [Quorus Architecture Specification](../docs/QUORUS_ARCHITECTURE_SPECIFICATION.md) for current architecture, security, consistency, observability, and production requirements.
2. [Quorus REST API Specification](../docs/QUORUS_REST_API_SPECIFICATION.md) for the complete normative control and operations API.
3. The [bundled OpenAPI contract](../quorus-controller/src/main/resources/openapi/quorus-controller-v1.yaml), also served at `GET /api/v1/openapi.yaml`, for the endpoints the current controller registers (decision `DR-Q7`).
4. [Quorus YAML Syntax Guide](../docs/QUORUS_YAML_SYNTAX_GUIDE.md) for fields accepted by the current workflow parser.
5. [Quorus Security Deployment Guide](../docs/QUORUS_SECURITY_DEPLOYMENT_GUIDE.md) for the implemented Phase 1 trust configuration.
6. [Quorus Certificate and Trust Incident Runbook](../docs/QUORUS_CERTIFICATE_INCIDENT_RUNBOOK.md) for containment and controlled recovery.

The current phased delivery roadmap is [Quorus Enterprise Implementation Plan](task/QUORUS_ENTERPRISE_IMPLEMENTATION_PLAN.md). It sequences the canonical requirements but does not override them.

Outstanding work across every planning document is consolidated in the [Quorus Outstanding Work Register](task/QUORUS_OUTSTANDING_WORK_REGISTER.md). The register is the single task list: it holds the documentation remediation from the 2026-09-24 and 2026-10-02 reviews (Section H) and a decision log (§3). The alpha plan, Stage 6 security and routes plan, OpenTelemetry plan, sealed-record design and configuration isolation handover are in `archive/`, with their open items carried into the register.

The record of each implementation slice is its commit message (decision `DR-Q6`); there is no separate evidence directory.

## Directory Status

| Directory | Status | How to interpret it |
|---|---|---|
| `design/` | Non-normative design material | May combine implemented, superseded, and target-state concepts; canonical specifications take precedence |
| `task/` | Live planning: the delivery roadmap and the outstanding-work register, which is the single task list | Completion markers describe the plan at its recorded date, not current production conformance |
| `reviews/` | Point-in-time codebase and documentation reviews | Findings describe the reviewed revision; follow-up status lives in the register |
| `architecture-decisions/` | Architecture decision records | A decision stands until a later ADR supersedes it |
| `reference/` | Engineering policies and reference material (versioning, reproducible builds, commit rewrite map) | Current unless the document states otherwise |
| `testing/` | Engineering test guidance and investigations | Demonstrates specific test procedures; does not establish security, availability, or production readiness by itself |
| `performance/` | The benchmark specification, its results log, and point-in-time optimization records | Claims apply only to the measured component, workload, hardware, and date |
| `dev/` | Engineering conventions for current code | Current unless the document states otherwise |
| `archive/` | Historical | Retained for provenance and must not be used as current implementation guidance |

## Interpretation Rules

- A historical `COMPLETE` or `PRODUCTION READY` label does not close a canonical conformance gap.
- A proposed endpoint does not exist unless the bundled OpenAPI contract declares it.
- Infrastructure health, logs, metrics, and traces support operations but do not replace per-transfer progress, deadlines, stall detection, alerts, attempts, and timelines.
- Security diagrams and plans do not establish implemented authentication, TLS/mTLS, service trust, secret handling, or secure agent lifecycle controls.
- PostgreSQL, Redis, and etcd diagrams are not authoritative controller-state designs; the current authority is the Raft log and snapshots.
- Cloud-storage protocols, dynamic Raft membership, autonomous route triggers, automatic agent assignment, agent-to-agent streaming, and duplicate-safe automatic reassignment remain unavailable unless the canonical architecture marks them implemented.
