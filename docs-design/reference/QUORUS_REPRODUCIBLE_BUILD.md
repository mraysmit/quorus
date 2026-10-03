<img src="../../docs/quorus-logo.png" alt="Quorus" width="120"/>

# Quorus Reproducible Build

**Version:** 1.2  
**Date:** 2026-10-03  
**Author:** Mark Ray-Smith — Cityline Ltd  
**License:** Apache 2.0

## What is and is not guaranteed

The build is **repeatable**, not byte-reproducible. Two clean builds from the same commit, with the same JDK and dependencies, produce the same test outcome and functionally identical artifacts. They do not produce byte-identical jars: no pom sets `project.build.outputTimestamp`, so jar entry timestamps differ between builds, and nothing compares the artifacts of two builds. Byte reproducibility would need that property in the root pom and a comparison step in CI.

## Locked baseline

- Java: JDK 27 (`maven.compiler.release` 27, set by `java.version` in the root `pom.xml`). CI uses Amazon Corretto 27 through `actions/setup-java` (decision `RT-Q4`).
- Maven: 3.9 or later. The version is not pinned: there is no Maven wrapper or enforcer rule, so CI uses the runner's Maven.
- Encoding: UTF-8.
- Dependency versions: Maven reactor and dependency-management entries in the root `pom.xml`.

The repository `.java-version` is authoritative for local Java selection. A release build must begin from a clean checkout and must not use module `target` directories from another run.

## Local verification

Run `mvn clean verify` twice from the repository root with JDK 27. The second run is a new clean build, not an incremental build. Docker is required for protocol and infrastructure integration tests that use Testcontainers. CI's "clean reproducible build" job does the same: it runs two clean builds and checks that both pass, without comparing their artifacts.

The Phase 0 focused gates are:

- authoritative invariant and lifecycle tests;
- durable single-controller and three-controller restart tests;
- OpenAPI path parity and schema compatibility tests;
- request-limit, problem-response, correlation-ID and redaction tests;
- document header, link, fence and endpoint checks;
- deployment configuration validation with loopback-only published ports.

CI has not yet passed as a whole (register item `ENG-07`), so these gates are currently shown by local runs.

## M0 verification

M0 was verified at revision `07195f6eaf33599d39aa0759cbe1d628b8a288d2` on Java 25, before the move to Java 27: two clean builds and 2,212 tests with no failures or errors.
