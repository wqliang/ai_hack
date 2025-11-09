package ai.hack.rocketmq.core;

import ai.hack.rocketmq.exception.TimeoutException;
import ai.hack.rocketmq.model.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Manages request-response correlation and timeout handling.
 * Provides complete correlation ID mapping with timeout management.
 */
public class CallbackManager implements InitializingBean, DisposableBean {

    private static final Logger logger = LoggerFactory.getLogger(CallbackManager.class);

    // Map correlation IDs to pending requests
    private final Map<String, CompletableFuture<Message>> pendingRequests;

    // Map correlation IDs to request metadata for cleanup
    private final Map<String, PendingRequestMetadata> requestMetadata;

    private final ScheduledExecutorService timeoutExecutor;
    private final Duration defaultTimeout;
    private final AtomicLong correlationIdSequence;

    public CallbackManager(Duration defaultTimeout) {
        this.defaultTimeout = defaultTimeout;
        this.pendingRequests = new ConcurrentHashMap<>();
        this.requestMetadata = new ConcurrentHashMap<>();
        this.timeoutExecutor = Executors.newScheduledThreadPool(2, new CallbackThreadFactory());
        this.correlationIdSequence = new AtomicLong(0);
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        // Start periodic cleanup task
        timeoutExecutor.scheduleAtFixedRate(this::cleanupExpiredRequests, 30, 30, TimeUnit.SECONDS);
        logger.info("CallbackManager initialized with default timeout: {}", defaultTimeout);
    }

