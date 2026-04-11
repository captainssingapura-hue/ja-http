package hue.captains.singapura.tao.http.rps2.remote;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import hue.captains.singapura.tao.http.actor.remote.MessageCodec;
import hue.captains.singapura.tao.http.rps2.Choice;
import hue.captains.singapura.tao.http.rps2.GameMessage;

/**
 * Encodes/decodes {@link GameMessage} to/from JSON for wire transport.
 * <p>
 * Wire format (client → server):
 * <pre>
 * {"type":"PlayerReady"}
 * {"type":"PlayerMove","choice":"ROCK"}
 * </pre>
 * <p>
 * The server stamps {@code playerId} and {@code playerName} onto decoded messages
 * via the identity transformer — the client doesn't know its server-side ActorId.
 * <p>
 * Note: {@code GameMessage.Init} is never sent over the wire.
 */
public class GameMessageCodec implements MessageCodec<GameMessage> {

    private final ObjectMapper mapper = new ObjectMapper();

    @Override
    public String encode(GameMessage message) {
        try {
            ObjectNode node = mapper.createObjectNode();
            switch (message) {
                case GameMessage.PlayerReady ignored ->
                        node.put("type", "PlayerReady");
                case GameMessage.PlayerMove m -> {
                    node.put("type", "PlayerMove");
                    node.put("choice", m.choice().name());
                }
                case GameMessage.Init ignored ->
                        throw new UnsupportedOperationException("Init is not sent over the wire");
            }
            return mapper.writeValueAsString(node);
        } catch (Exception e) {
            throw new RuntimeException("Failed to encode GameMessage", e);
        }
    }

    @Override
    public GameMessage decode(String data) {
        try {
            JsonNode node = mapper.readTree(data);
            String type = node.get("type").asText();
            return switch (type) {
                case "PlayerReady" -> new GameMessage.PlayerReady(null, null);
                case "PlayerMove" -> new GameMessage.PlayerMove(
                        null, null, Choice.valueOf(node.get("choice").asText()));
                default -> throw new IllegalArgumentException("Unknown GameMessage type: " + type);
            };
        } catch (Exception e) {
            throw new RuntimeException("Failed to decode GameMessage: " + data, e);
        }
    }
}
