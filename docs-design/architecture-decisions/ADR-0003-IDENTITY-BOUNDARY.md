<img src="../../docs/quorus-logo.png" alt="Quorus" width="120"/>

# ADR-0003: Enterprise Identity Boundary

**Version:** 1.0  
**Date:** 2026-09-01  
**Author:** Mark Ray-Smith — Cityline Ltd  
**License:** Apache 2.0

**Status:** Accepted direction; implementation begins in Phase 1

## Context

Human users, integrations, controllers, agents and deployment systems require distinct identities. Tenant identifiers supplied in request bodies are not trustworthy identity evidence.

## Decision

Quorus supports an enterprise gateway/OIDC boundary for human and integration identities, mutual TLS identities for controllers and agents, and workload identity for deployment automation. The controller derives tenant and environment scope from verified identity claims and applies resource policy; request fields may narrow but never expand that scope. Internal identity assertions are accepted only over a mutually authenticated trusted hop and are covered by audit evidence.

Phase 0 binds HTTP to loopback and loopback-publishes container ports because authentication and transport trust are not yet implemented.

## Consequences

No endpoint is anonymously exposed to untrusted networks. Break-glass access is time-bound, strongly authenticated and audited. Identity-provider, certificate and policy outages fail closed for mutations while health diagnostics remain carefully bounded.
