package hue.captains.singapura.tao.http.config;

import hue.captains.singapura.tao.ontology.StatelessFunctionalObject;

/**
 * Validation logic for one resolved-credential format. The validation rules differ by
 * format (JKS vs PKCS12 vs PEM), so each format has its own validator; {@link TlsValidator}
 * dispatches to the right one by resolved type.
 *
 * @param <R> the concrete {@link ResolvedTlsCredential} variant this validator handles
 */
public interface TlsCredentialValidator<R extends ResolvedTlsCredential>
        extends StatelessFunctionalObject {

    /** The resolved-credential class this validator handles. */
    Class<R> resolvedType();

    /** Loads and inspects the resolved material, producing a report or failing. */
    TlsValidationReport validate(R resolved) throws TlsValidationException;
}
