package hue.captains.singapura.tao.http.vertx.demo.pubsub.rps;

import hue.captains.singapura.tao.http.actor.Actor;
import hue.captains.singapura.tao.http.actor.ActorAction;
import hue.captains.singapura.tao.http.actor.ActorRef;
import hue.captains.singapura.tao.http.actor.Message;
import hue.captains.singapura.tao.http.actor.pubsub.TopicActor;
import hue.captains.singapura.tao.http.actor.pubsub.TopicManagerMessage;
import hue.captains.singapura.tao.http.vertx.demo.pubsub.VertxActorSystem;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Matchmaking actor. Queues players waiting for a game and pairs them.
 * When two players are waiting, creates a per-game topic + game actor and notifies both sessions.
 * <p>
 * Holds a reference to {@link VertxActorSystem} to dynamically create game infrastructure
 * (same pattern as WsLeadActor).
 */
public class LobbyActor implements Actor<Message._Receive, Message._Send> {

    private record PendingPlayer(ActorRef sessionRef, String playerName) {}

    private final VertxActorSystem actorSystem;
    private final ActorRef topicManagerRef;
    private final Deque<PendingPlayer> waitingPlayers = new ArrayDeque<>();
    private int gameCounter = 0;

    public LobbyActor(VertxActorSystem actorSystem, ActorRef topicManagerRef) {
        this.actorSystem = actorSystem;
        this.topicManagerRef = topicManagerRef;
    }

    @Override
    public List<ActorAction> receive(List<Message._Receive> messages) {
        var actions = new ArrayList<ActorAction>();

        for (var msg : messages) {
            switch (msg) {
                case RpsLobbyMessage.JoinRequest join -> {
                    waitingPlayers.add(new PendingPlayer(join.sessionRef(), join.playerName()));
                    System.out.printf("[Lobby] %s joined. Waiting: %d%n",
                        join.playerName(), waitingPlayers.size());

                    if (waitingPlayers.size() >= 2) {
                        var playerA = waitingPlayers.poll();
                        var playerB = waitingPlayers.poll();
                        actions.addAll(createGame(playerA, playerB));
                    }
                }

                case RpsLobbyMessage.LeaveRequest leave -> {
                    waitingPlayers.removeIf(p -> p.sessionRef().equals(leave.sessionRef()));
                    System.out.printf("[Lobby] Player left. Waiting: %d%n", waitingPlayers.size());
                }

                default -> {}
            }
        }

        return actions;
    }

    private List<ActorAction> createGame(PendingPlayer playerA, PendingPlayer playerB) {
        var actions = new ArrayList<ActorAction>();
        var gameId = "G" + (++gameCounter);

        // Create game topic
        var gameTopicRef = actorSystem.allocateRef("topic:game:" + gameId);
        actorSystem.register(gameTopicRef, new TopicActor<RpsGameMessage>());
        actorSystem.inject(topicManagerRef,
            new TopicManagerMessage.RegisterTopic("game:" + gameId, gameTopicRef));

        // Create game actor
        var gameActorRef = actorSystem.allocateRef("rps-game:" + gameId);
        actorSystem.register(gameActorRef, new RpsGameActor(gameActorRef));
        actorSystem.inject(gameActorRef, new RpsGameActor.RpsGameInit(
            gameId, gameTopicRef, topicManagerRef,
            playerA.playerName(), playerB.playerName()));

        System.out.printf("[Lobby] Matched: %s vs %s → Game %s%n",
            playerA.playerName(), playerB.playerName(), gameId);

        // Notify both sessions
        actions.add(new ActorAction.SendMessage<>(playerA.sessionRef(),
            new RpsLobbyMessage.GameCreated(gameId, gameTopicRef, playerB.playerName())));
        actions.add(new ActorAction.SendMessage<>(playerB.sessionRef(),
            new RpsLobbyMessage.GameCreated(gameId, gameTopicRef, playerA.playerName())));

        return actions;
    }
}
