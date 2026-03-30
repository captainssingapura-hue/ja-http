package hue.captains.singapura.tao.http.actor.demo.rps;

import hue.captains.singapura.tao.http.actor.demo.SingleThreadActorSystem;

public class RpsDemo {

    public static void main(String[] args) {
        var system = new SingleThreadActorSystem();
        var gameId = system.allocateId("game");
        var gameActor = system.registerFrontier(gameId,
                GameActor.constructor(gameId, 3, 180));

        System.out.println("=== Rock-Paper-Scissors Demo (3 sets × 200 rounds) ===");
        System.out.println();

        while (!gameActor.isFinished()) {
            gameActor.tick();
            system.processMailbox();
        }

        System.out.println("Active actors: " + system.actorCount());
    }
}
