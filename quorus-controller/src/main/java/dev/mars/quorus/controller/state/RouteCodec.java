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

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Protobuf codec for {@link RouteCommand}, {@link RouteConfiguration},
 * {@link TriggerConfiguration}, {@link RouteStatus}, and {@link TriggerType}.
 *
 * <p>Package-private utility class used by {@link ProtobufCommandCodec}.
 *
 * @author Mark Andrew Ray-Smith Cityline Ltd
 * @since 2025
 */
final class RouteCodec {

    private RouteCodec() {
    }

    // ── Command ─────────────────────────────────────────────────

    static RouteCommandProto toProto(RouteCommand cmd) {
        RouteCommandProto.Builder builder = RouteCommandProto.newBuilder()
                .setRouteId(cmd.routeId())
                .setTimestampEpochMs(cmd.timestamp().toEpochMilli());

        switch (cmd) {
            case RouteCommand.Create c -> {
                builder.setType(RouteCommandType.ROUTE_CMD_CREATE);
                builder.setRouteConfiguration(toProto(c.routeConfiguration()));
            }
            case RouteCommand.Update u -> {
                builder.setType(RouteCommandType.ROUTE_CMD_UPDATE);
                builder.setRouteConfiguration(toProto(u.routeConfiguration()));
            }
            case RouteCommand.Delete ignored -> {
                builder.setType(RouteCommandType.ROUTE_CMD_DELETE);
            }
            case RouteCommand.Suspend s -> {
                builder.setType(RouteCommandType.ROUTE_CMD_SUSPEND);
                if (s.reason() != null) builder.setReason(s.reason());
            }
            case RouteCommand.Resume ignored -> {
                builder.setType(RouteCommandType.ROUTE_CMD_RESUME);
            }
            case RouteCommand.UpdateStatus us -> {
                builder.setType(RouteCommandType.ROUTE_CMD_UPDATE_STATUS);
                builder.setNewStatus(toProto(us.newStatus()));
                if (us.reason() != null) builder.setReason(us.reason());
                builder.setExpectedStatus(toProto(us.expectedStatus()));
            }
        }

        return builder.build();
    }

    static RouteCommand fromProto(RouteCommandProto proto) {
        Instant timestamp = proto.getTimestampEpochMs() > 0
                ? Instant.ofEpochMilli(proto.getTimestampEpochMs()) : Instant.now();
        String routeId = proto.getRouteId();
        String reason = proto.getReason().isEmpty() ? null : proto.getReason();

        return switch (proto.getType()) {
            case ROUTE_CMD_CREATE -> new RouteCommand.Create(routeId,
                    fromProto(proto.getRouteConfiguration()), timestamp);
            case ROUTE_CMD_UPDATE -> new RouteCommand.Update(routeId,
                    fromProto(proto.getRouteConfiguration()), timestamp);
            case ROUTE_CMD_DELETE -> new RouteCommand.Delete(routeId, timestamp);
            case ROUTE_CMD_SUSPEND -> new RouteCommand.Suspend(routeId, reason, timestamp);
            case ROUTE_CMD_RESUME -> new RouteCommand.Resume(routeId, timestamp);
            case ROUTE_CMD_UPDATE_STATUS -> {
                RouteStatus newStatus = proto.getNewStatus() != RouteStatusProto.ROUTE_STATUS_UNSPECIFIED
                        ? fromProto(proto.getNewStatus()) : null;
                yield new RouteCommand.UpdateStatus(routeId, fromProto(proto.getExpectedStatus()), newStatus, reason, timestamp);
            }
            default -> throw new IllegalArgumentException("Unknown RouteCommandType: " + proto.getType());
        };
    }

    // ── Domain models ───────────────────────────────────────────

