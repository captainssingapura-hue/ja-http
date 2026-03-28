package hue.captains.singapura.tao.http.action;

/**
 * Get typed Params from a raw (typically untyped) request
 * @param <REQ>
 * @param <P>
 */
public interface ParamMarshaller<REQ, P extends Param>{

    P marshal(REQ req);

    interface _Header<REQ, HP extends Param._Header> extends ParamMarshaller<REQ, HP>{}
    interface _QueryString<REQ, QP extends Param._QueryString> extends ParamMarshaller<REQ, QP>{}
    interface _Post<REQ, PP extends Param._Post> extends ParamMarshaller<REQ, PP>{}
}
