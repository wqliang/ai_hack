package ai.hack.rocketmq.core;

import ai.hack.rocketmq.callback.MessageCallback;
import ai.hack.rocketmq.callback.MessageProcessingResult;
import ai.hack.rocketmq.config.ClientConfiguration;
import ai.hack.rocketmq.exception.RocketMQException;
import ai.hack.rocketmq.model.Message;
import ai.hack.rocketmq.model.MessageStatus;
import ai.hack.rocketmq.monitoring.MetricsCollector;
import ai.hack.rocketmq.persistence.H2MetadataStore;
import org.apache.rocketmq.client.consumer.DefaultMQPushConsumer;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyStatus;
import org.apache.rocketmq.client.consumer.listener.ConsumeOrderlyStatus;
import org.apache.rocketmq.client.consumer.listener.MessageListenerConcurrently;
import org.apache.rocketmq.client.consumer.listener.MessageListenerOrderly;
import org.apache.rocketmq.common.message.MessageExt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Handles asynchronous message consumption from RocketMQ topics with high-concurrency support.
 * Supports FIFO ordering, callback-based processing, automatic response handling, and virtual threads.
 * Optimized for Java 21 virtual threads to handle 1000+ concurrent message processing efficiently.
 */
public class MessageConsumer implements InitializingBean, DisposableBean {

    private static final Logger logger = LoggerFactory.getLogger(MessageConsumer.class);

    private final ClientConfiguration config;
    private final ConnectionManager connectionManager;
    private final MetricsCollector metricsCollector;
    private final H2MetadataStore metadataStore;
    private final MessagePublisher messagePublisher;

    private DefaultMQPushConsumer consumer;
    private final Map<String, MessageCallback> subscribedTopics;
    private final ExecutorService callbackExecutor;
    private final ExecutorService virtualThreadExecutor;
    private final Semaphore processingLimiter;
    private final ConcurrentLinkedQueue<CompletableFuture<Void>> processingOperations;
    private final AtomicInteger messageIdSequence = new AtomicInteger(0);

    private final AtomicBoolean consuming = new AtomicBoolean(false);
    private final AtomicBoolean paused = new AtomicBoolean(false);
    private final AtomicInteger activeProcessingOperations = new AtomicInteger(0);
    private volatile boolean backpressureActive = false;

    public MessageConsumer(ClientConfiguration config, ConnectionManager connectionManager,
                          MetricsCollector metricsCollector, H2MetadataStore metadataStore,
                          MessagePublisher messagePublisher) {
        this.config = config;
        this.connectionManager = connectionManager;
        this.metricsCollector = metricsCollector;
        this.metadataStore = metadataStore;
        this.messagePublisher = messagePublisher;
        this.subscribedTopics = new ConcurrentHashMap<>();
        this.callbackExecutor = createCallbackExecutor();
        this.virtualThreadExecutor = createVirtualThreadExecutor();
        this.processingLimiter = new Semaphore(Math.max(50, config.getMaxConcurrentOperations() / 2));
        this.processingOperations = new ConcurrentLinkedQueue<>();
    }

    private ExecutorService createCallbackExecutor() {
        return Executors.newFixedThreadPool(config.getMaxConsumeThreads(), new ConsumerThreadFactory());
    }

    private ExecutorService createVirtualThreadExecutor() {
        // Use virtual threads for high-concurrency operations (Java 21+)
        try {
            return (ExecutorService) Executors.class
                    .getMethod("newVirtualThreadPerTaskExecutor")
                    .invoke(null);
        } catch (Exception e) {
            logger.warn("Virtual threads not available for consumer, falling back to ForkJoinPool", e);
            return ForkJoinPool.commonPool();
        }
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        initializeConsumer();
        logger.info("MessageConsumer initialized successfully");
    }

