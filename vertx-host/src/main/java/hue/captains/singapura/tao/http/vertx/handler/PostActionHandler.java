package hue.captains.singapura.tao.http.vertx.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import hue.captains.singapura.tao.http.action.Param;
import hue.captains.singapura.tao.http.action.PostAction;
import hue.captains.singapura.tao.http.action.TypedContent;
import io.vertx.core.Handler;
import io.vertx.ext.web.RoutingContext;

public class PostActionHandler<PP extends Param._Post, HP extends Param._Header, R>
        implements Handler<RoutingContext> {

    private final PostAction<RoutingContext, PP, HP, R> action;
    private final ObjectMapper objectMapper;

    public PostActionHandler(PostAction<RoutingContext, PP, HP, R> action, ObjectMapper objectMapper) {
        this.action = action;
        this.objectMapper = objectMapper;
    }

    @Override
    public void handle(RoutingContext ctx) {
        try {
            PP postParams = action.postMarshaller().marshal(ctx);
            HP headerParams = action.headerMarshaller().marshal(ctx);

            action.execute(postParams, headerParams)
                    .thenAccept(result -> {
                        try {
                            if (result instanceof TypedContent tc) {
                                ctx.response()
                                        .putHeader("Content-Type", tc.contentType())
                                        .end(tc.body());
                            } else {
                                ctx.response()
                                        .putHeader("Content-Type", "application/json")
                                        .end(objectMapper.writeValueAsString(result));
                            }
                        } catch (Exception e) {
                            ErrorHandler.handle(ctx, e, objectMapper);
                        }
                    })
                    .exceptionally(ex -> {
                        ErrorHandler.handle(ctx, unwrap(ex), objectMapper);
                        return null;
                    });
        } catch (Exception e) {
            ErrorHandler.handle(ctx, e, objectMapper);
        }
    }

    private static Exception unwrap(Throwable t) {
        Throwable cause = t instanceof java.util.concurrent.CompletionException ? t.getCause() : t;
        return cause instanceof Exception ex ? ex : new RuntimeException(cause);
    }
}
