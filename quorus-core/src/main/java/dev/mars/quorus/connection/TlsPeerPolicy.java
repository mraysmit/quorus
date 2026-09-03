/*
 * Copyright 2026 Mark Andrew Ray-Smith Cityline Ltd
 * Licensed under the Apache License, Version 2.0.
 */
package dev.mars.quorus.connection;

import javax.net.ssl.SSLEngine;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509ExtendedTrustManager;
import javax.net.ssl.X509TrustManager;
import java.net.Socket;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Base64;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.ArrayList;

/** Default PKIX validation augmented with optional SHA-256 leaf-certificate pins. */
public final class TlsPeerPolicy {
    private TlsPeerPolicy() { }

    public static String sha256Fingerprint(byte[] encodedCertificate) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(encodedCertificate);
            return "SHA256:" + Base64.getEncoder().withoutPadding().encodeToString(digest);
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }

    public static String[] enabledProtocols(String minimumTlsVersion) {
        return switch (minimumTlsVersion) {
            case "TLSv1.2" -> new String[]{"TLSv1.2", "TLSv1.3"};
            case "TLSv1.3" -> new String[]{"TLSv1.3"};
            default -> throw new IllegalArgumentException("Unsupported minimum TLS version");
        };
    }

    public static void requireApproved(byte[] encodedCertificate, Set<String> approvedFingerprints)
            throws ConnectionPolicyException {
        if (approvedFingerprints == null || approvedFingerprints.isEmpty()) return;
        String observed = sha256Fingerprint(encodedCertificate);
        if (!approvedFingerprints.contains(observed)) {
            throw new ConnectionPolicyException("Q-TLS-PEER-PIN", "TLS peer certificate is not approved");
        }
    }

    /**
     * Restricts a normally valid PKIX chain to explicitly approved CA certificate
     * fingerprints. CA identifiers use the same SHA256:base64 form as peer pins.
     */
    public static void requireApprovedCa(List<byte[]> encodedChain, Set<String> approvedCaIds)
            throws ConnectionPolicyException {
        if (approvedCaIds == null || approvedCaIds.isEmpty()) return;
        boolean approved = encodedChain != null && encodedChain.stream()
                .filter(java.util.Objects::nonNull)
                .map(TlsPeerPolicy::sha256Fingerprint)
                .anyMatch(approvedCaIds::contains);
        if (!approved) {
            throw new ConnectionPolicyException("Q-TLS-CA-DENIED", "TLS certificate authority is not approved");
        }
    }

    public static X509ExtendedTrustManager defaultTrustManager(Set<String> approvedFingerprints) {
        return defaultTrustManager(Set.of(), approvedFingerprints);
    }

    public static X509ExtendedTrustManager defaultTrustManager(Set<String> approvedCaIds,
                                                                Set<String> approvedFingerprints) {
        try {
            TrustManagerFactory factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            factory.init((KeyStore) null);
            for (var manager : factory.getTrustManagers()) {
                if (manager instanceof X509TrustManager x509) {
                    return new PinnedTrustManager(x509, Set.copyOf(approvedCaIds),
                            Set.copyOf(approvedFingerprints));
                }
            }
            throw new IllegalStateException("Default X.509 trust manager is unavailable");
        } catch (Exception e) {
            throw new IllegalStateException("Unable to initialize the default TLS trust policy", e);
        }
    }

    /** Returns the peer chain augmented with its cryptographically linked local trust anchor. */
    static List<X509Certificate> withSelectedTrustAnchor(List<X509Certificate> peerChain,
                                                         X509Certificate[] trustAnchors) {
        List<X509Certificate> chain = new ArrayList<>(peerChain);
        if (chain.isEmpty() || trustAnchors == null) return List.copyOf(chain);
        X509Certificate last = chain.getLast();
        for (X509Certificate anchor : trustAnchors) {
            if (!last.getIssuerX500Principal().equals(anchor.getSubjectX500Principal())) continue;
            try {
                last.verify(anchor.getPublicKey());
                if (chain.stream().noneMatch(anchor::equals)) chain.add(anchor);
                return List.copyOf(chain);
            } catch (Exception ignored) {
                // Not the trust anchor selected for this otherwise-valid chain.
            }
        }
        return List.copyOf(chain);
    }

    private static final class PinnedTrustManager extends X509ExtendedTrustManager {
        private final X509TrustManager delegate;
        private final Set<String> approvedCaIds;
        private final Set<String> pins;

        private PinnedTrustManager(X509TrustManager delegate, Set<String> approvedCaIds, Set<String> pins) {
            this.delegate = delegate;
            this.approvedCaIds = approvedCaIds;
            this.pins = pins;
        }

        @Override public X509Certificate[] getAcceptedIssuers() { return delegate.getAcceptedIssuers(); }
        @Override public void checkClientTrusted(X509Certificate[] chain, String authType)
                throws CertificateException { delegate.checkClientTrusted(chain, authType); }
        @Override public void checkServerTrusted(X509Certificate[] chain, String authType)
                throws CertificateException { delegate.checkServerTrusted(chain, authType); requirePin(chain); }
        @Override public void checkClientTrusted(X509Certificate[] chain, String authType, Socket socket)
                throws CertificateException {
            if (delegate instanceof X509ExtendedTrustManager extended) {
                extended.checkClientTrusted(chain, authType, socket);
            } else delegate.checkClientTrusted(chain, authType);
        }
        @Override public void checkServerTrusted(X509Certificate[] chain, String authType, Socket socket)
                throws CertificateException {
            if (delegate instanceof X509ExtendedTrustManager extended) {
                extended.checkServerTrusted(chain, authType, socket);
            } else delegate.checkServerTrusted(chain, authType);
            requirePin(chain);
        }
        @Override public void checkClientTrusted(X509Certificate[] chain, String authType, SSLEngine engine)
                throws CertificateException {
            if (delegate instanceof X509ExtendedTrustManager extended) {
                extended.checkClientTrusted(chain, authType, engine);
            } else delegate.checkClientTrusted(chain, authType);
        }
        @Override public void checkServerTrusted(X509Certificate[] chain, String authType, SSLEngine engine)
                throws CertificateException {
            if (delegate instanceof X509ExtendedTrustManager extended) {
                extended.checkServerTrusted(chain, authType, engine);
            } else delegate.checkServerTrusted(chain, authType);
            requirePin(chain);
        }

        private void requirePin(X509Certificate[] chain) throws CertificateException {
            if (chain == null || chain.length == 0) throw new CertificateException("TLS peer sent no certificate");
            try {
                TlsPeerPolicy.requireApproved(chain[0].getEncoded(), pins);
                List<X509Certificate> certificates = withSelectedTrustAnchor(
                        Arrays.asList(chain), delegate.getAcceptedIssuers());
                TlsPeerPolicy.requireApprovedCa(certificates.stream().map(certificate -> {
                    try {
                        return certificate.getEncoded();
                    } catch (java.security.cert.CertificateEncodingException e) {
                        throw new IllegalStateException(e);
                    }
                }).toList(), approvedCaIds);
            } catch (ConnectionPolicyException denied) {
                throw new CertificateException(denied.getMessage(), denied);
            } catch (IllegalStateException encodingFailure) {
                throw new CertificateException("Unable to evaluate TLS certificate policy", encodingFailure);
            }
        }

    }
}
