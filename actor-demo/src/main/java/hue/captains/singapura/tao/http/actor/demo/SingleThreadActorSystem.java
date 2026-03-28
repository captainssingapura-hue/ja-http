package hue.captains.singapura.tao.http.actor.demo;

import hue.captains.singapura.tao.http.actor.Actor;
import hue.captains.singapura.tao.http.actor.ActorAction;
import hue.captains.singapura.tao.http.actor.ActorRef;
import hue.captains.singapura.tao.http.actor.Message;
import hue.captains.singapura.tao.http.actor.frontier.FrontierActor;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public class SingleThreadActorSystem {

    private final Map<ActorRef, Actor<?, ?>> actors = new LinkedHashMap<>();
    private final Deque<Envelope> mailbox = new ArrayDeque<>();

    private record Envelope(ActorRef target, Message._Receive message) {}

    private static final class Ref implements ActorRef {
        private static int nextId = 0;
        private final int id;
        private final String name;

        Ref(String name) {
            this.id = nextId++;
            this.name = name;
        }

        @Override
        public String toString() {
            return name + "#" + id;
        }
    }

    public ActorRef allocateRef(String name) {
        return new Ref(name);
    }

    public int actorCount() {
        return actors.size();
    }

    public void register(ActorRef ref, Actor<?, ?> actor) {
        actors.put(ref, actor);
    }

    public <R extends Message._Receive, S extends Message._Send, A extends FrontierActor<R, S>>
    A registerFrontier(ActorRef ref, FrontierActor._Constructor<R, S, A> constructor) {
        Consumer<ActorAction.SendMessage<S>> consumer = sendMsg ->
                mailbox.add(new Envelope(sendMsg.to(), (Message._Receive) sendMsg.message()));
        A actor = constructor.construct(consumer);
        actors.put(ref, actor);
        return actor;
    }

    public void processMailbox() {
        while (!mailbox.isEmpty()) {
            var byTarget = new LinkedHashMap<ActorRef, List<Message._Receive>>();
            while (!mailbox.isEmpty()) {
                var envelope = mailbox.poll();
                byTarget.computeIfAbsent(envelope.target(), k -> new ArrayList<>()).add(envelope.message());
            }

            for (var entry : byTarget.entrySet()) {
                var ref = entry.getKey();
                var actor = actors.get(ref);
                if (actor == null) {
                    continue; // actor was terminated, drop its messages
                }

                @SuppressWarnings("unchecked")
                var typedActor = (Actor<Message._Receive, Message._Send>) actor;
                var actions = typedActor.receive(entry.getValue());

                for (var action : actions) {
                    handleAction(ref, action);
                }
            }
        }
    }

    private void handleAction(ActorRef sender, ActorAction action) {
        switch (action) {
            case ActorAction.SendMessage<?> send ->
                    mailbox.add(new Envelope(send.to(), (Message._Receive) send.message()));
            case ActorAction.SpawnSubActor<?, ?, ?> spawn -> {
                if (spawn.actorType() instanceof ActorFactory<?, ?> factory) {
                    var ref = allocateRef("spawned");
                    @SuppressWarnings("unchecked")
                    var typedFactory = (ActorFactory<Message._Receive, Message._Send>) factory;
                    var actor = typedFactory.create(ref);
                    actors.put(ref, actor);
                    for (var msg : spawn.initialMessages()) {
                        mailbox.add(new Envelope(ref, (Message._Receive) msg));
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
