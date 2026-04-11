package hue.captains.singapura.tao.http.rps2;

import hue.captains.singapura.tao.http.actor.Actor;
import hue.captains.singapura.tao.http.actor.ActorAction;
import hue.captains.singapura.tao.http.actor.ActorId;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * A player that makes random moves.
 * <p>
 * Receives {@link PlayerMessage} from the game, sends {@link GameMessage} back.
 */
public class PlayerActor implements Actor<PlayerMessage> {

    public static final Actor._TypeRef<PlayerMessage, PlayerActor> ATR = new Actor._TypeRef<>() {};

    private static final Choice[] CHOICES = Choice.values();

    private final ActorId selfId;
    private final String name;
    private ActorId gameActorId;

    public PlayerActor(ActorId selfId, String name) {
        this.selfId = selfId;
        this.name = name;
    }

    @Override
    public List<ActorAction> receive(List<PlayerMessage> messages) {
        var actions = new ArrayList<ActorAction>();

        for (var msg : messages) {
            switch (msg) {
                case PlayerMessage.GameAssigned assigned -> {
                    this.gameActorId = assigned.gameActorId();
                    System.out.printf("[%s] Matched against %s%n", name, assigned.opponentName());
                    actions.add(new ActorAction.SendMessage<>(
                            gameActorId, new GameMessage.PlayerReady(selfId, name)));
                }

                case PlayerMessage.RoundStart roundStart -> {
                    var choice = CHOICES[ThreadLocalRandom.current().nextInt(CHOICES.length)];
                    actions.add(new ActorAction.SendMessage<>(
                            gameActorId, new GameMessage.PlayerMove(selfId, name, choice)));
                }

                case PlayerMessage.RoundResult result -> {
                    // Player could react to results here if needed
                }

                case PlayerMessage.GameOver gameOver -> {
                    var outcome = gameOver.winner().equals(name) ? "WON" : "LOST";
                    System.out.printf("[%s] Game over — %s! (%s %d - %s %d)%n",
                            name, outcome,
                            gameOver.playerA(), gameOver.scoreA(),
                            gameOver.playerB(), gameOver.scoreB());
                    actions.add(new ActorAction.SelfTerminate());
                }
            }
        }

        return actions;
    }
}
