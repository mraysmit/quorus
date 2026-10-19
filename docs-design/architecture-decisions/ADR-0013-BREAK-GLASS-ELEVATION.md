<img src="../../docs/quorus-logo.png" alt="Quorus" width="120"/>

# ADR-0013: Break-Glass Elevation for Direct mTLS Identities

**Version:** 1.0  
**Date:** 2026-10-19  
**Author:** Mark Ray-Smith — Cityline Ltd  
**License:** Apache 2.0

**Status:** Proposed on 2026-10-19 for owner review. It designs decision `DR-Q3` in the [Outstanding Work Register](../task/QUORUS_OUTSTANDING_WORK_REGISTER_2026-10-19.md#3-decision-log), which required a short design before delivery. Delivery item `SEC-06` implements it once this ADR is accepted.

**Relates to:** [ADR-0003](ADR-0003-IDENTITY-BOUNDARY.md), which requires break-glass access to be "time-bound, strongly authenticated and audited". The planned ADR-0009 (runtime trust-state scope) covers revocation, which is the main thing break-glass is for.

## Context

Some API scopes need a current, time-bounded elevation as well as the scope itself. They are `security:trust:write`, writes to service connections and secret references, and three scopes that no route uses yet. The check is in `AuthorizationPolicyEngine`.

Only a trusted gateway can grant elevation today. It sends `X-Quorus-Elevation-Expires-At` with its other identity assertions. A directly bound mTLS identity, configured in `quorus.security.mtls-identities` as a certificate subject bound to a principal, roles and scopes, is created with no elevation. It can never perform an elevated operation.

That leaves no way to revoke a certificate while the gateway is unavailable, or after the gateway's own identity is revoked. Revocation (`PUT /api/v1/security/trust/revocations`) needs `security:trust:write` with elevation, and it is the central step of the [certificate incident runbook](../../docs/QUORUS_CERTIFICATE_INCIDENT_RUNBOOK.md). The runbook already says to use a break-glass path only if it is "pre-approved, time-bounded, and externally audited", but Quorus provides none.

Decision `DR-Q3` (2026-10-03) settled the direction: a separately configured break-glass certificate binding may elevate, with a short elevation window and mandatory audit; ordinary certificate bindings never elevate.

Three facts shape the design:

- A direct binding matches on the certificate subject alone. Any CA in the controller's HTTP trust bundle could issue a certificate with that subject.
- Runtime trust state is node-local (`DR-Q2`). The revocation route and the authorization-check route are answered by every controller, not only the leader (`SEC-09`).
- Every authenticated request already writes authentication and authorization audit records that are durable before the request proceeds (`ENG-16`). A failed audit write fails the request.

## Decision

1. **Break-glass bindings are a separate list.** The new setting `quorus.security.break-glass-identities` uses the form `subject=>principal|tenant|environment|scopes`. A break-glass identity is always of type `HUMAN` and has **no roles**, only the scopes listed. Roles are excluded because a role grants scopes by prefix: `ADMINISTRATOR` grants every scope. The list is empty by default, so break-glass is off until an operator configures it.

2. **The scopes are limited to incident response.** Startup fails if a break-glass binding lists any scope outside this set:

   | Scope | Use |
   |---|---|
   | `security:trust:write` | Replace the runtime revocation set |
   | `security:trust:read` | Read the trust state and the revocation set |
   | `security:self:read` | `GET /api/v1/security/me`, to confirm the identity and its elevation expiry |
   | `security:explain` | Ask why a request would be allowed or denied |
   | `security-events:read` | Read service-connection security events |

3. **Break-glass certificates come from a pinned issuer.** The new setting `quorus.security.break-glass.issuer-sha256` lists the SHA-256 fingerprints of the CA certificates that may issue them. At startup the controller finds each pinned CA certificate in its HTTP trust bundle; startup fails if one is missing, or if break-glass bindings are configured with no pin. A certificate authenticates as a break-glass identity only if:
   - its subject is a break-glass binding; and
   - its signature verifies against a pinned CA's public key.

   A certificate with a break-glass subject from any other trusted CA is refused. A certificate from a pinned CA is refused unless its subject is a break-glass binding: it can never authenticate as an ordinary binding or as the gateway. The intended issuer is a dedicated CA whose key is held offline by the security team, so custody of that key is the approval.

4. **The elevation window is the certificate's lifetime.** A break-glass certificate whose validity period (`notAfter` minus `notBefore`) is longer than `quorus.security.break-glass.max-certificate-lifetime` is refused. The default is `PT1H`, and startup fails above `PT4H`. Both the identity's expiry and its elevation expiry are the certificate's `notAfter`. There is no activation step and no state: a certificate minted for an incident elevates its holder until it expires, and the normal serial revocation ends it sooner.

5. **Every use is audited as break-glass.** The authentication audit record of a break-glass request has the event type `BREAK_GLASS_AUTHENTICATION`. Its attributes carry the certificate serial, the pinned issuer's fingerprint and `notAfter`, and it is durable before the request proceeds, as for any authentication. Each request also logs a `WARN` line and increments a `quorus.security.break_glass.requests` counter. When break-glass bindings are configured, startup logs a `WARN` line and writes a `SECURITY_CONFIGURATION` audit record listing the bound subjects and pinned issuers.

6. **Ordinary bindings and the gateway are unchanged.** An ordinary direct binding still never elevates, and a test pins that. A subject may appear in only one of the trusted gateway subjects, the ordinary bindings and the break-glass bindings; startup fails otherwise.

7. **It works on every controller.** The design holds no state, so a break-glass certificate works the same on every node. The revocation and authorization-check routes are already answered by followers, so an operator can revoke on each controller even with no leader. That matches the node-local revocation model of `DR-Q2`.

## Alternatives considered

- **An activation endpoint.** A standing break-glass certificate would call `POST /api/v1/security/break-glass` with a reason, to open a short node-local window. Rejected: a stolen standing certificate could open its own window. It would also need volatile per-node state, activation on every controller, and another leader-guard exemption. Tying the window to a short-lived certificate puts the control in the CA instead.
- **An elevation flag on ordinary bindings.** Rejected by `DR-Q3`: ordinary bindings never elevate.
- **Always-elevated break-glass bindings with a long-lived certificate.** Rejected: there would be no short window.
- **A second factor**, such as a one-time code or a second certificate. Out of scope for the first version; it can be added later without changing the binding format.

## Consequences

- Operators who want break-glass must run a dedicated issuing CA, keep its key offline, add its certificate to every controller's HTTP trust bundle, and configure the pin and bindings. Without that, behaviour is unchanged.
- The break-glass procedure is: mint a certificate with a break-glass subject and a lifetime within the limit; record its serial in the incident; use it against each controller; then add its serial to the revocations, or let it expire.
- `SEC-06` closes when this is delivered. The restricted scope set and the absence of roles mean `SEC-05` (first-match role evaluation) does not affect break-glass identities.
- Service-connection and secret-reference writes stay gateway-only. If an incident needs one of them, for example to disable a compromised service connection, that is a change to the scope set in item 2 and a new revision of this ADR.

## Delivery and tests

Delivered under plan §6.1 as `SEC-06`, through the live controller HTTP boundary with generated test certificates, as `SecurityBoundaryIntegrationTest` does:

1. A break-glass certificate from a pinned CA, within the lifetime limit, replaces the revocation set. Its audit record is `BREAK_GLASS_AUTHENTICATION` with the serial and `notAfter`.
2. The same subject from another trusted CA is refused.
3. A certificate longer-lived than the limit is refused.
4. A break-glass identity is refused a scope outside the set, for example `transfers:write`.
5. An ordinary binding is refused `security:trust:write` with `Q-AUTHZ-ELEVATION-REQUIRED`.
6. A certificate from a pinned CA with an ordinary binding's subject is refused.
7. `GET /api/v1/security/me` reports the certificate's `notAfter` as the elevation expiry.
8. A follower accepts a break-glass revocation update.
9. Startup fails for: a subject in two lists; a scope outside the set; a pin missing from the trust bundle; bindings without a pin; a lifetime limit above `PT4H`.

Documents updated in the same change: the [Security Deployment Guide](../../docs/QUORUS_SECURITY_DEPLOYMENT_GUIDE.md) for the settings, the certificate incident runbook for the procedure, and the controller properties file for the defaults.

## Open questions for the owner

1. **The scope set (item 2).** It allows revocation and read access only. Should service-connection writes be added, so that a compromised connection can be disabled without the gateway?
2. **The lifetime limit (item 4).** The default is one hour and the hard maximum four hours. Are those right for the expected incident response time?
3. **Tenant and environment.** `SecurityIdentity` requires both, but the revocation set applies to the whole controller. The binding format keeps them for a consistent audit record. Is a fixed value such as `platform` acceptable to you?

## Revision history

| Version | Date | Changes |
|---|---|---|
| 1.0 | 2026-10-19 | Proposed |
