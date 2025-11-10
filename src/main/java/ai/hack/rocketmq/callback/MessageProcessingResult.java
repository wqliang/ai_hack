package ai.hack.rocketmq.callback;

import ai.hack.rocketmq.model.Message;

/**
 * Result of message processing in callback handlers.
 * Provides success/failure status and optional response message for request-response patterns.
 */
public class MessageProcessingResult {

    private final boolean success;
    private final String errorMessage;
    private final Exception exception;
    private final Message responseMessage;
    private final boolean retryable;

    // Private constructor for factory methods
    private MessageProcessingResult(boolean success, String errorMessage, Exception exception,
                                  Message responseMessage, boolean retryable) {
        this.success = success;
        this.errorMessage = errorMessage;
        this.exception = exception;
        this.responseMessage = responseMessage;
        this.retryable = retryable;
    }

    /**
     * Creates a successful processing result.
     */
    public static MessageProcessingResult success() {
        return new MessageProcessingResult(true, null, null, null, false);
    }

    /**
     * Creates a successful processing result with a response message.
     */
    public static MessageProcessingResult success(Message response) {
        return new MessageProcessingResult(true, null, null, response, false);
    }

    /**
     * Creates a failed processing result.
     */
    public static MessageProcessingResult failure(String error, Exception ex) {
        return new MessageProcessingResult(false, error, ex, null, true);
    }

    /**
     * Creates a failed processing result that should not be retried.
     */
    public static MessageProcessingResult failureNoRetry(String error, Exception ex) {
        return new MessageProcessingResult(false, error, ex, null, false);
    }

    /**
     * Checks if processing was successful.
     */
    public boolean isSuccess() {
        return success;
    }

    /**
     * Gets the error message if processing failed.
     */
    public String getErrorMessage() {
        return errorMessage;
    }

    /**
     * Gets the exception if processing failed.
     */
    public Exception getException() {
        return exception;
    }

    /**
     * Gets the response message (if any).
     */
    public Message getResponseMessage() {
        return responseMessage;
    }

    /**
     * Checks if the failure should be retried.
     */
    public boolean isRetryable() {
        return retryable && !success;
    }

    @Override
    public String toString() {
        return String.format("MessageProcessingResult{success=%s, error='%s', hasResponse=%s, retryable=%s}",
                           success, errorMessage, responseMessage != null, retryable);
    }
}