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
 * The entry point for new client connections.
 * For each {@link LeadMessage.NewConnection}, the Lead Actor spawns a new Session Actor
 * and sends it an initialization message with the refs it needs to operate.
 * <p>
 * The Lead Actor holds a session factory ({@link Actor._TypeRef}) that the actor system
 * uses to instantiate session actors. How this factory creates sessions is determined
 * by the actor system implementation (e.g., the demo's {@code ActorFactory}).
 *
 * @param <F> the session factory type, which must be an {@link Actor._TypeRef}
 */
@SuppressWarnings({"rawtypes", "unchecked"})
public class LeadActor<F extends Actor._TypeRef>
        implements Actor<LeadMessage> {

    private final ActorId selfId;
    private final ActorId topicManagerId;
    private final F sessionFactory;
    private final Set<ActorId> activeSessions = new LinkedHashSet<>();

    public LeadActor(ActorId selfId, ActorId topicManagerId, F sessionFactory) {
        this.selfId = selfId;
        this.topicManagerId = topicManagerId;
        this.sessionFactory = sessionFactory;
    }

    @Override
    public List<ActorAction> receive(List<LeadMessage> messages) {
        var actions = new ArrayList<ActorAction>();

        for (var msg : messages) {
            switch (msg) {
                case LeadMessage.NewConnection conn -> {
                    // Spawn a new session actor with an Init message
                    actions.add(new ActorAction.SpawnSubActor<>(
                        sessionFactory,
                        List.of(new SessionMessage.Init(
                            conn.connectionId(), selfId, topicManagerId))));
                }

                case LeadMessage.SessionEnded ended ->
                    activeSessions.remove(ended.sessionId());
            }
        }

        return actions;
    }
}
