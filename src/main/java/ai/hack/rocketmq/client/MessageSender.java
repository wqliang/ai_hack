package ai.hack.rocketmq.client;

import ai.hack.rocketmq.client.exception.RpcException;
import ai.hack.rocketmq.client.model.MessageMetadata;
import org.apache.rocketmq.client.exception.MQClientException;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.client.producer.SendCallback;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.client.producer.selector.SelectMessageQueueByHash;
import org.apache.rocketmq.common.message.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Wraps RocketMQ Producer for sending RPC messages.
 * <p>
 * This class provides a simplified interface for sending messages through RocketMQ,
 * handling both synchronous and asynchronous send operations. It supports ordered
 * message delivery for streaming scenarios using message queue selection.
 *
 * @author Claude Code
 * @since 1.0.0
 */
public class MessageSender {

    private static final Logger logger = LoggerFactory.getLogger(MessageSender.class);

    private final DefaultMQProducer producer;
    private final String requestTopic;

    /**
     * Constructs a new MessageSender with the specified producer and topic.
     *
     * @param producer the RocketMQ producer instance
     * @param requestTopic the topic for sending request messages
     */
    public MessageSender(DefaultMQProducer producer, String requestTopic) {
        this.producer = producer;
        this.requestTopic = requestTopic;
    }

    /**
     * Sends a message synchronously to the request topic.
     * <p>
     * This method blocks until the message is successfully sent to the broker
     * or an error occurs. The message metadata is applied as user properties.
     *
     * @param payload the message payload
     * @param metadata the message metadata (correlation ID, sender ID, etc.)
     * @return SendResult containing message ID and queue information
     * @throws RpcException if send operation fails
     */
    public SendResult sendSync(byte[] payload, MessageMetadata metadata) throws RpcException {
        try {
            // Create RocketMQ message
            Message message = new Message(requestTopic, payload);

            // Apply metadata as user properties
            metadata.applyToMessage(message);

            // Send synchronously
            SendResult result = producer.send(message);

            logger.debug("Sent message synchronously: correlationId={}, msgId={}",
                metadata.correlationId(), result.getMsgId());

            return result;
        } catch (Exception e) {
            logger.error("Failed to send message synchronously: correlationId={}",
                metadata.correlationId(), e);
            throw new RpcException("Failed to send message", e);
        }
    }

    /**
     * Sends a message asynchronously to the request topic.
     * <p>
     * This method returns immediately without blocking. The SendCallback
     * is invoked when the send operation completes (success or failure).
     *
     * @param payload the message payload
     * @param metadata the message metadata (correlation ID, sender ID, etc.)
     * @param callback the callback for send result or exception
     * @throws RpcException if send operation cannot be initiated
     */
    public void sendAsync(byte[] payload, MessageMetadata metadata, SendCallback callback)
        throws RpcException {
        try {
            // Create RocketMQ message
            Message message = new Message(requestTopic, payload);

            // Apply metadata as user properties
            metadata.applyToMessage(message);

            // Send asynchronously with callback
            producer.send(message, callback);

            logger.debug("Initiated async send: correlationId={}", metadata.correlationId());
        } catch (Exception e) {
            logger.error("Failed to initiate async send: correlationId={}",
                metadata.correlationId(), e);
            throw new RpcException("Failed to initiate async send", e);
        }
    }

    /**
     * Sends a message with ordered delivery guarantee for streaming scenarios.
     * <p>
     * Uses the session ID to select the same message queue, ensuring all messages
     * with the same session ID are processed by the same consumer in FIFO order.
     *
     * @param payload the message payload
     * @param metadata the message metadata (must include sessionId)
     * @param sessionId the session ID for queue selection (hash key)
     * @return SendResult containing message ID and queue information
     * @throws RpcException if send operation fails
     * @throws IllegalArgumentException if sessionId is null or blank
     */
    public SendResult sendOrdered(byte[] payload, MessageMetadata metadata, String sessionId)
        throws RpcException {
        if (sessionId == null || sessionId.isBlank()) {
            throw new IllegalArgumentException("sessionId must not be null or blank for ordered send");
        }

        try {
            // Create RocketMQ message
            Message message = new Message(requestTopic, payload);

            // Apply metadata as user properties
            metadata.applyToMessage(message);

            // Send with message queue selector using sessionId hash
            SendResult result = producer.send(
                message,
                new SelectMessageQueueByHash(),
                sessionId
            );

            logger.debug("Sent ordered message: sessionId={}, correlationId={}, queueId={}",
                sessionId, metadata.correlationId(), result.getMessageQueue().getQueueId());

            return result;
        } catch (Exception e) {
            logger.error("Failed to send ordered message: sessionId={}, correlationId={}",
                sessionId, metadata.correlationId(), e);
            throw new RpcException("Failed to send ordered message", e);
        }
    }

    /**
     * Starts the underlying RocketMQ producer.
     *
     * @throws RpcException if producer cannot be started
     */
    public void start() throws RpcException {
        try {
            producer.start();
            logger.info("MessageSender started successfully");
        } catch (MQClientException e) {
            logger.error("Failed to start MessageSender", e);
            throw new RpcException("Failed to start producer", e);
        }
    }

    /**
     * Shuts down the underlying RocketMQ producer.
     */
    public void shutdown() {
        producer.shutdown();
        logger.info("MessageSender shut down");
    }
}
