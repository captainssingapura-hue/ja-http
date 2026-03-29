package hue.captains.singapura.tao.http.actor.demo.pubsub;

import hue.captains.singapura.tao.http.actor.Message;

/**
 * A price update event — the payload type for the "prices" topic in the demo.
 * Implements both {@link Message._Receive} and {@link Message._Send} so it can
 * flow through topic fan-out and be received by any actor.
 */
public record PriceUpdate(String symbol, double price)
        implements Message, Message._Receive, Message._Send {}
