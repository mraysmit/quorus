/* Copyright 2026 Mark Andrew Ray-Smith Cityline Ltd. Licensed under Apache-2.0. */
package dev.mars.quorus.agent;

import dev.mars.quorus.agent.config.AgentConfiguration;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.junit5.VertxExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;
import static dev.mars.quorus.testing.TestFutureUtils.*;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(VertxExtension.class)
class AgentDefaultIsolationBoundaryTest {
    @Test
    void defaultAgentStopsAfterOneForeignAssignment(Vertx vertx) throws Exception {
        AtomicBoolean offered = new AtomicBoolean();
        Router router = Router.router(vertx);
        router.route().handler(BodyHandler.create());
        router.post("/api/v1/agents/register").handler(ctx -> ctx.json(new JsonObject().put("status", "registered")));
        router.delete("/api/v1/agents/:id").handler(ctx -> ctx.response().setStatusCode(204).end());
        router.get("/api/v1/agents/:id/jobs").handler(ctx -> ctx.json(new JsonObject().put("pendingJobs",
                offered.getAndSet(true) ? new JsonArray() : new JsonArray().add(new JsonObject()
                        .put("assignmentId", "foreign:other").put("jobId", "foreign").put("agentId", "other")
                        .put("sourceUri", "https://example.test/file").put("destinationUri", "file:///unused")))));
        var server = awaitSuccess(vertx.createHttpServer().requestHandler(router).listen(0), Duration.ofSeconds(5));
        var agent = new QuorusAgent(vertx, new AgentConfiguration.Builder()
                .securityProfile("development").allowInsecure(true).controllerTlsEnabled(false)
                .agentId("local").tenantId("tenant").agentPort(0).telemetryEnabled(false)
                .controllerUrl("http://localhost:" + server.actualPort() + "/api/v1")
                .jobPollingInitialDelayMs(1).jobPollingIntervalMs(20).build());
        try {
            agent.start();
            awaitSuccess(eventually(vertx, () -> offered.get() && !agent.isRunning(), Duration.ofSeconds(2)),
                    Duration.ofSeconds(3));
            assertFalse(agent.isRunning(), "Packaged default is fail-fast on the first foreign assignment");
        } finally {
            agent.shutdown();
            agent.awaitShutdown();
            awaitSuccess(server.close(), Duration.ofSeconds(5));
        }
    }
}
