<img src="../../docs/quorus-logo.png" alt="Quorus" width="120"/>

# Quorus Enterprise Implementation Plan

**Version:** 1.0  
**Date:** 2026-09-01  
**Author:** Mark Ray-Smith — Cityline Ltd  
**License:** Apache 2.0  
**Status:** Proposed phased delivery plan  
**Scope:** Enterprise control plane, transfer operations, security, governance, deployment, and user interfaces

## 1. Purpose and Authority

This plan defines the phased implementation path from the current Quorus alpha baseline to an evidence-backed enterprise release. It turns the requirements in the following documents into sequenced engineering work:

- [Quorus Architecture Specification](../../docs/QUORUS_ARCHITECTURE_SPECIFICATION.md)
- [Quorus REST API Specification](../../docs/QUORUS_REST_API_SPECIFICATION.md)
- [Quorus Comprehensive System Design](../design/QUORUS_SYSTEM_DESIGN.md)
- [Quorus HTTP API Reference](../../docs/QUORUS_API_REFERENCE.md)

The architecture and REST API specifications remain normative. This plan controls delivery order and exit evidence; it does not weaken a canonical requirement. Historical completion markers in older plans do not close current conformance gaps.

Calendar dates are deliberately not assigned until team size, deployment platform, enterprise identity provider, secrets provider, evidence-retention platform, and pilot scope are agreed. Relative size indicates expected breadth, not a commitment:

- **S:** contained change with limited schema and operational impact;
- **M:** cross-module capability with API, persistence, and test changes;
- **L:** platform workstream with security, migration, failure, and operational implications;
- **XL:** multiple coordinated workstreams or an enterprise release milestone.

## 2. Delivery Principles

Every phase follows these rules:

1. **Security and operations are product behavior.** They are not deferred documentation or deployment concerns.
2. **Transfer-process outcomes come first.** Infrastructure signals support transfer progress, deadlines, integrity, publication, and recovery.
3. **Raft remains authoritative.** New authoritative controller state is introduced through versioned commands, snapshots, migrations, and invariants.
4. **File bytes stay out of controllers and REST.** Agents connect directly to governed enterprise services.
5. **Secrets remain external.** Quorus stores opaque references and redacted metadata only.
6. **Current and target behavior stay distinct.** Documentation and OpenAPI coverage change in the same delivery as implementation.
7. **Failure paths are first-class.** Quorum loss, retries, stale agents, timeouts, partial files, lost acknowledgements, and rollback are tested before success claims.
8. **Backward compatibility is explicit.** Raft entries, snapshots, APIs, configuration, workflow definitions, and agent protocols have version and migration rules.
9. **No phase is complete on code alone.** Tests, audit, telemetry, runbooks, threat-model updates, migration, and operator evidence are part of the exit gate.
10. **The user interface uses public contracts.** It receives no private database, filesystem, Raft, or controller-internal access.

## 3. Current Baseline

The current baseline provides:

- Java 25 and Vert.x 5 modules;
- direct transfer execution through protocol adapters;
- YAML workflow parsing and in-process workflow execution;
- controller-local HTTP with Raft-replicated transfer, assignment, agent, and route commands;
- agent registration, heartbeat, work polling, execution, and compatibility status reporting;
- health, Raft status, aggregate metrics, and selected OpenTelemetry instrumentation;
- tenant models, quota services, route models, and supporting examples.

The baseline does not yet justify protected enterprise production use. The critical blockers include unauthenticated identities, incomplete TLS/mTLS boundaries, incomplete assignment lifecycle, no attempt fencing, incomplete transfer operations telemetry, uncontrolled service connectivity, incomplete agent trust lifecycle, and incomplete REST coverage.

## 4. Target Release Milestones

| Milestone | Completed phases | Release meaning |
|---|---|---|
| **M0 — Reproducible Alpha Baseline** | Phase 0 | Clean, repeatable baseline with corrected current lifecycle and durability defects |
| **M1 — Secure Transfer Core** | Phases 1–2 | Authenticated control plane and explainable, fenced transfer attempts |
| **M2 — Operational Beta** | Phases 3–5 | Critical transfers are observable; services and agents have governed trust lifecycles |
| **M3 — Enterprise Control Plane** | Phases 6–8 | Complete API, automated route/workflow operation, and evidenced recovery behavior |
| **M4 — Enterprise Operations Candidate** | Phases 9–11 | Governance, integrations, supportability, and role-specific user interfaces are available |
| **M5 — Enterprise Release Candidate** | Phase 12 | All release gates pass in a representative pilot environment |

No milestone may be described as production ready solely because its feature list is complete.

## 5. Phase Dependency Map

```mermaid
flowchart LR
    P0[Phase 0<br/>Baseline and Conformance] --> P1[Phase 1<br/>Identity and Trust]
    P0 --> P2[Phase 2<br/>Transfer Correctness]
    P2 --> P3[Phase 3<br/>Transfer Operations Telemetry]
    P1 --> P4[Phase 4<br/>Service Connectivity and Secrets]
    P1 --> P5[Phase 5<br/>Agent Lifecycle and Deployment]
    P4 --> P5
    P2 --> P6[Phase 6<br/>Complete REST Control Plane]
    P3 --> P6
    P4 --> P6
    P5 --> P6
    P6 --> P7[Phase 7<br/>Routes, Workflows, and Calendars]
    P2 --> P8[Phase 8<br/>Durability, HA, and DR]
    P0 --> P8
    P1 --> P9[Phase 9<br/>Governance, Audit, and Integrations]
    P3 --> P9
    P6 --> P9
    P8 --> P10[Phase 10<br/>Supportability and Service Management]
    P3 --> P10
    P5 --> P10
    P6 --> P10
    P7 --> P11[Phase 11<br/>Admin and Operations UI]
    P9 --> P11
    P10 --> P11
    P11 --> P12[Phase 12<br/>Enterprise Validation and Release]
    P7 --> P12
    P8 --> P12
```

