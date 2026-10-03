<img src="quorus-logo.png" alt="Quorus" width="120"/>

# Quorus Security Deployment Guide

**Version:** 1.9  
**Date:** 2026-10-03  
**Author:** Mark Ray-Smith — Cityline Ltd  
**License:** Apache 2.0  
**Status:** Phase 1 implementation guide  
**Scope:** Controller HTTP, Raft, agent control, identity assertions, authorization, and decision audit

## 1. Purpose and release boundary

This guide configures the Phase 1 authenticated control-plane foundation. It does not make Quorus production-ready by itself. Runtime revocation, expiry observation, trust-version telemetry, controlled certificate-overlap tests, security-configuration audit, retained local evidence, and governed service connections are implemented. Corporate PKI and gateway accreditation, automatic certificate issuance, agent enrollment, enterprise evidence-platform integration, and enterprise validation remain release work in the [Enterprise Implementation Plan](../docs-design/task/QUORUS_ENTERPRISE_IMPLEMENTATION_PLAN.md).

Production configuration fails closed. Quorus will not start a production controller unless HTTP and Raft mutual TLS and at least one trusted identity source are configured, and the operational audit path and retained-evidence path are distinct. Both audit paths have packaged defaults under `./data/audit/`, relative to the working directory, so set them explicitly to durable, access-controlled locations: the controller starts with the defaults if you do not. A production agent rejects plaintext controller URLs and missing client or trust material.

## 2. Implemented trust model

| Connection | Authentication | Authorization identity | Encryption and peer verification |
|---|---|---|---|
| Enterprise gateway to controller | Gateway client certificate | Protected `X-Quorus-*` assertion accepted only from an exact trusted gateway certificate subject | TLS 1.3, client certificate required, configured trust bundle |
| Direct agent to controller | Agent client certificate | Exact certificate-subject binding to agent, tenant, environment, roles, and scopes | TLS 1.3, client certificate required; agent verifies controller hostname and trust chain |
| Direct service or deployment workload to controller | Workload client certificate | Exact certificate-subject binding | TLS 1.3, client certificate required, configured trust bundle |
| Controller to controller | Controller certificate on both ends | Certificate trust and hostname. Any certificate that chains to the Raft trust bundle and is not revoked is accepted; certificates are not yet bound to a configured node ID (register item `SEC-04`) | TLS 1.3 mutual authentication with the controller trust bundle |

Network location is not identity. Request `tenantId` fields may narrow a request but cannot establish or expand the authenticated tenant.

## 3. PKI requirements

Use corporate PKI or a controlled workload PKI. Private keys must be supplied through the deployment platform's secret mechanism and must not be committed, baked into images, or stored in Raft state.

Certificates must meet these requirements:

- controller HTTP certificates contain every controller or load-balancer DNS name used by clients in Subject Alternative Name;
- Raft controller certificates contain the DNS name used in `quorus.cluster.nodes`;
- client certificates have a distinct subject per gateway, controller, agent, service integration, or deployment workload;
- trust bundles contain only approved issuing roots and intermediates for that boundary;
- HTTP-client and HTTP-server authentication extended key usages are present where the PKI enforces them;
- certificate lifetime, renewal window, revocation target, and owner are recorded outside Quorus;
- private-key file permissions are limited to the Quorus process identity.

A separate controller-cluster CA is recommended for Raft. Do not reuse a fleet-wide private key.

## 4. Controller production configuration

The packaged `quorus-controller.properties` selects `production`, enables security, and forbids insecure transport. Supply paths and identity sources through environment variables or an approved configuration injection mechanism.

The HTTP listener binds to `quorus.http.host`, which defaults to `127.0.0.1`. A controller outside a container must set it (environment `QUORUS_HTTP_HOST`) to the interface that gateways and agents reach, or it accepts only local connections. The controller image already sets `QUORUS_HTTP_HOST=0.0.0.0`.

