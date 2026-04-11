package hue.captains.singapura.tao.http.rps2.remote;

import hue.captains.singapura.tao.http.actor.remote.RemoteTransport;
import io.vertx.core.http.ServerWebSocket;

import java.util.function.Consumer;

/**
 * {@link RemoteTransport} backed by a Vert.x {@link ServerWebSocket}.
 * <p>
 * Supports an optional {@link #afterReceive(Runnable)} hook that runs
 * after each inbound message is dispatched — used by the server to
 * drain the actor mailbox after external events.
 */
public class VertxWsTransport implements RemoteTransport {

    private final ServerWebSocket ws;
    private Runnable afterReceive;

    public VertxWsTransport(ServerWebSocket ws) {
        this.ws = ws;
    }

    /**
     * Registers a hook that runs after each inbound message is dispatched
     * to the handler set via {@link #onReceive}. Must be called before
     * {@code onReceive} to take effect.
     */
    public void afterReceive(Runnable hook) {
        this.afterReceive = hook;
    }

    @Override
    public void send(String data) {
        ws.writeTextMessage(data);
    }

    @Override
    public void onReceive(Consumer<String> handler) {
        ws.textMessageHandler(data -> {
            handler.accept(data);
            if (afterReceive != null) {
                afterReceive.run();
            }
        });
    }

    @Override
    public void onClose(Runnable handler) {
        ws.closeHandler(v -> handler.run());
    }

    @Override
    public void close() {
        ws.close();
    }
}
