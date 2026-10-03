/*
 * Copyright 2026 Mark Andrew Ray-Smith Cityline Ltd
 * Licensed under the Apache License, Version 2.0.
 */
package dev.mars.quorus.agent.service;

import dev.mars.quorus.agent.config.AgentConfiguration;
import dev.mars.quorus.agent.testing.FakeController;
import dev.mars.quorus.agent.testing.FakeController.Reply;
import dev.mars.quorus.security.PemTls;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import javax.net.ssl.SSLHandshakeException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static dev.mars.quorus.testing.TestResourceUtils.copyResource;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The Phase 1 agent trust boundary, on the JDK controller client (RT-05a): a production agent talks
 * to its controller only over TLS 1.3, presents its own certificate, trusts only its trust bundle, and
 * verifies the controller's hostname.
 */
@Timeout(value = 30, unit = SECONDS, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
class ControllerClientTlsTest {
    private static final String PATH = "/agents/security-check";

    @TempDir
    Path tempDir;

    @Test
    void productionAgentPresentsItsCertificateToTrustedController() throws Exception {
        TlsMaterial tls = TlsMaterial.load(tempDir.resolve("tls"));
        try (FakeController controller = controller(tls.serverCertificate(), tls.serverPrivateKey(),
                tls.clientCertificate(), "TLSv1.3");
             ControllerClient client = ControllerClient.create(tls.productionConfig(controller.port()))) {

            assertEquals(204, client.get(PATH).status());
        }
    }

    @Test
    void productionAgentRejectsControllerOutsideItsTrustBundle() throws Exception {
        TlsMaterial tls = TlsMaterial.load(tempDir.resolve("tls"));
        // The controller presents the client certificate, which the agent's bundle does not hold.
        try (FakeController controller = controller(tls.clientCertificate(), tls.clientPrivateKey(),
                tls.clientCertificate(), "TLSv1.3");
             ControllerClient client = ControllerClient.create(tls.productionConfig(controller.port()))) {

            IOException failure = assertThrows(IOException.class, () -> client.get(PATH));

            assertCausedBy(failure, SSLHandshakeException.class);
            assertTrue(controller.requests().isEmpty(), "no request may reach an untrusted controller");
        }
    }

    @Test
    void productionAgentRejectsControllerHostnameMismatch() throws Exception {
        TlsMaterial tls = TlsMaterial.load(tempDir.resolve("tls"));
        // The controller's certificate is trusted, but it is issued to quorus-client, not localhost,
        // so hostname verification is the only check that can fail.
        Path trustsControllerCertificate = tempDir.resolve("tls/trust-with-client.pem");
        Files.writeString(trustsControllerCertificate, Files.readString(tls.serverCertificate())
                + System.lineSeparator() + Files.readString(tls.clientCertificate()));
        try (FakeController controller = controller(tls.clientCertificate(), tls.clientPrivateKey(),
                tls.clientCertificate(), "TLSv1.3");
             ControllerClient client = ControllerClient.create(tls.productionConfig(controller.port(),
                     tls.clientCertificate(), tls.clientPrivateKey(), trustsControllerCertificate))) {

            IOException failure = assertThrows(IOException.class, () -> client.get(PATH));

            assertChainMentions(failure, "no name matching localhost");
            assertTrue(controller.requests().isEmpty());
        }
    }

    @Test
    void productionAgentRefusesAControllerLimitedToTls12() throws Exception {
        TlsMaterial tls = TlsMaterial.load(tempDir.resolve("tls"));
        try (FakeController controller = controller(tls.serverCertificate(), tls.serverPrivateKey(),
                tls.clientCertificate(), "TLSv1.2");
             ControllerClient client = ControllerClient.create(tls.productionConfig(controller.port()))) {

            IOException failure = assertThrows(IOException.class, () -> client.get(PATH));

            assertCausedBy(failure, SSLHandshakeException.class);
            assertTrue(controller.requests().isEmpty());
        }
    }

    @Test
    void overlappingAgentCertificatesRemainTrustedDuringRotation() throws Exception {
        TlsMaterial tls = TlsMaterial.load(tempDir.resolve("tls"));
        Path overlapBundle = tempDir.resolve("tls/agent-overlap.pem");
        Files.writeString(overlapBundle, Files.readString(tls.clientCertificate())
                + System.lineSeparator() + Files.readString(tls.serverCertificate()));
        try (FakeController controller = controller(tls.serverCertificate(), tls.serverPrivateKey(),
                overlapBundle, "TLSv1.3");
             ControllerClient oldClient = ControllerClient.create(tls.productionConfig(controller.port(),
                     tls.clientCertificate(), tls.clientPrivateKey(), tls.serverCertificate()));
             ControllerClient rotatedClient = ControllerClient.create(tls.productionConfig(controller.port(),
                     tls.serverCertificate(), tls.serverPrivateKey(), tls.serverCertificate()))) {

            assertEquals(204, oldClient.get(PATH).status());
            assertEquals(204, rotatedClient.get(PATH).status());
        }
    }

    /** A controller that presents this identity and requires a client certificate from the bundle. */
    private static FakeController controller(Path certificate, Path privateKey, Path clientTrust,
                                             String protocol) throws Exception {
        FakeController controller = FakeController.startTls(PemTls.sslContext(certificate, privateKey, clientTrust),
                true, protocol);
        return controller.on("GET", "/api/v1" + PATH, Reply.status(204).always());
    }

    private static void assertCausedBy(Throwable failure, Class<? extends Throwable> type) {
        for (Throwable t = failure; t != null; t = t.getCause()) {
            if (type.isInstance(t)) {
                return;
            }
        }
        throw new AssertionError("expected a " + type.getSimpleName() + " in the cause chain", failure);
    }

    private static void assertChainMentions(Throwable failure, String text) {
        for (Throwable t = failure; t != null; t = t.getCause()) {
            if (t.getMessage() != null && t.getMessage().toLowerCase().contains(text)) {
                return;
            }
        }
        throw new AssertionError("expected the cause chain to mention '" + text + "'", failure);
    }

    private record TlsMaterial(Path serverCertificate, Path serverPrivateKey,
                               Path clientCertificate, Path clientPrivateKey) {
        static TlsMaterial load(Path targetDirectory) throws Exception {
            return new TlsMaterial(
                    copyResource(ControllerClientTlsTest.class, "/security/server-cert.pem", targetDirectory),
                    copyResource(ControllerClientTlsTest.class, "/security/server-key.pem", targetDirectory),
                    copyResource(ControllerClientTlsTest.class, "/security/client-cert.pem", targetDirectory),
                    copyResource(ControllerClientTlsTest.class, "/security/client-key.pem", targetDirectory));
        }

        AgentConfiguration productionConfig(int port) {
            return productionConfig(port, clientCertificate, clientPrivateKey, serverCertificate);
        }

        AgentConfiguration productionConfig(int port, Path certificate, Path privateKey, Path trustBundle) {
            return new AgentConfiguration.Builder()
                    .agentId("regulated-agent-1")
                    .tenantId("regulated-bank-a")
                    .controllerUrl("https://localhost:" + port + "/api/v1")
                    .securityProfile("production")
                    .allowInsecure(false)
                    .controllerTlsEnabled(true)
                    .tlsCertificatePath(certificate.toString())
                    .tlsPrivateKeyPath(privateKey.toString())
                    .tlsTrustBundlePath(trustBundle.toString())
                    .build();
        }
    }
}
