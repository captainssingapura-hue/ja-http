package hue.captains.singapura.tao.http.vertx.demo;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import hue.captains.singapura.tao.http.action.ParamMarshaller;
import hue.captains.singapura.tao.http.action.demo.EchoHeaders;
import hue.captains.singapura.tao.http.action.demo.EchoPostBody;
import hue.captains.singapura.tao.http.action.demo.EchoQueryParams;
import hue.captains.singapura.tao.http.action.demo.PongQueryParam;
import io.vertx.ext.web.RoutingContext;

import java.util.LinkedHashMap;
import java.util.Map;

public final class VertxMarshallers {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private VertxMarshallers() {}

    public static ParamMarshaller._QueryString<RoutingContext, EchoQueryParams> echoQueryParams() {
        return ctx -> {
            var params = new LinkedHashMap<String, String>();
            ctx.request().params().forEach(entry -> params.put(entry.getKey(), entry.getValue()));
            return new EchoQueryParams(params);
        };
    }

    public static ParamMarshaller._QueryString<RoutingContext, PongQueryParam> pongQueryParam() {
        return ctx -> new PongQueryParam(ctx.request().getParam("message"));
    }

    public static ParamMarshaller._Header<RoutingContext, EchoHeaders> echoHeaders() {
        return ctx -> {
            var headers = new LinkedHashMap<String, String>();
            ctx.request().headers().forEach(entry -> headers.put(entry.getKey(), entry.getValue()));
            return new EchoHeaders(headers);
        };
    }

    public static ParamMarshaller._Header<RoutingContext, EchoHeaders> emptyHeaders() {
        return ctx -> new EchoHeaders(Map.of());
    }

    public static ParamMarshaller._Post<RoutingContext, EchoPostBody> echoPostBody() {
        return ctx -> {
            try {
                String body = ctx.body().asString();
                Map<String, Object> parsed = MAPPER.readValue(body,
                        new TypeReference<LinkedHashMap<String, Object>>() {});
                return new EchoPostBody(parsed);
            } catch (Exception e) {
                return new EchoPostBody(Map.of("_raw", ctx.body().asString(), "_error", e.getMessage()));
            }
        };
    }
}
