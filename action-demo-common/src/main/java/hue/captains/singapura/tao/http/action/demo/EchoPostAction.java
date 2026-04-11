package hue.captains.singapura.tao.http.action.demo;

import hue.captains.singapura.tao.http.action.ParamMarshaller;
import hue.captains.singapura.tao.http.action.PostAction;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class EchoPostAction<REQ>
        implements PostAction<REQ, EchoPostBody, EchoHeaders, Map<String, Object>> {

    private final ParamMarshaller._Post<REQ, EchoPostBody> pMarshaller;
    private final ParamMarshaller._Header<REQ, EchoHeaders> hMarshaller;

    public EchoPostAction(ParamMarshaller._Post<REQ, EchoPostBody> pMarshaller,
                          ParamMarshaller._Header<REQ, EchoHeaders> hMarshaller) {
        this.pMarshaller = pMarshaller;
        this.hMarshaller = hMarshaller;
    }

    @Override
    public ParamMarshaller._Post<REQ, EchoPostBody> postMarshaller() { return pMarshaller; }

    @Override
    public ParamMarshaller._Header<REQ, EchoHeaders> headerMarshaller() { return hMarshaller; }

    @Override
    public CompletableFuture<Map<String, Object>> execute(EchoPostBody pp, EchoHeaders hp) {
        var result = new LinkedHashMap<String, Object>();
        result.put("method", "POST");
        result.put("body", pp.parsed());
        result.put("headers", hp.headers());
        return CompletableFuture.completedFuture(result);
    }
}
