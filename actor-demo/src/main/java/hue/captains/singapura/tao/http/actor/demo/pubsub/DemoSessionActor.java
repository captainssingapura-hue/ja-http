package hue.captains.singapura.tao.http.actor.demo.pubsub;

import hue.captains.singapura.tao.http.actor.Actor;
import hue.captains.singapura.tao.http.actor.ActorAction;
import hue.captains.singapura.tao.http.actor.ActorRef;
import hue.captains.singapura.tao.http.actor.Message;
import hue.captains.singapura.tao.http.actor.pubsub.LeadMessage;
import hue.captains.singapura.tao.http.actor.pubsub.TopicManagerMessage;
import hue.captains.singapura.tao.http.actor.pubsub.TopicMessage;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Demo session actor that handles both {@code SessionMessage}-like commands
 * and raw topic payloads (e.g., {@link PriceUpdate}).
 * <p>
 * In the demo, the actor system delivers all messages as {@link Message._Receive}
 * via unchecked cast. This actor uses {@code Message._Receive} as its receive type
 * so it can handle both control messages and topic data in one place.
 * <p>
 * In a real WebSocket deployment, this would be a FrontierActor that translates
 * between WebSocket frames and actor messages.
 */
public class DemoSessionActor implements Actor<Message._Receive, Message._Send> {

    private final ActorRef selfRef;
    private final String connectionId;

    private ActorRef leadRef;
    private ActorRef topicManagerRef;

    private final Map<String, ActorRef> knownTopics = new LinkedHashMap<>();
    private final Set<String> activeSubscriptions = new LinkedHashSet<>();

    public DemoSessionActor(ActorRef selfRef, String connectionId,
                            ActorRef leadRef, ActorRef topicManagerRef) {
        this.selfRef = selfRef;
        this.connectionId = connectionId;
        this.leadRef = leadRef;
        this.topicManagerRef = topicManagerRef;
    }

    @Override
    public List<ActorAction> receive(List<Message._Receive> messages) {
        var actions = new ArrayList<ActorAction>();

        for (var msg : messages) {
            switch (msg) {
                case TopicManagerMessage.TopicList topicList -> {
                    knownTopics.putAll(topicList.topics());
                    System.out.printf("  [Session %s] Discovered topics: %s%n",
                        connectionId, knownTopics.keySet());
                }

                case DemoCommand.Subscribe sub -> {
                    var topicRef = knownTopics.get(sub.topicName());
                    if (topicRef != null) {
                        activeSubscriptions.add(sub.topicName());
                        actions.add(new ActorAction.SendMessage<>(topicRef,
                            new TopicMessage.Subscribe<>(selfRef)));
                        System.out.printf("  [Session %s] Subscribed to '%s'%n",
                            connectionId, sub.topicName());
                    } else {
                        System.out.printf("  [Session %s] Unknown topic '%s'%n",
                            connectionId, sub.topicName());
                    }
                }

                case DemoCommand.Unsubscribe unsub -> {
                    var topicRef = knownTopics.get(unsub.topicName());
                    if (topicRef != null && activeSubscriptions.remove(unsub.topicName())) {
                        actions.add(new ActorAction.SendMessage<>(topicRef,
                            new TopicMessage.Unsubscribe<>(selfRef)));
                        System.out.printf("  [Session %s] Unsubscribed from '%s'%n",
                            connectionId, unsub.topicName());
                    }
                }

                case DemoCommand.Publish pub -> {
                    var topicRef = knownTopics.get(pub.topicName());
                    if (topicRef != null) {
                        actions.add(new ActorAction.SendMessage<>(topicRef,
                            new TopicMessage.Publish<>(pub.payload())));
                        System.out.printf("  [Session %s] Published to '%s': %s%n",
                            connectionId, pub.topicName(), pub.payload());
                    }
                }

                case DemoCommand.ListTopics ignored ->
                    actions.add(new ActorAction.SendMessage<>(topicManagerRef,
                        new TopicManagerMessage.QueryTopics(selfRef)));

                case DemoCommand.Disconnect ignored -> {
                    System.out.printf("  [Session %s] Disconnecting, unsubscribing from %s%n",
                        connectionId, activeSubscriptions);
                    for (var topicName : activeSubscriptions) {
                        var topicRef = knownTopics.get(topicName);
                        if (topicRef != null) {
                            actions.add(new ActorAction.SendMessage<>(topicRef,
                                new TopicMessage.Unsubscribe<>(selfRef)));
                        }
                    }
                    activeSubscriptions.clear();
                    if (leadRef != null) {
                        actions.add(new ActorAction.SendMessage<>(leadRef,
                            new LeadMessage.SessionEnded(selfRef)));
                    }
                    actions.add(new ActorAction.SelfTerminate());
                }

                // Topic payloads arrive here — the topic actor fans them out directly
                case PriceUpdate update ->
                    System.out.printf("  [Session %s] Received: %s @ %.2f%n",
                        connectionId, update.symbol(), update.price());

                default ->
                    System.out.printf("  [Session %s] Ignoring unknown message: %s%n",
                        connectionId, msg);
            }
        }

        return actions;
    }

    public String connectionId() { return connectionId; }
    public Set<String> activeSubscriptions() { return Set.copyOf(activeSubscriptions); }
}
