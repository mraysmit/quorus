package dev.mars.quorus.controller.http;

import dev.mars.quorus.controller.raft.RaftNode;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServer;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.BodyHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Reactive HTTP API Server using Vert.x Web.
 */
public class HttpApiServer {

    private static final Logger logger = LoggerFactory.getLogger(HttpApiServer.class);

    private final Vertx vertx;
    private final int port;
    private final RaftNode raftNode;
    private HttpServer httpServer;

    public HttpApiServer(Vertx vertx, int port, RaftNode raftNode) {
        this.vertx = vertx;
        this.port = port;
        this.raftNode = raftNode;
    }

    public Future<Void> start() {
        Router router = Router.router(vertx);

        // Enable body parsing
        router.route().handler(BodyHandler.create());

        // Define routes
        router.get("/health")
                .respond(ctx -> Future.succeededFuture(new io.vertx.core.json.JsonObject().put("status", "UP")));

        router.post("/api/v1/command").respond(ctx -> {
            io.vertx.core.json.JsonObject body = ctx.body().asJsonObject();
            return raftNode.submitCommand(body.getMap())
                    .thenApply(res -> new io.vertx.core.json.JsonObject().put("result", "OK").put("data", res))
                    .toCompletableFuture() // Ideal world: RaftNode returns Vert.x Future
                    // For now, wrapper as simplified text:
                    .thenApply(json -> (io.vertx.core.json.JsonObject) json);
        });

        httpServer = vertx.createHttpServer()
                .requestHandler(router);

        return httpServer.listen(port)
                .onSuccess(server -> logger.info("HTTP API Server listening on port {}", port))
                .onFailure(err -> logger.error("Failed to start HTTP API Server", err))
                .mapEmpty();
    }

    public Future<Void> stop() {
        if (httpServer != null) {
            return httpServer.close()
                    .onSuccess(v -> logger.info("HTTP API Server stopped"));
        }
        return Future.succeededFuture();
    }
}
