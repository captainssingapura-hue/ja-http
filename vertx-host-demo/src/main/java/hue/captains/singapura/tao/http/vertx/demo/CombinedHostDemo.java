package hue.captains.singapura.tao.http.vertx.demo;

import hue.captains.singapura.tao.http.actor.pubsub.TopicActor;
import hue.captains.singapura.tao.http.actor.pubsub.TopicManagerActor;
import hue.captains.singapura.tao.http.actor.pubsub.TopicManagerMessage;
import hue.captains.singapura.tao.http.actor.pubsub.TopicMessage;
import hue.captains.singapura.tao.http.actor.pubsub.TopicPayload;
import hue.captains.singapura.tao.http.rps.LobbyActor;
import hue.captains.singapura.tao.http.vertx.VertxCombinedHost;

/**
 * Demo of the combined host: HTTP actions + WebSocket pub-sub on a single port.
 * <p>
 * HTTP actions (port 8080):
 * <pre>
 *   curl "http://localhost:8080/echo?name=hello"
 *   curl -X POST -H "Content-Type: application/json" -d '{"message":"hi"}' http://localhost:8080/echo
 * </pre>
 * WebSocket pub-sub (same port):
 * <pre>
 *   websocat ws://localhost:8080/pubsub
 * </pre>
 */
public class CombinedHostDemo {

    public static void main(String[] args) {
        var host = new VertxCombinedHost(new EchoActionRegistry(), 8080);
        var actorSystem = host.actorSystem();

        // --- Topic Manager ---
        var topicManagerRef = actorSystem.allocateRef("topicManager");
        actorSystem.register(topicManagerRef, new TopicManagerActor());

        // --- Topics ---
        var pricesRef = actorSystem.allocateRef("topic:prices");
        actorSystem.register(pricesRef, new TopicActor<TopicPayload>());
        actorSystem.inject(topicManagerRef,
            new TopicManagerMessage.RegisterTopic("prices", pricesRef));

        var newsRef = actorSystem.allocateRef("topic:news");
        actorSystem.register(newsRef, new TopicActor<TopicPayload>());
        actorSystem.inject(topicManagerRef,
            new TopicManagerMessage.RegisterTopic("news", newsRef));

        // --- RPS lobby ---
        var lobbyTopicRef = actorSystem.allocateRef("topic:lobby");
        actorSystem.register(lobbyTopicRef, new TopicActor<TopicPayload>());
        actorSystem.inject(topicManagerRef,
            new TopicManagerMessage.RegisterTopic("lobby", lobbyTopicRef));

        var lobbyRef = actorSystem.allocateRef("lobby");
        actorSystem.register(lobbyRef, new LobbyActor(actorSystem, topicManagerRef, lobbyTopicRef));
        actorSystem.inject(lobbyTopicRef, new TopicMessage.Subscribe<>(lobbyRef));

        // --- Start ---
        host.start("/pubsub", topicManagerRef)
            .onSuccess(server -> {
                System.out.println("Combined server listening on port " + server.actualPort());
                System.out.println();
                System.out.println("HTTP actions:");
                System.out.println("  curl \"http://localhost:8080/echo?name=hello\"");
                System.out.println("  curl -X POST -H \"Content-Type: application/json\" "
                    + "-d '{\"message\":\"hi\"}' http://localhost:8080/echo");
                System.out.println();
                System.out.println("WebSocket pub-sub:");
                System.out.println("  websocat ws://localhost:8080/pubsub");
            })
            .onFailure(err -> {
                System.err.println("Failed to start: " + err.getMessage());
                System.exit(1);
            });
    }
}
