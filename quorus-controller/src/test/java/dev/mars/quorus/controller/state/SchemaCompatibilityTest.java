/*
 * Copyright 2025 Mark Andrew Ray-Smith Cityline Ltd
 * Licensed under the Apache License, Version 2.0.
 */
package dev.mars.quorus.controller.state;

import com.google.protobuf.ByteString;
import dev.mars.quorus.controller.raft.grpc.RaftCommandMessage;
import dev.mars.quorus.controller.raft.grpc.TransferJobCommandProto;
import dev.mars.quorus.controller.raft.grpc.TransferJobCommandType;
import dev.mars.quorus.controller.raft.grpc.TransferJobProto;
import dev.mars.quorus.controller.raft.grpc.TransferRequestProto;
import dev.mars.quorus.core.TransferStatus;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertFalse;

class SchemaCompatibilityTest {

    @Test
    void legacyCredentialBearingCreateReplaysRedactedAndTerminalAcrossSnapshot() {
        String legacyPassword = "legacy-secret-must-disappear";
        var request = TransferRequestProto.newBuilder().setRequestId("legacy-job")
                .setSourceUri("ftp://legacy-user:" + legacyPassword + "@files.example.test/in.dat")
                .setDestinationUri("file:///tmp/legacy.dat").setProtocol("ftp").build();
        var legacy = RaftCommandMessage.newBuilder().setTransferJobCommand(
                TransferJobCommandProto.newBuilder().setType(TransferJobCommandType.TRANSFER_JOB_CMD_CREATE)
                        .setJobId("legacy-job").setTenantId("legacy-tenant")
                        .setTransferJob(TransferJobProto.newBuilder().setRequest(request))).build().toByteString();

        var create = assertInstanceOf(TransferJobCommand.Create.class,
                ProtobufCommandCodec.deserialize(legacy));
        assertEquals(TransferStatus.FAILED, create.transferJob().getStatus());
        assertNull(create.transferJob().getRequest().getSourceUri().getUserInfo());

        QuorusStateStore state = new QuorusStateStore();
        assertInstanceOf(CommandResult.Success.class, state.apply(create));
        byte[] snapshot = state.takeSnapshot();
        assertFalse(new String(snapshot, StandardCharsets.UTF_8).contains(legacyPassword));
        QuorusStateStore restored = new QuorusStateStore();
        restored.restoreSnapshot(snapshot);
        var job = restored.findTransferJob("legacy-job").orElseThrow();
        assertEquals(TransferStatus.FAILED, job.getStatus());
        assertNull(java.net.URI.create(job.getSourceUri()).getUserInfo());
    }

    @Test
    void registryCoversEveryControlledContract() {
        assertEquals(SchemaVersionRegistry.Contract.values().length, SchemaVersionRegistry.all().size());
        SchemaVersionRegistry.all().forEach((contract, range) -> {
            assertDoesNotThrow(() -> SchemaVersionRegistry.requireReadable(contract, range.currentWritable()));
            assertThrows(IllegalArgumentException.class,
                    () -> SchemaVersionRegistry.requireReadable(contract, range.currentWritable() + 1));
        });
    }

    @Test
    void commandCodecReadsLegacyVersionZeroAndWritesCurrentVersion() throws Exception {
        ByteString legacyNoOp = RaftCommandMessage.getDefaultInstance().toByteString();
        assertEquals(null, ProtobufCommandCodec.deserialize(legacyNoOp));

        RaftCommandMessage current = RaftCommandMessage.parseFrom(ProtobufCommandCodec.serialize(null));
        assertEquals(SchemaVersionRegistry.current(SchemaVersionRegistry.Contract.RAFT_COMMAND_ENVELOPE),
                current.getSchemaVersion());
    }

    @Test
    void commandCodecRejectsFutureVersionBeforeStateApplication() {
        ByteString future = RaftCommandMessage.newBuilder().setSchemaVersion(999).build().toByteString();
        assertThrows(IllegalArgumentException.class, () -> ProtobufCommandCodec.deserialize(future));
    }
}
