<img src="../../docs/quorus-logo.png" alt="Quorus" width="120"/>

# ADR-0005: Agent Deployment Ownership

**Version:** 1.0  
**Date:** 2026-09-01  
**Author:** Mark Ray-Smith — Cityline Ltd  
**License:** Apache 2.0

**Status:** Accepted direction; implementation begins in Phase 5

## Context

Deploying agents changes code inside trusted network zones and can grant access to critical services. The controller control plane must not silently become a general remote-code execution system.

## Decision

Enterprise deployment platforms own agent installation, upgrade, rollback and removal. Quorus supplies signed artifacts, declarative desired versions, health/readiness signals and fleet status. Deployment automation authenticates independently and reports evidence. Controllers may drain, quarantine or deny incompatible agents but do not execute arbitrary installation commands on hosts.

## Consequences

Separation of duties remains possible between transfer operators and platform deployers. Canary, maintenance-window, signature-verification and rollback policies are enforced by the deployment system, while Quorus records desired/observed state and operational impact.
