/*
 * Copyright 2026 Mark Andrew Ray-Smith Cityline Ltd
 * Licensed under the Apache License, Version 2.0.
 */
package dev.mars.quorus.connection;

import dev.mars.quorus.security.CredentialBearingUriDetector;

import java.io.Serializable;
import java.net.URI;
import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/** Redacted, tenant-scoped authority for connecting to an external transfer service. */
public record ServiceConnection(
        String serviceConnectionId,
        String tenantId,
        Protocol protocol,
        URI endpoint,
        String networkZone,
        Set<String> allowedPaths,
        Set<Direction> allowedDirections,
        Set<String> allowedAgentPools,
        String owner,
        String environment,
        String classification,
        String secretReferenceId,
        String serviceIdentity,
        AuthenticationType authenticationType,
        TrustPolicy trustPolicy,
        EgressPolicy egressPolicy,
        int policyVersion,
        Status status,
        Instant createdAt,
        Instant updatedAt) implements Serializable {

    public ServiceConnection {
        serviceConnectionId = required(serviceConnectionId, "serviceConnectionId");
        tenantId = required(tenantId, "tenantId");
        protocol = Objects.requireNonNull(protocol, "protocol");
        endpoint = Objects.requireNonNull(endpoint, "endpoint");
        CredentialBearingUriDetector.requireCredentialFree(endpoint, "Service endpoint");
        if (!endpoint.isAbsolute() || endpoint.getHost() == null || endpoint.getHost().isBlank()) {
            throw new IllegalArgumentException("Service endpoint must be an absolute network URI with a host");
        }
        if (!protocol.scheme().equalsIgnoreCase(endpoint.getScheme())) {
            throw new IllegalArgumentException("Service endpoint scheme must match protocol " + protocol);
        }
        if (endpoint.getQuery() != null || endpoint.getFragment() != null) {
            throw new IllegalArgumentException("Service endpoint must not contain query or fragment data");
        }
        networkZone = required(networkZone, "networkZone");
        allowedPaths = Set.copyOf(Objects.requireNonNull(allowedPaths, "allowedPaths"));
        allowedDirections = Set.copyOf(Objects.requireNonNull(allowedDirections, "allowedDirections"));
        allowedAgentPools = Set.copyOf(Objects.requireNonNull(allowedAgentPools, "allowedAgentPools"));
        if (allowedPaths.isEmpty() || allowedDirections.isEmpty() || allowedAgentPools.isEmpty()) {
            throw new IllegalArgumentException("Path, direction, and agent-pool policies must be explicit");
        }
        allowedPaths.forEach(path -> {
            if (path == null || !path.startsWith("/") || path.contains("..")) {
                throw new IllegalArgumentException("Allowed paths must be absolute and traversal-free");
            }
        });
        owner = required(owner, "owner");
        environment = required(environment, "environment").toUpperCase(Locale.ROOT);
        classification = required(classification, "classification").toUpperCase(Locale.ROOT);
        secretReferenceId = required(secretReferenceId, "secretReferenceId");
        serviceIdentity = required(serviceIdentity, "serviceIdentity");
        authenticationType = Objects.requireNonNull(authenticationType, "authenticationType");
        trustPolicy = Objects.requireNonNull(trustPolicy, "trustPolicy");
        egressPolicy = Objects.requireNonNull(egressPolicy, "egressPolicy");
        if (policyVersion < 1) throw new IllegalArgumentException("policyVersion must be positive");
        status = Objects.requireNonNull(status, "status");
        createdAt = Objects.requireNonNull(createdAt, "createdAt");
        updatedAt = Objects.requireNonNull(updatedAt, "updatedAt");
        requireProtocolAuthentication(protocol, authenticationType);
        requireProtocolTrust(protocol, trustPolicy, egressPolicy);
    }

    public URI resolveRemotePath(String remotePath) {
        String normalized = ConnectionPolicyEnforcer.normalizeRemotePath(remotePath);
        String base = endpoint.toString();
        while (base.endsWith("/")) base = base.substring(0, base.length() - 1);
        try {
            return URI.create(base + new URI(null, null, normalized, null).toASCIIString());
        } catch (java.net.URISyntaxException e) {
            throw new IllegalArgumentException("remotePath is invalid", e);
        }
    }

    public int effectivePort() {
        return effectivePort(protocol, endpoint);
    }

    public static int effectivePort(Protocol protocol, URI endpoint) {
        return endpoint.getPort() > 0 ? endpoint.getPort() : protocol.defaultPort();
    }

    private static void requireProtocolTrust(Protocol protocol, TrustPolicy trust, EgressPolicy egress) {
        if (protocol == Protocol.SFTP && trust.sshHostKeyFingerprints().isEmpty()) {
            throw new IllegalArgumentException("SFTP requires at least one approved SSH host-key fingerprint");
        }
        if ((protocol == Protocol.HTTPS || protocol == Protocol.FTPS)
                && (!trust.tlsRequired() || !trust.hostnameVerification()
                || (trust.approvedCaIds().isEmpty() && trust.tlsPeerFingerprints().isEmpty()))) {
            throw new IllegalArgumentException("TLS protocols require TLS, hostname verification, and CA or peer pins");
        }
        if (protocol == Protocol.HTTPS && egress.allowRedirects()) {
            throw new IllegalArgumentException("HTTPS service connections must not permit redirects");
        }
        if ((protocol == Protocol.SMB || protocol == Protocol.NFS) && !trust.transportEncryptionRequired()) {
            throw new IllegalArgumentException(protocol + " service connections require transport encryption");
        }
    }

    private static void requireProtocolAuthentication(Protocol protocol, AuthenticationType authenticationType) {
        boolean supported = switch (protocol) {
            case SFTP -> authenticationType == AuthenticationType.PASSWORD
                    || authenticationType == AuthenticationType.SSH_PRIVATE_KEY;
            case HTTPS -> authenticationType == AuthenticationType.BASIC
                    || authenticationType == AuthenticationType.BEARER;
            case FTPS -> authenticationType == AuthenticationType.PASSWORD;
            case SMB, NFS -> authenticationType == AuthenticationType.KERBEROS;
        };
        if (!supported) {
            throw new IllegalArgumentException(authenticationType + " authentication is not supported for " + protocol);
        }
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " is required");
        return value;
    }

    public enum Protocol {
        SFTP("sftp", 22), HTTPS("https", 443), FTPS("ftps", 21), SMB("smb", 445), NFS("nfs", 2049);
        private final String scheme;
        private final int defaultPort;
        Protocol(String scheme, int defaultPort) { this.scheme = scheme; this.defaultPort = defaultPort; }
        public String scheme() { return scheme; }
        public int defaultPort() { return defaultPort; }
        public static Protocol fromScheme(String scheme) {
            for (Protocol protocol : values()) {
                if (protocol.scheme.equalsIgnoreCase(scheme)) return protocol;
            }
            throw new IllegalArgumentException("Unsupported service protocol scheme: " + scheme);
        }
    }

    public enum Direction { DOWNLOAD, UPLOAD }
    public enum AuthenticationType { PASSWORD, BASIC, BEARER, SSH_PRIVATE_KEY, KERBEROS }
    public enum Status { ACTIVE, SUSPENDED, REVOKED }

    /** Peer-identity requirements. Secret material is deliberately impossible to represent here. */
    public record TrustPolicy(
            boolean tlsRequired,
            boolean hostnameVerification,
            Set<String> approvedCaIds,
            Set<String> sshHostKeyFingerprints,
            String minimumTlsVersion,
            Set<String> tlsPeerFingerprints,
            boolean transportEncryptionRequired) implements Serializable {

        public TrustPolicy(boolean tlsRequired, boolean hostnameVerification, Set<String> approvedCaIds,
                           Set<String> sshHostKeyFingerprints, String minimumTlsVersion) {
            this(tlsRequired, hostnameVerification, approvedCaIds, sshHostKeyFingerprints,
                    minimumTlsVersion, Set.of(), tlsRequired);
        }

        public TrustPolicy {
            approvedCaIds = Set.copyOf(approvedCaIds == null ? Set.of() : approvedCaIds);
            sshHostKeyFingerprints = Set.copyOf(
                    sshHostKeyFingerprints == null ? Set.of() : sshHostKeyFingerprints);
            tlsPeerFingerprints = Set.copyOf(tlsPeerFingerprints == null ? Set.of() : tlsPeerFingerprints);
            minimumTlsVersion = minimumTlsVersion == null ? "TLSv1.3" : minimumTlsVersion;
            if (!minimumTlsVersion.equals("TLSv1.2") && !minimumTlsVersion.equals("TLSv1.3")) {
                throw new IllegalArgumentException("minimumTlsVersion must be TLSv1.2 or TLSv1.3");
            }
        }
    }

    /** Default-deny network constraints evaluated against both names and resolved addresses. */
    public record EgressPolicy(
            Set<String> allowedHostnames,
            Set<String> allowedCidrs,
            Set<Integer> allowedPorts,
            boolean allowRedirects,
            boolean pinResolvedAddresses) implements Serializable {
        public EgressPolicy {
            allowedHostnames = lowerCopy(allowedHostnames);
            allowedCidrs = Set.copyOf(allowedCidrs == null ? Set.of() : allowedCidrs);
            allowedPorts = Set.copyOf(allowedPorts == null ? Set.of() : allowedPorts);
            if (allowedHostnames.isEmpty() || allowedCidrs.isEmpty() || allowedPorts.isEmpty()) {
                throw new IllegalArgumentException("Egress host, CIDR, and port allowlists must be explicit");
            }
            if (!pinResolvedAddresses) {
                throw new IllegalArgumentException("Resolved-address pinning is mandatory");
            }
        }

        private static Set<String> lowerCopy(Set<String> values) {
            if (values == null) return Set.of();
            return values.stream().map(value -> value.toLowerCase(Locale.ROOT))
                    .collect(java.util.stream.Collectors.toUnmodifiableSet());
        }
    }
}
