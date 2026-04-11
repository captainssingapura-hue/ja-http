package hue.captains.singapura.tao.http.vertx;

import com.fasterxml.jackson.databind.ObjectMapper;
import hue.captains.singapura.tao.http.action.ActionRegistry;
import hue.captains.singapura.tao.http.vertx.handler.GetActionHandler;
import hue.captains.singapura.tao.http.vertx.handler.PostActionHandler;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServer;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;

public class VertxActionHost {

    private final Vertx vertx;
    private final ActionRegistry<RoutingContext> registry;
    private final ObjectMapper objectMapper;
    private final int port;

    public VertxActionHost(ActionRegistry<RoutingContext> registry, int port) {
        this(registry, port, new ObjectMapper());
    }

    public VertxActionHost(ActionRegistry<RoutingContext> registry, int port, ObjectMapper objectMapper) {
        this.vertx = Vertx.vertx();
        this.registry = registry;
        this.port = port;
        this.objectMapper = objectMapper;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public Future<HttpServer> start() {
        Router router = Router.router(vertx);
        router.route().handler(BodyHandler.create());

        for (var entry : registry.getActions().entrySet()) {
            router.get(entry.getKey())
                    .handler(new GetActionHandler(entry.getValue(), objectMapper));
        }

        for (var entry : registry.postActions().entrySet()) {
            router.post(entry.getKey())
                    .handler(new PostActionHandler(entry.getValue(), objectMapper));
        }

        return vertx.createHttpServer()
                .requestHandler(router)
                .listen(port);
    }

    public Future<Void> stop() {
        return vertx.close();
    }
}
