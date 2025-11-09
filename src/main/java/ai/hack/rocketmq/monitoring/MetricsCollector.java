package ai.hack.rocketmq.monitoring;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;

import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Collector for RocketMQ client metrics and performance data.
 * Provides real-time monitoring and metrics aggregation.
 */
public class MetricsCollector implements InitializingBean, DisposableBean {

    private static final Logger logger = LoggerFactory.getLogger(MetricsCollector.class);

    // Message counters
    private final AtomicLong messagesSent = new AtomicLong(0);
    private final AtomicLong messagesReceived = new AtomicLong(0);
    private final AtomicLong messagesFailed = new AtomicLong(0);
    private final AtomicLong bytesSent = new AtomicLong(0);
    private final AtomicLong bytesReceived = new AtomicLong(0);

    // Timing metrics (in nanoseconds)
    private final AtomicLong totalLatencyNanos = new AtomicLong(0);
    private final AtomicLong minLatencyNanos = new AtomicLong(Long.MAX_VALUE);
    private final AtomicLong maxLatencyNanos = new AtomicLong(0);

    // Connection metrics
    private final AtomicLong activeConnections = new AtomicLong(0);
    private final AtomicLong connectionErrors = new AtomicLong(0);
    private final AtomicLong timeouts = new AtomicLong(0);

    // Memory usage metrics (in MB)
    private final AtomicLong heapMemoryUsed = new AtomicLong(0);
    private final AtomicLong directMemoryUsed = new AtomicLong(0);
    private final AtomicLong maxHeapMemory = new AtomicLong(0);

    // Custom metrics
    private final ConcurrentHashMap<String, AtomicLong> customCounters = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, AtomicDouble> customGauges = new ConcurrentHashMap<>();

    // Scheduled task for metric calculations
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    private volatile boolean shutdown = false;

    // Calculated metrics
    private volatile double currentThroughput = 0.0; // messages per second
    private volatile double currentErrorRate = 0.0; // error percentage
    private volatile double averageLatencyMs = 0.0; // milliseconds

    @Override
    public void afterPropertiesSet() throws Exception {
        // Schedule periodic metric calculations
        scheduler.scheduleAtFixedRate(this::calculateDerivedMetrics, 1, 1, TimeUnit.SECONDS);
        logger.info("Metrics collector initialized with memory tracking");
    }

    @Override
    public void destroy() throws Exception {
        logger.info("Shutting down metrics collector");
        shutdown = true;
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
        logger.info("Metrics collector shutdown complete");
    }

    // Message metrics
    public void incrementMessagesSent() {
        messagesSent.incrementAndGet();
    }

    public void incrementMessagesSent(long count) {
        messagesSent.addAndGet(count);
    }

    public void incrementMessagesReceived() {
        messagesReceived.incrementAndGet();
    }

    public void incrementMessagesReceived(long count) {
        messagesReceived.addAndGet(count);
    }

    public void incrementMessagesFailed() {
        messagesFailed.incrementAndGet();
    }

    public void addBytesSent(long bytes) {
        bytesSent.addAndGet(bytes);
    }

    public void addBytesReceived(long bytes) {
        bytesReceived.addAndGet(bytes);
    }

    // Latency metrics
    public void recordLatency(long latencyNanos) {
        totalLatencyNanos.addAndGet(latencyNanos);

        // Update min/max in a thread-safe way
        long currentMin = minLatencyNanos.get();
        while (latencyNanos < currentMin && !minLatencyNanos.compareAndSet(currentMin, latencyNanos)) {
            currentMin = minLatencyNanos.get();
        }

        long currentMax = maxLatencyNanos.get();
        while (latencyNanos > currentMax && !maxLatencyNanos.compareAndSet(currentMax, latencyNanos)) {
            currentMax = maxLatencyNanos.get();
        }
    }

    // Connection metrics
    public void setActiveConnections(int count) {
        activeConnections.set(count);
    }

    public void incrementActiveConnections() {
        activeConnections.incrementAndGet();
    }

    public void decrementActiveConnections() {
        activeConnections.decrementAndGet();
    }

    public void incrementConnectionErrors() {
        connectionErrors.incrementAndGet();
    }

    public void incrementTimeouts() {
        timeouts.incrementAndGet();
    }

    // Custom metrics
    public void incrementCounter(String name) {
        customCounters.computeIfAbsent(name, k -> new AtomicLong(0)).incrementAndGet();
    }

    public void addToCounter(String name, long value) {
        customCounters.computeIfAbsent(name, k -> new AtomicLong(0)).addAndGet(value);
    }

    public void setGauge(String name, double value) {
        customGauges.computeIfAbsent(name, k -> new AtomicDouble(0)).set(value);
    }

