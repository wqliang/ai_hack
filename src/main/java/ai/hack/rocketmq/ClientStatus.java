package ai.hack.rocketmq;

import ai.hack.rocketmq.monitoring.MetricsCollector;
import ai.hack.rocketmq.model.MessageStatus;

import java.time.Instant;
import java.util.Collections;
import java.util.List;

/**
 * Current client status and performance metrics.
 * Provides comprehensive information about client health and performance.
 */
public class ClientStatus {

    private final ClientState state;
    private final MetricsCollector.PerformanceSnapshot metrics;
    private final List<String> subscribedTopics;
    private final Instant lastHealthCheck;

    public ClientStatus(ClientState state, MetricsCollector.PerformanceSnapshot metrics,
                       List<String> subscribedTopics, Instant lastHealthCheck) {
        this.state = state;
        this.metrics = metrics;
        this.subscribedTopics = subscribedTopics != null ? subscribedTopics : Collections.emptyList();
        this.lastHealthCheck = lastHealthCheck != null ? lastHealthCheck : Instant.now();
    }

    public ClientState getState() {
        return state;
    }

    public MetricsCollector.PerformanceSnapshot getMetrics() {
        return metrics;
    }

    public List<String> getSubscribedTopics() {
        return subscribedTopics;
    }

    public Instant getLastHealthCheck() {
        return lastHealthCheck;
    }

    /**
     * Checks if the client is in a healthy state.
     */
    public boolean isHealthy() {
        return state == ClientState.READY || state == ClientState.CONNECTING;
    }

    /**
     * Checks if the client is operational.
     */
    public boolean isOperational() {
        return state == ClientState.READY;
    }

    /**
     * Gets the current message throughput.
     */
    public double getCurrentThroughput() {
        return metrics != null ? metrics.getThroughput() : 0.0;
    }

    /**
     * Gets the current error rate.
     */
    public double getCurrentErrorRate() {
        return metrics != null ? metrics.getErrorRate() : 0.0;
    }

    /**
     * Gets the number of active connections.
     */
    public int getActiveConnections() {
        return metrics != null ? metrics.getActiveConnections() : 0;
    }

    @Override
    public String toString() {
        return String.format("ClientStatus{state=%s, subscribedTopics=%d, throughput=%.2f msg/s, " +
                           "errorRate=%.2f%%, connections=%d, lastCheck=%s}",
                           state, subscribedTopics.size(),
                           getCurrentThroughput(), getCurrentErrorRate(),
                           getActiveConnections(), lastHealthCheck);
    }
}