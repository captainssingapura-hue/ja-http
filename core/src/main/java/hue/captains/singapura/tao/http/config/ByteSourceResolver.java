package hue.captains.singapura.tao.http.config;

import java.io.IOException;

/**
 * Fulfils a single kind of {@link ByteSourceSpec} by producing its bytes.
 * <p>
 * One resolver supports exactly one spec type, reported by {@link #specType()};
 * a {@link TlsResolvers} registry dispatches a spec instance to the resolver registered
 * for its concrete class.
 *
 * @param <S> the concrete spec kind this resolver fulfils
 */
public interface ByteSourceResolver<S extends ByteSourceSpec> {

    /** The concrete spec class this resolver handles. */
    Class<S> specType();

    /** Produces the bytes described by {@code spec}. */
    byte[] resolve(S spec) throws IOException;
}
