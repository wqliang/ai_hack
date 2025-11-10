package ai.hack.rocketmq.model;

/**
 * Enumeration representing message processing states.
 * Tracks the lifecycle of a message through the RocketMQ client.
 */
public enum MessageStatus {

    /**
     * Message is pending processing.
     * Initial state for all messages.
     */
    PENDING("pending", "Awaiting processing"),

    /**
     * Message is currently being processed.
     * Active state for messages in transit or being handled.
     */
    PROCESSING("processing", "Currently being handled"),

    /**
     * Message processing completed successfully.
     * Terminal success state.
     */
    COMMITTED("committed", "Successfully processed"),

    /**
     * Message processing failed.
     * May be retried depending on configuration.
     */
    FAILED("failed", "Processing failed"),

    /**
     * Message processing timed out.
     * Special case of failure due to timeout.
     */
    TIMEOUT("timeout", "Processing timed out"),

    /**
     * Message is scheduled for retry.
     * Indicates retry attempt is pending.
     */
    RETRY("retry", "Scheduled for retry"),

    /**
     * Message has been delivered and acknowledged.
     * Final success confirmation state.
     */
    DELIVERED("delivered", "Delivered and acknowledged");

    private final String code;
    private final String description;

    MessageStatus(String code, String description) {
        this.code = code;
        this.description = description;
    }

    /**
     * Gets the status code.
     */
    public String getCode() {
        return code;
    }

    /**
     * Gets the status description.
     */
    public String getDescription() {
        return description;
    }

    /**
     * Checks if this is a terminal state (no further processing expected).
     */
    public boolean isTerminal() {
        return this == COMMITTED || this == DELIVERED;
    }

    /**
     * Checks if this is a failure state.
     */
    public boolean isFailure() {
        return this == FAILED || this == TIMEOUT;
    }

    /**
     * Checks if the message can be retried from this state.
     */
    public boolean isRetryable() {
        return this == FAILED || this == TIMEOUT;
    }

    /**
     * Checks if the message is in a active processing state.
     */
    public boolean isActive() {
        return this == PENDING || this == PROCESSING || this == RETRY;
    }

    /**
     * Gets the next state after a successful operation.
     */
    public MessageStatus getSuccessState() {
        return COMMITTED;
    }

    /**
     * Gets the next state after a failed operation.
     */
    public MessageStatus getFailureState() {
        return FAILED;
    }

    /**
     * Gets the retry state from this status.
     */
    public MessageStatus getRetryState() {
        return RETRY;
    }

    /**
     * Finds status by code string.
     */
    public static MessageStatus fromCode(String code) {
        for (MessageStatus status : values()) {
            if (status.code.equals(code)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown message status code: " + code);
    }

    @Override
    public String toString() {
        return String.format("%s (%s)", description, code);
    }
}