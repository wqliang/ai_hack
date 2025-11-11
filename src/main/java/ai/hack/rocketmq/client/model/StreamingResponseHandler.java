package ai.hack.rocketmq.client.model;

/**
 * Callback interface for handling incremental responses in bidirectional streaming.
 * <p>
 * This interface allows clients to receive responses incrementally as they arrive
 * during a streaming session, rather than waiting for a single final response.
 * This is useful for AI chat scenarios, real-time data processing, and other
 * use cases where partial results should be delivered as soon as available.
 * <p>
 * <strong>Thread Safety:</strong>
 * <p>Implementations must be thread-safe as callbacks may be invoked from
 * RocketMQ consumer threads concurrently.
 * <p>
 * <strong>Usage Example:</strong>
 * <pre>{@code
 * StreamingResponseHandler handler = new StreamingResponseHandler() {
 *     @Override
 *     public void onResponse(RpcResponse response) {
 *         String chunk = new String(response.payload());
 *         System.out.print(chunk); // Print incrementally
 *     }
 *
 *     @Override
 *     public void onComplete() {
 *         System.out.println("\nStreaming complete!");
 *     }
 *
 *     @Override
 *     public void onError(Throwable error) {
 *         System.err.println("Error: " + error.getMessage());
 *     }
 * };
 * }</pre>
 *
 * @author Claude Code
 * @since 1.0.0
 * @see RpcResponse
 */
@FunctionalInterface
public interface StreamingResponseHandler {

    /**
     * Called when an incremental response is received.
     * <p>
     * This method is invoked for each response message received during the
     * streaming session. Responses are delivered in the order they were sent
     * by the receiver.
     * <p>
     * <strong>Important:</strong> This method should return quickly to avoid
     * blocking the RocketMQ consumer thread. For long-running processing,
     * consider dispatching to a separate thread pool.
     *
     * @param response the incremental response (never null)
     */
    void onResponse(RpcResponse response);

    /**
     * Called when the streaming session completes successfully.
     * <p>
     * This method is invoked after all responses have been delivered and the
     * session has ended normally. No more responses will be delivered after
     * this callback.
     * <p>
     * Default implementation does nothing.
     */
    default void onComplete() {
        // Default: no-op
    }

    /**
     * Called when an error occurs during streaming.
     * <p>
     * This method is invoked if an error occurs during the streaming session,
     * such as network failure, timeout, or processing error. The session is
     * considered failed after this callback, and no more responses will be
     * delivered.
     * <p>
     * Default implementation does nothing.
     *
     * @param error the error that occurred (never null)
     */
    default void onError(Throwable error) {
        // Default: no-op
    }
}
