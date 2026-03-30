package hue.captains.singapura.tao.http.rps;

import com.fasterxml.jackson.databind.ObjectMapper;
import hue.captains.singapura.tao.http.actor.Actor;
import hue.captains.singapura.tao.http.actor.ActorAction;
import hue.captains.singapura.tao.http.actor.ActorId;
import hue.captains.singapura.tao.http.actor.Message;
import hue.captains.singapura.tao.http.actor.pubsub.TopicManagerMessage;
import hue.captains.singapura.tao.http.actor.pubsub.TopicMessage;
import hue.captains.singapura.tao.http.actor.pubsub.TopicPayload;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Manages a single RPS game between two players. Best-of-3 (first to 2 wins).
 * <p>
 * Subscribes to a per-game topic. All communication flows through {@link TopicPayload}
 * messages with JSON data:
 * <pre>
 *   Incoming: {"type": "ready", "name": "Alice"}
 *             {"type": "move", "name": "Alice", "move": "ROCK"}
 *   Outgoing: {"type": "round_start", "round": 1}
 *             {"type": "round_result", ...}
 *             {"type": "game_over", ...}
 * </pre>
 */
public class RpsGameActor implements Actor<Message._Receive, Message._Send> {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final int WINS_NEEDED = 10;

    private final ActorId selfId;

    // Set on Init
    private String gameId;
    private String gameTopic;
    private ActorId gameTopicId;
    private ActorId topicManagerId;
    private String playerAName;
    private String playerBName;

    private int readyCount = 0;
    private int round = 0;
    private int scoreA = 0;
    private int scoreB = 0;
    private final Map<String, Choice> pendingChoices = new LinkedHashMap<>();

    public RpsGameActor(ActorId selfId) {
        this.selfId = selfId;
    }

    @Override
    public List<ActorAction> receive(List<Message._Receive> messages) {
        var actions = new ArrayList<ActorAction>();

        for (var msg : messages) {
            switch (msg) {
                case RpsGameInit init -> {
                    this.gameId = init.gameId();
                    this.gameTopicId = init.gameTopicId();
                    this.topicManagerId = init.topicManagerId();
                    this.playerAName = init.playerAName();
                    this.playerBName = init.playerBName();
                    this.gameTopic = "game:" + gameId;

                    System.out.printf("[Game %s] Created: %s vs %s (waiting for players)%n",
                        gameId, playerAName, playerBName);

                    // Subscribe to the game topic; round starts when both players are ready
                    actions.add(new ActorAction.SendMessage<>(gameTopicId,
                        new TopicMessage.Subscribe<>(selfId)));
                }

                case TopicPayload tp -> {
                    if (gameTopic != null && gameTopic.equals(tp.topicName())) {
                        actions.addAll(handleGamePayload(tp.data()));
                    }
                }

                default -> {}
            }
        }

        return actions;
    }

    @SuppressWarnings("unchecked")
    private List<ActorAction> handleGamePayload(String json) {
        var actions = new ArrayList<ActorAction>();
        try {
            var data = MAPPER.readValue(json, Map.class);
            var type = (String) data.get("type");

            switch (type) {
                case "ready" -> {
                    readyCount++;
                    var name = (String) data.get("name");
                    System.out.printf("[Game %s] %s ready (%d/2)%n", gameId, name, readyCount);
                    if (readyCount == 2) {
                        round = 1;
                        System.out.printf("[Game %s] Both players ready — starting!%n", gameId);
                        actions.add(publish(Map.of("type", "round_start", "round", round)));
                    }
                }

                case "move" -> {
                    var name = (String) data.get("name");
                    var move = (String) data.get("move");
                    if (name == null || move == null) break;
                    if (!name.equals(playerAName) && !name.equals(playerBName)) break;

                    try {
                        var choice = Choice.valueOf(move.toUpperCase());
                        pendingChoices.put(name, choice);
                        if (pendingChoices.size() == 2) {
                            actions.addAll(resolveRound());
                        }
                    } catch (IllegalArgumentException ignored) {}
                }

                // Ignore messages we published ourselves
                case "round_start", "round_result", "game_over" -> {}

                default -> {}
            }
        } catch (Exception e) {
            System.err.printf("[Game %s] Failed to parse: %s%n", gameId, e.getMessage());
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

        System.out.printf("[Game %s] Round %d: %s=%s %s=%s -> %s%n",
            gameId, round, playerAName, choiceA, playerBName, choiceB, winner);

        actions.add(publish(Map.of(
            "type", "round_result",
            "round", round,
            "playerA", playerAName, "choiceA", choiceA.name(),
            "playerB", playerBName, "choiceB", choiceB.name(),
            "winner", winner)));

        if (scoreA >= WINS_NEEDED || scoreB >= WINS_NEEDED) {
            System.out.printf("[Game %s] Game over: %s %d - %s %d%n",
                gameId, playerAName, scoreA, playerBName, scoreB);

            actions.add(publish(Map.of(
                "type", "game_over",
                "playerA", playerAName, "scoreA", scoreA,
                "playerB", playerBName, "scoreB", scoreB)));

            // Cleanup
            actions.add(new ActorAction.SendMessage<>(gameTopicId,
                new TopicMessage.Unsubscribe<>(selfId)));
            actions.add(new ActorAction.SendMessage<>(topicManagerId,
                new TopicManagerMessage.RetireTopic(gameTopic)));
            actions.add(new ActorAction.SelfTerminate());
        } else {
            round++;
            actions.add(publish(Map.of("type", "round_start", "round", round)));
        }

        return actions;
    }

    private ActorAction publish(Map<String, Object> data) {
        try {
            var json = MAPPER.writeValueAsString(data);
            return new ActorAction.SendMessage<>(gameTopicId,
                new TopicMessage.Publish<>(new TopicPayload(gameTopic, json)));
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize game message", e);
        }
    }

    // ---- Choice enum ----

    public enum Choice {
        ROCK, PAPER, SCISSORS;

        public boolean beats(Choice other) {
            return switch (this) {
                case ROCK     -> other == SCISSORS;
                case PAPER    -> other == ROCK;
                case SCISSORS -> other == PAPER;
            };
        }
    }

    /** Initialization message sent by the LobbyActor after creating the game. */
    public record RpsGameInit(
            String gameId,
            ActorId gameTopicId,
            ActorId topicManagerId,
            String playerAName,
            String playerBName
    ) implements Message._Receive, Message._Send {}
}
