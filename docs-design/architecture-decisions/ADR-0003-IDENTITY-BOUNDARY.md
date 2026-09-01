<img src="../../docs/quorus-logo.png" alt="Quorus" width="120"/>

# ADR-0003: Enterprise Identity Boundary

**Version:** 1.1  
**Date:** 2026-09-01  
**Author:** Mark Ray-Smith — Cityline Ltd  
**License:** Apache 2.0

**Status:** Accepted; Phase 1 foundation implemented

## Context

Human users, integrations, controllers, agents and deployment systems require distinct identities. Tenant identifiers supplied in request bodies are not trustworthy identity evidence.

## Decision

Quorus supports an enterprise gateway/OIDC boundary for human and integration identities, mutual TLS identities for controllers and agents, and workload identity for deployment automation. The controller derives tenant and environment scope from verified identity claims and applies resource policy; request fields may narrow but never expand that scope. Internal identity assertions are accepted only over a mutually authenticated trusted hop and are covered by audit evidence.

During Phase 0, HTTP was bound and published only on loopback because authentication and transport trust had not yet been implemented.

The Phase 1 foundation implements this decision with TLS 1.3 mutual authentication, exact trusted-gateway certificate subjects, exact direct-workload certificate bindings, tenant/environment derivation, stable policy decisions, effective-identity and authorization-explanation APIs, and hash-chained security-decision audit. The packaged production profile is fail-closed. Plaintext compatibility constructors are restricted to explicit development/test use and emit a warning.

Certificate enrollment, automated overlap rotation, revocation propagation, and expiry monitoring remain required before this ADR's operational consequences are fully satisfied.

## Consequences

No endpoint is anonymously exposed to untrusted networks. Break-glass access is time-bound, strongly authenticated and audited. Identity-provider, certificate and policy outages fail closed for mutations while health diagnostics remain carefully bounded.
