package hue.captains.singapura.tao.http.action.demo;

import hue.captains.singapura.tao.http.action.ExternalError;
import hue.captains.singapura.tao.http.action.GetAction;
import hue.captains.singapura.tao.http.action.HttpReturnableException;
import hue.captains.singapura.tao.http.action.InternalError;
import hue.captains.singapura.tao.http.action.ParamMarshaller;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class PongGetAction<REQ>
        implements GetAction<REQ, PongQueryParam, EchoHeaders, Map<String, String>> {

    private final ParamMarshaller._QueryString<REQ, PongQueryParam> qsMarshaller;
    private final ParamMarshaller._Header<REQ, EchoHeaders> hMarshaller;

    public PongGetAction(ParamMarshaller._QueryString<REQ, PongQueryParam> qsMarshaller,
                         ParamMarshaller._Header<REQ, EchoHeaders> hMarshaller) {
        this.qsMarshaller = qsMarshaller;
        this.hMarshaller = hMarshaller;
    }

    @Override
    public ParamMarshaller._QueryString<REQ, PongQueryParam> queryStrMarshaller() { return qsMarshaller; }

    @Override
    public ParamMarshaller._Header<REQ, EchoHeaders> headerMarshaller() { return hMarshaller; }

    @Override
    public CompletableFuture<Map<String, String>> execute(PongQueryParam qp, EchoHeaders hp) {
        if (qp.message() != null && qp.message().equalsIgnoreCase("PING")) {
            return CompletableFuture.completedFuture(Map.of("response", "Pong"));
        }
        return CompletableFuture.failedFuture(new BadPongRequestException(qp.message()));
    }

    public static class BadPongRequestException extends RuntimeException
            implements HttpReturnableException<
                    BadPongRequestException.External,
                    BadPongRequestException.Internal> {

        record External(String error, String hint) implements ExternalError {}
        record Internal(String received, String caller) implements InternalError {}

        private final String received;

        public BadPongRequestException(String received) {
            super("Expected 'PING' but got: " + received);
            this.received = received;
        }

        @Override public int statusCode() { return 400; }

        @Override
        public External externalError() {
            return new External("Invalid message: expected 'PING'", "Send ?message=PING");
        }

        @Override
        public Internal internalError() {
            return new Internal(received, getStackTrace()[0].toString());
        }
    }
}
