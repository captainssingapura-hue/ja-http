package hue.captains.singapura.tao.http.config;

/**
 * Raised by Stage B (validation) when resolved TLS material does not form a usable
 * keystore. The {@link Kind} classifies the failure so callers can react programmatically
 * instead of parsing messages.
 */
public final class TlsValidationException extends Exception {

    /** The category of a validation failure. */
    public enum Kind {
        /** Material could not be resolved (e.g. file missing) — an IO/resolver failure. */
        MATERIAL_UNRESOLVABLE,
        /** The keystore password is incorrect. */
        WRONG_PASSWORD,
        /** The bytes are not a readable keystore of the expected format (corrupt/wrong type). */
        BAD_FORMAT,
        /** The keystore type/algorithm is unavailable in this JVM. */
        UNSUPPORTED
    }

    private final Kind kind;

    public TlsValidationException(Kind kind, String message, Throwable cause) {
        super(message, cause);
        this.kind = kind;
    }

    public Kind kind() {
        return kind;
    }
}
