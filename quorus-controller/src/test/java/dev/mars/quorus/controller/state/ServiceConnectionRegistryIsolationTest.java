/*
 * Copyright 2026 Mark Andrew Ray-Smith Cityline Ltd
 * Licensed under the Apache License, Version 2.0.
 */
package dev.mars.quorus.controller.state;

import dev.mars.quorus.controller.raft.grpc.RaftCommandMessage;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** R2 authoritative ownership and deterministic migration acceptance. */
class ServiceConnectionRegistryIsolationTest {
    private static final String LEGACY = "phase4.secret-reference.";

    @Test
    void stateApplicationRejectsKeyPayloadOwnershipMismatch() {
        QuorusStateStore state = new QuorusStateStore();
        ServiceConnectionRegistry registry = new ServiceConnectionRegistry(state);
        String key = registry.secretKey("bank", "ledger");
        assertInstanceOf(CommandResult.Rejected.class,
                state.apply(new SystemMetadataCommand.Set(key, secret("bank.other", "ledger"))));
        assertNull(state.getMetadata(key));
    }

    @Test
    void stateApplicationRejectsKeyPayloadIdMismatch() {
        QuorusStateStore state = new QuorusStateStore();
        ServiceConnectionRegistry registry = new ServiceConnectionRegistry(state);
        assertInstanceOf(CommandResult.Rejected.class, state.apply(new SystemMetadataCommand.Set(
                registry.secretKey("bank", "ledger"), secret("bank", "different"))));
    }

    @Test
    void firstVersionedMutationMigratesLegacyStateThroughReplicatedCommand() {
        QuorusStateStore leader = new QuorusStateStore();
        QuorusStateStore follower = new QuorusStateStore();
        String oldKey = LEGACY + "bank.branch.ledger";
        RaftCommand seed = new SystemMetadataCommand.Set(oldKey, secret("bank.branch", "ledger"));
        leader.apply(seed);
        follower.apply(ProtobufCommandCodec.deserialize(ProtobufCommandCodec.serialize(seed)));
        ServiceConnectionRegistry registry = new ServiceConnectionRegistry(leader);
        RaftCommand mutation = new SystemMetadataCommand.Set(registry.secretKey("bank", "branch.ledger"),
                secret("bank", "branch.ledger"));
        assertInstanceOf(CommandResult.Success.class, leader.apply(mutation));
        assertInstanceOf(CommandResult.Success.class, follower.apply(
                ProtobufCommandCodec.deserialize(ProtobufCommandCodec.serialize(mutation))));
        assertNull(leader.getMetadata(oldKey), "Migration must remove the ambiguous legacy address");
        assertEquals(leader.getSystemMetadata(), follower.getSystemMetadata());
        assertEquals("bank.branch", registry.findSecret("bank.branch", "ledger").tenantId());
        assertEquals("bank", registry.findSecret("bank", "branch.ledger").tenantId());
        assertInstanceOf(CommandResult.Success.class, leader.apply(mutation));
        assertEquals(follower.getSystemMetadata(), leader.getSystemMetadata(), "Replay must be idempotent");
    }

    @Test
    void ambiguousLegacyOwnershipRejectsMigrationWithoutPartialWrites() {
        QuorusStateStore state = new QuorusStateStore();
        state.apply(new SystemMetadataCommand.Set(LEGACY + "bank.ledger", secret("unknown", "ledger")));
        ServiceConnectionRegistry registry = new ServiceConnectionRegistry(state);
        var before = state.getSystemMetadata();
        assertInstanceOf(CommandResult.Rejected.class, state.apply(new SystemMetadataCommand.Set(
                registry.secretKey("bank", "new"), secret("bank", "new"))));
        assertEquals(before, state.getSystemMetadata());
    }

    @Test
    void legacyReadMustNotReturnPayloadOwnedByAnotherTenant() {
        QuorusStateStore state = new QuorusStateStore();
        state.apply(new SystemMetadataCommand.Set(LEGACY + "bank.branch.ledger", secret("bank.branch", "ledger")));
        assertNull(new ServiceConnectionRegistry(state).findSecret("bank", "branch.ledger"));
    }

    @Test
    void legacyPrefixListingFiltersExactPayloadOwner() {
        QuorusStateStore state = new QuorusStateStore();
        state.apply(new SystemMetadataCommand.Set(LEGACY + "bank.branch.ledger", secret("bank.branch", "ledger")));
        assertTrue(new ServiceConnectionRegistry(state).listSecrets("bank").isEmpty());
    }

    @Test
    void legacyWritesCannotReintroduceAmbiguousKeysAfterMigration() {
        QuorusStateStore state = new QuorusStateStore();
        ServiceConnectionRegistry registry = new ServiceConnectionRegistry(state);
        state.apply(new SystemMetadataCommand.Set(registry.secretKey("bank", "new"), secret("bank", "new")));
        assertInstanceOf(CommandResult.Rejected.class, state.apply(new SystemMetadataCommand.Set(
                LEGACY + "bank.ledger", secret("bank", "ledger"))));
    }


    @Test
    void versionedRegistryCommandsFenceOutOldReaders() throws Exception {
        var state = new QuorusStateStore();
        var registry = new ServiceConnectionRegistry(state);
        var bytes = ProtobufCommandCodec.serialize(new SystemMetadataCommand.Set(
                registry.secretKey("bank", "ledger"), secret("bank", "ledger")));
        assertEquals(3, RaftCommandMessage.parseFrom(bytes).getSchemaVersion(),
                "Old schema-2 readers must reject the new migration semantics");
    }

    @Test
    void migratedSnapshotsFenceOutOldReaders() {
        assertEquals(3, new QuorusSnapshot().getSchemaVersion(),
                "Old schema-2 readers must reject versioned registry snapshots");
    }


