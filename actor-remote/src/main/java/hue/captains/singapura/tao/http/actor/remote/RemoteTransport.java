package hue.captains.singapura.tao.http.actor.remote;

import java.util.function.Consumer;

/**
 * A bidirectional string channel to a single remote endpoint.
 * The transport is already connected when handed to the proxy actor.
 */
public interface RemoteTransport {

    void send(String data);

    void onReceive(Consumer<String> handler);

    void onClose(Runnable handler);

    void close();
}
