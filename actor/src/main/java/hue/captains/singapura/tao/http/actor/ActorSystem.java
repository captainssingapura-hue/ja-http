package hue.captains.singapura.tao.http.actor;

import hue.captains.singapura.tao.http.actor.frontier.FrontierActor;

/**
 * Core abstraction for an actor runtime.
 * <p>
 * Provides the operations that actors and hosting layers need to interact with the system:
 * allocating references, registering actors, and injecting external messages.
 * <p>
 * Implementations decide how messages are dispatched (single-threaded loop, event-loop, etc.).
 */
public interface ActorSystem {

    /** Allocate a new actor reference with a human-readable name. */
    ActorRef allocateRef(String name);

    /** Register an actor under the given reference. */
    void register(ActorRef ref, Actor<?, ?> actor);

    /**
     * Register a frontier actor. The constructor receives a listener that bridges
     * external events into the actor system's mailbox.
     *
     * @return the constructed frontier actor instance (for direct method calls from external code)
     */
    <R extends Message._Receive, S extends Message._Send, A extends FrontierActor<R, S>>
    A registerFrontier(ActorRef ref, FrontierActor._Constructor<R, S, A> constructor);

    /** Inject a message from outside the actor system (e.g. from a WebSocket handler). */
    void inject(ActorRef target, Message._Receive message);
}
