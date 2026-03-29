package hue.captains.singapura.tao.http.vertx.demo.pubsub.rps;

import hue.captains.singapura.tao.http.actor.Message;

/**
 * Messages that flow through a per-game topic.
 * Both the game actor and player sessions subscribe to the game topic.
 */
public sealed interface RpsGameMessage extends Message._Receive, Message._Send
        permits RpsGameMessage.PlayerReady,
                RpsGameMessage.RoundStart,
                RpsGameMessage.PlayerChoice,
                RpsGameMessage.RoundResult,
                RpsGameMessage.GameOver {

    /** Sent by a session after subscribing to the game topic. */
    record PlayerReady(String playerName) implements RpsGameMessage {}

    record RoundStart(int round) implements RpsGameMessage {}

    record PlayerChoice(String playerName, Choice choice) implements RpsGameMessage {}

    record RoundResult(int round,
                       String playerA, Choice choiceA,
                       String playerB, Choice choiceB,
                       String winner) implements RpsGameMessage {}

    record GameOver(String playerA, int scoreA,
                    String playerB, int scoreB) implements RpsGameMessage {}

    enum Choice {
        ROCK, PAPER, SCISSORS;

        public boolean beats(Choice other) {
            return switch (this) {
                case ROCK     -> other == SCISSORS;
                case PAPER    -> other == ROCK;
                case SCISSORS -> other == PAPER;
            };
        }
    }
}
