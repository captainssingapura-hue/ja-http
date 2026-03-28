package hue.captains.singapura.tao.http.actor.demo.rps;

import hue.captains.singapura.tao.http.actor.ActorAction;
import hue.captains.singapura.tao.http.actor.ActorRef;
import hue.captains.singapura.tao.http.actor.frontier.FrontierActor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public class GameActor implements FrontierActor<RpsMessage, RpsMessage> {

    private final Consumer<ActorAction.SendMessage<RpsMessage>> listener;
    private final ActorRef selfRef;
    private final int totalSets;
    private final int roundsPerSet;

    // Per-set state
    private final Map<String, ActorRef> players = new LinkedHashMap<>();
    private final Map<String, String> playerUuids = new LinkedHashMap<>();
    private final Map<String, RpsMessage.Choice> pendingChoices = new LinkedHashMap<>();
    private int setsCompleted = 0;
    private int roundsInSet = 0;
    private int setAWins = 0;
    private int setBWins = 0;
    private int setDraws = 0;

    // Overall stats
    private int totalAWins = 0;
    private int totalBWins = 0;
    private int totalDraws = 0;

    private GameActor(Consumer<ActorAction.SendMessage<RpsMessage>> listener,
                      ActorRef selfRef, int totalSets, int roundsPerSet) {
        this.listener = listener;
        this.selfRef = selfRef;
        this.totalSets = totalSets;
        this.roundsPerSet = roundsPerSet;
    }

    /** Called by the external clock to advance the game. */
    public void tick() {
        listener.accept(new ActorAction.SendMessage<>(selfRef, new RpsMessage.Tick()));
    }

    public boolean isFinished() {
        return setsCompleted >= totalSets;
    }

    @Override
    public List<ActorAction> receive(List<RpsMessage> messages) {
        var actions = new ArrayList<ActorAction>();
        for (var msg : messages) {
            switch (msg) {
                case RpsMessage.Tick ignored -> actions.addAll(handleTick());
                case RpsMessage.PlayerReady ready -> {
                    players.put(ready.playerId(), ready.playerRef());
                    playerUuids.put(ready.playerId(), ready.uuid());
                    if (players.size() == 2) {
                        System.out.printf("  Players: A (%s)  vs  B (%s)%n%n",
                                playerUuids.get("A"), playerUuids.get("B"));
                    }
                }
                case RpsMessage.PlayerChoice choice -> {
                    pendingChoices.put(choice.playerId(), choice.choice());
                    if (pendingChoices.size() == 2) {
                        resolveRound(choice.roundId(), actions);
                    }
                }
                default -> { /* ignore messages not meant for the game actor */ }
            }
        }
        return actions;
    }

    private List<ActorAction> handleTick() {
        if (setsCompleted >= totalSets) {
            return List.of();
        }
        if (players.size() < 2) {
            // Spawn fresh players for this set
            int setNumber = setsCompleted + 1;
            System.out.println("=== Set " + setNumber + " of " + totalSets + " ===");
            return List.of(
                    new ActorAction.SpawnSubActor<>(
                            PlayerActor.factory(),
                            List.of(new RpsMessage.AssignId("A", selfRef))),
                    new ActorAction.SpawnSubActor<>(
                            PlayerActor.factory(),
                            List.of(new RpsMessage.AssignId("B", selfRef)))
            );
        }
        if (roundsInSet < roundsPerSet) {
            int roundId = roundsInSet + 1;
            var actions = new ArrayList<ActorAction>();
            for (var entry : players.entrySet()) {
                actions.add(new ActorAction.SendMessage<>(
                        entry.getValue(), new RpsMessage.StartRound(roundId)));
            }
            return actions;
        }
        return List.of();
    }

    private void resolveRound(int roundId, List<ActorAction> actions) {
        var choiceA = pendingChoices.get("A");
        var choiceB = pendingChoices.get("B");
        pendingChoices.clear();
        roundsInSet++;

        String result;
        if (choiceA.beats(choiceB)) {
            setAWins++;
            result = "A wins";
        } else if (choiceB.beats(choiceA)) {
            setBWins++;
            result = "B wins";
        } else {
            setDraws++;
            result = "Draw";
        }
        System.out.printf("  Round %3d: A=%-8s B=%-8s → %s%n",
                roundId, choiceA, choiceB, result);

        if (roundsInSet >= roundsPerSet) {
            endSet(actions);
        }
    }

    private void endSet(List<ActorAction> actions) {
        setsCompleted++;

        System.out.println();
        System.out.println("  Set " + setsCompleted + " results: " +
                "A=" + setAWins + "  B=" + setBWins + "  Draws=" + setDraws);

        // Accumulate overall stats
        totalAWins += setAWins;
        totalBWins += setBWins;
        totalDraws += setDraws;

        // Shut down current players
        var shutdown = new RpsMessage.Shutdown();
        for (var playerRef : players.values()) {
            actions.add(new ActorAction.SendMessage<>(playerRef, shutdown));
        }

        // Reset per-set state for the next set
        players.clear();
        playerUuids.clear();
        roundsInSet = 0;
        setAWins = 0;
        setBWins = 0;
        setDraws = 0;

        if (setsCompleted >= totalSets) {
            System.out.println();
            System.out.println("========== Overall Results ==========");
            int totalRounds = totalAWins + totalBWins + totalDraws;
            System.out.println("Total rounds : " + totalRounds);
            System.out.println("Player A wins: " + totalAWins);
            System.out.println("Player B wins: " + totalBWins);
            System.out.println("Draws        : " + totalDraws);
            actions.add(new ActorAction.SelfTerminate());
        }

        System.out.println();
    }

    public static FrontierActor._Constructor<RpsMessage, RpsMessage, GameActor>
    constructor(ActorRef selfRef, int totalSets, int roundsPerSet) {
        return listener -> new GameActor(listener, selfRef, totalSets, roundsPerSet);
    }
}
