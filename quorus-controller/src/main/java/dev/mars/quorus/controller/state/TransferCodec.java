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
import dev.mars.quorus.core.*;

import java.net.URI;
import java.time.Instant;
import java.util.HashMap;
import java.util.Optional;

/**
 * Protobuf codec for transfer-related types: {@link TransferJobCommand},
 * {@link TransferJob}, {@link TransferRequest}, and {@link TransferStatus}.
 *
 * <p>Package-private utility class used by {@link ProtobufCommandCodec}.
 *
 * @author Mark Andrew Ray-Smith Cityline Ltd
 * @since 2025
 */
final class TransferCodec {

    private TransferCodec() {
    }

    // ── Command ─────────────────────────────────────────────────

    static TransferJobCommandProto toProto(TransferJobCommand cmd) {
        TransferJobCommandProto.Builder builder = TransferJobCommandProto.newBuilder()
                .setType(toProto(cmd.getType()));
        Optional.ofNullable(cmd.getJobId()).ifPresent(builder::setJobId);
        Optional.ofNullable(cmd.getTransferJob()).ifPresent(tj -> builder.setTransferJob(toProto(tj)));
        Optional.ofNullable(cmd.getStatus()).ifPresent(s -> builder.setStatus(toProto(s)));
        Optional.ofNullable(cmd.getBytesTransferred()).ifPresent(builder::setBytesTransferred);
        return builder.build();
    }

    static TransferJobCommand fromProto(TransferJobCommandProto proto) {
        return switch (fromProto(proto.getType())) {
            case CREATE -> TransferJobCommand.create(fromProto(proto.getTransferJob()));
            case UPDATE_STATUS -> TransferJobCommand.updateStatus(proto.getJobId(), fromProto(proto.getStatus()));
            case UPDATE_PROGRESS -> TransferJobCommand.updateProgress(proto.getJobId(), proto.getBytesTransferred());
            case DELETE -> TransferJobCommand.delete(proto.getJobId());
        };
    }

    // ── Domain models ───────────────────────────────────────────

    static TransferJobProto toProto(TransferJob job) {
        TransferJobProto.Builder builder = TransferJobProto.newBuilder()
                .setStatus(toProto(job.getStatus()))
                .setBytesTransferred(job.getBytesTransferred())
                .setTotalBytes(job.getTotalBytes());
        Optional.ofNullable(job.getRequest()).ifPresent(r -> builder.setRequest(toProto(r)));
        Optional.ofNullable(job.getStartTime()).ifPresent(t -> builder.setStartTimeEpochMs(t.toEpochMilli()));
        Optional.ofNullable(job.getLastUpdateTime()).ifPresent(t -> builder.setLastUpdateTimeEpochMs(t.toEpochMilli()));
        Optional.ofNullable(job.getErrorMessage()).ifPresent(builder::setErrorMessage);
        Optional.ofNullable(job.getActualChecksum()).ifPresent(builder::setActualChecksum);
        return builder.build();
    }

    static TransferJob fromProto(TransferJobProto proto) {
        TransferRequest request = fromProto(proto.getRequest());
        TransferJob job = new TransferJob(request);
        // The job starts in PENDING. For CREATE commands this is always correct.
        return job;
    }

    private static TransferRequestProto toProto(TransferRequest req) {
        TransferRequestProto.Builder builder = TransferRequestProto.newBuilder()
                .setExpectedSize(req.getExpectedSize());
        Optional.ofNullable(req.getRequestId()).ifPresent(builder::setRequestId);
        Optional.ofNullable(req.getSourceUri()).ifPresent(u -> builder.setSourceUri(u.toString()));
        Optional.ofNullable(req.getDestinationUri()).ifPresent(u -> {
            builder.setDestinationUri(u.toString());
            builder.setDestinationPath(u.toString());
        });
        Optional.ofNullable(req.getProtocol()).ifPresent(builder::setProtocol);
        Optional.ofNullable(req.getMetadata()).ifPresent(builder::putAllMetadata);
        Optional.ofNullable(req.getCreatedAt()).ifPresent(t -> builder.setCreatedAtEpochMs(t.toEpochMilli()));
        Optional.ofNullable(req.getExpectedChecksum()).ifPresent(builder::setExpectedChecksum);
        return builder.build();
    }

