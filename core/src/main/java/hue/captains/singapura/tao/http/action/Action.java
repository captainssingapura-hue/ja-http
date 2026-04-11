package hue.captains.singapura.tao.http.action;

public interface Action<REQ, HP extends Param._Header> {
    ParamMarshaller._Header<REQ, HP> headerMarshaller();
}
