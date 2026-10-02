/*
 * Copyright 2026 Mark Andrew Ray-Smith Cityline Ltd
 * Licensed under the Apache License, Version 2.0.
 */
package dev.mars.quorus.security;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Loads PEM certificates and private keys with the JDK alone (RT-05a), and builds TLS contexts from
 * them. Keys must be unencrypted PKCS#8 ({@code BEGIN PRIVATE KEY}), which is what OpenSSL 3 writes
 * by default; RSA, EC and EdDSA keys are accepted. A PKCS#1 key ({@code BEGIN RSA PRIVATE KEY}) is
 * rejected with the command that converts it.
 */
public final class PemTls {

    private static final Pattern KEY_BLOCK = Pattern.compile(
            "-----BEGIN ([A-Z ]*PRIVATE KEY)-----(.*?)-----END \\1-----", Pattern.DOTALL);
    private static final List<String> KEY_ALGORITHMS = List.of("RSA", "EC", "Ed25519", "Ed448", "RSASSA-PSS");
    private static final char[] NO_PASSWORD = new char[0];

    private PemTls() { }

    /**
     * Every certificate in a PEM file, in file order.
     *
     * @throws IllegalArgumentException if the file holds no certificate
     */
    public static List<X509Certificate> certificates(Path pem) throws IOException, GeneralSecurityException {
        byte[] content = Files.readAllBytes(pem);
        if (!new String(content, StandardCharsets.US_ASCII).contains("-----BEGIN CERTIFICATE-----")) {
            throw new IllegalArgumentException("No certificate in " + pem);
        }
        List<X509Certificate> certificates = CertificateFactory.getInstance("X.509")
                .generateCertificates(new ByteArrayInputStream(content)).stream()
                .map(X509Certificate.class::cast)
                .toList();
        if (certificates.isEmpty()) {
            throw new IllegalArgumentException("No certificate in " + pem);
        }
        return certificates;
    }

    /**
     * The private key in a PEM file.
     *
     * @throws IllegalArgumentException if the file holds no unencrypted PKCS#8 key
     */
    public static PrivateKey privateKey(Path pem) throws IOException {
        Matcher block = KEY_BLOCK.matcher(Files.readString(pem, StandardCharsets.US_ASCII));
        if (!block.find()) {
            throw new IllegalArgumentException("No private key in " + pem);
        }
        String type = block.group(1);
        switch (type) {
            case "PRIVATE KEY" -> { }
            case "ENCRYPTED PRIVATE KEY" -> throw new IllegalArgumentException(
                    "The private key in " + pem + " is encrypted; Quorus needs an unencrypted PKCS#8 key");
            default -> throw new IllegalArgumentException("The private key in " + pem + " is " + type
                    + ", not PKCS#8. Convert it with: openssl pkcs8 -topk8 -nocrypt -in <key> -out <pkcs8-key>");
        }
        byte[] der = Base64.getMimeDecoder().decode(block.group(2));
        try {
            PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(der);
            for (String algorithm : KEY_ALGORITHMS) {
                try {
                    return KeyFactory.getInstance(algorithm).generatePrivate(spec);
                } catch (InvalidKeySpecException | java.security.NoSuchAlgorithmException notThisAlgorithm) {
                    // Try the next algorithm.
                }
            }
            throw new IllegalArgumentException("Unsupported private key algorithm in " + pem);
        } finally {
            Arrays.fill(der, (byte) 0);
        }
    }

    /**
     * A TLS context that presents {@code certificateChain} with {@code privateKey} and trusts only the
     * certificates in {@code trustBundle}. The caller restricts protocol versions and client
     * authentication on the engine or socket parameters.
     */
    public static SSLContext sslContext(Path certificateChain, Path privateKey, Path trustBundle)
            throws IOException, GeneralSecurityException {
        KeyStore keys = KeyStore.getInstance("PKCS12");
        keys.load(null, null);
        keys.setKeyEntry("identity", privateKey(privateKey), NO_PASSWORD,
                certificates(certificateChain).toArray(Certificate[]::new));
        KeyManagerFactory keyManagers = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        keyManagers.init(keys, NO_PASSWORD);

        SSLContext context = SSLContext.getInstance("TLS");
        context.init(keyManagers.getKeyManagers(), trustManagers(trustBundle).getTrustManagers(), null);
        return context;
    }

    /** Trust managers that accept only certificates issued by, or equal to, one in the bundle. */
    public static TrustManagerFactory trustManagers(Path trustBundle) throws IOException, GeneralSecurityException {
        KeyStore anchors = KeyStore.getInstance("PKCS12");
        anchors.load(null, null);
        List<X509Certificate> certificates = certificates(trustBundle);
        for (int i = 0; i < certificates.size(); i++) {
            anchors.setCertificateEntry("trusted-" + i, certificates.get(i));
        }
        TrustManagerFactory factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        factory.init(anchors);
        return factory;
    }
}
