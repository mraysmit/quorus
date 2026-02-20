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

import java.util.Optional;

/**
 * Protobuf codec for {@link SystemMetadataCommand} and its type enum.
 *
 * <p>Package-private utility class used by {@link ProtobufCommandCodec}.
 *
 * @author Mark Andrew Ray-Smith Cityline Ltd
 * @since 2025
 */
final class SystemMetadataCodec {

    private SystemMetadataCodec() {
    }

    // ── Command ─────────────────────────────────────────────────

    static SystemMetadataCommandProto toProto(SystemMetadataCommand cmd) {
        SystemMetadataCommandProto.Builder builder = SystemMetadataCommandProto.newBuilder()
                .setType(toProto(cmd.getType()));
        Optional.ofNullable(cmd.getKey()).ifPresent(builder::setKey);
        Optional.ofNullable(cmd.getValue()).ifPresent(builder::setValue);
        return builder.build();
    }

    static SystemMetadataCommand fromProto(SystemMetadataCommandProto proto) {
        return switch (fromProto(proto.getType())) {
            case SET -> SystemMetadataCommand.set(proto.getKey(), proto.getValue());
            case DELETE -> SystemMetadataCommand.delete(proto.getKey());
        };
    }

    // ── Enums ───────────────────────────────────────────────────

    private static SystemMetadataCommandType toProto(SystemMetadataCommand.Type type) {
        return switch (type) {
            case SET -> SystemMetadataCommandType.SYSTEM_METADATA_CMD_SET;
            case DELETE -> SystemMetadataCommandType.SYSTEM_METADATA_CMD_DELETE;
        };
    }

    private static SystemMetadataCommand.Type fromProto(SystemMetadataCommandType type) {
        return switch (type) {
            case SYSTEM_METADATA_CMD_SET -> SystemMetadataCommand.Type.SET;
            case SYSTEM_METADATA_CMD_DELETE -> SystemMetadataCommand.Type.DELETE;
            default -> throw new IllegalArgumentException("Unknown SystemMetadataCommandType: " + type);
        };
    }
}
