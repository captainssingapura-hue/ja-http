package hue.captains.singapura.tao.http.vertx.demo.pubsub;

import hue.captains.singapura.tao.http.actor.ActorAction;
import hue.captains.singapura.tao.http.actor.ActorRef;
import hue.captains.singapura.tao.http.actor.Message;
import hue.captains.singapura.tao.http.actor.frontier.FrontierActor;
import io.vertx.core.http.ServerWebSocket;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/**
 * The entry point for WebSocket connections.
 * <p>
 * As a FrontierActor, it bridges the Vert.x WebSocket upgrade event into the actor system.
 * For each new connection, it registers a {@link WsSessionActor} and sends it an Init message.
 * <p>
 * The Lead Actor holds a reference to the {@link VertxActorSystem} because session creation
 * requires registering a new frontier actor (which needs the WebSocket handle and actor system
 * wiring that cannot be passed through {@code SpawnSubActor}).
 */
public class WsLeadActor implements FrontierActor<WsMessage, WsMessage> {

    private final Consumer<ActorAction.SendMessage<WsMessage>> listener;
    private final VertxActorSystem actorSystem;
    private final ActorRef topicManagerRef;
    private final ActorRef lobbyRef;
    private final Set<ActorRef> activeSessions = new LinkedHashSet<>();

    private WsLeadActor(Consumer<ActorAction.SendMessage<WsMessage>> listener,
                        VertxActorSystem actorSystem, ActorRef topicManagerRef,
                        ActorRef lobbyRef) {
        this.listener = listener;
        this.actorSystem = actorSystem;
        this.topicManagerRef = topicManagerRef;
        this.lobbyRef = lobbyRef;
    }

    /**
     * Called from the Vert.x WebSocket connect handler (external event).
     * Creates and registers a new session actor for this connection.
     */
    public void onNewConnection(ServerWebSocket ws) {
        var sessionRef = actorSystem.allocateRef("ws-session");
        var sessionActor = actorSystem.registerFrontier(sessionRef,
            WsSessionActor.constructor(ws, sessionRef));
        activeSessions.add(sessionRef);

        // Inject Init message to the new session
        actorSystem.inject(sessionRef, new WsMessage.Init(topicManagerRef, lobbyRef));

        System.out.println("[WS] New connection from " + ws.remoteAddress() + " -> " + sessionRef);
    }

    @Override
    public List<ActorAction> receive(List<WsMessage> messages) {
        // The Lead Actor doesn't receive messages in the current design.
        // Connection events are handled directly via onNewConnection().
        return List.of();
    }

    public static FrontierActor._Constructor<WsMessage, WsMessage, WsLeadActor>
    constructor(VertxActorSystem actorSystem, ActorRef topicManagerRef, ActorRef lobbyRef) {
        return listener -> new WsLeadActor(listener, actorSystem, topicManagerRef, lobbyRef);
    }
}
