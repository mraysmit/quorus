/*
 * Copyright 2026 Mark Andrew Ray-Smith Cityline Ltd
 * Licensed under the Apache License, Version 2.0.
 */
package dev.mars.quorus.connection;

import org.junit.jupiter.api.Test;

import javax.security.auth.x500.X500Principal;
import java.math.BigInteger;
import java.security.Principal;
import java.security.PublicKey;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Date;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertNotSame;

class TlsTrustAnchorPolicyTest {

    @Test
    void trustManagersAreReusedOnlyForTheSameImmutablePolicy() {
        var first = TlsPeerPolicy.defaultTrustManager(Set.of("SHA256:ca-a"), Set.of("SHA256:peer-a"));
        var same = TlsPeerPolicy.defaultTrustManager(Set.of("SHA256:ca-a"), Set.of("SHA256:peer-a"));
        var rotated = TlsPeerPolicy.defaultTrustManager(Set.of("SHA256:ca-a"), Set.of("SHA256:peer-b"));

        assertSame(first, same);
        assertNotSame(first, rotated);
    }

    @Test
    void approvedRootMayBeTheSelectedTrustAnchorOmittedByThePeer() throws Exception {
        X509Certificate root;
        try (var input = getClass().getResourceAsStream("/security/server-cert.pem")) {
            root = (X509Certificate) CertificateFactory.getInstance("X.509").generateCertificate(input);
        }
        X509Certificate leaf = new IssuedCertificate(root, "synthetic-leaf".getBytes());
        String approvedRoot = TlsPeerPolicy.sha256Fingerprint(root.getEncoded());

        assertThrows(ConnectionPolicyException.class, () -> TlsPeerPolicy.requireApprovedCa(
                List.of(leaf.getEncoded()), Set.of(approvedRoot)));
        List<X509Certificate> augmented = TlsPeerPolicy.withSelectedTrustAnchor(
                List.of(leaf), new X509Certificate[]{root});
        assertDoesNotThrow(() -> TlsPeerPolicy.requireApprovedCa(augmented.stream().map(certificate -> {
            try { return certificate.getEncoded(); }
            catch (Exception e) { throw new IllegalStateException(e); }
        }).toList(), Set.of(approvedRoot)));
    }

    private static final class IssuedCertificate extends X509Certificate {
        private final X509Certificate delegate;
        private final byte[] encoded;
        private IssuedCertificate(X509Certificate delegate, byte[] encoded) {
            this.delegate = delegate;
            this.encoded = encoded.clone();
        }
        @Override public X500Principal getIssuerX500Principal() { return delegate.getSubjectX500Principal(); }
        @Override public byte[] getEncoded() { return encoded.clone(); }
        @Override public void verify(PublicKey key) { }
        @Override public void verify(PublicKey key, String sigProvider) { }
        @Override public void checkValidity() { }
        @Override public void checkValidity(Date date) { }
        @Override public int getVersion() { return delegate.getVersion(); }
        @Override public BigInteger getSerialNumber() { return delegate.getSerialNumber(); }
        @Override public Principal getIssuerDN() { return getIssuerX500Principal(); }
        @Override public Principal getSubjectDN() { return delegate.getSubjectDN(); }
        @Override public Date getNotBefore() { return delegate.getNotBefore(); }
        @Override public Date getNotAfter() { return delegate.getNotAfter(); }
        @Override public byte[] getTBSCertificate() { return new byte[0]; }
        @Override public byte[] getSignature() { return new byte[0]; }
        @Override public String getSigAlgName() { return delegate.getSigAlgName(); }
        @Override public String getSigAlgOID() { return delegate.getSigAlgOID(); }
        @Override public byte[] getSigAlgParams() { return delegate.getSigAlgParams(); }
        @Override public boolean[] getIssuerUniqueID() { return null; }
        @Override public boolean[] getSubjectUniqueID() { return null; }
        @Override public boolean[] getKeyUsage() { return delegate.getKeyUsage(); }
        @Override public int getBasicConstraints() { return -1; }
        @Override public String toString() { return "synthetic issued certificate"; }
        @Override public PublicKey getPublicKey() { return delegate.getPublicKey(); }
        @Override public boolean hasUnsupportedCriticalExtension() { return false; }
        @Override public Set<String> getCriticalExtensionOIDs() { return null; }
        @Override public Set<String> getNonCriticalExtensionOIDs() { return null; }
        @Override public byte[] getExtensionValue(String oid) { return null; }
    }
}
