package hue.captains.singapura.tao.http.jetty.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import hue.captains.singapura.tao.http.action.Param;
import hue.captains.singapura.tao.http.action.PostAction;
import hue.captains.singapura.tao.http.action.TypedContent;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Response;
import org.eclipse.jetty.util.Callback;

import java.nio.ByteBuffer;

public class PostActionHandler<PP extends Param._Post, HP extends Param._Header, R> {

    private final PostAction<Request, PP, HP, R> action;
    private final ObjectMapper objectMapper;

    public PostActionHandler(PostAction<Request, PP, HP, R> action, ObjectMapper objectMapper) {
        this.action = action;
        this.objectMapper = objectMapper;
    }

    public void handle(Request request, Response response, Callback callback) {
        try {
            PP postParams = action.postMarshaller().marshal(request);
            HP headerParams = action.headerMarshaller().marshal(request);

            R result = action.execute(postParams, headerParams).join();

            response.setStatus(200);
            if (result instanceof TypedContent tc) {
                response.getHeaders().put("Content-Type", tc.contentType());
                response.write(true, ByteBuffer.wrap(tc.body().getBytes()), callback);
            } else {
                byte[] json = objectMapper.writeValueAsBytes(result);
                response.getHeaders().put("Content-Type", "application/json");
                response.write(true, ByteBuffer.wrap(json), callback);
            }
        } catch (Exception e) {
            ErrorHandler.handle(response, callback, ErrorHandler.unwrap(e), objectMapper);
        }
    }
}
