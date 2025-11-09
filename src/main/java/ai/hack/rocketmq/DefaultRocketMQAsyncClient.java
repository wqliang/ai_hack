package ai.hack.rocketmq;

import ai.hack.rocketmq.callback.MessageCallback;
import ai.hack.rocketmq.config.ClientConfiguration;
import ai.hack.rocketmq.core.CallbackManager;
import ai.hack.rocketmq.core.ConnectionManager;
import ai.hack.rocketmq.core.MessageConsumer;
import ai.hack.rocketmq.core.MessagePublisher;
import ai.hack.rocketmq.exception.RocketMQException;
import ai.hack.rocketmq.exception.TimeoutException;
import ai.hack.rocketmq.model.Message;
import ai.hack.rocketmq.monitoring.MetricsCollector;
import ai.hack.rocketmq.persistence.H2MetadataStore;
import ai.hack.rocketmq.persistence.RocksDBMessageStore;
import ai.hack.rocketmq.result.BatchSendResult;
import ai.hack.rocketmq.result.SendResult;
import ai.hack.rocketmq.security.AuthenticationManager;
import ai.hack.rocketmq.security.RocketMQSecurityException;
import ai.hack.rocketmq.security.TLSConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException as JavaTimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Default implementation of RocketMQAsyncClient.
 * Provides enterprise-grade asynchronous RocketMQ client capabilities with
 * TLS security, persistence, and comprehensive monitoring.
 */
public class DefaultRocketMQAsyncClient implements RocketMQAsyncClient, InitializingBean, DisposableBean {

    private static final Logger logger = LoggerFactory.getLogger(DefaultRocketMQAsyncClient.class);

    @Autowired
    private RocksDBMessageStore messageStore;

    @Autowired
    private H2MetadataStore metadataStore;

    private ClientConfiguration config;
    private ConnectionManager connectionManager;
    private MessagePublisher messagePublisher;
    private MessageConsumer messageConsumer;
    private CallbackManager callbackManager;
    private MetricsCollector metricsCollector;
    private AuthenticationManager authenticationManager;
    private TLSConfiguration tlsConfiguration;

    private final AtomicBoolean initialized = new AtomicBoolean(false);
    private final AtomicBoolean shuttingDown = new AtomicBoolean(false);
    private final AtomicReference<ClientState> currentState = new AtomicReference<>(ClientState.CREATED);

    private final ExecutorService clientExecutor;
    private final ConcurrentHashMap<String, MessageCallback> subscriptions;

    public DefaultRocketMQAsyncClient() {
        this.clientExecutor = Executors.newCachedThreadPool(new ClientThreadFactory());
        this.subscriptions = new ConcurrentHashMap<>();
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        // Spring post-construction initialization if needed
        logger.debug("DefaultRocketMQAsyncClient bean initialized");
    }

    @Override
    public void initialize(ClientConfiguration config) throws RocketMQException {
        if (initialized.compareAndSet(false, true)) {
            try {
                this.config = config;

                setState(ClientState.INITIALIZING);

                // Initialize security
                initializeSecurity();

                // Initialize metrics collector
                metricsCollector = new MetricsCollector();
                metricsCollector.afterPropertiesSet();

                // Initialize connection manager
                connectionManager = new ConnectionManager(
                        config.getNamesrvAddr(),
                        config.getMaxConnections(),
                        config.isTlsEnabled()
                );

                // Initialize message publisher
                messagePublisher = new MessagePublisher(config, connectionManager, metricsCollector, messageStore);

                // Initialize message consumer
                messageConsumer = new MessageConsumer(config, connectionManager, metricsCollector, metadataStore, messagePublisher);

                // Create a special callback function for handling responses
                MessageCallback responseHandler = message -> {
                    // Try to handle this message as a response via callback manager
                    boolean handledAsResponse = callbackManager.handleResponse(message);

                    if (handledAsResponse) {
                        logger.debug("Response handled via CallbackManager: {}", message.getMessageId());
                        return ai.hack.rocketmq.callback.MessageProcessingResult.success();
                    } else {
                        // Not a response, treat as regular message processing
                        logger.debug("Message not a response: {}", message.getMessageId());
                        return ai.hack.rocketmq.callback.MessageProcessingResult.success();
                    }
                };

                // Initialize callback manager
                callbackManager = new CallbackManager(config.getRequestTimeout());

                // Initialize core components
                connectionManager.afterPropertiesSet();
                messagePublisher.afterPropertiesSet();
                messageConsumer.afterPropertiesSet();
                callbackManager.afterPropertiesSet();

                // Start message consumption
                messageConsumer.start();

                setState(ClientState.READY);

                logger.info("RocketMQ async client initialized successfully with configuration: {}", config);

                // Initialize persistence stores if enabled
                if (config.isPersistenceEnabled()) {
                    messageStore.afterPropertiesSet();
                    metadataStore.afterPropertiesSet();
                }

                // Start maintenance tasks
                startMaintenanceTasks();

                logger.info("DefaultRocketMQAsyncClient is ready for operations");

            } catch (Exception e) {
                setState(ClientState.ERROR);
                throw new RocketMQException(ai.hack.rocketmq.exception.ErrorCode.INTERNAL_ERROR,
                        "Failed to initialize RocketMQ client", e);
            }
        }
    }

