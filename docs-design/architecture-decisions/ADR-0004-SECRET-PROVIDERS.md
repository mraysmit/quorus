<img src="../../docs/quorus-logo.png" alt="Quorus" width="120"/>

# ADR-0004: Secret Provider Boundary

**Version:** 1.0  
**Date:** 2026-09-01  
**Author:** Mark Ray-Smith — Cityline Ltd  
**License:** Apache 2.0

**Status:** Accepted and implemented in Phase 4

## Context

Transfer agents connect to financial-services endpoints whose credentials require controlled issuance, rotation, revocation and audit. Credentials embedded in URIs, workflows, controller state or deployment files are unacceptable.

## Decision

Quorus stores opaque credential references and non-sensitive connection policy only. A provider interface resolves short-lived credentials at the executing agent using its workload identity. Returned secrets remain in memory for the minimum operation lifetime, are never persisted or returned by REST, and are covered by centralized redaction. Provider access, denial and rotation events are audited without secret values.

Phase 0 adds shared redaction and tests. It does not make URI-embedded credentials an approved configuration mechanism.

Phase 4 implements the provider SPI and HashiCorp Vault KV v2 provider at the executing-agent boundary. Production transfers use a Raft-backed service connection and opaque reference; controller and agent policy checks precede provider access, and the closeable runtime lease is wiped after transfer completion. Vault origin configuration requires a credential-free HTTPS origin and workload token injection; no Vault response, token, or resolved value is persisted or returned.

## Consequences

Providers can include enterprise vaults and cloud secret managers without changing transfer definitions. Provider outage, expired credentials and revocation have explicit failure classifications. Support bundles contain references and policy decisions, never resolved values.