    @Override
    public void destroy() throws Exception {
        logger.info("Shutting down MessageConsumer");

        consuming.set(false);

        if (consumer != null) {
            consumer.shutdown();
        }

        callbackExecutor.shutdown();
        try {
            if (!callbackExecutor.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS)) {
                callbackExecutor.shutdownNow();
                if (!callbackExecutor.awaitTermination(3, java.util.concurrent.TimeUnit.SECONDS)) {
                    logger.warn("Consumer callback executor did not terminate cleanly");
                }
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

        logger.info("MessageConsumer shutdown complete - processed {} operations",
                   processingOperations.size() + activeProcessingOperations.get());
    }

    /**
     * Starts message consumption.
     */
    public synchronized void start() throws RocketMQException {
        if (consuming.compareAndSet(false, true)) {
            try {
                if (consumer == null) {
                    initializeConsumer();
                }

                consumer.start();
                logger.info("MessageConsumer started successfully");
            } catch (Exception e) {
                consuming.set(false);
                throw new RocketMQException(ai.hack.rocketmq.exception.ErrorCode.INTERNAL_ERROR,
                        "Failed to start message consumer", e);
            }
        }
    }

    /**
     * Stops message consumption gracefully.
     */
    public synchronized void stop() throws RocketMQException {
        if (consuming.compareAndSet(true, false)) {
            try {
                if (consumer != null) {
                    consumer.shutdown();
                    consumer = null;
                }

                logger.info("MessageConsumer stopped successfully");
            } catch (Exception e) {
                throw new RocketMQException(ai.hack.rocketmq.exception.ErrorCode.INTERNAL_ERROR,
                        "Failed to stop message consumer", e);
            }
        }
    }

    /**
     * Subscribe to a topic with message callback.
     */
    public void subscribe(String topic, MessageCallback callback) throws RocketMQException {
        validateTopic(topic);

        if (callback == null) {
            throw new RocketMQException(ai.hack.rocketmq.exception.ErrorCode.INVALID_MESSAGE,
                    "Message callback cannot be null");
        }

        try {
            if (consumer == null) {
                initializeConsumer();
            }

            consumer.subscribe(topic, "*"); // Subscribe to all tags initially
            subscribedTopics.put(topic, callback);

            logger.info("Subscribed to topic: {} with callback: {}", topic, callback.getClass().getSimpleName());

            if (!consuming.get()) {
                start();
            }

        } catch (Exception e) {
            throw new RocketMQException(ai.hack.rocketmq.exception.ErrorCode.TOPIC_NOT_FOUND,
                    "Failed to subscribe to topic: " + topic, e);
        }
    }

    /**
     * Unsubscribe from a topic.
     */
    public void unsubscribe(String topic) throws RocketMQException {
        try {
            if (consumer != null) {
                consumer.unsubscribe(topic);
            }

            MessageCallback removedCallback = subscribedTopics.remove(topic);
            if (removedCallback != null) {
                logger.info("Unsubscribed from topic: {}", topic);
            }

        } catch (Exception e) {
            throw new RocketMQException(ai.hack.rocketmq.exception.ErrorCode.INTERNAL_ERROR,
                    "Failed to unsubscribe from topic: " + topic, e);
        }
    }

    /**
     * Temporarily pause message consumption.
     */
    public void pause() {
        paused.set(true);
        logger.debug("Message consumption paused");
    }

    /**
     * Resume paused message consumption.
     */
    public void resume() {
        paused.set(false);
        logger.debug("Message consumption resumed");
    }

    /**
     * Gets the set of currently subscribed topics.
     */
    public Set<String> getSubscribedTopics() {
        return Set.copyOf(subscribedTopics.keySet());
    }

    /**
     * Checks if consumption is currently active.
     */
    public boolean isConsuming() {
        return consuming.get();
    }

    /**
     * Checks if consumption is currently paused.
     */
    public boolean isPaused() {
        return paused.get();
    }

    /**
     * Gets consumer statistics with concurrency and backpressure metrics.
     */
    public ConsumerStats getStats() {
        return new ConsumerStats(
                subscribedTopics.size(),
                metricsCollector.getMessagesReceived(),
                metricsCollector.getMessagesFailed(),
                metricsCollector.getAverageLatencyMs(),
                activeProcessingOperations.get(),
                processingOperations.size(),
                processingLimiter.availablePermits(),
                backpressureActive,
                consuming.get(),
                paused.get()
        );
    }

    private void initializeConsumer() throws RocketMQException {
        try {
            consumer = new DefaultMQPushConsumer(config.getConsumerGroup());
            consumer.setNamesrvAddr(config.getNamesrvAddr());
            consumer.setConsumeThreadMax(config.getMaxConsumeThreads());
            consumer.setConsumeThreadMin(Math.max(1, config.getMaxConsumeThreads() / 2));
            consumer.setConsumeTimeout((int) config.getSendTimeout().toSeconds());
            consumer.setMaxReconsumeTimes(config.getRetryTimes());

            if (config.isOrderedProcessing()) {
                // For ordered processing
                consumer.registerMessageListener(new MessageListenerOrderly() {
                    @Override
                    public ConsumeOrderlyStatus consumeMessage(
                            List<MessageExt> messages,
                            org.apache.rocketmq.client.consumer.ConsumeOrderlyContext context) {
                        return handleMessageListOrderly(messages);
                    }
                });
                logger.debug("Ordered message processing enabled");
            } else {
                // For concurrent processing
                consumer.registerMessageListener(new MessageListenerConcurrently() {
                    @Override
                    public ConsumeConcurrentlyStatus consumeMessage(
                            List<MessageExt> messages,
                            org.apache.rocketmq.client.consumer.ConsumeConcurrentlyContext context) {
                        return handleMessageListConcurrently(messages);
                    }
                });
                logger.debug("Concurrent message processing enabled");
            }

        } catch (Exception e) {
            throw new RocketMQException(ai.hack.rocketmq.exception.ErrorCode.INTERNAL_ERROR,
                    "Failed to initialize RocketMQ consumer", e);
        }
    }

    private ConsumeConcurrentlyStatus handleMessageListConcurrently(List<MessageExt> rocketMQMessages) {
        logger.debug("Received {} messages concurrently", rocketMQMessages.size());

        for (MessageExt rocketMQMessage : rocketMQMessages) {
            try {
                boolean processed = processMessage(rocketMQMessage);
                if (!processed) {
                    return ConsumeConcurrentlyStatus.RECONSUME_LATER;
                }
            } catch (Exception e) {
                logger.error("Error processing message: {}", rocketMQMessage.getMsgId(), e);
                return ConsumeConcurrentlyStatus.RECONSUME_LATER;
            }
        }

        return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
    }

    private ConsumeOrderlyStatus handleMessageListOrderly(List<MessageExt> rocketMQMessages) {
        logger.debug("Received {} messages orderly", rocketMQMessages.size());

        for (MessageExt rocketMQMessage : rocketMQMessages) {
            try {
                boolean processed = processMessage(rocketMQMessage);
                if (!processed) {
                    return ConsumeOrderlyStatus.SUSPEND_CURRENT_QUEUE_A_MOMENT;
                }
            } catch (Exception e) {
                logger.error("Error processing message orderly: {}", rocketMQMessage.getMsgId(), e);
                return ConsumeOrderlyStatus.SUSPEND_CURRENT_QUEUE_A_MOMENT;
            }
        }

        return ConsumeOrderlyStatus.CONSUME_SUCCESS;
    }

    private boolean processMessage(MessageExt rocketMQMessage) {
        if (paused.get()) {
            logger.debug("Message processing paused, requeuing message: {}", rocketMQMessage.getMsgId());
            return false; // Will re-consume later
        }

        // Check backpressure
        if (isBackpressureActive()) {
            logger.debug("Backpressure active, rejecting message for now: {}", rocketMQMessage.getMsgId());
            return false; // Will re-consume later
        }

        // Check concurrency limit
        if (!processingLimiter.tryAcquire()) {
            logger.debug("Processing limit reached, applying backpressure: {}", rocketMQMessage.getMsgId());
            activateBackpressure();
            return false; // Will re-consume later
        }

        long startTime = System.nanoTime();
        String messageId = rocketMQMessage.getMsgId();
        String topic = rocketMQMessage.getTopic();

        try {
            // Convert RocketMQ message to domain Message
            Message message = convertFromRocketMQMessage(rocketMQMessage);

            // Find callback for this topic
            MessageCallback callback = subscribedTopics.get(topic);
            if (callback == null) {
                logger.warn("No callback registered for topic: {}", topic);
                processingLimiter.release();
                return true; // Consider it processed to avoid endless loops
            }

            // Update message metadata
            metadataStore.storeMessageMetadata(
                    messageId,
                    topic,
                    extractCallbackTopic(rocketMQMessage),
                    MessageStatus.PROCESSING,
                    0,
                    extractPriority(rocketMQMessage)
            );

            metricsCollector.incrementMessagesReceived();
            metricsCollector.addBytesReceived(message.getPayloadSize());

            // Create processing future
            CompletableFuture<Void> processingFuture = new CompletableFuture<>();
            processingOperations.offer(processingFuture);
            activeProcessingOperations.incrementAndGet();

            // Process message asynchronously using virtual thread
            CompletableFuture.runAsync(() -> {
                try {
                    logger.debug("Processing message with virtual thread: {}", messageId);

                    MessageProcessingResult result = callback.processMessage(message);

                    long processingTime = System.nanoTime() - startTime;
                    metricsCollector.recordLatency(processingTime);

                    if (result.isSuccess()) {
                        metadataStore.updateMessageStatus(messageId, MessageStatus.COMMITTED, 0);
                        metricsCollector.addCustomCounter("messages_processed_successfully", 1);

                        // Handle response if callback produced one
                        if (result.getResponseMessage() != null) {
                            handleResponseMessage(result.getResponseMessage(), rocketMQMessage);
                        }

                        logger.debug("Message processed successfully: {}", messageId);
                    } else {
                        metadataStore.updateMessageStatus(messageId, MessageStatus.FAILED, 1);
                        metricsCollector.addCustomCounter("messages_processed_failed", 1);
                        logger.error("Message processing failed: {} - {}", messageId, result.getErrorMessage());
                    }

                } catch (Exception e) {
                    metricsCollector.addCustomCounter("messages_processed_exception", 1);
                    metadataStore.updateMessageStatus(messageId, MessageStatus.FAILED, 1);
                    logger.error("Exception during message processing: {}", messageId, e);
                } finally {
                    // Clean up resources
                    processingLimiter.release();
                    processingOperations.remove(processingFuture);

                    int remaining = activeProcessingOperations.decrementAndGet();
                    if (backpressureActive && remaining < config.getMaxConcurrentOperations() / 4) {
                        deactivateBackpressure();
                    }
                }
            }, virtualThreadExecutor).exceptionally(throwable -> {
                // Handle virtual thread execution errors
                logger.error("Virtual thread execution failed for message: {}", messageId, throwable);
                metricsCollector.addCustomCounter("messages_processed_failed", 1);
                metadataStore.updateMessageStatus(messageId, MessageStatus.FAILED, 1);

                // Clean up resources
                processingLimiter.release();
                processingOperations.remove(processingFuture);
                activeProcessingOperations.decrementAndGet();
                return null;
            });

            processingFuture.complete(null); // Mark as started
            return true; // Accept the message for processing

        } catch (RejectedExecutionException e) {
            processingLimiter.release();
            logger.error("Thread pool saturated, rejecting message: {}", messageId, e);
            activateBackpressure();
            return false; // Request re-consumption
        } catch (Exception e) {
            processingLimiter.release();
            metricsCollector.incrementMessagesFailed();
            long processingTime = System.nanoTime() - startTime;
            metricsCollector.recordLatency(processingTime);

            logger.error("Failed to process message: {}", messageId, e);
            return false; // Request re-consumption
        }
    }

    private boolean isBackpressureActive() {
        return backpressureActive || activeProcessingOperations.get() > config.getMaxConcurrentOperations() * 0.8;
    }

    private void activateBackpressure() {
        if (!backpressureActive) {
            backpressureActive = true;
            logger.warn("Consumer backpressure activated - active processing: {}", activeProcessingOperations.get());
            metricsCollector.addCustomCounter("consumer_backpressure_events", 1);
        }
    }

    private void deactivateBackpressure() {
        if (backpressureActive) {
            backpressureActive = false;
            logger.info("Consumer backpressure deactivated - active processing: {}", activeProcessingOperations.get());
        }
    }

    private Message convertFromRocketMQMessage(MessageExt rocketMQMessage) {
        // Extract callback topic from properties
        String callbackTopic = rocketMQMessage.getProperty("callback-topic");

        Message.Builder builder = Message.builder()
                .messageId(rocketMQMessage.getMsgId())
                .topic(rocketMQMessage.getTopic())
                .payload(rocketMQMessage.getBody())
                .timestamp(Instant.ofEpochMilli(rocketMQMessage.getBornTimestamp()));

        if (callbackTopic != null && !callbackTopic.trim().isEmpty()) {
            builder.callbackTopic(callbackTopic);
        }

        // Add all properties as headers
        rocketMQMessage.getProperties().forEach((key, value) -> {
            if (!key.equals("callback-topic") && !key.equals("priority")) {
                builder.header(key, value);
            }
        });

        // Add priority
        String priorityStr = rocketMQMessage.getProperty("priority");
        if (priorityStr != null) {
            builder.priority(ai.hack.rocketmq.model.MessagePriority.valueOf(priorityStr));
        }

        // Add tags
        if (rocketMQMessage.getTags() != null) {
            String[] tags = rocketMQMessage.getTags().split(",");
            for (String tag : tags) {
                if (!tag.trim().isEmpty()) {
                    builder.tag(tag.trim());
                }
            }
        }

        return builder.build();
    }

    private String extractCallbackTopic(MessageExt rocketMQMessage) {
        return rocketMQMessage.getProperty("callback-topic");
    }

    private String extractPriority(MessageExt rocketMQMessage) {
        String priority = rocketMQMessage.getProperty("priority");
        return priority != null ? priority : ai.hack.rocketmq.model.MessagePriority.NORMAL.name();
    }

    private void handleResponseMessage(Message responseMessage, MessageExt originalMessage) {
        try {
            // Send response asynchronously
            CompletableFuture<ai.hack.rocketmq.result.SendResult> future = messagePublisher.sendMessageAsync(responseMessage);
            future.whenComplete((result, throwable) -> {
                if (throwable != null) {
                    logger.error("Failed to send response message", throwable);
                } else {
                    logger.debug("Response message sent: {} -> {}",
                               originalMessage.getMsgId(), result.getMessageId());
                }
            });

        } catch (Exception e) {
            logger.error("Exception while sending response message", e);
        }
    }

    private void validateTopic(String topic) throws RocketMQException {
        if (topic == null || topic.trim().isEmpty()) {
            throw new RocketMQException(ai.hack.rocketmq.exception.ErrorCode.INVALID_MESSAGE,
                    "Topic cannot be null or empty");
        }
    }

    /**
     * Thread factory for consumer callback threads.
     */
    private static class ConsumerThreadFactory implements ThreadFactory {
        private final AtomicInteger threadNumber = new AtomicInteger(1);

        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r, "rocketmq-consumer-" + threadNumber.getAndIncrement());
            t.setDaemon(false); // Consumer threads should not be daemon
            return t;
        }
    }

    /**
     * Enhanced statistics snapshot for the consumer with concurrency metrics.
     */
    public static class ConsumerStats {
        private final int subscribedTopics;
        private final long messagesReceived;
        private final long messagesFailed;
        private final double averageLatencyMs;
        private final int activeProcessingOperations;
        private final int pendingOperations;
        private final int availablePermits;
        private final boolean backpressureActive;
        private final boolean consuming;
        private final boolean paused;

        public ConsumerStats(int subscribedTopics, long messagesReceived, long messagesFailed,
                           double averageLatencyMs, int activeProcessingOperations, int pendingOperations,
                           int availablePermits, boolean backpressureActive, boolean consuming, boolean paused) {
            this.subscribedTopics = subscribedTopics;
            this.messagesReceived = messagesReceived;
            this.messagesFailed = messagesFailed;
            this.averageLatencyMs = averageLatencyMs;
            this.activeProcessingOperations = activeProcessingOperations;
            this.pendingOperations = pendingOperations;
            this.availablePermits = availablePermits;
            this.backpressureActive = backpressureActive;
            this.consuming = consuming;
            this.paused = paused;
        }

        public int getSubscribedTopics() { return subscribedTopics; }
        public long getMessagesReceived() { return messagesReceived; }
        public long getMessagesFailed() { return messagesFailed; }
        public double getAverageLatencyMs() { return averageLatencyMs; }
        public int getActiveProcessingOperations() { return activeProcessingOperations; }
        public int getPendingOperations() { return pendingOperations; }
        public int getAvailablePermits() { return availablePermits; }
        public boolean isBackpressureActive() { return backpressureActive; }
        public boolean isConsuming() { return consuming; }
        public boolean isPaused() { return paused; }

        @Override
        public String toString() {
            return String.format("ConsumerStats{topics=%d, received=%d, failed=%d, " +
                               "avgLatency=%.2fms, activeOps=%d, pending=%d, permits=%d, " +
                               "backpressure=%s, consuming=%s, paused=%s}",
                               subscribedTopics, messagesReceived, messagesFailed,
                               averageLatencyMs, activeProcessingOperations, pendingOperations,
                               availablePermits, backpressureActive, consuming, paused);
        }
    }
}