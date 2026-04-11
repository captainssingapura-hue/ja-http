package hue.captains.singapura.tao.http.rps;

import com.fasterxml.jackson.databind.ObjectMapper;
import hue.captains.singapura.tao.http.actor.Actor;
import hue.captains.singapura.tao.http.actor.ActorAction;
import hue.captains.singapura.tao.http.actor.ActorId;
import hue.captains.singapura.tao.http.actor.ActorSystem;
import hue.captains.singapura.tao.http.actor.Message;
import hue.captains.singapura.tao.http.actor.pubsub.TopicActor;
import hue.captains.singapura.tao.http.actor.pubsub.TopicManagerMessage;
import hue.captains.singapura.tao.http.actor.pubsub.TopicMessage;
import hue.captains.singapura.tao.http.actor.pubsub.TopicPayload;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;

/**
 * Matchmaking actor for RPS games.
 * <p>
 * Subscribes to the "lobby" topic and listens for join/leave requests
 * as {@link TopicPayload} messages with JSON data:
 * <pre>
 *   {"type": "join", "name": "Alice"}
 *   {"type": "leave", "name": "Alice"}
 * </pre>
 * When two players are waiting, creates a per-game topic and game actor,
 * then publishes a match notification to the lobby topic:
 * <pre>
 *   {"type": "game_created", "gameId": "G1", "gameTopic": "game:G1", "playerA": "Alice", "playerB": "Bob"}
 * </pre>
 */
@SuppressWarnings({"rawtypes", "unchecked"})
public class LobbyActor implements Actor<Message._Receive> {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final ActorSystem actorSystem;
    private final ActorId topicManagerId;
    private final ActorId lobbyTopicId;
    private final Deque<String> waitingPlayers = new ArrayDeque<>();
    private int gameCounter = 0;

    public LobbyActor(ActorSystem actorSystem, ActorId topicManagerId, ActorId lobbyTopicId) {
        this.actorSystem = actorSystem;
        this.topicManagerId = topicManagerId;
        this.lobbyTopicId = lobbyTopicId;
    }

    @Override
    public List<ActorAction> receive(List<Message._Receive> messages) {
        var actions = new ArrayList<ActorAction>();

        for (var msg : messages) {
            if (msg instanceof TopicPayload tp && "lobby".equals(tp.topicName())) {
                actions.addAll(handleLobbyPayload(tp.data()));
            }
        }

        return actions;
    }

    @SuppressWarnings("unchecked")
    private List<ActorAction> handleLobbyPayload(String json) {
        var actions = new ArrayList<ActorAction>();
        try {
            var data = MAPPER.readValue(json, Map.class);
            var type = (String) data.get("type");

            switch (type) {
                case "join" -> {
                    var name = (String) data.get("name");
                    if (name != null && !waitingPlayers.contains(name)) {
                        waitingPlayers.add(name);
                        System.out.printf("[Lobby] %s joined. Waiting: %d%n", name, waitingPlayers.size());

                        if (waitingPlayers.size() >= 2) {
                            var playerA = waitingPlayers.poll();
                            var playerB = waitingPlayers.poll();
                            actions.addAll(createGame(playerA, playerB));
                        }
                    }
                }

                case "leave" -> {
                    var name = (String) data.get("name");
                    if (name != null) {
                        waitingPlayers.remove(name);
                        System.out.printf("[Lobby] %s left. Waiting: %d%n", name, waitingPlayers.size());
                    }
                }

                default -> {}
            }
        } catch (Exception e) {
            System.err.println("[Lobby] Failed to parse: " + e.getMessage());
        }
        return actions;
    }

    private List<ActorAction> createGame(String playerA, String playerB) {
        var actions = new ArrayList<ActorAction>();
        var gameId = "G" + (++gameCounter);
        var gameTopic = "game:" + gameId;

        // Create game topic
        var gameTopicId = ActorId.allocate(null, "topic:" + gameTopic);
        actorSystem.register(gameTopicId, new TopicActor<TopicPayload>());
        actorSystem.inject(topicManagerId,
            new TopicManagerMessage.RegisterTopic(gameTopic, gameTopicId));

        // Create game actor
        var gameActorId = ActorId.allocate(null, "rps-game:" + gameId);
        actorSystem.register(gameActorId, new RpsGameActor(gameActorId));
        actorSystem.inject(gameActorId, new RpsGameActor.RpsGameInit(
            gameId, gameTopicId, topicManagerId, playerA, playerB));

        System.out.printf("[Lobby] Matched: %s vs %s -> Game %s%n", playerA, playerB, gameId);

        // Publish match notification to lobby topic
        try {
            var payload = MAPPER.writeValueAsString(Map.of(
                "type", "game_created",
                "gameId", gameId,
                "gameTopic", gameTopic,
                "playerA", playerA,
                "playerB", playerB));
            actions.add(new ActorAction.SendMessage<>(lobbyTopicId,
                new TopicMessage.Publish<>(new TopicPayload("lobby", payload))));
        } catch (Exception e) {
            System.err.println("[Lobby] Failed to serialize game_created: " + e.getMessage());
        }

        return actions;
    }
}
