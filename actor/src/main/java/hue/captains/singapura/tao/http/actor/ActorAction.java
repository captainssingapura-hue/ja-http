package hue.captains.singapura.tao.http.actor;

import java.util.List;

public sealed interface ActorAction permits ActorAction.SendMessage, ActorAction.SpawnSubActor, ActorAction.SelfTerminate {

    record SendMessage<M extends Message._Send>(ActorRef to, M message) implements ActorAction{}

    /**
     *
     * @param actorType The type of sub-actor to spawn.
     * @param initialMessages The starting message for the sub-actor to start work. Analogy to constructor args.
     * @param <R>
     * @param <S>
     * @param <A>
     */
    record SpawnSubActor<R extends Message._Receive, S extends Message._Send, A extends Actor._TypeRef<R,S>>(A actorType, List<R> initialMessages) implements ActorAction{}

    /**
     * Returned by an actor to voluntarily terminate itself.
     * The actor system will remove the actor and clean up any associated resources.
     * Any other actions returned alongside this one are still processed before removal.
     */
    record SelfTerminate() implements ActorAction{}
}
