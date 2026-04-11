package hue.captains.singapura.tao.http.actor.remote;

import hue.captains.singapura.tao.http.actor.ActorAction;
import hue.captains.singapura.tao.http.actor.ActorId;
import hue.captains.singapura.tao.http.actor.Message;
import hue.captains.singapura.tao.http.actor.frontier.FrontierActor;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.UnaryOperator;

/**
 * A frontier actor that stands in for a remote actor.
 * From the perspective of other local actors, the proxy is indistinguishable
 * from a real local actor of the same receive type.
 * <p>
 * Outbound: receives {@code R} messages from local actors, encodes them, sends over transport.
 * Inbound: receives strings from transport, decodes as {@code S}, transforms (e.g., stamps identity),
 * and injects into the local actor system targeting a discovered ActorId.
 *
 * @param <R> message type the proxy receives from local actors (e.g., PlayerMessage)
 * @param <S> message type the remote sends back, to be injected locally (e.g., GameMessage)
 */
@SuppressWarnings({"rawtypes", "unchecked"})
public class RemoteProxyActor<R extends Message._Receive, S extends Message._Receive>
        implements FrontierActor<R> {

    private final Consumer<ActorAction.SendMessage<?,?>> listener;
    private final RemoteTransport transport;
    private final MessageCodec<R> outboundCodec;
    private final MessageCodec<S> inboundCodec;
    private final Function<R, ActorId> targetExtractor;
    private final UnaryOperator<S> inboundTransformer;

    private ActorId inboundTarget;

    private RemoteProxyActor(Consumer<ActorAction.SendMessage<?,?>> listener,
                             RemoteTransport transport,
                             MessageCodec<R> outboundCodec,
                             MessageCodec<S> inboundCodec,
                             Function<R, ActorId> targetExtractor,
                             UnaryOperator<S> inboundTransformer) {
        this.listener = listener;
        this.transport = transport;
        this.outboundCodec = outboundCodec;
        this.inboundCodec = inboundCodec;
        this.targetExtractor = targetExtractor;
        this.inboundTransformer = inboundTransformer;

        transport.onReceive(this::handleInbound);
        transport.onClose(this::handleClose);
    }

    @Override
    public List<ActorAction> receive(List<R> messages) {
        for (var msg : messages) {
            // Try to discover the inbound target from each message
            var target = targetExtractor.apply(msg);
            if (target != null) {
                this.inboundTarget = target;
            }

            // Encode and send to remote
            transport.send(outboundCodec.encode(msg));
        }
        return List.of();
    }

    private void handleInbound(String data) {
        if (inboundTarget == null) {
            System.err.println("[RemoteProxy] Received inbound message but no target discovered yet, dropping: " + data);
            return;
        }
        var decoded = inboundCodec.decode(data);
        var transformed = inboundTransformer.apply(decoded);
        listener.accept(new ActorAction.SendMessage(inboundTarget, transformed));
    }

    private void handleClose() {
        System.out.println("[RemoteProxy] Remote disconnected");
    }

    public static <R extends Message._Receive, S extends Message._Receive>
    FrontierActor._Constructor<R, RemoteProxyActor<R, S>> constructor(
            RemoteTransport transport,
            MessageCodec<R> outboundCodec,
            MessageCodec<S> inboundCodec,
            Function<R, ActorId> targetExtractor,
            UnaryOperator<S> inboundTransformer) {
        return listener -> new RemoteProxyActor<>(
                listener, transport, outboundCodec, inboundCodec,
                targetExtractor, inboundTransformer);
    }
}
