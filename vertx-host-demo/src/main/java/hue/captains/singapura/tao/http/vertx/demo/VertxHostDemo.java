package hue.captains.singapura.tao.http.vertx.demo;

import hue.captains.singapura.tao.http.vertx.VertxActionHost;

public class VertxHostDemo {

    public static void main(String[] args) {
        var host = new VertxActionHost(new EchoActionRegistry(), 8080);

        host.start().onSuccess(server -> {
            System.out.println("Echo server listening on port " + server.actualPort());
            System.out.println();
            System.out.println("Try:");
            System.out.println("  curl \"http://localhost:8080/echo?name=hello&count=3\"");
            System.out.println("  curl -X POST -H \"Content-Type: application/json\" " +
                    "-d '{\"message\":\"hello\"}' http://localhost:8080/echo");
        }).onFailure(err -> {
            System.err.println("Failed to start: " + err.getMessage());
            System.exit(1);
        });
    }
}