    @Override
    public CompletableFuture<SendResult> sendMessageAsync(Message message) throws RocketMQException {
        ensureReady();

        long startTime = System.nanoTime();

        return CompletableFuture
                .supplyAsync(() -> {
                    try {
                        metricsCollector.incrementMessagesSent();
                        return messagePublisher.sendMessageAsync(message);
                    } catch (RocketMQException e) {
                        throw new RuntimeException(e);
                    }
                }, clientExecutor)
                .thenCompose(future -> future)
                .whenComplete((result, throwable) -> {
                    long latency = System.nanoTime() - startTime;
                    metricsCollector.recordLatency(latency);

                    if (throwable != null) {
                        logger.error("Async message send failed: {}", message.getMessageId(), throwable);
                        metricsCollector.incrementMessagesFailed();
                    } else {
                        logger.debug("Async message sent successfully: {}", result.getMessageId());
                    }
                });
    }

    @Override
    public SendResult sendMessageSync(Message message, Duration timeout) throws RocketMQException, TimeoutException {
        ensureReady();

        try {
            logger.debug("Sending message synchronously: {}", message.getMessageId());
            return messagePublisher.sendMessageSync(message, timeout);
        } catch (TimeoutException e) {
            metricsCollector.incrementTimeouts();
            throw e;
        }
    }

