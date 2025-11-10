package ai.hack.rocketmq.exception;

/**
 * Error codes for different failure types in the RocketMQ client.
 * Provides standardized error classification and retry guidance.
 */
public enum ErrorCode {

    // Connection-related errors
    CONNECTION_FAILED("CONN_001", "Failed to connect to RocketMQ broker", false),
    AUTHENTICATION_FAILED("AUTH_001", "Authentication with broker failed", false),
    BROKER_UNAVAILABLE("BRK_001", "RocketMQ broker is unavailable", true),
    NETWORK_ERROR("NET_001", "Network communication error", true),

    // Message-related errors
    MESSAGE_TOO_LARGE("MSG_001", "Message exceeds size limit", false),
    INVALID_MESSAGE("MSG_002", "Invalid message format or content", false),
    MESSAGE_NOT_FOUND("MSG_003", "Message not found", false),

    // Time-related errors
    TIMEOUT("TIMEOUT_001", "Operation timed out", true),
    RETRY_EXCEEDED("RETRY_001", "Maximum retry attempts exceeded", false),

    // Configuration errors
    CONFIGURATION_ERROR("CONF_001", "Invalid configuration", false),
    TOPIC_NOT_FOUND("TOPIC_001", "Topic not found on broker", false),

    // Callback errors
    CALLBACK_FAILED("CALLBACK_001", "Message callback processing failed", false),
    CORRELATION_FAILED("CORREL_001", "Request-response correlation failed", false),

    // Persistence errors
    PERSISTENCE_ERROR("PERSIST_001", "Local persistence failed", false),
    RECOVERY_ERROR("RECOVERY_001", "Recovery from persistence failed", false),

    // Security errors
    TLS_ERROR("TLS_001", "TLS/TLS handshake failed", false),
    SECURITY_ERROR("SEC_001", "Security configuration error", false),

    // Resource errors
    RESOURCE_EXHAUSTED("RES_001", "Resources exhausted (memory, connections)", true),
    SYSTEM_OVERLOADED("SYS_001", "System overloaded, retry later", true),
    THREAD_POOL_ERROR("POOL_001", "Thread pool resource management error", false),

    // General errors
    INTERNAL_ERROR("INT_001", "Internal error occurred", false),
    UNKNOWN_ERROR("UNKNOWN", "Unknown error occurred", false),
    NOT_IMPLEMENTED("NI_001", "Feature not yet implemented", false);

    private final String code;
    private final String description;
    private final boolean retryable;

    ErrorCode(String code, String description, boolean retryable) {
        this.code = code;
        this.description = description;
        this.retryable = retryable;
    }

    /**
     * Gets the error code string.
     */
    public String getCode() {
        return code;
    }

    /**
     * Gets the error description.
     */
    public String getDescription() {
        return description;
    }

    /**
     * Checks if operations with this error should be retried.
     */
    public boolean isRetryable() {
        return retryable;
    }

    /**
     * Finds error code by code string.
     */
    public static ErrorCode fromCode(String code) {
        for (ErrorCode errorCode : values()) {
            if (errorCode.code.equals(code)) {
                return errorCode;
            }
        }
        return UNKNOWN_ERROR;
    }

    @Override
    public String toString() {
        return String.format("%s - %s (Retryable: %s)", code, description, retryable);
    }
}