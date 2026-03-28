package hue.captains.singapura.tao.http.actor.frontier;

import hue.captains.singapura.tao.http.actor.Actor;
import hue.captains.singapura.tao.http.actor.ActorAction;
import hue.captains.singapura.tao.http.actor.Message;

import java.util.function.Consumer;

/**
 * Frontier actors deal with external system, which will typically send in event without any control of current actor system. <br>
 * Therefore, we need to have a callback style listener to response the external event.
 * The actor's responsibility here is to translate external event/messages into an explicitly typed internal message.
 * It also acts as a regular actor to receive message from other actors and in most cases will be responsible to translate internal messages
 * to external ones and send out.
 * @param <R>
 * @param <S>
 */
public interface FrontierActor<R extends Message._Receive, S extends Message._Send> extends Actor<R, S> {

    /**
     * Adding a separate interface to make sure the FrontierActor is structurally immutable. <br>
     * i.e. once constructed, the listeners (which is typically managed by the actor system) should be fixed.
     * @param <R>
     * @param <S>
     * @param <A>
     */
    interface _Constructor<R extends Message._Receive, S extends Message._Send, A extends FrontierActor<R,S>>{
        /**
         * @param listeners to consume message received from external systems
         * @return
         */
        A construct(Consumer<ActorAction.SendMessage<S>> listeners);
    }
}
