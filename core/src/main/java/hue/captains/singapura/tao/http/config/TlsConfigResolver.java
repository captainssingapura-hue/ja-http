package hue.captains.singapura.tao.http.config;

import hue.captains.singapura.tao.ontology.StatelessFunctionalObject;

import java.io.IOException;

/**
 * Stage A of TLS handling: <em>resolve</em>. Invokes a {@link TlsCredential}'s provider
 * functions to obtain concrete {@link ResolvedTlsCredential} material (bytes + secret).
 * Performs no keystore loading, no validation, and never touches an HTTP server — that is
 * Stage B's job ({@link TlsValidator}).
 *
 * <p>Keeping resolution separate lets a caller resolve once and then validate (or feed a
 * server) independently.</p>
 */
public final class TlsConfigResolver implements StatelessFunctionalObject {

    /**
     * Invokes {@code credential}'s providers to produce concrete material.
     *
     * @throws IOException if a provider cannot supply its material (e.g. a missing file),
     *                     surfaced verbatim from the provider
     */
    public ResolvedTlsCredential resolve(TlsCredential credential) throws IOException {
        return switch (credential) {
            case TlsCredential.Jks jks -> new ResolvedTlsCredential.Jks(
                    jks.store().get(),
                    jks.password().get());
        };
    }
}
