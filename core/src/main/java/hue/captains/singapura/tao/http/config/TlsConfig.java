package hue.captains.singapura.tao.http.config;

/**
 * Pure value object describing the TLS configuration for a host.
 * <p>
 * Holds only declarative specs — never resolved bytes or secrets. Resolution is deferred
 * to a {@link TlsResolvers} registry at server-start time. Kept as a wrapper (rather than
 * exposing {@link TlsCredential} directly) so future TLS options — client auth, enabled
 * protocols, cipher suites — can be added without changing host signatures.
 */
public record TlsConfig(TlsCredential credential) {
}
