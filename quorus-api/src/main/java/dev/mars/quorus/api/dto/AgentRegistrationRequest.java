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

package dev.mars.quorus.api.dto;

import dev.mars.quorus.agent.AgentCapabilities;
import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import java.util.Map;

/**
 * DTO for agent registration requests.
 * Contains all the information needed to register a new agent with the fleet.
 * 
 * @author Mark Andrew Ray-Smith Cityline Ltd
 * @since 2.0
 */
public class AgentRegistrationRequest {

    @JsonProperty("agentId")
    @NotBlank(message = "Agent ID is required")
    private String agentId;

    @JsonProperty("hostname")
    @NotBlank(message = "Hostname is required")
    private String hostname;

    @JsonProperty("address")
    @NotBlank(message = "Address is required")
    private String address;

    @JsonProperty("port")
    @Min(value = 1, message = "Port must be greater than 0")
    @Max(value = 65535, message = "Port must be less than 65536")
    private int port;

    @JsonProperty("version")
    @NotBlank(message = "Version is required")
    private String version;

    @JsonProperty("region")
    private String region;

    @JsonProperty("datacenter")
    private String datacenter;

    @JsonProperty("capabilities")
    @NotNull(message = "Capabilities are required")
    private AgentCapabilities capabilities;

    @JsonProperty("authToken")
    private String authToken;

    @JsonProperty("metadata")
    private Map<String, String> metadata;

    /**
     * Default constructor.
     */
    public AgentRegistrationRequest() {
    }

    /**
     * Get the agent ID.
     * 
     * @return the agent ID
     */
    public String getAgentId() {
        return agentId;
    }

    /**
     * Set the agent ID.
     * 
     * @param agentId the agent ID
     */
    public void setAgentId(String agentId) {
        this.agentId = agentId;
    }

    /**
     * Get the hostname.
     * 
     * @return the hostname
     */
    public String getHostname() {
        return hostname;
    }

    /**
     * Set the hostname.
     * 
     * @param hostname the hostname
     */
    public void setHostname(String hostname) {
        this.hostname = hostname;
    }

    /**
     * Get the address.
     * 
     * @return the address
     */
    public String getAddress() {
        return address;
    }

    /**
     * Set the address.
     * 
     * @param address the address
     */
    public void setAddress(String address) {
        this.address = address;
    }

    /**
     * Get the port.
     * 
     * @return the port
     */
    public int getPort() {
        return port;
    }

    /**
     * Set the port.
     * 
     * @param port the port
     */
    public void setPort(int port) {
        this.port = port;
    }

    /**
     * Get the version.
     * 
     * @return the version
     */
    public String getVersion() {
        return version;
    }

    /**
     * Set the version.
     * 
     * @param version the version
     */
    public void setVersion(String version) {
        this.version = version;
    }

    /**
     * Get the region.
     * 
     * @return the region
     */
    public String getRegion() {
        return region;
    }

    /**
     * Set the region.
     * 
     * @param region the region
     */
    public void setRegion(String region) {
        this.region = region;
    }

    /**
     * Get the datacenter.
     * 
     * @return the datacenter
     */
    public String getDatacenter() {
        return datacenter;
    }

    /**
     * Set the datacenter.
     * 
     * @param datacenter the datacenter
     */
    public void setDatacenter(String datacenter) {
        this.datacenter = datacenter;
    }

    /**
     * Get the capabilities.
     * 
     * @return the capabilities
     */
    public AgentCapabilities getCapabilities() {
        return capabilities;
    }

    /**
     * Set the capabilities.
     * 
     * @param capabilities the capabilities
     */
    public void setCapabilities(AgentCapabilities capabilities) {
        this.capabilities = capabilities;
    }

    /**
     * Get the auth token.
     * 
     * @return the auth token
     */
    public String getAuthToken() {
        return authToken;
    }

    /**
     * Set the auth token.
     * 
     * @param authToken the auth token
     */
    public void setAuthToken(String authToken) {
        this.authToken = authToken;
    }

    /**
     * Get the metadata.
     * 
     * @return the metadata
     */
    public Map<String, String> getMetadata() {
        return metadata;
    }

    /**
     * Set the metadata.
     * 
     * @param metadata the metadata
     */
    public void setMetadata(Map<String, String> metadata) {
        this.metadata = metadata;
    }

    @Override
    public String toString() {
        return "AgentRegistrationRequest{" +
                "agentId='" + agentId + '\'' +
                ", hostname='" + hostname + '\'' +
                ", address='" + address + '\'' +
                ", port=" + port +
                ", version='" + version + '\'' +
                ", region='" + region + '\'' +
                ", datacenter='" + datacenter + '\'' +
                '}';
    }
}
