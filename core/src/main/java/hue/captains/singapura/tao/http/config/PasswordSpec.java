package hue.captains.singapura.tao.http.config;

/**
 * Open sum type describing <em>how</em> a secret (e.g. a keystore password) is obtained.
 * <p>
 * Orthogonal to {@link ByteSourceSpec}: byte material and the secrets that protect it
 * are independent concerns. Like {@code ByteSourceSpec} this is a pure declarative marker;
 * downstream projects define implementations and supply a matching {@link PasswordResolver}.
 */
public interface PasswordSpec {
}
