package ai.hack.rocketmq.client;

import ai.hack.rocketmq.client.config.RpcClientConfig;
import ai.hack.rocketmq.client.exception.RpcException;
import ai.hack.rocketmq.client.model.MessageMetadata;
import ai.hack.rocketmq.client.model.RpcResponse;
import jakarta.annotation.PreDestroy;
import org.apache.rocketmq.client.consumer.DefaultMQPushConsumer;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyContext;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyStatus;
import org.apache.rocketmq.client.consumer.listener.MessageListenerConcurrently;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.common.message.Message;
import org.apache.rocketmq.common.message.MessageExt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * RPC Server implementation that receives requests and invokes registered receivers.
 * <p>
 * This class acts as the server-side component of the RPC system. It listens to
 * the request topic, processes incoming messages using registered {@link RpcReceiver}
 * implementations, and sends responses back to the appropriate sender topics.
 * <p>
 * <strong>Features:</strong>
 * <ul>
 *   <li>Automatic receiver registration via constructor injection</li>
 *   <li>Support for both single and streaming requests</li>
 *   <li>Message accumulation for streaming sessions</li>
 *   <li>Error handling with error responses</li>
 *   <li>Lifecycle management with Spring @PreDestroy</li>
 * </ul>
 * <p>
 * <strong>Usage:</strong>
 * <pre>{@code
 * @Configuration
 * public class RpcConfig {
 *     @Bean
 *     public RpcServer rpcServer(RpcClientConfig config, RpcReceiver receiver) {
 *         RpcServer server = new RpcServer(config, receiver);
 *         server.start();
 *         return server;
 *     }
 * }
 * }</pre>
 *
 * @author Claude Code
 * @since 1.0.0
 * @see RpcReceiver
 */
@Service
public class RpcServer {

    private static final Logger logger = LoggerFactory.getLogger(RpcServer.class);

    private final RpcClientConfig config;
    private final RpcReceiver receiver;

    private DefaultMQPushConsumer consumer;
    private DefaultMQProducer producer;

    // Track streaming session messages: sessionId -> list of messages
    private final Map<String, List<byte[]>> sessionMessages;

    private final AtomicBoolean started = new AtomicBoolean(false);

    /**
     * Constructs a new RpcServer with configuration and receiver.
     *
     * @param config the RPC configuration
     * @param receiver the receiver implementation for processing requests
     */
    public RpcServer(RpcClientConfig config, RpcReceiver receiver) {
        this.config = config;
        this.receiver = receiver;
        this.sessionMessages = new ConcurrentHashMap<>();

        logger.info("RpcServer created with receiver: {}", receiver.getClass().getSimpleName());
    }

    /**
     * Starts the RPC server to listen for incoming requests.
     *
     * @throws RpcException if server cannot be started
     */
    public void start() throws RpcException {
        if (started.get()) {
            throw new IllegalStateException("RpcServer is already started");
        }

        try {
            logger.info("Starting RpcServer...");

            // Create and configure producer for sending responses
            producer = new DefaultMQProducer("RPC_SERVER_PRODUCER");
            producer.setNamesrvAddr(config.getBrokerUrl());
            producer.start();

            // Create and configure consumer for receiving requests
            consumer = new DefaultMQPushConsumer("RPC_SERVER_CONSUMER");
            consumer.setNamesrvAddr(config.getBrokerUrl());
            consumer.subscribe(config.getRequestTopic(), "*");
            consumer.setConsumeThreadMin(2);
            consumer.setConsumeThreadMax(8);

            // Register message listener to process requests
            consumer.registerMessageListener(new MessageListenerConcurrently() {
                @Override
                public ConsumeConcurrentlyStatus consumeMessage(
                    List<MessageExt> msgs,
                    ConsumeConcurrentlyContext context
                ) {
                    for (MessageExt msg : msgs) {
                        try {
                            processIncomingMessage(msg);
                        } catch (Exception e) {
                            logger.error("Error processing request message: msgId={}",
                                msg.getMsgId(), e);
                            // Continue processing other messages
                        }
                    }
                    return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
                }
            });

            // Start consumer
            consumer.start();

            started.set(true);

            logger.info("RpcServer started successfully, listening on topic: {}",
                config.getRequestTopic());

        } catch (Exception e) {
            cleanup();
            logger.error("Failed to start RpcServer", e);
            throw new RpcException("Failed to start RPC server", e);
        }
    }

    /**
     * Processes an incoming request message.
     *
     * @param message the received RocketMQ message
     */
    private void processIncomingMessage(MessageExt message) {
        try {
            // Extract metadata
            MessageMetadata metadata = MessageMetadata.fromMessage(message);
            String correlationId = metadata.correlationId();
            String senderId = metadata.senderId();
            String sessionId = metadata.sessionId();

            logger.debug("Processing message: correlationId={}, senderId={}, sessionId={}",
                correlationId, senderId, sessionId);

            // Determine if this is a streaming message
            boolean isStreaming = (sessionId != null && !sessionId.isBlank());

            if (isStreaming) {
                processStreamingMessage(message, metadata);
            } else {
                processSingleMessage(message, metadata);
            }

        } catch (Exception e) {
            logger.error("Error processing incoming message: msgId={}", message.getMsgId(), e);
        }
    }

