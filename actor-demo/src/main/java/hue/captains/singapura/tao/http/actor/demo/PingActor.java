package hue.captains.singapura.tao.http.actor.demo;

import hue.captains.singapura.tao.http.actor.Actor;
import hue.captains.singapura.tao.http.actor.ActorAction;
import hue.captains.singapura.tao.http.actor.ActorId;
import hue.captains.singapura.tao.http.actor.frontier.FrontierActor;

import java.util.List;
import java.util.function.Consumer;

@SuppressWarnings({"rawtypes", "unchecked"})
public class PingActor implements FrontierActor<PingPongMessage.Pong> {

    public static final Actor._TypeRef<PingPongMessage.Pong, PingActor> ATR = new Actor._TypeRef<>() {};

    private final Consumer<ActorAction.SendMessage<?,?>> listener;
    private final ActorId pongId;
    private final ActorId selfId;

    private PingActor(Consumer<ActorAction.SendMessage<?,?>> listener,
                      ActorId pongId, ActorId selfId) {
        this.listener = listener;
        this.pongId = pongId;
        this.selfId = selfId;
    }

    /** Simulates an external event (timer tick) that generates a Ping. */
    public void sendPing() {
        System.out.println("[PingActor " + selfId + "] Sending Ping -> " + pongId);
        listener.accept(new ActorAction.SendMessage<>(pongId, new PingPongMessage.Ping(selfId)));
    }

    @Override
    public List<ActorAction> receive(List<PingPongMessage.Pong> messages) {
        for (var pong : messages) {
            System.out.println("[PingActor " + selfId + "] Received Pong from " + pong.sender());
        }
        return List.of();
    }

    public static FrontierActor._Constructor<PingPongMessage.Pong, PingActor>
    constructor(ActorId pongId, ActorId selfId) {
        return listener -> new PingActor(listener, pongId, selfId);
    }
}
