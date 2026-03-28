package hue.captains.singapura.tao.http.action;

public interface Param {
    interface _Post extends Param{}
    interface _Header extends Param{}
    interface _QueryString extends Param{}

    /**
     * We might need to support Cookies but leave it for now.
     */
}
