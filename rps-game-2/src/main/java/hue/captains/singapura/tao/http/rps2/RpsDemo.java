package hue.captains.singapura.tao.http.rps2;

import hue.captains.singapura.tao.http.actor.system.EventLoopActorSystem;

public class RpsDemo {

    public static void main(String[] args) throws InterruptedException {
        var system = new EventLoopActorSystem();

        // Create lobby
        var lobbyId = system.allocateId(LobbyActor.ATR, "lobby");
        system.register(lobbyId, new LobbyActor());

        // Create players
        var aliceId = system.allocateId(PlayerActor.ATR, "player");
        system.register(aliceId, new PlayerActor(aliceId, "Alice"));

        var bobId = system.allocateId(PlayerActor.ATR, "player");
        system.register(bobId, new PlayerActor(bobId, "Bob"));

        System.out.println("=== Typed RPS Demo (first to 3 wins) ===");
        System.out.println();

        // Players join the lobby
        system.inject(lobbyId, new LobbyMessage.Join(aliceId, "Alice"));
        system.inject(lobbyId, new LobbyMessage.Join(bobId, "Bob"));

        // Start the event loop — game runs to completion asynchronously
        system.start();
        system.awaitIdle();

        System.out.println();
        System.out.println("Active actors: " + system.actorCount());

        system.shutdown();
    }
}
