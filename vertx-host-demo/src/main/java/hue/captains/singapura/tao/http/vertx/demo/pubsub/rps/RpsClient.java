package hue.captains.singapura.tao.http.vertx.demo.pubsub.rps;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.vertx.core.Vertx;
import io.vertx.core.http.WebSocket;

import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Simple WebSocket client that plays RPS using only generic pub-sub commands.
 * <p>
 * Flow:
 * <ol>
 *   <li>Connect, wait for initial topic list</li>
 *   <li>Subscribe to "lobby" topic</li>
 *   <li>Publish join request to "lobby"</li>
 *   <li>Wait for game_created from lobby topic</li>
 *   <li>Refresh topic list, subscribe to game topic, publish ready</li>
 *   <li>On round_start, publish a random move</li>
 *   <li>On game_over, close</li>
 * </ol>
 * Usage: {@code RpsClient <name>}
 */
public class RpsClient {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String[] MOVES = {"ROCK", "PAPER", "SCISSORS"};

    private enum State { CONNECTING, JOINING, WAITING, SUBSCRIBING_GAME, PLAYING }

    private final String name;
    private final Vertx vertx;
    private State state = State.CONNECTING;
    private String gameId;
    private String gameTopic;

    private RpsClient(String name, Vertx vertx) {
        this.name = name;
        this.vertx = vertx;
    }

    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: RpsClient <name>");
            System.exit(1);
        }

        var name = args[0];
        var vertx = Vertx.vertx();
        var client = new RpsClient(name, vertx);

        vertx.createHttpClient()
            .webSocket(8081, "localhost", "/pubsub")
            .onSuccess(client::onConnected)
            .onFailure(err -> {
                System.err.printf("[%s] Failed to connect: %s%n", name, err.getMessage());
                vertx.close();
            });
    }

    private void onConnected(WebSocket ws) {
        System.out.printf("[%s] Connected.%n", name);

        ws.textMessageHandler(text -> onMessage(ws, text));
        ws.closeHandler(v -> {
            System.out.printf("[%s] Connection closed.%n", name);
            vertx.close();
        });

        // Wait for initial topic list (sent by server on connect), then join lobby
    }

    @SuppressWarnings("unchecked")
    private void onMessage(WebSocket ws, String text) {
        try {
            var json = MAPPER.readValue(text, Map.class);
            var type = (String) json.get("type");

            switch (type) {
                case "topics" -> {
                    var topics = (java.util.List<String>) json.get("topics");
                    if (state == State.CONNECTING) {
                        // Initial topic list — subscribe to lobby and join
                        state = State.JOINING;
                        send(ws, Map.of("action", "subscribe", "topic", "lobby"));
                        send(ws, Map.of("action", "publish", "topic", "lobby",
                            "payload", Map.of("type", "join", "name", name)));
                        System.out.printf("[%s] Joined lobby.%n", name);
                        state = State.WAITING;
                    } else if (state == State.SUBSCRIBING_GAME && topics.contains(gameTopic)) {
                        // Game topic now visible — subscribe and signal ready
                        send(ws, Map.of("action", "subscribe", "topic", gameTopic));
                        send(ws, Map.of("action", "publish", "topic", gameTopic,
                            "payload", Map.of("type", "ready", "name", name)));
                        state = State.PLAYING;
                    } else if (state == State.SUBSCRIBING_GAME) {
                        // Game topic not yet visible — retry after a short delay
                        vertx.setTimer(100, id -> send(ws, Map.of("action", "list")));
                    }
                }

                case "subscribed" -> { }

                case "data" -> {
                    var topic = (String) json.get("topic");
                    var payload = json.get("payload");
                    if (payload instanceof Map<?,?> payloadMap) {
                        handleTopicData(ws, topic, (Map<String, Object>) payloadMap);
                    }
                }

                case "error" ->
                    System.err.printf("[%s] Error: %s%n", name, json.get("message"));

                default -> { }
            }
        } catch (Exception e) {
            System.err.printf("[%s] Failed to parse: %s%n", name, e.getMessage());
        }
    }

    private void handleTopicData(WebSocket ws, String topic, Map<String, Object> payload) {
        var payloadType = (String) payload.get("type");
        if (payloadType == null) return;

        switch (payloadType) {
            case "game_created" -> {
                var playerA = (String) payload.get("playerA");
                var playerB = (String) payload.get("playerB");
                // Only react if we're one of the matched players
                if (!name.equals(playerA) && !name.equals(playerB)) break;

                this.gameId = (String) payload.get("gameId");
                this.gameTopic = (String) payload.get("gameTopic");
                var opponent = name.equals(playerA) ? playerB : playerA;
                System.out.printf("[%s] Matched against %s (game %s)%n", name, opponent, gameId);

                // Refresh topic list to discover the new game topic
                state = State.SUBSCRIBING_GAME;
                send(ws, Map.of("action", "list"));
            }

            case "round_start" -> {
                if (!topic.equals(gameTopic)) break;
                var move = MOVES[ThreadLocalRandom.current().nextInt(MOVES.length)];
                System.out.printf("[%s] Round %s — playing %s%n", name, payload.get("round"), move);
                send(ws, Map.of("action", "publish", "topic", gameTopic,
                    "payload", Map.of("type", "move", "name", name, "move", move)));
            }

            case "round_result" -> {
                if (!topic.equals(gameTopic)) break;
                System.out.printf("[%s] Round %s: %s=%s vs %s=%s -> %s%n",
                    name, payload.get("round"),
                    payload.get("playerA"), payload.get("choiceA"),
                    payload.get("playerB"), payload.get("choiceB"),
                    payload.get("winner"));
            }

            case "game_over" -> {
                if (!topic.equals(gameTopic)) break;
                System.out.printf("[%s] Game over! %s %s - %s %s%n",
                    name,
                    payload.get("playerA"), payload.get("scoreA"),
                    payload.get("playerB"), payload.get("scoreB"));
                ws.close();
            }

            // Ignore join, leave, ready, move echoes
            default -> { }
        }
    }

    private static void send(WebSocket ws, Map<String, ?> message) {
        try {
            ws.writeTextMessage(MAPPER.writeValueAsString(message));
        } catch (Exception e) {
            System.err.println("Failed to send: " + e.getMessage());
        }
    }
}
