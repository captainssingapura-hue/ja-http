package hue.captains.singapura.tao.http.actor.pubsub;

import hue.captains.singapura.tao.http.actor.Actor;
import hue.captains.singapura.tao.http.actor.ActorAction;
import hue.captains.singapura.tao.http.actor.ActorId;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Per-client session actor. Translates client requests (subscribe, publish, etc.)
 * into messages to Topic Actors and the Topic Manager.
 * <p>
 * The session actor is spawned by the {@link LeadActor} and receives an {@link SessionMessage.Init}
 * message with the refs it needs. It queries the Topic Manager to discover available topics,
 * then interacts with Topic Actors directly for subscribe/publish.
 */
public class SessionActor implements Actor<SessionMessage, SessionMessage> {

    private final ActorId selfId;

    private ActorId leadId;
    private ActorId topicManagerId;
    private String connectionId;

    /** Resolved topic name to ActorId — populated from Topic Manager responses. */
    private final Map<String, ActorId> knownTopics = new LinkedHashMap<>();

    /** Topics this session is currently subscribed to (by name). */
    private final Set<String> activeSubscriptions = new LinkedHashSet<>();

    public SessionActor(ActorId selfId) {
        this.selfId = selfId;
    }

    @Override
    public List<ActorAction> receive(List<SessionMessage> messages) {
        var actions = new ArrayList<ActorAction>();

        for (var msg : messages) {
            switch (msg) {
                case SessionMessage.Init init -> {
                    this.connectionId = init.connectionId();
                    this.leadId = init.leadId();
                    this.topicManagerId = init.topicManagerId();
                    // Immediately query for available topics
                    actions.add(new ActorAction.SendMessage<>(topicManagerId,
                        new TopicManagerMessage.QueryTopics(selfId)));
                }

                case SessionMessage.TopicListReceived topicList ->
                    knownTopics.putAll(topicList.topics());

                case SessionMessage.ClientListTopics ignored ->
                    actions.add(new ActorAction.SendMessage<>(topicManagerId,
                        new TopicManagerMessage.QueryTopics(selfId)));

                case SessionMessage.ClientSubscribe sub -> {
                    var topicId = knownTopics.get(sub.topicName());
                    if (topicId != null) {
                        activeSubscriptions.add(sub.topicName());
                        actions.add(new ActorAction.SendMessage<>(topicId,
                            new TopicMessage.Subscribe<>(selfId)));
                    }
                }

                case SessionMessage.ClientUnsubscribe unsub -> {
                    var topicId = knownTopics.get(unsub.topicName());
                    if (topicId != null && activeSubscriptions.remove(unsub.topicName())) {
                        actions.add(new ActorAction.SendMessage<>(topicId,
                            new TopicMessage.Unsubscribe<>(selfId)));
                    }
                }

                case SessionMessage.ClientPublish pub -> {
                    var topicId = knownTopics.get(pub.topicName());
                    if (topicId != null) {
                        actions.add(new ActorAction.SendMessage<>(topicId,
                            new TopicMessage.Publish<>(pub.payload())));
                    }
                }

                case SessionMessage.TopicData data -> {
                    // In a real system, this is where we'd forward to the WebSocket.
                    // For the demo, the frontier actor subclass handles this.
                }

                case SessionMessage.ClientDisconnect ignored -> {
                    // Unsubscribe from all active topics
                    for (var topicName : activeSubscriptions) {
                        var topicId = knownTopics.get(topicName);
                        if (topicId != null) {
                            actions.add(new ActorAction.SendMessage<>(topicId,
                                new TopicMessage.Unsubscribe<>(selfId)));
                        }
                    }
                    activeSubscriptions.clear();
                    // Notify the lead actor
                    if (leadId != null) {
                        actions.add(new ActorAction.SendMessage<>(leadId,
                            new LeadMessage.SessionEnded(selfId)));
                    }
                    actions.add(new ActorAction.SelfTerminate());
                }
            }
        }

        return actions;
    }

    public String connectionId() {
        return connectionId;
    }

    public Set<String> activeSubscriptions() {
        return Set.copyOf(activeSubscriptions);
    }

    public Map<String, ActorId> knownTopics() {
        return Map.copyOf(knownTopics);
    }
}
