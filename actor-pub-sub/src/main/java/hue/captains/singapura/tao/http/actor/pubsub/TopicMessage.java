package hue.captains.singapura.tao.http.actor.pubsub;

import hue.captains.singapura.tao.http.actor.ActorId;
import hue.captains.singapura.tao.http.actor.Message;

/**
 * Messages understood by a {@link TopicActor}.
 * @param <M> the payload type carried by this topic
 */
public sealed interface TopicMessage<M extends Message>
        extends Message._Receive, Message._Send
        permits TopicMessage.Subscribe, TopicMessage.Unsubscribe, TopicMessage.Publish {

    /** Ask the topic actor to deliver future publications to {@code subscriber}. */
    record Subscribe<M extends Message>(ActorId subscriber) implements TopicMessage<M> {}

    /** Ask the topic actor to stop delivering publications to {@code subscriber}. */
    record Unsubscribe<M extends Message>(ActorId subscriber) implements TopicMessage<M> {}

    /** Publish a payload to all current subscribers. */
    record Publish<M extends Message>(M payload) implements TopicMessage<M> {}
}
