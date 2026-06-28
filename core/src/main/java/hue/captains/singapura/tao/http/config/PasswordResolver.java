package hue.captains.singapura.tao.http.config;

import java.io.IOException;

/**
 * Fulfils a single kind of {@link PasswordSpec} by producing its secret.
 *
 * @param <S> the concrete spec kind this resolver fulfils
 */
public interface PasswordResolver<S extends PasswordSpec> {

    /** The concrete spec class this resolver handles. */
    Class<S> specType();

    /** Produces the secret described by {@code spec}. */
    char[] resolve(S spec) throws IOException;
}
