package hue.captains.singapura.tao.http.vertx.demo.pubsub.rps;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.vertx.core.Vertx;
import io.vertx.core.http.WebSocket;

import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Simple WebSocket client that connects to the pub-sub server, joins the RPS lobby,
 * plays a game with random moves, and exits when the game ends.
 * <p>
 * Usage: {@code RpsClient <name>}
 */
public class RpsClient {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final RpsGameMessage.Choice[] CHOICES = RpsGameMessage.Choice.values();

    private final String name;
    private final Vertx vertx;
    private String gameId;

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
        send(ws, Map.of("action", "rps_join", "name", name));

        ws.textMessageHandler(text -> onMessage(ws, text));
        ws.closeHandler(v -> {
            System.out.printf("[%s] Connection closed.%n", name);
            vertx.close();
        });
    }

    @SuppressWarnings("unchecked")
    private void onMessage(WebSocket ws, String text) {
        try {
            var json = MAPPER.readValue(text, Map.class);
            var type = (String) json.get("type");

            switch (type) {
                case "rps_waiting" ->
                    System.out.printf("[%s] Waiting for opponent...%n", name);

                case "game_created" -> {
                    this.gameId = (String) json.get("gameId");
                    System.out.printf("[%s] Matched against %s (game %s)%n",
                        name, json.get("opponent"), gameId);
                }

                case "round_start" -> {
                    var choice = CHOICES[ThreadLocalRandom.current().nextInt(CHOICES.length)];
                    System.out.printf("[%s] Round %s — playing %s%n", name, json.get("round"), choice);
                    send(ws, Map.of(
                        "action", "rps_move",
                        "gameId", gameId,
                        "move", choice.name()));
                }

                case "round_result" ->
                    System.out.printf("[%s] Round %s: %s=%s vs %s=%s → %s%n",
                        name, json.get("round"),
                        json.get("playerA"), json.get("choiceA"),
                        json.get("playerB"), json.get("choiceB"),
                        json.get("winner"));

                case "game_over" -> {
                    System.out.printf("[%s] Game over! %s %s - %s %s%n",
                        name,
                        json.get("playerA"), json.get("scoreA"),
                        json.get("playerB"), json.get("scoreB"));
                    ws.close();
                }

                case "topics", "subscribed" -> { }

                case "error" ->
                    System.err.printf("[%s] Error: %s%n", name, json.get("message"));

                default ->
                    System.out.printf("[%s] %s%n", name, text);
            }
        } catch (Exception e) {
            System.err.printf("[%s] Failed to parse: %s%n", name, e.getMessage());
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
