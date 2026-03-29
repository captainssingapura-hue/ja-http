package hue.captains.singapura.tao.http.vertx.demo.pubsub.rps;

import hue.captains.singapura.tao.http.rps.LobbyActor;
import hue.captains.singapura.tao.http.actor.pubsub.TopicActor;
import hue.captains.singapura.tao.http.actor.pubsub.TopicManagerActor;
import hue.captains.singapura.tao.http.actor.pubsub.TopicManagerMessage;
import hue.captains.singapura.tao.http.actor.pubsub.TopicMessage;
import hue.captains.singapura.tao.http.actor.pubsub.TopicPayload;
import hue.captains.singapura.tao.http.vertx.ws.VertxActorSystem;
import hue.captains.singapura.tao.http.vertx.ws.WsLeadActor;
import io.vertx.core.Vertx;

/**
 * WebSocket-based pub-sub demo with Rock-Paper-Scissors.
 * <p>
 * The WebSocket bridge ({@link WsLeadActor}, {@code WsSessionActor}) is completely generic.
 * RPS is built on top using standard pub-sub operations:
 * <ul>
 *   <li>A "lobby" topic for matchmaking (join/leave/game_created)</li>
 *   <li>Per-game "game:G1" topics for gameplay (ready/move/round_start/round_result/game_over)</li>
 * </ul>
 * The lobby and game actors are regular actors that subscribe to topics and communicate
 * through {@link TopicPayload} messages — no special WebSocket knowledge needed.
 *
 * <p>Test with: {@code websocat ws://localhost:8081/pubsub}
 */
public class PubSubWsDemo {

    public static void main(String[] args) {
        var vertx = Vertx.vertx();
        var actorSystem = new VertxActorSystem(vertx);

        // --- Topic Manager ---
        var topicManagerRef = actorSystem.allocateRef("topicManager");
        actorSystem.register(topicManagerRef, new TopicManagerActor());

        // --- Pre-create topics ---
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
        // Lobby subscribes to the lobby topic to receive join/leave requests
        actorSystem.inject(lobbyTopicRef, new TopicMessage.Subscribe<>(lobbyRef));

        // --- WebSocket server (generic bridge) ---
        var leadRef = actorSystem.allocateRef("lead");
        var leadActor = actorSystem.registerFrontier(leadRef,
            WsLeadActor.constructor(actorSystem, topicManagerRef));

        vertx.createHttpServer()
            .webSocketHandler(ws -> {
                if ("/pubsub".equals(ws.path())) {
                    leadActor.onNewConnection(ws);
                } else {
                    ws.reject();
                }
            })
            .listen(8081)
            .onSuccess(server -> {
                System.out.println("Pub-Sub WebSocket server listening on port " + server.actualPort());
                System.out.println();
                System.out.println("Connect with: websocat ws://localhost:8081/pubsub");
                System.out.println();
                System.out.println("Pub-Sub commands:");
                System.out.println("  {\"action\": \"list\"}");
                System.out.println("  {\"action\": \"subscribe\", \"topic\": \"prices\"}");
                System.out.println("  {\"action\": \"publish\", \"topic\": \"prices\", \"payload\": {\"price\": 189.50}}");
                System.out.println("  {\"action\": \"unsubscribe\", \"topic\": \"prices\"}");
                System.out.println();
                System.out.println("Rock-Paper-Scissors (via pub-sub):");
                System.out.println("  {\"action\": \"subscribe\", \"topic\": \"lobby\"}");
                System.out.println("  {\"action\": \"publish\", \"topic\": \"lobby\", \"payload\": {\"type\": \"join\", \"name\": \"Alice\"}}");
                System.out.println("  (after game_created, refresh and subscribe to game topic)");
                System.out.println("  {\"action\": \"list\"}");
                System.out.println("  {\"action\": \"subscribe\", \"topic\": \"game:G1\"}");
                System.out.println("  {\"action\": \"publish\", \"topic\": \"game:G1\", \"payload\": {\"type\": \"ready\", \"name\": \"Alice\"}}");
                System.out.println("  {\"action\": \"publish\", \"topic\": \"game:G1\", \"payload\": {\"type\": \"move\", \"name\": \"Alice\", \"move\": \"ROCK\"}}");
            })
            .onFailure(err -> {
                System.err.println("Failed to start: " + err.getMessage());
                System.exit(1);
            });
    }
}
