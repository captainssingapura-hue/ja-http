package hue.captains.singapura.tao.http.jetty;

import com.fasterxml.jackson.databind.ObjectMapper;
import hue.captains.singapura.tao.http.action.ActionRegistry;
import hue.captains.singapura.tao.http.jetty.handler.GetActionHandler;
import hue.captains.singapura.tao.http.jetty.handler.PostActionHandler;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Response;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.util.Callback;

import java.util.Map;
import java.util.logging.Logger;

public class JettyActionHost extends Handler.Abstract {

    private static final Logger LOG = Logger.getLogger(JettyActionHost.class.getName());

    private final ActionRegistry<Request> registry;
    private final ObjectMapper objectMapper;
    private final Server server;

    public JettyActionHost(ActionRegistry<Request> registry, int port) {
        this(registry, port, new ObjectMapper());
    }

    public JettyActionHost(ActionRegistry<Request> registry, int port, ObjectMapper objectMapper) {
        this.registry = registry;
        this.objectMapper = objectMapper;
        this.server = new Server(port);
        this.server.setHandler(this);
    }

    public void startServer() throws Exception {
        server.start();
        LOG.info("JettyActionHost started on port " + getPort());
    }

    public void stopServer() throws Exception {
        server.stop();
    }

    public int getPort() {
        return server.getURI().getPort();
    }

    public void joinServer() throws InterruptedException {
        server.join();
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    @Override
    public boolean handle(Request request, Response response, Callback callback) throws Exception {
        String method = request.getMethod();
        String path = request.getHttpURI().getPath();

        if ("GET".equalsIgnoreCase(method)) {
            var action = registry.getActions().get(path);
            if (action != null) {
                new GetActionHandler(action, objectMapper).handle(request, response, callback);
                return true;
            }
        } else if ("POST".equalsIgnoreCase(method)) {
            var action = registry.postActions().get(path);
            if (action != null) {
                new PostActionHandler(action, objectMapper).handle(request, response, callback);
                return true;
            }
        }

        response.setStatus(404);
        response.getHeaders().put("Content-Type", "application/json");
        response.write(true,
                java.nio.ByteBuffer.wrap("{\"error\":\"Not found\"}".getBytes()),
                callback);
        return true;
    }
}
