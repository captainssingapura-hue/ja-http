package hue.captains.singapura.tao.http.vertx.demo.pubsub;

import hue.captains.singapura.tao.http.actor.ActorRef;
import hue.captains.singapura.tao.http.actor.Message;

import java.util.Map;

/**
 * Messages flowing through the WebSocket pub-sub system.
 * A single message type used by both the session actor and as topic payloads,
 * avoiding the cross-type delivery problem.
 */
public sealed interface WsMessage extends Message._Receive, Message._Send
        permits WsMessage.Init,
                WsMessage.IncomingFrame,
                WsMessage.TopicData,
                WsMessage.TopicListResult,
                WsMessage.Disconnected {

    /** Sent to a session actor after it is registered, providing the refs it needs. */
    record Init(ActorRef topicManagerRef, ActorRef lobbyRef) implements WsMessage {}

    /** A text frame received from the WebSocket client. */
    record IncomingFrame(String text) implements WsMessage {}

    /** A message received from a subscribed topic, to be forwarded to the client. */
    record TopicData(String topicName, String payload) implements WsMessage {}

    /** Topic list response from the Topic Manager (forwarded to client). */
    record TopicListResult(Map<String, ActorRef> topics) implements WsMessage {}

    /** The WebSocket connection was closed. */
    record Disconnected() implements WsMessage {}
}