Phases 1 and 2 may run in parallel after Phase 0 if their shared identity, command-versioning, and API decisions are agreed first. Phase 8 can begin early as a separate durability workstream but cannot finish until the attempt reconciliation model exists.

## 6. Cross-Cutting Definition of Done

A capability is complete only when all applicable items are satisfied:

- domain model and state transitions are specified;
- Raft command and snapshot schemas are versioned;
- referential, tenant, state, and authorization invariants are enforced during authoritative state application;
- safe migration and rollback behavior is documented and tested;
- OpenAPI 3.1 paths, schemas, scopes, examples, and problem responses are updated;
- idempotency, concurrency, leader, quorum-loss, and retry behavior is tested;
- unit, component, integration, protocol, multi-node, security, and failure tests pass as applicable;
- metrics, traces, domain events, audit events, alert conditions, and redaction are implemented;
- runbooks cover normal operation, expected failure, recovery, rollback, and escalation;
- threat model and data-flow diagrams are updated;
- no secret values appear in state, logs, telemetry, fixtures, exports, or support bundles;
- current documentation distinguishes implemented behavior from the remaining target state;
- measurable exit evidence is retained with build, configuration, environment, and artifact digests.

## 7. Phase 0 — Baseline, Conformance, and Delivery Controls

**Size:** L  
**Milestone:** M0  
**Primary gaps:** `ARCH-01`, `ARCH-05`, `ARCH-06`, `ARCH-07`, `API-01`, `API-12`

### Objective

Create one reproducible, durable, end-to-end distributed transfer path and the engineering controls needed to change authoritative state safely.

### Scope

1. Establish a clean Java 25 build and test baseline across all active modules.
2. Correct the agent success lifecycle so `ACCEPTED -> IN_PROGRESS -> COMPLETED` is observable and legal.
3. Enforce transfer, agent, assignment, route, and tenant referential invariants inside state-machine application.
4. Resolve and test the controller data path and container volume alignment.
5. Inventory every current endpoint and create the initial OpenAPI 3.1 contract.
6. Introduce standard problem responses, correlation identifiers, request limits, and redaction rules.
7. Define command, snapshot, API, configuration, workflow, and agent-protocol versioning policy.
8. Establish CI lanes for unit, integration, protocol, multi-node, security, documentation, and contract checks.
9. Add architecture-decision records for event storage, progress checkpointing, identity boundary, secret providers, and deployment ownership.

### Deliverables

- reproducible build instructions and locked toolchain baseline;
- one automated tenant-scoped transfer from submission through assignment, acceptance, running, progress, completion, persistence, restart, and API verification;
- corrected persistent-volume deployment and restart evidence;
- authoritative invariant test suite, including missing references and cross-tenant commands;
- initial OpenAPI document containing every registered path;
- standard error and correlation contract on current endpoints;
- schema-version registry and compatibility test harness;
- release evidence manifest that records code revision, build, artifacts, configuration, tests, and environment.

### Verification

- clean checkout build passes twice without relying on previous local artifacts;
- three-controller test commits a transfer, restarts all controllers, and recovers the same authoritative state;
- invalid and cross-tenant commands produce no state change on any node;
- agent success emits `IN_PROGRESS` before `COMPLETED`;
- all registered paths match OpenAPI and all declared current paths are registered;
- documentation link, header, fence, endpoint, and current-versus-required checks run in CI.

### Exit gate

M0 is achieved when the reference transfer path is repeatable, durable, state-machine invariant tests pass, and the current API is machine-described. No external untrusted exposure is permitted yet.

## 8. Phase 1 — Enterprise Identity, Authorization, and Transport Trust

**Size:** XL  
**Milestone:** contributes to M1  
**Primary gaps:** `ARCH-03`, `ARCH-08`, `ARCH-13`, `API-02`

### Objective

Establish authenticated and encrypted trust boundaries for human callers, service integrations, controllers, and agents.

### Scope

1. Select the enterprise identity boundary: trusted gateway plus protected identity assertions, or controller-native OIDC validation where required.
2. Define human, service integration, controller, agent, and deployment identities.
3. Implement TLS for controller HTTP and mutual TLS for Raft and agent control traffic.
4. Implement certificate validation, hostname verification, trust bundles, expiry monitoring, rotation, and revocation.
5. Introduce the canonical scopes and resource-policy evaluation model.
6. Derive tenant and environment scope from trusted identity rather than caller-supplied fields.
7. Add RBAC and ABAC enforcement for tenant, business service, environment, action, resource ownership, and data classification.
8. Establish separation-of-duties roles and time-bounded privileged elevation.
9. Create the immutable audit-event foundation for authentication, authorization, denial, mutation, and privileged reads.
10. Make insecure development profiles explicit, visibly warned, disabled by default, and impossible in production profiles.

### Deliverables

