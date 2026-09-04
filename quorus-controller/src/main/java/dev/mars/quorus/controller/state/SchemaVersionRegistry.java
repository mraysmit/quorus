/*
 * Copyright 2025 Mark Andrew Ray-Smith Cityline Ltd
 * Licensed under the Apache License, Version 2.0.
 */
package dev.mars.quorus.controller.state;

import java.util.EnumMap;
import java.util.Map;

/** Single source of truth for persisted and externally exchanged schema versions. */
public final class SchemaVersionRegistry {

    public enum Contract {
        RAFT_COMMAND_ENVELOPE,
        STATE_SNAPSHOT,
        REST_API,
        CONFIGURATION,
        WORKFLOW_DEFINITION,
        AGENT_PROTOCOL
    }

    public record VersionRange(int oldestReadable, int currentWritable) {
        public VersionRange {
            if (oldestReadable < 0 || currentWritable < 1 || oldestReadable > currentWritable) {
                throw new IllegalArgumentException("Invalid schema compatibility range");
            }
        }

        public boolean canRead(int version) {
            return version >= oldestReadable && version <= currentWritable;
        }
    }

    private static final Map<Contract, VersionRange> VERSIONS;

    static {
        EnumMap<Contract, VersionRange> versions = new EnumMap<>(Contract.class);
        // Version zero represents the unversioned alpha encoding and remains readable in Phase 0.
        // Version 3 fences older readers from R2 registry key/migration semantics.
        versions.put(Contract.RAFT_COMMAND_ENVELOPE, new VersionRange(0, 3));
        versions.put(Contract.STATE_SNAPSHOT, new VersionRange(0, 3));
        versions.put(Contract.REST_API, new VersionRange(1, 1));
        versions.put(Contract.CONFIGURATION, new VersionRange(1, 1));
        versions.put(Contract.WORKFLOW_DEFINITION, new VersionRange(1, 1));
        versions.put(Contract.AGENT_PROTOCOL, new VersionRange(1, 1));
        VERSIONS = Map.copyOf(versions);
    }

    private SchemaVersionRegistry() {
    }

    public static VersionRange version(Contract contract) {
        return VERSIONS.get(contract);
    }

    public static int current(Contract contract) {
        return version(contract).currentWritable();
    }

    public static void requireReadable(Contract contract, int version) {
        if (!version(contract).canRead(version)) {
            throw new IllegalArgumentException("Unsupported " + contract + " schema version: " + version);
        }
    }

    public static Map<Contract, VersionRange> all() {
        return VERSIONS;
    }
}
