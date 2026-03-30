package hue.captains.singapura.tao.http.actor.demo;

import hue.captains.singapura.tao.http.actor.Actor;
import hue.captains.singapura.tao.http.actor.ActorId;
import hue.captains.singapura.tao.http.actor.Message;

/**
 * A {@link Actor._TypeRef} that also knows how to create the actor instance.
 * The actor system uses this to handle {@link hue.captains.singapura.tao.http.actor.ActorAction.SpawnSubActor}.
 */
public interface ActorFactory<R extends Message._Receive, S extends Message._Send>
        extends Actor._TypeRef<R, S> {

    Actor<R, S> create(ActorId self);
}
