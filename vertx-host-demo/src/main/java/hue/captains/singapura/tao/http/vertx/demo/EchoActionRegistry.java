package hue.captains.singapura.tao.http.vertx.demo;

import hue.captains.singapura.tao.http.action.ActionRegistry;
import hue.captains.singapura.tao.http.action.GetAction;
import hue.captains.singapura.tao.http.action.PostAction;
import hue.captains.singapura.tao.http.action.demo.EchoGetAction;
import hue.captains.singapura.tao.http.action.demo.EchoPostAction;
import hue.captains.singapura.tao.http.action.demo.PongGetAction;
import io.vertx.ext.web.RoutingContext;

import java.util.Map;

public class EchoActionRegistry implements ActionRegistry<RoutingContext> {

    @Override
    public Map<String, GetAction<RoutingContext, ?, ?, ?>> getActions() {
        return Map.of(
                "/echo", new EchoGetAction<>(
                        VertxMarshallers.echoQueryParams(),
                        VertxMarshallers.echoHeaders()),
                "/pong", new PongGetAction<>(
                        VertxMarshallers.pongQueryParam(),
                        VertxMarshallers.emptyHeaders())
        );
    }

    @Override
    public Map<String, PostAction<RoutingContext, ?, ?, ?>> postActions() {
        return Map.of(
                "/echo", new EchoPostAction<>(
                        VertxMarshallers.echoPostBody(),
                        VertxMarshallers.echoHeaders())
        );
    }
}
