package ai.hack.rocketmq.client.exception;

/**
 * Exception thrown when there is an error with message correlation.
 * <p>
 * This exception indicates issues with matching responses to their original requests,
 * such as receiving a response with an unknown correlation ID or duplicate correlation IDs.
 *
 * @author Claude Code
 * @since 1.0.0
 */
public class CorrelationException extends RpcException {

    /**
     * Constructs a new correlation exception with the specified detail message.
     *
     * @param message the detail message explaining the correlation error
     */
    public CorrelationException(String message) {
        super(message);
    }

    /**
     * Constructs a new correlation exception with the specified detail message and cause.
     *
     * @param message the detail message explaining the correlation error
     * @param cause the cause of this exception
     */
    public CorrelationException(String message, Throwable cause) {
        super(message, cause);
    }
}