- identity and trust-boundary architecture decision;
- TLS/mTLS configuration, trust-bundle handling, and automated certificate rotation tests;
- `security/me` and authorization-explanation API foundations;
- policy engine with stable decisions and denial codes;
- authenticated tenant derivation and uniform authorization middleware;
- controller, agent, service-integration, operator, administrator, security, auditor, and deployment roles;
- immutable redacted audit-event schema;
- security deployment guide and certificate incident runbooks.

### Verification

- unauthenticated, expired, revoked, wrong-tenant, wrong-environment, and wrong-role calls fail closed;
- Raft peers reject unknown or revoked controller identities;
- agents reject untrusted controllers and controllers reject untrusted agents;
- authorization is enforced on collections, item resources, streams, exports, and actions;
- certificate rotation occurs without unauthorized overlap or loss of active trusted control;
- production profile cannot enable disabled verification or plaintext control traffic.

### Exit gate

Every production trust-boundary connection is authenticated, encrypted, peer-verified, authorized, and audited. Tenant identity is no longer derived solely from request content.

## 9. Phase 2 — Transfer Attempts, Fencing, Integrity, and Reconciliation

**Size:** XL  
**Milestone:** M1  
**Primary gaps:** `ARCH-01`, `ARCH-02`, `ARCH-05`, `ARCH-06`, `ARCH-17`, `API-03`, `API-07`, `API-12`

### Objective

Make distributed transfer outcomes explainable and safe under retries, failover, delayed agents, lost acknowledgements, and uncertain publication.

### Scope

1. Introduce immutable transfer attempts with `attemptId`, agent, sequence, lease, fencing generation, and outcome.
2. Define canonical transfer and attempt state machines with validated transitions.
3. Implement assignment offer, accept, reject, start, progress, complete, fail, cancel, and lease-renew actions.
4. Persist expected state, report sequence, and fencing token checks in authoritative application.
5. Add idempotency keys to submission and retriable control operations.
6. Define retry classification, maximum attempts, maximum elapsed time, backoff, jitter, and terminal conditions.
7. Add destination staging, integrity verification, publication, overwrite, partial-file, and cleanup policies.
8. Implement reconciliation for lease expiry, agent disappearance, controller failover, timeout, lost completion, and ambiguous publication.
9. Define protocol-specific retry and resume safety; fail unsupported capabilities before execution.
10. Migrate existing jobs and assignments to the versioned attempt model.

### Deliverables

- versioned transfer, attempt, assignment, lease, integrity, and publication schemas;
- attempt-aware agent protocol and REST resources;
- lease scheduler and authoritative fencing validation;
- idempotency store scoped to identity, tenant, request fingerprint, and expiry;
- staged publication abstraction with protocol capability checks;
- reconciliation service and operator actions;
- immutable attempt history and classified terminal reasons;
- migration and rollback tooling for existing snapshots and records.

### Verification

- delayed or partitioned agents cannot update or publish after losing the active fence;
- duplicate submission with the same idempotency fingerprint returns the original result;
- reuse of an idempotency key with different content fails;
- controller failover during each lifecycle transition produces one authoritative outcome;
- integrity failure never becomes `SUCCEEDED`;
- publication uncertainty becomes `RECONCILIATION_REQUIRED`, not an assumed success or blind retry;
- crash, network partition, lost response, lease expiry, and duplicate-report tests pass;
- every terminal transfer has complete attempt and publication evidence.

### Exit gate

Quorus can safely explain what ran, where it ran, which attempt is authoritative, what was published, and what requires reconciliation. It still does not claim exactly-once external execution.

## 10. Phase 3 — Critical Transfer Operations Telemetry and Alerting

**Size:** XL  
**Milestone:** contributes to M2  
**Primary gaps:** `ARCH-11`, `ARCH-12`, `API-03`, `API-04`

### Objective

Give technology operations teams a current, end-to-end operational view of every critical and time-sensitive transfer.

### Scope

1. Extend transfer context with business service, owner, criticality, environment, processing date, expected start, required completion time, and runbook.
2. Define the ordered transfer event vocabulary and event schema.
3. Separate authoritative lifecycle events from high-frequency telemetry samples and define retention for both.
4. Implement monotonic progress, throughput windows, ETA, confidence, telemetry freshness, and unknown-size behavior.
5. Implement deadline risk, late, stalled, degraded, and unknown conditions independently of lifecycle state.
6. Build transfer timeline, attempt, integrity, publication, and reconciliation read models.
7. Implement durable event query and resumable filtered server-sent event delivery.
8. Implement actionable alert policy, deduplication, acknowledgement, suppression, escalation, resolution, and notification-delivery evidence.
9. Control metrics cardinality and protect the control plane from slow consumers and event floods.
10. Add service-level reports for timeliness, success, retry, integrity, publication, alert response, and telemetry completeness.

### Deliverables

- transfer context and deadline model;
- domain event ledger and operational read models;
- progress, ETA, risk, stall, degradation, and telemetry-freshness engines;
- critical, at-risk, late, stalled, and degraded query APIs;
- alert resources and notification-delivery state;
- resumable event stream with gap notification and backpressure;
- operator runbooks and alert policy catalogue;
- retention, archival, and performance design for events and samples.

### Verification

- test transfers of known and unknown size produce correct progress semantics;
- lost telemetry becomes `UNKNOWN` or `DEGRADED`, never zero progress;
- stalled and deadline-risk transfers are detected within the configured policy window;
- timeline order remains correct across retries, controller failover, and agent restart;
- event replay resumes from the last acknowledged event and explicitly reports retention gaps;
- slow consumers cannot exhaust controller memory;
- every critical alert has owner, evidence, deadline impact, runbook, delivery state, and acknowledgement history.

