package hue.captains.singapura.tao.http.action;

/**
 * An exception that carries strongly-typed error payloads for both the external caller
 * and internal logging.
 * <p>
 * Actions throw concrete exceptions that implement this interface. The hosting layer
 * serializes {@link #externalError()} into the HTTP response and logs
 * {@link #internalError()} separately, keeping internal details out of the response.
 *
 * @param <E> the external error type, serialized and returned to the caller
 * @param <I> the internal error type, logged server-side
 */
public interface HttpReturnableException<E extends ExternalError, I extends InternalError> {

    int statusCode();

    E externalError();

    I internalError();
}
