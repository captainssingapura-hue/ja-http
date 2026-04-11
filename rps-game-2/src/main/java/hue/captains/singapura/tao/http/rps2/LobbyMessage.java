package hue.captains.singapura.tao.http.rps2;

import hue.captains.singapura.tao.http.actor.ActorId;
import hue.captains.singapura.tao.http.actor.Message;

/**
 * Messages that a {@link LobbyActor} can receive.
 */
public sealed interface LobbyMessage extends Message._Receive, Message._Send
        permits LobbyMessage.Join {

    /** A player requests to join the lobby for matchmaking. */
    record Join(ActorId playerId, String playerName) implements LobbyMessage {}
}
