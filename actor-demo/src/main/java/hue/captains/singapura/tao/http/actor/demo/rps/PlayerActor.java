package hue.captains.singapura.tao.http.actor.demo.rps;

import hue.captains.singapura.tao.http.actor.Actor;
import hue.captains.singapura.tao.http.actor.ActorAction;
import hue.captains.singapura.tao.http.actor.ActorId;
import hue.captains.singapura.tao.http.actor.ActorFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public class PlayerActor implements Actor<RpsMessage> {

    private static final RpsMessage.Choice[] CHOICES = RpsMessage.Choice.values();

    public static final ActorFactory<RpsMessage, PlayerActor> ATR = PlayerActor::new;

    private final ActorId selfId;
    private final String uuid = UUID.randomUUID().toString().substring(0, 8);
    private String playerId;
    private ActorId gameActorId;

    private PlayerActor(ActorId selfId) {
        this.selfId = selfId;
    }

    @Override
    public List<ActorAction> receive(List<RpsMessage> messages) {
        var actions = new ArrayList<ActorAction>();
        for (var msg : messages) {
            switch (msg) {
                case RpsMessage.AssignId assign -> {
                    this.playerId = assign.playerId();
                    this.gameActorId = assign.gameActorId();
                    actions.add(new ActorAction.SendMessage<>(
                            gameActorId, new RpsMessage.PlayerReady(playerId, uuid, selfId)));
                }
                case RpsMessage.StartRound round -> {
                    var choice = CHOICES[ThreadLocalRandom.current().nextInt(CHOICES.length)];
                    actions.add(new ActorAction.SendMessage<>(
                            gameActorId, new RpsMessage.PlayerChoice(playerId, round.roundId(), choice)));
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

    public static ActorFactory<RpsMessage, PlayerActor> factory() {
        return PlayerActor::new;
    }
}
