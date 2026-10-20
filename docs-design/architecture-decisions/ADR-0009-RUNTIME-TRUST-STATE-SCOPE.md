<img src="../../docs/quorus-logo.png" alt="Quorus" width="120"/>

# ADR-0009: Runtime Trust State Is Node-Local

**Version:** 1.0  
**Date:** 2026-10-19  
**Author:** Mark Ray-Smith — Cityline Ltd  
**License:** Apache 2.0

**Status:** Accepted. It records decision `DR-Q2`, taken by project authority on 2026-09-25, in the [Outstanding Work Register](../task/QUORUS_OUTSTANDING_WORK_REGISTER_2026-10-20.md#3-decision-log). Register items `SEC-01` and `SEC-02` close with it.

**Relates to:** [ADR-0003](ADR-0003-IDENTITY-BOUNDARY.md) (identity boundary), [ADR-0011](ADR-0011-CONSENSUS-VIA-QRAFT-GENERIC-ENGINE.md) (consensus through QRaft, which must keep this model) and the proposed [ADR-0013](ADR-0013-BREAK-GLASS-ELEVATION.md) (break-glass elevation, which relies on it).

## Context

A controller refuses a revoked certificate in two ways:

| Mechanism | Setting | Where it applies | When it changes |
|---|---|---|---|
| Certificate revocation list | `quorus.security.http.tls.crl` | The HTTP listener's TLS handshake only. Raft has no CRL | Read at startup; a change needs a restart |
| Revoked serial set | `quorus.security.revoked-certificate-serials`, and `PUT /api/v1/security/trust/revocations` at run time | Every authenticated HTTP request, and every inbound Raft RPC, including those on TLS connections that were open before the change | Configured set at startup; replaced at run time by the API |

The serial set lives in `CertificateTrustState`. Each controller process has one instance, shared by the HTTP authentication handler and the Raft peer interceptor. The run-time update replaces the whole set; it does not merge.

Before `DR-Q2`, the documents did not say whether a run-time revocation reached the other controllers. It does not: nothing replicates it. The 2026-09-24 documentation review also found that a serial written with leading zeros, as OpenSSL prints it, never matched (`SEC-01`).

## Decision

1. **The run-time trust state is node-local and volatile.** A revocation update changes only the controller that receives it. It is not replicated through Raft, and it is lost when that controller restarts. At restart the controller reloads the configured serial set.

2. **Operators revoke on every controller, then persist.** To revoke a certificate, send the complete replacement set to every controller, leader and followers alike, and add the same set to `quorus.security.revoked-certificate-serials` in each controller's configuration before any restart. The update is a replacement, so the set must include every serial that must stay revoked.

3. **Every controller answers a revocation update.** The leader guard lets the revocation route through on followers (`SEC-09`), so revocation works without a leader and without quorum.

4. **The change is audited before it applies.** The request must name the complete set. The controller writes a `SECURITY_CONFIGURATION_CHANGE` audit record listing the normalized serials, and replaces the set only once that record is durable. A failed audit write leaves the previous set in force (`SEC-11`).

5. **Serials compare by value.** Configured serials, requested serials and certificate serials are all normalized the same way before comparison: colons removed, upper case, leading zeros stripped. So the OpenSSL form `01:AF:44` matches the certificate serial `1AF44` (`SEC-01`).

## Why node-local

- **Revocation must not depend on the consensus it protects.** A revocation is most urgent when a controller or gateway key is compromised, or the cluster is degraded. Replicated state needs a leader and a quorum to change, so it would fail exactly then.
- **A leader must not control which peers are trusted.** If revocations were replicated, a compromised leader could revoke the honest controllers' certificates on every node and isolate them. Node-local state can only be changed by an authorized, elevated caller on each node.
- **Configuration stays the durable source.** The serial set that survives a restart is the one in configuration, which is deployed and reviewed with the rest of the controller's settings. The run-time update covers the time until that configuration is deployed.

## Alternatives considered

- **Replicate revocations through Raft.** Rejected for the reasons above.
- **Persist the run-time set to a local file.** Rejected for now: it would create a third source, besides configuration and memory, that could drift from both. Configuration remains the record.
- **Fetch revocation from the PKI** through OCSP or a distributed CRL. Not rejected, but outside this decision: it belongs with enterprise PKI integration in Phases 5 and 12, and would add a dependency on that infrastructure during an incident.

## Consequences

- An operator can leave controllers with different sets by missing one. `GET /api/v1/security/trust` on each controller reports `trustBundleVersion` and `revokedCertificateCount`, and each update's audit record lists its serials. Comparing those detects a missed controller; it does not compare the sets themselves. Reporting a digest of the set would make that comparison exact, and is a possible follow-on.
- A controller restarted before its configuration was updated stops refusing the certificate. The runbook therefore orders configuration before restart.
- Revocation is enforced on inbound Raft RPCs only. A leader still sends to a peer whose certificate is revoked (`SEC-10`), and Raft peers are not bound to node identities (`SEC-04`). Both are expected to close with `CE-08`, which must keep this ADR's model when the engine moves to QRaft.
- The [Security Deployment Guide §4.2](../../docs/QUORUS_SECURITY_DEPLOYMENT_GUIDE.md#42-direct-certificate-identity-bindings) and the [Certificate Incident Runbook](../../docs/QUORUS_CERTIFICATE_INCIDENT_RUNBOOK.md) §4.1 to §4.5 describe the operating procedure, and were updated for it under register items DR-A4 and DR-A10.

## Revision history

| Version | Date | Changes |
|---|---|---|
| 1.0 | 2026-10-19 | Records decision `DR-Q2` (2026-09-25) and the delivered `SEC-01`, `SEC-09` and `SEC-11` behaviour |
