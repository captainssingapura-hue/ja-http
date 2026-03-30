package hue.captains.singapura.tao.http.actor.demo.pubsub;

import hue.captains.singapura.tao.http.actor.ActorAction;
import hue.captains.singapura.tao.http.actor.ActorId;
import hue.captains.singapura.tao.http.actor.Message;
import hue.captains.singapura.tao.http.actor.demo.SingleThreadActorSystem;
import hue.captains.singapura.tao.http.actor.pubsub.TopicActor;
import hue.captains.singapura.tao.http.actor.pubsub.TopicManagerActor;
import hue.captains.singapura.tao.http.actor.pubsub.TopicManagerMessage;

/**
 * Demonstrates the pub-sub actor hierarchy using the single-threaded actor system.
 * <p>
 * Scenario:
 * <ol>
 *   <li>Set up Topic Manager and a "prices" topic</li>
 *   <li>Two clients (Alice, Bob) connect as sessions</li>
 *   <li>Both discover topics and subscribe to "prices"</li>
 *   <li>Alice publishes a price update — both receive it</li>
 *   <li>Bob unsubscribes — next update only reaches Alice</li>
 *   <li>Alice disconnects — cleanup verified</li>
 * </ol>
 */
public class PubSubDemo {

    public static void main(String[] args) {
        var system = new SingleThreadActorSystem();

        // --- Set up the topic branch ---
        var topicManagerId = system.allocateId("topicManager");
        system.register(topicManagerId, new TopicManagerActor());

        var pricesTopicId = system.allocateId("topic:prices");
        system.register(pricesTopicId, new TopicActor<PriceUpdate>());

        // Register the topic with the manager
        send(system, topicManagerId, new TopicManagerMessage.RegisterTopic("prices", pricesTopicId));
        system.processMailbox();

        System.out.println("=== Pub-Sub Demo ===\n");

        // --- Simulate two client sessions ---
        var aliceId = system.allocateId("session:alice");
        system.register(aliceId, new DemoSessionActor(aliceId, "alice", null, topicManagerId));

        var bobId = system.allocateId("session:bob");
        system.register(bobId, new DemoSessionActor(bobId, "bob", null, topicManagerId));

        // --- Step 1: Discover topics ---
        System.out.println("--- Step 1: Discover topics ---");
        send(system, aliceId, new DemoCommand.ListTopics());
        send(system, bobId, new DemoCommand.ListTopics());
        system.processMailbox();
        System.out.println();

        // --- Step 2: Both subscribe to "prices" ---
        System.out.println("--- Step 2: Subscribe to 'prices' ---");
        send(system, aliceId, new DemoCommand.Subscribe("prices"));
        send(system, bobId, new DemoCommand.Subscribe("prices"));
        system.processMailbox();
        System.out.println();

        // --- Step 3: Alice publishes a price update ---
        System.out.println("--- Step 3: Alice publishes AAPL @ 189.50 ---");
        send(system, aliceId, new DemoCommand.Publish("prices", new PriceUpdate("AAPL", 189.50)));
        system.processMailbox();
        System.out.println();

        // --- Step 4: Bob unsubscribes ---
        System.out.println("--- Step 4: Bob unsubscribes ---");
        send(system, bobId, new DemoCommand.Unsubscribe("prices"));
        system.processMailbox();
        System.out.println();

        // --- Step 5: Another price update — only Alice should receive ---
        System.out.println("--- Step 5: Alice publishes GOOG @ 142.30 (Bob unsubscribed) ---");
        send(system, aliceId, new DemoCommand.Publish("prices", new PriceUpdate("GOOG", 142.30)));
        system.processMailbox();
        System.out.println();

        // --- Step 6: Alice disconnects ---
        System.out.println("--- Step 6: Alice disconnects ---");
        send(system, aliceId, new DemoCommand.Disconnect());
        system.processMailbox();
        System.out.println();

        System.out.println("Active actors: " + system.actorCount());
        System.out.println("\n=== Done ===");
    }

    /** Helper to enqueue a message to an actor via the system's mailbox. */
    @SuppressWarnings("unchecked")
    private static <M extends Message._Receive & Message._Send> void send(
            SingleThreadActorSystem system, ActorId target, M message) {
        // Use a temporary frontier-style injection: directly place an envelope in the mailbox.
        // In a real system, this would come from a FrontierActor's external listener.
        system.inject(target, message);
    }
}
