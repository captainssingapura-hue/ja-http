package hue.captains.singapura.tao.http.vertx.demo.pubsub;

import com.fasterxml.jackson.databind.ObjectMapper;
import hue.captains.singapura.tao.http.actor.ActorAction;
import hue.captains.singapura.tao.http.actor.ActorRef;
import hue.captains.singapura.tao.http.actor.Message;
import hue.captains.singapura.tao.http.actor.frontier.FrontierActor;
import hue.captains.singapura.tao.http.actor.pubsub.TopicManagerMessage;
import hue.captains.singapura.tao.http.actor.pubsub.TopicMessage;
import hue.captains.singapura.tao.http.vertx.demo.pubsub.rps.RpsGameMessage;
import hue.captains.singapura.tao.http.vertx.demo.pubsub.rps.RpsLobbyMessage;
import io.vertx.core.http.ServerWebSocket;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 * A FrontierActor that wraps a single WebSocket connection.
 * <p>
 * Uses {@code Message._Receive / Message._Send} as its type parameters
 * so it can receive both {@link WsMessage} (from the WebSocket client and other actors)
 * and {@link TopicManagerMessage.TopicList} (from the Topic Manager).
 * <p>
 * Wire protocol (client sends JSON text frames):
 * <pre>
 *   {"action": "list"}
 *   {"action": "subscribe", "topic": "prices"}
 *   {"action": "unsubscribe", "topic": "prices"}
 *   {"action": "publish", "topic": "prices", "payload": "..."}
 * </pre>
 * Server sends back:
 * <pre>
 *   {"type": "topics", "topics": ["prices", "news"]}
 *   {"type": "data", "topic": "prices", "payload": "..."}
 *   {"type": "subscribed", "topic": "prices"}
 *   {"type": "unsubscribed", "topic": "prices"}
 *   {"type": "error", "message": "..."}
 * </pre>
 */
public class WsSessionActor implements FrontierActor<Message._Receive, Message._Send> {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final Consumer<ActorAction.SendMessage<Message._Send>> listener;
    private final ServerWebSocket ws;
    private final ActorRef selfRef;

    private ActorRef topicManagerRef;
    private ActorRef lobbyRef;
    private String playerName;
    private final Map<String, ActorRef> knownTopics = new LinkedHashMap<>();
    private final Set<String> activeSubscriptions = new LinkedHashSet<>();

    private WsSessionActor(Consumer<ActorAction.SendMessage<Message._Send>> listener,
                           ServerWebSocket ws, ActorRef selfRef) {
        this.listener = listener;
        this.ws = ws;
        this.selfRef = selfRef;

        // Wire WebSocket events into the actor system
        ws.textMessageHandler(text ->
            listener.accept(new ActorAction.SendMessage<>(selfRef, new WsMessage.IncomingFrame(text))));

        ws.closeHandler(v ->
            listener.accept(new ActorAction.SendMessage<>(selfRef, new WsMessage.Disconnected())));
    }

    @Override
    public List<ActorAction> receive(List<Message._Receive> messages) {
        var actions = new ArrayList<ActorAction>();

        for (var msg : messages) {
            switch (msg) {
                case WsMessage.Init init -> {
                    this.topicManagerRef = init.topicManagerRef();
                    this.lobbyRef = init.lobbyRef();
                    actions.add(new ActorAction.SendMessage<>(topicManagerRef,
                        new TopicManagerMessage.QueryTopics(selfRef)));
                }

                case TopicManagerMessage.TopicList topicList -> {
                    knownTopics.putAll(topicList.topics());
                    sendToClient(Map.of(
                        "type", "topics",
                        "topics", List.copyOf(knownTopics.keySet())));
                }

                case WsMessage.IncomingFrame frame ->
                    actions.addAll(handleClientFrame(frame.text()));

                case WsMessage.TopicData data ->
                    sendToClient(Map.of(
                        "type", "data",
                        "topic", data.topicName(),
                        "payload", data.payload()));

                case RpsLobbyMessage.GameCreated game -> {
                    // Auto-subscribe to the game topic
                    knownTopics.put("game:" + game.gameId(), game.gameTopicRef());
                    activeSubscriptions.add("game:" + game.gameId());
                    actions.add(new ActorAction.SendMessage<>(game.gameTopicRef(),
                        new TopicMessage.Subscribe<>(selfRef)));
                    // Signal readiness to the game actor
                    actions.add(new ActorAction.SendMessage<>(game.gameTopicRef(),
                        new TopicMessage.Publish<>(new RpsGameMessage.PlayerReady(playerName))));
                    sendToClient(Map.of(
                        "type", "game_created",
                        "gameId", game.gameId(),
                        "opponent", game.opponentName()));
                }

                case RpsGameMessage gameMsg -> {
                    switch (gameMsg) {
                        case RpsGameMessage.RoundStart rs ->
                            sendToClient(Map.of("type", "round_start", "round", rs.round()));
                        case RpsGameMessage.RoundResult rr ->
                            sendToClient(Map.of(
                                "type", "round_result",
                                "round", rr.round(),
                                "playerA", rr.playerA(), "choiceA", rr.choiceA().name(),
                                "playerB", rr.playerB(), "choiceB", rr.choiceB().name(),
                                "winner", rr.winner()));
                        case RpsGameMessage.GameOver go ->
                            sendToClient(Map.of(
                                "type", "game_over",
                                "playerA", go.playerA(), "scoreA", go.scoreA(),
                                "playerB", go.playerB(), "scoreB", go.scoreB()));
                        case RpsGameMessage.PlayerChoice ignored -> {}
                        case RpsGameMessage.PlayerReady ignored -> {}
                    }
                }

                case WsMessage.Disconnected ignored -> {
                    for (var topicName : activeSubscriptions) {
                        var topicRef = knownTopics.get(topicName);
                        if (topicRef != null) {
                            actions.add(new ActorAction.SendMessage<>(topicRef,
                                new TopicMessage.Unsubscribe<>(selfRef)));
                        }
                    }
                    activeSubscriptions.clear();
                    if (lobbyRef != null && playerName != null) {
                        actions.add(new ActorAction.SendMessage<>(lobbyRef,
                            new RpsLobbyMessage.LeaveRequest(selfRef)));
                    }
                    actions.add(new ActorAction.SelfTerminate());
                    System.out.println("[WS] Client disconnected: " + ws.remoteAddress());
                }

                default -> { }
            }
        }

        return actions;
    }

