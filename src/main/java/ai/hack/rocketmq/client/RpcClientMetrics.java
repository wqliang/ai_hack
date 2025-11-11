package ai.hack.rocketmq.client;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

/**
 * Collects and tracks performance metrics for RPC client operations.
 * <p>
 * This class provides thread-safe metrics collection for monitoring RPC client
 * performance including request counts, latencies, session activity, and throughput.
 * All operations are lock-free and designed for low overhead.
 *
 * @author Claude Code
 * @since 1.0.0
 */
public class RpcClientMetrics {

    // Request metrics
    private final LongAdder totalRequests = new LongAdder();
    private final LongAdder successfulRequests = new LongAdder();
    private final LongAdder failedRequests = new LongAdder();
    private final LongAdder timeoutRequests = new LongAdder();

    // Latency metrics (in microseconds for better precision)
    private final LongAdder totalLatencyMicros = new LongAdder();
    private final AtomicLong minLatencyMicros = new AtomicLong(Long.MAX_VALUE);
    private final AtomicLong maxLatencyMicros = new AtomicLong(0);

    // Session metrics
    private final LongAdder totalSessions = new LongAdder();
    private final LongAdder completedSessions = new LongAdder();
    private final AtomicLong activeSessions = new AtomicLong(0);

    // Message throughput metrics
    private final LongAdder streamingMessages = new LongAdder();
    private final LongAdder totalBytesSent = new LongAdder();
    private final LongAdder totalBytesReceived = new LongAdder();

    // Tracking start time for rate calculations
    private final Instant startTime = Instant.now();

    /**
     * Records a new request attempt.
     */
    public void recordRequest() {
        totalRequests.increment();
    }

    /**
     * Records a successful request with its latency.
     *
     * @param latencyNanos request latency in nanoseconds
     */
    public void recordSuccess(long latencyNanos) {
        successfulRequests.increment();
        recordLatency(latencyNanos);
    }

    /**
     * Records a failed request.
     */
    public void recordFailure() {
        failedRequests.increment();
    }

    /**
     * Records a timeout request.
     */
    public void recordTimeout() {
        timeoutRequests.increment();
        failedRequests.increment();
    }

    /**
     * Records request latency and updates min/max values.
     *
     * @param latencyNanos latency in nanoseconds
     */
    private void recordLatency(long latencyNanos) {
        long micros = latencyNanos / 1000;
        totalLatencyMicros.add(micros);

        // Update min latency
        minLatencyMicros.updateAndGet(current -> Math.min(current, micros));

        // Update max latency
        maxLatencyMicros.updateAndGet(current -> Math.max(current, micros));
    }

    /**
     * Records a new streaming session started.
     */
    public void recordSessionStart() {
        totalSessions.increment();
        activeSessions.incrementAndGet();
    }

    /**
     * Records a streaming session completed.
     */
    public void recordSessionEnd() {
        completedSessions.increment();
        activeSessions.decrementAndGet();
    }

    /**
     * Records a streaming message sent.
     */
    public void recordStreamingMessage() {
        streamingMessages.increment();
    }

    /**
     * Records bytes sent in a message.
     *
     * @param bytes number of bytes sent
     */
    public void recordBytesSent(long bytes) {
        totalBytesSent.add(bytes);
    }

    /**
     * Records bytes received in a response.
     *
     * @param bytes number of bytes received
     */
    public void recordBytesReceived(long bytes) {
        totalBytesReceived.add(bytes);
    }

    /**
     * Gets the total number of requests attempted.
     *
     * @return total request count
     */
    public long getTotalRequests() {
        return totalRequests.sum();
    }

    /**
     * Gets the number of successful requests.
     *
     * @return successful request count
     */
    public long getSuccessfulRequests() {
        return successfulRequests.sum();
    }

    /**
     * Gets the number of failed requests (including timeouts).
     *
     * @return failed request count
     */
    public long getFailedRequests() {
        return failedRequests.sum();
    }

    /**
     * Gets the number of timeout requests.
     *
     * @return timeout request count
     */
    public long getTimeoutRequests() {
        return timeoutRequests.sum();
    }

    /**
     * Calculates the success rate as a percentage.
     *
     * @return success rate (0.0 to 100.0), or 0.0 if no requests
     */
    public double getSuccessRate() {
        long total = getTotalRequests();
        if (total == 0) {
            return 0.0;
        }
        return (double) getSuccessfulRequests() / total * 100.0;
    }

    /**
     * Gets the average request latency in milliseconds.
     *
     * @return average latency in milliseconds, or 0.0 if no successful requests
     */
    public double getAverageLatencyMillis() {
        long successful = getSuccessfulRequests();
        if (successful == 0) {
            return 0.0;
        }
        return totalLatencyMicros.sum() / (double) successful / 1000.0;
    }

