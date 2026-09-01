<img src="../docs/quorus-logo.png" alt="Quorus" width="120"/>

# Quorus Design and Engineering Documents

**Version:** 1.0  
**Date:** 2026-09-01  
**Author:** Mark Ray-Smith — Cityline Ltd  
**License:** Apache 2.0  
**Status:** Documentation scope and precedence  
**Scope:** Material under `docs-design`

Documents under `docs-design` preserve design proposals, implementation plans, migration work, performance investigations, testing notes, and historical reviews. They are engineering evidence and working material; they are not collectively the current Quorus runtime contract.

The controlling documents are:

1. [Quorus Architecture Specification](../docs/QUORUS_ARCHITECTURE_SPECIFICATION.md) for current architecture, security, consistency, observability, and production requirements.
2. [Quorus REST API Specification](../docs/QUORUS_REST_API_SPECIFICATION.md) for the complete normative control and operations API.
3. [Quorus HTTP API Reference](../docs/QUORUS_API_REFERENCE.md) for endpoints registered by the current controller.
4. [Quorus YAML Syntax Guide](../docs/QUORUS_YAML_SYNTAX_GUIDE.md) for fields accepted by the current workflow parser.
5. [Quorus Security Deployment Guide](../docs/QUORUS_SECURITY_DEPLOYMENT_GUIDE.md) for the implemented Phase 1 trust configuration.
6. [Quorus Certificate and Trust Incident Runbook](../docs/QUORUS_CERTIFICATE_INCIDENT_RUNBOOK.md) for containment and controlled recovery.

The current phased delivery roadmap is [Quorus Enterprise Implementation Plan](task/QUORUS_ENTERPRISE_IMPLEMENTATION_PLAN.md). It sequences the canonical requirements but does not override them.

## Directory Status

| Directory | Status | How to interpret it |
|---|---|---|
| `design/` | Non-normative design material | May combine implemented, superseded, and target-state concepts; canonical specifications take precedence |
| `task/` | Working and point-in-time plans | Completion markers describe the plan at its recorded date, not current production conformance |
| `testing/` | Engineering test guidance and investigations | Demonstrates specific test procedures; does not establish security, availability, or production readiness by itself |
| `performance/` | Point-in-time benchmark and optimization records | Claims apply only to the measured component, workload, hardware, and date |
| `dev/` | Migration and external engineering advice | Often scoped to Vert.x migration or PeeGeeQ rather than the current Quorus product contract |
| `archive/` | Historical | Retained for provenance and must not be used as current implementation guidance |

## Interpretation Rules

- A historical `COMPLETE` or `PRODUCTION READY` label does not close a canonical conformance gap.
- A proposed endpoint does not exist unless it appears as `Current` in the HTTP API reference.
- Infrastructure health, logs, metrics, and traces support operations but do not replace per-transfer progress, deadlines, stall detection, alerts, attempts, and timelines.
- Security diagrams and plans do not establish implemented authentication, TLS/mTLS, service trust, secret handling, or secure agent lifecycle controls.
- PostgreSQL, Redis, and etcd diagrams are not authoritative controller-state designs; the current authority is Raft log and snapshots.
- Cloud-storage protocols, dynamic Raft membership, autonomous route triggers, agent-to-agent streaming, and duplicate-safe automatic reassignment remain unavailable unless the canonical architecture marks them implemented.
