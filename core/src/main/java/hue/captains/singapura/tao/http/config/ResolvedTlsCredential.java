package hue.captains.singapura.tao.http.config;

/**
 * The concrete material produced by Stage A (resolution) — the {@link TlsCredential}'s
 * specs replaced by the bytes/secret they resolve to. This is the input to Stage B
 * (validation). Mirrors the {@link TlsCredential} sum, one resolved variant per format.
 *
 * @see TlsConfigResolver
 * @see TlsValidator
 */
public sealed interface ResolvedTlsCredential permits ResolvedTlsCredential.Jks {

    /** A resolved JKS keystore: the raw keystore bytes plus the store password. */
    record Jks(byte[] keyStore, char[] password) implements ResolvedTlsCredential {
    }
}
