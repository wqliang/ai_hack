package ai.hack.rocketmq.callback;

import ai.hack.rocketmq.model.Message;

/**
 * Functional interface for message handling in async consumption.
 * Provides a clean callback mechanism for processing received messages.
 */
@FunctionalInterface
public interface MessageCallback {

    /**
     * Handle received message.
     *
     * @param message the received message
     * @return processing result indicating success/failure and optional response
     * @throws Exception if processing fails (will be wrapped in ProcessingResult.failure)
     */
    MessageProcessingResult processMessage(Message message) throws Exception;
}