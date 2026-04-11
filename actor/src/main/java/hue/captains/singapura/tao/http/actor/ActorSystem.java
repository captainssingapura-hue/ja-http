package hue.captains.singapura.tao.http.actor;

import hue.captains.singapura.tao.http.actor.frontier.FrontierActor;

/**
 * Core abstraction for an actor runtime.
 * <p>
 * Provides the operations that actors and hosting layers need to interact with the system:
 * allocating identities, registering actors, and injecting external messages.
 * <p>
 * Implementations decide how messages are dispatched (single-threaded loop, event-loop, etc.).
 */
public interface ActorSystem {

    /** Allocate a new actor identity with a human-readable name. */
    <R extends Message._Receive, A extends Actor<R>> ActorId<R,A> allocateId(Actor._TypeRef<R,A> atr, String name);

    /** Register an actor under the given identity. */
    void register(ActorId id, Actor<?> actor);

    /**
     * Register a frontier actor. The constructor receives a listener that bridges
     * external events into the actor system's mailbox.
     *
     * @return the constructed frontier actor instance (for direct method calls from external code)
     */
    <R extends Message._Receive, A extends FrontierActor<R>>
    A registerFrontier(ActorId id, FrontierActor._Constructor<R, A> constructor);

    /** Inject a message from outside the actor system (e.g. from a WebSocket handler). */
    void inject(ActorId target, Message._Receive message);
}
