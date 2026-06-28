package hue.captains.singapura.tao.http.vertx;

import hue.captains.singapura.tao.http.config.HostConfig;
import hue.captains.singapura.tao.http.config.TlsConfig;
import hue.captains.singapura.tao.http.config.TlsCredential;
import io.vertx.core.net.JksOptions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VertxTlsTest {

    @Test
    void plainHttpProducesNonSslOptions() {
        var opts = VertxTls.serverOptions(HostConfig.http(8080));

        assertFalse(opts.isSsl());
        assertNull(opts.getKeyCertOptions());
    }

    @Test
    void httpsBuildsJksOptionsFromProviders() {
        var ksBytes = "fake-keystore-bytes".getBytes();
        var tls = new TlsConfig(new TlsCredential.Jks(() -> ksBytes, () -> "changeit".toCharArray()));

        var opts = VertxTls.serverOptions(HostConfig.https(8443, tls));

        assertTrue(opts.isSsl());
        var jks = assertInstanceOf(JksOptions.class, opts.getKeyCertOptions());
        assertEquals("changeit", jks.getPassword());
        assertArrayEquals(ksBytes, jks.getValue().getBytes());
    }

    @Test
    void providerIoFailureSurfacesAsUnchecked() {
        var tls = new TlsConfig(new TlsCredential.Jks(
                () -> {
                    throw new IOException("boom");
                },
                () -> "x".toCharArray()));

        var ex = assertThrows(UncheckedIOException.class,
                () -> VertxTls.serverOptions(HostConfig.https(8443, tls)));
        assertTrue(ex.getMessage().contains("TLS material"));
    }
}
