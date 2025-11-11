package ai.hack.rocketmq.client;

import java.util.List;

/**
 * Interface for implementing custom request processing logic.
 * <p>
 * Receivers handle incoming RPC requests from senders and produce responses.
 * Implementations define the business logic for processing single requests
 * and streaming requests with multiple messages.
 *
 * @author Claude Code
 * @since 1.0.0
 */
public interface RpcReceiver {

    /**
     * Processes a single RPC request and produces a response.
     * <p>
     * This method is called for each non-streaming RPC request. The implementation
     * should process the request payload and return the response payload. Any
     * exceptions thrown will be caught and sent as error responses to the sender.
     *
     * @param requestPayload the request data sent by sender (never null)
     * @return the response data to send back to sender (should not be null)
     * @throws Exception any processing exception (will be wrapped in error response)
     */
    byte[] processRequest(byte[] requestPayload) throws Exception;

    /**
     * Processes multiple messages from a streaming session and produces response.
     * <p>
     * This method is called when a streaming session ends. All messages received
     * during the session are provided in order. The implementation should process
     * all messages and return an aggregated response.
     *
     * @param sessionId the session identifier for this stream (never null)
     * @param messages all messages received in this session, in order (never null or empty)
     * @return the aggregated response to send back to sender (should not be null)
     * @throws Exception any processing exception (will be wrapped in error response)
     */
    byte[] processStreamingRequest(String sessionId, List<byte[]> messages) throws Exception;
}
