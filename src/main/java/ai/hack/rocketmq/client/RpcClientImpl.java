package ai.hack.rocketmq.client;

import ai.hack.rocketmq.client.config.RpcClientConfig;
import ai.hack.rocketmq.client.exception.RpcException;
import ai.hack.rocketmq.client.exception.RpcTimeoutException;
import ai.hack.rocketmq.client.exception.SessionException;
import ai.hack.rocketmq.client.model.MessageMetadata;
import ai.hack.rocketmq.client.model.RpcRequest;
import ai.hack.rocketmq.client.model.RpcResponse;
import ai.hack.rocketmq.client.model.StreamingResponseHandler;
import ai.hack.rocketmq.client.model.StreamingSession;
import jakarta.annotation.PreDestroy;
import org.apache.rocketmq.client.consumer.DefaultMQPushConsumer;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.client.producer.SendCallback;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.common.message.MessageExt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Implementation of RpcClient interface providing RPC-style communication through RocketMQ.
 * <p>
 * This class implements both synchronous (blocking) and asynchronous (non-blocking) RPC calls.
 * Each client instance has a unique sender ID and dedicated response topic for receiving replies.
 * Thread-safe for concurrent request handling.
 *
 * <h3>Key Features:</h3>
 * <ul>
 *   <li>Synchronous and asynchronous RPC methods</li>
 *   <li>Request-response correlation using UUID correlation IDs</li>
 *   <li>Configurable timeout with automatic cleanup</li>
 *   <li>Lifecycle management with proper resource cleanup</li>
 *   <li>Support for streaming sessions (future enhancement)</li>
 * </ul>
 *
 * @author Claude Code
 * @since 1.0.0
 */
@Service
public class RpcClientImpl implements RpcClient {

    private static final Logger logger = LoggerFactory.getLogger(RpcClientImpl.class);

    private final RpcClientConfig config;
    private final String senderId;
    private final String responseTopic;

    private MessageSender messageSender;
    private MessageReceiver messageReceiver;
    private CorrelationManager correlationManager;
    private SessionManager sessionManager;
    private ScheduledExecutorService timeoutExecutor;
    private RpcClientMetrics metrics;
    private ScheduledFuture<?> metricsLoggingTask;

    private final AtomicBoolean started = new AtomicBoolean(false);

    /**
     * Constructs a new RpcClientImpl with the specified configuration.
     * <p>
     * Generates a unique sender ID (UUID) and determines the response topic name.
     * Actual RocketMQ producers/consumers are created and started in the start() method.
     *
     * @param config the RPC client configuration
     */
    public RpcClientImpl(RpcClientConfig config) {
        this.config = config;
        this.senderId = UUID.randomUUID().toString();
        this.responseTopic = config.getResponseTopicPrefix() + senderId;

        logger.info("RpcClient created: senderId={}, responseTopic={}", senderId, responseTopic);
    }

    /**
     * {@inheritDoc}
     *
     * <p><strong>Implementation Details:</strong></p>
     * <p>This method delegates to {@link #sendAsync(byte[], long)} and blocks on the
     * returned CompletableFuture. This provides consistent correlation tracking and
     * timeout handling for both sync and async operations.</p>
     *
     * @throws RpcTimeoutException if no response received within timeout
     * @throws RpcException if client not started or send fails
     */
    @Override
    public RpcResponse sendSync(byte[] payload, long timeoutMillis)
        throws RpcTimeoutException, RpcException {

        if (!started.get()) {
            throw new RpcException("RpcClient is not started");
        }

        try {
            // Delegate to async implementation and block on result
            CompletableFuture<RpcResponse> future = sendAsync(payload, timeoutMillis);

            // Block until response or timeout
            return future.get(timeoutMillis, TimeUnit.MILLISECONDS);

        } catch (TimeoutException e) {
            throw new RpcTimeoutException("Request timeout after " + timeoutMillis + "ms", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RpcException("Request interrupted", e);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RpcTimeoutException) {
                throw (RpcTimeoutException) cause;
            } else if (cause instanceof RpcException) {
                throw (RpcException) cause;
            } else {
                throw new RpcException("Request failed", cause);
            }
        }
    }

