package hue.captains.singapura.tao.http.vertx;

import hue.captains.singapura.tao.http.config.HostConfig;
import hue.captains.singapura.tao.http.config.TlsCredential;
import hue.captains.singapura.tao.http.config.TlsResolvers;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.net.JksOptions;
import io.vertx.core.net.KeyCertOptions;

import java.io.IOException;
import java.io.UncheckedIOException;

/**
 * Maps the framework-agnostic {@link HostConfig} TLS settings onto Vert.x
 * {@link HttpServerOptions}, resolving every spec to in-memory bytes/secrets via the
 * supplied {@link TlsResolvers} so all material sources converge on one code path.
 */
public final class VertxTls {

    private VertxTls() {
    }

    /**
     * Returns server options for {@code config}. Plain HTTP yields default options;
     * HTTPS yields SSL-enabled options with the resolved key/cert material installed.
     */
    public static HttpServerOptions serverOptions(HostConfig config, TlsResolvers resolvers) {
        var options = new HttpServerOptions();
        if (!config.isTls()) {
            return options;
        }
        return options
                .setSsl(true)
                .setKeyCertOptions(keyCertOptions(config.tls().credential(), resolvers));
    }

    private static KeyCertOptions keyCertOptions(TlsCredential credential, TlsResolvers resolvers) {
        try {
            return switch (credential) {
                case TlsCredential.Jks jks -> new JksOptions()
                        .setValue(Buffer.buffer(resolvers.resolveByteSource(jks.store())))
                        .setPassword(new String(resolvers.resolvePassword(jks.password())));
            };
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to resolve TLS material", e);
        }
    }
}