    @SuppressWarnings("unchecked")
    private List<ActorAction> handleClientFrame(String text) {
        var actions = new ArrayList<ActorAction>();
        try {
            var json = MAPPER.readValue(text, Map.class);
            var action = (String) json.get("action");
            if (action == null) {
                sendToClient(Map.of("type", "error", "message", "Missing 'action' field"));
                return actions;
            }

            switch (action) {
                case "list" ->
                    actions.add(new ActorAction.SendMessage<>(topicManagerRef,
                        new TopicManagerMessage.QueryTopics(selfRef)));

                case "subscribe" -> {
                    var topic = (String) json.get("topic");
                    var topicRef = knownTopics.get(topic);
                    if (topicRef != null) {
                        activeSubscriptions.add(topic);
                        actions.add(new ActorAction.SendMessage<>(topicRef,
                            new TopicMessage.Subscribe<>(selfRef)));
                        sendToClient(Map.of("type", "subscribed", "topic", topic));
                    } else {
                        sendToClient(Map.of("type", "error", "message", "Unknown topic: " + topic));
                    }
                }

                case "unsubscribe" -> {
                    var topic = (String) json.get("topic");
                    var topicRef = knownTopics.get(topic);
                    if (topicRef != null && activeSubscriptions.remove(topic)) {
                        actions.add(new ActorAction.SendMessage<>(topicRef,
                            new TopicMessage.Unsubscribe<>(selfRef)));
                        sendToClient(Map.of("type", "unsubscribed", "topic", topic));
                    }
                }

                case "publish" -> {
                    var topic = (String) json.get("topic");
                    var payload = json.get("payload");
                    var topicRef = knownTopics.get(topic);
                    if (topicRef != null) {
                        var payloadStr = payload instanceof String s ? s : MAPPER.writeValueAsString(payload);
                        actions.add(new ActorAction.SendMessage<>(topicRef,
                            new TopicMessage.Publish<>(new WsMessage.TopicData(topic, payloadStr))));
                    } else {
                        sendToClient(Map.of("type", "error", "message", "Unknown topic: " + topic));
                    }
                }

                case "rps_join" -> {
                    var name = (String) json.get("name");
                    if (name == null || name.isBlank()) {
                        sendToClient(Map.of("type", "error", "message", "Missing 'name' field"));
                    } else if (lobbyRef == null) {
                        sendToClient(Map.of("type", "error", "message", "RPS lobby not available"));
                    } else {
                        this.playerName = name;
                        actions.add(new ActorAction.SendMessage<>(lobbyRef,
                            new RpsLobbyMessage.JoinRequest(selfRef, name)));
                        sendToClient(Map.of("type", "rps_waiting", "name", name));
                    }
                }

                case "rps_move" -> {
                    var gameId = (String) json.get("gameId");
                    var move = (String) json.get("move");
                    var gameTopicRef = knownTopics.get("game:" + gameId);
                    if (gameTopicRef == null) {
                        sendToClient(Map.of("type", "error", "message", "Unknown game: " + gameId));
                    } else if (playerName == null) {
                        sendToClient(Map.of("type", "error", "message", "Not in a game"));
                    } else {
                        try {
                            var choice = RpsGameMessage.Choice.valueOf(move.toUpperCase());
                            actions.add(new ActorAction.SendMessage<>(gameTopicRef,
                                new TopicMessage.Publish<>(new RpsGameMessage.PlayerChoice(playerName, choice))));
                        } catch (IllegalArgumentException e) {
                            sendToClient(Map.of("type", "error", "message", "Invalid move: " + move + ". Use ROCK, PAPER, or SCISSORS"));
                        }
                    }
                }

                default ->
                    sendToClient(Map.of("type", "error", "message", "Unknown action: " + action));
            }
        } catch (Exception e) {
            sendToClient(Map.of("type", "error", "message", "Invalid JSON: " + e.getMessage()));
        }
        return actions;
    }

    private void sendToClient(Map<String, Object> message) {
        try {
            ws.writeTextMessage(MAPPER.writeValueAsString(message));
        } catch (Exception e) {
            System.err.println("[WS] Failed to send to client: " + e.getMessage());
        }
    }

    public static FrontierActor._Constructor<Message._Receive, Message._Send, WsSessionActor>
    constructor(ServerWebSocket ws, ActorRef selfRef) {
        return listener -> new WsSessionActor(listener, ws, selfRef);
    }
}
