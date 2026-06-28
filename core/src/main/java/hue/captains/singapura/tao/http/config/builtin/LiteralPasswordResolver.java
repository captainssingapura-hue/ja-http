package hue.captains.singapura.tao.http.config.builtin;

import hue.captains.singapura.tao.http.config.PasswordResolver;

/** Built-in resolver for {@link LiteralPassword}. */
public final class LiteralPasswordResolver implements PasswordResolver<LiteralPassword> {

    @Override
    public Class<LiteralPassword> specType() {
        return LiteralPassword.class;
    }

    @Override
    public char[] resolve(LiteralPassword spec) {
        return spec.value();
    }
}
