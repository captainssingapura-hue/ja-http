package hue.captains.singapura.tao.http.rps2.remote;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import hue.captains.singapura.tao.http.rps2.Choice;
import hue.captains.singapura.tao.http.rps2.GameMessage;
import hue.captains.singapura.tao.http.rps2.PlayerMessage;
import io.vertx.core.Vertx;
import io.vertx.core.http.WebSocket;
import io.vertx.core.http.WebSocketConnectOptions;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Standalone RPS client that connects to the remote server via WebSocket.
 * <p>
 * Usage: pass the player name as the first argument.
 * <pre>
 * mvn exec:java -pl rps-game-2-remote \
 *   -Dexec.mainClass="hue.captains.singapura.tao.http.rps2.remote.RpsRemoteClient" \
 *   -Dexec.args="Alice"
 * </pre>
 */
public class RpsRemoteClient {

    private static final Choice[] CHOICES = Choice.values();

    private final String playerName;
    private final PlayerMessageCodec playerCodec = new PlayerMessageCodec();
    private final GameMessageCodec gameCodec = new GameMessageCodec();
    private final ObjectMapper mapper = new ObjectMapper();

    public RpsRemoteClient(String playerName) {
        this.playerName = playerName;
    }

    public void connect() {
        var vertx = Vertx.vertx();

        var options = new WebSocketConnectOptions()
                .setHost("localhost")
                .setPort(8082)
                .setURI("/rps");

        vertx.createHttpClient()
                .webSocket(options)
                .onSuccess(ws -> {
                    System.out.printf("[%s] Connected to server%n", playerName);
                    sendHandshake(ws);
                    ws.textMessageHandler(frame -> handleMessage(ws, frame));
                    ws.closeHandler(v -> {
                        System.out.printf("[%s] Disconnected%n", playerName);
                        vertx.close();
                    });
                })
                .onFailure(err -> {
                    System.err.printf("[%s] Connection failed: %s%n", playerName, err.getMessage());
                    vertx.close();
                });
    }

    private void sendHandshake(WebSocket ws) {
        try {
            ObjectNode node = mapper.createObjectNode();
            node.put("name", playerName);
            ws.writeTextMessage(mapper.writeValueAsString(node));
        } catch (Exception e) {
            throw new RuntimeException("Failed to send handshake", e);
        }
    }

    private void handleMessage(WebSocket ws, String frame) {
        var msg = playerCodec.decode(frame);

        switch (msg) {
            case PlayerMessage.GameAssigned assigned -> {
                    System.out.printf("[%s] Matched against %s%n", playerName, assigned.opponentName());
                    var ready = new GameMessage.PlayerReady(null, null);
                    ws.writeTextMessage(gameCodec.encode(ready));
            }

            case PlayerMessage.RoundStart roundStart -> {
                var choice = CHOICES[ThreadLocalRandom.current().nextInt(CHOICES.length)];
                System.out.printf("[%s] Round %d — playing %s%n", playerName, roundStart.round(), choice);
                var move = new GameMessage.PlayerMove(null, null, choice);
                ws.writeTextMessage(gameCodec.encode(move));
            }

            case PlayerMessage.RoundResult result ->
                    System.out.printf("[%s] Round %d: %s(%s) vs %s(%s) — winner: %s%n",
                            playerName, result.round(),
                            result.playerA(), result.choiceA(),
                            result.playerB(), result.choiceB(),
                            result.winner());

            case PlayerMessage.GameOver gameOver -> {
                var outcome = gameOver.winner().equals(playerName) ? "WON" : "LOST";
                System.out.printf("[%s] Game over — %s! (%s %d - %s %d)%n",
                        playerName, outcome,
                        gameOver.playerA(), gameOver.scoreA(),
                        gameOver.playerB(), gameOver.scoreB());
                ws.close();
            }
        }
    }

    public static void main(String[] args) {
        var name = args.length > 0 ? args[0] : "Player" + ThreadLocalRandom.current().nextInt(1000);
        System.out.printf("=== RPS Remote Client: %s ===%n", name);
        new RpsRemoteClient(name).connect();
    }
}
