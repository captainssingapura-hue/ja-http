package hue.captains.singapura.tao.http.rps2;

import hue.captains.singapura.tao.http.actor.ActorId;
import hue.captains.singapura.tao.http.actor.Message;

/**
 * Messages that a {@link GameActor} can receive.
 * Sent by the lobby (Init) or by players (PlayerReady, PlayerMove).
 */
public sealed interface GameMessage extends Message._Receive, Message._Send
        permits GameMessage.Init,
                GameMessage.PlayerReady,
                GameMessage.PlayerMove {

    /** Initialization message sent by the lobby after matching two players. */
    record Init(ActorId playerA, String playerAName,
                ActorId playerB, String playerBName) implements GameMessage {}

    /** Sent by a player to indicate they are ready to start. */
    record PlayerReady(ActorId playerId, String playerName) implements GameMessage {}

    /** Sent by a player with their move for the current round. */
    record PlayerMove(ActorId playerId, String playerName, Choice choice) implements GameMessage {}
}
