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
        var topicManagerId = actorSystem.allocateId("topicManager");
        actorSystem.register(topicManagerId, new TopicManagerActor());

        // --- Topics ---
        var pricesId = actorSystem.allocateId("topic:prices");
        actorSystem.register(pricesId, new TopicActor<TopicPayload>());
        actorSystem.inject(topicManagerId,
            new TopicManagerMessage.RegisterTopic("prices", pricesId));

        var newsId = actorSystem.allocateId("topic:news");
        actorSystem.register(newsId, new TopicActor<TopicPayload>());
        actorSystem.inject(topicManagerId,
            new TopicManagerMessage.RegisterTopic("news", newsId));

        // --- RPS lobby ---
        var lobbyTopicId = actorSystem.allocateId("topic:lobby");
        actorSystem.register(lobbyTopicId, new TopicActor<TopicPayload>());
        actorSystem.inject(topicManagerId,
            new TopicManagerMessage.RegisterTopic("lobby", lobbyTopicId));

        var lobbyId = actorSystem.allocateId("lobby");
        actorSystem.register(lobbyId, new LobbyActor(actorSystem, topicManagerId, lobbyTopicId));
        actorSystem.inject(lobbyTopicId, new TopicMessage.Subscribe<>(lobbyId));

        // --- Start ---
        host.start("/pubsub", topicManagerId)
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
