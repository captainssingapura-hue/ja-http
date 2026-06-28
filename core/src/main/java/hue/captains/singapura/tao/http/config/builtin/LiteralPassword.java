package hue.captains.singapura.tao.http.config.builtin;

import hue.captains.singapura.tao.http.config.PasswordSpec;

/**
 * Built-in {@link PasswordSpec}: a literal password held in memory.
 * Convenient for development and tests; prefer an externalized source in production.
 * Fulfilled by {@link LiteralPasswordResolver}.
 */
public record LiteralPassword(char[] value) implements PasswordSpec {

    public static LiteralPassword of(String value) {
        return new LiteralPassword(value.toCharArray());
    }
}
