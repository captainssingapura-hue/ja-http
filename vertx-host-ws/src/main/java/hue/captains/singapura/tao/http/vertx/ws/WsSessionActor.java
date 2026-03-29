package hue.captains.singapura.tao.http.vertx.ws;

import com.fasterxml.jackson.databind.ObjectMapper;
import hue.captains.singapura.tao.http.actor.ActorAction;
import hue.captains.singapura.tao.http.actor.ActorRef;
import hue.captains.singapura.tao.http.actor.Message;
import hue.captains.singapura.tao.http.actor.frontier.FrontierActor;
import hue.captains.singapura.tao.http.actor.pubsub.TopicManagerMessage;
import hue.captains.singapura.tao.http.actor.pubsub.TopicMessage;
import hue.captains.singapura.tao.http.actor.pubsub.TopicPayload;
import io.vertx.core.http.ServerWebSocket;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 * A generic FrontierActor that bridges a single WebSocket connection to the pub-sub system.
 * <p>
 * Knows nothing about application protocols (RPS, chat, etc.). It translates between
 * JSON WebSocket frames and pub-sub actor messages:
 * <p>
 * Client sends JSON text frames:
 * <pre>
 *   {"action": "list"}
 *   {"action": "subscribe", "topic": "prices"}
 *   {"action": "unsubscribe", "topic": "prices"}
 *   {"action": "publish", "topic": "prices", "payload": ...}
 * </pre>
 * Server sends back:
 * <pre>
 *   {"type": "topics", "topics": ["prices", "news"]}
 *   {"type": "data", "topic": "prices", "payload": ...}
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

                case TopicPayload tp -> {
                    try {
                        var payloadNode = MAPPER.readTree(tp.data());
                        sendToClient(Map.of(
                            "type", "data",
                            "topic", tp.topicName(),
                            "payload", payloadNode));
                    } catch (Exception e) {
                        // Payload is not valid JSON — send as raw string
                        sendToClient(Map.of(
                            "type", "data",
                            "topic", tp.topicName(),
                            "payload", tp.data()));
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
                            new TopicMessage.Publish<>(new TopicPayload(topic, payloadStr))));
                    } else {
                        sendToClient(Map.of("type", "error", "message", "Unknown topic: " + topic));
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
