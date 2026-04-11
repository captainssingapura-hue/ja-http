package hue.captains.singapura.tao.http.jetty.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import hue.captains.singapura.tao.http.action.HttpReturnableException;
import org.eclipse.jetty.server.Response;
import org.eclipse.jetty.util.Callback;

import java.nio.ByteBuffer;
import java.util.concurrent.CompletionException;
import java.util.logging.Level;
import java.util.logging.Logger;

final class ErrorHandler {

    private static final Logger LOG = Logger.getLogger(ErrorHandler.class.getName());

    private ErrorHandler() {}

    static void handle(Response response, Callback callback, Exception e, ObjectMapper objectMapper) {
        if (e instanceof HttpReturnableException<?, ?> returnable) {
            try {
                LOG.log(Level.WARNING, "Action error [status={0}]: {1}",
                        new Object[]{returnable.statusCode(), returnable.internalError()});

                byte[] json = objectMapper.writeValueAsBytes(returnable.externalError());
                response.setStatus(returnable.statusCode());
                response.getHeaders().put("Content-Type", "application/json");
                response.write(true, ByteBuffer.wrap(json), callback);
            } catch (Exception serializationError) {
                LOG.log(Level.SEVERE, "Failed to serialize external error", serializationError);
                fallback(response, callback, 500, "Internal server error");
            }
        } else {
            LOG.log(Level.SEVERE, "Unhandled exception in action", e);
            fallback(response, callback, 500, "Internal server error");
        }
    }

    static Exception unwrap(Throwable t) {
        Throwable cause = t instanceof CompletionException ? t.getCause() : t;
        return cause instanceof Exception ex ? ex : new RuntimeException(cause);
    }

    private static void fallback(Response response, Callback callback, int status, String message) {
        response.setStatus(status);
        response.getHeaders().put("Content-Type", "application/json");
        response.write(true,
                ByteBuffer.wrap(("{\"error\":\"" + message + "\"}").getBytes()),
                callback);
    }
}
