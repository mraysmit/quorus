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

package dev.mars.quorus.agent;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.net.InetAddress;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Represents comprehensive information about a Quorus agent in the fleet.
 * This class contains all the metadata needed to manage and communicate with an agent.
 * 
 * @author Mark Andrew Ray-Smith Cityline Ltd
 * @since 2025-08-26
 * @version 1.0
 */
public class AgentInfo {

    @JsonProperty("agentId")
    private String agentId;

    @JsonProperty("hostname")
    private String hostname;

    @JsonProperty("address")
    private String address; // String representation of InetAddress for JSON serialization

    @JsonProperty("port")
    private int port;

    @JsonProperty("capabilities")
    private AgentCapabilities capabilities;

    @JsonProperty("status")
    private AgentStatus status;

    @JsonProperty("registrationTime")
    private Instant registrationTime;

    @JsonProperty("lastHeartbeat")
    private Instant lastHeartbeat;

    @JsonProperty("version")
    private String version;

    @JsonProperty("region")
    private String region;

    @JsonProperty("datacenter")
    private String datacenter;

    @JsonProperty("metadata")
    private Map<String, String> metadata;

    /**
     * Default constructor for JSON deserialization.
     */
    public AgentInfo() {
        this.metadata = new HashMap<>();
        this.status = AgentStatus.REGISTERING;
        this.registrationTime = Instant.now();
    }

    /**
     * Constructor for creating agent info with basic details.
     * 
     * @param agentId unique identifier for the agent
     * @param hostname the hostname of the agent
     * @param address the IP address of the agent
     * @param port the port the agent is listening on
     */
    public AgentInfo(String agentId, String hostname, String address, int port) {
        this();
        this.agentId = agentId;
        this.hostname = hostname;
        this.address = address;
        this.port = port;
    }

    /**
     * Get the unique agent identifier.
     * 
     * @return the agent ID
     */
    public String getAgentId() {
        return agentId;
    }

    /**
     * Set the agent identifier.
     * 
     * @param agentId the agent ID
     */
    public void setAgentId(String agentId) {
        this.agentId = agentId;
    }

    /**
     * Get the agent hostname.
     * 
     * @return the hostname
     */
    public String getHostname() {
        return hostname;
    }

    /**
     * Set the agent hostname.
     * 
     * @param hostname the hostname
     */
    public void setHostname(String hostname) {
        this.hostname = hostname;
    }

    /**
     * Get the agent IP address as a string.
     * 
     * @return the IP address
     */
    public String getAddress() {
        return address;
    }

    /**
     * Set the agent IP address.
     * 
     * @param address the IP address
     */
    public void setAddress(String address) {
        this.address = address;
    }

    /**
     * Get the agent port.
     * 
     * @return the port number
     */
    public int getPort() {
        return port;
    }

    /**
     * Set the agent port.
     * 
     * @param port the port number
     */
    public void setPort(int port) {
        this.port = port;
    }

    /**
     * Get the agent capabilities.
     * 
     * @return the capabilities
     */
    public AgentCapabilities getCapabilities() {
        return capabilities;
    }

    /**
     * Set the agent capabilities.
     * 
     * @param capabilities the capabilities
     */
    public void setCapabilities(AgentCapabilities capabilities) {
        this.capabilities = capabilities;
    }

    /**
     * Get the current agent status.
     * 
     * @return the status
     */
    public AgentStatus getStatus() {
        return status;
    }

    /**
     * Set the agent status.
     * 
     * @param status the status
     */
    public void setStatus(AgentStatus status) {
        this.status = status;
    }

    /**
     * Get the registration timestamp.
     * 
     * @return the registration time
     */
    public Instant getRegistrationTime() {
        return registrationTime;
    }

    /**
     * Set the registration timestamp.
     * 
     * @param registrationTime the registration time
     */
    public void setRegistrationTime(Instant registrationTime) {
        this.registrationTime = registrationTime;
    }

    /**
     * Get the last heartbeat timestamp.
     * 
     * @return the last heartbeat time
     */
    public Instant getLastHeartbeat() {
        return lastHeartbeat;
    }

    /**
     * Set the last heartbeat timestamp.
     * 
     * @param lastHeartbeat the last heartbeat time
     */
    public void setLastHeartbeat(Instant lastHeartbeat) {
        this.lastHeartbeat = lastHeartbeat;
    }

    /**
     * Get the agent version.
     * 
     * @return the version string
     */
    public String getVersion() {
        return version;
    }

    /**
     * Set the agent version.
     * 
     * @param version the version string
     */
    public void setVersion(String version) {
        this.version = version;
    }

    /**
     * Get the agent region.
     * 
     * @return the region
     */
    public String getRegion() {
        return region;
    }

    /**
     * Set the agent region.
     * 
     * @param region the region
     */
    public void setRegion(String region) {
        this.region = region;
    }

    /**
     * Get the agent datacenter.
     * 
     * @return the datacenter
     */
    public String getDatacenter() {
        return datacenter;
    }

    /**
     * Set the agent datacenter.
     * 
     * @param datacenter the datacenter
     */
    public void setDatacenter(String datacenter) {
        this.datacenter = datacenter;
    }

    /**
     * Get the agent metadata.
     * 
     * @return the metadata map
     */
    public Map<String, String> getMetadata() {
        return metadata;
    }

    /**
     * Set the agent metadata.
     * 
     * @param metadata the metadata map
     */
    public void setMetadata(Map<String, String> metadata) {
        this.metadata = metadata != null ? metadata : new HashMap<>();
    }

    /**
     * Add a metadata entry.
     * 
     * @param key the metadata key
     * @param value the metadata value
     */
    public void addMetadata(String key, String value) {
        if (this.metadata == null) {
            this.metadata = new HashMap<>();
        }
        this.metadata.put(key, value);
    }

    /**
     * Get the agent endpoint URL.
     * 
     * @return the endpoint URL
     */
    public String getEndpoint() {
        return "http://" + address + ":" + port;
    }

    /**
     * Check if the agent is currently healthy.
     * 
     * @return true if the agent is healthy
     */
    public boolean isHealthy() {
        return status == AgentStatus.HEALTHY || status == AgentStatus.ACTIVE;
    }

    /**
     * Check if the agent is available for new work.
     * 
     * @return true if the agent is available
     */
    public boolean isAvailable() {
        return status == AgentStatus.HEALTHY || 
               status == AgentStatus.ACTIVE || 
               status == AgentStatus.IDLE;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AgentInfo agentInfo = (AgentInfo) o;
        return Objects.equals(agentId, agentInfo.agentId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(agentId);
    }

    @Override
    public String toString() {
        return "AgentInfo{" +
                "agentId='" + agentId + '\'' +
                ", hostname='" + hostname + '\'' +
                ", address='" + address + '\'' +
                ", port=" + port +
                ", status=" + status +
                ", version='" + version + '\'' +
                ", region='" + region + '\'' +
                ", datacenter='" + datacenter + '\'' +
                '}';
    }
}
