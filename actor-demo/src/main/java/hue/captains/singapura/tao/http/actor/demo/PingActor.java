package hue.captains.singapura.tao.http.actor.demo;

import hue.captains.singapura.tao.http.actor.ActorAction;
import hue.captains.singapura.tao.http.actor.ActorRef;
import hue.captains.singapura.tao.http.actor.frontier.FrontierActor;

import java.util.List;
import java.util.function.Consumer;

public class PingActor implements FrontierActor<PingPongMessage.Pong, PingPongMessage.Ping> {

    private final Consumer<ActorAction.SendMessage<PingPongMessage.Ping>> listener;
    private final ActorRef pongRef;
    private final ActorRef selfRef;

    private PingActor(Consumer<ActorAction.SendMessage<PingPongMessage.Ping>> listener,
                      ActorRef pongRef, ActorRef selfRef) {
        this.listener = listener;
        this.pongRef = pongRef;
        this.selfRef = selfRef;
    }

    /** Simulates an external event (timer tick) that generates a Ping. */
    public void sendPing() {
        System.out.println("[PingActor " + selfRef + "] Sending Ping -> " + pongRef);
        listener.accept(new ActorAction.SendMessage<>(pongRef, new PingPongMessage.Ping(selfRef)));
    }

    @Override
    public List<ActorAction> receive(List<PingPongMessage.Pong> messages) {
        for (var pong : messages) {
            System.out.println("[PingActor " + selfRef + "] Received Pong from " + pong.sender());
        }
        return List.of();
    }

    public static FrontierActor._Constructor<PingPongMessage.Pong, PingPongMessage.Ping, PingActor>
    constructor(ActorRef pongRef, ActorRef selfRef) {
        return listener -> new PingActor(listener, pongRef, selfRef);
    }
}
