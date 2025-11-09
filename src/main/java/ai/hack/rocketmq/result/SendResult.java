package ai.hack.rocketmq.result;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Result of message sending operation.
 * Provides comprehensive information about the send operation outcome.
 */
public class SendResult {

    private final String messageId;
    private final String topic;
    private final boolean success;
    private final String errorMessage;
    private final long offset;
    private final Duration processingTime;
    private final String queueName;
    private final int queueId;
    private final long startTimestamp;

    // Create successful result
    public static SendResult success(String messageId, String topic, long offset, Duration processingTime) {
        return new SendResult(messageId, topic, true, null, offset, processingTime, null, -1, System.currentTimeMillis());
    }

    // Create successful result with queue information
    public static SendResult success(String messageId, String topic, long offset, Duration processingTime,
                                   String queueName, int queueId) {
        return new SendResult(messageId, topic, true, null, offset, processingTime, queueName, queueId, System.currentTimeMillis());
    }

    // Create failed result
    public static SendResult failure(String messageId, String topic, String errorMessage) {
        return new SendResult(messageId, topic, false, errorMessage, -1, Duration.ZERO, null, -1, System.currentTimeMillis());
    }

    // Private constructor
    private SendResult(String messageId, String topic, boolean success, String errorMessage,
                      long offset, Duration processingTime, String queueName, int queueId, long startTimestamp) {
        this.messageId = messageId;
        this.topic = topic;
        this.success = success;
        this.errorMessage = errorMessage;
        this.offset = offset;
        this.processingTime = processingTime;
        this.queueName = queueName;
        this.queueId = queueId;
        this.startTimestamp = startTimestamp;
    }

    // Getters
    public String getMessageId() {
        return messageId;
    }

    public String getTopic() {
        return topic;
    }

    public boolean isSuccess() {
        return success;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public long getOffset() {
        return offset;
    }

    public Duration getProcessingTime() {
        return processingTime;
    }

    public String getQueueName() {
        return queueName;
    }

    public int getQueueId() {
        return queueId;
    }

    public long getStartTimestamp() {
        return startTimestamp;
    }

    /**
     * Gets a list of errors if this result represents a failure.
     */
    public List<String> getErrors() {
        if (success) {
            return Collections.emptyList();
        }
        return Collections.singletonList(errorMessage);
    }

    /**
     * Checks if this result indicates a retryable error.
     */
    public boolean isRetryable() {
        if (success) {
            return false;
        }

        // Common retryable error conditions
        String error = errorMessage.toLowerCase();
        return error.contains("timeout") ||
               error.contains("connection") ||
               error.contains("network") ||
               error.contains("broker unavailable");
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SendResult that = (SendResult) o;
        return success == that.success &&
               offset == that.offset &&
               queueId == that.queueId &&
               Objects.equals(messageId, that.messageId) &&
               Objects.equals(topic, that.topic) &&
               Objects.equals(errorMessage, that.errorMessage) &&
               Objects.equals(processingTime, that.processingTime) &&
               Objects.equals(queueName, that.queueName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(messageId, topic, success, errorMessage, offset, processingTime, queueName, queueId);
    }

    @Override
    public String toString() {
        return String.format("SendResult{id='%s', topic='%s', success=%s, offset=%d, " +
                           "processingTime=%dms, queue='%s', queueId=%d%s}",
                           messageId, topic, success, offset,
                           processingTime != null ? processingTime.toMillis() : 0,
                           queueName, queueId,
                           success ? "" : ", error='" + errorMessage + "'");
    }
}