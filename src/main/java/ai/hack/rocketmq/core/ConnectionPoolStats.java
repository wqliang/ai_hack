package ai.hack.rocketmq.core;

import java.time.Instant;
import java.util.StringJoiner;

/**
 * Statistics snapshot for connection pool monitoring and diagnostics.
 * Provides comprehensive metrics about pool health and usage patterns.
 */
public class ConnectionPoolStats {

    private final int maxConnections;
    private final int activeConnections;
    private final int availableConnections;
    private final int totalConnectionsCreated;
    private final int consecutiveFailures;
    private final boolean circuitOpen;
    private final Instant circuitOpenTime;
    private final boolean healthy;

    public ConnectionPoolStats(int maxConnections,
                              int activeConnections,
                              int availableConnections,
                              int totalConnectionsCreated,
                              int consecutiveFailures,
                              boolean circuitOpen,
                              Instant circuitOpenTime,
                              boolean healthy) {
        this.maxConnections = maxConnections;
        this.activeConnections = activeConnections;
        this.availableConnections = availableConnections;
        this.totalConnectionsCreated = totalConnectionsCreated;
        this.consecutiveFailures = consecutiveFailures;
        this.circuitOpen = circuitOpen;
        this.circuitOpenTime = circuitOpenTime;
        this.healthy = healthy;
    }

    /**
     * Gets the maximum number of connections allowed in the pool.
     */
    public int getMaxConnections() {
        return maxConnections;
    }

    /**
     * Gets the current number of active (borrowed) connections.
     */
    public int getActiveConnections() {
        return activeConnections;
    }

    /**
     * Gets the current number of available connections in the pool.
     */
    public int getAvailableConnections() {
        return availableConnections;
    }

    /**
     * Gets the total number of connections created since pool initialization.
     */
    public int getTotalConnectionsCreated() {
        return totalConnectionsCreated;
    }

    /**
     * Gets the number of consecutive failures that have occurred.
     */
    public int getConsecutiveFailures() {
        return consecutiveFailures;
    }

    /**
     * Checks if the circuit breaker is currently open.
     */
    public boolean isCircuitOpen() {
        return circuitOpen;
    }

    /**
     * Gets the time when the circuit breaker was opened (null if not open).
     */
    public Instant getCircuitOpenTime() {
        return circuitOpenTime;
    }

    /**
     * Checks if the connection pool is healthy and operational.
     */
    public boolean isHealthy() {
        return healthy;
    }

    /**
     * Gets the connection pool utilization percentage (0-100).
     */
    public double getUtilizationPercentage() {
        if (maxConnections == 0) {
            return 0.0;
        }
        return ((double) activeConnections / maxConnections) * 100.0;
    }

    /**
     * Gets the connection pool availability percentage (0-100).
     */
    public double getAvailabilityPercentage() {
        if (maxConnections == 0) {
            return 0.0;
        }
        return ((double) availableConnections / maxConnections) * 100.0;
    }

    @Override
    public String toString() {
        return new StringJoiner(", ", ConnectionPoolStats.class.getSimpleName() + "[", "]")
                .add("maxConnections=" + maxConnections)
                .add("activeConnections=" + activeConnections)
                .add("availableConnections=" + availableConnections)
                .add("totalConnectionsCreated=" + totalConnectionsCreated)
                .add("consecutiveFailures=" + consecutiveFailures)
                .add("circuitOpen=" + circuitOpen)
                .add("circuitOpenTime=" + circuitOpenTime)
                .add("healthy=" + healthy)
                .add("utilization=" + String.format("%.1f%%", getUtilizationPercentage()))
                .add("availability=" + String.format("%.1f%%", getAvailabilityPercentage()))
                .toString();
    }
}