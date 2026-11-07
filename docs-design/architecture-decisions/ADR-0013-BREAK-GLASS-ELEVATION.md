<img src="../../docs/quorus-logo.png" alt="Quorus" width="120"/>

# ADR-0013: Break-Glass Elevation for Direct mTLS Identities

**Version:** 1.1  
**Date:** 2026-11-03  
**Author:** Mark Ray-Smith — Cityline Ltd  
**License:** Apache 2.0

**Status:** Accepted on 2026-11-03 after owner review. It designs decision `DR-Q3` in the [Outstanding Work Register](../task/QUORUS_OUTSTANDING_WORK_REGISTER_2026-11-07.md#3-decision-log), which required a short design before delivery. Delivery item `SEC-06` implements it.

**Relates to:** [ADR-0003](ADR-0003-IDENTITY-BOUNDARY.md), which requires break-glass access to be "time-bound, strongly authenticated and audited". [ADR-0009](ADR-0009-RUNTIME-TRUST-STATE-SCOPE.md) (runtime trust-state scope) covers revocation, which is the main thing break-glass is for.

## Context

Some API scopes need a current, time-bounded elevation as well as the scope itself. They are `security:trust:write`, writes to service connections and secret references, and three scopes that no route uses yet. The check is in `AuthorizationPolicyEngine`.

Only a trusted gateway can grant elevation today. It sends `X-Quorus-Elevation-Expires-At` with its other identity assertions. A directly bound mTLS identity, configured in `quorus.security.mtls-identities` as a certificate subject bound to a principal, roles and scopes, is created with no elevation. It can never perform an elevated operation.

That leaves no way to revoke a certificate while the gateway is unavailable, or after the gateway's own identity is revoked. Revocation (`PUT /api/v1/security/trust/revocations`) needs `security:trust:write` with elevation, and it is the central step of the [certificate incident runbook](../../docs/QUORUS_CERTIFICATE_INCIDENT_RUNBOOK.md). The runbook already says to use a break-glass path only if it is "pre-approved, time-bounded, and externally audited", but Quorus provides none.

Decision `DR-Q3` (2026-10-03) settled the direction: a separately configured break-glass certificate binding may elevate, with a short elevation window and mandatory audit; ordinary certificate bindings never elevate.

These facts shape the design:

- A direct binding matches on the certificate subject alone. Any CA in the controller's HTTP trust bundle could issue a certificate with that subject.
- Runtime trust state is node-local and volatile, and a revocation update replaces the whole set (`DR-Q2`, ADR-0009). The revocation route and the authorization-check route are answered by every controller, not only the leader (`SEC-09`).
- Revocation is keyed by certificate serial alone, not by issuer and serial (`CertificateTrustState`).
- Every authenticated request already writes authentication and authorization audit records that are durable before the request proceeds (`ENG-16`). The audit log is a local file on each controller, not a Raft commit, so it needs no leader. A failed audit write fails the request.
- Route authorization carries no resource tenant, but tenant-scoped reads such as `GET /api/v1/security-events` are filtered to the caller's own tenant (`SecurityContext.trustedTenant`).

## Decision

1. **Break-glass bindings are a separate list.** The new setting `quorus.security.break-glass-identities` uses the form `subject=>principal|scopes`. A break-glass identity is always of type `HUMAN`, with tenant and environment both fixed at `platform`, and has **no roles**, only the scopes listed. Roles are excluded because a role grants scopes by prefix: `ADMINISTRATOR` grants every scope. No allowed scope is tenant-filtered, so the fixed tenant and environment only label the audit record. The list is empty by default, so break-glass is off until an operator configures it.

2. **The scopes are limited to incident response.** Startup fails if a break-glass binding lists any scope outside this set:

   | Scope | Use |
   |---|---|
   | `security:trust:write` | Replace the runtime revocation set |
   | `security:trust:read` | Read the trust state, including the number of revoked serials |
   | `security:self:read` | `GET /api/v1/security/me`, to confirm the identity and its elevation expiry |
   | `security:explain` | Ask why a request would be allowed or denied |

   `security-events:read` is left out: the route returns only the caller's own tenant's events, so a `platform` identity would see none. Service-connection and secret-reference writes are left out too: they are Raft-committed and refused without a leader, and they would widen what a stolen break-glass certificate can do.

3. **Break-glass certificates come from a pinned issuer.** The new setting `quorus.security.break-glass.issuer-sha256` lists the SHA-256 fingerprints of the CA certificates that may issue them. At startup the controller finds each pinned CA certificate in its HTTP trust bundle; startup fails if one is missing, or if break-glass bindings are configured with no pin. A certificate authenticates as a break-glass identity only if:
   - its subject is a break-glass binding; and
   - its signature verifies against a pinned CA's public key. The pinned CA must therefore be the certificate's direct issuer; intermediate CAs are not supported.

   A certificate with a break-glass subject from any other trusted CA is refused. A certificate from a pinned CA is refused unless its subject is a break-glass binding: it can never authenticate as an ordinary binding or as the gateway. The intended issuer is a dedicated CA whose key is held offline by the security team, so custody of that key is the approval. Because revocation is keyed by serial alone, that CA must issue serials of at least 64 random bits, so that revoking a break-glass certificate cannot also revoke an unrelated certificate from another CA.

4. **The elevation window is the certificate's lifetime.** A break-glass certificate whose validity period (`notAfter` minus `notBefore`) is longer than `quorus.security.break-glass.max-certificate-lifetime` is refused. The default is `PT1H`, and startup fails above `PT4H`. Both the identity's expiry and its elevation expiry are the certificate's `notAfter`. There is no activation step and no state: a certificate minted for an incident elevates its holder until it expires, and the normal serial revocation ends it sooner. An incident that outlasts one certificate mints another, which is audited separately.

5. **Every use and every refused attempt is audited as break-glass.** The authentication audit record of a break-glass request has the event type `BREAK_GLASS_AUTHENTICATION`. Its attributes carry the certificate serial, the pinned issuer's fingerprint and `notAfter`, and it is durable before the request proceeds, as for any authentication. Each request also logs a `WARN` line and increments a `quorus.security.break_glass.requests` counter. A refused attempt (a break-glass subject from an unpinned issuer, a certificate over the lifetime limit, or a pinned-issuer certificate with any other subject) is denied with `Q-AUTHN-BREAK-GLASS-REFUSED`, which is distinct from the ordinary authentication denial codes because it may signal a compromise. It logs a `WARN` line and increments `quorus.security.break_glass.refusals`. When break-glass bindings are configured, startup logs a `WARN` line and writes a `SECURITY_CONFIGURATION` audit record listing the bound subjects and pinned issuers.

6. **Ordinary bindings and the gateway are unchanged.** An ordinary direct binding still never elevates, and a test pins that. A subject may appear in only one of the trusted gateway subjects, the ordinary bindings and the break-glass bindings; startup fails otherwise. This also changes current behaviour: today a subject listed both as a gateway and as an ordinary binding silently authenticates as the gateway, and after this change that configuration fails startup.

7. **It works on every controller.** The design holds no state, and the audit log is local, so a break-glass certificate works the same on every node. The revocation and authorization-check routes are already answered by followers, so an operator can revoke on each controller even with no leader. That matches the node-local revocation model of `DR-Q2`.

## Alternatives considered

- **An activation endpoint.** A standing break-glass certificate would call `POST /api/v1/security/break-glass` with a reason, to open a short node-local window. Rejected: a stolen standing certificate could open its own window. It would also need volatile per-node state, activation on every controller, and another leader-guard exemption. Tying the window to a short-lived certificate puts the control in the CA instead.
- **An elevation flag on ordinary bindings.** Rejected by `DR-Q3`: ordinary bindings never elevate.
- **Always-elevated break-glass bindings with a long-lived certificate.** Rejected: there would be no short window.
- **A second factor**, such as a one-time code or a second certificate. Out of scope for the first version; it can be added later without changing the binding format.
- **A real tenant per binding**, so that `security-events:read` would show that tenant's events. Rejected at review: break-glass exists to revoke, which is controller-wide, and a per-tenant binding would add configuration without helping revocation.

## Consequences

- Operators who want break-glass must run a dedicated issuing CA, keep its key offline, add its certificate to every controller's HTTP trust bundle, and configure the pin and bindings. Without that, behaviour is unchanged.
- The break-glass procedure is: mint a certificate with a break-glass subject and a lifetime within the limit, and record its serial in the incident. Build the complete replacement revocation set from `quorus.security.revoked-certificate-serials` plus the serials being revoked, because the update replaces the set and the API reports only its size. Send that set to every controller, and check each one's `revokedCertificateCount` on `GET /api/v1/security/trust`. Then, as ADR-0009 requires, add the same set to each controller's configuration before any restart. Finally, revoke the break-glass certificate's serial, or let it expire.
- `SEC-06` closes when this is delivered. The restricted scope set and the absence of roles mean `SEC-05` (first-match role evaluation) does not affect break-glass identities.
- Service-connection and secret-reference writes, and reading security events, stay gateway-only. If an incident needs one of them, for example to disable a compromised service connection, that is a change to the scope set in item 2 and a new revision of this ADR.

## Delivery and tests

Delivered under plan §6.1 as `SEC-06`, through the live controller HTTP boundary with generated test certificates, as `SecurityBoundaryIntegrationTest` does:

1. A break-glass certificate from a pinned CA, within the lifetime limit, replaces the revocation set. Its audit record is `BREAK_GLASS_AUTHENTICATION` with the serial and `notAfter`, and tenant and environment `platform`.
2. The same subject from another trusted CA is refused with `Q-AUTHN-BREAK-GLASS-REFUSED`, and the refusal is audited.
3. A certificate longer-lived than the limit is refused with `Q-AUTHN-BREAK-GLASS-REFUSED`.
4. A break-glass identity is refused a scope outside the set, for example `transfers:write`.
5. An ordinary binding is refused `security:trust:write` with `Q-AUTHZ-ELEVATION-REQUIRED`.
6. A certificate from a pinned CA with an ordinary binding's subject is refused with `Q-AUTHN-BREAK-GLASS-REFUSED`.
7. `GET /api/v1/security/me` reports the certificate's `notAfter` as the elevation expiry.
8. A follower accepts a break-glass revocation update.
9. Startup fails for: a subject in two lists; a scope outside the set, including `security-events:read`; a pin missing from the trust bundle; bindings without a pin; a lifetime limit above `PT4H`.

Documents updated in the same change: the [Security Deployment Guide](../../docs/QUORUS_SECURITY_DEPLOYMENT_GUIDE.md) for the settings and the CA requirements (direct issuance, random serials), the certificate incident runbook for the procedure, and the controller properties file for the defaults.

## Owner decisions (2026-11-03)

The three questions left open in v1.0 were answered at review:

1. **The scope set.** Revocation and read access only; service-connection writes are not added. They need a leader, so they would not help in a no-leader incident.
2. **The lifetime limit.** `PT1H` default, `PT4H` hard maximum, as proposed.
3. **Tenant and environment.** Fixed at `platform`, and taken out of the binding format. `security-events:read` is removed from the scope set, because that route is tenant-filtered.

## Revision history

| Version | Date | Changes |
|---|---|---|
| 1.1 | 2026-11-03 | Accepted after owner review. Owner decisions recorded for the three open questions: the scope set unchanged except that `security-events:read` is removed; `PT1H` and `PT4H` kept; tenant and environment fixed at `platform` and removed from the binding format. Review corrections: the audit log needs no leader; revocation is keyed by serial alone, so the break-glass CA must issue random serials; the pinned CA must be the direct issuer; refused attempts get `Q-AUTHN-BREAK-GLASS-REFUSED` and a counter; the procedure builds the full replacement set from configuration, because the API reports only its size; item 6 states the change to gateway-and-binding duplicates; the `security:trust:read` description corrected; the stale "planned ADR-0009" reference updated |
| 1.0 | 2026-10-19 | Proposed |
