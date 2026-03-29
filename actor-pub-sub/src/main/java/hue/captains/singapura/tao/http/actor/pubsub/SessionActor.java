package hue.captains.singapura.tao.http.actor.pubsub;

import hue.captains.singapura.tao.http.actor.Actor;
import hue.captains.singapura.tao.http.actor.ActorAction;
import hue.captains.singapura.tao.http.actor.ActorRef;

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

    private final ActorRef selfRef;

    private ActorRef leadRef;
    private ActorRef topicManagerRef;
    private String connectionId;

    /** Resolved topic name to ActorRef — populated from Topic Manager responses. */
    private final Map<String, ActorRef> knownTopics = new LinkedHashMap<>();

    /** Topics this session is currently subscribed to (by name). */
    private final Set<String> activeSubscriptions = new LinkedHashSet<>();

    public SessionActor(ActorRef selfRef) {
        this.selfRef = selfRef;
    }

    @Override
    public List<ActorAction> receive(List<SessionMessage> messages) {
        var actions = new ArrayList<ActorAction>();

        for (var msg : messages) {
            switch (msg) {
                case SessionMessage.Init init -> {
                    this.connectionId = init.connectionId();
                    this.leadRef = init.leadRef();
                    this.topicManagerRef = init.topicManagerRef();
                    // Immediately query for available topics
                    actions.add(new ActorAction.SendMessage<>(topicManagerRef,
                        new TopicManagerMessage.QueryTopics(selfRef)));
                }

                case SessionMessage.TopicListReceived topicList ->
                    knownTopics.putAll(topicList.topics());

                case SessionMessage.ClientListTopics ignored ->
                    actions.add(new ActorAction.SendMessage<>(topicManagerRef,
                        new TopicManagerMessage.QueryTopics(selfRef)));

                case SessionMessage.ClientSubscribe sub -> {
                    var topicRef = knownTopics.get(sub.topicName());
                    if (topicRef != null) {
                        activeSubscriptions.add(sub.topicName());
                        actions.add(new ActorAction.SendMessage<>(topicRef,
                            new TopicMessage.Subscribe<>(selfRef)));
                    }
                }

                case SessionMessage.ClientUnsubscribe unsub -> {
                    var topicRef = knownTopics.get(unsub.topicName());
                    if (topicRef != null && activeSubscriptions.remove(unsub.topicName())) {
                        actions.add(new ActorAction.SendMessage<>(topicRef,
                            new TopicMessage.Unsubscribe<>(selfRef)));
                    }
                }

                case SessionMessage.ClientPublish pub -> {
                    var topicRef = knownTopics.get(pub.topicName());
                    if (topicRef != null) {
                        actions.add(new ActorAction.SendMessage<>(topicRef,
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
                        var topicRef = knownTopics.get(topicName);
                        if (topicRef != null) {
                            actions.add(new ActorAction.SendMessage<>(topicRef,
                                new TopicMessage.Unsubscribe<>(selfRef)));
                        }
                    }
                    activeSubscriptions.clear();
                    // Notify the lead actor
                    if (leadRef != null) {
                        actions.add(new ActorAction.SendMessage<>(leadRef,
                            new LeadMessage.SessionEnded(selfRef)));
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

    public Map<String, ActorRef> knownTopics() {
        return Map.copyOf(knownTopics);
    }
}
