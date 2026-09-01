/*
 * Copyright 2026 Mark Andrew Ray-Smith Cityline Ltd
 * Licensed under the Apache License, Version 2.0.
 */
package dev.mars.quorus.agent.service;

import dev.mars.quorus.agent.config.AgentConfiguration;
import io.vertx.core.Vertx;
import io.vertx.core.net.PemKeyCertOptions;
import io.vertx.core.net.PemTrustOptions;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.WebClientOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Creates the single approved controller client posture for all agent services. */
final class ControllerWebClientFactory {
    private static final Logger logger = LoggerFactory.getLogger(ControllerWebClientFactory.class);

    private ControllerWebClientFactory() {
    }

    static WebClient create(Vertx vertx, AgentConfiguration config) {
        WebClientOptions options = new WebClientOptions()
                .setConnectTimeout(config.getHttpConnectionTimeout())
                .setIdleTimeout(config.getHttpIdleTimeout())
                .setUserAgent("Quorus-Agent/1.0")
                .setVerifyHost(true)
                .setTrustAll(false);
        if (config.isControllerTlsEnabled()) {
            options.setSsl(true)
                    .setKeyCertOptions(new PemKeyCertOptions()
                            .setCertPath(config.getTlsCertificatePath())
                            .setKeyPath(config.getTlsPrivateKeyPath()))
                    .setTrustOptions(new PemTrustOptions().addCertPath(config.getTlsTrustBundlePath()))
                    .setEnabledSecureTransportProtocols(java.util.Set.of("TLSv1.3"));
        } else {
            logger.warn("INSECURE DEVELOPMENT MODE: agent-to-controller traffic is plaintext");
        }
        return WebClient.create(vertx, options);
    }
}