### Exit gate

Operations can detect, understand, own, and act on a critical transfer before its deadline is missed. Infrastructure monitoring alone is not accepted as completion.

## 11. Phase 4 — Governed Service Connections, Egress, and Secrets

**Size:** XL  
**Milestone:** contributes to M2  
**Primary gaps:** `ARCH-08`, `ARCH-14`, `ARCH-17`, `API-06`

### Objective

Ensure that agents connect only to approved enterprise services using verified peer identities, permitted network paths, and externally managed secrets.

### Scope

1. Implement tenant-scoped service connection aliases stored as authoritative redacted metadata.
2. Define protocol, endpoint, port, network zone, paths, transfer direction, allowed agent pools, owner, environment, and classification policy.
3. Integrate at least one enterprise secrets provider through opaque references.
4. Add trust policy for TLS certificates, approved CAs, hostname verification, SSH host keys, and pinned fingerprints.
5. Enforce default-deny egress, DNS and resolved-address policy, redirect restrictions, ports, protocols, and path scopes.
6. Enforce policy before secret retrieval and again on the executing agent before connection.
7. Implement validation and staged connection tests with redacted DNS, route, negotiation, identity, authentication, and authorization results.
8. Remove credential-bearing URI behavior from production flows and migration paths.
9. Add rotation, expiry, last-use, failure, revocation, and trust-change events.
10. Harden SFTP, HTTPS, FTPS, SMB/CIFS, and NFS adapters against their protocol-specific trust requirements.

### Deliverables

- service connection, trust, egress, and secret-reference schemas;
- service connection and secret-reference REST resources;
- secrets-provider service provider interface and first production provider;
- controller and agent policy-enforcement components;
- connection-validation and test operations;
- managed known-hosts or host-key pinning for SFTP;
- URI credential detector and migration tooling;
- security events, audit evidence, and incident runbooks.

### Verification

- unapproved aliases, hosts, resolved addresses, redirects, ports, protocols, paths, and agent pools fail before secret retrieval;
- unknown or changed SSH host keys fail closed;
- invalid TLS chains and hostnames fail closed;
- secret values never appear in API, Raft state, snapshots, logs, traces, metrics, fixtures, or support bundles;
- revoked or rotated secrets and trust material take effect within the defined interval;
- DNS rebinding and server-side request-forgery tests fail safely.

### Exit gate

Every production service connection has explicit ownership, identity verification, network and path policy, secret reference, audit history, and tested failure behavior.

## 12. Phase 5 — Secure Agent Enrollment, Deployment, and Fleet Operations

**Size:** XL  
**Milestone:** M2  
**Primary gaps:** `ARCH-15`, `ARCH-16`, `API-05`

### Objective

Make every agent artifact and workload identifiable, attestable, governable, safely upgradeable, and revocable.

### Scope

1. Build reproducible signed agent artifacts with digest, SBOM, provenance, and vulnerability result.
2. Implement artifact admission policy and approved-version catalogue.
3. Replace unrestricted alpha registration with constrained short-lived enrollment.
4. Bind unique agent identity to tenant, environment, pool, capabilities, and effective service policy.
5. Add posture, version, certificate expiry, configuration version, capacity, and capability reporting.
6. Implement drain, resume, quarantine, identity rotation, revocation, and decommissioning.
7. Implement staged deployments with canary selection, health gates, failure thresholds, pause, resume, and rollback.
8. Harden agent runtime: non-root, minimal image, read-only filesystem where practical, restricted temporary storage, bounded resources, and default-deny network policy.
9. Define compatibility negotiation between controller, agent protocol, configuration, and artifact version.
10. Add compromised-agent incident and emergency-revocation procedures.

### Deliverables

- signed build pipeline, SBOM, provenance, and admission policy;
- agent enrollment, inventory, posture, event, and effective-policy resources;
- rollout and deployment operation resources;
- drain, rotation, quarantine, revocation, rollback, and decommissioning workflows;
- hardened reference container and orchestration policies;
- fleet compatibility and upgrade matrix;
- fleet security dashboards as API read models, not UI-only logic.

### Verification

- unsigned, altered, incompatible, or policy-rejected artifacts cannot enroll or run;
- enrollment authority is short-lived, constrained, single-purpose, and revocable;
- compromised identity loses controller and service access within the target interval;
- drain prevents new assignments and accounts for every active attempt;
- failed canary pauses rollout automatically;
- rollback restores an approved version without losing authoritative assignment evidence;
- decommissioning requires drain and revocation and preserves retained audit history.

### Exit gate

The fleet can be securely admitted, operated, upgraded, rolled back, isolated, and removed through supported APIs without direct host administration.

## 13. Phase 6 — Complete REST Control Plane and Integration Contract

**Size:** XL  
**Milestone:** contributes to M3  
**Primary gaps:** `ARCH-18`, `API-01`, `API-03`, `API-08`, `API-09`, `API-11`, `API-12`, `API-13`, `API-14`

### Objective

Complete the supported, versioned interface for every system control and observation required by automation, operations, security, audit, and future user interfaces.

### Scope