    /**
     * {@inheritDoc}
     *
     * <p><strong>Implementation Details:</strong></p>
     * <p>Steps performed:</p>
     * <ol>
     *   <li>Generate unique correlation ID (UUID)</li>
     *   <li>Register pending request with CorrelationManager (creates CompletableFuture and timeout task)</li>
     *   <li>Create RpcRequest with correlation ID and sender ID</li>
     *   <li>Create MessageMetadata with REQUEST type</li>
     *   <li>Send message asynchronously via MessageSender</li>
     *   <li>Return CompletableFuture (will be completed when response arrives or timeout occurs)</li>
     * </ol>
     *
     * @throws IllegalStateException if client not started
     * @throws IllegalArgumentException if payload is null or timeout invalid
     */
    @Override
    public CompletableFuture<RpcResponse> sendAsync(byte[] payload, long timeoutMillis) {
        if (!started.get()) {
            return CompletableFuture.failedFuture(
                new RpcException("RpcClient is not started")
            );
        }

        if (payload == null) {
            return CompletableFuture.failedFuture(
                new IllegalArgumentException("payload must not be null")
            );
        }

        if (timeoutMillis <= 0 || timeoutMillis > 300000) {
            return CompletableFuture.failedFuture(
                new IllegalArgumentException("timeoutMillis must be between 1 and 300000")
            );
        }

        // Check concurrent request limit
        if (correlationManager.getPendingCount() >= config.getMaxConcurrentRequests()) {
            return CompletableFuture.failedFuture(
                new RpcException("Max concurrent requests limit reached: " +
                    config.getMaxConcurrentRequests())
            );
        }

        // Generate unique correlation ID
        String correlationId = UUID.randomUUID().toString();

        // Record metrics: new request
        metrics.recordRequest();
        long startTime = System.nanoTime();

        // Register pending request with timeout handling
        CompletableFuture<RpcResponse> future = correlationManager.registerRequest(
            correlationId,
            timeoutMillis
        );

        // Add metrics recording on completion
        future.whenComplete((response, throwable) -> {
            long latencyNanos = System.nanoTime() - startTime;
            if (throwable == null && response != null && response.success()) {
                metrics.recordSuccess(latencyNanos);
                metrics.recordBytesReceived(response.payload() != null ? response.payload().length : 0);
            } else if (throwable instanceof java.util.concurrent.TimeoutException ||
                (throwable instanceof java.util.concurrent.ExecutionException &&
                    throwable.getCause() instanceof RpcTimeoutException)) {
                metrics.recordTimeout();
            } else {
                metrics.recordFailure();
            }
        });

        try {
            // Record bytes sent
            metrics.recordBytesSent(payload != null ? payload.length : 0);

            // Create message metadata
            MessageMetadata metadata = new MessageMetadata(
                correlationId,
                senderId,
                null,  // No session ID for non-streaming requests
                MessageMetadata.MessageType.REQUEST,
                System.currentTimeMillis()
            );

            // Send message asynchronously
            messageSender.sendAsync(payload, metadata, new SendCallback() {
                @Override
                public void onSuccess(SendResult sendResult) {
                    logger.debug("Async send successful: correlationId={}, msgId={}",
                        correlationId, sendResult.getMsgId());
                }

                @Override
                public void onException(Throwable e) {
                    logger.error("Async send failed: correlationId={}", correlationId, e);
                    // Cancel the pending request and complete future with exception
                    correlationManager.cancelRequest(correlationId);
                    future.completeExceptionally(new RpcException("Send failed", e));
                }
            });

            logger.debug("Initiated async RPC request: correlationId={}, timeout={}ms",
                correlationId, timeoutMillis);

        } catch (Exception e) {
            // If send initiation fails, cancel the pending request
            correlationManager.cancelRequest(correlationId);
            future.completeExceptionally(new RpcException("Failed to send request", e));
        }

        return future;
    }

