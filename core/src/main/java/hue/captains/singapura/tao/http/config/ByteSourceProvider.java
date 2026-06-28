package hue.captains.singapura.tao.http.config;

import java.io.IOException;

/**
 * Supplies a blob of bytes (a keystore, a certificate, a key) on demand. This is the
 * function a {@link TlsCredential} carries directly — sufficient on its own to start a
 * server, with no resolver registry involved.
 *
 * <p>The {@link ByteSourceSpec} / {@link ByteSourceResolver} / {@link TlsResolvers} suite is
 * an optional utility for <em>building</em> one of these (see
 * {@link TlsResolvers#byteSourceProvider}); downstream may also supply any other lambda.</p>
 */
@FunctionalInterface
public interface ByteSourceProvider {

    byte[] get() throws IOException;
}