1. Consolidate transfer, attempt, telemetry, service connection, agent, and security APIs delivered by earlier phases.
2. Add workflow definition, validation, plan, execution, step, event, and control resources.
3. Add tenant, hierarchy, quota, reservation, usage, and effective-policy resources.
4. Add route validation, activation, execution history, and event resources.
5. Add audit query and evidence-export resources.
6. Add cluster, replication, snapshot, and redacted effective-configuration resources.
7. Complete idempotency, ETag, precondition, pagination, filtering, sorting, field selection, asynchronous operation, stable problem, rate-limit, and quota conventions.
8. Implement leader discovery and explicit read consistency.
9. Implement version, compatibility, deprecation, retention, and event-replay contracts.
10. Generate client libraries and consumer-driven contract tests for approved integration languages.

### Deliverables

- complete OpenAPI 3.1 contract and reusable schemas;
- implementation coverage report for every declared endpoint;
- generated clients and compatibility suite;
- standard API middleware for identity, scope, correlation, idempotency, concurrency, rate limits, audit, leader state, and errors;
- API lifecycle and deprecation policy;
- integration developer guide and reference environments.

### Verification

- every implemented capability maps to a documented API operation or event;
- operators need no database, shell, filesystem, or undocumented endpoint access;
- every collection and item path passes tenant and ownership isolation tests;
- followers, quorum loss, stale preconditions, duplicate requests, throttling, and async cancellation return the documented behavior;
- generated clients pass against the running controller;
- API examples validate automatically against schemas;
- file bytes and secret values cannot enter or leave through the REST contract.

### Exit gate

The REST and event contracts are sufficient to operate and integrate the platform without privileged internal access. `ARCH-18` and the required API coverage gaps can be closed only at this gate.

## 14. Phase 7 — Route, Workflow, Scheduling, and Business-Calendar Automation

**Size:** L  
**Milestone:** contributes to M3  
**Primary gaps:** `ARCH-04`, `API-08`, `API-10`

### Objective

Turn route and workflow definitions into validated, versioned, governed, observable execution services.

### Scope

1. Implement autonomous route evaluator lifecycle and readiness.
2. Support manual, schedule, interval, event, and approved file-arrival triggers.
3. Add idempotent trigger identity and duplicate-event suppression.
4. Validate service connections, agent capabilities, policies, variables, dependencies, and evaluator readiness before activation.
5. Persist immutable route and workflow versions and pin executions to exact versions.
6. Implement workflow execution records, step dependencies, linked transfers, pause, cancel, retry, and reconciliation.
7. Add dry-run and virtual-plan behavior without external side effects.
8. Implement processing dates, market holidays, time zones, daylight-saving rules, cut-offs, blackout windows, maintenance windows, and exception calendars.
9. Implement controlled backfill and reprocessing with approval and publication protection.
10. Connect execution deadlines and escalation policy to Phase 3 transfer operations.

### Deliverables

- route evaluator service and readiness signal;
- versioned route and workflow repositories;
- trigger, schedule, calendar, and duplicate-suppression services;
- execution, step, event, and control APIs;
- activation and validation gate;
- backfill and reprocessing operation with approval hooks;
- financial-services calendar test fixtures and runbooks.

### Verification

- no route reports active unless evaluator, policy, services, and capabilities are ready;
- duplicate trigger delivery creates one execution;
- daylight-saving changes, holidays, cut-offs, and exception calendars produce deterministic schedules;
- dry-run and plan modes perform no external side effects or secret disclosure;
- reprocessing cannot overwrite or duplicate a previously published result without explicit policy and approval;
- workflow cancellation and retry preserve complete step and transfer history.

### Exit gate

Routes and workflows execute autonomously and predictably under governed schedules and business calendars, with full transfer-level operational visibility.

## 15. Phase 8 — Durability, High Availability, Backup, and Disaster Recovery

**Size:** XL  
**Milestone:** M3  
**Primary gaps:** `ARCH-07`, `ARCH-10`, `API-13`, `API-14`

### Objective

Prove that authoritative state, active transfer knowledge, configuration, and evidence can survive realistic controller and site failures within declared recovery objectives.

### Scope

1. Define supported static topologies, failure domains, quorum rules, storage classes, and latency limits.
2. Automate snapshot creation, integrity verification, retention, encryption, replication, and restore.
3. Define backup scope for Raft state, audit evidence, event data, configuration, and deployment metadata.
4. Test one-node loss, leader loss, quorum loss, corrupt log, corrupt snapshot, full-cluster loss, and accidental deletion.
5. Define and measure RPO and RTO for each data class.
6. Implement reconciliation of active transfers after controller recovery.
7. Implement controller and agent rolling compatibility tests, upgrade, pause, rollback, and mixed-version limits.
8. Add disaster declaration, maintenance mode, degraded mode, restore, and return-to-service runbooks.
9. Automate scheduled restore exercises and evidence capture.
10. Decide whether dynamic Raft membership is required for the first enterprise release.

### Dynamic membership decision

The first enterprise release MAY retain documented static membership if supported topologies, replacement, backup, restore, and recovery meet requirements. Live node add/remove MUST remain unavailable unless joint-consensus membership, compatibility, recovery, audit, and rollback are implemented and tested as a separate Phase 8B workstream.

### Deliverables

- durability and recovery architecture;
- supported topology and storage matrix;
- snapshot, backup, restore, integrity, and retention automation;
- RPO/RTO definitions and measurement harness;
- full-cluster recovery environment and runbooks;
- mixed-version and rolling-upgrade compatibility suite;
- scheduled disaster-recovery exercise and evidence report;
- optional dynamic-membership design and go/no-go decision.

