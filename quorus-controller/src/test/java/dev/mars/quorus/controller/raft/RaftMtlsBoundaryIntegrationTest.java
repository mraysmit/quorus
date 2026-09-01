/*
 * Copyright 2026 Mark Andrew Ray-Smith Cityline Ltd
 * Licensed under the Apache License, Version 2.0.
 */
package dev.mars.quorus.controller.raft;

import dev.mars.quorus.controller.raft.grpc.VoteRequest;
import dev.mars.quorus.controller.raft.grpc.VoteResponse;
import dev.mars.quorus.controller.security.SecurityProfile;
import dev.mars.quorus.controller.security.CertificateTrustState;
import dev.mars.quorus.controller.state.QuorusStateStore;
import io.vertx.core.Vertx;
import io.vertx.core.net.NetServer;
import io.vertx.junit5.VertxExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static dev.mars.quorus.testing.TestFutureUtils.awaitFailure;
import static dev.mars.quorus.testing.TestFutureUtils.awaitSuccess;
import static dev.mars.quorus.testing.TestResourceUtils.copyResource;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/** Retrospective external-path characterization of the Phase 1 Raft trust boundary. */
@ExtendWith(VertxExtension.class)
class RaftMtlsBoundaryIntegrationTest {
    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    @TempDir
    Path tempDir;

    @Test
    void raftAcceptsTrustedPeerAndRejectsCertificateOutsidePeerTrust(Vertx vertx) throws Exception {
        TlsMaterial tls = TlsMaterial.load(tempDir.resolve("tls"));
        int port = reservePort(vertx);
        String nodeId = "raft-mtls-server";
        RaftNode node = RaftNode.builder()
                .vertx(vertx)
                .nodeId(nodeId)
                .clusterNodes(Set.of(nodeId))
                .transport(new InMemoryTransportSimulator(nodeId))
                .stateMachine(new QuorusStateStore())
                .mode(RaftNodeMode.volatileMode())
                .electionTimeout(5_000)
                .heartbeatInterval(1_000)
                .build();
        CertificateTrustState trustState = new CertificateTrustState("raft-v1", Set.of(), Duration.ofDays(30));
        GrpcRaftServer server = new GrpcRaftServer(vertx, port, node, tls.serverConfig(), trustState);
        GrpcRaftTransport trusted = new GrpcRaftTransport(vertx, "trusted-client",
                Map.of(nodeId, "localhost:" + port), 2, 16, tls.clientConfig());
        GrpcRaftTransport untrusted = new GrpcRaftTransport(vertx, "untrusted-client",
                Map.of(nodeId, "localhost:" + port), 2, 16, tls.serverConfig());
        try {
            awaitSuccess(node.start(), TIMEOUT);
            awaitSuccess(server.start(), TIMEOUT);

            VoteRequest request = VoteRequest.newBuilder()
                    .setTerm(1)
                    .setCandidateId("trusted-client")
                    .setLastLogIndex(0)
                    .setLastLogTerm(0)
                    .build();
            VoteResponse response = awaitSuccess(trusted.sendVoteRequest(nodeId, request), TIMEOUT);
            assertNotNull(response);

            trustState.update("raft-v2", Set.of(tls.clientSerial()));
            Throwable revokedOnExistingChannel = awaitFailure(trusted.sendVoteRequest(nodeId, request), TIMEOUT);
            assertNotNull(revokedOnExistingChannel);

            Throwable rejected = awaitFailure(untrusted.sendVoteRequest(nodeId, request), TIMEOUT);
            assertNotNull(rejected);
        } finally {
            awaitSuccess(trusted.stop(), TIMEOUT);
            awaitSuccess(untrusted.stop(), TIMEOUT);
            awaitSuccess(server.stop(), TIMEOUT);
            awaitSuccess(node.stop(), TIMEOUT);
        }
    }