    /**
     * Gets the minimum request latency in milliseconds.
     *
     * @return minimum latency in milliseconds, or 0.0 if no requests
     */
    public double getMinLatencyMillis() {
        long min = minLatencyMicros.get();
        return min == Long.MAX_VALUE ? 0.0 : min / 1000.0;
    }

    /**
     * Gets the maximum request latency in milliseconds.
     *
     * @return maximum latency in milliseconds
     */
    public double getMaxLatencyMillis() {
        return maxLatencyMicros.get() / 1000.0;
    }

    /**
     * Gets the total number of streaming sessions started.
     *
     * @return total session count
     */
    public long getTotalSessions() {
        return totalSessions.sum();
    }

    /**
     * Gets the number of currently active streaming sessions.
     *
     * @return active session count
     */
    public long getActiveSessions() {
        return activeSessions.get();
    }

    /**
     * Gets the number of completed streaming sessions.
     *
     * @return completed session count
     */
    public long getCompletedSessions() {
        return completedSessions.sum();
    }

    /**
     * Gets the total number of streaming messages sent.
     *
     * @return streaming message count
     */
    public long getStreamingMessages() {
        return streamingMessages.sum();
    }

    /**
     * Gets the total bytes sent.
     *
     * @return total bytes sent
     */
    public long getTotalBytesSent() {
        return totalBytesSent.sum();
    }

    /**
     * Gets the total bytes received.
     *
     * @return total bytes received
     */
    public long getTotalBytesReceived() {
        return totalBytesReceived.sum();
    }

    /**
     * Calculates the uptime duration since metrics tracking started.
     *
     * @return uptime duration
     */
    public Duration getUptime() {
        return Duration.between(startTime, Instant.now());
    }

    /**
     * Calculates the average request rate (requests per second).
     *
     * @return requests per second, or 0.0 if uptime is zero
     */
    public double getRequestsPerSecond() {
        long uptimeSeconds = getUptime().toSeconds();
        if (uptimeSeconds == 0) {
            return 0.0;
        }
        return (double) getTotalRequests() / uptimeSeconds;
    }

    /**
     * Calculates the average throughput in bytes per second (sent).
     *
     * @return bytes per second sent
     */
    public double getThroughputBytesSentPerSecond() {
        long uptimeSeconds = getUptime().toSeconds();
        if (uptimeSeconds == 0) {
            return 0.0;
        }
        return (double) getTotalBytesSent() / uptimeSeconds;
    }

    /**
     * Calculates the average throughput in bytes per second (received).
     *
     * @return bytes per second received
     */
    public double getThroughputBytesReceivedPerSecond() {
        long uptimeSeconds = getUptime().toSeconds();
        if (uptimeSeconds == 0) {
            return 0.0;
        }
        return (double) getTotalBytesReceived() / uptimeSeconds;
    }

    /**
     * Returns a formatted summary of all metrics.
     *
     * @return metrics summary string
     */
    public String getSummary() {
        return String.format(
            "RpcClientMetrics[" +
                "requests=%d, success=%d, failed=%d, timeout=%d, successRate=%.2f%%, " +
                "avgLatency=%.2fms, minLatency=%.2fms, maxLatency=%.2fms, " +
                "sessions=%d, activeSessions=%d, completedSessions=%d, streamingMessages=%d, " +
                "bytesSent=%d, bytesReceived=%d, uptime=%s, requestsPerSec=%.2f" +
                "]",
            getTotalRequests(), getSuccessfulRequests(), getFailedRequests(), getTimeoutRequests(),
            getSuccessRate(), getAverageLatencyMillis(), getMinLatencyMillis(), getMaxLatencyMillis(),
            getTotalSessions(), getActiveSessions(), getCompletedSessions(), getStreamingMessages(),
            getTotalBytesSent(), getTotalBytesReceived(), getUptime(), getRequestsPerSecond()
        );
    }

    /**
     * Resets all metrics to initial state.
     * <p>
     * This method is primarily for testing purposes. In production, metrics
     * should typically accumulate over the lifetime of the client.
     */
    public void reset() {
        totalRequests.reset();
        successfulRequests.reset();
        failedRequests.reset();
        timeoutRequests.reset();
        totalLatencyMicros.reset();
        minLatencyMicros.set(Long.MAX_VALUE);
        maxLatencyMicros.set(0);
        totalSessions.reset();
        completedSessions.reset();
        activeSessions.set(0);
        streamingMessages.reset();
        totalBytesSent.reset();
        totalBytesReceived.reset();
    }
}
