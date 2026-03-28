package hue.captains.singapura.tao.http.action;

import java.util.concurrent.CompletableFuture;

public interface GetAction<REQ, QP extends Param._QueryString, HP extends Param._Header, R> extends Action<REQ, HP> {

    ParamMarshaller._QueryString<REQ, QP> queryStrMarshaller();

    /**
     * For GET, just support header and query string.
     * Returns a {@link CompletableFuture} to support asynchronous execution by default.
     * Synchronous implementations can return {@link CompletableFuture#completedFuture(Object)}.
     */
    CompletableFuture<R> execute(QP qp, HP hp);
}
