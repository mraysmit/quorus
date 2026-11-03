<img src="../../docs/quorus-logo.png" alt="Quorus" width="120"/>

# ADR-0007: Layered Configuration Without System Properties

**Version:** 1.0  
**Date:** 2026-11-03  
**Author:** Mark Ray-Smith — Cityline Ltd  
**License:** Apache 2.0

**Status:** Accepted. It records the configuration design delivered on 2026-09-03 (commit `9fed585a`), written down under register item DR-D4 in the [Outstanding Work Register](../task/QUORUS_OUTSTANDING_WORK_REGISTER_2026-11-03.md). The residual defects are register items `CFG-02` to `CFG-06`.

**Relates to:** the archived [configuration isolation handover](../archive/QUORUS_CONFIGURATION_ISOLATION_HANDOVER_2026-09-03.md), which recorded the delivery; [ADR-0006](ADR-0006-RAFT-STORAGE-RAFTLOG-AND-SNAPSHOT-SIDECAR.md), whose storage library reads settings of its own.

## Context

Before 2026-09-03 the controller, the agent and the core engine each read configuration through a static singleton (`AppConfig.get()`, `AgentConfig.get()`, a mutable `QuorusConfiguration`), and through JVM system properties. Images built `-Dquorus.raft.*` flags in their entrypoint, and some protocol settings, such as the NFS mount root and the encrypted-mount attestations, could be set only as system properties.

That meant two controllers or agents in one JVM, or two tests in one surefire fork, shared configuration through static state and `System.setProperty`. A test could pass or fail depending on what an earlier test had set, and a value could reach a component without passing through any configuration the operator could see.

## Decision

1. **Each component builds its configuration once, at its application boundary, and passes it explicitly.** `AppConfig(profile, overrides)`, `AgentConfig(profile, overrides)` and `QuorusConfiguration(profile, overrides)` are ordinary instances. There is no static accessor, and components that need a setting take the configuration, or the value, as a constructor argument.

2. **Every instance reads the same four layers, lowest priority first:**
   1. the packaged defaults (`quorus-controller.properties`, `quorus-agent.properties`, `quorus.properties`);
   2. an optional profile resource, `<name>-<profile>.properties`, unless the profile is `default`;
   3. environment variables, named by upper-casing the key and replacing `.` and `-` with `_` (for example `QUORUS_RAFT_STORAGE_PATH`);
   4. explicit `Properties` supplied by the caller.

   An environment variable applies whether or not its key is in the packaged file. The agent also accepts a fixed set of older environment names, such as `CONTROLLER_URL`, below the canonical names. A blank value counts as unset.

3. **JVM system properties are not a configuration source.** Nothing in Quorus reads a `quorus.*` system property; a caller that needs an override passes it as `Properties`. A test in each module (`systemPropertyIsNotAConfigurationChannel` and its equivalents in `AgentConfigTest` and `QuorusConfigurationTest`) sets a system property and shows that it has no effect. System properties are still read for platform facts such as `os.name` and `java.io.tmpdir`, which are not configuration.

4. **Failing to read the packaged defaults fails construction.** A missing packaged resource logs a warning, and an unreadable one throws. A missing profile resource is normal.

5. **Validation is separate from loading.** `AppConfig.validate()` and `SecurityConfig.validate()` check the assembled values at startup and fail the controller before it opens storage or listens.

## Alternatives considered

- **Keep the singletons and reset them between tests.** Rejected: it hides the dependency, and it cannot support two instances in one JVM, which the multi-controller tests need.
- **Keep system properties as a top layer.** Rejected: a system property is global to the JVM, invisible in the configuration files, and was the main channel through which tests contaminated each other. Environment variables cover the container case, and explicit overrides cover the programmatic case.
- **A configuration framework** such as MicroProfile Config or Spring's `Environment`. Not chosen: the four layers need about a hundred lines (`LayeredProperties`), and a framework would bring its own source ordering and system-property layer, which is exactly what is excluded.

## Consequences

- Images set `QUORUS_*` environment variables. The controller entrypoint script was changed to export them instead of building `-D` flags, then deleted under `ENG-20`: the image never ran it.
- `raftlog-core` is outside this model. It resolves its own settings (free-space floor, maximum payload, write verification) from JVM system properties, `RAFTLOG_*` environment variables or a `raftlog.properties` file. Quorus does not set or expose them, so their defaults apply (ADR-0006).
- Residual defects, all open in Section I of the register:
  - `CFG-02`: an agent accessor default differs from its packaged value;
  - `CFG-03`: an invalid number falls back to the accessor default with a warning instead of failing validation;
  - `CFG-04`: a blank environment value cannot clear a packaged value;
  - `CFG-05`: `QuorusConfiguration` reads `System.getenv()` directly, so its environment layer cannot be injected in tests;
  - `CFG-06`: a boolean value other than `true`, such as `yes`, `1` or a typo, reads as `false` with no warning. For `quorus.raft.storage.fsync` this turns durability off.

  Each conflicts with the intent of item 5, that an invalid configuration stops startup.

## Revision history

| Version | Date | Changes |
|---|---|---|
| 1.0 | 2026-11-03 | Records the configuration design delivered on 2026-09-03, under register item DR-D4 |
