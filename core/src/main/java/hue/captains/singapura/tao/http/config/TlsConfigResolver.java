package hue.captains.singapura.tao.http.config;

import hue.captains.singapura.tao.ontology.StatelessFunctionalObject;

import java.io.IOException;

/**
 * Stage A of TLS handling: <em>resolve</em>. Turns a declarative {@link TlsCredential}
 * (specs) into concrete {@link ResolvedTlsCredential} material (bytes + secret) using the
 * supplied {@link TlsResolvers}. Performs no keystore loading, no validation, and never
 * touches an HTTP server — that is Stage B's job ({@link TlsValidator}).
 *
 * <p>Keeping resolution separate lets a caller resolve once and then validate (or feed a
 * server) independently.</p>
 */
public final class TlsConfigResolver implements StatelessFunctionalObject {

    /**
     * Resolves {@code credential}'s specs to concrete material.
     *
     * @throws IOException if a {@link ByteSourceSpec} or {@link PasswordSpec} cannot be
     *                     fulfilled (e.g. a missing file), surfaced verbatim from the resolver
     */
    public ResolvedTlsCredential resolve(TlsCredential credential, TlsResolvers resolvers)
            throws IOException {
        return switch (credential) {
            case TlsCredential.Jks jks -> new ResolvedTlsCredential.Jks(
                    resolvers.resolveByteSource(jks.store()),
                    resolvers.resolvePassword(jks.password()));
        };
    }
}
