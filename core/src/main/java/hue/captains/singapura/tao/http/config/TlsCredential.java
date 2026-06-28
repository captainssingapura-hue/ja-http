package hue.captains.singapura.tao.http.config;

/**
 * The TLS identity material, composed from the orthogonal {@link ByteSourceSpec} and
 * {@link PasswordSpec} axes.
 * <p>
 * Unlike those axes, the set of container <em>formats</em> is bounded by what the host
 * framework knows how to install, so this sum is {@code sealed} and exhaustively matchable.
 * Only JKS is supported for now; PKCS#12 and PEM are planned additions.
 */
public sealed interface TlsCredential permits TlsCredential.Jks {

    /** A Java KeyStore (JKS) blob protected by a password. */
    record Jks(ByteSourceSpec store, PasswordSpec password) implements TlsCredential {
    }
}
