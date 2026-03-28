package hue.captains.singapura.tao.http.jetty.demo;

import hue.captains.singapura.tao.http.action.ActionRegistry;
import hue.captains.singapura.tao.http.action.GetAction;
import hue.captains.singapura.tao.http.action.PostAction;
import hue.captains.singapura.tao.http.action.demo.EchoGetAction;
import hue.captains.singapura.tao.http.action.demo.EchoPostAction;
import hue.captains.singapura.tao.http.action.demo.PongGetAction;
import org.eclipse.jetty.server.Request;

import java.util.Map;

public class JettyEchoActionRegistry implements ActionRegistry<Request> {

    @Override
    public Map<String, GetAction<Request, ?, ?, ?>> getActions() {
        return Map.of(
                "/echo", new EchoGetAction<>(
                        JettyMarshallers.echoQueryParams(),
                        JettyMarshallers.echoHeaders()),
                "/pong", new PongGetAction<>(
                        JettyMarshallers.pongQueryParam(),
                        JettyMarshallers.emptyHeaders())
        );
    }

    @Override
    public Map<String, PostAction<Request, ?, ?, ?>> postActions() {
        return Map.of(
                "/echo", new EchoPostAction<>(
                        JettyMarshallers.echoPostBody(),
                        JettyMarshallers.echoHeaders())
        );
    }
}
