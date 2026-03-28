package hue.captains.singapura.tao.http.action;

import java.util.concurrent.CompletableFuture;

/**
 * For a regular post action
 * @param <REQ>
 * @param <PP>
 * @param <HP>
 * @param <R>
 */
public interface PostAction<REQ, PP extends Param._Post, HP extends Param._Header, R> extends Action<REQ, HP> {
    ParamMarshaller._Post<REQ, PP> postMarshaller();

    /**
     * Returns a {@link CompletableFuture} to support asynchronous execution by default.
     * Synchronous implementations can return {@link CompletableFuture#completedFuture(Object)}.
     */
    CompletableFuture<R> execute(PP pp, HP hp);
}
