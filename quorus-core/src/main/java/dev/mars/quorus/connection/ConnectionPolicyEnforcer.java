/*
 * Copyright 2026 Mark Andrew Ray-Smith Cityline Ltd
 * Licensed under the Apache License, Version 2.0.
 */
package dev.mars.quorus.connection;

import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Shared default-deny policy engine used before controller approval and again by the agent. */
public final class ConnectionPolicyEnforcer {

    public ConnectionAuthorization authorizeController(ServiceConnection connection,
                                                         ConnectionAccessRequest request,
                                                         HostResolver resolver)
            throws ConnectionPolicyException {
        return authorize(connection, request, resolver, false);
    }

    public ConnectionAuthorization authorizeAtAgent(ServiceConnection connection,
                                                      ConnectionAccessRequest request,
                                                      HostResolver resolver)
            throws ConnectionPolicyException {
        return authorize(connection, request, resolver, true);
    }

    private ConnectionAuthorization authorize(ServiceConnection connection, ConnectionAccessRequest request,
                                                HostResolver resolver, boolean agentCheck)
            throws ConnectionPolicyException {
        if (connection.status() != ServiceConnection.Status.ACTIVE) {
            deny("Q-CONNECTION-INACTIVE", "Service connection is not active");
        }
        if (!connection.tenantId().equals(request.tenantId())) {
            deny("Q-CONNECTION-TENANT", "Service connection tenant does not match the request tenant");
        }
        String normalizedPath = normalizeRemotePath(request.remotePath());
        if (connection.allowedPaths().stream().noneMatch(root -> within(root, normalizedPath))) {
            deny("Q-CONNECTION-PATH", "Remote path is outside the approved path scope");
        }
        if (!connection.allowedDirections().contains(request.direction())) {
            deny("Q-CONNECTION-DIRECTION", "Transfer direction is not approved");
        }
        if (request.agentPool() == null || request.agentPool().isBlank()
                || !connection.allowedAgentPools().contains(request.agentPool())) {
            deny("Q-CONNECTION-AGENT-POOL", "Agent pool is not approved");
        }
        if (agentCheck && (request.networkZone() == null || request.networkZone().isBlank()
                || !connection.networkZone().equals(request.networkZone()))) {
            deny("Q-CONNECTION-NETWORK-ZONE", "Executing agent network zone is not approved");
        }
        String host = connection.endpoint().getHost().toLowerCase(Locale.ROOT);
        if (connection.egressPolicy().allowedHostnames().stream().noneMatch(rule -> hostnameMatches(rule, host))) {
            deny("Q-EGRESS-HOST", "Endpoint host is not approved");
        }
        if (!connection.egressPolicy().allowedPorts().contains(connection.effectivePort())) {
            deny("Q-EGRESS-PORT", "Endpoint port is not approved");
        }

        List<String> resolved = resolveApproved(host, resolver, connection.egressPolicy().allowedCidrs());
        if (agentCheck && connection.egressPolicy().pinResolvedAddresses()) {
            List<String> approved = sorted(request.controllerResolvedAddresses());
            if (approved.isEmpty() || !approved.equals(resolved)) {
                deny("Q-EGRESS-DNS-REBIND", "Agent DNS result differs from the controller-approved address set");
            }
        }
        return new ConnectionAuthorization(connection.serviceConnectionId(), connection.tenantId(),
                connection.resolveRemotePath(normalizedPath), resolved, connection.policyVersion(),
                policyDigest(connection, resolved), Instant.now());
    }

    public void verifySshHostKey(ServiceConnection connection, String observedFingerprint)
            throws ConnectionPolicyException {
        if (connection.protocol() != ServiceConnection.Protocol.SFTP
                || observedFingerprint == null
                || !connection.trustPolicy().sshHostKeyFingerprints().contains(observedFingerprint)) {
            deny("Q-TRUST-SSH-HOST-KEY", "SSH host key is unknown or has changed");
        }
    }

