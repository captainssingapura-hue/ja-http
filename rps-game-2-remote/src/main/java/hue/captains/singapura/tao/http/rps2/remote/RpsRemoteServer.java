package hue.captains.singapura.tao.http.rps2.remote;

import com.fasterxml.jackson.databind.ObjectMapper;
import hue.captains.singapura.tao.http.actor.ActorId;
import hue.captains.singapura.tao.http.actor.remote.RemoteProxyActor;
import hue.captains.singapura.tao.http.actor.system.EventLoopActorSystem;
import hue.captains.singapura.tao.http.rps2.GameMessage;
import hue.captains.singapura.tao.http.rps2.LobbyActor;
import hue.captains.singapura.tao.http.rps2.LobbyMessage;
import hue.captains.singapura.tao.http.rps2.PlayerActor;
import hue.captains.singapura.tao.http.rps2.PlayerMessage;
import io.vertx.core.Vertx;
import io.vertx.core.http.ServerWebSocket;

/**
 * RPS game server that accepts remote players via WebSocket.
 * <p>
 * Uses {@link EventLoopActorSystem} for all actor dispatch.
 * Vert.x is used only as the WebSocket transport layer.
 * <p>
 * Protocol:
 * <ol>
 *   <li>Client connects to {@code ws://localhost:8082/rps}</li>
 *   <li>Client sends handshake: {@code {"name":"Alice"}}</li>
 *   <li>Server creates a proxy actor, joins lobby</li>
 *   <li>Game proceeds via PlayerMessage (server→client) and GameMessage (client→server)</li>
 * </ol>
 */
@SuppressWarnings({"rawtypes", "unchecked"})
public class RpsRemoteServer {

    private static final int PORT = 8082;

    private final EventLoopActorSystem system = new EventLoopActorSystem();
    private final ActorId lobbyId;
    private final ObjectMapper mapper = new ObjectMapper();

    public RpsRemoteServer() {
        lobbyId = system.allocateId(LobbyActor.ATR, "lobby");
        system.register(lobbyId, new LobbyActor());
    }

    public void start() {
        system.start();
        var vertx = Vertx.vertx();

        vertx.createHttpServer()
                .webSocketHandler(this::handleConnection)
                .listen(PORT)
                .onSuccess(server ->
                        System.out.printf("[Server] Listening on ws://localhost:%d/rps%n", PORT))
                .onFailure(err ->
                        System.err.println("[Server] Failed to start: " + err.getMessage()));
    }

    private void handleConnection(ServerWebSocket ws) {
        if (!"/rps".equals(ws.path())) {
            ws.reject();
            return;
        }

        // First frame is handshake: {"name":"Alice"}
        ws.textMessageHandler(firstFrame -> {
            try {
                var node = mapper.readTree(firstFrame);
                var playerName = node.get("name").asText();
                System.out.printf("[Server] Player connected: %s%n", playerName);

                registerProxy(ws, playerName);
            } catch (Exception e) {
                System.err.println("[Server] Bad handshake: " + firstFrame);
                ws.close();
            }
        });
    }

    private void registerProxy(ServerWebSocket ws, String playerName) {
        var transport = new VertxWsTransport(ws);

        // Allocate a typed proxy ActorId — from the system's perspective, this is a PlayerActor
        var proxyId = system.allocateId(PlayerActor.ATR, "remote-" + playerName);

        // Identity transformer: stamp the proxy's ActorId and player name onto inbound GameMessages
        var inboundTransformer = new java.util.function.UnaryOperator<GameMessage>() {
            @Override
            public GameMessage apply(GameMessage msg) {
                return switch (msg) {
                    case GameMessage.PlayerReady ignored ->
                            new GameMessage.PlayerReady(proxyId, playerName);
                    case GameMessage.PlayerMove m ->
                            new GameMessage.PlayerMove(proxyId, playerName, m.choice());
                    case GameMessage.Init init -> init; // should not happen
                };
            }
        };

        // Target extractor: learn the game actor ID from GameAssigned messages
        var targetExtractor = new java.util.function.Function<PlayerMessage, ActorId>() {
            @Override
            public ActorId apply(PlayerMessage msg) {
                if (msg instanceof PlayerMessage.GameAssigned assigned) {
                    return assigned.gameActorId();
                }
                return null;
            }
        };

        // Register the proxy as a frontier actor — this sets up the transport's onReceive handler
        var constructor = RemoteProxyActor.constructor(
                transport,
                new PlayerMessageCodec(),
                new GameMessageCodec(),
                targetExtractor,
                inboundTransformer);

        system.registerFrontier(proxyId, constructor);

        // Join the lobby
        system.inject(lobbyId, new LobbyMessage.Join(proxyId, playerName));
    }

    public static void main(String[] args) {
        System.out.println("=== RPS Remote Server ===");
        new RpsRemoteServer().start();
    }
}
