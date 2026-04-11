package hue.captains.singapura.tao.http.rps2;

import hue.captains.singapura.tao.http.actor.Actor;
import hue.captains.singapura.tao.http.actor.ActorAction;
import hue.captains.singapura.tao.http.actor.ActorId;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Matchmaking actor. Collects players and pairs them into games.
 * <p>
 * When two players are waiting, spawns a {@link GameActor} with an
 * {@link GameMessage.Init} message containing both player identities.
 */
public class LobbyActor implements Actor<LobbyMessage> {

    public static final Actor._TypeRef<LobbyMessage, LobbyActor> ATR = new Actor._TypeRef<>() {};

    private record WaitingPlayer(ActorId id, String name) {}

    private final Deque<WaitingPlayer> waitingPlayers = new ArrayDeque<>();

    @Override
    public List<ActorAction> receive(List<LobbyMessage> messages) {
        var actions = new ArrayList<ActorAction>();

        for (var msg : messages) {
            switch (msg) {
                case LobbyMessage.Join join -> {
                    waitingPlayers.add(new WaitingPlayer(join.playerId(), join.playerName()));
                    System.out.printf("[Lobby] %s joined. Waiting: %d%n",
                            join.playerName(), waitingPlayers.size());

                    if (waitingPlayers.size() >= 2) {
                        var playerA = waitingPlayers.poll();
                        var playerB = waitingPlayers.poll();

                        System.out.printf("[Lobby] Matched: %s vs %s%n",
                                playerA.name(), playerB.name());

                        actions.add(new ActorAction.SpawnSubActor<>(
                                GameActor.factory(),
                                List.of(new GameMessage.Init(
                                        playerA.id(), playerA.name(),
                                        playerB.id(), playerB.name()))));
                    }
                }
            }
        }

        return actions;
    }
}