```properties
quorus.security.profile=production
quorus.security.enabled=true
quorus.security.allow-insecure=false

quorus.security.http.tls.enabled=true
quorus.security.http.tls.certificate=/run/secrets/controller-http.crt
quorus.security.http.tls.private-key=/run/secrets/controller-http.key
quorus.security.http.tls.trust-bundle=/run/secrets/controller-client-ca.crt
quorus.security.http.tls.crl=/run/secrets/controller-client.crl
quorus.security.trust-bundle.version=corp-pki-2026-09
quorus.security.certificate.expiry-warning-days=30

quorus.security.raft.tls.enabled=true
quorus.security.raft.tls.certificate=/run/secrets/controller-raft.crt
quorus.security.raft.tls.private-key=/run/secrets/controller-raft.key
quorus.security.raft.tls.trust-bundle=/run/secrets/controller-cluster-ca.crt

quorus.security.audit.path=/var/lib/quorus/audit/security-audit.jsonl
quorus.security.audit.evidence-path=/var/lib/quorus/evidence/security-audit-evidence.jsonl
```

The HTTP CRL is optional at configuration level because some deployments distribute revocation by trust-bundle replacement. The production operating design must nevertheless select and test one PKI revocation mechanism. The configured serial list and runtime revocation API add an application-level fail-closed control that is evaluated on every authenticated HTTP request and every inbound Raft RPC; they do not replace PKI revocation or change the TLS trust anchors loaded by the process. Raft enforcement is inbound only: a node presenting a revoked certificate cannot call its peers, but the leader still sends entries to it and counts its replies (register item `SEC-10`). Stop such a node rather than relying on revocation to exclude it.

### 4.1 Trusted gateway identity

List exact RFC 2253 certificate subjects separated by semicolons:

```properties
quorus.security.trusted-gateway-subjects=CN=quorus-gateway-prod,OU=Workloads,O=Cityline Ltd,C=GB;CN=quorus-gateway-dr,OU=Workloads,O=Cityline Ltd,C=GB
```

Only a request whose mutually authenticated client certificate has one of these exact subjects may provide identity headers:

| Header | Required | Meaning |
|---|---|---|
| `X-Quorus-Principal` | Yes | Stable human or integration principal identifier |
| `X-Quorus-Identity-Type` | Yes | `HUMAN`, `SERVICE_INTEGRATION`, `CONTROLLER`, `AGENT`, or `DEPLOYMENT` |
| `X-Quorus-Tenant` | Yes | Authorized tenant established by the external identity decision |
| `X-Quorus-Environment` | Yes | Authorized environment such as `production` or `disaster-recovery` |
| `X-Quorus-Roles` | Yes | Comma-separated canonical roles |
| `X-Quorus-Scopes` | Yes | Comma-separated explicit scopes; may be empty when role policy is sufficient |
| `X-Quorus-Expires-At` | Yes | UTC assertion expiry in ISO-8601 format |
| `X-Quorus-Elevation-Expires-At` | No | UTC expiry of time-bounded privileged elevation |

The gateway must remove inbound copies of all `X-Quorus-*` headers, authenticate the external caller, perform MFA and corporate policy where required, construct new assertions, and use its own client certificate to reach Quorus. Quorus protects the assertion on the dedicated mutually authenticated hop; it does not accept these headers from arbitrary client certificates.

### 4.2 Direct certificate identity bindings

Use exact subject bindings for agents, service integrations, and deployment workloads. Entries are separated with semicolons. Fields use the format:

```text
subject=>principal|type|tenant|environment|roles|scopes
```

Multiple roles or scopes use `+`:

```properties
quorus.security.mtls-identities=CN=payments-agent-01,OU=Quorus Agents,O=Cityline Ltd,C=GB=>payments-agent-01|AGENT|payments|production|AGENT|agents:register+agents:heartbeat+agents:jobs:read+transfers:status:update
```

An `AGENT` principal must equal the `agentId` used for registration, heartbeat, polling, and status reporting. Cross-agent and cross-tenant operations fail with `403`.

Certificate serials that must be refused before a replacement trust bundle or CRL is deployed can be listed in uppercase hexadecimal, separated by commas:

```properties
quorus.security.revoked-certificate-serials=01AF44,09BC20
```

Leading zeroes and colon separators are ignored when serials are compared, so OpenSSL forms such as `01:AF:44` match the certificate serial `1AF44`.

An actively elevated `SECURITY` or `ADMINISTRATOR` identity, or one granted the `security:trust:write` scope with elevation, can atomically replace the runtime serial set with `PUT /api/v1/security/trust/revocations`. Only gateway-asserted identities can hold elevation; configured certificate bindings cannot. The request includes a new `trustBundleVersion` and the complete replacement `revokedCertificateSerials` array, which is required: a request without it is rejected with `400` and changes nothing. The controller applies the new set only after the change's audit record is durable, and the audit record lists the serials.