    /**
     * {@inheritDoc}
     *
     * <p><strong>Implementation Details:</strong></p>
     * <p>Steps performed:</p>
     * <ol>
     *   <li>Generate unique session ID (UUID)</li>
     *   <li>Generate correlation ID for final response</li>
     *   <li>Create StreamingSession and register in SessionManager</li>
     *   <li>Check max concurrent sessions limit</li>
     * </ol>
     *
     * @return session ID for this streaming session
     * @throws RpcException if client not started or max sessions exceeded
     */
    @Override
    public String sendStreamingStart() throws RpcException {
        if (!started.get()) {
            throw new RpcException("RpcClient is not started");
        }

        // Check concurrent sessions limit
        if (sessionManager.getActiveSessionCount() >= config.getMaxConcurrentSessions()) {
            throw new RpcException("Max concurrent sessions limit reached: " +
                config.getMaxConcurrentSessions());
        }

        // Generate unique session ID and correlation ID
        String sessionId = UUID.randomUUID().toString();
        String correlationId = UUID.randomUUID().toString();

        // Create and register streaming session
        StreamingSession session = StreamingSession.create(sessionId, senderId, correlationId);
        try {
            sessionManager.registerSession(session);
            metrics.recordSessionStart();
            logger.info("Started streaming session: sessionId={}, correlationId={}",
                sessionId, correlationId);
            return sessionId;
        } catch (SessionException e) {
            throw new RpcException("Failed to start streaming session", e);
        }
    }

    /**
     * {@inheritDoc}
     *
     * <p><strong>Implementation Details:</strong></p>
     * <p>Steps performed:</p>
     * <ol>
     *   <li>Validate session exists and is active</li>
     *   <li>Record activity in SessionManager (increment message count, update timestamp)</li>
     *   <li>Create MessageMetadata with session ID</li>
     *   <li>Send message using ordered sending (MessageQueueSelector) to ensure same queue</li>
     * </ol>
     *
     * <p>All messages with the same session ID are routed to the same message queue,
     * ensuring they are processed by the same receiver in FIFO order.</p>
     *
     * @throws SessionException if session not found or not active
     * @throws RpcException if client not started or send fails
     */
    @Override
    public void sendStreamingMessage(String sessionId, byte[] payload)
        throws SessionException, RpcException {
        if (!started.get()) {
            throw new RpcException("RpcClient is not started");
        }

        if (payload == null) {
            throw new IllegalArgumentException("payload must not be null");
        }

        // Validate session is active and record activity
        StreamingSession session = sessionManager.recordActivity(sessionId);

        try {
            // Create message metadata with session ID
            MessageMetadata metadata = new MessageMetadata(
                null,  // No correlation ID for streaming messages (only for final response)
                senderId,
                sessionId,
                MessageMetadata.MessageType.REQUEST,
                System.currentTimeMillis()
            );

            // Send using ordered delivery (MessageQueueSelector with sessionId hash)
            messageSender.sendOrdered(payload, metadata, sessionId);

            // Record metrics
            metrics.recordStreamingMessage();
            metrics.recordBytesSent(payload.length);

            logger.debug("Sent streaming message: sessionId={}, messageCount={}",
                sessionId, session.messageCount());

        } catch (Exception e) {
            logger.error("Failed to send streaming message: sessionId={}", sessionId, e);
            throw new RpcException("Failed to send streaming message", e);
        }
    }

