package hue.captains.singapura.tao.http.vertx.demo;

import hue.captains.singapura.tao.http.config.HostConfig;
import hue.captains.singapura.tao.http.config.TlsConfig;
import hue.captains.singapura.tao.http.config.TlsCredential;
import hue.captains.singapura.tao.http.config.builtin.FileByteSource;
import hue.captains.singapura.tao.http.config.builtin.LiteralPassword;
import hue.captains.singapura.tao.http.vertx.VertxActionHost;

public class VertxHttpsHostDemo {

    public static void main(String[] args) {
        var keystorePath = args.length > 0 ? args[0] : "vertx-host-demo/certs/dev-keystore.jks";
        var password = args.length > 1 ? args[1] : "changeit";

        var tls = new TlsConfig(new TlsCredential.Jks(
                new FileByteSource(keystorePath),
                LiteralPassword.of(password)));
        var host = new VertxActionHost(new EchoActionRegistry(), HostConfig.https(8443, tls));

        host.start().onSuccess(server -> {
            System.out.println("Echo server listening on HTTPS port " + server.actualPort());
            System.out.println();
            System.out.println("Try (-k trusts the self-signed cert):");
            System.out.println("  curl -k \"https://localhost:8443/echo?name=hello&count=3\"");
        }).onFailure(err -> {
            System.err.println("Failed to start: " + err.getMessage());
            System.exit(1);
        });
    }
}
