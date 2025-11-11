package ai.hack.rocketmq.client.exception;

/**
 * Base exception for all RPC client errors.
 * <p>
 * This exception serves as the parent for all RPC-related exceptions,
 * providing a common type for error handling in RPC operations.
 *
 * @author Claude Code
 * @since 1.0.0
 */
public class RpcException extends RuntimeException {

    /**
     * Constructs a new RPC exception with the specified detail message.
     *
     * @param message the detail message explaining the error
     */
    public RpcException(String message) {
        super(message);
    }

    /**
     * Constructs a new RPC exception with the specified detail message and cause.
     *
     * @param message the detail message explaining the error
     * @param cause the cause of this exception
     */
    public RpcException(String message, Throwable cause) {
        super(message, cause);
    }
}
