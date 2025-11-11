package ai.hack.rocketmq.client;

import ai.hack.rocketmq.client.exception.RpcException;
import org.apache.rocketmq.client.consumer.DefaultMQPushConsumer;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyContext;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyStatus;
import org.apache.rocketmq.client.consumer.listener.MessageListenerConcurrently;
import org.apache.rocketmq.client.exception.MQClientException;
import org.apache.rocketmq.common.message.MessageExt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.function.Consumer;

/**
 * Wraps RocketMQ Consumer for receiving RPC response messages.
 * <p>
 * This class provides a simplified interface for subscribing to response topics
 * and processing incoming messages. It uses push-style consumption with concurrent
 * message listeners for high throughput.
 *
 * @author Claude Code
 * @since 1.0.0
 */
public class MessageReceiver {

    private static final Logger logger = LoggerFactory.getLogger(MessageReceiver.class);

    private final DefaultMQPushConsumer consumer;
    private final String responseTopic;
    private Consumer<MessageExt> messageHandler;

    /**
     * Constructs a new MessageReceiver with the specified consumer and topic.
     *
     * @param consumer the RocketMQ consumer instance
     * @param responseTopic the topic for receiving response messages
     */
    public MessageReceiver(DefaultMQPushConsumer consumer, String responseTopic) {
        this.consumer = consumer;
        this.responseTopic = responseTopic;
    }

    /**
     * Sets the message handler for processing received messages.
     * <p>
     * The handler is invoked for each received message. It should extract
     * the correlation ID from message properties and complete the corresponding
     * pending request future.
     *
     * @param handler the function to process received messages
     */
    public void setMessageHandler(Consumer<MessageExt> handler) {
        this.messageHandler = handler;
    }

    /**
     * Subscribes to the response topic and starts consuming messages.
     * <p>
     * Registers a concurrent message listener that invokes the configured
     * message handler for each received message. Messages are acknowledged
     * automatically after successful processing.
     *
     * @throws RpcException if subscription or consumer start fails
     * @throws IllegalStateException if message handler is not set
     */
    public void start() throws RpcException {
        if (messageHandler == null) {
            throw new IllegalStateException("Message handler must be set before starting");
        }

        try {
            // Subscribe to response topic with wildcard tag (all messages)
            consumer.subscribe(responseTopic, "*");

            // Register concurrent message listener
            consumer.registerMessageListener(new MessageListenerConcurrently() {
                @Override
                public ConsumeConcurrentlyStatus consumeMessage(
                    List<MessageExt> msgs,
                    ConsumeConcurrentlyContext context
                ) {
                    // Process each message in the batch
                    for (MessageExt msg : msgs) {
                        try {
                            // Extract correlation ID for logging
                            String correlationId = msg.getUserProperty("correlationId");

                            logger.debug("Received message: correlationId={}, msgId={}",
                                correlationId, msg.getMsgId());

                            // Invoke the message handler
                            messageHandler.accept(msg);

                        } catch (Exception e) {
                            logger.error("Error processing message: msgId={}", msg.getMsgId(), e);
                            // Continue processing other messages even if one fails
                        }
                    }

                    // Acknowledge all messages as consumed
                    return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
                }
            });

            // Start the consumer
            consumer.start();

            logger.info("MessageReceiver started successfully, subscribed to topic: {}",
                responseTopic);

        } catch (MQClientException e) {
            logger.error("Failed to start MessageReceiver for topic: {}", responseTopic, e);
            throw new RpcException("Failed to start consumer", e);
        }
    }

    /**
     * Unsubscribes from the response topic.
     *
     * @throws RpcException if unsubscribe operation fails
     */
    public void unsubscribe() throws RpcException {
        try {
            consumer.unsubscribe(responseTopic);
            logger.info("Unsubscribed from topic: {}", responseTopic);
        } catch (Exception e) {
            logger.error("Failed to unsubscribe from topic: {}", responseTopic, e);
            throw new RpcException("Failed to unsubscribe", e);
        }
    }

    /**
     * Shuts down the underlying RocketMQ consumer.
     * <p>
     * This method stops message consumption and releases all resources.
     * It is safe to call even if the consumer is not started.
     */
    public void shutdown() {
        consumer.shutdown();
        logger.info("MessageReceiver shut down");
    }

    /**
     * Gets the response topic this receiver is subscribed to.
     *
     * @return the response topic name
     */
    public String getResponseTopic() {
        return responseTopic;
    }
}