### Verification

- committed state survives every supported single-node and leader-loss scenario;
- snapshot restore recreates the same authoritative state and resource versions;
- corrupt artifacts fail validation and cannot silently replace good state;
- full-cluster restore meets declared RPO/RTO in a clean environment;
- recovered active transfers become safely resumed, failed, or reconciliation-required according to evidence;
- upgrade and rollback preserve API, Raft, snapshot, and agent compatibility;
- disaster exercise requires no undocumented data manipulation.

### Exit gate

Availability and durability claims are supported by repeatable failure and restore evidence, not topology diagrams alone.

## 16. Phase 9 — Governance, Audit, Evidence, and Enterprise Integrations

**Size:** XL  
**Milestone:** contributes to M4  
**Primary gaps:** `API-11` plus governance requirements in the main design

### Objective

Provide accountable change control, immutable evidence, data governance, and integration with enterprise security and operations ecosystems.

### Scope

1. Complete immutable audit search, retention, archival, integrity, and signed export.
2. Implement data classification, metadata masking, residency, retention, legal hold, and defensible deletion policy.
3. Implement four-eyes approval for configured high-risk actions.
4. Add time-bounded emergency access and emergency-change workflow.
5. Integrate audit and security events with at least one SIEM platform.
6. Integrate actionable alerts with at least one on-call or incident platform.
7. Integrate change and incident evidence with an ITSM platform.
8. Add governed webhooks and event subscriptions with signing, replay protection, retry, throttling, and dead-letter handling.
9. Add CMDB or service-catalogue synchronization for ownership, criticality, environment, and dependencies.
10. Define compliance-control mappings without claiming certification.

### Deliverables

- immutable audit query and signed export service;
- retention, legal-hold, deletion, residency, classification, and masking policies;
- approval, privileged elevation, and emergency-change resources;
- SIEM, ITSM, on-call, webhook, and service-catalogue connectors;
- notification routing, deduplication, escalation, acknowledgement, suppression, delivery, and dead-letter evidence;
- control-evidence catalogue and assessment guide.

### Verification

- audit events cannot be mutated through supported interfaces;
- exports contain filter, schema, authorization context, digest, count, expiry, and retrieval audit;
- a requester cannot approve their own protected action;
- expired approvals and emergency authority cannot be reused;
- legal hold prevents governed deletion while preserving tenant isolation;
- connector failure is observable, retried safely, and dead-lettered without losing the underlying event;
- webhook signature, replay, tenant isolation, and destination policy tests pass;
- compliance documentation clearly distinguishes control evidence from certification.

### Exit gate

Security, operational, change, and transfer evidence is searchable, exportable, integrity-protected, retained, and integrated with the enterprise operating model.

## 17. Phase 10 — Configuration, Supportability, Capacity, and Service Management

**Size:** L  
**Milestone:** contributes to M4

### Objective

Make the platform reproducible, diagnosable, maintainable, and capacity-managed without unsafe privileged access.

### Scope

1. Implement configuration schema, candidate validation, redacted effective view, source, version, reloadability, and drift state.
2. Implement configuration-as-code promotion through environments without copying secrets.
3. Add maintenance windows, emergency changes, controlled degraded mode, and maintenance mode.
4. Build redacted support bundles with integrity, access, expiry, and audit.
5. Correlate API, controller, agent, service connection, transfer, alert, and notification evidence.
6. Add capacity models for queues, agent pools, bandwidth, temporary storage, controllers, event retention, and exports.
7. Add service-level reports and ownership/runbook completeness checks.
8. Implement tenant usage, forecasting, showback, and optional chargeback data.
9. Add compatibility, certificate, secret-rotation, artifact, configuration, and retention-expiry forecasts.
10. Produce operator, security, support, deployment, backup, restore, and incident runbooks.

### Deliverables

- configuration schema, validation, effective-view, and drift APIs;
- signed environment-promotion workflow;
- maintenance and degraded-mode controls;
- redacted support-bundle operation;
- capacity forecasting and service-level read models;
- tenant usage and allocation reports;
- runbook catalogue tied to business services and alerts;
- readiness review for user-interface implementation.

### Verification

- effective configuration can be explained without exposing secrets;
- drift from the approved version is detected and alerted;
- support bundles contain sufficient evidence for a reference incident and no secret values;
- capacity alerts fire before configured saturation thresholds;
- maintenance mode prevents unsafe new work while accounting for active attempts;
- environment promotion preserves approved policy and uses environment-local secret references;
- every critical service and alert has an owner and accessible runbook.

### Exit gate

Operations and support can reproduce configuration, diagnose incidents, forecast capacity, and execute maintenance without controller shell or storage access.

## 18. Phase 11 — Administration and Operations User Interfaces

**Size:** XL  
**Milestone:** M4

### Objective

Provide role-specific enterprise interfaces using only the supported REST and event contracts.

### Scope

