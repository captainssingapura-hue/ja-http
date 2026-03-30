package hue.captains.singapura.tao.http.actor.pubsub;

import hue.captains.singapura.tao.http.actor.Actor;
import hue.captains.singapura.tao.http.actor.ActorAction;
import hue.captains.singapura.tao.http.actor.ActorId;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The authority on what topics exist.
 * Maintains a registry of topic name to {@link ActorId} and answers {@link TopicManagerMessage.QueryTopics} requests.
 * <p>
 * Topics are registered externally via {@link TopicManagerMessage.RegisterTopic} —
 * the manager is a directory, not a factory.
 */
public class TopicManagerActor implements Actor<TopicManagerMessage, TopicManagerMessage> {

    private final Map<String, ActorId> topics = new LinkedHashMap<>();

    @Override
    public List<ActorAction> receive(List<TopicManagerMessage> messages) {
        var actions = new ArrayList<ActorAction>();

        for (var msg : messages) {
            switch (msg) {
                case TopicManagerMessage.RegisterTopic reg ->
                    topics.put(reg.topicName(), reg.topicId());

                case TopicManagerMessage.RetireTopic retire ->
                    topics.remove(retire.topicName());

                case TopicManagerMessage.QueryTopics query ->
                    actions.add(new ActorAction.SendMessage<>(
                        query.replyTo(),
                        new TopicManagerMessage.TopicList(Map.copyOf(topics))));

                case TopicManagerMessage.TopicList ignored -> {
                    // TopicList is a response message — the manager doesn't process it
                }
            }
        }

        return actions;
    }
}
