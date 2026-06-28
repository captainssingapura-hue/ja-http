package hue.captains.singapura.tao.http.config;

/**
 * Pure value object carrying a host's bind address, port, and optional TLS configuration.
 * <p>
 * Replaces the bare {@code int port} previously threaded through the host constructors.
 */
public record HostConfig(String host, int port, TlsConfig tls) {

    private static final String ALL_INTERFACES = "0.0.0.0";

    /** Plain HTTP on the given port, bound to all interfaces. */
    public static HostConfig http(int port) {
        return new HostConfig(ALL_INTERFACES, port, null);
    }

    /** HTTPS on the given port, bound to all interfaces. */
    public static HostConfig https(int port, TlsConfig tls) {
        return new HostConfig(ALL_INTERFACES, port, tls);
    }

    public boolean isTls() {
        return tls != null;
    }
}
