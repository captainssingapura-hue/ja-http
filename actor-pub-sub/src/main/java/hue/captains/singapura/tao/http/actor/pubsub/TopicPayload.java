package hue.captains.singapura.tao.http.actor.pubsub;

import hue.captains.singapura.tao.http.actor.Message;

/**
 * A generic topic message carrier.
 * <p>
 * Embeds the topic name alongside a string payload (typically JSON).
 * This allows subscribers to know which topic a message came from,
 * and keeps the payload format agnostic — the actor system and pub-sub
 * primitives never parse the data.
 * <p>
 * Convention: use {@code TopicActor<TopicPayload>} for topics that carry
 * string-serialized data across heterogeneous subscribers (e.g. WebSocket
 * sessions and internal actors on the same topic).
 *
 * @param topicName the name of the topic this payload belongs to
 * @param data      the payload content (typically a JSON string)
 */
public record TopicPayload(String topicName, String data)
        implements Message._Receive, Message._Send {}
