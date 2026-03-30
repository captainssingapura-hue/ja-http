package hue.captains.singapura.tao.http.actor.demo;

import hue.captains.singapura.tao.http.actor.ActorId;
import hue.captains.singapura.tao.http.actor.Message;

public sealed interface PingPongMessage extends Message
        permits PingPongMessage.Ping, PingPongMessage.Pong {

    record Ping(ActorId sender) implements PingPongMessage, Message._Receive, Message._Send {}

    record Pong(ActorId sender) implements PingPongMessage, Message._Receive, Message._Send {}
}