    /**
     * {@inheritDoc}
     *
     * <p><strong>Implementation Details:</strong></p>
     * <p>Steps performed:</p>
     * <ol>
     *   <li>Validate session exists and is active</li>
     *   <li>Deactivate session (no more messages can be sent)</li>
     *   <li>Register correlation for final response</li>
     *   <li>Send END marker message with correlation ID</li>
     *   <li>Wait for final aggregated response (blocking with timeout)</li>
     *   <li>Remove session from SessionManager</li>
     * </ol>
     *
     * @return final aggregated response from receiver
     * @throws SessionException if session not found or not active
     * @throws RpcTimeoutException if no response received within timeout
     * @throws RpcException if client not started or send fails
     */
    @Override
    public RpcResponse sendStreamingEnd(String sessionId, long timeoutMillis)
        throws SessionException, RpcTimeoutException, RpcException {
        if (!started.get()) {
            throw new RpcException("RpcClient is not started");
        }

        if (timeoutMillis <= 0 || timeoutMillis > 300000) {
            throw new IllegalArgumentException("timeoutMillis must be between 1 and 300000");
        }

        // Validate session and deactivate
        StreamingSession session = sessionManager.getActiveSession(sessionId);
        sessionManager.deactivateSession(sessionId);

        logger.info("Ending streaming session: sessionId={}, totalMessages={}",
            sessionId, session.messageCount());

        try {
            // Get correlation ID for final response
            String correlationId = session.correlationId();

            // Register pending request for final response
            CompletableFuture<RpcResponse> future = correlationManager.registerRequest(
                correlationId,
                timeoutMillis
            );

            // Create END marker metadata
            MessageMetadata metadata = new MessageMetadata(
                correlationId,
                senderId,
                sessionId,
                MessageMetadata.MessageType.REQUEST,
                System.currentTimeMillis()
            );

            // Send END marker using ordered delivery (same queue as stream messages)
            messageSender.sendOrdered(new byte[0], metadata, sessionId);

            logger.debug("Sent streaming END marker: sessionId={}, correlationId={}",
                sessionId, correlationId);

            // Wait for final response (blocking)
            RpcResponse response = future.get(timeoutMillis, TimeUnit.MILLISECONDS);

            // Record bytes received
            if (response.payload() != null) {
                metrics.recordBytesReceived(response.payload().length);
            }

            // Remove session from manager
            sessionManager.removeSession(sessionId);

            // Record session end
            metrics.recordSessionEnd();

            logger.info("Streaming session completed: sessionId={}, totalMessages={}",
                sessionId, session.messageCount());

            return response;

        } catch (TimeoutException e) {
            // Remove session on timeout
            sessionManager.removeSession(sessionId);
            metrics.recordSessionEnd();
            throw new RpcTimeoutException("Streaming end timeout after " + timeoutMillis + "ms", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            sessionManager.removeSession(sessionId);
            metrics.recordSessionEnd();
            throw new RpcException("Streaming end interrupted", e);
        } catch (ExecutionException e) {
            sessionManager.removeSession(sessionId);
            metrics.recordSessionEnd();
            Throwable cause = e.getCause();
            if (cause instanceof RpcTimeoutException) {
                throw (RpcTimeoutException) cause;
            } else if (cause instanceof RpcException) {
                throw (RpcException) cause;
            } else {
                throw new RpcException("Streaming end failed", cause);
            }
        } catch (Exception e) {
            sessionManager.removeSession(sessionId);
            metrics.recordSessionEnd();
            throw new RpcException("Failed to end streaming session", e);
        }
    }

    /**
     * {@inheritDoc}
     *
     * <p><strong>Implementation Details:</strong></p>
     * <p>This method enables bidirectional streaming where the receiver can send
     * incremental responses while the sender is still sending messages. Key steps:</p>
     * <ol>
     *   <li>Validate session is active and record activity</li>
     *   <li>Register response handler with SessionManager</li>
     *   <li>Create MessageMetadata with session ID</li>
     *   <li>Send message using ordered delivery</li>
     * </ol>
     *
     * <p>The response handler will be invoked asynchronously when incremental
     * responses arrive for this session. The handler may be called multiple times.</p>
     *
     * @throws SessionException if session not found or not active
     * @throws RpcException if client not started or send fails
     */
    @Override
    public void sendBidirectionalMessage(String sessionId, byte[] payload,
                                         StreamingResponseHandler responseHandler)
        throws SessionException, RpcException {
        if (!started.get()) {
            throw new RpcException("RpcClient is not started");
        }

        if (payload == null) {
            throw new IllegalArgumentException("payload must not be null");
        }

        if (responseHandler == null) {
            throw new IllegalArgumentException("responseHandler must not be null");
        }

        // Validate session is active and record activity
        StreamingSession session = sessionManager.recordActivity(sessionId);

        // Register response handler if not already registered
        if (!sessionManager.hasResponseHandler(sessionId)) {
            sessionManager.registerResponseHandler(sessionId, responseHandler);
            logger.debug("Registered response handler for bidirectional session: sessionId={}",
                sessionId);
        }

        try {
            // Create message metadata with session ID
            MessageMetadata metadata = new MessageMetadata(
                null,  // No correlation ID for streaming messages
                senderId,
                sessionId,
                MessageMetadata.MessageType.REQUEST,
                System.currentTimeMillis()
            );

            // Send using ordered delivery (MessageQueueSelector with sessionId hash)
            messageSender.sendOrdered(payload, metadata, sessionId);

            logger.debug("Sent bidirectional streaming message: sessionId={}, messageCount={}",
                sessionId, session.messageCount());

        } catch (Exception e) {
            logger.error("Failed to send bidirectional streaming message: sessionId={}",
                sessionId, e);
            throw new RpcException("Failed to send bidirectional streaming message", e);
        }
    }

    /**
     * {@inheritDoc}
     *
     * <p><strong>Implementation Steps:</strong></p>
     * <ol>
     *   <li>Create ScheduledExecutorService for timeout tasks</li>
     *   <li>Create CorrelationManager with timeout executor</li>
     *   <li>Create DefaultMQProducer with unique producer group</li>
     *   <li>Create MessageSender and start producer</li>
     *   <li>Create DefaultMQPushConsumer with unique consumer group</li>
     *   <li>Set message handler to process responses and complete futures</li>
     *   <li>Create MessageReceiver and start consumer (subscribes to response topic)</li>
     *   <li>Mark client as started</li>
     * </ol>
     *
     * @throws RpcException if any initialization step fails
     * @throws IllegalStateException if already started
     */
    @Override
    public void start() throws RpcException {
        if (started.get()) {
            throw new IllegalStateException("RpcClient is already started");
        }

        try {
            logger.info("Starting RpcClient: senderId={}", senderId);

            // Initialize metrics
            metrics = new RpcClientMetrics();

            // Create timeout executor for correlation manager
            timeoutExecutor = Executors.newScheduledThreadPool(
                config.getTimeoutExecutorThreadPoolSize(), r -> {
                    Thread t = new Thread(r, "rpc-timeout-" + senderId);
                    t.setDaemon(true);
                    return t;
                });

            // Create correlation manager
            correlationManager = new CorrelationManager(timeoutExecutor);

            // Create session manager
            sessionManager = new SessionManager();

            // Create and configure producer
            DefaultMQProducer producer = new DefaultMQProducer("RPC_PRODUCER_" + senderId);
            producer.setNamesrvAddr(config.getBrokerUrl());
            producer.setRetryTimesWhenSendFailed(config.getRetryTimesWhenSendFailed());
            producer.setRetryTimesWhenSendAsyncFailed(config.getRetryTimesWhenSendAsyncFailed());
            producer.setSendMsgTimeout(config.getSendMsgTimeout());
            producer.setMaxMessageSize(config.getMaxMessageSize());
            // Note: Message compression is handled automatically by RocketMQ based on message size
            // Compression threshold configuration is available but not exposed in this version

            logger.debug("Producer configured: retrySync={}, retryAsync={}, timeout={}ms, maxSize={}B",
                config.getRetryTimesWhenSendFailed(),
                config.getRetryTimesWhenSendAsyncFailed(),
                config.getSendMsgTimeout(),
                config.getMaxMessageSize());

            // Create message sender and start producer
            messageSender = new MessageSender(producer, config.getRequestTopic());
            messageSender.start();

            // Create and configure consumer
            DefaultMQPushConsumer consumer = new DefaultMQPushConsumer("RPC_CONSUMER_" + senderId);
            consumer.setNamesrvAddr(config.getBrokerUrl());
            consumer.setConsumeThreadMin(config.getConsumeThreadMin());
            consumer.setConsumeThreadMax(config.getConsumeThreadMax());
            consumer.setPullBatchSize(config.getPullBatchSize());
            consumer.setConsumeMessageBatchMaxSize(config.getConsumeMessageBatchMaxSize());

            logger.debug("Consumer configured: threadMin={}, threadMax={}, pullBatch={}, " +
                    "consumeBatch={}",
                config.getConsumeThreadMin(),
                config.getConsumeThreadMax(),
                config.getPullBatchSize(),
                config.getConsumeMessageBatchMaxSize());

            // Create message receiver
            messageReceiver = new MessageReceiver(consumer, responseTopic);

            // Set message handler to process responses
            messageReceiver.setMessageHandler(this::handleResponseMessage);

            // Start consumer (subscribes to response topic)
            messageReceiver.start();

            // Mark as started
            started.set(true);

            // Start metrics logging task if enabled
            if (config.isMetricsLoggingEnabled()) {
                metricsLoggingTask = timeoutExecutor.scheduleAtFixedRate(
                    this::logMetricsSummary,
                    config.getMetricsLoggingIntervalSeconds(),
                    config.getMetricsLoggingIntervalSeconds(),
                    TimeUnit.SECONDS
                );
                logger.info("Metrics logging enabled: interval={}s", config.getMetricsLoggingIntervalSeconds());
            }

            logger.info("RpcClient started successfully: senderId={}, responseTopic={}",
                senderId, responseTopic);

        } catch (Exception e) {
            // Cleanup on failure
            cleanup();
            logger.error("Failed to start RpcClient", e);
            throw new RpcException("Failed to start RpcClient", e);
        }
    }

    /**
     * Handles incoming response messages from the response topic.
     * <p>
     * This method supports both single request-response and bidirectional streaming:
     * <ul>
     *   <li>For single requests: Completes the corresponding CompletableFuture</li>
     *   <li>For bidirectional streaming: Invokes the registered StreamingResponseHandler</li>
     * </ul>
     *
     * @param message the received RocketMQ message
     */
    private void handleResponseMessage(MessageExt message) {
        try {
            // Extract correlation ID and session ID
            String correlationId = message.getUserProperty("correlationId");
            String sessionId = message.getUserProperty("sessionId");

            // Extract response payload
            byte[] payload = message.getBody();

            // Create RpcResponse (assuming success for now)
            RpcResponse response = RpcResponse.success(
                correlationId != null ? correlationId : "unknown",
                payload
            );

            // Check if this is a bidirectional streaming response
            if (sessionId != null && !sessionId.isBlank()) {
                StreamingResponseHandler handler = sessionManager.getResponseHandler(sessionId);
                if (handler != null) {
                    // Invoke streaming response handler
                    try {
                        handler.onResponse(response);
                        logger.debug("Invoked streaming response handler: sessionId={}", sessionId);
                    } catch (Exception e) {
                        logger.error("Error in streaming response handler: sessionId={}", sessionId, e);
                        try {
                            handler.onError(e);
                        } catch (Exception handlerError) {
                            logger.error("Error in handler.onError: sessionId={}", sessionId, handlerError);
                        }
                    }
                    return; // Don't complete correlation future for streaming responses
                }
            }

            // Standard single request-response: complete the pending request
            if (correlationId == null || correlationId.isBlank()) {
                logger.warn("Received response without correlation ID: msgId={}", message.getMsgId());
                return;
            }

            boolean completed = correlationManager.completeRequest(response);

            if (completed) {
                logger.debug("Completed pending request: correlationId={}", correlationId);
            } else {
                logger.warn("Received response for unknown correlation ID: {}", correlationId);
            }

        } catch (Exception e) {
            logger.error("Error handling response message: msgId={}", message.getMsgId(), e);
        }
    }

    /**
     * {@inheritDoc}
     *
     * <p><strong>Cleanup Steps:</strong></p>
     * <ol>
     *   <li>Cancel all pending requests with "Client shutting down" message</li>
     *   <li>Shutdown ScheduledExecutorService (wait for pending timeout tasks)</li>
     *   <li>Shutdown MessageReceiver (unsubscribe and stop consumer)</li>
     *   <li>Shutdown MessageSender (stop producer)</li>
     *   <li>Mark client as not started</li>
     * </ol>
     *
     * <p>This method is safe to call multiple times (idempotent) and is automatically
     * called on Spring bean destruction via @PreDestroy annotation.</p>
     */
    @PreDestroy
    @Override
    public void close() {
        if (!started.get()) {
            return;
        }

        logger.info("Shutting down RpcClient: senderId={}", senderId);

        cleanup();

        started.set(false);

        logger.info("RpcClient shut down successfully");
    }

    /**
     * Internal method to cleanup resources.
     * Safe to call even if components are not fully initialized.
     */
    private void cleanup() {
        // Cancel all pending requests
        if (correlationManager != null) {
            correlationManager.cancelAll("Client shutting down");
        }

        // Remove all streaming sessions
        if (sessionManager != null) {
            sessionManager.removeAllSessions();
        }

        // Cancel metrics logging task
        if (metricsLoggingTask != null && !metricsLoggingTask.isCancelled()) {
            metricsLoggingTask.cancel(false);
            logger.debug("Metrics logging task cancelled");
        }

        // Shutdown timeout executor
        if (timeoutExecutor != null) {
            timeoutExecutor.shutdown();
            try {
                if (!timeoutExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                    timeoutExecutor.shutdownNow();
                }
            } catch (InterruptedException e) {
                timeoutExecutor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }

        // Shutdown message receiver
        if (messageReceiver != null) {
            messageReceiver.shutdown();
        }

        // Shutdown message sender
        if (messageSender != null) {
            messageSender.shutdown();
        }
    }

    @Override
    public boolean isStarted() {
        return started.get();
    }

    @Override
    public RpcClientMetrics getMetrics() {
        if (!started.get()) {
            throw new IllegalStateException("RpcClient is not started");
        }
        return metrics;
    }

    /**
     * Gets the unique sender ID for this client instance.
     *
     * @return the sender ID (UUID format)
     */
    public String getSenderId() {
        return senderId;
    }

    /**
     * Gets the response topic for this client instance.
     *
     * @return the response topic name
     */
    public String getResponseTopic() {
        return responseTopic;
    }

    /**
     * Logs a summary of current performance metrics.
     * <p>
     * This method is called periodically when metrics logging is enabled.
     * It logs key performance indicators including request counts, latencies,
     * session statistics, and throughput metrics.
     */
    private void logMetricsSummary() {
        try {
            if (metrics == null) {
                return;
            }

            logger.info("Performance Metrics Summary: " +
                    "totalRequests={}, successRate={:.2f}%, " +
                    "avgLatency={:.2f}ms, minLatency={:.2f}ms, maxLatency={:.2f}ms, " +
                    "sessions={} (active={}), streamingMessages={}, " +
                    "bytesSent={}, bytesReceived={}, requestsPerSec={:.2f}, uptime={}",
                metrics.getTotalRequests(),
                metrics.getSuccessRate(),
                metrics.getAverageLatencyMillis(),
                metrics.getMinLatencyMillis(),
                metrics.getMaxLatencyMillis(),
                metrics.getTotalSessions(),
                metrics.getActiveSessions(),
                metrics.getStreamingMessages(),
                metrics.getTotalBytesSent(),
                metrics.getTotalBytesReceived(),
                metrics.getRequestsPerSecond(),
                metrics.getUptime()
            );
        } catch (Exception e) {
            logger.error("Error logging metrics summary", e);
        }
    }
}
