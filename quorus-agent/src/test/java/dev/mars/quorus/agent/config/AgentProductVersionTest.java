/*
 * Copyright 2026 Mark Andrew Ray-Smith Cityline Ltd
 * Licensed under the Apache License, Version 2.0.
 */
package dev.mars.quorus.agent.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.mars.quorus.agent.QuorusAgent;
import dev.mars.quorus.agent.testing.FakeController;
import dev.mars.quorus.agent.testing.FakeController.Reply;
import dev.mars.quorus.config.PomVersion;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.Properties;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Register decision DR-Q5: the agent reports one product version, the root pom version, and it is not
 * a setting.
 */
@Timeout(value = 30, unit = SECONDS, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
class AgentProductVersionTest {

    private static final Duration WAIT = Duration.ofSeconds(10);

    @Test
    void registrationAndStatusReportThePomVersionWhateverTheOldSettingsSay() throws Exception {
        String pomVersion = PomVersion.of(Path.of("..", "pom.xml"));
        Properties leftover = new Properties();
        leftover.setProperty("quorus.agent.version", "9.9.9-setting");
        try (FakeController controller = FakeController.start()
                .on("POST", "/api/v1/agents/register", Reply.json(201, "{}").always())
                .on("POST", "/api/v1/agents/heartbeat", Reply.json(200, "{}").always())
                .on("GET", "/api/v1/agents/agent-v/jobs", Reply.json(200, "{\"pendingJobs\":[]}").always())
                .on("DELETE", "/api/v1/agents/agent-v", Reply.status(204).always())) {
            leftover.setProperty("quorus.agent.id", "agent-v");
            leftover.setProperty("quorus.agent.tenant.id", "tenant");
            leftover.setProperty("quorus.agent.controller.url", controller.url() + "/api/v1");
            leftover.setProperty("quorus.agent.security.profile", "development");
            leftover.setProperty("quorus.agent.security.allow-insecure", "true");
            leftover.setProperty("quorus.agent.tls.enabled", "false");
            leftover.setProperty("quorus.agent.port", Integer.toString(freePort()));
            AgentConfiguration config = AgentConfiguration.from(
                    new AgentConfig("default", leftover, Map.of("AGENT_VERSION", "8.8.8-environment")));
            QuorusAgent agent = new QuorusAgent(config);
            try {
                agent.start();

                String registered = controller.awaitRequests("POST", "/api/v1/agents/register", 1, WAIT)
                        .getFirst().json().get("version").asText();
                assertEquals(pomVersion, registered);
                try (HttpClient client = HttpClient.newHttpClient()) {
                    HttpResponse<String> status = client.send(HttpRequest.newBuilder(
                                    URI.create("http://localhost:" + agent.healthPort() + "/status")).build(),
                            HttpResponse.BodyHandlers.ofString());
                    assertEquals(pomVersion, new ObjectMapper().readTree(status.body()).get("version").asText());
                }
            } finally {
                agent.shutdown();
                agent.awaitShutdown();
            }
        }
    }

    private static int freePort() throws Exception {
        try (java.net.ServerSocket socket = new java.net.ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    @Test
    void thePackagedConfigurationDeclaresNoVersionSetting() throws Exception {
        Properties packaged = new Properties();
        try (InputStream in = Files.newInputStream(Path.of("src", "main", "resources", "quorus-agent.properties"))) {
            packaged.load(in);
        }

        assertFalse(packaged.containsKey("quorus.agent.version"), "the product version is not a setting");
    }
}
