package hue.captains.singapura.tao.http.actor.demo;

import hue.captains.singapura.tao.http.actor.Actor;
import hue.captains.singapura.tao.http.actor.ActorAction;
import hue.captains.singapura.tao.http.actor.ActorId;
import hue.captains.singapura.tao.http.actor.ActorSystem;
import hue.captains.singapura.tao.http.actor.Message;
import hue.captains.singapura.tao.http.actor.frontier.FrontierActor;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public class SingleThreadActorSystem implements ActorSystem {

    private final Map<ActorId, Actor<?, ?>> actors = new LinkedHashMap<>();
    private final Deque<Envelope> mailbox = new ArrayDeque<>();

    private record Envelope(ActorId target, Message._Receive message) {}

    public ActorId allocateId(String name) {
        return ActorId.allocate(name);
    }

    public int actorCount() {
        return actors.size();
    }

    public void register(ActorId id, Actor<?, ?> actor) {
        actors.put(id, actor);
    }

    public <R extends Message._Receive, S extends Message._Send, A extends FrontierActor<R, S>>
    A registerFrontier(ActorId id, FrontierActor._Constructor<R, S, A> constructor) {
        Consumer<ActorAction.SendMessage<S>> consumer = sendMsg ->
                mailbox.add(new Envelope(sendMsg.to(), (Message._Receive) sendMsg.message()));
        A actor = constructor.construct(consumer);
        actors.put(id, actor);
        return actor;
    }

    /** Inject a message into the mailbox from outside the actor system (simulating external events). */
    public void inject(ActorId target, Message._Receive message) {
        mailbox.add(new Envelope(target, message));
    }

    public void processMailbox() {
        while (!mailbox.isEmpty()) {
            var byTarget = new LinkedHashMap<ActorId, List<Message._Receive>>();
            while (!mailbox.isEmpty()) {
                var envelope = mailbox.poll();
                byTarget.computeIfAbsent(envelope.target(), k -> new ArrayList<>()).add(envelope.message());
            }

            for (var entry : byTarget.entrySet()) {
                var id = entry.getKey();
                var actor = actors.get(id);
                if (actor == null) {
                    continue; // actor was terminated, drop its messages
                }

                @SuppressWarnings("unchecked")
                var typedActor = (Actor<Message._Receive, Message._Send>) actor;
                var actions = typedActor.receive(entry.getValue());

                for (var action : actions) {
                    handleAction(id, action);
                }
            }
        }
    }

    private void handleAction(ActorId sender, ActorAction action) {
        switch (action) {
            case ActorAction.SendMessage<?> send ->
                    mailbox.add(new Envelope(send.to(), (Message._Receive) send.message()));
            case ActorAction.SpawnSubActor<?, ?, ?> spawn -> {
                if (spawn.actorType() instanceof ActorFactory<?, ?> factory) {
                    var id = allocateId("spawned");
                    @SuppressWarnings("unchecked")
                    var typedFactory = (ActorFactory<Message._Receive, Message._Send>) factory;
                    var actor = typedFactory.create(id);
                    actors.put(id, actor);
                    for (var msg : spawn.initialMessages()) {
                        mailbox.add(new Envelope(id, (Message._Receive) msg));
                    }
                } else {
                    throw new UnsupportedOperationException(
                            "SpawnSubActor requires actorType to implement ActorFactory");
                }
            }
            case ActorAction.SelfTerminate ignored ->
                    actors.remove(sender);
        }
    }
}