    private static RouteConfigurationProto toProto(RouteConfiguration config) {
        RouteConfigurationProto.Builder builder = RouteConfigurationProto.newBuilder();
        Optional.ofNullable(config.getRouteId()).ifPresent(builder::setRouteId);
        Optional.ofNullable(config.getName()).ifPresent(builder::setName);
        Optional.ofNullable(config.getDescription()).ifPresent(builder::setDescription);
        Optional.ofNullable(config.getSourceAgentId()).ifPresent(builder::setSourceAgentId);
        Optional.ofNullable(config.getSourceLocation()).ifPresent(builder::setSourceLocation);
        Optional.ofNullable(config.getDestinationAgentId()).ifPresent(builder::setDestinationAgentId);
        Optional.ofNullable(config.getDestinationLocation()).ifPresent(builder::setDestinationLocation);
        Optional.ofNullable(config.getTrigger()).ifPresent(t -> builder.setTrigger(toProto(t)));
        Optional.ofNullable(config.getStatus()).ifPresent(s -> builder.setStatus(toProto(s)));
        Optional.ofNullable(config.getOptions()).ifPresent(builder::putAllOptions);
        Optional.ofNullable(config.getCreatedAt()).ifPresent(t -> builder.setCreatedAtEpochMs(t.toEpochMilli()));
        Optional.ofNullable(config.getUpdatedAt()).ifPresent(t -> builder.setUpdatedAtEpochMs(t.toEpochMilli()));
        return builder.build();
    }

    private static RouteConfiguration fromProto(RouteConfigurationProto proto) {
        Instant createdAt = proto.getCreatedAtEpochMs() > 0
                ? Instant.ofEpochMilli(proto.getCreatedAtEpochMs()) : null;
        Instant updatedAt = proto.getUpdatedAtEpochMs() > 0
                ? Instant.ofEpochMilli(proto.getUpdatedAtEpochMs()) : null;
        RouteStatus status = proto.getStatus() != RouteStatusProto.ROUTE_STATUS_UNSPECIFIED
                ? fromProto(proto.getStatus()) : null;
        TriggerConfiguration trigger = proto.hasTrigger() ? fromProto(proto.getTrigger()) : null;
        Map<String, String> options = proto.getOptionsCount() > 0
                ? new HashMap<>(proto.getOptionsMap()) : null;

        return new RouteConfiguration(
                proto.getRouteId().isEmpty() ? null : proto.getRouteId(),
                proto.getName().isEmpty() ? null : proto.getName(),
                proto.getDescription().isEmpty() ? null : proto.getDescription(),
                proto.getSourceAgentId().isEmpty() ? null : proto.getSourceAgentId(),
                proto.getSourceLocation().isEmpty() ? null : proto.getSourceLocation(),
                proto.getDestinationAgentId().isEmpty() ? null : proto.getDestinationAgentId(),
                proto.getDestinationLocation().isEmpty() ? null : proto.getDestinationLocation(),
                trigger,
                status,
                options,
                createdAt,
                updatedAt);
    }

    private static TriggerConfigurationProto toProto(TriggerConfiguration trigger) {
        TriggerConfigurationProto.Builder builder = TriggerConfigurationProto.newBuilder()
                .setType(toProto(trigger.getType()));
        Optional.ofNullable(trigger.getEventPatterns()).ifPresent(builder::addAllEventPatterns);
        Optional.ofNullable(trigger.getExcludePatterns()).ifPresent(builder::addAllExcludePatterns);
        builder.setDebounceMs(trigger.getDebounceMs());
        Optional.ofNullable(trigger.getCronExpression()).ifPresent(builder::setCronExpression);
        Optional.ofNullable(trigger.getTimezone()).ifPresent(builder::setTimezone);
        builder.setIntervalMinutes(trigger.getIntervalMinutes());
        builder.setFileCountThreshold(trigger.getFileCountThreshold());
        builder.setSizeThresholdMb(trigger.getSizeThresholdMb());
        builder.setMaxWaitMinutes(trigger.getMaxWaitMinutes());
        Optional.ofNullable(trigger.getCompositeOperator()).ifPresent(builder::setCompositeOperator);
        Optional.ofNullable(trigger.getChildTriggers()).ifPresent(children ->
                children.forEach(c -> builder.addChildTriggers(toProto(c))));
        return builder.build();
    }

