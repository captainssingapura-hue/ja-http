package hue.captains.singapura.tao.http.actor.pubsub;

import hue.captains.singapura.tao.http.actor.ActorId;
import hue.captains.singapura.tao.http.actor.Message;

/**
 * Messages understood by a {@link LeadActor}.
 * The Lead Actor is the entry point for new client connections.
 */
public sealed interface LeadMessage extends Message._Receive, Message._Send
        permits LeadMessage.NewConnection, LeadMessage.SessionEnded {

    /**
     * A new client connection request.
     * @param connectionId an identifier for the connection (e.g., WebSocket session id)
     */
    record NewConnection(String connectionId) implements LeadMessage {}

    /** Notification that a session has ended (client disconnected). */
    record SessionEnded(ActorId sessionId) implements LeadMessage {}
}
