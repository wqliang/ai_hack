package ai.hack.rocketmq.result;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Result of batch message sending operation.
 * Aggregates results from multiple message send operations.
 */
public class BatchSendResult {

    private final List<SendResult> results;
    private final int totalCount;
    private final int successCount;
    private final int failureCount;
    private final Duration totalProcessingTime;

    public BatchSendResult(List<SendResult> results) {
        this.results = Collections.unmodifiableList(results);
        this.totalCount = results.size();
        this.successCount = (int) results.stream().mapToInt(r -> r.isSuccess() ? 1 : 0).sum();
        this.failureCount = totalCount - successCount;

        // Calculate total processing time
        long totalNanos = results.stream()
                .filter(SendResult::isSuccess)
                .mapToLong(r -> r.getProcessingTime() != null ? r.getProcessingTime().toNanos() : 0)
                .sum();
        this.totalProcessingTime = Duration.ofNanos(totalNanos);
    }

    public List<SendResult> getResults() {
        return results;
    }

    public int getTotalCount() {
        return totalCount;
    }

    public int getSuccessCount() {
        return successCount;
    }

    public int getFailureCount() {
        return failureCount;
    }

    public Duration getTotalProcessingTime() {
        return totalProcessingTime;
    }

    /**
     * Gets the success rate as a percentage (0.0 to 100.0).
     */
    public double getSuccessRate() {
        if (totalCount == 0) {
            return 100.0;
        }
        return (successCount * 100.0) / totalCount;
    }

    /**
     * Gets the average processing time per message.
     */
    public Duration getAverageProcessingTime() {
        if (successCount == 0) {
            return Duration.ZERO;
        }
        return Duration.ofMillis(totalProcessingTime.toMillis() / successCount);
    }

    /**
     * Checks if the entire batch was successful.
     */
    public boolean isFullySuccessful() {
        return failureCount == 0;
    }

    /**
     * Checks if the batch had any success.
     */
    public boolean hasAnySuccess() {
        return successCount > 0;
    }

    /**
     * Gets only the successful results.
     */
    public List<SendResult> getSuccessfulResults() {
        return results.stream()
                .filter(SendResult::isSuccess)
                .toList();
    }

    /**
     * Gets only the failed results.
     */
    public List<SendResult> getFailedResults() {
        return results.stream()
                .filter(r -> !r.isSuccess())
                .toList();
    }

    /**
     * Gets the results that can be retried.
     */
    public List<SendResult> getRetryableResults() {
        return results.stream()
                .filter(r -> !r.isSuccess() && r.isRetryable())
                .toList();
    }

    /**
     * Creates a summary string of the batch result.
     */
    public String getSummary() {
        return String.format("Batch send: %d total, %d success (%.1f%%), %d failed, avg time: %dms",
                           totalCount, successCount, getSuccessRate(), failureCount,
                           getAverageProcessingTime().toMillis());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        BatchSendResult that = (BatchSendResult) o;
        return totalCount == that.totalCount &&
               successCount == that.successCount &&
               failureCount == that.failureCount &&
               Objects.equals(results, that.results) &&
               Objects.equals(totalProcessingTime, that.totalProcessingTime);
    }

    @Override
    public int hashCode() {
        return Objects.hash(results, totalCount, successCount, failureCount, totalProcessingTime);
    }

    @Override
    public String toString() {
        return String.format("BatchSendResult{%s, successRate=%.1f%%, avgTime=%dms}",
                           getSummary(), getSuccessRate(), getAverageProcessingTime().toMillis());
    }
}