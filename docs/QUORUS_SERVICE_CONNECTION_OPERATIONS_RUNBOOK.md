<img src="quorus-logo.png" alt="Quorus" width="120"/>

# Quorus Service Connection Operations Runbook

**Version:** 1.1  
**Date:** 2026-10-03  
**Author:** Mark Ray-Smith — Cityline Ltd  
**License:** Apache 2.0  
**Status:** Current Phase 4 operations runbook  
**Scope:** Governed transfer service connectivity, secrets, trust, egress, and incident response

## 1. Operating Principle

Production transfers use a tenant-scoped `serviceConnectionId`, an absolute `remotePath`, and an approved `agentPool`. Never place a username, password, token, private key, or other credential in a URI, route, workflow, request, log, ticket, or diagnostic attachment. Quorus stores only policy and opaque secret-provider references.

The controller and executing agent make independent allow decisions. The controller commits the policy version, digest, and resolved address set with the transfer. Before asking Vault for a value, the agent repeats tenant, status, path, direction, deployment-configured pool and network zone, protocol, hostname, port, CIDR, DNS-pin, policy-version, local-root, and policy-digest checks. It constructs the remote endpoint from that authorization and binds the socket to an approved address. A disagreement is a security denial, not a retryable network error.

## 2. Required Configuration

A service connection is ready only when all of the following are explicit:

- tenant, owner, environment, classification, network zone, service identity, and authentication type;
- protocol and credential-free endpoint, allowed path roots, directions, and agent pools;
- hostname, CIDR, and port allowlists with resolved-address pinning and redirects disabled;
- SFTP SHA-256 host-key pins, or TLS hostname verification plus approved CA identity or peer pins;
- a protocol-compatible authentication type: SFTP `PASSWORD`/`SSH_PRIVATE_KEY`, HTTPS `BASIC`/`BEARER`, FTPS `PASSWORD`, or SMB/NFS `KERBEROS`;
- an active opaque secret reference with provider, path, key, version, optional expiry, and last-rotation time.

Agents resolving Vault KV v2 references require `QUORUS_VAULT_ADDR` set to a credential-free HTTPS origin and `QUORUS_VAULT_TOKEN` supplied by the workload runtime. Do not bake the token into an image or configuration file. Governed SMB and NFS transfers additionally require an operating-system mount that is already authenticated and encrypted; attest it per agent deployment with `QUORUS_AGENT_SMB_ENCRYPTED_AUTHENTICATED_MOUNT=true` or `QUORUS_AGENT_NFS_ENCRYPTED_AUTHENTICATED_MOUNT=true` only after the platform control has verified that condition. JVM system properties are not a Quorus configuration channel.

Each agent deployment should set `QUORUS_AGENT_POOL`, `QUORUS_AGENT_NETWORK_ZONE`, `QUORUS_AGENT_UPLOAD_ROOT`, and `QUORUS_AGENT_DOWNLOAD_ROOT`. The agent does not require them: unset, it uses pool `default`, network zone `default`, and the roots `data/uploads` and `data/downloads` relative to its working directory. A governed transfer then fails closed unless the connection allows that pool and zone, so set them explicitly rather than allowing `default` in a policy. Upload files must already exist under the upload root. Download destinations must remain under the download root after canonical and symbolic-link resolution. Do not broaden either root to a drive, home directory, or shared application filesystem.

For HTTPS and FTPS, record each approved CA certificate as `SHA256:<base64>` over its DER encoding. Normal JVM PKIX validation always runs first; the approved-CA set then restricts the presented chain, and optional `tlsPeerFingerprints` further restrict the leaf certificate. A TLS 1.2 floor permits TLS 1.2 and TLS 1.3; a TLS 1.3 floor permits TLS 1.3 only. For SFTP private-key authentication, store the unencrypted key only in the external provider; the agent loads it into the SSH client for that transfer and wipes the transient input buffer.

## 3. Change and Validation Procedure

1. Register or update the opaque secret reference. A version change emits `SECRET_REFERENCE_ROTATED`; status changes emit expiry or revocation events.
2. Create or update the service connection. Updates increment `policyVersion`; trust changes also emit `SERVICE_TRUST_CHANGED`.
3. Call `POST /api/v1/service-connections/{serviceConnectionId}/validate` with tenant, remote path, direction, and agent pool. Use `probeNetwork=true` for a bounded active TCP route check to an approved address; this does not retrieve a secret or authenticate to the service.
4. Submit a controlled transfer through the alias. Confirm `SERVICE_CONNECTION_AUTHORIZED` at submission and `SERVICE_CONNECTION_LAST_USED` only after the agent has resolved policy and secret authority, together with the linked transfer timeline.
5. Retain the request ID, transfer ID, policy version, policy digest, validation time, and event IDs as change evidence.

Never bypass a failed stage by widening a CIDR, path, hostname, port, agent pool, or trust pin without an approved change that explains the exact required scope.

### 3.1 Authorization for changes

Creating, updating or deleting a service connection or secret reference requires the matching `service-connections:*` or `secret-references:*` write scope, which the `SECURITY` and `ADMINISTRATOR` roles grant, and an active time-bounded elevation. Only gateway-asserted identities can hold elevation: the trusted gateway sends `X-Quorus-Elevation-Expires-At` with the other `X-Quorus-*` assertion headers. Configured certificate bindings cannot elevate (register decision `DR-Q3`). Reads need only the read scope.

### 3.2 Behaviour to expect

