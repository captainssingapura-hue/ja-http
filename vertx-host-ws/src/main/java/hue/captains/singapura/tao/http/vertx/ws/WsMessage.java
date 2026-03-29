package hue.captains.singapura.tao.http.vertx.ws;

import hue.captains.singapura.tao.http.actor.ActorRef;
import hue.captains.singapura.tao.http.actor.Message;

/**
 * Internal messages for the WebSocket pub-sub bridge.
 * <p>
 * These are WebSocket-layer concerns only: connection lifecycle and raw frame delivery.
 * Topic data flows through {@link hue.captains.singapura.tao.http.actor.pubsub.TopicPayload}.
 */
public sealed interface WsMessage extends Message._Receive, Message._Send
        permits WsMessage.Init,
                WsMessage.IncomingFrame,
                WsMessage.Disconnected {

    /** Sent to a session actor after registration, providing the topic manager ref. */
    record Init(ActorRef topicManagerRef) implements WsMessage {}

    /** A text frame received from the WebSocket client. */
    record IncomingFrame(String text) implements WsMessage {}

    /** The WebSocket connection was closed. */
    record Disconnected() implements WsMessage {}
}
