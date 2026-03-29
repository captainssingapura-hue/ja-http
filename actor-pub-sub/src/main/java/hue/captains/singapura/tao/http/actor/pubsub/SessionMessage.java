package hue.captains.singapura.tao.http.actor.pubsub;

import hue.captains.singapura.tao.http.actor.ActorRef;
import hue.captains.singapura.tao.http.actor.Message;

import java.util.Map;

/**
 * Messages understood by a {@link SessionActor}.
 * The Session Actor is the per-client boundary that translates between
 * external client requests and internal actor messages.
 */
public sealed interface SessionMessage extends Message._Receive, Message._Send
        permits SessionMessage.Init,
                SessionMessage.ClientSubscribe,
                SessionMessage.ClientUnsubscribe,
                SessionMessage.ClientPublish,
                SessionMessage.ClientListTopics,
                SessionMessage.ClientDisconnect,
                SessionMessage.TopicListReceived,
                SessionMessage.TopicData {

    /** Initialization message sent by the Lead Actor after spawning. */
    record Init(String connectionId, ActorRef leadRef, ActorRef topicManagerRef)
            implements SessionMessage {}

    /** Client wants to subscribe to a topic by name. */
    record ClientSubscribe(String topicName) implements SessionMessage {}

    /** Client wants to unsubscribe from a topic by name. */
    record ClientUnsubscribe(String topicName) implements SessionMessage {}

    /** Client publishes a payload to a topic by name. */
    record ClientPublish(String topicName, Message payload) implements SessionMessage {}

    /** Client requests the list of available topics. */
    record ClientListTopics() implements SessionMessage {}

    /** Client has disconnected. Session should clean up and terminate. */
    record ClientDisconnect() implements SessionMessage {}

    /** Response from the Topic Manager with available topics. */
    record TopicListReceived(Map<String, ActorRef> topics) implements SessionMessage {}

    /** A message received from a topic the session is subscribed to. */
    record TopicData(Message payload) implements SessionMessage {}
}