- **Partial updates.** An update that omits the CA, SSH or TLS pin fields keeps the existing values, and the existing minimum TLS version is kept. Review the complete returned, redacted connection after every trust change.
- **FTPS ports.** A portless `ftps://` endpoint uses explicit TLS (`AUTH TLS`) on port 21. Implicit TLS requires an explicit `:990` and an egress policy that allows 990. Policy approval and the adapter's socket use the same effective port. Review portless FTPS aliases whose policies allowed only 990.
- **Validation probes.** A portless endpoint is probed on the protocol's default port.
- **Trust caching.** Governed HTTP clients are closed after each transfer. Trust managers are cached for up to 64 immutable policies; a CA or peer-pin rotation creates a new policy entry, and the old one remains only until normal eviction. Validate the new policy before use.
- **Security events.** `GET /api/v1/security-events` is bounded: request `limit=1..1000` and follow the opaque `nextCursor` until it is absent. The returned `total` is the row count of that page. Quorus does not prune these authoritative events; preserve snapshots and use an approved external evidence process until Phase 9 adds archive, legal hold and retention controls.
- **Credentials in URIs.** New transfer requests whose URI contains user-info are rejected. During compatibility replay, a legacy command containing user-info is stripped and its job made terminal with a redacted resubmission message. Never restore the removed credential from logs or backups; create an external secret reference and resubmit through a governed connection.

### 3.3 Controller DNS authorization

Transfer submission and service-connection validation share one DNS authorization budget per controller HTTP server. Native DNS and policy checks run on the controller's worker pool; registry checks, responses and Raft submission resume on the request context.

| Property | Environment override | Default |
|---|---|---|
| `quorus.http.dns.max-concurrent` | `QUORUS_HTTP_DNS_MAX_CONCURRENT` | `8` |
| `quorus.http.dns.timeout-ms` | `QUORUS_HTTP_DNS_TIMEOUT_MS` | `5000` |

Both settings must be positive. The admission limit counts queued and running work; the deadline includes time spent waiting for a worker.

| Response | Meaning | Operator action |
|---|---|---|
| `503 SERVICE_UNAVAILABLE` | The DNS budget is occupied | Retry later. If it stays occupied, investigate resolver availability: a native lookup keeps its slot until it ends, and raising the deadline does not cancel an OS lookup |
| `504 TIMEOUT` | The DNS authorization deadline expired | A late result creates no transfer and records no approval. Check resolver latency |
| `409 CONFLICT` | The connection or secret state changed during resolution | Send a fresh request against the current state |

Tenant isolation, allowed CIDRs, port, path and pool policy and the exact resolved address set remain enforced. The optional TCP route probe has its own timeout; DNS authorization does not prove that the service is ready.

## 4. Rotation and Revocation

For normal rotation, create the new provider version, update the reference version and `lastRotatedAt`, validate, and run a controlled transfer before retiring the old provider version. Existing assignments carrying an older policy version or digest fail closed and must be resubmitted after authorization.

For emergency revocation, set the secret reference or service connection status to `REVOKED`. Stop or quarantine affected routes and agents using the applicable lifecycle controls, preserve the tenant security-event history, rotate the upstream credential, and create a new policy version. Do not delete evidence during the incident.

## 5. Incident Triage

| Decision or symptom | Meaning | Operator action |
|---|---|---|
| `Q-CONNECTION-PATH`, `Q-CONNECTION-DIRECTION`, `Q-CONNECTION-AGENT-POOL` | Assignment exceeds explicit use policy | Correct the transfer or obtain an approved policy change |
| `Q-EGRESS-HOST`, `Q-EGRESS-PORT` | Endpoint is outside the allowlist | Verify the upstream service record and change control |
| `Q-EGRESS-DNS-REBIND` | Agent DNS differs from controller pins | Treat as possible DNS compromise or split-horizon drift; stop and investigate |
| `Q-EGRESS-SOCKET-BIND` | Actual socket target is outside the approved DNS set | Stop the transfer and investigate resolver or adapter drift |
| `Q-CONNECTION-NETWORK-ZONE`, `Q-LOCAL-PATH` | Agent placement or local filesystem boundary does not match policy | Correct deployment labels or spool roots; never widen them as a workaround |
| `Q-CONNECTION-POLICY-VERSION`, `Q-CONNECTION-POLICY-DIGEST` | Policy changed after assignment | Resubmit under the current policy; investigate unexpected change |
| SSH host-key denial | Unknown or changed server identity | Verify the new key out of band; never accept it interactively |
| TLS chain or hostname failure | Server certificate is untrusted or mismatched | Check certificate, CA, DNS, SNI, and expiry; never disable verification |
| `Q-TLS-CA-DENIED`, `Q-TLS-PEER-PIN` | Valid chain is outside the approved CA or leaf-pin set | Verify certificate rotation out of band and update trust only through approved change control |
| `Q-SECRET-UNAVAILABLE`, `Q-SECRET-PROVIDER` | Reference expired/revoked or provider unavailable | Check lifecycle state, Vault health, workload identity, and least-privilege policy |
| SMB/NFS mount unverified | Encrypted authenticated mount attestation absent | Verify and remediate the mount; do not set the attestation as a workaround |

## 6. Evidence and Escalation

Export the redacted security events, transfer timeline, active attempt, agent identity, alias version, policy digest, validation stages, and upstream service incident reference. Secret values, Vault tokens, URI user-info, packet payloads, and private keys must never be collected. Escalate trust changes, DNS rebinding, repeated authentication failures, or unexpected revocation immediately to security operations and the service owner recorded on the alias.

The canonical API definitions are in [Quorus REST API Specification](QUORUS_REST_API_SPECIFICATION.md), the current API is the [OpenAPI contract](../quorus-controller/src/main/resources/openapi/quorus-controller-v1.yaml), and the architecture is in [Quorus Architecture Specification](QUORUS_ARCHITECTURE_SPECIFICATION.md).
