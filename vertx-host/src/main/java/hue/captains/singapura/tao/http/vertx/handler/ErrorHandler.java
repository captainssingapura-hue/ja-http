package hue.captains.singapura.tao.http.vertx.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import hue.captains.singapura.tao.http.action.HttpReturnableException;
import io.vertx.ext.web.RoutingContext;

import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

final class ErrorHandler {

    private static final Logger LOG = Logger.getLogger(ErrorHandler.class.getName());

    private ErrorHandler() {}

    static void handle(RoutingContext ctx, Exception e, ObjectMapper objectMapper) {
        if (e instanceof HttpReturnableException<?, ?> returnable) {
            try {
                LOG.log(Level.WARNING, "Action error [status={0}]: {1}",
                        new Object[]{returnable.statusCode(), returnable.internalError()});

                ctx.response()
                        .setStatusCode(returnable.statusCode())
                        .putHeader("Content-Type", "application/json")
                        .end(objectMapper.writeValueAsString(returnable.externalError()));
            } catch (Exception serializationError) {
                LOG.log(Level.SEVERE, "Failed to serialize external error", serializationError);
                fallback(ctx, 500, "Internal server error");
            }
        } else {
            LOG.log(Level.SEVERE, "Unhandled exception in action", e);
            fallback(ctx, 500, "Internal server error");
        }
    }

    private static void fallback(RoutingContext ctx, int status, String message) {
        ctx.response()
                .setStatusCode(status)
                .putHeader("Content-Type", "application/json")
                .end("{\"error\":\"" + message + "\"}");
    }
}
