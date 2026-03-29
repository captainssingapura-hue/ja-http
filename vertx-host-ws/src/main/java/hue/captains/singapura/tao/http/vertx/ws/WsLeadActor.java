package hue.captains.singapura.tao.http.vertx.ws;

import hue.captains.singapura.tao.http.actor.ActorAction;
import hue.captains.singapura.tao.http.actor.ActorRef;
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
 * {@link WsMessage.Init} with the topic manager ref.
 * <p>
 * Application-agnostic: knows nothing about what topics exist or what messages flow through them.
 */
public class WsLeadActor implements FrontierActor<WsMessage, WsMessage> {

    private final Consumer<ActorAction.SendMessage<WsMessage>> listener;
    private final ActorSystem actorSystem;
    private final ActorRef topicManagerRef;
    private final Set<ActorRef> activeSessions = new LinkedHashSet<>();

    private WsLeadActor(Consumer<ActorAction.SendMessage<WsMessage>> listener,
                        ActorSystem actorSystem, ActorRef topicManagerRef) {
        this.listener = listener;
        this.actorSystem = actorSystem;
        this.topicManagerRef = topicManagerRef;
    }

    /**
     * Called from the Vert.x WebSocket connect handler (external event).
     * Creates and registers a new session actor for this connection.
     */
    public void onNewConnection(ServerWebSocket ws) {
        var sessionRef = actorSystem.allocateRef("ws-session");
        actorSystem.registerFrontier(sessionRef,
            WsSessionActor.constructor(ws, sessionRef));
        activeSessions.add(sessionRef);

        actorSystem.inject(sessionRef, new WsMessage.Init(topicManagerRef));

        System.out.println("[WS] New connection from " + ws.remoteAddress() + " -> " + sessionRef);
    }

    @Override
    public List<ActorAction> receive(List<WsMessage> messages) {
        return List.of();
    }

    public static FrontierActor._Constructor<WsMessage, WsMessage, WsLeadActor>
    constructor(ActorSystem actorSystem, ActorRef topicManagerRef) {
        return listener -> new WsLeadActor(listener, actorSystem, topicManagerRef);
    }
}
