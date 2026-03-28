package hue.captains.singapura.tao.http.jetty.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import hue.captains.singapura.tao.http.action.GetAction;
import hue.captains.singapura.tao.http.action.Param;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Response;
import org.eclipse.jetty.util.Callback;

import java.nio.ByteBuffer;

public class GetActionHandler<QP extends Param._QueryString, HP extends Param._Header, R> {

    private final GetAction<Request, QP, HP, R> action;
    private final ObjectMapper objectMapper;

    public GetActionHandler(GetAction<Request, QP, HP, R> action, ObjectMapper objectMapper) {
        this.action = action;
        this.objectMapper = objectMapper;
    }

    public void handle(Request request, Response response, Callback callback) {
        try {
            QP queryParams = action.queryStrMarshaller().marshal(request);
            HP headerParams = action.headerMarshaller().marshal(request);

            R result = action.execute(queryParams, headerParams).join();

            byte[] json = objectMapper.writeValueAsBytes(result);
            response.setStatus(200);
            response.getHeaders().put("Content-Type", "application/json");
            response.write(true, ByteBuffer.wrap(json), callback);
        } catch (Exception e) {
            ErrorHandler.handle(response, callback, ErrorHandler.unwrap(e), objectMapper);
        }
    }
}
