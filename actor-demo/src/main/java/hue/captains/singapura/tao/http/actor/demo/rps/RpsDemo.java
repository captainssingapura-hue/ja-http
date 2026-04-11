package hue.captains.singapura.tao.http.actor.demo.rps;

import hue.captains.singapura.tao.http.actor.system.EventLoopActorSystem;

public class RpsDemo {

    public static void main(String[] args) throws InterruptedException {
        var system = new EventLoopActorSystem();
        var gameId = system.allocateId(GameActor.ATR, "game");
        var gameActor = system.registerFrontier(gameId,
                GameActor.constructor(gameId, 3, 180));

        system.start();

        System.out.println("=== Rock-Paper-Scissors Demo (3 sets × 200 rounds) ===");
        System.out.println();

        while (!gameActor.isFinished()) {
            gameActor.tick();
            system.awaitIdle();
        }

        System.out.println("Active actors: " + system.actorCount());
        system.shutdown();
    }
}
