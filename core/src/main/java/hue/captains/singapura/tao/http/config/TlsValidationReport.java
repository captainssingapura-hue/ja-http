package hue.captains.singapura.tao.http.config;

import java.time.Instant;
import java.util.List;

/**
 * The result of successfully validating a keystore (Stage B): its type and one entry per
 * alias, including each X.509 certificate's validity window. Lets a caller inspect the
 * keystore — and decide whether to warn or fail on expiry — without an HTTP server.
 *
 * @param storeType the keystore type (e.g. {@code "JKS"})
 * @param entries   one per alias, in keystore order
 */
public record TlsValidationReport(String storeType, List<Entry> entries) {

    /**
     * A single keystore entry.
     *
     * @param alias          the entry alias
     * @param keyEntry       whether this is a private-key entry (vs a trusted cert)
     * @param keyRecoverable for key entries, whether the key was recoverable with the
     *                       keystore password (false signals a key-password mismatch)
     * @param notBefore      certificate validity start, or {@code null} if no X.509 cert
     * @param notAfter       certificate validity end, or {@code null} if no X.509 cert
     */
    public record Entry(String alias, boolean keyEntry, boolean keyRecoverable,
                        Instant notBefore, Instant notAfter) {
    }

    /** True if every certificate-bearing entry's validity window contains {@code at}. */
    public boolean validAt(Instant at) {
        return entries.stream()
                .filter(e -> e.notAfter() != null)
                .allMatch(e -> !at.isBefore(e.notBefore()) && !at.isAfter(e.notAfter()));
    }

    /** Aliases whose certificate has expired as of {@code at}. */
    public List<String> expiredAt(Instant at) {
        return entries.stream()
                .filter(e -> e.notAfter() != null && at.isAfter(e.notAfter()))
                .map(Entry::alias)
                .toList();
    }
}