    @Override
    public void destroy() throws Exception {
        logger.info("Shutting down CallbackManager");

        // Cancel all pending requests
        for (Map.Entry<String, CompletableFuture<Message>> entry : pendingRequests.entrySet()) {
            CompletableFuture<Message> future = entry.getValue();
            if (!future.isDone()) {
                future.completeExceptionally(new TimeoutException("shutdown", Duration.ZERO,
                        "CallbackManager is shutting down"));
            }
        }
        pendingRequests.clear();
        requestMetadata.clear();

        timeoutExecutor.shutdown();
        try {
            if (!timeoutExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                timeoutExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            timeoutExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }

        logger.info("CallbackManager shutdown complete");
    }

    /**
     * Send a message expecting a response and returns a CompletableFuture.
     *
     * @param message the request message
     * @param timeout timeout for response
     * @return CompletableFuture that completes with the response message
     */
    public CompletableFuture<Message> sendWithCallback(Message message, Duration timeout) {
        if (message == null) {
            CompletableFuture<Message> future = new CompletableFuture<>();
            future.completeExceptionally(new IllegalArgumentException("Message cannot be null"));
            return future;
        }

        // Generate correlation ID
        String correlationId = generateCorrelationId();

        // Add correlation ID as header
        message.withHeader("correlation-id", correlationId);

        // Create CompletableFuture for response
        CompletableFuture<Message> responseFuture = new CompletableFuture<>();

        // Store pending request
        pendingRequests.put(correlationId, responseFuture);
        requestMetadata.put(correlationId, new PendingRequestMetadata(message, Instant.now(), timeout));

        // Schedule timeout
        timeoutExecutor.schedule(() -> {
            handleTimeout(correlationId, timeout);
        }, timeout.toMillis(), TimeUnit.MILLISECONDS);

        logger.debug("Registered pending request with correlation ID: {} (timeout: {})", correlationId, timeout);

        return responseFuture;
    }

    /**
     * Handle an incoming response message.
     *
     * @param response the response message
     * @return true if message was handled as a valid response, false otherwise
     */
    public boolean handleResponse(Message response) {
        if (response == null) {
            return false;
        }

        // Extract correlation ID from headers
        String correlationId = response.getHeader("response-to");
        if (correlationId == null) {
            correlationId = response.getHeader("correlation-id");
        }

        if (correlationId == null) {
            logger.debug("Response message has no correlation ID: {}", response.getMessageId());
            return false;
        }

        // Lookup pending request
        CompletableFuture<Message> future = pendingRequests.remove(correlationId);
        if (future == null) {
            logger.debug("No pending request found for correlation ID: {}", correlationId);
            return false;
        }

        // Clean up metadata
        requestMetadata.remove(correlationId);

        // Complete the future with response
        if (!future.isDone()) {
            future.complete(response);
            logger.debug("Response delivered for correlation ID: {}", correlationId);
            return true;
        } else {
            logger.debug("Request already completed for correlation ID: {}", correlationId);
            return false;
        }
    }

    /**
     * Cancel a pending request.
     *
     * @param correlationId the correlation ID to cancel
     * @return true if request was found and cancelled
     */
    public boolean cancelPendingRequest(String correlationId) {
        CompletableFuture<Message> future = pendingRequests.remove(correlationId);
        if (future != null) {
            requestMetadata.remove(correlationId);

            if (!future.isDone()) {
                future.cancel(true);
                logger.info("Cancelled pending request: {}", correlationId);
                return true;
            }
        }
        return false;
    }

    /**
     * Gets the number of currently pending requests.
     */
    public int getPendingCount() {
        return pendingRequests.size();
    }

    /**
     * Gets statistics about pending requests.
     */
    public CallbackStats getStats() {
        int pending = pendingRequests.size();
        long oldestAge = 0;

        if (!requestMetadata.isEmpty()) {
            Instant now = Instant.now();
            oldestAge = requestMetadata.values().stream()
                    .mapToLong(metadata -> Duration.between(metadata.createdAt, now).toSeconds())
                    .max()
                    .orElse(0L);
        }

        return new CallbackStats(pending, oldestAge, correlationIdSequence.get());
    }

    private void handleTimeout(String correlationId, Duration timeout) {
        CompletableFuture<Message> future = pendingRequests.get(correlationId);
        if (future != null && !future.isDone()) {
            // Remove from pending map
            pendingRequests.remove(correlationId);
            requestMetadata.remove(correlationId);

            // Complete with timeout exception
            TimeoutException timeoutException = new TimeoutException(
                    "request-response", timeout,
                    "Request timed out after " + timeout.toSeconds() + " seconds", correlationId);
            future.completeExceptionally(timeoutException);

            logger.debug("Request timed out for correlation ID: {}", correlationId);
        }
    }

    private void cleanupExpiredRequests() {
        Instant now = Instant.now();

        requestMetadata.entrySet().removeIf(entry -> {
            String correlationId = entry.getKey();
            PendingRequestMetadata metadata = entry.getValue();

            // Check if request has expired
            if (metadata.isExpired(now)) {
                CompletableFuture<Message> future = pendingRequests.remove(correlationId);

                if (future != null && !future.isDone()) {
                    TimeoutException timeoutException = new TimeoutException(
                            "request-response", metadata.timeout,
                            "Request expired due to age limit", correlationId);
                    future.completeExceptionally(timeoutException);
                }

                logger.debug("Cleaned up expired request: {}", correlationId);
                return true; // Remove from map
            }

            return false; // Keep in map
        });
    }

    private String generateCorrelationId() {
        return Long.toHexString(System.currentTimeMillis()) + "-" +
               correlationIdSequence.incrementAndGet() + "-" +
               UUID.randomUUID().toString().substring(0, 8);
    }

    /**
     * Metadata for pending requests.
     */
    private static class PendingRequestMetadata {
        private final Message originalMessage;
        private final Instant createdAt;
        private final Duration timeout;

        public PendingRequestMetadata(Message originalMessage, Instant createdAt, Duration timeout) {
            this.originalMessage = originalMessage;
            this.createdAt = createdAt;
            this.timeout = timeout;
        }

        public boolean isExpired(Instant now) {
            // Consider expired if more than double the timeout has passed
            return Duration.between(createdAt, now).toMillis() > timeout.toMillis() * 2;
        }
    }

    /**
     * Thread factory for callback manager threads.
     */
    private static class CallbackThreadFactory implements ThreadFactory {
        private final AtomicInteger threadNumber = new AtomicInteger(1);

        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r, "rocketmq-callback-" + threadNumber.getAndIncrement());
            t.setDaemon(true);
            return t;
        }
    }

    /**
     * Statistics for callback operations.
     */
    public static class CallbackStats {
        private final int pendingRequests;
        private final long oldestPendingAgeSeconds;
        private final long totalCorrelationIdsGenerated;

        public CallbackStats(int pendingRequests, long oldestPendingAgeSeconds, long totalCorrelationIdsGenerated) {
            this.pendingRequests = pendingRequests;
            this.oldestPendingAgeSeconds = oldestPendingAgeSeconds;
            this.totalCorrelationIdsGenerated = totalCorrelationIdsGenerated;
        }

        public int getPendingRequests() {
            return pendingRequests;
        }

        public long getOldestPendingAgeSeconds() {
            return oldestPendingAgeSeconds;
        }

        public long getTotalCorrelationIdsGenerated() {
            return totalCorrelationIdsGenerated;
        }

        @Override
        public String toString() {
            return String.format("CallbackStats{pending=%d, oldestAge=%ds, totalGenerated=%d}",
                               pendingRequests, oldestPendingAgeSeconds, totalCorrelationIdsGenerated);
        }
    }
}