package hue.captains.singapura.tao.http.vertx.demo.pubsub.rps;

import hue.captains.singapura.tao.http.actor.ActorRef;
import hue.captains.singapura.tao.http.actor.Message;

/**
 * Messages for interacting with the {@link LobbyActor}.
 */
public sealed interface RpsLobbyMessage extends Message._Receive, Message._Send
        permits RpsLobbyMessage.JoinRequest,
                RpsLobbyMessage.LeaveRequest,
                RpsLobbyMessage.GameCreated {

    /** A player wants to join the lobby. Sent by the session actor. */
    record JoinRequest(ActorRef sessionRef, String playerName) implements RpsLobbyMessage {}

    /** A player is leaving the lobby (disconnected before matching). */
    record LeaveRequest(ActorRef sessionRef) implements RpsLobbyMessage {}

    /** Sent by the lobby to both matched sessions with game details. */
    record GameCreated(String gameId, ActorRef gameTopicRef, String opponentName)
            implements RpsLobbyMessage {}
}
