package hue.captains.singapura.tao.http.vertx.demo.pubsub.rps;

import hue.captains.singapura.tao.http.actor.Actor;
import hue.captains.singapura.tao.http.actor.ActorAction;
import hue.captains.singapura.tao.http.actor.ActorRef;
import hue.captains.singapura.tao.http.actor.Message;
import hue.captains.singapura.tao.http.actor.pubsub.TopicManagerMessage;
import hue.captains.singapura.tao.http.actor.pubsub.TopicMessage;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Manages a single RPS game between two players. Best-of-3 (first to 2 wins).
 * <p>
 * Subscribes to a per-game topic. Publishes round starts and results to the topic.
 * Receives player choices via the same topic. Ignores its own published messages.
 */
public class RpsGameActor implements Actor<Message._Receive, Message._Send> {

    private static final int WINS_NEEDED = 2;

    private final ActorRef selfRef;

    // Set on Init
    private String gameId;
    private ActorRef gameTopicRef;
    private ActorRef topicManagerRef;
    private String playerAName;
    private String playerBName;

    private int readyCount = 0;
    private int round = 0;
    private int scoreA = 0;
    private int scoreB = 0;
    private final Map<String, RpsGameMessage.Choice> pendingChoices = new LinkedHashMap<>();

    public RpsGameActor(ActorRef selfRef) {
        this.selfRef = selfRef;
    }

    @Override
    public List<ActorAction> receive(List<Message._Receive> messages) {
        var actions = new ArrayList<ActorAction>();

        for (var msg : messages) {
            switch (msg) {
                case RpsGameInit init -> {
                    this.gameId = init.gameId();
                    this.gameTopicRef = init.gameTopicRef();
                    this.topicManagerRef = init.topicManagerRef();
                    this.playerAName = init.playerAName();
                    this.playerBName = init.playerBName();

                    System.out.printf("[Game %s] Created: %s vs %s (waiting for players)%n",
                        gameId, playerAName, playerBName);

                    // Subscribe to the game topic; round starts when both players are ready
                    actions.add(new ActorAction.SendMessage<>(gameTopicRef,
                        new TopicMessage.Subscribe<>(selfRef)));
                }

                case RpsGameMessage.PlayerReady ready -> {
                    readyCount++;
                    System.out.printf("[Game %s] %s ready (%d/2)%n", gameId, ready.playerName(), readyCount);
                    if (readyCount == 2) {
                        round = 1;
                        System.out.printf("[Game %s] Both players ready — starting!%n", gameId);
                        actions.add(new ActorAction.SendMessage<>(gameTopicRef,
                            new TopicMessage.Publish<>(new RpsGameMessage.RoundStart(round))));
                    }
                }

                case RpsGameMessage.PlayerChoice choice -> {
                    // Only process if it's a known player and for the current round
                    if (!choice.playerName().equals(playerAName) && !choice.playerName().equals(playerBName)) {
                        break;
                    }
                    pendingChoices.put(choice.playerName(), choice.choice());
                    if (pendingChoices.size() == 2) {
                        actions.addAll(resolveRound());
                    }
                }

                // Ignore messages we published ourselves
                case RpsGameMessage.RoundStart ignored -> {}
                case RpsGameMessage.RoundResult ignored -> {}
                case RpsGameMessage.GameOver ignored -> {}

                default -> {}
            }
        }

        return actions;
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

        System.out.printf("[Game %s] Round %d: %s=%s %s=%s → %s%n",
            gameId, round, playerAName, choiceA, playerBName, choiceB, winner);

        // Publish round result
        actions.add(new ActorAction.SendMessage<>(gameTopicRef,
            new TopicMessage.Publish<>(new RpsGameMessage.RoundResult(
                round, playerAName, choiceA, playerBName, choiceB, winner))));

        // Check for game over
        if (scoreA >= WINS_NEEDED || scoreB >= WINS_NEEDED) {
            System.out.printf("[Game %s] Game over: %s %d - %s %d%n",
                gameId, playerAName, scoreA, playerBName, scoreB);

            actions.add(new ActorAction.SendMessage<>(gameTopicRef,
                new TopicMessage.Publish<>(new RpsGameMessage.GameOver(
                    playerAName, scoreA, playerBName, scoreB))));

            // Cleanup: unsubscribe, retire topic, self-terminate
            actions.add(new ActorAction.SendMessage<>(gameTopicRef,
                new TopicMessage.Unsubscribe<>(selfRef)));
            actions.add(new ActorAction.SendMessage<>(topicManagerRef,
                new TopicManagerMessage.RetireTopic("game:" + gameId)));
            actions.add(new ActorAction.SelfTerminate());
        } else {
            // Next round
            round++;
            actions.add(new ActorAction.SendMessage<>(gameTopicRef,
                new TopicMessage.Publish<>(new RpsGameMessage.RoundStart(round))));
        }

        return actions;
    }

    /** Initialization message sent by the LobbyActor after creating the game. */
    public record RpsGameInit(
            String gameId,
            ActorRef gameTopicRef,
            ActorRef topicManagerRef,
            String playerAName,
            String playerBName
    ) implements Message._Receive, Message._Send {}
}
