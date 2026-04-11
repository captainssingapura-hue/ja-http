package hue.captains.singapura.tao.http.action.demo;

import hue.captains.singapura.tao.http.action.GetAction;
import hue.captains.singapura.tao.http.action.ParamMarshaller;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class EchoGetAction<REQ>
        implements GetAction<REQ, EchoQueryParams, EchoHeaders, Map<String, Object>> {

    private final ParamMarshaller._QueryString<REQ, EchoQueryParams> qsMarshaller;
    private final ParamMarshaller._Header<REQ, EchoHeaders> hMarshaller;

    public EchoGetAction(ParamMarshaller._QueryString<REQ, EchoQueryParams> qsMarshaller,
                         ParamMarshaller._Header<REQ, EchoHeaders> hMarshaller) {
        this.qsMarshaller = qsMarshaller;
        this.hMarshaller = hMarshaller;
    }

    @Override
    public ParamMarshaller._QueryString<REQ, EchoQueryParams> queryStrMarshaller() { return qsMarshaller; }

    @Override
    public ParamMarshaller._Header<REQ, EchoHeaders> headerMarshaller() { return hMarshaller; }

    @Override
    public CompletableFuture<Map<String, Object>> execute(EchoQueryParams qp, EchoHeaders hp) {
        var result = new LinkedHashMap<String, Object>();
        result.put("method", "GET");
        result.put("queryParams", qp.params());
        result.put("headers", hp.headers());
        return CompletableFuture.completedFuture(result);
    }
}
