/*
 * Copyright 2026 Mark Andrew Ray-Smith Cityline Ltd
 * Licensed under the Apache License, Version 2.0.
 */
package dev.mars.quorus.controller.raft;

import dev.mars.quorus.controller.security.CertificateTrustState;
import io.grpc.ForwardingServerCall;
import io.grpc.Grpc;
import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.Status;

import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLSession;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;

/** Re-evaluates Raft peer revocation on every RPC, including established TLS channels. */
final class RaftPeerAuthorizationInterceptor implements ServerInterceptor {
    private final CertificateTrustState trustState;

    RaftPeerAuthorizationInterceptor(CertificateTrustState trustState) {
        this.trustState = trustState;
    }

    @Override
    public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
            ServerCall<ReqT, RespT> call, Metadata headers, ServerCallHandler<ReqT, RespT> next) {
        try {
            SSLSession session = call.getAttributes().get(Grpc.TRANSPORT_ATTR_SSL_SESSION);
            if (session == null) return reject(call, "Raft mutual TLS session is required");
            X509Certificate peer = peerCertificate(session);
            CertificateTrustState.Evaluation evaluation = trustState.evaluate(peer);
            if (evaluation.revoked()) {
                trustState.recordRejection("raft-revoked");
                return reject(call, "Raft peer certificate is revoked");
            }
            return next.startCall(call, headers);
        } catch (SSLPeerUnverifiedException exception) {
            trustState.recordRejection("raft-unverified");
            return reject(call, "Verified Raft peer certificate is required");
        }
    }

    private static X509Certificate peerCertificate(SSLSession session) throws SSLPeerUnverifiedException {
        for (Certificate certificate : session.getPeerCertificates()) {
            if (certificate instanceof X509Certificate x509) return x509;
        }
        throw new SSLPeerUnverifiedException("No X.509 peer certificate");
    }

    private static <ReqT, RespT> ServerCall.Listener<ReqT> reject(
            ServerCall<ReqT, RespT> call, String description) {
        ServerCall<ReqT, RespT> guarded = new ForwardingServerCall.SimpleForwardingServerCall<>(call) { };
        guarded.close(Status.UNAUTHENTICATED.withDescription(description), new Metadata());
        return new ServerCall.Listener<>() { };
    }
}
