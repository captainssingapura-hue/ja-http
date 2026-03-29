package hue.captains.singapura.tao.http.actor.pubsub;

import hue.captains.singapura.tao.http.actor.ActorRef;
import hue.captains.singapura.tao.http.actor.Message;

import java.util.Map;

/**
 * Messages understood by a {@link TopicManagerActor}.
 * The Topic Manager is the authority on what topics exist and their {@link ActorRef}s.
 */
public sealed interface TopicManagerMessage extends Message._Receive, Message._Send
        permits TopicManagerMessage.RegisterTopic,
                TopicManagerMessage.RetireTopic,
                TopicManagerMessage.QueryTopics,
                TopicManagerMessage.TopicList {

    /** Register an existing topic actor under a name. Sent by the system wiring that creates topics. */
    record RegisterTopic(String topicName, ActorRef topicRef) implements TopicManagerMessage {}

    /** Retire a topic by name. The Topic Manager removes it from the registry. */
    record RetireTopic(String topicName) implements TopicManagerMessage {}

    /** Query available topics. The Topic Manager replies with a {@link TopicList} to {@code replyTo}. */
    record QueryTopics(ActorRef replyTo) implements TopicManagerMessage {}

    /** Response to a {@link QueryTopics} request — a snapshot of available topic names and refs. */
    record TopicList(Map<String, ActorRef> topics) implements TopicManagerMessage {}
}
