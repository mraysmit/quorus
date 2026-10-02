/* Copyright 2026 Mark Andrew Ray-Smith Cityline Ltd. Licensed under Apache-2.0. */
package dev.mars.quorus.controller.http;

import dev.mars.quorus.connection.*;
import dev.mars.quorus.controller.http.handlers.*;
import dev.mars.quorus.controller.raft.*;
import dev.mars.quorus.controller.security.SecurityProfile;
import dev.mars.quorus.controller.state.*;
import io.vertx.core.*;
import io.vertx.core.http.HttpServer;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.client.WebClient;
import io.vertx.junit5.VertxExtension;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.net.InetAddress;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.Semaphore;
import io.vertx.core.buffer.Buffer;
import io.vertx.ext.web.client.HttpResponse;

import static dev.mars.quorus.testing.TestFutureUtils.*;
import static org.junit.jupiter.api.Assertions.*;

/** Real HTTP entry points with real policy, registry and Raft; only DNS is a purpose-built fixture. */
@ExtendWith(VertxExtension.class)
class ControllerDnsBoundaryTest {
    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void dnsNeverRunsOnHttpEventLoop(boolean transfer, Vertx vertx) throws Exception {
        AtomicBoolean onEventLoop = new AtomicBoolean();
        HostResolver resolver = host -> {
            onEventLoop.set(Context.isOnEventLoopThread());
            return List.of(InetAddress.getByAddress(new byte[]{127, 0, 0, 1}));
        };
        try (Fixture fixture = new Fixture(vertx, resolver)) {
            var response = awaitSuccess(fixture.client.post(fixture.server.actualPort(), "localhost",
                    transfer ? "/api/v1/transfers" : "/api/v1/service-connections/connection/validate")
                    .sendJsonObject(body()), TIMEOUT);
            assertEquals(transfer ? 201 : 200, response.statusCode(), response.bodyAsString());
            assertFalse(onEventLoop.get(), "DNS resolution must leave the HTTP event loop");
        }
    }

