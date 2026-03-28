package hue.captains.singapura.tao.http.actor;

/**
 * Actors receive and send messages.
 */
public interface Message {
    /**
     * Here we deliberately distinct messaged to receive and to send.<br>
     * In reality, they might be the same co-incidence.
     */
    interface _Receive extends Message{}
    interface _Send extends Message{}
}
