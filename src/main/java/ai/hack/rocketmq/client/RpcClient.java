package ai.hack.rocketmq.client;

import ai.hack.rocketmq.client.exception.RpcException;
import ai.hack.rocketmq.client.exception.RpcTimeoutException;
import ai.hack.rocketmq.client.exception.SessionException;
import ai.hack.rocketmq.client.model.RpcResponse;
import ai.hack.rocketmq.client.model.StreamingResponseHandler;

import java.util.concurrent.CompletableFuture;

/**
 * Main interface for RocketMQ RPC Client operations.
 * <p>
 * This interface provides both synchronous (blocking) and asynchronous (non-blocking)
 * methods for RPC-style communication through RocketMQ. It supports single request-response
 * patterns as well as streaming scenarios for AI and batch processing use cases.
 *
 * @author Claude Code
 * @since 1.0.0
 */
public interface RpcClient extends AutoCloseable {

    /**
     * Sends a request synchronously and blocks until response is received or timeout occurs.
     * <p>
     * This method blocks the calling thread until either a response is received from the
     * receiver or the timeout period expires. It is suitable for scenarios where immediate
     * response is required and blocking is acceptable.
     *
     * @param payload the request data (non-null, can be empty array)
     * @param timeoutMillis timeout in milliseconds (1 to 300000)
     * @return the response from the receiver
     * @throws RpcTimeoutException if no response received within timeout period
     * @throws RpcException if send operation fails or client is not started
     * @throws IllegalArgumentException if payload is null or timeout is invalid
     */
    RpcResponse sendSync(byte[] payload, long timeoutMillis)
        throws RpcTimeoutException, RpcException;

    /**
     * Sends a request asynchronously and returns immediately with a CompletableFuture.
     * <p>
     * This method returns immediately without blocking the calling thread. The response
     * (or exception) will be delivered via the CompletableFuture when available. This is
     * suitable for high-throughput scenarios and when the caller needs to perform other
     * work while waiting for the response.
     *
     * @param payload the request data (non-null, can be empty array)
     * @param timeoutMillis timeout in milliseconds (1 to 300000)
     * @return CompletableFuture that will be completed with response or exception
     * @throws IllegalArgumentException if payload is null or timeout is invalid
     */
    CompletableFuture<RpcResponse> sendAsync(byte[] payload, long timeoutMillis);

    /**
     * Starts a new streaming session and returns the session ID.
     * <p>
     * A streaming session allows sending multiple related messages that should be
     * processed by the same receiver in order. The session ID is used to route all
     * messages to the same message queue, ensuring FIFO processing.
     *
     * @return unique session ID (UUID format) for this streaming session
     * @throws RpcException if session creation fails or client is not started
     * @throws IllegalStateException if max concurrent sessions limit is reached
     */
    String sendStreamingStart() throws RpcException;

    /**
     * Sends a message as part of an existing streaming session.
     * <p>
     * All messages with the same session ID are guaranteed to be delivered to the
     * same receiver in the order they were sent (FIFO guarantee).
     *
     * @param sessionId the session ID returned from sendStreamingStart() (non-null)
     * @param payload the message data (non-null, can be empty array)
     * @throws SessionException if sessionId is unknown or session is not active
     * @throws RpcException if send operation fails
     * @throws IllegalArgumentException if sessionId or payload is null
     */
    void sendStreamingMessage(String sessionId, byte[] payload)
        throws SessionException, RpcException;

    /**
     * Ends a streaming session and waits for final response.
     * <p>
     * This method signals the end of the message stream, sends an end marker,
     * and blocks until the final aggregated response is received or timeout occurs.
     *
     * @param sessionId the session ID to end (non-null)
     * @param timeoutMillis timeout for receiving final response
     * @return the final response from receiver after processing all messages
     * @throws SessionException if sessionId is unknown
     * @throws RpcTimeoutException if no response within timeout
     * @throws RpcException if operation fails
     * @throws IllegalArgumentException if sessionId is null or timeout is invalid
     */
    RpcResponse sendStreamingEnd(String sessionId, long timeoutMillis)
        throws SessionException, RpcTimeoutException, RpcException;

    /**
     * Sends a message in a bidirectional streaming session with incremental response handling.
     * <p>
     * This method enables bidirectional streaming where responses can be delivered
     * incrementally as they become available, rather than waiting for all messages
     * to be sent before receiving any response. This is ideal for AI chat applications,
     * real-time data processing, and other scenarios requiring immediate feedback.
     * <p>
     * The provided handler will be invoked for each response received from the receiver.
     * Multiple responses may be received for a single sent message, or responses may
     * aggregate multiple sent messages.
     * <p>
     * <strong>Example Use Case (AI Chat):</strong>
     * <pre>{@code
     * String sessionId = client.sendStreamingStart();
     *
     * StreamingResponseHandler handler = response -> {
     *     String chunk = new String(response.payload());
     *     System.out.print(chunk); // Print each chunk as it arrives
     * };
     *
     * client.sendBidirectionalMessage(sessionId, "Hello".getBytes(), handler);
     * client.sendBidirectionalMessage(sessionId, " World".getBytes(), handler);
     *
     * client.sendStreamingEnd(sessionId, 30000);
     * }</pre>
     *
     * @param sessionId the session ID for this stream (non-null)
     * @param payload the message data (non-null)
     * @param responseHandler callback for handling incremental responses (non-null)
     * @throws SessionException if sessionId is unknown or session is not active
     * @throws RpcException if send operation fails
     * @throws IllegalArgumentException if any parameter is null
     * @since 1.0.0
     */
    void sendBidirectionalMessage(String sessionId, byte[] payload,
                                  StreamingResponseHandler responseHandler)
        throws SessionException, RpcException;

    /**
     * Initializes the client and starts RocketMQ producer/consumer.
     * <p>
     * This method must be called before any send operations. It initializes the
     * RocketMQ producer, creates the response topic, subscribes the consumer, and
     * starts all background threads.
     *
     * @throws RpcException if initialization fails (broker unreachable, config error, etc.)
     * @throws IllegalStateException if client is already started
     */
    void start() throws RpcException;

    /**
     * Shuts down the client and releases all resources.
     * <p>
     * This method cancels all pending requests, terminates all active sessions,
     * shuts down the RocketMQ producer and consumer, and releases thread pools.
     * It is safe to call multiple times (idempotent).
     * <p>
     * Implements AutoCloseable for use with try-with-resources.
     */
    @Override
    void close();

    /**
     * Checks if the client is currently started and ready to use.
     *
     * @return true if client is started, false otherwise
     */
    boolean isStarted();

    /**
     * Gets the performance metrics for this client.
     * <p>
     * Returns metrics including request counts, latencies, session statistics,
     * and throughput measurements. Metrics are collected from the time the client
     * was started.
     *
     * @return RpcClientMetrics instance with current statistics
     * @throws IllegalStateException if client is not started
     * @since 1.0.0
     */
    RpcClientMetrics getMetrics();
}
