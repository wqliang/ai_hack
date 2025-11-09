package ai.hack.rocketmq;

import ai.hack.rocketmq.callback.MessageCallback;
import ai.hack.rocketmq.config.ClientConfiguration;
import ai.hack.rocketmq.exception.RocketMQException;
import ai.hack.rocketmq.exception.TimeoutException;
import ai.hack.rocketmq.model.Message;
import ai.hack.rocketmq.result.BatchSendResult;
import ai.hack.rocketmq.result.SendResult;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Main async client interface for RocketMQ messaging operations.
 * Provides the core API for asynchronous message publishing and consumption with
 * enterprise-grade features like TLS security, FIFO ordering, and at-least-once delivery.
 */
public interface RocketMQAsyncClient extends AutoCloseable {

    /**
     * Initialize the client with configuration.
     *
     * @param config the client configuration
     * @throws RocketMQException if initialization fails
     */
    void initialize(ClientConfiguration config) throws RocketMQException;

    /**
     * Send a message asynchronously.
     *
     * @param message the message to send
     * @return CompletableFuture that completes with the send result
     * @throws RocketMQException if the send cannot be initiated
     */
    CompletableFuture<SendResult> sendMessageAsync(Message message) throws RocketMQException;

    /**
     * Send a message synchronously with timeout.
     *
     * @param message the message to send
     * @param timeout maximum time to wait for completion
     * @return the send result
     * @throws RocketMQException if the send fails
     * @throws TimeoutException if the send times out
     */
    SendResult sendMessageSync(Message message, Duration timeout) throws RocketMQException, TimeoutException;

    /**
     * Send multiple messages in batch.
     *
     * @param messages list of messages to send
     * @return CompletableFuture that completes with the batch send result
     * @throws RocketMQException if the batch send cannot be initiated
     */
    CompletableFuture<BatchSendResult> sendBatchAsync(List<Message> messages) throws RocketMQException;

    /**
     * Send message and wait for response (request-response pattern).
     *
     * @param message the request message
     * @param timeout maximum time to wait for response
     * @return CompletableFuture that completes with the response message
     * @throws RocketMQException if the request cannot be sent
     */
    CompletableFuture<Message> sendAndReceiveAsync(Message message, Duration timeout) throws RocketMQException;

    /**
     * Subscribe to a topic with message callback.
     *
     * @param topic the topic to subscribe to
     * @param callback the message processing callback
     * @throws RocketMQException if subscription fails
     */
    void subscribe(String topic, MessageCallback callback) throws RocketMQException;

    /**
     * Unsubscribe from a topic.
     *
     * @param topic the topic to unsubscribe from
     * @throws RocketMQException if unsubscription fails
     */
    void unsubscribe(String topic) throws RocketMQException;

    /**
     * Get current client status and metrics.
     *
     * @return current client status
     */
    ClientStatus getClientStatus();

    /**
     * Shutdown the client gracefully.
     *
     * @param timeout maximum time to wait for shutdown
     * @throws RocketMQException if shutdown fails
     */
    void shutdown(Duration timeout) throws RocketMQException;

    /**
     * Check if the client is initialized and ready.
     *
     * @return true if ready, false otherwise
     */
    boolean isReady();

    /**
     * Check if the client is shutting down.
     *
     * @return true if shutting down, false otherwise
     */
    default boolean isShuttingDown() {
        return !isReady();
    }
}