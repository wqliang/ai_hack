package ai.hack.rocketmq.client.exception;

/**
 * Exception thrown when an RPC request times out without receiving a response.
 * <p>
 * This exception is thrown when a synchronous or streaming RPC call does not
 * receive a response within the configured timeout period. It indicates that
 * the request may still be processing or the network/receiver is unresponsive.
 *
 * @author Claude Code
 * @since 1.0.0
 */
public class RpcTimeoutException extends RpcException {

    /**
     * Constructs a new RPC timeout exception with the specified detail message.
     *
     * @param message the detail message explaining the timeout
     */
    public RpcTimeoutException(String message) {
        super(message);
    }

    /**
     * Constructs a new RPC timeout exception with the specified detail message and cause.
     *
     * @param message the detail message explaining the timeout
     * @param cause the cause of this exception
     */
    public RpcTimeoutException(String message, Throwable cause) {
        super(message, cause);
    }
}