The new state applies only to the controller that receives the request, where it is shared by that process's HTTP and Raft enforcement and applies to subsequent requests or RPCs on already-established TLS connections. Every controller accepts the request, followers included. It is held in memory, is not replicated, and is lost when that controller restarts. Send the complete replacement set to every controller and also add it to configuration before any restart. Because this is replacement rather than merge behavior, operators must supply every serial that must remain revoked. The [Certificate Incident Runbook §4.5](QUORUS_CERTIFICATE_INCIDENT_RUNBOOK.md#45-revocation-request) gives an example request.

## 5. Agent production configuration

The agent uses the same secure client posture for registration, heartbeat, job polling, and status reporting. Set:

```text
QUORUS_AGENT_ID=payments-agent-01
QUORUS_AGENT_TENANT_ID=payments
QUORUS_AGENT_CONTROLLER_URL=https://controller.prod.example.net:8080/api/v1
QUORUS_AGENT_SECURITY_PROFILE=production
QUORUS_AGENT_SECURITY_ALLOW_INSECURE=false
QUORUS_AGENT_TLS_ENABLED=true
QUORUS_AGENT_TLS_CERTIFICATE=/run/secrets/agent.crt
QUORUS_AGENT_TLS_PRIVATE_KEY=/run/secrets/agent.key
QUORUS_AGENT_TLS_TRUST_BUNDLE=/run/secrets/controller-ca.crt
```

The agent ID must equal the principal in the agent's certificate binding (§4.2), and the agent refuses to start without a tenant ID. The legacy names `AGENT_ID`, `AGENT_TENANT_ID` and `CONTROLLER_URL` are still read, but the `QUORUS_AGENT_*` names win when both are set. The agent image's entrypoint waits for the controller's `/health/live` before starting the agent, presenting the certificate, key and trust bundle above when the controller URL is `https`.

The agent client uses TLS 1.3 only, validates the controller's certificate chain against the trust bundle, and verifies its hostname. A production build does not silently fall back to HTTP or an untrusted certificate.

The agent's private key must be an unencrypted PKCS#8 PEM file (`-----BEGIN PRIVATE KEY-----`), which is what OpenSSL 3 writes by default; RSA, EC and EdDSA keys are accepted. The agent refuses to start with a PKCS#1 key (`-----BEGIN RSA PRIVATE KEY-----`) or an encrypted key, and names the file. Convert a PKCS#1 key with `openssl pkcs8 -topk8 -nocrypt -in agent-rsa.key -out agent.key`. The certificate file may hold the agent certificate followed by its intermediates, and the trust bundle may hold several certificates, for example during a controller CA rotation.

## 6. Authorization model

The middleware computes one required scope per protected route and evaluates identity expiry, tenant, environment, explicit scopes, roles, and time-bounded elevation. Decisions have stable codes such as `Q-AUTHZ-TENANT-MISMATCH`, `Q-AUTHZ-ENVIRONMENT-MISMATCH`, `Q-AUTHZ-SCOPE-MISSING`, and `Q-AUTHZ-ELEVATION-REQUIRED`.

Canonical roles are `OPERATOR`, `ADMINISTRATOR`, `SECURITY`, `AUDITOR`, `SERVICE_INTEGRATION`, `CONTROLLER`, `AGENT`, and `DEPLOYMENT`. An administrator role does not override tenant or environment boundaries. Scope wildcards are supported only when explicitly issued.

Use these authenticated endpoints to validate a deployment:

- `GET /api/v1/security/me` returns the effective identity;
- `GET /api/v1/security/authorization/explain` provides query-form explanation;
- `POST /api/v1/security/authorization/check` evaluates a proposed method, path, tenant, environment, and classification through the same policy engine;
- `GET /api/v1/security/trust` reports the runtime trust-policy version, load time, revoked-serial count, caller-certificate expiry, threshold, and alert state;
- `PUT /api/v1/security/trust/revocations` replaces the runtime revocation set under elevated security authorization and records the change.

## 7. Audit evidence

Authentication, authorization, protected completion, certificate-lifecycle, and security-configuration decisions are appended as redacted JSON Lines. Each record contains a SHA-256 `hash` over its content and `previousHash`, forming a chain that continues after restart. Startup verifies every existing link and record hash and fails closed if either configured chain has been altered.

Durability works as follows:

