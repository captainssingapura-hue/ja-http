package hue.captains.singapura.tao.http.vertx;

import com.fasterxml.jackson.databind.ObjectMapper;
import hue.captains.singapura.tao.http.action.ActionRegistry;
import hue.captains.singapura.tao.http.actor.ActorRef;
import hue.captains.singapura.tao.http.actor.ActorSystem;
import hue.captains.singapura.tao.http.vertx.handler.GetActionHandler;
import hue.captains.singapura.tao.http.vertx.handler.PostActionHandler;
import hue.captains.singapura.tao.http.vertx.ws.VertxActorSystem;
import hue.captains.singapura.tao.http.vertx.ws.WsLeadActor;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServer;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;

/**
 * A Vert.x host that serves both HTTP actions and WebSocket pub-sub on a single port.
 * <p>
 * Shares one {@link Vertx} instance and one HTTP server across both subsystems.
 * The caller gets the {@link ActorSystem} to register topics, actors, and application
 * logic before starting the server.
 * <p>
 * Usage:
 * <pre>
 *   var host = new VertxCombinedHost(registry, 8080);
 *   var actorSystem = host.actorSystem();
 *
 *   // Wire up pub-sub: topic manager, topics, application actors...
 *   var topicManagerRef = actorSystem.allocateRef("topicManager");
 *   actorSystem.register(topicManagerRef, new TopicManagerActor());
 *   // ...
 *
 *   host.start("/pubsub", topicManagerRef);
 * </pre>
 */
public class VertxCombinedHost {

    private final Vertx vertx;
    private final VertxActorSystem actorSystem;
    private final ActionRegistry<RoutingContext> registry;
    private final ObjectMapper objectMapper;
    private final int port;

    public VertxCombinedHost(ActionRegistry<RoutingContext> registry, int port) {
        this(registry, port, new ObjectMapper());
    }

    public VertxCombinedHost(ActionRegistry<RoutingContext> registry, int port,
                             ObjectMapper objectMapper) {
        this.vertx = Vertx.vertx();
        this.actorSystem = new VertxActorSystem(vertx);
        this.registry = registry;
        this.objectMapper = objectMapper;
        this.port = port;
    }

    /**
     * Returns the actor system backed by this host's Vert.x event loop.
     * Use this to register topics, actors, and application logic before calling {@link #start}.
     */
    public ActorSystem actorSystem() {
        return actorSystem;
    }

    /**
     * Starts the combined server with HTTP action routes and a WebSocket pub-sub endpoint.
     *
     * @param wsPath          the WebSocket upgrade path (e.g. {@code "/pubsub"})
     * @param topicManagerRef the topic manager actor ref (must already be registered in the actor system)
     * @return a future that completes when the server is listening
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public Future<HttpServer> start(String wsPath, ActorRef topicManagerRef) {
        Router router = Router.router(vertx);
        router.route().handler(BodyHandler.create());

        // --- HTTP action routes ---
        for (var entry : registry.getActions().entrySet()) {
            router.get(entry.getKey())
                    .handler(new GetActionHandler(entry.getValue(), objectMapper));
        }

        for (var entry : registry.postActions().entrySet()) {
            router.post(entry.getKey())
                    .handler(new PostActionHandler(entry.getValue(), objectMapper));
        }

        // --- WebSocket pub-sub ---
        var leadRef = actorSystem.allocateRef("ws-lead");
        var leadActor = actorSystem.registerFrontier(leadRef,
                WsLeadActor.constructor(actorSystem, topicManagerRef));

        return vertx.createHttpServer()
                .webSocketHandler(ws -> {
                    if (wsPath.equals(ws.path())) {
                        leadActor.onNewConnection(ws);
                    } else {
                        ws.reject();
                    }
                })
                .requestHandler(router)
                .listen(port);
    }

    public Future<Void> stop() {
        return vertx.close();
    }
}
