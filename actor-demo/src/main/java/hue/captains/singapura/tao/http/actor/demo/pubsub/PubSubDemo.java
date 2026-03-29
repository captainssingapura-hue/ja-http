package hue.captains.singapura.tao.http.actor.demo.pubsub;

import hue.captains.singapura.tao.http.actor.ActorAction;
import hue.captains.singapura.tao.http.actor.ActorRef;
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
        var topicManagerRef = system.allocateRef("topicManager");
        system.register(topicManagerRef, new TopicManagerActor());

        var pricesTopicRef = system.allocateRef("topic:prices");
        system.register(pricesTopicRef, new TopicActor<PriceUpdate>());

        // Register the topic with the manager
        send(system, topicManagerRef, new TopicManagerMessage.RegisterTopic("prices", pricesTopicRef));
        system.processMailbox();

        System.out.println("=== Pub-Sub Demo ===\n");

        // --- Simulate two client sessions ---
        var aliceRef = system.allocateRef("session:alice");
        system.register(aliceRef, new DemoSessionActor(aliceRef, "alice", null, topicManagerRef));

        var bobRef = system.allocateRef("session:bob");
        system.register(bobRef, new DemoSessionActor(bobRef, "bob", null, topicManagerRef));

        // --- Step 1: Discover topics ---
        System.out.println("--- Step 1: Discover topics ---");
        send(system, aliceRef, new DemoCommand.ListTopics());
        send(system, bobRef, new DemoCommand.ListTopics());
        system.processMailbox();
        System.out.println();

        // --- Step 2: Both subscribe to "prices" ---
        System.out.println("--- Step 2: Subscribe to 'prices' ---");
        send(system, aliceRef, new DemoCommand.Subscribe("prices"));
        send(system, bobRef, new DemoCommand.Subscribe("prices"));
        system.processMailbox();
        System.out.println();

        // --- Step 3: Alice publishes a price update ---
        System.out.println("--- Step 3: Alice publishes AAPL @ 189.50 ---");
        send(system, aliceRef, new DemoCommand.Publish("prices", new PriceUpdate("AAPL", 189.50)));
        system.processMailbox();
        System.out.println();

        // --- Step 4: Bob unsubscribes ---
        System.out.println("--- Step 4: Bob unsubscribes ---");
        send(system, bobRef, new DemoCommand.Unsubscribe("prices"));
        system.processMailbox();
        System.out.println();

        // --- Step 5: Another price update — only Alice should receive ---
        System.out.println("--- Step 5: Alice publishes GOOG @ 142.30 (Bob unsubscribed) ---");
        send(system, aliceRef, new DemoCommand.Publish("prices", new PriceUpdate("GOOG", 142.30)));
        system.processMailbox();
        System.out.println();

        // --- Step 6: Alice disconnects ---
        System.out.println("--- Step 6: Alice disconnects ---");
        send(system, aliceRef, new DemoCommand.Disconnect());
        system.processMailbox();
        System.out.println();

        System.out.println("Active actors: " + system.actorCount());
        System.out.println("\n=== Done ===");
    }

    /** Helper to enqueue a message to an actor via the system's mailbox. */
    @SuppressWarnings("unchecked")
    private static <M extends Message._Receive & Message._Send> void send(
            SingleThreadActorSystem system, ActorRef target, M message) {
        // Use a temporary frontier-style injection: directly place an envelope in the mailbox.
        // In a real system, this would come from a FrontierActor's external listener.
        system.inject(target, message);
    }
}
