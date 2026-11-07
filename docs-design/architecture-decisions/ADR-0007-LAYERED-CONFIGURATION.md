<img src="../../docs/quorus-logo.png" alt="Quorus" width="120"/>

# ADR-0007: Layered Configuration Without System Properties

**Version:** 1.1  
**Date:** 2026-11-07  
**Author:** Mark Ray-Smith — Cityline Ltd  
**License:** Apache 2.0

**Status:** Accepted. It records the configuration design delivered on 2026-09-03 (commit `9fed585a`), written down under register item DR-D4 in the [Outstanding Work Register](../task/QUORUS_OUTSTANDING_WORK_REGISTER_2026-11-07.md). The residual defects, register items `CFG-02` to `CFG-06`, were resolved on 2026-11-07 (item 6 and Consequences).

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

   An environment variable applies whether or not its key is in the packaged file. The agent also accepts a fixed set of older environment names, such as `CONTROLLER_URL`, below the canonical names. A blank value counts as unset: a blank environment variable leaves the lower layer's value in place, and a blank value that reaches an accessor gives the accessor's default. Nothing can set a key to blank to clear it (`CFG-04`, closed by design on 2026-11-07).

3. **JVM system properties are not a configuration source.** Nothing in Quorus reads a `quorus.*` system property; a caller that needs an override passes it as `Properties`. A test in each module (`systemPropertyIsNotAConfigurationChannel` and its equivalents in `AgentConfigTest` and `QuorusConfigurationTest`) sets a system property and shows that it has no effect. System properties are still read for platform facts such as `os.name` and `java.io.tmpdir`, which are not configuration.

4. **Failing to read the packaged defaults fails construction.** A missing packaged resource logs a warning, and an unreadable one throws. A missing profile resource is normal.

5. **Validation is separate from loading.** `AppConfig.validate()` and `SecurityConfig.validate()` check the assembled values at startup and fail the controller before it opens storage or listens.

6. **A value that does not parse is an error, not a default.** A number accessor throws `IllegalStateException`, naming the key and the value, when the value is not a number. A boolean accessor accepts only `true` or `false`, ignoring case and surrounding space, and throws the same way for anything else, such as `yes`, `1` or a typo (`CFG-03`, `CFG-06`). `AppConfig.validate()` reads every typed `AppConfig` accessor, so a malformed value stops the controller before it opens storage. `AgentConfiguration.from` reads every agent accessor, so it stops the agent. Building a configuration does not throw: the startup summary that `AppConfig` and `AgentConfig` log is diagnostic only. In `AgentConfig` an invalid value ends the summary with a warning; `AppConfig` can still fail construction when a summarised value is malformed. Settings that other classes read with `getInt`, `getLong` or `getBoolean` and their own key, such as `quorus.http.dns.timeout-ms`, fail when they are read, during deployment.

## Alternatives considered

- **Keep the singletons and reset them between tests.** Rejected: it hides the dependency, and it cannot support two instances in one JVM, which the multi-controller tests need.
- **Keep system properties as a top layer.** Rejected: a system property is global to the JVM, invisible in the configuration files, and was the main channel through which tests contaminated each other. Environment variables cover the container case, and explicit overrides cover the programmatic case.
- **A configuration framework** such as MicroProfile Config or Spring's `Environment`. Not chosen: the four layers need about a hundred lines (`LayeredProperties`), and a framework would bring its own source ordering and system-property layer, which is exactly what is excluded.

## Consequences

- Images set `QUORUS_*` environment variables. The controller entrypoint script was changed to export them instead of building `-D` flags, then deleted under `ENG-20`: the image never ran it.
- `raftlog-core` is outside this model. It resolves its own settings (free-space floor, maximum payload, write verification) from JVM system properties, `RAFTLOG_*` environment variables or a `raftlog.properties` file. Quorus does not set or expose them, so their defaults apply (ADR-0006).
- Residual defects found after delivery, all resolved on 2026-11-07 (register Section I):
  - `CFG-02`: the agent's foreign-assignment threshold defaulted to 3 in code but shipped as 1. The code default is now 1, and a test compares every agent accessor default with the packaged value.
  - `CFG-03`: an invalid number fell back to the accessor default with a warning. It now fails (item 6).
  - `CFG-04`: a blank environment value cannot clear a packaged value. Closed by design: blank means unset (item 2), and a test pins it.
  - `CFG-05`: `QuorusConfiguration` read `System.getenv()` directly. Its environment layer can now be injected through a package-private constructor, as `AppConfig`'s and `AgentConfig`'s can. An environment variable now also reaches a key that no resource declares, as item 2 requires.
  - `CFG-06`: a boolean value other than `true`, such as `yes`, `1` or a typo, read as `false` with no warning, so `quorus.raft.storage.fsync=yes` turned durability off. It now fails (item 6).
- Operators whose configuration relied on the old leniency, such as `yes` for a boolean or a number with a unit, will find that startup now fails and names the key. All the repository's properties and Compose files use `true`/`false` and plain numbers.

## Revision history

| Version | Date | Changes |
|---|---|---|
| 1.1 | 2026-11-07 | Item 6 added: a malformed number or a boolean other than `true`/`false` stops startup (`CFG-03`, `CFG-06`). Item 2 states that blank means unset in every layer (`CFG-04`, closed by design). Consequences record `CFG-02` to `CFG-06` as resolved |
| 1.0 | 2026-11-03 | Records the configuration design delivered on 2026-09-03, under register item DR-D4 |
