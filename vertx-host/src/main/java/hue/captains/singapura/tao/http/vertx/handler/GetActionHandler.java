package hue.captains.singapura.tao.http.vertx.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import hue.captains.singapura.tao.http.action.GetAction;
import hue.captains.singapura.tao.http.action.Param;
import hue.captains.singapura.tao.http.action.TypedContent;
import io.vertx.core.Handler;
import io.vertx.ext.web.RoutingContext;

public class GetActionHandler<QP extends Param._QueryString, HP extends Param._Header, R>
        implements Handler<RoutingContext> {

    private final GetAction<RoutingContext, QP, HP, R> action;
    private final ObjectMapper objectMapper;

    public GetActionHandler(GetAction<RoutingContext, QP, HP, R> action, ObjectMapper objectMapper) {
        this.action = action;
        this.objectMapper = objectMapper;
    }

    @Override
    public void handle(RoutingContext ctx) {
        try {
            QP queryParams = action.queryStrMarshaller().marshal(ctx);
            HP headerParams = action.headerMarshaller().marshal(ctx);

            action.execute(queryParams, headerParams)
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