    // Metric accessors
    public long getMessagesSent() {
        return messagesSent.get();
    }

    public long getMessagesReceived() {
        return messagesReceived.get();
    }

    public long getMessagesFailed() {
        return messagesFailed.get();
    }

    public long getBytesSent() {
        return bytesSent.get();
    }

    public long getBytesReceived() {
        return bytesReceived.get();
    }

    public double getAverageLatencyMs() {
        return averageLatencyMs;
    }

    public long getMinLatencyNanos() {
        long min = minLatencyNanos.get();
        return min == Long.MAX_VALUE ? 0 : min;
    }

    public long getMaxLatencyNanos() {
        return maxLatencyNanos.get();
    }

    public int getActiveConnections() {
        return (int) activeConnections.get();
    }

    public long getConnectionErrors() {
        return connectionErrors.get();
    }

    public long getTimeouts() {
        return timeouts.get();
    }

    public double getCurrentThroughput() {
        return currentThroughput;
    }

    public double getCurrentErrorRate() {
        return currentErrorRate;
    }

    public long getCustomCounter(String name) {
        AtomicLong counter = customCounters.get(name);
        return counter != null ? counter.get() : 0;
    }

    public double getCustomGauge(String name) {
        AtomicDouble gauge = customGauges.get(name);
        return gauge != null ? gauge.get() : 0.0;
    }

    /**
     * Gets a comprehensive performance snapshot.
     */
    public PerformanceSnapshot getSnapshot() {
        return new PerformanceSnapshot(
                getMessagesSent(),
                getMessagesReceived(),
                getMessagesFailed(),
                getBytesSent(),
                getBytesReceived(),
                getAverageLatencyMs(),
                getMinLatencyNanos(),
                getMaxLatencyNanos(),
                getActiveConnections(),
                getConnectionErrors(),
                getTimeouts(),
                getCurrentThroughput(),
                getCurrentErrorRate(),
                Instant.now()
        );
    }

    /**
     * Calculates derived metrics like throughput and error rate.
     */
    private void calculateDerivedMetrics() {
        if (shutdown) {
            return;
        }

        try {
            long sent = messagesSent.get();
            long received = messagesReceived.get();
            long failed = messagesFailed.get();
            long totalMessages = sent + received;

            // Calculate average latency
            long totalLatency = totalLatencyNanos.get();
            long latencyCount = sent + received; // Rough approximation
            if (latencyCount > 0) {
                averageLatencyMs = (totalLatency / latencyCount) / 1_000_000.0;
            }

            // Calculate throughput (messages per second over last interval)
            // This is a simplified calculation - in production, implement proper time-windowed metrics

            // Update memory metrics
            updateMemoryMetrics();
            currentThroughput = totalMessages / 60.0; // Simple average over last minute

            // Calculate error rate
            if (totalMessages > 0) {
                currentErrorRate = (failed * 100.0) / totalMessages;
            }

            logger.debug("Calculated metrics - throughput: {:.2f} msg/s, error_rate: {:.2f}%, avg_latency: {:.2f}ms",
                       currentThroughput, currentErrorRate, averageLatencyMs);

        } catch (Exception e) {
            logger.error("Error calculating derived metrics", e);
        }
    }

    /**
     * Resets all counters (useful for testing).
     */
    public void reset() {
        messagesSent.set(0);
        messagesReceived.set(0);
        messagesFailed.set(0);
        bytesSent.set(0);
        bytesReceived.set(0);
        totalLatencyNanos.set(0);
        minLatencyNanos.set(Long.MAX_VALUE);
        maxLatencyNanos.set(0);
        connectionErrors.set(0);
        timeouts.set(0);
        customCounters.clear();
        customGauges.clear();

        logger.info("All metrics reset");
    }

    /**
     * Snapshot of current performance metrics.
     */
    public static class PerformanceSnapshot {
        private final long messagesSent;
        private final long messagesReceived;
        private final long messagesFailed;
        private final long bytesSent;
        private final long bytesReceived;
        private final double averageLatencyMs;
        private final long minLatencyNanos;
        private final long maxLatencyNanos;
        private final int activeConnections;
        private final long connectionErrors;
        private final long timeouts;
        private final double throughput;
        private final double errorRate;
        private final Instant timestamp;

