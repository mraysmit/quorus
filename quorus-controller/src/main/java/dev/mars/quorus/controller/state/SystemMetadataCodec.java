/*
 * Copyright 2025 Mark Andrew Ray-Smith Cityline Ltd
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package dev.mars.quorus.controller.state;

import dev.mars.quorus.controller.raft.grpc.*;

/**
 * Protobuf codec for {@link SystemMetadataCommand} sealed subtypes.
 *
 * <p>Package-private utility class used by {@link ProtobufCommandCodec}.
 * Pattern-matches on sealed record variants — the compiler enforces
 * exhaustiveness, so adding a new variant produces a compile error here.
 *
 * @author Mark Andrew Ray-Smith Cityline Ltd
 * @since 2025
 */
final class SystemMetadataCodec {

    private SystemMetadataCodec() {
    }

    // ── Command ─────────────────────────────────────────────────

    static SystemMetadataCommandProto toProto(SystemMetadataCommand cmd) {
        var builder = SystemMetadataCommandProto.newBuilder()
                .setKey(cmd.key());
        return switch (cmd) {
            case SystemMetadataCommand.Set s -> builder
                    .setType(SystemMetadataCommandType.SYSTEM_METADATA_CMD_SET)
                    .setValue(s.value())
                    .build();
            case SystemMetadataCommand.Delete ignored -> builder
                    .setType(SystemMetadataCommandType.SYSTEM_METADATA_CMD_DELETE)
                    .build();
        };
    }

    static SystemMetadataCommand fromProto(SystemMetadataCommandProto proto) {
        return switch (proto.getType()) {
            case SYSTEM_METADATA_CMD_SET -> SystemMetadataCommand.set(proto.getKey(), proto.getValue());
            case SYSTEM_METADATA_CMD_DELETE -> SystemMetadataCommand.delete(proto.getKey());
            default -> throw new IllegalArgumentException("Unknown SystemMetadataCommandType: " + proto.getType());
        };
    }
}
