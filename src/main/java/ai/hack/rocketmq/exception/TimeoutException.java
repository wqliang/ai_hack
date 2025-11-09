package ai.hack.rocketmq.exception;

import java.time.Duration;

/**
 * Exception thrown when RocketMQ client operations exceed timeout limits.
 */
public class TimeoutException extends RocketMQException {

    private final Duration timeout;
    private final String operation;
    private final String correlationId;

    public TimeoutException(String operation, Duration timeout, String message) {
        super(ErrorCode.TIMEOUT, message);
        this.operation = operation;
        this.timeout = timeout;
        this.correlationId = null;
    }

    public TimeoutException(String operation, Duration timeout, String message, String correlationId) {
        super(ErrorCode.TIMEOUT, message);
        this.operation = operation;
        this.timeout = timeout;
        this.correlationId = correlationId;
    }

    public TimeoutException(String operation, Duration timeout, String message, Throwable cause) {
        super(ErrorCode.TIMEOUT, message, cause);
        this.operation = operation;
        this.timeout = timeout;
        this.correlationId = null;
    }

    public TimeoutException(String operation, Duration timeout, String message, String correlationId, Throwable cause) {
        super(ErrorCode.TIMEOUT, message, cause);
        this.operation = operation;
        this.timeout = timeout;
        this.correlationId = correlationId;
    }

    /**
     * Gets the timeout duration that was exceeded.
     */
    public Duration getTimeout() {
        return timeout;
    }

    /**
     * Gets the operation that timed out.
     */
    public String getOperation() {
        return operation;
    }

    /**
     * Gets the correlation ID if this timeout is related to a request-response pattern.
     */
    public String getCorrelationId() {
        return correlationId;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("TimeoutException{operation='").append(operation).append('\'');
        sb.append(", timeout=").append(timeout);
        if (correlationId != null) {
            sb.append(", correlationId='").append(correlationId).append('\'');
        }
        sb.append("}");
        return sb.toString();
    }
}