    private static TransferRequest fromProto(TransferRequestProto proto) {
        TransferRequest.Builder builder = TransferRequest.builder()
                .requestId(proto.getRequestId())
                .sourceUri(URI.create(proto.getSourceUri()))
                .expectedSize(proto.getExpectedSize());

        if (!proto.getDestinationUri().isEmpty()) {
            builder.destinationUri(URI.create(proto.getDestinationUri()));
        } else if (!proto.getDestinationPath().isEmpty()) {
            builder.destinationUri(URI.create(proto.getDestinationPath()));
        }

        if (!proto.getProtocol().isEmpty()) {
            builder.protocol(proto.getProtocol());
        }
        if (proto.getMetadataCount() > 0) {
            builder.metadata(new HashMap<>(proto.getMetadataMap()));
        }
        if (proto.getCreatedAtEpochMs() > 0) {
            builder.createdAt(Instant.ofEpochMilli(proto.getCreatedAtEpochMs()));
        }
        if (!proto.getExpectedChecksum().isEmpty()) {
            builder.expectedChecksum(proto.getExpectedChecksum());
        }
        return builder.build();
    }

    // ── Enums ───────────────────────────────────────────────────

    private static TransferJobCommandType toProto(TransferJobCommand.Type type) {
        return switch (type) {
            case CREATE -> TransferJobCommandType.TRANSFER_JOB_CMD_CREATE;
            case UPDATE_STATUS -> TransferJobCommandType.TRANSFER_JOB_CMD_UPDATE_STATUS;
            case UPDATE_PROGRESS -> TransferJobCommandType.TRANSFER_JOB_CMD_UPDATE_PROGRESS;
            case DELETE -> TransferJobCommandType.TRANSFER_JOB_CMD_DELETE;
        };
    }

    private static TransferJobCommand.Type fromProto(TransferJobCommandType type) {
        return switch (type) {
            case TRANSFER_JOB_CMD_CREATE -> TransferJobCommand.Type.CREATE;
            case TRANSFER_JOB_CMD_UPDATE_STATUS -> TransferJobCommand.Type.UPDATE_STATUS;
            case TRANSFER_JOB_CMD_UPDATE_PROGRESS -> TransferJobCommand.Type.UPDATE_PROGRESS;
            case TRANSFER_JOB_CMD_DELETE -> TransferJobCommand.Type.DELETE;
            default -> throw new IllegalArgumentException("Unknown TransferJobCommandType: " + type);
        };
    }

    static TransferStatusProto toProto(TransferStatus status) {
        return switch (status) {
            case PENDING -> TransferStatusProto.TRANSFER_STATUS_PENDING;
            case IN_PROGRESS -> TransferStatusProto.TRANSFER_STATUS_IN_PROGRESS;
            case COMPLETED -> TransferStatusProto.TRANSFER_STATUS_COMPLETED;
            case FAILED -> TransferStatusProto.TRANSFER_STATUS_FAILED;
            case CANCELLED -> TransferStatusProto.TRANSFER_STATUS_CANCELLED;
            case PAUSED -> TransferStatusProto.TRANSFER_STATUS_PAUSED;
        };
    }

    static TransferStatus fromProto(TransferStatusProto status) {
        return switch (status) {
            case TRANSFER_STATUS_PENDING -> TransferStatus.PENDING;
            case TRANSFER_STATUS_IN_PROGRESS -> TransferStatus.IN_PROGRESS;
            case TRANSFER_STATUS_COMPLETED -> TransferStatus.COMPLETED;
            case TRANSFER_STATUS_FAILED -> TransferStatus.FAILED;
            case TRANSFER_STATUS_CANCELLED -> TransferStatus.CANCELLED;
            case TRANSFER_STATUS_PAUSED -> TransferStatus.PAUSED;
            default -> throw new IllegalArgumentException("Unknown TransferStatusProto: " + status);
        };
    }
}
