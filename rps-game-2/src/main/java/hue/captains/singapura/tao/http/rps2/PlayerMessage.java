package hue.captains.singapura.tao.http.rps2;

import hue.captains.singapura.tao.http.actor.ActorId;
import hue.captains.singapura.tao.http.actor.Message;

/**
 * Messages that a {@link PlayerActor} can receive.
 * Sent by the game actor to notify players of game events.
 */
public sealed interface PlayerMessage extends Message._Receive, Message._Send
        permits PlayerMessage.GameAssigned,
                PlayerMessage.RoundStart,
                PlayerMessage.RoundResult,
                PlayerMessage.GameOver {

    /** Tells the player which game actor to communicate with. */
    record GameAssigned(ActorId gameActorId, String opponentName) implements PlayerMessage {}

    /** Signals the start of a new round. */
    record RoundStart(int round) implements PlayerMessage {}

    /** Result of a completed round. */
    record RoundResult(int round,
                       String playerA, Choice choiceA,
                       String playerB, Choice choiceB,
                       String winner) implements PlayerMessage {}

    /** The game has ended. */
    record GameOver(String winner,
                    String playerA, int scoreA,
                    String playerB, int scoreB) implements PlayerMessage {}
}
