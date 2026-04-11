package hue.captains.singapura.tao.http.actor.demo;

import hue.captains.singapura.tao.http.actor.Actor;
import hue.captains.singapura.tao.http.actor.ActorAction;
import hue.captains.singapura.tao.http.actor.ActorId;

import java.util.ArrayList;
import java.util.List;

@SuppressWarnings({"rawtypes", "unchecked"})
public class PongActor implements Actor<PingPongMessage.Ping> {

    public static final Actor._TypeRef<PingPongMessage.Ping, PongActor> ATR = new Actor._TypeRef<>() {};

    private final ActorId selfId;

    public PongActor(ActorId selfId) {
        this.selfId = selfId;
    }

    @Override
    public List<ActorAction> receive(List<PingPongMessage.Ping> messages) {
        var actions = new ArrayList<ActorAction>();
        for (var ping : messages) {
            System.out.println("[PongActor " + selfId + "] Received Ping from " + ping.sender() + ", sending Pong back");
            actions.add(new ActorAction.SendMessage<>(ping.sender(), new PingPongMessage.Pong(selfId)));
        }
        return actions;
    }
}