- Records are written in chain order and synced to storage in groups: one sync covers every record written before it began.
- A request does not proceed past authentication or authorization until those decisions' records are durable. A security-configuration change takes effect only after its record is durable.
- The completion record of a protected request is written after the response has been sent, and nothing waits for it.
- If a write or sync fails, the log stops accepting records: every later append fails, and so does every request that needs one. Treat that as an audit-storage incident (see [§6 of the Certificate Incident Runbook](QUORUS_CERTIFICATE_INCIDENT_RUNBOOK.md#6-audit-chain-incident)) and restart the controller once storage is restored.

The controller writes the same event to distinct operational and retained-evidence paths, with the retained chain written first. These files are tamper-evident, not a substitute for WORM retention or a SIEM. Forward the retained path through an approved integrity-preserving collector. Restrict filesystem access, monitor collection lag, retain according to policy, and verify the chain before evidence use. Request bodies, credentials, certificate private material, and secret values are not audit fields.

## 8. Deployment sequence

1. Issue distinct controller HTTP, controller Raft, gateway, and agent certificates.
2. Verify certificate chain, SAN, extended key usage, expiry, owner, and file permissions outside Quorus.
3. Deploy trust bundles before leaf certificates that depend on new issuers.
4. Configure exact gateway subjects and direct identity bindings.
5. Start one controller in a controlled environment and confirm production validation succeeds.
6. Verify `/health/live` and `/health/ready` with a client certificate that the HTTP trust bundle accepts but that has no identity binding: the listener requires a client certificate at the TLS handshake, and these two paths, with `/api/v1/openapi.yaml`, are the only ones served without an identity. Verify that detailed health, status, metrics, and all other API resources require authentication.
7. Verify `security/me` matches the expected principal, tenant, environment, roles, and scopes.
8. Start remaining controllers and confirm Raft quorum over mutual TLS.
9. Start one canary agent and confirm registration, heartbeat, polling, and status use its bound identity.
10. Run the negative checks below before wider rollout.
11. Confirm both local hash chains remain valid and the retained evidence path reaches the approved enterprise evidence platform.

## 9. Mandatory negative checks

The release evidence must show rejection of:

- plaintext HTTP and plaintext Raft in production;
- missing client certificates;
- untrusted, expired, not-yet-valid, and revoked certificates;
- controller certificates whose SAN does not match the configured host;
- assertion headers received from a non-gateway certificate;
- missing or expired gateway assertions;
- wrong tenant, environment, role, or scope;
- an agent acting for another agent ID;
- cross-tenant list, item, route, assignment, polling, heartbeat, and status access;
- production startup with disabled authentication, disabled TLS, `allow-insecure=true`, missing trust material, or no identity source.

## 10. Current operational limitations

Certificate files and PEM trust bundles are loaded at process start; Quorus does not hot-reload their key or CA material. The validated overlap process therefore deploys trust overlap first and uses a controlled rolling restart while preserving Raft quorum and active control. A runtime serial-revocation update takes effect without restart only on the receiving controller, including for established HTTP and inbound Raft TLS connections. It is not replicated or persisted. Apply the complete set to every controller and add it to configuration before any restart. Follow the [Certificate Incident Runbook](QUORUS_CERTIFICATE_INCIDENT_RUNBOOK.md), drain affected agents when required, and preserve the change evidence.

The built-in audit provides complete Phase 1 security-boundary evidence and a second retained local chain. Searchable enterprise audit queries, WORM storage, evidence-collector delivery state, signed export, and broader resource-version detail remain part of the complete enterprise audit target.

## 11. Registry isolation upgrade and recovery

R2 changes the persisted service-connection, secret-reference and security-event key
format. Raft commands and snapshots write schema 3, which older schema-2 binaries reject.
This is a coordinated upgrade, not a mixed-version rolling upgrade.

1. Quiesce control-plane mutations and agent polling/reporting under the approved
   maintenance procedure; stop all controllers cleanly.
2. Preserve every controller's complete WAL, snapshot and compaction-marker files together.
   Retain the previous binaries and configuration. Never clear a data directory to migrate.
3. Start all controllers on the schema-3-capable release and verify quorum, authenticated
   identities and the expected recovered state before resuming requests.
4. The first successful registry mutation migrates legacy records and its own change in
   one replicated state application. Exact stored ownership and complete legacy addresses
   must agree. Reads before that point enforce ownership without changing state.
5. Verify tenant-scoped connection, secret-reference and security-event collections,
   policy versions and opaque references, including dotted identifiers. Retain redacted
   evidence of the operation and resulting registry schema marker.
6. If ownership is missing/mismatched or old/new records conflict, the mutation fails
   closed. Keep traffic quiesced, preserve evidence and escalate for an explicitly reviewed
   recovery procedure. Do not infer ownership by splitting keys or edit one follower.
   This release does not supply an automatic ambiguity-repair tool.
7. After schema-3 writes, reverting only the binaries is unsupported. Restoring the
   preserved pre-upgrade cluster requires an approved recovery decision and reconciliation
   of every subsequent operation; it is not a lossless rollback. A record already
   overwritten by a legacy key collision cannot be reconstructed from its surviving row.

The local test results do not accredit a production deployment. R6 local final-source
acceptance and R1 container-recreation acceptance (2026-09-07) are complete; the R1
production-filesystem and power-loss gates remain required.

## 12. Pre-execution failure and acknowledgement reconciliation

R3 preparation rejections fail the accepted attempt, assignment and pending transfer
atomically. They must not create `IN_PROGRESS`, `TRANSFER_STARTED`, or connection-use
evidence. Request construction and local-path checks precede the start acknowledgement.
Repeated polls for the same attempt/fencing generation are suppressed in the running
agent until the original lease expires; this claim cache is not durable recovery state.

The agent sends an attempt-aware report at most three times, retaining the exact payload
and sequence, with 100 ms and 200 ms retry delays. Transport failures, HTTP 408/429 and
5xx responses are retryable; a 403/409 rejection is not. Each send has the configured
agent HTTP idle-timeout in milliseconds as its response deadline. A legacy status
without attempt identity is sent once.

On `Q-REPORT-UNRESOLVED` after those retries:

1. Preserve the job/attempt identifiers, fencing generation, sequence and redacted
   diagnostic. A timeout is not evidence that the controller rejected the command.
2. Query the authoritative leader's transfer, attempt and assignment resources. Compare
   the last sequence and outcome before deciding on recovery; do not infer authority
   from a stale follower response.
3. For an unresolved start, the agent does not execute the transfer or manufacture a
   `FAILED` report with the next sequence. Verify the committed state and preserve the
   lease/fence boundary before any reassignment.
4. For an unresolved terminal report, do not rerun a possibly completed transfer.
   Reconcile the controller outcome and destination evidence under an approved recovery
   procedure. Never edit state on one controller or bypass a stale-fence rejection.

Durable agent report-outbox recovery, automatic lease-expiry/reassignment and
destination reconciliation remain open Phase 2 deliverables. Bounded replay is not
a claim of automatic recovery across agent restarts or prolonged controller outages.

## 13. Handover remediation compatibility — 2026-09-05

The agent configuration builder now derives defaults from packaged configuration.
It requires production TLS by default and stops after the first foreign assignment.
Applications using a plaintext development fixture must explicitly select
`securityProfile("development")`, `allowInsecure(true)` and `controllerTlsEnabled(false)`.
Explicit builder values still override defaults. Environment values are applied through
`AgentConfig` at the application boundary, not implicitly by the builder.

The controller image runs `java -jar` directly and reads only the `QUORUS_*` environment
names. The legacy unprefixed controller names (for example `NODE_ID`) have no effect in
the shipped image: the entrypoint script that once mapped them was never run by the image and was removed on 2026-10-03 (register item `ENG-20`).
Shell files require LF line endings, enforced by `.gitattributes` for future checkouts.

Remote paths are literal filename data. Supply `/out/report#1?.dat` for that filename;
do not pre-encode it as a URL. Root scope `/` permits descendants; traversal segments
remain denied. Pinned HTTPS retains the original hostname for TLS verification and the
port in the HTTP Host authority. Ordinary development HTTP transfers may follow
redirects; governed clients continue to reject them.

The service-connection behaviour introduced by this remediation (FTPS ports, validation probes, partial updates, security-event paging, trust caching and URI user-info) is described in [Service Connection Operations Runbook §3.2](QUORUS_SERVICE_CONNECTION_OPERATIONS_RUNBOOK.md#32-behaviour-to-expect). No registry record or deployed data was migrated automatically by these corrections.

## 14. Bounded controller DNS authorization

Moved to [Service Connection Operations Runbook §3.3](QUORUS_SERVICE_CONNECTION_OPERATIONS_RUNBOOK.md#33-controller-dns-authorization), with its configuration keys and the meaning of `503`, `504` and `409`.