    /**
     * Processes a single (non-streaming) request message.
     *
     * @param message the RocketMQ message
     * @param metadata the extracted metadata
     */
    private void processSingleMessage(MessageExt message, MessageMetadata metadata) {
        String correlationId = metadata.correlationId();
        String senderId = metadata.senderId();

        try {
            // Invoke receiver to process request
            byte[] requestPayload = message.getBody();
            byte[] responsePayload = receiver.processRequest(requestPayload);

            // Send success response
            sendResponse(senderId, correlationId, responsePayload, true, null);

            logger.debug("Processed single request: correlationId={}", correlationId);

        } catch (Exception e) {
            logger.error("Receiver failed to process request: correlationId={}",
                correlationId, e);

            // Send error response
            sendResponse(senderId, correlationId, new byte[0], false,
                "Processing error: " + e.getMessage());
        }
    }

    /**
     * Processes a streaming request message.
     *
     * @param message the RocketMQ message
     * @param metadata the extracted metadata
     */
    private void processStreamingMessage(MessageExt message, MessageMetadata metadata) {
        String sessionId = metadata.sessionId();
        String correlationId = metadata.correlationId();
        String senderId = metadata.senderId();

        // Check if this is END marker (has correlation ID)
        boolean isEndMarker = (correlationId != null && !correlationId.isBlank());

        if (isEndMarker) {
            // Process accumulated messages and send aggregated response
            processStreamingEnd(sessionId, correlationId, senderId);
        } else {
            // Accumulate message for this session
            byte[] payload = message.getBody();
            sessionMessages.computeIfAbsent(sessionId, k -> new ArrayList<>()).add(payload);

            logger.debug("Accumulated streaming message: sessionId={}, totalMessages={}",
                sessionId, sessionMessages.get(sessionId).size());
        }
    }

    /**
     * Processes the end of a streaming session and sends aggregated response.
     *
     * @param sessionId the session identifier
     * @param correlationId the correlation ID for the response
     * @param senderId the sender ID to send response to
     */
    private void processStreamingEnd(String sessionId, String correlationId, String senderId) {
        try {
            // Get accumulated messages
            List<byte[]> messages = sessionMessages.remove(sessionId);

            if (messages == null || messages.isEmpty()) {
                logger.warn("No messages found for streaming session: sessionId={}", sessionId);
                messages = Collections.emptyList();
            }

            logger.info("Processing streaming session end: sessionId={}, messageCount={}",
                sessionId, messages.size());

            // Invoke receiver to process streaming request
            byte[] responsePayload = receiver.processStreamingRequest(sessionId, messages);

            // Send success response
            sendResponse(senderId, correlationId, responsePayload, true, null);

            logger.info("Processed streaming session: sessionId={}, messageCount={}",
                sessionId, messages.size());

        } catch (Exception e) {
            logger.error("Receiver failed to process streaming session: sessionId={}",
                sessionId, e);

            // Send error response
            sendResponse(senderId, correlationId, new byte[0], false,
                "Streaming processing error: " + e.getMessage());

            // Clean up session
            sessionMessages.remove(sessionId);
        }
    }

    /**
     * Sends a response back to the sender.
     *
     * @param senderId the sender's unique ID
     * @param correlationId the correlation ID to match request
     * @param payload the response payload
     * @param success whether processing was successful
     * @param errorMessage error message if success=false
     */
    private void sendResponse(String senderId, String correlationId, byte[] payload,
                              boolean success, String errorMessage) {
        try {
            // Determine response topic
            String responseTopic = config.getResponseTopicPrefix() + senderId;

            // Create response
            RpcResponse response = success ?
                RpcResponse.success(correlationId, payload) :
                RpcResponse.error(correlationId, errorMessage);

            // Create RocketMQ message
            Message message = new Message(responseTopic, response.payload());
            message.putUserProperty("correlationId", response.correlationId());
            message.putUserProperty("success", String.valueOf(response.success()));
            if (response.errorMessage() != null) {
                message.putUserProperty("errorMessage", response.errorMessage());
            }
            message.putUserProperty("timestamp", String.valueOf(response.timestamp()));

            // Send response
            producer.send(message);

            logger.debug("Sent response: correlationId={}, responseTopic={}, success={}",
                correlationId, responseTopic, success);

        } catch (Exception e) {
            logger.error("Failed to send response: correlationId={}", correlationId, e);
        }
    }

    /**
     * Shuts down the RPC server and releases resources.
     */
    @PreDestroy
    public void close() {
        if (!started.get()) {
            return;
        }

        logger.info("Shutting down RpcServer...");

        cleanup();

        started.set(false);

        logger.info("RpcServer shut down successfully");
    }

    /**
     * Internal cleanup method.
     */
    private void cleanup() {
        // Clear session messages
        sessionMessages.clear();

        // Shutdown consumer
        if (consumer != null) {
            consumer.shutdown();
        }

        // Shutdown producer
        if (producer != null) {
            producer.shutdown();
        }
    }

    /**
     * Checks if the server is started.
     *
     * @return true if server is started, false otherwise
     */
    public boolean isStarted() {
        return started.get();
    }
}
