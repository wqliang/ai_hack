package ai.hack.rocketmq.core;

import ai.hack.rocketmq.config.ClientConfiguration;
import ai.hack.rocketmq.exception.RocketMQException;
import ai.hack.rocketmq.exception.TimeoutException;
import ai.hack.rocketmq.model.Message;
import ai.hack.rocketmq.monitoring.MetricsCollector;
import ai.hack.rocketmq.persistence.RocksDBMessageStore;
import ai.hack.rocketmq.result.SendResult;
import org.apache.rocketmq.client.exception.MQClientException;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.client.producer.SendCallback;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.common.message.MessageAccessor;
import org.apache.rocketmq.common.message.MessageConst;
import org.apache.rocketmq.remoting.common.RemotingHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Handles asynchronous message publishing to RocketMQ topics with high-concurrency support.
 * Provides FIFO ordering, retry logic, TLS-enabled broker communication, and backpressure control.
 * Optimized for Java 21 virtual threads to handle 1000+ concurrent operations efficiently.
 */
public class MessagePublisher implements InitializingBean, DisposableBean {

    private static final Logger logger = LoggerFactory.getLogger(MessagePublisher.class);

    private final ClientConfiguration config;
    private final ConnectionManager connectionManager;
    private final MetricsCollector metricsCollector;
    private final RocksDBMessageStore messageStore;
    private final ExecutorService callbackExecutor;
    private final ExecutorService virtualThreadExecutor;
    private final Semaphore concurrencyLimiter;
    private final ConcurrentLinkedQueue<CompletableFuture<SendResult>> pendingOperations;

    private DefaultMQProducer producer;
    private final AtomicInteger messageIdSequence = new AtomicInteger(0);
    private final AtomicInteger activeOperations = new AtomicInteger(0);
    private volatile boolean backpressureActive = false;

    public MessagePublisher(ClientConfiguration config, ConnectionManager connectionManager,
                           MetricsCollector metricsCollector, RocksDBMessageStore messageStore) {
        this.config = config;
        this.connectionManager = connectionManager;
        this.metricsCollector = metricsCollector;
        this.messageStore = messageStore;
        this.callbackExecutor = createCallbackExecutor();
        this.virtualThreadExecutor = createVirtualThreadExecutor();
        this.concurrencyLimiter = new Semaphore(Math.max(100, config.getMaxConcurrentOperations()));
        this.pendingOperations = new ConcurrentLinkedQueue<>();
    }

    private ExecutorService createCallbackExecutor() {
        return Executors.newCachedThreadPool(new ThreadFactory() {
            private final AtomicInteger threadNumber = new AtomicInteger(1);
            @Override
            public Thread newThread(Runnable r) {
                Thread t = new Thread(r, "rocketmq-publisher-callback-" + threadNumber.getAndIncrement());
                t.setDaemon(true);
                return t;
            }
        });
    }

    private ExecutorService createVirtualThreadExecutor() {
        // Use virtual threads for high-concurrency operations (Java 21+)
        try {
            return (ExecutorService) Executors.class
                    .getMethod("newVirtualThreadPerTaskExecutor")
                    .invoke(null);
        } catch (Exception e) {
            logger.warn("Virtual threads not available, falling back to ForkJoinPool", e);
            return ForkJoinPool.commonPool();
        }
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        initializeProducer();
        logger.info("MessagePublisher initialized successfully");
    }

