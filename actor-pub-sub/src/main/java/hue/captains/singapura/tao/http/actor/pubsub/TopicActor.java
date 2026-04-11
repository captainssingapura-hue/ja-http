package hue.captains.singapura.tao.http.actor.pubsub;

import hue.captains.singapura.tao.http.actor.Actor;
import hue.captains.singapura.tao.http.actor.ActorAction;
import hue.captains.singapura.tao.http.actor.ActorId;
import hue.captains.singapura.tao.http.actor.Message;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * A single-purpose fan-out actor for one topic.
 * Manages a subscriber set and delivers published payloads to all subscribers via {@link ActorAction.SendMessage}.
 * @param <M> the payload type this topic carries
 */
@SuppressWarnings({"rawtypes", "unchecked"})
public class TopicActor<M extends Message._Send>
        implements Actor<TopicMessage<M>> {

    private final Set<ActorId> subscribers = new LinkedHashSet<>();

    @Override
    public List<ActorAction> receive(List<TopicMessage<M>> messages) {
        var actions = new ArrayList<ActorAction>();

        for (var msg : messages) {
            switch (msg) {
                case TopicMessage.Subscribe<?> sub ->
                    subscribers.add(sub.subscriber());

                case TopicMessage.Unsubscribe<?> unsub ->
                    subscribers.remove(unsub.subscriber());

                case TopicMessage.Publish<?> pub -> {
                    @SuppressWarnings("unchecked")
                    var payload = (M) pub.payload();
                    for (var ref : subscribers) {
                        actions.add(new ActorAction.SendMessage(ref, (Message._Receive) payload));
                    }
                }
            }
        }

        return actions;
    }
}
