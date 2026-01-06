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

import java.util.List;

/**
 * Network information for a Quorus agent.
 * Contains network configuration and performance metrics.
 * 
 * @author Mark Andrew Ray-Smith Cityline Ltd
 * @since 2025-08-26
 * @version 1.0
 */
public class AgentNetworkInfo {

    @JsonProperty("publicIpAddress")
    private String publicIpAddress;

    @JsonProperty("privateIpAddress")
    private String privateIpAddress;

    @JsonProperty("networkInterfaces")
    private List<String> networkInterfaces;

    @JsonProperty("bandwidthCapacity")
    private long bandwidthCapacity; // bytes per second

    @JsonProperty("currentBandwidthUsage")
    private long currentBandwidthUsage; // bytes per second

    @JsonProperty("latencyMs")
    private double latencyMs; // milliseconds to controller

    @JsonProperty("packetLossPercentage")
    private double packetLossPercentage;

    @JsonProperty("connectionType")
    private String connectionType; // e.g., "ethernet", "wifi", "cellular"

    @JsonProperty("isNatTraversal")
    private boolean isNatTraversal;

    @JsonProperty("firewallPorts")
    private List<Integer> firewallPorts;

    /**
     * Default constructor.
     */
    public AgentNetworkInfo() {
    }

    /**
     * Get the public IP address.
     * 
     * @return the public IP address
     */
    public String getPublicIpAddress() {
        return publicIpAddress;
    }

    /**
     * Set the public IP address.
     * 
     * @param publicIpAddress the public IP address
     */
    public void setPublicIpAddress(String publicIpAddress) {
        this.publicIpAddress = publicIpAddress;
    }

    /**
     * Get the private IP address.
     * 
     * @return the private IP address
     */
    public String getPrivateIpAddress() {
        return privateIpAddress;
    }

    /**
     * Set the private IP address.
     * 
     * @param privateIpAddress the private IP address
     */
    public void setPrivateIpAddress(String privateIpAddress) {
        this.privateIpAddress = privateIpAddress;
    }

    /**
     * Get the network interfaces.
     * 
     * @return the network interfaces
     */
    public List<String> getNetworkInterfaces() {
        return networkInterfaces;
    }

    /**
     * Set the network interfaces.
     * 
     * @param networkInterfaces the network interfaces
     */
    public void setNetworkInterfaces(List<String> networkInterfaces) {
        this.networkInterfaces = networkInterfaces;
    }

    /**
     * Get the bandwidth capacity in bytes per second.
     * 
     * @return the bandwidth capacity
     */
    public long getBandwidthCapacity() {
        return bandwidthCapacity;
    }

    /**
     * Set the bandwidth capacity in bytes per second.
     * 
     * @param bandwidthCapacity the bandwidth capacity
     */
    public void setBandwidthCapacity(long bandwidthCapacity) {
        this.bandwidthCapacity = bandwidthCapacity;
    }

    /**
     * Get the current bandwidth usage in bytes per second.
     * 
     * @return the current bandwidth usage
     */
    public long getCurrentBandwidthUsage() {
        return currentBandwidthUsage;
    }

    /**
     * Set the current bandwidth usage in bytes per second.
     * 
     * @param currentBandwidthUsage the current bandwidth usage
     */
    public void setCurrentBandwidthUsage(long currentBandwidthUsage) {
        this.currentBandwidthUsage = currentBandwidthUsage;
    }

    /**
     * Get the latency to the controller in milliseconds.
     * 
     * @return the latency in milliseconds
     */
    public double getLatencyMs() {
        return latencyMs;
    }

    /**
     * Set the latency to the controller in milliseconds.
     * 
     * @param latencyMs the latency in milliseconds
     */
    public void setLatencyMs(double latencyMs) {
        this.latencyMs = latencyMs;
    }

    /**
     * Get the packet loss percentage.
     * 
     * @return the packet loss percentage
     */
    public double getPacketLossPercentage() {
        return packetLossPercentage;
    }

    /**
     * Set the packet loss percentage.
     * 
     * @param packetLossPercentage the packet loss percentage
     */
    public void setPacketLossPercentage(double packetLossPercentage) {
        this.packetLossPercentage = packetLossPercentage;
    }

    /**
     * Get the connection type.
     * 
     * @return the connection type
     */
    public String getConnectionType() {
        return connectionType;
    }

    /**
     * Set the connection type.
     * 
     * @param connectionType the connection type
     */
    public void setConnectionType(String connectionType) {
        this.connectionType = connectionType;
    }

    /**
     * Check if NAT traversal is required.
     * 
     * @return true if NAT traversal is required
     */
    public boolean isNatTraversal() {
        return isNatTraversal;
    }

    /**
     * Set whether NAT traversal is required.
     * 
     * @param natTraversal true if NAT traversal is required
     */
    public void setNatTraversal(boolean natTraversal) {
        isNatTraversal = natTraversal;
    }

    /**
     * Get the firewall ports.
     * 
     * @return the firewall ports
     */
    public List<Integer> getFirewallPorts() {
        return firewallPorts;
    }

    /**
     * Set the firewall ports.
     * 
     * @param firewallPorts the firewall ports
     */
    public void setFirewallPorts(List<Integer> firewallPorts) {
        this.firewallPorts = firewallPorts;
    }

    /**
     * Get the bandwidth utilization percentage.
     * 
     * @return the bandwidth utilization percentage
     */
    public double getBandwidthUtilizationPercentage() {
        if (bandwidthCapacity <= 0) {
            return 0.0;
        }
        return ((double) currentBandwidthUsage / bandwidthCapacity) * 100.0;
    }

    /**
     * Get the available bandwidth in bytes per second.
     * 
     * @return the available bandwidth
     */
    public long getAvailableBandwidth() {
        return Math.max(0, bandwidthCapacity - currentBandwidthUsage);
    }

    /**
     * Check if the network connection is healthy.
     * 
     * @return true if the network connection is healthy
     */
    public boolean isNetworkHealthy() {
        return latencyMs < 1000.0 && // Less than 1 second latency
               packetLossPercentage < 5.0 && // Less than 5% packet loss
               getBandwidthUtilizationPercentage() < 90.0; // Less than 90% bandwidth utilization
    }

    /**
     * Calculate a network quality score (0.0 to 1.0).
     * Higher scores indicate better network quality.
     * 
     * @return the network quality score
     */
    public double getNetworkQualityScore() {
        // Latency score (lower is better)
        double latencyScore = Math.max(0.0, Math.min(1.0, (1000.0 - latencyMs) / 1000.0));
        
        // Packet loss score (lower is better)
        double packetLossScore = Math.max(0.0, (100.0 - packetLossPercentage) / 100.0);
        
        // Bandwidth availability score (higher is better)
        double bandwidthScore = Math.max(0.0, (100.0 - getBandwidthUtilizationPercentage()) / 100.0);
        
        // Weighted average: latency 40%, packet loss 30%, bandwidth 30%
        return (latencyScore * 0.4) + (packetLossScore * 0.3) + (bandwidthScore * 0.3);
    }

    @Override
    public String toString() {
        return "AgentNetworkInfo{" +
                "publicIpAddress='" + publicIpAddress + '\'' +
                ", privateIpAddress='" + privateIpAddress + '\'' +
                ", bandwidthCapacity=" + bandwidthCapacity +
                ", currentBandwidthUsage=" + currentBandwidthUsage +
                ", latencyMs=" + latencyMs +
                ", packetLossPercentage=" + packetLossPercentage +
                ", connectionType='" + connectionType + '\'' +
                '}';
    }
}
