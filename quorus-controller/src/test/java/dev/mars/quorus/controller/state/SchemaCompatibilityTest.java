/*
 * Copyright 2025 Mark Andrew Ray-Smith Cityline Ltd
 * Licensed under the Apache License, Version 2.0.
 */
package dev.mars.quorus.controller.state;

import com.google.protobuf.ByteString;
import dev.mars.quorus.controller.raft.grpc.RaftCommandMessage;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SchemaCompatibilityTest {

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