    @Test
    void conflictingLegacyAndVersionedOwnershipFailsClosedOnRead() {
        QuorusStateStore empty = new QuorusStateStore();
        String key = new ServiceConnectionRegistry(empty).secretKey("bank", "ledger");
        QuorusStateStore state = new QuorusStateStore(Map.of(key, secret("bank", "ledger"),
                LEGACY + "bank.ledger", new JsonObject(secret("bank", "ledger")).put("version", "2").encode()));
        assertThrows(IllegalArgumentException.class,
                () -> new ServiceConnectionRegistry(state).findSecret("bank", "ledger"));
    }

    @Test
    void missingDeleteDoesNotPartiallyCommitMigration() {
        QuorusStateStore state = new QuorusStateStore(Map.of(
                LEGACY + "bank.ledger", secret("bank", "ledger")));
        var before = state.getSystemMetadata();
        var registry = new ServiceConnectionRegistry(state);
        assertInstanceOf(CommandResult.NotFound.class,
                state.apply(new SystemMetadataCommand.Delete(registry.secretKey("bank", "absent"))));
        assertEquals(before, state.getSystemMetadata(), "Unsuccessful mutation must not publish migration");
    }

    @Test
    void migrationRejectsMalformedExistingVersionedRecordAtomically() {
        QuorusStateStore empty = new QuorusStateStore();
        String key = new ServiceConnectionRegistry(empty).secretKey("bank", "ledger");
        QuorusStateStore state = new QuorusStateStore(Map.of(key, secret("foreign", "ledger"),
                LEGACY + "bank.old", secret("bank", "old")));
        var before = state.getSystemMetadata();
        var registry = new ServiceConnectionRegistry(state);
        assertInstanceOf(CommandResult.Rejected.class, state.apply(new SystemMetadataCommand.Set(
                registry.secretKey("bank", "new"), secret("bank", "new"))));
        assertEquals(before, state.getSystemMetadata());
    }


    @Test
    void schemaTwoSnapshotMigratesConnectionsSecretsAndEventsWithoutChangingPayloads() {
        String tenant = "bank.branch";
        String timestamp = "2026-09-04T00:00:00Z";
        JsonObject connection = new JsonObject().put("serviceConnectionId", "settlement").put("tenantId", tenant)
                .put("protocol", "SFTP").put("endpoint", "sftp://192.0.2.10:22").put("networkZone", "payments-dmz")
                .put("allowedPaths", new JsonArray().add("/outbound"))
                .put("allowedDirections", new JsonArray().add("DOWNLOAD"))
                .put("allowedAgentPools", new JsonArray().add("payments-agents"))
                .put("owner", "payments-platform").put("environment", "PRODUCTION").put("classification", "CONFIDENTIAL")
                .put("secretReferenceId", "ledger").put("serviceIdentity", "payments-batch").put("authenticationType", "PASSWORD")
                .put("trustPolicy", new JsonObject().put("sshHostKeyFingerprints",
                        new JsonArray().add("SHA256:synthetic-host-key-pin")))
                .put("egressPolicy", new JsonObject().put("allowedHostnames", new JsonArray().add("192.0.2.10"))
                        .put("allowedCidrs", new JsonArray().add("192.0.2.0/24"))
                        .put("allowedPorts", new JsonArray().add(22)))
                .put("policyVersion", 7).put("status", "ACTIVE").put("createdAt", timestamp).put("updatedAt", timestamp);
        JsonObject event = new JsonObject().put("eventId", "migration-event").put("tenantId", tenant)
                .put("eventType", "SERVICE_CONNECTION_CREATED").put("resourceType", "SERVICE_CONNECTION")
                .put("resourceId", "settlement").put("outcome", "SUCCESS").put("reasonCode", "Q-CONNECTION-CREATED")
                .put("policyVersion", 7).put("timestamp", timestamp);
        String eventKey = "phase4.security-event." + tenant + "."
                + Instant.parse(timestamp).toEpochMilli() + ".00000000-0000-0000-0000-000000000001";
        var legacy = Map.of(LEGACY + tenant + ".ledger", secret(tenant, "ledger"),
                "phase4.service-connection." + tenant + ".settlement", connection.encode(), eventKey, event.encode());
        QuorusStateStore source = new QuorusStateStore(legacy);
        JsonObject snapshot = new JsonObject(Buffer.buffer(source.takeSnapshot()))
                .put("schemaVersion", 2);
        QuorusStateStore recovered = new QuorusStateStore();
        recovered.restoreSnapshot(snapshot.toBuffer().getBytes());
        var registry = new ServiceConnectionRegistry(recovered);
        assertEquals(7, registry.findConnection(tenant, "settlement").policyVersion());
        assertInstanceOf(CommandResult.Success.class, recovered.apply(new SystemMetadataCommand.Set(
                registry.secretKey("bank", "new"), secret("bank", "new"))));
        assertTrue(legacy.keySet().stream().noneMatch(recovered.getSystemMetadata()::containsKey));
        assertEquals(connection.encode(), recovered.getMetadata(registry.connectionKey(tenant, "settlement")));
        assertEquals(secret(tenant, "ledger"), recovered.getMetadata(registry.secretKey(tenant, "ledger")));
        assertEquals(1, registry.listEvents(tenant).size());
        assertEquals("migration-event", registry.listEvents(tenant).getFirst().eventId());
        assertTrue(registry.listEvents("bank").isEmpty());
    }

    private static String secret(String tenant, String id) {
        return new JsonObject().put("secretReferenceId", id).put("tenantId", tenant)
                .put("provider", "VAULT_KV_V2").put("path", "quorus/data/payments")
                .put("key", "password").put("version", "1").put("status", "ACTIVE").encode();
    }
}
