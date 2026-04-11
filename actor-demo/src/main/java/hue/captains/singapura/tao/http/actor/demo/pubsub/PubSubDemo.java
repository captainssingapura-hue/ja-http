package hue.captains.singapura.tao.http.actor.demo.pubsub;

import hue.captains.singapura.tao.http.actor.ActorId;
import hue.captains.singapura.tao.http.actor.ActorSystem;
import hue.captains.singapura.tao.http.actor.Message;
import hue.captains.singapura.tao.http.actor.system.EventLoopActorSystem;
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
@SuppressWarnings({"rawtypes", "unchecked"})
public class PubSubDemo {

    public static void main(String[] args) throws InterruptedException {
        var system = new EventLoopActorSystem();

        // --- Set up the topic branch ---
        var topicManagerId = ActorId.allocate(null, "topicManager");
        system.register(topicManagerId, new TopicManagerActor());

        var pricesTopicId = ActorId.allocate(null, "topic:prices");
        system.register(pricesTopicId, new TopicActor<PriceUpdate>());

        // Register the topic with the manager
        send(system, topicManagerId, new TopicManagerMessage.RegisterTopic("prices", pricesTopicId));
        system.start();
        system.awaitIdle();

        System.out.println("=== Pub-Sub Demo ===\n");

        // --- Simulate two client sessions ---
        var aliceId = ActorId.allocate(null, "session:alice");
        system.register(aliceId, new DemoSessionActor(aliceId, "alice", null, topicManagerId));

        var bobId = ActorId.allocate(null, "session:bob");
        system.register(bobId, new DemoSessionActor(bobId, "bob", null, topicManagerId));

        // --- Step 1: Discover topics ---
        System.out.println("--- Step 1: Discover topics ---");
        send(system, aliceId, new DemoCommand.ListTopics());
        send(system, bobId, new DemoCommand.ListTopics());
        system.awaitIdle();
        System.out.println();

        // --- Step 2: Both subscribe to "prices" ---
        System.out.println("--- Step 2: Subscribe to 'prices' ---");
        send(system, aliceId, new DemoCommand.Subscribe("prices"));
        send(system, bobId, new DemoCommand.Subscribe("prices"));
        system.awaitIdle();
        System.out.println();

        // --- Step 3: Alice publishes a price update ---
        System.out.println("--- Step 3: Alice publishes AAPL @ 189.50 ---");
        send(system, aliceId, new DemoCommand.Publish("prices", new PriceUpdate("AAPL", 189.50)));
        system.awaitIdle();
        System.out.println();

        // --- Step 4: Bob unsubscribes ---
        System.out.println("--- Step 4: Bob unsubscribes ---");
        send(system, bobId, new DemoCommand.Unsubscribe("prices"));
        system.awaitIdle();
        System.out.println();

        // --- Step 5: Another price update — only Alice should receive ---
        System.out.println("--- Step 5: Alice publishes GOOG @ 142.30 (Bob unsubscribed) ---");
        send(system, aliceId, new DemoCommand.Publish("prices", new PriceUpdate("GOOG", 142.30)));
        system.awaitIdle();
        System.out.println();

        // --- Step 6: Alice disconnects ---
        System.out.println("--- Step 6: Alice disconnects ---");
        send(system, aliceId, new DemoCommand.Disconnect());
        system.awaitIdle();
        System.out.println();

        System.out.println("Active actors: " + system.actorCount());
        System.out.println("\n=== Done ===");
        system.shutdown();
    }

    /** Helper to enqueue a message to an actor via the system's mailbox. */
    @SuppressWarnings("unchecked")
    private static <M extends Message._Receive & Message._Send> void send(
            ActorSystem system, ActorId target, M message) {
        system.inject(target, message);
    }
}