1. Create a shared design system, accessibility baseline, navigation model, and permission-aware component model.
2. Implement technology-operations views for critical, at-risk, late, stalled, degraded, failed, and reconciliation-required transfers.
3. Implement transfer detail with end-to-end timeline, attempts, progress, deadline, integrity, publication, alerts, and audit context.
4. Implement route and workflow definition, validation, activation, schedule, calendar, execution, and reprocessing views.
5. Implement agent fleet inventory, posture, drain, deployment, upgrade, rollback, quarantine, rotation, and revocation views.
6. Implement service connection, trust, policy, secret-reference metadata, validation, and connection-test views.
7. Implement tenant, quota, usage, policy, ownership, and capacity views.
8. Implement alert acknowledgement, suppression, escalation, notification, and incident linkage.
9. Implement approval queues, audit search, evidence export, configuration drift, cluster state, snapshot, backup, restore, and deployment views.
10. Implement security-administrator, auditor, platform-administrator, application-owner, transfer-operator, and support role experiences.

### UI constraints

- no private controller, database, Raft, filesystem, or deployment-platform access;
- authorization is enforced server-side; hiding controls is not authorization;
- every mutation uses idempotency and preconditions where required;
- stale, local, bounded, and linearizable read state is visibly distinguished;
- secrets are never rendered or retrieved;
- destructive and high-risk actions show impact, reason, approval, and resulting operation;
- accessibility meets the agreed enterprise standard;
- large lists use server pagination, filtering, and saved operational views;
- real-time views tolerate disconnect, resume from event position, and expose telemetry gaps.

### Deliverables

- operator console and administration interface;
- role-specific navigation and saved views;
- accessibility and usability test suite;
- UI/API contract tests;
- audit and correlation coverage for every mutation;
- operational training, guided incident scenarios, and user documentation.

### Verification

- each role sees only authorized tenants, resources, fields, and actions;
- complete reference incidents can be detected, investigated, owned, controlled, and evidenced through the UI;
- disconnect and event-stream gap behavior is clear and recoverable;
- concurrent edits produce visible precondition conflicts rather than silent overwrite;
- four-eyes actions cannot be self-approved;
- UI actions and automation produce the same API and audit behavior;
- accessibility and high-volume performance targets pass.

### Exit gate

Representative operators, security administrators, auditors, application owners, and support staff can complete their approved workflows without internal-system access.

## 19. Phase 12 — Enterprise Validation, Pilot, and Release Candidate

**Size:** XL  
**Milestone:** M5

### Objective

Prove the complete platform in a representative enterprise environment and produce the evidence required for a release decision.

### Scope

1. Freeze release-candidate APIs, schemas, configuration, artifact versions, and compatibility matrix.
2. Execute end-to-end functional, security, isolation, performance, scale, soak, recovery, upgrade, rollback, and disaster tests.
3. Run threat-model review, dependency and container scanning, penetration testing, and remediation.
4. Execute financial-services pilot scenarios with critical deadlines, business calendars, retries, failures, and incident response.
5. Validate service connections across every supported protocol and trust mode.
6. Validate multi-tenant isolation across API, state, events, metrics, exports, support bundles, and UI.
7. Validate backup, restore, RPO, RTO, reconciliation, and evidence retention.
8. Conduct operational readiness, support readiness, security readiness, and change-approval reviews.
9. Train operators and execute game days without engineering intervention.
10. Record accepted risks, deferred capabilities, support boundaries, and release decision.

### Reference pilot scenarios

- daily settlement file with a hard completion deadline and escalating risk;
- clearing-house inbound file with duplicate submission and lost acknowledgement;
- outbound regulatory report with integrity verification and atomic publication;
- SFTP host-key rotation and unexpected changed-key rejection;
- agent compromise followed by quarantine, revocation, reassignment decision, and evidence export;
- leader loss during progress reporting and during destination publication;
- secrets rotation during an active processing window;
- workflow scheduled across holiday and daylight-saving boundaries;
- quorum loss followed by full restore and transfer reconciliation;
- failed agent rollout followed by automatic pause and controlled rollback;
- cross-tenant attack attempts across list, item, stream, export, and UI paths;
- SIEM, ITSM, on-call, and webhook delivery failure with retry and dead-letter evidence.

### Deliverables

- signed artifact inventory, SBOMs, provenance, and vulnerability disposition;
- OpenAPI coverage and generated-client compatibility report;
- security, isolation, penetration, and threat-model report;
- transfer correctness and failure-injection report;
- performance, scale, and soak report with workload and environment details;
- backup, restore, RPO, RTO, and disaster-recovery report;
- agent rollout, upgrade, rollback, rotation, and revocation report;
- operational telemetry and alert-detection report;
- audit, retention, legal-hold, and evidence-export report;
- accessibility, usability, and role-workflow report;
- known limitations, accepted risks, and go/no-go record.

### Verification

- every critical architecture and API gap has linked implementation and test evidence;
- every applicable high gap is closed or explicitly blocks release;
- all reference pilot scenarios pass in the representative enterprise environment;
- security, operations, support, audit, application-owner, and technology-risk reviewers accept the evidence relevant to their responsibilities;
- game-day participants complete detection, investigation, control, recovery, escalation, and evidence workflows without undocumented engineering intervention;
- the published supported-feature matrix matches the running release candidate and contains no target-state capability claims;
- unresolved risks have named owners, explicit impact, expiry or review date, and release authority approval.

### Exit gate

The enterprise release candidate is approved only when all critical canonical gaps are closed, applicable high gaps are closed or explicitly release-blocking, measurable release gates pass, pilot stakeholders accept the operating model, and no undocumented privileged procedure is required.

## 20. Workstreams and Ownership Model

Named individuals are assigned during delivery planning. At minimum, each phase requires accountable ownership for:

