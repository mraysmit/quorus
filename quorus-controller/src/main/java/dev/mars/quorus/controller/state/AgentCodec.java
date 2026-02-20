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

import dev.mars.quorus.agent.AgentCapabilities;
import dev.mars.quorus.agent.AgentInfo;
import dev.mars.quorus.agent.AgentNetworkInfo;
import dev.mars.quorus.agent.AgentStatus;
import dev.mars.quorus.agent.AgentSystemInfo;
import dev.mars.quorus.controller.raft.grpc.*;

import java.time.Instant;
import java.util.*;
import java.util.Optional;

/**
 * Protobuf codec for agent-related types: {@link AgentCommand},
 * {@link AgentInfo}, {@link AgentCapabilities}, {@link AgentSystemInfo},
 * {@link AgentNetworkInfo}, and {@link AgentStatus}.
 *
 * <p>Package-private utility class used by {@link ProtobufCommandCodec}.
 *
 * @author Mark Andrew Ray-Smith Cityline Ltd
 * @since 2025
 */
final class AgentCodec {

    private AgentCodec() {
    }

    // ── Command ─────────────────────────────────────────────────

    static AgentCommandProto toProto(AgentCommand cmd) {
        AgentCommandProto.Builder builder = AgentCommandProto.newBuilder()
                .setAgentId(cmd.agentId())
                .setTimestampEpochMs(cmd.timestamp().toEpochMilli());

        switch (cmd) {
            case AgentCommand.Register r -> {
                builder.setType(AgentCommandType.AGENT_CMD_REGISTER);
                builder.setAgentInfo(toProto(r.agentInfo()));
            }
            case AgentCommand.Deregister ignored -> {
                builder.setType(AgentCommandType.AGENT_CMD_DEREGISTER);
            }
            case AgentCommand.UpdateStatus u -> {
                builder.setType(AgentCommandType.AGENT_CMD_UPDATE_STATUS);
                builder.setNewStatus(toProto(u.newStatus()));
            }
            case AgentCommand.UpdateCapabilities c -> {
                builder.setType(AgentCommandType.AGENT_CMD_UPDATE_CAPABILITIES);
                builder.setNewCapabilities(toProto(c.newCapabilities()));
            }
            case AgentCommand.Heartbeat h -> {
                builder.setType(AgentCommandType.AGENT_CMD_HEARTBEAT);
                if (h.status() != null) {
                    builder.setNewStatus(toProto(h.status()));
                }
            }
        }

        return builder.build();
    }

    static AgentCommand fromProto(AgentCommandProto proto) {
        Instant timestamp = proto.getTimestampEpochMs() > 0
                ? Instant.ofEpochMilli(proto.getTimestampEpochMs()) : Instant.now();
        AgentStatus newStatus = proto.getNewStatus() != AgentStatusProto.AGENT_STATUS_UNSPECIFIED
                ? fromProto(proto.getNewStatus()) : null;
        return switch (proto.getType()) {
            case AGENT_CMD_REGISTER -> new AgentCommand.Register(
                    proto.getAgentId(), fromProto(proto.getAgentInfo()), timestamp);
            case AGENT_CMD_DEREGISTER -> new AgentCommand.Deregister(
                    proto.getAgentId(), timestamp);
            case AGENT_CMD_UPDATE_STATUS -> new AgentCommand.UpdateStatus(
                    proto.getAgentId(), newStatus, timestamp);
            case AGENT_CMD_UPDATE_CAPABILITIES -> new AgentCommand.UpdateCapabilities(
                    proto.getAgentId(), fromProto(proto.getNewCapabilities()), timestamp);
            case AGENT_CMD_HEARTBEAT -> new AgentCommand.Heartbeat(
                    proto.getAgentId(), newStatus, timestamp);
            default -> throw new IllegalArgumentException("Unknown AgentCommandType: " + proto.getType());
        };
    }

    // ── Domain models ───────────────────────────────────────────

    private static AgentInfoProto toProto(AgentInfo info) {
        AgentInfoProto.Builder builder = AgentInfoProto.newBuilder()
                .setPort(info.getPort());
        Optional.ofNullable(info.getAgentId()).ifPresent(builder::setAgentId);
        Optional.ofNullable(info.getHostname()).ifPresent(builder::setHostname);
        Optional.ofNullable(info.getAddress()).ifPresent(builder::setAddress);
        Optional.ofNullable(info.getCapabilities()).ifPresent(c -> builder.setCapabilities(toProto(c)));
        Optional.ofNullable(info.getStatus()).ifPresent(s -> builder.setStatus(toProto(s)));
        Optional.ofNullable(info.getRegistrationTime()).ifPresent(t -> builder.setRegistrationTimeEpochMs(t.toEpochMilli()));
        Optional.ofNullable(info.getLastHeartbeat()).ifPresent(t -> builder.setLastHeartbeatEpochMs(t.toEpochMilli()));
        Optional.ofNullable(info.getVersion()).ifPresent(builder::setVersion);
        Optional.ofNullable(info.getRegion()).ifPresent(builder::setRegion);
        Optional.ofNullable(info.getDatacenter()).ifPresent(builder::setDatacenter);
        Optional.ofNullable(info.getMetadata()).ifPresent(builder::putAllMetadata);
        return builder.build();
    }

