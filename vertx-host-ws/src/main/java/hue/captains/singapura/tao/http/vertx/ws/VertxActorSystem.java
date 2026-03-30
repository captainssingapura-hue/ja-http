package hue.captains.singapura.tao.http.vertx.ws;

import hue.captains.singapura.tao.http.actor.Actor;
import hue.captains.singapura.tao.http.actor.ActorAction;
import hue.captains.singapura.tao.http.actor.ActorId;
import hue.captains.singapura.tao.http.actor.ActorSystem;
import hue.captains.singapura.tao.http.actor.Message;
import hue.captains.singapura.tao.http.actor.frontier.FrontierActor;
import io.vertx.core.Vertx;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * An {@link ActorSystem} that dispatches on the Vert.x event loop.
 * <p>
 * When messages are injected (via {@link #inject} or frontier actor listeners),
 * mailbox processing is scheduled via {@code vertx.runOnContext()}.
 * This ensures all actor processing is single-threaded and non-blocking,
 * cooperating with the Vert.x event loop rather than fighting it.
 */
public class VertxActorSystem implements ActorSystem {

    private final Vertx vertx;
    private final Map<ActorId, Actor<?, ?>> actors = new LinkedHashMap<>();
    private final Deque<Envelope> mailbox = new ArrayDeque<>();
    private boolean processingScheduled = false;

    private record Envelope(ActorId target, Message._Receive message) {}

    public VertxActorSystem(Vertx vertx) {
        this.vertx = vertx;
    }

    @Override
    public ActorId allocateId(String name) {
        return ActorId.allocate(name);
    }

    @Override
    public void register(ActorId id, Actor<?, ?> actor) {
        actors.put(id, actor);
    }

    @Override
    public <R extends Message._Receive, S extends Message._Send, A extends FrontierActor<R, S>>
    A registerFrontier(ActorId id, FrontierActor._Constructor<R, S, A> constructor) {
        Consumer<ActorAction.SendMessage<S>> consumer = sendMsg -> {
            mailbox.add(new Envelope(sendMsg.to(), (Message._Receive) sendMsg.message()));
            scheduleProcessing();
        };
        A actor = constructor.construct(consumer);
        actors.put(id, actor);
        return actor;
    }

    @Override
    public void inject(ActorId target, Message._Receive message) {
        mailbox.add(new Envelope(target, message));
        scheduleProcessing();
    }

    public int actorCount() {
        return actors.size();
    }

    private void scheduleProcessing() {
        if (!processingScheduled) {
            processingScheduled = true;
            vertx.runOnContext(v -> {
                processingScheduled = false;
                processMailbox();
            });
        }
    }

    private void processMailbox() {
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
                    continue;
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
            case ActorAction.SpawnSubActor<?, ?, ?> spawn ->
                throw new UnsupportedOperationException(
                    "SpawnSubActor not yet supported in VertxActorSystem");
            case ActorAction.SelfTerminate ignored ->
                actors.remove(sender);
        }
    }
}
