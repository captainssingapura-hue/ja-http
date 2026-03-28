package hue.captains.singapura.tao.http.actor.demo.rps;

import hue.captains.singapura.tao.http.actor.ActorRef;
import hue.captains.singapura.tao.http.actor.Message;

public sealed interface RpsMessage extends Message, Message._Receive, Message._Send
        permits RpsMessage.Tick,
                RpsMessage.AssignId,
                RpsMessage.PlayerReady,
                RpsMessage.StartRound,
                RpsMessage.PlayerChoice,
                RpsMessage.Shutdown {

    /** External clock tick delivered to the GameActor. */
    record Tick() implements RpsMessage {}

    /** Initial message sent to a newly-spawned player to assign its identity. */
    record AssignId(String playerId, ActorRef gameRef) implements RpsMessage {}

    /** Sent by a player back to the game actor after it has been assigned an id. */
    record PlayerReady(String playerId, String uuid, ActorRef playerRef) implements RpsMessage {}

    /** Sent by the game actor to both players to start a round. */
    record StartRound(int roundId) implements RpsMessage {}

    /** A player's choice for a given round. */
    record PlayerChoice(String playerId, int roundId, Choice choice) implements RpsMessage {}

    /** Sent by the game actor to players when the game is over. */
    record Shutdown() implements RpsMessage {}

    enum Choice {
        PAPER, SCISSORS, STONE;

        /** Returns true if {@code this} beats {@code other}. */
        public boolean beats(Choice other) {
            return switch (this) {
                case PAPER    -> other == STONE;
                case SCISSORS -> other == PAPER;
                case STONE    -> other == SCISSORS;
            };
        }
    }
}
