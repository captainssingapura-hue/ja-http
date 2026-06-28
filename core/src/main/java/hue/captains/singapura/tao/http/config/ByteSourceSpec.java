package hue.captains.singapura.tao.http.config;

/**
 * Open sum type describing <em>where</em> a blob of bytes comes from — a keystore,
 * a certificate chain, or a private key.
 * <p>
 * This is a pure, declarative marker: it carries no bytes and performs no IO.
 * Downstream projects define their own implementations (a file, a classpath
 * resource, a Vault path, a KMS handle, ...) and supply a matching
 * {@link ByteSourceResolver} to fulfil it.
 */
public interface ByteSourceSpec {
}