| Workstream | Responsibilities |
|---|---|
| Architecture | State ownership, consistency, failure semantics, ADRs, compatibility, and gap closure |
| Transfer engine | Protocol behavior, attempts, integrity, publication, retry, resume, and large-file safety |
| Controller and Raft | Commands, invariants, snapshots, leader behavior, durability, and recovery |
| Agent | Execution protocol, identity, posture, telemetry, drain, deployment, and hardening |
| API and integration | OpenAPI, REST conventions, clients, events, connectors, and compatibility |
| Security | Identity, authorization, PKI, service trust, secrets, threat model, scanning, and incident response |
| Operations and SRE | Telemetry, alerts, SLOs, capacity, runbooks, backup, restore, DR, and readiness |
| Tenant and governance | Hierarchy, quotas, policy, usage, approvals, audit, retention, and evidence |
| Workflow and scheduling | Definitions, execution, triggers, calendars, backfill, and dependency behavior |
| User experience | Operator and administrator research, design system, accessibility, UI, and workflow validation |
| Quality engineering | Contract, isolation, protocol, failure, scale, soak, security, upgrade, and recovery automation |
| Product and domain | Financial-services scenarios, priorities, acceptance, operating model, and pilot coordination |

Architecture, security, operations, quality, and product/domain owners participate in every phase gate; they are not final-phase reviewers.

## 21. Gap-to-Phase Traceability

| Gap | Delivery phase |
|---|---|
| `ARCH-01` Agent omits `IN_PROGRESS` | Phase 0, completed structurally in Phase 2 |
| `ARCH-02` No attempt lease or fencing | Phase 2 |
| `ARCH-03` No authenticated identity boundary | Phase 1 |
| `ARCH-04` Route trigger evaluator not wired | Phase 7 |
| `ARCH-05` Retriable writes lack idempotency and leader discovery | Phases 0, 2, and 6 |
| `ARCH-06` Assignment reference and tenant invariants incomplete | Phases 0 and 1 |
| `ARCH-07` Persistent controller path and volume not proven | Phases 0 and 8 |
| `ARCH-08` SFTP host-key verification disabled | Phase 4, supported by Phase 1 trust foundations |
| `ARCH-09` HTTP adapter buffers full payload | Phase 4 protocol hardening and Phase 12 scale validation |
| `ARCH-10` Dynamic membership absent | Phase 8 decision or optional Phase 8B |
| `ARCH-11` Transfer operations telemetry incomplete | Phase 3 |
| `ARCH-12` Operational business context absent | Phase 3 |
| `ARCH-13` TLS/mTLS boundary incomplete | Phase 1 |
| `ARCH-14` Service alias, egress, verification, and secret policy absent | Phase 4 |
| `ARCH-15` Agent identity lifecycle incomplete | Phase 5 |
| `ARCH-16` Governed agent deployment absent | Phase 5 |
| `ARCH-17` Credentials may appear in URI user-info | Phase 4 |
| `ARCH-18` REST coverage incomplete | Phase 6, with incremental delivery in Phases 1–5 |
| `API-01` OpenAPI and path coverage absent | Phases 0 and 6 |
| `API-02` Authenticated scope enforcement absent | Phase 1 |
| `API-03` Transfer lifecycle and evidence resources absent | Phases 2, 3, and 6 |
| `API-04` Operational risk and alert APIs absent | Phase 3 |
| `API-05` Secure agent lifecycle API absent | Phase 5 |
| `API-06` Service connection and secret-reference API absent | Phase 4 |
| `API-07` Assignment lease and fencing contract absent | Phase 2 |
| `API-08` Workflow REST resources absent | Phases 6 and 7 |
| `API-09` Tenant and quota REST resources absent | Phase 6 |
| `API-10` Route validation and execution history absent | Phase 7 |
| `API-11` Audit query and export API absent | Phases 6 and 9 |
| `API-12` Standard reliability conventions absent | Phases 0, 2, and 6 |
| `API-13` Cluster and configuration administration incomplete | Phases 6, 8, and 10 |
| `API-14` Compatibility, retention, export, and replay incomplete | Phases 3, 6, 8, and 9 |

## 22. Backlog Classification

Every implementation item is classified as one of:

- **Release blocker:** required to close a critical or applicable high gap;
- **Phase blocker:** prevents the current phase exit gate;
- **Pilot blocker:** required for the agreed representative pilot;
- **Enterprise follow-on:** valuable but not required for the first supported enterprise release;
- **Research:** requires architecture or product decision before commitment.

The following should default to enterprise follow-on unless the pilot requires them:

- dynamic Raft membership;
- agent-to-agent streaming;
- S3, Azure Blob, and Google Cloud Storage adapters;
- multi-cluster federation;
- automatic controller sharding;
- additional secrets, SIEM, ITSM, scheduler, and notification providers beyond the first supported integration in each category;
- advanced chargeback and cost optimization.

Deferral MUST be explicit and must not leave documentation implying that the feature is current.

## 23. Plan Governance

At the end of each phase:

1. update the architecture and API conformance tables;
2. link evidence for every exit criterion;
3. remove a gap only when implementation, tests, migrations, operations, and documentation agree;
4. review accepted risks and dependencies;
5. record scope changes through an architecture or product decision;
6. publish current-versus-required endpoint coverage;
7. confirm that the next phase assumptions remain valid;
8. obtain architecture, security, operations, quality, and product/domain sign-off.

The plan is revised when requirements or implementation evidence change. Revision history MUST describe changes to sequencing, scope, exit gates, or release meaning.
