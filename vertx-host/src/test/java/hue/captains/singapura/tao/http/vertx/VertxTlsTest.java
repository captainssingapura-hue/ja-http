package hue.captains.singapura.tao.http.vertx;

import hue.captains.singapura.tao.http.config.ByteSourceResolver;
import hue.captains.singapura.tao.http.config.ByteSourceSpec;
import hue.captains.singapura.tao.http.config.HostConfig;
import hue.captains.singapura.tao.http.config.TlsConfig;
import hue.captains.singapura.tao.http.config.TlsCredential;
import hue.captains.singapura.tao.http.config.TlsResolvers;
import hue.captains.singapura.tao.http.config.builtin.LiteralPassword;
import io.vertx.core.net.JksOptions;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VertxTlsTest {

    /** A downstream-defined byte source, proving the open extensibility of the sum. */
    private record InMemoryBytes(byte[] bytes) implements ByteSourceSpec {
    }

    private static TlsResolvers resolversFor(InMemoryBytes ignored) {
        return TlsResolvers.defaults().register(new ByteSourceResolver<InMemoryBytes>() {
            @Override
            public Class<InMemoryBytes> specType() {
                return InMemoryBytes.class;
            }

            @Override
            public byte[] resolve(InMemoryBytes spec) {
                return spec.bytes();
            }
        });
    }

    @Test
    void plainHttpProducesNonSslOptions() {
        var opts = VertxTls.serverOptions(HostConfig.http(8080), TlsResolvers.defaults());

        assertFalse(opts.isSsl());
        assertNull(opts.getKeyCertOptions());
    }

    @Test
    void httpsResolvesJksMaterialIntoSslOptions() {
        var ksBytes = "fake-keystore-bytes".getBytes();
        var store = new InMemoryBytes(ksBytes);
        var tls = new TlsConfig(new TlsCredential.Jks(store, LiteralPassword.of("changeit")));

        var opts = VertxTls.serverOptions(HostConfig.https(8443, tls), resolversFor(store));

        assertTrue(opts.isSsl());
        var jks = assertInstanceOf(JksOptions.class, opts.getKeyCertOptions());
        assertEquals("changeit", jks.getPassword());
        assertArrayEquals(ksBytes, jks.getValue().getBytes());
    }

    @Test
    void missingResolverForSpecFailsFast() {
        // defaults() knows nothing about InMemoryBytes
        var tls = new TlsConfig(new TlsCredential.Jks(
                new InMemoryBytes(new byte[0]), LiteralPassword.of("x")));

        var ex = assertThrows(IllegalStateException.class,
                () -> VertxTls.serverOptions(HostConfig.https(8443, tls), TlsResolvers.defaults()));
        assertTrue(ex.getMessage().contains("InMemoryBytes"),
                "message should name the unresolved spec, was: " + ex.getMessage());
    }
}
