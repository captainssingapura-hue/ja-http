package hue.captains.singapura.tao.http.actor;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Immutable identity for an actor in the system.
 * <p>
 * Unlike Akka's ActorRef, this is a pure value type with no behavior —
 * it cannot send messages or interact with the actor system.
 * It serves only as an address for routing messages.
 *
 * @param name human-readable name (e.g. "topic:prices", "ws-session")
 * @param id   unique numeric identifier
 */
public record ActorId<R extends Message._Receive, A extends Actor<R>>(Actor._TypeRef<R,A> atr, String name, int id) {

    private static final AtomicInteger NEXT_ID = new AtomicInteger(0);

    /** Allocate a new ActorId with the given human-readable name. */
    public static <R extends Message._Receive, A extends Actor<R>> ActorId<R,A> allocate(Actor._TypeRef<R,A> atr, String name) {
        return new ActorId<>(atr, name, NEXT_ID.getAndIncrement());
    }

    @Override
    public String toString() {
        return name + "#" + id;
    }
}
