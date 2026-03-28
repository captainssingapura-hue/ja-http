package hue.captains.singapura.tao.http.action;

import java.util.Map;

/**
 * Immutable registry of HTTP action mappings.
 * Client applications implement this interface to declare their routes.
 *
 * @param <REQ> the raw request type, bound by the hosting implementation
 */
public interface ActionRegistry<REQ> {

    Map<String, GetAction<REQ, ?, ?, ?>> getActions();

    Map<String, PostAction<REQ, ?, ?, ?>> postActions();
}
