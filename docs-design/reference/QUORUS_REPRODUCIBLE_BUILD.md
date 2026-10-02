<img src="../../docs/quorus-logo.png" alt="Quorus" width="120"/>

# Quorus Reproducible Build

**Version:** 1.1  
**Date:** 2026-10-02  
**Author:** Mark Ray-Smith — Cityline Ltd  
**License:** Apache 2.0

## Locked baseline

- Java: OpenJDK 25 (`--release 25`)
- Maven: 3.9.11 in CI
- Encoding: UTF-8
- Dependency versions: Maven reactor and dependency-management entries in the root `pom.xml`
- CI runtime: `maven:3.9.11-eclipse-temurin-25`

The repository `.java-version` is authoritative for local Java selection. A release build must begin from a clean checkout and must not use module `target` directories from another run.

## Local verification

Run `mvn clean verify` twice from the repository root with Java 25. The second run is a new clean build, not an incremental build. Docker is required for protocol and infrastructure integration tests that use Testcontainers.

The Phase 0 focused gates are:

- authoritative invariant and lifecycle tests;
- durable single-controller and three-controller restart tests;
- OpenAPI path parity and schema compatibility tests;
- request-limit, problem-response, correlation-ID and redaction tests;
- document header, link, fence and endpoint checks;
- deployment configuration validation with loopback-only published ports.

## M0 verification

M0 was verified and committed at revision `07195f6eaf33599d39aa0759cbe1d628b8a288d2`: two clean Java 25 builds and 2,212 tests with no failures or errors.
