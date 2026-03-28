package hue.captains.singapura.tao.http.actor;

import java.util.List;

public interface Actor<R extends Message._Receive, S extends Message._Send> {

    /**
     * We don't really care about how the actor works here.
     * Just the actor itself.
     */

    interface _TypeRef<R extends Message._Receive, S extends Message._Send>{}
    /**
     * The actor will directly process message received.
     * Actor system need to ensure this method is always called sequentially. <br>
     * We will disable the actor to interact with the actor system directly also. <br>
     * It should largely act functionally by return a list of action induced by the given message. <br>
     * We will give a list of messages to the actor by default as the Actor System will have no knowledge on how to <br>
     * optimize accumulated messages.
     * @param messages
     */
    List<ActorAction> receive(List<R> messages);

}
