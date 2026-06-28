package hue.captains.singapura.tao.http.config;

import hue.captains.singapura.tao.ontology.StatelessFunctionalObject;

/**
 * Stage B of TLS handling: <em>validate</em>. Dispatches resolved material to the
 * {@link TlsCredentialValidator} for its format and returns the resulting
 * {@link TlsValidationReport}. No HTTP server is involved.
 *
 * <p>Pair with Stage A:</p>
 * <pre>{@code
 * var resolved = new TlsConfigResolver().resolve(credential, resolvers);
 * var report   = new TlsValidator().validate(resolved);   // throws TlsValidationException on bad material
 * if (!report.validAt(Instant.now())) { ...warn about expiry... }
 * }</pre>
 *
 * <p>Only JKS is handled today; the switch is exhaustive over the sealed
 * {@link ResolvedTlsCredential}, so adding a format is a compile-time prompt to add its
 * validator here.</p>
 */
public final class TlsValidator implements StatelessFunctionalObject {

    public TlsValidationReport validate(ResolvedTlsCredential resolved) throws TlsValidationException {
        return switch (resolved) {
            case ResolvedTlsCredential.Jks jks -> new JksTlsValidator().validate(jks);
        };
    }
}