    @Test
    void raftOverlapKeepsRotatedPeerAvailableWhenOldPeerIsRevoked(Vertx vertx) throws Exception {
        TlsMaterial tls = TlsMaterial.load(tempDir.resolve("tls"));
        Path overlapBundle = tempDir.resolve("tls/raft-overlap.pem");
        Files.writeString(overlapBundle, Files.readString(tls.clientCertificate())
                + System.lineSeparator() + Files.readString(tls.serverCertificate()),
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        int port = reservePort(vertx);
        String nodeId = "raft-rotation-server";
        RaftNode node = RaftNode.builder()
                .vertx(vertx).nodeId(nodeId).clusterNodes(Set.of(nodeId))
                .transport(new InMemoryTransportSimulator(nodeId)).stateMachine(new QuorusStateStore())
                .mode(RaftNodeMode.volatileMode()).electionTimeout(5_000).heartbeatInterval(1_000).build();
        CertificateTrustState trustState = new CertificateTrustState("raft-overlap-v1", Set.of(),
                Duration.ofDays(30));
        GrpcRaftServer server = new GrpcRaftServer(vertx, port, node, tls.serverConfig(overlapBundle), trustState);
        GrpcRaftTransport oldPeer = new GrpcRaftTransport(vertx, "old-peer",
                Map.of(nodeId, "localhost:" + port), 2, 16, tls.clientConfig());
        GrpcRaftTransport rotatedPeer = new GrpcRaftTransport(vertx, "rotated-peer",
                Map.of(nodeId, "localhost:" + port), 2, 16, tls.rotatedClientConfig());
        try {
            awaitSuccess(node.start(), TIMEOUT);
            awaitSuccess(server.start(), TIMEOUT);
            VoteRequest oldVote = VoteRequest.newBuilder().setTerm(1).setCandidateId("old-peer").build();
            VoteRequest rotatedVote = VoteRequest.newBuilder().setTerm(1).setCandidateId("rotated-peer").build();
            assertNotNull(awaitSuccess(oldPeer.sendVoteRequest(nodeId, oldVote), TIMEOUT));
            assertNotNull(awaitSuccess(rotatedPeer.sendVoteRequest(nodeId, rotatedVote), TIMEOUT));

            trustState.update("raft-overlap-v2", Set.of(tls.clientSerial()));
            assertNotNull(awaitFailure(oldPeer.sendVoteRequest(nodeId, oldVote), TIMEOUT));
            assertNotNull(awaitSuccess(rotatedPeer.sendVoteRequest(nodeId, rotatedVote), TIMEOUT));
        } finally {
            awaitSuccess(oldPeer.stop(), TIMEOUT);
            awaitSuccess(rotatedPeer.stop(), TIMEOUT);
            awaitSuccess(server.stop(), TIMEOUT);
            awaitSuccess(node.stop(), TIMEOUT);
        }
    }

    private static int reservePort(Vertx vertx) {
        NetServer reservation = awaitSuccess(vertx.createNetServer()
                .connectHandler(socket -> socket.close())
                .listen(0, "127.0.0.1"), TIMEOUT);
        int port = reservation.actualPort();
        awaitSuccess(reservation.close(), TIMEOUT);
        return port;
    }

    private record TlsMaterial(Path serverCertificate, Path serverPrivateKey,
                               Path clientCertificate, Path clientPrivateKey, String clientSerial) {
        static TlsMaterial load(Path targetDirectory) throws Exception {
            Path serverCertificate = copyResource(RaftMtlsBoundaryIntegrationTest.class,
                    "/security/server-cert.pem", targetDirectory);
            Path serverPrivateKey = copyResource(RaftMtlsBoundaryIntegrationTest.class,
                    "/security/server-key.pem", targetDirectory);
            Path clientCertificate = copyResource(RaftMtlsBoundaryIntegrationTest.class,
                    "/security/client-cert.pem", targetDirectory);
            Path clientPrivateKey = copyResource(RaftMtlsBoundaryIntegrationTest.class,
                    "/security/client-key.pem", targetDirectory);
            try (InputStream input = Files.newInputStream(clientCertificate)) {
                X509Certificate certificate = (X509Certificate) CertificateFactory.getInstance("X.509")
                        .generateCertificate(input);
                return new TlsMaterial(serverCertificate, serverPrivateKey, clientCertificate, clientPrivateKey,
                        certificate.getSerialNumber().toString(16).toUpperCase(Locale.ROOT));
            }
        }

        RaftTlsConfig serverConfig() {
            return serverConfig(clientCertificate);
        }

        RaftTlsConfig serverConfig(Path trustBundle) {
            return new RaftTlsConfig(SecurityProfile.PRODUCTION, true, false,
                    serverCertificate, serverPrivateKey, trustBundle);
        }

        RaftTlsConfig clientConfig() {
            return new RaftTlsConfig(SecurityProfile.PRODUCTION, true, false,
                    clientCertificate, clientPrivateKey, serverCertificate);
        }

        RaftTlsConfig rotatedClientConfig() {
            return new RaftTlsConfig(SecurityProfile.PRODUCTION, true, false,
                    serverCertificate, serverPrivateKey, serverCertificate);
        }

    }
}
