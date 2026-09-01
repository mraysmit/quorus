/*
 * Copyright 2026 Mark Andrew Ray-Smith Cityline Ltd
 * Licensed under the Apache License, Version 2.0.
 */
package dev.mars.quorus.controller.security;

import dev.mars.quorus.controller.http.CorrelationIdHandler;
import dev.mars.quorus.controller.http.ErrorCode;
import dev.mars.quorus.controller.http.QuorusApiException;
import dev.mars.quorus.controller.security.audit.AuditEvent;
import dev.mars.quorus.controller.security.audit.AuditSink;
import io.vertx.core.Handler;
import io.vertx.ext.web.RoutingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLPeerUnverifiedException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/** Authenticates a trusted gateway assertion or a directly bound mTLS identity. */
public final class AuthenticationHandler implements Handler<RoutingContext> {
    public static final String PRINCIPAL = "X-Quorus-Principal";
    public static final String IDENTITY_TYPE = "X-Quorus-Identity-Type";
    public static final String TENANT = "X-Quorus-Tenant";
    public static final String ENVIRONMENT = "X-Quorus-Environment";
    public static final String ROLES = "X-Quorus-Roles";
    public static final String SCOPES = "X-Quorus-Scopes";
    public static final String EXPIRES_AT = "X-Quorus-Expires-At";
    public static final String ELEVATION_EXPIRES_AT = "X-Quorus-Elevation-Expires-At";

    private static final Logger logger = LoggerFactory.getLogger(AuthenticationHandler.class);

    private final SecurityConfig config;
    private final AuditSink auditSink;
    private final CertificateTrustState trustState;

    public AuthenticationHandler(SecurityConfig config, AuditSink auditSink) {
        this(config, auditSink, CertificateTrustState.from(config));
    }

    public AuthenticationHandler(SecurityConfig config, AuditSink auditSink, CertificateTrustState trustState) {
        this.config = config;
        this.auditSink = auditSink;
        this.trustState = trustState;
    }

    @Override
    public void handle(RoutingContext context) {
        if (isPublic(context.request().path())) {
            context.next();
            return;
        }
        if (!config.enabled()) {
            context.next();
            return;
        }
        try {
            if (!context.request().connection().isSsl()) {
                deny(context, "Q-AUTHN-TLS-REQUIRED", "Authenticated API access requires TLS");
                return;
            }
            X509Certificate certificate = peerCertificate(context);
            certificate.checkValidity();
            CertificateTrustState.Evaluation certificateEvaluation = trustState.evaluate(certificate);
            if (certificateEvaluation.revoked()) {
                trustState.recordRejection("revoked");
                deny(context, "Q-AUTHN-CERTIFICATE-REVOKED", "Client certificate is revoked");
                return;
            }
            String subject = certificate.getSubjectX500Principal().getName();
            SecurityIdentity identity;
            if (config.trustedGatewaySubjects().contains(subject)) {
                identity = gatewayIdentity(context, subject);
            } else {
                identity = config.mtlsIdentities().get(subject);
                if (identity == null) {
                    deny(context, "Q-AUTHN-PEER-UNTRUSTED", "Client certificate is not bound to a Quorus identity");
                    return;
                }
            }
            if (identity.isExpired(Instant.now())) {
                deny(context, "Q-AUTHN-IDENTITY-EXPIRED", "Identity assertion has expired");
                return;
            }
            SecurityContext.setIdentity(context, identity);
            audit(context, identity, "AUTHENTICATION", "ALLOW", "Q-AUTHN-VERIFIED");
            if (certificateEvaluation.expiryAlertState() == CertificateTrustState.ExpiryAlertState.WARNING) {
                auditSink.append(new AuditEvent(Instant.now(), "CERTIFICATE_EXPIRY_WARNING", "WARNING",
                        "Q-CERT-EXPIRY-WARNING", identity.principalId(), identity.type().name(), identity.tenantId(),
                        identity.environment(), identity.certificateSubject(), context.request().method().name(),
                        context.request().path(), CorrelationIdHandler.getRequestId(context),
                        java.util.Map.of("trustBundleVersion", certificateEvaluation.trustBundleVersion(),
                                "certificateSecondsRemaining",
                                Long.toString(certificateEvaluation.certificateSecondsRemaining()))));
            }
            context.next();
        } catch (SSLPeerUnverifiedException exception) {
            deny(context, "Q-AUTHN-CERTIFICATE-MISSING", "A verified client certificate is required");
        } catch (CertificateException exception) {
            trustState.recordRejection("invalid");
            deny(context, "Q-AUTHN-CERTIFICATE-INVALID", "Client certificate is not currently valid");
        } catch (IllegalArgumentException | DateTimeParseException exception) {
            deny(context, "Q-AUTHN-ASSERTION-INVALID", "Trusted identity assertion is incomplete or invalid");
        } catch (RuntimeException exception) {
            context.fail(exception);
        }
    }

    private static X509Certificate peerCertificate(RoutingContext context) throws SSLPeerUnverifiedException {
        for (Certificate certificate : context.request().connection().peerCertificates()) {
            if (certificate instanceof X509Certificate x509) return x509;
        }
        throw new SSLPeerUnverifiedException("No X.509 client certificate");
    }

    private static SecurityIdentity gatewayIdentity(RoutingContext context, String subject) {
        String principal = required(context, PRINCIPAL);
        IdentityType type = IdentityType.valueOf(required(context, IDENTITY_TYPE).toUpperCase(Locale.ROOT));
        String tenant = required(context, TENANT);
        String environment = required(context, ENVIRONMENT);
        Set<SecurityRole> roles = tokens(context.request().getHeader(ROLES)).stream()
                .map(value -> SecurityRole.valueOf(value.toUpperCase(Locale.ROOT))).collect(Collectors.toUnmodifiableSet());
        Set<String> scopes = tokens(context.request().getHeader(SCOPES));
        Instant expiresAt = Instant.parse(required(context, EXPIRES_AT));
        String elevation = context.request().getHeader(ELEVATION_EXPIRES_AT);
        return new SecurityIdentity(principal, type, tenant, environment, roles, scopes, subject,
                Instant.now(), expiresAt, elevation == null || elevation.isBlank() ? null : Instant.parse(elevation));
    }

    private void deny(RoutingContext context, String code, String reason) {
        audit(context, null, "AUTHENTICATION", "DENY", code);
        logger.warn("Authentication denied: code={}, method={}, path={}", code,
                context.request().method(), context.request().path());
        context.fail(new QuorusApiException(ErrorCode.UNAUTHORIZED, reason));
    }

    private void audit(RoutingContext context, SecurityIdentity identity, String eventType,
                       String outcome, String code) {
        auditSink.append(new AuditEvent(Instant.now(), eventType, outcome, code,
                identity == null ? null : identity.principalId(), identity == null ? null : identity.type().name(),
                identity == null ? null : identity.tenantId(), identity == null ? null : identity.environment(),
                identity == null ? null : identity.certificateSubject(), context.request().method().name(),
                context.request().path(), CorrelationIdHandler.getRequestId(context), null));
    }

    private static boolean isPublic(String path) {
        return path.equals("/health/live") || path.equals("/health/ready")
                || path.equals("/api/v1/openapi.yaml");
    }

    private static String required(RoutingContext context, String header) {
        String value = context.request().getHeader(header);
        if (value == null || value.isBlank()) throw new IllegalArgumentException("Missing " + header);
        return value.trim();
    }

    private static Set<String> tokens(String value) {
        if (value == null || value.isBlank()) return Set.of();
        return Arrays.stream(value.split(",")).map(String::trim).filter(token -> !token.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
    }
}
