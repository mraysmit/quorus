/*
 * Copyright 2026 Mark Andrew Ray-Smith Cityline Ltd
 * Licensed under the Apache License, Version 2.0.
 */
package dev.mars.quorus.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.Base64;
import java.util.List;

import static dev.mars.quorus.testing.TestResourceUtils.copyResource;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Plan item RT-05a: PEM certificates and PKCS#8 keys load with the JDK alone, replacing Vert.x's PEM
 * options for the agent's controller client (and later the controller, RT-06).
 */
@DisplayName("PemTls")
class PemTlsTest {

    @TempDir
    Path directory;

    @Test
    @DisplayName("Loads every certificate of a bundle, in order")
    void loadsEveryCertificateOfABundle() throws Exception {
        Path server = copyResource(getClass(), "/security/server-cert.pem", directory);
        Path client = copyResource(getClass(), "/security/client-cert.pem", directory);
        Path bundle = directory.resolve("bundle.pem");
        Files.writeString(bundle, Files.readString(server) + System.lineSeparator() + Files.readString(client));

        List<X509Certificate> certificates = PemTls.certificates(bundle);

        assertEquals(2, certificates.size());
        assertEquals("CN=localhost", certificates.get(0).getSubjectX500Principal().getName());
        assertEquals("CN=quorus-client", certificates.get(1).getSubjectX500Principal().getName());
    }

    @Test
    @DisplayName("Loads a PKCS#8 RSA key that matches its certificate")
    void loadsAPkcs8RsaKey() throws Exception {
        Path key = copyResource(getClass(), "/security/server-key.pem", directory);
        Path certificate = copyResource(getClass(), "/security/server-cert.pem", directory);

        PrivateKey privateKey = PemTls.privateKey(key);

        assertEquals("RSA", privateKey.getAlgorithm());
        assertEquals(((java.security.interfaces.RSAPublicKey) PemTls.certificates(certificate).getFirst().getPublicKey())
                .getModulus(), ((java.security.interfaces.RSAPrivateKey) privateKey).getModulus());
    }

    @Test
    @DisplayName("Loads a PKCS#8 EC key")
    void loadsAPkcs8EcKey() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
        generator.initialize(256);
        KeyPair pair = generator.generateKeyPair();
        Path key = writePem("ec-key.pem", "PRIVATE KEY", pair.getPrivate().getEncoded());

        assertEquals(pair.getPrivate(), PemTls.privateKey(key));
    }

    @Test
    @DisplayName("Rejects a PKCS#1 RSA key with the conversion command")
    void rejectsAPkcs1Key() throws Exception {
        Path key = writePem("rsa-key.pem", "RSA PRIVATE KEY", new byte[]{1, 2, 3});

        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class, () -> PemTls.privateKey(key));

        assertTrue(failure.getMessage().contains("openssl pkcs8 -topk8 -nocrypt"), failure.getMessage());
    }

    @Test
    @DisplayName("Rejects an encrypted key and a file with no key")
    void rejectsEncryptedAndMissingKeys() throws Exception {
        Path encrypted = writePem("encrypted.pem", "ENCRYPTED PRIVATE KEY", new byte[]{1, 2, 3});
        Path certificateOnly = copyResource(getClass(), "/security/server-cert.pem", directory);

        assertTrue(assertThrows(IllegalArgumentException.class, () -> PemTls.privateKey(encrypted))
                .getMessage().contains("encrypted"));
        assertThrows(IllegalArgumentException.class, () -> PemTls.privateKey(certificateOnly));
    }

    @Test
    @DisplayName("Rejects a certificate file with no certificate")
    void rejectsAnEmptyCertificateFile() throws Exception {
        Path empty = Files.writeString(directory.resolve("empty.pem"), "not a certificate");

        assertThrows(IllegalArgumentException.class, () -> PemTls.certificates(empty));
    }

    @Test
    @DisplayName("Builds a TLS context from a key, its certificate and a trust bundle")
    void buildsATlsContext() throws Exception {
        Path certificate = copyResource(getClass(), "/security/client-cert.pem", directory);
        Path key = copyResource(getClass(), "/security/client-key.pem", directory);
        Path trust = copyResource(getClass(), "/security/server-cert.pem", directory);

        var context = PemTls.sslContext(certificate, key, trust);

        assertEquals("TLS", context.getProtocol());
        assertNotNull(context.createSSLEngine());
    }

    private Path writePem(String name, String type, byte[] der) throws Exception {
        String body = Base64.getMimeEncoder(64, "\n".getBytes()).encodeToString(der);
        return Files.writeString(directory.resolve(name),
                "-----BEGIN " + type + "-----\n" + body + "\n-----END " + type + "-----\n");
    }
}