    public void verifyTlsPeer(ServiceConnection connection, boolean chainTrusted,
                              boolean hostnameVerified, String observedFingerprint)
            throws ConnectionPolicyException {
        ServiceConnection.TrustPolicy trust = connection.trustPolicy();
        if (!trust.tlsRequired() || !chainTrusted || (trust.hostnameVerification() && !hostnameVerified)) {
            deny("Q-TRUST-TLS-PEER", "TLS chain or hostname verification failed");
        }
        if (!trust.tlsPeerFingerprints().isEmpty()
                && !trust.tlsPeerFingerprints().contains(observedFingerprint)) {
            deny("Q-TRUST-TLS-PIN", "TLS peer fingerprint is not approved");
        }
    }

    static String normalizeRemotePath(String value) {
        if (value == null || value.isBlank() || !value.startsWith("/")) {
            throw new IllegalArgumentException("remotePath must be an absolute path");
        }
        if (value.indexOf('\\') >= 0 || value.chars().anyMatch(Character::isISOControl)
                || java.util.Arrays.stream(value.split("/", -1)).anyMatch(".."::equals)) {
            throw new IllegalArgumentException("remotePath must not contain traversal segments");
        }
        try {
            // remotePath is literal filename data, not a URI containing query/fragment syntax.
            return new java.net.URI(null, null, value, null).normalize().getPath();
        } catch (java.net.URISyntaxException e) {
            throw new IllegalArgumentException("remotePath is invalid", e);
        }
    }

    private static boolean within(String root, String path) {
        String normalizedRoot = root.endsWith("/") && root.length() > 1
                ? root.substring(0, root.length() - 1) : root;
        return normalizedRoot.equals("/") || path.equals(normalizedRoot) || path.startsWith(normalizedRoot + "/");
    }

    private static boolean hostnameMatches(String rule, String host) {
        if (rule.startsWith("*.")) {
            String suffix = rule.substring(1);
            return host.endsWith(suffix) && host.length() > suffix.length();
        }
        return rule.equals(host);
    }

    private static List<String> resolveApproved(String host, HostResolver resolver, Set<String> cidrs)
            throws ConnectionPolicyException {
        final List<InetAddress> addresses;
        try {
            addresses = resolver.resolve(host);
        } catch (Exception e) {
            throw new ConnectionPolicyException("Q-EGRESS-DNS", "Endpoint DNS resolution failed");
        }
        if (addresses == null || addresses.isEmpty()) {
            deny("Q-EGRESS-DNS", "Endpoint DNS returned no addresses");
        }
        List<String> approved = new ArrayList<>();
        for (InetAddress address : addresses) {
            if (cidrs.stream().noneMatch(cidr -> contains(cidr, address))) {
                deny("Q-EGRESS-ADDRESS", "A resolved endpoint address is outside the approved CIDR policy");
            }
            approved.add(address.getHostAddress());
        }
        return sorted(approved);
    }

    private static boolean contains(String cidr, InetAddress address) {
        try {
            String[] parts = cidr.split("/", -1);
            if (parts.length != 2) return false;
            byte[] network = InetAddress.getByName(parts[0]).getAddress();
            byte[] candidate = address.getAddress();
            int prefix = Integer.parseInt(parts[1]);
            if (network.length != candidate.length || prefix < 0 || prefix > network.length * 8) return false;
            int fullBytes = prefix / 8;
            int remaining = prefix % 8;
            for (int i = 0; i < fullBytes; i++) if (network[i] != candidate[i]) return false;
            if (remaining == 0) return true;
            int mask = 0xff << (8 - remaining);
            return (network[fullBytes] & mask) == (candidate[fullBytes] & mask);
        } catch (Exception ignored) {
            return false;
        }
    }

    private static String policyDigest(ServiceConnection connection, List<String> addresses) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String canonical = connection.serviceConnectionId() + '|' + connection.policyVersion() + '|'
                    + connection.endpoint() + '|' + String.join(",", addresses);
            return HexFormat.of().formatHex(digest.digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    private static List<String> sorted(List<String> values) {
        return values.stream().distinct().sorted(Comparator.naturalOrder()).toList();
    }

    private static void deny(String code, String message) throws ConnectionPolicyException {
        throw new ConnectionPolicyException(code, message);
    }

    public record ConnectionAuthorization(String serviceConnectionId, String tenantId,
                                          java.net.URI endpoint, List<String> resolvedAddresses,
                                          int policyVersion, String policyDigest,
                                          Instant authorizedAt) {
        public ConnectionAuthorization {
            resolvedAddresses = List.copyOf(resolvedAddresses);
        }
    }
}