    private static AgentInfo fromProto(AgentInfoProto proto) {
        AgentInfo info = new AgentInfo(
                proto.getAgentId(),
                proto.getHostname(),
                proto.getAddress(),
                proto.getPort());
        if (proto.hasCapabilities()) {
            info.setCapabilities(fromProto(proto.getCapabilities()));
        }
        if (proto.getStatus() != AgentStatusProto.AGENT_STATUS_UNSPECIFIED) {
            info.setStatus(fromProto(proto.getStatus()));
        }
        if (proto.getRegistrationTimeEpochMs() > 0) {
            info.setRegistrationTime(Instant.ofEpochMilli(proto.getRegistrationTimeEpochMs()));
        }
        if (proto.getLastHeartbeatEpochMs() > 0) {
            info.setLastHeartbeat(Instant.ofEpochMilli(proto.getLastHeartbeatEpochMs()));
        }
        if (!proto.getVersion().isEmpty()) {
            info.setVersion(proto.getVersion());
        }
        if (!proto.getRegion().isEmpty()) {
            info.setRegion(proto.getRegion());
        }
        if (!proto.getDatacenter().isEmpty()) {
            info.setDatacenter(proto.getDatacenter());
        }
        if (proto.getMetadataCount() > 0) {
            info.setMetadata(new HashMap<>(proto.getMetadataMap()));
        }
        return info;
    }

    private static AgentCapabilitiesProto toProto(AgentCapabilities caps) {
        AgentCapabilitiesProto.Builder builder = AgentCapabilitiesProto.newBuilder()
                .setMaxConcurrentTransfers(caps.getMaxConcurrentTransfers())
                .setMaxTransferSize(caps.getMaxTransferSize())
                .setMaxBandwidth(caps.getMaxBandwidth());
        Optional.ofNullable(caps.getSupportedProtocols()).ifPresent(builder::addAllSupportedProtocols);
        Optional.ofNullable(caps.getAvailableRegions()).ifPresent(builder::addAllAvailableRegions);
        Optional.ofNullable(caps.getSupportedCompressionTypes()).ifPresent(builder::addAllSupportedCompressionTypes);
        Optional.ofNullable(caps.getSupportedEncryptionTypes()).ifPresent(builder::addAllSupportedEncryptionTypes);
        Optional.ofNullable(caps.getCustomCapabilities()).ifPresent(cc ->
                cc.forEach((k, v) -> builder.putCustomCapabilities(k, v != null ? v.toString() : "")));
        Optional.ofNullable(caps.getSystemInfo()).ifPresent(si -> builder.setSystemInfo(toProto(si)));
        Optional.ofNullable(caps.getNetworkInfo()).ifPresent(ni -> builder.setNetworkInfo(toProto(ni)));
        return builder.build();
    }

    private static AgentCapabilities fromProto(AgentCapabilitiesProto proto) {
        AgentCapabilities caps = new AgentCapabilities();
        caps.setSupportedProtocols(new HashSet<>(proto.getSupportedProtocolsList()));
        caps.setMaxConcurrentTransfers(proto.getMaxConcurrentTransfers());
        caps.setMaxTransferSize(proto.getMaxTransferSize());
        caps.setMaxBandwidth(proto.getMaxBandwidth());
        caps.setAvailableRegions(new HashSet<>(proto.getAvailableRegionsList()));
        caps.setSupportedCompressionTypes(new HashSet<>(proto.getSupportedCompressionTypesList()));
        caps.setSupportedEncryptionTypes(new HashSet<>(proto.getSupportedEncryptionTypesList()));
        if (proto.getCustomCapabilitiesCount() > 0) {
            caps.setCustomCapabilities(new HashMap<>(proto.getCustomCapabilitiesMap()));
        }
        if (proto.hasSystemInfo()) {
            caps.setSystemInfo(fromProto(proto.getSystemInfo()));
        }
        if (proto.hasNetworkInfo()) {
            caps.setNetworkInfo(fromProto(proto.getNetworkInfo()));
        }
        return caps;
    }

    private static AgentSystemInfoProto toProto(AgentSystemInfo info) {
        AgentSystemInfoProto.Builder builder = AgentSystemInfoProto.newBuilder()
                .setTotalMemory(info.getTotalMemory())
                .setAvailableMemory(info.getAvailableMemory())
                .setTotalDiskSpace(info.getTotalDiskSpace())
                .setAvailableDiskSpace(info.getAvailableDiskSpace())
                .setCpuCores(info.getCpuCores())
                .setCpuUsage(info.getCpuUsage())
                .setLoadAverage(info.getLoadAverage());
        Optional.ofNullable(info.getOperatingSystem()).ifPresent(builder::setOperatingSystem);
        Optional.ofNullable(info.getArchitecture()).ifPresent(builder::setArchitecture);
        Optional.ofNullable(info.getJavaVersion()).ifPresent(builder::setJavaVersion);
        return builder.build();
    }

