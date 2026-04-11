package hue.captains.singapura.tao.http.vertx.demo;

import hue.captains.singapura.tao.http.actor.ActorId;
import hue.captains.singapura.tao.http.actor.system.EventLoopActorSystem;
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
@SuppressWarnings({"rawtypes", "unchecked"})
public class CombinedHostDemo {

    public static void main(String[] args) {
        var actorSystem = new EventLoopActorSystem();
        var host = new VertxCombinedHost(actorSystem, new EchoActionRegistry(), 8080);

        // --- Topic Manager ---
        var topicManagerId = ActorId.allocate(null,"topicManager");
        actorSystem.register(topicManagerId, new TopicManagerActor());

        // --- Topics ---
        var pricesId = ActorId.allocate(null,"topic:prices");
        actorSystem.register(pricesId, new TopicActor<TopicPayload>());
        actorSystem.inject(topicManagerId,
            new TopicManagerMessage.RegisterTopic("prices", pricesId));

        var newsId = ActorId.allocate(null,"topic:news");
        actorSystem.register(newsId, new TopicActor<TopicPayload>());
        actorSystem.inject(topicManagerId,
            new TopicManagerMessage.RegisterTopic("news", newsId));

        // --- RPS lobby ---
        var lobbyTopicId = ActorId.allocate(null,"topic:lobby");
        actorSystem.register(lobbyTopicId, new TopicActor<TopicPayload>());
        actorSystem.inject(topicManagerId,
            new TopicManagerMessage.RegisterTopic("lobby", lobbyTopicId));

        var lobbyId = ActorId.allocate(null,"lobby");
        actorSystem.register(lobbyId, new LobbyActor(actorSystem, topicManagerId, lobbyTopicId));
        actorSystem.inject(lobbyTopicId, new TopicMessage.Subscribe<>(lobbyId));

        // --- Start ---
        actorSystem.start();
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
