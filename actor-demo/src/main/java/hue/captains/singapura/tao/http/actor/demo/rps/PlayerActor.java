package hue.captains.singapura.tao.http.actor.demo.rps;

import hue.captains.singapura.tao.http.actor.Actor;
import hue.captains.singapura.tao.http.actor.ActorAction;
import hue.captains.singapura.tao.http.actor.ActorRef;
import hue.captains.singapura.tao.http.actor.demo.ActorFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public class PlayerActor implements Actor<RpsMessage, RpsMessage> {

    private static final RpsMessage.Choice[] CHOICES = RpsMessage.Choice.values();

    private final ActorRef selfRef;
    private final String uuid = UUID.randomUUID().toString().substring(0, 8);
    private String playerId;
    private ActorRef gameRef;

    private PlayerActor(ActorRef selfRef) {
        this.selfRef = selfRef;
    }

    @Override
    public List<ActorAction> receive(List<RpsMessage> messages) {
        var actions = new ArrayList<ActorAction>();
        for (var msg : messages) {
            switch (msg) {
                case RpsMessage.AssignId assign -> {
                    this.playerId = assign.playerId();
                    this.gameRef = assign.gameRef();
                    actions.add(new ActorAction.SendMessage<>(
                            gameRef, new RpsMessage.PlayerReady(playerId, uuid, selfRef)));
                }
                case RpsMessage.StartRound round -> {
                    var choice = CHOICES[ThreadLocalRandom.current().nextInt(CHOICES.length)];
                    actions.add(new ActorAction.SendMessage<>(
                            gameRef, new RpsMessage.PlayerChoice(playerId, round.roundId(), choice)));
                }
                case RpsMessage.Shutdown ignored -> {
                    System.out.println("  [Player " + playerId + " (" + uuid + ")] Shutting down");
                    actions.add(new ActorAction.SelfTerminate());
                }
                default -> { /* ignore messages not meant for players */ }
            }
        }
        return actions;
    }

    public static ActorFactory<RpsMessage, RpsMessage> factory() {
        return PlayerActor::new;
    }
}
