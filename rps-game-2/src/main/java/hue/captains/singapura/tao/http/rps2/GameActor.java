package hue.captains.singapura.tao.http.rps2;

import hue.captains.singapura.tao.http.actor.Actor;
import hue.captains.singapura.tao.http.actor.ActorAction;
import hue.captains.singapura.tao.http.actor.ActorId;
import hue.captains.singapura.tao.http.actor.ActorFactory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Manages a single RPS game between two players. First to {@value WINS_NEEDED} wins.
 * <p>
 * Receives {@link GameMessage} from players, sends {@link PlayerMessage} back.
 */
@SuppressWarnings({"rawtypes", "unchecked"})
public class GameActor implements Actor<GameMessage> {

    public static final Actor._TypeRef<GameMessage, GameActor> ATR = new Actor._TypeRef<>() {};

    private static final int WINS_NEEDED = 3;

    private final ActorId selfId;

    private ActorId playerAId;
    private ActorId playerBId;
    private String playerAName;
    private String playerBName;

    private int readyCount = 0;
    private int round = 0;
    private int scoreA = 0;
    private int scoreB = 0;
    private final Map<String, Choice> pendingChoices = new LinkedHashMap<>();

    public GameActor(ActorId selfId) {
        this.selfId = selfId;
    }

    @Override
    public List<ActorAction> receive(List<GameMessage> messages) {
        var actions = new ArrayList<ActorAction>();

        for (var msg : messages) {
            switch (msg) {
                case GameMessage.Init init -> {
                    this.playerAId = init.playerA();
                    this.playerAName = init.playerAName();
                    this.playerBId = init.playerB();
                    this.playerBName = init.playerBName();

                    System.out.printf("[Game] Created: %s vs %s%n", playerAName, playerBName);

                    // Notify both players of their assignment
                    actions.add(new ActorAction.SendMessage<>(playerAId,
                            new PlayerMessage.GameAssigned(selfId, playerBName)));
                    actions.add(new ActorAction.SendMessage<>(playerBId,
                            new PlayerMessage.GameAssigned(selfId, playerAName)));
                }

                case GameMessage.PlayerReady ready -> {
                    readyCount++;
                    System.out.printf("[Game] %s ready (%d/2)%n", ready.playerName(), readyCount);
                    if (readyCount == 2) {
                        round = 1;
                        System.out.printf("[Game] Both players ready — starting!%n");
                        actions.addAll(broadcastRoundStart());
                    }
                }

                case GameMessage.PlayerMove move -> {
                    pendingChoices.put(move.playerName(), move.choice());
                    if (pendingChoices.size() == 2) {
                        actions.addAll(resolveRound());
                    }
                }
            }
        }

        return actions;
    }

    private List<ActorAction> broadcastRoundStart() {
        var roundStart = new PlayerMessage.RoundStart(round);
        return List.of(
                new ActorAction.SendMessage<>(playerAId, roundStart),
                new ActorAction.SendMessage<>(playerBId, roundStart));
    }

    private List<ActorAction> resolveRound() {
        var actions = new ArrayList<ActorAction>();

        var choiceA = pendingChoices.get(playerAName);
        var choiceB = pendingChoices.get(playerBName);
        pendingChoices.clear();

        String winner;
        if (choiceA.beats(choiceB)) {
            scoreA++;
            winner = playerAName;
        } else if (choiceB.beats(choiceA)) {
            scoreB++;
            winner = playerBName;
        } else {
            winner = "draw";
        }

        System.out.printf("[Game] Round %d: %s=%s %s=%s -> %s%n",
                round, playerAName, choiceA, playerBName, choiceB, winner);

        var result = new PlayerMessage.RoundResult(
                round, playerAName, choiceA, playerBName, choiceB, winner);
        actions.add(new ActorAction.SendMessage<>(playerAId, result));
        actions.add(new ActorAction.SendMessage<>(playerBId, result));

        if (scoreA >= WINS_NEEDED || scoreB >= WINS_NEEDED) {
            var gameWinner = scoreA >= WINS_NEEDED ? playerAName : playerBName;
            System.out.printf("[Game] Game over! %s wins (%d-%d)%n", gameWinner, scoreA, scoreB);

            var gameOver = new PlayerMessage.GameOver(
                    gameWinner, playerAName, scoreA, playerBName, scoreB);
            actions.add(new ActorAction.SendMessage<>(playerAId, gameOver));
            actions.add(new ActorAction.SendMessage<>(playerBId, gameOver));
            actions.add(new ActorAction.SelfTerminate());
        } else {
            round++;
            actions.addAll(broadcastRoundStart());
        }

        return actions;
    }

    public static ActorFactory<GameMessage, GameActor> factory() {
        return GameActor::new;
    }
}
