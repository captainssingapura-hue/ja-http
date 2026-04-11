/**
 * Actor-based pub-sub application.
 * <p>
 * Built entirely on the actor primitives (send messages, spawn actors).
 * The actor system knows nothing about topics or subscriptions.
 * <p>
 * Actor hierarchy:
 * <pre>
 *                     Actor System
 *                    /            \
 *           Lead Actor            Topic Manager
 *          (connections)            (topics)
 *           /       \              /    |    \
 *     Session A   Session B   Topic X  Topic Y  Topic Z
 * </pre>
 */
package hue.captains.singapura.tao.http.actor.pubsub;