    static JsonObject body() {
        return new JsonObject().put("tenantId", "tenant").put("serviceConnectionId", "connection")
                .put("remotePath", "/approved/file.dat").put("direction", "DOWNLOAD")
                .put("agentPool", "pool").put("destinationPath", "downloads/dns-test.dat");
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void slowDnsKeepsHealthResponsiveAndSharesCapacityAcrossRoutes(boolean transfer, Vertx vertx) {
        GatedResolver resolver = new GatedResolver();
        try (Fixture fixture = new Fixture(vertx, new ControllerConnectionAuthorizer(resolver, 1, 5_000))) {
            try {
                var pending = fixture.post(transfer);
                awaitSuccess(resolver.entered.future(), TIMEOUT);
                var health = awaitSuccess(fixture.client.get(fixture.server.actualPort(), "localhost", "/health/live")
                        .timeout(1_000).send(), TIMEOUT);
                assertEquals(200, health.statusCode());
                var overloaded = awaitSuccess(fixture.post(!transfer), TIMEOUT);
                assertEquals(503, overloaded.statusCode(), "Both routes must share one DNS capacity limit");
                assertEquals(1, resolver.calls.get(), "Overload must not enqueue another native lookup");
                resolver.release.release();
                assertEquals(transfer ? 201 : 200, awaitSuccess(pending, TIMEOUT).statusCode());
            } finally {
                resolver.release.release();
            }
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void timeoutRetainsCapacityUntilNativeLookupEndsAndDiscardsLateResult(boolean transfer, Vertx vertx) {
        GatedResolver resolver = new GatedResolver();
        try (Fixture fixture = new Fixture(vertx, new ControllerConnectionAuthorizer(resolver, 1, 100))) {
            try {
                var pending = fixture.post(transfer);
                awaitSuccess(resolver.entered.future(), TIMEOUT);
                assertEquals(504, awaitSuccess(pending, TIMEOUT).statusCode(), "DNS must finish the HTTP request at its deadline");
                assertEquals(503, awaitSuccess(fixture.post(!transfer), TIMEOUT).statusCode());
                assertEquals(1, resolver.calls.get(), "Timeout must not free a still-running lookup's slot");
                resolver.release.release();
                var recovered = awaitSuccess(validationAfterCapacityReturns(fixture, vertx, 100), TIMEOUT);
                assertEquals(200, recovered.statusCode());
                assertEquals(0, fixture.state.getTransferJobCount(), "Late resolution must never create a timed-out transfer");
                long approvals = fixture.state.getSystemMetadata().values().stream()
                        .filter(value -> value.contains("\"reasonCode\":\"Q-CONNECTION-POLICY-APPROVED\"")).count();
                assertEquals(1, approvals, "Only the recovery request may record validation approval");
            } finally {
                resolver.release.release();
            }
        }
    }

    private Future<HttpResponse<Buffer>> validationAfterCapacityReturns(Fixture fixture, Vertx vertx, int remaining) {
        return fixture.post(false).compose(response -> {
            if (response.statusCode() != 503 || remaining == 0) return Future.succeededFuture(response);
            Promise<Void> next = Promise.promise();
            vertx.setTimer(10, ignored -> next.complete());
            return next.future().compose(ignored -> validationAfterCapacityReturns(fixture, vertx, remaining - 1));
        });
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void removedConnectionCannotBeApprovedAfterDnsReturns(boolean transfer, Vertx vertx) {
        GatedResolver resolver = new GatedResolver();
        try (Fixture fixture = new Fixture(vertx, resolver)) {
            try {
                var pending = fixture.post(transfer);
                awaitSuccess(resolver.entered.future(), TIMEOUT);
                var registry = new ServiceConnectionRegistry(fixture.state);
                awaitSuccess(fixture.node.submitCommand(new SystemMetadataCommand.Delete(
                        registry.connectionKey("tenant", "connection"))), TIMEOUT);
                resolver.release.release();
                assertEquals(409, awaitSuccess(pending, TIMEOUT).statusCode());
                assertEquals(0, fixture.state.getTransferJobCount());
            } finally {
                resolver.release.release();
            }
        }
    }

    @Test
    void removedSecretCannotAuthorizeTransferAfterDnsReturns(Vertx vertx) {
        GatedResolver resolver = new GatedResolver();
        try (Fixture fixture = new Fixture(vertx, resolver)) {
            try {
                var pending = fixture.post(true);
                awaitSuccess(resolver.entered.future(), TIMEOUT);
                var registry = new ServiceConnectionRegistry(fixture.state);
                awaitSuccess(fixture.node.submitCommand(new SystemMetadataCommand.Delete(
                        registry.secretKey("tenant", "secret"))), TIMEOUT);
                resolver.release.release();
                assertEquals(409, awaitSuccess(pending, TIMEOUT).statusCode());
                assertEquals(0, fixture.state.getTransferJobCount());
            } finally {
                resolver.release.release();
            }
        }
    }

    @Test
    void secretExpiringDuringDnsRetainsDurableExpiryTransition(Vertx vertx) {
        GatedResolver resolver = new GatedResolver();
        try (Fixture fixture = new Fixture(vertx, resolver)) {
            try {
                var registry = new ServiceConnectionRegistry(fixture.state);
                Instant expires = Instant.now().plusMillis(500);
                var secret = new SecretReference("secret", "tenant", "vault", "opaque", "key", "1",
                        SecretReference.Status.ACTIVE, expires, null);
                awaitSuccess(fixture.node.submitCommand(new SystemMetadataCommand.Set(
                        registry.secretKey("tenant", "secret"), ServiceConnectionRegistry.encode(secret))), TIMEOUT);
                var pending = fixture.post(true);
                awaitSuccess(resolver.entered.future(), TIMEOUT);
                awaitSuccess(eventually(vertx, () -> !Instant.now().isBefore(expires), TIMEOUT), TIMEOUT);
                resolver.release.release();
                assertEquals(409, awaitSuccess(pending, TIMEOUT).statusCode());
                assertEquals(SecretReference.Status.EXPIRED, registry.findSecret("tenant", "secret").status());
                assertTrue(fixture.state.getSystemMetadata().values().stream()
                        .anyMatch(value -> value.contains("\"reasonCode\":\"Q-SECRET-EXPIRED\"")));
                assertEquals(0, fixture.state.getTransferJobCount());
            } finally {
                resolver.release.release();
            }
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void mixedApprovedAndDeniedDnsAnswersFailClosed(boolean transfer, Vertx vertx) {
        HostResolver resolver = host -> List.of(InetAddress.getByAddress(new byte[]{127, 0, 0, 1}),
                InetAddress.getByAddress(new byte[]{10, 0, 0, 1}));
        try (Fixture fixture = new Fixture(vertx, resolver)) {
            assertEquals(400, awaitSuccess(fixture.post(transfer), TIMEOUT).statusCode());
            assertEquals(0, fixture.state.getTransferJobCount());
        }
    }

    @Test
    void failedLookupReleasesCapacityAndSuccessfulTransferRetainsPinnedAddresses(Vertx vertx) {
        AtomicInteger calls = new AtomicInteger();
        HostResolver resolver = host -> {
            if (calls.incrementAndGet() == 1) throw new java.net.UnknownHostException("fixture failure");
            return List.of(InetAddress.getByAddress(new byte[]{127, 0, 0, 1}));
        };
        try (Fixture fixture = new Fixture(vertx, new ControllerConnectionAuthorizer(resolver, 1, 5_000))) {
            assertEquals(400, awaitSuccess(fixture.post(true), TIMEOUT).statusCode());
            assertEquals(201, awaitSuccess(fixture.post(true), TIMEOUT).statusCode());
            assertEquals(List.of("127.0.0.1"), fixture.state.getTransferJobs().values().iterator().next()
                    .getControllerResolvedAddresses());
        }
    }

    @Test
    void expiredQueuedWorkDoesNotStartAnotherNativeLookup() {
        Vertx singleWorker = Vertx.vertx(new VertxOptions().setWorkerPoolSize(1));
        GatedResolver resolver = new GatedResolver();
        try (Fixture fixture = new Fixture(singleWorker, new ControllerConnectionAuthorizer(resolver, 2, 100))) {
            try {
                var first = fixture.post(true);
                awaitSuccess(resolver.entered.future(), TIMEOUT);
                var queued = fixture.post(false);
                assertEquals(504, awaitSuccess(first, TIMEOUT).statusCode());
                assertEquals(504, awaitSuccess(queued, TIMEOUT).statusCode());
                assertEquals(1, resolver.calls.get());
                resolver.release.release();
                assertEquals(200, awaitSuccess(validationAfterCapacityReturns(fixture, singleWorker, 100), TIMEOUT).statusCode());
                assertEquals(2, resolver.calls.get(), "Only the initial and recovery lookup should reach the resolver");
                assertEquals(0, fixture.state.getTransferJobCount());
            } finally {
                resolver.release.release();
            }
        } finally {
            awaitSuccess(singleWorker.close(), TIMEOUT);
        }
    }

    @Test
    void independentLookupsUseAvailableCapacityConcurrently(Vertx vertx) {
        Promise<Void> bothEntered = Promise.promise();
        AtomicInteger calls = new AtomicInteger();
        Semaphore release = new Semaphore(0);
        HostResolver resolver = host -> {
            if (calls.incrementAndGet() == 2) bothEntered.complete();
            release.acquire();
            return List.of(InetAddress.getByAddress(new byte[]{127, 0, 0, 1}));
        };
        try (Fixture fixture = new Fixture(vertx, new ControllerConnectionAuthorizer(resolver, 2, 5_000))) {
            try {
                var first = fixture.post(true);
                var second = fixture.post(false);
                awaitSuccess(bothEntered.future(), Duration.ofSeconds(1));
                assertEquals(503, awaitSuccess(fixture.post(true), TIMEOUT).statusCode());
                assertEquals(2, calls.get());
                release.release(2);
                assertEquals(201, awaitSuccess(first, TIMEOUT).statusCode());
                assertEquals(200, awaitSuccess(second, TIMEOUT).statusCode());
            } finally {
                release.release(2);
            }
        }
    }

    /** The semaphore models a native blocking resolver; test coordination uses Vert.x futures. */
    static final class GatedResolver implements HostResolver {
        final Promise<Void> entered = Promise.promise();
        final Semaphore release = new Semaphore(0);
        final AtomicInteger calls = new AtomicInteger();

        public List<InetAddress> resolve(String host) throws Exception {
            if (calls.incrementAndGet() == 1) {
                entered.complete();
                release.acquire();
            }
            return List.of(InetAddress.getByAddress(new byte[]{127, 0, 0, 1}));
        }
    }

    static final class Fixture implements AutoCloseable {
        final QuorusStateStore state = new QuorusStateStore();
        final RaftNode node;
        final HttpServer server;
        final WebClient client;

        Fixture(Vertx vertx, HostResolver resolver) {
            this(vertx, new ControllerConnectionAuthorizer(resolver));
        }

        Fixture(Vertx vertx, ControllerConnectionAuthorizer authorizer) {
            String id = "dns-" + System.nanoTime();
            node = RaftNode.builder().vertx(vertx).nodeId(id).clusterNodes(Set.of(id))
                    .transport(new InMemoryTransportSimulator(id)).stateMachine(state)
                    .mode(RaftNodeMode.volatileMode()).electionTimeout(250).heartbeatInterval(50).build();
            awaitSuccess(node.start(), TIMEOUT);
            awaitSuccess(eventually(vertx, node::isLeader, TIMEOUT), TIMEOUT.plusSeconds(1));
            var registry = new ServiceConnectionRegistry(state);
            var secret = new SecretReference("secret", "tenant", "vault", "opaque", "key", "1",
                    SecretReference.Status.ACTIVE, null, null);
            var connection = new ServiceConnection("connection", "tenant", ServiceConnection.Protocol.SFTP,
                    URI.create("sftp://dns.example.test"), "zone", Set.of("/approved"),
                    Set.of(ServiceConnection.Direction.DOWNLOAD), Set.of("pool"), "owner", "test", "internal",
                    "secret", "identity", ServiceConnection.AuthenticationType.PASSWORD,
                    new ServiceConnection.TrustPolicy(false, false, Set.of(), Set.of("SHA256:fixture"), "TLSv1.3"),
                    new ServiceConnection.EgressPolicy(Set.of("dns.example.test"), Set.of("127.0.0.0/8"),
                            Set.of(22), false, true), 1, ServiceConnection.Status.ACTIVE, Instant.now(), Instant.now());
            awaitSuccess(node.submitCommand(new SystemMetadataCommand.Set(registry.secretKey("tenant", "secret"),
                    ServiceConnectionRegistry.encode(secret))), TIMEOUT);
            awaitSuccess(node.submitCommand(new SystemMetadataCommand.Set(registry.connectionKey("tenant", "connection"),
                    ServiceConnectionRegistry.encode(connection))), TIMEOUT);
            Router router = Router.router(vertx);
            router.route().handler(BodyHandler.create());
            router.post("/api/v1/transfers").handler(new TransferHandler(node, state,
                    SecurityProfile.DEVELOPMENT, authorizer).handleCreate());
            router.post("/api/v1/service-connections/:serviceConnectionId/validate")
                    .handler(new ServiceConnectionHandler(node, state, authorizer).validateConnection());
            router.get("/health/live").handler(ctx -> ctx.response().end("OK"));
            router.route().failureHandler(new GlobalErrorHandler());
            server = awaitSuccess(vertx.createHttpServer().requestHandler(router).listen(0, "127.0.0.1"), TIMEOUT);
            client = WebClient.create(vertx);
        }

        Future<HttpResponse<Buffer>> post(boolean transfer) {
            return client.post(server.actualPort(), "localhost", transfer ? "/api/v1/transfers"
                    : "/api/v1/service-connections/connection/validate").timeout(2_000).sendJsonObject(body());
        }

        public void close() {
            client.close();
            awaitSuccess(server.close(), TIMEOUT);
            awaitSuccess(node.stop(), TIMEOUT);
        }
    }
}