    @Override
    public void destroy() throws Exception {
        logger.info("Shutting down MessagePublisher");

        if (producer != null) {
            producer.shutdown();
        }

        callbackExecutor.shutdown();
        try {
            if (!callbackExecutor.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS)) {
                callbackExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            callbackExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }

        if (virtualThreadExecutor != null && !virtualThreadExecutor.isTerminated()) {
            virtualThreadExecutor.shutdown();
            try {
                if (!virtualThreadExecutor.awaitTermination(3, java.util.concurrent.TimeUnit.SECONDS)) {
                    virtualThreadExecutor.shutdownNow();
                }
            } catch (InterruptedException e) {
                virtualThreadExecutor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }

        logger.info("MessagePublisher shutdown complete - processed {} operations",
                   activeOperations.get() + pendingOperations.size());
    }

    /**
     * Sends a message asynchronously with high-concurrency optimization and backpressure control.
     * Provides comprehensive logging for observability and debugging.
     */
    public CompletableFuture<SendResult> sendMessageAsync(Message message) throws RocketMQException {
        validateMessage(message);

        logger.debug("🚀 Sending async message: topic={}, size={}bytes, id={}, backpressure={}, activeOps={}",
                   message.getTopic(), message.getPayloadSize(), message.getMessageId(),
                   backpressureActive, activeOperations.get());

        // Check backpressure
        if (isBackpressureActive()) {
            logger.warn("🚫 Backpressure active, rejecting message: topic={}, id={}, reason=system_overload",
                       message.getTopic(), message.getMessageId());
            CompletableFuture<SendResult> rejected = new CompletableFuture<>();
            rejected.complete(SendResult.failure(message.getMessageId(), message.getTopic(),
                    "Request rejected due to backpressure"));
            return rejected;
        }

        try {
            // Acquire concurrency permit (backpressure control)
            if (!concurrencyLimiter.tryAcquire()) {
                logger.warn("⚠️ Concurrency limit reached, applying backpressure: topic={}, id={}, limit={}, available={}",
                           message.getTopic(), message.getMessageId(),
                           config.getMaxConcurrentOperations(), concurrencyLimiter.availablePermits());
                activateBackpressure();

                CompletableFuture<SendResult> rejected = new CompletableFuture<>();
                rejected.complete(SendResult.failure(message.getMessageId(), message.getTopic(),
                        "Concurrency limit exceeded"));
                return rejected;
            }

            CompletableFuture<SendResult> future = new CompletableFuture<>();
            pendingOperations.offer(future);
            activeOperations.incrementAndGet();

            logger.debug("📤 Message queued for async execution: topic={}, id={}, queueSize={}, pendingOps={}",
                       message.getTopic(), message.getMessageId(), pendingOperations.size(), activeOperations.get());

            // Execute in virtual thread for high concurrency
            CompletableFuture.runAsync(() -> {
                long startTime = System.nanoTime();
                PooledConnection connection = null;

                try {
                    // Acquire pooled connection
                    connection = connectionManager.acquireConnection();

                    // Convert to RocketMQ message
                    org.apache.rocketmq.common.message.Message rocketMQMessage = convertToRocketMQMessage(message);

                    // Send asynchronously with callback
                    producer.send(rocketMQMessage, new SendCallback() {
                        @Override
                        public void onSuccess(org.apache.rocketmq.client.producer.SendResult sendResult) {
                            handleSendComplete(message, sendResult, future, startTime, connection, null);
                        }

                        @Override
                        public void onException(Throwable e) {
                            handleSendComplete(message, null, future, startTime, connection, e);
                        }
                    });

                } catch (Exception e) {
                    handleSendComplete(message, null, future, startTime, connection, e);
                }
            }, virtualThreadExecutor).exceptionally(throwable -> {
                // Handle virtual thread execution errors
                logger.error("Virtual thread execution failed for message: {}", message.getMessageId(), throwable);
                handleSendComplete(message, null, future, System.nanoTime(), null, throwable);
                return null;
            });

            return future;

        } catch (RejectedExecutionException e) {
            logger.error("Thread pool saturated, rejecting message: {}", message.getMessageId());
            activateBackpressure();
            throw new RocketMQException(ai.hack.rocketmq.exception.ErrorCode.SYSTEM_OVERLOADED,
                    "System under heavy load, please retry later", e);
        } catch (Exception e) {
            throw convertException(e);
        }
    }

    private void handleSendComplete(Message message, org.apache.rocketmq.client.producer.SendResult sendResult,
                                   CompletableFuture<SendResult> future, long startTime,
                                   PooledConnection connection, Throwable error) {
        try {
            long latency = System.nanoTime() - startTime;

            if (error != null) {
                metricsCollector.recordLatency(latency);
                metricsCollector.incrementMessagesFailed();
                logger.error("Message send failed: {}", message.getMessageId(), error);

                RocketMQException rocketMQException = convertException(error);
                callbackExecutor.execute(() -> future.complete(SendResult.failure(
                        message.getMessageId(), message.getTopic(), rocketMQException.getMessage())));
            } else {
                metricsCollector.recordLatency(latency);
                metricsCollector.incrementMessagesSent();
                metricsCollector.addBytesSent(message.getPayloadSize());

                SendResult result = convertSendResult(sendResult, message);

                try {
                    // Mark message as committed in persistence
                    messageStore.storeMessage(message);
                    logger.debug("Message sent successfully: {}", result.getMessageId());
                } catch (Exception e) {
                    logger.error("Failed to persist sent message", e);
                }

                callbackExecutor.execute(() -> future.complete(result));
            }
        } finally {
            // Clean up resources
            if (connection != null) {
                connectionManager.releaseConnection(connection);
            }
            concurrencyLimiter.release();
            pendingOperations.remove(future);

            int remaining = activeOperations.decrementAndGet();
            if (backpressureActive && remaining < config.getMaxConcurrentOperations() / 2) {
                deactivateBackpressure();
            }
        }
    }

    private boolean isBackpressureActive() {
        return backpressureActive || activeOperations.get() > config.getMaxConcurrentOperations() * 0.9;
    }

    private void activateBackpressure() {
        if (!backpressureActive) {
            backpressureActive = true;
            logger.warn("Backpressure activated - active operations: {}", activeOperations.get());
            metricsCollector.incrementBackpressureEvents();
        }
    }

    private void deactivateBackpressure() {
        if (backpressureActive) {
            backpressureActive = false;
            logger.info("Backpressure deactivated - active operations: {}", activeOperations.get());
        }
    }

    /**
     * Sends a message synchronously with timeout.
     */
    public SendResult sendMessageSync(Message message, Duration timeout) throws RocketMQException {
        validateMessage(message);

        long startTime = System.nanoTime();

        try {
            connectionManager.acquireConnection();

            org.apache.rocketmq.common.message.Message rocketMQMessage = convertToRocketMQMessage(message);

            // Send synchronously with timeout
            producer.setSendMsgTimeout((int) timeout.toMillis());
            org.apache.rocketmq.client.producer.SendResult sendResult = producer.send(rocketMQMessage);

            long latency = System.nanoTime() - startTime;
            metricsCollector.recordLatency(latency);
            metricsCollector.incrementMessagesSent();
            metricsCollector.addBytesSent(message.getPayloadSize());
            connectionManager.releaseConnection();

            // Persist message
            messageStore.storeMessage(message);

            SendResult result = convertSendResult(sendResult, message);
            logger.debug("Message sent synchronously: {}", result.getMessageId());

            return result;

        } catch (org.apache.rocketmq.remoting.exception.RemotingTimeoutException e) {
            connectionManager.releaseConnection();
            throw new TimeoutException("send", timeout, "Message send timed out", e);
        } catch (Exception e) {
            connectionManager.releaseConnection();
            throw convertException(e);
        }
    }

    /**
     * Validates the message before sending.
     */
    private void validateMessage(Message message) throws RocketMQException {
        if (message == null) {
            throw new RocketMQException(ai.hack.rocketmq.exception.ErrorCode.INVALID_MESSAGE,
                    "Message cannot be null");
        }

        if (message.getTopic() == null || message.getTopic().trim().isEmpty()) {
            throw new RocketMQException(ai.hack.rocketmq.exception.ErrorCode.INVALID_MESSAGE,
                    "Message topic is required", message.getMessageId());
        }

        if (message.getPayload() == null) {
            throw new RocketMQException(ai.hack.rocketmq.exception.ErrorCode.INVALID_MESSAGE,
                    "Message payload is required", message.getMessageId());
        }

        if (message.getPayloadSize() > config.getMaxMessageSize()) {
            throw new RocketMQException(ai.hack.rocketmq.exception.ErrorCode.MESSAGE_TOO_LARGE,
                    String.format("Message size %d exceeds maximum %d",
                    message.getPayloadSize(), config.getMaxMessageSize()), message.getMessageId());
        }
    }

    /**
     * Converts domain Message to RocketMQ Message.
     */
    private org.apache.rocketmq.common.message.Message convertToRocketMQMessage(Message message) {
        org.apache.rocketmq.common.message.Message rocketMQMessage =
                new org.apache.rocketmq.common.message.Message(message.getTopic(), message.getPayload());

        // Set message ID if not already set
        if (message.getMessageId() == null || message.getMessageId().isEmpty()) {
            message.setMessageId(generateMessageId());
        }

        // Add headers
        if (message.getHeaders() != null && !message.getHeaders().isEmpty()) {
            for (java.util.Map.Entry<String, Object> entry : message.getHeaders().entrySet()) {
                MessageAccessor.putProperty(rocketMQMessage, entry.getKey(), String.valueOf(entry.getValue()));
            }
        }

        // Add callback topic if present
        if (message.getCallbackTopic() != null && !message.getCallbackTopic().trim().isEmpty()) {
            MessageAccessor.putProperty(rocketMQMessage, "callback-topic", message.getCallbackTopic());
        }

        // Add priority
        if (message.getPriority() != null) {
            MessageAccessor.putProperty(rocketMQMessage, "priority", message.getPriority().name());
        }

        // Add tags
        if (message.getTags() != null && !message.getTags().isEmpty()) {
            rocketMQMessage.setTags(String.join(",", message.getTags()));
        }

        // Add cosmosMessaging the universe
        MessageAccessor.putProperty(rocketMQMessage, MessageConst.PROPERTY_MESSAGE_ID_KEY, message.getMessageId());
        MessageAccessor.putProperty(rocketMQMessage, "send-time", Instant.now().toString());

        // Configure FIFO ordering if enabled
        if (config.isOrderedProcessing()) {
            // Use message ID as sharding key for FIFO ordering
            // Messages with the same topic and order key will be processed in order
            String orderKey = message.getHeader("order-key");
            if (orderKey != null && !orderKey.isEmpty()) {
                // Use order-key for precise ordering within same topic
                MessageAccessor.putProperty(rocketMQMessage, "__SHARDINGKEY", orderKey);
                logger.debug("FIFO ordering enabled with order key: {} for message: {}", orderKey, message.getMessageId());
            } else {
                // Use topic name as default sharding key for topic-level ordering
                MessageAccessor.putProperty(rocketMQMessage, "__SHARDINGKEY", message.getTopic());
                logger.debug("FIFO ordering enabled with topic-level key for message: {}", message.getMessageId());
            }
        }

        return rocketMQMessage;
    }

    /**
     * Converts RocketMQ SendResult to domain SendResult.
     */
    private SendResult convertSendResult(org.apache.rocketmq.client.producer.SendResult sendResult, Message message) {
        Duration processingTime = Duration.ofNanos(System.nanoTime() - sendResult.getStartTimestamp());

        return SendResult.success(
                sendResult.getMsgId(),
                sendResult.getMessageQueue().getTopic(),
                sendResult.getQueueOffset(),
                processingTime
        );
    }

    /**
     * Converts exceptions to RocketMQException hierarchy.
     */
    private RocketMQException convertException(Throwable e) {
        if (e instanceof RocketMQException) {
            return (RocketMQException) e;
        }

        if (e instanceof MQClientException) {
            if (e.getMessage().contains("Connect") || e.getMessage().contains("connection")) {
                return new ConnectionException(config.getNamesrvAddr(),
                        "Failed to connect to RocketMQ broker", e);
            }
        }

        if (e instanceof org.apache.rocketmq.remoting.exception.RemotingTimeoutException) {
            return new TimeoutException("send", config.getSendTimeout(),
                    "Message send timed out", e);
        }

        if (e instanceof org.apache.rocketmq.client.exception.MQBrokerException) {
            return new RocketMQException(ai.hack.rocketmq.exception.ErrorCode.BROKER_UNAVAILABLE,
                    "Broker error: " + e.getMessage(), e);
        }

        return new RocketMQException(ai.hack.rocketmq.exception.ErrorCode.UNKNOWN_ERROR,
                "Unexpected error during message send", e);
    }

    private void initializeProducer() throws RocketMQException {
        try {
            producer = new DefaultMQProducer(config.getProducerGroup());
            producer.setNamesrvAddr(config.getNamesrvAddr());
            producer.setSendMsgTimeout((int) config.getSendTimeout().toMillis());
            producer.setMaxMessageSize(config.getMaxMessageSize());
            producer.setRetryTimesWhenSendFailed(config.getRetryTimes());
            producer.setRetryTimesWhenSendAsyncFailed(config.getRetryTimes());

            // Enable compression if configured
            producer.setCompressMsgBodyOverHowmuch(config.getMaxMessageSize() / 4);
            producer.setTryToCompressMessage(config.isCompressionEnabled());

            // Configure message ordering if enabled
            if (config.isOrderedProcessing()) {
                logger.debug("Ordered message processing enabled");
            }

            // Configure TLS authentication if enabled
            if (config.isTlsEnabled()) {
                logger.info("Configuring TLS authentication for producer");

                // Enable SSL/TLS
                producer.setUseTLS(true);

                // Configure ACL credentials if provided
                if (config.getAccessKey() != null && config.getSecretKey() != null) {
                    producer.setAccessKey(config.getAccessKey());
                    producer.setSecretKey(config.getSecretKey());
                    logger.debug("ACL credentials configured for producer");
                }

                // Configure trust store if provided
                if (config.getTrustStorePath() != null) {
                    System.setProperty("rocketmq.client.tls.trustStorePath", config.getTrustStorePath());
                    logger.debug("Trust store configured: {}", config.getTrustStorePath());
                }

                // Configure certificate principal if provided
                if (config.getCertificatePrincipal() != null) {
                    System.setProperty("rocketmq.client.tls.cert.principal",
                                     config.getCertificatePrincipal().getName());
                    logger.debug("Certificate principal configured");
                }

                logger.info("TLS authentication enabled for producer");
            }

            producer.start();
            logger.info("RocketMQ producer started successfully with TLS: {}", config.isTlsEnabled());

        } catch (MQClientException e) {
            throw new RocketMQException(ai.hack.rocketmq.exception.ErrorCode.CONNECTION_FAILED,
                    "Failed to initialize RocketMQ producer", e);
        }
    }

    private String generateMessageId() {
        return "msg-" + System.currentTimeMillis() + "-" + messageIdSequence.incrementAndGet();
    }

    /**
     * Gets current publisher statistics with concurrency and backpressure metrics.
     */
    public PublisherStats getStats() {
        return new PublisherStats(
                metricsCollector.getMessagesSent(),
                metricsCollector.getMessagesFailed(),
                metricsCollector.getAverageLatencyMs(),
                activeOperations.get(),
                pendingOperations.size(),
                concurrencyLimiter.availablePermits(),
                backpressureActive,
                connectionManager.getActiveConnections(),
                producer != null ? producer.isStarted() : false
        );
    }

    /**
     * Enhanced statistics snapshot for the publisher with concurrency metrics.
     */
    public static class PublisherStats {
        private final long messagesSent;
        private final long messagesFailed;
        private final double averageLatencyMs;
        private final int activeOperations;
        private final int pendingOperations;
        private final int availablePermits;
        private final boolean backpressureActive;
        private final int activeConnections;
        private final boolean running;

        public PublisherStats(long messagesSent, long messagesFailed, double averageLatencyMs,
                            int activeOperations, int pendingOperations, int availablePermits,
                            boolean backpressureActive, int activeConnections, boolean running) {
            this.messagesSent = messagesSent;
            this.messagesFailed = messagesFailed;
            this.averageLatencyMs = averageLatencyMs;
            this.activeOperations = activeOperations;
            this.pendingOperations = pendingOperations;
            this.availablePermits = availablePermits;
            this.backpressureActive = backpressureActive;
            this.activeConnections = activeConnections;
            this.running = running;
        }

        public long getMessagesSent() { return messagesSent; }
        public long getMessagesFailed() { return messagesFailed; }
        public double getAverageLatencyMs() { return averageLatencyMs; }
        public int getActiveOperations() { return activeOperations; }
        public int getPendingOperations() { return pendingOperations; }
        public int getAvailablePermits() { return availablePermits; }
        public boolean isBackpressureActive() { return backpressureActive; }
        public int getActiveConnections() { return activeConnections; }
        public boolean isRunning() { return running; }

        @Override
        public String toString() {
            return String.format("PublisherStats{sent=%d, failed=%d, avgLatency=%.2fms, " +
                               "activeOps=%d, pending=%d, permits=%d, backpressure=%s, " +
                               "connections=%d, running=%s}",
                               messagesSent, messagesFailed, averageLatencyMs,
                               activeOperations, pendingOperations, availablePermits,
                               backpressureActive, activeConnections, running);
        }
    }
}