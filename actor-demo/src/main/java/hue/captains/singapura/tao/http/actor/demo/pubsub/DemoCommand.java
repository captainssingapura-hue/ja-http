package hue.captains.singapura.tao.http.actor.demo.pubsub;

import hue.captains.singapura.tao.http.actor.Message;

/**
 * Simulated client commands for the demo.
 * In a real system these would arrive as WebSocket frames
 * and be translated by a FrontierActor.
 */
public sealed interface DemoCommand extends Message._Receive, Message._Send
        permits DemoCommand.Subscribe,
                DemoCommand.Unsubscribe,
                DemoCommand.Publish,
                DemoCommand.ListTopics,
                DemoCommand.Disconnect {

    record Subscribe(String topicName) implements DemoCommand {}
    record Unsubscribe(String topicName) implements DemoCommand {}
    record Publish(String topicName, Message payload) implements DemoCommand {}
    record ListTopics() implements DemoCommand {}
    record Disconnect() implements DemoCommand {}
}
