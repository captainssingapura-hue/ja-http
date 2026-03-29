package hue.captains.singapura.tao.http.action;

/**
 * Marker interface for action responses that specify their own content type.
 * When a hosting handler receives a result that implements TypedContent,
 * it uses {@link #contentType()} and {@link #body()} directly instead of
 * JSON-serializing via ObjectMapper.
 *
 * <p>Sub-interfaces provide default content types for common MIME types.
 * Application code defines concrete records implementing the appropriate sub-interface.</p>
 */
public interface TypedContent {

    String contentType();

    String body();

    interface Js extends TypedContent {
        default String contentType() { return "application/javascript"; }
    }

    interface Html extends TypedContent {
        default String contentType() { return "text/html"; }
    }

    interface Css extends TypedContent {
        default String contentType() { return "text/css"; }
    }

    interface Svg extends TypedContent {
        default String contentType() { return "image/svg+xml"; }
    }
}
