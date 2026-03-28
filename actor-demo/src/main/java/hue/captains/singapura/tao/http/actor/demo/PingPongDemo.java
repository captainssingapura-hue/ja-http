package hue.captains.singapura.tao.http.actor.demo;

public class PingPongDemo {

    public static void main(String[] args) throws InterruptedException {
        var system = new SingleThreadActorSystem();

        var pingRef = system.allocateRef("ping");
        var pongRef = system.allocateRef("pong");

        system.register(pongRef, new PongActor(pongRef));
        var pingActor = system.registerFrontier(pingRef, PingActor.constructor(pongRef, pingRef));

        System.out.println("=== Ping-Pong Demo (10 rounds, 1 per second) ===\n");

        for (int i = 1; i <= 10; i++) {
            System.out.println("--- Tick " + i + " ---");
            pingActor.sendPing();       // external event: timer fires
            system.processMailbox();    // process all messages to completion
            System.out.println();
            Thread.sleep(1000);
        }

        System.out.println("=== Done ===");
    }
}
