package hue.captains.singapura.tao.http.config;

import hue.captains.singapura.tao.http.config.builtin.FileByteSourceResolver;
import hue.captains.singapura.tao.http.config.builtin.LiteralPasswordResolver;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Registry that binds each spec kind to the resolver able to fulfil it, then dispatches
 * a spec instance to its resolver by concrete class.
 * <p>
 * The framework knows only the {@link ByteSourceSpec} / {@link PasswordSpec} interfaces;
 * downstream projects register their own spec/resolver pairs here.
 */
public final class TlsResolvers {

    private final Map<Class<?>, ByteSourceResolver<?>> byteSources = new HashMap<>();
    private final Map<Class<?>, PasswordResolver<?>> passwords = new HashMap<>();

    /**
     * A registry pre-loaded with the built-in resolvers
     * ({@link FileByteSourceResolver}, {@link LiteralPasswordResolver}).
     */
    public static TlsResolvers defaults() {
        return new TlsResolvers()
                .register(new FileByteSourceResolver())
                .register(new LiteralPasswordResolver());
    }

    public <S extends ByteSourceSpec> TlsResolvers register(ByteSourceResolver<S> resolver) {
        byteSources.put(resolver.specType(), resolver);
        return this;
    }

    public <S extends PasswordSpec> TlsResolvers register(PasswordResolver<S> resolver) {
        passwords.put(resolver.specType(), resolver);
        return this;
    }

    @SuppressWarnings("unchecked")
    public byte[] resolveByteSource(ByteSourceSpec spec) throws IOException {
        var resolver = (ByteSourceResolver<ByteSourceSpec>) byteSources.get(spec.getClass());
        if (resolver == null) {
            throw new IllegalStateException(
                    "No ByteSourceResolver registered for " + spec.getClass().getName());
        }
        return resolver.resolve(spec);
    }

    @SuppressWarnings("unchecked")
    public char[] resolvePassword(PasswordSpec spec) throws IOException {
        var resolver = (PasswordResolver<PasswordSpec>) passwords.get(spec.getClass());
        if (resolver == null) {
            throw new IllegalStateException(
                    "No PasswordResolver registered for " + spec.getClass().getName());
        }
        return resolver.resolve(spec);
    }

    /**
     * Adapts a spec into the deferred {@link ByteSourceProvider} a {@link TlsCredential}
     * consumes — the bridge from the spec/resolver suite to a plain provider function.
     */
    public ByteSourceProvider byteSourceProvider(ByteSourceSpec spec) {
        return () -> resolveByteSource(spec);
    }

    /** Adapts a spec into the deferred {@link PasswordProvider} a {@link TlsCredential} consumes. */
    public PasswordProvider passwordProvider(PasswordSpec spec) {
        return () -> resolvePassword(spec);
    }
}
