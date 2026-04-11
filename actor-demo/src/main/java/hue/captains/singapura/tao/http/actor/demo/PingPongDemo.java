package hue.captains.singapura.tao.http.actor.demo;

import hue.captains.singapura.tao.http.actor.system.EventLoopActorSystem;

public class PingPongDemo {

    public static void main(String[] args) throws InterruptedException {
        var system = new EventLoopActorSystem();

        var pingId = system.allocateId(PingActor.ATR, "ping");
        var pongId = system.allocateId(PongActor.ATR, "pong");

        system.register(pongId, new PongActor(pongId));
        var pingActor = system.registerFrontier(pingId, PingActor.constructor(pongId, pingId));

        system.start();

        System.out.println("=== Ping-Pong Demo (10 rounds, 1 per second) ===\n");

        for (int i = 1; i <= 10; i++) {
            System.out.println("--- Tick " + i + " ---");
            pingActor.sendPing();       // external event: timer fires
            system.awaitIdle();         // wait for all messages to be processed
            System.out.println();
            Thread.sleep(1000);
        }

        System.out.println("=== Done ===");
        system.shutdown();
    }
}
