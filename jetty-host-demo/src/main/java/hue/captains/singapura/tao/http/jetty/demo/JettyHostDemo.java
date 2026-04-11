package hue.captains.singapura.tao.http.jetty.demo;

import hue.captains.singapura.tao.http.jetty.JettyActionHost;

public class JettyHostDemo {

    public static void main(String[] args) throws Exception {
        var host = new JettyActionHost(new JettyEchoActionRegistry(), 8081);
        host.startServer();

        System.out.println("Jetty echo server listening on port 8081");
        System.out.println();
        System.out.println("Try:");
        System.out.println("  curl \"http://localhost:8081/echo?name=hello&count=3\"");
        System.out.println("  curl -X POST -H \"Content-Type: application/json\" " +
                "-d '{\"message\":\"hello\"}' http://localhost:8081/echo");
        System.out.println("  curl \"http://localhost:8081/pong?message=PING\"");
        System.out.println("  curl \"http://localhost:8081/pong?message=wrong\"");

        host.joinServer();
    }
}
