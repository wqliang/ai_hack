package ai.hack.rocketmq.client.example;

import ai.hack.rocketmq.client.RpcReceiver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Example RPC receiver implementation that echoes requests back as responses.
 * <p>
 * This class demonstrates how to implement the {@link RpcReceiver} interface
 * for processing RPC requests. It provides simple echo functionality for both
 * single requests and streaming requests.
 * <p>
 * <strong>Usage Example:</strong>
 * <pre>{@code
 * // This receiver is automatically registered as a Spring bean
 * // and will process all incoming RPC requests
 *
 * // Single request processing:
 * // Input: "Hello"
 * // Output: "Echo: Hello"
 *
 * // Streaming request processing:
 * // Input: ["Message 1", "Message 2", "Message 3"]
 * // Output: "Streamed Echo: Message 1; Message 2; Message 3; (Total: 3 messages)"
 * }</pre>
 * <p>
 * <strong>Thread Safety:</strong>
 * <p>This implementation is thread-safe as it maintains no mutable state.
 * Multiple threads can safely call the processing methods concurrently.
 * <p>
 * <strong>Customization:</strong>
 * <p>To create your own receiver, implement the {@link RpcReceiver} interface
 * and annotate with {@code @Service}. Replace the echo logic with your
 * business logic.
 *
 * @author Claude Code
 * @since 1.0.0
 * @see RpcReceiver
 */
@Service
public class EchoReceiver implements RpcReceiver {

    private static final Logger logger = LoggerFactory.getLogger(EchoReceiver.class);

    /**
     * Processes a single RPC request by echoing it back with a prefix.
     * <p>
     * This method demonstrates basic request processing:
     * <ol>
     *   <li>Convert byte[] payload to String</li>
     *   <li>Process the request (in this case, add "Echo: " prefix)</li>
     *   <li>Convert response String back to byte[]</li>
     *   <li>Return response</li>
     * </ol>
     *
     * @param requestPayload the request data from sender
     * @return echoed response with "Echo: " prefix
     * @throws Exception if payload cannot be processed
     */
    @Override
    public byte[] processRequest(byte[] requestPayload) throws Exception {
        // Convert payload to string
        String requestText = new String(requestPayload, StandardCharsets.UTF_8);

        logger.debug("EchoReceiver processing single request: '{}'", requestText);

        // Process request - simple echo with prefix
        String responseText = "Echo: " + requestText;

        logger.debug("EchoReceiver sending response: '{}'", responseText);

        // Convert response back to bytes
        return responseText.getBytes(StandardCharsets.UTF_8);
    }

    /**
     * Processes multiple messages from a streaming session by concatenating them.
     * <p>
     * This method demonstrates streaming request processing:
     * <ol>
     *   <li>Iterate through all messages in order</li>
     *   <li>Convert each byte[] message to String</li>
     *   <li>Concatenate all messages with delimiter</li>
     *   <li>Add summary information (total count)</li>
     *   <li>Return aggregated response</li>
     * </ol>
     * <p>
     * <strong>Note:</strong> All messages are guaranteed to arrive in the order
     * they were sent, as they are routed to the same message queue based on
     * session ID.
     *
     * @param sessionId the unique session identifier
     * @param messages all messages received in this session, in order
     * @return aggregated response containing all messages
     * @throws Exception if messages cannot be processed
     */
    @Override
    public byte[] processStreamingRequest(String sessionId, List<byte[]> messages) throws Exception {
        logger.info("EchoReceiver processing streaming session: sessionId={}, messageCount={}",
            sessionId, messages.size());

        // StringBuilder for efficient string concatenation
        StringBuilder aggregated = new StringBuilder();
        aggregated.append("Streamed Echo: ");

        // Process each message in order
        for (int i = 0; i < messages.size(); i++) {
            byte[] messagePayload = messages.get(i);
            String messageText = new String(messagePayload, StandardCharsets.UTF_8);

            logger.debug("  Message {}: '{}'", i + 1, messageText);

            // Append message with delimiter
            aggregated.append(messageText);
            aggregated.append("; ");
        }

        // Add summary
        aggregated.append("(Total: ").append(messages.size()).append(" messages)");

        String responseText = aggregated.toString();
        logger.info("EchoReceiver streaming response: '{}'", responseText);

        return responseText.getBytes(StandardCharsets.UTF_8);
    }
}
