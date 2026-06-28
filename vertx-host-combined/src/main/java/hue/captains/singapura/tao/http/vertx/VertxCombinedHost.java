package hue.captains.singapura.tao.http.vertx;

import com.fasterxml.jackson.databind.ObjectMapper;
import hue.captains.singapura.tao.http.action.ActionRegistry;
import hue.captains.singapura.tao.http.actor.ActorId;
import hue.captains.singapura.tao.http.actor.ActorSystem;
import hue.captains.singapura.tao.http.config.HostConfig;
import hue.captains.singapura.tao.http.config.TlsResolvers;
import hue.captains.singapura.tao.http.vertx.handler.GetActionHandler;
import hue.captains.singapura.tao.http.vertx.handler.PostActionHandler;
import hue.captains.singapura.tao.http.vertx.ws.WsLeadActor;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServer;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;

import java.util.regex.Pattern;

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
 *   var topicManagerId = actorSystem.allocateId("topicManager");
 *   actorSystem.register(topicManagerId, new TopicManagerActor());
 *   // ...
 *
 *   host.start("/pubsub", topicManagerId);
 * </pre>
 */
public class VertxCombinedHost {

    private final Vertx vertx;
    private final ActorSystem actorSystem;
    private final ActionRegistry<RoutingContext> registry;
    private final ObjectMapper objectMapper;
    private final HostConfig config;
    private final TlsResolvers resolvers;

    public VertxCombinedHost(ActorSystem actorSystem, ActionRegistry<RoutingContext> registry, int port) {
        this(actorSystem, registry, HostConfig.http(port));
    }

    public VertxCombinedHost(ActorSystem actorSystem, ActionRegistry<RoutingContext> registry, int port,
                             ObjectMapper objectMapper) {
        this(actorSystem, registry, HostConfig.http(port), TlsResolvers.defaults(), objectMapper);
    }

    public VertxCombinedHost(ActorSystem actorSystem, ActionRegistry<RoutingContext> registry, HostConfig config) {
        this(actorSystem, registry, config, TlsResolvers.defaults(), new ObjectMapper());
    }

    public VertxCombinedHost(ActorSystem actorSystem, ActionRegistry<RoutingContext> registry, HostConfig config,
                             TlsResolvers resolvers, ObjectMapper objectMapper) {
        this.vertx = Vertx.vertx();
        this.actorSystem = actorSystem;
        this.registry = registry;
        this.objectMapper = objectMapper;
        this.config = config;
        this.resolvers = resolvers;
    }

    /**
     * Returns the actor system used by this host.
     * Use this to register topics, actors, and application logic before calling {@link #start}.
     */
    public ActorSystem actorSystem() {
        return actorSystem;
    }

    /**
     * Starts the combined server with HTTP action routes and a WebSocket pub-sub endpoint.
     *
     * @param wsPath         the WebSocket upgrade path (e.g. {@code "/pubsub"})
     * @param topicManagerId the topic manager actor id (must already be registered in the actor system)
     * @return a future that completes when the server is listening
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public Future<HttpServer> start(String wsPath, ActorId topicManagerId) {
        Router router = Router.router(vertx);
        router.route().handler(BodyHandler.create());

        // --- HTTP action routes ---
        for (var entry : registry.getActions().entrySet()) {
            router.get().pathRegex(exactPath(entry.getKey()))
                    .handler(new GetActionHandler(entry.getValue(), objectMapper));
        }

        for (var entry : registry.postActions().entrySet()) {
            router.post().pathRegex(exactPath(entry.getKey()))
                    .handler(new PostActionHandler(entry.getValue(), objectMapper));
        }

        // --- WebSocket pub-sub ---
        @SuppressWarnings({"rawtypes", "unchecked"})
        var leadId = ActorId.allocate(null, "ws-lead");
        var leadActor = actorSystem.registerFrontier(leadId,
                WsLeadActor.constructor(actorSystem, topicManagerId));

        return vertx.createHttpServer(VertxTls.serverOptions(config, resolvers))
                .webSocketHandler(ws -> {
                    if (wsPath.equals(ws.path())) {
                        leadActor.onNewConnection(ws);
                    } else {
                        ws.reject();
                    }
                })
                .requestHandler(router)
                .listen(config.port(), config.host());
    }

    private static String exactPath(String path) {
        return "^" + Pattern.quote(path) + "$";
    }

    public Future<Void> stop() {
        return vertx.close();
    }
}
