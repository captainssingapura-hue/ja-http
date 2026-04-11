package hue.captains.singapura.tao.http.rps2.remote;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import hue.captains.singapura.tao.http.actor.remote.MessageCodec;
import hue.captains.singapura.tao.http.rps2.Choice;
import hue.captains.singapura.tao.http.rps2.PlayerMessage;

/**
 * Encodes/decodes {@link PlayerMessage} to/from JSON for wire transport.
 * <p>
 * Wire format (server → client):
 * <pre>
 * {"type":"GameAssigned","opponentName":"Bob"}
 * {"type":"RoundStart","round":1}
 * {"type":"RoundResult","round":1,"playerA":"Alice","choiceA":"ROCK","playerB":"Bob","choiceB":"SCISSORS","winner":"Alice"}
 * {"type":"GameOver","winner":"Alice","playerA":"Alice","scoreA":3,"playerB":"Bob","scoreB":1}
 * </pre>
 * <p>
 * Note: {@code GameAssigned.gameActorId} is NOT sent to the remote client — the
 * proxy knows the game actor and will route responses accordingly.
 */
public class PlayerMessageCodec implements MessageCodec<PlayerMessage> {

    private final ObjectMapper mapper = new ObjectMapper();

    @Override
    public String encode(PlayerMessage message) {
        try {
            ObjectNode node = mapper.createObjectNode();
            switch (message) {
                case PlayerMessage.GameAssigned m -> {
                    node.put("type", "GameAssigned");
                    node.put("opponentName", m.opponentName());
                }
                case PlayerMessage.RoundStart m -> {
                    node.put("type", "RoundStart");
                    node.put("round", m.round());
                }
                case PlayerMessage.RoundResult m -> {
                    node.put("type", "RoundResult");
                    node.put("round", m.round());
                    node.put("playerA", m.playerA());
                    node.put("choiceA", m.choiceA().name());
                    node.put("playerB", m.playerB());
                    node.put("choiceB", m.choiceB().name());
                    node.put("winner", m.winner());
                }
                case PlayerMessage.GameOver m -> {
                    node.put("type", "GameOver");
                    node.put("winner", m.winner());
                    node.put("playerA", m.playerA());
                    node.put("scoreA", m.scoreA());
                    node.put("playerB", m.playerB());
                    node.put("scoreB", m.scoreB());
                }
            }
            return mapper.writeValueAsString(node);
        } catch (Exception e) {
            throw new RuntimeException("Failed to encode PlayerMessage", e);
        }
    }

    @Override
    public PlayerMessage decode(String data) {
        try {
            JsonNode node = mapper.readTree(data);
            String type = node.get("type").asText();
            return switch (type) {
                case "GameAssigned" -> new PlayerMessage.GameAssigned(
                        null, // gameActorId not sent on wire
                        node.get("opponentName").asText());
                case "RoundStart" -> new PlayerMessage.RoundStart(
                        node.get("round").asInt());
                case "RoundResult" -> new PlayerMessage.RoundResult(
                        node.get("round").asInt(),
                        node.get("playerA").asText(),
                        Choice.valueOf(node.get("choiceA").asText()),
                        node.get("playerB").asText(),
                        Choice.valueOf(node.get("choiceB").asText()),
                        node.get("winner").asText());
                case "GameOver" -> new PlayerMessage.GameOver(
                        node.get("winner").asText(),
                        node.get("playerA").asText(),
                        node.get("scoreA").asInt(),
                        node.get("playerB").asText(),
                        node.get("scoreB").asInt());
                default -> throw new IllegalArgumentException("Unknown PlayerMessage type: " + type);
            };
        } catch (Exception e) {
            throw new RuntimeException("Failed to decode PlayerMessage: " + data, e);
        }
    }
}
