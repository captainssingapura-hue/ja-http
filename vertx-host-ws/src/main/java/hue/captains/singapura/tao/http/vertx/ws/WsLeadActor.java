package hue.captains.singapura.tao.http.vertx.ws;

import hue.captains.singapura.tao.http.actor.ActorAction;
import hue.captains.singapura.tao.http.actor.ActorId;
import hue.captains.singapura.tao.http.actor.ActorSystem;
import hue.captains.singapura.tao.http.actor.frontier.FrontierActor;
import io.vertx.core.http.ServerWebSocket;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/**
 * The entry point for WebSocket connections.
 * <p>
 * As a FrontierActor, it bridges Vert.x WebSocket upgrade events into the actor system.
 * For each new connection, it registers a {@link WsSessionActor} and sends it an
 * {@link WsMessage.Init} with the topic manager id.
 * <p>
 * Application-agnostic: knows nothing about what topics exist or what messages flow through them.
 */
public class WsLeadActor implements FrontierActor<WsMessage, WsMessage> {

    private final Consumer<ActorAction.SendMessage<WsMessage>> listener;
    private final ActorSystem actorSystem;
    private final ActorId topicManagerId;
    private final Set<ActorId> activeSessions = new LinkedHashSet<>();

    private WsLeadActor(Consumer<ActorAction.SendMessage<WsMessage>> listener,
                        ActorSystem actorSystem, ActorId topicManagerId) {
        this.listener = listener;
        this.actorSystem = actorSystem;
        this.topicManagerId = topicManagerId;
    }

    /**
     * Called from the Vert.x WebSocket connect handler (external event).
     * Creates and registers a new session actor for this connection.
     */
    public void onNewConnection(ServerWebSocket ws) {
        var sessionId = actorSystem.allocateId("ws-session");
        actorSystem.registerFrontier(sessionId,
            WsSessionActor.constructor(ws, sessionId));
        activeSessions.add(sessionId);

        actorSystem.inject(sessionId, new WsMessage.Init(topicManagerId));

        System.out.println("[WS] New connection from " + ws.remoteAddress() + " -> " + sessionId);
    }

    @Override
    public List<ActorAction> receive(List<WsMessage> messages) {
        return List.of();
    }

    public static FrontierActor._Constructor<WsMessage, WsMessage, WsLeadActor>
    constructor(ActorSystem actorSystem, ActorId topicManagerId) {
        return listener -> new WsLeadActor(listener, actorSystem, topicManagerId);
    }
}
