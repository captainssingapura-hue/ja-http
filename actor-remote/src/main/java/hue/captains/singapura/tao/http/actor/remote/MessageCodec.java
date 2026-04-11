package hue.captains.singapura.tao.http.actor.remote;

/**
 * Serializes and deserializes messages of type {@code M} to/from strings.
 * One codec per message type hierarchy (e.g., one for PlayerMessage, one for GameMessage).
 *
 * @param <M> the message type
 */
public interface MessageCodec<M> {

    String encode(M message);

    M decode(String data);
}
