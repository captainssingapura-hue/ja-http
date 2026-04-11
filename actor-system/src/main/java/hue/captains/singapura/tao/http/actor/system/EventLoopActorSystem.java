package hue.captains.singapura.tao.http.actor.system;

import hue.captains.singapura.tao.http.actor.Actor;
import hue.captains.singapura.tao.http.actor.ActorAction;
import hue.captains.singapura.tao.http.actor.ActorId;
import hue.captains.singapura.tao.http.actor.ActorSystem;
import hue.captains.singapura.tao.http.actor.Message;
import hue.captains.singapura.tao.http.actor.ActorFactory;
import hue.captains.singapura.tao.http.actor.frontier.FrontierActor;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;

/**
 * A single-threaded actor system with its own event loop.
 * <p>
 * Owns a dedicated thread that processes actor messages. The event loop
 * blocks when idle and wakes automatically when messages arrive via
 * {@link #inject} or frontier actor callbacks.
 * <p>
 * All actor code runs on the event loop thread. External threads interact
 * only through {@link #inject} and frontier actor listeners, both of which
 * are thread-safe.
 * <p>
 * Usage:
 * <pre>
 *   var system = new EventLoopActorSystem();
 *   // register actors, inject initial messages...
 *   system.start();  // starts the event loop
 *   // ...
 *   system.shutdown();
 * </pre>
 * Registration ({@link #register}, {@link #registerFrontier}) must happen
 * either before {@link #start} or from actor processing code (on the event loop thread).
 */
public class EventLoopActorSystem implements ActorSystem {

    private final Map<ActorId, Actor<?>> actors = new ConcurrentHashMap<>();

    /** Thread-safe inbox for messages from external threads. */
    private final LinkedBlockingQueue<Envelope> inbox = new LinkedBlockingQueue<>();

    /** Internal mailbox — only accessed from the event loop thread. */
    private final Deque<Envelope> mailbox = new ArrayDeque<>();

    private final ReentrantLock idleLock = new ReentrantLock();
    private final Condition idleCondition = idleLock.newCondition();
    private volatile boolean idle = true;

    private volatile Thread eventLoop;

    private record Envelope(ActorId target, Message._Receive message) {}

    @Override
    public <R extends Message._Receive, A extends Actor<R>> ActorId<R, A> allocateId(Actor._TypeRef<R, A> atr, String name) {
        return ActorId.allocate(atr, name);
    }

    @Override
    public void register(ActorId id, Actor<?> actor) {
        actors.put(id, actor);
    }

    @Override
    public <R extends Message._Receive, A extends FrontierActor<R>>
    A registerFrontier(ActorId id, FrontierActor._Constructor<R, A> constructor) {
        Consumer<ActorAction.SendMessage<?, ?>> listener = sendMsg ->
                inject(sendMsg.to(), (Message._Receive) sendMsg.message());
        A actor = constructor.construct(listener);
        actors.put(id, actor);
        return actor;
    }

    /** Inject a message from any thread. Thread-safe. */
    @Override
    public void inject(ActorId target, Message._Receive message) {
        idleLock.lock();
        try {
            idle = false;
        } finally {
            idleLock.unlock();
        }
        inbox.add(new Envelope(target, message));
    }

    /**
     * Block the calling thread until the event loop has processed all pending messages
     * and is idle (waiting for new input). Useful for tests and demos.
     */
    public void awaitIdle() throws InterruptedException {
        idleLock.lock();
        try {
            while (!idle) {
                idleCondition.await();
            }
        } finally {
            idleLock.unlock();
        }
    }

    public int actorCount() {
        return actors.size();
    }

    /** Start the event loop on a dedicated thread. */
    public void start() {
        if (eventLoop != null) {
            throw new IllegalStateException("Already started");
        }
        eventLoop = Thread.ofPlatform()
                .name("actor-event-loop")
                .daemon(true)
                .start(this::run);
    }

    /** Interrupt the event loop thread and wait for it to finish. */
    public void shutdown() {
        var thread = eventLoop;
        if (thread != null) {
            thread.interrupt();
            try {
                thread.join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private void run() {
        try {
            while (!Thread.currentThread().isInterrupted()) {
                // Block until at least one external message arrives
                mailbox.add(inbox.take());
                drainInbox();

                // Process until both queues are empty
                while (!mailbox.isEmpty()) {
                    processOneBatch();
                    drainInbox();
                }

                // Signal idle
                idleLock.lock();
                try {
                    idle = true;
                    idleCondition.signalAll();
                } finally {
                    idleLock.unlock();
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void drainInbox() {
        Envelope e;
        while ((e = inbox.poll()) != null) {
            mailbox.add(e);
        }
    }

    private void processOneBatch() {
        // Group pending messages by target
        var byTarget = new LinkedHashMap<ActorId, List<Message._Receive>>();
        while (!mailbox.isEmpty()) {
            var envelope = mailbox.poll();
            byTarget.computeIfAbsent(envelope.target(), k -> new ArrayList<>())
                    .add(envelope.message());
        }

        // Deliver to each actor
        for (var entry : byTarget.entrySet()) {
            var id = entry.getKey();
            var actor = actors.get(id);
            if (actor == null) {
                continue; // actor was terminated, drop its messages
            }

            @SuppressWarnings("unchecked")
            var typedActor = (Actor<Message._Receive>) actor;
            var actions = typedActor.receive(entry.getValue());

            for (var action : actions) {
                handleAction(id, action);
            }
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void handleAction(ActorId sender, ActorAction action) {
        switch (action) {
            case ActorAction.SendMessage<?, ?> send ->
                    mailbox.add(new Envelope(send.to(), (Message._Receive) send.message()));

            case ActorAction.SpawnSubActor<?, ?> spawn -> {
                if (spawn.actorType() instanceof ActorFactory<?, ?> factory) {
                    var typedFactory = (ActorFactory<Message._Receive, Actor<Message._Receive>>) factory;
                    var id = ActorId.allocate(typedFactory, "spawned");
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
