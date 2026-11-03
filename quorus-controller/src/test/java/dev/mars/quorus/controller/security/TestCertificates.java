/*
 * Copyright 2026 Mark Andrew Ray-Smith Cityline Ltd
 * Licensed under the Apache License, Version 2.0.
 */
package dev.mars.quorus.controller.security;

import javax.security.auth.x500.X500Principal;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Generates X.509 certificates for tests that need a chosen issuer, subject and validity period, which the
 * fixed PEM resources cannot provide (register item SEC-06). The JDK has no public certificate builder and
 * the project carries no certificate library, so this encodes the DER directly and signs it with
 * {@code SHA256withRSA}. A CA certificate is a self-signed trust anchor with {@code cA=TRUE}; a leaf has
 * no extensions, which TLS client authentication accepts.
 */
public final class TestCertificates {
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final byte[] SHA256_WITH_RSA = {0x30, 0x0D, 0x06, 0x09, 0x2A, (byte) 0x86, 0x48, (byte) 0x86,
            (byte) 0xF7, 0x0D, 0x01, 0x01, 0x0B, 0x05, 0x00};
    private static final byte[] BASIC_CONSTRAINTS = {0x06, 0x03, 0x55, 0x1D, 0x13};
    private static final DateTimeFormatter UTC_TIME =
            DateTimeFormatter.ofPattern("yyMMddHHmmss'Z'").withZone(ZoneOffset.UTC);

    private TestCertificates() {
    }

    /** A certificate and its private key. */
    public record Issued(X509Certificate certificate, PrivateKey privateKey) {
        public String subject() {
            return certificate.getSubjectX500Principal().getName();
        }

        public String serial() {
            return certificate.getSerialNumber().toString(16).toUpperCase(java.util.Locale.ROOT);
        }

        public String sha256() {
            try {
                return HexFormat.of().withUpperCase()
                        .formatHex(MessageDigest.getInstance("SHA-256").digest(certificate.getEncoded()));
            } catch (Exception exception) {
                throw new IllegalStateException(exception);
            }
        }

        public Path writeCertificate(Path path) throws Exception {
            return write(path, pem("CERTIFICATE", certificate.getEncoded()));
        }

        public Path writePrivateKey(Path path) throws Exception {
            return write(path, pem("PRIVATE KEY", privateKey.getEncoded()));
        }
    }

    public static Issued certificateAuthority(String subject) throws Exception {
        KeyPair keys = keyPair();
        X500Principal name = new X500Principal(subject);
        Instant now = Instant.now();
        return sign(name, keys.getPrivate(), name, keys, now.minusSeconds(3_600), now.plusSeconds(86_400), true);
    }

    public static Issued issue(Issued issuer, String subject, Instant notBefore, Instant notAfter) throws Exception {
        return sign(issuer.certificate().getSubjectX500Principal(), issuer.privateKey(), new X500Principal(subject),
                keyPair(), notBefore, notAfter, false);
    }

    /** Writes the certificates, in order, as one PEM bundle. */
    public static Path writeBundle(Path path, X509Certificate... certificates) throws Exception {
        StringBuilder bundle = new StringBuilder();
        for (X509Certificate certificate : certificates) bundle.append(pem("CERTIFICATE", certificate.getEncoded()));
        return write(path, bundle.toString());
    }

    private static Issued sign(X500Principal issuer, PrivateKey issuerKey, X500Principal subject, KeyPair keys,
                               Instant notBefore, Instant notAfter, boolean certificateAuthority) throws Exception {
        byte[] extensions = certificateAuthority
                ? tlv(0xA3, tlv(0x30, tlv(0x30, BASIC_CONSTRAINTS, tlv(0x01, new byte[]{(byte) 0xFF}),
                        tlv(0x04, tlv(0x30, tlv(0x01, new byte[]{(byte) 0xFF}))))))
                : new byte[0];
        byte[] tbs = tlv(0x30,
                tlv(0xA0, tlv(0x02, new byte[]{2})),
                tlv(0x02, new BigInteger(127, RANDOM).add(BigInteger.ONE).toByteArray()),
                SHA256_WITH_RSA,
                issuer.getEncoded(),
                tlv(0x30, utcTime(notBefore), utcTime(notAfter)),
                subject.getEncoded(),
                keys.getPublic().getEncoded(),
                extensions);
        Signature signature = Signature.getInstance("SHA256withRSA");
        signature.initSign(issuerKey);
        signature.update(tbs);
        byte[] signed = signature.sign();
        byte[] bitString = new byte[signed.length + 1];
        System.arraycopy(signed, 0, bitString, 1, signed.length);
        byte[] der = tlv(0x30, tbs, SHA256_WITH_RSA, tlv(0x03, bitString));
        X509Certificate certificate = (X509Certificate) CertificateFactory.getInstance("X.509")
                .generateCertificate(new ByteArrayInputStream(der));
        return new Issued(certificate, keys.getPrivate());
    }

    private static KeyPair keyPair() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        return generator.generateKeyPair();
    }

    private static byte[] utcTime(Instant instant) {
        return tlv(0x17, UTC_TIME.format(instant).getBytes(StandardCharsets.US_ASCII));
    }

    private static byte[] tlv(int tag, byte[]... values) {
        ByteArrayOutputStream content = new ByteArrayOutputStream();
        for (byte[] value : values) content.writeBytes(value);
        int length = content.size();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(tag);
        if (length < 0x80) {
            out.write(length);
        } else {
            byte[] digits = BigInteger.valueOf(length).toByteArray();
            int offset = digits[0] == 0 ? 1 : 0;
            out.write(0x80 | (digits.length - offset));
            out.write(digits, offset, digits.length - offset);
        }
        out.writeBytes(content.toByteArray());
        return out.toByteArray();
    }

    private static String pem(String type, byte[] der) {
        return "-----BEGIN " + type + "-----\n"
                + Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.US_ASCII)).encodeToString(der)
                + "\n-----END " + type + "-----\n";
    }

    private static Path write(Path path, String content) throws Exception {
        Path parent = path.toAbsolutePath().getParent();
        if (parent != null) Files.createDirectories(parent);
        return Files.writeString(path, content, StandardCharsets.US_ASCII);
    }
}