    private static AgentSystemInfo fromProto(AgentSystemInfoProto proto) {
        AgentSystemInfo info = new AgentSystemInfo();
        info.setOperatingSystem(proto.getOperatingSystem());
        info.setArchitecture(proto.getArchitecture());
        info.setJavaVersion(proto.getJavaVersion());
        info.setTotalMemory(proto.getTotalMemory());
        info.setAvailableMemory(proto.getAvailableMemory());
        info.setTotalDiskSpace(proto.getTotalDiskSpace());
        info.setAvailableDiskSpace(proto.getAvailableDiskSpace());
        info.setCpuCores(proto.getCpuCores());
        info.setCpuUsage(proto.getCpuUsage());
        info.setLoadAverage(proto.getLoadAverage());
        return info;
    }

    private static AgentNetworkInfoProto toProto(AgentNetworkInfo info) {
        AgentNetworkInfoProto.Builder builder = AgentNetworkInfoProto.newBuilder()
                .setBandwidthCapacity(info.getBandwidthCapacity())
                .setCurrentBandwidthUsage(info.getCurrentBandwidthUsage())
                .setLatencyMs(info.getLatencyMs())
                .setPacketLossPercentage(info.getPacketLossPercentage())
                .setIsNatTraversal(info.isNatTraversal());
        Optional.ofNullable(info.getPublicIpAddress()).ifPresent(builder::setPublicIpAddress);
        Optional.ofNullable(info.getPrivateIpAddress()).ifPresent(builder::setPrivateIpAddress);
        Optional.ofNullable(info.getNetworkInterfaces()).ifPresent(builder::addAllNetworkInterfaces);
        Optional.ofNullable(info.getConnectionType()).ifPresent(builder::setConnectionType);
        Optional.ofNullable(info.getFirewallPorts()).ifPresent(builder::addAllFirewallPorts);
        return builder.build();
    }

    private static AgentNetworkInfo fromProto(AgentNetworkInfoProto proto) {
        AgentNetworkInfo info = new AgentNetworkInfo();
        info.setPublicIpAddress(proto.getPublicIpAddress());
        info.setPrivateIpAddress(proto.getPrivateIpAddress());
        info.setNetworkInterfaces(new ArrayList<>(proto.getNetworkInterfacesList()));
        info.setBandwidthCapacity(proto.getBandwidthCapacity());
        info.setCurrentBandwidthUsage(proto.getCurrentBandwidthUsage());
        info.setLatencyMs(proto.getLatencyMs());
        info.setPacketLossPercentage(proto.getPacketLossPercentage());
        info.setConnectionType(proto.getConnectionType());
        info.setNatTraversal(proto.getIsNatTraversal());
        info.setFirewallPorts(new ArrayList<>(proto.getFirewallPortsList()));
        return info;
    }

    // ── Enums ───────────────────────────────────────────────────

    private static AgentStatusProto toProto(AgentStatus status) {
        return switch (status) {
            case REGISTERING -> AgentStatusProto.AGENT_STATUS_REGISTERING;
            case HEALTHY -> AgentStatusProto.AGENT_STATUS_HEALTHY;
            case ACTIVE -> AgentStatusProto.AGENT_STATUS_ACTIVE;
            case IDLE -> AgentStatusProto.AGENT_STATUS_IDLE;
            case DEGRADED -> AgentStatusProto.AGENT_STATUS_DEGRADED;
            case OVERLOADED -> AgentStatusProto.AGENT_STATUS_OVERLOADED;
            case MAINTENANCE -> AgentStatusProto.AGENT_STATUS_MAINTENANCE;
            case DRAINING -> AgentStatusProto.AGENT_STATUS_DRAINING;
            case UNREACHABLE -> AgentStatusProto.AGENT_STATUS_UNREACHABLE;
            case FAILED -> AgentStatusProto.AGENT_STATUS_FAILED;
            case DEREGISTERED -> AgentStatusProto.AGENT_STATUS_DEREGISTERED;
        };
    }

    private static AgentStatus fromProto(AgentStatusProto status) {
        return switch (status) {
            case AGENT_STATUS_REGISTERING -> AgentStatus.REGISTERING;
            case AGENT_STATUS_HEALTHY -> AgentStatus.HEALTHY;
            case AGENT_STATUS_ACTIVE -> AgentStatus.ACTIVE;
            case AGENT_STATUS_IDLE -> AgentStatus.IDLE;
            case AGENT_STATUS_DEGRADED -> AgentStatus.DEGRADED;
            case AGENT_STATUS_OVERLOADED -> AgentStatus.OVERLOADED;
            case AGENT_STATUS_MAINTENANCE -> AgentStatus.MAINTENANCE;
            case AGENT_STATUS_DRAINING -> AgentStatus.DRAINING;
            case AGENT_STATUS_UNREACHABLE -> AgentStatus.UNREACHABLE;
            case AGENT_STATUS_FAILED -> AgentStatus.FAILED;
            case AGENT_STATUS_DEREGISTERED -> AgentStatus.DEREGISTERED;
            default -> throw new IllegalArgumentException("Unknown AgentStatusProto: " + status);
        };
    }
}
