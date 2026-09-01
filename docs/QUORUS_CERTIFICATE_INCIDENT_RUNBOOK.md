<img src="quorus-logo.png" alt="Quorus" width="120"/>

# Quorus Certificate and Trust Incident Runbook

**Version:** 1.1  
**Date:** 2026-09-01  
**Author:** Mark Ray-Smith — Cityline Ltd  
**License:** Apache 2.0  
**Status:** Phase 1 operational runbook  
**Scope:** Gateway, controller HTTP, Raft, agent, trust-bundle, and identity-binding incidents

## 1. Activation criteria

Activate this runbook for suspected private-key compromise, unauthorized certificate use, unexpected issuer or subject, certificate expiry or not-yet-valid failure, hostname mismatch, trust-bundle error, CRL failure, repeated `Q-AUTHN-*` denials, loss of Raft peer trust, agent authentication loss, or a broken security-audit hash chain.

Treat a gateway or controller private-key compromise as critical because it can affect control-plane identity or quorum. Treat an agent key compromise as critical for the bound tenant and service access until scope is established.

## 2. Immediate safety actions

1. Declare the incident owner, affected environment, business services, tenants, and transfer deadlines.
2. Preserve controller, gateway, agent, PKI, deployment, network, and audit evidence. Do not copy private keys into tickets or chat.
3. Stop new privileged changes through the affected identity. Use a separately authenticated break-glass path only if it is pre-approved, time-bounded, and externally audited.
4. For an agent identity, drain or stop the affected agent before revocation when safe; never reassign a transfer blindly if publication outcome is uncertain.
5. For a controller identity, preserve Raft quorum. Never stop or replace a majority simultaneously.
6. For a gateway identity, remove the exact subject from `quorus.security.trusted-gateway-subjects` and route traffic through a known-good gateway identity.
7. Revoke the affected certificate in corporate PKI and record serial, subject, issuer, reason, incident, and effective time.

## 3. Triage evidence

Collect and correlate:

- certificate subject, issuer, serial, SAN, validity, fingerprint, owner, and deployment target;
- the first and last authentication failure or suspicious success;
- controller node, Raft term, leader, quorum, and peer connectivity;
- agent ID, tenant, environment, active transfers, assignments, last heartbeat, and last status sequence;
- gateway principal, assertion expiry, tenant, environment, roles, and scopes without retaining external credentials;
- security audit `requestId`, decision code, record hash, and previous hash;
- PKI issuance, renewal, revocation, and trust-bundle distribution history;
- deployment version, configuration version, and trust-material digest.

Do not infer that a transfer failed merely because its agent lost control connectivity. Follow transfer reconciliation requirements and preserve any destination staging evidence.

## 4. Containment by identity type

### 4.1 Gateway certificate

1. Use a separately authenticated, actively elevated `SECURITY` identity to replace the runtime revocation set through `PUT /api/v1/security/trust/revocations`, retaining all earlier serials and adding the compromised serial under a new trust-policy version.
2. Confirm the old certificate is rejected on its next request, including over an existing TLS connection, and preserve the `SECURITY_CONFIGURATION_CHANGE` audit event.
3. Remove the compromised subject from the configured trusted gateway list and revoke the certificate in PKI.
4. Deploy a trust bundle or CRL that rejects it, then rolling-restart controllers so the subject and PEM/CRL changes take effect.
5. Confirm requests using the old certificate fail before identity headers are evaluated.
6. Confirm the replacement gateway presents the intended subject and `security/me` output.

### 4.2 Agent certificate

1. Identify all active and recently completed assignments for the bound agent and tenant.
2. Drain or stop the agent. Preserve local transfer and publication evidence.
3. Add its serial through `PUT /api/v1/security/trust/revocations`, retaining every existing revoked serial, and confirm the old agent is refused on its next controller request.
4. Remove its exact direct identity binding, revoke the certificate in PKI, and distribute updated trust material.
5. Rolling-restart controllers for the identity-binding and PEM/CRL changes; runtime serial revocation itself is already active without restart.
6. Issue a distinct replacement key and certificate. Never reuse the compromised private key.
7. Restore the binding, start the agent as a canary, and verify self-binding, tenant, polling, and status authorization.
8. Reconcile uncertain transfers before resuming normal assignment.

### 4.3 Controller HTTP certificate

1. Remove the affected controller from client and load-balancer rotation if service continuity allows.
2. Issue a replacement with correct SANs and a new private key.
3. Deploy the certificate and key to that controller only.
4. Restart the controller and verify client hostname and chain validation.
5. Return it to rotation after health, identity, authorization, and audit checks pass.
6. Repeat one controller at a time.

### 4.4 Controller Raft certificate or cluster trust

1. Confirm the current leader and quorum before changing any node.
2. If one node is affected, stop only that node and preserve the majority.
3. Issue a replacement key and certificate whose SAN matches the Raft hostname in cluster configuration.
4. If an issuing CA changes, distribute an overlap trust bundle to every node first, one node at a time.
5. Restart one follower, confirm it rejoins and catches up, then proceed to the next follower.
6. Transfer leadership or restart the leader only after all followers are healthy.
7. Remove the old CA from trust bundles through another one-at-a-time rolling restart.
8. Verify the revoked or old certificate can no longer join.

Do not perform concurrent majority restarts. Certificate and PEM trust-bundle reload still requires process restart. The Phase 1 suite proves old/new Raft certificate overlap and runtime old-peer revocation while the rotated peer remains trusted; the deployment team must repeat the controlled sequence against its selected PKI and topology.

## 5. Expiry and hostname incidents

For expiry, determine whether the leaf, intermediate, or root is invalid. Replace the narrowest affected material. Check clock synchronization before issuance because a not-yet-valid error may indicate clock drift.

For hostname mismatch, correct the certificate SAN or the configured endpoint. Do not use hostname-verification bypass, `trustAll`, IP substitution, or insecure fallback in production.

## 6. Audit-chain incident

If `previousHash` does not match the preceding record's `hash`, preserve the file and filesystem metadata read-only, stop log rotation that could destroy context, and compare the separately configured retained local chain and its external collected copy. Treat startup refusal or missing, edited, reordered, or truncated records as an evidence-integrity incident. Do not repair the original file in place. Start a new retained chain only under incident authority, recording the prior terminal hash and incident reference externally.

## 7. Recovery validation

Before closing containment, verify:

- production startup still rejects plaintext and incomplete trust configuration;
- old certificates and subjects are refused;
- replacement certificates pass chain, validity, SAN, client-auth, and server-auth checks;
- `GET /api/v1/security/trust` reports the intended trust-policy version and expiry posture;
- Raft retains quorum and every node catches up;
- agent and gateway `security/me` output matches expected principal, tenant, environment, roles, and scopes;
- wrong-tenant, wrong-agent, expired-assertion, and removed-scope tests fail closed;
- operational and retained audit chains pass startup verification, and external collection resumes with intact request correlation and hash linkage;
- critical transfers affected during containment have an explicit reconciled outcome.

## 8. Closure evidence

Record incident timeline, affected identities and resources, revocation effective time, replacement certificate fingerprints, trust-bundle versions, controller restart order, quorum evidence, agent drain and reconciliation results, negative-test results, audit-chain result, residual risk, and follow-up owner. Private keys and external authentication credentials must never appear in the closure record.
