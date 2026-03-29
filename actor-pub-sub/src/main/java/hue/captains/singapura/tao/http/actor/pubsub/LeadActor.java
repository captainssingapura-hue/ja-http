package hue.captains.singapura.tao.http.actor.pubsub;

import hue.captains.singapura.tao.http.actor.Actor;
import hue.captains.singapura.tao.http.actor.ActorAction;
import hue.captains.singapura.tao.http.actor.ActorRef;
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
public class LeadActor<F extends Actor._TypeRef<SessionMessage, SessionMessage>>
        implements Actor<LeadMessage, LeadMessage> {

    private final ActorRef selfRef;
    private final ActorRef topicManagerRef;
    private final F sessionFactory;
    private final Set<ActorRef> activeSessions = new LinkedHashSet<>();

    public LeadActor(ActorRef selfRef, ActorRef topicManagerRef, F sessionFactory) {
        this.selfRef = selfRef;
        this.topicManagerRef = topicManagerRef;
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
                            conn.connectionId(), selfRef, topicManagerRef))));
                }

                case LeadMessage.SessionEnded ended ->
                    activeSessions.remove(ended.sessionRef());
            }
        }

        return actions;
    }
}
