package ai.hack.rocketmq.client;

import ai.hack.rocketmq.client.exception.RpcTimeoutException;
import ai.hack.rocketmq.client.model.RpcResponse;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Manages correlation between RPC requests and responses.
 * <p>
 * This class tracks pending requests using their correlation IDs, manages timeouts
 * for each request, and completes the corresponding CompletableFuture when a
 * response arrives or timeout occurs. Thread-safe for concurrent request handling.
 *
 * @author Claude Code
 * @since 1.0.0
 */
public class CorrelationManager {

    /**
     * Map of correlation ID to pending request entry.
     * ConcurrentHashMap provides thread-safe access for multiple concurrent requests.
     */
    private final ConcurrentHashMap<String, CorrelationEntry> pendingRequests;

    /**
     * Executor service for scheduling timeout tasks.
     * Separate from message processing threads to avoid blocking.
     */
    private final ScheduledExecutorService timeoutExecutor;

    /**
     * Constructs a new CorrelationManager with the specified timeout executor.
     *
     * @param timeoutExecutor the executor for scheduling timeout tasks
     */
    public CorrelationManager(ScheduledExecutorService timeoutExecutor) {
        this.pendingRequests = new ConcurrentHashMap<>();
        this.timeoutExecutor = timeoutExecutor;
    }

    /**
     * Registers a new pending request with timeout handling.
     * <p>
     * Creates a correlation entry with a CompletableFuture and schedules a timeout task.
     * If the timeout expires before a response arrives, the future is completed exceptionally.
     *
     * @param correlationId the unique correlation ID for this request
     * @param timeoutMillis timeout in milliseconds
     * @return CompletableFuture that will be completed with response or timeout exception
     */
    public CompletableFuture<RpcResponse> registerRequest(String correlationId, long timeoutMillis) {
        CompletableFuture<RpcResponse> future = new CompletableFuture<>();

        // Schedule timeout task
        ScheduledFuture<?> timeoutTask = timeoutExecutor.schedule(() -> {
            CorrelationEntry removed = pendingRequests.remove(correlationId);
            if (removed != null) {
                long elapsed = System.currentTimeMillis() - removed.createdAt;
                removed.future.completeExceptionally(
                    new RpcTimeoutException("Request timeout after " + elapsed + "ms")
                );
            }
        }, timeoutMillis, TimeUnit.MILLISECONDS);

        // Create and store correlation entry
        CorrelationEntry entry = new CorrelationEntry(correlationId, future, timeoutTask);
        pendingRequests.put(correlationId, entry);

        return future;
    }

    /**
     * Completes a pending request with the received response.
     * <p>
     * Retrieves the correlation entry by ID, cancels the timeout task,
     * and completes the CompletableFuture with the response.
     *
     * @param response the received response
     * @return true if correlation entry was found and completed, false if not found
     */
    public boolean completeRequest(RpcResponse response) {
        CorrelationEntry entry = pendingRequests.remove(response.correlationId());
        if (entry != null) {
            // Cancel timeout task
            entry.timeoutTask.cancel(false);
            // Complete the future with response
            entry.future.complete(response);
            return true;
        }
        return false;
    }

    /**
     * Cancels a pending request.
     * <p>
     * Removes the correlation entry and cancels both the timeout task and the future.
     *
     * @param correlationId the correlation ID to cancel
     * @return true if correlation entry was found and cancelled, false if not found
     */
    public boolean cancelRequest(String correlationId) {
        CorrelationEntry entry = pendingRequests.remove(correlationId);
        if (entry != null) {
            entry.timeoutTask.cancel(false);
            entry.future.cancel(true);
            return true;
        }
        return false;
    }

    /**
     * Cancels all pending requests.
     * <p>
     * Used during shutdown to complete all futures exceptionally and clean up resources.
     *
     * @param reason the reason for cancellation (e.g., "Client shutting down")
     */
    public void cancelAll(String reason) {
        pendingRequests.values().forEach(entry -> {
            entry.timeoutTask.cancel(false);
            entry.future.completeExceptionally(new RpcTimeoutException(reason));
        });
        pendingRequests.clear();
    }

    /**
     * Gets the number of currently pending requests.
     *
     * @return count of pending requests
     */
    public int getPendingCount() {
        return pendingRequests.size();
    }

    /**
     * Internal class representing a pending request correlation entry.
     */
    private static class CorrelationEntry {
        final String correlationId;
        final CompletableFuture<RpcResponse> future;
        final ScheduledFuture<?> timeoutTask;
        final long createdAt;

        CorrelationEntry(String correlationId, CompletableFuture<RpcResponse> future, ScheduledFuture<?> timeoutTask) {
            this.correlationId = correlationId;
            this.future = future;
            this.timeoutTask = timeoutTask;
            this.createdAt = System.currentTimeMillis();
        }
    }
}
