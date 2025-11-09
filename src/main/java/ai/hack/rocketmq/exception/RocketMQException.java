package ai.hack.rocketmq.exception;

/**
 * Base exception for RocketMQ client operations.
 * All RocketMQ client exceptions extend this base class to provide
 * consistent error handling and error code mapping.
 */
public class RocketMQException extends Exception {

    private final ErrorCode errorCode;
    private final String context;

    public RocketMQException(ErrorCode errorCode, String message) {
        this(errorCode, message, null, null);
    }

    public RocketMQException(ErrorCode errorCode, String message, Throwable cause) {
        this(errorCode, message, cause, null);
    }

    public RocketMQException(ErrorCode errorCode, String message, String context) {
        this(errorCode, message, null, context);
    }

    public RocketMQException(ErrorCode errorCode, String message, Throwable cause, String context) {
        super(formatMessage(errorCode, message, context), cause);
        this.errorCode = errorCode;
        this.context = context;
    }

    private static String formatMessage(ErrorCode errorCode, String message, String context) {
        StringBuilder sb = new StringBuilder();
        sb.append("[").append(errorCode.getCode()).append("] ");
        sb.append(message);
        if (context != null && !context.isEmpty()) {
            sb.append(" (Context: ").append(context).append(")");
        }
        return sb.toString();
    }

    /**
     * Gets the error code for this exception.
     */
    public ErrorCode getErrorCode() {
        return errorCode;
    }

    /**
     * Gets the context information for this exception.
     */
    public String getContext() {
        return context;
    }

    /**
     * Checks if this exception indicates a retryable condition.
     */
    public boolean isRetryable() {
        return errorCode.isRetryable();
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "{" +
                "errorCode=" + errorCode +
                ", message='" + getMessage() + '\'' +
                (getStackTrace().length > 0 ? ", cause=" + getCause() : "") +
                '}';
    }
}