        public PerformanceSnapshot(long messagesSent, long messagesReceived, long messagesFailed,
                                 long bytesSent, long bytesReceived, double averageLatencyMs,
                                 long minLatencyNanos, long maxLatencyNanos, int activeConnections,
                                 long connectionErrors, long timeouts, double throughput,
                                 double errorRate, Instant timestamp) {
            this.messagesSent = messagesSent;
            this.messagesReceived = messagesReceived;
            this.messagesFailed = messagesFailed;
            this.bytesSent = bytesSent;
            this.bytesReceived = bytesReceived;
            this.averageLatencyMs = averageLatencyMs;
            this.minLatencyNanos = minLatencyNanos;
            this.maxLatencyNanos = maxLatencyNanos;
            this.activeConnections = activeConnections;
            this.connectionErrors = connectionErrors;
            this.timeouts = timeouts;
            this.throughput = throughput;
            this.errorRate = errorRate;
            this.timestamp = timestamp;
        }

        // Getters
        public long getMessagesSent() { return messagesSent; }
        public long getMessagesReceived() { return messagesReceived; }
        public long getMessagesFailed() { return messagesFailed; }
        public long getBytesSent() { return bytesSent; }
        public long getBytesReceived() { return bytesReceived; }
        public double getAverageLatencyMs() { return averageLatencyMs; }
        public long getMinLatencyNanos() { return minLatencyNanos; }
        public long getMaxLatencyNanos() { return maxLatencyNanos; }
        public int getActiveConnections() { return activeConnections; }
        public long getConnectionErrors() { return connectionErrors; }
        public long getTimeouts() { return timeouts; }
        public double getThroughput() { return throughput; }
        public double getErrorRate() { return errorRate; }
        public Instant getTimestamp() { return timestamp; }

        @Override
        public String toString() {
            return String.format("PerformanceSnapshot{sent=%d, received=%d, failed=%d, " +
                               "throughput=%.2f msg/s, errorRate=%.2f%%, avgLatency=%.2fms, " +
                               "connections=%d, memoryHeap=%dMB, memoryDirect=%dMB, timestamp=%s}",
                               messagesSent, messagesReceived, messagesFailed,
                               throughput, errorRate, averageLatencyMs,
                               activeConnections, heapMemoryUsed, directMemoryUsed, timestamp);
        }
    }

    /**
     * Updates memory usage metrics for monitoring purposes.
     */
    private void updateMemoryMetrics() {
        try {
            Runtime runtime = Runtime.getRuntime();

            // Heap memory in MB
            long totalMemory = runtime.totalMemory();
            long freeMemory = runtime.freeMemory();
            long usedMemory = totalMemory - freeMemory;
            long maxMemory = runtime.maxMemory();

            heapMemoryUsed.set(usedMemory / (1024 * 1024));
            maxHeapMemory.set(maxMemory / (1024 * 1024));

            // Direct memory estimation (simplified - in production use proper JMX or -XX:NativeMemoryTracking)
            try {
                Class<?> vmClass = Class.forName("sun.misc.VM");
                java.lang.reflect.Method maxDirectMemoryMethod = vmClass.getMethod("maxDirectMemory");
                long maxDirectMemory = (Long) maxDirectMemoryMethod.invoke(null);
                directMemoryUsed.set(Math.max(0, maxDirectMemory / (1024 * 1024) - 32)); // Estimate
            } catch (Exception e) {
                // Fallback for environments without sun.misc.VM
                directMemoryUsed.set(0);
            }

        } catch (Exception e) {
            logger.warn("Failed to update memory metrics", e);
        }
    }

    /**
     * Gets current memory usage statistics.
     */
    public MemoryStats getMemoryStats() {
        return new MemoryStats(
                heapMemoryUsed.get(),
                directMemoryUsed.get(),
                maxHeapMemory.get()
        );
    }

    /**
     * Statistics for memory usage monitoring.
     */
    public static class MemoryStats {
        private final long heapUsedMB;
        private final long directUsedMB;
        private final long maxHeapMB;

        public MemoryStats(long heapUsedMB, long directUsedMB, long maxHeapMB) {
            this.heapUsedMB = heapUsedMB;
            this.directUsedMB = directUsedMB;
            this.maxHeapMB = maxHeapMB;
        }

        public long getHeapUsedMB() { return heapUsedMB; }
        public long getDirectUsedMB() { return directUsedMB; }
        public long getMaxHeapMB() { return maxHeapMB; }

        public double getHeapUtilizationPercent() {
            return maxHeapMB > 0 ? ((double) heapUsedMB / maxHeapMB) * 100.0 : 0.0;
        }

        public boolean isWithinMemoryLimit(long limitMB) {
            return (heapUsedMB + directUsedMB) <= limitMB;
        }

        @Override
        public String toString() {
            return String.format("MemoryStats{heap=%dMB, direct=%dMB, max=%dMB, utilization=%.1f%%}",
                               heapUsedMB, directUsedMB, maxHeapMB, getHeapUtilizationPercent());
        }
    }
}