    @Override
    public CompletableFuture<BatchSendResult> sendBatchAsync(List<Message> messages) throws RocketMQException {
        ensureReady();

        if (messages == null || messages.isEmpty()) {
            return CompletableFuture.completedFuture(new BatchSendResult(new ArrayList<>()));
        }

        long startTime = System.nanoTime();

        return CompletableFuture
                .supplyAsync(() -> {
                    try {
                        List<CompletableFuture<SendResult>> futures = new ArrayList<>();

                        for (Message message : messages) {
                           CompletableFuture<SendResult> future = messagePublisher.sendMessageAsync(message);
                            futures.add(future);
                        }

                        // Wait for all futures to complete
                        CompletableFuture<Void> allFutures = CompletableFuture.allOf(
                                futures.toArray(new CompletableFuture[0]));

                        return allFutures.thenApply(v -> {
                            List<SendResult> results = new ArrayList<>();
                            for (CompletableFuture<SendResult> future : futures) {
                                results.add(future.join());
                            }
                            return new BatchSendResult(results);
                        });
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                }, clientExecutor)
                .thenCompose(future -> future)
                .whenComplete((result, throwable) -> {
                    long latency = System.nanoTime() - startTime;
                    metricsCollector.recordLatency(latency);

                    if (throwable != null) {
                        logger.error("Batch async message send failed", throwable);
                    } else {
                        logger.debug("Batch async message sent: {}", result.getSummary());
                    }
                });
    }

    @Override
    public CompletableFuture<Message> sendAndReceiveAsync(Message message, Duration timeout) throws RocketMQException {
        ensureReady();

        if (message == null) {
            CompletableFuture<Message> future = new CompletableFuture<>();
            future.completeExceptionally(new IllegalArgumentException("Message cannot be null"));
            return future;
        }

        if (timeout == null || timeout.isZero() || timeout.isNegative()) {
            timeout = config.getRequestTimeout();
        }

        logger.info("🔄 Starting request-response: {} (timeout: {}s)", message.getMessageId(), timeout.toSeconds());

        try {
            // Register the pending request with callback manager
            CompletableFuture<Message> responseFuture = callbackManager.sendWithCallback(message, timeout);

            // Send the message asynchronously
            CompletableFuture<ai.hack.rocketmq.result.SendResult> sendFuture = sendMessageAsync(message);

            // Handle send failures
            sendFuture.whenComplete((sendResult, throwable) -> {
                if (throwable != null) {
                    logger.error("Send failed for request-response message: {}", message.getMessageId(), throwable);

                    // Correlate the error to the response future
                    if (!responseFuture.isDone()) {
                        responseFuture.completeExceptionally(throwable);
                    }
                } else {
                    logger.debug("Request message sent successfully: {} -> {}",
                                message.getMessageId(), sendResult.getMessageId());
                }
            });

            return responseFuture;

        } catch (Exception e) {
            throw new RocketMQException(ai.hack.rocketmq.exception.ErrorCode.INTERNAL_ERROR,
                    "Failed to initiate request-response operation", e);
        }
    }

    @Override
    public void subscribe(String topic, MessageCallback callback) throws RocketMQException {
        ensureReady();
        logger.info("Subscribing to topic: {}", topic);
        messageConsumer.subscribe(topic, callback);
        subscriptions.put(topic, callback);

            // Also subscribe to response handling if we have a callback topic that points to this topic
            for (String subscribedTopic : subscriptions.keySet()) {
                String callbackTopic = getResponseTopicForRequest(subscribedTopic);
                if (callbackTopic != null && !callbackTopic.equals(subscribedTopic)) {
                    // Subscribe to callback topic with our response handler
                    if (!subscriptions.containsKey(callbackTopic)) {
                        messageConsumer.subscribe(callbackTopic, createResponseHandler());
                        logger.debug("Auto-subscribed to response topic: {}", callbackTopic);
                    }
                }
            }
    }

    @Override
    public void unsubscribe(String topic) throws RocketMQException {
        ensureReady();
        logger.info("Unsubscribing from topic: {}", topic);
        messageConsumer.unsubscribe(topic);
        subscriptions.remove(topic);
    }

    @Override
    public ClientStatus getClientStatus() {
        if (!initialized.get()) {
            return new ClientStatus(ClientState.CREATED, null, new ArrayList<>(), null);
        }

        if (shuttingDown.get()) {
            return new ClientStatus(ClientState.SHUTTING_DOWN, null, new ArrayList<>(), null);
        }

        // Get publisher and consumer stats
        MessagePublisher.PublisherStats publisherStats = null;
        MessageConsumer.ConsumerStats consumerStats = null;
        if (messagePublisher != null) {
            publisherStats = messagePublisher.getStats();
        }
        if (messageConsumer != null) {
            consumerStats = messageConsumer.getStats();
        }

        // Get performance snapshot from metrics
        MetricsCollector.PerformanceSnapshot performanceSnapshot = null;
        if (metricsCollector != null) {
            performanceSnapshot = metricsCollector.getSnapshot();
        }

        ClientState state = currentState.get();
        return new ClientStatus(state, performanceSnapshot, new ArrayList<>(subscriptions.keySet()), Instant.now());
    }

    @Override
    public void shutdown(Duration timeout) throws RocketMQException {
        if (shuttingDown.compareAndSet(false, true)) {
            try {
                setState(ClientState.SHUTTING_DOWN);

                logger.info("Shutting down DefaultRocketMQAsyncClient");

                // Wait for in-flight operations to complete
                clientExecutor.shutdown();
                if (!clientExecutor.awaitTermination(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
                    clientExecutor.shutdownNow();
                    if (!clientExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                        logger.warn("Client executor did not terminate cleanly");
                    }
                }

                // Shutdown message consumer, callback manager, and publisher
                if (messageConsumer != null) {
                    messageConsumer.destroy();
                }
                if (callbackManager != null) {
                    callbackManager.destroy();
                }
                if (messagePublisher != null) {
                    messagePublisher.destroy();
                }

                // Shutdown connection manager
                if (connectionManager != null) {
                    connectionManager.destroy();
                }

                // Shutdown metrics collector
                if (metricsCollector != null) {
                    metricsCollector.destroy();
                }

                // Close persistence stores
                if (messageStore != null) {
                    messageStore.destroy();
                }
                if (metadataStore != null) {
                    metadataStore.destroy();
                }

                setState(ClientState.SHUTDOWN);

                logger.info("DefaultRocketMQAsyncClient shutdown complete");

            } catch (Exception e) {
                setState(ClientState.ERROR);
                throw new RocketMQException(ai.hack.rocketmq.exception.ErrorCode.INTERNAL_ERROR,
                        "Failed to shutdown RocketMQ client", e);
            }
        }
    }

    @Override
    public void close() throws Exception {
        shutdown(Duration.ofSeconds(30));
    }

    @Override
    public boolean isReady() {
        return initialized.get() && !shuttingDown.get() && currentState.get() == ClientState.READY;
    }

    private void ensureReady() throws RocketMQException {
        if (!isReady()) {
            throw new RocketMQException(ai.hack.rocketmq.exception.ErrorCode.CONFIGURATION_ERROR,
                    "Client is not ready. Current state: " + currentState.get());
        }
    }

    private void initializeSecurity() throws RocketMQException {
        try {
            // Initialize authentication manager
            if (config.getAccessKey() != null && config.getSecretKey() != null) {
                authenticationManager = new AuthenticationManager(config.getAccessKey(), config.getSecretKey());
                if (!authenticationManager.validateCredentials()) {
                    throw new RocketMQSecurityException("Invalid authentication credentials");
                }
            }

            // Initialize TLS configuration
            if (config.isTlsEnabled()) {
                tlsConfiguration = new TLSConfiguration(
                        true,
                        config.getTrustStorePath(),
                        false, // keyStorePath
                        null,  // keyStorePassword
                        null   // trustStorePassword
                );
                tlsConfiguration.initialize();
            }

        } catch (RocketMQSecurityException e) {
            throw new RocketMQException(ai.hack.rocketmq.exception.ErrorCode.SECURITY_ERROR,
                    "Security initialization failed", e);
        }
    }

    private void startMaintenanceTasks() {
        // Start periodic maintenance tasks (cleanup, health checks, etc.)
        // Implementation for maintenance tasks will be added later
    }

    private void setState(ClientState state) {
        currentState.set(state);
        logger.debug("Client state changed to: {}", state);
    }

    private String getResponseTopicForRequest(String requestTopic) {
        // Simple heuristic: if topic ends with ".request", response ends with ".response"
        if (requestTopic.endsWith(".request")) {
            return requestTopic.replace(".request", ".response");
        }
        // If topic starts with "req.", response starts with "resp."
        if (requestTopic.startsWith("req.")) {
            return "resp." + requestTopic.substring(4);
        }
        return null;
    }

    private MessageCallback createResponseHandler() {
        return message -> {
            boolean handledAsResponse = callbackManager.handleResponse(message);

            if (handledAsResponse) {
                logger.debug("Response handled via CallbackManager: {}", message.getMessageId());
                return ai.hack.rocketmq.callback.MessageProcessingResult.success();
            } else {
                logger.debug("Message not a response: {}", message.getMessageId());
                return ai.hack.rocketmq.callback.MessageProcessingResult.success();
            }
        };
    }

    /**
     * Thread factory for client background threads.
     */
    private static class ClientThreadFactory implements ThreadFactory {
        private final AtomicInteger threadNumber = new AtomicInteger(1);

        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r, "rocketmq-client-" + threadNumber.getAndIncrement());
            t.setDaemon(true);
            return t;
        }
    }
}