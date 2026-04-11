package hue.captains.singapura.tao.http.actor;

/**
 * A {@link Actor._TypeRef} that also knows how to create the actor instance.
 * The actor system uses this to handle {@link ActorAction.SpawnSubActor}.
 */
public interface ActorFactory<R extends Message._Receive, A extends Actor<R>>
        extends Actor._TypeRef<R, A> {

    A create(ActorId<R, A> self);
}