    private static TriggerConfiguration fromProto(TriggerConfigurationProto proto) {
        TriggerType type = fromProto(proto.getType());
        List<String> eventPatterns = proto.getEventPatternsCount() > 0
                ? new ArrayList<>(proto.getEventPatternsList()) : null;
        List<String> excludePatterns = proto.getExcludePatternsCount() > 0
                ? new ArrayList<>(proto.getExcludePatternsList()) : null;
        String cronExpression = proto.getCronExpression().isEmpty() ? null : proto.getCronExpression();
        String timezone = proto.getTimezone().isEmpty() ? null : proto.getTimezone();
        String compositeOperator = proto.getCompositeOperator().isEmpty() ? null : proto.getCompositeOperator();
        List<TriggerConfiguration> childTriggers = proto.getChildTriggersCount() > 0
                ? proto.getChildTriggersList().stream()
                        .map(RouteCodec::fromProto)
                        .collect(Collectors.toList())
                : null;

        return new TriggerConfiguration(
                type,
                eventPatterns,
                excludePatterns,
                proto.getDebounceMs(),
                cronExpression,
                timezone,
                proto.getIntervalMinutes(),
                proto.getFileCountThreshold(),
                proto.getSizeThresholdMb(),
                proto.getMaxWaitMinutes(),
                compositeOperator,
                childTriggers);
    }

    // ── Enums ───────────────────────────────────────────────────

    private static RouteStatusProto toProto(RouteStatus status) {
        return switch (status) {
            case CONFIGURED -> RouteStatusProto.ROUTE_STATUS_CONFIGURED;
            case ACTIVE -> RouteStatusProto.ROUTE_STATUS_ACTIVE;
            case TRIGGERED -> RouteStatusProto.ROUTE_STATUS_TRIGGERED;
            case TRANSFERRING -> RouteStatusProto.ROUTE_STATUS_TRANSFERRING;
            case SUSPENDED -> RouteStatusProto.ROUTE_STATUS_SUSPENDED;
            case DEGRADED -> RouteStatusProto.ROUTE_STATUS_DEGRADED;
            case FAILED -> RouteStatusProto.ROUTE_STATUS_FAILED;
            case DELETED -> RouteStatusProto.ROUTE_STATUS_DELETED;
        };
    }

    private static RouteStatus fromProto(RouteStatusProto status) {
        return switch (status) {
            case ROUTE_STATUS_CONFIGURED -> RouteStatus.CONFIGURED;
            case ROUTE_STATUS_ACTIVE -> RouteStatus.ACTIVE;
            case ROUTE_STATUS_TRIGGERED -> RouteStatus.TRIGGERED;
            case ROUTE_STATUS_TRANSFERRING -> RouteStatus.TRANSFERRING;
            case ROUTE_STATUS_SUSPENDED -> RouteStatus.SUSPENDED;
            case ROUTE_STATUS_DEGRADED -> RouteStatus.DEGRADED;
            case ROUTE_STATUS_FAILED -> RouteStatus.FAILED;
            case ROUTE_STATUS_DELETED -> RouteStatus.DELETED;
            default -> throw new IllegalArgumentException("Unknown RouteStatusProto: " + status);
        };
    }

    private static TriggerTypeProto toProto(TriggerType type) {
        return switch (type) {
            case EVENT -> TriggerTypeProto.TRIGGER_TYPE_EVENT;
            case TIME -> TriggerTypeProto.TRIGGER_TYPE_TIME;
            case INTERVAL -> TriggerTypeProto.TRIGGER_TYPE_INTERVAL;
            case BATCH -> TriggerTypeProto.TRIGGER_TYPE_BATCH;
            case SIZE -> TriggerTypeProto.TRIGGER_TYPE_SIZE;
            case COMPOSITE -> TriggerTypeProto.TRIGGER_TYPE_COMPOSITE;
        };
    }

    private static TriggerType fromProto(TriggerTypeProto type) {
        return switch (type) {
            case TRIGGER_TYPE_EVENT -> TriggerType.EVENT;
            case TRIGGER_TYPE_TIME -> TriggerType.TIME;
            case TRIGGER_TYPE_INTERVAL -> TriggerType.INTERVAL;
            case TRIGGER_TYPE_BATCH -> TriggerType.BATCH;
            case TRIGGER_TYPE_SIZE -> TriggerType.SIZE;
            case TRIGGER_TYPE_COMPOSITE -> TriggerType.COMPOSITE;
            default -> throw new IllegalArgumentException("Unknown TriggerTypeProto: " + type);
        };
    }
}
