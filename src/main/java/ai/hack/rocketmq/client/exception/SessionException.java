package ai.hack.rocketmq.client.exception;

/**
 * Exception thrown when there is an error with streaming session operations.
 * <p>
 * This exception indicates issues with streaming sessions, such as attempting
 * to use an unknown session ID, operating on an inactive session, or exceeding
 * the maximum number of concurrent sessions.
 *
 * @author Claude Code
 * @since 1.0.0
 */
public class SessionException extends RpcException {

    /**
     * Constructs a new session exception with the specified detail message.
     *
     * @param message the detail message explaining the session error
     */
    public SessionException(String message) {
        super(message);
    }

    /**
     * Constructs a new session exception with the specified detail message and cause.
     *
     * @param message the detail message explaining the session error
     * @param cause the cause of this exception
     */
    public SessionException(String message, Throwable cause) {
        super(message, cause);
    }
}
