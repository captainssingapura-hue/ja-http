package hue.captains.singapura.tao.http.jetty.demo;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import hue.captains.singapura.tao.http.action.ParamMarshaller;
import hue.captains.singapura.tao.http.action.demo.EchoHeaders;
import hue.captains.singapura.tao.http.action.demo.EchoPostBody;
import hue.captains.singapura.tao.http.action.demo.EchoQueryParams;
import hue.captains.singapura.tao.http.action.demo.PongQueryParam;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.util.Fields;

import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.Map;

public final class JettyMarshallers {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private JettyMarshallers() {}

    public static ParamMarshaller._QueryString<Request, EchoQueryParams> echoQueryParams() {
        return request -> {
            var params = new LinkedHashMap<String, String>();
            Fields fields = Request.extractQueryParameters(request);
            for (Fields.Field field : fields) {
                params.put(field.getName(), field.getValue());
            }
            return new EchoQueryParams(params);
        };
    }

    public static ParamMarshaller._QueryString<Request, PongQueryParam> pongQueryParam() {
        return request -> {
            Fields fields = Request.extractQueryParameters(request);
            Fields.Field messageField = fields.get("message");
            return new PongQueryParam(messageField != null ? messageField.getValue() : null);
        };
    }

    public static ParamMarshaller._Header<Request, EchoHeaders> echoHeaders() {
        return request -> {
            var headers = new LinkedHashMap<String, String>();
            for (var field : request.getHeaders()) {
                headers.put(field.getName(), field.getValue());
            }
            return new EchoHeaders(headers);
        };
    }

    public static ParamMarshaller._Header<Request, EchoHeaders> emptyHeaders() {
        return request -> new EchoHeaders(Map.of());
    }

    public static ParamMarshaller._Post<Request, EchoPostBody> echoPostBody() {
        return request -> {
            try {
                InputStream is = Request.asInputStream(request);
                byte[] body = is.readAllBytes();
                Map<String, Object> parsed = MAPPER.readValue(body,
                        new TypeReference<LinkedHashMap<String, Object>>() {});
                return new EchoPostBody(parsed);
            } catch (Exception e) {
                return new EchoPostBody(Map.of("_error", e.getMessage()));
            }
        };
    }
}
