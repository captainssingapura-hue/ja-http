package hue.captains.singapura.tao.http.vertx.demo.pubsub;

import hue.captains.singapura.tao.http.actor.pubsub.TopicActor;
import hue.captains.singapura.tao.http.actor.pubsub.TopicManagerActor;
import hue.captains.singapura.tao.http.actor.pubsub.TopicManagerMessage;
import hue.captains.singapura.tao.http.vertx.demo.pubsub.rps.LobbyActor;
import io.vertx.core.Vertx;

/**
 * WebSocket-based pub-sub demo using Vert.x.
 * <p>
 * Starts a WebSocket server on port 8081. Clients connect and interact via JSON frames:
 * <pre>
 *   {"action": "list"}                                    → list available topics
 *   {"action": "subscribe", "topic": "prices"}            → subscribe to a topic
 *   {"action": "unsubscribe", "topic": "prices"}          → unsubscribe from a topic
 *   {"action": "publish", "topic": "prices", "payload": "AAPL 189.50"} → publish to a topic
 * </pre>
 *
 * Test with: websocat ws://localhost:8081/pubsub
 */
public class PubSubWsDemo {

    public static void main(String[] args) {
        var vertx = Vertx.vertx();
        var actorSystem = new VertxActorSystem(vertx);

        // --- Set up the topic branch ---
        var topicManagerRef = actorSystem.allocateRef("topicManager");
        actorSystem.register(topicManagerRef, new TopicManagerActor());

        // Pre-create topics
        var pricesRef = actorSystem.allocateRef("topic:prices");
        actorSystem.register(pricesRef, new TopicActor<WsMessage.TopicData>());
        actorSystem.inject(topicManagerRef,
            new TopicManagerMessage.RegisterTopic("prices", pricesRef));

        var newsRef = actorSystem.allocateRef("topic:news");
        actorSystem.register(newsRef, new TopicActor<WsMessage.TopicData>());
        actorSystem.inject(topicManagerRef,
            new TopicManagerMessage.RegisterTopic("news", newsRef));

        // --- Set up the RPS lobby ---
        var lobbyRef = actorSystem.allocateRef("lobby");
        actorSystem.register(lobbyRef, new LobbyActor(actorSystem, topicManagerRef));

        // --- Set up the lead actor ---
        var leadRef = actorSystem.allocateRef("lead");
        var leadActor = actorSystem.registerFrontier(leadRef,
            WsLeadActor.constructor(actorSystem, topicManagerRef, lobbyRef));

        // --- Start WebSocket server ---
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
                System.out.println("Commands (JSON text frames):");
                System.out.println("  {\"action\": \"list\"}");
                System.out.println("  {\"action\": \"subscribe\", \"topic\": \"prices\"}");
                System.out.println("  {\"action\": \"publish\", \"topic\": \"prices\", \"payload\": \"AAPL 189.50\"}");
                System.out.println("  {\"action\": \"unsubscribe\", \"topic\": \"prices\"}");
                System.out.println();
                System.out.println("Rock-Paper-Scissors:");
                System.out.println("  {\"action\": \"rps_join\", \"name\": \"Alice\"}");
                System.out.println("  {\"action\": \"rps_move\", \"gameId\": \"G1\", \"move\": \"ROCK\"}");
            })
            .onFailure(err -> {
                System.err.println("Failed to start: " + err.getMessage());
                System.exit(1);
            });
    }
}
